package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 20

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("New preset editor.", "Pick an action, then press the Thor button you want for it. Underneath, choose what it does in the game from Buttons, Wii Remote moves and Nunchuk moves: every motion is there now. On the in-game bottom screen, tap an action and pick from the same tiles."),
        Item("Cleaner controls map.", "No more connector lines: every label starts with its button's own badge."),
        Item("Red Steel.", "Zoom is now on L2 and crouch on L1."),
        Item("Tidier Home.", "The \"All games\" tile in Recently played is gone (the full list is right below)."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
