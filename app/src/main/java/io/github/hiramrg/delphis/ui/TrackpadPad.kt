package io.github.hiramrg.delphis.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.Trackpad

private val PadAccent = Color(0xFF34BEED)
private val PadIdleBorder = Color(0xFF3A6F86)

/**
 * The in-game trackpad (option "A · while aiming"): a big pad that reports finger movement in dp.
 * Double-tapping it recenters. [onActive] tells the screen to fade everything else while a finger is on it.
 */
@Composable
fun TrackpadPad(
    modifier: Modifier,
    onActive: (Boolean) -> Unit,
    onDown: () -> Unit,
    onMove: (dxDp: Float, dyDp: Float) -> Unit,
    onUp: () -> Unit,
    onDoubleTap: () -> Unit,
    /** Bumped on every recenter (double tap or the Recenter button): the pad shows a ring in its middle. */
    recenterCount: Int = 0,
) {
    var active by remember { mutableStateOf(false) }
    val trail = remember { mutableStateListOf<Offset>() }
    val glow by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "pad")
    val trailAlpha by animateFloatAsState(if (active) 1f else 0f, tween(if (active) 80 else 450), label = "trail")
    val ring = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(recenterCount) {
        if (recenterCount > 0) { ring.snapTo(1f); ring.animateTo(0f, tween(700)) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.radialGradient(listOf(Color(0xFF1E3440), Color(0xFF1A2229))))
            .border(2.dp, if (active) PadAccent else PadIdleBorder, RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                var lastTapAt = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val t0 = SystemClock.uptimeMillis()
                    active = true; onActive(true); onDown()
                    trail.clear(); trail.add(down.position)
                    var last = down.position
                    var travelled = 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val d = ch.position - last
                        last = ch.position
                        if (d != Offset.Zero) {
                            onMove(d.x / density, d.y / density)
                            travelled += d.getDistance() / density
                            trail.add(ch.position); if (trail.size > 28) trail.removeAt(0)
                        }
                        ch.consume()
                    }
                    active = false; onActive(false); onUp()
                    val now = SystemClock.uptimeMillis()
                    if (now - t0 < Trackpad.TAP_MS && travelled < Trackpad.TAP_SLOP_DP) {
                        if (now - lastTapAt < Trackpad.DOUBLE_TAP_MS) { lastTapAt = 0L; onDoubleTap() } else lastTapAt = now
                    } else lastTapAt = 0L
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Dot grid, brighter while touching.
            val step = 22.dp.toPx()
            val dot = Color(0xFF3A4A56).copy(alpha = 0.7f + 0.3f * glow)
            var y = step / 2f
            while (y < size.height) {
                var x = step / 2f
                while (x < size.width) { drawCircle(dot, 1.3.dp.toPx(), Offset(x, y)); x += step }
                y += step
            }
            if (glow > 0f) drawRect(PadAccent.copy(alpha = 0.10f * glow))
            // Recentered: a ring shrinking into the middle.
            if (ring.value > 0f) {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(PadAccent.copy(alpha = 0.8f * ring.value), (14 + 40 * ring.value).dp.toPx(), c, style = Stroke(3.dp.toPx()))
                drawCircle(Color(0xFF7FD6F5).copy(alpha = ring.value), 5.dp.toPx(), c)
            }
            // Faint trail behind the finger.
            if (trail.size > 1 && trailAlpha > 0f) {
                val path = Path().apply { moveTo(trail[0].x, trail[0].y); trail.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, PadAccent.copy(alpha = 0.16f * trailAlpha), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, PadAccent.copy(alpha = 0.5f * trailAlpha), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                val tip = trail.last()
                drawCircle(PadAccent.copy(alpha = 0.25f * trailAlpha), 22.dp.toPx(), tip)
                drawCircle(Color(0xFF7FD6F5).copy(alpha = 0.8f * trailAlpha), 13.dp.toPx(), tip)
            }
        }
        Column(Modifier.align(Alignment.Center).alpha(1f - glow), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("☝", fontSize = 26.sp)
            Text("Slide to aim", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7FB6CC))
            Text("Double tap · recenter", fontSize = 11.sp, color = Color(0xFF5F8A9C), modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** Small segmented control (Map / Trackpad, Slow / Normal / Fast). */
@Composable
fun <T> SegmentedPill(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.clip(shape).background(Color(0xFF15181C)).border(2.dp, Color(0xFF3A424C), shape).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) PadAccent else Color.Transparent)
                    .clickable { onSelect(value) }.padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (on) Color(0xFF0A2533) else Color(0xFFA3ADB8), textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Touch buttons beside the trackpad (presets with [io.github.hiramrg.delphis.core.TOUCH_BUTTONS_OPTION]).
 * Each is held while a finger is on it, so it works together with a finger on the pad.
 */
@Composable
fun TouchButtonColumn(
    buttons: List<io.github.hiramrg.delphis.core.TouchButton>,
    modifier: Modifier,
    onPress: (index: Int, pressed: Boolean) -> Unit,
) {
    DisposableEffect(buttons) { onDispose { buttons.indices.forEach { onPress(it, false) } } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        buttons.forEachIndexed { i, b ->
            TouchMotionButton(b, Modifier.weight(1f).fillMaxWidth()) { onPress(i, it) }
        }
    }
}

@Composable
private fun TouchMotionButton(b: io.github.hiramrg.delphis.core.TouchButton, modifier: Modifier, onPress: (Boolean) -> Unit) {
    var down by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (down) PadAccent.copy(alpha = 0.28f) else Color(0xFF1D2329))
            .border(2.dp, if (down) PadAccent else Color(0xFF3A424C), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    down = true; onPress(true)
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == first.id } ?: break
                            ch.consume()
                            if (!ch.pressed) break
                        }
                    } finally {
                        down = false; onPress(false)
                    }
                }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(b.icon, fontSize = 15.sp, color = Color(0xFF7FD6F5))
        Text(b.label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE8EDF2), maxLines = 1)
        Text(b.sub, fontSize = 9.sp, color = Color(0xFF8E9AA6), maxLines = 1, textAlign = TextAlign.Center)
    }
}
