package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 55

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Icons look the same everywhere.", "Every emoji and symbol is now an icon drawn by Delphis, in the theme's colours, so no device shows a box or a different picture.", fix = true),
        Item("Easier to spot cursor.", "The breathing ring has a strip of background around the button, so it stands out even on blue buttons."),
        Item("One button rule everywhere.", "A confirms and B goes back on every screen, pop-up and the in-game menu, by the letters on your controller."),
        Item("Icons follow Player 1.", "The buttons shown on screen are always Player 1's controller."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
