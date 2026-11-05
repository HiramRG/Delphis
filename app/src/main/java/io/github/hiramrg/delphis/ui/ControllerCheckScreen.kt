package io.github.hiramrg.delphis.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        val wide = maxWidth > maxHeight
        val done = step >= STEPS.size
        val body: @Composable (Modifier) -> Unit = { m ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Controller check  ${minOf(step + 1, STEPS.size)}/${STEPS.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                if (!done) {
                    Text(STEPS[step].prompt, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                    Text(if (waitRelease) "Let go of everything…" else "Waiting for input", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("All controls recorded!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Device: ${devices.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key}", fontSize = 13.sp)
                }
                if (lastSeen.isNotEmpty()) Text("Last input: $lastSeen", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    if (!done) {
                        OutlinedButton(onClick = { if (step > 0) step-- }, enabled = step > 0) { Text("Back") }
                        OutlinedButton(onClick = { step++ }) { Text("Skip") }
                    } else {
                        OutlinedButton(onClick = { step = 0; recorded.clear(); devices.clear() }) { Text("Start over") }
                        Button(onClick = {
                            act.prefs.calibration = recorded.toMap()
                            act.prefs.calibratedDeviceName = devices.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                            act.screen = if (act.isSetUp()) Screen.Library else Screen.Setup
                        }) { Text("Save") }
                    }
                    if (!done) TextButton(onClick = { act.screen = if (act.isSetUp()) Screen.Library else Screen.Setup }) { Text("Cancel") }
                }
                if (done) {
                    Text(
                        recorded.entries.sortedBy { it.key }.joinToString("\n") { "${it.key} = ${it.value.joinToString()}" },
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp).heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
        val current = if (step < STEPS.size) STEPS[step].control else null
        val diagram: @Composable (Modifier) -> Unit = { m ->
            ThorDiagram(highlightPreset(current ?: ThorControl.START), m, compact = true, highlight = setOfNotNull(current))
        }
        if (wide) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            diagram(Modifier.weight(1f).fillMaxHeight())
            body(Modifier.weight(1f))
        } else Column(Modifier.fillMaxSize()) {
            diagram(Modifier.fillMaxWidth().weight(0.4f))
            body(Modifier.fillMaxWidth().weight(0.6f))
        }
    }
}
