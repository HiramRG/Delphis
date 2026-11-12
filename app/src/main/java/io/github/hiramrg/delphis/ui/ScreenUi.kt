package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.GameScreen
import io.github.hiramrg.delphis.core.GameScreen.Choice
import io.github.hiramrg.delphis.core.GameScreen.Fill
import io.github.hiramrg.delphis.core.GameScreen.Shape
import io.github.hiramrg.delphis.core.GameScreen.Sides

private val Sel = Color(0xFF34BEED)

/** "SCREEN · 4:3 | 16:9", the look line ("More of the world ›") and how it's done (game patch / hack). Game page, GameCube only. */
@Composable
fun ScreenSection(choice: Choice, gameId: String?, useHack: Boolean, onShape: (Shape) -> Unit, onOpen: () -> Unit) {
    val c = LocalWii.current
    Text("SCREEN", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = c.textDim, modifier = Modifier.padding(top = 8.dp))
    ShapeSwitch(choice.shape, onShape, Modifier.padding(top = 4.dp))
    Row(
        Modifier.padding(top = 5.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(GameScreen.lookLabel(choice, gameId, useHack), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1)
        Text("  ›", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
    }
    when (GameScreen.method(choice, gameId, useHack)) {
        GameScreen.Method.PATCH -> Tag("✓ game patch", Color(0xFF7BD88F), Color(0xFF1D3A26))
        GameScreen.Method.HACK -> Tag("hack · edges may glitch", Color(0xFFF0B25A), Color(0xFF3A2D16))
        else -> {}
    }
}

@Composable
private fun Tag(text: String, fg: Color, bg: Color) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1,
        modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(9.dp)).background(bg).border(1.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(9.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
}

@Composable
fun ShapeSwitch(shape: Shape, onShape: (Shape) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalWii.current
    Row(modifier.clip(RoundedCornerShape(50)).background(if (c.dark) Color(0xFF262C33) else c.bar).padding(2.dp)) {
        listOf(Shape.STANDARD to "4:3", Shape.WIDE to "16:9").forEach { (v, label) ->
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (shape == v) Color(0xFF0A2533) else c.textDim,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (shape == v) c.accent else Color.Transparent).clickable { onShape(v) }.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

/** The choices for the current shape, as preview cards. 16:9: More of the world / Stretch; 4:3: the sides. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenChoices(choice: Choice, gameId: String?, useHack: Boolean, colour: Int, cardW: Dp, showSub: Boolean = true, onPick: (Choice) -> Unit) {
    val patch = io.github.hiramrg.delphis.core.WidescreenPatches.forGame(gameId) != null
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (choice.shape == Shape.WIDE) {
            val worldSub = when { patch -> "Game patch for this game. Nothing stretched."; useHack -> "Dolphin's widescreen hack. Edges may glitch."; else -> "No patch for this game: stretches (hack is off in Settings)." }
            ChoiceCard("More of the world", if (showSub) worldSub else null, choice.fill == Fill.WORLD, cardW, { Preview(Preview.WORLD, colour) }) { onPick(choice.copy(fill = Fill.WORLD)) }
            ChoiceCard("Stretch", if (showSub) "Everything gets about 33% wider. Works with every game." else null, choice.fill == Fill.STRETCH, cardW, { Preview(Preview.STRETCH, colour) }) { onPick(choice.copy(fill = Fill.STRETCH)) }
        } else {
            listOf(Sides.BLACK to "Like the original.", Sides.PATTERN to "A calm pattern, no logos.", Sides.COLOUR to "A colour taken from the cover.").forEach { (sd, sub) ->
                ChoiceCard(sd.label, if (showSub) sub else null, choice.sides == sd, cardW, {
                    Preview(when (sd) { Sides.BLACK -> Preview.BLACK; Sides.PATTERN -> Preview.PATTERN; Sides.COLOUR -> Preview.COLOUR }, colour)
                }) { onPick(choice.copy(sides = sd)) }
            }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, sub: String?, on: Boolean, w: Dp, preview: @Composable () -> Unit, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(w).focusRing(shape).clip(shape).background(if (on) (if (c.dark) Color(0xFF173444) else c.accentSoft) else (if (c.dark) Color(0xFF20252B) else c.card))
            .border(if (on) 3.dp else 2.dp, if (on) Sel else c.cardBorder, shape).clickable(onClick = onClick).padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(7.dp))) { preview() }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1)
            if (on) Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF6BCB77))
        }
        if (sub != null) Text(sub, fontSize = 10.sp, color = c.textDim, lineHeight = 12.sp, minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

private enum class Preview { WORLD, STRETCH, BLACK, PATTERN, COLOUR }

/** A tiny made-up scene (sky, grass, a red block) showing how the picture fills the screen. */
@Composable
private fun Preview(kind: Preview, colour: Int) {
    val base = Color(colour)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val pw = if (kind == Preview.WORLD || kind == Preview.STRETCH) w else h * 4f / 3f
        val left = (w - pw) / 2f
        when (kind) {
            Preview.BLACK -> drawRect(Color.Black)
            Preview.COLOUR -> drawRect(base.copy(red = base.red * 0.62f, green = base.green * 0.62f, blue = base.blue * 0.62f))
            Preview.PATTERN -> {
                drawRect(base.copy(red = base.red * 0.55f, green = base.green * 0.55f, blue = base.blue * 0.55f))
                val stripe = base.copy(red = base.red * 0.68f, green = base.green * 0.68f, blue = base.blue * 0.68f)
                var x = -h; while (x < w + h) { drawLine(stripe, Offset(x, 0f), Offset(x + h, h), strokeWidth = 4f); x += 9f }
            }
            else -> {}
        }
        drawRect(Brush.verticalGradient(listOf(Color(0xFF7EC8F0), Color(0xFFBFE6F7))), Offset(left, 0f), Size(pw, h * 0.46f))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF5AA04A), Color(0xFF3D7A34))), Offset(left, h * 0.46f), Size(pw, h * 0.54f))
        // The block: same size in every view, except Stretch (wider).
        val bw = (h * 4f / 3f) * 0.12f * (if (kind == Preview.STRETCH) 1.33f else 1f)
        drawRoundRect(Color(0xFFC85A3A), Offset(w / 2f - bw / 2f, h * 0.3f), Size(bw, h * 0.28f), CornerRadius(bw * 0.3f))
    }
}


/** The choices the D-pad steps through for a shape: the sides for 4:3, the fill for 16:9. */
fun screenOptions(c: Choice): List<Choice> =
    if (c.shape == Shape.WIDE) Fill.entries.map { c.copy(fill = it) } else Sides.entries.map { c.copy(sides = it) }

/** The next choice to the left (-1) or right (+1) of [c]. */
fun screenStep(c: Choice, delta: Int): Choice {
    val all = screenOptions(c)
    val i = all.indexOfFirst { it == c }.coerceAtLeast(0)
    return all[(i + delta).coerceIn(0, all.lastIndex)]
}

/**
 * The screen picker, on the page (not a pop-up, so the cards sit side by side and the gamepad keeps
 * working): L1 / R1 = 4:3 / 16:9, D-pad ◀ ▶ = pick a card, Ⓐ Done, Ⓑ Cancel. Keys are handled by the page.
 */
@Composable
fun BoxScope.ScreenPanel(
    gameTitle: String, choice: Choice, gameId: String?, useHack: Boolean, colour: Int,
    onPick: (Choice) -> Unit, onCancel: () -> Unit, onDone: () -> Unit,
) {
    val c = LocalWii.current
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onCancel))
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.align(Alignment.Center).fillMaxWidth(0.9f).clip(shape)
            .background(if (c.dark) c.sidebar else c.card).border(2.dp, Sel, shape)
            .clickable(enabled = false) {}.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("SCREEN · " + gameTitle.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = c.textDim, maxLines = 1)
                Text(if (choice.shape == Shape.WIDE) "16:9 · stretch it or show more of the world?" else "4:3 · what goes in the sides?",
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            KeyBadge("L1")
            ShapeSwitch(choice.shape, { onPick(choice.copy(shape = it)) })
            KeyBadge("R1")
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Every card is a third of the width in both views, so the panel keeps its height; 16:9's two sit centred.
            val w = (maxWidth - 20.dp) / 3
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ScreenChoices(choice, gameId, useHack, colour, w, onPick = onPick) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyIcon("DPAD_LR")
            Text("Choose", fontSize = 12.sp, color = c.textDim)
            Spacer(Modifier.weight(1f))
            PanelButton("Cancel", "B", primary = false, onClick = onCancel)
            PanelButton("Done", "A", primary = true, onClick = onDone)
        }
    }
}

@Composable
private fun PanelButton(label: String, key: String, primary: Boolean, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(40.dp).clip(shape).background(if (primary) Sel else c.card).border(2.dp, if (primary) Sel else c.cardBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = if (primary) Color(0xFF0A2533) else c.text, maxLines = 1)
        KeyIcon(key, if (primary) Color(0xFF0A2533).copy(alpha = 0.75f) else c.textDim)
    }
}
