package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 50

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Rumble.", "Feel the game's rumble in your controller, the phone or tablet, or the Thor. Settings → Controllers (Controls on the Thor), with a button to try it."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
