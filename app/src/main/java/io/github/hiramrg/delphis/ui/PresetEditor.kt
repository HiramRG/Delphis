package io.github.hiramrg.delphis.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.ActionCatalog
import io.github.hiramrg.delphis.core.Mapping
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.core.PresetEdits
import io.github.hiramrg.delphis.core.ThorControl
import io.github.hiramrg.delphis.core.ThorControl.*

/** Controls the editor lists, in the order they sit on the Thor. */
private val EDIT_ORDER = listOf(L2, L1, R2, R1, LEFT_STICK, L3, DPAD, FACE_EAST, FACE_SOUTH, FACE_NORTH, FACE_WEST, RIGHT_STICK, R3, SELECT, START, GYRO)

private val SelBorder = Color(0xFF34BEED)
private val SelFill = Color(0xFF173444)
private val PressFill = Color(0xFF10303D)

@Composable
private fun motionColor() = if (LocalWii.current.dark) Color(0xFFD9CFFC) else Color(0xFF6D5BB8)

/**
 * The preset editor: pick an action, then press the Thor button for it (top screen) or tap one
 * (bottom screen, where the game keeps the buttons); the panel underneath says what the action
 * does in the game, as three rows of tiles (Buttons · Wii Remote moves · Nunchuk moves). Both
 * screens share the same pieces: action rows, tiles, the "press the button" box.
 * [controlForKey] turns a key press into a Thor control (top screen only).
 */
@Composable
fun PresetEditor(
    base: Preset,
    customId: String,
    saveLabel: String,
    modifier: Modifier = Modifier,
    controlForKey: ((AndroidKey) -> ThorControl?)? = null,
    onCancel: () -> Unit,
    onSave: (Preset) -> Unit,
) {
    var maps by remember(base.id) { mutableStateOf(base.mappings) }
    val editable = remember(base.id) { EDIT_ORDER.filter { PresetEdits.sections(base.controller, it).isNotEmpty() } }
    var selected by remember(base.id) { mutableStateOf(editable.firstOrNull { c -> base.mappings.any { it.control == c } } ?: editable.firstOrNull()) }
    val changed = maps != base.mappings
    val draft = PresetEdits.build(base, maps, customId)

    BoxWithConstraints(modifier) {
        val wide = maxWidth > maxHeight * 1.05f
        val state = EditorState(
            base = base, maps = maps, editable = editable, selected = selected,
            select = { selected = it },
            move = { to -> selected?.let { from -> if (PresetEdits.compatible(from, to)) { maps = PresetEdits.move(maps, from, to); selected = to } } },
            pick = { tile -> selected?.let { maps = PresetEdits.setTarget(maps, it, tile) } },
            clear = { selected?.let { maps = PresetEdits.clear(maps, it) } },
            undo = { maps = base.mappings },
            changed = changed,
        )
        if (wide) TopEditor(state, saveLabel, controlForKey, onCancel) { onSave(draft) }
        else BottomEditor(state, saveLabel, onCancel) { onSave(draft) }
    }
}

private class EditorState(
    val base: Preset,
    val maps: List<Mapping>,
    val editable: List<ThorControl>,
    val selected: ThorControl?,
    val select: (ThorControl) -> Unit,
    val move: (ThorControl) -> Unit,
    val pick: (PresetEdits.Tile) -> Unit,
    val clear: () -> Unit,
    val undo: () -> Unit,
    val changed: Boolean,
) {
    fun mappingOn(c: ThorControl) = maps.firstOrNull { it.control == c }
    val used get() = editable.filter { mappingOn(it) != null }
    val free get() = editable.filter { mappingOn(it) == null }
    val selectedName: String get() = selected?.let { s -> mappingOn(s)?.let { PresetEdits.actionName(it) } ?: controlName(s) } ?: ""
}

private fun controlName(c: ThorControl) = when (c) {
    LEFT_STICK -> "Left stick"; RIGHT_STICK -> "Right stick"; DPAD -> "D-pad"; L3 -> "Left stick click"; R3 -> "Right stick click"
    FACE_EAST -> "A button"; FACE_SOUTH -> "B button"; FACE_NORTH -> "X button"; FACE_WEST -> "Y button"; GYRO -> "Gyro"; else -> c.shortLabel
}

// ============================================================================ top screen

@Composable
private fun TopEditor(s: EditorState, saveLabel: String, controlForKey: ((AndroidKey) -> ThorControl?)?, onCancel: () -> Unit, onSave: () -> Unit) {
    val c = LocalWii.current
    var listening by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(listening) { if (listening) runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().focusRequester(focus).focusable()
            .onPreviewKeyEvent { e ->
                if (!listening || controlForKey == null) return@onPreviewKeyEvent false
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                val control = controlForKey(e.nativeKeyEvent)
                if (control != null) { s.move(control); listening = false }
                true
            },
    ) {
        Text(withOrangeTags("Editing: ${s.base.name.removeSuffix(" (Custom)")}", c.orange), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Pick an action, then press the Thor button for it", fontSize = 11.sp, color = c.textDim)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyColumn(Modifier.weight(0.47f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(s.used, key = { it.name }) { control ->
                    ActionRow(control, s.mappingOn(control), control == s.selected, tail = if (control == s.selected) (if (listening) "press…" else "editing") else "›") {
                        s.select(control); listening = controlForKey != null && control.kind == ThorControl.Kind.BUTTON && control != GYRO
                    }
                }
                if (s.free.isNotEmpty()) {
                    item { SectionLabel("NOT USED · pick one to give it an action") }
                    items(s.free, key = { "free-" + it.name }) { control ->
                        ActionRow(control, null, control == s.selected, tail = if (control == s.selected) "editing" else "＋") { s.select(control); listening = false }
                    }
                }
            }
            Column(Modifier.weight(0.53f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AssignBox(s, listening = listening, canPress = controlForKey != null, onTap = { listening = !listening && controlForKey != null })
                PickerPanel(s, Modifier.weight(1f).fillMaxWidth(), tileW = 35.dp, tileH = 33.dp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            WiiButton("Undo", onClick = s.undo, enabled = s.changed, fontSize = 13.sp)
            Text("Ⓐ pick action · press any Thor button to move it · ✚ tiles", fontSize = 10.sp, color = c.textDim, modifier = Modifier.weight(1f), maxLines = 1)
            WiiButton("Cancel", onClick = onCancel, fontSize = 13.sp)
            PrimaryGlowButton(saveLabel, androidx.compose.material.icons.Icons.Filled.Save, onClick = onSave, enabled = s.changed, fontSize = 13.sp)
        }
    }
}

// ============================================================================ in-game bottom screen

@Composable
private fun BottomEditor(s: EditorState, saveLabel: String, onCancel: () -> Unit, onSave: () -> Unit) {
    val c = LocalWii.current
    var sheet by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("✕ Cancel", onClick = onCancel)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Edit controls", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text(s.base.name.removeSuffix(" (Custom)"), fontSize = 10.sp, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.changed) Pill("Undo", onClick = s.undo)
                Pill(saveLabel, onClick = onSave, primary = true, enabled = s.changed)
            }
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(s.used + s.free, key = { it.name }) { control ->
                    ActionRow(control, s.mappingOn(control), false, compact = true, tail = null) { s.select(control); sheet = true }
                }
            }
        }
        if (sheet && s.selected != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable { sheet = false })
            val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.8f).clip(shape)
                    .background(if (c.dark) Color(0xFF1B1F24) else c.card).border(2.dp, SelBorder, shape).padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MapBadge(s.selected, 20.dp)
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.selectedName, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        WiiGetsText(s.mappingOn(s.selected)?.target, 10.5.sp)
                    }
                    Pill("Done", onClick = { sheet = false })
                }
                Spacer(Modifier.height(5.dp))
                // The game owns the Thor's buttons here, so the new button is tapped instead of pressed.
                TapAssignBox(s)
                Spacer(Modifier.height(5.dp))
                PickerPanel(s, Modifier.weight(1f).fillMaxWidth(), tileW = 31.dp, tileH = 30.dp, framed = false)
            }
        }
    }
}

// ============================================================================ shared pieces

/** Control badge drawn like the controls map: blue chips, coloured face buttons, stick circles, D-pad cross. */
@Composable
fun MapBadge(control: ThorControl, height: Dp) {
    val blue = Color(0xFF5C8DF6)
    when (control) {
        FACE_EAST, FACE_SOUTH, FACE_NORTH, FACE_WEST -> Box(Modifier.size(height).clip(CircleShape).background(controlColor(control)), contentAlignment = Alignment.Center) {
            Text(control.shortLabel, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (height.value * 0.5f).sp)
        }
        LEFT_STICK, RIGHT_STICK -> Box(Modifier.size(height), contentAlignment = Alignment.Center) {
            val ring = if (control == RIGHT_STICK) controlColor(control) else blue
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(Color(0xFF3A4250), r)
                drawCircle(ring, r - 1.5.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            Text(if (control == LEFT_STICK) "L" else "R", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (height.value * 0.45f).sp)
        }
        DPAD -> Canvas(Modifier.size(height)) {
            val w = size.width; val t = w * 0.34f
            drawRoundRect(blue, Offset((w - t) / 2, 0f), Size(t, w), CornerRadius(t * 0.25f))
            drawRoundRect(blue, Offset(0f, (w - t) / 2), Size(w, t), CornerRadius(t * 0.25f))
        }
        else -> {
            val label = when (control) { START -> "Start"; SELECT -> "Select"; GYRO -> "Gyro"; MOTION -> "Tilt"; else -> control.shortLabel }
            val col = if (control == GYRO || control == R3) controlColor(control) else blue
            Box(Modifier.height(height).widthIn(min = height * 1.35f).clip(RoundedCornerShape(50)).background(col).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (height.value * 0.48f).sp, maxLines = 1)
            }
        }
    }
}

/** "What the Wii gets": buttons in grey, moves in lavender with their arrow. */
@Composable
private fun WiiGetsText(target: io.github.hiramrg.delphis.core.Target?, size: androidx.compose.ui.unit.TextUnit) {
    val c = LocalWii.current
    val mc = motionColor()
    val parts = PresetEdits.describe(target)
    Text(
        buildAnnotatedString {
            if (parts.isEmpty()) withStyle(SpanStyle(color = c.textDim)) { append("Nothing") }
            parts.forEachIndexed { i, p ->
                if (i > 0) withStyle(SpanStyle(color = c.textDim)) { append(" + ") }
                withStyle(SpanStyle(color = if (p.motion) mc else c.textDim, fontWeight = if (p.motion) FontWeight.ExtraBold else FontWeight.SemiBold)) { append(p.text) }
            }
        },
        fontSize = size, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = LocalWii.current.textDim, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 2.dp))
}

/** One action: badge · name · what the Wii gets. Same piece on both screens (compact on the bottom one). */
@Composable
private fun ActionRow(control: ThorControl, m: Mapping?, selected: Boolean, compact: Boolean = false, tail: String?, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().focusRing(shape).clip(shape)
            .background(if (selected) (if (c.dark) SelFill else c.accentSoft) else (if (c.dark) Color(0xFF20252B) else c.card))
            .border(2.dp, if (selected) SelBorder else c.cardBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 3.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MapBadge(control, if (compact) 18.dp else 22.dp)
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        Column(Modifier.weight(1f)) {
            Text(m?.let { PresetEdits.actionName(it) } ?: controlName(control), fontSize = if (compact) 11.sp else 12.5.sp, fontWeight = FontWeight.ExtraBold,
                color = if (m == null) c.textDim else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WiiGetsText(m?.target, if (compact) 9.5.sp else 10.5.sp)
        }
        if (tail != null) Text(tail, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Top screen: "Press the button for Block · now on X". Tap it to start / stop listening. */
@Composable
private fun AssignBox(s: EditorState, listening: Boolean, canPress: Boolean, onTap: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(14.dp)
    val sel = s.selected ?: return
    val directional = sel.kind == ThorControl.Kind.DIRECTIONAL || sel == GYRO
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (c.dark) PressFill else c.accentSoft)
            .dashedBorder(if (listening) SelBorder else SelBorder.copy(alpha = 0.6f), shape)
            .clickable(enabled = canPress && !directional, onClick = onTap).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (directional) {
            Text("Move ${s.selectedName} to", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.editable.filter { it != sel && PresetEdits.compatible(sel, it) }.forEach { to ->
                    Box(Modifier.clip(RoundedCornerShape(50)).clickable { s.move(to) }.padding(2.dp)) { MapBadge(to, 22.dp) }
                }
                if (s.editable.none { it != sel && PresetEdits.compatible(sel, it) }) Text("nowhere else", fontSize = 11.sp, color = c.textDim)
            }
        } else {
            Text(if (listening) "Press the button for ${s.selectedName}" else "${s.selectedName}: tap here, then press a Thor button",
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(if (s.mappingOn(sel) != null) "now on " else "not used yet · ", fontSize = 11.sp, color = c.textDim)
                MapBadge(sel, 18.dp)
                if (s.mappingOn(sel) != null) Text("  ·  ✕ clear", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, modifier = Modifier.clickable(onClick = s.clear))
            }
        }
    }
}

/** Bottom screen: the game owns the Thor's buttons, so the new one is tapped from a strip. */
@Composable
private fun TapAssignBox(s: EditorState) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    val sel = s.selected ?: return
    Column(Modifier.fillMaxWidth().clip(shape).background(if (c.dark) PressFill else c.accentSoft).dashedBorder(SelBorder, shape).padding(horizontal = 8.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tap the Thor button for ${s.selectedName}", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.mappingOn(sel) != null) Text("✕ clear", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, modifier = Modifier.clickable(onClick = s.clear))
        }
        Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            s.editable.filter { PresetEdits.compatible(sel, it) }.forEach { to ->
                val on = to == sel
                Box(
                    Modifier.clip(RoundedCornerShape(50)).border(2.dp, if (on) SelBorder else Color.Transparent, RoundedCornerShape(50))
                        .clickable { s.move(to) }.padding(3.dp),
                ) { MapBadge(to, 20.dp) }
            }
        }
    }
}

/** "What X does in the game": labelled rows of tiles, no tabs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerPanel(s: EditorState, modifier: Modifier, tileW: Dp, tileH: Dp, framed: Boolean = true) {
    val c = LocalWii.current
    val sel = s.selected ?: return
    val sections = remember(s.base.controller, sel) { PresetEdits.sections(s.base.controller, sel) }
    val current = s.mappingOn(sel)?.target
    val shape = RoundedCornerShape(14.dp)
    Column(
        (if (framed) modifier.clip(shape).background(if (c.dark) Color(0xFF1B1F24) else c.card).border(2.dp, c.cardBorder, shape).padding(8.dp) else modifier)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("What ${s.selectedName} does in the game", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
        sections.forEach { sec ->
            SectionLabel(sec.title)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sec.tiles.forEach { t -> Tile(t, ActionCatalog.sameTarget(t.target, current), tileW, tileH) { s.pick(t) } }
            }
        }
    }
}

@Composable
private fun Tile(t: PresetEdits.Tile, on: Boolean, w: Dp, h: Dp, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(9.dp)
    Column(
        Modifier.size(w, h).focusRing(shape).clip(shape)
            .background(if (on) (if (c.dark) SelFill else c.accentSoft) else (if (c.dark) Color(0xFF20252B) else c.bg))
            .border(2.dp, if (on) SelBorder else c.cardBorder, shape).clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(t.glyph, fontSize = if (t.small.isEmpty()) 14.sp else 12.sp, fontWeight = FontWeight.ExtraBold, color = if (t.motion) motionColor() else c.text, lineHeight = 13.sp, maxLines = 1)
        if (t.small.isNotEmpty()) Text(t.small, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = if (on) c.accentText else c.textDim, lineHeight = 8.sp, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Text(
        text, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = if (primary) Color(0xFF0A2533) else c.text, maxLines = 1,
        modifier = Modifier.clip(shape).background(if (primary) c.accent.copy(alpha = if (enabled) 1f else 0.4f) else Color.Transparent)
            .border(2.dp, if (primary) c.accent.copy(alpha = if (enabled) 1f else 0.4f) else c.cardBorder, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private fun Modifier.dashedBorder(color: Color, shape: RoundedCornerShape): Modifier = drawBehind {
    val stroke = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx())))
    drawOutline(shape.createOutline(size, layoutDirection, this), color, style = stroke)
}
