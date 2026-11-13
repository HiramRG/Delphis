package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 33

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("A Wii-style bottom bar.", "A thin line instead of a pill, with the clock in the middle: big time, small date."),
        Item("A breathing cursor.", "The blue ring slowly pulses so it's easy to spot on any screen."),
        Item("Channel zoom.", "Pages zoom in when you open them and settle back when you return Home."),
        Item("Menu sounds.", "Soft clicks and chimes, off by default: Settings → Sound."),
        Item("Smoother rows.", "Moving between rows on Home no longer jumps and settles back.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
