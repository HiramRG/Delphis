package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 1

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("One scrolling home.", "Continue playing, recently played and your whole library on one screen, with a Library column you can hide with L1."),
        Item("Layout pictures.", "Each control layout shows how you hold the controller: sideways remote, remote + Nunchuk, wheel and more."),
        Item("Simpler gyro.", "Two gyro styles; invert up/down and left/right in Settings."),
        Item("Fixed:", "the Wii pointer no longer sits in the middle of the screen and steals the D-pad's selection in menus (New Super Mario Bros. Wii's \"Exit Course\", Mario Kart Wii and more).", fix = true),
        Item("Fixed:", "Super Mario Galaxy 2 shows its proper name.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
