package io.github.hiramrg.delphis.core

import io.github.hiramrg.delphis.core.BottomScreen.Look
import io.github.hiramrg.delphis.core.BottomScreen.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BrightnessTest {
    @Test
    fun endsOfTheCurve() {
        assertEquals(100, Brightness.percentOf(1f))
        assertEquals(0, Brightness.percentOf(0f))
        assertEquals(1f, Brightness.levelOf(100), 0.001f)
        assertEquals(Brightness.MIN_LEVEL, Brightness.levelOf(0), 0.0001f)
    }

    @Test
    fun percentAndLevelRoundTrip() {
        for (p in 1..100) assertTrue("$p", abs(Brightness.percentOf(Brightness.levelOf(p)) - p) <= 1)
    }

    @Test
    fun theCurveIsSmoothAndRising() {
        var last = 0f
        for (p in 0..100) { val l = Brightness.levelOf(p); assertTrue(l >= last); last = l }
        // Half way on Android's slider is far below half the light (the reason the old straight slider felt jumpy).
        assertTrue(Brightness.levelOf(50) < 0.15f)
    }

    @Test
    fun systemSettingConvertsLikeAndroid() {
        assertEquals(1f, Brightness.levelOfSetting(255), 0.0001f)
        assertEquals(Brightness.MIN_LEVEL, Brightness.levelOfSetting(1), 0.0001f)
        assertEquals(127f / 254f, Brightness.levelOfSetting(128), 0.0001f)
    }

    @Test
    fun dimmingIsAShareOfNormalSoItAlwaysShows() {
        val night = 0.05f
        assertTrue(Brightness.dimmed(night, Brightness.Strength.MEDIUM) < night)
        assertEquals(0.3f, Brightness.dimmed(1f, Brightness.Strength.MEDIUM), 0.0001f)
        assertEquals(Brightness.MIN_LEVEL, Brightness.dimmed(0.01f, Brightness.Strength.STRONG), 0.0001f)
    }

    @Test
    fun oldSettingsCarryOver() {
        assertEquals(Brightness.Strength.MEDIUM, Brightness.strengthFromOldLevel(12))
        assertEquals(Brightness.Strength.STRONG, Brightness.strengthFromOldLevel(2))
        assertEquals(Brightness.Strength.LIGHT, Brightness.strengthFromOldLevel(40))
        assertEquals(100, Brightness.percentFromOldLinear(100))
        assertTrue(Brightness.percentFromOldLinear(12) > 40)
    }

    @Test
    fun achievementsLightItUpAndTrackpadNeverGoesBlack() {
        assertEquals(Look.BRIGHT, BottomScreen.look(Mode.DIM, autoDim = true, idle = true, screenOff = false, keepBright = false, event = true))
        assertEquals(Look.DIM, BottomScreen.look(Mode.OFF, autoDim = true, idle = true, screenOff = false, keepBright = false, trackpad = true))
        // "Turn screen off" is a choice the player made, so it still goes black.
        assertEquals(Look.BLACK, BottomScreen.look(Mode.DIM, autoDim = true, idle = true, screenOff = true, keepBright = false, event = true))
    }

    @Test
    fun panelSummary() {
        assertEquals("Dims after 10 s · Medium", BottomScreen.summary(Mode.DIM, 10, Brightness.Strength.MEDIUM, false))
        assertEquals("Turns off after 1 min", BottomScreen.summary(Mode.OFF, 60, Brightness.Strength.MEDIUM, false))
        assertEquals("Dims after 30 s · Strong", BottomScreen.summary(Mode.OFF, 30, Brightness.Strength.STRONG, true))
    }
}
