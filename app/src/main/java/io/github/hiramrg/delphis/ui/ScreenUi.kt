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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Star
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

/** The look the D-pad lands on from [l]: left / right = ±1, up / down = ±2 (the list is 2 wide). */
fun lookStep(l: io.github.hiramrg.delphis.core.Looks.Look, delta: Int): io.github.hiramrg.delphis.core.Looks.Look {
    val all = io.github.hiramrg.delphis.core.Looks.Look.entries
    return all[(l.ordinal + delta).coerceIn(0, all.lastIndex)]
}

/**
 * The Picture panel on the game page (a pop-up over the page, so the gamepad keeps working): the same
 * [PictureChoices] list as the in-game menu, with Cancel and Done. Keys are handled by the page.
 * [choice] null = no Screen tab; [look] null = no Look tab; [res] null = no Sharpness tab.
 */
@Composable
fun BoxScope.PicturePanel(
    gameTitle: String, tab: Int, onTab: (Int) -> Unit,
    choice: Choice?, gameId: String?, useHack: Boolean, @Suppress("UNUSED_PARAMETER") colour: Int, onPick: (Choice) -> Unit,
    look: io.github.hiramrg.delphis.core.Looks.Look?, onLook: (io.github.hiramrg.delphis.core.Looks.Look) -> Unit,
    onCancel: () -> Unit, onDone: () -> Unit, doneLabel: String = "Done",
    res: io.github.hiramrg.delphis.core.Resolution.Level? = null, onRes: (io.github.hiramrg.delphis.core.Resolution.Level) -> Unit = {},
    advice: io.github.hiramrg.delphis.platform.DeviceInfo.ResolutionAdvice? = null,
    /** Show the controller buttons. */
    keys: Boolean = true,
    /** Where the D-pad's glow is (null = on the picked one): the D-pad moves it, [pickKey] picks. */
    lookCursor: io.github.hiramrg.delphis.core.Looks.Look? = null,
    resCursor: io.github.hiramrg.delphis.core.Resolution.Level? = null,
    pickKey: String = "A",
) {
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onCancel))
    val tabs = pictureTabs(choice != null, look != null, res != null)
    val shown = if (tab in tabs) tab else tabs.first()
    val short = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    PopupPanel(
        "Picture · $gameTitle", "Picture",
        Modifier.align(Alignment.Center).fillMaxWidth(0.9f).clickable(enabled = false) {},
        buttons = {
            if (keys && !PadGlyphs.touch) HintText(pictureHint(tabs, shown, pickKey), fontSize = 11.sp, color = LocalWii.current.textDim, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            PanelBtn("Cancel", if (keys) "B" else null, false, onClick = onCancel)
            PanelBtn(doneLabel, if (keys) "⊕" else null, true, onClick = onDone)
        },
    ) {
        PictureChoices(
            tabs, shown, onTab, choice, gameId, useHack, onPick, look, lookCursor, onLook, res, resCursor, onRes, advice,
            keys = keys && !PadGlyphs.touch, status = pictureExplain(shown, choice, advice), shapeKey = if (keys && !PadGlyphs.touch) "Y" else null,
            modifier = Modifier.fillMaxWidth().height(if (short) 190.dp else 230.dp),
        )
    }
}

/** The controls line for the Picture choices: "✚ Move   Ⓐ Pick   L2 R2 Look / Sharpness". */
fun pictureHint(tabs: List<Int>, shown: Int, pickKey: String): String {
    val move = if (shown == 0) "✚ Choose" else "✚ Move   ${glyph(pickKey)} Pick"
    return if (tabs.size > 1) "$move   L2 R2 " + tabs.joinToString(" / ") { pictureTabName(it) } else move
}

fun pictureTabName(t: Int) = when (t) { 0 -> "Screen"; 1 -> "Look"; else -> "Sharpness" }

/** The status line when nothing changed: what the tab's tags mean. */
fun pictureExplain(shown: Int, choice: Choice?, advice: io.github.hiramrg.delphis.platform.DeviceInfo.ResolutionAdvice?): String = when (shown) {
    1 -> "The picture's style. Tags: extra battery use."
    2 -> if (advice != null) "How sharp the picture is. Best suits this device." else "How sharp the picture is."
    else -> if (choice?.shape == Shape.WIDE) "How to fill 16:9." else "What goes in the sides of 4:3."
}

/** A look's few words, short enough for the narrowest list. */
fun lookShort(l: io.github.hiramrg.delphis.core.Looks.Look) = when (l) {
    io.github.hiramrg.delphis.core.Looks.Look.ORIGINAL -> "No filter"
    io.github.hiramrg.delphis.core.Looks.Look.SHARP -> "Crisper edges"
    io.github.hiramrg.delphis.core.Looks.Look.VIVID -> "Richer colours"
    io.github.hiramrg.delphis.core.Looks.Look.SCANLINES -> "Old TV lines"
    io.github.hiramrg.delphis.core.Looks.Look.CRT -> "Old TV glow"
    io.github.hiramrg.delphis.core.Looks.Look.SOFT -> "Smoother edges"
}

/** A sharpness level's name and few words: "2×" / "720p". */
fun resName(l: io.github.hiramrg.delphis.core.Resolution.Level) = l.short
fun resShort(l: io.github.hiramrg.delphis.core.Resolution.Level) = when (l) {
    io.github.hiramrg.delphis.core.Resolution.Level.DOLPHIN -> "Dolphin's setting"
    io.github.hiramrg.delphis.core.Resolution.Level.X1 -> "480p · original"
    else -> l.label.substringAfter("· ")
}

private class OptTag(val text: String, val fg: Color, val bg: Color, val star: Boolean = false)

private fun lookTag(l: io.github.hiramrg.delphis.core.Looks.Look): OptTag? = when (l.cost) {
    io.github.hiramrg.delphis.core.Looks.Cost.LIGHT -> OptTag("Light", Color(0xFF7FE0A0), Color(0xFF1F3B2A))
    io.github.hiramrg.delphis.core.Looks.Cost.MEDIUM -> OptTag("Medium", Color(0xFFF5C26B), Color(0xFF3D331A))
    else -> null
}

private fun resTag(l: io.github.hiramrg.delphis.core.Resolution.Level, recommended: Int?): OptTag? = when (recommended?.let { io.github.hiramrg.delphis.core.Resolution.fit(l, it) }) {
    io.github.hiramrg.delphis.core.Resolution.Fit.RECOMMENDED -> OptTag("Best", Color(0xFF8FD8FF), Color(0xFF1E4A63), star = true)
    io.github.hiramrg.delphis.core.Resolution.Fit.MAYBE -> OptTag("Slower", Color(0xFFF5C26B), Color(0xFF3D331A))
    io.github.hiramrg.delphis.core.Resolution.Fit.SLOW -> OptTag("Too slow", Color(0xFFFF9A9A), Color(0xFF3D1F1F))
    io.github.hiramrg.delphis.core.Resolution.Fit.FAST -> OptTag("Faster", Color(0xFF7FE0A0), Color(0xFF1F3B2A))
    null -> null
}

/** The shortest an option row may be (name and its few words, one line each). */
private val MIN_ROW = 32.dp
/** The shortest a row may be with just the name. */
private val MIN_ROW_BRIEF = 22.dp

/**
 * The Picture choices as a plain list (no pictures), the same on the game page, the one-screen menu and the
 * Thor's bottom screen: the Screen / Look / Sharpness toggle on top (L2 / R2 with a controller), two columns
 * of three rows, each with a name, a few words and its tag, then one status line. The rows share the height
 * they're given, so nothing moves between tabs.
 */
@Composable
fun PictureChoices(
    tabs: List<Int>, shown: Int, onTab: (Int) -> Unit,
    choice: Choice?, gameId: String?, useHack: Boolean, onPick: (Choice) -> Unit,
    look: io.github.hiramrg.delphis.core.Looks.Look?, lookCursor: io.github.hiramrg.delphis.core.Looks.Look?, onLook: (io.github.hiramrg.delphis.core.Looks.Look) -> Unit,
    res: io.github.hiramrg.delphis.core.Resolution.Level?, resCursor: io.github.hiramrg.delphis.core.Resolution.Level?, onRes: (io.github.hiramrg.delphis.core.Resolution.Level) -> Unit,
    advice: io.github.hiramrg.delphis.platform.DeviceInfo.ResolutionAdvice?,
    keys: Boolean, status: String, statusWarn: Boolean = false, modifier: Modifier = Modifier,
    /** The key that switches 4:3 / 16:9 on the Screen tab (null = none shown). */
    shapeKey: String? = null,
    /** How many columns the list uses (2, or 3 when it's too short for three rows): the D-pad's up / down step. */
    onColumns: (Int) -> Unit = {},
) {
    val c = LocalWii.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (tabs.size > 1) Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            if (keys) KeyIcon("L2")
            Segmented(tabs.map { it to pictureTabName(it) }, shown, onTab, height = 28.dp)
            if (keys) KeyIcon("R2")
        }
        // Each cell: (name, few words, tag, picked, cursor, action). Null = an empty cell.
        class Cell(val name: String, val sub: String, val tag: OptTag?, val on: Boolean, val cursor: Boolean, val onClick: () -> Unit)
        val cells: List<Cell?> = when (shown) {
            1 -> io.github.hiramrg.delphis.core.Looks.Look.entries.map { l -> Cell(l.label, lookShort(l), lookTag(l), l == look, l == (lookCursor ?: look), { onLook(l) }) }
            2 -> io.github.hiramrg.delphis.core.Resolution.Level.entries.map { l -> Cell(resName(l), resShort(l), resTag(l, advice?.scale), l == res, l == (resCursor ?: res), { onRes(l) }) }
            else -> {
                val ch = choice!!
                val patch = io.github.hiramrg.delphis.core.WidescreenPatches.forGame(gameId) != null
                val opts = if (ch.shape == Shape.WIDE) listOf(
                    Cell("More of the world", when { patch -> "Game patch"; useHack -> "Widescreen hack"; else -> "Stretches (hack off)" }, null, ch.fill == Fill.WORLD, false, { onPick(ch.copy(fill = Fill.WORLD)) }),
                    Cell("Stretch", "Everything wider", null, ch.fill == Fill.STRETCH, false, { onPick(ch.copy(fill = Fill.STRETCH)) }),
                ) else listOf(
                    Cell(Sides.BLACK.label, "Like the original", null, ch.sides == Sides.BLACK, false, { onPick(ch.copy(sides = Sides.BLACK)) }),
                    Cell(Sides.PATTERN.label, "A calm pattern", null, ch.sides == Sides.PATTERN, false, { onPick(ch.copy(sides = Sides.PATTERN)) }),
                    Cell(Sides.COLOUR.label, "From the cover", null, ch.sides == Sides.COLOUR, false, { onPick(ch.copy(sides = Sides.COLOUR)) }),
                )
                opts
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        // Three rows of two. A short panel (a phone) drops the few words under each name; only when even
        // that doesn't fit, two rows of three.
        val brief = maxHeight < MIN_ROW * 3 + 12.dp
        val cols = if (maxHeight >= MIN_ROW_BRIEF * 3 + 12.dp) 2 else 3
        androidx.compose.runtime.SideEffect { onColumns(cols) }
        val rows = (6 + cols - 1) / cols
        // The Screen tab: the shape switch on the first row, its options after it.
        val laid: List<Cell?> = if (shown == 0) List(cols) { null } + cells else cells
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until rows) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (shown == 0 && r == 0) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Shape", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
                        ShapeSwitch(choice!!.shape, { onPick(choice.copy(shape = it)) })
                        if (shapeKey != null) KeyIcon(shapeKey)
                    }
                } else for (col in 0 until cols) {
                    val cell = laid.getOrNull(r * cols + col)
                    if (cell == null) Spacer(Modifier.weight(1f))
                    else OptionCell(cell.name, if (brief) null else cell.sub, cell.tag, cell.on, cell.cursor, Modifier.weight(1f).fillMaxHeight(), cell.onClick)
                }
            }
        }
        }
        Text(
            status, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, color = if (statusWarn) Color(0xFFF5C26B) else c.textDim,
            onTextLayout = { TextFit.check(status, it) },
        )
    }
}

@Composable
private fun OptionCell(name: String, sub: String?, tag: OptTag?, on: Boolean, cursor: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    val green = Color(0xFF6BCB77)
    Row(
        modifier
            .then(if (cursor) Modifier.shadow(8.dp, shape, ambientColor = Sel, spotColor = Sel) else Modifier)
            .focusRing(shape).clip(shape)
            .background(if (on) (if (c.dark) Color(0xFF203A2B) else Color(0xFFE3F4E7)) else (if (c.dark) Color(0xFF2A3038) else c.card))
            .border(2.dp, if (cursor) Sel else if (on) green.copy(alpha = 0.7f) else c.cardBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A round mark: empty, or green with a tick for the picked option.
        Canvas(Modifier.size(16.dp)) {
            val r = size.minDimension / 2f
            if (on) {
                drawCircle(green, r)
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(r * 0.5f, r * 1.02f); lineTo(r * 0.86f, r * 1.38f); lineTo(r * 1.5f, r * 0.66f) }
                drawPath(p, Color(0xFF0E2416), style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.28f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            } else drawCircle(c.textDim, r - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(name, fontSize = 12.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1, onTextLayout = { TextFit.check(name, it) })
            if (sub != null) Text(sub, fontSize = 10.sp, lineHeight = 12.sp, color = c.textDim, maxLines = 1, onTextLayout = { TextFit.check(sub, it) })
        }
        if (tag != null) Row(
            Modifier.clip(RoundedCornerShape(7.dp)).background(tag.bg).padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (tag.star) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Star, null, tint = tag.fg, modifier = Modifier.size(10.dp))
            Text(tag.text, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = tag.fg, maxLines = 1, softWrap = false)
        }
    }
}

/** The glyph HintText turns into the controller's button icon. */
private fun glyph(key: String) = when (key) { "A" -> "Ⓐ"; "B" -> "Ⓑ"; "X" -> "Ⓧ"; "Y" -> "Ⓨ"; else -> key }

/** The in-game Picture panel's choices: whether something changed, and what "Next time" / "Apply & restart" do. */
class PictureInGame(val changed: Boolean, val autosave: Boolean, val onNextTime: () -> Unit, val onApply: () -> Unit)

/** The Picture panel's tabs that apply: 0 Screen (GameCube), 1 Look, 2 Resolution. */
fun pictureTabs(screen: Boolean, look: Boolean, res: Boolean): List<Int> =
    listOfNotNull(0.takeIf { screen }, 1.takeIf { look }, 2.takeIf { res }).ifEmpty { listOf(0) }

/** Why Delphis suggests the level it does, in one or two short sentences. */
fun resolutionWhy(a: io.github.hiramrg.delphis.platform.DeviceInfo.ResolutionAdvice): String {
    val level = io.github.hiramrg.delphis.core.Resolution.forScale(a.scale).short
    val chip = if (a.known && a.chip != null) "${a.chip} chip, " else ""
    return "Sharper pictures work the graphics chip harder. For your ${a.device} (${chip}${a.screen} screen) Delphis suggests $level" +
        (if (a.known) "" else ", a guess since it doesn't know this chip") + ". If the game stutters or gets hot, go one step down."
}
