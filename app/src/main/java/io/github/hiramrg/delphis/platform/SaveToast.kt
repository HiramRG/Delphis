package io.github.hiramrg.delphis.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The quick save / load buttons' pop-up at the top centre of the game: "Keep holding to load" with a ring that
 * fills, "Saving…", "Saved" with the save's picture, or what went wrong. Never takes touches or focus.
 */
class SaveToast(private val service: android.accessibilityservice.AccessibilityService) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var wm: WindowManager? = null
    private var ring: Ring? = null
    private var image: ImageView? = null
    private var title: TextView? = null
    private var sub: TextView? = null
    private var shownOn: Int? = null
    private val hideRunnable = Runnable { hide() }

    private val blue = 0xFF34BEED.toInt()
    private val amber = 0xFFF2B544.toInt()

    /** Holding the load buttons: the ring fills over [holdMs]. */
    fun holding(displayId: Int?, picture: Bitmap?, line: String, holdMs: Long) =
        show(displayId, picture, "Keep holding to load", line, blue, ringMs = holdMs, stayMs = 0)

    /** "Saving…" / "Loading…" (stays until the next call). */
    fun busy(displayId: Int?, picture: Bitmap?, text: String, line: String) = show(displayId, picture, text, line, blue, ringMs = -1, stayMs = 0)

    /** Done: shown for [stayMs] (3 s). */
    fun done(displayId: Int?, picture: Bitmap?, text: String, line: String, stayMs: Long = 3_000, edge: Int = blue) = show(displayId, picture, text, line, edge, ringMs = null, stayMs = stayMs)

    /** Something went wrong: shown for 4.5 s, amber. */
    fun failed(displayId: Int?, text: String, line: String) = show(displayId, null, text, line, amber, ringMs = null, stayMs = 4_500)

    /**
     * [ringMs]: null = no ring, -1 = a ring that spins (busy), otherwise it fills over that time.
     * [stayMs]: 0 = until the next call or [hide].
     */
    private fun show(displayId: Int?, picture: Bitmap?, text: String, line: String, edge: Int, ringMs: Long?, stayMs: Long) {
        handler.removeCallbacks(hideRunnable)
        if (root == null || shownOn != displayId) {
            hide()
            runCatching { attach(displayId) }.onFailure { CrashLog.event("save pop-up failed: ${it.message}") }
        }
        val card = root ?: return
        val d = card.resources.displayMetrics.density
        (card.background as? GradientDrawable)?.setStroke((2 * d).toInt(), edge)
        title?.text = text
        sub?.text = line
        sub?.visibility = if (line.isBlank()) View.GONE else View.VISIBLE
        image?.let { iv -> if (picture != null) { iv.setImageBitmap(picture); iv.visibility = View.VISIBLE } else iv.visibility = View.GONE }
        ring?.let { r ->
            r.animate().cancel()
            r.visibility = if (ringMs == null) View.GONE else View.VISIBLE
            r.spin = ringMs == -1L
            if (ringMs != null && ringMs > 0) r.fill(ringMs) else r.progress = if (ringMs == -1L) 0.3f else 0f
        }
        if (stayMs > 0) handler.postDelayed(hideRunnable, stayMs)
    }

    /** A quick white flash over the game's screen (a screenshot was taken). */
    fun flash(displayId: Int?) {
        runCatching {
            val ctx: Context = if (android.os.Build.VERSION.SDK_INT >= 30 && displayId != null) {
                val display = service.getSystemService(DisplayManager::class.java).getDisplay(displayId)
                if (display != null) service.createWindowContext(display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null) else service
            } else service
            val manager = ctx.getSystemService(WindowManager::class.java)
            val v = View(ctx).apply { setBackgroundColor(Color.WHITE); alpha = 0.75f }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
            manager.addView(v, params)
            v.animate().alpha(0f).setDuration(260).withEndAction { runCatching { manager.removeView(v) } }.start()
        }
    }

    val amberEdge get() = amber

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        ring?.stop()
        root?.let { v -> runCatching { wm?.removeView(v) } }
        root = null; ring = null; image = null; title = null; sub = null; shownOn = null
    }

    private fun attach(displayId: Int?) {
        val ctx: Context = if (android.os.Build.VERSION.SDK_INT >= 30 && displayId != null) {
            val display = service.getSystemService(DisplayManager::class.java).getDisplay(displayId)
            if (display != null) service.createWindowContext(display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null) else service
        } else service
        val manager = ctx.getSystemService(WindowManager::class.java)
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(0xEB14171B.toInt()); setStroke(dp(2f), blue) }
            setPadding(dp(8f), dp(6f), dp(14f), dp(6f))
            elevation = dp(6f).toFloat()
        }
        val r = Ring(ctx)
        card.addView(r, LinearLayout.LayoutParams(dp(30f), dp(30f)).apply { marginEnd = dp(8f) })
        val iv = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { cornerRadius = dp(7f).toFloat(); setColor(0xFF2A2F36.toInt()) }
            clipToOutline = true
        }
        card.addView(iv, LinearLayout.LayoutParams(dp(64f), dp(36f)).apply { marginEnd = dp(10f) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val t = TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; maxLines = 2 }
        val s = TextView(ctx).apply { setTextColor(0xFFC5CDD6.toInt()); textSize = 11.5f; maxLines = 2 }
        col.addView(t); col.addView(s)
        card.addView(col, LinearLayout.LayoutParams(-2, -2))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(10f) }
        card.minimumWidth = dp(220f)
        manager.addView(card, params)
        wm = manager; root = card; ring = r; image = iv; title = t; sub = s; shownOn = displayId
    }

    /** The ring: fills while the load buttons are held, spins while busy. */
    private class Ring(ctx: Context) : View(ctx) {
        var progress = 0f; set(v) { field = v; invalidate() }
        var spin = false; set(v) { field = v; invalidate() }
        private var angle = 0f
        private var animator: android.animation.ValueAnimator? = null
        private val d = ctx.resources.displayMetrics.density
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4 * d; color = 0xFF3A424C.toInt() }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4 * d; color = 0xFF34BEED.toInt(); strokeCap = Paint.Cap.ROUND }

        fun fill(ms: Long) {
            stop()
            animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ms; interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { progress = it.animatedValue as Float }
                start()
            }
        }

        fun stop() { animator?.cancel(); animator = null }

        override fun onDraw(canvas: Canvas) {
            val r = RectF(arc.strokeWidth, arc.strokeWidth, width - arc.strokeWidth, height - arc.strokeWidth)
            canvas.drawOval(r, track)
            if (spin) {
                angle = (angle + 9f) % 360f
                canvas.drawArc(r, angle - 90f, 110f, false, arc)
                postInvalidateOnAnimation()
            } else if (progress > 0f) canvas.drawArc(r, -90f, 360f * progress, false, arc)
        }
    }
}
