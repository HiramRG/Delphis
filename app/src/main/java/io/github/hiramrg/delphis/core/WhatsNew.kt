package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 17

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("RetroAchievements.", "Connect your account in Settings → Achievements: a badge pops up when you unlock one (top screen, bottom screen or both), and a 🏆 button shows each game's list with your progress. Dolphin still does the unlocking, so sign in there too."),
        Item("Red Steel presets.", "Two new presets made for Red Steel: aim with the trackpad, hold L1 to zoom, flick the right stick to slash, and the Nunchuk's motions are on Y and X. \"+ Touch Buttons\" adds Zoom, Reload, Grenade and Push beside the trackpad."),
        Item("One gyro preset per game.", "Games that offered two gyro presets now offer just \"Gyro Aiming\" (the precise one: hold R1 to re-position). Games set to the old one switch to it by themselves."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
