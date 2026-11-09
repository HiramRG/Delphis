package io.github.hiramrg.delphis.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.WindowManager

/**
 * The unlock badge over the game on the top screen: a small card in the top-right corner that slides
 * in, stays [SHOW_MS] and slides away. Never takes touches or focus. Several unlocks queue up.
 */
class AchievementToast(private val service: android.accessibilityservice.AccessibilityService) {
    companion object { const val SHOW_MS = 5_000L }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val queue = ArrayDeque<Pair<Achievements.Popup, Bitmap?>>()
    private var showing: View? = null
    private var wm: WindowManager? = null

    fun show(popup: Achievements.Popup, badge: Bitmap?, displayId: Int?) {
        queue.addLast(popup to badge)
        if (showing == null) next(displayId)
    }

    private fun next(displayId: Int?) {
        val (popup, badge) = queue.removeFirstOrNull() ?: return
        val v = runCatching { attach(popup, badge, displayId) }.getOrElse { CrashLog.event("achievement pop-up failed: ${it.message}"); null } ?: return
        showing = v
        v.translationX = v.resources.displayMetrics.density * 380
        v.alpha = 0f
        v.animate().translationX(0f).alpha(1f).setDuration(260).start()
        handler.postDelayed({
            v.animate().translationX(v.resources.displayMetrics.density * 380).alpha(0f).setDuration(260).withEndAction {
                runCatching { wm?.removeView(v) }
                showing = null
                next(displayId)
            }.start()
        }, SHOW_MS)
    }

    private fun attach(popup: Achievements.Popup, badge: Bitmap?, displayId: Int?): View {
        val ctx: Context = if (android.os.Build.VERSION.SDK_INT >= 30 && displayId != null) {
            val display = service.getSystemService(DisplayManager::class.java).getDisplay(displayId)
            if (display != null) service.createWindowContext(display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null) else service
        } else service
        val manager = ctx.getSystemService(WindowManager::class.java)
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val gold = 0xFFE8B64A.toInt()
        val u = popup.unlock

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14f).toFloat(); setColor(0xF014181D.toInt()); setStroke(dp(2f), gold) }
            setPadding(dp(8f), dp(8f), dp(12f), dp(8f))
            elevation = dp(6f).toFloat()
        }
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { cornerRadius = dp(9f).toFloat(); setColor(0xFF2A2F36.toInt()) }
            clipToOutline = true
            if (badge != null) setImageBitmap(badge)
        }
        card.addView(img, LinearLayout.LayoutParams(dp(46f), dp(46f)).apply { marginEnd = dp(10f) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(ctx).apply { text = "🏆 ACHIEVEMENT UNLOCKED"; setTextColor(gold); textSize = 9.5f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f })
        col.addView(TextView(ctx).apply { text = u.title; setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; maxLines = 1 })
        col.addView(TextView(ctx).apply { text = u.description; setTextColor(0xFFA3ADB8.toInt()); textSize = 11f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        card.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val pts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        pts.addView(TextView(ctx).apply { text = "${u.points} pts"; setTextColor(gold); textSize = 11f; typeface = Typeface.DEFAULT_BOLD })
        if (popup.unlocked != null && popup.total != null) pts.addView(TextView(ctx).apply { text = "${popup.unlocked} / ${popup.total}"; setTextColor(0xFF8E9AA6.toInt()); textSize = 10f; typeface = Typeface.DEFAULT_BOLD })
        card.addView(pts, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8f) })

        val params = WindowManager.LayoutParams(
            dp(340f), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(14f); y = dp(14f) }
        manager.addView(card, params)
        wm = manager
        return card
    }
}
