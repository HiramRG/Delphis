package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 48

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Picture with the controller.", "One screen: the in-game Picture panel now works with buttons: LB / RB switch tabs, the D-pad picks, A Done, B Cancel."),
        Item("Bottom bar on one line.", "With a big interface size the button hints no longer drop to a second line: the clock makes room, and the hints shrink a little if needed."),
        Item("Touch buttons switched off from Dolphin's menu.", "When a controller is connected, Delphis turns Dolphin's touch buttons off from Dolphin's own menu once the game starts.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
