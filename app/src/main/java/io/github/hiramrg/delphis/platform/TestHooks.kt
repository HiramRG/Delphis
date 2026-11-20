package io.github.hiramrg.delphis.platform

/**
 * Set by the layout check (unit tests that draw every screen) so Delphis never goes online there:
 * no update check, cover or name downloads, or RetroAchievements calls while a test screen is drawn.
 */
object TestHooks {
    @Volatile @JvmField var offline = false

    /** Endless animations (the breathing cursor, scrolling titles) stand still: the layout check reads a still screen. */
    @Volatile @JvmField var still = false

    /** The layout check opens the game page's Picture panel on this tab (2 = Resolution); null = closed. */
    @Volatile @JvmField var pictureTab: Int? = null

    /** The layout check opens the in-game menu on Change controls. */
    @Volatile @JvmField var inGameChange = false

    /** The layout check opens the in-game menu on the Saves tab, with these saves and the helper [helperUp] or stopped. */
    @Volatile @JvmField var savesTab = false
    @Volatile @JvmField var saves: List<DolphinBridge.SaveEntry> = emptyList()
    @Volatile @JvmField var helperUp = false
    /** The layout check opens the game page's Saves list ([saves]) / shows Home's "helper has stopped" banner. */
    @Volatile @JvmField var savesDialog = false
    @Volatile @JvmField var helperStopped = false

    /** The layout check pretends this controller is connected (one screen): vendor id, e.g. 0x045e = Xbox. */
    @Volatile @JvmField var padVendor: Int? = null

    /** Every download calls this first: throws (like being offline) while [offline] is set. */
    fun net() { if (offline) throw java.io.IOException("offline (layout check)") }
}
