package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.core.ThorControl
import io.github.hiramrg.delphis.core.ThorControl.*
import io.github.hiramrg.delphis.core.splitAction

/** One line of the legend: the control(s) and what they do. */
private class Entry(val controls: List<ThorControl>, val action: String, val wiiInput: String?)

/**
 * The controls view. Labels are placed where the controls physically are on the Thor: the bottom
 * screen sits between the two halves of the controls, so the left column lines up with the left
 * shoulder buttons, left stick and D-pad, and the right column with R1/R2, the A/B/X/Y buttons and
 * the right stick. The middle shows a small drawing of the Thor with the used controls lit up.
 */
@Composable
fun ControlsLegend(preset: Preset, modifier: Modifier = Modifier) {
    val byControl = remember(preset) {
        val m = linkedMapOf<ThorControl, String>()
        preset.mappings.forEach { m.putIfAbsent(it.control, it.action) }
        m
    }
    fun entries(vararg controls: ThorControl): List<Entry> =
        controls.filter { it in byControl }
            .groupBy { byControl.getValue(it) }
            .map { (action, cs) -> splitAction(action).let { (main, wii) -> Entry(cs, main, wii) } }

    // Listed top to bottom in the order the controls sit on the Thor; controls on the same side
    // that do the same thing share one row.
    val left = entries(L2, L1, LEFT_STICK, L3, DPAD)
    val right = entries(R2, R1, FACE_NORTH, FACE_WEST, FACE_EAST, FACE_SOUTH, RIGHT_STICK, R3)
    val center = entries(SELECT, START)
    val motion = byControl[MOTION]?.let { splitAction(it).first }

    BoxWithConstraints(modifier) {
        val wide = maxWidth > maxHeight * 1.05f
        val sideRows = maxOf(left.size, right.size, 1)
        val h = if (wide) maxHeight else maxHeight * 0.75f
        // A row is roughly 3.4 text-heights tall (two lines of action + the small "Wii:" line).
        val textSize = (h.value / sideRows / 3.6f).coerceIn(10f, 13f).sp

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Side(left, textSize, alignEnd = true, modifier = Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(0.75f).fillMaxHeight().padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    center.forEach { EntryRow(it, textSize * 0.9f, centered = true) }
                    ThorDiagram(preset, Modifier.weight(1f).fillMaxWidth(), compact = true)
                    if (motion != null) MotionLine(motion, textSize)
                }
                Side(right, textSize, alignEnd = false, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().weight(0.25f), verticalAlignment = Alignment.CenterVertically) {
                    ThorDiagram(preset, Modifier.weight(1f).fillMaxHeight(), compact = true)
                    Column(Modifier.weight(1f)) {
                        center.forEach { EntryRow(it, textSize * 0.9f) }
                        if (motion != null) MotionLine(motion, textSize)
                    }
                }
                Row(Modifier.fillMaxWidth().weight(0.75f)) {
                    Side(left, textSize, alignEnd = true, modifier = Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(8.dp))
                    Side(right, textSize, alignEnd = false, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun MotionLine(action: String, textSize: TextUnit) {
    Text(
        "⟲ Tilt the Thor: $action", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
        fontSize = textSize * 0.85f, textAlign = TextAlign.Center, maxLines = 2,
    )
}

/** One side of the Thor, top to bottom: shoulders, then sticks/face buttons, then D-pad/right stick. */
@Composable
private fun Side(entries: List<Entry>, textSize: TextUnit, alignEnd: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.SpaceEvenly) {
        entries.forEach { EntryRow(it, textSize, mirrored = alignEnd) }
    }
}

/**
 * "[badge] ( icon  What it does )". The text sits in a rounded chip outlined in the control's
 * colour. On the left half the badge is on the right, next to the physical control just past the
 * screen edge; on the right half it's on the left for the same reason.
 */
@Composable
private fun EntryRow(e: Entry, textSize: TextUnit, mirrored: Boolean = false, centered: Boolean = false) {
    val badgeSize = (textSize.value * 1.8f).dp
    val color = controlColor(e.controls.first())
    val icon = if (e.controls.any { it in faceColorsSet }) null else actionIcon(e.action)
    val badges: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { e.controls.take(4).forEach { ControlBadge(it, badgeSize) } }
    }
    val chipShape = RoundedCornerShape(12.dp)
    val chip: @Composable (Modifier) -> Unit = { m ->
        Row(
            m.border(1.5.dp, color.copy(alpha = 0.85f), chipShape).clip(chipShape).background(color.copy(alpha = 0.10f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (mirrored) Arrangement.End else Arrangement.Start,
        ) {
            if (icon != null) {
                androidx.compose.material3.Icon(icon, null, tint = color, modifier = Modifier.size((textSize.value * 1.3f).dp))
                Spacer(Modifier.width(6.dp))
            }
            Column(horizontalAlignment = if (mirrored) Alignment.End else Alignment.Start, modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    e.action, fontSize = textSize, fontWeight = FontWeight.SemiBold, lineHeight = textSize * 1.1f, color = LocalWii.current.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = if (mirrored) TextAlign.End else TextAlign.Start,
                )
                if (e.wiiInput != null) Text("Wii: ${e.wiiInput}", fontSize = textSize * 0.7f, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else if (mirrored) Arrangement.End else Arrangement.Start,
    ) {
        if (mirrored) {
            chip(Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(6.dp))
            badges()
        } else {
            badges()
            Spacer(Modifier.width(6.dp))
            chip(if (centered) Modifier else Modifier.weight(1f, fill = false))
        }
    }
}

private val faceColorsSet = setOf(FACE_EAST, FACE_SOUTH, FACE_NORTH, FACE_WEST)
private val BadgeFill = Color(0xFF0F1B30)

/** A small icon that looks like the control itself. */
@Composable
fun ControlBadge(control: ThorControl, size: Dp) {
    val face = if (control in faceColorsSet) controlColor(control) else null
    val BadgeBlue = controlColor(control)
    when {
        // Solid coloured circles like the mockup's face buttons.
        face != null -> Box(
            Modifier.size(size).clip(CircleShape).background(face).border(1.5.dp, face.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(control.shortLabel, color = Color(0xFF0B0F18), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.5f).sp) }

        control == LEFT_STICK || control == RIGHT_STICK || control == L3 || control == R3 -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            val click = control == L3 || control == R3
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2
                drawCircle(BadgeFill, r)
                drawCircle(BadgeBlue, r - 1.5f, style = Stroke(3f))
                drawCircle(BadgeBlue, r * 0.5f, style = if (click) androidx.compose.ui.graphics.drawscope.Fill else Stroke(3f))
            }
            Text(if (control == LEFT_STICK || control == L3) "L" else "R", color = if (control == L3 || control == R3) Color.Black else Color.White,
                fontWeight = FontWeight.Bold, fontSize = (size.value * 0.32f).sp)
        }

        control == DPAD -> Canvas(Modifier.size(size)) {
            val w = this.size.width; val t = w * 0.32f
            val corner = CornerRadius(t * 0.25f)
            drawRoundRect(BadgeFill, Offset((w - t) / 2, 0f), Size(t, w), corner)
            drawRoundRect(BadgeFill, Offset(0f, (w - t) / 2), Size(w, t), corner)
            drawRoundRect(BadgeBlue, Offset((w - t) / 2, 0f), Size(t, w), corner, style = Stroke(3f))
            drawRoundRect(BadgeBlue, Offset(0f, (w - t) / 2), Size(w, t), corner, style = Stroke(3f))
        }

        else -> {
            val label = when (control) { START -> "Start"; SELECT -> "Select"; MOTION -> "Tilt"; else -> control.shortLabel }
            Box(
                Modifier.height(size).widthIn(min = size * 1.2f).clip(RoundedCornerShape(size / 3))
                    .background(BadgeFill).border(1.5.dp, BadgeBlue, RoundedCornerShape(size / 3)).padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp, maxLines = 1) }
        }
    }
}
