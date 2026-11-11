package io.github.hiramrg.delphis.platform

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import io.github.hiramrg.delphis.core.GameScreen

/**
 * Fills the black bars beside a GameCube game's 4:3 picture on the top screen with a pattern or a
 * colour taken from the game's cover. Two thin, never-touchable overlay windows (left and right),
 * a few pixels narrower than the bars so they can't cover the picture. Hidden while Dolphin's own
 * menu is open (it sits at the screen's edge).
 */
class SideFill(private val service: AccessibilityService) {
    private val views = mutableListOf<View>()
    private var wm: WindowManager? = null
    var showing = false; private set

    fun show(sides: GameScreen.Sides, colour: Int, displayId: Int?) {
        hide()
        if (sides == GameScreen.Sides.BLACK) return
        val ctx: Context = if (android.os.Build.VERSION.SDK_INT >= 30 && displayId != null) {
            service.getSystemService(DisplayManager::class.java).getDisplay(displayId)
                ?.let { service.createWindowContext(it, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null) } ?: service
        } else service
        val manager = ctx.getSystemService(WindowManager::class.java)
        val bounds = manager.currentWindowMetrics.bounds
        val w = bounds.width(); val h = bounds.height()
        val bar = ((w - h * 4f / 3f) / 2f).toInt() - 4
        if (bar < 8) return
        for (gravity in listOf(Gravity.START, Gravity.END)) {
            val v = FillView(ctx, sides, colour)
            val params = WindowManager.LayoutParams(
                bar, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE,
            ).apply {
                this.gravity = gravity or Gravity.TOP
                if (android.os.Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            runCatching { manager.addView(v, params); views += v }.onFailure { CrashLog.event("side fill failed: ${it.message}") }
        }
        wm = manager
        showing = views.isNotEmpty()
    }

    /** Temporarily hides / shows the fills (Dolphin's menu open / closed) without rebuilding them. */
    fun setVisible(visible: Boolean) { views.forEach { it.visibility = if (visible) View.VISIBLE else View.GONE } }

    fun hide() {
        views.forEach { runCatching { wm?.removeView(it) } }
        views.clear(); showing = false
    }

    private class FillView(ctx: Context, val sides: GameScreen.Sides, val colour: Int) : View(ctx) {
        private val dark = Paint().apply { color = shade(colour, 0.55f) }
        private val light = Paint().apply { color = shade(colour, 0.68f); isAntiAlias = true }
        override fun onDraw(c: Canvas) {
            c.drawColor(if (sides == GameScreen.Sides.COLOUR) shade(colour, 0.62f) else dark.color)
            if (sides != GameScreen.Sides.PATTERN) return
            // Calm diagonal stripes.
            val step = 28f * resources.displayMetrics.density
            light.strokeWidth = step / 2f
            var x = -height.toFloat()
            while (x < width + height) { c.drawLine(x, 0f, x + height, height.toFloat(), light); x += step }
        }
        companion object {
            fun shade(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
        }
    }

    companion object {
        /** A calm colour from the cover: the average of its pixels, nudged toward saturation. Purple-grey without a cover. */
        fun colourOf(cover: Bitmap?): Int {
            if (cover == null) return Color.rgb(75, 58, 143)
            val small = Bitmap.createScaledBitmap(cover, 16, 16, true)
            var r = 0L; var g = 0L; var b = 0L
            for (y in 0 until 16) for (x in 0 until 16) { val p = small.getPixel(x, y); r += Color.red(p); g += Color.green(p); b += Color.blue(p) }
            val hsv = FloatArray(3)
            Color.RGBToHSV((r / 256).toInt(), (g / 256).toInt(), (b / 256).toInt(), hsv)
            hsv[1] = (hsv[1] * 1.3f).coerceIn(0.25f, 0.8f); hsv[2] = hsv[2].coerceIn(0.45f, 0.8f)
            return Color.HSVToColor(hsv)
        }
    }
}
