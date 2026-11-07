package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.core.ThorControl
import io.github.hiramrg.delphis.core.ThorControl.*
import io.github.hiramrg.delphis.core.splitAction

/**
 * The game page's controls card: the preset's picture and name, then a map of the Thor's controls
 * with what each one does in this game. Laid out on a fixed 460×214 design grid that's scaled to fit.
 */
@Composable
fun ControlsMapCard(preset: Preset, suggested: Boolean, modifier: Modifier = Modifier) {
    val c = LocalWii.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(84.dp, 56.dp).clip(RoundedCornerShape(12.dp)).background(if (c.dark) c.sidebar else c.bg),
                contentAlignment = Alignment.Center,
            ) { PresetIcon(preset, Modifier.size(64.dp, 48.dp)) }
            Column(Modifier.weight(1f)) {
                SelectedControlsTitle(10.sp)
                Text(presetTitle(preset.name, suggested), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = if (preset.controller.label != preset.name) preset.controller.label else preset.summary
                Text(sub, fontSize = 11.sp, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ControlsMap(preset, Modifier.weight(1f).fillMaxWidth())
    }
}

/** "✓ SELECTED CONTROLS": marks the preset shown as the one that's chosen for the game. */
@Composable
fun SelectedControlsTitle(size: androidx.compose.ui.unit.TextUnit = 11.sp) {
    val c = LocalWii.current
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = c.green)); append("✓ "); pop()
            append("SELECTED CONTROLS")
        },
        fontSize = size, fontWeight = FontWeight.ExtraBold, color = c.textDim, letterSpacing = 1.sp,
    )
}

/** "+ (Pause)" → "Pause"; anything else as written. */
private fun mapLabel(action: String): String {
    val (name, inner) = splitAction(action)
    return if (name.trim().length <= 2 && inner != null) inner else action.trim()
}

private const val VW = 460f
private const val VH = 214f
private val ChipBlue = Color(0xFF5C8DF6)
private val White = Color.White

@Composable
fun ControlsMap(preset: Preset, modifier: Modifier = Modifier) {
    val c = LocalWii.current
    val measurer = rememberTextMeasurer()
    val actions = preset.mappings.groupBy { it.control }.mapValues { (_, ms) -> mapLabel(ms.first().action) }
    Canvas(modifier) { drawMap(actions, measurer, c) }
}

private fun DrawScope.drawMap(actions: Map<ThorControl, String>, tm: TextMeasurer, c: WiiColors) {
    val s = minOf(size.width / VW, size.height / VH)
    val ox = (size.width - VW * s) / 2f
    val oy = (size.height - VH * s) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    val unused = if (c.dark) Color(0xFF3A4250) else Color(0xFFD5DBE2)
    val deviceFill = if (c.dark) c.sidebar else Color(0xFFF7F9FA)
    val screenFill = if (c.dark) c.bar else Color(0xFFE6EAEE)
    val line = c.textDim.copy(alpha = 0.6f)

    /** Draws [text] with its baseline-ish top at y, aligned by [align] (-1 left, 0 centre, 1 right). */
    fun text(t: String, x: Float, y: Float, size: Float, color: Color, align: Int = -1, bold: Boolean = true, maxW: Float = VW) {
        val layout = tm.measure(
            t, TextStyle(fontSize = (size * s).toSp(), fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, color = color),
            overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = (maxW * s).toInt().coerceAtLeast(1)),
        )
        val w = layout.size.width / s
        val left = when (align) { -1 -> x; 0 -> x - w / 2f; else -> x - w }
        drawText(layout, topLeft = p(left, y - size * 0.95f))
    }
    /**
     * Lays out an action label in up to [maxLines] lines no wider than [maxW]. Long labels wrap onto a
     * second line first; only if they still don't fit does the font shrink (down to [minSize]), and
     * only after that is anything cut. Labels are never shortened otherwise, so "(A)", "(shake)" etc. stay.
     */
    fun fitted(t: String, size: Float, maxW: Float, color: Color, align: Int, maxLines: Int = 2, minSize: Float = 9f): androidx.compose.ui.text.TextLayoutResult {
        var sz = size
        while (true) {
            val l = tm.measure(
                t,
                TextStyle(
                    fontSize = (sz * s).toSp(), fontWeight = FontWeight.Bold, color = color, lineHeight = (sz * 1.15f * s).toSp(),
                    textAlign = when (align) { -1 -> androidx.compose.ui.text.style.TextAlign.Start; 0 -> androidx.compose.ui.text.style.TextAlign.Center; else -> androidx.compose.ui.text.style.TextAlign.End },
                ),
                overflow = TextOverflow.Ellipsis, maxLines = maxLines, constraints = Constraints(maxWidth = (maxW * s).toInt().coerceAtLeast(1)),
            )
            if (!l.hasVisualOverflow || sz <= minSize) return l
            sz -= 0.5f
        }
    }
    /** Draws a [fitted] block whose first line sits where [text] would put a single line; returns its height. */
    fun block(l: androidx.compose.ui.text.TextLayoutResult, x: Float, y: Float, size: Float, align: Int): Float {
        val w = l.size.width / s
        val left = when (align) { -1 -> x; 0 -> x - w / 2f; else -> x - w }
        drawText(l, topLeft = p(left, y - size * 0.95f))
        return l.size.height / s
    }
    fun width(t: String, size: Float) = tm.measure(t, TextStyle(fontSize = (size * s).toSp(), fontWeight = FontWeight.ExtraBold)).size.width / s
    fun chip(label: String, x: Float, yMid: Float, color: Color, h: Float = 16f): Float {
        val w = maxOf(h + 6f, width(label, 9f) + 12f)
        drawRoundRect(color, p(x, yMid - h / 2f), Size(w * s, h * s), CornerRadius(h / 2f * s))
        text(label, x + w / 2f, yMid + 4f, 9f, White, align = 0)
        return w
    }
    fun used(k: ThorControl) = actions[k] != null
    fun leader(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(line, p(x1, y1), p(x2, y2), strokeWidth = 1.2f * s)

    // Device body, bottom screen.
    val dx0 = 138f; val dx1 = 322f; val dy0 = 52f; val dy1 = 176f
    drawRoundRect(deviceFill, p(dx0, dy0), Size((dx1 - dx0) * s, (dy1 - dy0) * s), CornerRadius(22f * s))
    drawRoundRect(c.cardBorder, p(dx0, dy0), Size((dx1 - dx0) * s, (dy1 - dy0) * s), CornerRadius(22f * s), style = Stroke(2.5f * s))
    drawRoundRect(screenFill, p(dx0 + 58f, dy0 + 22f), Size(68f * s, 78f * s), CornerRadius(6f * s))
    drawRoundRect(c.cardBorder, p(dx0 + 58f, dy0 + 22f), Size(68f * s, 78f * s), CornerRadius(6f * s), style = Stroke(1.5f * s))
    // Select and Start sit above the bottom screen, Select on the left and Start on the right (as on the Thor).
    listOf(SELECT to 190f, START to 254f).forEach { (k, x) ->
        drawRoundRect(if (used(k)) ChipBlue else unused, p(x, 57f), Size(16f * s, 5f * s), CornerRadius(2.5f * s))
        text(if (k == SELECT) "SELECT" else "START", x + 8f, 70f, 5.5f, c.textDim, align = 0)
    }

    // Shoulder tabs, labelled.
    listOf(L2 to (148f to 34f), L1 to (186f to 28f), R1 to (246f to 28f), R2 to (278f to 34f)).forEach { (k, xw) ->
        val (x, w) = xw
        drawRoundRect(if (used(k)) ChipBlue else unused, p(x, 38f), Size(w * s, 14f * s), CornerRadius(7f * s))
        text(k.shortLabel, x + w / 2f, 48.5f, 9f, White, align = 0)
    }
    // Shoulder rows: chip + action, left and right.
    val half = VW / 2f - 40f
    listOf(L2 to 14f, L1 to 32f).forEach { (k, y) ->
        chip(k.shortLabel, 0f, y - 4f, if (used(k)) ChipBlue else unused)
        block(fitted(actions[k] ?: "—", 12f, half, if (used(k)) c.text else c.textDim, -1, maxLines = 1), 28f, y, 12f, -1)
    }
    listOf(R2 to 14f, R1 to 32f).forEach { (k, y) ->
        chip(k.shortLabel, VW - 22f, y - 4f, if (used(k)) ChipBlue else unused)
        block(fitted(actions[k] ?: "—", 12f, half, if (used(k)) c.text else c.textDim, 1, maxLines = 1), VW - 28f, y, 12f, 1)
    }

    val sideW = dx0 - 14f
    // Left stick.
    val ls = Offset(dx0 + 29f, dy0 + 32f)
    drawCircle(unused, 14f * s, p(ls.x, ls.y)); drawCircle(if (used(LEFT_STICK)) ChipBlue else unused, 14f * s, p(ls.x, ls.y), style = Stroke(2.5f * s))
    leader(ls.x - 14f, ls.y, dx0 - 8f, ls.y)
    text("Left stick", dx0 - 12f, ls.y - 4f, 10f, c.textDim, align = 1, bold = false, maxW = sideW)
    block(fitted(actions[LEFT_STICK] ?: "—", 12f, sideW, c.text, 1), dx0 - 12f, ls.y + 10f, 12f, 1)
    // D-pad.
    val dp = Offset(dx0 + 29f, dy0 + 86f)
    val dpc = if (used(DPAD)) ChipBlue else unused
    drawRoundRect(dpc, p(dp.x - 6f, dp.y - 17f), Size(12f * s, 34f * s), CornerRadius(3f * s))
    drawRoundRect(dpc, p(dp.x - 17f, dp.y - 6f), Size(34f * s, 12f * s), CornerRadius(3f * s))
    leader(dp.x - 17f, dp.y, dx0 - 8f, dp.y)
    text("D-Pad", dx0 - 12f, dp.y - 4f, 10f, c.textDim, align = 1, bold = false, maxW = sideW)
    block(fitted(actions[DPAD] ?: "—", 12f, sideW, c.text, 1), dx0 - 12f, dp.y + 10f, 12f, 1)

    // Face buttons, then their actions grouped ("A B  Jump").
    val fc = Offset(dx1 - 29f, dy0 + 32f)
    val faces = listOf(FACE_NORTH to Offset(0f, -13f), FACE_EAST to Offset(13f, 0f), FACE_SOUTH to Offset(0f, 13f), FACE_WEST to Offset(-13f, 0f))
    faces.forEach { (k, o) ->
        val at = fc + o
        drawCircle(if (used(k)) controlColor(k) else unused, 7f * s, p(at.x, at.y))
        text(k.shortLabel, at.x, at.y + 3.2f, 8.5f, White, align = 0)
    }
    leader(fc.x + 20f, fc.y, dx1 + 8f, fc.y)
    val groups = linkedMapOf<String, MutableList<ThorControl>>()
    listOf(FACE_EAST, FACE_SOUTH, FACE_NORTH, FACE_WEST).forEach { k -> actions[k]?.let { groups.getOrPut(it) { mutableListOf() } += k } }
    val rightW = VW - dx1 - 14f
    // Lay every group out first (long ones wrap), then centre the whole block on the face buttons.
    val laid = groups.map { (act, keys) ->
        val textX = dx1 + 12f + keys.size * 15f + 4f
        Triple(keys, textX, fitted(act, 12f, VW - textX - 2f, c.text, -1))
    }
    val heights = laid.map { (_, _, l) -> maxOf(17f, l.size.height / s + 3f) }
    var gy = fc.y + 4f - (heights.sum() - 17f) / 2f
    laid.forEachIndexed { i, (keys, textX, l) ->
        var x = dx1 + 12f
        keys.forEach { k ->
            drawCircle(controlColor(k), 6.5f * s, p(x + 6f, gy - 4f))
            text(k.shortLabel, x + 6f, gy - 0.8f, 8.5f, White, align = 0)
            x += 15f
        }
        block(l, textX, gy, 12f, -1)
        gy += heights[i]
    }
    // Right stick (or the gyro, when the pointer is on the gyro instead).
    val rs = Offset(dx1 - 29f, dy0 + 86f)
    val rsAction = actions[RIGHT_STICK]
    drawCircle(unused, 14f * s, p(rs.x, rs.y)); drawCircle(if (rsAction != null) controlColor(RIGHT_STICK) else unused, 14f * s, p(rs.x, rs.y), style = Stroke(2.5f * s))
    leader(rs.x + 14f, rs.y, dx1 + 8f, rs.y)
    text("Right stick", dx1 + 12f, rs.y - 4f, 10f, c.textDim, bold = false, maxW = rightW)
    block(fitted(rsAction ?: "—", 12f, rightW, c.text, -1), dx1 + 12f, rs.y + 10f, 12f, -1)

    // System row: only what the preset uses.
    val sys = listOf(SELECT, START, L3, R3, GYRO, MOTION).mapNotNull { k -> actions[k]?.let { k to it } }
    if (sys.isNotEmpty()) {
        val gap = 16f
        val maxItem = if (sys.size <= 2) 170f else (VW - gap * (sys.size - 1)) / sys.size - 30f
        val parts = sys.map { (k, a) ->
            val name = when (k) { GYRO -> "Gyro"; MOTION -> "Tilt"; else -> k.shortLabel }
            Triple(name, fitted(a, 11f, maxItem, c.text, -1), if (k == GYRO || k == R3) controlColor(k) else ChipBlue)
        }
        // A wrapped item needs a second line, so the row moves up to keep it inside the map.
        val y = if (parts.any { it.second.lineCount > 1 }) VH - 21f else VH - 8f
        val widths = parts.map { (n, l, _) -> maxOf(22f, width(n, 9f) + 12f) + 5f + l.size.width / s }
        var x = (VW - (widths.sum() + gap * (parts.size - 1))) / 2f
        parts.forEachIndexed { i, (n, l, col) ->
            val w = chip(n, x, y - 4f, col)
            block(l, x + w + 5f, y, 11f, -1)
            x += widths[i] + gap
        }
    }
}
