package io.github.hiramrg.delphis.platform

import android.view.KeyEvent
import io.github.hiramrg.delphis.core.ControllerProfile
import io.github.hiramrg.delphis.core.Direction
import io.github.hiramrg.delphis.core.ThorControl

/**
 * One screen: some controllers don't report Android's standard gamepad keys. A Nintendo Switch Pro
 * Controller without Android's Nintendo driver, for example, sends "Button 1…14" instead of A / B / X / Y,
 * so Delphis' menus (which listen for A, B, Start…) ignored it. This turns such keys into the standard
 * ones, from the Controller check (each button recorded once) or, before a check, from the usual
 * Switch order. The Thor's own controls are never changed.
 */
object PadKeys {
    private val SLOT_KEYS: List<Pair<String, Int>> = listOf(
        ControllerProfile.slotKey(ThorControl.FACE_EAST) to KeyEvent.KEYCODE_BUTTON_A,
        ControllerProfile.slotKey(ThorControl.FACE_SOUTH) to KeyEvent.KEYCODE_BUTTON_B,
        ControllerProfile.slotKey(ThorControl.FACE_NORTH) to KeyEvent.KEYCODE_BUTTON_X,
        ControllerProfile.slotKey(ThorControl.FACE_WEST) to KeyEvent.KEYCODE_BUTTON_Y,
        ControllerProfile.slotKey(ThorControl.L1) to KeyEvent.KEYCODE_BUTTON_L1,
        ControllerProfile.slotKey(ThorControl.R1) to KeyEvent.KEYCODE_BUTTON_R1,
        ControllerProfile.slotKey(ThorControl.L2) to KeyEvent.KEYCODE_BUTTON_L2,
        ControllerProfile.slotKey(ThorControl.R2) to KeyEvent.KEYCODE_BUTTON_R2,
        ControllerProfile.slotKey(ThorControl.L3) to KeyEvent.KEYCODE_BUTTON_THUMBL,
        ControllerProfile.slotKey(ThorControl.R3) to KeyEvent.KEYCODE_BUTTON_THUMBR,
        ControllerProfile.slotKey(ThorControl.START) to KeyEvent.KEYCODE_BUTTON_START,
        ControllerProfile.slotKey(ThorControl.SELECT) to KeyEvent.KEYCODE_BUTTON_SELECT,
        ControllerProfile.slotKey(ThorControl.DPAD, Direction.UP) to KeyEvent.KEYCODE_DPAD_UP,
        ControllerProfile.slotKey(ThorControl.DPAD, Direction.DOWN) to KeyEvent.KEYCODE_DPAD_DOWN,
        ControllerProfile.slotKey(ThorControl.DPAD, Direction.LEFT) to KeyEvent.KEYCODE_DPAD_LEFT,
        ControllerProfile.slotKey(ThorControl.DPAD, Direction.RIGHT) to KeyEvent.KEYCODE_DPAD_RIGHT,
    )

    /** Switch controllers in plain HID mode: Button 1 = B, 2 = A, 3 = Y, 4 = X, 5 = L, 6 = R, 7 = ZL, 8 = ZR, 9 = −, 10 = +, 11/12 = sticks, 13 = Home. */
    private val SWITCH_ORDER = mapOf(
        1 to KeyEvent.KEYCODE_BUTTON_B, 2 to KeyEvent.KEYCODE_BUTTON_A, 3 to KeyEvent.KEYCODE_BUTTON_Y, 4 to KeyEvent.KEYCODE_BUTTON_X,
        5 to KeyEvent.KEYCODE_BUTTON_L1, 6 to KeyEvent.KEYCODE_BUTTON_R1, 7 to KeyEvent.KEYCODE_BUTTON_L2, 8 to KeyEvent.KEYCODE_BUTTON_R2,
        9 to KeyEvent.KEYCODE_BUTTON_SELECT, 10 to KeyEvent.KEYCODE_BUTTON_START, 11 to KeyEvent.KEYCODE_BUTTON_THUMBL,
        12 to KeyEvent.KEYCODE_BUTTON_THUMBR, 13 to KeyEvent.KEYCODE_BUTTON_MODE,
    )

    private var cachedFor: Map<String, List<String>>? = null
    private var cached: Map<Int, Int> = emptyMap()

    /** Keycode → standard keycode, from the Controller check. */
    private fun fromCheck(prefs: Prefs): Map<Int, Int> {
        val cal = prefs.calibration
        if (cal == cachedFor) return cached
        val byName = (KeyEvent.KEYCODE_UNKNOWN..KeyEvent.getMaxKeyCode()).mapNotNull { k -> DeviceInfo.dolphinNameForKeycode(k)?.let { it to k } }.toMap()
        val out = mutableMapOf<Int, Int>()
        for ((slot, target) in SLOT_KEYS) {
            val code = cal[slot]?.firstNotNullOfOrNull { byName[it] } ?: continue
            if (code != target) out[code] = target
        }
        cachedFor = cal; cached = out
        return out
    }

    /** The standard keycode [e] stands for (or [e] itself). Only on one screen, and never for the Thor. */
    fun translate(e: KeyEvent, prefs: Prefs, oneScreen: Boolean): KeyEvent {
        if (!oneScreen) return e
        val device = e.device ?: return e
        if (device.isVirtual) return e
        val target = if (device.name == prefs.calibratedDeviceName) fromCheck(prefs)[e.keyCode]
        else if (device.vendorId == 0x057E && e.keyCode in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16)
            SWITCH_ORDER[e.keyCode - KeyEvent.KEYCODE_BUTTON_1 + 1]
        else null
        if (target == null || target == e.keyCode) return e
        return KeyEvent(e.downTime, e.eventTime, e.action, target, e.repeatCount, e.metaState, e.deviceId, e.scanCode, e.flags, e.source)
    }

    /**
     * What Android itself would do with an unused A or B: press the item under the cursor, or go back.
     * (Android does this for standard keys; translated ones need it done here.)
     */
    fun fallback(e: KeyEvent): KeyEvent? {
        val code = when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> return null
        }
        return KeyEvent(e.downTime, e.eventTime, e.action, code, e.repeatCount, e.metaState, e.deviceId, e.scanCode, e.flags, e.source)
    }
}
