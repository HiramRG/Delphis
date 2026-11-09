package io.github.hiramrg.delphis.core

/**
 * Bottom-screen trackpad maths (pure, unit-tested). The pointer position is kept in top-screen pixels
 * from the centre of the game picture, clamped to the picture, so relayed touches always stay on
 * Dolphin's game screen and Dolphin's Drag-mode pointer moves by exactly the same amount.
 */
object Trackpad {
    enum class Speed(val id: String, val label: String, val factor: Float) {
        SLOW("slow", "Slow", 0.6f), NORMAL("normal", "Normal", 1f), FAST("fast", "Fast", 1.6f);
        companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: NORMAL }
    }

    /** At Normal speed, sliding this far (dp) on the pad moves the pointer across the whole picture. */
    const val FULL_SWEEP_DP = 260f

    /** Dolphin presses a button when its touch screen is touched twice within 300 ms; stay clear of it. */
    const val MIN_DOWN_GAP_MS = 330L

    /** After the finger lifts, the relayed touch stays down this long, so a quick re-touch continues it. */
    const val LIFT_GRACE_MS = 350L

    /** A tap: shorter than this (ms) and moving less than [TAP_SLOP_DP]. Two taps within [DOUBLE_TAP_MS] recenter. */
    const val TAP_MS = 220L
    const val TAP_SLOP_DP = 10f
    const val DOUBLE_TAP_MS = 320L

    /** Top-screen pixels per pad dp for a picture [pictureWidthPx] wide. */
    fun gain(pictureWidthPx: Float, speed: Speed): Float = pictureWidthPx / FULL_SWEEP_DP * speed.factor

    /** The game picture inside a [w]×[h] screen, assuming Dolphin's usual 16:9 output (letterboxed). */
    fun picture(w: Float, h: Float, aspect: Float = 16f / 9f): Pair<Float, Float> =
        if (w / h >= aspect) (h * aspect) to h else w to (w / aspect)

    /** Pointer position (px from the picture's centre), clamped to the picture. */
    data class Pos(val x: Float = 0f, val y: Float = 0f) {
        fun moved(dx: Float, dy: Float, halfW: Float, halfH: Float) =
            Pos((x + dx).coerceIn(-halfW, halfW), (y + dy).coerceIn(-halfH, halfH))
    }
}
