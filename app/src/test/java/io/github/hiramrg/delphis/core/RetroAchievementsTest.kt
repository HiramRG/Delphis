package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroAchievementsTest {
    @Test
    fun parsesGameProgressKeyedByIdAndEmpty() {
        val json = """{"ID":1234,"Title":"Red Steel","NumAchievements":3,"Achievements":{
            "1":{"ID":1,"Title":"A","Description":"d1","Points":10,"BadgeName":"111","DateEarned":"2026-11-09 20:00:00","DisplayOrder":2},
            "2":{"ID":2,"Title":"B","Description":"d2","Points":25,"BadgeName":"222","DisplayOrder":1},
            "3":{"ID":3,"Title":"C","Description":"d3","Points":5,"BadgeName":"333","DateEarnedHardcore":"2026-11-09 21:00:00","DisplayOrder":3}}}"""
        val p = RetroAchievements.parseGameProgress(json)!!
        assertEquals(1234, p.gameId); assertEquals(3, p.total); assertEquals(2, p.unlocked)
        assertEquals(15, p.pointsEarned); assertEquals(40, p.pointsTotal); assertEquals(66, p.percent)
        assertEquals(listOf(3, 1, 2), p.sorted.map { it.id }) // newest unlock first, then locked
        assertTrue(p.achievements.first { it.id == 2 }.badgeUrl().endsWith("/Badge/222_lock.png"))
        assertEquals(0, RetroAchievements.parseGameProgress("""{"ID":5,"Title":"X","Achievements":[]}""")!!.total)
        assertNull(RetroAchievements.parseGameProgress("""{"message":"bad key"}"""))
    }

    @Test
    fun parsesProfileAndRecent() {
        val prof = RetroAchievements.parseProfile("""{"User":"HiramRG","TotalPoints":2310,"LastGameID":1234}""")!!
        assertEquals("HiramRG", prof.user); assertEquals(2310, prof.points); assertEquals(1234, prof.lastGameId)
        assertNull(RetroAchievements.parseProfile("""{"message":"Unauthenticated."}"""))
        val r = RetroAchievements.parseRecent("""[{"Date":"2026-11-09 20:00:00","AchievementID":9,"Title":"T","Description":"D","BadgeName":"999","Points":10,"GameTitle":"Red Steel","GameID":1234}]""")
        assertEquals(9, r.single().achievementId); assertEquals("https://media.retroachievements.org/Badge/999.png", r.single().badgeUrl())
        assertTrue(RetroAchievements.parseRecent("oops").isEmpty())
    }

    @Test
    fun matchesDiscTitlesToRaTitles() {
        val list = listOf(
            RetroAchievements.GameListing(1, "Legend of Zelda, The: Twilight Princess"),
            RetroAchievements.GameListing(2, "Super Mario Galaxy"),
            RetroAchievements.GameListing(3, "Super Mario Galaxy 2"),
            RetroAchievements.GameListing(4, "Red Steel"),
        )
        assertEquals(1, RetroAchievements.match("The Legend of Zelda: Twilight Princess", list)?.id)
        assertEquals(2, RetroAchievements.match("Super Mario Galaxy", list)?.id)
        assertEquals(3, RetroAchievements.match("Super Mario Galaxy 2", list)?.id)
        assertEquals(4, RetroAchievements.match("Red Steel (USA)", list)?.id)
        assertNull(RetroAchievements.match("Wii Sports", list))
        assertTrue(RetroAchievements.sameGame("Red Steel", "Red Steel"))
        assertFalse(RetroAchievements.sameGame("Red Steel", "Mario Kart Wii"))
        assertTrue(RetroAchievements.url("API_GetUserProfile", "a b", "k", "u" to "a b").contains("z=a+b&y=k&u=a+b"))
    }
}
