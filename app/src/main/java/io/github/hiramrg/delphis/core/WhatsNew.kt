package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 11

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Full-screen menus.", "The top screen now uses the whole display: Android's status bar is hidden, and Home's bar shows the battery, Wi-Fi, Bluetooth audio and Do Not Disturb next to the clock. Swipe down from the top edge to see Android's bar for a moment."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
