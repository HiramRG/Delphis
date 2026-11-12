package io.github.hiramrg.delphis.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.ControllerProfile
import io.github.hiramrg.delphis.core.Direction
import io.github.hiramrg.delphis.core.EmulatedController
import io.github.hiramrg.delphis.core.Mapping
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.core.Target
import io.github.hiramrg.delphis.core.ThorControl
import io.github.hiramrg.delphis.platform.InputCapture

/** One step of the Controller Check. Stick steps record one direction and derive the opposite. */
private data class CheckStep(val control: ThorControl, val dir: Direction?, val prompt: String, val isStick: Boolean = false)

private val STEPS = listOf(
    CheckStep(ThorControl.FACE_EAST, null, "Press  A  (right face button)"),
    CheckStep(ThorControl.FACE_SOUTH, null, "Press  B  (bottom face button)"),
    CheckStep(ThorControl.FACE_NORTH, null, "Press  X  (top face button)"),
    CheckStep(ThorControl.FACE_WEST, null, "Press  Y  (left face button)"),
    CheckStep(ThorControl.DPAD, Direction.UP, "Press D-pad UP"),
    CheckStep(ThorControl.DPAD, Direction.DOWN, "Press D-pad DOWN"),
    CheckStep(ThorControl.DPAD, Direction.LEFT, "Press D-pad LEFT"),
    CheckStep(ThorControl.DPAD, Direction.RIGHT, "Press D-pad RIGHT"),
    CheckStep(ThorControl.LEFT_STICK, Direction.UP, "Push the LEFT stick UP", isStick = true),
    CheckStep(ThorControl.LEFT_STICK, Direction.RIGHT, "Push the LEFT stick RIGHT", isStick = true),
    CheckStep(ThorControl.RIGHT_STICK, Direction.UP, "Push the RIGHT stick UP", isStick = true),
    CheckStep(ThorControl.RIGHT_STICK, Direction.RIGHT, "Push the RIGHT stick RIGHT", isStick = true),
    CheckStep(ThorControl.L1, null, "Press L1 (upper left shoulder)"),
    CheckStep(ThorControl.R1, null, "Press R1 (upper right shoulder)"),
    CheckStep(ThorControl.L2, null, "Pull L2 all the way (lower left trigger)"),
    CheckStep(ThorControl.R2, null, "Pull R2 all the way (lower right trigger)"),
    CheckStep(ThorControl.L3, null, "Click the LEFT stick in"),
    CheckStep(ThorControl.R3, null, "Click the RIGHT stick in"),
    CheckStep(ThorControl.START, null, "Press Start"),
    CheckStep(ThorControl.SELECT, null, "Press Select"),
)

private fun highlightPreset(control: ThorControl) = Preset(
    "check", "", "", EmulatedController.GAMECUBE, listOf(Mapping(control, Target.Buttons(emptyList()), "")),
)

/**
 * Walks through every control once and records the exact Dolphin input name it produces and which
 * Android device it came from. Presets are then written with those real names, which removes the
 * guesswork about how the Thor's firmware reports buttons, triggers, sticks and the D-pad.
 */
@Composable
fun ControllerCheckScreen(act: MainActivity) {
    var step by remember { mutableIntStateOf(0) }
    val recorded = remember { mutableStateMapOf<String, List<String>>() }
    val devices = remember { mutableStateListOf<String>() }
    var lastSeen by remember { mutableStateOf("") }
    val capture = remember { InputCapture() }
    var waitRelease by remember { mutableStateOf(false) }
    var lastCaptureAt by remember { mutableLongStateOf(0L) }

    fun accept(c: InputCapture.Captured) {
        if (step >= STEPS.size) return
        val s = STEPS[step]
        if (s.isStick && c.axisBase == null) return // sticks must be analog axes
        // D-pad and analog triggers can arrive as a key *or* an axis depending on the firmware; keep the
        // recorded input and also the usual alternatives so both paths work in Dolphin.
        val names = if (s.control == ThorControl.DPAD || s.control == ThorControl.L2 || s.control == ThorControl.R2)
            (listOf(c.name) + ControllerProfile.defaultInputs(s.control, s.dir)).distinct() else listOf(c.name)
        recorded[ControllerProfile.slotKey(s.control, s.dir)] = names
        if (s.isStick && c.opposite != null) recorded[ControllerProfile.slotKey(s.control, s.dir!!.opposite())] = listOf(c.opposite!!)
        devices += c.deviceName
        step++
        waitRelease = true
        lastCaptureAt = SystemClock.uptimeMillis()
    }

    fun ready() = !waitRelease || (capture.isNeutral() && SystemClock.uptimeMillis() - lastCaptureAt > 250)

    DisposableEffect(Unit) {
        act.keyListener = { e ->
            val c = capture.onKey(e)
            if (c != null) lastSeen = "${c.name}  ·  ${c.deviceName}"
            if (waitRelease && capture.isNeutral()) waitRelease = false
            if (c != null && ready()) accept(c)
            // Swallow gamepad keys while checking, but let the system Back key through when not captured.
            c != null || e.keyCode != android.view.KeyEvent.KEYCODE_BACK
        }
        act.motionListener = { e ->
            val c = capture.onMotion(e)
            if (c != null) lastSeen = "${c.name}  ·  ${c.deviceName}"
            if (waitRelease && capture.isNeutral()) waitRelease = false
            if (c != null && ready()) accept(c)
            true
        }
        onDispose { act.keyListener = null; act.motionListener = null }
    }

    val c = LocalWii.current
    val done = step >= STEPS.size
    val leave = { act.screen = if (act.isSetUp()) Screen.Settings else Screen.Setup }
    val current = if (step < STEPS.size) STEPS[step].control else null
    Column(Modifier.fillMaxSize()) {
        // Every button is recorded here (Ⓑ too), so this page is left by touch.
        PageHeader(if (act.isSetUp()) "Settings" else "Setup", leave, "Settings · Controls", "Controller check", Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp), backKey = false)
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val shape = RoundedCornerShape(18.dp)
            Box(Modifier.weight(1f).fillMaxHeight().clip(shape).background(c.card).border(2.dp, c.cardBorder, shape).padding(8.dp)) {
                ThorDiagram(highlightPreset(current ?: ThorControl.START), Modifier.fillMaxSize(), compact = true, highlight = setOfNotNull(current))
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(shape).background(c.card).border(2.dp, c.cardBorder, shape).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CapsLabel(if (done) "All ${STEPS.size} recorded" else "Step ${step + 1} of ${STEPS.size}")
                Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(50)).background(c.cardBorder)) {
                    Box(Modifier.fillMaxWidth(minOf(step, STEPS.size) / STEPS.size.toFloat()).fillMaxHeight().clip(RoundedCornerShape(50)).background(c.accent))
                }
                Spacer(Modifier.weight(1f))
                if (!done) {
                    Text(STEPS[step].prompt, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = c.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 26.sp)
                    Text(if (waitRelease) "Let go of everything…" else "Waiting for input", fontSize = 12.sp, color = c.textDim)
                } else {
                    Text("All controls recorded!", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
                    Text("Device: ${devices.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key}", fontSize = 12.sp, color = c.textDim)
                }
                if (lastSeen.isNotEmpty()) Text("Last input: $lastSeen", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = c.textDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!done) {
                        PanelBtn("Back", null, false, enabled = step > 0) { if (step > 0) step-- }
                        PanelBtn("Skip", null, false) { step++ }
                    } else {
                        PanelBtn("Start over", null, false) { step = 0; recorded.clear(); devices.clear() }
                        PanelBtn("Save", null, true) {
                            act.prefs.calibration = recorded.toMap()
                            act.prefs.calibratedDeviceName = devices.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                            act.screen = if (act.isSetUp()) Screen.Home else Screen.Setup
                        }
                    }
                }
            }
        }
        AppBottomBar("Press each control once · tap to go back")
    }
}
