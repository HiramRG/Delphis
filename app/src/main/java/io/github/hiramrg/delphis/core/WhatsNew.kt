package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 57

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Safer.", "Updates are checked before installing (from GitHub, signed by Delphis). Shared reports hide your account name, and your RetroAchievements key is stored encrypted."),
        Item("Built for Android 16.", "Fewer warnings when installing on newer phones and tablets. Nothing changes on the Thor."),
        Item("The helper looks at less.", "To close Dolphin it only reads the Recent apps screen, and reports no longer name other apps.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
