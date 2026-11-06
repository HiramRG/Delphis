package io.github.hiramrg.delphis.core

/**
 * Earlier autosaves. Before Dolphin's Quick Save slot is overwritten, the state that's there is
 * copied into Delphis' own storage as `<last modified ms>.s09`, so one bad save never costs more
 * than the progress since the save before it.
 */
object AutosaveBackups {
    /** How many earlier autosaves are kept per game. */
    const val KEEP = 3

    fun fileName(savedAt: Long) = "$savedAt.s09"

    /** The save time encoded in a backup's file name, or null if it isn't one. */
    fun savedAt(fileName: String): Long? =
        fileName.removeSuffix(".s09").takeIf { it != fileName }?.toLongOrNull()?.takeIf { it > 0 }

    /** Backup file names, newest first. Anything that isn't a backup is ignored. */
    fun newestFirst(names: Collection<String>): List<String> =
        names.filter { savedAt(it) != null }.sortedByDescending { savedAt(it)!! }

    /** The backups to delete so only the newest [keep] remain. */
    fun toDelete(names: Collection<String>, keep: Int = KEEP): List<String> = newestFirst(names).drop(keep)
}
