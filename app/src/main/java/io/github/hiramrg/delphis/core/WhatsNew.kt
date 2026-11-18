package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 41

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("More than one games folder.", "Settings → Library lists all your games folders. Add one for Wii and one for GameCube (or as many as you like); all their games show together on Home."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
