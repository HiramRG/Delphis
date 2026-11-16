package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 39

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("A fuller bottom screen.", "While you browse, the bottom screen shows the game's banner with either its controls or your progress (achievements, play time, sessions and your autosave's date). L3 switches between them, L2 flips the controls between Thor and List."),
        Item("Favorite on Ⓨ.", "The bottom screen's Favorite button now shows its button, and the big one says Play Ⓐ."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
