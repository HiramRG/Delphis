package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 58

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Clearer menu pill.", "On phones and tablets the pill now says \"Open Delphis' menu\" and which buttons to press, for 5 seconds. Choose every game start, once per game or never in Settings → Display."),
        Item("Faster exit.", "Exit no longer tries to remove Dolphin from Recent apps. Your game is still saved and closed first.", fix = true),
        Item("Safer.", "Updates are checked before installing (from GitHub, signed by Delphis). Shared reports hide your account name, and your RetroAchievements key is stored encrypted."),
        Item("Built for Android 16.", "Fewer warnings when installing on newer phones and tablets. Nothing changes on the Thor."),
        Item("The helper looks at less.", "To close Dolphin it only reads the Recent apps screen, and reports no longer name other apps.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
