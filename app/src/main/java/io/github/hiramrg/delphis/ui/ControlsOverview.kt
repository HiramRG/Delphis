package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.core.ThorControl
import io.github.hiramrg.delphis.core.ThorControl.*
import io.github.hiramrg.delphis.core.splitAction

/** One thing the player can do, and every Thor control that does it. */
class ControlAction(val label: String, val controls: List<ThorControl>)

/** The controls view split into the (up to four) main actions and everything else. */
class ControlsSummary(val main: List<ControlAction>, val other: List<ControlAction>)

private val FACE = listOf(FACE_EAST, FACE_SOUTH, FACE_NORTH, FACE_WEST)

/** Controls that are never shown as a big tile: movement, camera pad and system buttons. */
private val NEVER_MAIN = setOf(LEFT_STICK, DPAD, START, SELECT, L3, R3, MOTION)

/** Order the big tiles are picked in: face buttons first, then the right side, then the left. */
private val MAIN_ORDER = FACE + listOf(R2, R1, GYRO, RIGHT_STICK, L2, L1)

/** Order of the small "Also" items: roughly how often players reach for them. */
private val OTHER_ORDER = listOf(LEFT_STICK, MOTION, GYRO, L2, L1, DPAD, R2, R1, RIGHT_STICK) + FACE + listOf(START, SELECT, L3, R3)

/** "Crouch / Ground pound (Z)" → "Crouch / Ground pound". Same-named actions are merged. */
fun summarize(preset: Preset): ControlsSummary {
    val byLabel = linkedMapOf<String, MutableList<ThorControl>>()
    preset.mappings.distinctBy { it.control }.forEach { m ->
        val (name, inner) = splitAction(m.action)
        // "+ (Pause)" reads better as "Pause" than "+".
        val label = if (name.trim().length <= 2 && inner != null) inner else name.trim()
        byLabel.getOrPut(label) { mutableListOf() } += m.control
    }
    val all = byLabel.map { (label, cs) -> ControlAction(label, cs) }

    fun rank(a: ControlAction, order: List<ThorControl>) =
        a.controls.minOf { c -> order.indexOf(c).let { if (it < 0) order.size else it } }

    val main = all.filter { a -> a.controls.any { it !in NEVER_MAIN } }
        .sortedBy { rank(it, MAIN_ORDER) }
        .take(4)
    val other = (all - main.toSet()).sortedBy { rank(it, OTHER_ORDER) }
    return ControlsSummary(main, other)
}

/** Short form for the small items: "Crouch / Ground pound" → "Crouch", "Aim pointer, grab…" → "Aim pointer". */
private fun shortLabel(label: String): String =
    label.split(" / ", ", ").first().trim().ifEmpty { label }

/**
 * The in-game controls screen: the main actions as big tiles, then a small "Also" grid with the rest.
 * Kept deliberately plain (no Wii button names, no device drawing) so it reads at a glance.
 *
 * Everything is sized from the space available: the rows always fill the area, so a game with few
 * actions gets big tiles and large text, and one with many actions gets smaller ones.
 */
@Composable
fun ControlsOverview(preset: Preset, modifier: Modifier = Modifier) {
    val summary = remember(preset) { summarize(preset) }
    val fontScale = LocalDensity.current.fontScale
    // Text sizes are worked out in dp; this keeps them right if the system font size was changed.
    val sp: (Float) -> TextUnit = { (it / fontScale).sp }

    BoxWithConstraints(modifier) {
        val w = maxWidth.value
        val h = if (constraints.hasBoundedHeight) maxHeight.value else 320f
        val main = summary.main
        val other = summary.other

        val mainRows = (main.size + 1) / 2
        val maxCols = when { w >= 400f -> 4; w >= 280f -> 3; else -> 2 }
        // Balanced grid: 7 items in 4 columns → 4 + 3, 5 items → 3 + 2.
        val cols = if (other.isEmpty()) 1 else ((other.size + ceilDiv(other.size, maxCols) - 1) / ceilDiv(other.size, maxCols))
        val otherRows = if (other.isEmpty()) 0 else ceilDiv(other.size, cols)

        val tileGap = 8f
        val gridGap = 4f
        val labelH = if (other.isEmpty()) 0f else 18f
        val sectionGap = if (main.isNotEmpty() && other.isNotEmpty()) 10f else 0f
        val fixed = (mainRows - 1).coerceAtLeast(0) * tileGap + labelH + (otherRows - 1).coerceAtLeast(0) * gridGap + sectionGap
        // A tile row counts as 2.2 small rows.
        val units = 2.2f * mainRows + otherRows
        val unit = if (units > 0) ((h - fixed) / units).coerceAtLeast(10f) else 0f
        val tileH = (unit * 2.2f).coerceIn(44f, 170f)
        val smallH = unit.coerceIn(26f, 80f)
        val tileW = (w - tileGap) / 2f
        val cellW = (w - (cols - 1) * 6f) / cols

        Column(Modifier.fillMaxSize()) {
            main.chunked(2).forEachIndexed { i, pair ->
                if (i > 0) Spacer(Modifier.height(tileGap.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tileGap.dp)) {
                    pair.forEach { MainTile(it, tileH, tileW, sp, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (other.isNotEmpty()) {
                Spacer(Modifier.height(sectionGap.dp))
                Text(
                    "ALSO", fontSize = sp(11f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(labelH.dp),
                )
                other.chunked(cols).forEachIndexed { i, row ->
                    if (i > 0) Spacer(Modifier.height(gridGap.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { SmallItem(it, smallH, cellW, sp, Modifier.weight(1f)) }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b

/**
 * A big tile. Tall tiles put the badges above the text so long names get the full width; short
 * tiles put them side by side.
 */
@Composable
private fun MainTile(a: ControlAction, tileH: Float, tileW: Float, sp: (Float) -> TextUnit, modifier: Modifier) {
    // Show the face buttons when there are any (A B, X Y); other controls doing the same thing go in a small note.
    val face = a.controls.filter { it in FACE }.sortedBy { FACE.indexOf(it) }
    val shown = (face.ifEmpty { a.controls.sortedBy { if (it == GYRO) 0 else 1 } }).take(2)
    val extra = a.controls - shown.toSet()
    val color = controlColor(shown.first())
    val shape = RoundedCornerShape((tileH * 0.16f).coerceIn(12f, 22f).dp)
    val tall = tileH >= 84f
    val pad = if (tall) 10f else 12f
    val noteH = if (extra.isNotEmpty()) 14f else 0f
    val box = modifier.height(tileH.dp).clip(shape)
        .background(color.copy(alpha = 0.10f)).border(1.5.dp, color.copy(alpha = 0.45f), shape)
        .padding(horizontal = 12.dp, vertical = pad.dp)

    if (tall) {
        val badge = (tileH * 0.34f).coerceIn(26f, 58f)
        val textW = tileW - 24f
        val textH = tileH - 2 * pad - badge - 6f - noteH
        val font = minOf(textH / 2.5f, textW / 7.5f).coerceIn(13f, 32f)
        Column(box, verticalArrangement = Arrangement.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { shown.forEach { ControlBadge(it, badge.dp) } }
            Spacer(Modifier.height(6.dp))
            TileText(a.label, font, sp)
            if (extra.isNotEmpty()) Note(extra, sp)
        }
    } else {
        val badge = (tileH * 0.5f).coerceIn(22f, 40f)
        val textW = tileW - 24f - shown.size * (badge + 4f) - 10f
        val font = minOf((tileH - 2 * pad - noteH) / 2.4f, textW / 7f).coerceIn(12f, 22f)
        Row(box, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { shown.forEach { ControlBadge(it, badge.dp) } }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                TileText(a.label, font, sp)
                if (extra.isNotEmpty()) Note(extra, sp)
            }
        }
    }
}

@Composable
private fun TileText(label: String, font: Float, sp: (Float) -> TextUnit) {
    Text(
        label, color = LocalWii.current.text, fontWeight = FontWeight.Bold,
        fontSize = sp(font), lineHeight = sp(font * 1.1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Note(extra: List<ThorControl>, sp: (Float) -> TextUnit) {
    Text(
        "also " + extra.joinToString(" ") { badgeName(it) }, fontSize = sp(11f), lineHeight = sp(13f),
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
    )
}

/** A small "Also" item: one badge and a short label, sized to its grid cell. */
@Composable
private fun SmallItem(a: ControlAction, h: Float, cellW: Float, sp: (Float) -> TextUnit, modifier: Modifier) {
    val badge = (h * 0.62f).coerceIn(20f, 42f)
    val textW = cellW - badge - 6f
    val font = minOf(h * 0.32f, textW / 6.2f).coerceIn(11f, 20f)
    Row(modifier.height(h.dp), verticalAlignment = Alignment.CenterVertically) {
        ControlBadge(a.controls.first(), badge.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            shortLabel(a.label), color = LocalWii.current.text, fontWeight = FontWeight.Bold,
            fontSize = sp(font), lineHeight = sp(font * 1.1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun badgeName(c: ThorControl) = when (c) {
    LEFT_STICK -> "L stick"; RIGHT_STICK -> "R stick"; MOTION -> "tilt"; GYRO -> "gyro"; else -> c.shortLabel
}
