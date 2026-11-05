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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    data object Library : Screen
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
        data object Installing : UpdateState
        data class Failed(val reason: String) : UpdateState
    }
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)

    var screen by mutableStateOf<Screen>(Screen.Home)
    /** Light (classic Wii, default) or dark menus. The in-game bottom screen is always dark. */
    var darkTheme by mutableStateOf(false)
    /** Bumped when favorites / play stats change so lists re-read them. */
    var statsVersion by mutableIntStateOf(0)
    /** Game highlighted in All games (shown in detail on the bottom screen). */
    var focusedGame by mutableStateOf<GameEntry?>(null)
    /** Preset choice for the open game page, shared with the bottom-screen picker. */
    var picker by mutableStateOf<GamePicker?>(null)
    var librarySection by mutableStateOf(LibSection.ALL)
    var librarySort by mutableStateOf(LibSort.RECENT)
    var libraryQuery by mutableStateOf("")
    var settingsSection by mutableStateOf(SettingsSection.APPEARANCE)
    /** True while a Recently played tile (not the hero card) has controller focus on Home. */
    var homeFocusedTile = false
    var games by mutableStateOf<List<GameEntry>>(emptyList())
    var scanning by mutableStateOf(false)
    var scanCount by mutableIntStateOf(0)
    /** Bumped whenever setup state changes so the UI re-reads it. */
    var setupVersion by mutableIntStateOf(0)
    /** Set when starting a game needs the close-games helper, which isn't turned on yet. */
    var exitHelperPrompt by mutableStateOf<(() -> Unit)?>(null)

    /** Set by the Controller Check / Diagnostics screens to receive raw gamepad input. */
    var keyListener: ((KeyEvent) -> Boolean)? = null
    var motionListener: ((MotionEvent) -> Boolean)? = null

    private val pickDolphinFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persist(uri, write = true)
            prefs.dolphinTreeUri = uri
            setupVersion++
            if (!bridge.looksLikeDolphinFolder()) {
                Toast.makeText(this, "That folder doesn't look like Dolphin's user folder (no Config inside). Pick \"Dolphin\" in the picker's side menu.", Toast.LENGTH_LONG).show()
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
        prefs = Prefs(this)
        bridge = DolphinBridge(this, prefs)
        library = GameLibrary(this, prefs)
        covers = CoverArt(this)
        store = PresetStore(this)
        updater = Updater(this)
        checkForUpdates(silent = true)
        darkTheme = prefs.themeDark
        games = library.cached()
        if (!isSetUp()) screen = Screen.Setup
        else if (games.isEmpty()) rescan()

        setContent {
            WiiTheme(darkTheme) {
                BackHandler(enabled = screen != Screen.Home && screen != Screen.Setup) {
                    screen = when (screen) {
                        Screen.Diagnostics, Screen.GyroTest, Screen.ControllerCheck -> if (isSetUp()) Screen.Settings else Screen.Setup
                        is Screen.Game -> Screen.Library
                        else -> Screen.Home
                    }
                }
                exitHelperPrompt?.let { action ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { exitHelperPrompt = null },
                        title = { androidx.compose.material3.Text("Turn on \"close games\"?") },
                        text = {
                            androidx.compose.material3.Text(
                                "On the Thor, Android won't let Delphis force-close Dolphin, so a game that's still running in Dolphin " +
                                    "stops the next one from starting. Turn on \"Delphis – close games\" in Accessibility and Delphis " +
                                    "will press Dolphin's own \"Exit Emulation\" for you. It doesn't touch your controller input."
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                exitHelperPrompt = null
                                io.github.hiramrg.delphis.platform.DolphinExitService.openSettings(this)
                            }) { androidx.compose.material3.Text("Open settings") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                exitHelperPrompt = null
                                prefs.skipExitHelperPrompt = true
                                action()
                            }) { androidx.compose.material3.Text("Not now") }
                        },
                    )
                }
                when (val s = screen) {
                    Screen.Setup -> SetupScreen(this)
                    Screen.Home -> HomeScreen(this)
                    Screen.Library -> LibraryScreen(this)
                    is Screen.Game -> GameScreen(this, s.entry)
                    Screen.Settings -> SettingsScreen(this)
                    Screen.ControllerCheck -> ControllerCheckScreen(this)
                    Screen.Diagnostics -> DiagnosticsScreen(this)
                    Screen.GyroTest -> GyroTestScreen(this)
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        keyListener?.let { if (it(event)) return true }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        motionListener?.let { if (it(event)) return true }
        return super.dispatchGenericMotionEvent(event)
    }

    fun setTheme(dark: Boolean) { darkTheme = dark; prefs.themeDark = dark }

    /** The most recently played game that's still in the library. */
    fun lastGame(): GameEntry? {
        val last = prefs.lastPlayed()
        return games.filter { it.key in last }.maxByOrNull { last[it.key] ?: 0L }
    }

    /** Opens a game's page (preset choice). */
    fun openGame(game: GameEntry) {
        picker = GamePicker(this, game)
        screen = Screen.Game(game)
    }

    /** Plays straight away with the remembered controls, otherwise opens the game's page to choose. */
    fun quickPlay(game: GameEntry) {
        val saved = prefs.presetFor(game.key)?.let { store.byId(it) }
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

    fun connectDolphinFolder() = pickDolphinFolder.launch(bridge.pickerInitialUri())
    fun chooseGamesFolder() = pickGamesFolder.launch(prefs.gamesTreeUri)

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
                updateState = UpdateState.Installing
                updater.install(apk)
            } catch (e: Exception) {
                updateState = UpdateState.Failed(e.message ?: "Download failed")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupVersion++ // re-read things that can change in system settings (e.g. the close-games helper)
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
        GameLauncher.run(this) {
            val applied = applyPreset(this@MainActivity, bridge, prefs, entry.info.gameId, preset)
            val roles = DeviceInfo.screenRoles(this@MainActivity, prefs.swapScreens)
            if (roles.bottomDisplayId != null) {
                val session = GameSession(entry.uri, entry.fileName, entry.info, preset.id, applied)
                val intent = session.writeTo(Intent(this@MainActivity, NowPlayingActivity::class.java))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val opts = ActivityOptions.makeBasic().setLaunchDisplayId(roles.bottomDisplayId).toBundle()
                runCatching { startActivity(intent, opts) }
            }
            val resumeId = entry.info.gameId?.takeIf {
                prefs.autoSaveResume && withContext(Dispatchers.IO) { bridge.hasAutosave(it) }
            }
            if (!bridge.launchFresh(entry.uri, roles.topDisplayId, resumeId)) {
                Toast.makeText(applicationContext, "Dolphin isn't installed.", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        /** Writes the preset (per game when the ID is known, otherwise as the default config). */
        suspend fun applyPreset(ctx: android.content.Context, bridge: DolphinBridge, prefs: Prefs, gameId: String?, preset: Preset): Boolean {
            val profile = DeviceInfo.controllerProfile(prefs)
            val result = withContext(Dispatchers.IO) {
                if (prefs.autoSaveResume) bridge.ensureSaveStatesEnabled()
                if (gameId != null) bridge.applyPreset(gameId, preset, profile) else bridge.applyGlobally(preset, profile)
            }
            if (result is DolphinBridge.ApplyResult.Failed) {
                Toast.makeText(ctx.applicationContext, "Couldn't write the preset: ${result.reason}", Toast.LENGTH_LONG).show()
            }
            return result is DolphinBridge.ApplyResult.Ok
        }
    }
}
