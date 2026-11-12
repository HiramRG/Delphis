package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 24

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Screen picker with buttons.", "On a GameCube game, ⊖ (or the ▭ chip) opens it: L1 / R1 switch 4:3 and 16:9, ◀ ▶ picks black bars, pattern or colour (4:3) or more of the world / stretch (16:9). Ⓐ Done, Ⓑ Cancel."),
        Item("Restart & update.", "Updates download in the background. When one is ready, Restart & update closes Delphis, installs and opens it again by itself; Later keeps a banner until you're ready."),
        Item("Dolphin's on-screen buttons stay hidden.", "GameCube games no longer show Dolphin's touch buttons. If they still appear once, force-stop Dolphin one time.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
