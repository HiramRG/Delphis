package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 51

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Picture: move, then pick.", "The D-pad only moves the blue glow over the Look and Sharpness cards; A picks. Apply & restart is on the Menu button (Done on the game page)."),
        Item("Menu buttons stay in the menu.", "A button pressed in Delphis' in-game menu no longer also reaches the game.", fix = true),
        Item("Closing Dolphin, traced.", "Exiting Delphis now logs each step of closing Dolphin in Diagnostics.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
