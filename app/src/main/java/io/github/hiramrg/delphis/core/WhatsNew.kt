package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 9

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Play from your frontend.", "Start Wii and GameCube games from frontends that let you add a custom launch command, like RetroHrai!, and still get Delphis' controls and the bottom-screen map. Home takes you back to the frontend. Settings → Frontends has the command to paste."),
        Item("No more cut-off labels.", "Long button descriptions on the controls map now wrap onto a second line instead of being cut off.", fix = true),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
