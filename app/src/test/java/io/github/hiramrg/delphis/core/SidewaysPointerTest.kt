package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SidewaysPointerTest {
    private val pad = ControllerProfile(devices = listOf("Android/1/Odin Controller"))

    private fun profile(p: Preset): Map<String, String> =
        DolphinConfigWriter.renderProfile(p, pad).lines().filter { " = " in it }
            .associate { it.substringBefore(" = ") to it.substringAfter(" = ") }

    @Test
    fun sidewaysPresetsHideTheIdlePointerSoTheDpadWorksInMenus() {
        for (p in listOf(PresetLibrary.NSMB_SIDEWAYS, PresetLibrary.DKCR_SIDEWAYS, PresetLibrary.KIRBY_SIDEWAYS, PresetLibrary.GENERIC_SIDEWAYS,
            PresetLibrary.MKW_NUNCHUK, PresetLibrary.MKW_WHEEL_STICK, PresetLibrary.MKW_WHEEL_GYRO)) {
            assertEquals(p.id, "True", profile(p)["IR/Auto-Hide"])
        }
    }

    @Test
    fun pointerOnlyHidesWhenIdle() {
        assertFalse(profile(PresetLibrary.PAPER_MARIO_SIDEWAYS).containsKey("IR/Auto-Hide"))
        val withPointer = PresetLibrary.NSMB_SIDEWAYS.copy(
            mappings = PresetLibrary.NSMB_SIDEWAYS.mappings +
                Mapping(ThorControl.RIGHT_STICK, Target.Directional("IR/Up", "IR/Down", "IR/Left", "IR/Right"), "Point"),
        )
        // A stick pointer still auto-hides: it comes back as soon as the stick moves.
        assertEquals("True", profile(withPointer)["IR/Auto-Hide"])
        assertEquals("True", profile(PresetLibrary.MKW_NUNCHUK)["IR/Auto-Hide"])
        assertTrue(profile(PresetLibrary.MKW_NUNCHUK).containsKey("IR/Up"))
    }
}
