package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutosaveTest {
    @Test
    fun enablesSaveStatesAndKeepsOtherSettings() {
        val before = "[General]\nISOPaths = 1\n[Core]\nCPUThread = True\nEnableSaveStates = False\n"
        val after = DolphinConfigWriter.enableSaveStates(before)
        assertTrue(after.contains("EnableSaveStates = True"))
        assertTrue(after.contains("CPUThread = True"))
        assertTrue(after.contains("ISOPaths = 1"))
        assertEquals(1, Regex("EnableSaveStates").findAll(after).count())
        // Works on an empty / missing file too.
        assertTrue(DolphinConfigWriter.enableSaveStates("").contains("[Core]"))
    }

    @Test
    fun quickSlotFileMatchesDolphin() {
        // Dolphin's Quick Save is NativeLibrary.SaveState(9) → "<GAMEID>.s09" in StateSaves.
        assertEquals(listOf("StateSaves", "RMGE01.s09"), DolphinConfigWriter.quickStatePath("RMGE01"))
    }
}
