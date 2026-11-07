package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 7

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("See which controls are selected.", "The bottom screen now shows the picture of the selected controls when you browse games, marked \"Selected controls\"."),
        Item("Select and Start on the map.", "The button map now shows Select and Start on the Thor, in the same order as on the device."),
        Item("A clearer way home.", "In a game, the bottom screen's button now says Home, and the game's name sits in the middle."),
        Item("Back to the top.", "On Home, moving up to Play or Controls now scrolls all the way up so the whole Continue playing card shows.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
