package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 60

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Saves with pictures.", "Every save now shows what was on screen when it was made, on the game page and in the game. (Android 11 and up.)"),
        Item("Tap a save to choose.", "Each save opens its own pop-up: load it, keep it under a name, rename it or delete it. Kept saves stay at the top and new autosaves never replace them."),
        Item("Name saves anywhere.", "Delphis has its own keyboard for naming saves, so it works on the Thor's bottom screen and with a controller, with suggestions and the names you used before."),
        Item("Quick save and load buttons.", "While playing: Select + L1 saves, hold Select + R1 to load the latest save. Change or turn it off in Settings → Controls."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
