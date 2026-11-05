package io.github.hiramrg.delphis.platform

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import io.github.hiramrg.delphis.core.ControllerProfile

/** Which physical screen is which on a dual-screen handheld. */
data class ScreenRoles(val topDisplayId: Int, val bottomDisplayId: Int?) {
    val isDualScreen get() = bottomDisplayId != null
}

object DeviceInfo {

    /**
     * On the AYN Thor the 6" top panel is Android's default display and the 3.92" bottom panel is
     * a second built-in display. If anything else is connected (HDMI), the panel nearest in size to
     * 1240x1080 is preferred for the bottom.
     */
    fun screenRoles(context: Context, swap: Boolean): ScreenRoles {
        val dm = context.getSystemService(DisplayManager::class.java)
        val default = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return ScreenRoles(Display.DEFAULT_DISPLAY, null)
        val others = dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }
        val second = others.minByOrNull { d ->
            val mode = d.mode
            val area = mode.physicalWidth.toLong() * mode.physicalHeight
            kotlin.math.abs(area - 1240L * 1080L)
        } ?: return ScreenRoles(default.displayId, null)
        return if (swap) ScreenRoles(second.displayId, default.displayId) else ScreenRoles(default.displayId, second.displayId)
    }

    private fun allDevices(): List<InputDevice> =
        InputDevice.getDeviceIds().toList().mapNotNull { id -> InputDevice.getDevice(id) }

    private fun isGamepad(d: InputDevice) =
        (d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (d.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

    /** Name of the built-in gamepad, which Dolphin uses in "Android/<n>/<name>". */
    fun builtInGamepadName(): String? {
        val pads = allDevices().filter { !it.isVirtual && isGamepad(it) }
        return (pads.firstOrNull { !it.isExternal } ?: pads.firstOrNull())?.name
    }

    fun allGamepadNames(): List<String> = allDevices().filter { !it.isVirtual && isGamepad(it) }.map { it.name }.distinct()

    /**
     * Dolphin names each Android input device "Android/<id>/<name>". For gamepads the id is Android's
     * *controller number* (InputDevice.getControllerNumber(), usually 1, see AndroidDevice::GetPreferredId
     * in Dolphin's Android.cpp); other devices get the lowest free id per name. This replicates that
     * assignment, then also adds ids 0-4 for the same name so a binding survives the controller being
     * renumbered after a reconnect (unknown devices simply read as 0 in Dolphin).
     */
    fun dolphinQualifiers(names: List<String>): List<String> {
        val assigned = mutableMapOf<String, MutableSet<Int>>()
        val exact = mutableListOf<String>()
        for (d in allDevices()) {
            val used = assigned.getOrPut(d.name) { mutableSetOf() }
            val preferred = d.controllerNumber.takeIf { it != 0 && it !in used }
            val id = preferred ?: generateSequence(0) { it + 1 }.first { it !in used }
            used += id
            if (d.name in names) exact += "Android/$id/${d.name}"
        }
        val extras = names.flatMap { name -> (0..4).map { "Android/$it/$name" } }
        return (exact + extras).distinct()
    }

    /** Builds the controller description used to write profiles, from the user's calibration. */
    fun controllerProfile(prefs: Prefs): ControllerProfile {
        val names = listOfNotNull(prefs.deviceName ?: prefs.calibratedDeviceName)
            .ifEmpty { allGamepadNames() }
            .ifEmpty { listOf(ControllerProfile.DEFAULT_DEVICE_NAME) }
        return ControllerProfile(
            devices = dolphinQualifiers(names),
            calibrated = prefs.calibration,
            invertMotion = prefs.invertMotion,
            gyroSpeed = prefs.gyroSensitivity * 0.9,
            gyroHorizontal = prefs.gyroHorizontal,
        )
    }

    /** Human-readable dump of every input device, for the diagnostics report. */
    fun describeDevices(): String = allDevices().joinToString("\n") { d ->
        val axes = d.motionRanges.map { "${it.axis}(${MotionEvent.axisToString(it.axis)})" }.distinct()
        "#${d.id} \"${d.name}\" controllerNumber=${d.controllerNumber} virtual=${d.isVirtual} external=${d.isExternal} " +
            "sources=0x${Integer.toHexString(d.sources)} gamepad=${isGamepad(d)} axes=$axes"
    }

    /** Dolphin's name for a gamepad keycode (see KEYCODE_NAMES in Dolphin's Android.cpp). */
    fun dolphinNameForKeycode(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> "Button A"
        KeyEvent.KEYCODE_BUTTON_B -> "Button B"
        KeyEvent.KEYCODE_BUTTON_C -> "Button C"
        KeyEvent.KEYCODE_BUTTON_X -> "Button X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Button Y"
        KeyEvent.KEYCODE_BUTTON_Z -> "Button Z"
        KeyEvent.KEYCODE_BUTTON_L1 -> "Button L1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "Button R1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "Button L2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "Button R2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "Button L3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "Button R3"
        KeyEvent.KEYCODE_BUTTON_START -> "Start"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
        KeyEvent.KEYCODE_BUTTON_MODE -> "Mode"
        KeyEvent.KEYCODE_DPAD_UP -> "Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        KeyEvent.KEYCODE_DPAD_CENTER -> "Center"
        KeyEvent.KEYCODE_BACK -> "Back"
        KeyEvent.KEYCODE_MENU -> "Menu"
        in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16 -> "Button ${keyCode - KeyEvent.KEYCODE_BUTTON_1 + 1}"
        else -> null
    }
}
