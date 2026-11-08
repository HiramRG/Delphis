package io.github.hiramrg.delphis.platform

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
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
import java.io.File

/**
 * Wii-style "Saving your progress / Starting" screen that covers the top screen while Delphis
 * drives Dolphin's menus (quick save, exit, boot, quick load), so the player doesn't see the
 * buttons being pressed.
 *
 * It's an accessibility overlay owned by [DolphinExitService]: it sits above Dolphin without
 * taking focus or touches, so Dolphin keeps running and keeps the controller, and the service can
 * still press Dolphin's buttons underneath. Plain Android views keep it light.
 */
class LoadingOverlay(private val service: android.accessibilityservice.AccessibilityService) {

    enum class Phase(val label: String, val step: Int) {
        SAVING("SAVING YOUR PROGRESS", 0),
        CLOSING("CLOSING THE GAME", 1),
        STARTING("STARTING", 2),
    }

    private var root: View? = null
    private var wm: WindowManager? = null
    private var phaseView: TextView? = null
    private var titleView: TextView? = null
    private var lineView: TextView? = null
    private var coverView: ImageView? = null
    private var coverTitle: TextView? = null
    private var dots: List<View> = emptyList()
    private var pal = Palette.light
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val safety = Runnable { hide() }

    val isShowing: Boolean get() = root != null

    fun show(phase: Phase, title: String?, gameId: String?, line: String, displayId: Int?) {
        handler.removeCallbacks(hideLater)
        if (root == null) runCatching { attach(displayId) }.onFailure { CrashLog.event("loading screen failed: ${it.message}"); return }
        phaseView?.text = phase.label
        titleView?.text = title.orEmpty()
        titleView?.visibility = if (title.isNullOrBlank()) View.GONE else View.VISIBLE
        lineView?.text = line
        dots.forEachIndexed { i, d -> (d.background as GradientDrawable).setColor(if (i == phase.step) pal.accent else pal.border) }
        setCover(title, gameId)
        // Never leave the screen covered if something goes wrong.
        handler.removeCallbacks(safety)
        handler.postDelayed(safety, 60_000)
    }

    private val hideLater = Runnable { hide() }
    fun hide(delayMs: Long = 0) {
        if (delayMs > 0) { handler.removeCallbacks(hideLater); handler.postDelayed(hideLater, delayMs); return }
        handler.removeCallbacks(safety)
        handler.removeCallbacks(hideLater)
        val v = root ?: return
        root = null
        runCatching { wm?.removeView(v) }
    }

    private fun attach(displayId: Int?) {
        pal = if (isDark()) Palette.dark else Palette.light
        val ctx: Context = if (android.os.Build.VERSION.SDK_INT >= 30 && displayId != null) {
            val display = service.getSystemService(DisplayManager::class.java).getDisplay(displayId)
            if (display != null) service.createWindowContext(display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null) else service
        } else service
        val manager = ctx.getSystemService(WindowManager::class.java)
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        val frame = FrameLayout(ctx).apply { background = stripes(d) }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

        // Cover with the blue frame (or a coloured card with the title when there's no cover yet).
        val coverFrame = FrameLayout(ctx).apply {
            background = GradientDrawable().apply { cornerRadius = dp(18f).toFloat(); setColor(pal.card); setStroke(dp(3f), pal.accent) }
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        }
        val inner = FrameLayout(ctx).apply {
            background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0xFF2B3F9E.toInt()) }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, dp(12f).toFloat())
            }
        }
        coverView = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        coverTitle = TextView(ctx).apply {
            setTextColor(Color.WHITE); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.BOTTOM or Gravity.START; setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
        }
        inner.addView(coverTitle, FrameLayout.LayoutParams(-1, -1))
        inner.addView(coverView, FrameLayout.LayoutParams(-1, -1))
        coverFrame.addView(inner, FrameLayout.LayoutParams(-1, -1))
        row.addView(coverFrame, LinearLayout.LayoutParams(dp(150f), dp(206f)).apply { marginEnd = dp(26f) })

        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        phaseView = TextView(ctx).apply { setTextColor(pal.accentText); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f }
        titleView = TextView(ctx).apply { setTextColor(pal.text); textSize = 26f; typeface = Typeface.DEFAULT_BOLD; maxLines = 2 }
        lineView = TextView(ctx).apply { setTextColor(pal.dim); textSize = 14f }
        col.addView(phaseView)
        col.addView(titleView, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8f) })
        col.addView(lineView, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8f) })
        val progress = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        progress.addView(ProgressBar(ctx).apply { isIndeterminate = true; indeterminateTintList = ColorStateList.valueOf(pal.accent) }, LinearLayout.LayoutParams(dp(32f), dp(32f)))
        dots = List(3) { i ->
            View(ctx).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(pal.border) } }
                .also { progress.addView(it, LinearLayout.LayoutParams(dp(10f), dp(10f)).apply { marginStart = dp(if (i == 0) 14f else 8f) }) }
        }
        col.addView(progress, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14f) })
        row.addView(col, LinearLayout.LayoutParams(dp(330f), -2))
        frame.addView(row, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        frame.addView(TextView(ctx).apply { text = "Delphis"; setTextColor(pal.dim); textSize = 11f },
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

    private fun setCover(title: String?, gameId: String?) {
        coverTitle?.text = title.orEmpty()
        val file = gameId?.let { File(service.filesDir, "covers-hq/$it.png") }?.takeIf { it.exists() }
        val bmp = file?.let { f ->
            // Full resolution (the HQ covers are about 350×500, about what the 150×206 dp frame needs
            // on the Thor), so the cover is as sharp as in the library.
            runCatching { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) }.getOrNull()
        }
        coverView?.setImageDrawable(bmp?.let {
            android.graphics.drawable.BitmapDrawable(service.resources, it).apply { setFilterBitmap(true); setDither(true) }
        })
        coverView?.visibility = if (bmp != null) View.VISIBLE else View.GONE
    }

    private fun isDark(): Boolean = when (Prefs(service).themeMode) {
        "dark" -> true
        "auto" -> (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }

    /** The app's pinstripe background, as a tiled 1×4 dp bitmap. */
    private fun stripes(density: Float): android.graphics.drawable.Drawable {
        val h = (4 * density).toInt().coerceAtLeast(4)
        val line = (1 * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(1, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(pal.bg)
            drawRect(0f, (h - line).toFloat(), 1f, h.toFloat(), Paint().apply { color = pal.stripe })
        }
        return ShapeDrawable(RectShape()).apply { paint.shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    }

    private data class Palette(val bg: Int, val stripe: Int, val card: Int, val border: Int, val text: Int, val dim: Int, val accent: Int, val accentText: Int) {
        companion object {
            val light = Palette(0xFFF3F5F7.toInt(), 0xFFE9EDF1.toInt(), Color.WHITE, 0xFFCDD4DC.toInt(), 0xFF3A3F45.toInt(), 0xFF6B737D.toInt(), 0xFF34BEED.toInt(), 0xFF0F7FB8.toInt())
            val dark = Palette(0xFF1D2127.toInt(), 0xFF191C21.toInt(), 0xFF2A3038.toInt(), 0xFF4A535E.toInt(), 0xFFE8ECF0.toInt(), 0xFFA3ADB8.toInt(), 0xFF34BEED.toInt(), 0xFF5CCBF2.toInt())
        }
    }
}
