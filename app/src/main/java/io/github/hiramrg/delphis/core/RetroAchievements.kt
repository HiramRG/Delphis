package io.github.hiramrg.delphis.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * RetroAchievements, as Delphis shows it. Dolphin (2512+) does the real work: it signs in, watches
 * the game's memory and unlocks achievements. Delphis only reads the player's account through the
 * public Web API (retroachievements.org/API, username + web API key) to show unlock badges and each
 * game's list. This file is the pure part: responses → data, and matching a disc to an RA game.
 */
object RetroAchievements {
    const val API = "https://retroachievements.org/API/"
    const val MEDIA = "https://media.retroachievements.org"
    /** RA console ids. */
    const val CONSOLE_GAMECUBE = 16
    const val CONSOLE_WII = 19

    data class Profile(val user: String, val points: Int, val lastGameId: Int?)

    data class Achievement(
        val id: Int,
        val title: String,
        val description: String,
        val points: Int,
        val badge: String,
        /** When it was unlocked (RA's "yyyy-MM-dd HH:mm:ss", UTC), or null while locked. */
        val earnedAt: String?,
        val displayOrder: Int = 0,
    ) {
        val unlocked: Boolean get() = earnedAt != null
        fun badgeUrl(): String = "$MEDIA/Badge/$badge" + (if (unlocked) "" else "_lock") + ".png"
    }

    data class GameProgress(val gameId: Int, val title: String, val achievements: List<Achievement>) {
        val total: Int get() = achievements.size
        val unlocked: Int get() = achievements.count { it.unlocked }
        val pointsTotal: Int get() = achievements.sumOf { it.points }
        val pointsEarned: Int get() = achievements.filter { it.unlocked }.sumOf { it.points }
        val percent: Int get() = if (total == 0) 0 else unlocked * 100 / total
        /** Unlocked first (newest first), then locked in the game's own order. */
        val sorted: List<Achievement> get() =
            achievements.filter { it.unlocked }.sortedByDescending { it.earnedAt } +
                achievements.filter { !it.unlocked }.sortedWith(compareBy({ it.displayOrder }, { it.id }))
    }

    /** One unlock from the player's recent achievements. */
    data class Unlock(
        val achievementId: Int,
        val gameId: Int,
        val gameTitle: String,
        val title: String,
        val description: String,
        val points: Int,
        val badge: String,
        val date: String,
    ) {
        fun badgeUrl() = "$MEDIA/Badge/$badge.png"
    }

    data class GameListing(val id: Int, val title: String)

    fun consoleFor(p: Platform): Int? = when (p) {
        Platform.GAMECUBE -> CONSOLE_GAMECUBE
        Platform.WII -> CONSOLE_WII
        Platform.UNKNOWN -> null
    }

    // ------------------------------------------------------------------ requests

    fun url(endpoint: String, user: String, key: String, vararg params: Pair<String, Any>): String {
        fun enc(v: Any) = java.net.URLEncoder.encode(v.toString(), "UTF-8")
        val extra = params.joinToString("") { (k, v) -> "&$k=${enc(v)}" }
        return "$API$endpoint.php?z=${enc(user)}&y=${enc(key)}$extra"
    }

    // ------------------------------------------------------------------ responses

    /** API_GetUserProfile. Null when the answer isn't a profile (wrong user or key). */
    fun parseProfile(json: String): Profile? = runCatching {
        val o = JSONObject(json)
        if (!o.has("User")) return null
        Profile(o.getString("User"), o.optInt("TotalPoints", 0), o.optInt("LastGameID", 0).takeIf { it > 0 })
    }.getOrNull()

    /** API_GetGameInfoAndUserProgress. "Achievements" is an object keyed by id (or [] when the game has none). */
    fun parseGameProgress(json: String): GameProgress? = runCatching {
        val o = JSONObject(json)
        val id = o.optInt("ID", 0).takeIf { it > 0 } ?: return null
        val list = mutableListOf<Achievement>()
        val raw = o.opt("Achievements")
        val items: List<JSONObject> = when (raw) {
            is JSONObject -> raw.keys().asSequence().map { raw.getJSONObject(it) }.toList()
            is JSONArray -> (0 until raw.length()).map { raw.getJSONObject(it) }
            else -> emptyList()
        }
        for (a in items) {
            val earned = a.optString("DateEarnedHardcore").takeIf { it.isNotBlank() && it != "null" }
                ?: a.optString("DateEarned").takeIf { it.isNotBlank() && it != "null" }
            list += Achievement(
                a.optInt("ID"), a.optString("Title"), a.optString("Description"), a.optInt("Points"),
                a.optString("BadgeName"), earned, a.optInt("DisplayOrder", 0),
            )
        }
        GameProgress(id, o.optString("Title"), list)
    }.getOrNull()

    /** API_GetUserRecentAchievements. */
    fun parseRecent(json: String): List<Unlock> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).map { a.getJSONObject(it) }.map { u ->
            Unlock(
                u.optInt("AchievementID"), u.optInt("GameID"), u.optString("GameTitle"), u.optString("Title"),
                u.optString("Description"), u.optInt("Points"), u.optString("BadgeName"), u.optString("Date"),
            )
        }
    }.getOrDefault(emptyList())

    /** API_GetGameList (games with achievements only). */
    fun parseGameList(json: String): List<GameListing> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).map { a.getJSONObject(it) }.map { GameListing(it.optInt("ID"), it.optString("Title")) }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------------ matching a disc to an RA game

    /**
     * Comparable form of a title: lower case, "&" → "and", a trailing ", The" moved to the front,
     * region tags and punctuation dropped. "Legend of Zelda, The: Twilight Princess" and
     * "The Legend of Zelda: Twilight Princess" both become "thelegendofzeldatwilightprincess".
     */
    fun normalize(title: String): String {
        var t = title.lowercase().replace("&", " and ")
        t = t.replace(Regex("\\s*[\\[(][^)\\]]*[)\\]]"), " ")
        Regex("^(.*?),\\s*the\\b(.*)$").find(t)?.let { m -> t = "the " + m.groupValues[1] + m.groupValues[2] }
        t = t.replace(Regex("[^a-z0-9]"), "")
        return t.removePrefix("the")
    }

    /** The RA game whose title matches [title] (exactly after [normalize]), if any. */
    fun match(title: String, list: List<GameListing>): GameListing? {
        val n = normalize(title)
        if (n.isEmpty()) return null
        return list.firstOrNull { normalize(it.title) == n }
            // RA sometimes adds a subtitle ("Red Steel" vs "Red Steel | ..."): accept one that starts with it.
            ?: list.filter { normalize(it.title).startsWith(n) }.minByOrNull { it.title.length }
    }

    /** True when an RA title plausibly is this disc (used to trust "the game you're playing now"). */
    fun sameGame(discTitle: String, raTitle: String): Boolean {
        val a = normalize(discTitle); val b = normalize(raTitle)
        return a.isNotEmpty() && b.isNotEmpty() && (a == b || a.startsWith(b) || b.startsWith(a))
    }
}
