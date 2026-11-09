package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadTest {
    @Test
    fun positionIsClampedToThePicture() {
        val p = Trackpad.Pos().moved(5000f, -5000f, 960f, 540f)
        assertEquals(960f, p.x, 0.001f)
        assertEquals(-540f, p.y, 0.001f)
        val back = p.moved(-100f, 40f, 960f, 540f)
        assertEquals(860f, back.x, 0.001f)
        assertEquals(-500f, back.y, 0.001f)
    }

    @Test
    fun normalSpeedSweepsTheWholePicture() {
        val g = Trackpad.gain(1920f, Trackpad.Speed.NORMAL)
        assertEquals(1920f, g * Trackpad.FULL_SWEEP_DP, 0.01f)
        assertTrue(Trackpad.gain(1920f, Trackpad.Speed.SLOW) < g)
        assertTrue(Trackpad.gain(1920f, Trackpad.Speed.FAST) > g)
    }

    @Test
    fun pictureIsLetterboxedTo16by9() {
        assertEquals(1920f to 1080f, Trackpad.picture(1920f, 1080f))
        assertEquals(1440f to 810f, Trackpad.picture(1440f, 1000f))
        assertEquals(1600f to 900f, Trackpad.picture(2000f, 900f))
    }

    @Test
    fun speedIdsRoundTrip() {
        Trackpad.Speed.entries.forEach { assertEquals(it, Trackpad.Speed.of(it.id)) }
        assertEquals(Trackpad.Speed.NORMAL, Trackpad.Speed.of(null))
    }

    @Test
    fun relayedTouchesStayClearOfDolphinsDoubleTap() {
        assertTrue(Trackpad.MIN_DOWN_GAP_MS > 300)
    }

    @Test
    fun trackpadPresetsAreFlaggedAndOptionStaysOutOfTheProfile() {
        assertTrue(PresetLibrary.POINTER_TRACKPAD.isTrackpad)
        assertTrue(PresetLibrary.GALAXY_TRACKPAD.isTrackpad)
        assertFalse(PresetLibrary.POINTER_ADVENTURE.isTrackpad)
        val ini = DolphinConfigWriter.renderProfile(PresetLibrary.POINTER_TRACKPAD, ControllerProfile())
        assertFalse(ini.contains("Delphis/"))
    }

    @Test
    fun trackpadOverlayHidesEveryButtonAndUsesDragMode() {
        val ini = DolphinConfigWriter.trackpadOverlay("[Android]\nShowInputOverlay = False\n")
        assertTrue(ini.contains("ShowInputOverlay = True"))
        assertTrue(ini.contains("IRMode = 2"))
        assertTrue(ini.contains("IRAlwaysRecenter = False"))
        DolphinConfigWriter.TRACKPAD_HIDDEN_BUTTONS.forEach { assertTrue(it, ini.contains("$it = False")) }
    }
}
