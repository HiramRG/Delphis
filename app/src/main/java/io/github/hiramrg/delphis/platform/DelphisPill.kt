package io.github.hiramrg.delphis.platform

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.hiramrg.delphis.R

/**
 * One screen (phones, tablets): the Delphis pill at the top centre of the game, the one spot Dolphin's
 * touch buttons never use. The full pill (icon · "Delphis menu" · ⊖ + Start · controller) shows for
 * [FULL_MS], then it shrinks to a small icon at the chosen opacity. A tap opens Delphis' in-game menu.
 * Achievement badges replace it while they show (see [hold]).
 */
class DelphisPill(private val service: AccessibilityService, private val onTap: () -> Unit) {
    companion object {
        const val FULL_MS = 3_000L
        private const val ACCENT = 0xFF34BEED.toInt()
        private const val ACCENT_DARK = 0xFF0A2533.toInt()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var full: View? = null
    private var small: View? = null
    private var wm: WindowManager? = null
    private var opacity = 0.6f
    /** Wanted on screen (Dolphin's game is in front). */
    private var visible = true
    /** An achievement badge is showing in the pill's place. */
    private var held = false

    val showing: Boolean get() = root != null

    /** Puts the pill up for a game: full first when [showFull], then small at [opacityPercent]. */
    fun start(showFull: Boolean, opacityPercent: Int, controller: String, shortcut: String) {
        stop()
        opacity = opacityPercent / 100f
        runCatching { attach(controller, shortcut) }.onFailure { CrashLog.event("Delphis pill failed: ${it.message}"); return }
        if (showFull) {
            full?.visibility = View.VISIBLE; small?.visibility = View.GONE
            handler.postDelayed({ shrink() }, FULL_MS)
        } else shrink()
        apply()
    }

    private fun shrink() {
        full?.visibility = View.GONE
        small?.visibility = View.VISIBLE
        small?.alpha = opacity
    }

    fun setVisible(v: Boolean) { if (visible != v) { visible = v; apply() } }

    /** Hides the pill while an achievement badge shows in its place. */
    fun hold(ms: Long) {
        held = true; apply()
        handler.removeCallbacks(release)
        handler.postDelayed(release, ms)
    }
    private val release = Runnable { held = false; apply() }

    private fun apply() { root?.visibility = if (visible && !held) View.VISIBLE else View.GONE }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        held = false
        root?.let { v -> runCatching { wm?.removeView(v) } }
        root = null; full = null; small = null
    }

    private fun attach(controller: String, shortcut: String) {
        val ctx = service
        val manager = ctx.getSystemService(WindowManager::class.java)
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        fun icon(size: Float, tint: Int, circle: Int?) = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_launcher_monochrome)
            setColorFilter(tint, PorterDuff.Mode.SRC_IN)
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (circle != null) background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(circle) }
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }
        fun glass(stroke: Int) = GradientDrawable().apply { cornerRadius = dp(19f).toFloat(); setColor(0xC014171B.toInt()); setStroke(dp(1.5f), stroke) }
        fun text(t: String, size: Float, bold: Boolean, alpha: Float = 1f) = TextView(ctx).apply {
            text = t; textSize = size; setTextColor(Color.WHITE); this.alpha = alpha; maxLines = 1
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

        val fullPill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = glass(ACCENT)
            setPadding(dp(5f), dp(5f), dp(14f), dp(5f))
            addView(icon(28f, ACCENT_DARK, ACCENT))
            addView(text("Delphis menu", 12.5f, true).apply { setPadding(dp(9f), 0, 0, 0) })
            addView(text(shortcut, 11.5f, true, 0.85f).apply { setPadding(dp(9f), 0, 0, 0) })
            addView(View(ctx).apply { setBackgroundColor(0x40FFFFFF) }, LinearLayout.LayoutParams(dp(1f), dp(18f)).apply { marginStart = dp(9f); marginEnd = dp(9f) })
            addView(text(controller, 11.5f, false, 0.85f))
        }
        val smallPill = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0x8014171B.toInt()); setStroke(dp(1.5f), 0x38FFFFFF) }
            addView(icon(24f, Color.WHITE, null))
        }
        val frame = FrameLayout(ctx).apply {
            // A little extra around the small pill so it's easy to tap.
            setPadding(dp(8f), dp(4f), dp(8f), dp(8f))
            addView(fullPill, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))
            addView(smallPill, FrameLayout.LayoutParams(dp(58f), dp(26f), Gravity.CENTER_HORIZONTAL))
            setOnClickListener { onTap() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(4f) }
        manager.addView(frame, params)
        wm = manager; root = frame; full = fullPill; small = smallPill
    }
}
