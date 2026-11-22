package io.github.hiramrg.delphis.core

/** Which of Delphis' settings go into a backup, and how a restore merges them with what's on the device. */
object BackupRules {
    /** Favorites and play time (merged, never replaced). */
    val PLAY_KEYS = setOf("favorites", "last_played", "sessions", "play_time")

    /**
     * Never backed up: secrets (the RetroAchievements key and account), folder permissions that only work on this
     * device, caches, and notes about this device's state.
     */
    private val NEVER = setOf(
        "ra_key", "ra_user", "dolphin_tree", "games_tree", "games_trees", "library_cache", "library_cache_version",
        "crash_seen", "prompted_update_build", "relaunch_after_update", "whats_new_seen", "last_applied",
        "osd_off_by_delphis", "overlay_in_dolphin", "save_states_off", "dolphin_pkg", "last_backup_at",
        "sleep_save_log", "pill_seen",
    )

    fun isSetting(key: String) = key !in NEVER && key !in PLAY_KEYS && !key.startsWith("kept_names_")
    fun isKeptName(key: String) = key.startsWith("kept_names_")

    /** Play time, sessions, last played ("key=value" lines): the bigger value of each wins (restoring twice adds nothing). */
    fun mergeMax(here: String?, backup: String?): String {
        fun parse(s: String?) = s.orEmpty().lines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to (it[1].toLongOrNull() ?: return@mapNotNull null) } }.toMap()
        val a = parse(here); val b = parse(backup)
        return (a.keys + b.keys).joinToString("\n") { k -> "$k=${maxOf(a[k] ?: 0L, b[k] ?: 0L)}" }
    }

    /** Names you used for kept saves (newline lists): yours first, then the backup's, no repeats, up to 8. */
    fun mergeNames(here: String?, backup: String?): String =
        (here.orEmpty().lines() + backup.orEmpty().lines()).filter { it.isNotBlank() }.distinctBy { it.lowercase() }.take(8).joinToString("\n")

    /** A file name inside the backup (letters and digits only, from a game id). */
    fun safe(id: String) = id.filter { it.isLetterOrDigit() }

    /** Only paths Delphis writes itself: kept-saves/<game>/<file> with a plain file name. */
    fun keptPath(entry: String): Pair<String, String>? {
        val parts = entry.split('/')
        if (parts.size != 3 || parts[0] != "kept-saves") return null
        val (game, file) = parts[1] to parts[2]
        if (game.isEmpty() || game != safe(game)) return null
        if (!Regex("^[0-9]+\\.(state|txt|jpg)$").matches(file)) return null
        return game to file
    }
}
