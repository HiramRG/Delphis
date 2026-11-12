package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 25

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Wii and GameCube rows.", "Home shows Recently played, Wii, GameCube, Favorites and All games as rows. Covers have a white (Wii) or purple (GameCube) rim. The row you're on grows, the top card previews the game under the ring, and Ⓐ plays it."),
        Item("Sort each row.", "On a row, R1 switches between Last played, A–Z, Most played and Recently added. Each row remembers its own order."),
        Item("Achievements on the continue card.", "R3 opens the list."),
        Item("More accurate play time.", "Time only counts while the screen is on and unlocked and the game is open, not while the Thor sleeps or is locked.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
