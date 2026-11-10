package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadTest {
    @Test
    fun normalSpeedSweepsTheWholePicture() {
        val (gx, gy) = Trackpad.normalizedGain(Trackpad.Speed.NORMAL)
        assertEquals(2f, gx * Trackpad.FULL_SWEEP_DP, 0.001f)
        assertEquals(gx * 16f / 9f, gy, 0.0001f)
        assertTrue(Trackpad.normalizedGain(Trackpad.Speed.SLOW).first < gx)
        assertTrue(Trackpad.normalizedGain(Trackpad.Speed.FAST).first > gx)
    }

    @Test
    fun speedIdsRoundTrip() {
        Trackpad.Speed.entries.forEach { assertEquals(it, Trackpad.Speed.of(it.id)) }
        assertEquals(Trackpad.Speed.NORMAL, Trackpad.Speed.of(null))
    }

    @Test
    fun trackpadPresetsPutDelphisTouchpadOnThePointer() {
        listOf(PresetLibrary.POINTER_TRACKPAD, PresetLibrary.GALAXY_TRACKPAD).forEach { p ->
            assertTrue(p.isTrackpad)
            assertTrue(p.mappings.none { it.control == ThorControl.RIGHT_STICK || it.control == ThorControl.R3 })
            val ini = DolphinConfigWriter.renderProfile(p, ControllerProfile())
            assertTrue(ini.contains("IR/Up = `DSUClient/0/Delphis:Touch Y-`"))
            assertTrue(ini.contains("IR/Right = `DSUClient/0/Delphis:Touch X+`"))
            assertTrue(ini.contains("IR/Relative Input = False"))
            assertFalse(ini.contains("Delphis/"))
        }
        assertFalse(PresetLibrary.POINTER_ADVENTURE.isTrackpad)
        assertFalse(DolphinConfigWriter.renderProfile(PresetLibrary.POINTER_ADVENTURE, ControllerProfile()).contains("DSUClient"))
    }

    @Test
    fun dolphinGetsDelphisServerAndKeepsOthers() {
        val ini = DolphinConfigWriter.dsuClientIni("[Server]\nEntries = Phone:192.168.1.5:26760;\n")
        assertTrue(ini.contains("Enabled = True"))
        assertTrue(ini.contains("Entries = Phone:192.168.1.5:26760;Delphis:127.0.0.1:${Dsu.PORT};"))
        assertEquals(ini, DolphinConfigWriter.dsuClientIni(ini)) // no duplicates
        assertTrue(DolphinConfigWriter.dsuClientIni("").contains("Entries = Delphis:127.0.0.1:${Dsu.PORT};"))
    }

    @Test
    fun dolphinsOwnTouchPointerIsTurnedOff() {
        assertTrue(DolphinConfigWriter.trackpadDolphinIni("[Android]\nIRMode = 1\n").contains("IRMode = 0"))
    }

    @Test
    fun redSteelPresetsAreSuggestedAndTouchButtonsReachDolphin() {
        val s = GameDatabase.suggest(GameInfo("REDE41", "Red Steel", Platform.WII))
        assertEquals(listOf(PresetLibrary.RED_STEEL_TRACKPAD, PresetLibrary.RED_STEEL_TOUCH), s.choices)
        assertTrue(PresetLibrary.RED_STEEL_TOUCH.isTrackpad)
        assertTrue(TouchButtons.of(PresetLibrary.RED_STEEL_TRACKPAD).isEmpty())
        assertEquals(4, TouchButtons.of(PresetLibrary.RED_STEEL_TOUCH).size)
        val ini = DolphinConfigWriter.renderProfile(PresetLibrary.RED_STEEL_TOUCH, ControllerProfile())
        // Zoom (touch button 0) holds A and pushes the remote forward, next to L1's own binding.
        assertTrue(ini.lines().first { it.startsWith("Swing/Forward = ") }.contains("${Dsu.DEVICE}:Square"))
        assertTrue(ini.lines().first { it.startsWith("Buttons/A = ") }.contains("${Dsu.DEVICE}:Square"))
        assertTrue(ini.contains("IR/Up = `${Dsu.DEVICE}:Touch Y-`"))
        assertFalse(ini.contains("Delphis/"))
        // Multiplayer falls back to the plain Nunchuk + pointer preset.
        assertEquals(PresetLibrary.POINTER_ADVENTURE, Players.multiplayerPreset(PresetLibrary.RED_STEEL_TOUCH))
    }

    @Test
    fun touchButtonsPressTheDsuFaceButtons() {
        val pad = Dsu.padData(7, 1, 1, 0, 0, faceButtons = 0b0101)
        // Analog face buttons at offset 48: Square, Cross, Circle, Triangle.
        assertEquals(255, pad[48].toInt() and 0xFF); assertEquals(0, pad[49].toInt())
        assertEquals(255, pad[50].toInt() and 0xFF); assertEquals(0, pad[51].toInt())
    }

    @Test
    fun editorOffersThePresetsOwnAndMotionActions() {
        val rs = PresetLibrary.RED_STEEL_TRACKPAD
        assertEquals("Zoom (A + push toward screen)", rs.mappings.first { it.control == ThorControl.L2 }.action)
        val forR3 = ActionCatalog.optionsFor(ThorControl.R3, rs).map { it.label }
        assertTrue("Zoom (A + push toward screen)" in forR3)
        assertTrue("Block (Nunchuk thrust)" in forR3)
        assertTrue("Nunchuk: swing up" !in forR3 || forR3.count { it.contains("swing up", ignoreCase = true) } >= 1)
        assertTrue("Wii Remote: push forward" !in forR3) // same target as the preset's own "Push (Wii Remote forward)"
        assertTrue("Wii Remote: swing left" in forR3)
        val forStick = ActionCatalog.optionsFor(ThorControl.LEFT_STICK, rs).map { it.label }
        assertTrue("Katana (flick)" in forStick)
        assertTrue("Swing Nunchuk" in forStick)
        assertEquals("Nothing", forStick.first())
    }
}
