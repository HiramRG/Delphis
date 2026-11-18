package io.github.hiramrg.delphis.ui

/**
 * One screen (phones, tablets): Delphis' in-game menu. It's the Thor's in-game bottom screen (controls,
 * achievements, picture, change / edit preset, quit) shown as a panel over the game. It opens behind the
 * game when the game starts and comes to the front from the Delphis pill or ⊖ + Start; Ⓑ, ▶ Resume or a
 * tap on the game hides it again.
 */
class InGameMenuActivity : NowPlayingActivity() {
    companion object {
        /** Opened from the pill or ⊖ + Start (not a game starting). */
        const val EXTRA_OPEN = "delphis.open_menu"
        /** Delphis' Home asked to quit the game that's running in the background. */
        const val EXTRA_QUIT = "delphis.quit_game"
    }

    override val panelMode: Boolean = true
}
