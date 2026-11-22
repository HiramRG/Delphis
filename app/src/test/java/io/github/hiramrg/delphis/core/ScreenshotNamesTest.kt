package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotNamesTest {
    @Test fun folders() {
        assertEquals("New Super Mario Bros. Wii", ScreenshotNames.folder("New Super Mario Bros. Wii"))
        assertEquals("Zelda Twilight Princess", ScreenshotNames.folder("Zelda: Twilight / Princess"))
        assertEquals("Game", ScreenshotNames.folder("  ..  "))
        assertEquals("Pictures/Delphis/Game/", ScreenshotNames.relativePath(null))
    }

    @Test fun bars() {
        val dark = BooleanArray(100) { it < 12 || it >= 88 }
        assertEquals(12..87, ScreenshotNames.gameColumns(dark))
        assertEquals(0 until 100, ScreenshotNames.gameColumns(BooleanArray(100) { it < 1 }))
        assertEquals(0 until 100, ScreenshotNames.gameColumns(BooleanArray(100) { true }))
    }
}
