package io.github.hiramrg.delphis.platform

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import io.github.hiramrg.delphis.core.ScreenshotNames

/**
 * Screenshots of the game, saved to the phone's Photos in Pictures/Delphis/<game>. Delphis only ever sees the
 * pictures it saved itself (no photo permission needed).
 */
object Screenshots {
    data class Shot(val uri: Uri, val takenAt: Long)

    private val collection: Uri get() = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** Without the black bars at the sides (when there are any). */
    fun gameOnly(b: Bitmap): Bitmap {
        val w = b.width; val h = b.height
        if (w < 10 || h < 10) return b
        val dark = BooleanArray(w)
        val col = IntArray(h)
        for (x in 0 until w) {
            b.getPixels(col, 0, 1, x, 0, 1, h)
            var black = true
            var y = 0
            while (y < h) {
                val p = col[y]
                if (((p shr 16) and 0xFF) > 14 || ((p shr 8) and 0xFF) > 14 || (p and 0xFF) > 14) { black = false; break }
                y += 6
            }
            dark[x] = black
        }
        val keep = ScreenshotNames.gameColumns(dark)
        if (keep.isEmpty() || (keep.first == 0 && keep.last == w - 1)) return b
        return Bitmap.createBitmap(b, keep.first, 0, keep.last - keep.first + 1, h)
    }

    /** Saves [b] as a PNG; returns where, or null. Call off the main thread. */
    fun save(context: Context, b: Bitmap, title: String?): Uri? = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, ScreenshotNames.fileName(title, java.util.Date()))
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, ScreenshotNames.relativePath(title))
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("no place to save it")
        try {
            resolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("couldn't write it")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) { resolver.delete(uri, null, null); throw t }
        CrashLog.event("screenshot saved (${b.width}×${b.height})")
        uri
    }.getOrElse { CrashLog.event("screenshot not saved: ${it.message}"); null }

    /** The game's screenshots, newest first. Call off the main thread. */
    fun list(context: Context, title: String?): List<Shot> = runCatching {
        context.contentResolver.query(
            collection, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED),
            "${MediaStore.Images.Media.RELATIVE_PATH} = ?", arrayOf(ScreenshotNames.relativePath(title)),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            buildList { while (c.moveToNext()) add(Shot(android.content.ContentUris.withAppendedId(collection, c.getLong(0)), c.getLong(1) * 1000)) }
        }.orEmpty()
    }.getOrElse { emptyList() }

    fun thumbnail(context: Context, uri: Uri): Bitmap? =
        runCatching { context.contentResolver.loadThumbnail(uri, android.util.Size(320, 180), null) }.getOrNull()

    fun delete(context: Context, uri: Uri): Boolean = runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
}
