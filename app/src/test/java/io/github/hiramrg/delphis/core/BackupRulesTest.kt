package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRulesTest {
    @Test fun keys() {
        assertFalse(BackupRules.isSetting("ra_key"))
        assertFalse(BackupRules.isSetting("games_trees"))
        assertFalse(BackupRules.isSetting("favorites"))
        assertFalse(BackupRules.isSetting("kept_names_SMNE01"))
        assertTrue(BackupRules.isSetting("look_SMNE01"))
    }

    @Test fun merges() {
        assertEquals("a=5\nb=2\nc=1", BackupRules.mergeMax("a=5\nb=1", "b=2\nc=1\nbad"))
        assertEquals("Test\nBoss", BackupRules.mergeNames("Test", "test\nBoss"))
    }

    @Test fun keptPaths() {
        assertEquals("SMNE01" to "12.state", BackupRules.keptPath("kept-saves/SMNE01/12.state"))
        assertNull(BackupRules.keptPath("kept-saves/../12.state"))
        assertNull(BackupRules.keptPath("kept-saves/SMNE01/evil.sh"))
        assertNull(BackupRules.keptPath("other/SMNE01/12.state"))
    }
}
