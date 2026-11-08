package io.github.hiramrg.delphis.core

import io.github.hiramrg.delphis.core.BottomScreen.Look
import io.github.hiramrg.delphis.core.BottomScreen.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class BottomScreenTest {
    @Test
    fun brightUntilIdleThenDimOrBlack() {
        assertEquals(Look.BRIGHT, BottomScreen.look(Mode.DIM, autoDim = true, idle = false, screenOff = false, keepBright = false))
        assertEquals(Look.DIM, BottomScreen.look(Mode.DIM, autoDim = true, idle = true, screenOff = false, keepBright = false))
        assertEquals(Look.BLACK, BottomScreen.look(Mode.OFF, autoDim = true, idle = true, screenOff = false, keepBright = false))
    }

    @Test
    fun autoDimOffKeepsItBrightButScreenOffStillWorks() {
        assertEquals(Look.BRIGHT, BottomScreen.look(Mode.DIM, autoDim = false, idle = true, screenOff = false, keepBright = false))
        assertEquals(Look.BLACK, BottomScreen.look(Mode.ON, autoDim = false, idle = false, screenOff = true, keepBright = false))
    }

    @Test
    fun menusAndDialogsKeepItBright() {
        assertEquals(Look.BRIGHT, BottomScreen.look(Mode.OFF, autoDim = true, idle = true, screenOff = true, keepBright = true))
    }

    @Test
    fun oldSliderValuesFallBackToTenSeconds() {
        assertEquals(10, BottomScreen.delayOf(3))
        assertEquals(30, BottomScreen.delayOf(30))
        assertEquals(Mode.DIM, BottomScreen.modeOf(null))
        assertEquals("1 min", BottomScreen.delayLabel(60))
    }
}
