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
}
