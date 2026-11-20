package io.github.hiramrg.delphis.core

/**
 * Dolphin's save states for a game live in StateSaves as `<GAMEID>.s01` … `.s10`. Slot 9 is the Quick Save
 * slot Delphis' autosave uses; the others are the slots you save to from Dolphin's own menu.
 */
object SaveSlots {
    const val QUICK = 9

    /** The slot number of [fileName] for [gameId] (1–10), or null if it isn't one of the game's states. */
    fun slotOf(fileName: String, gameId: String): Int? {
        if (!fileName.startsWith("$gameId.s")) return null
        val n = fileName.removePrefix("$gameId.s")
        if (n.length != 2 || !n.all { it.isDigit() }) return null
        return n.toInt().takeIf { it in 1..10 }
    }

    /** Dolphin's own slots (not the Quick Save one), in the order the Saves list shows them. */
    fun ownSlots(fileNames: Collection<String>, gameId: String): List<Pair<Int, String>> =
        fileNames.mapNotNull { f -> slotOf(f, gameId)?.takeIf { it != QUICK }?.let { it to f } }.sortedBy { it.first }
}
