package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 54

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("One button rule everywhere.", "A confirms and B goes back on every screen, pop-up and the in-game menu, by the letters on your controller (✕ and ○ on PlayStation)."),
        Item("Icons follow Player 1.", "The buttons shown on screen are always Player 1's controller."),
        Item("Pop-ups answer the right buttons.", "B closes only the pop-up that's open; buttons a screen doesn't use do nothing.", fix = true),
        Item("Button letters centred.", "Letters and symbols sit exactly in the middle of their circles.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
