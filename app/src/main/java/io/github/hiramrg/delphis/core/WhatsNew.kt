package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 14

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Trackpad no longer triggers Android's gestures.", "Aiming near the edges used to open Recents or go Back. The trackpad now stays clear of Android's gesture edges and still reaches the whole picture.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
