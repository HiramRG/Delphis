package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 30

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("An easier-to-see cursor.", "A thick blue ring with a soft glow and a small gap from the Wii / GameCube rim; the game under it grows a little."),
        Item("Rows start at the first game.", "Moving up or down into a row always puts the cursor on its first game.", fix = true),
        Item("Exit closes Dolphin too.", "Exiting now removes Dolphin from Recent apps, so it really closes.", fix = true),
        Item("Tidier bottom bar.", "⏻ matches the ⚙ button, with “Hold Ⓑ” next to it."),
        Item("Picture panel.", "Screen and Look are the same size, so switching tabs doesn't jump."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
