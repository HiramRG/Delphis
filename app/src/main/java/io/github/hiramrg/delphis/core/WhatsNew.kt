package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 37

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Update with the buttons.", "When a new version is out, Delphis asks once (Ⓐ Download). After that, R2 on Home downloads it, then restarts to install it."),
        Item("Smooth rows.", "Moving up and down between rows on Home is one smooth move, with no bounce at the end.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
