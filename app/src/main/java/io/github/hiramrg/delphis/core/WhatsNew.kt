package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 29

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Looks.", "Give each game its own look: Sharp, Vivid, Scanlines, CRT or Soft. Pick it from the Picture chip on the game page (▭ Screen and ✦ Look share one panel; L1 / R1 switch) or ✦ on the bottom screen in game. Default and on/off in Settings → Display."),
        Item("Exit Delphis.", "⏻ in Home's bottom bar, or hold Ⓑ on Home: closes Delphis on both screens and Dolphin too."),
        Item("The bottom bar on the game page.", "Clock and battery stay when you open a game; ◀ Home Ⓑ moved into the bar."),
        Item("Game names in game.", "On the bottom screen the name has its own line; long names scroll."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
