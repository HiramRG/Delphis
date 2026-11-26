package io.github.hiramrg.delphis.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Brightness levels, as Android does them.
 *  - A *level* (0..1) is what a window's screenBrightness and the system's brightness float use (linear light).
 *  - A *percent* (0..100) is where Android's own brightness slider sits: the same curve (HLG), so equal slider
 *    steps look like equal changes, and "38" in Delphis means what 38 on the top screen's slider means.
 */
object Brightness {
    private const val R = 0.5f
    private const val A = 0.17883277f
    private const val B = 0.28466892f
    private const val C = 0.55991073f

    /** Never fully off (0 turns some panels' backlight off). */
    const val MIN_LEVEL = 0.004f

    /** Like Android, the curve runs from [MIN_LEVEL] (0 on the slider) to full (100). */
    fun percentOf(level: Float): Int {
        val n = ((level - MIN_LEVEL) / (1f - MIN_LEVEL)).coerceIn(0f, 1f) * 12f
        val g = if (n <= 1f) R * sqrt(n) else A * ln(n - B) + C
        return (g * 100f).roundToInt().coerceIn(0, 100)
    }

    fun levelOf(percent: Int): Float {
        val g = percent.coerceIn(0, 100) / 100f
        val l = if (g <= R) (g / R) * (g / R) / 12f else (exp((g - C) / A) + B) / 12f
        return (MIN_LEVEL + (1f - MIN_LEVEL) * l).coerceIn(MIN_LEVEL, 1f)
    }

    /** The system's 0..255 SCREEN_BRIGHTNESS as a level, the way Android converts it (1 = minimum, 255 = full). */
    fun levelOfSetting(value: Int): Float = ((value - 1) / 254f).coerceIn(MIN_LEVEL, 1f)

    /** How much "Dim" lowers the screen, as a share of the normal level. */
    enum class Strength(val id: String, val label: String, val factor: Float) {
        LIGHT("light", "Light", 0.6f), MEDIUM("medium", "Medium", 0.3f), STRONG("strong", "Strong", 0.1f)
    }

    fun strengthOf(id: String?): Strength? = Strength.entries.firstOrNull { it.id == id }

    /** The old fixed "Dimmed level" (0..100 of full) turned into the nearest strength. */
    fun strengthFromOldLevel(old: Int): Strength = when {
        old >= 40 -> Strength.LIGHT
        old >= 8 -> Strength.MEDIUM
        else -> Strength.STRONG
    }

    fun dimmed(normal: Float, strength: Strength): Float = (normal * strength.factor).coerceIn(MIN_LEVEL, 1f)

    /** The old saved bottom level was a straight 0..100 of full; on the new slider it sits where that light is. */
    fun percentFromOldLinear(old: Int): Int = percentOf(old / 100f)
}
