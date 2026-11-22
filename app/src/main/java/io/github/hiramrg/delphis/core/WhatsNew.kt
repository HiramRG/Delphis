package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 61

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Automatic saves.", "New Settings → Saves: save every 10, 20 or 30 minutes while you play, when the battery gets low, and (being tested) when the screen turns off."),
        Item("Screenshots.", "Tap 📷 on the bottom screen or press Select + Y: the game goes to your Photos, in Pictures/Delphis. See them from the game's Saves window."),
        Item("Back up and move.", "New Settings → Backup: one file with your presets, kept saves, settings, favorites and play time, for a new device or after reinstalling."),
        Item("Tap a save to choose.", "Each save opens its own pop-up: load it, keep it under a name, rename it or delete it."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
