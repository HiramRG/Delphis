package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 42

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Phones and tablets.", "Delphis now works on one screen. A small Delphis pill at the top of the game (or ⊖ + Start) opens its menu over the game: controls, achievements, picture, change controls and quit."),
        Item("Touch controls when there's no controller.", "On one screen, Dolphin's on-screen buttons come back until you connect a controller. Delphis asks once when it opens without one."),
        Item("Settings → Display → Screens.", "Choose one or two screens, and set the pill's opacity, when its full version shows, or turn it off."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
