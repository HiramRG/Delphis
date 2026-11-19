package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 53

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Picture is a tab of the menu.", "Look and Sharpness are now a simple list next to Controls, in the same box, on one screen and on the Thor."),
        Item("Every toggle has a button.", "LB / RB switch the tabs, LT / RT switch Map / List and Look / Sharpness."),
        Item("New Change controls screen.", "Layouts on the left, a preview of the highlighted one on the right."),
        Item("Your autosave after an update.", "If the autosave can't be loaded when a game starts, Delphis now offers to load it.", fix = true),
        Item("Button icons follow your controller.", "Pick up another controller and the icons switch to it.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
