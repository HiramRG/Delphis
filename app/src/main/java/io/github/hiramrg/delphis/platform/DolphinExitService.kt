package io.github.hiramrg.delphis.platform

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Closes the running game the way a player would: opens Dolphin's in-game menu (Back) and taps
 * its "Exit Emulation" button.
 *
 * Why: leaving Dolphin's game screen only pauses emulation, and Dolphin ignores new launches until
 * emulation really stops. On the AYN Thor Android doesn't let Delphis force-close Dolphin, so
 * the old game kept running and the next launch collided with it. Exiting through Dolphin's own
 * menu stops emulation properly.
 *
 * On the Thor it never filters key or controller events, so it adds no input delay. On one-screen
 * devices (phones, tablets), while a game runs, it watches for ⊖ + Start to open Delphis' in-game
 * menu; every other key goes straight through untouched.
 */
class DolphinExitService : AccessibilityService() {

    companion object {
        @Volatile private var instance: DolphinExitService? = null
            set(v) { field = v; runningState.value = v != null }
        /** Whether the helper is running right now (Android restarts it after an update). */
        val running: Boolean get() = instance != null
        /** [running] for screens that should update when the helper starts or stops. */
        val runningState = androidx.compose.runtime.mutableStateOf(false)

        /**
         * Turned on in Accessibility but not running: Android stopped it (after Delphis crashed it stays
         * stopped until it's switched off and on again).
         */
        fun stopped(context: Context): Boolean = !running && isEnabled(context)

        /** True while Android has the service running (the user turned it on). */
        val isRunning: Boolean get() = instance != null

        /** True if the user turned the service on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            if (isRunning) return true
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, DolphinExitService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun openSettings(context: Context) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        /** See [exitEmulation]. Null when the service isn't running. */
        suspend fun exit(pkg: String): Result? = instance?.exitEmulation(pkg)

        /** See [dismissFromRecentsImpl]. False when the service isn't running. */
        suspend fun dismissFromRecents(appLabel: String, recentsOpen: Boolean = false): Boolean =
            instance?.dismissFromRecentsImpl(appLabel, recentsOpen) ?: false.also { CrashLog.event("dismiss $appLabel: the helper isn't running") }

        /**
         * Settings → Controllers → "Test it": catches the controller's menu buttons the way it does during a game
         * (the key filter), and tells [onKey] "home:<key>" or "combo". False when "close games" isn't running.
         */
        fun startShortcutTest(onKey: (String) -> Unit): Boolean {
            val s = instance ?: return false
            s.handler.post { s.testCallback = onKey; s.selectHeld = false; s.startHeld = false; s.menuShortcut(true); CrashLog.event("menu button test: key filter ${if (s.filterActive()) "on" else "NOT on"}") }
            return true
        }
        fun stopShortcutTest() {
            val s = instance ?: return
            s.handler.post { s.testCallback = null; if (GameWatch.session == null) s.menuShortcut(false) }
        }

        /**
         * The quick save / load buttons (Select + L1 saves, Select + R1 loads), while a game runs and they're on in
         * Settings. [holdMs]: how long Select + R1 must be held (0 = a press loads).
         */
        fun quickButtons(on: Boolean, holdMs: Long = 1000, screenshot: Boolean = false) {
            val s = instance ?: return
            s.handler.post {
                if (s.quickOn != on) CrashLog.event("quick save buttons ${if (on) "on" else "off"}")
                s.quickOn = on; s.quickHoldMs = holdMs; s.loadHeld = false; s.shotOn = screenshot; s.updateFilter()
                if (!on && !screenshot) { s.saveToast.hide(); s.menuShot = null }
            }
        }

        /** A game started ([running]) or ended: the automatic saves (screen off, low battery) follow it. */
        fun gameSession(running: Boolean) {
            val s = instance ?: return
            s.handler.post { if (running) s.startAutoSaves() else s.stopAutoSaves() }
        }

        /** The camera button: a screenshot of the game's screen. False when the helper isn't running. */
        fun screenshot(): Boolean {
            val s = instance ?: return false
            s.handler.post { s.screenshotButton() }
            return true
        }

        /**
         * A picture of the game for a save (Android 11 and up): taken now if the game is on screen, otherwise the one
         * taken when Delphis' menu last opened over it (one screen). Null when there's none.
         */
        suspend fun gamePicture(): android.graphics.Bitmap? = instance?.picture()

        /** See [pressQuickSave]. Null when the service isn't running. */
        suspend fun quickSave(pkg: String, closeMenu: Boolean = false): StateResult? = instance?.pressQuickSave(pkg, closeMenu)

        /** See [pressQuickLoad]. Null when the service isn't running. */
        suspend fun quickLoad(pkg: String, bootMs: Long = 6000): StateResult? = instance?.pressQuickLoad(pkg, bootMs)
        suspend fun syncTouchButtons(pkg: String, show: Boolean): String = instance?.syncTouchButtonsImpl(pkg, show) ?: "helper off"

        /** Covers the top screen with the loading screen (no-op without the service). Returns whether it's showing. */
        fun showLoading(phase: LoadingOverlay.Phase, title: String?, gameId: String?, line: String, displayId: Int?): Boolean {
            val s = instance ?: return false
            s.handler.post { s.loading.show(phase, title, gameId, line, displayId) }
            return true
        }

        /** Fills the bars beside a 4:3 GameCube picture (pattern / colour); BLACK hides them. False without the service. */
        fun showSides(sides: io.github.hiramrg.delphis.core.GameScreen.Sides, colour: Int, displayId: Int?): Boolean {
            val s = instance ?: return false
            s.handler.post {
                s.sideFill.show(sides, colour, displayId)
                s.handler.removeCallbacks(s.sideMenuCheck)
                if (s.sideFill.showing) s.handler.postDelayed(s.sideMenuCheck, 800)
            }
            return true
        }

        fun hideSides() {
            val s = instance ?: return
            s.handler.post { s.handler.removeCallbacks(s.sideMenuCheck); s.sideFill.hide() }
        }

        /** The unlock badge over the game on the top screen. False without the service. */
        fun showAchievement(popup: Achievements.Popup, badge: android.graphics.Bitmap?, displayId: Int?, showMs: Long = AchievementToast.DEFAULT_MS): Boolean {
            val s = instance ?: return false
            s.handler.post {
                // One screen: the badge shows at the top centre, in the Delphis pill's place.
                val centre = s.pill.showing
                if (centre) s.pill.hold(showMs + 700)
                s.achievementToast.show(popup, badge, displayId, centre, showMs)
            }
            return true
        }

        /**
         * One screen: a game started (or restarted). Puts up the Delphis pill (if it's on in Settings) and
         * starts watching for ⊖ + Start. [gameKey] decides "once per game" for the full pill.
         */
        fun gameStarted(context: Context, gameKey: String) {
            val s = instance ?: run { CrashLog.event("one screen game: \"close games\" isn't running, so no pill or shortcut"); return }
            val prefs = Prefs(context)
            val first = prefs.firstPillFor(gameKey)
            val showFull = when (prefs.pillFull) { 0 -> true; 1 -> first; else -> false }
            val pads = DeviceInfo.gamepads()
            val controller = pads.firstOrNull()?.name ?: "Touch controls"
            s.handler.post {
                s.lastPillState = null; s.keyLogs = 0
                s.menuShortcut(true)
                s.shortcut = prefs.menuShortcut
                CrashLog.event("one screen game: pill ${if (prefs.pillShown) "on" else "off"}, shortcut ${prefs.menuShortcutLabel}, key filter ${if (s.filterActive()) "on" else "NOT on"}")
                s.pillArgs = if (prefs.pillShown) PillArgs(showFull, prefs.pillOpacity, controller, if (pads.isEmpty()) "Tap here to open it" else prefs.menuShortcutSentence) else null
                s.pill.stop()
                s.handler.removeCallbacks(s.pillCheck)
                s.handler.post(s.pillCheck)
            }
        }

        /** One screen: brings Delphis' in-game menu over the game. False without the service. */
        fun openMenu(): Boolean {
            val s = instance ?: return false
            s.handler.post { s.openInGameMenu() }
            return true
        }

        /** One screen: the game was closed. */
        fun gameEnded() {
            val s = instance ?: return
            CrashLog.event("one screen game ended (session ${GameWatch.session != null})")
            s.handler.post { s.handler.removeCallbacks(s.pillCheck); s.pill.stop(); s.pillArgs = null; s.menuShortcut(false) }
        }

        fun hideLoading(delayMs: Long = 0) { instance?.let { s -> s.handler.post { s.loading.hide(delayMs) } } }

        /** "Closing Delphis · See you next time" on both screens (exit). Stays until [hideExit]. */
        fun showExit(topDisplayId: Int?, bottomDisplayId: Int?, lastTitle: String?, lastGameId: String?): Boolean {
            val s = instance ?: return false
            s.handler.post {
                // The cover is the last game played (the one on Home's Continue card).
                s.loading.show(LoadingOverlay.Phase.EXITING, "See you next time", lastGameId, "", topDisplayId, coverName = lastTitle ?: "Delphis")
                if (bottomDisplayId != null && bottomDisplayId != topDisplayId) s.exitBottom.show(LoadingOverlay.Phase.EXITING, "See you next time", null, "", bottomDisplayId)
            }
            return true
        }
        /** Set while "Exit Delphis" runs, so its own task removal doesn't start a second Recent-apps pass. */
        @Volatile var exitRunning = false
        fun hideExit(delayMs: Long = 0) { instance?.let { s -> s.handler.post { s.loading.hide(delayMs); s.exitBottom.hide(delayMs) } } }

        // ---- bottom-screen trackpad: served to Dolphin as a DSU pad (see DsuServer); kept here for the callers
        @Suppress("UNUSED_PARAMETER") fun trackpadDown(topDisplayId: Int) { DsuServer.ensureStarted() }
        fun trackpadMove(dxDp: Float, dyDp: Float, speed: io.github.hiramrg.delphis.core.Trackpad.Speed) = DsuServer.move(dxDp, dyDp, speed)
        fun trackpadUp() {}
        @Suppress("UNUSED_PARAMETER") fun trackpadRecenter(topDisplayId: Int) = DsuServer.recenter()
        fun trackpadReset() = DsuServer.reset()

        /** The game is on screen and nothing of Delphis covers it (what the hidden save button needs). */
        fun gameInFront(): Boolean = instance?.gameInFront() ?: false

        /** True if Dolphin's game screen is up. */
        fun gameShowing(pkg: String): Boolean = instance?.find(pkg, "frame_emulation_fragment") != null

        /** Waits (up to [timeoutMs]) for Dolphin's game screen to appear. */
        suspend fun awaitGameScreen(pkg: String, timeoutMs: Long): Boolean =
            instance?.waitFor(timeoutMs) { instance?.find(pkg, "frame_emulation_fragment") != null } ?: false
    }

    enum class Result { NO_GAME, EXITED, FAILED }
    enum class StateResult { DONE, NO_GAME, SAVE_STATES_OFF, FAILED }

    override fun onServiceConnected() {
        instance = this
        // After "Restart & update": open the new version (allowed from an accessibility service).
        relaunchIfUpdated(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        loading.hide()
        stopAutoSaves()
        saveToast.hide()
        scope.cancel()
        pill.stop()
        handler.removeCallbacksAndMessages(null)
        instance = null
        super.onDestroy()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val loading by lazy { LoadingOverlay(this) }
    private val achievementToast by lazy { AchievementToast(this) }
    private val exitBottom by lazy { LoadingOverlay(this) }
    private val sideFill by lazy { SideFill(this) }
    private val saveToast by lazy { SaveToast(this) }
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private val pill by lazy { DelphisPill(this) { openInGameMenu() } }
    /** While the pill is up: shown only while Dolphin's game is in front (not Delphis' menu, Home or another app). */
    private data class PillArgs(val showFull: Boolean, val opacity: Int, val controller: String, val shortcut: String)
    /** What the pill shows for the running game (null = pill off). */
    private var pillArgs: PillArgs? = null
    /**
     * While a one-screen game runs (until [gameEnded]): keeps the pill attached (puts it back if anything
     * removed it) and shows it only while the game is in front. Every change is logged for Diagnostics.
     */
    private val pillCheck: Runnable = object : Runnable {
        override fun run() {
            val args = pillArgs
            if (args == null || !shortcutOn) return
            if (!pill.showing) {
                val ok = pill.start(args.showFull, args.opacity, args.controller, args.shortcut)
                CrashLog.event(if (ok) "pill attached" else "pill couldn't be attached")
                if (ok) pillArgs = args.copy(showFull = false) // the full pill only the first time
                if (!ok) { handler.postDelayed(this, 3000); return }
            }
            val state = gameState()
            if (state != lastPillState) { lastPillState = state; CrashLog.event("pill: $state") }
            pill.setVisible(state == "in front")
            handler.postDelayed(this, 700)
        }
    }

    // ------------------------------------------------------------------ one screen: Delphis' in-game menu

    /**
     * Dolphin's game is on screen and Delphis' menu isn't over it. (Checks Dolphin's game view itself: the
     * "top app" can be another window on some tablets, e.g. Samsung's game tools.)
     */
    private fun gameInFront(): Boolean = gameState() == "in front"

    /**
     * Why the pill shows or not, in words (also written to the Diagnostics events when it changes).
     * Dolphin's game view or Dolphin as the top app counts as "in front"; when neither can be read (some
     * tablets hide them), a running game that hasn't gone to the background counts too, unless Delphis'
     * own game list is on screen.
     */
    private fun gameState(): String {
        if (GameWatch.session == null) return "no game"
        if (GameLauncher.busy) return "starting / closing"
        if (GameWatch.menuOpen) return "Delphis' menu open"
        val view = DolphinBridge.KNOWN_PACKAGES.any { runCatching { find(it, "frame_emulation_fragment") != null }.getOrDefault(false) }
        if (view) return "in front"
        val top = topScreenApp()
        if (top != null && top in DolphinBridge.KNOWN_PACKAGES) return "in front"
        if (top == packageName) return "Delphis on screen"
        // Other apps aren't named in reports (only the home screen / System UI, which help with problems).
        if (top != null) return if (top in recentsPackages()) "another app on screen ($top)" else "another app on screen"
        if (GameWatch.suspended.value) return "game in the background"
        return "in front"
    }
    private var lastPillState: String? = null
    private var keyLogs = 0

    private var shortcutOn = false
    private var testCallback: ((String) -> Unit)? = null
    /** Prefs.menuShortcut: 0 = Home button, 1 = ⊖ + Start, 2 = both. */
    private var shortcut = 0
    private var selectHeld = false
    private var startHeld = false
    /** Keys whose release is swallowed too (their press opened the menu). */
    private val swallowUp = mutableSetOf<Int>()

    /** The in-game menu shortcut: on while a game runs on a one-screen device. */
    private fun menuShortcut(on: Boolean) {
        if (shortcutOn == on) return
        shortcutOn = on
        selectHeld = false; startHeld = false; swallowUp.clear()
        updateFilter()
    }

    /** Key filtering is only on while a game runs and something needs it (one screen's menu shortcut, the quick save buttons). */
    private fun updateFilter() {
        val on = shortcutOn || quickOn || shotOn
        if (on == filterActive()) return
        runCatching {
            serviceInfo = serviceInfo.apply {
                flags = if (on) flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                else flags and android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
            }
        }.onFailure { CrashLog.event("couldn't change key filtering: ${it.message}") }
    }

    // ------------------------------------------------------------------ quick save / load buttons

    private var quickOn = false
    private var quickHoldMs = 1000L
    /** Select + R1 is being held (the load fires when the time is up, unless it's let go first). */
    private var loadHeld = false
    private var holdToken = 0
    /** A quick save or load is running (another press waits for it). */
    private var quickBusy = false

    private fun gameDisplay(): Int? = runCatching { DeviceInfo.screenRoles(this, Prefs(this).swapScreens).topDisplayId }.getOrNull()

    /** Select + L1: a fresh autosave, with its picture, without leaving the game. */
    private fun quickSaveButton() {
        if (quickBusy) return
        val id = GameWatch.session?.info?.gameId ?: run { saveToast.failed(gameDisplay(), "Couldn't save", "Delphis doesn't know this game's ID"); return }
        quickBusy = true
        scope.launch {
            try {
                val display = gameDisplay()
                val shot = picture()
                if (Prefs(this@DolphinExitService).pillShown) pill.hold(6_000)
                saveToast.busy(display, shot, "Saving…", "")
                val bridge = DolphinBridge(this@DolphinExitService, Prefs(this@DolphinExitService))
                val ok = bridge.saveNow(id, shot)
                CrashLog.event("quick save button: ${if (ok) "saved" else "not saved"}")
                if (ok) {
                    val t = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { bridge.quickStateTime(id) } ?: System.currentTimeMillis()
                    saveToast.done(display, shot, "Saved", "${io.github.hiramrg.delphis.ui.formatSaveTime(t)} · it's the latest autosave")
                } else saveToast.failed(
                    display, "Couldn't save",
                    if (Prefs(this@DolphinExitService).saveStatesOff) "Dolphin's Enable Savestates setting is off" else "Try again in a moment",
                )
            } finally { quickBusy = false }
        }
    }

    /** Select + R1 pressed: after [quickHoldMs] of holding (or right away) the latest autosave loads. */
    private fun quickLoadPressed() {
        if (quickBusy) return
        val id = GameWatch.session?.info?.gameId ?: return
        loadHeld = true
        val token = ++holdToken
        scope.launch {
            val display = gameDisplay()
            val bridge = DolphinBridge(this@DolphinExitService, Prefs(this@DolphinExitService))
            val latest = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { bridge.saves(id).firstOrNull { it.kind == DolphinBridge.SaveKind.LATEST } }
            val pic = latest?.picture?.let { f -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { android.graphics.BitmapFactory.decodeFile(f.path) }.getOrNull() } }
            if (token != holdToken || (!loadHeld && quickHoldMs > 0)) return@launch
            if (latest == null) { saveToast.failed(display, "Nothing to load yet", "Save first: Select + L1"); return@launch }
            val line = "Latest autosave · ${io.github.hiramrg.delphis.ui.formatSaveTime(latest.savedAt)}"
            if (Prefs(this@DolphinExitService).pillShown) pill.hold(quickHoldMs + 9_000)
            if (quickHoldMs > 0) {
                saveToast.holding(display, pic, line, quickHoldMs)
                kotlinx.coroutines.delay(quickHoldMs)
                if (token != holdToken || !loadHeld) return@launch
            }
            loadHeld = false
            if (quickBusy) return@launch
            quickBusy = true
            try {
                saveToast.busy(display, pic, "Loading…", line)
                val ok = bridge.loadSaveInGame(id, latest)
                CrashLog.event("quick load button: ${if (ok) "loaded" else "not loaded"}")
                if (ok) saveToast.done(display, pic, "Loaded", "Your progress from before is the newest earlier autosave")
                else saveToast.failed(display, "Couldn't load it", "Try again, or use Dolphin's menu → Load State")
            } finally { quickBusy = false }
        }
    }

    /** Select or R1 let go before the load fired. */
    private fun quickLoadReleased() {
        if (!loadHeld) return
        loadHeld = false
        holdToken++
        if (!quickBusy) saveToast.hide()
    }

    // ------------------------------------------------------------------ automatic saves while a game runs

    private var autoJob: kotlinx.coroutines.Job? = null
    private var autoReceiver: android.content.BroadcastReceiver? = null
    /** The low-battery save was made this charge. */
    private var lowBatterySaved = false
    /** How the save when the screen turned off went ("ok" / "fail"), shown when the screen comes back on. */
    private var sleepResult: Pair<Boolean, Long?>? = null
    private var sleepSaving = false

    private fun screenOn() = runCatching { (getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive }.getOrDefault(true)
    private val deviceWord get() = if (android.os.Build.MODEL.contains("Thor", ignoreCase = true)) "the Thor" else "your device"

    fun startAutoSaves() {
        if (autoJob != null) return
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_SCREEN_ON -> handler.postDelayed({ showSleepResult() }, 1_200)
                    Intent.ACTION_BATTERY_CHANGED -> onBattery(i)
                }
            }
        }
        val f = android.content.IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_BATTERY_CHANGED) }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(r, f)
            autoReceiver = r
        }.onFailure { CrashLog.event("automatic saves: couldn't watch the screen / battery: ${it.message}") }
        CrashLog.event("automatic saves on (screen off, low battery)")
    }

    fun stopAutoSaves() {
        autoJob?.cancel(); autoJob = null
        autoReceiver?.let { runCatching { unregisterReceiver(it) } }; autoReceiver = null
    }

    /** A low-battery save, with its small pop-up. Only in the background (Dolphin's save button): never Dolphin's menu. */
    private suspend fun automaticSave(id: String, batteryLine: String): Boolean {
        if (quickBusy) return false
        quickBusy = true
        try {
            val p = Prefs(this)
            val display = gameDisplay()
            val shot = picture()
            val bridge = DolphinBridge(this, p)
            val ok = bridge.saveNow(id, shot, backgroundOnly = true)
            CrashLog.event("low battery save: ${if (ok) "saved" else "not saved (${DolphinBridge.lastSaveNote})"}")
            if (ok) {
                val time = io.github.hiramrg.delphis.ui.formatSaveTime(System.currentTimeMillis())
                saveToast.done(display, shot, batteryLine, "Keep playing, or plug in · $time", 4_500, saveToast.amberEdge)
            }
            return ok
        } finally { quickBusy = false }
    }

    private fun onBattery(i: Intent) {
        val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val charging = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (level < 0) return
        val pct = level * 100 / scale
        if (charging) { lowBatterySaved = false; return }
        val p = Prefs(this)
        val at = p.lowBatteryLevel
        val id = GameWatch.session?.info?.gameId ?: return
        if (at <= 0 || pct > at || lowBatterySaved || !p.autoSaveFor(id) || quickBusy || !screenOn() || !gameInFront()) return
        if (DolphinBridge(this, p).silentSaves() == false) return // this Dolphin can't save in the background
        lowBatterySaved = true
        scope.launch { if (!automaticSave(id, batteryLine = "Battery at $pct%: game saved")) lowBatterySaved = false }
    }

    /**
     * The screen turned off during a game (Thor closed, power button): try to save before Android pauses Dolphin.
     * Whether this can work at all is still being tested, so every try is written to the Diagnostics report: what
     * the helper could see, what happened, how long it took.
     */
    private fun onScreenOff() {
        val p = Prefs(this)
        val id = GameWatch.session?.info?.gameId ?: return
        if (!p.saveOnSleep || !p.autoSaveFor(id)) return
        if (DolphinBridge(this, p).silentSaves() == false) return // this Dolphin can't save in the background
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        if (quickBusy || sleepSaving) { p.sleepSaveLog = listOf("$stamp · SKIPPED · another save was running") + p.sleepSaveLog; return }
        sleepSaving = true; quickBusy = true
        val wake = runCatching {
            (getSystemService(POWER_SERVICE) as android.os.PowerManager).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Delphis:sleepSave").apply { setReferenceCounted(false); acquire(25_000) }
        }.getOrNull()
        val t0 = android.os.SystemClock.elapsedRealtime()
        // What the helper can see right after the screen went off: the test's main question.
        val top = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: "nothing readable"
        val view = DolphinBridge.KNOWN_PACKAGES.any { runCatching { find(it, "frame_emulation_fragment") != null }.getOrDefault(false) }
        scope.launch {
            var ok = false
            var after: Long? = null
            try {
                val bridge = DolphinBridge(this@DolphinExitService, p)
                val before = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { bridge.quickStateModified(id) }
                DolphinBridge.lastSaveNote = ""
                ok = kotlinx.coroutines.withTimeoutOrNull(18_000) { bridge.saveNow(id, backgroundOnly = true) } ?: false
                after = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { bridge.quickStateModified(id) }
                val secs = (android.os.SystemClock.elapsedRealtime() - t0) / 1000.0
                val line = "$stamp · ${if (ok) "SAVED" else "NOT SAVED"} in ${"%.1f".format(java.util.Locale.US, secs)} s · " +
                    (DolphinBridge.lastSaveNote.ifEmpty { "took longer than 18 s" }) +
                    " · window on top: ${if (top in DolphinBridge.KNOWN_PACKAGES) "Dolphin" else if (top == packageName) "Delphis" else top}" +
                    " · Dolphin's game view: ${if (view) "found" else "not found"}" +
                    " · save file ${if (after != null && after != before) "written" else "unchanged"}" +
                    " · screen ${if (screenOn()) "back on" else "still off"}"
                p.sleepSaveLog = listOf(line) + p.sleepSaveLog
                CrashLog.event("sleep save: $line")
                sleepResult = ok to (if (ok) after else before)
            } finally {
                quickBusy = false; sleepSaving = false
                runCatching { wake?.release() }
                if (screenOn()) showSleepResult()
            }
        }
    }

    /** Back on: how the save when the screen turned off went. */
    private fun showSleepResult() {
        if (sleepSaving) return
        val (ok, t) = sleepResult ?: return
        sleepResult = null
        val display = gameDisplay()
        val id = GameWatch.session?.info?.gameId
        scope.launch {
            val time = t?.let { io.github.hiramrg.delphis.ui.formatSaveTime(it) }
            if (ok) {
                val pic = id?.let { g -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { DolphinBridge(this@DolphinExitService, Prefs(this@DolphinExitService)).latestPicture(g)?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.path) }.getOrNull() } } }
                saveToast.done(display, pic, "Saved before $deviceWord slept", "${time ?: "Just now"} · it's the latest autosave", 4_000, saveToast.amberEdge)
            } else saveToast.failed(display, "Couldn't save before sleep", if (time != null) "Your last save is from $time" else "There's no save of this game yet")
        }
    }

    // ------------------------------------------------------------------ screenshots

    private var shotOn = false
    private var shotBusy = false

    /** The camera button / Select + Y: a full-size picture of the game's screen into Photos (Pictures/Delphis/<game>). */
    private fun screenshotButton() {
        if (shotBusy) return
        val title = GameWatch.session?.info?.title
        val display = gameDisplay()
        if (android.os.Build.VERSION.SDK_INT < 30) { saveToast.failed(display, "Screenshots need Android 11", "This device can't take them from Delphis"); return }
        shotBusy = true
        scope.launch {
            try {
                if (!gameVisible()) { saveToast.failed(display, "Screenshot not taken", "The game isn't on screen"); return@launch }
                val result = kotlinx.coroutines.CompletableDeferred<android.graphics.Bitmap?>()
                shootAsync(display ?: android.view.Display.DEFAULT_DISPLAY, full = true) { result.complete(it) }
                val full = kotlinx.coroutines.withTimeoutOrNull(3_000) { result.await() }
                if (full == null) { saveToast.failed(display, "Screenshot not taken", "Android didn't give Delphis the picture. Try again."); return@launch }
                saveToast.flash(display)
                val p = Prefs(this@DolphinExitService)
                val (uri, thumb) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val pic = if (p.screenshotGameOnly) Screenshots.gameOnly(full) else full
                    val u = Screenshots.save(this@DolphinExitService, pic, title)
                    u to android.graphics.Bitmap.createScaledBitmap(pic, 192, (pic.height * 192 / pic.width).coerceAtLeast(1), true)
                }
                kotlinx.coroutines.delay(300) // after the flash
                if (uri != null) saveToast.done(display, thumb, "Screenshot saved", "Photos → ${io.github.hiramrg.delphis.core.ScreenshotNames.relativePath(title).trimEnd('/')}")
                else saveToast.failed(display, "Screenshot not saved", "There was no room for it, or Photos refused it")
            } finally { shotBusy = false }
        }
    }

    // ------------------------------------------------------------------ pictures of saves

    /** The picture taken when Delphis' menu last opened over the game (one screen), and when. */
    private var menuShot: android.graphics.Bitmap? = null
    private var menuShotAt = 0L

    /** The game is what's on its screen: no Delphis menu or loading screen over it, no other app in front. */
    private fun gameVisible(): Boolean {
        if (GameWatch.menuOpen || loading.showing) return false
        val top = topScreenApp()
        if (top != null && top !in DolphinBridge.KNOWN_PACKAGES) return false
        return DolphinBridge.KNOWN_PACKAGES.any { runCatching { find(it, "frame_emulation_fragment") != null }.getOrDefault(false) }
    }

    private suspend fun picture(): android.graphics.Bitmap? {
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        if (gameVisible()) shoot()?.let { return it }
        // Delphis' menu is over the game: the picture from when it opened (if it's from the last half hour).
        return menuShot?.takeIf { android.os.SystemClock.elapsedRealtime() - menuShotAt < 30 * 60_000L }
    }

    /** A screenshot of the game's screen, scaled down for a save's picture. Android 11 and up. */
    private suspend fun shoot(): android.graphics.Bitmap? {
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        val display = gameDisplay() ?: android.view.Display.DEFAULT_DISPLAY
        val result = kotlinx.coroutines.CompletableDeferred<android.graphics.Bitmap?>()
        shootAsync(display) { result.complete(it) }
        return kotlinx.coroutines.withTimeoutOrNull(2_000) { result.await() }
    }

    @android.annotation.TargetApi(30)
    private fun shootAsync(display: Int, full: Boolean = false, done: (android.graphics.Bitmap?) -> Unit) {
        runCatching {
            takeScreenshot(display, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(r: ScreenshotResult) {
                    val small = runCatching {
                        val hw = android.graphics.Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace) ?: return@runCatching null
                        val soft = hw.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                        hw.recycle()
                        val w = io.github.hiramrg.delphis.core.SavePictures.WIDTH
                        if (!full && soft.width > w) android.graphics.Bitmap.createScaledBitmap(soft, w, (soft.height * w / soft.width).coerceAtLeast(1), true).also { if (it !== soft) soft.recycle() } else soft
                    }.getOrNull()
                    runCatching { r.hardwareBuffer.close() }
                    done(small)
                }
                override fun onFailure(errorCode: Int) { CrashLog.event("save picture not taken (error $errorCode)"); done(null) }
            })
        }.onFailure { CrashLog.event("save picture not taken: ${it.message}"); done(null) }
    }

    /**
     * The Home button (⌂ / Xbox / PS) opens Delphis' menu and never reaches the game (no preset uses it).
     * ⊖ + Start works in either order: the second button goes only to Delphis (the first was already sent
     * to the game, since a press can't be held back and passed on later).
     */
    override fun onKeyEvent(raw: android.view.KeyEvent): Boolean {
        if (!shortcutOn && !quickOn && !shotOn) return false
        // Controllers that don't report standard keys (e.g. a Switch Pro Controller): by what they stand for.
        val event = PadKeys.translate(raw, Prefs(this), true)
        val code = event.keyCode
        val down = event.action == android.view.KeyEvent.ACTION_DOWN
        val home = code == android.view.KeyEvent.KEYCODE_BUTTON_MODE || code == android.view.KeyEvent.KEYCODE_HOME
        // Settings' "Test it": report what arrives; the Home button is kept (so Android doesn't go home).
        testCallback?.let { cb ->
            if (fromGamepad(event)) {
                if (down && event.repeatCount == 0) CrashLog.event("menu button test: ${android.view.KeyEvent.keyCodeToString(raw.keyCode)} from ${raw.device?.name ?: "?"}")
                if (home && down && event.repeatCount == 0) handler.post { cb("home:" + android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")) }
                if (code == android.view.KeyEvent.KEYCODE_BUTTON_SELECT) selectHeld = down
                if (code == android.view.KeyEvent.KEYCODE_BUTTON_START) startHeld = down
                if (down && selectHeld && startHeld) handler.post { cb("combo") }
            }
            return home && fromGamepad(event)
        }
        // Every controller key, for the first few in a game: shows in Diagnostics what each button sends.
        if (down && event.repeatCount == 0 && keyLogs < 10 && fromGamepad(event)) {
            keyLogs++
            CrashLog.event("key ${android.view.KeyEvent.keyCodeToString(raw.keyCode)}" + (if (raw.keyCode != code) " (as ${android.view.KeyEvent.keyCodeToString(code)})" else "") +
                " from ${raw.device?.name ?: "?"} · game ${gameState()}")
        }
        if (!down && swallowUp.remove(code)) {
            if (code == android.view.KeyEvent.KEYCODE_BUTTON_SELECT) { selectHeld = false; quickLoadReleased() }
            if (code == android.view.KeyEvent.KEYCODE_BUTTON_START) startHeld = false
            if (code == android.view.KeyEvent.KEYCODE_BUTTON_R1) quickLoadReleased()
            return true
        }
        // Quick save / load: Select + L1 saves, Select + R1 loads. L1 / R1 then never reach the game (Select already
        // did: a press can't be held back and passed on later).
        if (code == android.view.KeyEvent.KEYCODE_BUTTON_SELECT) { selectHeld = down; if (!down) quickLoadReleased() }
        // Screenshot: Select + Y.
        if (shotOn && code == android.view.KeyEvent.KEYCODE_BUTTON_Y && fromGamepad(event) && down && event.repeatCount == 0 && selectHeld && gameInFront()) {
            swallowUp += code
            handler.post { screenshotButton() }
            return true
        }
        if (quickOn && (code == android.view.KeyEvent.KEYCODE_BUTTON_L1 || code == android.view.KeyEvent.KEYCODE_BUTTON_R1) && fromGamepad(event)) {
            if (down && event.repeatCount == 0 && selectHeld && gameInFront()) {
                swallowUp += code
                if (code == android.view.KeyEvent.KEYCODE_BUTTON_L1) handler.post { quickSaveButton() } else handler.post { quickLoadPressed() }
                return true
            }
        }
        if (!shortcutOn) return false
        fun open(): Boolean { swallowUp += code; handler.post { openInGameMenu() }; return true }
        when (code) {
            // Some tablets report a controller's Home button as Android's Home key.
            android.view.KeyEvent.KEYCODE_BUTTON_MODE, android.view.KeyEvent.KEYCODE_HOME -> if (shortcut != 1 && fromGamepad(event) && gameInFront()) {
                if (down && event.repeatCount == 0) return open()
                return true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_SELECT, android.view.KeyEvent.KEYCODE_BUTTON_START -> {
                val isSelect = code == android.view.KeyEvent.KEYCODE_BUTTON_SELECT
                if (!down) { if (isSelect) selectHeld = false else startHeld = false; return false }
                val otherHeld = if (isSelect) startHeld else selectHeld
                if (isSelect) selectHeld = true else startHeld = true
                if (shortcut != 0 && event.repeatCount == 0 && otherHeld && gameInFront()) return open()
            }
        }
        return false
    }

    private fun filterActive(): Boolean = runCatching {
        (serviceInfo.flags and android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
    }.getOrDefault(false)

    private fun fromGamepad(e: android.view.KeyEvent): Boolean {
        val src = e.source
        return (src and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD ||
            (src and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK ||
            e.device?.let { d -> !d.isVirtual && d.isExternal && (d.sources and android.view.InputDevice.SOURCE_GAMEPAD) != 0 } == true
    }

    /** Brings Delphis' in-game menu over the game (it waits behind it while you play). */
    private fun openInGameMenu() {
        CrashLog.event("opening Delphis' menu (session ${GameWatch.session != null}, busy ${GameLauncher.busy})")
        if (GameWatch.session == null || GameLauncher.busy) return
        val intent = Intent(this, io.github.hiramrg.delphis.ui.InGameMenuActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(io.github.hiramrg.delphis.ui.InGameMenuActivity.EXTRA_OPEN, true)
            // The session too, in case Android closed the menu meanwhile (it's then opened fresh with it).
            .also { i -> GameWatch.session?.writeTo(i) }
        val top = DeviceInfo.screenRoles(this, Prefs(this).swapScreens).topDisplayId
        // The game as it is now, for the picture of a save made while the menu covers it (taken before the menu draws).
        if (android.os.Build.VERSION.SDK_INT >= 30 && gameVisible()) shootAsync(top) { b -> if (b != null) { menuShot = b; menuShotAt = android.os.SystemClock.elapsedRealtime() } }
        val options = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(top).toBundle()
        runCatching { startActivity(intent, options) }.onFailure { CrashLog.event("couldn't open the in-game menu: ${it.message}") }
        pill.setVisible(false)
    }
    /** While side fills show: hide them whenever Dolphin's menu (at the screen's edge) is open. */
    private val sideMenuCheck: Runnable = object : Runnable {
        override fun run() {
            if (!sideFill.showing) return
            val menuOpen = DolphinBridge.KNOWN_PACKAGES.any { pkg -> runCatching { find(pkg, "menu_exit")?.isVisibleToUser == true }.getOrDefault(false) }
            sideFill.setVisible(!menuOpen)
            handler.postDelayed(this, 800)
        }
    }
    private val checkGame = Runnable {
        if (GameWatch.session == null || GameLauncher.busy) return@Runnable
        val stillShowing = DolphinBridge.KNOWN_PACKAGES.any { find(it, "frame_emulation_fragment") != null }
        if (stillShowing) {
            if (GameWatch.suspended.value) bringBottomScreenBack()
            return@Runnable
        }
        val app = topScreenApp()
        when {
            // Dolphin itself is showing, but not the game: the game was closed or Dolphin crashed.
            app != null && app in DolphinBridge.KNOWN_PACKAGES -> GameWatch.reportGone("Dolphin's game screen closed (Dolphin may have crashed)")
            // Our own game list: MainActivity.onResume decides.
            app == packageName -> Unit
            // The home screen, another app, or nothing (screen off): the game is just in the background.
            else -> GameWatch.setSuspended(true)
        }
    }

    /** Package of the app shown on the top screen, if any. */
    private fun topScreenApp(): String? = runCatching {
        val top = DeviceInfo.screenRoles(this, Prefs(this).swapScreens).topDisplayId
        val list = if (android.os.Build.VERSION.SDK_INT >= 30) windowsOnAllDisplays.get(top).orEmpty() else windows
        list.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
            ?.root?.packageName?.toString()
    }.getOrNull()

    /** The game is back on the top screen: show the in-game bottom screen again. */
    private fun bringBottomScreenBack() {
        GameWatch.setSuspended(false)
        val bottom = DeviceInfo.screenRoles(this, Prefs(this).swapScreens).bottomDisplayId ?: return
        val intent = Intent(this, io.github.hiramrg.delphis.ui.NowPlayingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(io.github.hiramrg.delphis.ui.NowPlayingActivity.EXTRA_RESUMED, true)
        val options = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(bottom).toBundle()
        runCatching { startActivity(intent, options) }.onFailure { CrashLog.event("couldn't bring the bottom screen back: ${it.message}") }
    }

    /**
     * Only "windows changed" events are requested. Those come from the system, not from apps, so no
     * app (Dolphin included) does extra work for this service. While a game runs, a change is
     * checked once, 1.5 s later, to see whether Dolphin's game screen is still there.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (GameWatch.session == null || GameLauncher.busy) return
        handler.removeCallbacks(checkGame)
        handler.postDelayed(checkGame, 1500)
    }
    override fun onInterrupt() = Unit

    /** The apps that show Recent apps: the home app (Launcher3, Samsung's One UI Home…) and System UI. */
    private fun recentsPackages(): Set<String> {
        val home = runCatching {
            packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        }.getOrNull()
        return setOfNotNull(home, "com.android.systemui")
    }

    private fun dolphinRoots(pkg: String): List<AccessibilityNodeInfo> {
        val roots = runCatching { windows.mapNotNull { it.root } }.getOrDefault(emptyList()) +
            listOfNotNull(runCatching { rootInActiveWindow }.getOrNull())
        return roots.filter { it.packageName?.toString() == pkg }
    }

    private fun find(pkg: String, id: String): AccessibilityNodeInfo? =
        dolphinRoots(pkg).firstNotNullOfOrNull { root ->
            runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstOrNull() }.getOrNull()
        }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
        }
        return false
    }

    private suspend fun waitFor(timeoutMs: Long, check: () -> Boolean): Boolean {
        var waited = 0L
        while (waited < timeoutMs) {
            if (check()) return true
            delay(100); waited += 100
        }
        return check()
    }

    /**
     * Back, for Dolphin's menu. Never sent while another app is in front (Back goes to whatever has focus):
     * only Dolphin, Delphis itself or System UI may be the active window.
     */
    private fun dolphinBack(pkg: String): Boolean {
        val active = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (active != null && active != pkg && active != packageName && active != "com.android.systemui") {
            CrashLog.event("Back not sent: another app is in front")
            return false
        }
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /** Opens Dolphin's in-game menu (Back toggles it) unless it's already open. */
    private suspend fun openMenu(pkg: String): Boolean {
        if (find(pkg, "menu_exit") != null) return true
        dolphinBack(pkg)
        return waitFor(1500) { find(pkg, "menu_exit") != null }
    }

    /**
     * Opens Dolphin's menu and presses "Quick Save". The menu is left open (exiting next uses it).
     * The state is written in the background by Dolphin; the caller waits for the file.
     */
    suspend fun pressQuickSave(pkg: String, closeMenu: Boolean = false): StateResult {
        if (find(pkg, "frame_emulation_fragment") == null && find(pkg, "menu_exit") == null) return StateResult.NO_GAME
        if (!openMenu(pkg)) return StateResult.FAILED
        // The button is hidden while Dolphin's "Enable Savestates" setting is off.
        waitFor(1500) { find(pkg, "menu_quicksave")?.isVisibleToUser == true }
        val save = find(pkg, "menu_quicksave")?.takeIf { it.isVisibleToUser }
            ?: return if (find(pkg, "menu_quicksave") == null) StateResult.SAVE_STATES_OFF else StateResult.FAILED
        delay(300)
        val ok = click(save)
        if (closeMenu) {
            delay(400)
            if (find(pkg, "menu_exit") != null) dolphinBack(pkg) // close the menu, the game goes on
        }
        return if (ok) StateResult.DONE else StateResult.FAILED
    }

    /**
     * Once the game is running: opens Dolphin's menu, presses "Quick Load", and closes the menu.
     *
     * A state can only be loaded once Dolphin's emulated console is actually running; a load sent
     * while it's still booting is lost (or worse). Dolphin's game screen appears long before that,
     * so: wait [bootMs] after it appears, then open the menu and check that it shows the game's
     * title (Dolphin only knows it once the game has booted far enough). If not, close the menu and
     * try again a bit later.
     */
    suspend fun pressQuickLoad(pkg: String, bootMs: Long = 6000): StateResult {
        if (!waitFor(30_000) { find(pkg, "frame_emulation_fragment") != null }) return StateResult.NO_GAME
        delay(bootMs)
        repeat(5) { attempt ->
            if (find(pkg, "frame_emulation_fragment") == null && find(pkg, "menu_exit") == null) return StateResult.NO_GAME
            if (!openMenu(pkg)) { delay(2000); return@repeat }
            val title = find(pkg, "text_game_title")?.text?.toString().orEmpty()
            if (title.isBlank()) {
                CrashLog.event("resume: game not booted yet (attempt ${attempt + 1})")
                dolphinBack(pkg) // close the menu, try again later
                delay(3000)
                return@repeat
            }
            // The menu slides in; wait until the button is really on screen.
            waitFor(1500) { find(pkg, "menu_quickload")?.isVisibleToUser == true }
            val load = find(pkg, "menu_quickload")?.takeIf { it.isVisibleToUser }
            if (load == null) {
                dolphinBack(pkg)
                return if (find(pkg, "menu_quickload") == null) StateResult.SAVE_STATES_OFF else StateResult.FAILED
            }
            delay(500) // let the slide-in animation settle so the click lands
            val ok = click(load)
            delay(400)
            if (find(pkg, "menu_exit") != null) dolphinBack(pkg) // close the menu
            return if (ok) StateResult.DONE else StateResult.FAILED
        }
        return StateResult.FAILED
    }

    /**
     * Makes Dolphin show or hide its touch buttons right now, from its own in-game menu (Overlay controls →
     * Show overlay), the way a person would. Restarting Dolphin so it re-reads Dolphin.ini doesn't always
     * happen (Samsung can keep it alive after its Recent apps card is removed), but this applies at once.
     * Returns what happened, for Diagnostics (it lists the menu's items, so a renamed item shows up).
     */
    suspend fun syncTouchButtonsImpl(pkg: String, show: Boolean): String {
        if (find(pkg, "frame_emulation_fragment") == null) return "no game on screen"
        if (!openMenu(pkg)) return "Dolphin's menu didn't open"
        waitFor(1500) { find(pkg, "menu_overlay_controls")?.isVisibleToUser == true }
        val button = find(pkg, "menu_overlay_controls")?.takeIf { it.isVisibleToUser }
        if (button == null) { dolphinBack(pkg); return "no Overlay controls button" }
        delay(400)
        if (!click(button)) { dolphinBack(pkg); return "Overlay controls didn't open" }
        // The pop-up's rows: a title, and a check box for the items that can be ticked.
        data class Row(val title: String, val checked: Boolean?, val node: AccessibilityNodeInfo)
        // Dolphin's pop-up is an AppCompat menu, so its rows' title id is Dolphin's own ("<pkg>:id/title"), not
        // Android's; and some versions use other layouts. So: both ids, then any row with a check box and a text.
        fun rows(): List<Row> {
            val roots = dolphinRoots(pkg)
            val byId = roots.flatMap { root ->
                listOf("android:id/title", "$pkg:id/title").flatMap { id -> runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrDefault(emptyList()) }
            }.mapNotNull { t ->
                val row = t.parent ?: return@mapNotNull null
                val box = (0 until row.childCount).mapNotNull { row.getChild(it) }.firstOrNull { it.isCheckable }
                Row(t.text?.toString().orEmpty(), box?.isChecked ?: row.isChecked.takeIf { row.isCheckable }, row)
            }
            if (byId.isNotEmpty()) return byId
            val found = mutableListOf<Row>()
            fun textIn(n: AccessibilityNodeInfo, depth: Int = 0): String? =
                n.text?.toString()?.takeIf { it.isNotBlank() } ?: if (depth > 3) null
                else (0 until n.childCount).firstNotNullOfOrNull { i -> n.getChild(i)?.let { textIn(it, depth + 1) } }
            fun walk(n: AccessibilityNodeInfo, depth: Int) {
                if (depth > 12) return
                val kids = (0 until n.childCount).mapNotNull { n.getChild(it) }
                val box = kids.firstOrNull { it.isCheckable } ?: n.takeIf { it.isCheckable }
                val title = if (box != null) textIn(n) else null
                if (box != null && title != null) { found += Row(title, box.isChecked, n); return }
                kids.forEach { walk(it, depth + 1) }
            }
            roots.forEach { walk(it, 0) }
            return found
        }
        waitFor(1500) { rows().isNotEmpty() }
        val items = rows()
        val listed = items.joinToString(" | ") { it.title + (when (it.checked) { true -> " [on]"; false -> " [off]"; null -> "" }) }
        val target = items.firstOrNull { r ->
            r.checked != null && Regex("overlay|superposici|en pantalla|on-screen|show controls|mostrar controles", RegexOption.IGNORE_CASE).containsMatchIn(r.title)
        }
        val result = when {
            target == null -> { dolphinBack(pkg); "no show-overlay item found" }
            target.checked == show -> { dolphinBack(pkg); "already ${if (show) "on" else "off"}" }
            else -> { delay(300); if (click(target.node)) "switched ${if (show) "on" else "off"}" else "click failed" }
        }
        delay(400)
        if (find(pkg, "menu_exit") != null) dolphinBack(pkg) // close Dolphin's menu
        return "$result · items: $listed"
    }

    /**
     * Removes an app's card from Recent apps, the way a person would: opens Recents, finds the card by the
     * app's name, presses its "Close" action (or swipes it up), then goes Home. Android then ends that app's
     * process. Used by "Exit Delphis" for Dolphin, since no app may force-stop another one.
     */
    suspend fun dismissFromRecentsImpl(appLabel: String, recentsOpen: Boolean = false): Boolean {
        // Already in Recent apps (Delphis was just swiped away there): use it as it is.
        if (recentsOpen) delay(600)
        else {
            if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) { CrashLog.event("dismiss $appLabel: Recent apps didn't open"); return false }
            delay(900)
        }
        // Only the Recent apps screen is searched (the home app or System UI), never another app's window.
        val recentsApps = recentsPackages()
        fun findLabel(): AccessibilityNodeInfo? {
            val roots = runCatching { windows.mapNotNull { it.root } }.getOrDefault(emptyList()) +
                listOfNotNull(runCatching { rootInActiveWindow }.getOrNull())
            return roots.filter { it.packageName?.toString()?.let { p -> p in recentsApps } == true }
                .firstNotNullOfOrNull { r -> runCatching { r.findAccessibilityNodeInfosByText(appLabel).firstOrNull() }.getOrNull() }
        }
        // Each try is checked: the card must really be gone from Recent apps (pressing "Close" isn't proof).
        suspend fun gone(): Boolean { delay(700); return findLabel() == null }
        val tried = mutableListOf<String>()
        var gone = false
        var seen = false
        for (attempt in 0 until 3) {
            val label = findLabel()
            if (label == null) { if (seen) { gone = true; break }; delay(500); continue }
            seen = true
            // The card's "Close" action (Launcher3 adds one to each task); walk up to find it.
            var n: AccessibilityNodeInfo? = label
            var pressed = false
            while (n != null && !pressed) {
                val act = n.actionList.firstOrNull { a ->
                    a.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id ||
                        (a.label?.toString()?.lowercase()?.let { l -> listOf("close", "dismiss", "remove", "cerrar", "descartar", "quitar").any { it in l } } == true)
                }
                if (act != null) { pressed = n.performAction(act.id); tried += "action '${act.label ?: act.id}' on ${n.className?.toString()?.substringAfterLast('.')}" }
                n = n.parent
            }
            if (pressed && gone()) { gone = true; break }
            // Swipe the card up. The swipe has to start on the card's picture: find the card around the name, bring
            // it to the middle if it's at the side (Samsung's Recent apps shows it cut off at the edge), then swipe
            // from the card's middle and from points a third and half a screen below the name.
            var lab = label
            val screen = android.graphics.Rect().also { r -> runCatching { lab.window?.getBoundsInScreen(r) } }
            if (screen.isEmpty) screen.set(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
            fun cardOf(l: AccessibilityNodeInfo): android.graphics.Rect? {
                val lr = android.graphics.Rect(); l.getBoundsInScreen(lr)
                var n: AccessibilityNodeInfo? = l.parent
                repeat(5) {
                    val node = n ?: return null
                    val r = android.graphics.Rect(); node.getBoundsInScreen(r)
                    if (r.height() >= lr.height() * 3 && r.width() >= lr.width() && r.width() < screen.width() * 0.95f) return r
                    n = node.parent
                }
                return null
            }
            var card = cardOf(lab)
            val off = (card?.centerX() ?: android.graphics.Rect().also { lab.getBoundsInScreen(it) }.centerX()) - screen.centerX()
            if (kotlin.math.abs(off) > screen.width() * 0.15f) {
                val y = (card?.centerY() ?: screen.centerY()).toFloat()
                if (swipe(screen.centerX().toFloat(), y, (screen.centerX() - off).toFloat().coerceIn(10f, screen.right - 10f), y, lab, 300)) tried += "centred card (moved ${-off})"
                delay(700)
                lab = findLabel() ?: run { if (gone()) { gone = true }; null } ?: break
                card = cardOf(lab)
            }
            val lr = android.graphics.Rect(); lab.getBoundsInScreen(lr)
            val starts = listOfNotNull(
                card?.let { it.centerY() + it.height() / 8 },
                lr.centerY() + (screen.height() * 0.33f).toInt(),
                lr.centerY() + (screen.height() * 0.5f).toInt(),
            ).map { it.coerceAtMost(screen.bottom - 40) }.distinct()
            for (y0 in starts) {
                val x = (card?.centerX() ?: lr.centerX()).toFloat()
                if (swipe(x, y0.toFloat(), x, (screen.top + 10).toFloat(), lab, 320)) tried += "swipe from y $y0 of ${screen.height()}"
                if (gone()) { gone = true; break }
            }
            if (gone) break
        }
        delay(400)
        performGlobalAction(GLOBAL_ACTION_HOME)
        CrashLog.event("dismiss $appLabel from recents: " + when {
            gone -> "gone"
            !seen -> "no card found"
            else -> "card still there"
        } + (if (tried.isNotEmpty()) " (${tried.joinToString(", ")})" else ""))
        return gone
    }

    /** A straight swipe from (x0, y0) to (x1, y1) on the display [on] is shown on. */
    private suspend fun swipe(x0: Float, y0: Float, x1: Float, y1: Float, on: AccessibilityNodeInfo, ms: Long): Boolean {
        val path = android.graphics.Path().apply { moveTo(x0, y0); lineTo(x1, y1) }
        val builder = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, ms))
        if (android.os.Build.VERSION.SDK_INT >= 30) runCatching { on.window?.displayId?.let { builder.setDisplayId(it) } }
        val result = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val sent = dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(g: android.accessibilityservice.GestureDescription?) { result.complete(true) }
            override fun onCancelled(g: android.accessibilityservice.GestureDescription?) { result.complete(false) }
        }, null)
        return sent && (kotlinx.coroutines.withTimeoutOrNull(1500) { result.await() } ?: false)
    }

    /** Opens Dolphin's menu and presses "Exit Emulation", then waits until the game screen is gone. */
    suspend fun exitEmulation(pkg: String): Result {
        if (find(pkg, "frame_emulation_fragment") == null && find(pkg, "menu_exit") == null) return Result.NO_GAME
        repeat(3) {
            var exit = find(pkg, "menu_exit")
            if (exit == null) {
                dolphinBack(pkg) // Dolphin opens its in-game menu on Back
                if (waitFor(1500) { find(pkg, "menu_exit") != null }) exit = find(pkg, "menu_exit")
            }
            if (exit != null && click(exit)) {
                if (waitFor(6000) { find(pkg, "frame_emulation_fragment") == null }) {
                    delay(700) // let Dolphin finish shutting the core down
                    return Result.EXITED
                }
            }
        }
        return Result.FAILED
    }
}
