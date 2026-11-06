package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 2

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Loading screen.", "While Delphis saves, closes or starts a game, a loading screen covers Dolphin's menus (needs the \"close games\" helper)."),
        Item("Easier with buttons.", "The Library column button is reachable with the D-pad (or press L1), and everything you move onto gets a blue outline."),
        Item("Search on your terms.", "The keyboard only opens when you press Ⓐ on the search box; Ⓑ closes it."),
        Item("Auto theme.", "Settings → Appearance now has Light, Dark and Auto (follows Android)."),
        Item("Softer corners", "on the Library column and the bottom bar."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
