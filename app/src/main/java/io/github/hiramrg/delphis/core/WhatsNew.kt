package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 23

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("GameCube widescreen.", "Each GameCube game now has a Screen switch under its cover: 16:9 shows more of the world (with Dolphin's own widescreen code for the game when there is one, otherwise its widescreen hack) or stretches; 4:3 keeps the original picture with black, patterned or cover-coloured sides. Defaults in Settings → Display; change the sides live from the ▭ button in game."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
