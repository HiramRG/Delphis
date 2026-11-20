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
        // Settings → Display → Screen mode "One": everything on the main screen, even with a second one.
        if (Prefs(context).screenMode == 1) return ScreenRoles(default.displayId, null)
        val others = dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }
        val second = others.minByOrNull { d ->
            val mode = d.mode
            val area = mode.physicalWidth.toLong() * mode.physicalHeight
            kotlin.math.abs(area - 1240L * 1080L)
        } ?: return ScreenRoles(default.displayId, null)
        return if (swap) ScreenRoles(second.displayId, default.displayId) else ScreenRoles(default.displayId, second.displayId)
    }

    /** True when a second built-in screen exists (whatever Screen mode says). */
    fun hasSecondScreen(context: Context): Boolean =
        context.getSystemService(DisplayManager::class.java).displays.any { it.displayId != Display.DEFAULT_DISPLAY }

    /** True when Delphis runs on one screen (phones, tablets, or Screen mode "One"). */
    fun oneScreen(context: Context): Boolean = screenRoles(context, Prefs(context).swapScreens).bottomDisplayId == null

    /**
     * Every input device whose name is safe to write into Dolphin's settings. A name with a line break, a control
     * character or a backtick (Dolphin's quote) could add lines or controls to its files, so such a device is left out.
     */
    private fun allDevices(): List<InputDevice> =
        InputDevice.getDeviceIds().toList().mapNotNull { id -> InputDevice.getDevice(id) }.filter { safeName(it.name) }

    fun safeName(name: String?): Boolean = !name.isNullOrEmpty() && name.none { it.isISOControl() || it == '`' }

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
    /** Dolphin's "Android/<id>/<name>" for every input device, by Android device id (same assignment as below). */
    fun dolphinQualifiersById(): Map<Int, String> {
        val assigned = mutableMapOf<String, MutableSet<Int>>()
        val out = mutableMapOf<Int, String>()
        for (d in allDevices()) {
            val used = assigned.getOrPut(d.name) { mutableSetOf() }
            val preferred = d.controllerNumber.takeIf { it != 0 && it !in used }
            val id = preferred ?: generateSequence(0) { it + 1 }.first { it !in used }
            used += id
            out[d.id] = "Android/$id/${d.name}"
        }
        return out
    }

    /** Real gamepads (not virtual), for multiplayer: Android device, its name and whether it's external. */
    /** A handheld's own controls (not a keyboard or button strip that happens to report gamepad keys): face buttons and a stick. */
    fun looksLikeHandheldPad(d: InputDevice): Boolean = runCatching {
        d.hasKeys(android.view.KeyEvent.KEYCODE_BUTTON_A, android.view.KeyEvent.KEYCODE_BUTTON_B).all { it } && d.getMotionRange(android.view.MotionEvent.AXIS_X) != null
    }.getOrDefault(false)

    /** This device's name for people: "Retroid Pocket 5", "Galaxy Tab S12+"… */
    fun deviceLabel(): String {
        val model = android.os.Build.MODEL.orEmpty().trim()
        val maker = android.os.Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.uppercase() }
        return when {
            model.isEmpty() -> "Built-in controls"
            model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model"
        }
    }

    fun gamepads(): List<InputDevice> = allDevices().filter { !it.isVirtual && isGamepad(it) }

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
        // Only the Thor's own pad: an external controller is a separate player (see Pads).
        val names = listOfNotNull(prefs.deviceName ?: prefs.calibratedDeviceName)
            .ifEmpty { listOfNotNull(builtInGamepadName()) }
            .ifEmpty { allGamepadNames() }
            .ifEmpty { listOf(ControllerProfile.DEFAULT_DEVICE_NAME) }
        return ControllerProfile(
            devices = dolphinQualifiers(names),
            calibrated = prefs.calibration,
            invertMotion = prefs.invertMotion,
            gyroSpeed = prefs.gyroSensitivity * 0.9,
            gyroHorizontal = prefs.gyroHorizontal,
            gyroInvertX = prefs.gyroInvertX,
            gyroInvertY = prefs.gyroInvertY,
            rumble = prefs.rumble,
            // Two screens: these are the Thor's own controls, so "device" rumble also tries their motor.
            rumbleBuiltIn = !oneScreen(prefs.context),
        )
    }

    /** Human-readable dump of every input device, for the diagnostics report. */
    fun describeDevices(): String = allDevices().joinToString("\n") { d ->
        val axes = d.motionRanges.map { "${it.axis}(${MotionEvent.axisToString(it.axis)})" }.distinct()
        "#${d.id} \"${d.name}\" vendor=0x${Integer.toHexString(d.vendorId)} product=0x${Integer.toHexString(d.productId)} controllerNumber=${d.controllerNumber} virtual=${d.isVirtual} external=${d.isExternal} " +
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

    /** What resolution this device should manage, and why (for the Picture panel's Resolution tab). */
    data class ResolutionAdvice(val scale: Int, val device: String, val chip: String?, val screen: String, val known: Boolean)

    fun resolutionAdvice(ctx: Context): ResolutionAdvice {
        val chipId = (if (android.os.Build.VERSION.SDK_INT >= 31) android.os.Build.SOC_MODEL else null)
            ?.takeIf { it.isNotBlank() && it != android.os.Build.UNKNOWN } ?: android.os.Build.HARDWARE.orEmpty()
        val known = io.github.hiramrg.delphis.core.Resolution.chipScale(chipId)
        val chipScale = known ?: run {
            // Not recognised: guess from memory (8 GB and more is usually a recent, fast chip).
            val mem = android.app.ActivityManager.MemoryInfo().also { ctx.getSystemService(android.app.ActivityManager::class.java)?.getMemoryInfo(it) }
            if (mem.totalMem >= 7L * 1024 * 1024 * 1024) 2 else 1
        }
        val mode = runCatching { ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).mode }.getOrNull()
        val dm = ctx.resources.displayMetrics
        val w = mode?.physicalWidth ?: dm.widthPixels
        val h = mode?.physicalHeight ?: dm.heightPixels
        val short = minOf(w, h)
        val long = maxOf(w, h)
        val name = runCatching { android.provider.Settings.Global.getString(ctx.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: deviceLabel()
        return ResolutionAdvice(
            io.github.hiramrg.delphis.core.Resolution.recommend(chipScale, short), name,
            io.github.hiramrg.delphis.core.Resolution.chipFamily(chipId), "$long × $short", known != null,
        )
    }
}
