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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
 * with what each one does in this game. The Thor is drawn at its real shape (wide base, nearly square
 * bottom screen); the whole map (drawing + labels) is centred, so a side with longer labels pushes the
 * drawing the other way.
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

private val ChipBlue = Color(0xFF5C8DF6)
private val White = Color.White

@Composable
fun ControlsMap(preset: Preset, modifier: Modifier = Modifier) {
    val c = LocalWii.current
    val measurer = rememberTextMeasurer()
    val actions = preset.mappings.groupBy { it.control }.mapValues { (_, ms) -> mapLabel(ms.first().action) }
    Canvas(modifier) { drawMap(actions, measurer, c) }
}

/**
 * Thor base design: 300×150 units. Bottom screen (90,14)-(210,118); Select/Start dots beside its top
 * corners; left stick (44,50), D-pad (44,108), face buttons around (256,50), right stick (256,108);
 * shoulder tabs L2/L1/R1/R2 on the top edge.
 */
private object Thor {
    const val W = 300f; const val H = 150f; const val TABS = 14f
    const val SCREEN_X = 90f; const val SCREEN_Y = 14f; const val SCREEN_W = 120f; const val SCREEN_H = 104f
    const val INNER_TAB_EDGE = 108f // inner edge of the L1 tab (R1's is at W - 108)
}

private fun DrawScope.drawMap(actions: Map<ThorControl, String>, tm: TextMeasurer, c: WiiColors) {
    val s = density // 1 unit = 1 dp
    val W = size.width / s
    val H = size.height / s
    val wide = W >= 520f
    val fs = if (wide) 12f else 10.5f
    val small = fs * 0.8f
    val unused = if (c.dark) Color(0xFF3A4250) else Color(0xFFD5DBE2)
    val deviceFill = if (c.dark) c.sidebar else Color(0xFFF7F9FA)
    val screenFill = if (c.dark) c.bar else Color(0xFFE6EAEE)
    val line = c.textDim.copy(alpha = 0.6f)
    fun used(k: ThorControl) = actions[k] != null

    fun align(a: Int) = when (a) { -1 -> TextAlign.Start; 0 -> TextAlign.Center; else -> TextAlign.End }
    /**
     * An action label in up to [maxLines] lines no wider than [maxW]. Long labels wrap first, then the
     * font shrinks (down to [minSize]); if it still doesn't fit it gets more lines rather than being cut,
     * so the Wii-button hints in brackets ("(A)", "(shake)", "(2)") are always shown.
     */
    fun fitted(t: String, size: Float, maxW: Float, color: Color, a: Int, maxLines: Int = 2, minSize: Float = 8.5f, bold: Boolean = true): TextLayoutResult {
        var sz = size
        val cons = Constraints(maxWidth = (maxW * s).toInt().coerceAtLeast(1))
        fun style(z: Float) = TextStyle(fontSize = (z * s).toSp(), fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, color = color, lineHeight = (z * 1.18f * s).toSp(), textAlign = align(a))
        while (true) {
            val l = tm.measure(t, style(sz), overflow = TextOverflow.Clip, maxLines = maxLines, constraints = cons)
            if (!l.hasVisualOverflow) return l
            if (sz <= minSize) return tm.measure(t, style(sz), overflow = TextOverflow.Visible, maxLines = maxLines + 2, constraints = cons)
            sz -= 0.5f
        }
    }
    fun w(l: TextLayoutResult) = l.size.width / s
    fun h(l: TextLayoutResult) = l.size.height / s
    fun textWidth(t: String, size: Float) = tm.measure(t, TextStyle(fontSize = (size * s).toSp(), fontWeight = FontWeight.ExtraBold)).size.width / s
    fun chipWidth(label: String, size: Float) = maxOf(size + 10f, textWidth(label, size) + 12f)

    // ---- sizes
    val sys = listOf(L3, R3, GYRO, MOTION).mapNotNull { k -> actions[k]?.let { k to it } }
    val gap = 10f
    var k = W * (if (wide) 0.46f else 0.52f) / Thor.W
    val colW0 = { kk: Float -> (W - Thor.W * kk) / 2f - gap - 4f }

    // Shoulder labels (stacked above their tabs: L1/R1 nearest the Thor, L2/R2 above).
    val shoulderMax = W / 2f - 8f
    data class Sh(val key: ThorControl, val chipW: Float, val l: TextLayoutResult)
    fun shoulder(key: ThorControl, a: Int): Sh {
        val cw = chipWidth(key.shortLabel, fs - 2f)
        return Sh(key, cw, fitted(actions[key] ?: "—", fs, shoulderMax - cw - 6f - 30f, if (used(key)) c.text else c.textDim, a, maxLines = 1))
    }
    val shL = listOf(shoulder(L1, 1), shoulder(L2, 1))
    val shR = listOf(shoulder(R1, -1), shoulder(R2, -1))
    val rowGap = 5f
    val bandH = maxOf(shL.sumOf { maxOf(h(it.l), fs + 6f).toDouble() }, shR.sumOf { maxOf(h(it.l), fs + 6f).toDouble() }).toFloat() + rowGap + 8f

    // System row (only what the preset uses besides Select / Start, which are drawn on the screen).
    val sysParts = sys.map { (key, a) ->
        val name = when (key) { GYRO -> "Gyro"; MOTION -> "Tilt"; else -> key.shortLabel }
        val maxItem = if (sys.size <= 2) W / 2f - 60f else (W - 16f * (sys.size - 1)) / sys.size - 30f
        Triple(name, fitted(a, fs - 1f, maxItem, c.text, -1), if (key == GYRO || key == R3) controlColor(key) else ChipBlue)
    }
    val sysH = if (sysParts.isEmpty()) 0f else sysParts.maxOf { maxOf(h(it.second), fs + 6f) } + 12f

    // The Thor shrinks if the card is short.
    k = minOf(k, (H - bandH - sysH - 6f) / (Thor.H + Thor.TABS)).coerceAtLeast(0.3f)
    val tw = Thor.W * k; val th = Thor.H * k; val tabs = Thor.TABS * k
    val colW = colW0(k)

    // Side labels.
    val lsL = fitted(actions[LEFT_STICK] ?: "—", fs, colW, if (used(LEFT_STICK)) c.text else c.textDim, 1)
    val dpL = fitted(actions[DPAD] ?: "—", fs, colW, if (used(DPAD)) c.text else c.textDim, 1)
    val rsL = fitted(actions[RIGHT_STICK] ?: "—", fs, colW, if (used(RIGHT_STICK)) c.text else c.textDim, -1)
    val dotR = fs * 0.55f
    val groups = linkedMapOf<String, MutableList<ThorControl>>()
    listOf(FACE_EAST, FACE_SOUTH, FACE_NORTH, FACE_WEST).forEach { key -> actions[key]?.let { groups.getOrPut(it) { mutableListOf() } += key } }
    val faceRows = groups.map { (act, keys) ->
        val dotsW = keys.size * (dotR * 2f + 3f) + 4f
        Triple(keys, dotsW, fitted(act, fs, colW - dotsW, c.text, -1))
    }

    // ---- balance: centre the whole map (labels included), not just the drawing
    fun shoulderOut(sh: Sh) = maxOf(0f, sh.chipW + 6f + w(sh.l) - Thor.INNER_TAB_EDGE * k)
    val leftExt = maxOf(
        gap + maxOf(w(lsL), w(dpL), textWidth("Left stick", small), textWidth("D-Pad", small)),
        shL.maxOf { shoulderOut(it) },
    )
    val rightExt = maxOf(
        gap + maxOf(w(rsL), textWidth("Right stick", small), faceRows.maxOfOrNull { it.second + w(it.third) } ?: 0f),
        shR.maxOf { shoulderOut(it) },
    )
    val ox = ((W - (leftExt + tw + rightExt)) / 2f + leftExt).coerceIn(minOf(leftExt, (W - tw) / 2f), maxOf(W - rightExt - tw, (W - tw) / 2f))
    val oy = (H - (bandH + tabs + th + sysH)) / 2f + bandH + tabs

    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    fun X(u: Float) = ox + u * k
    fun Y(u: Float) = oy + u * k
    fun draw(l: TextLayoutResult, x: Float, top: Float, a: Int) = drawText(l, topLeft = p(when (a) { -1 -> x; 0 -> x - w(l) / 2f; else -> x - w(l) }, top))
    fun leader(x1: Float, y: Float, x2: Float) = drawLine(line, p(x1, y), p(x2, y), strokeWidth = 1.2f * s)
    fun label(t: String, x: Float, top: Float, size: Float, color: Color, a: Int, weight: FontWeight = FontWeight.ExtraBold) {
        val l = tm.measure(t, TextStyle(fontSize = (size * s).toSp(), fontWeight = weight, color = color))
        draw(l, x, top, a)
    }
    fun chip(text: String, x: Float, yMid: Float, color: Color, size: Float, a: Int): Float {
        val cw = chipWidth(text, size); val ch = size + 6f
        val left = if (a == 1) x - cw else x
        drawRoundRect(color, p(left, yMid - ch / 2f), Size(cw * s, ch * s), CornerRadius(ch / 2f * s))
        val l = tm.measure(text, TextStyle(fontSize = (size * s).toSp(), fontWeight = FontWeight.ExtraBold, color = White))
        draw(l, left + cw / 2f, yMid - h(l) / 2f, 0)
        return cw
    }
    /** Small grey title over the action, the pair centred on [yMid]. */
    fun titled(title: String, l: TextLayoutResult, x: Float, yMid: Float, a: Int) {
        val tl = tm.measure(title, TextStyle(fontSize = (small * s).toSp(), fontWeight = FontWeight.SemiBold, color = c.textDim))
        val total = h(tl) + h(l)
        val top = yMid - total / 2f + 2f
        draw(tl, x, top, a); draw(l, x, top + h(tl), a)
    }

    // ---- the Thor
    listOf(L2 to 14f, L1 to 62f, R1 to 192f, R2 to 240f).forEach { (key, x) ->
        drawRoundRect(if (used(key)) ChipBlue else unused, p(X(x), oy - tabs), Size(46f * k * s, (tabs + 4f * k) * s), CornerRadius(7f * k * s))
        val l = tm.measure(key.shortLabel, TextStyle(fontSize = (maxOf(7f, 9f * k) * s).toSp(), fontWeight = FontWeight.ExtraBold, color = White))
        draw(l, X(x + 23f), oy - tabs + (tabs - h(l)) / 2f, 0)
    }
    drawRoundRect(deviceFill, p(ox, oy), Size(tw * s, th * s), CornerRadius(34f * k * s))
    drawRoundRect(c.cardBorder, p(ox, oy), Size(tw * s, th * s), CornerRadius(34f * k * s), style = Stroke(2.5f * s))
    drawRoundRect(screenFill, p(X(Thor.SCREEN_X), Y(Thor.SCREEN_Y)), Size(Thor.SCREEN_W * k * s, Thor.SCREEN_H * k * s), CornerRadius(7f * k * s))
    drawRoundRect(c.cardBorder, p(X(Thor.SCREEN_X), Y(Thor.SCREEN_Y)), Size(Thor.SCREEN_W * k * s, Thor.SCREEN_H * k * s), CornerRadius(7f * k * s), style = Stroke(1.5f * s))

    // Select / Start: dots beside the screen's top corners, labelled on the screen.
    val ssW = Thor.SCREEN_W * k / 2f - 10f
    listOf(Triple(SELECT, 76f, -1), Triple(START, 224f, 1)).forEach { (key, dx, a) ->
        drawCircle(if (used(key)) ChipBlue else unused, 5f * k * s, p(X(dx), Y(20f)))
        val tx = if (a == -1) X(Thor.SCREEN_X + 8f) else X(Thor.SCREEN_X + Thor.SCREEN_W - 8f)
        drawLine(line, p(X(dx) + (if (a == -1) 5f else -5f) * k, Y(20f) + 2f * k), p(tx + (if (a == -1) -2f else 2f), Y(30f)), strokeWidth = 1.2f * s)
        val tl = tm.measure(if (key == SELECT) "Select" else "Start", TextStyle(fontSize = (small * 0.95f * s).toSp(), fontWeight = FontWeight.SemiBold, color = c.textDim))
        draw(tl, tx, Y(28f), a)
        draw(fitted(actions[key] ?: "—", fs * 0.95f, ssW, if (used(key)) c.text else c.textDim, a, maxLines = 3), tx, Y(28f) + h(tl), a)
    }

    val L = ox - gap
    val R = ox + tw + gap
    // Left stick + D-pad.
    drawCircle(unused, 19f * k * s, p(X(44f), Y(50f)))
    drawCircle(if (used(LEFT_STICK)) ChipBlue else unused, 19f * k * s, p(X(44f), Y(50f)), style = Stroke(3f * k * s))
    leader(L + 3f, Y(50f), X(44f - 21f))
    titled("Left stick", lsL, L, Y(50f), 1)
    val dpc = if (used(DPAD)) ChipBlue else unused
    drawRoundRect(dpc, p(X(38f), Y(90f)), Size(12f * k * s, 36f * k * s), CornerRadius(3f * k * s))
    drawRoundRect(dpc, p(X(26f), Y(102f)), Size(36f * k * s, 12f * k * s), CornerRadius(3f * k * s))
    leader(L + 3f, Y(108f), X(26f))
    titled("D-Pad", dpL, L, Y(108f), 1)

    // Face buttons; actions grouped ("A B  Jump (2)") on the right, the block centred on the buttons.
    listOf(FACE_NORTH to Offset(0f, -14f), FACE_EAST to Offset(14f, 0f), FACE_SOUTH to Offset(0f, 14f), FACE_WEST to Offset(-14f, 0f)).forEach { (key, o) ->
        drawCircle(if (used(key)) controlColor(key) else unused, 7.5f * k * s, p(X(256f + o.x), Y(50f + o.y)))
        val l = tm.measure(key.shortLabel, TextStyle(fontSize = (maxOf(6.5f, 8.5f * k) * s).toSp(), fontWeight = FontWeight.ExtraBold, color = White))
        draw(l, X(256f + o.x), Y(50f + o.y) - h(l) / 2f, 0)
    }
    leader(X(256f + 23f), Y(50f), R - 3f)
    val rowH = faceRows.map { maxOf(h(it.third), dotR * 2f + 2f) }
    var fy = Y(50f) - (rowH.sum() + 4f * (rowH.size - 1).coerceAtLeast(0)) / 2f
    faceRows.forEachIndexed { i, (keys, dotsW, l) ->
        var x = R + dotR
        val firstLineMid = fy + minOf(h(l), fs * 1.18f) / 2f
        keys.forEach { key ->
            drawCircle(controlColor(key), dotR * s, p(x, firstLineMid))
            val kl = tm.measure(key.shortLabel, TextStyle(fontSize = (dotR * 1.1f * s).toSp(), fontWeight = FontWeight.ExtraBold, color = White))
            draw(kl, x, firstLineMid - h(kl) / 2f, 0)
            x += dotR * 2f + 3f
        }
        draw(l, R + dotsW, fy, -1)
        fy += rowH[i] + 4f
    }

    // Right stick.
    drawCircle(unused, 19f * k * s, p(X(256f), Y(108f)))
    drawCircle(if (used(RIGHT_STICK)) controlColor(RIGHT_STICK) else unused, 19f * k * s, p(X(256f), Y(108f)), style = Stroke(3f * k * s))
    leader(X(256f + 21f), Y(108f), R - 3f)
    titled("Right stick", rsL, R, Y(108f), -1)

    // Shoulders, above their tabs: L1 / R1 nearest the Thor, L2 / R2 above them.
    fun shoulders(list: List<Sh>, edge: Float, a: Int) {
        var bottom = oy - tabs - 6f
        list.forEach { sh ->
            val rh = maxOf(h(sh.l), fs + 6f)
            val mid = bottom - rh / 2f
            val cw = chip(sh.key.shortLabel, edge, mid, if (used(sh.key)) ChipBlue else unused, fs - 2f, a)
            val tx = if (a == 1) edge - cw - 6f else edge + cw + 6f
            draw(sh.l, tx, mid - h(sh.l) / 2f, a)
            bottom -= rh + rowGap
        }
    }
    shoulders(shL, X(Thor.INNER_TAB_EDGE), 1)
    shoulders(shR, X(Thor.W - Thor.INNER_TAB_EDGE), -1)

    // System row under the Thor (L3, R3, gyro, tilt), centred on the drawing.
    if (sysParts.isNotEmpty()) {
        val y = oy + th + 6f + (sysH - 6f) / 2f
        val widths = sysParts.map { (n, l, _) -> chipWidth(n, fs - 2f) + 5f + w(l) }
        var x = (ox + tw / 2f - (widths.sum() + 16f * (sysParts.size - 1)) / 2f).coerceIn(0f, maxOf(0f, W - widths.sum() - 16f * (sysParts.size - 1)))
        sysParts.forEachIndexed { i, (n, l, col) ->
            val cw = chip(n, x, y, col, fs - 2f, -1)
            draw(l, x + cw + 5f, y - minOf(h(l), fs * 1.18f) / 2f, -1)
            x += widths[i] + 16f
        }
    }
}
