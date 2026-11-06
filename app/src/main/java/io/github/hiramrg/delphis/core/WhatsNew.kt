package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 5

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("See what every button does.", "A game's page now shows a map of the Thor with what each button does in that game, next to a picture of the controller it plays like."),
        Item("Clearer buttons.", "L1 turns Autosave on or off, R1 turns Remember controls on or off (on by default), and Y opens earlier autosaves."),
        Item("No more accidental HOME menu.", "Clicking the left stick no longer opens the Wii's HOME menu, which could reset or close the game."),
        Item("Smoother menus.", "Moving up on Home scrolls all the way back to Continue playing, Play and Controls show the blue ring when selected, and Settings has rounded edges.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
