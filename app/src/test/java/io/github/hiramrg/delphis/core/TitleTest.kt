package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleTest {
    @Test
    fun knownGamesUseTheirProperName() {
        // Super Mario Galaxy 2's disc calls itself "SUPER MARIO GALAXY MORE".
        assertEquals("Super Mario Galaxy 2", GameDatabase.titleFor(GameInfo("SB4E01", "SUPER MARIO GALAXY MORE", Platform.WII)))
        assertEquals("Super Mario Galaxy 2", GameDatabase.titleFor(GameInfo("SB4P01", "SUPER MARIO GALAXY MORE", Platform.WII)))
        assertNull(GameDatabase.titleFor(GameInfo("ZZZE01", "Something", Platform.WII)))
        assertNull(GameDatabase.titleFor(GameInfo(null, "Something", Platform.WII)))
    }
}
