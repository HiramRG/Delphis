package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayersTest {
    private val thor = Pad("thor", "Odin Controller", listOf("Android/1/Odin Controller"), builtIn = true)
    private val xbox = Pad("xbox", "Xbox Wireless Controller", listOf("Android/2/Xbox Wireless Controller"), builtIn = false)
    private val bitdo = Pad("8bd", "8BitDo Pro 2", listOf("Android/3/8BitDo Pro 2"), builtIn = false)

    @Test
    fun thorIsPlayerOneByDefault() {
        assertEquals(listOf(thor, xbox), Players.assign(listOf(xbox, thor), PlayerSetup()))
    }

    @Test
    fun thorCanSitOut() {
        val setup = PlayerSetup(out = setOf("thor"))
        assertEquals(listOf(xbox, bitdo), Players.assign(listOf(thor, xbox, bitdo), setup))
    }

    @Test
    fun savedOrderWinsAndNewPadsGoLast() {
        val setup = PlayerSetup(order = listOf("8bd", "thor"))
        assertEquals(listOf(bitdo, thor, xbox), Players.assign(listOf(thor, xbox, bitdo), setup))
    }

    @Test
    fun nobodyLeftMeansTheThorPlays() {
        assertEquals(listOf(thor), Players.assign(listOf(thor, xbox), PlayerSetup(out = setOf("thor", "xbox"))))
    }

    @Test
    fun atMostFourPlayers() {
        val pads = (1..6).map { Pad("p$it", "Pad $it", emptyList(), builtIn = it == 1) }
        assertEquals(4, Players.assign(pads, PlayerSetup()).size)
    }

    @Test
    fun setupRoundTrips() {
        val s = PlayerSetup(listOf("a", "b"), setOf("b"))
        assertEquals(s, PlayerSetup.decode(s.encode()))
        assertEquals(PlayerSetup(), PlayerSetup.decode(null))
    }

    @Test
    fun externalPlayersDontGetTheThorsSensors() {
        val p = Players.forExternal(PresetLibrary.POINTER_PRECISION)
        assertTrue(p.mappings.none { it.control == ThorControl.GYRO })
        assertTrue(p.mappings.none { m -> m.target is Target.Buttons && GYRO_HOLD in (m.target as Target.Buttons).keys })
        assertFalse(Players.forExternal(PresetLibrary.POINTER_TRACKPAD).isTrackpad)
        assertEquals(PresetLibrary.MKW_WHEEL_STICK, Players.forExternal(PresetLibrary.MKW_WHEEL_GYRO))
    }

    @Test
    fun gameIniTurnsOnPlayersTwoToFourForThisGameOnly() {
        val ini = Players.mergeGameIni("[Controls]\nWiimoteProfile1 = Delphis-mkw\n", listOf(PresetLibrary.MKW_NUNCHUK, PresetLibrary.MKW_NUNCHUK))
        assertTrue(ini.contains("WiimoteProfile2 = Delphis-${PresetLibrary.MKW_NUNCHUK.id}-p2"))
        assertTrue(ini.contains("WiimoteSource1 = 1"))
        assertTrue(ini.contains("WiimoteProfile3 = Delphis-${PresetLibrary.MKW_NUNCHUK.id}-p3"))
        assertTrue(ini.contains("WiimoteSource2 = 1"))
        assertFalse(ini.contains("WiimoteProfile4"))
        assertTrue(ini.contains("WiimoteProfile1 = Delphis-mkw"))
        val solo = Players.mergeGameIni(ini, emptyList())
        assertFalse(solo.contains("WiimoteProfile2"))
        assertFalse(solo.contains("WiimoteSource1"))
        assertTrue(solo.contains("WiimoteProfile1 = Delphis-mkw"))
    }

    @Test
    fun gameCubePlayersUsePadPorts() {
        val ini = Players.mergeGameIni("", listOf(PresetLibrary.GAMECUBE))
        assertTrue(ini.contains("PadProfile2 = Delphis-gamecube-p2"))
        assertTrue(ini.contains("PadType1 = 6"))
    }

    @Test
    fun multiplayerUsesThePlainVersionOfGyroAndTrackpadPresets() {
        assertEquals(PresetLibrary.POINTER_ADVENTURE, Players.multiplayerPreset(PresetLibrary.POINTER_PRECISION))
        assertEquals(PresetLibrary.POINTER_ADVENTURE, Players.multiplayerPreset(PresetLibrary.POINTER_TRACKPAD))
        assertEquals(PresetLibrary.GALAXY_RIGHT_STICK, Players.multiplayerPreset(PresetLibrary.GALAXY_GYRO))
        assertEquals(PresetLibrary.NSMB_SIDEWAYS, Players.multiplayerPreset(PresetLibrary.NSMB_SIDEWAYS))
        assertTrue(PresetLibrary.ALL.none { Players.usesThorOnly(Players.multiplayerPreset(it)) })
    }

    @Test
    fun presetNamesAreTitleCase() {
        assertEquals("Wii Wheel (Tilt on Stick)", presetTitleCase("Wii Wheel (tilt on stick)"))
        assertEquals("Sideways Wii Remote (Custom)", presetTitleCase("Sideways Wii Remote (custom)"))
        assertEquals("Nunchuk + Pointer on Right Stick", presetTitleCase("Nunchuk + pointer on right stick"))
        PresetLibrary.ALL.forEach { assertEquals(it.name, presetTitleCase(it.name), it.name) }
        EmulatedController.entries.forEach { assertEquals(it.label, presetTitleCase(it.label)) }
    }

    @Test
    fun buttonLetterNotesOnlyForOtherLayouts() {
        assertEquals(ButtonLabels.NINTENDO, ButtonLabels.forVendor(0x057e))
        assertEquals(ButtonLabels.PLAYSTATION, ButtonLabels.forVendor(0x054c))
        assertEquals(ButtonLabels.XBOX, ButtonLabels.forVendor(0x045e))
        assertEquals(ButtonLabels.XBOX, ButtonLabels.forVendor(0x2dc8))
        assertEquals(null, ButtonLabels.NINTENDO.note)
        assertEquals("A and B, X and Y are swapped", ButtonLabels.XBOX.note)
        assertEquals(ButtonLabels.NINTENDO, thor.labels)
        assertEquals(ButtonLabels.XBOX, xbox.labels)
    }
}
