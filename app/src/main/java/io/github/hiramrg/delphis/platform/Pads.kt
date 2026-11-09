package io.github.hiramrg.delphis.platform

import android.content.Context
import android.hardware.input.InputManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.hiramrg.delphis.core.ButtonLabels
import io.github.hiramrg.delphis.core.ControllerProfile
import io.github.hiramrg.delphis.core.Pad
import io.github.hiramrg.delphis.core.Players
import io.github.hiramrg.delphis.core.Preset

/** Connected controllers for multiplayer, and the controller description each player's profile is written with. */
object Pads {
    /**
     * Every connected gamepad, one per physical controller (Android may split one controller into
     * several input devices with the same descriptor). The Thor's pad is the internal one, or the one
     * the controller check / settings named.
     */
    fun detect(prefs: Prefs): List<Pad> {
        val thorNames = setOfNotNull(prefs.deviceName, prefs.calibratedDeviceName)
        val qualifiers = DeviceInfo.dolphinQualifiersById()
        val pads = DeviceInfo.gamepads().groupBy { it.descriptor ?: "id${it.id}" }.map { (key, devs) ->
            val d = devs.first()
            val builtIn = d.name in thorNames || (thorNames.isEmpty() && !d.isExternal)
            Triple(key, d, builtIn) to devs.mapNotNull { qualifiers[it.id] }
        }
        val names = pads.groupingBy { it.first.second.name }.eachCount()
        return pads.map { (info, exact) ->
            val (key, d, builtIn) = info
            val quals = if (builtIn) DeviceInfo.dolphinQualifiers(listOf(d.name))
            // A controller whose name is unique also gets ids 0-4, in case Dolphin numbers it differently.
            else (exact + if (names[d.name] == 1) (0..4).map { "Android/$it/${d.name}" } else emptyList()).distinct()
            Pad(key, d.name, quals, builtIn, if (builtIn) ButtonLabels.NINTENDO else ButtonLabels.forVendor(d.vendorId), if (builtIn) true else prefs.padByLabel(key))
        }
    }

    /** Controller description for [pad]: the Thor keeps its calibration; others use Android's standard layout (buttons by position). */
    fun profileFor(pad: Pad?, prefs: Prefs): ControllerProfile = when {
        pad == null || pad.builtIn -> DeviceInfo.controllerProfile(prefs)
        // Reports buttons by letter on a Nintendo layout: Button A is on the right, Button B at the bottom.
        pad.byLabel == true -> ControllerProfile(
            devices = pad.qualifiers,
            calibrated = ControllerProfile.NINTENDO_FACE_BUTTONS.map { (k, v) -> ControllerProfile.slotKey(k) to listOf(v) }.toMap(),
        )
        else -> ControllerProfile(devices = pad.qualifiers)
    }

    /** True when [players] means gyro / tilt / trackpad presets are off (several players, or the Thor sitting out). */
    fun multiplayer(players: List<Pad>): Boolean = players.size > 1 || players.any { !it.builtIn }

    /** Who plays [gameKey] right now, P1 first. */
    fun players(prefs: Prefs, gameKey: String?): List<Pad> {
        val pads = detect(prefs)
        return if (gameKey == null) pads.filter { it.builtIn }.take(1) else Players.assign(pads, prefs.playerSetup(gameKey))
    }

    /** P1's preset and controller, then players 2-4's, for [preset] and the controllers connected now. */
    fun slots(prefs: Prefs, gameKey: String?, preset: Preset): List<Pair<Preset, ControllerProfile>> {
        val players = players(prefs, gameKey).ifEmpty { listOf(null) }
        // Gyro, tilt and trackpad only when the Thor plays alone; otherwise everyone gets the plain version.
        val solo = players.size == 1 && players[0]?.builtIn != false
        val p = if (solo) preset else Players.multiplayerPreset(preset)
        return players.map { pad -> p to profileFor(pad, prefs) }
    }
}

/** Connected controllers, kept up to date while the screen is shown. */
@Composable
fun rememberPads(prefs: Prefs): List<Pad> {
    val ctx = LocalContext.current
    var pads by remember { mutableStateOf(runCatching { Pads.detect(prefs) }.getOrDefault(emptyList())) }
    DisposableEffect(Unit) {
        val im = ctx.getSystemService(Context.INPUT_SERVICE) as InputManager
        val listener = object : InputManager.InputDeviceListener {
            fun refresh() { pads = runCatching { Pads.detect(prefs) }.getOrDefault(pads) }
            override fun onInputDeviceAdded(deviceId: Int) = refresh()
            override fun onInputDeviceRemoved(deviceId: Int) = refresh()
            override fun onInputDeviceChanged(deviceId: Int) = refresh()
        }
        im.registerInputDeviceListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
        onDispose { im.unregisterInputDeviceListener(listener) }
    }
    return pads
}
