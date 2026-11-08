package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusLevelsTest {
    @Test
    fun wifiBarsFollowSignal() {
        assertEquals(4, StatusLevels.wifiBars(-50))
        assertEquals(3, StatusLevels.wifiBars(-60))
        assertEquals(2, StatusLevels.wifiBars(-70))
        assertEquals(1, StatusLevels.wifiBars(-85))
        assertEquals(0, StatusLevels.wifiBars(-95))
        assertEquals(4, StatusLevels.wifiBars(null))
        assertEquals(4, StatusLevels.wifiBars(Int.MIN_VALUE))
    }

    @Test
    fun dndOnlyWhenFilterIsNotAll() {
        assertFalse(StatusLevels.dndOn(0))
        assertFalse(StatusLevels.dndOn(1))
        assertTrue(StatusLevels.dndOn(2))
        assertTrue(StatusLevels.dndOn(3))
        assertTrue(StatusLevels.dndOn(4))
    }

    @Test
    fun batteryLowOnlyWhenNotCharging() {
        assertTrue(StatusLevels.batteryLow(12, charging = false))
        assertFalse(StatusLevels.batteryLow(12, charging = true))
        assertFalse(StatusLevels.batteryLow(71, charging = false))
    }
}
