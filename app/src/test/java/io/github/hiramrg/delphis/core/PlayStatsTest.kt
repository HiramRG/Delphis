package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class PlayStatsTest {
    private val wed = LocalDate.of(2026, 11, 25) // a Wednesday

    @Test fun addsUpTheDayAndDropsOldDays() {
        var days = PlayStats.add(emptyMap(), wed, 60_000)
        days = PlayStats.add(days, wed, 120_000)
        assertEquals(180_000L, days["2026-11-25"])
        days = days + ("2026-01-01" to 5L)
        days = PlayStats.add(days, wed, 1)
        assertFalse("2026-01-01" in days)
    }

    @Test fun weekRunsMondayToSunday() {
        val days = mapOf("2026-11-23" to 1_200_000L, "2026-11-25" to 600_000L, "2026-11-22" to 999L)
        val w = PlayStats.week(days, wed, Locale.ENGLISH)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), w.map { it.label })
        assertEquals(1_200_000L, w[0].ms); assertEquals(600_000L, w[2].ms)
        assertTrue(w[2].today); assertTrue(w[3].future); assertFalse(w[1].future)
        assertEquals(999L, PlayStats.lastWeekTotal(days, wed))
    }

    @Test fun wordsAndOrder() {
        assertEquals("45 min", PlayStats.format(45 * 60_000L))
        assertEquals("3 h 40 min", PlayStats.format(220 * 60_000L))
        assertEquals("12 h", PlayStats.format(12 * 3_600_000L + 5 * 60_000L))
        assertEquals("1 h 10 min more than last week", PlayStats.compare(130 * 60_000L, 60 * 60_000L))
        assertEquals("20 min less than last week", PlayStats.compare(40 * 60_000L, 60 * 60_000L))
        assertEquals("Same as last week", PlayStats.compare(60_000L, 60_000L))
        val top = PlayStats.mostPlayed(mapOf("a" to 5_000_000L, "b" to 9_000_000L, "c" to 1_000L), mapOf("b" to 3L))
        assertEquals(listOf("b", "a"), top.map { it.key }); assertEquals(3L, top[0].sessions)
    }

    @Test fun nextUpByHowManyPlayersHaveThem() {
        val json = """{"ID":95,"Title":"NSMB","NumDistinctPlayers":1000,"Achievements":{
            "1":{"ID":1,"Title":"A","Points":5,"BadgeName":"1","NumAwarded":900,"DateEarned":"2026-11-01 10:00:00","DisplayOrder":1},
            "2":{"ID":2,"Title":"B","Points":5,"BadgeName":"2","NumAwarded":30,"DisplayOrder":2},
            "3":{"ID":3,"Title":"C","Points":5,"BadgeName":"3","NumAwarded":620,"DisplayOrder":3}}}"""
        val p = RetroAchievements.parseGameProgress(json)!!
        assertEquals(listOf(3, 2), p.nextUp.map { it.id })
        assertEquals(62, p.rarity(p.nextUp[0])); assertEquals("Common", rarityName(62))
        assertEquals(3, p.rarity(p.nextUp[1])); assertEquals("Very rare", rarityName(3))
        assertEquals("Uncommon", rarityName(20)); assertEquals("Rare", rarityName(5))
    }
}
