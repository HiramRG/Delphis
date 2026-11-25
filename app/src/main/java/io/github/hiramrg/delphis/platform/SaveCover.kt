package io.github.hiramrg.delphis.platform

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * The cover over the game while Delphis saves or loads through Dolphin's menu (mock/savescreens.png, option A):
 * the game blurred behind, the save's picture, what's happening and a progress line. Dolphin's menu and Delphis'
 * taps happen underneath and are never seen. Taps pass through (it's not touchable), like [LoadingOverlay].
 */
class SaveCover(private val service: android.accessibilityservice.AccessibilityService) {
    private var root: View? = null
    private var wm: WindowManager? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val safety = Runnable { hide() }
    private val hideLater = Runnable { hide() }

    val showing: Boolean get() = root != null

    /** [picture]: the game right now (saving) or the chosen save's picture (loading). */
    fun show(displayId: Int?, picture: Bitmap?, caps: String, title: String, line: String) {
        handler.removeCallbacks(hideLater)
        hideNow()
        runCatching { attach(displayId, picture, caps, title, line) }.onFailure { CrashLog.event("save cover failed: ${it.message}") }
        handler.removeCallbacks(safety)
        handler.postDelayed(safety, 40_000) // never leave the game covered
    }

    /** Fades out after [delayMs]. */
    fun hide(delayMs: Long = 0) {
        if (delayMs > 0) { handler.removeCallbacks(hideLater); handler.postDelayed(hideLater, delayMs); return }
        handler.removeCallbacks(safety)
        handler.removeCallbacks(hideLater)
        val v = root ?: return
        v.animate().alpha(0f).setDuration(300).withEndAction { if (root === v) hideNow() else runCatching { wm?.removeView(v) } }.start()
    }

    private fun hideNow() {
        val v = root ?: return
        root = null
        runCatching { wm?.removeView(v) }
    }

    private fun attach(displayId: Int?, picture: Bitmap?, caps: String, title: String, line: String) {
        val ctx: Context = if (android.os.Build.VERSION.SDK_INT >= 30 && displayId != null) {
            val display = service.getSystemService(DisplayManager::class.java).getDisplay(displayId)
            if (display != null) service.createWindowContext(display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null) else service
        } else service
        val manager = ctx.getSystemService(WindowManager::class.java)
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val accent = 0xFF34BEED.toInt()

        val frame = FrameLayout(ctx).apply { setBackgroundColor(0xFF10161C.toInt()) }
        // The game, blurred and darkened (scaled far down, then up with filtering).
        blurred(picture)?.let { b ->
            frame.addView(ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(BitmapDrawable(ctx.resources, b).apply { setFilterBitmap(true) })
                setColorFilter(0x8C000000.toInt(), android.graphics.PorterDuff.Mode.SRC_ATOP)
            }, FrameLayout.LayoutParams(-1, -1))
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val narrow = ctx.resources.displayMetrics.widthPixels / d < 560f
        if (picture != null && !narrow) {
            val card = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(BitmapDrawable(ctx.resources, picture).apply { setFilterBitmap(true) })
                background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setStroke(dp(3f), Color.WHITE) }
                setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, dp(12f).toFloat())
                }
                elevation = dp(8f).toFloat()
            }
            row.addView(card, LinearLayout.LayoutParams(dp(250f), dp(141f)).apply { marginEnd = dp(28f) })
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(ctx).apply { text = caps; setTextColor(0xFF9FD8F0.toInt()); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f })
        col.addView(TextView(ctx).apply { text = title; setTextColor(Color.WHITE); textSize = 24f; typeface = Typeface.DEFAULT_BOLD; maxLines = 2 },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4f) })
        col.addView(TextView(ctx).apply { text = line; setTextColor(0xFFC5CDD6.toInt()); textSize = 14f; maxLines = 2 },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4f) })
        col.addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(0x2EFFFFFF)
        }, LinearLayout.LayoutParams(-1, dp(6f)).apply { topMargin = dp(14f) })
        row.addView(col, LinearLayout.LayoutParams(dp(260f), -2))
        frame.addView(row, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        frame.addView(TextView(ctx).apply { text = "DELPHIS"; setTextColor(0x8CFFFFFF.toInt()); textSize = 11f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f },
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(12f) })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        )
        if (android.os.Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        manager.addView(frame, params)
        wm = manager
        root = frame
    }

    private fun blurred(src: Bitmap?): Bitmap? = runCatching {
        src ?: return null
        val w = 24
        val h = (src.height * w / src.width).coerceAtLeast(1)
        val tiny = Bitmap.createScaledBitmap(src, w, h, true)
        Bitmap.createScaledBitmap(tiny, w * 8, h * 8, true).also { if (it !== tiny) tiny.recycle() }
    }.getOrNull()
}
