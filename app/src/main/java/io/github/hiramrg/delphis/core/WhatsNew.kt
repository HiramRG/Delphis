package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 69

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Wii Sports and Wii Sports Resort presets.", "Swing with the right stick, aim menus with the left. Resort adds twists on L1 / R1 and a Tilt the Thor option. Also for the disc with both games."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
