package io.github.hiramrg.delphis.platform

import android.app.ActivityOptions
import android.content.ClipData
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
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
            prefs.lastApplied = "$gameId|${preset.id}"
            ApplyResult.Ok
        } catch (e: Exception) {
            ApplyResult.Failed(e.message ?: e.javaClass.simpleName)
        }
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
    fun applyGlobally(preset: Preset, controller: ControllerProfile): ApplyResult {
        val tree = prefs.dolphinTreeUri ?: return ApplyResult.Failed("Dolphin's folder hasn't been connected yet.")
        return try {
            writeText(tree, DolphinConfigWriter.profilePath(preset), DolphinConfigWriter.renderProfile(preset, controller))
            val path = DolphinConfigWriter.globalConfigPath(preset)
            val existing = readText(tree, path) ?: ""
            writeText(tree, path, DolphinConfigWriter.mergeGlobalConfig(existing, preset, controller))
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
        val intent = Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(pkg, MAIN_ACTIVITY)
            setDataAndType(gameUri, "application/octet-stream")
            clipData = ClipData.newRawUri("game", gameUri)
            putExtra("AutoStartFile", gameUri.toString())
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        runCatching { context.grantUriPermission(pkg, gameUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
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
            setDataAndType(gameUri, "application/octet-stream")
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
        // Autosave first, while the game is still running.
        GameWatch.session?.info?.gameId?.let { id -> if (prefs.autoSaveResume) autoSave(pkg, id) }
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

    /** Starts the game in a fresh Dolphin, so the newly written controls are always loaded. */
    suspend fun launchFresh(gameUri: Uri, displayId: Int?, resumeGameId: String? = null): Boolean {
        closeDolphin(displayId, "Starting game…")
        if (!launch(gameUri, displayId)) return false
        if (resumeGameId != null) autoResume(resumeGameId)
        return true
    }

    // ------------------------------------------------------------------ autosave / resume

    /** Turns on Dolphin's "Enable Savestates" (Config/Dolphin.ini [Core]) so its Quick Save/Load buttons exist. */
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

    /** Presses Dolphin's Quick Save and waits (up to 10 s) for the state file to be written. */
    private suspend fun autoSave(pkg: String, gameId: String) {
        val before = stateTime(gameId) ?: 0L
        when (DolphinExitService.quickSave(pkg)) {
            DolphinExitService.StateResult.DONE -> {
                prefs.saveStatesOff = false
                val saved = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                    while ((stateTime(gameId) ?: 0L) <= before) kotlinx.coroutines.delay(300)
                    // Written; give Dolphin a moment to finish and close the file.
                    kotlinx.coroutines.delay(500)
                }
                CrashLog.event(if (saved != null) "autosaved $gameId" else "autosave of $gameId not confirmed in 10 s")
            }
            DolphinExitService.StateResult.SAVE_STATES_OFF -> {
                prefs.saveStatesOff = true
                CrashLog.event("autosave skipped: Dolphin's Enable Savestates is off")
            }
            else -> Unit
        }
    }

    /** After launching: loads the game's Quick Save state once it has booted. */
    private suspend fun autoResume(gameId: String) {
        val pkg = installedPackage() ?: return
        val result = DolphinExitService.quickLoad(pkg)
        if (result == DolphinExitService.StateResult.SAVE_STATES_OFF) prefs.saveStatesOff = true
        if (result == DolphinExitService.StateResult.DONE) prefs.saveStatesOff = false
        CrashLog.event("resume $gameId: $result")
    }

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
