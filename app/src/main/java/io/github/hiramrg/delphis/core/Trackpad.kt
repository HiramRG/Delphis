package io.github.hiramrg.delphis.core

/**
 * Bottom-screen trackpad settings (pure, unit-tested). The pointer position itself lives in
 * platform/DsuServer, which serves it to Dolphin as a DSU pad's touchpad (see [Dsu]).
 */
object Trackpad {
    enum class Speed(val id: String, val label: String, val factor: Float) {
        SLOW("slow", "Slow", 0.6f), NORMAL("normal", "Normal", 1f), FAST("fast", "Fast", 1.6f);
        companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: NORMAL }
    }

    /** At Normal speed, sliding this far (dp) on the pad moves the pointer across the whole picture. */
    const val FULL_SWEEP_DP = 260f

    /** A tap: shorter than this (ms) and moving less than [TAP_SLOP_DP]. Two taps within [DOUBLE_TAP_MS] recenter. */
    const val TAP_MS = 250L
    const val TAP_SLOP_DP = 10f
    const val DOUBLE_TAP_MS = 420L

    /**
     * Pointer change ([-1, 1] range) per pad dp, across and down: at Normal speed sliding [FULL_SWEEP_DP]
     * crosses the whole picture; vertically the same finger distance moves the pointer the same
     * on-screen distance on a 16:9 picture.
     */
    fun normalizedGain(speed: Speed): Pair<Float, Float> {
        val gx = 2f / FULL_SWEEP_DP * speed.factor
        return gx to gx * 16f / 9f
    }
}
