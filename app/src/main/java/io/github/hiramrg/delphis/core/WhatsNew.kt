package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 26

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("A new Home.", "Rows for Recently played, Wii, GameCube, All games and Favorites. The card at the top stays put and previews the game under the ring (play time, achievements, controls, autosave, screen); the row you're on sits fully under it. Ⓐ plays."),
        Item("A rail that follows you.", "The icons on the left light up for the row you're on. Pick one to jump there, pick it again for the full list. L1 shows names; 🔍 searches."),
        Item("Favorites.", "Pick favorites with the “+” at the end of the rows, or add one of the suggestions from your most played games."),
        Item("Sort in the bar.", "On a row, R1 switches between Last played, A–Z, Most played and Recently added; the bottom bar shows the current one. Each row remembers its own."),
        Item("Matching button icons, Wii / GameCube rims, achievements on the continue card.", ""),
        Item("More accurate play time.", "Time only counts while the screen is on and unlocked and the game is open.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
