package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 23

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("A cleaner game page.", "Achievements, screen, Autosave and Remember sit in one row of chips; the controls card shows the map, with Change › for the list and ⋯ for more. Edit and Play live under the cover."),
        Item("Choosing controls.", "Presets are in sections that don't fold (Suggested, My presets, Gyro, Trackpad, Other): just scroll. The blue ring shows where you are and the map previews it; the preset in use is tinted with a green ✓ Selected. Ⓐ picks the one under the ring."),
        Item("Saving your edits.", "Save asks whether to update the preset for this game (an amber dot marks it as edited) or save it as a new preset under My presets. ↺ Reset to default (in the editor or ⋯) brings the original back. The map always shows your changes."),
        Item("Trackpad presets are no longer experimental.", ""),
        Item("GameCube widescreen.", "Each GameCube game now has a Screen switch under its cover: 16:9 shows more of the world (with Dolphin's own widescreen code for the game when there is one, otherwise its widescreen hack) or stretches; 4:3 keeps the original picture with black, patterned or cover-coloured sides. Defaults in Settings → Display; change the sides live from the ▭ button in game."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
