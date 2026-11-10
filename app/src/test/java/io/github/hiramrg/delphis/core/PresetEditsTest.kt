package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetEditsTest {
    private val rs = PresetLibrary.RED_STEEL_TRACKPAD

    @Test
    fun describesButtonsAndMoves() {
        fun on(c: ThorControl) = rs.mappings.first { it.control == c }.target
        assertEquals("A + ⤒ Wii Remote push", PresetEdits.wiiGets(on(ThorControl.L2)))
        assertEquals("⤒ Nunchuk thrust", PresetEdits.wiiGets(on(ThorControl.FACE_NORTH)))
        assertEquals("≈ Nunchuk shake", PresetEdits.wiiGets(on(ThorControl.FACE_WEST)))
        assertEquals("↑ Nunchuk swing up", PresetEdits.wiiGets(on(ThorControl.L3)))
        assertEquals("✥ Wii Remote swing", PresetEdits.wiiGets(on(ThorControl.RIGHT_STICK)))
        assertEquals("Nunchuk stick", PresetEdits.wiiGets(on(ThorControl.LEFT_STICK)))
        assertTrue(PresetEdits.describe(on(ThorControl.L2)).let { !it[0].motion && it[1].motion })
    }

    @Test
    fun movingSwapsAndKindsStayApart() {
        val moved = PresetEdits.move(rs.mappings, ThorControl.L2, ThorControl.R1)
        assertEquals("Zoom (A + push toward screen)", moved.first { it.control == ThorControl.R1 }.action)
        assertEquals("Lock on (A)", moved.first { it.control == ThorControl.L2 }.action)
        assertEquals(rs.mappings, PresetEdits.move(rs.mappings, ThorControl.L2, ThorControl.LEFT_STICK)) // button ↛ stick
        val toFree = PresetEdits.move(rs.mappings, ThorControl.L2, ThorControl.R3)
        assertTrue(toFree.none { it.control == ThorControl.L2 })
    }

    @Test
    fun pickingATileKeepsTheActionName() {
        val tile = PresetEdits.sections(rs.controller, ThorControl.FACE_NORTH).first { it.title == "NUNCHUK MOVES" }.tiles.first { it.small == "shake" }
        val edited = PresetEdits.setTarget(rs.mappings, ThorControl.FACE_NORTH, tile)
        assertEquals("Block (Nunchuk shake)", edited.first { it.control == ThorControl.FACE_NORTH }.action)
        val fresh = PresetEdits.setTarget(rs.mappings, ThorControl.R3, tile)
        assertEquals("Nunchuk shake", fresh.first { it.control == ThorControl.R3 }.action)
        assertEquals(rs.mappings.size - 1, PresetEdits.clear(rs.mappings, ThorControl.FACE_NORTH).size)
        val built = PresetEdits.build(rs, edited, "custom-x")
        assertEquals("Red Steel Trackpad (Custom)", built.name)
        // The rendered profile still works (no crash, Nunchuk shake on X).
        assertTrue(DolphinConfigWriter.renderProfile(built, ControllerProfile()).contains("Nunchuk/Shake/X"))
    }

    @Test
    fun everyPresetHasSectionsForItsControls() {
        PresetLibrary.ALL.forEach { p ->
            listOf(ThorControl.FACE_EAST, ThorControl.LEFT_STICK).forEach { c -> assertTrue("${p.id} $c", PresetEdits.sections(p.controller, c).isNotEmpty()) }
            p.mappings.forEach { m -> PresetEdits.describe(m.target) }
        }
    }

    @Test
    fun combosSplitIntoTilesAndBuildBack() {
        val zoom = rs.mappings.first { it.control == ThorControl.L2 }
        val d = PresetEdits.decompose(rs.controller, ThorControl.L2, zoom.target)!!
        assertEquals(listOf("A", "⤒"), d.tiles.map { it.glyph })
        assertTrue(d.isCombo && d.extra.isEmpty())
        val single = PresetEdits.decompose(rs.controller, ThorControl.FACE_WEST, rs.mappings.first { it.control == ThorControl.FACE_WEST }.target)!!
        assertEquals(listOf("shake"), single.tiles.map { it.small }) // the 3 shake keys are one tile
        val b = PresetEdits.sections(rs.controller, ThorControl.L2)[0].tiles.first { it.glyph == "B" }
        val edited = PresetEdits.setCombo(rs.mappings, ThorControl.L2, listOf(b, d.tiles[1]))
        val m = edited.first { it.control == ThorControl.L2 }
        assertEquals(listOf("Buttons/B", "Swing/Forward"), (m.target as Target.Buttons).keys)
        assertEquals("Zoom (B + Wii Remote push)", m.action)
        assertEquals("Choose a Wii button or move", PresetEdits.pickerTitle(rs.controller, ThorControl.L2))
        assertEquals("Choose a GameCube button", PresetEdits.pickerTitle(EmulatedController.GAMECUBE, ThorControl.L2))
    }
}
