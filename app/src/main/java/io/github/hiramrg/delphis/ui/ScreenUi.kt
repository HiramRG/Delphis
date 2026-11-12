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
import androidx.compose.ui.draw.blur
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
fun ScreenChoices(choice: Choice, gameId: String?, useHack: Boolean, colour: Int, cardW: Dp, showSub: Boolean = true, cardH: Dp? = null, onPick: (Choice) -> Unit) {
    val patch = io.github.hiramrg.delphis.core.WidescreenPatches.forGame(gameId) != null
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (choice.shape == Shape.WIDE) {
            val worldSub = when { patch -> "Game patch for this game. Nothing stretched."; useHack -> "Dolphin's widescreen hack. Edges may glitch."; else -> "No patch for this game: stretches (hack is off in Settings)." }
            ChoiceCard("More of the world", if (showSub) worldSub else null, choice.fill == Fill.WORLD, cardW, cardH, { Preview(Preview.WORLD, colour) }) { onPick(choice.copy(fill = Fill.WORLD)) }
            ChoiceCard("Stretch", if (showSub) "Everything gets about 33% wider. Works with every game." else null, choice.fill == Fill.STRETCH, cardW, cardH, { Preview(Preview.STRETCH, colour) }) { onPick(choice.copy(fill = Fill.STRETCH)) }
        } else {
            listOf(Sides.BLACK to "Like the original.", Sides.PATTERN to "A calm pattern, no logos.", Sides.COLOUR to "A colour taken from the cover.").forEach { (sd, sub) ->
                ChoiceCard(sd.label, if (showSub) sub else null, choice.sides == sd, cardW, cardH, {
                    Preview(when (sd) { Sides.BLACK -> Preview.BLACK; Sides.PATTERN -> Preview.PATTERN; Sides.COLOUR -> Preview.COLOUR }, colour)
                }) { onPick(choice.copy(sides = sd)) }
            }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, sub: String?, on: Boolean, w: Dp, h: Dp?, preview: @Composable () -> Unit, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(w).then(if (h != null) Modifier.height(h) else Modifier).focusRing(shape).clip(shape).background(if (on) (if (c.dark) Color(0xFF173444) else c.accentSoft) else (if (c.dark) Color(0xFF20252B) else c.card))
            .border(if (on) 3.dp else 2.dp, if (on) Sel else c.cardBorder, shape).clickable(onClick = onClick).padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().then(if (h != null) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f)).clip(RoundedCornerShape(7.dp))) { preview() }
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

/** The look the D-pad lands on from [l]: ◀ ▶ = ±1, ▲ ▼ = ±3 (the grid is 3 wide). */
fun lookStep(l: io.github.hiramrg.delphis.core.Looks.Look, delta: Int): io.github.hiramrg.delphis.core.Looks.Look {
    val all = io.github.hiramrg.delphis.core.Looks.Look.entries
    return all[(l.ordinal + delta).coerceIn(0, all.lastIndex)]
}

/**
 * The Picture panel, on the page (not a pop-up, so the gamepad keeps working): two tabs, ▭ Screen (GameCube)
 * and ✦ Look, switched with L1 / R1. Screen: Ⓨ = 4:3 / 16:9, ◀ ▶ = card. Look: the D-pad moves on the grid.
 * Ⓐ Done, Ⓑ Cancel. Keys are handled by the page. [choice] null = no Screen tab; [look] null = no Look tab.
 */
@Composable
fun BoxScope.PicturePanel(
    gameTitle: String, tab: Int, onTab: (Int) -> Unit,
    choice: Choice?, gameId: String?, useHack: Boolean, colour: Int, onPick: (Choice) -> Unit,
    look: io.github.hiramrg.delphis.core.Looks.Look?, onLook: (io.github.hiramrg.delphis.core.Looks.Look) -> Unit,
    onCancel: () -> Unit, onDone: () -> Unit, doneLabel: String = "Done", note: String? = null, compact: Boolean = false,
) {
    val c = LocalWii.current
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onCancel))
    val showLook = look != null && (tab == 1 || choice == null)
    PopupPanel(
        "Picture · $gameTitle",
        if (showLook) "Look · how should the picture look?" else if (choice!!.shape == Shape.WIDE) "Screen · 16:9: stretch it or show more of the world?" else "Screen · 4:3: what goes in the sides?",
        Modifier.align(if (compact) Alignment.TopCenter else Alignment.Center).padding(top = if (compact) 6.dp else 0.dp)
            .fillMaxWidth(if (compact) 0.97f else 0.9f).clickable(enabled = false) {},
        corner = if (choice != null && look != null) ({
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!compact) KeyIcon("L1")
                Segmented(listOf(0 to "▭ Screen", 1 to "✦ Look"), if (showLook) 1 else 0, onTab)
                if (!compact) KeyIcon("R1")
            }
        }) else null,
        buttons = {
            if (note != null) Text(note, fontSize = 11.sp, color = c.textDim, modifier = Modifier.weight(1f), lineHeight = 14.sp)
            else if (!compact) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyIcon(if (showLook) "DPAD" else "DPAD_LR"); Text("Choose", fontSize = 12.sp, color = c.textDim)
            }
            PanelBtn("Cancel", if (compact) null else "B", false, onClick = onCancel)
            PanelBtn(doneLabel, if (compact) null else "A", true, onClick = onDone)
        },
    ) {
        // One frame for both tabs: the same sub-line and a content area of fixed height, so switching
        // tabs doesn't make anything jump. Screen's cards fill it in one row, Look's six in two rows.
        val area = if (compact) 150.dp else 190.dp
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!showLook) {
                CapsLabel(if (choice!!.shape == Shape.WIDE) "16:9 fill" else "Sides · change right away", Modifier.weight(1f))
                Segmented(listOf(Shape.STANDARD to "4:3", Shape.WIDE to "16:9"), choice.shape, { onPick(choice.copy(shape = it)) })
                if (!compact) KeyIcon("Y")
            } else {
                CapsLabel("Applies when the game starts", Modifier.weight(1f))
                if (!compact) Text("Only the final picture: same game speed", fontSize = 11.sp, color = c.textDim, maxLines = 1)
            }
        }
        if (!showLook) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(area)) {
                val w = (maxWidth - 16.dp) / 3
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ScreenChoices(choice!!, gameId, useHack, colour, w, showSub = !compact, cardH = area, onPick = onPick) }
            }
        } else {
            Column(Modifier.fillMaxWidth().height(area), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                io.github.hiramrg.delphis.core.Looks.Look.entries.chunked(3).forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { l -> LookCard(l, l == look, Modifier.weight(1f).fillMaxHeight()) { onLook(l) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LookCard(l: io.github.hiramrg.delphis.core.Looks.Look, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.focusRing(shape).clip(shape).background(if (on) (if (c.dark) Color(0xFF173444) else c.accentSoft) else (if (c.dark) Color(0xFF20252B) else c.card))
            .border(if (on) 3.dp else 2.dp, if (on) Sel else c.cardBorder, shape).clickable(onClick = onClick).padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(7.dp))) { LookPreview(l) }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(l.label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            if (on) Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF6BCB77))
            Spacer(Modifier.weight(1f))
            when (l.cost) {
                io.github.hiramrg.delphis.core.Looks.Cost.LIGHT -> CostTag("⚡ Light", Color(0xFF6BCB77), Color(0xFF1F3B2A))
                io.github.hiramrg.delphis.core.Looks.Cost.MEDIUM -> CostTag("⚡⚡ Medium", Color(0xFFF5C542), Color(0xFF3D331A))
                else -> {}
            }
        }
    }
}

@Composable
private fun CostTag(text: String, fg: Color, bg: Color) {
    Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1, softWrap = false,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 5.dp, vertical = 1.dp))
}

/** A drawn scene (sky, hills, a red car) showing roughly what each look does. Not real shader output. */
@Composable
private fun LookPreview(l: io.github.hiramrg.delphis.core.Looks.Look) {
    val vivid = l == io.github.hiramrg.delphis.core.Looks.Look.VIVID
    Canvas(Modifier.fillMaxSize().then(if (l == io.github.hiramrg.delphis.core.Looks.Look.SOFT) Modifier.blur(0.8.dp) else Modifier)) {
        val w = size.width; val h = size.height
        val sky = if (vivid) listOf(Color(0xFF2E8BFF), Color(0xFF9FDCFF)) else listOf(Color(0xFF5AA9FF), Color(0xFFBFE6FF))
        drawRect(Brush.verticalGradient(sky))
        drawCircle(if (vivid) Color(0xFFFFD640) else Color(0xFFFFE27A), h * 0.1f, Offset(w * 0.8f, h * 0.22f))
        val hill = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.68f); lineTo(w * 0.2f, h * 0.42f); lineTo(w * 0.34f, h * 0.6f); lineTo(w * 0.5f, h * 0.34f)
            lineTo(w * 0.7f, h * 0.64f); lineTo(w * 0.85f, h * 0.48f); lineTo(w, h * 0.66f); lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(hill, if (vivid) Color(0xFF4FA03A) else Color(0xFF6B8F5E))
        drawRect(if (vivid) Color(0xFF2FB52A) else Color(0xFF4F9A3A), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
        val car = Color(if (vivid) 0xFFF03A1E else 0xFFD8442C)
        drawRoundRect(car, Offset(w * 0.44f, h * 0.68f), Size(w * 0.12f, h * 0.14f), CornerRadius(3f))
        if (l == io.github.hiramrg.delphis.core.Looks.Look.SHARP) drawRoundRect(Color(0xFF3A120A), Offset(w * 0.44f, h * 0.68f), Size(w * 0.12f, h * 0.14f), CornerRadius(3f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        if (l == io.github.hiramrg.delphis.core.Looks.Look.SCANLINES || l == io.github.hiramrg.delphis.core.Looks.Look.CRT) { var y = 0f; while (y < h) { drawRect(Color.Black.copy(alpha = 0.3f), Offset(0f, y), Size(w, 1.2f)); y += 3.5f } }
        if (l == io.github.hiramrg.delphis.core.Looks.Look.CRT) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center = Offset(w / 2, h / 2), radius = w * 0.65f))
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
