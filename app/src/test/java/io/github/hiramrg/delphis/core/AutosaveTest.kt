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

    @Test
    fun backupsKeepTheNewestThree() {
        assertEquals("1700000000000.s09", AutosaveBackups.fileName(1_700_000_000_000))
        assertEquals(1_700_000_000_000, AutosaveBackups.savedAt("1700000000000.s09"))
        assertEquals(null, AutosaveBackups.savedAt("notes.txt"))
        assertEquals(null, AutosaveBackups.savedAt("abc.s09"))
        assertEquals(null, AutosaveBackups.savedAt("1700.s09.tmp"))
        val names = listOf("100.s09", "400.s09", "200.s09", "300.s09", "500.s09.tmp", "x.s09")
        assertEquals(listOf("400.s09", "300.s09", "200.s09", "100.s09"), AutosaveBackups.newestFirst(names))
        assertEquals(listOf("100.s09"), AutosaveBackups.toDelete(names))
        assertTrue(AutosaveBackups.toDelete(listOf("1.s09", "2.s09")).isEmpty())
    }

    @Test
    fun realClockGamesStartWithAutosaveOff() {
        for (id in listOf("GAFE01", "GAFP01", "GAFJ01", "GAEJ01", "RUUE01", "RUUP01", "ruuk01"))
            assertTrue(id, AutosaveRules.offByDefault(id))
        for (id in listOf("RMGE01", "GALE01", "SB4E01", "GA", "", null))
            assertTrue(id.toString(), !AutosaveRules.offByDefault(id))
    }
}
