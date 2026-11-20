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
 * touch buttons never use. The full pill ("Open Delphis' menu" over "Press the Home button, or − and + at the same
 * time", with a countdown line) shows for [FULL_MS], then it shrinks to a small icon at the chosen opacity. A tap opens
 * Delphis' in-game menu.
 * Achievement badges replace it while they show (see [hold]).
 */
class DelphisPill(private val service: AccessibilityService, private val onTap: () -> Unit) {
    companion object {
        const val FULL_MS = 5_000L
        private const val ACCENT = 0xFF34BEED.toInt()
        private const val ACCENT_DARK = 0xFF0A2533.toInt()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var full: View? = null
    private var countdown: View? = null
    private var small: View? = null
    private var wm: WindowManager? = null
    private var opacity = 0.6f
    /** Wanted on screen (Dolphin's game is in front). */
    private var visible = true
    /** An achievement badge is showing in the pill's place. */
    private var held = false

    val showing: Boolean get() = root != null

    /** Puts the pill up for a game: full first when [showFull], then small at [opacityPercent]. */
    fun start(showFull: Boolean, opacityPercent: Int, controller: String, shortcut: String): Boolean {
        stop()
        opacity = opacityPercent / 100f
        runCatching { attach(controller, shortcut) }.onFailure { CrashLog.event("Delphis pill failed: ${it.javaClass.simpleName} ${it.message}"); return false }
        // The full pill waits until the game is actually on screen (the game takes a while to load).
        pendingFull = showFull
        if (showFull) { full?.visibility = View.VISIBLE; small?.visibility = View.GONE } else shrink()
        visible = false
        apply()
        return true
    }
    private var pendingFull = false

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

    private fun apply() {
        val on = visible && !held
        root?.visibility = if (on) View.VISIBLE else View.GONE
        if (on && pendingFull) {
            pendingFull = false
            handler.postDelayed({ shrink() }, FULL_MS)
            // The line under the text runs down while the full pill shows.
            countdown?.let { v -> v.pivotX = 0f; v.scaleX = 1f; v.animate().scaleX(0f).setDuration(FULL_MS).setInterpolator(android.view.animation.LinearInterpolator()).start() }
        }
    }

    fun stop() {
        if (root != null) CrashLog.event("pill removed")
        handler.removeCallbacksAndMessages(null)
        held = false
        root?.let { v -> runCatching { wm?.removeView(v) } }
        countdown?.animate()?.cancel()
        root = null; full = null; small = null; countdown = null
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
            text = io.github.hiramrg.delphis.ui.plainText(t); textSize = size; setTextColor(Color.WHITE); this.alpha = alpha; maxLines = 1
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

        // Two lines: "Open Delphis' menu" over the buttons to press, and a countdown line underneath.
        val bar = View(ctx).apply { background = GradientDrawable().apply { cornerRadius = dp(1.5f).toFloat(); setColor(ACCENT) } }
        val fullPill = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(ACCENT)
            setPadding(dp(6f), dp(6f), dp(16f), dp(5f))
            val top = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(icon(34f, ACCENT_DARK, ACCENT))
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10f), 0, 0, 0)
                    addView(text("Open Delphis' menu", 13.5f, true))
                    addView(text(shortcut, 12f, true, 0.85f))
                })
            }
            addView(top)
            // The line spans the text (it starts under it, past the icon).
            val track = FrameLayout(ctx).apply {
                background = GradientDrawable().apply { cornerRadius = dp(1.5f).toFloat(); setColor(0x2EFFFFFF) }
                addView(bar, FrameLayout.LayoutParams(-1, -1))
            }
            addView(track, LinearLayout.LayoutParams(-1, dp(3f)).apply { topMargin = dp(5f); marginStart = dp(44f) })
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
        wm = manager; root = frame; full = fullPill; small = smallPill; countdown = bar
    }
}
