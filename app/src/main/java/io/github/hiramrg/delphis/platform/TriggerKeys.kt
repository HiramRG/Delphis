package io.github.hiramrg.delphis.platform

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Controllers whose triggers are only axes (Xbox, many PlayStation pads) send no L2 / R2 key, so menu
 * shortcuts on L2 / R2 would never fire. This turns a trigger pulled past halfway into an L2 / R2 press
 * and letting go (below a quarter) into its release. Controllers that do send the keys (the Thor, Switch
 * Pro) also move these axes on some firmwares; the key is only made from the axis when the device has no
 * L2 / R2 key of its own, so nothing is pressed twice.
 */
class TriggerKeys {
    private val down = HashMap<Int, Boolean>() // keyCode → held

    /** Calls [send] with the made-up key events for [event] (a joystick motion event). Returns whether any were sent. */
    fun onMotion(event: MotionEvent, send: (KeyEvent) -> Unit): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK || event.action != MotionEvent.ACTION_MOVE) return false
        val dev = event.device ?: return false
        var sent = false
        for ((code, axes) in listOf(
            KeyEvent.KEYCODE_BUTTON_L2 to intArrayOf(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE),
            KeyEvent.KEYCODE_BUTTON_R2 to intArrayOf(MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS),
        )) {
            if (dev.hasKeys(code).firstOrNull() == true) continue
            val v = axes.maxOf { event.getAxisValue(it) }
            val held = down[code] == true
            val now = if (held) v > 0.25f else v > 0.5f
            if (now == held) continue
            down[code] = now
            val t = SystemClock.uptimeMillis()
            send(KeyEvent(t, t, if (now) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, 0, 0, event.deviceId, 0, 0, InputDevice.SOURCE_GAMEPAD))
            sent = true
        }
        return sent
    }
}
