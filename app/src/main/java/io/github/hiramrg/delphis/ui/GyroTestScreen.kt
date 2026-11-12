package io.github.hiramrg.delphis.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.KeyEvent
import android.view.Surface
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.GyroAim
import io.github.hiramrg.delphis.core.GyroHorizontal
import kotlin.math.abs

/** Latest sensor values and the simulated pointer, updated from the sensor thread. */
private class GyroState {
    @Volatile var g = DoubleArray(3)
    @Volatile var a = doubleArrayOf(0.0, 0.0, 9.81)
    val tracker = GyroAim.Tracker()
    val x get() = tracker.x
    val y get() = tracker.y
    fun center() { tracker.center(a[1], a[2]) }
    /** Peak |rate| per gyro axis over the last moments, to show which axis a movement used. */
    val peak = DoubleArray(3)
    var lastGyroNs = 0L
    /** R1 held: precision aim is paused. */
    @Volatile var hold = false
    var hasGyro = false
}

/**
 * Lets the user try gyro aiming without starting a game: the pointer here is driven by exactly the
 * same math Delphis writes into the Dolphin profile (GyroAim), reading the same Android sensors
 * Dolphin's "Device Sensors" uses, with the same screen-rotation adjustment Dolphin applies.
 */
@Composable
fun GyroTestScreen(act: MainActivity) {
    val prefs = act.prefs
    var mode by remember { mutableStateOf(prefs.gyroHorizontal) }
    var speedLevel by remember { mutableFloatStateOf(prefs.gyroSensitivity.toFloat()) }
    var invertX by remember { mutableStateOf(prefs.gyroInvertX) }
    var invertY by remember { mutableStateOf(prefs.gyroInvertY) }
    val precision = true // only the precision style is offered (the "Gyro Aiming" presets)
    val state = remember { GyroState() }
    var frame by remember { mutableLongStateOf(0L) }

    val modeNow by rememberUpdatedState(mode)
    val speedNow by rememberUpdatedState(speedLevel * 0.9)
    val invXNow by rememberUpdatedState(invertX)
    val invYNow by rememberUpdatedState(invertY)
    val precisionNow by rememberUpdatedState(precision)

    // Dolphin rotates the sensor X/Y axes by the rotation of the display it runs on.
    @Suppress("DEPRECATION")
    val rotation = remember {
        runCatching { if (Build.VERSION.SDK_INT >= 30) act.display!!.rotation else act.windowManager.defaultDisplay.rotation }
            .getOrDefault(Surface.ROTATION_0)
    }

    DisposableEffect(Unit) {
        val sm = act.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fun rotated(v: FloatArray): DoubleArray {
            val (x, y) = when (rotation) {
                Surface.ROTATION_90 -> -v[1] to v[0]
                Surface.ROTATION_180 -> -v[0] to -v[1]
                Surface.ROTATION_270 -> v[1] to -v[0]
                else -> v[0] to v[1]
            }
            return doubleArrayOf(x.toDouble(), y.toDouble(), v[2].toDouble())
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> state.a = rotated(e.values)
                    Sensor.TYPE_GYROSCOPE -> {
                        val g = rotated(e.values)
                        state.g = g
                        state.hasGyro = true
                        for (i in 0..2) state.peak[i] = maxOf(abs(g[i]), state.peak[i] * 0.995)
                        if (state.lastGyroNs != 0L) {
                            val dt = (e.timestamp - state.lastGyroNs) / 1e9
                            if (dt in 0.0..0.1) {
                                val a = state.a
                                state.tracker.step(dt, modeNow, invXNow, invYNow, speedNow, g[0], g[1], g[2], a[0], a[1], a[2], precisionNow, state.hold)
                            }
                        }
                        state.lastGyroNs = e.timestamp
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        // R3 or A centers the pointer, like R3 does in the game.
        act.keyListener = { e ->
            when {
                e.keyCode == KeyEvent.KEYCODE_BUTTON_R1 -> { state.hold = e.action == KeyEvent.ACTION_DOWN; true }
                e.action == KeyEvent.ACTION_DOWN && (e.keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR || e.keyCode == KeyEvent.KEYCODE_BUTTON_A) -> { state.center(); true }
                else -> false
            }
        }
        onDispose { sm.unregisterListener(listener); act.keyListener = null }
    }
    // Redraw every frame while this screen is open (sensors are only read while it's open).
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }

    val c = LocalWii.current
    val g = state.g; val a = state.a
    val axisNames = listOf("X (tilt toward/away)", "Y (turn, held upright)", "Z (turn, lying flat)")
    val strongest = state.peak.indices.maxByOrNull { state.peak[it] }
    val readings = buildString {
        appendLine("gyro rad/s  x=%+.2f y=%+.2f z=%+.2f".format(g[0], g[1], g[2]))
        appendLine("accel m/s²  x=%+.1f y=%+.1f z=%+.1f".format(a[0], a[1], a[2]))
        append("display rotation ${rotation * 90}°, mode ${mode.name}, speed ${speedLevel.toInt()}, ${if (precision) "precision" else "classic"}")
        if (strongest != null && state.peak[strongest] > 0.3) append("\nstrongest recent rotation: around ${axisNames[strongest]}")
    }

    val copy = {
        act.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Gyro readings", readings))
        Notices.done(act, "Readings copied")
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Settings", { act.screen = Screen.Settings }, "Settings · Gyro", "Gyro test", Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp)) {
            PanelBtn("Copy readings", null, false, onClick = copy)
            PanelBtn("Center", "A", true) { state.center() }
        }
        // Two columns so everything fits the Thor's wide top screen without scrolling.
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Left: the "TV" with the pointer, sized to whatever height is available.
            BoxWithConstraints(Modifier.weight(1.25f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val w = minOf(maxWidth, maxHeight * 16f / 9f)
                val f = frame // read so the canvas redraws each frame
                Box(
                    Modifier.size(w, w * 9f / 16f).clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0B1220)).border(2.dp, c.cardBorder, RoundedCornerShape(18.dp)),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        f.hashCode()
                        val mid = Offset(size.width / 2, size.height / 2)
                        drawLine(Color(0xFF1E2B44), Offset(mid.x, 0f), Offset(mid.x, size.height), 2f)
                        drawLine(Color(0xFF1E2B44), Offset(0f, mid.y), Offset(size.width, mid.y), 2f)
                        val p = Offset(mid.x + (state.x * size.width / 2).toFloat(), mid.y - (state.y * size.height / 2).toFloat())
                        drawCircle(c.accent, 22f, p, style = Stroke(5f))
                        drawCircle(c.accent, 5f, p)
                    }
                    if (!state.hasGyro) Text("No gyroscope data yet…", Modifier.align(Alignment.Center), color = Color.White, fontSize = 13.sp)
                    HintText("Aim the way you play · R3 or Ⓐ centers" + if (precision) " · hold R1 to pause" else "", Modifier.align(Alignment.BottomStart).padding(10.dp), fontSize = 11.sp, color = Color(0xFFA3ADB8))
                }
            }
            // Right: settings, in the shared card rows.
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupCard {
                    SettingRow("Left / right follows", mode.description) {
                        Segmented(GyroHorizontal.entries.map { it to it.label }, mode, { mode = it; prefs.gyroHorizontal = it })
                    }
                    RowDivider()
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("Speed · ${speedLevel.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        Text("5 ≈ a real Wii Remote. Slow moves aim finely, fast moves go far.", fontSize = 12.sp, color = c.textDim)
                        Slider(
                            value = speedLevel, onValueChange = { speedLevel = it }, onValueChangeFinished = { prefs.gyroSensitivity = speedLevel.toInt() }, valueRange = 1f..10f, steps = 8,
                            colors = SliderDefaults.colors(thumbColor = c.text, activeTrackColor = c.accent, inactiveTrackColor = c.cardBorder, activeTickColor = c.onAccent, inactiveTickColor = c.textDim),
                        )
                    }
                    RowDivider()
                    ToggleRow("Invert up / down", null, invertY) { invertY = it; prefs.gyroInvertY = it }
                    RowDivider()
                    ToggleRow("Invert left / right", null, invertX) { invertX = it; prefs.gyroInvertX = it }
                }
                Text("Saved for every gyro preset (also in Settings → Gyro aiming).", fontSize = 11.sp, color = c.textDim)
                Text(readings, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, lineHeight = 13.sp, color = c.textDim)
            }
        }
        AppBottomBar("Ⓐ Center  Ⓑ Back")
    }
}
