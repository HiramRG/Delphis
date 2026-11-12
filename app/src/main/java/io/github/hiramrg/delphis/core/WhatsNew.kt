package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 28

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Continue is a row.", "The top card is now a row of its own (▶ in the rail): the ring goes on it and it uses the same buttons as every game, Ⓐ Play, Ⓧ Game page, R3 Achievements."),
        Item("A bigger top card.", "Box art, the game (play time and achievements) and “Ready to play”: the controls, where it starts and the screen. Long names scroll."),
        Item("⊖ opens Settings.", "From anywhere on Home."),
        Item("Game page buttons.", "Change Ⓨ and Edit Ⓧ sit in the controls card, L2 switches Thor | List, R2 opens earlier autosaves."),
        Item("Matching Wii and GameCube icons.", "The remote and the cube now show in the rail, the rows and the tags."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
