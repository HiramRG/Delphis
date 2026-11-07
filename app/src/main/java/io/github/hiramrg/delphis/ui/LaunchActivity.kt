package io.github.hiramrg.delphis.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import io.github.hiramrg.delphis.core.GameDatabase
import io.github.hiramrg.delphis.core.GameIdReader
import io.github.hiramrg.delphis.core.PresetLibrary
import io.github.hiramrg.delphis.platform.DeviceInfo
import io.github.hiramrg.delphis.platform.DolphinBridge
import io.github.hiramrg.delphis.platform.GameEntry
import io.github.hiramrg.delphis.platform.GameLibrary
import io.github.hiramrg.delphis.platform.GameLauncher
import io.github.hiramrg.delphis.platform.Prefs
import io.github.hiramrg.delphis.platform.PresetStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entry point for frontends (RetroHrai!, Cocoon, iiSU, Daijishō, ES-DE, LaunchBox…). They start it
 * the same way they start Dolphin — a game as the intent's data, or as Dolphin's "AutoStartFile"
 * extra — and Delphis plays it with that game's controls and the bottom-screen map, skipping its
 * own menu. Closing the game with Home goes back to the frontend.
 *
 * Frontends use the short name `io.github.hiramrg.delphis/.Play` (an alias of this activity).
 * It has no screen of its own: it hands the game to [MainActivity.start] and closes.
 */
class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = gameFrom(intent)
        if (raw == null) {
            Toast.makeText(this, "Delphis didn't receive a game to play.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val uri = if (raw.startsWith("/")) Uri.fromFile(File(raw)) else Uri.parse(raw)
        val frontend = referrer?.takeIf { it.scheme == "android-app" }?.host
            ?.takeIf { it != packageName && !it.startsWith("org.dolphinemu") }
        val app = applicationContext
        val prefs = Prefs(app)
        val bridge = DolphinBridge(app, prefs)
        val name = displayName(uri)
        GameLauncher.run(app) {
            val entry = withContext(Dispatchers.IO) { resolve(app, prefs, uri, name) }
            if (!(bridge.installedPackage() != null && bridge.hasConfigAccess())) {
                // Not set up yet: start the game anyway, just without Delphis' controls.
                Toast.makeText(app, "Open Delphis once to set it up. Starting the game without its controls.", Toast.LENGTH_LONG).show()
                bridge.launch(entry.uri, DeviceInfo.screenRoles(app, prefs.swapScreens).topDisplayId)
                return@run
            }
            val store = PresetStore(app)
            val suggestion = GameDatabase.suggest(entry.info)
            val preset = prefs.presetFor(entry.key)?.let { store.byId(it) }
                ?: store.customFor(entry.key)
                ?: suggestion.choices.firstOrNull()
                ?: suggestion.alternatives.firstOrNull()
                ?: PresetLibrary.GAMECUBE
            prefs.markPlayed(entry.key)
            MenuCompanion.close()
            MainActivity.start(app, bridge, prefs, entry, preset, returnTo = frontend)
        }
        finish()
    }

    /** The game the frontend asked for: intent data, clip data, or one of the usual extras. */
    private fun gameFrom(i: Intent): String? {
        i.data?.let { return it.toString() }
        i.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.let { return it.toString() }
        val extras = i.extras ?: return null
        for (k in KEYS) {
            runCatching { extras.getString(k) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            runCatching { extras.getStringArray(k) }.getOrNull()?.firstOrNull { it.isNotBlank() }?.let { return it }
        }
        // Any other text extra that names a game file.
        for (k in extras.keySet()) {
            @Suppress("DEPRECATION")
            val v = extras.get(k) as? String ?: continue
            if (GameIdReader.isCandidateFile(v.substringAfterLast('/'))) return v
        }
        return null
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { return it }
                }
            }
        }
        val last = Uri.decode(uri.lastPathSegment ?: uri.toString())
        return last.substringAfterLast('/').substringAfterLast(':')
    }

    companion object {
        private val KEYS = listOf("AutoStartFile", "AutoStartFiles", "SelectedGames", "game", "rom", "ROM", "path", "file", "uri")

        /**
         * The game in Delphis' library when it's there (same file, or same file name), so remembered
         * controls and autosaves apply; otherwise it's read straight from what the frontend sent.
         */
        fun resolve(ctx: android.content.Context, prefs: Prefs, uri: Uri, name: String): GameEntry {
            val library = GameLibrary(ctx, prefs)
            val games = library.cached()
            games.firstOrNull { it.uri == uri }?.let { return it }
            games.firstOrNull { it.fileName.equals(name, ignoreCase = true) }?.let { return it }
            return GameEntry(uri, name, 0L, 0L, library.identify(uri, name))
        }
    }
}
