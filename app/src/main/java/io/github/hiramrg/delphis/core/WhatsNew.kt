package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 66

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Play stats.", "Settings → Play stats: this week's play time by day, your most played games and their sessions, and Reset for a game's time."),
        Item("Next up achievements.", "The in-game Achievements tab has its own buttons now. \"Next up\" lists the ones you haven't unlocked, the ones most players have first, marked Common to Very rare."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
