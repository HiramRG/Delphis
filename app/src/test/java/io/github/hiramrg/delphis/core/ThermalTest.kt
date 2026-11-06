package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThermalTest {
    @Test
    fun readsMillidegreesAndDegrees() {
        assertEquals(41.5f, Thermal.celsius("41500\n")!!, 0.01f)
        assertEquals(38f, Thermal.celsius("38")!!, 0.01f)
        assertNull(Thermal.celsius("-40000"))
        assertNull(Thermal.celsius("n/a"))
    }

    @Test
    fun picksTheHottestCpuZone() {
        val zones = listOf("battery" to "33000", "cpu-0-0-usr" to "52000", "cpu-1-2-usr" to "61000", "gpuss-0" to "70000", "cpuss-1" to "58000")
        assertEquals(61f, Thermal.cpuTemp(zones)!!, 0.01f)
        assertNull(Thermal.cpuTemp(listOf("battery" to "33000")))
    }
}
