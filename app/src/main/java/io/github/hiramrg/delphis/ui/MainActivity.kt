package io.github.hiramrg.delphis.ui

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.hiramrg.delphis.core.ControllerProfile
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.platform.CoverArt
import io.github.hiramrg.delphis.platform.DeviceInfo
import io.github.hiramrg.delphis.platform.DolphinBridge
import io.github.hiramrg.delphis.platform.GameEntry
import io.github.hiramrg.delphis.platform.GameLauncher
import io.github.hiramrg.delphis.platform.GameLibrary
import io.github.hiramrg.delphis.platform.GameSession
import io.github.hiramrg.delphis.platform.Prefs
import io.github.hiramrg.delphis.platform.PresetStore
import io.github.hiramrg.delphis.platform.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Setup : Screen
    data object Home : Screen
    data class Game(val entry: GameEntry) : Screen
    data object Settings : Screen
    data object ControllerCheck : Screen
    data object Diagnostics : Screen
    data object GyroTest : Screen
}

class MainActivity : ComponentActivity() {
    lateinit var prefs: Prefs
    lateinit var bridge: DolphinBridge
    lateinit var library: GameLibrary
    lateinit var covers: CoverArt
    lateinit var store: PresetStore
    lateinit var updater: Updater

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data class UpToDate(val version: String) : UpdateState
        data class Available(val release: Updater.Release) : UpdateState
        data class Downloading(val progress: Float) : UpdateState
        data object NeedsPermission : UpdateState
        /** Downloaded and waiting for "Restart & update". */
        data class Ready(val release: Updater.Release, val apk: java.io.File) : UpdateState
        data object Installing : UpdateState
        data class Failed(val reason: String) : UpdateState
    }
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
    /** The "Build N is ready" card; "Later" hides it and leaves the banner. */
    var updatePrompt by mutableStateOf(false)

    var screen by mutableStateOf<Screen>(Screen.Home)
    /** Light (classic Wii, default) or dark menus. The in-game bottom screen is always dark. */
    /** "light", "dark" or "auto"; [darkTheme] is what's actually shown (Auto follows Android). */
    var themeMode by mutableStateOf("light")
    var darkTheme by mutableStateOf(false)
    /** Bumped when favorites / play stats change so lists re-read them. */
    var statsVersion by mutableIntStateOf(0)
    /** Game highlighted in All games (shown in detail on the bottom screen). */
    var focusedGame by mutableStateOf<GameEntry?>(null)
    /** The Home row the D-pad is on (null = the top card, sidebar or bar). */
    var homeRow by mutableStateOf<HomeRow?>(null)
    /** Preset choice for the open game page, shared with the bottom-screen picker. */
    var picker by mutableStateOf<GamePicker?>(null)
    var librarySection by mutableStateOf(LibSection.ALL)
    var librarySort by mutableStateOf(LibSort.RECENT)
    var libraryQuery by mutableStateOf("")
    var settingsSection by mutableStateOf(SettingsSection.APPEARANCE)
    /** True while a Recently played tile (not the hero card) has controller focus on Home. */
    var homeFocusedTile = false
    /** Replaces the home bar's button hints while something special is focused (column button, search). */
    var homeHint by mutableStateOf<String?>(null)
    /** True while the search box has the keyboard open: Home's button shortcuts stay off. */
    var searchEditing = false
    /** Home's scroll position (kept while visiting a game page). */
    val homeGrid = androidx.compose.foundation.lazy.grid.LazyGridState()
    /** Library column on Home: open (labels) or retracted (icons only). */
    var libraryOpen by mutableStateOf(false)
    /** Search shown over Home's rows (the rail's 🔍): the grid with its search box. */
    var searchOpen by mutableStateOf(false)
    /** The rail asks Home to jump to a row: [railTarget], bumped [railJump]. */
    var railTarget by mutableStateOf<HomeRow?>(null)
    var railJump by mutableIntStateOf(0)
    /** The first tile of each Home row, so the rail can put the ring on it. */
    val rowFocus = mutableMapOf<HomeRow, androidx.compose.ui.focus.FocusRequester>()
    /** Until when the sort options pop up above the bar's sort pill (after R1). */
    var sortPopupUntil by mutableLongStateOf(0L)
    /** Bumped to scroll Home down to the game list. */
    var scrollToGamesRequest by mutableIntStateOf(0)

    /** Shows Home scrolled down to the game list. */
    fun showAllGames() {
        screen = Screen.Home
        scrollToGamesRequest++
    }
    var games by mutableStateOf<List<GameEntry>>(emptyList())
    var scanning by mutableStateOf(false)
    var scanCount by mutableIntStateOf(0)
    /** Bumped whenever setup state changes so the UI re-reads it. */
    var setupVersion by mutableIntStateOf(0)
    /** Set when starting a game needs the close-games helper, which isn't turned on yet. */
    var exitHelperPrompt by mutableStateOf<(() -> Unit)?>(null)

    /** Non-null while the "What's new" card is showing (holds the version name). */
    private var whatsNewVersion by mutableStateOf<String?>(null)

    /** Set by the Controller Check / Diagnostics screens to receive raw gamepad input. */
    var keyListener: ((KeyEvent) -> Boolean)? = null
    var motionListener: ((MotionEvent) -> Boolean)? = null

    private val pickDolphinFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persist(uri, write = true)
            prefs.dolphinTreeUri = uri
            setupVersion++
            if (!bridge.looksLikeDolphinFolder()) {
                Notices.problem(this, "That isn't Dolphin's folder", "There's no Config folder inside. In the picker, open the side menu and pick \"Dolphin\".", "Try again") { connectDolphinFolder() }
            }
        }
    }

    private val pickMusic = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            prefs.menuMusicUri = uri
            io.github.hiramrg.delphis.platform.MenuMusic.refresh(this, prefs)
            setupVersion++
        }
    }

    /** Lets the user pick their own menu music file. */
    fun chooseMenuMusic() = pickMusic.launch(arrayOf("audio/*"))

    private val pickGamesFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persist(uri, write = false)
            prefs.gamesTreeUri = uri
            setupVersion++
            rescan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullScreen()
        prefs = Prefs(this)
        bridge = DolphinBridge(this, prefs)
        library = GameLibrary(this, prefs)
        covers = CoverArt(this)
        store = PresetStore(this)
        updater = Updater(this)
        checkForUpdates(silent = true)
        themeMode = prefs.themeMode
        io.github.hiramrg.delphis.platform.MenuSounds.init(this, prefs.menuSounds)
        // Release notes: once after an update; a fresh install just marks them as seen.
        val pkg = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val isUpdate = pkg != null && pkg.lastUpdateTime > pkg.firstInstallTime
        if (io.github.hiramrg.delphis.core.WhatsNew.shouldShow(prefs.whatsNewSeen, isUpdate) && isSetUp()) {
            whatsNewVersion = pkg?.versionName.orEmpty()
        } else if (prefs.whatsNewSeen < io.github.hiramrg.delphis.core.WhatsNew.RELEASE) {
            prefs.whatsNewSeen = io.github.hiramrg.delphis.core.WhatsNew.RELEASE
        }
        games = library.cached()
        if (!isSetUp()) screen = Screen.Setup
        else if (games.isEmpty()) rescan()
        // Proper game names (GameTDB's list, downloaded once a month), then refresh the list.
        lifecycleScope.launch {
            val ids = games.mapNotNull { it.info.gameId }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                io.github.hiramrg.delphis.platform.GameTitles.load(this@MainActivity, ids)
                io.github.hiramrg.delphis.platform.GameTitles.refresh(this@MainActivity, ids)
            }
            if (!scanning) games = library.cached()
        }

        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = when (themeMode) { "dark" -> true; "auto" -> systemDark; else -> false }
            androidx.compose.runtime.SideEffect { darkTheme = dark }
            WiiTheme(dark) {
                BackHandler(enabled = screen != Screen.Home && screen != Screen.Setup) {
                    screen = when (screen) {
                        Screen.Diagnostics, Screen.GyroTest, Screen.ControllerCheck -> if (isSetUp()) Screen.Settings else Screen.Setup
                        is Screen.Game -> Screen.Home
                        else -> Screen.Home
                    }
                }
                // Ⓑ never closes the app: on Home and Setup it does nothing (no accidental exits).
                BackHandler(enabled = screen == Screen.Home || screen == Screen.Setup) { }
                exitHelperPrompt?.let { action ->
                    val notNow = { exitHelperPrompt = null; prefs.skipExitHelperPrompt = true; action() }
                    val open = { exitHelperPrompt = null; io.github.hiramrg.delphis.platform.DolphinExitService.openSettings(this) }
                    androidx.compose.ui.window.Dialog(onDismissRequest = { exitHelperPrompt = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                        val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
                        PopupPanel(
                            "Closing games", "Turn on \"close games\"?",
                            androidx.compose.ui.Modifier.widthIn(max = 480.dp).focusRequester(focus).focusable()
                                .onPreviewKeyEvent { e ->
                                    if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key.nativeKeyCode) {
                                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { open(); true }
                                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { notNow(); true }
                                        else -> false
                                    }
                                },
                            buttons = {
                                PanelBtn("Not now", "B", false, onClick = notNow)
                                PanelBtn("Open settings", "A", true, onClick = open)
                            },
                        ) {
                            androidx.compose.material3.Text(
                                "On the Thor, Android won't let Delphis force-close Dolphin, so a game that's still running in Dolphin " +
                                    "stops the next one from starting. Turn on \"Delphis – close games\" in Accessibility and Delphis " +
                                    "will press Dolphin's own \"Exit Emulation\" for you. It doesn't touch your controller input.",
                                fontSize = 12.5.sp, color = LocalWii.current.textDim, lineHeight = 16.sp,
                            )
                        }
                        androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    }
                }
                whatsNewVersion?.let { v ->
                    WhatsNewDialog(v) {
                        whatsNewVersion = null
                        prefs.whatsNewSeen = io.github.hiramrg.delphis.core.WhatsNew.RELEASE
                    }
                }
                androidx.compose.foundation.layout.Box {
                    ChannelZoom(screen) {
                    when (val s = screen) {
                        Screen.Setup -> SetupScreen(this@MainActivity)
                        Screen.Home -> HomeScreen(this@MainActivity)
                        is Screen.Game -> GameScreen(this@MainActivity, s.entry)
                        Screen.Settings -> SettingsScreen(this@MainActivity)
                        Screen.ControllerCheck -> ControllerCheckScreen(this@MainActivity)
                        Screen.Diagnostics -> DiagnosticsScreen(this@MainActivity)
                        Screen.GyroTest -> GyroTestScreen(this@MainActivity)
                    }
                    }
                    (updateState as? UpdateState.Ready)?.let { r -> if (updatePrompt) UpdateReadyCard(r.release.versionCode, onLater = { updatePrompt = false }) { restartAndUpdate() } }
                    NoticeHost()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT ->
                io.github.hiramrg.delphis.platform.MenuSounds.play(io.github.hiramrg.delphis.platform.MenuSounds.Kind.MOVE)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> if (event.repeatCount == 0)
                io.github.hiramrg.delphis.platform.MenuSounds.play(io.github.hiramrg.delphis.platform.MenuSounds.Kind.SELECT)
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> if (event.repeatCount == 0)
                io.github.hiramrg.delphis.platform.MenuSounds.play(io.github.hiramrg.delphis.platform.MenuSounds.Kind.BACK)
        }
        // "Build N is ready": Ⓐ Restart & update, Ⓑ Later.
        if (updatePrompt && updateState is UpdateState.Ready) when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) restartAndUpdate(); return true
            }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) updatePrompt = false; return true
            }
        }
        // A notice with a fix ("Fix it Ⓨ"): Ⓨ presses it.
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_Y && Notices.current?.onAction != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) Notices.pressAction()
            return true
        }
        keyListener?.let { if (it(event)) return true }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        io.github.hiramrg.delphis.platform.MenuSounds.onMotion(event)
        motionListener?.let { if (it(event)) return true }
        return super.dispatchGenericMotionEvent(event)
    }

    fun setTheme(mode: String) { themeMode = mode; prefs.themeMode = mode }

    /** "Exit Delphis?" panel (⏻ in Home's bar). */
    var exitPrompt by mutableStateOf(false)

    /** Closes everything: a running game (with its autosave), Dolphin's background process, menu music, both screens. */
    fun exitAll() {
        exitPrompt = false
        lifecycleScope.launch {
            val pkg = bridge.installedPackage()
            val roles = DeviceInfo.screenRoles(this@MainActivity, prefs.swapScreens)
            if (io.github.hiramrg.delphis.platform.GameWatch.session != null) runCatching {
                bridge.closeDolphin(roles.topDisplayId, "Closing the game…")
            }
            // "Closing Delphis and Dolphin" covers both screens while Recent apps is used behind it.
            val last = lastGame()?.info
            io.github.hiramrg.delphis.platform.DolphinExitService.showExit(roles.topDisplayId, roles.bottomDisplayId, last?.title, last?.gameId)
            kotlinx.coroutines.delay(350)
            pkg?.let { p ->
                // No app may force-stop another, so Dolphin's card is removed from Recent apps (Android then
                // ends its process); the background-process call is a fallback.
                val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString() }.getOrDefault("Dolphin")
                runCatching { io.github.hiramrg.delphis.platform.DolphinExitService.dismissFromRecents(label) }
                runCatching { getSystemService(android.app.ActivityManager::class.java).killBackgroundProcesses(p) }
            }
            runCatching { io.github.hiramrg.delphis.platform.MenuMusic.stop() }
            MenuCompanion.close()
            io.github.hiramrg.delphis.platform.DolphinExitService.hideExit(1200)
            finishAndRemoveTask()
        }
    }

    /** The most recently played game that's still in the library. */
    fun lastGame(): GameEntry? {
        val last = prefs.lastPlayed()
        return games.filter { it.key in last }.maxByOrNull { last[it.key] ?: 0L }
    }

    /** Opens a game's page (preset choice). */
    fun openGame(game: GameEntry) {
        focusedGame = null
        picker = GamePicker(this, game)
        screen = Screen.Game(game)
    }

    /** Plays straight away with the remembered controls, otherwise opens the game's page to choose. */
    fun quickPlay(game: GameEntry) {
        val saved = prefs.presetFor(game.key)?.let { store.forGame(game.key, it) }
        if (saved != null) play(game, saved) else openGame(game)
    }

    /** Moves Delphis' top-screen window back to the front (and input focus with it). */
    fun bringSelfToFront() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        val top = DeviceInfo.screenRoles(this, prefs.swapScreens).topDisplayId
        val opts = ActivityOptions.makeBasic().setLaunchDisplayId(top).toBundle()
        runCatching { startActivity(intent, opts) }
    }

    fun isSetUp() = bridge.installedPackage() != null && bridge.hasConfigAccess() && prefs.gamesTreeUri != null

    fun connectDolphinFolder(): Unit = pickDolphinFolder.launch(bridge.pickerInitialUri())
    fun chooseGamesFolder(): Unit = pickGamesFolder.launch(prefs.gamesTreeUri)

    private fun persist(uri: Uri, write: Boolean) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
    }

    fun rescan() {
        if (scanning) return
        scanning = true
        scanCount = 0
        lifecycleScope.launch {
            games = library.scan { n -> lifecycleScope.launch { scanCount = n } }
            // Names for games that were just added.
            val ids = games.mapNotNull { it.info.gameId }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                io.github.hiramrg.delphis.platform.GameTitles.load(this@MainActivity, ids)
                io.github.hiramrg.delphis.platform.GameTitles.refresh(this@MainActivity, ids)
            }
            games = library.cached()
            scanning = false
        }
    }

    /** Looks for a newer build on GitHub. [silent] = background check that only surfaces good news. */
    fun checkForUpdates(silent: Boolean = false) {
        if (updateState is UpdateState.Checking || updateState is UpdateState.Downloading) return
        updateState = UpdateState.Checking
        lifecycleScope.launch {
            updateState = when (val r = updater.check()) {
                is Updater.Check.UpdateAvailable -> UpdateState.Available(r.release)
                is Updater.Check.UpToDate -> if (silent) UpdateState.Idle else UpdateState.UpToDate(updater.installedVersionName())
                is Updater.Check.Failed -> if (silent) UpdateState.Idle else UpdateState.Failed(r.reason)
            }
        }
    }

    /** Downloads the newer APK and hands it to Android's installer (which asks the user to confirm). */
    fun installUpdate(release: Updater.Release) {
        if (!updater.canInstall()) {
            updateState = UpdateState.NeedsPermission
            updater.openInstallPermissionSettings()
            return
        }
        lifecycleScope.launch {
            try {
                updateState = UpdateState.Downloading(0f)
                val apk = updater.download(release) { p -> lifecycleScope.launch { updateState = UpdateState.Downloading(p) } }
                updateState = UpdateState.Ready(release, apk)
                updatePrompt = true
            } catch (e: Exception) {
                updateState = UpdateState.Failed(e.message ?: "Download failed")
            }
        }
    }

    /** Closes Delphis, installs the downloaded update and opens Delphis again on the new version. */
    fun restartAndUpdate() {
        val s = updateState as? UpdateState.Ready ?: return
        updatePrompt = false
        updateState = UpdateState.Installing
        prefs.relaunchAfterUpdate = true
        try { updater.install(s.apk) } catch (e: Exception) {
            prefs.relaunchAfterUpdate = false
            updateState = UpdateState.Failed(e.message ?: "Install failed")
        }
    }

    /**
     * Full screen on the top display: Android's status bar and gesture line are hidden (Home's bar
     * shows the time, battery, Wi-Fi etc. instead). A swipe from the edge shows them for a moment,
     * over the app rather than pushing it around.
     */
    private fun goFullScreen() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs, the keyboard or the system pickers can bring the bars back; hide them again.
        if (hasFocus) goFullScreen()
    }

    override fun onResume() {
        super.onResume()
        goFullScreen()
        setupVersion++ // re-read things that can change in system settings (e.g. the close-games helper)
        // Back from a game (or anywhere): reload achievement progress so new unlocks show.
        io.github.hiramrg.delphis.platform.Achievements.refresh()
        // The game list showing up while a game is running (and Delphis didn't close it) means
        // Dolphin's game screen went away, most likely because Dolphin crashed.
        // (Unless the player had left the game for the home screen and opened Delphis from there.)
        if (!io.github.hiramrg.delphis.platform.GameWatch.suspended.value)
            io.github.hiramrg.delphis.platform.GameWatch.reportGone("Delphis' game list came back on screen")
        // Coming back from the "Install unknown apps" screen: continue if it's now allowed.
        if (updateState is UpdateState.NeedsPermission && updater.canInstall()) checkForUpdates()
        // Bottom-screen menu (quick tiles / touch preset picker), shortly after we're on screen.
        window.decorView.postDelayed({ if (!isFinishing && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) MenuCompanion.show(this) }, 400)
    }

    override fun onStart() {
        super.onStart()
        io.github.hiramrg.delphis.platform.MenuMusic.start(this, prefs)
    }

    override fun onStop() {
        // Leaving for the home screen / another app (Back or Home): the bottom-screen menu goes too.
        // It comes back in onResume.
        MenuCompanion.close()
        io.github.hiramrg.delphis.platform.MenuMusic.pause()
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) { MenuCompanion.close(); io.github.hiramrg.delphis.platform.MenuMusic.release() }
        super.onDestroy()
    }

    fun controllerProfile(): ControllerProfile = DeviceInfo.controllerProfile(prefs)

    /**
     * Applies [preset] to Dolphin, opens the always-on controls view on the bottom display and
     * starts the game on the top display. The bottom view is opened first so Dolphin, launched last,
     * keeps input focus.
     */
    fun play(entry: GameEntry, preset: Preset) {
        if (!io.github.hiramrg.delphis.platform.DolphinExitService.isEnabled(this) && !prefs.skipExitHelperPrompt) {
            exitHelperPrompt = { play(entry, preset) }
            return
        }
        io.github.hiramrg.delphis.platform.MenuMusic.pause()
        prefs.markPlayed(entry.key)
        statsVersion++
        MenuCompanion.close()
        start(this, bridge, prefs, entry, preset)
    }

    companion object {
        /**
         * Applies [preset] to Dolphin, opens the always-on controls view on the bottom display and
         * starts the game on the top display. Used by the game list and by frontends ([LaunchActivity]);
         * [returnTo] is the frontend to go back to when the game is closed.
         */
        fun start(ctx: android.content.Context, bridge: DolphinBridge, prefs: Prefs, entry: GameEntry, preset: Preset, returnTo: String? = null) {
            val app = ctx.applicationContext
            GameLauncher.run(ctx) {
                val applied = applyPreset(app, bridge, prefs, entry.info.gameId, preset)
                val roles = DeviceInfo.screenRoles(app, prefs.swapScreens)
                if (roles.bottomDisplayId != null) {
                    val session = GameSession(entry.uri, entry.fileName, entry.info, preset.id, applied, returnTo)
                    val intent = session.writeTo(Intent(app, NowPlayingActivity::class.java))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    val opts = ActivityOptions.makeBasic().setLaunchDisplayId(roles.bottomDisplayId).toBundle()
                    runCatching { app.startActivity(intent, opts) }
                }
                val resumeId = entry.info.gameId?.takeIf {
                    prefs.autoSaveFor(it) && withContext(Dispatchers.IO) { bridge.hasAutosave(it) }
                }
                if (!bridge.launchFresh(entry.uri, roles.topDisplayId, resumeId, entry.info.title, entry.info.gameId)) {
                    Notices.info(app, "Dolphin isn't installed", "Install Dolphin from the Play Store, then come back.")
                }
            }
        }

        /** Writes the preset (per game when the ID is known, otherwise as the default config). */
        suspend fun applyPreset(ctx: android.content.Context, bridge: DolphinBridge, prefs: Prefs, gameId: String?, preset: Preset): Boolean {
            val result = withContext(Dispatchers.IO) {
                if (prefs.autoSaveResume) bridge.ensureSaveStatesEnabled()
                bridge.ensureTouchOverlayOff()
                // Multiplayer: P1 is the Thor unless it sits out; players 2-4 are the other controllers connected now.
                val slots = io.github.hiramrg.delphis.platform.Pads.slots(prefs, gameId, preset)
                val (p1, profile) = slots.first()
                if (gameId != null) bridge.applyPreset(gameId, p1, profile).also {
                    bridge.applyPlayers(gameId, slots.drop(1))
                    // GameCube games: how the 4:3 picture fills the 16:9 screen.
                    if (preset.controller == io.github.hiramrg.delphis.core.EmulatedController.GAMECUBE) bridge.applyScreen(gameId, prefs.screenFor(gameId))
                    // Looks: the game's post-processing shader (Wii and GameCube).
                    if (prefs.looksEnabled) bridge.applyLook(gameId, prefs.lookFor(gameId))
                }
                else bridge.applyGlobally(p1, profile)
            }
            if (result is DolphinBridge.ApplyResult.Failed) {
                Notices.problem(ctx, "Couldn't write the preset", result.reason)
            }
            return result is DolphinBridge.ApplyResult.Ok
        }
    }
}
