package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/** [onFit] gets the label font size (dp) the diagram ended up using, so callers can switch to the list when it gets small. */
@Composable
fun ControlsMap(preset: Preset, modifier: Modifier = Modifier, onFit: ((Float) -> Unit)? = null) {
    val c = LocalWii.current
    val measurer = rememberTextMeasurer()
    val actions = preset.mappings.groupBy { it.control }.mapValues { (_, ms) -> mapLabel(ms.first().action) }
    Canvas(modifier) { val fs = drawMap(actions, measurer, c); onFit?.invoke(fs) }
}

/** Below this label size (dp ≈ pt) the diagram is hard to read, so the card opens on the list. */
const val MIN_MAP_FONT = 10f

/** Order of the list: the Thor's left side top to bottom, then the right side. */
private val LIST_ORDER = listOf(L2, L1, LEFT_STICK, L3, DPAD, SELECT, GYRO, MOTION, R2, R1, FACE_EAST, FACE_SOUTH, FACE_NORTH, FACE_WEST, RIGHT_STICK, R3, START)

/**
 * The controls as a list: two balanced columns filled top to bottom in the Thor's order. Every control is
 * its own row on a soft background, all rows the same height (as tall as fits, max 34 dp), the same 4 dp
 * gap between rows and 8 dp between columns, badges in a fixed-width column so every label starts at the
 * same place. Labels wrap to two lines; nothing is cut (it scrolls in the extreme case instead).
 */
@Composable
fun ControlsList(preset: Preset, modifier: Modifier = Modifier) {
    val c = LocalWii.current
    val actions = preset.mappings.groupBy { it.control }.mapValues { (_, ms) -> mapLabel(ms.first().action) }
    val all = LIST_ORDER.filter { actions[it] != null }
    if (all.isEmpty()) return
    val n = (all.size + 1) / 2
    val gap = 4.dp
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val rowH = minOf(34.dp, (maxHeight - gap * (n - 1)) / n).coerceAtLeast(22.dp)
        val fs = if (rowH >= 27.dp) 11.sp else 10.5.sp
        val rowBg = if (c.dark) Color(0xFF22272D) else c.bg
        Row(
            Modifier.fillMaxWidth().androidxVerticalScroll(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(all.take(n), all.drop(n)).forEach { col ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                    col.forEach { k ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = rowH).clip(RoundedCornerShape(9.dp)).background(rowBg).padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) { ControlBadge(k) }
                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                            Text(actions[k]!!, fontSize = fs, fontWeight = FontWeight.Bold, lineHeight = (fs.value * 1.15f).sp, color = c.text, maxLines = 2, overflow = TextOverflow.Visible)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.androidxVerticalScroll(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))

/** The same badge as on the diagram: coloured face buttons, blue chips, stick rings, the D-pad cross. */
@Composable
fun ControlBadge(k: ThorControl) {
    when {
        k == FACE_EAST || k == FACE_SOUTH || k == FACE_NORTH || k == FACE_WEST ->
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(50)).background(controlColor(k)), contentAlignment = Alignment.Center) {
                Text(k.shortLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = White)
            }
        k == LEFT_STICK || k == RIGHT_STICK ->
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(50)).background(Color(0xFF3A4250))
                    .border(2.dp, if (k == RIGHT_STICK) controlColor(k) else ChipBlue, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) { Text(if (k == LEFT_STICK) "L" else "R", fontSize = 10.sp, fontWeight = FontWeight.Black, color = White) }
        k == DPAD -> Canvas(Modifier.size(18.dp)) {
            val a = size.minDimension / 2f; val b = a * 0.34f
            drawRoundRect(ChipBlue, Offset(a - b, 0f), Size(2 * b, 2 * a), CornerRadius(2f))
            drawRoundRect(ChipBlue, Offset(0f, a - b), Size(2 * a, 2 * b), CornerRadius(2f))
        }
        else -> {
            val name = when (k) { GYRO -> "Gyro"; MOTION -> "Tilt"; SELECT -> "Select"; START -> "Start"; else -> k.shortLabel }
            val col = if (k == GYRO || k == R3) controlColor(k) else ChipBlue
            Box(Modifier.height(18.dp).clip(RoundedCornerShape(50)).background(col).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Text(name, fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = White, maxLines = 1)
            }
        }
    }
}

/**
 * Thor base design: 300×150 units. Bottom screen (90,14)-(210,118); Select/Start dots beside its top
 * corners; left stick (44,50), D-pad (44,108), face buttons around (256,50), right stick (256,108);
 * shoulder tabs L2/L1/R1/R2 on the top edge.
 */
private object Thor {
    const val W = 300f; const val H = 150f; const val TABS = 14f
    const val SCREEN_X = 90f; const val SCREEN_Y = 14f; const val SCREEN_W = 120f; const val SCREEN_H = 104f
}

/** Label columns: left-hand controls on the left, right-hand ones on the right, top to bottom as on the Thor. */
private val LEFT_COLUMN = listOf(L2, L1, LEFT_STICK, L3, DPAD, SELECT)
private val RIGHT_COLUMN = listOf(R2, R1, FACE_EAST, FACE_SOUTH, FACE_NORTH, FACE_WEST, RIGHT_STICK, R3, START)
private val EXTRA = listOf(GYRO, MOTION)

/**
 * No connector lines: the Thor in the middle shows which controls are used, and every label in the two
 * columns starts with that control's own badge (same colour and shape as on the drawing). Only used
 * controls get a row. Labels wrap, then shrink together, until both columns fit; never cut, so the
 * Wii-button hints in brackets always show.
 */
private fun DrawScope.drawMap(actions: Map<ThorControl, String>, tm: TextMeasurer, c: WiiColors): Float {
    val s = density // 1 unit = 1 dp
    val W = size.width / s
    val H = size.height / s
    val wide = W >= 520f
    val unused = if (c.dark) Color(0xFF3A4250) else Color(0xFFD5DBE2)
    val deviceFill = if (c.dark) c.sidebar else Color(0xFFF7F9FA)
    val screenFill = if (c.dark) c.bar else Color(0xFFE6EAEE)
    fun used(k: ThorControl) = actions[k] != null
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    fun w(l: TextLayoutResult) = l.size.width / s
    fun h(l: TextLayoutResult) = l.size.height / s
    fun measure(t: String, size: Float, color: Color, weight: FontWeight = FontWeight.ExtraBold) =
        tm.measure(t, TextStyle(fontSize = (size * s).toSp(), fontWeight = weight, color = color))
    fun fitted(t: String, size: Float, maxW: Float, a: TextAlign, maxLines: Int): TextLayoutResult {
        val cons = Constraints(maxWidth = (maxW * s).toInt().coerceAtLeast(1))
        val style = TextStyle(fontSize = (size * s).toSp(), fontWeight = FontWeight.Bold, color = c.text, lineHeight = (size * 1.18f * s).toSp(), textAlign = a)
        val l = tm.measure(t, style, overflow = TextOverflow.Clip, maxLines = maxLines, constraints = cons)
        return if (!l.hasVisualOverflow) l else tm.measure(t, style, overflow = TextOverflow.Visible, maxLines = maxLines + 3, constraints = cons)
    }

    // ---- rows (used controls only); gyro / tilt go under the shorter column
    val left = LEFT_COLUMN.filter(::used).toMutableList()
    val right = RIGHT_COLUMN.filter(::used).toMutableList()
    EXTRA.filter(::used).forEach { (if (left.size <= right.size) left else right) += it }

    fun badgeName(k: ThorControl) = when (k) {
        GYRO -> "Gyro"; MOTION -> "Tilt"; SELECT -> "Select"; START -> "Start"; else -> k.shortLabel
    }
    fun isChip(k: ThorControl) = k !in listOf(FACE_EAST, FACE_SOUTH, FACE_NORTH, FACE_WEST, LEFT_STICK, RIGHT_STICK, DPAD)
    fun chipW(k: ThorControl, bs: Float) = maxOf(bs + 10f, w(measure(badgeName(k), bs, Color.White)) + 12f)

    // ---- the Thor's size: about a third of the width, smaller if the card is short
    var k = W * (if (wide) 0.34f else 0.30f) / Thor.W
    k = minOf(k, (H * 0.62f) / (Thor.H + Thor.TABS)).coerceAtLeast(0.25f)
    val tw = Thor.W * k; val th = Thor.H * k; val tabs = Thor.TABS * k
    val colGap = if (wide) 14f else 9f
    val colW = (W - tw) / 2f - colGap - 4f
    val rowGap = 5f

    // ---- fit: one font size for every label
    var fs = if (wide) 12.5f else 11f
    lateinit var leftL: List<TextLayoutResult>; lateinit var rightL: List<TextLayoutResult>
    var bs = 0f; var badgeH = 0f
    fun badgeW(k: ThorControl) = if (isChip(k)) chipW(k, bs) else badgeH
    while (true) {
        bs = maxOf(7.5f, fs - 2f); badgeH = bs + 7f
        val lines = if (fs <= 10f) 3 else 2
        leftL = left.map { fitted(mapLabel(actions[it]!!), fs, colW - badgeW(it) - 6f, TextAlign.End, lines) }
        rightL = right.map { fitted(mapLabel(actions[it]!!), fs, colW - badgeW(it) - 6f, TextAlign.Start, lines) }
        fun colH(ls: List<TextLayoutResult>) = ls.sumOf { maxOf(h(it), badgeH).toDouble() }.toFloat() + rowGap * (ls.size - 1).coerceAtLeast(0)
        if (maxOf(colH(leftL), colH(rightL)) <= H - 6f || fs <= 7.5f) break
        fs -= 0.5f
    }
    fun colH(ls: List<TextLayoutResult>) = ls.sumOf { maxOf(h(it), badgeH).toDouble() }.toFloat() + rowGap * (ls.size - 1).coerceAtLeast(0)

    // ---- badges
    fun drawBadge(k: ThorControl, x: Float, yMid: Float, anchorRight: Boolean): Float {
        val bw = badgeW(k)
        val left0 = if (anchorRight) x - bw else x
        val cx = left0 + bw / 2f
        val r = badgeH / 2f
        when {
            isChip(k) -> {
                val col = if (k == GYRO || k == R3) controlColor(k) else ChipBlue
                drawRoundRect(col, p(left0, yMid - r), Size(bw * s, badgeH * s), CornerRadius(r * s))
                val l = measure(badgeName(k), bs, White)
                drawText(l, topLeft = p(cx - w(l) / 2f, yMid - h(l) / 2f))
            }
            k == LEFT_STICK || k == RIGHT_STICK -> {
                drawCircle(unused, r * s, p(cx, yMid))
                drawCircle(if (k == RIGHT_STICK) controlColor(k) else ChipBlue, (r - 1f) * s, p(cx, yMid), style = Stroke(2f * s))
                val l = measure(if (k == LEFT_STICK) "L" else "R", bs * 0.95f, White)
                drawText(l, topLeft = p(cx - w(l) / 2f, yMid - h(l) / 2f))
            }
            k == DPAD -> {
                val a = r * 0.9f; val b = r * 0.32f
                drawRoundRect(ChipBlue, p(cx - b, yMid - a), Size(2 * b * s, 2 * a * s), CornerRadius(1.5f * s))
                drawRoundRect(ChipBlue, p(cx - a, yMid - b), Size(2 * a * s, 2 * b * s), CornerRadius(1.5f * s))
            }
            else -> { // face button
                drawCircle(controlColor(k), r * s, p(cx, yMid))
                val l = measure(k.shortLabel, bs * 0.95f, White)
                drawText(l, topLeft = p(cx - w(l) / 2f, yMid - h(l) / 2f))
            }
        }
        return bw
    }

    // ---- layout: Thor centred, columns beside it, both vertically centred
    val ox = (W - tw) / 2f
    val oy = (H - (th + tabs)) / 2f + tabs
    val X = { u: Float -> ox + u * k }
    val Y = { u: Float -> oy + u * k }
    fun column(keys: List<ThorControl>, ls: List<TextLayoutResult>, onLeft: Boolean) {
        var y = (H - colH(ls)) / 2f
        val edge = if (onLeft) ox - colGap else ox + tw + colGap
        keys.forEachIndexed { i, key ->
            val l = ls[i]
            val rh = maxOf(h(l), badgeH)
            val firstLine = minOf(h(l), fs * 1.18f)
            val badgeMid = y + firstLine / 2f + (if (h(l) < badgeH) (badgeH - h(l)) / 2f else 0f)
            val bw = drawBadge(key, edge, badgeMid, anchorRight = onLeft)
            val textTop = y + (if (h(l) < badgeH) (badgeH - h(l)) / 2f else 0f)
            if (onLeft) drawText(l, topLeft = p(edge - bw - 6f - w(l), textTop))
            else drawText(l, topLeft = p(edge + bw + 6f, textTop))
            y += rh + rowGap
        }
    }

    // ---- the Thor
    listOf(L2 to 14f, L1 to 62f, R1 to 192f, R2 to 240f).forEach { (key, x) ->
        drawRoundRect(if (used(key)) ChipBlue else unused, p(X(x), oy - tabs), Size(46f * k * s, (tabs + 4f * k) * s), CornerRadius(7f * k * s))
        val l = measure(key.shortLabel, maxOf(6.5f, 9f * k), White)
        if (h(l) < tabs + 3f) drawText(l, topLeft = p(X(x + 23f) - w(l) / 2f, oy - tabs + (tabs - h(l)) / 2f))
    }
    drawRoundRect(deviceFill, p(ox, oy), Size(tw * s, th * s), CornerRadius(34f * k * s))
    drawRoundRect(c.cardBorder, p(ox, oy), Size(tw * s, th * s), CornerRadius(34f * k * s), style = Stroke(2f * s))
    drawRoundRect(screenFill, p(X(Thor.SCREEN_X), Y(Thor.SCREEN_Y)), Size(Thor.SCREEN_W * k * s, Thor.SCREEN_H * k * s), CornerRadius(7f * k * s))
    drawRoundRect(c.cardBorder, p(X(Thor.SCREEN_X), Y(Thor.SCREEN_Y)), Size(Thor.SCREEN_W * k * s, Thor.SCREEN_H * k * s), CornerRadius(7f * k * s), style = Stroke(1.2f * s))
    drawCircle(if (used(SELECT)) ChipBlue else unused, 5f * k * s, p(X(76f), Y(20f)))
    drawCircle(if (used(START)) ChipBlue else unused, 5f * k * s, p(X(224f), Y(20f)))
    drawCircle(unused, 19f * k * s, p(X(44f), Y(50f)))
    drawCircle(if (used(LEFT_STICK) || used(L3)) ChipBlue else unused, 19f * k * s, p(X(44f), Y(50f)), style = Stroke(3f * k * s))
    val dpc = if (used(DPAD)) ChipBlue else unused
    drawRoundRect(dpc, p(X(38f), Y(90f)), Size(12f * k * s, 36f * k * s), CornerRadius(3f * k * s))
    drawRoundRect(dpc, p(X(26f), Y(102f)), Size(36f * k * s, 12f * k * s), CornerRadius(3f * k * s))
    listOf(FACE_NORTH to Offset(0f, -14f), FACE_EAST to Offset(14f, 0f), FACE_SOUTH to Offset(0f, 14f), FACE_WEST to Offset(-14f, 0f)).forEach { (key, o) ->
        drawCircle(if (used(key)) controlColor(key) else unused, 7.5f * k * s, p(X(256f + o.x), Y(50f + o.y)))
        val l = measure(key.shortLabel, maxOf(6f, 8.5f * k), White)
        if (h(l) < 15f * k + 2f) drawText(l, topLeft = p(X(256f + o.x) - w(l) / 2f, Y(50f + o.y) - h(l) / 2f))
    }
    drawCircle(unused, 19f * k * s, p(X(256f), Y(108f)))
    drawCircle(if (used(RIGHT_STICK) || used(R3)) controlColor(RIGHT_STICK) else unused, 19f * k * s, p(X(256f), Y(108f)), style = Stroke(3f * k * s))

    column(left, leftL, onLeft = true)
    column(right, rightL, onLeft = false)
    return fs
}


/**
 * The controls card's body with the "Thor | List" switch on top, on every game. Starts on the diagram;
 * opens on the list when the diagram's text would get too small to read. The switch is remembered per game.
 */
@Composable
fun ControlsSwitchable(preset: Preset, prefs: io.github.hiramrg.delphis.platform.Prefs, gameKey: String?, modifier: Modifier = Modifier) {
    val saved = androidx.compose.runtime.remember(gameKey) { androidx.compose.runtime.mutableStateOf(gameKey?.let { prefs.controlsView(it) }) }
    var tooSmall by androidx.compose.runtime.remember(preset.id) { androidx.compose.runtime.mutableStateOf(false) }
    val list = when (saved.value) { "list" -> true; "thor" -> false; else -> tooSmall }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Segmented(listOf(false to "Thor", true to "List"), list, { v ->
            saved.value = if (v) "list" else "thor"
            gameKey?.let { prefs.setControlsView(it, saved.value!!) }
        }, height = 28.dp)
        if (list) ControlsList(preset, Modifier.weight(1f).fillMaxWidth())
        else ControlsMap(preset, Modifier.weight(1f).fillMaxWidth(), onFit = { fs -> if (saved.value == null && fs < MIN_MAP_FONT && !tooSmall) tooSmall = true })
    }
}
