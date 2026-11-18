package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 47

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("A button for everything in the in-game menu.", "One screen: Resume, Change controls, Restart and Quit game each have their own controller button, shown on the button itself."),
        Item("Sharpness before you play.", "Picture → Sharpness picks the game's resolution, from 1× to 4K. Delphis marks the level your device should handle and warns about the rest."),
        Item("Touch controls really go away now.", "Dolphin is restarted whenever its touch setting has to change, also when it was sitting in its game list.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
