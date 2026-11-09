package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 12

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Trackpad aiming (Experimental).", "For pointer games (Metroid Prime 3, Galaxy, Twilight Princess…): pick \"Trackpad aiming (Experimental)\" and slide a finger on the bottom screen to aim, like a laptop trackpad. First time only: force-stop Dolphin in Android's settings so it picks up the trackpad setting."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
