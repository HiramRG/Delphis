package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 64

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Each save shows once.", "A kept save or Dolphin save slot you load is no longer listed again as an autosave. It's marked as the save your game continues from.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
