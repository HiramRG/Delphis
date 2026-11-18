package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 44

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Report a problem.", "Settings → Diagnostics → Report a problem: pick what went wrong, write a line, and Delphis opens a GitHub report with your device and controller filled in (nothing personal). After a crash, Home offers it too."),
        Item("Phones and tablets: the pill and the Home button.", "The Delphis pill and your controller's Home button now work while you play, and Delphis' Home offers \"Back to game\" or \"Quit game\" if you left a game open.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
