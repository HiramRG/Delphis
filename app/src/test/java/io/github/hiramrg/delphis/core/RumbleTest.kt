package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RumbleTest {
    @Test fun offWritesNothing() {
        assertNull(Rumble.expression(Rumble.Mode.OFF, listOf("Android/1/Pad")))
        assertFalse(DolphinConfigWriter.renderProfile(PresetLibrary.POINTER_ADVENTURE, ControllerProfile()).contains("Rumble/Motor"))
    }

    @Test fun padUsesItsMotors() {
        val e = Rumble.expression(Rumble.Mode.PAD, listOf("Android/1/Pad", "Android/0/Pad"))!!
        assertTrue(e.contains("`Android/1/Pad:Motor`") && e.contains("`Android/0/Pad:Motor 0`"))
        assertFalse(e.contains("Device Sensors"))
    }

    @Test fun deviceUsesSensorsMotorAndOnTheThorItsControls() {
        assertEquals("`Android/0/Device Sensors:Motor`", Rumble.expression(Rumble.Mode.DEVICE, listOf("Android/0/Thor")))
        assertTrue(Rumble.expression(Rumble.Mode.DEVICE, listOf("Android/0/Thor"), builtIn = true)!!.contains("`Android/0/Thor:Motor`"))
    }

    @Test fun profileGetsTheLine() {
        val ini = DolphinConfigWriter.renderProfile(PresetLibrary.POINTER_ADVENTURE, ControllerProfile(rumble = Rumble.Mode.PAD))
        assertTrue(ini.lines().any { it.startsWith("Rumble/Motor = ") })
    }
}
