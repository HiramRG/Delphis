package io.github.hiramrg.delphis.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import io.github.hiramrg.delphis.R

/** Soft, original menu sounds (a tick when moving, a two-note chime on Ⓐ, a falling one on Ⓑ). */
object MenuSounds {
    enum class Kind { MOVE, SELECT, BACK }
    private var pool: SoundPool? = null
    private val ids = IntArray(3)
    private var lastMove = 0L
    private val loaded = BooleanArray(3)
    private var hatX = 0f
    private var hatY = 0f

    /** The Thor's D-pad can arrive as a hat axis instead of key presses: tick when it's pushed. */
    fun onMotion(e: android.view.MotionEvent) {
        val x = e.getAxisValue(android.view.MotionEvent.AXIS_HAT_X)
        val y = e.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y)
        val pushed = (kotlin.math.abs(x) > 0.5f && kotlin.math.abs(hatX) <= 0.5f) || (kotlin.math.abs(y) > 0.5f && kotlin.math.abs(hatY) <= 0.5f)
        hatX = x; hatY = y
        if (pushed) play(Kind.MOVE)
    }
    var enabled = false

    fun init(ctx: Context, on: Boolean) {
        enabled = on
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
            // USAGE_GAME plays on the media volume (like the menu music); the system/touch-sound volume is often muted.
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        ).build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) ids.indexOf(id).takeIf { it >= 0 }?.let { loaded[it] = true } }
        ids[0] = p.load(ctx, R.raw.ui_move, 1)
        ids[1] = p.load(ctx, R.raw.ui_select, 1)
        ids[2] = p.load(ctx, R.raw.ui_back, 1)
        pool = p
    }

    fun play(k: Kind) {
        if (!enabled) return
        val p = pool ?: return
        if (!loaded[k.ordinal]) return
        if (k == Kind.MOVE) {
            val now = SystemClock.uptimeMillis()
            if (now - lastMove < 70) return // holding the D-pad: not a buzz
            lastMove = now
        }
        val v = if (k == Kind.MOVE) 0.55f else 0.8f
        p.play(ids[k.ordinal], v, v, 1, 0, 1f)
    }
}
