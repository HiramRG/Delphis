package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 34

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Achievements stay up to date.", "Progress reloads whenever you come back to Delphis, and a game matched to the wrong achievement set is corrected while you play.", fix = true),
        Item("Achievements in Diagnostics.", "Settings → Diagnostics shows Dolphin's achievement settings (on, signed in, Hardcore) and which set each game uses."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
