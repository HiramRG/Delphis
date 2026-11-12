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

    // Two columns so everything fits the Thor's wide top screen without scrolling.
    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // Left: the "TV" with the pointer, sized to whatever height is available.
        Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◀ Gyro test", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { act.screen = Screen.Settings }.padding(4.dp))
                Spacer(Modifier.width(8.dp))
                HintText("Hold the Thor the way you play and aim. R3 or Ⓐ centers" + if (precision) ", hold R1 to pause." else ".", fontSize = 11.sp)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val w = minOf(maxWidth, maxHeight * 16f / 9f)
                val f = frame // read so the canvas redraws each frame
                Box(
                    Modifier.size(w, w * 9f / 16f).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0B1220)).border(2.dp, c.accent, RoundedCornerShape(12.dp)),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        f.hashCode()
                        val mid = Offset(size.width / 2, size.height / 2)
                        drawLine(Color(0xFF1E2B44), Offset(mid.x, 0f), Offset(mid.x, size.height), 2f)
                        drawLine(Color(0xFF1E2B44), Offset(0f, mid.y), Offset(size.width, mid.y), 2f)
                        val p = Offset(mid.x + (state.x * size.width / 2).toFloat(), mid.y - (state.y * size.height / 2).toFloat())
                        drawCircle(Glow.Yellow, 22f, p, style = Stroke(5f))
                        drawCircle(Glow.Yellow, 5f, p)
                    }
                    if (!state.hasGyro) Text("No gyroscope data yet…", Modifier.align(Alignment.Center), color = Color.White)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WiiButton("Center", onClick = { state.center() }, fontSize = 14.sp)
                WiiButton("Copy readings", onClick = {
                    act.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Gyro readings", readings))
                    Toast.makeText(act, "Readings copied", Toast.LENGTH_SHORT).show()
                }, fontSize = 14.sp)
            }
            Text(readings, fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 11.sp, color = c.textDim, maxLines = 4)
        }
        // Right: settings.
        WiiCard(Modifier.weight(1f).fillMaxHeight(), radius = 20.dp, padding = 12.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Slow moves aim finely, fast moves go far. Hold R1 to freeze the pointer and re-position. Matches the \"Gyro Aiming\" presets.",
                    fontSize = 11.sp, color = c.textDim,
                )
                Text("Left/right follows", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GyroHorizontal.entries.forEach { m -> WiiChip(m.label, mode == m, { mode = m; prefs.gyroHorizontal = m }) }
                }
                Text(mode.description, fontSize = 11.sp, color = c.textDim, maxLines = 3)
                Text("Speed: ${speedLevel.toInt()}  (5 ≈ a real Wii Remote)", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
                Slider(value = speedLevel, onValueChange = { speedLevel = it }, onValueChangeFinished = { prefs.gyroSensitivity = speedLevel.toInt() }, valueRange = 1f..10f, steps = 8)
                Text("Invert", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WiiChip("Up/down", invertY, { invertY = !invertY; prefs.gyroInvertY = invertY })
                    WiiChip("Left/right", invertX, { invertX = !invertX; prefs.gyroInvertX = invertX })
                }
                Text("Saved for every gyro preset (also in Settings → Gyro aiming).", fontSize = 11.sp, color = c.textDim)
            }
        }
    }
}
