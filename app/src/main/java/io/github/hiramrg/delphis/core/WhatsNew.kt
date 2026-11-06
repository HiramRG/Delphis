package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 3

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Earlier autosaves.", "Before each autosave, Delphis now keeps the one before it (up to 3 per game). If a save ever won't load, press R1 on the game's page to go back to an earlier one."),
        Item("Space under control.", "Settings → Closing games shows how much space they use, with a button to delete them."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
