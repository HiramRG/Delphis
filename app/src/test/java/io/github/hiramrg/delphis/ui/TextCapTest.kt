package io.github.hiramrg.delphis.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCapTest {
    @Test fun followsAndroidUpTo115() {
        assertEquals(1f, TextCap.capped(1f), 0f)
        assertEquals(0.85f, TextCap.capped(0.85f), 0f)
        assertEquals(1.15f, TextCap.capped(1.15f), 0f)
        assertEquals(1.15f, TextCap.capped(1.3f), 0f)
        assertEquals(1.15f, TextCap.capped(2f), 0f)
    }
}
