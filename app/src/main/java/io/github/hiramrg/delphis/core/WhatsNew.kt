package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 6

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Button map while you play.", "The in-game bottom screen now shows the same map of the Thor's buttons as the game's page."),
        Item("Temperature at a glance.", "The bar at the top of the in-game bottom screen shows how warm the Thor is, next to the battery. It turns orange when the Thor gets hot."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
