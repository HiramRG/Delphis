package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 21

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Bigger, simpler editor.", "Much bigger tiles under \"Choose a Wii button or move\" (\"GameCube button\" for GameCube games). Cancel · Undo · Save sit under the action list on the top screen and at the bottom of the in-game screen."),
        Item("Combos.", "Switch to Combo to make one Thor button do several things at once, like Red Steel's zoom: tap a slot (A + ⤒ push + ＋), then a tile."),
        Item("⇄ Swap.", "Trade two actions' buttons in one tap."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
