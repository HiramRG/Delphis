package io.github.hiramrg.delphis.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import io.github.hiramrg.delphis.core.Trackpad

/**
 * Relays the bottom-screen trackpad to Dolphin's own touch pointer on the top screen.
 *
 * Dolphin's overlay (set to Drag mode with every button hidden, see DolphinConfigWriter.trackpadOverlay)
 * moves the Wii pointer by however far a touch on the game screen moves. This injects one continuous
 * touch there per pad touch (`dispatchGesture` + `continueStroke`), moving it by the finger's movement
 * scaled to the top screen. Rules that keep Dolphin from misreading it:
 * - a new relayed touch starts at least [Trackpad.MIN_DOWN_GAP_MS] after the previous one (two touches
 *   within 300 ms are Dolphin's double tap, which presses a button);
 * - after the finger lifts, the relayed touch stays down [Trackpad.LIFT_GRACE_MS], so a quick re-touch
 *   continues it instead of starting a new one;
 * - the position is clamped to the game picture, so the touch never leaves Dolphin's game screen.
 * Runs on the service's main-thread handler.
 */
class TrackpadRelay(private val service: AccessibilityService, private val handler: Handler) {
    private var displayId = -1
    private var cx = 0f; private var cy = 0f; private var halfW = 1f; private var halfH = 1f; private var pictureW = 1f
    private var pos = Trackpad.Pos()

    private var stroke: GestureDescription.StrokeDescription? = null
    private var at = Trackpad.Pos() // where the relayed touch is now
    private var dispatching = false
    private var ending = false
    private var fingerDown = false
    private var lastDownAt = 0L
    private var generation = 0 // callbacks from an older (cancelled/replaced) gesture are ignored
    private val endRunnable = Runnable { if (!fingerDown) end() }
    private val startRunnable = Runnable { if (stroke == null && (fingerDown || pos != at)) start() }

    /** Pad touched: [topDisplayId] is the display Dolphin's game is on. */
    fun down(topDisplayId: Int) {
        fingerDown = true
        handler.removeCallbacks(endRunnable)
        if (displayId != topDisplayId) measure(topDisplayId)
        if (stroke == null) start()
    }

    /** Finger moved [dxDp]/[dyDp] dp on the pad. */
    fun move(dxDp: Float, dyDp: Float, speed: Trackpad.Speed) {
        if (displayId < 0) return
        val g = Trackpad.gain(pictureW, speed)
        pos = pos.moved(dxDp * g, dyDp * g, halfW, halfH)
        if (stroke == null) start() else pump()
    }

    fun up() {
        fingerDown = false
        handler.removeCallbacks(endRunnable)
        handler.postDelayed(endRunnable, Trackpad.LIFT_GRACE_MS)
    }

    /** Pointer back to the middle of the picture. */
    fun recenter(topDisplayId: Int) {
        if (displayId != topDisplayId) measure(topDisplayId)
        if (displayId < 0) return
        pos = Trackpad.Pos()
        if (stroke == null) start() else pump()
        if (!fingerDown) { handler.removeCallbacks(endRunnable); handler.postDelayed(endRunnable, Trackpad.LIFT_GRACE_MS) }
    }

    /** New game: Dolphin's pointer starts in the middle again. */
    fun reset() {
        handler.removeCallbacks(endRunnable); handler.removeCallbacks(startRunnable)
        stroke = null; dispatching = false; ending = false; fingerDown = false
        pos = Trackpad.Pos(); at = Trackpad.Pos(); displayId = -1
    }

    private fun measure(id: Int) {
        val d = service.getSystemService(DisplayManager::class.java)?.getDisplay(id) ?: return
        val size = android.graphics.Point()
        @Suppress("DEPRECATION") d.getRealSize(size)
        val (pw, ph) = Trackpad.picture(size.x.toFloat(), size.y.toFloat())
        cx = size.x / 2f; cy = size.y / 2f; halfW = pw / 2f; halfH = ph / 2f; pictureW = pw
        pos = pos.moved(0f, 0f, halfW, halfH); at = at.moved(0f, 0f, halfW, halfH)
        displayId = id
    }

    private fun px(p: Trackpad.Pos) = (cx + p.x) to (cy + p.y)

    private fun start() {
        if (displayId < 0 || Build.VERSION.SDK_INT < 30 || dispatching) return
        val wait = lastDownAt + Trackpad.MIN_DOWN_GAP_MS - SystemClock.uptimeMillis()
        if (wait > 0) { handler.removeCallbacks(startRunnable); handler.postDelayed(startRunnable, wait); return }
        // Dolphin only moves the pointer by the touch's movement, so the touch starts where the
        // pointer is and follows it.
        val (x, y) = px(at)
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.1f, y) }
        val s = GestureDescription.StrokeDescription(path, 0, SEGMENT_MS, true)
        if (dispatch(s)) { lastDownAt = SystemClock.uptimeMillis(); ending = false }
    }

    /** Sends the touch to the latest position (one segment at a time). */
    private fun pump() {
        val s = stroke ?: return
        if (dispatching) return
        if (pos == at && !ending) return
        val (x0, y0) = px(at).let { (it.first + 0.1f) to it.second }
        val target = pos
        val (x1, y1) = px(target)
        val path = Path().apply { moveTo(x0, y0); lineTo(x1 + 0.1f, y1) }
        val next = runCatching { s.continueStroke(path, 0, SEGMENT_MS, !ending) }.getOrNull() ?: run { stroke = null; return }
        val wasEnding = ending
        at = target
        if (!dispatch(next) || wasEnding) stroke = null
    }

    private fun end() {
        if (stroke == null) return
        ending = true
        if (!dispatching) pump()
    }

    private fun dispatch(s: GestureDescription.StrokeDescription): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val gesture = GestureDescription.Builder().addStroke(s).setDisplayId(displayId).build()
        dispatching = true
        stroke = s
        val gen = ++generation
        val ok = runCatching {
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    if (gen != generation) return
                    dispatching = false
                    if (stroke == null) { ending = false; if (fingerDown || pos != at) start(); return }
                    if (pos != at || ending) pump()
                }
                override fun onCancelled(g: GestureDescription?) {
                    if (gen != generation) return
                    // Something else touched the screen (or Dolphin's window changed): start afresh.
                    dispatching = false; stroke = null; ending = false
                    if (fingerDown) handler.postDelayed({ if (fingerDown && stroke == null) start() }, 50)
                }
            }, handler)
        }.getOrDefault(false)
        if (!ok) { dispatching = false; stroke = null; CrashLog.event("trackpad: gesture not dispatched") }
        return ok
    }

    private companion object { const val SEGMENT_MS = 8L }
}
