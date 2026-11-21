package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 59

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Saves with pictures.", "Every save now shows what was on screen when it was made, in the Saves list on the game page and in the game. (Android 11 and up.)"),
        Item("Keep your favourite saves.", "Press Y on a save (or long-press it) to keep it and give it a name. Kept saves stay at the top and new autosaves never replace them. Up to 10 per game."),
        Item("Quick save and load buttons.", "While playing: Select + L1 saves, hold Select + R1 to load the latest save. A small pop-up confirms it. Change or turn it off in Settings → Controls."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
