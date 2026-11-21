package io.github.hiramrg.delphis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavePicturesTest {
    @Test fun nearest() {
        val names = listOf("100000.jpg", "130000.jpg", "x.jpg", "200000.txt")
        assertEquals("100000.jpg", SavePictures.nearest(names, 104_000))
        assertEquals("130000.jpg", SavePictures.nearest(names, 129_000))
        assertNull(SavePictures.nearest(names, 160_000))
    }

    @Test fun unused() {
        val names = listOf("100000.jpg", "300000.jpg", "a.tmp", "notes.txt")
        assertEquals(listOf("300000.jpg", "a.tmp"), SavePictures.unused(names, listOf(102_000L)))
    }
}
