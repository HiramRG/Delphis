package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.core.ThorControl
import io.github.hiramrg.delphis.core.ThorControl.*

/**
 * Draws the AYN Thor's bottom half (sticks, D-pad, face buttons, stacked shoulders, Start/Select
 * above the bottom screen) and labels every control with what it does in the current preset.
 *
 * [compact] drops the call-out labels and only highlights which controls are used (for previews).
 */
@Composable
fun ThorDiagram(
    preset: Preset,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    labelSize: TextUnit = 13.sp,
    /** Controls drawn in a strong highlight colour (used by the Controller Check). */
    highlight: Set<ThorControl> = emptySet(),
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val geo = ThorGeometry.fit(size, compact)
        drawDevice(geo, preset, measurer, compact, highlight)
        if (!compact) drawCallouts(geo, preset, measurer, labelSize)
    }
}

/** All coordinates of the drawn device, derived from the canvas size. */
private class ThorGeometry(val body: Rect, val canvas: Size, val leftColumn: ClosedFloatingPointRange<Float>, val rightColumn: ClosedFloatingPointRange<Float>) {
    val u get() = body.height
    fun at(fx: Float, fy: Float) = Offset(body.left + body.width * fx, body.top + body.height * fy)

    val leftStick get() = at(0.15f, 0.33f)
    val dpad get() = at(0.15f, 0.70f)
    val face get() = at(0.85f, 0.33f)
    val rightStick get() = at(0.85f, 0.70f)
    val stickR get() = u * 0.13f
    val faceOffset get() = u * 0.105f
    val faceR get() = u * 0.052f

    fun faceCenter(c: ThorControl) = when (c) {
        FACE_EAST -> face + Offset(faceOffset, 0f)
        FACE_WEST -> face - Offset(faceOffset, 0f)
        FACE_NORTH -> face - Offset(0f, faceOffset)
        else -> face + Offset(0f, faceOffset)
    }

    fun shoulder(c: ThorControl): Rect {
        val outer = c == L2 || c == R2
        val left = c == L1 || c == L2
        val x0 = if (outer) 0.05f else 0.025f
        val x1 = if (outer) 0.23f else 0.25f
        val y0 = if (outer) -0.15f else -0.075f
        val y1 = if (outer) -0.06f else 0.02f
        val a = if (left) at(x0, y0) else at(1f - x1, y0)
        val b = if (left) at(x1, y1) else at(1f - x0, y1)
        return Rect(a, b)
    }

    fun smallPill(c: ThorControl): Rect {
        val cx = if (c == SELECT) 0.42f else 0.58f
        val center = at(cx, 0.085f)
        return Rect(center - Offset(body.width * 0.045f, u * 0.022f), center + Offset(body.width * 0.045f, u * 0.022f))
    }

    val screen get() = Rect(at(0.30f, 0.17f), at(0.70f, 0.80f))

    /**
     * Point a call-out line should touch for [c]: the edge of the control facing the label column,
     * so the dot never covers the printed letter.
     */
    fun anchor(c: ThorControl, fromLeft: Boolean): Offset {
        val dir = if (fromLeft) -1f else 1f
        return when (c) {
            FACE_EAST, FACE_WEST, FACE_NORTH, FACE_SOUTH -> faceCenter(c) + Offset(dir * faceR, 0f)
            DPAD -> dpad + Offset(dir * u * 0.12f, 0f)
            LEFT_STICK -> leftStick + Offset(dir * stickR, 0f)
            RIGHT_STICK -> rightStick + Offset(dir * stickR, 0f)
            L3 -> leftStick + Offset(dir * stickR * 0.55f, 0f)
            R3 -> rightStick + Offset(dir * stickR * 0.55f, 0f)
            L1, L2, R1, R2, START, SELECT -> {
                val r = if (c == START || c == SELECT) smallPill(c) else shoulder(c)
                Offset(if (fromLeft) r.left else r.right, r.center.y)
            }
            MOTION, GYRO -> at(0.5f, 1f)
        }
    }

    companion object {
        fun fit(size: Size, compact: Boolean): ThorGeometry {
            val sideFraction = if (compact) 0f else 0.25f
            val maxW = size.width * (1f - 2 * sideFraction) * 0.96f
            // Leave room above for the stacked shoulders (0.15h) and below for the motion banner (0.2h).
            val maxH = size.height * if (compact) 0.80f else 0.72f
            val h = minOf(maxW / 1.6f, maxH)
            val w = h * 1.6f
            val left = (size.width - w) / 2f
            val top = if (compact) size.height * 0.16f else (size.height - h * 1.35f) / 2f + h * 0.15f
            val body = Rect(left, top, left + w, top + h)
            val colW = size.width * sideFraction
            return ThorGeometry(body, size, 0f..colW, (size.width - colW)..size.width)
        }
    }
}

private val Outline = Color(0xFF5A5F6B)
private val BodyFill = Color(0xFF15171C)
private val Accent = Color(0xFF4F9DFF)
private val AccentFill = Color(0x554F9DFF)
private val LabelBg = Color(0xFF1C2028)

private val faceLetterColors = mapOf(
    FACE_EAST to Color(0xFFFF6B6B), FACE_SOUTH to Color(0xFFFFD166),
    FACE_NORTH to Color(0xFF6BCBFF), FACE_WEST to Color(0xFF7BE07B),
)

private val Highlight = Color(0xFFFFD166)

private fun DrawScope.drawDevice(g: ThorGeometry, preset: Preset, m: TextMeasurer, compact: Boolean, highlight: Set<ThorControl>) {
    val used = preset.mappings.map { it.control }.toSet()
    val stroke = Stroke(width = (g.u * 0.012f).coerceAtLeast(1.5f))
    fun fillFor(c: ThorControl) = when (c) { in highlight -> Highlight.copy(alpha = 0.55f); in used -> AccentFill; else -> BodyFill }
    fun lineFor(c: ThorControl) = when (c) { in highlight -> Highlight; in used -> Accent; else -> Outline }

    // Shoulders sit "behind" the body, so draw them first.
    for (c in listOf(L2, R2, L1, R1)) {
        val r = g.shoulder(c)
        drawRoundRect(fillFor(c), r.topLeft, r.size, CornerRadius(g.u * 0.04f))
        drawRoundRect(lineFor(c), r.topLeft, r.size, CornerRadius(g.u * 0.04f), style = stroke)
        drawCenteredText(m, c.shortLabel, Offset(r.center.x, r.top + r.height * 0.45f), (g.u * 0.045f).toSp(), lineFor(c))
    }

    drawRoundRect(BodyFill, g.body.topLeft, g.body.size, CornerRadius(g.u * 0.14f))
    drawRoundRect(Outline, g.body.topLeft, g.body.size, CornerRadius(g.u * 0.14f), style = stroke)

    // The bottom screen itself (where this app lives).
    val s = g.screen
    drawRoundRect(Color.Black, s.topLeft, s.size, CornerRadius(g.u * 0.03f))
    drawRoundRect(Outline.copy(alpha = 0.6f), s.topLeft, s.size, CornerRadius(g.u * 0.03f), style = stroke)
    if (!compact) drawCenteredText(m, "this screen", s.center, (g.u * 0.045f).toSp(), Outline)

    // Start / Select above the screen, Back / Home below it.
    for (c in listOf(SELECT, START)) {
        val r = g.smallPill(c)
        drawRoundRect(fillFor(c), r.topLeft, r.size, CornerRadius(r.height / 2))
        drawRoundRect(lineFor(c), r.topLeft, r.size, CornerRadius(r.height / 2), style = stroke)
    }
    for (fx in listOf(0.45f, 0.55f)) drawCircle(Outline, g.u * 0.028f, g.at(fx, 0.90f), style = stroke)

    // Sticks (L3/R3 = click, so highlight if either the stick or its click is used).
    for ((stick, click, center) in listOf(Triple(LEFT_STICK, L3, g.leftStick), Triple(RIGHT_STICK, R3, g.rightStick))) {
        val hot = stick in highlight || click in highlight
        val on = stick in used || click in used
        drawCircle(if (hot) Highlight.copy(alpha = 0.55f) else if (on) AccentFill else BodyFill, g.stickR, center)
        drawCircle(if (hot) Highlight else if (on) Accent else Outline, g.stickR, center, style = stroke)
        drawCircle(if (click in highlight) Highlight else if (stick in used) Accent else Outline, g.stickR * 0.55f, center, style = stroke)
    }

    // D-pad cross.
    val arm = g.u * 0.12f
    val half = g.u * 0.04f
    val dc = g.dpad
    val dFill = fillFor(DPAD)
    val dLine = lineFor(DPAD)
    drawRoundRect(dFill, Offset(dc.x - half, dc.y - arm), Size(half * 2, arm * 2), CornerRadius(half * 0.4f))
    drawRoundRect(dFill, Offset(dc.x - arm, dc.y - half), Size(arm * 2, half * 2), CornerRadius(half * 0.4f))
    drawRoundRect(dLine, Offset(dc.x - half, dc.y - arm), Size(half * 2, arm * 2), CornerRadius(half * 0.4f), style = stroke)
    drawRoundRect(dLine, Offset(dc.x - arm, dc.y - half), Size(arm * 2, half * 2), CornerRadius(half * 0.4f), style = stroke)

    // Face buttons, labelled as printed on the Thor (Nintendo layout).
    for (c in listOf(FACE_NORTH, FACE_WEST, FACE_EAST, FACE_SOUTH)) {
        val center = g.faceCenter(c)
        drawCircle(fillFor(c), g.faceR, center)
        drawCircle(lineFor(c), g.faceR, center, style = stroke)
        drawCenteredText(m, c.shortLabel, center, (g.faceR * 0.95f).toSp(), faceLetterColors.getValue(c), bold = true)
    }

    if (MOTION in used) {
        val motion = preset.mappings.first { it.control == MOTION }
        val y = g.body.bottom + g.u * 0.13f
        drawCenteredText(m, "⟲  Tilt the Thor: ${motion.action}  ⟳", Offset(g.canvas.width / 2, y), (g.u * 0.065f).toSp(), Accent, bold = true)
    }
}

private val LEFT_SIDE = setOf(L1, L2, SELECT, LEFT_STICK, L3, DPAD)

/** Controls on the same side that share an action get one label with several leader lines. */
private class CalloutGroup(val controls: List<ThorControl>, val action: String)

private fun DrawScope.drawCallouts(g: ThorGeometry, preset: Preset, m: TextMeasurer, labelSize: TextUnit) {
    val firstAction = linkedMapOf<ThorControl, String>()
    preset.mappings.forEach { if (it.control != MOTION) firstAction.putIfAbsent(it.control, it.action) }

    fun column(onLeft: Boolean, range: ClosedFloatingPointRange<Float>) {
        val groups = firstAction.entries
            .filter { (it.key in LEFT_SIDE) == onLeft }
            .groupBy({ it.value }, { it.key })
            .map { (action, controls) -> CalloutGroup(controls, action) }
            // Order labels top-to-bottom by where their controls sit, so leader lines don't cross.
            .sortedBy { grp -> grp.controls.minOf { g.anchor(it, onLeft).y } }
        if (groups.isEmpty()) return
        val top = g.canvas.height * 0.03f
        val bottom = g.canvas.height * 0.97f
        val slot = (bottom - top) / groups.size
        val pad = g.canvas.width * 0.008f
        val colWidth = (range.endInclusive - range.start) - pad * 2
        groups.forEachIndexed { i, grp ->
            val single = grp.controls.singleOrNull()
            val badge = grp.controls.joinToString(" / ") { it.shortLabel }
            val badgeStyle = TextStyle(fontSize = labelSize * 0.82f, fontWeight = FontWeight.Bold, color = single?.let { faceLetterColors[it] } ?: Accent)
            val actionStyle = TextStyle(fontSize = labelSize, color = Color.White)
            val badgeLayout = m.measure(badge, badgeStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = (colWidth - pad * 3).toInt().coerceAtLeast(1)))
            val actionLayout = m.measure(
                grp.action, actionStyle, overflow = TextOverflow.Ellipsis, maxLines = 2,
                constraints = Constraints(maxWidth = (colWidth - pad * 3).toInt().coerceAtLeast(1)),
            )
            val chipH = minOf(slot * 0.92f, badgeLayout.size.height + actionLayout.size.height + pad * 2.5f)
            val chipTop = top + slot * i + (slot - chipH) / 2
            val chipLeft = range.start + pad
            drawRoundRect(LabelBg, Offset(chipLeft, chipTop), Size(colWidth, chipH), CornerRadius(pad * 1.5f))
            drawText(badgeLayout, topLeft = Offset(chipLeft + pad * 1.5f, chipTop + pad))
            drawText(actionLayout, topLeft = Offset(chipLeft + pad * 1.5f, chipTop + pad + badgeLayout.size.height))

            val from = Offset(if (onLeft) chipLeft + colWidth else chipLeft, chipTop + chipH / 2)
            for (control in grp.controls) {
                val to = g.anchor(control, onLeft)
                drawLine(Accent.copy(alpha = 0.5f), from, to, strokeWidth = (g.u * 0.006f).coerceAtLeast(1f))
                drawCircle(Accent, (g.u * 0.013f).coerceAtLeast(2f), to)
            }
        }
    }
    column(onLeft = true, g.leftColumn)
    column(onLeft = false, g.rightColumn)
}

private fun DrawScope.drawCenteredText(m: TextMeasurer, text: String, center: Offset, size: TextUnit, color: Color, bold: Boolean = false) {
    val layout = m.measure(text, TextStyle(fontSize = size, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}
