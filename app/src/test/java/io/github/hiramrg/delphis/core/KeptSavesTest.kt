package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeptSavesTest {
    @Test fun names() {
        assertEquals("Before the boss", KeptSaves.cleanName("  Before\nthe \t boss  "))
        assertEquals(KeptSaves.NAME_MAX, KeptSaves.cleanName("x".repeat(100)).length)
        assertEquals("a b", KeptSaves.cleanName("a\u0000b"))
    }

    @Test fun meta() {
        assertEquals(1_700_000_000_000L to "Good spot", KeptSaves.parseMeta(KeptSaves.meta(1_700_000_000_000L, "Good spot")))
        assertEquals(5L to "Kept save", KeptSaves.parseMeta("5\n"))
        assertNull(KeptSaves.parseMeta("nope\nname"))
        assertNull(KeptSaves.parseMeta(""))
    }

    @Test fun ids() {
        assertEquals(12L, KeptSaves.id("12.state"))
        assertNull(KeptSaves.id("12.txt"))
        assertNull(KeptSaves.id("x.state"))
        assertEquals(12L, KeptSaves.newId(10, listOf(10L, 11L)))
    }
}
