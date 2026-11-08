package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 10

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Calmer bottom screen.", "While you play, the bottom screen now waits 10 seconds before dimming, fades instead of snapping, and every touch keeps it bright for the full time again.", fix = true),
        Item("Turn the bottom screen off.", "Tap the brightness pill in game: you can turn Auto-dim off just for this game, or turn the bottom screen off (tap it to wake it). Settings → Bottom screen lets you choose Always on, Dim or Screen off, and how long it waits."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
