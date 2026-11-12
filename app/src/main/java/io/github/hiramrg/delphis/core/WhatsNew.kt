package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 27

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("One look everywhere.", "Settings, setup, Controller check, Diagnostics, the gyro test, the Quick menu and every pop-up now use the same cards, toggles, buttons and button icons as Home."),
        Item("Thor | List.", "The controls card has a switch on every game: the Thor diagram, or a clean two-column list. Busy presets open on the list so the text stays readable; your choice is remembered per game."),
        Item("Settings with a rail.", "Line icons, a lit bar for the page you're on, and grouped cards. The clock and battery stay in the bottom bar."),
        Item("Delphis notices.", "Saves and problems show as a card at the top, with a button when there's a fix (Ⓨ presses it)."),
        Item("Friendlier setup and empty screens.", "Setup walks through each step with plain words; screens with nothing to show tell you why and how to fix it."),
        Item("Fewer accidents.", "Ⓑ never closes Delphis from Home, and deleting or resetting always asks once (Ⓐ Yes / Ⓑ Cancel)."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
