package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 19

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("No more surprise Dolphin menu.", "Android's swipe gestures are turned off on the in-game bottom screen, so a swipe there can't open Dolphin's menu anymore."),
        Item("Red Steel: Nunchuk swing up.", "Click the left stick (L3) to swing the Nunchuk up. In \"+ Touch Buttons\" it's also a touch button (replacing Push, which stays on A)."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
