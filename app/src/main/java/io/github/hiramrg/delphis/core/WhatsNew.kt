package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 65

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("A cover while saving and loading.", "When Delphis has to use Dolphin's menu to save or load, the game is covered by a short \"Saving\" or \"Loading save\" screen, so the menu never flashes by."),
        Item("Clearer Saves list.", "After loading, where you were is labelled \"Before loading\" with its picture, and the save you loaded is marked \"Loaded\".", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
