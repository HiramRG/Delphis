package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 18

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Trackpad edges fixed.", "Swiping near the bottom screen's left or right edge no longer opens Dolphin's menu: the trackpad stays clear of Android's back-gesture edges."),
        Item("Gyro and Trackpad folders.", "Preset lists now keep gyro and trackpad presets in their own folders."),
        Item("Cleaner controls map.", "Long labels (like Red Steel's) no longer overlap: they wrap or get a little smaller."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
