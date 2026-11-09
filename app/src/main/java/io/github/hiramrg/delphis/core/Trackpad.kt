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
    const val TAP_MS = 250L
    const val TAP_SLOP_DP = 10f
    const val DOUBLE_TAP_MS = 420L

    /** Top-screen pixels per pad dp for a picture [pictureWidthPx] wide. */
    fun gain(pictureWidthPx: Float, speed: Speed): Float = pictureWidthPx / FULL_SWEEP_DP * speed.factor

    /** The game picture inside a [w]×[h] screen, assuming Dolphin's usual 16:9 output (letterboxed). */
    fun picture(w: Float, h: Float, aspect: Float = 16f / 9f): Pair<Float, Float> =
        if (w / h >= aspect) (h * aspect) to h else w to (w / aspect)

    /**
     * Android's gesture zones on the top screen: a touch starting near the bottom edge swipes Home /
     * Recents, near the sides it's Back, near the top it pulls the notification shade. Relayed touches
     * stay this far (dp) inside the edges.
     */
    const val SIDE_MARGIN_DP = 40f
    const val TOP_MARGIN_DP = 40f
    const val BOTTOM_MARGIN_DP = 60f

    /** Dolphin's default IR "Total Yaw" / "Total Pitch" (degrees), which reach the screen edges in most games. */
    const val DEFAULT_YAW = 25.0
    const val DEFAULT_PITCH = 20.0

    /**
     * The area relayed touches may use on a [w]×[h] px screen ([density] px per dp): half width and half
     * height around the centre, inside the gesture zones and the game picture (symmetric, so the
     * pointer's middle stays the screen's middle).
     */
    data class Area(val halfW: Float, val halfH: Float, val pictureHalfW: Float, val pictureHalfH: Float) {
        /** How much of the pointer's range ([-1, 1]) the area covers, horizontally and vertically. */
        val reachX: Float get() = halfW / pictureHalfW
        val reachY: Float get() = halfH / pictureHalfH
    }

    fun area(w: Float, h: Float, density: Float): Area {
        val (pw, ph) = picture(w, h)
        val halfW = minOf(pw / 2f, w / 2f - SIDE_MARGIN_DP * density).coerceAtLeast(1f)
        val halfH = minOf(ph / 2f, h / 2f - maxOf(TOP_MARGIN_DP, BOTTOM_MARGIN_DP) * density).coerceAtLeast(1f)
        return Area(halfW, halfH, pw / 2f, ph / 2f)
    }

    /**
     * [preset] with Dolphin's IR yaw / pitch widened so the smaller touch area still reaches the edges of
     * the game picture: Dolphin turns the pointer by (Total Yaw / 2) × x, so dividing x's range by
     * reachX is undone by multiplying the yaw by 1 / reachX.
     */
    fun withReach(preset: Preset, area: Area): Preset {
        fun f(v: Double) = "%.1f".format(java.util.Locale.US, v)
        return preset.copy(options = preset.options + mapOf(
            "IR/Total Yaw" to f(DEFAULT_YAW / area.reachX.coerceIn(0.2f, 1f)),
            "IR/Total Pitch" to f(DEFAULT_PITCH / area.reachY.coerceIn(0.2f, 1f)),
        ))
    }

    /** Pointer position (px from the picture's centre), clamped to the picture. */
    data class Pos(val x: Float = 0f, val y: Float = 0f) {
        fun moved(dx: Float, dy: Float, halfW: Float, halfH: Float) =
            Pos((x + dx).coerceIn(-halfW, halfW), (y + dy).coerceIn(-halfH, halfH))
    }
}
