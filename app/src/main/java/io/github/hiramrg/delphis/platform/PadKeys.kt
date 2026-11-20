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
    /** The Controller check records positions; this is the standard (Xbox-style) key for each position. */
    private val SLOT_KEYS: List<Pair<String, Int>> = listOf(
        ControllerProfile.slotKey(ThorControl.FACE_SOUTH) to KeyEvent.KEYCODE_BUTTON_A,
        ControllerProfile.slotKey(ThorControl.FACE_EAST) to KeyEvent.KEYCODE_BUTTON_B,
        ControllerProfile.slotKey(ThorControl.FACE_WEST) to KeyEvent.KEYCODE_BUTTON_X,
        ControllerProfile.slotKey(ThorControl.FACE_NORTH) to KeyEvent.KEYCODE_BUTTON_Y,
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

    /** Switch controllers in plain HID mode: Button 1 = B, 2 = A, 3 = Y, 4 = X, 5 = L, 6 = R, 7 = ZL, 8 = ZR, 9 = −, 10 = +, 11/12 = sticks, 13 = Home (by letter). */
    private val SWITCH_ORDER = mapOf(
        1 to KeyEvent.KEYCODE_BUTTON_B, 2 to KeyEvent.KEYCODE_BUTTON_A, 3 to KeyEvent.KEYCODE_BUTTON_Y, 4 to KeyEvent.KEYCODE_BUTTON_X,
        5 to KeyEvent.KEYCODE_BUTTON_L1, 6 to KeyEvent.KEYCODE_BUTTON_R1, 7 to KeyEvent.KEYCODE_BUTTON_L2, 8 to KeyEvent.KEYCODE_BUTTON_R2,
        9 to KeyEvent.KEYCODE_BUTTON_SELECT, 10 to KeyEvent.KEYCODE_BUTTON_START, 11 to KeyEvent.KEYCODE_BUTTON_THUMBL,
        12 to KeyEvent.KEYCODE_BUTTON_THUMBR, 13 to KeyEvent.KEYCODE_BUTTON_MODE,
    )

    /** A ↔ B, X ↔ Y: from Android's positions (A bottom) to a Nintendo layout's letters (A right). */
    private val NINTENDO_SWAP = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_B to KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_X to KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_Y to KeyEvent.KEYCODE_BUTTON_X,
    )

    private var cachedFor: Map<String, List<String>>? = null
    private var cached: Map<Int, Int> = emptyMap()

    /** Keycode → standard (positional) keycode, from the Controller check. */
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

    /**
     * Whether [device]'s letters are on a Nintendo layout (A right, B bottom, X top, Y left): Nintendo controllers
     * yes, Xbox and PlayStation no, others as the "press A" check found (Xbox-style until then).
     */
    fun nintendoLayout(device: android.view.InputDevice, prefs: Prefs): Boolean = when (device.vendorId) {
        0x057E -> true
        0x045E, 0x054C -> false
        else -> prefs.padNintendoLayout(device.descriptor ?: "") ?: false
    }

    /**
     * One screen: the key [e] stands for, by the letter printed on the controller (A confirms, B goes back, X / Y
     * as printed; PlayStation ✕ = A, ○ = B, □ = X, △ = Y). Android reports most controllers by position (Xbox
     * style: A at the bottom), so a Nintendo layout gets A ↔ B and X ↔ Y. The Thor and two screens: unchanged.
     */
    fun translate(e: KeyEvent, prefs: Prefs, oneScreen: Boolean): KeyEvent {
        if (!oneScreen) return e
        val device = e.device ?: return e
        if (device.isVirtual) return e
        val key = device.descriptor ?: ""
        val target = when {
            // The checked controller: its recorded positions, then its letters.
            device.name == prefs.calibratedDeviceName -> {
                val pos = fromCheck(prefs)[e.keyCode] ?: e.keyCode
                if (nintendoLayout(device, prefs)) NINTENDO_SWAP[pos] ?: pos else pos
            }
            !device.isExternal -> null
            device.vendorId == 0x057E && e.keyCode in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16 -> SWITCH_ORDER[e.keyCode - KeyEvent.KEYCODE_BUTTON_1 + 1]
            // Reports its letters already (found by the Players window's one-press check).
            prefs.padByLabel(key) == true -> null
            nintendoLayout(device, prefs) -> NINTENDO_SWAP[e.keyCode]
            else -> null
        }
        if (target == null || target == e.keyCode) return e
        return KeyEvent(e.downTime, e.eventTime, e.action, target, e.repeatCount, e.metaState, e.deviceId, e.scanCode, e.flags, e.source)
    }

    /** True for a controller button Delphis decides about itself (never passed on to Android's own meaning). */
    fun isPadButton(e: KeyEvent): Boolean =
        (e.keyCode in KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_MODE || e.keyCode in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16) &&
            e.keyCode != KeyEvent.KEYCODE_BUTTON_MODE

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
