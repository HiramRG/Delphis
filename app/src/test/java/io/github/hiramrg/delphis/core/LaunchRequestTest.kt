package io.github.hiramrg.delphis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchRequestTest {
    private val me = "io.github.hiramrg.delphis"
    @Test fun gamesOnly() {
        assertTrue(LaunchRequest.accept(null, null, "Mario Kart Wii.rvz", me))
        assertTrue(LaunchRequest.accept("file", null, "game.iso", me))
        assertTrue(LaunchRequest.accept("content", "com.android.externalstorage.documents", "game.wbfs", me))
        assertFalse(LaunchRequest.accept("content", "$me.updates", "Delphis.apk", me))
        assertFalse(LaunchRequest.accept("content", "$me.updates", "game.iso", me))
        assertFalse(LaunchRequest.accept("content", "com.android.externalstorage.documents", "notes.txt", me))
        assertFalse(LaunchRequest.accept("https", "example.com", "game.iso", me))
    }
}
