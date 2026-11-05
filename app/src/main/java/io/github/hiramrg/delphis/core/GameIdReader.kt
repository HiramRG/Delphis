package io.github.hiramrg.delphis.core

import java.util.zip.Inflater

/** Random-access reader over a game file. Returns fewer bytes than asked at end of file. */
fun interface ByteSource {
    fun read(offset: Long, length: Int): ByteArray
}

fun ByteArray.asByteSource() = ByteSource { offset, length ->
    if (offset >= size) ByteArray(0) else copyOfRange(offset.toInt(), minOf(size, offset.toInt() + length))
}

/**
 * Identifies a GameCube/Wii game from its file, without decompressing the whole thing.
 *
 * A file only counts as a game when its disc header carries the Wii magic (0x5D1C9EA3 at 0x18) or
 * the GameCube magic (0xC2339F3D at 0x1C), so PS2 ISOs and other look-alikes are filtered out.
 *
 *  - ISO / GCM / NKit: disc header at offset 0
 *  - WBFS: disc header at 0x200
 *  - RVZ / WIA: 0x48-byte header 1, then header 2 = 4 u32s + raw disc header, so it sits at 0x58
 *  - CISO: 0x8000-byte header, then block 0
 *  - GCZ: zlib-compressed blocks; block 0 is inflated to read the header
 *  - WAD (Wii channels): recognised by its "Is"/"ib" header type; title from the file name
 */
object GameIdReader {
    private const val WII_MAGIC = 0x5D1C9EA3
    private const val GC_MAGIC = 0xC2339F3D.toInt()

    val SUPPORTED_EXTENSIONS = setOf("iso", "gcm", "rvz", "wia", "wbfs", "ciso", "gcz", "nkit.iso", "wad", "tgc")

    fun isCandidateFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return SUPPORTED_EXTENSIONS.any { lower.endsWith(".$it") }
    }

    /** Convenience for tests: identify from an in-memory copy of the start of the file. */
    fun identify(header: ByteArray, fileName: String): GameInfo = identify(header.asByteSource(), fileName)

    fun identify(src: ByteSource, fileName: String): GameInfo {
        val head = src.read(0, 0x80)
        if (head.size < 8) return notAGame(fileName)
        val magic = String(head, 0, 4, Charsets.ISO_8859_1)

        val disc: ByteArray? = when {
            magic == "WBFS" -> src.read(0x200, 0x80)
            magic == "RVZ\u0001" || magic == "WIA\u0001" -> src.read(0x58, 0x80)
            magic == "CISO" -> src.read(0x8000, 0x80)
            isGcz(head) -> runCatching { gczFirstBytes(src, head) }.getOrNull()
            else -> head
        }

        if (disc != null && disc.size >= 0x20) {
            val platform = when {
                readBigEndianInt(disc, 0x18) == WII_MAGIC -> Platform.WII
                readBigEndianInt(disc, 0x1C) == GC_MAGIC -> Platform.GAMECUBE
                else -> null
            }
            if (platform != null) {
                val id = String(disc, 0, 6, Charsets.ISO_8859_1).takeIf { ID_PATTERN.matches(it) }
                val title = readTitle(disc)?.takeIf { it.isNotBlank() } ?: titleFromFileName(fileName)
                return GameInfo(id, title, platform)
            }
        }

        // Wii channel WADs: header size 0x20, then type "Is\0\0" (installable) or "ib\0\0" (boot2).
        if (fileName.lowercase().endsWith(".wad") && readBigEndianInt(head, 0) == 0x20) {
            val type = String(head, 4, 2, Charsets.ISO_8859_1)
            if (type == "Is" || type == "ib") return GameInfo(null, titleFromFileName(fileName), Platform.WII)
        }
        return notAGame(fileName)
    }

    private fun notAGame(fileName: String) = GameInfo(null, titleFromFileName(fileName), Platform.UNKNOWN)

    private fun isGcz(h: ByteArray) =
        h[0] == 0x01.toByte() && h[1] == 0xC0.toByte() && h[2] == 0x0B.toByte() && h[3] == 0xB1.toByte()

    /** Reads + inflates GCZ block 0 (Dolphin's CompressedBlobReader format, little-endian header). */
    private fun gczFirstBytes(src: ByteSource, head: ByteArray): ByteArray? {
        val compressedSize = readLittleEndianLong(head, 8)
        val numBlocks = readLittleEndianInt(head, 28)
        if (numBlocks <= 0) return null
        val pointers = src.read(32, 16)
        val p0raw = readLittleEndianLong(pointers, 0)
        val uncompressed = p0raw < 0 // top bit set
        val p0 = p0raw and Long.MAX_VALUE
        val p1 = if (numBlocks > 1) readLittleEndianLong(pointers, 8) and Long.MAX_VALUE else compressedSize
        val dataStart = 32L + numBlocks.toLong() * 12
        val len = (p1 - p0).coerceIn(0, 1 shl 20).toInt()
        val block = src.read(dataStart + p0, len)
        if (uncompressed) return block.copyOf(minOf(block.size, 0x80))
        val inflater = Inflater()
        return try {
            inflater.setInput(block)
            val out = ByteArray(0x80)
            var n = 0
            while (n < out.size && !inflater.finished()) {
                val r = inflater.inflate(out, n, out.size - n)
                if (r == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                n += r
            }
            out.copyOf(n)
        } finally {
            inflater.end()
        }
    }

    private fun readTitle(h: ByteArray): String? {
        val start = 0x20
        if (h.size <= start) return null
        val end = minOf(h.size, start + 0x40)
        var len = 0
        while (start + len < end && h[start + len] != 0.toByte()) len++
        return String(h, start, len, Charsets.UTF_8).trim()
    }

    private fun readBigEndianInt(h: ByteArray, o: Int): Int? {
        if (h.size < o + 4) return null
        return ((h[o].toInt() and 0xFF) shl 24) or ((h[o + 1].toInt() and 0xFF) shl 16) or
            ((h[o + 2].toInt() and 0xFF) shl 8) or (h[o + 3].toInt() and 0xFF)
    }

    private fun readLittleEndianInt(h: ByteArray, o: Int): Int =
        (h[o].toInt() and 0xFF) or ((h[o + 1].toInt() and 0xFF) shl 8) or
            ((h[o + 2].toInt() and 0xFF) shl 16) or ((h[o + 3].toInt() and 0xFF) shl 24)

    private fun readLittleEndianLong(h: ByteArray, o: Int): Long =
        (readLittleEndianInt(h, o).toLong() and 0xFFFFFFFFL) or (readLittleEndianInt(h, o + 4).toLong() shl 32)

    fun titleFromFileName(fileName: String): String {
        var name = fileName.substringAfterLast('/')
        name = name.replace(Regex("""\.(nkit\.iso|[A-Za-z0-9]{2,4})$"""), "")
        name = name.replace(Regex("""\[[^]]*]|\([^)]*\)"""), " ")
        return name.replace('_', ' ').replace(Regex("""\s+"""), " ").trim().ifEmpty { fileName }
    }

    private val ID_PATTERN = Regex("[A-Z0-9]{6}")

    /** GameTDB cover URLs to try for a game ID, best region first (same scheme Dolphin uses). */
    fun coverUrls(gameId: String): List<String> {
        val primary = when (gameId.getOrNull(3)) {
            'E', 'N' -> "US"
            'J' -> "JA"
            'K', 'Q', 'T' -> "KO"
            'D' -> "DE"; 'F' -> "FR"; 'I' -> "IT"; 'S' -> "ES"; 'H' -> "NL"; 'U' -> "AU"; 'R' -> "RU"
            else -> "EN"
        }
        val regions = listOf(primary, "US", "EN", "JA", "KO", "DE", "FR", "ES", "IT", "NL", "AU", "RU").distinct()
        // High-resolution front covers (coverHQ, ~350x500) in every region first, then the small
        // standard covers (~160x224), then full box art and 3D covers as last resorts.
        return regions.map { "https://art.gametdb.com/wii/coverHQ/$it/$gameId.png" } +
            regions.map { "https://art.gametdb.com/wii/cover/$it/$gameId.png" } +
            listOf(primary, "US", "EN").distinct().map { "https://art.gametdb.com/wii/coverfull/$it/$gameId.png" } +
            listOf(primary, "US", "EN").distinct().map { "https://art.gametdb.com/wii/cover3D/$it/$gameId.png" }
    }
}
