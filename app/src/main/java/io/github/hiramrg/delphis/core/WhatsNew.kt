package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 46

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Controllers page.", "Phones and tablets: Settings → Controllers shows each controller, its player and whether its buttons were checked."),
        Item("Add a controller, step by step.", "Pick Xbox, PlayStation, Switch or another, see which button to hold for pairing, and Delphis moves on by itself when it connects."),
        Item("A menu button that works.", "Settings → Controllers: open Delphis' menu with the Xbox / PS / Home button, View + Menu, or both (the new default), and test it."),
        Item("No more fake \"AYN Thor\" player.", "Off the Thor, the first controller is Player 1; a handheld's own controls go by its name. The controls map shows a plain controller.", fix = true),
        Item("Touch controls go away with a controller.", "On Android 14 and newer, Delphis restarts Dolphin when the touch controls need to change.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
