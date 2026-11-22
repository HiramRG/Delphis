package io.github.hiramrg.delphis.platform

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import io.github.hiramrg.delphis.core.BackupRules
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * One file with what Delphis keeps (Settings → Backup): presets, kept saves, the latest autosave of each game,
 * settings, favorites and play time. A zip with `manifest.json` first, then the parts. Never in it: the
 * RetroAchievements sign-in, folder permissions (they only work on the device that gave them), the games.
 */
object Backup {
    enum class Part(val label: String) {
        PRESETS("Presets and your edits"), KEPT("Kept saves, with pictures"), AUTOSAVES("Latest autosave of each game"),
        SETTINGS("Settings"), PLAY("Favorites and play time"),
    }

    /** What a part holds right now (or in a backup): "14 presets", its size, whether there's anything. */
    data class PartInfo(val part: Part, val detail: String, val bytes: Long, val count: Int)

    /** What a backup file holds. */
    data class Preview(val madeAt: Long, val device: String, val build: Int, val parts: List<PartInfo>)

    private const val FORMAT = 1
    private fun main(ctx: Context) = ctx.getSharedPreferences("thor_companion", Context.MODE_PRIVATE)
    private fun presets(ctx: Context) = ctx.getSharedPreferences("custom_presets", Context.MODE_PRIVATE)

    fun fileName(at: Long = System.currentTimeMillis()) =
        "Delphis backup " + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(at)) + ".zip"

    private fun sizeText(b: Long) = io.github.hiramrg.delphis.ui.formatSize(b)
    private fun dirSize(f: java.io.File): Long = f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** What each part would hold now. [gameIds]: the library's games (for their autosaves). Call off the main thread. */
    fun summary(ctx: Context, bridge: DolphinBridge, gameIds: List<String>): List<PartInfo> {
        val p = presets(ctx).all
        val kept = bridge.keptSavesRoot
        val keptCount = kept.walkTopDown().count { it.isFile && it.name.endsWith(".state") }
        val keptGames = kept.listFiles()?.count { d -> d.listFiles()?.any { it.name.endsWith(".state") } == true } ?: 0
        val autos = gameIds.mapNotNull { bridge.latestAutosave(it) }
        val settings = main(ctx).all.filterKeys { BackupRules.isSetting(it) }
        val play = main(ctx).all.filterKeys { it in BackupRules.PLAY_KEYS }
        return listOf(
            PartInfo(Part.PRESETS, "${p.size} preset${if (p.size == 1) "" else "s"}", p.values.sumOf { it.toString().length.toLong() }, p.size),
            PartInfo(Part.KEPT, "$keptCount save${if (keptCount == 1) "" else "s"} of $keptGames game${if (keptGames == 1) "" else "s"}", dirSize(kept), keptCount),
            PartInfo(Part.AUTOSAVES, "${autos.size} game${if (autos.size == 1) "" else "s"}", autos.sumOf { it.third }, autos.size),
            PartInfo(Part.SETTINGS, "controllers, screens, looks, per-game choices", settings.values.sumOf { it.toString().length.toLong() }, settings.size),
            PartInfo(Part.PLAY, "favorites, play time, last played", play.values.sumOf { it.toString().length.toLong() }, play.size),
        ).map { it.copy(detail = it.detail + " · " + sizeText(it.bytes)) }
    }

    private fun prefsJson(sp: SharedPreferences, keep: (String) -> Boolean): JSONObject {
        val o = JSONObject()
        sp.all.forEach { (k, v) ->
            if (!keep(k) || v == null) return@forEach
            val e = JSONObject()
            when (v) {
                is String -> { e.put("t", "s"); e.put("v", v) }
                is Boolean -> { e.put("t", "b"); e.put("v", v) }
                is Int -> { e.put("t", "i"); e.put("v", v) }
                is Long -> { e.put("t", "l"); e.put("v", v) }
                is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                is Set<*> -> { e.put("t", "set"); e.put("v", JSONArray(v.map { it.toString() })) }
                else -> return@forEach
            }
            o.put(k, e)
        }
        return o
    }

    /** Puts one saved value back. */
    private fun SharedPreferences.Editor.putJson(k: String, e: JSONObject) {
        when (e.optString("t")) {
            "s" -> putString(k, e.getString("v"))
            "b" -> putBoolean(k, e.getBoolean("v"))
            "i" -> putInt(k, e.getInt("v"))
            "l" -> putLong(k, e.getLong("v"))
            "f" -> putFloat(k, e.getDouble("v").toFloat())
            "set" -> putStringSet(k, e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
        }
    }

    /** Writes the backup to [out]. Returns its parts with what went in. Call off the main thread. */
    fun write(ctx: Context, out: java.io.OutputStream, parts: Set<Part>, bridge: DolphinBridge, gameIds: List<String>, build: Int): List<PartInfo> {
        val infos = summary(ctx, bridge, gameIds).filter { it.part in parts }
        val autosaves = JSONObject()
        ZipOutputStream(out.buffered()).use { zip ->
            fun text(name: String, s: String) { zip.putNextEntry(ZipEntry(name)); zip.write(s.toByteArray()); zip.closeEntry() }
            val manifest = JSONObject().apply {
                put("app", "Delphis"); put("format", FORMAT); put("build", build); put("made", System.currentTimeMillis())
                put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim())
                put("parts", JSONObject().apply { infos.forEach { put(it.part.name, JSONObject().put("detail", it.detail).put("bytes", it.bytes).put("count", it.count)) } })
            }
            text("manifest.json", manifest.toString(1))
            if (Part.PRESETS in parts) text("presets.json", prefsJson(presets(ctx)) { true }.toString())
            if (Part.SETTINGS in parts) text("settings.json", prefsJson(main(ctx)) { BackupRules.isSetting(it) }.toString())
            if (Part.PLAY in parts) text("play.json", prefsJson(main(ctx)) { it in BackupRules.PLAY_KEYS }.toString())
            if (Part.KEPT in parts) {
                text("kept-names.json", prefsJson(main(ctx)) { BackupRules.isKeptName(it) }.toString())
                bridge.keptSavesRoot.listFiles()?.forEach { dir ->
                    dir.listFiles()?.forEach { f ->
                        val entry = "kept-saves/${dir.name}/${f.name}"
                        if (BackupRules.keptPath(entry) != null) { zip.putNextEntry(ZipEntry(entry)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                    }
                }
            }
            if (Part.AUTOSAVES in parts) gameIds.distinct().forEach { id ->
                val (uri, t, _) = bridge.latestAutosave(id) ?: return@forEach
                val safe = BackupRules.safe(id)
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        zip.putNextEntry(ZipEntry("autosaves/$safe.sav")); input.copyTo(zip); zip.closeEntry()
                        autosaves.put(safe, JSONObject().put("id", id).put("savedAt", t))
                    }
                    bridge.latestPicture(id)?.let { pic -> zip.putNextEntry(ZipEntry("autosaves/$safe.jpg")); pic.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                }.onFailure { CrashLog.event("backup: couldn't copy the autosave of $id: ${it.message}") }
            }
            if (Part.AUTOSAVES in parts) text("autosaves.json", autosaves.toString())
        }
        CrashLog.event("backup made: ${infos.joinToString { it.part.name }}")
        return infos
    }

    /** What the backup at [uri] holds, or null when it isn't one of Delphis'. Call off the main thread. */
    fun preview(ctx: Context, uri: Uri): Preview? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                val first = zip.nextEntry ?: return null
                if (first.name != "manifest.json") return null
                val m = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                if (m.optString("app") != "Delphis" || m.optInt("format") > FORMAT) return null
                val parts = m.optJSONObject("parts") ?: JSONObject()
                Preview(
                    m.optLong("made"), m.optString("device"), m.optInt("build"),
                    Part.entries.mapNotNull { p -> parts.optJSONObject(p.name)?.let { PartInfo(p, it.optString("detail"), it.optLong("bytes"), it.optInt("count")) } },
                )
            }
        }
    }.getOrNull()

    /**
     * Restores [parts] from the backup at [uri]. [replace]: when something is both here and in the backup, the
     * backup's wins (otherwise what's here stays). Favorites and play time are always merged. Returns a summary
     * in words. Call off the main thread.
     */
    fun restore(ctx: Context, uri: Uri, parts: Set<Part>, replace: Boolean, bridge: DolphinBridge): String {
        val done = mutableListOf<String>()
        var presetsN = 0; var settingsN = 0; var keptN = 0; var autosN = 0; var skipped = 0
        val tmp = java.io.File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val autoInfo = mutableMapOf<String, Pair<String, Long>>()
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val name = e.name
                        when {
                            name == "presets.json" && Part.PRESETS in parts -> {
                                val o = JSONObject(zip.readBytes().toString(Charsets.UTF_8)); val sp = presets(ctx); val ed = sp.edit()
                                o.keys().forEach { k -> if (replace || !sp.contains(k)) { ed.putJson(k, o.getJSONObject(k)); presetsN++ } }
                                ed.apply()
                            }
                            name == "settings.json" && Part.SETTINGS in parts -> {
                                val o = JSONObject(zip.readBytes().toString(Charsets.UTF_8)); val sp = main(ctx); val ed = sp.edit()
                                o.keys().forEach { k -> if (BackupRules.isSetting(k) && (replace || !sp.contains(k))) { ed.putJson(k, o.getJSONObject(k)); settingsN++ } }
                                ed.apply()
                            }
                            name == "play.json" && Part.PLAY in parts -> {
                                val o = JSONObject(zip.readBytes().toString(Charsets.UTF_8)); val sp = main(ctx); val ed = sp.edit()
                                o.keys().forEach { k ->
                                    val v = o.getJSONObject(k)
                                    when {
                                        k == "favorites" -> ed.putStringSet(k, (sp.getStringSet(k, emptySet()).orEmpty() + v.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) } }).toSet())
                                        k in BackupRules.PLAY_KEYS && v.optString("t") == "s" -> ed.putString(k, BackupRules.mergeMax(sp.getString(k, null), v.getString("v")))
                                    }
                                }
                                ed.apply(); done += "favorites and play time"
                            }
                            name == "kept-names.json" && Part.KEPT in parts -> {
                                val o = JSONObject(zip.readBytes().toString(Charsets.UTF_8)); val sp = main(ctx); val ed = sp.edit()
                                o.keys().forEach { k -> if (BackupRules.isKeptName(k)) ed.putString(k, BackupRules.mergeNames(sp.getString(k, null), o.getJSONObject(k).optString("v"))) }
                                ed.apply()
                            }
                            name.startsWith("kept-saves/") && Part.KEPT in parts -> {
                                val (game, file) = BackupRules.keptPath(name) ?: continue
                                val target = java.io.File(java.io.File(bridge.keptSavesRoot, game).apply { mkdirs() }, file)
                                if (replace || !target.exists()) { target.outputStream().use { zip.copyTo(it) }; if (file.endsWith(".state")) keptN++ }
                            }
                            name == "autosaves.json" && Part.AUTOSAVES in parts -> {
                                val o = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                                o.keys().forEach { k -> o.optJSONObject(k)?.let { autoInfo[k] = it.optString("id") to it.optLong("savedAt") } }
                            }
                            name.startsWith("autosaves/") && Part.AUTOSAVES in parts -> {
                                val f = name.removePrefix("autosaves/")
                                if (Regex("^[A-Za-z0-9]+\\.(sav|jpg)$").matches(f)) java.io.File(tmp, f).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                }
            } ?: error("couldn't open the file")
            // Autosaves last (their list comes after the files): into Dolphin's Quick Save slot of each game.
            autoInfo.forEach { (safe, info) ->
                val (id, _) = info
                val f = java.io.File(tmp, "$safe.sav")
                if (id.isBlank() || !f.exists()) return@forEach
                if (!replace && bridge.latestAutosave(id) != null) { skipped++; return@forEach }
                val pic = java.io.File(tmp, "$safe.jpg").takeIf { it.exists() }?.readBytes()
                if (bridge.restoreAutosaveFile(id, f, pic)) autosN++
            }
        } finally { tmp.deleteRecursively() }
        if (Part.PRESETS in parts) done.add(0, "$presetsN preset${if (presetsN == 1) "" else "s"}")
        if (Part.KEPT in parts) done += "$keptN kept save${if (keptN == 1) "" else "s"}"
        if (Part.AUTOSAVES in parts) done += "$autosN autosave${if (autosN == 1) "" else "s"}" + (if (skipped > 0) " ($skipped kept as they were)" else "")
        if (Part.SETTINGS in parts) done += "$settingsN setting${if (settingsN == 1) "" else "s"}"
        CrashLog.event("backup restored: ${done.joinToString()}")
        return done.joinToString(", ")
    }
}
