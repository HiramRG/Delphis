package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GyroAimingTest {
    private val pad = ControllerProfile(devices = listOf("Android/1/Odin Controller"))

    private fun profile(p: Preset, c: ControllerProfile = pad): Map<String, String> =
        DolphinConfigWriter.renderProfile(p, c).lines().filter { " = " in it }
            .associate { it.substringBefore(" = ") to it.substringAfter(" = ") }

    @Test
    fun gyroPresetDrivesTheAbsolutePointerFromGyroAndGravity() {
        val p = profile(PresetLibrary.GALAXY_GYRO)
        assertEquals("False", p["IR/Relative Input"])
        val right = p.getValue("IR/Right")
        // Default: upright like the 3DS, turning around the Thor's own up axis.
        assertTrue(right.contains("Gyro Roll Right`"))
        assertFalse(right.contains("Accel"))
        assertTrue(right.contains("relative("))
        // Automatic mode projects the gyro on gravity.
        val auto = profile(PresetLibrary.GALAXY_GYRO, pad.copy(gyroHorizontal = GyroHorizontal.AUTO)).getValue("IR/Right")
        assertTrue(auto.contains("Gyro Roll Right` * `Android/0/Device Sensors:Accel Forward`"))
        // Right stick still adds to the same pointer, and R3 recenters it.
        assertTrue(right.contains("Axis 11+"))
        assertTrue(right.contains("Button R3"))
        assertTrue(p.getValue("IR/Up").contains("Gyro Pitch Up"))
        // Up/down is anchored to the tilt measured from gravity.
        assertTrue(p.getValue("IR/Up").contains("atan2(`Android/0/Device Sensors:Accel Forward`, `Android/0/Device Sensors:Accel Up`)"))
        assertTrue(PresetLibrary.GALAXY_GYRO_ALL.all { it.name.endsWith("(Experimental)") })
        // Dolphin's IMU pointing isn't used any more.
        assertFalse(p.keys.any { it.startsWith("IMUIR/") || it.startsWith("IMUGyroscope/") })
    }

    @Test
    fun invertSettingsFlipOnlyTheirAxis() {
        for (preset in listOf(PresetLibrary.GALAXY_GYRO, PresetLibrary.GALAXY_PRECISION)) {
            val normal = profile(preset)
            val invY = profile(preset, pad.copy(gyroInvertY = true))
            val invX = profile(preset, pad.copy(gyroInvertX = true))
            assertEquals(normal["IR/Right"], invY["IR/Right"])
            assertTrue(normal["IR/Up"] != invY["IR/Up"])
            assertEquals(normal["IR/Up"], invX["IR/Up"])
            assertTrue(normal["IR/Right"] != invX["IR/Right"])
        }
        // Only one preset of each kind now; inverting is a setting.
        assertEquals(1, PresetLibrary.GALAXY_GYRO_ALL.size)
        assertEquals(1, PresetLibrary.GALAXY_PRECISION_ALL.size)
    }

    @Test
    fun trackerDirectionsAndNoDrift() {
        val upright = doubleArrayOf(0.0, 9.81, 0.0)
        // Turning left (positive rotation around Y, held upright) moves the pointer left.
        val t = GyroAim.Tracker()
        repeat(20) { t.step(0.01, GyroHorizontal.UPRIGHT, false, false, 4.5, 0.0, 1.0, 0.0, upright[0], upright[1], upright[2]) }
        assertTrue(t.x < 0)
        // Up/down comes back to the same height after tilting away and back (gravity anchor).
        val v = GyroAim.Tracker()
        fun at(angle: Double) = doubleArrayOf(0.0, 9.81 * Math.cos(angle), -9.81 * Math.sin(angle))
        var angle = 0.0
        repeat(300) { // tilt 0.3 rad over 1.5 s, but report only 80% of it on the gyro (imperfect gyro)
            angle += 0.002; val a = at(angle); v.step(0.005, GyroHorizontal.UPRIGHT, false, false, 2.7, 0.4 * 0.8, 0.0, 0.0, a[0], a[1], a[2])
        }
        repeat(300) {
            angle -= 0.002; val a = at(angle); v.step(0.005, GyroHorizontal.UPRIGHT, false, false, 2.7, -0.4, 0.0, 0.0, a[0], a[1], a[2])
        }
        repeat(400) { val a = at(0.0); v.step(0.005, GyroHorizontal.UPRIGHT, false, false, 2.7, 0.0, 0.0, 0.0, a[0], a[1], a[2]) }
        assertEquals(0.0, v.y, 0.05)
        // Holding still doesn't move it.
        val still = GyroAim.Tracker()
        repeat(200) { still.step(0.005, GyroHorizontal.AUTO, false, false, 4.5, 0.002, 0.003, -0.002, 0.0, 9.81, 0.0) }
        assertEquals(0.0, still.x, 0.01)
    }

    @Test
    fun speedComesFromSettings() {
        assertTrue(profile(PresetLibrary.POINTER_GYRO, pad.copy(gyroSpeed = 9.0)).getValue("IR/Right").contains("* 9.00"))
    }

    @Test
    fun otherPresetsKeepTheStickPointer() {
        val p = profile(PresetLibrary.GALAXY_RIGHT_STICK)
        assertEquals("True", p["IR/Relative Input"])
        assertFalse(p.values.any { it.contains("Device Sensors") })
    }

    @Test
    fun gyroOptionsOnlyForWiiRemoteStyles() {
        assertEquals(3, ActionCatalog.optionsFor(ThorControl.GYRO, EmulatedController.WIIMOTE_NUNCHUK).size)
        assertEquals(1, ActionCatalog.optionsFor(ThorControl.GYRO, EmulatedController.GAMECUBE).size)
    }

    @Test
    fun galaxyOffersStickAndAllGyroVariants() {
        val s = GameDatabase.suggest(GameInfo("SB4E01", "Super Mario Galaxy 2", Platform.WII))
        assertEquals(listOf("galaxy-rstick", "galaxy-precise"), s.choices.map { it.id })
        // The classic gyro presets are still there, first under "Other layouts".
        assertEquals("galaxy-gyro", s.alternatives.first().id)
    }

    @Test
    fun precisionPresetUsesPureGyroAccelerationAndHold() {
        val p = profile(PresetLibrary.GALAXY_PRECISION)
        assertEquals("False", p["IR/Relative Input"])
        val right = p.getValue("IR/Right")
        val up = p.getValue("IR/Up")
        // No gravity pull in precision mode.
        assertFalse(up.contains("atan2"))
        assertTrue(up.contains("Gyro Pitch Up"))
        // Acceleration curve and the R1 hold.
        assertTrue(right.contains("0.40 + 0.90 * min("))
        assertTrue(right.contains("if((`Android/1/Odin Controller:Button R1`"))
        assertTrue(up.contains("Button R1"))
        // The hold pseudo-key never reaches Dolphin, and R1 no longer spins.
        assertFalse(p.keys.any { it.startsWith("Delphis") })
        assertFalse(p.getValue("Shake/X").contains("Button R1"))
        assertTrue(p.getValue("Shake/X").contains("Button Y"))
        assertTrue(PresetLibrary.GALAXY_PRECISION_ALL.all { it.name.endsWith("(Experimental)") })
    }

    @Test
    fun precisionPointerLayoutMovesShakeToX() {
        val p = profile(PresetLibrary.POINTER_PRECISION)
        // X (north; "Button Y" in Android's default naming) now shakes the remote; L3 is 1.
        assertTrue(p.getValue("Shake/X").contains("Button Y"))
        assertFalse(p.getValue("Shake/X").contains("Button R1"))
        assertTrue(p.getValue("Buttons/1").contains("Button L3"))
        val s = GameDatabase.suggest(GameInfo("R3ME01", "Metroid Prime Trilogy", Platform.WII))
        assertEquals("nunchuk-precise", s.choices[1].id)
    }

    @Test
    fun precisionTrackerAimsFinelyAndHoldFreezes() {
        val up = doubleArrayOf(0.0, 9.81, 0.0)
        fun run(rate: Double, steps: Int, hold: Boolean = false): Double {
            val t = GyroAim.Tracker()
            repeat(steps) { t.step(0.01, GyroHorizontal.UPRIGHT, false, false, 4.5, 0.0, rate, 0.0, up[0], up[1], up[2], precision = true, hold = hold) }
            return t.x
        }
        // Same total rotation (0.2 rad): slowly moves less than quickly (acceleration).
        val slow = Math.abs(run(0.1, 200))
        val fast = Math.abs(run(1.0, 20))
        assertTrue("slow=$slow fast=$fast", slow < fast)
        // Holding R1 freezes the pointer.
        assertEquals(0.0, run(1.0, 20, hold = true), 1e-9)
    }

}
