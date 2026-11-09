package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 16

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("New trackpad engine.", "The trackpad now talks to Dolphin directly, as a controller, instead of touching Dolphin's screen: smooth, reaches every edge, and can't set off Android's gestures. Force-stop Dolphin once (Android Settings → Apps → Dolphin → Force stop) so it connects to Delphis.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
