package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveSlotsTest {
    @Test fun slots() {
        assertEquals(1, SaveSlots.slotOf("SMNE01.s01", "SMNE01"))
        assertEquals(10, SaveSlots.slotOf("SMNE01.s10", "SMNE01"))
        assertNull(SaveSlots.slotOf("SMNE01.s11", "SMNE01"))
        assertNull(SaveSlots.slotOf("SMNE01.s01.dtm", "SMNE01"))
        assertNull(SaveSlots.slotOf("SB4E01.s01", "SMNE01"))
        val own = SaveSlots.ownSlots(listOf("SMNE01.s09", "SMNE01.s03", "SMNE01.s01", "other.txt"), "SMNE01")
        assertEquals(listOf(1, 3), own.map { it.first })
    }
}
