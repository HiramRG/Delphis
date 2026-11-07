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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
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

/**
 * The bottom screen while a game runs:
 *  - top-left back arrow closes the game and Dolphin and returns to Delphis,
 *  - top-right brightness pill opens a 0-100 slider for this screen,
 *  - the controls in the middle: main actions as big tiles, everything else in a small grid,
 *  - "Change preset" and "Edit preset" at the bottom (both restart the game to apply).
 * The window never takes input focus, so gamepad presses keep going to Dolphin on the top screen.
 */
private const val WAKE_MS = 1500L

class NowPlayingActivity : ComponentActivity() {
    companion object {
        /** Set when the game came back from the background and this screen is shown again. */
        const val EXTRA_RESUMED = "delphis.resumed"
    }

    private enum class Mode { VIEW, CHANGE, EDIT }

    private lateinit var prefs: Prefs
    private lateinit var bridge: DolphinBridge
    private lateinit var store: PresetStore
    private var session by mutableStateOf<GameSession?>(null)
    private var startedAt by mutableLongStateOf(SystemClock.elapsedRealtime())
    private var busy by mutableStateOf<String?>(null)
    /** When the current game started on this screen, for the play-time stat. */
    private var playStart = SystemClock.elapsedRealtime()

    private fun recordPlayTime() {
        val s = session ?: return
        val ms = SystemClock.elapsedRealtime() - playStart
        if (ms > 30_000) prefs.addPlayTime(s.key, ms)
        playStart = SystemClock.elapsedRealtime()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        bridge = DolphinBridge(this, prefs)
        store = PresetStore(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        preferLowRefreshRate()
        session = GameSession.from(intent)
        watch(session)
        setContent { WiiTheme(dark = true) { session?.let { NowPlaying(it) } } }
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
        if (focusSink != null) return
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
        if (intent.getBooleanExtra(EXTRA_RESUMED, false)) {
            // Back from the home screen: this window came to the front, so give focus back to the game.
            window.decorView.postDelayed({
                val s = session ?: return@postDelayed
                bridge.bringToFront(s.uri, DeviceInfo.screenRoles(this, prefs.swapScreens).topDisplayId)
            }, 300)
            return
        }
        GameSession.from(intent)?.let { recordPlayTime(); prefs.markPlayed(it.key); session = it; startedAt = SystemClock.elapsedRealtime(); watch(it) }
    }

    /** Lets the rest of the app know which game is running, to spot it stopping unexpectedly. */
    private fun watch(s: GameSession?) {
        GameWatch.session = s
        GameWatch.unexpectedStop.value = false
        GameWatch.suspended.value = false
        // The player went to the home screen / another app: hide with the game (it comes back with it).
        GameWatch.onSuspended = { gone -> if (gone) runOnUiThread { moveTaskToBack(true) } }
    }

    override fun onPause() {
        super.onPause()
        recordPlayTime() // banked as we go, in case the process is killed without finishing
    }

    override fun onDestroy() {
        removeFocusSink()
        if (isFinishing) { recordPlayTime(); GameWatch.session = null; GameWatch.onSuspended = null; GameWatch.suspended.value = false }
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

    private fun setWindowBrightness(level: Float) {
        window.attributes = window.attributes.apply { screenBrightness = level }
    }

    private fun autosaveOn() = prefs.autoSaveFor(session?.info?.gameId) && DolphinExitService.isEnabled(this)

    private fun resolve(id: String): Preset = store.byId(id) ?: PresetLibrary.GAMECUBE

    /** Writes [preset] for this game and restarts the game in a fresh Dolphin. */
    private fun applyAndRestart(s: GameSession, preset: Preset, remember: Boolean) {
        busy = "Restarting the game with the new controls…"
        GameLauncher.run(this) {
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

    /** Closes the game and Dolphin, then shows Delphis' game list. */
    private fun backToMainMenu() {
        busy = "Closing the game…"
        GameLauncher.run(this) {
            val roles = DeviceInfo.screenRoles(this@NowPlayingActivity, prefs.swapScreens)
            bridge.closeDolphin(roles.topDisplayId, "Closing the game…")
            // Show the game list on the top screen, where the game was, and close this one.
            val menu = Intent(this@NowPlayingActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
        val preset = remember(s.presetId) { resolve(s.presetId) }
        var mode by remember { mutableStateOf(Mode.VIEW) }
        var brightness by remember { mutableIntStateOf(prefs.brightnessPercent) }
        var showBrightness by remember { mutableStateOf(false) }
        var started by remember(startedAt) { mutableStateOf(false) }
        var confirmExit by remember { mutableStateOf(false) }
        // Set when a change needs the game restarted; shows the confirmation first.
        var restartAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        // Set when closing the game needs the close-games helper, which isn't turned on yet.
        var helperPrompt by remember { mutableStateOf<(() -> Unit)?>(null) }
        // Closing/restarting goes through here: asks once to turn on the helper if it's off.
        val closingGame: (() -> Unit) -> Unit = { action ->
            if (DolphinExitService.isEnabled(this@NowPlayingActivity) || prefs.skipExitHelperPrompt) action()
            else helperPrompt = action
        }
        var lastBrightnessTouch by remember { mutableLongStateOf(0L) }

        // Full brightness for the first few seconds (so you can read it), then the chosen level.
        // While changing or editing the preset the screen stays bright.
        LaunchedEffect(startedAt) {
            delay(prefs.dimDelaySeconds * 1000L)
            started = true
        }
        // Android's back gesture must never just hide this screen and leave the game running
        // without its controls view: it backs out of change/edit, otherwise it's "Main menu".
        BackHandler(enabled = busy == null) {
            when {
                showBrightness -> showBrightness = false
                confirmExit || restartAction != null || helperPrompt != null -> { confirmExit = false; restartAction = null; helperPrompt = null }
                mode != Mode.VIEW -> mode = Mode.VIEW
                prefs.skipExitWarning -> closingGame { backToMainMenu() }
                else -> confirmExit = true
            }
        }

        // Touching the dimmed screen wakes it (that first touch only wakes, so it can't press "Main
        // menu" by accident); it dims again after WAKE_MS without touches. Menus/dialogs keep it bright.
        var awake by remember { mutableStateOf(false) }
        var lastTouch by remember { mutableLongStateOf(0L) }
        LaunchedEffect(lastTouch) {
            if (!awake) return@LaunchedEffect
            delay(WAKE_MS)
            awake = false
        }
        val overlayOpen = confirmExit || restartAction != null || helperPrompt != null || busy != null
        // While the brightness slider is open, show the level being picked.
        val bright = !showBrightness && (!started || mode != Mode.VIEW || awake || overlayOpen)
        val dimmedNow by rememberUpdatedState(!bright && !showBrightness)
        LaunchedEffect(bright, brightness) {
            setWindowBrightness(if (bright) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else Prefs.windowBrightness(brightness))
        }
        // Firmwares that ignore per-display brightness still get darker pixels at the lowest settings.
        val contentAlpha = if (!bright && brightness <= 5) 0.6f else 1f

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (dimmedNow) event.changes.forEach { it.consume() }
                        awake = true
                        lastTouch = SystemClock.uptimeMillis()
                    }
                }
            },
        ) {
            StatusBanner(Modifier.alpha(contentAlpha))
            Column(Modifier.fillMaxSize().alpha(contentAlpha).padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 6.dp)) {
                // ---------------- top bar: Home | title (centred) | brightness
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        HomePill { if (prefs.skipExitWarning) backToMainMenu() else confirmExit = true }
                    }
                    Column(
                        Modifier.align(Alignment.Center).padding(horizontal = 104.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(s.info.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text(
                            (if (!s.applied) "⚠ not applied · " else "") + preset.controller.label,
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (s.applied) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        BrightnessPill(brightness, open = showBrightness) {
                            showBrightness = !showBrightness
                            started = true // show the chosen brightness right away
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // ---------------- middle
                when (mode) {
                    // Same Thor button map as the game page.
                    Mode.VIEW -> WiiCard(Modifier.weight(1f).fillMaxWidth(), radius = 18.dp, padding = 8.dp) {
                        ControlsMap(preset, Modifier.fillMaxSize())
                    }
                    Mode.CHANGE -> LayoutSwitcher(s, preset, onCancel = { mode = Mode.VIEW }) { chosen ->
                        restartAction = { mode = Mode.VIEW; applyAndRestart(s, chosen, remember = true) }
                    }
                    Mode.EDIT -> PresetEditor(
                        base = preset, customId = store.customIdFor(s.key), saveLabel = "Save & restart",
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onCancel = { mode = Mode.VIEW },
                        onSave = { custom ->
                            restartAction = {
                                store.save(custom)
                                mode = Mode.VIEW
                                applyAndRestart(s, custom, remember = true)
                            }
                        },
                    )
                }

                // ---------------- bottom bar
                if (mode == Mode.VIEW) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryGlowButton("Change preset", null, onClick = { showBrightness = false; mode = Mode.CHANGE }, modifier = Modifier.weight(1f))
                        SecondaryGlowButton("Edit preset", null, onClick = { showBrightness = false; mode = Mode.EDIT }, modifier = Modifier.weight(1f))
                    }
                }
            }

            // The brightness panel closes by itself 1 s after the last touch (or by tapping outside it).
            if (showBrightness) {
                LaunchedEffect(lastBrightnessTouch) {
                    delay(1000)
                    showBrightness = false
                }
                BrightnessPanel(
                    value = brightness,
                    onChange = { brightness = it; prefs.brightnessPercent = it; lastBrightnessTouch = SystemClock.uptimeMillis() },
                    onDone = { lastBrightnessTouch = SystemClock.uptimeMillis() },
                    onDismiss = { showBrightness = false },
                )
            }

            // In-window dialogs (no separate dialog window, so Dolphin keeps gamepad focus).
            if (confirmExit) ExitDialog(onDismiss = { confirmExit = false }) { dontAsk ->
                confirmExit = false
                if (dontAsk) prefs.skipExitWarning = true
                closingGame { backToMainMenu() }
            }
            restartAction?.let { action ->
                OverlayDialog(
                    title = "Restart the game?",
                    body = "Dolphin only reads controls when a game starts, so Delphis will close and relaunch the game with your new controls. " +
                        if (autosaveOn()) "Your progress is autosaved first and the game continues from there." else "Any unsaved progress will be lost.",
                    confirm = "Save & restart", dismiss = "Cancel",
                    onDismiss = { restartAction = null },
                    onConfirm = { restartAction = null; closingGame(action) },
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

    /** The 0-100 slider under the brightness pill. Tapping outside closes it. */
    @Composable
    private fun BrightnessPanel(value: Int, onChange: (Int) -> Unit, onDone: () -> Unit, onDismiss: () -> Unit) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 74.dp, end = 10.dp).width(250.dp)
                    .clip(RoundedCornerShape(16.dp)).background(LocalWii.current.card)
                    .border(2.dp, LocalWii.current.cardBorder, RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {}.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Brightness", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("$value", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Glow.Yellow)
                }
                Slider(
                    value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, onValueChangeFinished = onDone, valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = Glow.Yellow, activeTrackColor = Glow.Yellow, inactiveTrackColor = LocalWii.current.cardBorder),
                )
                Row {
                    Text("0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("100", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { dontAsk = !dontAsk }) {
                Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                Text("Don't ask again", fontSize = 14.sp)
            }
        }
    }

    @Composable
    private fun ExitDialog(onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
        var dontAsk by remember { mutableStateOf(false) }
        OverlayDialog(
            title = "Back to main menu?",
            body = "This will close the game and Dolphin. " +
                if (autosaveOn()) "Your progress is autosaved, and the game continues from there next time you start it from Delphis." else "Any unsaved progress will be lost.",
            confirm = "Close game", dismiss = "Keep playing",
            onDismiss = onDismiss, onConfirm = { onConfirm(dontAsk) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { dontAsk = !dontAsk }) {
                Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                Text("Don't show this again", fontSize = 14.sp)
            }
        }
    }

    /** A dialog drawn inside this window (a real Dialog window would take focus away from Dolphin). */
    @Composable
    private fun OverlayDialog(
        title: String, body: String, confirm: String, dismiss: String,
        onDismiss: () -> Unit, onConfirm: () -> Unit, onOutside: () -> Unit = onDismiss, extra: @Composable () -> Unit = {},
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(onClick = onOutside),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 420.dp).padding(16.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(enabled = false) {}.padding(18.dp),
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                extra()
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text(dismiss) }
                    PrimaryGlowButton(confirm, null, onClick = onConfirm, fontSize = 14.sp)
                }
            }
        }
    }

    /** Pick another preset (built-in or your edited one), preview it, then apply with a restart. */
    @Composable
    private fun ColumnScope.LayoutSwitcher(s: GameSession, current: Preset, onCancel: () -> Unit, onApply: (Preset) -> Unit) {
        val suggestion = remember(s.key) { GameDatabase.suggest(s.info) }
        val choices = remember(s.key) { listOfNotNull(store.customFor(s.key)) + suggestion.choices }
        var candidate by remember { mutableStateOf(current) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth > maxHeight * 1.05f
            val list: @Composable (Modifier) -> Unit = { m ->
                PresetChooser(suggestion.question, choices, suggestion.alternatives, candidate, suggestedId = suggestion.choices.firstOrNull()?.id, modifier = m) { candidate = it }
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
