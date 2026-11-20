package io.github.hiramrg.delphis.core

/** Where Delphis' updates may come from: GitHub's release pages and the GitHub download servers they send you to. */
object UpdateSource {
    private val HOSTS = setOf("github.com", "api.github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    fun allowed(url: java.net.URL): Boolean =
        url.protocol.equals("https", ignoreCase = true) && url.host.lowercase().let { h -> h in HOSTS || h.endsWith(".githubusercontent.com") }
}
