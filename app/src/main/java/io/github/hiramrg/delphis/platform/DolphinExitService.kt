package io.github.hiramrg.delphis.platform

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * Closes the running game the way a player would: opens Dolphin's in-game menu (Back) and taps
 * its "Exit Emulation" button.
 *
 * Why: leaving Dolphin's game screen only pauses emulation, and Dolphin ignores new launches until
 * emulation really stops. On the AYN Thor Android doesn't let Delphis force-close Dolphin, so
 * the old game kept running and the next launch collided with it. Exiting through Dolphin's own
 * menu stops emulation properly.
 *
 * It never filters key or controller events, so it adds no input delay; it only receives
 * Dolphin's (rare) window-change events and does nothing with them.
 */
class DolphinExitService : AccessibilityService() {

    companion object {
        @Volatile private var instance: DolphinExitService? = null

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
        suspend fun dismissFromRecents(appLabel: String): Boolean = instance?.dismissFromRecentsImpl(appLabel) ?: false

        /** See [pressQuickSave]. Null when the service isn't running. */
        suspend fun quickSave(pkg: String): StateResult? = instance?.pressQuickSave(pkg)

        /** See [pressQuickLoad]. Null when the service isn't running. */
        suspend fun quickLoad(pkg: String): StateResult? = instance?.pressQuickLoad(pkg)

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
        fun showAchievement(popup: Achievements.Popup, badge: android.graphics.Bitmap?, displayId: Int?): Boolean {
            val s = instance ?: return false
            s.handler.post { s.achievementToast.show(popup, badge, displayId) }
            return true
        }

        fun hideLoading(delayMs: Long = 0) { instance?.let { s -> s.handler.post { s.loading.hide(delayMs) } } }

        // ---- bottom-screen trackpad: served to Dolphin as a DSU pad (see DsuServer); kept here for the callers
        @Suppress("UNUSED_PARAMETER") fun trackpadDown(topDisplayId: Int) { DsuServer.ensureStarted() }
        fun trackpadMove(dxDp: Float, dyDp: Float, speed: io.github.hiramrg.delphis.core.Trackpad.Speed) = DsuServer.move(dxDp, dyDp, speed)
        fun trackpadUp() {}
        @Suppress("UNUSED_PARAMETER") fun trackpadRecenter(topDisplayId: Int) = DsuServer.recenter()
        fun trackpadReset() = DsuServer.reset()

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
        handler.removeCallbacksAndMessages(null)
        instance = null
        super.onDestroy()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val loading by lazy { LoadingOverlay(this) }
    private val achievementToast by lazy { AchievementToast(this) }
    private val sideFill by lazy { SideFill(this) }
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

    /** Opens Dolphin's in-game menu (Back toggles it) unless it's already open. */
    private suspend fun openMenu(pkg: String): Boolean {
        if (find(pkg, "menu_exit") != null) return true
        performGlobalAction(GLOBAL_ACTION_BACK)
        return waitFor(1500) { find(pkg, "menu_exit") != null }
    }

    /**
     * Opens Dolphin's menu and presses "Quick Save". The menu is left open (exiting next uses it).
     * The state is written in the background by Dolphin; the caller waits for the file.
     */
    suspend fun pressQuickSave(pkg: String): StateResult {
        if (find(pkg, "frame_emulation_fragment") == null && find(pkg, "menu_exit") == null) return StateResult.NO_GAME
        if (!openMenu(pkg)) return StateResult.FAILED
        // The button is hidden while Dolphin's "Enable Savestates" setting is off.
        waitFor(1500) { find(pkg, "menu_quicksave")?.isVisibleToUser == true }
        val save = find(pkg, "menu_quicksave")?.takeIf { it.isVisibleToUser }
            ?: return if (find(pkg, "menu_quicksave") == null) StateResult.SAVE_STATES_OFF else StateResult.FAILED
        delay(300)
        return if (click(save)) StateResult.DONE else StateResult.FAILED
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
                performGlobalAction(GLOBAL_ACTION_BACK) // close the menu, try again later
                delay(3000)
                return@repeat
            }
            // The menu slides in; wait until the button is really on screen.
            waitFor(1500) { find(pkg, "menu_quickload")?.isVisibleToUser == true }
            val load = find(pkg, "menu_quickload")?.takeIf { it.isVisibleToUser }
            if (load == null) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return if (find(pkg, "menu_quickload") == null) StateResult.SAVE_STATES_OFF else StateResult.FAILED
            }
            delay(500) // let the slide-in animation settle so the click lands
            val ok = click(load)
            delay(400)
            if (find(pkg, "menu_exit") != null) performGlobalAction(GLOBAL_ACTION_BACK) // close the menu
            return if (ok) StateResult.DONE else StateResult.FAILED
        }
        return StateResult.FAILED
    }

    /**
     * Removes an app's card from Recent apps, the way a person would: opens Recents, finds the card by the
     * app's name, presses its "Close" action (or swipes it up), then goes Home. Android then ends that app's
     * process. Used by "Exit Delphis" for Dolphin, since no app may force-stop another one.
     */
    suspend fun dismissFromRecentsImpl(appLabel: String): Boolean {
        if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) return false
        delay(900)
        var done = false
        for (attempt in 0 until 3) {
            val roots = runCatching { windows.mapNotNull { it.root } }.getOrDefault(emptyList()) +
                listOfNotNull(runCatching { rootInActiveWindow }.getOrNull())
            val label = roots.filter { it.packageName?.toString() != packageName }.firstNotNullOfOrNull { r -> runCatching { r.findAccessibilityNodeInfosByText(appLabel).firstOrNull() }.getOrNull() }
            if (label == null) { delay(500); continue }
            // The card offers a "Close" action (Launcher3 adds it to each task); walk up to find it.
            var n: AccessibilityNodeInfo? = label
            while (n != null && !done) {
                val act = n.actionList.firstOrNull { a ->
                    a.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id ||
                        (a.label?.toString()?.lowercase()?.let { l -> listOf("close", "dismiss", "remove", "cerrar", "descartar", "quitar").any { it in l } } == true)
                }
                if (act != null) done = n.performAction(act.id)
                n = n.parent
            }
            if (!done) done = swipeUp(label)
            if (done) break
            delay(400)
        }
        delay(600)
        performGlobalAction(GLOBAL_ACTION_HOME)
        CrashLog.event("dismiss $appLabel from recents: " + if (done) "done" else "not found")
        return done
    }

    /** Swipes a Recents card up (from just below its label, where the card's picture is). */
    private suspend fun swipeUp(label: AccessibilityNodeInfo): Boolean {
        val r = android.graphics.Rect(); label.getBoundsInScreen(r)
        val path = android.graphics.Path().apply { moveTo(r.centerX().toFloat(), (r.bottom + 260).toFloat()); lineTo(r.centerX().toFloat(), 20f) }
        val builder = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 250))
        if (android.os.Build.VERSION.SDK_INT >= 30) runCatching { label.window?.displayId?.let { builder.setDisplayId(it) } }
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
                performGlobalAction(GLOBAL_ACTION_BACK) // Dolphin opens its in-game menu on Back
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
