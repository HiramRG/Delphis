package io.github.hiramrg.delphis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    @Test
    fun showsOnceAfterAnUpdateButNeverOnAFreshInstall() {
        assertTrue(WhatsNew.shouldShow(seenRelease = 0, isUpdate = true))
        assertFalse(WhatsNew.shouldShow(seenRelease = WhatsNew.RELEASE, isUpdate = true))
        assertFalse(WhatsNew.shouldShow(seenRelease = 0, isUpdate = false))
        assertTrue(WhatsNew.items.isNotEmpty())
    }
}
