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
    var enabled = false

    fun init(ctx: Context, on: Boolean) {
        enabled = on
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        ).build()
        ids[0] = p.load(ctx, R.raw.ui_move, 1)
        ids[1] = p.load(ctx, R.raw.ui_select, 1)
        ids[2] = p.load(ctx, R.raw.ui_back, 1)
        pool = p
    }

    fun play(k: Kind) {
        if (!enabled) return
        val p = pool ?: return
        if (k == Kind.MOVE) {
            val now = SystemClock.uptimeMillis()
            if (now - lastMove < 70) return // holding the D-pad: not a buzz
            lastMove = now
        }
        val v = if (k == Kind.MOVE) 0.55f else 0.8f
        p.play(ids[k.ordinal], v, v, 1, 0, 1f)
    }
}
