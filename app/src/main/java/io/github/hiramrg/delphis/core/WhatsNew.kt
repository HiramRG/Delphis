package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 31

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("A proper goodbye.", "Exiting shows “Closing Delphis and Dolphin” on both screens while everything closes."),
        Item("Back from Change and Edit.", "◀ Back Ⓑ closes the preset list or the editor and returns to the game page; leaving the editor with unsaved changes asks first."),
        Item("Clearer hints.", "In Settings the bar reads ✚ Choose · Ⓐ Select · Ⓑ Back.", fix = true),
        Item("An easier-to-see cursor, rows that start at the first game, and exit that closes Dolphin too.", ""),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
