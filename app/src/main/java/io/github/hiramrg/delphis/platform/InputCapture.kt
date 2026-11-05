package io.github.hiramrg.delphis.platform

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Turns raw Android key/motion events into the exact input names Dolphin's Android backend uses,
 * so the Controller Check records what Dolphin will really see.
 */
class InputCapture {
    data class Captured(
        /** Dolphin input name, e.g. "Button A" or "Axis 14-". */
        val name: String,
        val deviceName: String,
        /** For axes: the name without its sign ("Axis 14"), so the opposite direction can be derived. */
        val axisBase: String? = null,
        val positive: Boolean = true,
    ) {
        val opposite: String? get() = axisBase?.let { "$it${if (positive) "-" else "+"}" }
    }

    private val heldKeys = mutableSetOf<Int>()
    private val axisValues = mutableMapOf<String, Float>()
    private val axisRest = mutableMapOf<String, Float>()

    /** True when no key is held and every axis is back near its resting position. */
    fun isNeutral(): Boolean =
        heldKeys.isEmpty() && axisValues.all { (k, v) -> kotlin.math.abs(v - (axisRest[k] ?: 0f)) < 0.35f }

    fun onKey(e: KeyEvent): Captured? {
        when (e.action) {
            KeyEvent.ACTION_UP -> { heldKeys -= e.keyCode; return null }
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount > 0) return null
                heldKeys += e.keyCode
                val name = DeviceInfo.dolphinNameForKeycode(e.keyCode) ?: return null
                return Captured(name, e.device?.name ?: "?")
            }
        }
        return null
    }

    /** Returns the most deflected axis if it moved well past rest (>60% of its range). */
    fun onMotion(e: MotionEvent): Captured? {
        val device = e.device ?: return null
        var best: Captured? = null
        var bestDelta = 0.6f
        val ranges = device.motionRanges.filter { it.source == e.source }.ifEmpty { device.motionRanges }
        for (range in ranges) {
            val axis = range.axis
            val v = e.getAxisValue(axis)
            val key = "${device.id}:$axis"
            val rest = if (axis in TRIGGER_AXES) range.min.coerceAtMost(0f) else 0f
            axisRest[key] = rest
            axisValues[key] = v
            val delta = v - rest
            if (kotlin.math.abs(delta) > bestDelta) {
                bestDelta = kotlin.math.abs(delta)
                val base = "${axisPrefix(range.source)}$axis"
                val positive = delta > 0
                best = Captured("$base${if (positive) "+" else "-"}", device.name, base, positive)
            }
        }
        return best
    }

    companion object {
        private val TRIGGER_AXES = setOf(
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS, MotionEvent.AXIS_BRAKE,
        )

        /** Mirrors ConstructAxisNamePrefix() in Dolphin's Android.cpp. */
        fun axisPrefix(source: Int): String = when (source) {
            InputDevice.SOURCE_KEYBOARD -> "Keyboard "
            InputDevice.SOURCE_DPAD -> "Dpad "
            InputDevice.SOURCE_GAMEPAD -> "Gamepad "
            InputDevice.SOURCE_TOUCHSCREEN -> "Touch "
            InputDevice.SOURCE_MOUSE -> "Cursor "
            InputDevice.SOURCE_STYLUS -> "Stylus "
            InputDevice.SOURCE_TRACKBALL -> "Trackball "
            InputDevice.SOURCE_MOUSE_RELATIVE -> "Mouse "
            InputDevice.SOURCE_TOUCHPAD -> "Touchpad "
            InputDevice.SOURCE_TOUCH_NAVIGATION -> "Touchnav "
            InputDevice.SOURCE_JOYSTICK -> "Axis "
            InputDevice.SOURCE_HDMI -> "HDMI "
            InputDevice.SOURCE_ROTARY_ENCODER -> "Rotary "
            else -> "Axis %08x/".format(source)
        }
    }
}
