package io.github.hiramrg.delphis.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import io.github.hiramrg.delphis.core.GameInfo
import io.github.hiramrg.delphis.core.RetroAchievements
import io.github.hiramrg.delphis.core.RetroAchievements.GameProgress
import io.github.hiramrg.delphis.core.RetroAchievements.Unlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * The player's RetroAchievements account (read-only, through RA's Web API): game lists with progress,
 * badges, and new unlocks while a game runs. See core/RetroAchievements for the protocol side.
 */
object Achievements {
    data class Account(val user: String, val key: String)

    fun account(prefs: Prefs): Account? {
        val u = prefs.raUser?.takeIf { it.isNotBlank() } ?: return null
        val k = prefs.raKey?.takeIf { it.isNotBlank() } ?: return null
        return Account(u, k)
    }

    /** Bumped whenever a game's progress may have changed (an unlock), so lists refresh. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    @Volatile var lastError: String? = null; private set
    @Volatile private var lastRefresh = 0L

    /**
     * Forget cached progress and tell every list to reload. Called when Delphis comes back on screen
     * (e.g. after a game), so unlocks earned while playing show up even if they weren't caught live.
     */
    fun refresh(minGapMs: Long = 15_000) {
        val now = System.currentTimeMillis()
        if (now - lastRefresh < minGapMs) return
        lastRefresh = now
        progressCache.clear()
        _version.value++
    }

    private val progressCache = ConcurrentHashMap<Int, Pair<Long, GameProgress>>()
    private val gameLists = ConcurrentHashMap<Int, List<RetroAchievements.GameListing>>()
    private val badges = LruCache<String, Bitmap>(64)

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000
                setRequestProperty("User-Agent", "Delphis (Android Dolphin companion)")
            }
            try {
                if (conn.responseCode != 200) { lastError = "HTTP ${conn.responseCode}"; null }
                else conn.inputStream.bufferedReader().use { it.readText() }.also { lastError = null }
            } finally { conn.disconnect() }
        }.getOrElse { lastError = it.message; null }
    }

    /**
     * Signs in the way Dolphin does (RetroAchievements' "login2" with the password) and returns the
     * account's sign-in token, which Dolphin keeps in Config/RetroAchievements.ini. The password is
     * only sent to retroachievements.org, never stored.
     */
    suspend fun dolphinToken(user: String, password: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("https://retroachievements.org/dorequest.php").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000; requestMethod = "POST"; doOutput = true
                setRequestProperty("User-Agent", "Delphis (Android Dolphin companion)")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
                conn.outputStream.use { it.write("r=login2&u=${enc(user)}&p=${enc(password)}".toByteArray()) }
                val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val j = runCatching { org.json.JSONObject(body) }.getOrNull() ?: error("RetroAchievements didn't answer (HTTP ${conn.responseCode})")
                if (!j.optBoolean("Success")) error(j.optString("Error").ifBlank { "Wrong username or password" })
                val token = j.optString("Token").ifBlank { error("RetroAchievements didn't send a sign-in") }
                j.optString("User").ifBlank { user } to token
            } finally { conn.disconnect() }
        }
    }

    /** Checks a username + web API key. Null when RA doesn't accept them (or can't be reached). */
    suspend fun verify(user: String, key: String): RetroAchievements.Profile? =
        get(RetroAchievements.url("API_GetUserProfile", user, key, "u" to user))?.let(RetroAchievements::parseProfile)

    // ---------------------------------------------------------------- which RA game a disc is

    private suspend fun gameList(context: Context, a: Account, console: Int): List<RetroAchievements.GameListing> {
        gameLists[console]?.let { return it }
        // Kept on disk for a week: the list is long and rarely changes.
        val file = File(context.cacheDir, "ra-games-$console.json")
        val text = if (file.exists() && System.currentTimeMillis() - file.lastModified() < 7 * 24 * 3600_000L) file.readText()
        else get(RetroAchievements.url("API_GetGameList", a.user, a.key, "i" to console, "f" to 1))?.also { runCatching { file.writeText(it) } }
        val list = text?.let(RetroAchievements::parseGameList).orEmpty()
        if (list.isNotEmpty()) gameLists[console] = list
        return list
    }

    /** The RA game id for a game: learned while playing, else matched by title. Null when it has no set. */
    suspend fun gameIdFor(context: Context, prefs: Prefs, key: String, info: GameInfo): Int? {
        prefs.raGameId(key)?.takeIf { it > 0 }?.let { return it }
        val a = account(prefs) ?: return null
        val console = RetroAchievements.consoleFor(info.platform) ?: return null
        val hit = RetroAchievements.match(info.title, gameList(context, a, console)) ?: return null
        prefs.setRaGameId(key, hit.id)
        return hit.id
    }

    suspend fun progressById(prefs: Prefs, gameId: Int, maxAgeMs: Long = 120_000): GameProgress? {
        progressCache[gameId]?.let { (at, p) -> if (System.currentTimeMillis() - at < maxAgeMs) return p }
        val a = account(prefs) ?: return null
        val p = get(RetroAchievements.url("API_GetGameInfoAndUserProgress", a.user, a.key, "g" to gameId, "u" to a.user))
            ?.let(RetroAchievements::parseGameProgress) ?: return progressCache[gameId]?.second
        progressCache[gameId] = System.currentTimeMillis() to p
        return p
    }

    /** This game's achievements with the player's progress, or null (not signed in, no set, offline). */
    suspend fun progress(context: Context, prefs: Prefs, key: String, info: GameInfo): GameProgress? {
        val id = gameIdFor(context, prefs, key, info) ?: return null
        return progressById(prefs, id)?.takeIf { it.total > 0 }
    }

    // ---------------------------------------------------------------- badges

    suspend fun badge(context: Context, url: String): Bitmap? {
        badges.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "ra-badges/" + url.substringAfterLast('/'))
            val bytes = if (file.exists()) file.readBytes() else runCatching {
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 10_000; readTimeout = 15_000
                    try { if (responseCode == 200) inputStream.use { it.readBytes() } else null } finally { disconnect() }
                }
            }.getOrNull()?.also { b -> runCatching { file.parentFile?.mkdirs(); file.writeBytes(b) } }
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.also { badges.put(url, it) }
        }
    }

    // ---------------------------------------------------------------- while a game runs

    /** What a pop-up shows: the unlock plus the game's count after it. */
    data class Popup(val unlock: Unlock, val unlocked: Int?, val total: Int?)

    private val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    private fun parseDate(s: String): Long? = runCatching { synchronized(dateFormat) { dateFormat.parse(s)?.time } }.getOrNull()

    /**
     * Runs while the game is open: checks the player's recent unlocks every few seconds and calls
     * [onUnlock] for each one earned since the game started. Also learns which RA game this disc is
     * (from the unlocks, or from "last played" on the profile once Dolphin has reported the game).
     */
    suspend fun watch(context: Context, prefs: Prefs, key: String, info: GameInfo, onUnlock: suspend (Popup) -> Unit) {
        val start = System.currentTimeMillis()
        val seen = HashSet<Int>()
        var profileChecks = 0
        while (coroutineContext.isActive) {
            delay(if (seen.isEmpty()) 6_000 else 8_000)
            val a = account(prefs) ?: continue
            if (!prefs.raPopups && prefs.raGameId(key) != null) { delay(30_000); continue }
            val recent = get(RetroAchievements.url("API_GetUserRecentAchievements", a.user, a.key, "u" to a.user, "m" to 30))
                ?.let(RetroAchievements::parseRecent) ?: continue
            for (u in recent.sortedBy { it.date }) {
                if (!seen.add(u.achievementId)) continue
                val at = parseDate(u.date) ?: continue
                if (at < start - 20_000) continue // earned before this game started
                // Learn (or correct) which RA game this disc is from what Dolphin actually unlocked.
                if (prefs.raGameId(key) != u.gameId && RetroAchievements.sameGame(info.title, u.gameTitle)) {
                    CrashLog.event("RA game for ${info.title}: ${prefs.raGameId(key)} → ${u.gameId}")
                    prefs.setRaGameId(key, u.gameId)
                }
                progressCache.remove(u.gameId)
                val p = progressById(prefs, u.gameId)
                _version.value++
                CrashLog.event("achievement unlocked: ${u.title} (${u.gameTitle})")
                if (prefs.raPopups) onUnlock(Popup(u, p?.unlocked, p?.total))
            }
            // Check which RA game this is: Dolphin reports the game to RA a little after it starts. The id
            // matched by title can be the wrong set (another region or a subset), so a learned id is
            // re-checked once too, and corrected when RA says Dolphin is running a different set.
            val known = prefs.raGameId(key)
            if (profileChecks < (if (known == null) 3 else 1) && System.currentTimeMillis() - start > 20_000) {
                profileChecks++
                val last = verify(a.user, a.key)?.lastGameId
                if (last != null && last != known) {
                    val p = progressById(prefs, last, maxAgeMs = 0)
                    if (p != null && RetroAchievements.sameGame(info.title, p.title)) {
                        CrashLog.event("RA game for ${info.title}: $known → $last (from the profile)")
                        prefs.setRaGameId(key, last); _version.value++
                    }
                }
            }
        }
    }
}
