package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 68

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Both screens, same brightness.", "The bottom screen now matches the top screen when a game starts, and follows it if you change it. Pick your own level with ☀ in game.", fix = true),
        Item("Smoother brightness slider.", "It uses the same curve as Android's slider, so 40 on the bottom looks like 40 on the top."),
        Item("Gentler Auto-dim.", "Choose how dim (Light, Medium or Strong), it lights up for achievements, and you can turn it off for good in games where you watch the bottom screen."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
