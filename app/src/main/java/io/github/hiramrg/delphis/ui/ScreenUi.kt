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
    res: io.github.hiramrg.delphis.core.Resolution.Level? = null, onRes: (io.github.hiramrg.delphis.core.Resolution.Level) -> Unit = {},
    advice: io.github.hiramrg.delphis.platform.DeviceInfo.ResolutionAdvice? = null,
    /** Show the controller buttons (the game page, and the in-game menu on one screen). */
    keys: Boolean = !compact,
    /** In the in-game menu: Cancel · Next time · Apply & restart (or just Close), always the same size. */
    inGame: PictureInGame? = null,
) {
    val c = LocalWii.current
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onCancel))
    val tabs = pictureTabs(choice != null, look != null, res != null)
    val shown = if (tab in tabs) tab else tabs.first()
    val showLook = shown == 1
    val showRes = shown == 2
    PopupPanel(
        "Picture · $gameTitle",
        when {
            showRes -> "Resolution · how sharp should it be?"
            showLook -> "Look · how should the picture look?"
            choice!!.shape == Shape.WIDE -> "Screen · 16:9: stretch it or show more of the world?"
            else -> "Screen · 4:3: what goes in the sides?"
        },
        Modifier.align(if (compact) Alignment.TopCenter else Alignment.Center).padding(top = if (compact) 6.dp else 0.dp)
            .fillMaxWidth(if (compact) 0.97f else 0.9f).clickable(enabled = false) {},
        corner = if (tabs.size > 1) ({
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (keys) KeyIcon("L1")
                Segmented(tabs.map { it to when (it) { 0 -> "▭ Screen"; 1 -> "✦ Look"; else -> "▣ Sharpness" } }, shown, onTab)
                if (keys) KeyIcon("R1")
            }
        }) else null,
        buttons = if (inGame != null) ({
            // Bottom button = the main choice, right button = back, left button = Next time (Thor names: B, A, Y).
            Spacer(Modifier.weight(1f))
            if (inGame.changed) {
                PanelBtn("Cancel", if (keys) "A" else null, false, onClick = onCancel)
                PanelBtn("Next time", if (keys) "Y" else null, false, onClick = inGame.onNextTime)
                PanelBtn("Apply & restart", if (keys) "B" else null, true, onClick = inGame.onApply)
            } else PanelBtn("Close", if (keys) "B" else null, true, onClick = onCancel)
        }) else ({
            if (note != null) Text(note, fontSize = 11.sp, color = c.textDim, modifier = Modifier.weight(1f), lineHeight = 14.sp)
            else if (keys) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyIcon(if (showLook || showRes) "DPAD" else "DPAD_LR"); Text("Choose", fontSize = 12.sp, color = c.textDim)
            }
            PanelBtn("Cancel", if (keys) "B" else null, false, onClick = onCancel)
            PanelBtn(doneLabel, if (keys) "A" else null, true, onClick = onDone)
        }),
    ) {
        // One frame for both tabs: the same sub-line and a content area of fixed height, so switching
        // tabs doesn't make anything jump. Screen's cards fill it in one row, Look's six in two rows.
        // A short screen (a phone) in the in-game menu: a lower content area, Sharpness in three columns.
        val shortCompact = compact && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
        val area = if (shortCompact) 104.dp else if (compact) 150.dp else 190.dp
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showRes) {
                CapsLabel("Applies when the game starts", Modifier.weight(1f))
                if (advice != null) Text(
                    "★ ${io.github.hiramrg.delphis.core.Resolution.forScale(advice.scale).short} suits this device", fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, color = Color(0xFF6BCB77), maxLines = 1,
                )
            } else if (!showLook) {
                CapsLabel(if (choice!!.shape == Shape.WIDE) "16:9 fill" else "Sides · change right away", Modifier.weight(1f))
                Segmented(listOf(Shape.STANDARD to "4:3", Shape.WIDE to "16:9"), choice.shape, { onPick(choice.copy(shape = it)) })
                if (keys) KeyIcon(if (inGame != null) "X" else "Y")
            } else {
                // The tags on the cards: how much extra battery each look uses (Original: none).
                if (!compact) CapsLabel("Applies when the game starts", Modifier.weight(1f))
                Text("Tags show extra battery use: Light or Medium.", fontSize = 11.sp, color = c.textDim, maxLines = 1,
                    modifier = if (compact) Modifier.weight(1f) else Modifier)
            }
        }
        if (showRes) {
            if (compact) {
                // The in-game menu: one line per level, its tag on the right, so everything fits the panel.
                // Two columns of one-line rows (short tags), so the panel fits a phone too.
                Column(Modifier.fillMaxWidth().height(area), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                    io.github.hiramrg.delphis.core.Resolution.Level.entries.chunked(if (shortCompact) 3 else 2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { l -> ResolutionLine(l, l == res, advice?.scale, Modifier.weight(1f)) { onRes(l) } }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().height(area), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    io.github.hiramrg.delphis.core.Resolution.Level.entries.chunked(3).forEach { row ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { l -> ResolutionCard(l, l == res, advice?.scale, true, Modifier.weight(1f).fillMaxHeight()) { onRes(l) } }
                        }
                    }
                }
                // Short screens (a phone held sideways) keep only the ★ line above: the reason would push the buttons off.
                if (advice != null && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp >= 480) Text(
                    resolutionWhy(advice), fontSize = 11.sp, lineHeight = 14.sp, color = c.textDim,
                )
            }
        } else if (!showLook) {
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
        // In the game: one note line of fixed height, so the panel never changes size.
        if (inGame != null) Text(
            if (!inGame.changed) "Pick what you like. Changes apply when the game starts again."
            else if (inGame.autosave) "Changes need the game to start again. Your progress is autosaved first."
            else "Changes need the game to start again, from its last save.",
            fontSize = 11.sp, lineHeight = 14.sp, color = c.textDim, maxLines = 2,
            modifier = Modifier.fillMaxWidth().height(30.dp).padding(top = 4.dp),
        )
    }
}

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

@Composable
private fun ResolutionLine(l: io.github.hiramrg.delphis.core.Resolution.Level, on: Boolean, recommended: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(10.dp)
    val fit = recommended?.let { io.github.hiramrg.delphis.core.Resolution.fit(l, it) }
    // Name on top, a short tag under it: fits the in-game menu's narrow columns.
    Column(
        modifier.heightIn(min = 40.dp).focusRing(shape).clip(shape)
            .background(if (on) (if (c.dark) Color(0xFF173444) else c.accentSoft) else (if (c.dark) Color(0xFF20252B) else c.card))
            .border(if (on) 2.dp else 1.dp, if (on) Sel else c.cardBorder, shape).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(l.label, fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
            if (on) Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF6BCB77))
        }
        when (fit) {
            io.github.hiramrg.delphis.core.Resolution.Fit.RECOMMENDED -> CostTag("★ Best", Color(0xFF6BCB77), Color(0xFF1F3B2A))
            io.github.hiramrg.delphis.core.Resolution.Fit.MAYBE -> CostTag("Slower", Color(0xFFF5C542), Color(0xFF3D331A))
            io.github.hiramrg.delphis.core.Resolution.Fit.SLOW -> CostTag("Too slow", Color(0xFFFF8A80), Color(0xFF3D1F1F))
            io.github.hiramrg.delphis.core.Resolution.Fit.FAST -> CostTag("Faster", c.textDim, if (c.dark) Color(0xFF2A3038) else c.cardBorder)
            null -> {}
        }
    }
}

@Composable
private fun ResolutionCard(l: io.github.hiramrg.delphis.core.Resolution.Level, on: Boolean, recommended: Int?, detail: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    val fit = recommended?.let { io.github.hiramrg.delphis.core.Resolution.fit(l, it) }
    Column(
        modifier.focusRing(shape).clip(shape).background(if (on) (if (c.dark) Color(0xFF173444) else c.accentSoft) else (if (c.dark) Color(0xFF20252B) else c.card))
            .border(if (on) 3.dp else 2.dp, if (on) Sel else c.cardBorder, shape).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(l.label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false), onTextLayout = { TextFit.check(l.label, it) })
            if (on) Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF6BCB77))
        }
        if (detail) Text(l.detail, fontSize = 10.5.sp, color = c.textDim, maxLines = 1, lineHeight = 13.sp)
        when (fit) {
            io.github.hiramrg.delphis.core.Resolution.Fit.RECOMMENDED -> CostTag(fit.label, Color(0xFF6BCB77), Color(0xFF1F3B2A))
            io.github.hiramrg.delphis.core.Resolution.Fit.MAYBE -> CostTag(fit.label, Color(0xFFF5C542), Color(0xFF3D331A))
            io.github.hiramrg.delphis.core.Resolution.Fit.SLOW -> CostTag(fit.label, Color(0xFFFF8A80), Color(0xFF3D1F1F))
            io.github.hiramrg.delphis.core.Resolution.Fit.FAST -> CostTag(fit.label, c.textDim, if (c.dark) Color(0xFF2A3038) else c.cardBorder)
            null -> {}
        }
    }
}

@Composable
private fun LookCard(l: io.github.hiramrg.delphis.core.Looks.Look, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    // The scene fills the card; the name sits on a dark strip at the bottom. The chosen look glows blue.
    BoxWithConstraints(
        modifier
            .then(if (on) Modifier.shadow(10.dp, shape, ambientColor = Sel, spotColor = Sel) else Modifier)
            .focusRing(shape).clip(shape).background(Color(0xFF20252B))
            .border(if (on) 3.dp else 2.dp, if (on) Sel else c.cardBorder, shape).clickable(onClick = onClick),
    ) {
        LookPreview(l)
        Row(
            // At least tall enough for the name and tag, even on a phone's small cards.
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(maxOf(24.dp, maxHeight * 0.36f))
                .background(Brush.horizontalGradient(0f to (if (on) Color(0xEB103042) else Color(0xB80A0C0F)), 0.6f to (if (on) Color(0xEB103042) else Color(0xB80A0C0F)), 1f to Color(0x260A0C0F)))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(l.label, fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, softWrap = false)
            if (on) Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.weight(1f))
            when (l.cost) {
                io.github.hiramrg.delphis.core.Looks.Cost.LIGHT -> CostTag("Light", Color(0xFF7FE0A0), Color(0xE61F4A2E))
                io.github.hiramrg.delphis.core.Looks.Cost.MEDIUM -> CostTag("Medium", Color(0xFFF5C26B), Color(0xE64A3A14))
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

/**
 * A little platform-game scene drawn by Delphis (sky, hills, blocks, coins, a purple creature), with each
 * look's effect on it: roughly what the look does, not real shader output.
 */
@Composable
private fun LookPreview(l: io.github.hiramrg.delphis.core.Looks.Look) {
    val vivid = l == io.github.hiramrg.delphis.core.Looks.Look.VIVID; val sharp = l == io.github.hiramrg.delphis.core.Looks.Look.SHARP
    fun col(base: Long, strong: Long) = Color(if (vivid) strong else base)
    Canvas(Modifier.fillMaxSize().then(if (l == io.github.hiramrg.delphis.core.Looks.Look.SOFT) Modifier.blur(1.2.dp) else Modifier)) {
        val w = size.width; val h = size.height
        fun X(u: Float) = w * u / 200f
        fun Y(u: Float) = h * u / 110f
        drawRect(Brush.verticalGradient(listOf(col(0xFF5DB8F5, 0xFF21A4FF), col(0xFFA9DCFA, 0xFF8FE0FF))))
        drawCircle(col(0xFFFFF3B0, 0xFFFFE45C), Y(9f), Offset(X(170f), Y(18f)))
        fun hills(y0: Float, amp: Float, colour: Color) = drawPath(androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, Y(y0)); var x = 0f; var up = true
            while (x < 200f) { quadraticTo(X(x + 25f), Y(y0 - (if (up) amp else 0f)), X(x + 50f), Y(y0)); x += 50f; up = !up }
            lineTo(w, h); lineTo(0f, h); close()
        }, colour)
        hills(74f, 26f, col(0xFF7CC85A, 0xFF5BDB3A))
        hills(86f, 20f, col(0xFF4FA83E, 0xFF2FB52A))
        drawRect(col(0xFF66CC44, 0xFF44E02A), Offset(0f, Y(92f)), Size(w, Y(4f)))
        drawRect(col(0xFFC98A4B, 0xFFE0904A), Offset(0f, Y(96f)), Size(w, h - Y(96f)))
        // Blocks and coins.
        listOf(40f, 104f, 118f, 132f).forEach { x ->
            drawRoundRect(col(0xFFE7A23A, 0xFFFFA62A), Offset(X(x), Y(40f)), Size(X(14f), Y(14f)), CornerRadius(X(2f)))
            drawRoundRect(Color(if (sharp) 0xFF5A3208 else 0xFF9A5E1C), Offset(X(x), Y(40f)), Size(X(14f), Y(14f)), CornerRadius(X(2f)), style = androidx.compose.ui.graphics.drawscope.Stroke(if (sharp) 2f else 1.2f))
        }
        listOf(108f, 118f, 128f, 138f).forEach { x ->
            drawOval(col(0xFFFFD23F, 0xFFFFE01A), Offset(X(x - 3.5f), Y(25f)), Size(X(7f), Y(10f)))
        }
        // A small purple creature on the hill.
        drawOval(col(0xFFB07CE8, 0xFFB45CFF), Offset(X(157f), Y(54f)), Size(X(18f), Y(16f)))
        drawCircle(Color.White, X(1.8f), Offset(X(163f), Y(60f))); drawCircle(Color.White, X(1.8f), Offset(X(169f), Y(60f)))
        drawCircle(Color(0xFF222222), X(0.9f), Offset(X(163.4f), Y(60.3f))); drawCircle(Color(0xFF222222), X(0.9f), Offset(X(169.4f), Y(60.3f)))
        if (l == io.github.hiramrg.delphis.core.Looks.Look.SCANLINES || l == io.github.hiramrg.delphis.core.Looks.Look.CRT) { var y = 0f; while (y < h) { drawRect(Color.Black.copy(alpha = if (l == io.github.hiramrg.delphis.core.Looks.Look.CRT) 0.14f else 0.28f), Offset(0f, y), Size(w, 1.4f)); y += 3.5f } }
        if (l == io.github.hiramrg.delphis.core.Looks.Look.CRT) {
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), center = Offset(w / 2, h / 2), radius = w * 0.62f))
            drawRoundRect(Color(0xFF0B0D10), Offset(2f, 2f), Size(w - 4f, h - 4f), CornerRadius(w * 0.08f, h * 0.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(5f))
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
