package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsPresetsTest {
    private val sportsIds = setOf(PresetLibrary.SPORTS_SWING.id, PresetLibrary.RESORT_SWING.id, PresetLibrary.RESORT_TILT.id)
    private fun offered(id: String?, title: String = "Game") = GameDatabase.suggest(GameInfo(id, title, GameDatabase.platformOf(id))).let { s ->
        (s.choices + s.alternatives).map { it.id }.toSet()
    }

    @Test
    fun eachGameGetsItsOwn() {
        assertEquals(PresetLibrary.SPORTS_SWING.id, GameDatabase.suggest(GameInfo("RSPE01", "Wii Sports", Platform.WII)).choices.first().id)
        assertEquals(listOf(PresetLibrary.RESORT_SWING.id, PresetLibrary.RESORT_TILT.id), GameDatabase.suggest(GameInfo("RZTP01", "Wii Sports Resort", Platform.WII)).choices.map { it.id })
        assertEquals(sportsIds, GameDatabase.suggest(GameInfo("SP2E01", "Wii Sports + Wii Sports Resort", Platform.WII)).choices.map { it.id }.toSet())
    }

    @Test
    fun neverOfferedForOtherGames() {
        for (id in listOf("RMGE01", "RMCE01", "RZDE01", "SMNE01", "R8PE01", "RSBE01", "SXXE01", null, "GALE01")) {
            assertTrue(id.toString(), offered(id).intersect(sportsIds).isEmpty())
        }
        // Wii Sports doesn't get Resort's MotionPlus presets either.
        assertFalse(offered("RSPE01").contains(PresetLibrary.RESORT_SWING.id))
    }

    @Test
    fun fileNamesWithoutAnIdMatchTheRightGame() {
        assertEquals("Wii Sports Resort", GameDatabase.suggest(GameInfo(null, "Wii Sports Resort (USA).rvz", Platform.UNKNOWN)).matchedTitle)
        assertEquals("Wii Sports", GameDatabase.suggest(GameInfo(null, "Wii Sports (Europe).iso", Platform.UNKNOWN)).matchedTitle)
    }

    @Test
    fun multiplayerSwapsTiltForSticks() {
        assertEquals(PresetLibrary.RESORT_SWING, Players.multiplayerPreset(PresetLibrary.RESORT_TILT))
        assertEquals(PresetLibrary.SPORTS_SWING, Players.multiplayerPreset(PresetLibrary.SPORTS_SWING))
    }

    @Test
    fun resortHasMotionPlusAndTheSwingIsOnTheRightStick() {
        assertEquals("True", PresetLibrary.RESORT_SWING.options["Extension/Attach MotionPlus"])
        val swing = PresetLibrary.SPORTS_SWING.mappings.first { it.control == ThorControl.RIGHT_STICK }.target as Target.Directional
        assertEquals("Swing/Up", swing.up); assertEquals("Swing/Left", swing.left)
    }
}
