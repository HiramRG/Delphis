package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 17

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Red Steel presets.", "Two new presets made for Red Steel: aim with the trackpad, hold L1 to zoom, flick the right stick to slash, and the Nunchuk's motions are on Y and X. \"+ Touch Buttons\" adds Zoom, Reload, Grenade and Push beside the trackpad."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
