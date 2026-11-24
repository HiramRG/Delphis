package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveButtonsTest {
    @Test fun hotkeysPointAtDelphisPadAndKeepOthers() {
        val out = DolphinConfigWriter.hotkeysIni("[Hotkeys]\nDevice = Android/0/Touchscreen\nGeneral/Take Screenshot = `Button X`\n")
        val ini = IniEditor(out)
        assertEquals("`DSUClient/0/Delphis:PS`", ini.get("Hotkeys", DolphinConfigWriter.SAVE_HOTKEY))
        assertEquals("`DSUClient/0/Delphis:Share`", ini.get("Hotkeys", DolphinConfigWriter.LOAD_HOTKEY))
        assertEquals("`Button X`", ini.get("Hotkeys", "General/Take Screenshot"))
        assertEquals(out, DolphinConfigWriter.hotkeysIni(out)) // written once, then unchanged
    }

    @Test fun emptyFileGetsTheSection() {
        val out = DolphinConfigWriter.hotkeysIni("")
        assertTrue(out.contains("[Hotkeys]"))
        assertTrue(out.contains("Save State/Save State Slot 9 = `DSUClient/0/Delphis:PS`"))
    }

    @Test fun padDataCarriesTheButtons() {
        // Header 16 + type 4 + pad 12 + counter 4: then states1 (Share), states2, PS, touch.
        val off = 16 + 4 + 12 + 4
        val none = Dsu.padData(1, 1, 0, 0, 0)
        assertEquals(0, none[off].toInt()); assertEquals(0, none[off + 2].toInt())
        val save = Dsu.padData(1, 1, 0, 0, 0, saveButtons = Dsu.SAVE_BUTTON)
        assertEquals(0, save[off].toInt()); assertEquals(1, save[off + 2].toInt())
        val load = Dsu.padData(1, 1, 0, 0, 0, saveButtons = Dsu.LOAD_BUTTON)
        assertEquals(1, load[off].toInt()); assertEquals(0, load[off + 2].toInt())
    }
}
