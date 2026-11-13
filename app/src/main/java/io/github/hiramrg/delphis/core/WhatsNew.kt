package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 35

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Sign in Dolphin from Delphis.", "Settings → Achievements signs Dolphin and Delphis in to RetroAchievements together. If Dolphin gets signed out, Home tells you."),
        Item("Smoother Settings.", "The row you're on is clearly lit, and the cursor stays where it was instead of starting over.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
