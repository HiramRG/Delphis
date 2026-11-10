package io.github.hiramrg.delphis.core

/**
 * The notes shown once after an update. When a release has user-facing changes, bump [RELEASE]
 * and replace [items]; people who already saw this release's notes won't see them again.
 */
object WhatsNew {
    const val RELEASE = 22

    data class Item(val title: String, val text: String, val fix: Boolean = false)

    val items = listOf(
        Item("Bottom-screen brightness.", "The ☀ button in game now sets the bottom screen's normal brightness (when it isn't dimmed). The dimmed level stays in Settings → Bottom screen."),
    )

    /** Show after an update (never on a fresh install) until this release's notes were seen. */
    fun shouldShow(seenRelease: Int, isUpdate: Boolean) = isUpdate && seenRelease < RELEASE
}
