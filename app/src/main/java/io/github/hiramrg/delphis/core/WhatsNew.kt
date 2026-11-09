package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 15

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Trackpad aiming fixed.", "The pointer no longer snaps back to the middle, reaches the edges, and Recenter / double tap put it back in the middle (a ring on the pad shows it). With a trackpad preset the right stick no longer aims; the trackpad does.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
