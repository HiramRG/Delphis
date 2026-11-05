package io.github.hiramrg.delphis.ui

import io.github.hiramrg.delphis.core.PresetLibrary
import io.github.hiramrg.delphis.core.ThorControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlsSummaryTest {
    @Test
    fun galaxyMainTilesAreJumpSpinShootAim() {
        val s = summarize(PresetLibrary.GALAXY_RIGHT_STICK)
        assertEquals(listOf("Jump", "Spin", "Shoot Star Bit", "Aim pointer, grab Star Bits"), s.main.map { it.label })
        assertEquals(setOf(ThorControl.FACE_NORTH, ThorControl.FACE_WEST, ThorControl.R1), s.main[1].controls.toSet())
    }

    @Test
    fun galaxyPauseIsMergedAndMoveComesFirst() {
        val s = summarize(PresetLibrary.GALAXY_RIGHT_STICK)
        assertEquals("Move Mario", s.other.first().label)
        val pause = s.other.single { it.label == "Pause" }
        assertTrue(ThorControl.START in pause.controls && ThorControl.SELECT in pause.controls)
    }

    @Test
    fun everyPresetHasAtMostFourTilesAndNoControlIsLost() {
        PresetLibrary.ALL.forEach { p ->
            val s = summarize(p)
            assertTrue(p.id, s.main.size <= 4)
            val shown = (s.main + s.other).flatMap { it.controls }.toSet()
            assertEquals(p.id, p.mappings.map { it.control }.toSet(), shown)
        }
    }
}
