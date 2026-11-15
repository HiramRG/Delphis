package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 36

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("One achievement pop-up.", "Dolphin's own pop-up is hidden so only Delphis' badge shows (Settings → Achievements → Hide Dolphin's pop-ups). Offline, Dolphin's comes back."),
        Item("Achievements offline.", "Your progress and each game's list (with badges) still show without internet, marked Offline."),
        Item("Settings scroll both ways.", "Going back up with the D-pad scrolls the page back up.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
