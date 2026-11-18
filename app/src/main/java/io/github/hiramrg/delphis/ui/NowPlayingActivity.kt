package io.github.hiramrg.delphis.ui

import androidx.compose.material3.Icon

import kotlin.math.roundToInt
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.hiramrg.delphis.core.GameDatabase
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.core.PresetLibrary
import io.github.hiramrg.delphis.platform.CoverActivity
import io.github.hiramrg.delphis.platform.DeviceInfo
import io.github.hiramrg.delphis.platform.DolphinBridge
import io.github.hiramrg.delphis.platform.DolphinExitService
import io.github.hiramrg.delphis.platform.CrashLog
import io.github.hiramrg.delphis.platform.GameLauncher
import io.github.hiramrg.delphis.platform.GameSession
import io.github.hiramrg.delphis.platform.GameWatch
import io.github.hiramrg.delphis.platform.PresetStore
import io.github.hiramrg.delphis.platform.Prefs
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.PowerSettingsNew
import io.github.hiramrg.delphis.core.BottomScreen

/**
 * The bottom screen while a game runs:
 *  - top-left back arrow closes the game and Dolphin and returns to Delphis,
 *  - top-right brightness pill opens a panel: normal brightness, Auto-dim for this session and
 *    "Turn screen off" (black at minimum brightness until the next touch),
 *  - the controls in the middle: main actions as big tiles, everything else in a small grid,
 *  - "Change preset" and "Edit preset" at the bottom (both restart the game to apply).
 * The window never takes input focus, so gamepad presses keep going to Dolphin on the top screen.
 */

/** Narrowest back-gesture zone assumed when Android reports none (immersive mode on some firmwares). */
private val EDGE_MIN = 28.dp

open class NowPlayingActivity : ComponentActivity() {
    companion object {
        /** Set when the game came back from the background and this screen is shown again. */
        const val EXTRA_RESUMED = "delphis.resumed"
    }

    private enum class Mode { VIEW, CHANGE, EDIT }

    /**
     * One screen (phones, tablets; see [InGameMenuActivity]): this view is Delphis' in-game menu, a panel
     * over the game. It takes the controller's focus while open, Ⓑ / ▶ Resume hide it again, and nothing
     * that belongs to the Thor's bottom screen (brightness, dimming, low refresh rate) is used.
     */
    protected open val panelMode: Boolean = false

    /**
     * One screen: hide the menu and give the game back the screen and the controller, by bringing Dolphin's
     * game to the front. (Never moveTaskToBack: that puts this task behind the Home screen, and Android closes
     * tasks that are kept out of Recent apps once they're behind Home, which ended the session, the pill and
     * the Home-button shortcut a second after every game started. On the Thor this view is on the second
     * display, which Android never trims.)
     */
    protected fun resumeGame() {
        val s = session ?: GameWatch.session
        if (s != null) bridge.bringToFront(s.uri, DeviceInfo.screenRoles(this, prefs.swapScreens).topDisplayId)
    }

    /** Set when the player quits from this menu (so closing it ends the session; Android closing it doesn't). */
    private var quitting = false
    /** One screen: opened without a game; closed right away and touches nothing on the way out. */
    private var emptyMenu = false

    /** Bumped each time the one-screen menu opens, to put the cursor on ▶ Resume. */
    private var openSignal by mutableIntStateOf(0)

    /** One screen: the Interface size factor (Settings → Display) for the panel. */
    private var uiScale = 1f

    /** One screen: L1 / R1 switch the menu's tabs (sequence, direction). */
    private var tabStep by mutableStateOf(0 to 0)

    override fun onResume() {
        super.onResume()
        if (panelMode) { openSignal++; GameWatch.menuOpen = true; PadGlyphs.update(true) }
    }

    override fun onPause() {
        if (panelMode) GameWatch.menuOpen = false
        super.onPause()
        recordPlayTime() // banked as we go, in case the process is killed without finishing
    }

    /**
     * One screen, "Quit game" from Delphis' Home while the game sits in the background: bring Dolphin back
     * to the front (its own menu does the autosave and exit), then close it like "Quit game" here.
     */
    private fun quitFromHome() {
        val s = session ?: return
        GameLauncher.run(this) {
            bridge.bringToFront(s.uri, DeviceInfo.screenRoles(this@NowPlayingActivity, prefs.swapScreens).topDisplayId)
            delay(1500)
            GameWatch.setSuspended(false)
        }
        backToMainMenu()
    }

    private lateinit var prefs: Prefs
    private lateinit var bridge: DolphinBridge
    private lateinit var store: PresetStore
    private var session by mutableStateOf<GameSession?>(null)
    private var startedAt by mutableLongStateOf(SystemClock.elapsedRealtime())
    private var busy by mutableStateOf<String?>(null)
    /**
     * Play time only counts while someone can actually be playing: the screen is on and unlocked, and
     * the game isn't in the background. (It used to count from start to finish, so a game left open
     * with the Thor asleep or locked piled up hours.)
     */
    private var playStart = SystemClock.elapsedRealtime()
    private var screenAwake = true
    private var counting = true
    /** Time counted since the last time it was saved; saved once it passes 30 s. */
    private var unsaved = 0L

    private fun recordPlayTime() {
        val s = session ?: return
        val now = SystemClock.elapsedRealtime()
        if (counting) unsaved += now - playStart
        playStart = now
        if (unsaved > 30_000) { prefs.addPlayTime(s.key, unsaved); unsaved = 0L }
    }

    /** Starts or stops the play-time clock (screen off / locked, game in the background). */
    private fun updateCounting() {
        val should = screenAwake && !GameWatch.suspended.value
        if (should == counting) return
        recordPlayTime()
        counting = should
        playStart = SystemClock.elapsedRealtime()
    }

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            screenAwake = when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> false
                Intent.ACTION_USER_PRESENT -> true
                // Screen on but still locked: wait for USER_PRESENT.
                Intent.ACTION_SCREEN_ON -> !(getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked
                else -> screenAwake
            }
            updateCounting()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        io.github.hiramrg.delphis.platform.Achievements.init(this)
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        bridge = DolphinBridge(this, prefs)
        store = PresetStore(this)
        registerReceiver(screenReceiver, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        })
        if (panelMode) {
            // A panel on the right of the game; a tap on the dimmed game beside it resumes.
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            val dm = resources.displayMetrics
            // Interface size: on a big screen the panel is drawn bigger, like the rest of Delphis.
            uiScale = UiScale.forScreen(this, prefs)
            val width = minOf((470 * dm.density * uiScale).toInt(), (dm.widthPixels * 0.66f).toInt())
            window.setLayout(width, WindowManager.LayoutParams.MATCH_PARENT)
            window.setGravity(Gravity.END)
        } else window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (panelMode) {
            padsAtStart += DeviceInfo.gamepads().map { it.name }
            getSystemService(android.hardware.input.InputManager::class.java).registerInputDeviceListener(padListener, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        if (!panelMode) preferLowRefreshRate()
        // One screen: a game starting (Dolphin, launched next, covers this), or the menu opened again after
        // Android closed it (the game kept running: same session, no new start).
        val reopened = panelMode && (intent.getBooleanExtra(InGameMenuActivity.EXTRA_OPEN, false) || intent.getBooleanExtra(InGameMenuActivity.EXTRA_QUIT, false))
        session = GameSession.from(intent) ?: if (panelMode) GameWatch.session else null
        // One screen: opened with no game running (e.g. a late tap after quitting): nothing to show, so close
        // at once instead of leaving an empty, invisible window over Delphis.
        if (panelMode && session == null) { emptyMenu = true; finish(); return }
        watch(session, announce = !reopened)
        if (panelMode && intent.getBooleanExtra(InGameMenuActivity.EXTRA_QUIT, false)) window.decorView.post { quitFromHome() }
        setContent {
            WiiTheme(dark = true) {
                session?.let { s ->
                    if (panelMode) ScaledUi(uiScale) {
                        val c = LocalWii.current
                        val shape = RoundedCornerShape(20.dp)
                        Box(Modifier.fillMaxSize().padding(8.dp).clip(shape).background(c.bg).border(2.dp, c.accent, shape)) { NowPlaying(s) }
                    } else NowPlaying(s)
                }
            }
        }
        if (!panelMode) blockSystemGestures()
    }

    /**
     * One screen: the menu's controller shortcuts for what's on screen right now (keys in the Thor's layout:
     * B = bottom, A = right, Y = left, X = top), kept up to date by the composition.
     */
    private var panelKeys: Map<Int, () -> Unit> = emptyMap()

    override fun dispatchKeyEvent(raw: KeyEvent): Boolean {
        if (!panelMode) return super.dispatchKeyEvent(raw)
        // One screen: controllers that don't report standard keys (e.g. a Switch Pro Controller) are translated.
        val event = io.github.hiramrg.delphis.platform.PadKeys.translate(raw, prefs, true)
        panelKeys[event.keyCode]?.let { action ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                CrashLog.event("menu shortcut ${KeyEvent.keyCodeToString(event.keyCode)}")
                action()
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1 || event.keyCode == KeyEvent.KEYCODE_BUTTON_R1)) {
            tabStep = (tabStep.first + 1) to (if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1)
            return true
        }
        if (super.dispatchKeyEvent(event)) return true
        if (event !== raw) io.github.hiramrg.delphis.platform.PadKeys.fallback(event)?.let { return super.dispatchKeyEvent(it) }
        return false
    }

    /** One screen: a controller connected while the game runs (it only works after a restart, and the touch buttons stay until then). */
    private var padJoined by mutableStateOf<String?>(null)
    private val padsAtStart = mutableSetOf<String>()
    private val padListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            PadGlyphs.update(true)
            if (session == null || GameLauncher.busy) return
            val pad = DeviceInfo.gamepads().firstOrNull { it.name !in padsAtStart } ?: return
            padsAtStart += pad.name
            padJoined = pad.name
            DolphinExitService.openMenu()
        }
        override fun onInputDeviceRemoved(deviceId: Int) = PadGlyphs.update(true)
        override fun onInputDeviceChanged(deviceId: Int) = Unit
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // One screen: a tap on the game beside the menu resumes it.
        if (panelMode && event.action == MotionEvent.ACTION_OUTSIDE) { resumeGame(); return true }
        return super.onTouchEvent(event)
    }

    /**
     * No Android navigation gestures on the in-game bottom screen: an edge swipe there is Android's
     * Back, which goes to Dolphin (the focused app) and opens its menu. The whole window is excluded;
     * with the bars stickily hidden Android honours all of it (otherwise the bottom 200 dp of each edge).
     * The swipe up from the very bottom edge (Home) can't be turned off by apps.
     */
    private fun blockSystemGestures() {
        if (android.os.Build.VERSION.SDK_INT < 29) return
        val decor = window.decorView
        decor.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            v.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, r - l, b - t))
        }
    }

    // ------------------------------------------------------------------ focus sink
    //
    // This screen's window is deliberately not focusable, so the gamepad keeps going to Dolphin.
    // But Android still treats this screen as the bottom display's active app, and any key event
    // sent to that display (the back gesture, some firmware keys) then waits 5 s for a focused
    // window that never comes → "Delphis isn't responding" (ANR: "Application does not have a
    // focused window"). A 1x1 invisible, untouchable, focusable sub-window gives Android somewhere
    // to deliver those keys. It's never touched, so it never pulls focus away from the top screen;
    // if it ever does receive gamepad input, the game is brought back to the front.

    private var focusSink: View? = null
    private var lastBounce = 0L

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (panelMode || focusSink != null) return
        val sink = object : View(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean = onSinkKey(event)
            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean = onSinkMotion(event)
        }.apply { isFocusable = true; isFocusableInTouchMode = true }
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            token = window.decorView.windowToken
            gravity = Gravity.TOP or Gravity.START
            title = "Delphis focus"
        }
        runCatching { windowManager.addView(sink, lp); focusSink = sink; sink.requestFocus() }
            .onFailure { CrashLog.event("couldn't add focus window: ${it.message}") }
    }

    private fun isGamepad(source: Int) =
        (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

    private fun onSinkKey(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBackPressedDispatcher.onBackPressed()
            return true
        }
        if (isGamepad(event.source)) { returnFocusToGame(); return true }
        return false
    }

    private fun onSinkMotion(event: MotionEvent): Boolean {
        if (isGamepad(event.source)) { returnFocusToGame(); return true }
        return false
    }

    /** Gamepad input landed on this screen: move the game back to the front so it gets the input. */
    private fun returnFocusToGame() {
        val s = session ?: return
        if (GameLauncher.busy || GameWatch.unexpectedStop.value) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBounce < 1500) return
        lastBounce = now
        CrashLog.event("gamepad input reached the bottom screen; giving it back to the game")
        val roles = DeviceInfo.screenRoles(this, prefs.swapScreens)
        bridge.bringToFront(s.uri, roles.topDisplayId)
    }

    private fun removeFocusSink() {
        focusSink?.let { runCatching { windowManager.removeViewImmediate(it) } }
        focusSink = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (panelMode && intent.getBooleanExtra(InGameMenuActivity.EXTRA_QUIT, false)) { quitFromHome(); return }
        // One screen: just opening the menu over the running game (the intent carries the session, it's not a new game).
        if (panelMode && intent.getBooleanExtra(InGameMenuActivity.EXTRA_OPEN, false)) return
        if (intent.getBooleanExtra(EXTRA_RESUMED, false)) {
            // Back from the home screen: this window came to the front, so give focus back to the game.
            window.decorView.postDelayed({
                val s = session ?: return@postDelayed
                bridge.bringToFront(s.uri, DeviceInfo.screenRoles(this, prefs.swapScreens).topDisplayId)
            }, 300)
            return
        }
        GameSession.from(intent)?.let {
            recordPlayTime(); session?.let { old -> if (unsaved > 0) prefs.addPlayTime(old.key, unsaved) }; unsaved = 0L
            prefs.markPlayed(it.key); session = it; startedAt = SystemClock.elapsedRealtime(); playStart = startedAt; watch(it)
        }
    }

    /** Lets the rest of the app know which game is running, to spot it stopping unexpectedly. */
    private fun watch(s: GameSession?, announce: Boolean = true) {
        if (panelMode && s != null && announce) DolphinExitService.gameStarted(this, s.key)
        GameWatch.session = s
        GameWatch.unexpectedStop.value = false
        GameWatch.suspended.value = false
        // The player went to the home screen / another app: hide with the game (it comes back with it).
        // (One screen: nothing to hide, the menu is already behind the game.)
        GameWatch.onSuspended = { gone -> runOnUiThread { updateCounting(); if (gone && !panelMode) moveTaskToBack(true) } }
    }

    override fun onDestroy() {
        if (emptyMenu) {
            runCatching { getSystemService(android.hardware.input.InputManager::class.java).unregisterInputDeviceListener(padListener) }
            runCatching { unregisterReceiver(screenReceiver) }
            super.onDestroy()
            return
        }
        if (panelMode) runCatching { getSystemService(android.hardware.input.InputManager::class.java).unregisterInputDeviceListener(padListener) }
        removeFocusSink()
        runCatching { unregisterReceiver(screenReceiver) }
        if (isFinishing && panelMode && !quitting) {
            // One screen: Android closed the menu (e.g. after its Home button) but the game is still running in
            // Dolphin. Keep the session, the pill and the shortcut; the menu opens again from them.
            recordPlayTime(); session?.let { if (unsaved > 0) prefs.addPlayTime(it.key, unsaved) }; unsaved = 0L
            GameWatch.onSuspended = null
            GameWatch.menuOpen = false
            io.github.hiramrg.delphis.platform.CrashLog.event("in-game menu closed by Android; the game keeps running")
            super.onDestroy()
            return
        }
        if (isFinishing && panelMode) DolphinExitService.gameEnded()
        if (isFinishing) { DolphinExitService.hideSides(); recordPlayTime(); session?.let { if (unsaved > 0) prefs.addPlayTime(it.key, unsaved) }; unsaved = 0L; GameWatch.session = null; GameWatch.onSuspended = null; GameWatch.suspended.value = false }
        super.onDestroy()
    }

    /**
     * This screen is static, so ask for the display's lowest refresh rate (same resolution). The
     * panel redrawing less often saves battery and heat while Dolphin runs on the other screen.
     */
    @Suppress("DEPRECATION")
    private fun preferLowRefreshRate() {
        runCatching {
            val display = windowManager.defaultDisplay
            val current = display.mode
            val lowest = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .minByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = lowest.modeId
                preferredRefreshRate = lowest.refreshRate
            }
        }
    }

    /** The system brightness as a 0-1 window level (where a fade from "bright" starts). */
    private fun systemBrightness(): Float =
        runCatching { android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f }
            .getOrDefault(0.5f).coerceIn(0.05f, 1f)

    private fun setWindowBrightness(level: Float) {
        window.attributes = window.attributes.apply { screenBrightness = level }
    }

    private fun autosaveOn() = prefs.autoSaveFor(session?.info?.gameId) && DolphinExitService.isEnabled(this)

    private fun resolve(id: String): Preset = store.byId(id) ?: PresetLibrary.GAMECUBE

    /** Writes [preset] for this game and restarts the game in a fresh Dolphin. */
    private fun applyAndRestart(s: GameSession, preset: Preset, remember: Boolean) {
        busy = "Restarting the game with the new controls…"
        GameLauncher.run(this) {
            // One screen: Dolphin comes to the front first, for its own menu (autosave, exit).
            if (panelMode) { bridge.bringToFront(s.uri, DeviceInfo.screenRoles(this@NowPlayingActivity, prefs.swapScreens).topDisplayId); delay(700) }
            val applied = MainActivity.applyPreset(this@NowPlayingActivity, bridge, prefs, s.info.gameId, preset)
            if (remember) prefs.setPresetFor(s.key, preset.id)
            val roles = DeviceInfo.screenRoles(this@NowPlayingActivity, prefs.swapScreens)
            // With autosave on, the game was just saved while closing, so it picks up right there.
            bridge.launchFresh(s.uri, roles.topDisplayId, s.info.gameId?.takeIf { prefs.autoSaveFor(it) }, s.info.title, s.info.gameId)
            session = s.copy(presetId = preset.id, applied = applied)
            watch(session)
            startedAt = SystemClock.elapsedRealtime()
            busy = null
        }
    }

    /** Opens [pkg] (a frontend) the way its icon would, so its existing screen comes back. */
    private fun frontendIntent(pkg: String): Intent? {
        val i = packageManager.getLaunchIntentForPackage(pkg)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
                .takeIf { packageManager.resolveActivity(it, 0) != null }
        return i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    /** Closes the game and Dolphin, then shows Delphis' game list (or the frontend that started the game). */
    private fun backToMainMenu() {
        busy = "Closing the game…"
        quitting = true
        GameLauncher.run(this) {
            if (panelMode) (session ?: GameWatch.session)?.let { s0 ->
                bridge.bringToFront(s0.uri, DeviceInfo.screenRoles(this@NowPlayingActivity, prefs.swapScreens).topDisplayId); delay(700)
            }
            val roles = DeviceInfo.screenRoles(this@NowPlayingActivity, prefs.swapScreens)
            bridge.closeDolphin(roles.topDisplayId, "Closing the game…")
            // The game is closed: Delphis' Home mustn't see it as still running when it comes back (it would offer
            // "Back to game" and mark the game as gone to the background).
            GameWatch.session = null
            GameWatch.suspended.value = false
            // Show the game list (or the frontend that started the game) on the top screen, where the game was, and close this one.
            val menu = session?.returnTo?.let { frontendIntent(it) }
                ?: Intent(this@NowPlayingActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val onTop = roles.topDisplayId?.let { ActivityOptions.makeBasic().setLaunchDisplayId(it).toBundle() }
            if (runCatching { startActivity(menu, onTop) }.isFailure) runCatching { startActivity(menu) }
            CoverActivity.dismiss()
            // Lift the loading screen once the game list is up.
            io.github.hiramrg.delphis.platform.DolphinExitService.hideLoading(900)
            busy = null
            finish()
        }
    }

    @Composable
    private fun NowPlaying(s: GameSession) {
        // Who plays (multiplayer): with several players, gyro / tilt / trackpad were left out at launch.
        val gid = s.info.gameId
        val startPlayers = remember(startedAt) { if (gid != null) runCatching { io.github.hiramrg.delphis.platform.Pads.players(prefs, gid) }.getOrDefault(emptyList()) else emptyList() }
        val preset = remember(s.presetId, startPlayers) {
            resolve(s.presetId).let { if (io.github.hiramrg.delphis.platform.Pads.multiplayer(startPlayers)) io.github.hiramrg.delphis.core.Players.multiplayerPreset(it) else it }
        }
        var mode by remember { mutableStateOf(Mode.VIEW) }
        // ☀ pill: the bottom screen's normal brightness (awake, or dimming off). The dimmed level is set in Settings.
        var brightness by remember { mutableIntStateOf(prefs.bottomBrightness ?: (systemBrightness() * 100).roundToInt()) }
        var brightnessChosen by remember { mutableStateOf(prefs.bottomBrightness != null) }
        val dimLevel = remember { prefs.brightnessPercent }
        var showBrightness by remember { mutableStateOf(false) }
        var confirmExit by remember { mutableStateOf(false) }
        var exitDontAsk by remember { mutableStateOf(false) }
        // Set when a change needs the game restarted; shows the confirmation first.
        var restartAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        // "Restart" from the action row (no new controls): the confirmation says so.
        var plainRestart by remember { mutableStateOf(false) }
        // Set when closing the game needs the close-games helper, which isn't turned on yet.
        var helperPrompt by remember { mutableStateOf<(() -> Unit)?>(null) }
        // Closing/restarting goes through here: asks once to turn on the helper if it's off.
        val closingGame: (() -> Unit) -> Unit = { action ->
            if (DolphinExitService.isEnabled(this@NowPlayingActivity) || prefs.skipExitHelperPrompt) action()
            else helperPrompt = action
        }

        // Bottom-screen trackpad (trackpad presets): Map / Trackpad switch, finger-on-pad state, speed.
        // (One screen has no second screen to use as a trackpad: such presets show their map there.)
        var padView by remember(preset.id) { mutableStateOf(preset.isTrackpad && !panelMode) }
        var padActive by remember { mutableStateOf(false) }
        var padBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var padSpeed by remember(s.key) { mutableStateOf(prefs.trackpadSpeed(s.key)) }
        var recenterCount by remember { mutableIntStateOf(0) }
        val topDisplay = remember { DeviceInfo.screenRoles(this@NowPlayingActivity, prefs.swapScreens).topDisplayId }
        val recenterPointer = { recenterCount++; DolphinExitService.trackpadRecenter(topDisplay) }

        // GameCube: the 4:3 / 16:9 picture. The shape and fill apply when the game starts; the side fills change live.
        val screenGid = gid?.takeIf { s.info.platform == io.github.hiramrg.delphis.core.Platform.GAMECUBE }
        var screenChoice by remember { mutableStateOf(screenGid?.let { prefs.screenFor(it) }) }
        val runningScreen = remember(startedAt) { screenGid?.let { prefs.screenFor(it) } }
        var showPicture by remember { mutableStateOf(false) }
        // Looks: the game's post-processing shader; a change applies when the game starts again.
        val lookGid = gid?.takeIf { prefs.looksEnabled }
        var gameLook by remember { mutableStateOf(lookGid?.let { prefs.lookFor(it) }) }
        val runningLook = remember(startedAt) { lookGid?.let { prefs.lookFor(it) } }
        var pictureTab by remember { mutableIntStateOf(0) }
        var pictureBefore by remember { mutableStateOf<Pair<io.github.hiramrg.delphis.core.GameScreen.Choice?, io.github.hiramrg.delphis.core.Looks.Look?>?>(null) }
        // Resolution: the Picture panel's third tab (needs a restart).
        var gameRes by remember { mutableStateOf(gid?.let { prefs.resolutionFor(it) }) }
        val runningRes = remember(startedAt) { gid?.let { prefs.resolutionFor(it) } }
        var resBefore by remember { mutableStateOf(gameRes) }
        val resAdvice = remember { io.github.hiramrg.delphis.platform.DeviceInfo.resolutionAdvice(this@NowPlayingActivity) }
        fun openPicture(tab: Int) { pictureBefore = screenChoice to gameLook; resBefore = gameRes; pictureTab = tab; showBrightness = false; showPicture = true }
        val sideColour by produceState(android.graphics.Color.rgb(75, 58, 143), screenGid) {
            screenGid?.let { value = io.github.hiramrg.delphis.platform.SideFill.colourOf(io.github.hiramrg.delphis.platform.CoverArt(this@NowPlayingActivity).load(it)) }
        }
        LaunchedEffect(startedAt, screenChoice?.sides, sideColour) {
            val sc = screenChoice
            if (sc != null && runningScreen?.shape == io.github.hiramrg.delphis.core.GameScreen.Shape.STANDARD) DolphinExitService.showSides(sc.sides, sideColour, topDisplay)
            else DolphinExitService.hideSides()
        }
        LaunchedEffect(startedAt) { DolphinExitService.trackpadReset() }
        val fade by animateFloatAsState(if (padActive) 0.25f else 1f, tween(200), label = "padFade")

        // RetroAchievements: unlock badges (top screen over the game, bottom screen here, or both) and the 🏆 list.
        val achievements = rememberAchievements(prefs, s.key, s.info)
        var showAchievements by remember { mutableStateOf(false) }
        var bottomPopup by remember { mutableStateOf<io.github.hiramrg.delphis.platform.Achievements.Popup?>(null) }
        LaunchedEffect(startedAt) {
            val ctx = this@NowPlayingActivity
            io.github.hiramrg.delphis.platform.Achievements.watch(ctx, prefs, s.key, s.info) { popup ->
                val where = if (panelMode) "top" else prefs.raWhere
                var shownOnTop = false
                if (where != "bottom") {
                    val badge = io.github.hiramrg.delphis.platform.Achievements.badge(ctx, popup.unlock.badgeUrl())
                    shownOnTop = DolphinExitService.showAchievement(popup, badge, topDisplay)
                }
                // Bottom screen when chosen, or when the top-screen helper isn't on.
                if (where != "top" || !shownOnTop) {
                    bottomPopup = popup
                    delay(5_000)
                    bottomPopup = null
                    delay(400)
                }
            }
        }

        // Turned off with "Turn screen off" in the brightness panel; the next touch wakes it.
        var screenOff by remember { mutableStateOf(false) }
        // Android's back gesture must never just hide this screen and leave the game running
        // without its controls view: it backs out of change/edit, otherwise it's "Main menu".
        BackHandler(enabled = busy == null) {
            when {
                screenOff -> screenOff = false
                showBrightness -> showBrightness = false
                confirmExit || restartAction != null || helperPrompt != null -> { confirmExit = false; restartAction = null; helperPrompt = null }
                mode != Mode.VIEW -> mode = Mode.VIEW
                showAchievements -> showAchievements = false
                showPicture -> showPicture = false
                panelMode -> resumeGame()
                prefs.skipExitWarning -> closingGame { backToMainMenu() }
                else -> confirmExit = true
            }
        }

        // Bright for the chosen delay (Settings → Bottom screen) after the game starts and after every
        // touch, then dim or black depending on the mode. A touch on a dimmed/black screen only wakes it
        // (so it can't press "Main menu" by accident). Menus, dialogs and the editor keep it bright.
        val screenMode = remember { prefs.bottomScreenMode }
        val delayMs = remember { prefs.dimDelaySeconds * 1000L }
        var autoDim by remember { mutableStateOf(!panelMode && screenMode != BottomScreen.Mode.ON) }
        var idle by remember { mutableStateOf(false) }
        var lastTouch by remember { mutableLongStateOf(0L) }
        val overlayOpen = confirmExit || restartAction != null || helperPrompt != null || busy != null
        val keepBright = mode != Mode.VIEW || overlayOpen || showBrightness || showPicture
        LaunchedEffect(startedAt, lastTouch, autoDim, keepBright) {
            idle = false
            if (!autoDim || keepBright) return@LaunchedEffect
            delay(delayMs)
            idle = true
        }
        val look = BottomScreen.look(screenMode, autoDim, idle, screenOff, keepBright)
        val dimmedNow by rememberUpdatedState(look != BottomScreen.Look.BRIGHT)
        // Touches on the trackpad aim; they never wake, dim or get swallowed.
        val padHit by rememberUpdatedState<(androidx.compose.ui.geometry.Offset) -> Boolean> { p ->
            mode == Mode.VIEW && padView && !showAchievements && padBounds?.contains(p) == true
        }

        // Window brightness: the chosen normal level when bright (the system level until one is chosen);
        // the dimmed level (never brighter than normal) when dimmed. Changes fade over FADE_MS.
        val target: Float? = when {
            panelMode -> null
            showBrightness -> Prefs.windowBrightness(brightness)
            look == BottomScreen.Look.BRIGHT -> if (brightnessChosen) Prefs.windowBrightness(brightness) else null
            look == BottomScreen.Look.DIM -> Prefs.windowBrightness(minOf(dimLevel, brightness))
            else -> Prefs.windowBrightness(0)
        }
        val level = remember { Animatable(-1f) } // -1 = follow the system brightness
        LaunchedEffect(target) {
            val to = target ?: systemBrightness()
            if (showBrightness) {
                level.snapTo(to); setWindowBrightness(to)
                return@LaunchedEffect
            }
            if (level.value < 0f) level.snapTo(systemBrightness())
            level.animateTo(to, tween(BottomScreen.FADE_MS)) { setWindowBrightness(value) }
            if (target == null) {
                level.snapTo(-1f)
                setWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            }
        }
        val blackAlpha by animateFloatAsState(if (look == BottomScreen.Look.BLACK) 1f else 0f, tween(BottomScreen.FADE_MS), label = "black")
        // Firmwares that ignore per-display brightness still get darker pixels at the lowest settings.
        val contentAlpha = if ((look == BottomScreen.Look.DIM && minOf(dimLevel, brightness) <= 5) || brightness <= 3) 0.6f else 1f

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.isNotEmpty() && event.changes.all { padHit(it.position) }) continue
                        if (dimmedNow) event.changes.forEach { it.consume() }
                        screenOff = false
                        lastTouch = SystemClock.uptimeMillis()
                    }
                }
            },
        ) {
            StatusBanner(Modifier.alpha(contentAlpha * fade))
            Column(Modifier.fillMaxSize().alpha(contentAlpha).padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 6.dp)) {
                // ---------------- header: the same on the Thor's bottom screen and the one-screen menu (cover, game,
                // controls in use; ▶ Resume over the game on one screen, ☀ on the Thor), then the tabs.
                val changeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                val padName = if (panelMode) io.github.hiramrg.delphis.platform.rememberPads(prefs).firstOrNull()?.name else null
                Row(Modifier.fillMaxWidth().alpha(fade), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameThumb(gid, s.info.platform)
                    Column(Modifier.weight(1f)) {
                        CapsLabel(if (panelMode) "Paused · Delphis" else "Playing")
                        ScrollingTitle(s.info.title, 14.sp, start = true)
                        val controlsLine = (if (!s.applied) "⚠ not applied · " else "") +
                            (if (panelMode) (padName?.let { "🎮 $it · " } ?: "👆 Touch controls · ") else "") + preset.controller.label
                        Text(
                            controlsLine, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, onTextLayout = { TextFit.check(controlsLine, it) },
                            color = if (s.applied) LocalWii.current.textDim else MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (panelMode) {
                        val resumeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                        LaunchedEffect(openSignal) { delay(150); runCatching { resumeFocus.requestFocus() } }
                        // D-pad down goes straight to the action buttons instead of stopping at the map's toggle.
                        PanelBtn("▶ Resume", "B", false, Modifier.focusRequester(resumeFocus).focusProperties { if (mode == Mode.VIEW && !(preset.isTrackpad && padView)) down = changeFocus }) { resumeGame() }
                    } else {
                        // Trackpad on screen: the action row is replaced by the pad's controls, so Quit stays up here.
                        if (preset.isTrackpad && padView && mode == Mode.VIEW) TopChip("Quit") { if (prefs.skipExitWarning) closingGame { backToMainMenu() } else confirmExit = true }
                        TopChip("☀", on = showBrightness) {
                            showPicture = false
                            showBrightness = !showBrightness
                        }
                    }
                }
                // Tabs: Controls · Achievements · Picture (L1 / R1 on one screen; touch on the Thor, whose buttons belong to Dolphin).
                val tabs = buildList {
                    add(0)
                    if (achievements != null) add(1)
                    if (screenChoice != null || gameLook != null || gameRes != null) add(2)
                }
                val tab = when { showPicture -> 2; showAchievements -> 1; else -> 0 }
                fun selectTab(t: Int) {
                    showBrightness = false
                    when (t) {
                        1 -> { showPicture = false; showAchievements = true }
                        2 -> { showAchievements = false; openPicture(if (screenChoice != null) 0 else 1) }
                        else -> { showAchievements = false; showPicture = false }
                    }
                }
                LaunchedEffect(tabStep) {
                    val (seq, dir) = tabStep
                    // The Picture panel is a pop-up with its own Cancel / Done: L1 / R1 wait until it's closed.
                    if (seq == 0 || mode != Mode.VIEW || tabs.size < 2 || showPicture) return@LaunchedEffect
                    selectTab(tabs[(tabs.indexOf(tab).coerceAtLeast(0) + dir + tabs.size) % tabs.size])
                }
                if (mode == Mode.VIEW && tabs.size > 1) Row(
                    Modifier.fillMaxWidth().padding(top = 7.dp).alpha(fade),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (panelMode) KeyIcon("L1")
                    Segmented(
                        tabs.map { it to when (it) { 1 -> "🏆 Achievements"; 2 -> "▭ Picture"; else -> "🎮 Controls" } },
                        tab, { selectTab(it) }, height = 28.dp,
                    )
                    if (panelMode) KeyIcon("R1")
                }
                Spacer(Modifier.height(8.dp))

                // ---------------- players (multiplayer): who's who; tap to restart with the controllers connected now
                val livePads = io.github.hiramrg.delphis.platform.rememberPads(prefs)
                val livePlayers = if (gid != null) io.github.hiramrg.delphis.core.Players.assign(livePads, prefs.playerSetup(gid)) else emptyList()
                if (mode == Mode.VIEW && (startPlayers.size > 1 || livePlayers.size > 1)) {
                    val changed = livePlayers.map { it.key } != startPlayers.map { it.key }
                    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).alpha(fade), horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayersRow(if (changed) livePlayers else startPlayers, onChange = {
                            showBrightness = false
                            restartAction = { applyAndRestart(s, preset, remember = false) }
                        }, label = false)
                        if (changed) Text("↻ Controllers changed · tap to restart with these players", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 3.dp))
                    }
                }

                // ---------------- middle
                when (mode) {
                    // Same Thor button map as the game page.
                    Mode.VIEW -> if (showAchievements && achievements != null) {
                        val shape = RoundedCornerShape(16.dp)
                        AchievementList(
                            achievements,
                            Modifier.weight(1f).fillMaxWidth().clip(shape).background(Color(0xFF1F242A)).border(2.dp, Color(0xFF6B5A2E), shape).padding(8.dp),
                            onClose = { showAchievements = false },
                        )
                    } else if (panelMode && PadGlyphs.touch) WiiCard(Modifier.weight(1f).fillMaxWidth(), radius = 18.dp, padding = 14.dp) {
                        // No controller: what's really on screen is Dolphin's touch buttons, not a controller map.
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CapsLabel("Touch controls")
                            Text("Dolphin's on-screen buttons", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = LocalWii.current.text)
                            Text(
                                "No controller is connected, so you play with the buttons Dolphin draws over the game, set up as ${preset.controller.label}. " +
                                    "Connect a controller and restart the game to play with it instead.",
                                fontSize = 12.sp, color = LocalWii.current.textDim, lineHeight = 16.sp,
                            )
                        }
                    } else if (!preset.isTrackpad || panelMode) WiiCard(Modifier.weight(1f).fillMaxWidth(), radius = 18.dp, padding = 8.dp) {
                        ControlsSwitchable(preset, prefs, s.key, Modifier.fillMaxSize())
                    } else Column(Modifier.weight(1f).fillMaxWidth()) {
                        Segmented(
                            listOf(false to "▦ Map", true to "◎ Trackpad"), padView, { padView = it },
                            Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp).alpha(fade),
                        )
                        val touchButtons = remember(preset) { io.github.hiramrg.delphis.core.TouchButtons.of(preset) }
                        // Keep the pad (and its buttons) out of Android's left/right back-gesture zones: a swipe that starts
                        // there sends Back to Dolphin, which opens its menu. Padding moves them clear; exclusion covers the rest.
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
                        val gestures = WindowInsets.systemGestures
                        val edgeL = with(density) { gestures.getLeft(density, dir).toDp() }.coerceAtLeast(EDGE_MIN)
                        val edgeR = with(density) { gestures.getRight(density, dir).toDp() }.coerceAtLeast(EDGE_MIN)
                        if (padView) Row(
                            Modifier.weight(1f).fillMaxWidth().padding(start = (edgeL - 10.dp + 4.dp).coerceAtLeast(0.dp), end = (edgeR - 10.dp + 4.dp).coerceAtLeast(0.dp)),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TrackpadPad(
                                Modifier.weight(1f).fillMaxHeight().systemGestureExclusion().onGloballyPositioned { padBounds = it.boundsInRoot() },
                                onActive = { padActive = it },
                                onDown = { DolphinExitService.trackpadDown(topDisplay) },
                                onMove = { dx, dy -> DolphinExitService.trackpadMove(dx, dy, padSpeed) },
                                onUp = { DolphinExitService.trackpadUp() },
                                onDoubleTap = { recenterPointer() },
                                recenterCount = recenterCount,
                            )
                            // Motion touch buttons (e.g. Red Steel Trackpad + Touch Buttons): held while touched.
                            if (touchButtons.isNotEmpty()) TouchButtonColumn(
                                touchButtons, Modifier.width(96.dp).fillMaxHeight().systemGestureExclusion(),
                                onPress = { i, pressed -> io.github.hiramrg.delphis.platform.DsuServer.setTouchButton(i, pressed) },
                            )
                        } else WiiCard(Modifier.weight(1f).fillMaxWidth(), radius = 18.dp, padding = 8.dp) {
                            ControlsSwitchable(preset, prefs, s.key, Modifier.fillMaxSize())
                        }
                    }
                    Mode.CHANGE -> LayoutSwitcher(s, preset, onCancel = { mode = Mode.VIEW }) { chosen ->
                        restartAction = { mode = Mode.VIEW; applyAndRestart(s, chosen, remember = true) }
                    }
                    Mode.EDIT -> PresetEditor(
                        base = preset, customId = store.customIdFor(s.key), saveLabel = "Save & restart",
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        updateName = if (store.isMine(preset.id)) null else store.root(preset).name,
                        newName = store.newName(s.key, preset),
                        onReset = if (store.isEdited(preset.id)) ({
                            restartAction = {
                                val original = store.root(preset)
                                store.delete(preset.id)
                                mode = Mode.VIEW
                                applyAndRestart(s, original, remember = true)
                            }
                        }) else null,
                        onCancel = { mode = Mode.VIEW },
                        onSave = { edited, newName ->
                            restartAction = {
                                // One of My presets keeps its id; a built-in becomes this game's version or a new preset.
                                val saved = when {
                                    store.isMine(preset.id) -> edited.copy(id = preset.id, name = preset.name).also { store.save(it) }
                                    newName == null -> store.saveEdited(s.key, preset, edited)
                                    else -> store.saveNew(s.key, edited, newName)
                                }
                                mode = Mode.VIEW
                                applyAndRestart(s, saved, remember = true)
                            }
                        },
                    )
                }

                // ---------------- bottom bar
                // Multiplayer: what's different for Xbox / PlayStation players (the map uses the Thor's letters).
                if (mode == Mode.VIEW && !(preset.isTrackpad && padView) && io.github.hiramrg.delphis.platform.Pads.multiplayer(startPlayers)) {
                    ButtonLetterNotes(startPlayers, Modifier.padding(top = 5.dp))
                }
                if (mode == Mode.VIEW && preset.isTrackpad && padView) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp).alpha(fade), verticalAlignment = Alignment.CenterVertically) {
                        PanelBtn("◎ Recenter", null, false) { recenterPointer() }
                        Spacer(Modifier.weight(1f))
                        Text("Speed", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalWii.current.textDim, modifier = Modifier.padding(end = 6.dp))
                        Segmented(
                            io.github.hiramrg.delphis.core.Trackpad.Speed.entries.map { it to it.label }, padSpeed,
                            { padSpeed = it; prefs.setTrackpadSpeed(s.key, it) },
                        )
                    }
                } else if (mode == Mode.VIEW) {
                    // The same actions on the Thor and on one screen: change or edit the controls, restart, quit.
                    // One screen: each shows its own controller button, so they sit in two rows to keep every label whole.
                    val change: @Composable (Modifier) -> Unit = { m -> PanelBtn("Change", if (panelMode && !PadGlyphs.touch) "Y" else null, true, m.focusRequester(changeFocus)) { showBrightness = false; mode = Mode.CHANGE } }
                    val edit: @Composable (Modifier) -> Unit = { m -> PanelBtn("✎ Edit", null, false, m) { showBrightness = false; mode = Mode.EDIT } }
                    val restart: @Composable (Modifier) -> Unit = { m ->
                        PanelBtn("Restart", if (panelMode && !PadGlyphs.touch) "⊖" else null, false, m) {
                            showBrightness = false; plainRestart = true
                            restartAction = { applyAndRestart(s, preset, remember = false) }
                        }
                    }
                    val quit: @Composable (Modifier) -> Unit = { m ->
                        PanelBtn("Quit game", if (panelMode && !PadGlyphs.touch) "X" else null, false, m, warn = true) {
                            showBrightness = false
                            if (prefs.skipExitWarning) closingGame { backToMainMenu() } else { exitDontAsk = false; confirmExit = true }
                        }
                    }
                    // With touch controls there are no controller buttons to show, so one row is enough.
                    if (panelMode && !PadGlyphs.touch) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            change(Modifier.weight(1f)); edit(Modifier.weight(0.85f))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            restart(Modifier.weight(1f)); quit(Modifier.weight(1.1f))
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                        ) {
                            KeyIcon("A", size = 16.dp)
                            Text("or", fontSize = 11.sp, color = LocalWii.current.textDim)
                            KeyIcon("⊕", size = 16.dp)
                            Text("back to the game", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalWii.current.textDim)
                        }
                    } else Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        change(Modifier.weight(1f)); edit(Modifier.weight(0.85f)); restart(Modifier.weight(1f)); quit(Modifier.weight(1.2f))
                    }
                }
            }

            UnlockToast(bottomPopup, Modifier.align(Alignment.TopCenter).padding(start = 10.dp, end = 10.dp, top = 30.dp))

            // The brightness panel closes by itself 4 s after the last touch (or by tapping outside it).
            if (showBrightness) {
                LaunchedEffect(lastTouch) {
                    delay(4000)
                    showBrightness = false
                }
                BrightnessPanel(
                    value = brightness,
                    onChange = { brightness = it; brightnessChosen = true; prefs.bottomBrightness = it },
                    autoDim = autoDim,
                    onAutoDim = { autoDim = it },
                    onScreenOff = { showBrightness = false; screenOff = true },
                    onDismiss = { showBrightness = false },
                )
            }

            // Picture: ▭ Screen (GameCube; the sides change right away, shape / 16:9 fill need a restart)
            // and ✦ Look (needs a restart). Touch only here: in game the Thor's buttons belong to Dolphin.
            if (showPicture && (screenChoice != null || gameLook != null || gameRes != null)) {
                val sc = screenChoice
                val needsRestart = (runningScreen != null && sc != null && (sc.shape != runningScreen.shape ||
                    (sc.shape == io.github.hiramrg.delphis.core.GameScreen.Shape.WIDE && sc.fill != runningScreen.fill))) ||
                    (gameLook != null && gameLook != runningLook) || (gameRes != null && gameRes != runningRes)
                PicturePanel(
                    s.info.title, pictureTab, { pictureTab = it },
                    sc, screenGid, prefs.gcUseHack, sideColour, { ch -> screenChoice = ch; screenGid?.let { prefs.setScreenFor(it, ch) } },
                    gameLook, { l -> gameLook = l; lookGid?.let { prefs.setLookFor(it, l) } },
                    res = gameRes, onRes = { l -> gameRes = l; gid?.let { prefs.setResolutionFor(it, l) } }, advice = resAdvice,
                    onCancel = {
                        resBefore?.let { r -> gameRes = r; gid?.let { prefs.setResolutionFor(it, r) } }
                        pictureBefore?.let { (bs, bl) ->
                            if (bs != null) { screenChoice = bs; screenGid?.let { prefs.setScreenFor(it, bs) } }
                            if (bl != null) { gameLook = bl; lookGid?.let { prefs.setLookFor(it, bl) } }
                        }
                        showPicture = false
                    },
                    onDone = {
                        showPicture = false
                        if (needsRestart) restartAction = { applyAndRestart(s, preset, remember = false) }
                    },
                    doneLabel = if (needsRestart) "Restart game" else "Done",
                    note = if (needsRestart) "Dolphin applies this when the game starts." else null,
                    compact = true,
                )
            }

            // In-window dialogs (no separate dialog window, so Dolphin keeps gamepad focus).
            // One screen: a button for every action, so nobody has to steer the focus with a stick.
            if (panelMode) SideEffect {
                val quit = { showBrightness = false; if (prefs.skipExitWarning) closingGame { backToMainMenu() } else { exitDontAsk = false; confirmExit = true } }
                val restart = { showBrightness = false; plainRestart = true; restartAction = { applyAndRestart(s, preset, remember = false) } }
                val pending = restartAction
                panelKeys = when {
                    confirmExit -> mapOf(
                        KeyEvent.KEYCODE_BUTTON_B to { confirmExit = false; if (exitDontAsk) prefs.skipExitWarning = true; closingGame { backToMainMenu() } },
                        KeyEvent.KEYCODE_BUTTON_A to { confirmExit = false },
                        KeyEvent.KEYCODE_BUTTON_Y to { exitDontAsk = !exitDontAsk },
                    )
                    pending != null -> mapOf(
                        KeyEvent.KEYCODE_BUTTON_B to { restartAction = null; pending() },
                        KeyEvent.KEYCODE_BUTTON_A to { restartAction = null },
                    )
                    mode != Mode.VIEW || showPicture || showBrightness -> emptyMap()
                    else -> mapOf(
                        KeyEvent.KEYCODE_BUTTON_A to { resumeGame() },
                        KeyEvent.KEYCODE_BUTTON_START to { resumeGame() },
                        KeyEvent.KEYCODE_BUTTON_Y to { showBrightness = false; mode = Mode.CHANGE },
                        KeyEvent.KEYCODE_BUTTON_X to quit,
                        KeyEvent.KEYCODE_BUTTON_SELECT to restart,
                    )
                }
            }
            if (confirmExit) ExitDialog(exitDontAsk, { exitDontAsk = it }, onDismiss = { confirmExit = false }) { dontAsk ->
                confirmExit = false
                if (dontAsk) prefs.skipExitWarning = true
                closingGame { backToMainMenu() }
            }
            restartAction?.let { action ->
                OverlayDialog(
                    title = "Restart the game?",
                    body = (if (plainRestart) "Delphis will close the game and start it again. "
                        else "Dolphin only reads controls when a game starts, so Delphis will close and relaunch the game with your new controls. ") +
                        if (autosaveOn()) "Your progress is autosaved first and the game continues from there." else "Any unsaved progress will be lost.",
                    confirm = if (plainRestart) "Restart" else "Save & restart", dismiss = "Cancel",
                    onDismiss = { restartAction = null; plainRestart = false },
                    onConfirm = { restartAction = null; plainRestart = false; closingGame(action) },
                )
            }
            // One screen: a controller connected mid-game. Dolphin only picks controllers up when a game starts.
            padJoined?.let { name ->
                // Which player it becomes on this device: on a phone / tablet it takes over from the touch controls.
                val joinedAs = remember(name) {
                    val pads = io.github.hiramrg.delphis.platform.Pads.detect(prefs)
                    io.github.hiramrg.delphis.core.Players.assign(pads, prefs.playerSetup(s.key)).indexOfFirst { it.name == name }.takeIf { it >= 0 }?.plus(1) ?: 1
                }
                val replacesTouch = panelMode && joinedAs == 1
                OverlayDialog(
                    title = "$name · Player $joinedAs",
                    body = "Dolphin only picks up controllers when a game starts, so restart the game to play with it" +
                        (if (replacesTouch) " (the touch controls go away then). " else " as Player $joinedAs. ") +
                        if (autosaveOn()) "Your progress is autosaved first and the game continues from there." else "Any unsaved progress will be lost.",
                    confirm = "Restart game", dismiss = "Later",
                    onDismiss = { padJoined = null },
                    onConfirm = { padJoined = null; closingGame { applyAndRestart(s, preset, remember = false) } },
                )
            }
            // Dolphin's game screen went away without Delphis closing it (most likely a Dolphin crash).
            if (GameWatch.unexpectedStop.value && busy == null && helperPrompt == null) {
                OverlayDialog(
                    title = "The game stopped",
                    body = "Dolphin's game screen closed on its own — Dolphin may have crashed. Restart the game?",
                    confirm = "Restart game", dismiss = "Main menu",
                    onDismiss = { GameWatch.unexpectedStop.value = false; backToMainMenu() },
                    onConfirm = { GameWatch.unexpectedStop.value = false; applyAndRestart(s, preset, remember = false) },
                    onOutside = { GameWatch.unexpectedStop.value = false },
                )
            }
            helperPrompt?.let { action ->
                ExitHelperDialog(
                    onOpenSettings = { helperPrompt = null; DolphinExitService.openSettings(this@NowPlayingActivity) },
                    onSkip = { dontAsk -> helperPrompt = null; if (dontAsk) prefs.skipExitHelperPrompt = true; action() },
                    onDismiss = { helperPrompt = null },
                )
            }

            // Screen off (button or the "Screen off" mode): black, with a hint that fades out after 2 s.
            if (blackAlpha > 0f) {
                var hint by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { delay(2000); hint = false }
                val hintAlpha by animateFloatAsState(if (hint) 1f else 0f, tween(600), label = "hint")
                Box(Modifier.fillMaxSize().alpha(blackAlpha).background(Color.Black), contentAlignment = Alignment.Center) {
                    Column(Modifier.alpha(hintAlpha), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PowerSettingsNew, null, tint = Color(0xFF5A626C), modifier = Modifier.size(30.dp))
                        Text("Screen off · tap to wake", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF5A626C), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            busy?.let { msg ->
                Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(msg, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }

    }

    /** The game's cover, small, beside its name in the menu header (platform colour while it loads or if it has none). */
    @Composable
    private fun GameThumb(gameId: String?, platform: io.github.hiramrg.delphis.core.Platform) {
        val cover by produceState<android.graphics.Bitmap?>(null, gameId) {
            value = gameId?.let { io.github.hiramrg.delphis.platform.CoverArt(this@NowPlayingActivity).load(it) }
        }
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier.size(width = 34.dp, height = 46.dp).clip(shape)
                .background(if (platform == io.github.hiramrg.delphis.core.Platform.GAMECUBE) Color(0xFF3B2F66) else Color(0xFF2B3F9E)),
        ) {
            cover?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
    }

    /** The panel under the brightness pill: normal brightness, Auto-dim for this session, Turn screen off. */
    @Composable
    private fun BrightnessPanel(
        value: Int, onChange: (Int) -> Unit, autoDim: Boolean, onAutoDim: (Boolean) -> Unit,
        onScreenOff: () -> Unit, onDismiss: () -> Unit,
    ) {
        val c = LocalWii.current
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
            PopupPanel(
                "Bottom screen", "Brightness",
                Modifier.align(Alignment.TopEnd).padding(top = 74.dp, end = 10.dp).width(280.dp).clickable(enabled = false) {},
                corner = { Text("$value", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Glow.Yellow) },
            ) {
                Slider(
                    value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.accent, inactiveTrackColor = c.cardBorder),
                )
                SettingRow("Auto-dim", "Just for this game session", onClick = { onAutoDim(!autoDim) }) { ThemedToggle(autoDim) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PanelBtn("Turn screen off", null, false, onClick = onScreenOff)
                }
                Text("Tap anywhere to turn it back on", fontSize = 11.sp, color = c.textDim, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    /** Explains the close-games helper and opens Accessibility settings to turn it on. */
    @Composable
    private fun ExitHelperDialog(onOpenSettings: () -> Unit, onSkip: (Boolean) -> Unit, onDismiss: () -> Unit) {
        var dontAsk by remember { mutableStateOf(false) }
        OverlayDialog(
            title = "Turn on \"close games\"?",
            body = "On the Thor, Android won't let Delphis force-close Dolphin, so the old game can keep running and the next one fails to start. " +
                "Turn on \"Delphis – close games\" in Accessibility (Installed apps / Downloaded services) and Delphis will press Dolphin's own " +
                "\"Exit Emulation\" for you. It doesn't touch your controller input, so there's no input delay.",
            confirm = "Open settings", dismiss = "Try without it",
            onDismiss = { onSkip(dontAsk) }, onConfirm = onOpenSettings,
            onOutside = onDismiss,
        ) {
            DontAskRow("Don't ask again", dontAsk) { dontAsk = it }
        }
    }

    @Composable
    private fun ExitDialog(dontAsk: Boolean, onDontAsk: (Boolean) -> Unit, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
        OverlayDialog(
            title = "Back to main menu?",
            body = "This will close the game and Dolphin. " +
                if (autosaveOn()) "Your progress is autosaved, and the game continues from there next time you start it from Delphis." else "Any unsaved progress will be lost.",
            confirm = "Close game", dismiss = "Keep playing",
            onDismiss = onDismiss, onConfirm = { onConfirm(dontAsk) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DontAskRow("Don't show this again", dontAsk, onDontAsk)
                if (panelMode) KeyIcon("Y")
            }
        }
    }

    /** A dialog drawn inside this window (a real Dialog window would take focus away from Dolphin). Uses the shared pop-up panel. */
    @Composable
    private fun OverlayDialog(
        title: String, body: String, confirm: String, dismiss: String,
        onDismiss: () -> Unit, onConfirm: () -> Unit, onOutside: () -> Unit = onDismiss, extra: @Composable () -> Unit = {},
    ) {
        OverlayPanel(onOutside) {
            PopupPanel(
                listOfNotNull("In game", GameWatch.session?.info?.title).joinToString(" · "), title, Modifier.widthIn(max = 420.dp),
                buttons = {
                    PanelBtn(dismiss, if (panelMode) "A" else null, false, onClick = onDismiss)
                    PanelBtn(confirm, if (panelMode) "B" else null, true, onClick = onConfirm)
                },
            ) {
                Text(body, fontSize = 12.5.sp, lineHeight = 16.sp, color = LocalWii.current.textDim)
                extra()
            }
        }
    }

    /** Pick another preset (built-in or your edited one), preview it, then apply with a restart. */
    @Composable
    private fun ColumnScope.LayoutSwitcher(s: GameSession, current: Preset, onCancel: () -> Unit, onApply: (Preset) -> Unit) {
        val suggestion = remember(s.key) { GameDatabase.suggest(s.info) }
        val groups = remember(s.key) {
            io.github.hiramrg.delphis.ui.PresetGroups(store.withEdits(s.key, suggestion.choices), store.withEdits(s.key, suggestion.alternatives), store.mine(s.key))
        }
        var candidate by remember { mutableStateOf(current) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth > maxHeight * 1.05f
            val list: @Composable (Modifier) -> Unit = { m ->
                PresetChooser(groups, selected = current, focused = candidate, gameTitle = s.info.title, isEdited = { store.isEdited(it.id) }, modifier = m) { candidate = it }
            }
            val preview: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    ControlsOverview(candidate, Modifier.weight(1f).fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                        PrimaryGlowButton("Use this preset", Icons.Filled.Check, onClick = { onApply(candidate) }, enabled = candidate.id != current.id || !s.applied, fontSize = 14.sp)
                    }
                }
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                list(Modifier.weight(0.42f).fillMaxHeight())
                Spacer(Modifier.width(8.dp))
                preview(Modifier.weight(0.58f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                preview(Modifier.weight(0.55f).fillMaxWidth())
                list(Modifier.weight(0.45f).fillMaxWidth())
            }
        }
    }
}

/** Thin bar across the top of the in-game screen: app name, clock, battery. */
@Composable
private fun StatusBanner(modifier: Modifier = Modifier) {
    val c = LocalWii.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    var batteryTemp by remember { mutableStateOf<Float?>(null) }
    var cpuTemp by remember { mutableStateOf<Float?>(null) }
    // CPU temperature from the thermal zones, read once a minute (cheap; keeps the Thor cool).
    LaunchedEffect(Unit) {
        while (true) {
            cpuTemp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readCpuTemp() }
            kotlinx.coroutines.delay(60_000)
        }
    }
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, i: Intent) {
                val l = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && scale > 0) level = l * 100 / scale
                val st = i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                charging = st == android.os.BatteryManager.BATTERY_STATUS_CHARGING || st == android.os.BatteryManager.BATTERY_STATUS_FULL
                val t = i.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                if (t != Int.MIN_VALUE) batteryTemp = t / 10f
            }
        }
        val sticky = ctx.registerReceiver(receiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { receiver.onReceive(ctx, it) }
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
    Box(
        modifier.fillMaxWidth().height(22.dp).background(c.bar).border(androidx.compose.foundation.BorderStroke(1.dp, c.barBorder)).padding(horizontal = 10.dp),
    ) {
        Text("Delphis", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, modifier = Modifier.align(Alignment.CenterStart))
        Text(rememberClock(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.text, modifier = Modifier.align(Alignment.Center))
        if (level >= 0) Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            // Temperature: the CPU when Android lets us read it, otherwise the battery. Orange when
            // Android itself reports the Thor is getting hot (or past a sensible fallback limit).
            val cpu = cpuTemp
            val temp = cpu ?: batteryTemp
            if (temp != null) {
                val status = remember(temp) { thermalStatus(ctx) }
                val hot = if (status >= 0) status >= android.os.PowerManager.THERMAL_STATUS_MODERATE
                else if (cpu != null) temp >= 80f else temp >= 45f
                val tc = if (hot) c.orange else c.text
                Icon(Icons.Filled.Thermostat, contentDescription = "Temperature", tint = tc, modifier = Modifier.size(13.dp))
                Text("${temp.roundToInt()}°", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tc)
                Spacer(Modifier.width(9.dp))
            }
            BatteryIcon(level, charging)
            Spacer(Modifier.width(5.dp))
            Text("$level%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.text)
        }
    }
}

/** Reads every /sys/class/thermal zone once and returns the hottest CPU one, or null if none is readable. */
private fun readCpuTemp(): Float? = runCatching {
    val zones = java.io.File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }.orEmpty()
        .mapNotNull { z -> runCatching { java.io.File(z, "type").readText() to java.io.File(z, "temp").readText() }.getOrNull() }
    io.github.hiramrg.delphis.core.Thermal.cpuTemp(zones)
}.getOrNull()

/** Android's own thermal status (PowerManager.THERMAL_STATUS_*), or -1 if unavailable. */
private fun thermalStatus(ctx: android.content.Context): Int =
    runCatching { (ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).currentThermalStatus }.getOrDefault(-1)

@Composable
private fun BatteryIcon(level: Int, charging: Boolean) {
    val c = LocalWii.current
    val fill = when { charging -> Glow.Green; level <= 15 -> Glow.Red; else -> c.text }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 20.dp, height = 10.dp).border(1.5.dp, c.textDim, RoundedCornerShape(2.dp)).padding(1.5.dp)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((level.coerceIn(0, 100) / 100f).coerceAtLeast(0.08f)).background(fill, RoundedCornerShape(1.dp)))
        }
        Box(Modifier.size(width = 2.dp, height = 5.dp).background(c.textDim))
    }
}
