package io.github.hiramrg.delphis.platform

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import java.lang.ref.WeakReference

/**
 * A plain black screen shown briefly on the top display. Putting something in front of Dolphin
 * moves it to the background, which is what allows Android to close it
 * (ActivityManager.killBackgroundProcesses only stops apps that aren't visible).
 */
class CoverActivity : Activity() {
    companion object {
        private var current: WeakReference<CoverActivity>? = null
        /** Completed once the cover is on screen (so Dolphin has been pushed to the background). */
        @Volatile var shown: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        fun dismiss() { current?.get()?.finish() }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        setContentView(TextView(this).apply {
            text = intent.getStringExtra("message") ?: "Delphis…"
            setTextColor(Color.GRAY)
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        })
        handler.postDelayed({ finish() }, 10_000)
    }

    override fun onResume() {
        super.onResume()
        shown?.complete(Unit)
    }

    // Once Dolphin (or anything else) comes back on top, this screen has done its job.
    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
