package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 49

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("New Look cards.", "Each look is shown on a little game scene, with the chosen one glowing blue."),
        Item("Apply & restart, or Next time.", "In the game's Picture panel: apply a change now (the game restarts after an autosave) or keep it for the next time you play."),
        Item("Dolphin closes with Delphis.", "Exiting Delphis now checks that Dolphin's card is really gone from Recent apps, and tries again if not.", fix = true),
        Item("Exit button.", "The empty box next to \"Exit\" is gone.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
