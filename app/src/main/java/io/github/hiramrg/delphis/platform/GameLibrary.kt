package io.github.hiramrg.delphis.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.hiramrg.delphis.core.ByteSource
import io.github.hiramrg.delphis.core.GameIdReader
import io.github.hiramrg.delphis.core.GameInfo
import io.github.hiramrg.delphis.core.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.ByteBuffer

data class GameEntry(
    val uri: Uri,
    val fileName: String,
    val size: Long,
    val lastModified: Long,
    val info: GameInfo,
) {
    /** Stable key for remembering the user's preset choice. */
    val key: String get() = info.gameId ?: fileName
    val isDolphinGame: Boolean get() = info.platform == Platform.WII || info.platform == Platform.GAMECUBE
}

/** Scans the user's games folder (granted via the folder picker) and reads each disc header. */
class GameLibrary(private val context: Context, private val prefs: Prefs) {

    /** Only GameCube and Wii games are ever shown; everything else in the folder is ignored. */
    fun cached(): List<GameEntry> = prefs.libraryCache?.let(::decode)?.filter { it.isDolphinGame }?.map(::withProperName) ?: emptyList()

    /** The game with its proper name (see [GameTitles]); only for display, the cache keeps the disc's name. */
    private fun withProperName(g: GameEntry) = g.copy(info = g.info.copy(title = GameTitles.titleFor(g.info)))

    suspend fun scan(onProgress: (Int) -> Unit = {}): List<GameEntry> = withContext(Dispatchers.IO) {
        val tree = prefs.gamesTreeUri ?: return@withContext emptyList()
        val previous = cached().associateBy { it.uri.toString() }
        val found = mutableListOf<GameEntry>()
        walk(tree, DocumentsContract.getTreeDocumentId(tree), depth = 0) { uri, name, size, modified ->
            val old = previous[uri.toString()]
            found += if (old != null && old.size == size && old.lastModified == modified) old
            else GameEntry(uri, name, size, modified, identify(uri, name))
            onProgress(found.size)
        }
        val sorted = found.sortedBy { it.info.title.lowercase() }
        prefs.libraryCache = encode(sorted) // non-games are cached too so rescans stay fast
        sorted.filter { it.isDolphinGame }.map(::withProperName)
    }

    /** Reads the disc header with random access (needed for WBFS/CISO/GCZ, where it isn't at offset 0). */
    private fun identify(uri: Uri, name: String): GameInfo = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                val source = ByteSource { offset, length ->
                    val buf = ByteBuffer.allocate(length)
                    var pos = offset
                    while (buf.hasRemaining()) {
                        val n = ch.read(buf, pos)
                        if (n <= 0) break
                        pos += n
                    }
                    buf.array().copyOf(buf.position())
                }
                GameIdReader.identify(source, name)
            }
        } ?: GameInfo(null, GameIdReader.titleFromFileName(name), Platform.UNKNOWN)
    } catch (_: Exception) {
        GameInfo(null, GameIdReader.titleFromFileName(name), Platform.UNKNOWN)
    }

    private fun walk(tree: Uri, docId: String, depth: Int, onFile: (Uri, String, Long, Long) -> Unit) {
        if (depth > 4) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val subdirs = mutableListOf<String>()
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.startsWith(".")) subdirs += id
                } else if (GameIdReader.isCandidateFile(name)) {
                    onFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, c.getLong(3), c.getLong(4))
                }
            }
        }
        subdirs.forEach { walk(tree, it, depth + 1, onFile) }
    }

    private fun encode(list: List<GameEntry>) = JSONArray().apply {
        list.forEach { e ->
            put(JSONObject().apply {
                put("uri", e.uri.toString()); put("name", e.fileName); put("size", e.size); put("mod", e.lastModified)
                put("id", e.info.gameId ?: JSONObject.NULL); put("title", e.info.title); put("platform", e.info.platform.name)
            })
        }
    }.toString()

    private fun decode(json: String): List<GameEntry> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GameEntry(
                Uri.parse(o.getString("uri")), o.getString("name"), o.getLong("size"), o.getLong("mod"),
                GameInfo(if (o.isNull("id")) null else o.getString("id"), o.getString("title"), Platform.valueOf(o.getString("platform"))),
            )
        }
    } catch (_: Exception) { emptyList() }
}
