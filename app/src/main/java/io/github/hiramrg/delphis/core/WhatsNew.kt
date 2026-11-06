package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 4

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Autosave per game.", "Each game's page now has an Autosave switch (L1). Turn it off for games you'd rather save yourself."),
        Item("Animal Crossing keeps real time.", "Animal Crossing starts with autosave off, so its clock always matches the real one. Save in the game before closing it."),
        Item("Earlier autosaves.", "Delphis keeps the last 3 autosaves per game. If a save won't load, press R1 on the game's page to go back to an earlier one."),
        Item("Cleaner covers.", "The blue frame around covers is gone; the only blue ring is the one you move with the D-pad."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
