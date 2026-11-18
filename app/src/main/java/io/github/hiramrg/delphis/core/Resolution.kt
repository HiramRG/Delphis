package io.github.hiramrg.delphis.core

/**
 * The game's internal resolution: Dolphin's [Video_Settings] InternalResolution in GameSettings/<id>.ini
 * (1 = the console's own 480p, 2 = 720p, 3 = 1080p, 4 = 1440p, 6 = 4K). Read when the game starts.
 * [Level.DOLPHIN] leaves Dolphin's own setting alone (and removes a value only if Delphis wrote it).
 */
object Resolution {
    enum class Level(val scale: Int, val label: String, val short: String, val detail: String) {
        DOLPHIN(0, "Dolphin's setting", "Dolphin", "Leave it as set in Dolphin"),
        X1(1, "1× · 480p", "1×", "The console's own picture"),
        X2(2, "2× · 720p", "2×", "Twice as sharp"),
        X3(3, "3× · 1080p", "3×", "Full HD"),
        X4(4, "4× · 1440p", "4×", "Very sharp"),
        X6(6, "6× · 4K", "6×", "Sharpest"),
    }

    /** How a level compares with what the device should manage ([recommended] is a scale, 1-6). */
    enum class Fit(val label: String) { FAST("Faster"), RECOMMENDED("★ Recommended"), MAYBE("May slow down"), SLOW("Likely too slow") }

    fun fit(level: Level, recommended: Int): Fit? = when {
        level == Level.DOLPHIN -> null
        level.scale < recommended -> Fit.FAST
        level.scale == recommended -> Fit.RECOMMENDED
        level.scale == recommended + 1 -> Fit.MAYBE
        else -> Fit.SLOW
    }

    fun byName(name: String?): Level? = Level.entries.firstOrNull { it.name == name }

    /** The level for a scale (the nearest one Delphis offers). */
    fun forScale(scale: Int): Level = Level.entries.filter { it.scale > 0 }.minBy { kotlin.math.abs(it.scale - scale) }

    /** The level the D-pad lands on: ◀ ▶ = ±1, ▲ ▼ = ±3 (the grid is 3 wide). */
    fun step(l: Level, delta: Int): Level = Level.entries[(l.ordinal + delta).coerceIn(0, Level.entries.lastIndex)]

    private const val SECTION = "Video_Settings"
    private const val KEY = "InternalResolution"
    private const val MARK = "# Delphis: resolution"

    fun applyToGameIni(existing: String, level: Level): String = IniEditor(existing).apply {
        if (level != Level.DOLPHIN) {
            set(SECTION, mapOf(KEY to level.scale.toString()))
            if (!hasLine(SECTION, MARK)) addLine(SECTION, MARK)
        } else if (hasLine(SECTION, MARK)) {
            remove(SECTION, KEY); removeLine(SECTION, MARK)
        }
    }.toString()

    /**
     * What a graphics chip should manage in Dolphin, as a scale: Adreno flagships (Snapdragon 8 Gen 2 and
     * newer) 3×; other recent flagships (Snapdragon 8 Gen 1 / 888 / 865, Dimensity 9000+, Exynos 2200+) 2×;
     * everything else 1×. [chip] is Build.SOC_MODEL / Build.HARDWARE, upper case. Null = not recognised.
     */
    fun chipScale(chip: String): Int? {
        val c = chip.uppercase()
        fun any(vararg ids: String) = ids.any { c.contains(it) }
        return when {
            any("SM8550", "SM8650", "SM8635", "SM8735", "SM8750", "SM8850", "KALAMA", "PINEAPPLE") -> 3
            any("SM8450", "SM8475", "SM8350", "SM8250", "SM8325", "SM7675", "TARO", "LAHAINA", "KONA") -> 2
            any("MT6983", "MT6985", "MT6989", "MT6991", "MT6993", "S5E9925", "S5E9945", "S5E9955", "S5E9965") -> 2
            any("SM", "MT", "S5E", "GS", "ZUMA", "TENSOR", "EXYNOS") -> 1
            else -> null
        }
    }

    /** The chip family for the explanation ("Snapdragon", "Dimensity"…), or null. */
    fun chipFamily(chip: String): String? {
        val c = chip.uppercase()
        return when {
            c.contains("SM") || c.contains("QCOM") || c.contains("KALAMA") || c.contains("PINEAPPLE") -> "Snapdragon"
            c.contains("MT") -> "MediaTek"
            c.contains("S5E") || c.contains("EXYNOS") -> "Exynos"
            c.contains("GS") || c.contains("ZUMA") || c.contains("TENSOR") -> "Tensor"
            else -> null
        }
    }

    /**
     * The recommended scale: what the chip manages, but no more than the screen can show
     * (a Wii picture is 528 lines tall, so a 1080-line screen is filled at 3×).
     */
    fun recommend(chipScale: Int, screenShortSide: Int): Int {
        val needed = kotlin.math.ceil(screenShortSide / 528.0).toInt().coerceIn(1, 6)
        return minOf(chipScale, needed).coerceAtLeast(1)
    }
}
