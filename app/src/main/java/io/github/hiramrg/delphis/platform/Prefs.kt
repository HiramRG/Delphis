package io.github.hiramrg.delphis.platform

import android.content.Context
import android.net.Uri
import io.github.hiramrg.delphis.core.ControllerProfile

/** All user settings, backed by SharedPreferences. */
class Prefs(context: Context) {
    /** The app context (for checks like one screen vs two). */
    val context: Context = context.applicationContext
    private val sp = context.getSharedPreferences("thor_companion", Context.MODE_PRIVATE)

    var dolphinTreeUri: Uri?
        get() = sp.getString("dolphin_tree", null)?.let(Uri::parse)
        set(v) = sp.edit().putString("dolphin_tree", v?.toString()).apply()

    /** The first games folder (older builds had only one; see [gamesFolders]). */
    val gamesTreeUri: Uri? get() = gamesFolders.firstOrNull()

    /** Every games folder the user added (e.g. one for Wii and one for GameCube). The old single folder carries over. */
    var gamesFolders: List<Uri>
        get() {
            val raw = sp.getString("games_trees", null)
            if (raw == null) return listOfNotNull(sp.getString("games_tree", null)?.let(Uri::parse))
            return runCatching { org.json.JSONArray(raw).let { a -> (0 until a.length()).map { Uri.parse(a.getString(it)) } } }.getOrDefault(emptyList())
        }
        set(v) = sp.edit().putString("games_trees", org.json.JSONArray(v.map { it.toString() }).toString()).apply()

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
    /**
     * The in-game bottom screen's normal brightness (0..100), set with its ☀ pill; used whenever the
     * screen isn't dimmed. Null = follow the system brightness (until the player picks a level).
     */
    var bottomBrightness: Int?
        get() = if (sp.contains("bottom_brightness")) sp.getInt("bottom_brightness", 50).coerceIn(0, 100) else null
        set(v) { if (v == null) sp.edit().remove("bottom_brightness").apply() else sp.edit().putInt("bottom_brightness", v.coerceIn(0, 100)).apply() }

    var brightnessPercent: Int
        get() = if (sp.contains("brightness_percent")) sp.getInt("brightness_percent", 12).coerceIn(0, 100)
        else when (sp.getInt("brightness_level", 2)) { 1 -> 2; 2 -> 12; 3 -> 40; else -> 100 }
        set(v) = sp.edit().putInt("brightness_percent", v.coerceIn(0, 100)).apply()

    /** "Don't show again" on the Back to main menu warning. */
    /** Menu theme: false = light (classic Wii), true = dark. The in-game bottom screen is always dark. */
    /** The last [io.github.hiramrg.delphis.core.WhatsNew.RELEASE] whose notes were shown (or skipped on a fresh install). */
    var whatsNewSeen: Int
        get() = sp.getInt("whats_new_seen", 0)
        set(v) = sp.edit().putInt("whats_new_seen", v).apply()

    /** "light", "dark" or "auto" (follow Android's dark theme setting). Older installs only stored a boolean. */
    var themeMode: String
        get() = sp.getString("theme_mode", null) ?: if (sp.getBoolean("theme_dark", false)) "dark" else "light"
        set(v) = sp.edit().putString("theme_mode", v).apply()

    // ---------------------------------------------------------------- favorites, last played, play time
    private fun readMap(key: String): Map<String, Long> =
        sp.getString(key, null).orEmpty().lines().mapNotNull { l ->
            val i = l.lastIndexOf('='); if (i <= 0) null else l.substring(0, i) to (l.substring(i + 1).toLongOrNull() ?: return@mapNotNull null)
        }.toMap()

    private fun writeMap(key: String, m: Map<String, Long>) =
        sp.edit().putString(key, m.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()

    /** How a Home row is sorted (LibSort name), per row. */
    fun rowSort(row: String): String? = sp.getString("row_sort_$row", null)
    fun setRowSort(row: String, sort: String) = sp.edit().putString("row_sort_$row", sort).apply()

    /** The controls card's view for a game: "thor", "list", or null = not picked yet (diagram unless it's too small). */
    fun controlsView(game: String): String? = sp.getString("controls_view_$game", null)
    fun setControlsView(game: String, view: String) = sp.edit().putString("controls_view_$game", view).apply()

    fun favorites(): Set<String> = sp.getStringSet("favorites", emptySet()) ?: emptySet()
    fun isFavorite(gameKey: String) = gameKey in favorites()
    fun toggleFavorite(gameKey: String) {
        val now = favorites().toMutableSet()
        if (!now.add(gameKey)) now.remove(gameKey)
        sp.edit().putStringSet("favorites", now).apply()
    }

    /** When each game was last started (epoch ms). */
    fun lastPlayed(): Map<String, Long> = readMap("last_played")
    fun markPlayed(gameKey: String) {
        writeMap("last_played", lastPlayed() + (gameKey to System.currentTimeMillis()))
        val n = sessions()
        writeMap("sessions", n + (gameKey to (n[gameKey] ?: 0L) + 1))
    }

    /** How many times each game was started (counted from build 205 on). */
    fun sessions(): Map<String, Long> = readMap("sessions")

    /** Bottom screen on Home: "controls" (how to play) or "progress" (achievements and stats). L3 flips it. */
    var bottomView: String
        get() = sp.getString("bottom_view", "controls") ?: "controls"
        set(v) = sp.edit().putString("bottom_view", v).apply()

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

    /** Per-game choice from the game's page, or the default (off for games that follow the real clock). */
    fun autoSaveChoiceFor(gameId: String?): Boolean {
        if (gameId == null) return false
        if (gameId in (sp.getStringSet("autosave_games_on", emptySet()) ?: emptySet())) return true
        if (gameId in (sp.getStringSet("autosave_games_off", emptySet()) ?: emptySet())) return false
        return !io.github.hiramrg.delphis.core.AutosaveRules.offByDefault(gameId)
    }

    fun setAutoSaveFor(gameId: String, on: Boolean) {
        val onSet = (sp.getStringSet("autosave_games_on", emptySet()) ?: emptySet()).toMutableSet()
        val offSet = (sp.getStringSet("autosave_games_off", emptySet()) ?: emptySet()).toMutableSet()
        if (on) { onSet.add(gameId); offSet.remove(gameId) } else { offSet.add(gameId); onSet.remove(gameId) }
        sp.edit().putStringSet("autosave_games_on", onSet).putStringSet("autosave_games_off", offSet).apply()
    }

    /** Whether this game is autosaved and resumed: the global setting and the game's own choice. */
    fun autoSaveFor(gameId: String?): Boolean = autoSaveResume && autoSaveChoiceFor(gameId)

    /** Set when Dolphin's menu had no Quick Save button (its "Enable Savestates" setting is off). */
    var saveStatesOff: Boolean
        get() = sp.getBoolean("save_states_off", false)
        set(v) = sp.edit().putBoolean("save_states_off", v).apply()

    /** Settings → Gyro aiming: invert the gyro pointer's left/right and up/down (every gyro preset). */
    var gyroInvertX: Boolean
        get() = sp.getBoolean("gyro_invert_x", false)
        set(v) = sp.edit().putBoolean("gyro_invert_x", v).apply()
    var gyroInvertY: Boolean
        get() = sp.getBoolean("gyro_invert_y", false)
        set(v) = sp.edit().putBoolean("gyro_invert_y", v).apply()

    /** Background music in Delphis' menus (not during games). */
    var menuMusicOn: Boolean
        get() = sp.getBoolean("menu_music_on", true)
        set(v) = sp.edit().putBoolean("menu_music_on", v).apply()

    /** Newest build Delphis already asked about ("A new version is ready to download"), so it asks once. */
    var promptedUpdateBuild: Int
        get() = sp.getInt("prompted_update_build", 0)
        set(v) = sp.edit().putInt("prompted_update_build", v).apply()

    /** Soft clicks when moving around the menus (off by default). */
    var menuSounds: Boolean
        get() = sp.getBoolean("menu_sounds", false)
        set(v) = sp.edit().putBoolean("menu_sounds", v).apply()

    /** 0-100, relative to the system media volume. Default a bit under it. */
    var menuMusicVolume: Int
        get() = sp.getInt("menu_music_volume", 70)
        set(v) = sp.edit().putInt("menu_music_volume", v.coerceIn(0, 100)).apply()

    /** An audio file the user picked instead of the built-in tune (content URI), or null. */
    var menuMusicUri: android.net.Uri?
        get() = sp.getString("menu_music_uri", null)?.let(android.net.Uri::parse)
        set(v) = sp.edit().putString("menu_music_uri", v?.toString()).apply()

    /** Seconds without a touch before the in-game bottom screen dims or goes black (one of BottomScreen.DELAYS). */
    var dimDelaySeconds: Int
        get() = io.github.hiramrg.delphis.core.BottomScreen.delayOf(sp.getInt("dim_after", io.github.hiramrg.delphis.core.BottomScreen.DEFAULT_DELAY))
        set(v) = sp.edit().putInt("dim_after", v).apply()

    /** In-game bottom screen while idle: Always on / Dim / Screen off (BottomScreen.Mode ids). */
    var bottomScreenMode: io.github.hiramrg.delphis.core.BottomScreen.Mode
        get() = io.github.hiramrg.delphis.core.BottomScreen.modeOf(sp.getString("bottom_screen_mode", null))
        set(v) = sp.edit().putString("bottom_screen_mode", v.id).apply()

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

    /** Dolphin has been set up to read Delphis' trackpad server, so the server starts with the app. */
    /** Set just before an update installs: Delphis opens itself again on the new version. */
    var relaunchAfterUpdate: Boolean
        get() = sp.getBoolean("relaunch_after_update", false)
        set(v) = sp.edit().putBoolean("relaunch_after_update", v).commit().let { }

    var trackpadServerSetUp: Boolean
        get() = sp.getBoolean("trackpad_dsu", false)
        set(v) = sp.edit().putBoolean("trackpad_dsu", v).apply()

    /** One-time check per controller (by descriptor): true = reports buttons by letter, false = by position, null = not checked. */
    fun padByLabel(key: String): Boolean? = if (sp.contains("pad_by_label_$key")) sp.getBoolean("pad_by_label_$key", false) else null
    fun setPadByLabel(key: String, byLabel: Boolean) = sp.edit().putBoolean("pad_by_label_$key", byLabel).apply()
    /** A controller of an unknown brand: its letters are on a Nintendo layout (A right), from the "press A" check; null = not asked. */
    fun padNintendoLayout(key: String): Boolean? = if (sp.contains("pad_nin_layout_$key")) sp.getBoolean("pad_nin_layout_$key", false) else null
    fun setPadNintendoLayout(key: String, nintendo: Boolean) = sp.edit().putBoolean("pad_nin_layout_$key", nintendo).apply()

    /** Multiplayer: who plays a game, in which order (see core/Players). */
    fun playerSetup(gameKey: String) = io.github.hiramrg.delphis.core.PlayerSetup.decode(sp.getString("players_$gameKey", null))
    fun setPlayerSetup(gameKey: String, setup: io.github.hiramrg.delphis.core.PlayerSetup) = sp.edit().putString("players_$gameKey", setup.encode()).apply()

    /** Bottom-screen trackpad speed, per game. */
    fun trackpadSpeed(gameKey: String) = io.github.hiramrg.delphis.core.Trackpad.Speed.of(sp.getString("trackpad_speed_$gameKey", null))
    fun setTrackpadSpeed(gameKey: String, speed: io.github.hiramrg.delphis.core.Trackpad.Speed) = sp.edit().putString("trackpad_speed_$gameKey", speed.id).apply()

    // ---- RetroAchievements (Settings → Achievements)
    var raUser: String?
        get() = sp.getString("ra_user", null)
        set(v) = sp.edit().putString("ra_user", v).apply()
    /** The RetroAchievements web API key, kept encrypted (see [SecretBox]); an older plain copy is encrypted on first read. */
    var raKey: String?
        get() {
            val stored = sp.getString("ra_key", null) ?: return null
            if (SecretBox.isSealed(stored)) return SecretBox.open(stored)
            SecretBox.seal(stored)?.let { sp.edit().putString("ra_key", it).apply() }
            return stored
        }
        set(v) {
            // Never kept in plain text: if Android's keystore can't encrypt it, it isn't saved (you'd sign in again).
            val sealed = v?.let { SecretBox.seal(it) }
            if (v != null && sealed == null) CrashLog.event("RetroAchievements key not saved: the keystore couldn't encrypt it")
            sp.edit().putString("ra_key", sealed).apply()
        }
    var raPopups: Boolean
        get() = sp.getBoolean("ra_popups", true)
        set(v) = sp.edit().putBoolean("ra_popups", v).apply()
    /** How long the unlock badge stays on screen, in seconds (Settings → Achievements). */
    var raBadgeSeconds: Int
        get() = sp.getInt("ra_badge_seconds", RA_BADGE_DEFAULT).coerceIn(RA_BADGE_MIN, RA_BADGE_MAX)
        set(v) = sp.edit().putInt("ra_badge_seconds", v.coerceIn(RA_BADGE_MIN, RA_BADGE_MAX)).apply()
    /** Turn off Dolphin's own on-screen messages (its achievement pop-up) so only Delphis' badge shows. */
    var raHideDolphin: Boolean
        get() = sp.getBoolean("ra_hide_dolphin", true)
        set(v) = sp.edit().putBoolean("ra_hide_dolphin", v).apply()
    /** Delphis turned Dolphin's on-screen messages off (so it only ever turns back on what it turned off). */
    var osdOffByDelphis: Boolean
        get() = sp.getBoolean("osd_off_by_delphis", false)
        set(v) = sp.edit().putBoolean("osd_off_by_delphis", v).apply()
    /** Where unlock badges show: "top", "bottom" or "both". */
    var raWhere: String
        get() = sp.getString("ra_where", "top") ?: "top"
        set(v) = sp.edit().putString("ra_where", v).apply()
    var raList: Boolean
        get() = sp.getBoolean("ra_list", true)
        set(v) = sp.edit().putBoolean("ra_list", v).apply()
    /** RA game id learned for a game (0 = looked up, no achievement set). */
    fun raGameId(gameKey: String): Int? = if (sp.contains("ra_game_$gameKey")) sp.getInt("ra_game_$gameKey", 0) else null
    fun setRaGameId(gameKey: String, id: Int) = sp.edit().putInt("ra_game_$gameKey", id).apply()

    // ---- GameCube screen (Settings → Display defaults; each game can override on its page)
    var gcDefaultWide: Boolean
        get() = sp.getBoolean("gc_default_wide", false)
        set(v) = sp.edit().putBoolean("gc_default_wide", v).apply()
    var gcFill: io.github.hiramrg.delphis.core.GameScreen.Fill
        get() = runCatching { io.github.hiramrg.delphis.core.GameScreen.Fill.valueOf(sp.getString("gc_fill", "WORLD")!!) }.getOrDefault(io.github.hiramrg.delphis.core.GameScreen.Fill.WORLD)
        set(v) = sp.edit().putString("gc_fill", v.name).apply()
    var gcSides: io.github.hiramrg.delphis.core.GameScreen.Sides
        get() = runCatching { io.github.hiramrg.delphis.core.GameScreen.Sides.valueOf(sp.getString("gc_sides", "BLACK")!!) }.getOrDefault(io.github.hiramrg.delphis.core.GameScreen.Sides.BLACK)
        set(v) = sp.edit().putString("gc_sides", v.name).apply()
    var gcUseHack: Boolean
        get() = sp.getBoolean("gc_use_hack", true)
        set(v) = sp.edit().putBoolean("gc_use_hack", v).apply()

    /** This GameCube game's screen choice (its own, else the defaults). */
    fun screenFor(gameId: String): io.github.hiramrg.delphis.core.GameScreen.Choice {
        val shape = sp.getString("screen_shape_$gameId", null)?.let { runCatching { io.github.hiramrg.delphis.core.GameScreen.Shape.valueOf(it) }.getOrNull() }
            ?: if (gcDefaultWide) io.github.hiramrg.delphis.core.GameScreen.Shape.WIDE else io.github.hiramrg.delphis.core.GameScreen.Shape.STANDARD
        val fill = sp.getString("screen_fill_$gameId", null)?.let { runCatching { io.github.hiramrg.delphis.core.GameScreen.Fill.valueOf(it) }.getOrNull() } ?: gcFill
        val sides = sp.getString("screen_sides_$gameId", null)?.let { runCatching { io.github.hiramrg.delphis.core.GameScreen.Sides.valueOf(it) }.getOrNull() } ?: gcSides
        return io.github.hiramrg.delphis.core.GameScreen.Choice(shape, fill, sides)
    }

    fun setScreenFor(gameId: String, c: io.github.hiramrg.delphis.core.GameScreen.Choice) = sp.edit()
        .putString("screen_shape_$gameId", c.shape.name).putString("screen_fill_$gameId", c.fill.name).putString("screen_sides_$gameId", c.sides.name).apply()

    /** Looks (post-processing shaders): on/off for all games, the default for new games, and each game's own. */
    var looksEnabled: Boolean
        get() = sp.getBoolean("looks_enabled", true)
        set(v) = sp.edit().putBoolean("looks_enabled", v).apply()
    var defaultLook: io.github.hiramrg.delphis.core.Looks.Look
        get() = io.github.hiramrg.delphis.core.Looks.byName(sp.getString("look_default", null)) ?: io.github.hiramrg.delphis.core.Looks.Look.ORIGINAL
        set(v) = sp.edit().putString("look_default", v.name).apply()
    /** Rumble: the controller (default on phones and tablets), the device itself (default on the Thor), or off. */
    var rumble: io.github.hiramrg.delphis.core.Rumble.Mode
        get() = io.github.hiramrg.delphis.core.Rumble.byName(sp.getString("rumble", null))
            ?: if (DeviceInfo.oneScreen(context)) io.github.hiramrg.delphis.core.Rumble.Mode.PAD else io.github.hiramrg.delphis.core.Rumble.Mode.DEVICE
        set(v) = sp.edit().putString("rumble", v.name).apply()

    /** The game's resolution (Dolphin's own setting until one is picked). */
    fun resolutionFor(gameId: String): io.github.hiramrg.delphis.core.Resolution.Level =
        io.github.hiramrg.delphis.core.Resolution.byName(sp.getString("res_$gameId", null)) ?: io.github.hiramrg.delphis.core.Resolution.Level.DOLPHIN
    fun setResolutionFor(gameId: String, level: io.github.hiramrg.delphis.core.Resolution.Level) = sp.edit().putString("res_$gameId", level.name).apply()
    fun lookFor(gameKey: String): io.github.hiramrg.delphis.core.Looks.Look =
        io.github.hiramrg.delphis.core.Looks.byName(sp.getString("look_$gameKey", null)) ?: defaultLook
    fun setLookFor(gameKey: String, look: io.github.hiramrg.delphis.core.Looks.Look) = sp.edit().putString("look_$gameKey", look.name).apply()

    fun presetFor(gameKey: String): String? = sp.getString("preset_$gameKey", null)
    fun setPresetFor(gameKey: String, presetId: String?) = sp.edit().putString("preset_$gameKey", presetId).apply()

    var libraryCache: String?
        get() = if (sp.getInt("library_cache_version", 0) == LIBRARY_CACHE_VERSION) sp.getString("library_cache", null) else null
        set(v) = sp.edit().putString("library_cache", v).putInt("library_cache_version", LIBRARY_CACHE_VERSION).apply()

    /**
     * Settings → Updates → Update channel: "stable" (everyone) or "beta" (new builds first, to test).
     * Defaults to the channel this build came from, so a beta APK keeps getting beta updates.
     */
    var updateChannel: String
        get() = sp.getString("update_channel", null) ?: Updater.builtChannel(context)
        set(v) = sp.edit().putString("update_channel", if (v == "beta") "beta" else "stable").apply()

    /** The first line of the last crash Delphis offered to report (so it's offered once). */
    var crashSeen: String?
        get() = sp.getString("crash_seen", null)
        set(v) = sp.edit().putString("crash_seen", v).apply()

    // ---------------------------------------------------------------- one screen (phones, tablets)

    /** Screens: 0 = automatic (whatever the device has), 1 = one screen, 2 = two screens. */
    var screenMode: Int
        get() = sp.getInt("screen_mode", 0).coerceIn(0, 2)
        set(v) = sp.edit().putInt("screen_mode", v).apply()
    /** One screen: Interface size. 0 = Automatic (a big screen is filled the way the Thor's is), 1 = Normal (no scaling). */
    var uiSize: Int
        get() = sp.getInt("ui_size", 0).coerceIn(0, 1)
        set(v) = sp.edit().putInt("ui_size", v.coerceIn(0, 1)).apply()
    /** One screen: Dolphin's on-screen buttons while no controller is connected. */
    var touchWhenNoController: Boolean
        get() = sp.getBoolean("touch_no_pad", true)
        set(v) = sp.edit().putBoolean("touch_no_pad", v).apply()
    /** One screen: the "No controller found" pop-up when Delphis opens. */
    var warnNoController: Boolean
        get() = sp.getBoolean("warn_no_pad", true)
        set(v) = sp.edit().putBoolean("warn_no_pad", v).apply()
    /** One screen: what opens Delphis' menu in a game: 0 = the Home button (⌂ / Xbox / PS), 1 = ⊖ + Start, 2 = both. */
    var menuShortcut: Int
        get() = sp.getInt("menu_shortcut", 2).coerceIn(0, 2)
        set(v) = sp.edit().putInt("menu_shortcut", v).apply()
    /** The shortcut as the pill and Settings name it, in the connected controller's own words. */
    val menuShortcutLabel: String get() {
        val names = ShortcutNames.forConnected()
        return when (menuShortcut) { 0 -> "the ${names.home}"; 1 -> "${names.combo} together"; else -> "the ${names.home}, or ${names.combo} together" }
    }
    /** "Press the Home button, or − and + at the same time" (the pill's second line). */
    val menuShortcutSentence: String get() {
        val names = ShortcutNames.forConnected()
        return "Press " + when (menuShortcut) { 0 -> "the ${names.home}"; 1 -> "${names.combo} at the same time"; else -> "the ${names.home}, or ${names.combo} at the same time" }
    }
    /**
     * Android 14+: Delphis can't end Dolphin's process, and Dolphin keeps the touch-controls setting it read when
     * it started. The setting Dolphin was last restarted with (null = unknown), so Delphis knows when to restart it.
     */
    var overlayInDolphin: Boolean?
        get() = if (sp.contains("overlay_in_dolphin")) sp.getBoolean("overlay_in_dolphin", false) else null
        set(v) = sp.edit().apply { if (v == null) remove("overlay_in_dolphin") else putBoolean("overlay_in_dolphin", v) }.apply()
    /** One screen: the Delphis pill at the top of the game. */
    var pillShown: Boolean
        get() = sp.getBoolean("pill_shown", true)
        set(v) = sp.edit().putBoolean("pill_shown", v).apply()
    /** The small pill's opacity, 20..100 %. */
    var pillOpacity: Int
        get() = sp.getInt("pill_opacity", 60).coerceIn(20, 100)
        set(v) = sp.edit().putInt("pill_opacity", v.coerceIn(20, 100)).apply()
    /** Names you gave kept saves of [gameId] (newest first, up to 8): offered first when naming the next one. */
    fun keptNames(gameId: String): List<String> =
        sp.getString("kept_names_" + gameId.filter { it.isLetterOrDigit() }, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    fun rememberKeptName(gameId: String, name: String) {
        val list = (listOf(name) + keptNames(gameId).filterNot { it.equals(name, ignoreCase = true) }).take(8)
        sp.edit().putString("kept_names_" + gameId.filter { it.isLetterOrDigit() }, list.joinToString("\n")).apply()
    }

    /** Quick save / load buttons while playing: Select + L1 saves, Select + R1 loads (Settings → Controls). */
    var quickSaveButtons: Boolean
        get() = sp.getBoolean("quick_save_buttons", true)
        set(v) = sp.edit().putBoolean("quick_save_buttons", v).apply()
    /** Loading needs Select + R1 held for 1 s (true, the default) or just pressed. */
    var quickLoadHold: Boolean
        get() = sp.getBoolean("quick_load_hold", true)
        set(v) = sp.edit().putBoolean("quick_load_hold", v).apply()
    val quickLoadHoldMs: Long get() = if (quickLoadHold) 1000L else 0L
    /** When the pill shows how to open the menu (5 s): 0 = every game start (default), 1 = once per game, 2 = never. */
    var pillFull: Int
        get() = sp.getInt("pill_full", 0).coerceIn(0, 2)
        set(v) = sp.edit().putInt("pill_full", v).apply()
    /** True the first time for [gameKey] (and marks it seen). */
    fun firstPillFor(gameKey: String): Boolean {
        val seen = sp.getStringSet("pill_seen", emptySet()) ?: emptySet()
        if (gameKey in seen) return false
        sp.edit().putStringSet("pill_seen", seen + gameKey).apply()
        return true
    }

    companion object {
        /** Window brightness (0..1) for a 0..100 setting. 0 stays just above "backlight off". */
        fun windowBrightness(percent: Int): Float = (percent / 100f).coerceIn(0.005f, 1f)

        /** Bump when game detection changes so old (unfiltered) scans are discarded. */
        const val LIBRARY_CACHE_VERSION = 2

        /** Achievement badge time, seconds: default and the slider's range. */
        const val RA_BADGE_DEFAULT = 7
        const val RA_BADGE_MIN = 3
        const val RA_BADGE_MAX = 15
    }
}
