package io.github.hiramrg.delphis.core

/**
 * Which games start with "Autosave & resume" off. A Dolphin save state also stores the console's
 * clock, so resuming one puts a game back at the time it was saved. Games that follow the real
 * clock (Animal Crossing) should boot normally instead, so they read the current time.
 * The player can still switch autosave on or off for any game on its page.
 */
object AutosaveRules {
    /** Game ID prefixes (first three characters, any region). */
    private val REAL_TIME_GAMES = setOf(
        "GAF", // Animal Crossing (GameCube; also Doubutsu no Mori+)
        "GAE", // Doubutsu no Mori e+
        "RUU", // Animal Crossing: City Folk / Let's Go to the City
    )

    fun offByDefault(gameId: String?): Boolean =
        gameId != null && gameId.length >= 3 && gameId.substring(0, 3).uppercase() in REAL_TIME_GAMES
}
