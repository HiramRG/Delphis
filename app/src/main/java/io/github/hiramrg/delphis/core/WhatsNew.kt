package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 43

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Your controller's Home button opens Delphis' menu.", "On phones and tablets, press ⌂ (Switch), the Xbox button or PS to open Delphis' menu over the game; the game never sees it. Settings → Display → In-game menu also offers ⊖ + Start, now in either order."),
        Item("Controllers work in Delphis' menus.", "Switch, Xbox and PlayStation controllers now use the Thor's button positions everywhere. When one connects, Delphis shows what each button does and offers a one-minute button check."),
        Item("A controller that connects during a game.", "Delphis' menu offers to restart the game so the controller works and the touch buttons go away."),
        Item("\"Close games\" reminders.", "If it's off, Home and Play explain how to turn it on (Allow restricted settings, then Accessibility).", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
