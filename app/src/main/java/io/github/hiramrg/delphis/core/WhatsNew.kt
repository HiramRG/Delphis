package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 62

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("A tidier game page.", "Buttons and chips now share one set of sizes. Autosave and Remember moved to the bottom bar, and Saves and Play line up under the cover."),
        Item("Nothing cut with big text.", "Delphis follows Android's text size up to 115%, so every button and label stays whole on phones and tablets.", fix = true),
        Item("No more duplicate saves.", "The same save no longer shows twice, and the duplicate Sideways Wii Remote preset is gone.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
