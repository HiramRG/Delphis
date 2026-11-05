package io.github.hiramrg.delphis.platform

import android.content.Context
import android.net.Uri
import io.github.hiramrg.delphis.core.ControllerProfile

/** All user settings, backed by SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("thor_companion", Context.MODE_PRIVATE)

    var dolphinTreeUri: Uri?
        get() = sp.getString("dolphin_tree", null)?.let(Uri::parse)
        set(v) = sp.edit().putString("dolphin_tree", v?.toString()).apply()

    var gamesTreeUri: Uri?
        get() = sp.getString("games_tree", null)?.let(Uri::parse)
        set(v) = sp.edit().putString("games_tree", v?.toString()).apply()

    /** Package of the Dolphin build to launch; null = auto-detect. */
    var dolphinPackage: String?
        get() = sp.getString("dolphin_pkg", null)
        set(v) = sp.edit().putString("dolphin_pkg", v).apply()

    var deviceName: String?
        get() = sp.getString("device_name", null)
        set(v) = sp.edit().putString("device_name", v).apply()

    /** Which rotation moves the gyro pointer left/right (GyroHorizontal name). */
    var gyroHorizontal: io.github.hiramrg.delphis.core.GyroHorizontal
        get() = runCatching { io.github.hiramrg.delphis.core.GyroHorizontal.valueOf(sp.getString("gyro_horizontal", null)!!) }
            .getOrDefault(io.github.hiramrg.delphis.core.GyroHorizontal.UPRIGHT)
        set(v) = sp.edit().putString("gyro_horizontal", v.name).apply()

    /** Gyro aiming speed, 1 (slow) .. 10 (fast); 5 ≈ a real Wii Remote. */
    var gyroSensitivity: Int
        get() = sp.getInt("gyro_sensitivity", 5).coerceIn(1, 10)
        set(v) = sp.edit().putInt("gyro_sensitivity", v.coerceIn(1, 10)).apply()

    var invertMotion: Boolean
        get() = sp.getBoolean("invert_motion", false)
        set(v) = sp.edit().putBoolean("invert_motion", v).apply()

    /**
     * Bottom-screen brightness while playing, 0 (dimmest) .. 100 (full).
     * Older versions stored a 1..4 level instead; that carries over the first time.
     */
    var brightnessPercent: Int
        get() = if (sp.contains("brightness_percent")) sp.getInt("brightness_percent", 12).coerceIn(0, 100)
        else when (sp.getInt("brightness_level", 2)) { 1 -> 2; 2 -> 12; 3 -> 40; else -> 100 }
        set(v) = sp.edit().putInt("brightness_percent", v.coerceIn(0, 100)).apply()

    /** "Don't show again" on the Back to main menu warning. */
    /** Menu theme: false = light (classic Wii), true = dark. The in-game bottom screen is always dark. */
    var themeDark: Boolean
        get() = sp.getBoolean("theme_dark", false)
        set(v) = sp.edit().putBoolean("theme_dark", v).apply()

    // ---------------------------------------------------------------- favorites, last played, play time
    private fun readMap(key: String): Map<String, Long> =
        sp.getString(key, null).orEmpty().lines().mapNotNull { l ->
            val i = l.lastIndexOf('='); if (i <= 0) null else l.substring(0, i) to (l.substring(i + 1).toLongOrNull() ?: return@mapNotNull null)
        }.toMap()

    private fun writeMap(key: String, m: Map<String, Long>) =
        sp.edit().putString(key, m.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()

    fun favorites(): Set<String> = sp.getStringSet("favorites", emptySet()) ?: emptySet()
    fun isFavorite(gameKey: String) = gameKey in favorites()
    fun toggleFavorite(gameKey: String) {
        val now = favorites().toMutableSet()
        if (!now.add(gameKey)) now.remove(gameKey)
        sp.edit().putStringSet("favorites", now).apply()
    }

    /** When each game was last started (epoch ms). */
    fun lastPlayed(): Map<String, Long> = readMap("last_played")
    fun markPlayed(gameKey: String) = writeMap("last_played", lastPlayed() + (gameKey to System.currentTimeMillis()))

    /** Total time played per game (ms). */
    fun playTime(): Map<String, Long> = readMap("play_time")
    fun addPlayTime(gameKey: String, ms: Long) {
        if (ms <= 0) return
        val m = playTime()
        writeMap("play_time", m + (gameKey to (m[gameKey] ?: 0L) + ms))
    }

    /** "Don't ask again" on the prompt to turn on the close-games helper. */
    var skipExitHelperPrompt: Boolean
        get() = sp.getBoolean("skip_exit_helper_prompt", false)
        set(v) = sp.edit().putBoolean("skip_exit_helper_prompt", v).apply()

    var skipExitWarning: Boolean
        get() = sp.getBoolean("skip_exit_warning", false)
        set(v) = sp.edit().putBoolean("skip_exit_warning", v).apply()

    /**
     * Save a Dolphin save state (the Quick Save slot) when Delphis closes a game, and load it
     * again when Delphis starts that game. On by default.
     */
    var autoSaveResume: Boolean
        get() = sp.getBoolean("auto_save_resume", true)
        set(v) = sp.edit().putBoolean("auto_save_resume", v).apply()

    /** Set when Dolphin's menu had no Quick Save button (its "Enable Savestates" setting is off). */
    var saveStatesOff: Boolean
        get() = sp.getBoolean("save_states_off", false)
        set(v) = sp.edit().putBoolean("save_states_off", v).apply()

    /** Background music in Delphis' menus (not during games). */
    var menuMusicOn: Boolean
        get() = sp.getBoolean("menu_music_on", true)
        set(v) = sp.edit().putBoolean("menu_music_on", v).apply()

    /** 0-100, relative to the system media volume. Default a bit under it. */
    var menuMusicVolume: Int
        get() = sp.getInt("menu_music_volume", 70)
        set(v) = sp.edit().putInt("menu_music_volume", v.coerceIn(0, 100)).apply()

    /** An audio file the user picked instead of the built-in tune (content URI), or null. */
    var menuMusicUri: android.net.Uri?
        get() = sp.getString("menu_music_uri", null)?.let(android.net.Uri::parse)
        set(v) = sp.edit().putString("menu_music_uri", v?.toString()).apply()

    /** Seconds after the game launches before the bottom screen dims. */
    var dimDelaySeconds: Int
        get() = sp.getInt("dim_delay", 3)
        set(v) = sp.edit().putInt("dim_delay", v).apply()

    /** Swap which physical display is treated as "top" (game) and "bottom" (companion). */
    var swapScreens: Boolean
        get() = sp.getBoolean("swap_screens", false)
        set(v) = sp.edit().putBoolean("swap_screens", v).apply()

    /** Inputs recorded by the Controller Check (slot -> Dolphin input names). */
    var calibration: Map<String, List<String>>
        get() = ControllerProfile.decodeCalibration(sp.getString("calibration", null))
        set(v) = sp.edit().putString("calibration", ControllerProfile.encodeCalibration(v)).apply()

    /** Android name of the gamepad the user pressed during the Controller Check. */
    var calibratedDeviceName: String?
        get() = sp.getString("calibrated_device", null)
        set(v) = sp.edit().putString("calibrated_device", v).apply()

    /** Cover art in the game list. On by default (an older "off" choice is reset once, in v2). */
    var showCovers: Boolean
        get() = sp.getBoolean("show_covers_v2", true)
        set(v) = sp.edit().putBoolean("show_covers_v2", v).apply()

    var listView: Boolean
        get() = sp.getBoolean("list_view", false)
        set(v) = sp.edit().putBoolean("list_view", v).apply()

    /** Last preset written to Dolphin, for the diagnostics report. */
    var lastApplied: String?
        get() = sp.getString("last_applied", null)
        set(v) = sp.edit().putString("last_applied", v).apply()

    fun presetFor(gameKey: String): String? = sp.getString("preset_$gameKey", null)
    fun setPresetFor(gameKey: String, presetId: String?) = sp.edit().putString("preset_$gameKey", presetId).apply()

    var libraryCache: String?
        get() = if (sp.getInt("library_cache_version", 0) == LIBRARY_CACHE_VERSION) sp.getString("library_cache", null) else null
        set(v) = sp.edit().putString("library_cache", v).putInt("library_cache_version", LIBRARY_CACHE_VERSION).apply()

    companion object {
        /** Window brightness (0..1) for a 0..100 setting. 0 stays just above "backlight off". */
        fun windowBrightness(percent: Int): Float = (percent / 100f).coerceIn(0.005f, 1f)

        /** Bump when game detection changes so old (unfiltered) scans are discarded. */
        const val LIBRARY_CACHE_VERSION = 2
    }
}
