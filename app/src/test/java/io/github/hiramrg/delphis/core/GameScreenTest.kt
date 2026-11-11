package io.github.hiramrg.delphis.core

import io.github.hiramrg.delphis.core.GameScreen.Choice
import io.github.hiramrg.delphis.core.GameScreen.Fill
import io.github.hiramrg.delphis.core.GameScreen.Shape
import io.github.hiramrg.delphis.core.GameScreen.Sides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameScreenTest {
    private val wide = Choice(Shape.WIDE, Fill.WORLD, Sides.BLACK)
    private val patched = "G2VE08" // Dolphin ships "$16:9 Widescreen" for it

    @Test
    fun patchTurnsOnTheCodeAndCheatsForThisGameOnly() {
        assertEquals(GameScreen.Method.PATCH, GameScreen.method(wide, patched, true))
        val ini = GameScreen.applyToGameIni("[Controls]\nPadProfile1 = Delphis\n", wide, patched, true)
        val e = IniEditor(ini)
        assertEquals("1", e.get("Video_Settings", "AspectRatio"))
        assertEquals("False", e.get("Video_Settings", "wideScreenHack"))
        assertTrue(e.hasLine("Gecko_Enabled", "\$16:9 Widescreen"))
        assertEquals("True", e.get("Core", "EnableCheats"))
        assertEquals("Delphis", e.get("Controls", "PadProfile1"))
        // Back to 4:3: code off, Delphis' own cheats setting removed.
        val back = IniEditor(GameScreen.applyToGameIni(ini, wide.copy(shape = Shape.STANDARD), patched, true))
        assertEquals("2", back.get("Video_Settings", "AspectRatio"))
        assertFalse(back.hasLine("Gecko_Enabled", "\$16:9 Widescreen"))
        assertEquals(null, back.get("Core", "EnableCheats"))
    }

    @Test
    fun usersOwnCheatsStayOn() {
        val ini = "[Core]\nEnableCheats = True\n"
        val on = GameScreen.applyToGameIni(ini, wide, patched, true)
        val off = IniEditor(GameScreen.applyToGameIni(on, wide.copy(shape = Shape.STANDARD), patched, true))
        assertEquals("True", off.get("Core", "EnableCheats"))
    }

    @Test
    fun hackOrStretchWithoutAPatch() {
        val none = "GXXE99"
        assertEquals(GameScreen.Method.HACK, GameScreen.method(wide, none, true))
        assertEquals("True", IniEditor(GameScreen.applyToGameIni("", wide, none, true)).get("Video_Settings", "wideScreenHack"))
        assertEquals(GameScreen.Method.STRETCH, GameScreen.method(wide, none, false))
        assertEquals("3", IniEditor(GameScreen.applyToGameIni("", wide.copy(fill = Fill.STRETCH), patched, true)).get("Video_Settings", "AspectRatio"))
        assertEquals("Sides: pattern", GameScreen.lookLabel(Choice(Shape.STANDARD, Fill.WORLD, Sides.PATTERN), none, true))
    }
}
