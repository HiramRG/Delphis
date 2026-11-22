package io.github.hiramrg.delphis.platform

import io.github.hiramrg.delphis.core.SaveSlots
import io.github.hiramrg.delphis.core.SavePictures
import io.github.hiramrg.delphis.core.KeptSaves

import android.app.ActivityOptions
import android.content.ClipData
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.hiramrg.delphis.core.Trackpad
import android.provider.DocumentsContract
import io.github.hiramrg.delphis.core.AutosaveBackups
import io.github.hiramrg.delphis.core.DolphinConfigWriter
import io.github.hiramrg.delphis.core.ControllerProfile
import io.github.hiramrg.delphis.core.Preset

/**
 * Talks to Dolphin:
 *  - writes input profiles + per-game INIs through Dolphin's own DocumentsProvider
 *    (authority "<package>.user", root document "root" = Dolphin's user folder), which the user
 *    grants once with the system folder picker;
 *  - launches games the same way frontends like ES-DE do (MainActivity + content URI / AutoStartFile).
 */
class DolphinBridge(private val context: Context, private val prefs: Prefs) {
    private val resolver: ContentResolver get() = context.contentResolver

    companion object {
        /** How the last autosave went, in words (for Diagnostics: the sleep save test). */
        @Volatile var lastSaveNote = ""

        val KNOWN_PACKAGES = listOf(
            "org.dolphinemu.dolphinemu",
            "org.dolphinemu.dolphinemu.debug",
            "org.dolphinemu.dolphinemu.benchmark",
        )
        const val MAIN_ACTIVITY = "org.dolphinemu.dolphinemu.ui.main.MainActivity"
    }

    fun installedPackage(): String? {
        prefs.dolphinPackage?.let { if (isInstalled(it)) return it }
        return KNOWN_PACKAGES.firstOrNull(::isInstalled)
    }

    private fun isInstalled(pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    /** URI to pre-select Dolphin's folder in the system picker. */
    fun pickerInitialUri(): Uri? = installedPackage()?.let {
        DocumentsContract.buildDocumentUri("$it.user", "root")
    }

    fun hasConfigAccess(): Boolean {
        val tree = prefs.dolphinTreeUri ?: return false
        return resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
    }

    // ------------------------------------------------------------------ applying presets

    sealed interface ApplyResult {
        data object Ok : ApplyResult
        data class Failed(val reason: String) : ApplyResult
    }

    /** Writes the profile and points the game's INI at it. Safe to call before every launch. */
    fun applyPreset(gameId: String, preset: Preset, controller: ControllerProfile): ApplyResult {
        val tree = prefs.dolphinTreeUri ?: return ApplyResult.Failed("Dolphin's folder hasn't been connected yet.")
        return try {
            ensureBaseConfig(tree, controller)
            writeText(tree, DolphinConfigWriter.profilePath(preset), DolphinConfigWriter.renderProfile(preset, controller))
            val iniPath = DolphinConfigWriter.gameIniPath(gameId)
            val existing = readText(tree, iniPath) ?: ""
            writeText(tree, iniPath, DolphinConfigWriter.mergeGameIni(existing, preset))
            if (preset.isTrackpad) ensureTrackpadOverlay(tree)
            prefs.lastApplied = "$gameId|${preset.id}"
            ApplyResult.Ok
        } catch (e: Exception) {
            ApplyResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Writes a GameCube game's screen choice (aspect ratio, widescreen code or hack) into its Dolphin settings. */
    fun applyScreen(gameId: String, choice: io.github.hiramrg.delphis.core.GameScreen.Choice) {
        val tree = prefs.dolphinTreeUri ?: return
        runCatching {
            val iniPath = DolphinConfigWriter.gameIniPath(gameId)
            val existing = readText(tree, iniPath) ?: ""
            writeText(tree, iniPath, io.github.hiramrg.delphis.core.GameScreen.applyToGameIni(existing, choice, gameId, prefs.gcUseHack))
        }.onFailure { CrashLog.event("screen settings not written: ${it.message}") }
    }

    /** Writes the game's resolution into its Dolphin settings file (Dolphin's own setting = only Delphis' value removed). */
    fun applyResolution(gameId: String, level: io.github.hiramrg.delphis.core.Resolution.Level) {
        val tree = prefs.dolphinTreeUri ?: return
        runCatching {
            val iniPath = DolphinConfigWriter.gameIniPath(gameId)
            val existing = readText(tree, iniPath) ?: ""
            val updated = io.github.hiramrg.delphis.core.Resolution.applyToGameIni(existing, level)
            if (updated != existing) { writeText(tree, iniPath, updated); CrashLog.event("resolution for $gameId: ${level.label}") }
        }.onFailure { CrashLog.event("resolution not written: ${it.message}") }
    }

    /** Copies the look's shader into Dolphin's Shaders folder (if needed) and picks it in the game's settings file. */
    fun applyLook(gameId: String, look: io.github.hiramrg.delphis.core.Looks.Look) {
        val tree = prefs.dolphinTreeUri ?: return
        runCatching {
            val file = look.file
            val src = io.github.hiramrg.delphis.core.Looks.source(look)
            if (file != null && src != null) {
                val path = listOf("Shaders", "$file.glsl")
                if (readText(tree, path) != src) writeText(tree, path, src)
            }
            val iniPath = DolphinConfigWriter.gameIniPath(gameId)
            val existing = readText(tree, iniPath) ?: ""
            val updated = io.github.hiramrg.delphis.core.Looks.applyToGameIni(existing, look)
            if (updated != existing) writeText(tree, iniPath, updated)
        }.onFailure { CrashLog.event("look not written: ${it.message}") }
    }

    /** Creates Config/WiimoteNew.ini and GCPadNew.ini if Dolphin hasn't yet; without them Dolphin ignores per-game profiles. */
    private fun ensureBaseConfig(tree: Uri, controller: ControllerProfile) {
        for (path in DolphinConfigWriter.baseConfigPaths()) {
            val existing = readText(tree, path)
            if (DolphinConfigWriter.needsBaseConfig(existing)) writeText(tree, path, DolphinConfigWriter.baseConfig(path, controller))
        }
    }

    /** Status of Dolphin's main controller files, for diagnostics. */
    fun baseConfigStatus(): String {
        val tree = prefs.dolphinTreeUri ?: return "(not connected)"
        return DolphinConfigWriter.baseConfigPaths().joinToString("\n") { path ->
            val text = runCatching { readText(tree, path) }.getOrNull()
            val sections = text?.lines()?.filter { it.trim().startsWith("[") }?.joinToString(" ") ?: ""
            "${path.joinToString("/")}: " + when {
                text == null -> "MISSING"
                sections.isEmpty() -> "EMPTY"
                else -> "ok ($sections)"
            }
        }
    }

    fun configListing(): List<String> {
        val tree = prefs.dolphinTreeUri ?: return emptyList()
        return runCatching {
            val config = findChild(tree, rootDocId(tree), "Config")?.first ?: return emptyList()
            val out = mutableListOf<String>()
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, config),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
            )?.use { c -> while (c.moveToNext()) out += c.getString(0) }
            out.sorted()
        }.getOrDefault(emptyList())
    }

    /** For games without a readable ID: make the preset player 1's default controller config. */
    /**
     * Players 2-4 for [gameId] ([others] = their preset and controller, may be empty): writes each
     * player's profile and switches their Wii Remote / GameCube port on for this game only.
     */
    fun applyPlayers(gameId: String, others: List<Pair<Preset, ControllerProfile>>) {
        val tree = prefs.dolphinTreeUri ?: return
        runCatching {
            others.forEachIndexed { i, (p, controller) ->
                val profile = io.github.hiramrg.delphis.core.Players.profileFor(p, i + 2)
                writeText(tree, DolphinConfigWriter.profilePath(profile), DolphinConfigWriter.renderProfile(profile, controller))
            }
            val iniPath = DolphinConfigWriter.gameIniPath(gameId)
            val existing = readText(tree, iniPath) ?: ""
            val updated = io.github.hiramrg.delphis.core.Players.mergeGameIni(existing, others.map { it.first })
            if (updated != existing) writeText(tree, iniPath, updated)
            if (others.isNotEmpty()) CrashLog.event("players: ${others.size + 1} for $gameId (" + others.joinToString { it.second.dolphinDevice } + ")")
        }.onFailure { CrashLog.event("players: couldn't set up players 2-4: ${it.message}") }
    }

    fun applyGlobally(preset: Preset, controller: ControllerProfile): ApplyResult {
        val tree = prefs.dolphinTreeUri ?: return ApplyResult.Failed("Dolphin's folder hasn't been connected yet.")
        return try {
            writeText(tree, DolphinConfigWriter.profilePath(preset), DolphinConfigWriter.renderProfile(preset, controller))
            val path = DolphinConfigWriter.globalConfigPath(preset)
            val existing = readText(tree, path) ?: ""
            writeText(tree, path, DolphinConfigWriter.mergeGlobalConfig(existing, preset, controller))
            if (preset.isTrackpad) ensureTrackpadOverlay(tree)
            prefs.lastApplied = "|${preset.id}"
            ApplyResult.Ok
        } catch (e: Exception) {
            ApplyResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Removes the companion's per-game override so Dolphin's normal controller settings apply. */
    fun clearPreset(gameId: String): ApplyResult {
        val tree = prefs.dolphinTreeUri ?: return ApplyResult.Failed("Not connected")
        return try {
            val path = DolphinConfigWriter.gameIniPath(gameId)
            readText(tree, path)?.let { writeText(tree, path, DolphinConfigWriter.clearGameIni(it)) }
            ApplyResult.Ok
        } catch (e: Exception) {
            ApplyResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    // ------------------------------------------------------------------ diagnostics

    /** True when the connected folder really is Dolphin's user folder (it contains Config/). */
    fun looksLikeDolphinFolder(): Boolean {
        val tree = prefs.dolphinTreeUri ?: return false
        return runCatching { findChild(tree, rootDocId(tree), "Config") != null }.getOrDefault(false)
    }

    /** Names of the entries at the top of the connected folder. */
    fun rootListing(): List<String> {
        val tree = prefs.dolphinTreeUri ?: return emptyList()
        return runCatching {
            val out = mutableListOf<String>()
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootDocId(tree)),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
            )?.use { c -> while (c.moveToNext()) out += c.getString(0) }
            out.sorted()
        }.getOrDefault(emptyList())
    }

    /** Reads back what Dolphin will see for this game: the game INI and the profile it points to. */
    fun readBack(gameId: String?, preset: Preset): String {
        val tree = prefs.dolphinTreeUri ?: return "(Dolphin folder not connected)"
        return buildString {
            if (gameId != null) {
                appendLine("--- GameSettings/$gameId.ini")
                appendLine(runCatching { readText(tree, DolphinConfigWriter.gameIniPath(gameId)) }.getOrNull() ?: "(missing)")
            }
            val path = DolphinConfigWriter.profilePath(preset)
            appendLine("--- ${path.joinToString("/")}")
            appendLine(runCatching { readText(tree, path) }.getOrNull() ?: "(missing)")
        }
    }

    // ------------------------------------------------------------------ launching

    /**
     * Starts [gameUri] in Dolphin on [displayId] (the Thor's top screen).
     * Dolphin's StartupHandler accepts clip data, intent data and the "AutoStartFile" extra.
     */
    fun launch(gameUri: Uri, displayId: Int?): Boolean {
        val pkg = installedPackage() ?: return false
        val isPath = gameUri.scheme == "file"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(pkg, MAIN_ACTIVITY)
            // A plain file path (from a frontend) can't go in the intent's data: Android forbids file:// URIs
            // between apps. Dolphin reads it from the AutoStartFile extra instead.
            if (!isPath) {
                setDataAndType(gameUri, "application/octet-stream")
                clipData = ClipData.newRawUri("game", gameUri)
            }
            putExtra("AutoStartFile", if (isPath) gameUri.path else gameUri.toString())
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        if (!isPath) runCatching { context.grantUriPermission(pkg, gameUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val options = displayId?.let { ActivityOptions.makeBasic().setLaunchDisplayId(it).toBundle() }
        return try {
            context.startActivity(intent, options); true
        } catch (_: Exception) {
            // Some firmwares refuse cross-display launches; fall back to the default display.
            try { context.startActivity(intent); true } catch (_: Exception) { false }
        }
    }

    /**
     * Brings the running game back to the front on [displayId] (so it has gamepad focus again)
     * without restarting it. Re-sending the exact intent the game was started with (same action,
     * component, data and type) makes Android just move Dolphin's existing task to the front.
     */
    fun bringToFront(gameUri: Uri, displayId: Int?) {
        val pkg = installedPackage() ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(pkg, MAIN_ACTIVITY)
            if (gameUri.scheme != "file") setDataAndType(gameUri, "application/octet-stream")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val options = displayId?.let { ActivityOptions.makeBasic().setLaunchDisplayId(it).toBundle() }
        runCatching { context.startActivity(intent, options) }
    }

    /** How [closeDolphin] went. */
    enum class CloseResult {
        /** Dolphin's own Exit Emulation was pressed and the game screen is gone. */
        EXITED,
        /** No game was running. */
        NO_GAME,
        /** Force-close was requested; without the exit helper there is no way to check it worked. */
        UNVERIFIED,
    }

    /**
     * Stops the running game so a new launch is accepted. Leaving Dolphin's game screen only
     * *pauses* emulation, and while that paused emulation exists Dolphin ignores new launch requests
     * (EmulationActivity.ignoreLaunchRequests); launching anyway made Dolphin crash.
     *
     * 1. With the exit helper turned on ([DolphinExitService]): press Dolphin's "Exit Emulation".
     *    This is the reliable way; on the AYN Thor Android refuses to force-close Dolphin.
     * 2. Otherwise (or if that failed): cover the top screen so Dolphin is in the background and
     *    ask Android to stop it.
     */
    suspend fun closeDolphin(displayId: Int?, message: String = "Closing Dolphin…"): CloseResult {
        val pkg = installedPackage() ?: return CloseResult.NO_GAME
        // Cover the top screen while Dolphin's menus are pressed (the caller hides it when it's done).
        val playing = GameWatch.session?.info
        // The save's picture is taken before the cover goes up (one screen, with Delphis' menu open: the one
        // taken when the menu opened).
        var shot: android.graphics.Bitmap? = null
        if (DolphinExitService.gameShowing(pkg)) {
            val saving = playing?.gameId != null && prefs.autoSaveFor(playing.gameId)
            if (saving) shot = DolphinExitService.gamePicture()
            DolphinExitService.showLoading(
                if (saving) LoadingOverlay.Phase.SAVING else LoadingOverlay.Phase.CLOSING,
                playing?.title, playing?.gameId, if (saving) "Autosaving and closing the game…" else "Closing the game…", displayId,
            )
            kotlinx.coroutines.delay(250) // let it draw before Dolphin's menu opens
        }
        // Autosave first, while the game is still running.
        playing?.gameId?.let { id -> if (prefs.autoSaveFor(id)) autoSave(pkg, id, shot, capture = false) }
        when (DolphinExitService.exit(pkg)) {
            DolphinExitService.Result.EXITED -> return CloseResult.EXITED
            DolphinExitService.Result.NO_GAME -> return CloseResult.NO_GAME
            DolphinExitService.Result.FAILED, null -> Unit
        }

        val shown = kotlinx.coroutines.CompletableDeferred<Unit>()
        CoverActivity.shown = shown
        val cover = Intent(context, CoverActivity::class.java)
            .putExtra("message", message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val options = displayId?.let { ActivityOptions.makeBasic().setLaunchDisplayId(it).toBundle() }
        if (runCatching { context.startActivity(cover, options) }.isFailure) {
            runCatching { context.startActivity(cover) }
        }
        kotlinx.coroutines.withTimeoutOrNull(2000) { shown.await() }
        kotlinx.coroutines.delay(400)
        val am = context.getSystemService(android.app.ActivityManager::class.java)
        runCatching { am.killBackgroundProcesses(pkg) }
        kotlinx.coroutines.delay(400)
        runCatching { am.killBackgroundProcesses(pkg) }
        kotlinx.coroutines.delay(900)
        CoverActivity.shown = null
        // Dolphin is hidden behind the cover, so there's no way to see whether that worked.
        return CloseResult.UNVERIFIED
    }

    /**
     * Android 14+ no longer lets one app end another's process, and Dolphin only reads its touch-controls
     * setting when it starts. Leaving a game only takes Dolphin back to its game list, with the old setting
     * still in memory. So when the setting has to change, Dolphin's card is removed from Recent apps
     * (Android then ends it) before the game starts, whether or not a game was running.
     */
    private suspend fun restartForTouchSetting() {
        val want = overlayWanted ?: return
        if (android.os.Build.VERSION.SDK_INT < 34 || prefs.overlayInDolphin == want) return
        val pkg = installedPackage() ?: return
        val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault("Dolphin")
        val closed = runCatching { DolphinExitService.dismissFromRecents(label) }.getOrDefault(false)
        CrashLog.event("Dolphin restarted for touch controls ${if (want) "on" else "off"}: ${if (closed) "closed from Recent apps" else "no Recent apps card (not running?)"}")
        // Not found usually means Dolphin isn't running, so it reads the new setting when it starts.
        prefs.overlayInDolphin = want
        kotlinx.coroutines.delay(if (closed) 900 else 200)
    }

    /** Starts the game in a fresh Dolphin, so the newly written controls are always loaded. */
    suspend fun launchFresh(gameUri: Uri, displayId: Int?, resumeGameId: String? = null, title: String? = null, gameId: String? = null): Boolean {
        try {
            closeDolphin(displayId, "Starting game…")
            restartForTouchSetting()
            DolphinExitService.showLoading(
                LoadingOverlay.Phase.STARTING, title, gameId,
                if (resumeGameId != null) "Picking up where you left off…" else "Getting the game ready…", displayId,
            )
            if (!launch(gameUri, displayId)) return false
            val pkg = installedPackage()
            if (resumeGameId != null) autoResume(resumeGameId)
            else if (pkg != null && DolphinExitService.awaitGameScreen(pkg, 20_000)) kotlinx.coroutines.delay(800)
            // One screen: set Dolphin's touch buttons from its own menu too, while the loading screen still covers it.
            val want = overlayWanted
            if (pkg != null && want != null && DeviceInfo.oneScreen(context)) {
                if (resumeGameId == null) kotlinx.coroutines.delay(1500) // let the game finish booting
                CrashLog.event("touch buttons ${if (want) "on" else "off"} in Dolphin's menu: ${DolphinExitService.syncTouchButtons(pkg, want)}")
            }
            return true
        } finally {
            DolphinExitService.hideLoading(400)
        }
    }

    // ------------------------------------------------------------------ autosave / resume

    /** Turns on Dolphin's "Enable Savestates" (Config/Dolphin.ini [Core]) so its Quick Save/Load buttons exist. */
    /**
     * Trackpad presets: adds Delphis' trackpad server to Dolphin's DSU servers (Config/DSUClient.ini) and
     * turns Dolphin's own touch pointer off (Dolphin.ini), then starts the server. Dolphin reads both
     * files only when its app starts, so a running Dolphin picks them up after a force-stop.
     */
    private fun ensureTrackpadOverlay(tree: Uri) {
        DsuServer.ensureStarted()
        runCatching {
            var changed = false
            val ini = readText(tree, DolphinConfigWriter.dolphinIniPath) ?: ""
            DolphinConfigWriter.trackpadDolphinIni(ini).let { if (it != ini) { writeText(tree, DolphinConfigWriter.dolphinIniPath, it); changed = true } }
            val dsu = readText(tree, DolphinConfigWriter.DSU_CLIENT_INI) ?: ""
            DolphinConfigWriter.dsuClientIni(dsu).let { if (it != dsu) { writeText(tree, DolphinConfigWriter.DSU_CLIENT_INI, it); changed = true } }
            prefs.trackpadServerSetUp = true
            if (changed) CrashLog.event("trackpad: Dolphin set up to read Delphis' trackpad (DSU server 127.0.0.1:${io.github.hiramrg.delphis.core.Dsu.PORT})")
        }.onFailure { CrashLog.event("trackpad: couldn't set up Dolphin for the trackpad: ${it.message}") }
    }

    /** Config/DSUClient.ini, for Diagnostics. */
    fun dsuClientSettings(): String? {
        val tree = prefs.dolphinTreeUri ?: return null
        return readText(tree, DolphinConfigWriter.DSU_CLIENT_INI)?.lines()?.filter { it.isNotBlank() }?.joinToString("\n") { "  $it" } ?: "  (no DSUClient.ini)"
    }

    /**
     * Dolphin's own RetroAchievements switches (Config/RetroAchievements.ini [Achievements]), for
     * Diagnostics: whether it's on, signed in (only "yes/no", never the token) and Hardcore.
     */
    fun achievementSettings(): String {
        val tree = prefs.dolphinTreeUri ?: return "  (no Dolphin folder)"
        val text = runCatching { readText(tree, listOf("Config", "RetroAchievements.ini")) }.getOrNull()
            ?: return "  (no Config/RetroAchievements.ini: Dolphin has never been signed in to RetroAchievements here)"
        val ed = io.github.hiramrg.delphis.core.IniEditor(text)
        fun v(k: String) = ed.get("Achievements", k)
        return "  Enabled=${v("Enabled") ?: "(unset)"}, Hardcore=${v("HardcoreEnabled") ?: "(unset)"}, " +
            "Username=${v("Username")?.takeIf { it.isNotBlank() } ?: "(none)"}, signed in=${if (v("ApiToken").isNullOrBlank()) "no" else "yes"}"
    }

    data class RaStatus(val enabled: Boolean, val user: String?, val signedIn: Boolean)
    private val raIni = listOf("Config", "RetroAchievements.ini")

    /** Whether Dolphin is signed in to RetroAchievements (Config/RetroAchievements.ini). Null without the Dolphin folder. */
    fun raStatus(): RaStatus? {
        val tree = prefs.dolphinTreeUri ?: return null
        val text = runCatching { readText(tree, raIni) }.getOrNull() ?: return RaStatus(false, null, false)
        val ed = io.github.hiramrg.delphis.core.IniEditor(text)
        return RaStatus(
            ed.get("Achievements", "Enabled").equals("True", ignoreCase = true),
            ed.get("Achievements", "Username")?.takeIf { it.isNotBlank() },
            !ed.get("Achievements", "ApiToken").isNullOrBlank(),
        )
    }

    /**
     * Signs Dolphin in (or out, with a null token) by writing its own settings, exactly what Dolphin saves
     * after you sign in there. Dolphin reads this when it starts, and must not be running (it would save
     * its old settings over it), so the caller closes it first.
     */
    fun setDolphinRa(user: String?, token: String?): Boolean {
        val tree = prefs.dolphinTreeUri ?: return false
        return runCatching {
            val ed = io.github.hiramrg.delphis.core.IniEditor(readText(tree, raIni).orEmpty())
            if (token != null) ed.set("Achievements", linkedMapOf("Enabled" to "True", "Username" to (user ?: ""), "ApiToken" to token))
            else ed.set("Achievements", linkedMapOf("ApiToken" to ""))
            writeText(tree, raIni, ed.toString())
            CrashLog.event(if (token != null) "Dolphin signed in to RetroAchievements as $user" else "Dolphin signed out of RetroAchievements")
            true
        }.getOrDefault(false)
    }

    /** Ends Dolphin's process when no game is running, so it starts fresh with new settings. */
    fun stopIdleDolphin() {
        val pkg = installedPackage() ?: return
        runCatching { context.getSystemService(android.app.ActivityManager::class.java).killBackgroundProcesses(pkg) }
    }

    /** [Android] and [AndroidOverlayButtons] of Dolphin.ini, for Diagnostics. */
    fun dolphinTouchSettings(): String? {
        val tree = prefs.dolphinTreeUri ?: return null
        val text = readText(tree, DolphinConfigWriter.dolphinIniPath) ?: return "(no Dolphin.ini)"
        var section = ""
        return text.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.startsWith("[")) { section = t; null }
            else if (t.isNotEmpty() && section in setOf("[Android]", "[AndroidOverlayButtons]") &&
                (section == "[Android]" || t.startsWith("ButtonToggleWiimote") || t.startsWith("ButtonToggleNunchuk") || t.startsWith("DoubleTap"))) "  $section $t"
            else null
        }.joinToString("\n").ifEmpty { "  (none)" }
    }

    /** Keeps Dolphin's touch overlay off (see DolphinConfigWriter.touchOverlayOff); Dolphin picks it up when it next starts. */
    /** The touch-controls setting the next game should start with (see closeDolphin). */
    var overlayWanted: Boolean? = null

    fun ensureTouchOverlayOff() = setTouchOverlay(false)

    /** Dolphin's on-screen buttons: off on the Thor; on for one-screen devices with no controller connected. */
    fun setTouchOverlay(on: Boolean) {
        overlayWanted = on
        val tree = prefs.dolphinTreeUri ?: return
        runCatching {
            val existing = readText(tree, DolphinConfigWriter.dolphinIniPath) ?: ""
            val updated = DolphinConfigWriter.touchOverlay(existing, on)
            // The file disagreed: whatever Dolphin last saved there (its own menu writes it too) may be what it has
            // in memory, so the next start makes sure Dolphin is restarted.
            if (updated != existing) prefs.overlayInDolphin = null
            if (updated != existing) { writeText(tree, DolphinConfigWriter.dolphinIniPath, updated); CrashLog.event("Dolphin's touch overlay turned ${if (on) "on" else "off"}") }
        }.onFailure { CrashLog.event("couldn't change Dolphin's touch overlay: ${it.message}") }
    }

    /**
     * Dolphin's "Show on-screen messages" ([Interface] OnScreenDisplayMessages in Dolphin.ini, read when
     * Dolphin starts). Off hides Dolphin's achievement pop-up (and its other messages) so only Delphis'
     * badge shows. Turning it back on only happens if Delphis was the one that turned it off.
     */
    fun setDolphinMessages(hide: Boolean) {
        val tree = prefs.dolphinTreeUri ?: return
        if (!hide && !prefs.osdOffByDelphis) return
        runCatching {
            val existing = readText(tree, DolphinConfigWriter.dolphinIniPath) ?: ""
            val updated = io.github.hiramrg.delphis.core.IniEditor(existing).apply { set("Interface", mapOf("OnScreenDisplayMessages" to if (hide) "False" else "True")) }.toString()
            if (updated != existing) writeText(tree, DolphinConfigWriter.dolphinIniPath, updated)
            prefs.osdOffByDelphis = hide
        }.onFailure { CrashLog.event("couldn't change Dolphin's on-screen messages: ${it.message}") }
    }

    fun ensureSaveStatesEnabled() {
        val tree = prefs.dolphinTreeUri ?: return
        runCatching {
            val existing = readText(tree, DolphinConfigWriter.dolphinIniPath) ?: ""
            if (Regex("(?im)^\\s*EnableSaveStates\\s*=\\s*True\\s*$").containsMatchIn(existing)) return
            writeText(tree, DolphinConfigWriter.dolphinIniPath, DolphinConfigWriter.enableSaveStates(existing))
        }.onFailure { CrashLog.event("couldn't enable save states: ${it.message}") }
    }

    /** When the game's Quick Save state was last written, or null if there is none. */
    fun quickStateTime(gameId: String): Long? {
        val tree = prefs.dolphinTreeUri ?: return null
        return runCatching {
            val uri = resolve(tree, DolphinConfigWriter.quickStatePath(gameId), create = false) ?: return null
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && c.getLong(1) > 0) c.getLong(0) else null
            }
        }.getOrNull()
    }

    fun hasAutosave(gameId: String?): Boolean = gameId != null && quickStateTime(gameId) != null

    private suspend fun stateTime(gameId: String): Long? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { quickStateTime(gameId) }

    /** (time, size) of the game's Quick Save file. */
    private suspend fun stateInfo(gameId: String): Pair<Long, Long>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        latestAutosave(gameId)?.let { it.second to it.third }
    }

    /**
     * Dolphin writes a save in the background and keeps writing for a while after the file first changes (a
     * 37 MB save was copied at 21 MB on a Galaxy Tab): waits until its time and size stop changing (three
     * readings in a row, 1.2 s), at most [maxMs]. True when it settled.
     */
    private suspend fun waitUntilWritten(gameId: String, maxMs: Long): Boolean = kotlinx.coroutines.withTimeoutOrNull(maxMs) {
        var last: Pair<Long, Long>? = null
        var same = 0
        while (same < 3) {
            val now = stateInfo(gameId)
            same = if (now != null && now == last) same + 1 else 0
            last = now
            kotlinx.coroutines.delay(400)
        }
        true
    } ?: false

    /**
     * Presses Dolphin's Quick Save and waits (up to 10 s) for the state file to be written. True when it was.
     * [shot] is the save's picture; without one it's taken now ([capture]) while the game is on screen.
     * [closeMenu]: close Dolphin's menu afterwards (the game goes on); otherwise it's left open for what comes next.
     */
    private suspend fun autoSave(pkg: String, gameId: String, shot: android.graphics.Bitmap? = null, capture: Boolean = true, closeMenu: Boolean = false): Boolean {
        val picture = shot ?: if (capture) DolphinExitService.gamePicture() else null
        // A save made just before may still be being written: copy it only once it's whole.
        waitUntilWritten(gameId, 6_000)
        // Keep a copy of what's in the slot now, in case the new save turns out broken.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { backupCurrentAutosave(gameId) }
        val before = stateTime(gameId) ?: 0L
        val result = DolphinExitService.quickSave(pkg, closeMenu)
        lastSaveNote = when (result) {
            null -> "the helper isn't running"
            DolphinExitService.StateResult.NO_GAME -> "Dolphin's game wasn't found on screen"
            DolphinExitService.StateResult.FAILED -> "Dolphin's menu didn't open or Quick Save couldn't be pressed"
            DolphinExitService.StateResult.SAVE_STATES_OFF -> "Dolphin's save states are off"
            DolphinExitService.StateResult.DONE -> "Quick Save pressed"
        }
        when (result) {
            DolphinExitService.StateResult.DONE -> {
                prefs.saveStatesOff = false
                val saved = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                    while ((stateTime(gameId) ?: 0L) <= before) kotlinx.coroutines.delay(300)
                }?.let {
                    // Started; now wait until Dolphin has finished writing it (it goes on in the background).
                    if (!waitUntilWritten(gameId, 20_000)) CrashLog.event("autosave of $gameId: still changing after 20 s")
                    Unit
                }
                CrashLog.event(if (saved != null) "autosaved $gameId" else "autosave of $gameId not confirmed in 10 s")
                lastSaveNote = if (saved != null) "saved" + (if (picture == null) " (no picture)" else "") else "Quick Save pressed, but no file was written in 10 s"
                if (saved != null && picture != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    stateTime(gameId)?.let { storePicture(gameId, it, picture) }
                }
                return saved != null
            }
            DolphinExitService.StateResult.SAVE_STATES_OFF -> {
                prefs.saveStatesOff = true
                CrashLog.event("autosave skipped: Dolphin's Enable Savestates is off")
            }
            else -> Unit
        }
        return false
    }

    /** After launching: loads the game's Quick Save state once it has booted. */
    private suspend fun autoResume(gameId: String) {
        val pkg = installedPackage() ?: return
        ResumeNotice.failed.value = null
        // Right after Delphis is updated Android restarts its helper, and without it Quick Load can't be pressed:
        // wait for it (up to 15 s) instead of giving up.
        val helperAtStart = DolphinExitService.running
        if (!helperAtStart) kotlinx.coroutines.withTimeoutOrNull(15_000) { while (!DolphinExitService.running) kotlinx.coroutines.delay(300) }
        val savedAt = stateTime(gameId)
        val result = DolphinExitService.quickLoad(pkg)
        if (result == DolphinExitService.StateResult.SAVE_STATES_OFF) prefs.saveStatesOff = true
        if (result == DolphinExitService.StateResult.DONE) prefs.saveStatesOff = false
        CrashLog.event(
            "resume $gameId: ${result ?: "helper not running"} (autosave from " +
                (savedAt?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(it)) } ?: "none") +
                (if (helperAtStart) "" else if (DolphinExitService.running) ", waited for the helper" else ", helper never came back") + ")",
        )
        // Not loaded (and not because save states are off): the in-game menu offers to load it.
        if (savedAt != null && result != DolphinExitService.StateResult.DONE && result != DolphinExitService.StateResult.SAVE_STATES_OFF) {
            ResumeNotice.failed.value = ResumeNotice.Failed(gameId, savedAt, helperOff = result == null)
        }
    }

    /** "Load it" after a failed resume: presses Dolphin's Quick Load now (the game must be in front). */
    suspend fun loadAutosaveNow(gameId: String): Boolean {
        val pkg = installedPackage() ?: return false
        val result = DolphinExitService.quickLoad(pkg, bootMs = 0)
        CrashLog.event("load autosave of $gameId by hand: ${result ?: "helper not running"}")
        return result == DolphinExitService.StateResult.DONE
    }

    // ------------------------------------------------------------------ the Saves list

    enum class SaveKind { KEPT, LATEST, EARLIER, SLOT }

    /**
     * One entry of the Saves list: a save you kept ([name], [file], [keptId]), the latest autosave (Dolphin's Quick
     * Save slot), an earlier autosave kept by Delphis, or one of Dolphin's own save slots ([slot] 1–10).
     * [picture] is what was on screen when it was made (none for Dolphin's own slots, or before Android 11).
     */
    data class SaveEntry(
        val kind: SaveKind, val savedAt: Long, val size: Long, val slot: Int = 0, val backup: AutosaveBackup? = null, val uri: Uri? = null,
        val name: String? = null, val file: java.io.File? = null, val picture: java.io.File? = null, val keptId: Long = 0,
    )

    /** The game's StateSaves files: name → (uri, last modified, size). */
    private fun stateFiles(gameId: String): Map<String, Triple<Uri, Long, Long>> {
        val tree = prefs.dolphinTreeUri ?: return emptyMap()
        return runCatching {
            val dir = resolve(tree, listOf("StateSaves"), create = false) ?: return emptyMap()
            val dirId = DocumentsContract.getDocumentId(dir)
            val out = mutableMapOf<String, Triple<Uri, Long, Long>>()
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (!name.startsWith("$gameId.")) continue
                    if (c.getLong(3) <= 0) continue
                    out[name] = Triple(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), c.getLong(2), c.getLong(3))
                }
            }
            out
        }.getOrDefault(emptyMap())
    }

    /**
     * Everything the Saves list shows for the game, in its order: kept saves, the newest autosave, the earlier
     * ones, then Dolphin's slots. Call off the main thread.
     */
    fun saves(gameId: String?): List<SaveEntry> {
        if (gameId.isNullOrBlank()) return emptyList()
        val files = stateFiles(gameId)
        val out = mutableListOf<SaveEntry>()
        keptSaves(gameId).forEach { k -> out += SaveEntry(SaveKind.KEPT, k.savedAt, k.file.length(), name = k.name, file = k.file, picture = k.picture, keptId = k.id) }
        val picDir = pictureDir(gameId)
        val pics = picDir.list()?.toList().orEmpty()
        fun pic(t: Long) = SavePictures.nearest(pics, t)?.let { java.io.File(picDir, it) }
        val latest = files[DolphinConfigWriter.quickStatePath(gameId).last()]
        latest?.let { (uri, t, size) -> out += SaveEntry(SaveKind.LATEST, t, size, SaveSlots.QUICK, uri = uri, picture = pic(t)) }
        val backups = autosaveBackups(gameId)
        backups.forEach { out += SaveEntry(SaveKind.EARLIER, it.savedAt, it.size, backup = it, picture = pic(it.savedAt)) }
        // Pictures of autosaves that are gone (only when the slot could be read, so a missing folder deletes nothing).
        if (latest != null) SavePictures.unused(pics, backups.map { it.savedAt } + latest.second).forEach { java.io.File(picDir, it).delete() }
        SaveSlots.ownSlots(files.keys, gameId).forEach { (slot, name) ->
            val (uri, t, size) = files.getValue(name)
            out += SaveEntry(SaveKind.SLOT, t, size, slot, uri = uri)
        }
        return out
    }

    /** Writes [file] into the game's Quick Save slot; [picture] (the save's, as JPEG bytes) goes with it. */
    private fun writeQuickSlot(gameId: String, file: java.io.File, picture: ByteArray? = null) {
        val tree = prefs.dolphinTreeUri ?: error("no Dolphin folder")
        val uri = resolve(tree, DolphinConfigWriter.quickStatePath(gameId), create = true) ?: error("couldn't open the save slot")
        // Written twice if needed: a save Dolphin was still finishing could land on top of it.
        repeat(3) { attempt ->
            resolver.openOutputStream(uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out, 256 * 1024) } } ?: error("couldn't write the save slot")
            Thread.sleep(800)
            val size = latestAutosave(gameId)?.third
            if (size == file.length()) {
                if (attempt > 0) CrashLog.event("save slot of $gameId: Dolphin was still writing, wrote it again")
                if (picture != null) quickStateTime(gameId)?.let { writePicture(gameId, it, picture) }
                return
            }
        }
        error("Dolphin kept writing its own save over it")
    }

    /** A save's picture as bytes (read before its file can move or be pruned). */
    private fun pictureBytes(entry: SaveEntry): ByteArray? = runCatching { entry.picture?.takeIf { it.exists() }?.readBytes() }.getOrNull()

    /**
     * Game page, before Play: puts [entry] in the Quick Save slot so the game starts from it. The save that was
     * there is kept as an earlier autosave. Call off the main thread.
     */
    fun prepareSave(gameId: String, entry: SaveEntry): Boolean = when (entry.kind) {
        SaveKind.LATEST -> true
        SaveKind.EARLIER -> entry.backup?.let { restoreAutosave(gameId, it) } ?: false
        SaveKind.SLOT, SaveKind.KEPT -> runCatching {
            val picture = pictureBytes(entry)
            val staged = stage(gameId, entry)
            try { backupCurrentAutosave(gameId); writeQuickSlot(gameId, staged, picture) } finally { staged.delete() }
            CrashLog.event("${if (entry.kind == SaveKind.KEPT) "kept save" else "Dolphin slot ${entry.slot}"} of $gameId put in the Quick Save slot")
            true
        }.getOrElse { CrashLog.event("couldn't use ${if (entry.kind == SaveKind.KEPT) "a kept save" else "slot ${entry.slot}"} of $gameId: ${it.message}"); false }
    }

    /** A private copy of [entry] (an earlier autosave is moved, so keeping new backups can't delete it meanwhile). */
    private fun stage(gameId: String, entry: SaveEntry): java.io.File {
        val dir = backupDir(gameId).apply { mkdirs() }
        val staged = java.io.File(dir, "loading.bin")
        staged.delete()
        if (entry.kind == SaveKind.EARLIER) {
            val f = entry.backup?.file ?: error("no file")
            if (!f.renameTo(staged)) error("couldn't prepare the copy")
        } else if (entry.kind == SaveKind.KEPT) {
            (entry.file ?: error("no file")).copyTo(staged, overwrite = true)
        } else {
            val uri = entry.uri ?: error("no file")
            resolver.openInputStream(uri)?.use { input -> staged.outputStream().use { input.copyTo(it, 256 * 1024) } } ?: error("couldn't read the save")
        }
        return staged
    }

    /**
     * In game: loads [entry] right now. Your progress is autosaved first (so it becomes an earlier autosave you can
     * go back to), the chosen save goes into the Quick Save slot and Quick Load is pressed. Needs the helper.
     */
    suspend fun loadSaveInGame(gameId: String, entry: SaveEntry): Boolean {
        val pkg = installedPackage() ?: return false
        if (!DolphinExitService.running) { CrashLog.event("load save of $gameId: helper not running"); return false }
        val picture = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { pictureBytes(entry) }
        val staged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { stage(gameId, entry) }.getOrElse { CrashLog.event("load save of $gameId: ${it.message}"); null }
        } ?: return false
        try {
            autoSave(pkg, gameId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { backupCurrentAutosave(gameId); writeQuickSlot(gameId, staged, picture) }
        } catch (e: Exception) {
            CrashLog.event("load save of $gameId: ${e.message}")
            if (entry.kind == SaveKind.EARLIER) entry.backup?.file?.let { staged.renameTo(it) } // nothing is lost
            return false
        } finally {
            if (staged.exists() && entry.kind != SaveKind.EARLIER) staged.delete()
        }
        staged.delete()
        val result = DolphinExitService.quickLoad(pkg, bootMs = 0)
        val what = when (entry.kind) { SaveKind.KEPT -> "kept save"; SaveKind.LATEST -> "latest autosave"; SaveKind.EARLIER -> "earlier autosave"; SaveKind.SLOT -> "Dolphin slot ${entry.slot}" }
        CrashLog.event("load $what of $gameId in game: ${result ?: "helper not running"}")
        return result == DolphinExitService.StateResult.DONE
    }

    /**
     * In game: "Save now" (or the quick save buttons), a fresh autosave without leaving the game; Dolphin's menu
     * is closed again afterwards. [shot]: the picture, if it was already taken. Needs the helper.
     */
    suspend fun saveNow(gameId: String, shot: android.graphics.Bitmap? = null): Boolean {
        val pkg = installedPackage() ?: return false
        if (!DolphinExitService.running) return false
        return autoSave(pkg, gameId, shot, closeMenu = true)
    }

    // ------------------------------------------------------------------ for backups

    /** The game's latest autosave: (where, when, size), or null. Call off the main thread. */
    fun latestAutosave(gameId: String): Triple<Uri, Long, Long>? = stateFiles(gameId)[DolphinConfigWriter.quickStatePath(gameId).last()]

    /** The picture of the game's latest autosave, if there is one. */
    fun latestPicture(gameId: String): java.io.File? {
        val t = latestAutosave(gameId)?.second ?: return null
        val d = pictureDir(gameId)
        return SavePictures.nearest(d.list()?.toList().orEmpty(), t)?.let { java.io.File(d, it) }
    }

    /** Puts [file] (a restored autosave) into the game's Quick Save slot; what was there becomes an earlier autosave. */
    fun restoreAutosaveFile(gameId: String, file: java.io.File, picture: ByteArray?): Boolean = runCatching {
        backupCurrentAutosave(gameId)
        writeQuickSlot(gameId, file, picture)
        true
    }.getOrElse { CrashLog.event("couldn't restore the autosave of $gameId: ${it.message}"); false }

    /** Where kept saves live (for backups). */
    val keptSavesRoot: java.io.File get() = java.io.File(context.filesDir, "kept-saves")

    // ------------------------------------------------------------------ pictures

    private val picturesRoot get() = java.io.File(context.filesDir, "save-pictures")
    private fun pictureDir(gameId: String) = java.io.File(picturesRoot, gameId.filter { it.isLetterOrDigit() })

    /** Small JPEG of [bitmap], [SavePictures.WIDTH] wide. */
    private fun jpeg(bitmap: android.graphics.Bitmap): ByteArray {
        val w = SavePictures.WIDTH
        val small = if (bitmap.width > w) android.graphics.Bitmap.createScaledBitmap(bitmap, w, (bitmap.height * w / bitmap.width).coerceAtLeast(1), true) else bitmap
        return java.io.ByteArrayOutputStream().also { small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
    }

    private fun writePicture(gameId: String, savedAt: Long, bytes: ByteArray) {
        runCatching {
            val dir = pictureDir(gameId).apply { mkdirs() }
            val tmp = java.io.File(dir, "$savedAt.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(java.io.File(dir, SavePictures.fileName(savedAt)))) tmp.delete()
        }.onFailure { CrashLog.event("couldn't keep a save's picture: ${it.message}") }
    }

    /** Keeps [bitmap] as the picture of the game's save made at [savedAt]. Call off the main thread. */
    fun storePicture(gameId: String, savedAt: Long, bitmap: android.graphics.Bitmap) {
        runCatching { writePicture(gameId, savedAt, jpeg(bitmap)) }.onFailure { CrashLog.event("couldn't keep a save's picture: ${it.message}") }
    }

    // ------------------------------------------------------------------ kept saves

    /** A save you kept (see [KeptSaves]). */
    data class KeptSave(val id: Long, val file: java.io.File, val savedAt: Long, val name: String, val picture: java.io.File?)

    enum class KeepResult { KEPT, FULL, FAILED }

    private val keptRoot get() = java.io.File(context.filesDir, "kept-saves")
    private fun keptDir(gameId: String) = java.io.File(keptRoot, gameId.filter { it.isLetterOrDigit() })

    /** The game's kept saves, newest first. Call off the main thread. */
    fun keptSaves(gameId: String?): List<KeptSave> {
        if (gameId.isNullOrBlank()) return emptyList()
        val dir = keptDir(gameId)
        return (dir.list() ?: return emptyList()).mapNotNull { n ->
            val id = KeptSaves.id(n) ?: return@mapNotNull null
            val (t, name) = runCatching { KeptSaves.parseMeta(java.io.File(dir, "$id.txt").readText()) }.getOrNull() ?: return@mapNotNull null
            KeptSave(id, java.io.File(dir, n), t, name, java.io.File(dir, "$id.jpg").takeIf { it.exists() })
        }.sortedByDescending { it.savedAt }
    }

    /**
     * Keeps [entry] under [name]. An earlier autosave moves to the kept saves; the latest autosave and Dolphin's
     * slots are copied (they stay where they are). At most [KeptSaves.MAX] per game. Call off the main thread.
     */
    fun keepSave(gameId: String, entry: SaveEntry, name: String): KeepResult {
        if (entry.kind == SaveKind.KEPT) return if (renameKept(gameId, entry.keptId, name)) KeepResult.KEPT else KeepResult.FAILED
        val existing = keptSaves(gameId)
        if (existing.size >= KeptSaves.MAX) return KeepResult.FULL
        return runCatching {
            val dir = keptDir(gameId).apply { mkdirs() }
            val id = KeptSaves.newId(System.currentTimeMillis(), existing.map { it.id })
            val target = java.io.File(dir, "$id.state")
            val tmp = java.io.File(dir, "$id.tmp")
            val picture = pictureBytes(entry)
            when (entry.kind) {
                SaveKind.EARLIER -> { val f = entry.backup?.file ?: error("no file"); if (!f.renameTo(tmp)) f.copyTo(tmp, overwrite = true).also { f.delete() } }
                else -> resolver.openInputStream(entry.uri ?: error("no file"))?.use { input -> tmp.outputStream().use { input.copyTo(it, 256 * 1024) } } ?: error("couldn't read the save")
            }
            if (!tmp.renameTo(target)) { tmp.delete(); error("couldn't store it") }
            java.io.File(dir, "$id.txt").writeText(KeptSaves.meta(entry.savedAt, name))
            if (picture != null) java.io.File(dir, "$id.jpg").writeBytes(picture)
            CrashLog.event("kept a save of $gameId (${target.length() / 1024} KB)")
            KeepResult.KEPT
        }.getOrElse { CrashLog.event("couldn't keep a save of $gameId: ${it.message}"); KeepResult.FAILED }
    }

    fun renameKept(gameId: String, id: Long, name: String): Boolean = runCatching {
        val meta = java.io.File(keptDir(gameId), "$id.txt")
        val (t, _) = KeptSaves.parseMeta(meta.readText()) ?: error("not a kept save")
        meta.writeText(KeptSaves.meta(t, name))
        true
    }.getOrElse { CrashLog.event("couldn't rename a kept save of $gameId: ${it.message}"); false }

    fun deleteKept(gameId: String, id: Long): Boolean {
        val dir = keptDir(gameId)
        listOf("state", "txt", "jpg").forEach { java.io.File(dir, "$id.$it").delete() }
        CrashLog.event("deleted a kept save of $gameId")
        return !java.io.File(dir, "$id.state").exists()
    }

    // ------------------------------------------------------------------ earlier autosaves

    /** An earlier autosave kept in Delphis' own storage (see [AutosaveBackups]). */
    data class AutosaveBackup(val file: java.io.File, val savedAt: Long, val size: Long)

    private val backupsRoot get() = java.io.File(context.filesDir, "autosave-backups")
    private fun backupDir(gameId: String) = java.io.File(backupsRoot, gameId.filter { it.isLetterOrDigit() })

    /** The game's earlier autosaves, newest first. Call off the main thread. */
    fun autosaveBackups(gameId: String?): List<AutosaveBackup> {
        if (gameId.isNullOrBlank()) return emptyList()
        val dir = backupDir(gameId)
        val names = dir.list()?.toList() ?: return emptyList()
        return AutosaveBackups.newestFirst(names).map { n ->
            val f = java.io.File(dir, n)
            AutosaveBackup(f, AutosaveBackups.savedAt(n)!!, f.length())
        }
    }

    /**
     * Copies the game's current Quick Save state into Delphis' storage (unless that exact save is
     * already there) and keeps only the newest [AutosaveBackups.KEEP]. Call off the main thread.
     */
    fun backupCurrentAutosave(gameId: String): Boolean {
        val tree = prefs.dolphinTreeUri ?: return false
        return runCatching {
            val savedAt = quickStateTime(gameId) ?: return false
            val uri = resolve(tree, DolphinConfigWriter.quickStatePath(gameId), create = false) ?: return false
            val dir = backupDir(gameId).apply { mkdirs() }
            val target = java.io.File(dir, AutosaveBackups.fileName(savedAt))
            if (!target.exists()) {
                val tmp = java.io.File(dir, target.name + ".tmp")
                resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it, 256 * 1024) } }
                    ?: error("couldn't read the save state")
                if (!tmp.renameTo(target)) { tmp.delete(); error("couldn't store the copy") }
                CrashLog.event("kept earlier autosave of $gameId (${target.length() / 1024} KB)")
            }
            pruneBackups(gameId)
            true
        }.getOrElse {
            CrashLog.event("couldn't keep earlier autosave of $gameId: ${it.message}")
            false
        }
    }

    private fun pruneBackups(gameId: String) {
        val dir = backupDir(gameId)
        val names = dir.list()?.toList() ?: return
        names.filter { it.endsWith(".tmp") }.forEach { java.io.File(dir, it).delete() }
        AutosaveBackups.toDelete(names.filterNot { it.endsWith(".tmp") }).forEach { java.io.File(dir, it).delete() }
    }

    /**
     * Puts [backup] back in Dolphin's Quick Save slot, so the next Play continues from it. The save
     * that was in the slot becomes an earlier autosave itself, so restoring can be undone.
     * Call off the main thread.
     */
    fun restoreAutosave(gameId: String, backup: AutosaveBackup): Boolean {
        val tree = prefs.dolphinTreeUri ?: return false
        return runCatching {
            if (!backup.file.exists()) return false
            val picture = pictureDir(gameId).let { d -> SavePictures.nearest(d.list()?.toList().orEmpty(), backup.savedAt)?.let { runCatching { java.io.File(d, it).readBytes() }.getOrNull() } }
            // Hold the chosen copy aside first: keeping the current save could otherwise prune it.
            val chosen = java.io.File(backup.file.parentFile, "restoring.bin")
            if (!backup.file.renameTo(chosen)) error("couldn't prepare the copy")
            try {
                backupCurrentAutosave(gameId)
                val uri = resolve(tree, DolphinConfigWriter.quickStatePath(gameId), create = true) ?: error("couldn't open the save slot")
                resolver.openOutputStream(uri, "wt")?.use { out -> chosen.inputStream().use { it.copyTo(out, 256 * 1024) } }
                    ?: error("couldn't write the save slot")
                chosen.delete()
                if (picture != null) quickStateTime(gameId)?.let { writePicture(gameId, it, picture) }
            } catch (e: Exception) {
                chosen.renameTo(backup.file) // put it back so nothing is lost
                throw e
            }
            CrashLog.event("restored earlier autosave of $gameId from ${backup.savedAt}")
            true
        }.getOrElse {
            CrashLog.event("couldn't restore autosave of $gameId: ${it.message}")
            false
        }
    }

    /** Space used by all earlier autosaves, in bytes. */
    fun backupsSize(): Long = backupsRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun deleteAllBackups() { backupsRoot.deleteRecursively() }

    // ------------------------------------------------------------------ SAF helpers

    private fun rootDocId(tree: Uri) = DocumentsContract.getTreeDocumentId(tree)

    private fun findChild(tree: Uri, parentId: String, name: String): Pair<String, Boolean>? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return c.getString(0) to (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
            }
        }
        return null
    }

    /** Walks [path] (folders then file name) from the tree root. Creates missing pieces if [create]. */
    private fun resolve(tree: Uri, path: List<String>, create: Boolean): Uri? {
        var parent = rootDocId(tree)
        for ((i, part) in path.withIndex()) {
            val isLast = i == path.lastIndex
            val found = findChild(tree, parent, part)
            parent = when {
                found != null -> found.first
                !create -> return null
                else -> {
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parent)
                    val mime = if (isLast) "text/plain" else DocumentsContract.Document.MIME_TYPE_DIR
                    val created = DocumentsContract.createDocument(resolver, parentUri, mime, part)
                        ?: error("Couldn't create $part")
                    DocumentsContract.getDocumentId(created)
                }
            }
        }
        return DocumentsContract.buildDocumentUriUsingTree(tree, parent)
    }

    private fun readText(tree: Uri, path: List<String>): String? {
        val uri = resolve(tree, path, create = false) ?: return null
        return resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }

    private fun writeText(tree: Uri, path: List<String>, text: String) {
        val uri = resolve(tree, path, create = true) ?: error("Couldn't open ${path.joinToString("/")}")
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Couldn't write ${path.joinToString("/")}")
    }
}
