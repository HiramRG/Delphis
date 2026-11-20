package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 56

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Saves.", "Pick which save to play from on the game page (R2), or load one and save now from the new Saves tab in game. Dolphin's own save slots are listed too."),
        Item("Badge time.", "Achievement badges stay 7 seconds, and Settings → Achievements has a slider (3–15 s) with an example."),
        Item("Sliders work with the D-pad.", "Left / right move a slider, up / down go to the next setting.", fix = true),
        Item("No more silent \"Load it\".", "If Delphis' helper has stopped, Delphis says so and opens Accessibility to turn it back on. The rumble test can't crash Delphis any more.", fix = true),
        Item("Icons look the same everywhere.", "Every emoji and symbol is now an icon drawn by Delphis, in the theme's colours, so no device shows a box or a different picture.", fix = true),
        Item("Easier to spot cursor.", "The breathing ring has a strip of background around the button, so it stands out even on blue buttons."),
        Item("One button rule everywhere.", "A confirms and B goes back on every screen, pop-up and the in-game menu, by the letters on your controller."),
        Item("Icons follow Player 1.", "The buttons shown on screen are always Player 1's controller."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
