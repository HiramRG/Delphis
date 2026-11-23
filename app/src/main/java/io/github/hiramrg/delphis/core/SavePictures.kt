package io.github.hiramrg.delphis.core

import kotlin.math.abs

/**
 * Pictures of saves: what was on the game's screen when a save was made, kept in Delphis' own storage as
 * `<save time ms>.jpg`. A picture belongs to the save made at (about) that time: Dolphin can touch the file a
 * moment after Delphis reads its time, so a few seconds either way still count (not more: two saves a few
 * seconds apart, like the one made before loading and the loaded one, must keep their own pictures).
 */
object SavePictures {
    /** How far a picture's time may be from its save's. */
    const val TOLERANCE_MS = 4_000L
    /** Width the pictures are stored at (the lists show them small). */
    const val WIDTH = 384

    fun fileName(savedAt: Long) = "$savedAt.jpg"

    fun savedAt(fileName: String): Long? =
        fileName.removeSuffix(".jpg").takeIf { it != fileName }?.toLongOrNull()?.takeIf { it > 0 }

    /** The picture for the save made at [savedAt]: the closest one in time, if it's close enough. */
    fun nearest(names: Collection<String>, savedAt: Long): String? =
        names.mapNotNull { n -> savedAt(n)?.let { n to abs(it - savedAt) } }
            .filter { it.second <= TOLERANCE_MS }.minByOrNull { it.second }?.first

    /** Pictures (and leftover temporary files) that none of the saves made at [live] needs. */
    fun unused(names: Collection<String>, live: Collection<Long>): List<String> = names.filter { n ->
        if (n.endsWith(".tmp")) return@filter true
        val t = savedAt(n) ?: return@filter false
        live.none { abs(it - t) <= TOLERANCE_MS }
    }
}
