package io.github.hiramrg.delphis.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import io.github.hiramrg.delphis.core.GameIdReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Box art from GameTDB (the same source and URL scheme Dolphin uses), cached on disk so each cover
 * is downloaded once. Games without a cover are remembered so we don't retry on every scroll.
 */
class CoverArt(context: Context) {
    // "covers-hq": high-resolution covers. The old "covers" folder (low-res) is removed once.
    private val dir = File(context.filesDir, "covers-hq").apply { mkdirs() }

    init {
        // Older versions marked a game as "no cover" after any failed download, even a temporary
        // network error, and never tried again. Clear those markers once so every game gets another try.
        val prefs = context.getSharedPreferences("covers", Context.MODE_PRIVATE)
        File(context.filesDir, "covers").takeIf { it.exists() }?.deleteRecursively()
        if (!prefs.getBoolean("retry_v2", false)) {
            dir.listFiles { f -> f.name.endsWith(".none") }?.forEach { it.delete() }
            prefs.edit().putBoolean("retry_v2", true).apply()
        }
    }
    private val network = Mutex()

    companion object {
        /**
         * Decoded covers, shared by every screen, limited by size (about 1/8 of the app's memory);
         * dropped when Android needs the memory (e.g. during a game).
         */
        private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }
        fun clearMemory() = memory.evictAll()
    }

    suspend fun load(gameId: String): Bitmap? {
        memory.get(gameId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(dir, "$gameId.png")
            val missing = File(dir, "$gameId.none")
            // A "no cover" result is retried after a week, in case GameTDB adds it.
            if (missing.exists() && System.currentTimeMillis() - missing.lastModified() > 7L * 24 * 3600 * 1000) missing.delete()
            if (!file.exists() && !missing.exists()) {
                // One download at a time keeps the library responsive and is polite to GameTDB.
                network.withLock { if (!file.exists()) download(gameId, file, missing) }
            }
            if (!file.exists()) return@withContext null
            // Full resolution: the HQ covers are ~350x500, about what a grid tile shows on the Thor.
            BitmapFactory.decodeFile(file.path)?.also { memory.put(gameId, it) }
        }
    }

    /**
     * Tries every region's cover, then the full and 3D box art. Only when GameTDB answers "not found"
     * for all of them is the game marked as having no cover; any other failure (offline, timeout,
     * server error) just tries again next time.
     */
    private fun download(gameId: String, file: File, missing: File) {
        for (url in GameIdReader.coverUrls(gameId)) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000; readTimeout = 8000
                    setRequestProperty("User-Agent", "Delphis/1.0")
                }
                val code = conn.responseCode
                if (code == 200) {
                    val tmp = File(file.path + ".part")
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    if (tmp.length() > 0) { tmp.renameTo(file); return }
                    tmp.delete()
                }
                conn.disconnect()
                if (code != 404) return // not a definite "no cover": try again next time
            } catch (_: Exception) {
                return // offline: try again next time
            }
        }
        missing.createNewFile()
    }
}
