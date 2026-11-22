package io.github.hiramrg.delphis.core

/**
 * Kept saves: saves you name and pin, so new autosaves never push them out. Each is three files in Delphis'
 * own storage: `<id>.state` (the save itself), `<id>.txt` (its time and name) and, if there is one, `<id>.jpg`.
 */
object KeptSaves {
    /** Kept saves per game. */
    const val MAX = 10
    const val NAME_MAX = 40
    /** Quick picks for naming a kept save that fit any game (after the names you used before for that game). */
    val SUGGESTIONS = listOf("Before a boss", "New area", "Checkpoint", "Best run", "Try again later")

    /** One line, no control characters, at most [NAME_MAX] characters. */
    fun cleanName(raw: String): String =
        raw.map { if (it.isISOControl()) ' ' else it }.joinToString("").replace(Regex("\\s+"), " ").trim().take(NAME_MAX).trim()

    /** What's written to `<id>.txt`. */
    fun meta(savedAt: Long, name: String): String = "$savedAt\n${cleanName(name)}"

    /** (save time, name) from `<id>.txt`; null if it isn't one. A missing name reads as "Kept save". */
    fun parseMeta(text: String): Pair<Long, String>? {
        val lines = text.lines()
        val t = lines.firstOrNull()?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return t to cleanName(lines.drop(1).joinToString(" ")).ifEmpty { "Kept save" }
    }

    /** The id of a `<id>.state` file, or null. */
    fun id(fileName: String): Long? = fileName.removeSuffix(".state").takeIf { it != fileName }?.toLongOrNull()?.takeIf { it > 0 }

    /** A new id that isn't one of [taken] (ids are times, so kept saves made in the same millisecond still differ). */
    fun newId(now: Long, taken: Collection<Long>): Long { var id = now; while (id in taken) id++; return id }
}
