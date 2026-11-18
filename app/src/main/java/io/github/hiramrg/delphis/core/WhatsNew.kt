package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 45

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Delphis check.", "Settings → Diagnostics lists everything Delphis needs, each with a Fix button. A dot on ⚙ and one notice tell you when something needs attention."),
        Item("One in-game menu.", "The same menu on the Thor's bottom screen and over the game on phones and tablets: Controls, Achievements and Picture, then Change, Edit, Restart and Quit game."),
        Item("Hints for your controller.", "On phones and tablets the button hints show your controller's own buttons (Xbox, PlayStation, Switch). With touch controls they're left out."),
        Item("Interface size.", "Settings → Display: Automatic makes Delphis fill a tablet the way it fills the Thor's screen."),
        Item("Beta updates.", "Settings → Updates → Update channel: Beta gets new versions first, to try them."),
        Item("Every word fits.", "Home's card, the game page and Report a problem no longer cut words off. Quitting a game on one screen no longer leaves a hidden prompt on Home.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
