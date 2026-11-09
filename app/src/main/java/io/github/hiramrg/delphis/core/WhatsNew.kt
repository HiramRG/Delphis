package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 13

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Steadier trackpad aiming.", "The trackpad no longer drifts or jumps after a while. Force-stop Dolphin once more (Android Settings → Apps → Dolphin → Force stop) so it picks up the new touch setting.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
