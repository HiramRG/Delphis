package io.github.hiramrg.delphis.core

import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSourceTest {
    @Test fun githubOnly() {
        assertTrue(UpdateSource.allowed(URL("https://github.com/HiramRG/Delphis/releases/download/latest/Delphis.apk")))
        assertTrue(UpdateSource.allowed(URL("https://objects.githubusercontent.com/github-production-release-asset/x")))
        assertTrue(UpdateSource.allowed(URL("https://release-assets.githubusercontent.com/x")))
        assertFalse(UpdateSource.allowed(URL("http://github.com/x")))
        assertFalse(UpdateSource.allowed(URL("https://github.com.evil.example/x")))
        assertFalse(UpdateSource.allowed(URL("https://example.com/Delphis.apk")))
    }
}
