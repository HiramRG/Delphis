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
 * Delphis keeps the pointer's position itself (moved by the finger like a laptop trackpad, clamped to
 * the game picture) and relays one continuous touch at exactly that spot on Dolphin's game screen
 * (`dispatchGesture` + `continueStroke`). Dolphin's overlay is set to Follow mode, so the Wii pointer
 * simply goes where that touch is. Because the position is absolute, a relayed touch that Android
 * cancels (Dolphin ignores ACTION_CANCEL) can't put Delphis and Dolphin out of step: the next touch
 * puts the pointer back exactly where it should be. (With Drag mode it drifted after a while.)
 * - a new relayed touch starts at least [Trackpad.MIN_DOWN_GAP_MS] after the previous one (two touches
 *   within 300 ms are Dolphin's double tap, which presses a button);
 * - after the finger lifts, the relayed touch stays down [Trackpad.LIFT_GRACE_MS], so a quick re-touch
 *   continues it instead of starting a new one.
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
    private var cancels = 0
    private var lastCancelLog = 0L
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
        CrashLog.event("trackpad: recenter")
        if (stroke == null) start() else pump()
        if (!fingerDown && stroke != null) { handler.removeCallbacks(endRunnable); handler.postDelayed(endRunnable, Trackpad.LIFT_GRACE_MS) }
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
        // Only inside Android's gesture zones' edges (else a touch near the bottom opens Recents); the
        // profile's IR yaw / pitch are widened to match, so the pointer still reaches the picture's edges.
        val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
        val area = Trackpad.area(size.x.toFloat(), size.y.toFloat(), m.density)
        cx = size.x / 2f; cy = size.y / 2f; halfW = area.halfW; halfH = area.halfH; pictureW = 2f * area.halfW
        pos = pos.moved(0f, 0f, halfW, halfH); at = at.moved(0f, 0f, halfW, halfH)
        displayId = id
    }

    private fun px(p: Trackpad.Pos) = (cx + p.x) to (cy + p.y)

    private fun start() {
        if (displayId < 0 || Build.VERSION.SDK_INT < 30 || dispatching) return
        val wait = lastDownAt + Trackpad.MIN_DOWN_GAP_MS - SystemClock.uptimeMillis()
        if (wait > 0) { handler.removeCallbacks(startRunnable); handler.postDelayed(startRunnable, wait); return }
        // Follow mode: the touch goes down where the pointer should be, so Dolphin's pointer jumps there.
        at = pos
        val (x, y) = px(at)
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.1f, y) }
        val s = GestureDescription.StrokeDescription(path, 0, SEGMENT_MS, true)
        if (dispatch(s)) {
            lastDownAt = SystemClock.uptimeMillis(); ending = false
            // Started without a finger on the pad (recenter, or a start that had to wait for the double-tap
            // gap): lift it again after the grace period, or it would stay down.
            if (!fingerDown) { handler.removeCallbacks(endRunnable); handler.postDelayed(endRunnable, Trackpad.LIFT_GRACE_MS) }
        }
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
                    cancels++
                    val now = SystemClock.uptimeMillis()
                    if (now - lastCancelLog > 10_000) { CrashLog.event("trackpad: relayed touch interrupted ($cancels so far)"); lastCancelLog = now }
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
