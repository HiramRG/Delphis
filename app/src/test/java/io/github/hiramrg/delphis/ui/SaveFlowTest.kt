package io.github.hiramrg.delphis.ui

import android.view.KeyEvent
import io.github.hiramrg.delphis.platform.DolphinBridge.KeepResult
import io.github.hiramrg.delphis.platform.DolphinBridge.SaveEntry
import io.github.hiramrg.delphis.platform.DolphinBridge.SaveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveFlowTest {
    private val kept = SaveEntry(SaveKind.KEPT, 1_000L, 10L, name = "Test", keptId = 7)
    private val latest = SaveEntry(SaveKind.LATEST, 2_000L, 10L, 9)

    private fun flow(log: MutableList<String>) = SaveFlow(
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        SaveActions(keep = { _, name -> log += "keep $name"; KeepResult.KEPT }, delete = { e -> log += "delete ${e.keptId}"; true }),
        yourNames = { listOf("Rainbow Road", "before a boss") },
        load = { e -> log += "load ${e.kind}" },
    ) { msg, _ -> log += msg }

    @Test fun savePopupButtons() {
        val log = mutableListOf<String>()
        val f = flow(log)
        f.show(kept)
        assertEquals(listOf(SaveFlow.Btn.DELETE, SaveFlow.Btn.BACK, SaveFlow.Btn.KEEP, SaveFlow.Btn.LOAD), f.buttons(kept))
        assertEquals("ring starts on Load", 3, f.ring)
        f.key(KeyEvent.KEYCODE_DPAD_LEFT)
        f.key(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals("A on the ringed Rename", SaveFlow.Step.NAME, f.step)
        f.key(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(SaveFlow.Step.SAVE, f.step)
        f.key(KeyEvent.KEYCODE_BUTTON_X)
        assertEquals("X deletes a kept save (asks first)", SaveFlow.Step.DELETE, f.step)
        assertEquals("the check starts on Cancel", 0, f.ring)
        f.key(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals("A on Cancel goes back", SaveFlow.Step.SAVE, f.step)
        f.key(KeyEvent.KEYCODE_BUTTON_X); f.key(KeyEvent.KEYCODE_DPAD_RIGHT); f.key(KeyEvent.KEYCODE_BUTTON_A)
        assertTrue(log.contains("delete 7"))
        assertFalse(f.open)
        f.show(latest)
        assertEquals("no Delete for autosaves", 3, f.buttons(latest).size)
        f.key(KeyEvent.KEYCODE_BUTTON_X)
        assertEquals(SaveFlow.Step.SAVE, f.step)
        f.key(KeyEvent.KEYCODE_BUTTON_A)
        assertTrue(log.contains("load LATEST"))
    }

    @Test fun naming() {
        val log = mutableListOf<String>()
        val f = flow(log)
        f.show(latest)
        f.press(SaveFlow.Btn.KEEP)
        val n = f.naming!!
        assertEquals("your names first, then the rest without repeats", listOf("Rainbow Road", "before a boss", "New area", "Checkpoint", "Best run", "Try again later"), n.suggestions.map { it.first })
        n.press("G"); n.press("O"); n.press(NameState.SPACE); n.press("O"); n.press(NameState.BACKSPACE); n.press("8")
        assertEquals("Go 8", n.name)
        f.key(KeyEvent.KEYCODE_DPAD_DOWN) // suggestions → first row of keys
        assertEquals(1, n.row)
        f.key(KeyEvent.KEYCODE_BUTTON_START)
        assertTrue(log.contains("keep Go 8"))
        assertFalse(f.open)
    }
}
