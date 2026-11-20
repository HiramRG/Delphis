package io.github.hiramrg.delphis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPrivacyTest {
    @Test fun hidesAccountEverywhere() {
        val report = """
            dolphin folder: content://org.dolphinemu.dolphinemu.user/tree/root%2F
              Enabled=True, Hardcore=False, Username=evangaylionn, signed in=yes
            retroachievements (Delphis): account=evangaylionn, last error=none
            2026-11-17 20:59:52  Dolphin signed in to RetroAchievements as evangaylionn
            api key: abc123
        """.trimIndent()
        val out = ReportPrivacy.sanitize(report, listOf("evangaylionn", null))
        assertFalse(out.contains("evangaylionn"))
        assertFalse(out.contains("content://"))
        assertFalse(out.contains("abc123"))
        assertTrue(out.contains("signed in to RetroAchievements as (hidden)"))
        assertTrue(out.contains("signed in=yes"))
    }
}
