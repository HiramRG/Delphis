package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 40

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("A simpler bottom screen.", "Bigger and easier to read: the game on a blurred banner, the controls you're using (Change Ⓧ), and three facts: achievements, time played and your autosave."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
