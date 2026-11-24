package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 63

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Saves that don't interrupt.", "With a Dolphin development build from November 2026 or newer, Delphis saves through Dolphin's own save button: the game keeps running and nothing opens. Settings → Saves shows whether your Dolphin can."),
        Item("Timed saves are off for now.", "They had to open Dolphin's menu in the middle of a game. Saves when you open the menu, load or quit work as before.", fix = true),
        Item("Each save shows once.", "A save you load keeps its real time and is no longer listed twice.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
