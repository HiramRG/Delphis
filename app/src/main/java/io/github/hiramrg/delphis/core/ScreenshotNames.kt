package io.github.hiramrg.delphis.core

/** Where screenshots go (Pictures/Delphis/<game>) and what they're called, and finding a picture's black side bars. */
object ScreenshotNames {
    const val ROOT = "Pictures/Delphis"

    /** A folder name from the game's title: no characters folders can't have, at most 60 long. */
    fun folder(title: String?): String =
        (title ?: "").replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), " ").replace(Regex("\\s+"), " ").trim().trim('.').take(60).trim().ifEmpty { "Game" }

    fun relativePath(title: String?) = "$ROOT/${folder(title)}/"

    /** "New Super Mario Bros. Wii 2026-11-22 10-57-03.png" */
    fun fileName(title: String?, at: java.util.Date): String =
        folder(title) + " " + java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss", java.util.Locale.US).format(at) + ".png"

    /**
     * The game picture inside a screenshot: [dark] says, per column, whether it's (nearly) black top to bottom.
     * Returns the first and last column to keep; the whole width when the bars are too thin to matter (< 3%).
     */
    fun gameColumns(dark: BooleanArray): IntRange {
        val n = dark.size
        if (n == 0) return IntRange.EMPTY
        val first = dark.indexOfFirst { !it }
        if (first < 0) return 0 until n
        val last = dark.indexOfLast { !it }
        val min = (n * 0.03).toInt()
        return if (first < min && n - 1 - last < min) 0 until n else first..last
    }
}
