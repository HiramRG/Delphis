package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 67

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Trackpad games: every tab has its buttons.", "Picture, Saves and Achievements no longer show the trackpad's Recenter and Speed row, so Apply & restart is there again.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
