package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 52

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("One Picture panel layout.", "Look and Sharpness now share the same grid of picture cards, the same lines and the same buttons, so nothing moves when you switch tabs."),
        Item("Dolphin closes when Delphis is swiped away.", "Swiping Delphis away in Recent apps now closes Dolphin too (unless a game is running).", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
