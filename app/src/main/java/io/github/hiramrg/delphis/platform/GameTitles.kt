package io.github.hiramrg.delphis.platform

import android.content.Context
import io.github.hiramrg.delphis.core.GameDatabase
import io.github.hiramrg.delphis.core.GameInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Proper game names. The name stored inside a disc is often a short internal one (Super Mario
 * Galaxy 2's disc says "SUPER MARIO GALAXY MORE"), so names come from, in order:
 *  1. Delphis' own game database (the games it has presets for),
 *  2. GameTDB's title list (the same site the covers come from), downloaded once and refreshed monthly,
 *  3. the disc's own name.
 */
object GameTitles {
    private const val URL_EN = "https://www.gametdb.com/wiitdb.txt?LANG=EN"
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private val known = ConcurrentHashMap<String, String>()

    private fun file(context: Context) = File(context.filesDir, "gametdb-titles.txt")

    fun titleFor(info: GameInfo): String =
        GameDatabase.titleFor(info) ?: info.gameId?.let { known[it.uppercase()] } ?: info.title

    /** Loads the names of [ids] from the downloaded list (if any). Call off the main thread. */
    fun load(context: Context, ids: Collection<String>) {
        val f = file(context)
        if (!f.exists() || ids.isEmpty()) return
        val wanted = ids.map { it.uppercase() }.toSet()
        runCatching {
            f.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val eq = line.indexOf(" = ")
                    if (eq != 6) continue // "RMGE01 = Super Mario Galaxy"
                    val id = line.substring(0, 6).uppercase()
                    if (id in wanted) known[id] = line.substring(eq + 3).trim()
                }
            }
        }.onFailure { CrashLog.event("couldn't read the game names list: ${it.message}") }
    }

    /** Downloads GameTDB's name list if it's missing or a month old. Returns true if it changed. Call off the main thread. */
    fun refresh(context: Context, ids: Collection<String>): Boolean {
        val f = file(context)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS) return false
        return runCatching {
            val conn = (URL(URL_EN).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 20000
                setRequestProperty("User-Agent", "Delphis/1.0")
            }
            if (conn.responseCode != 200) return false
            val tmp = File(f.path + ".part")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(f)
            load(context, ids)
            true
        }.getOrElse { CrashLog.event("couldn't download the game names list: ${it.message}"); false }
    }
}
