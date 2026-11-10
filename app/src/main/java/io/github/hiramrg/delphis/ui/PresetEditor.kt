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
        // The top screen is ~700 dp wide; the in-game bottom screen ~450 dp.
        val wide = maxWidth >= 520.dp
        val state = EditorState(
            base = base, maps = maps, editable = editable, selected = selected,
            select = { selected = it },
            move = { to -> selected?.let { from -> if (PresetEdits.compatible(from, to)) { maps = PresetEdits.move(maps, from, to); selected = to } } },
            pick = { tile -> selected?.let { maps = PresetEdits.setTarget(maps, it, tile) } },
            setCombo = { tiles, extra -> selected?.let { maps = PresetEdits.setCombo(maps, it, tiles, extra) } },
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
    val setCombo: (List<PresetEdits.Tile>, List<String>) -> Unit,
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
    var swap by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text("Editing: ${s.base.name.removeSuffix(" (Custom)")}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Left: the actions, with Cancel · Undo · Save closing the column.
                Column(Modifier.weight(0.43f).fillMaxHeight()) {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(s.used, key = { it.name }) { control ->
                            ActionRow(control, s.mappingOn(control), control == s.selected, tail = if (control == s.selected) "editing" else "›") { s.select(control) }
                        }
                        if (s.free.isNotEmpty()) {
                            item { SectionLabel("NOT USED · pick one to give it an action") }
                            items(s.free, key = { "free-" + it.name }) { control ->
                                ActionRow(control, null, control == s.selected, tail = if (control == s.selected) "editing" else "＋") { s.select(control) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WiiButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        WiiButton("↶ Undo", onClick = s.undo, modifier = Modifier.weight(1f), enabled = s.changed, fontSize = 13.sp)
                        PrimaryGlowButton(saveLabel, androidx.compose.material.icons.Icons.Filled.Save, onClick = onSave, modifier = Modifier.weight(1.3f), enabled = s.changed, fontSize = 13.sp)
                    }
                }
                // Right: the picker, as big as it can be (it scrolls).
                PickerPanel(s, Modifier.weight(0.57f).fillMaxHeight(), tileW = 54.dp, tileH = 50.dp, onSwap = { swap = true })
            }
        }
        if (swap) SwapOverlay(s) { swap = false }
    }
}

// ============================================================================ in-game bottom screen

@Composable
private fun BottomEditor(s: EditorState, saveLabel: String, onCancel: () -> Unit, onSave: () -> Unit) {
    val c = LocalWii.current
    var sheet by remember { mutableStateOf(false) }
    var swap by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                Text("Edit controls · ${s.base.name.removeSuffix(" (Custom)")}", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 5.dp))
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
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.88f).clip(shape)
                        .background(if (c.dark) Color(0xFF1B1F24) else c.card).border(2.dp, SelBorder, shape).padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MapBadge(s.selected, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.selectedName, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            WiiGetsText(s.mappingOn(s.selected)?.target, 11.sp)
                        }
                        Pill("Done", onClick = { sheet = false })
                    }
                    Spacer(Modifier.height(6.dp))
                    PickerPanel(s, Modifier.weight(1f).fillMaxWidth(), tileW = 50.dp, tileH = 46.dp, framed = false, onSwap = { swap = true })
                }
            }
            if (swap) SwapOverlay(s) { swap = false }
        }
        // Sticky footer.
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Cancel", onClick = onCancel, big = true)
            Pill("↶ Undo", onClick = s.undo, enabled = s.changed, big = true)
            Spacer(Modifier.weight(1f))
            Pill(saveLabel, onClick = onSave, primary = true, enabled = s.changed, big = true)
        }
    }
}

/** ⇄ Swap: the Thor's other controls of the same kind, with what each does now; picking one trades the two actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwapOverlay(s: EditorState, onClose: () -> Unit) {
    val c = LocalWii.current
    val sel = s.selected ?: return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier.fillMaxWidth(0.9f).clip(shape).background(if (c.dark) Color(0xFF1B1F24) else c.card).border(2.dp, SelBorder, shape)
                .clickable(enabled = false) {}.padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Swap ${s.selectedName} with…", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Pill("✕", onClick = onClose)
            }
            Text("The two actions trade buttons.", fontSize = 11.sp, color = c.textDim, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.editable.filter { it != sel && PresetEdits.compatible(sel, it) }.forEach { to ->
                    val m = s.mappingOn(to)
                    val pill = RoundedCornerShape(50)
                    Row(
                        Modifier.focusRing(pill).clip(pill).background(if (c.dark) Color(0xFF20252B) else c.bg).border(2.dp, c.cardBorder, pill)
                            .clickable { s.move(to); onClose() }.padding(start = 4.dp, end = 11.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MapBadge(to, 22.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(m?.let { PresetEdits.actionName(it) } ?: "—", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (m == null) c.textDim else c.text)
                    }
                }
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



/**
 * "Choose a Wii button or move": labelled rows of tiles, no tabs. Button actions get a Single / Combo
 * switch; in Combo a row of big slots (A + ⤒ push + ＋) shows what the game gets: tap a slot, then a tile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerPanel(s: EditorState, modifier: Modifier, tileW: Dp, tileH: Dp, framed: Boolean = true, onSwap: () -> Unit) {
    val c = LocalWii.current
    val sel = s.selected ?: return
    val controller = s.base.controller
    val sections = remember(controller, sel) { PresetEdits.sections(controller, sel) }
    val current = s.mappingOn(sel)?.target
    val decomposed = PresetEdits.decompose(controller, sel, current)
    val canCombo = sel.kind == ThorControl.Kind.BUTTON && sel != GYRO
    var combo by remember(sel) { mutableStateOf(decomposed?.isCombo == true) }
    var slot by remember(sel) { mutableStateOf(0) }
    val parts = decomposed?.tiles.orEmpty()
    val extra = decomposed?.extra.orEmpty()
    val shape = RoundedCornerShape(14.dp)
    Column(
        (if (framed) modifier.clip(shape).background(if (c.dark) Color(0xFF1B1F24) else c.card).border(2.dp, c.cardBorder, shape).padding(10.dp) else modifier)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(PresetEdits.pickerTitle(controller, sel), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.text, modifier = Modifier.weight(1f))
            if (canCombo) SingleCombo(combo) { on ->
                if (!on && combo) {
                    // Back to Single: keep only the selected slot's tile.
                    parts.getOrNull(slot)?.let { s.pick(it) }
                }
                combo = on; slot = 0
            }
            Text("⇄ Swap", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText,
                modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onSwap).padding(4.dp))
        }
        if (combo && canCombo) {
            // The formula: one big slot per part, then an empty ＋ slot.
            Row(
                Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (c.dark) Color(0xFF15191D) else c.bg)
                    .padding(8.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                parts.forEachIndexed { i, t ->
                    if (i > 0) PlusSign()
                    Slot(t.glyph, t.small, t.motion, active = i == slot, w = tileW + 8.dp, h = tileH + 6.dp, onClick = { slot = i },
                        onRemove = if (parts.size > 1) ({ s.setCombo(parts.filterIndexed { j, _ -> j != i }, extra); slot = 0 }) else null)
                }
                if (parts.size < 3) {
                    if (parts.isNotEmpty()) PlusSign(dim = true)
                    Slot("＋", "add", false, active = slot >= parts.size, w = tileW + 8.dp, h = tileH + 6.dp, empty = true, onClick = { slot = parts.size })
                }
            }
            Text("Tap a slot, then a tile. Everything in the row happens together.", fontSize = 10.5.sp, color = c.textDim, modifier = Modifier.padding(top = 3.dp, start = 2.dp))
        }
        sections.forEach { sec ->
            SectionLabel(sec.title)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sec.tiles.forEach { t ->
                    val on = if (combo && canCombo) parts.getOrNull(slot)?.let { it == t } == true else ActionCatalog.sameTarget(t.target, current)
                    Tile(t, on, tileW, tileH) {
                        if (combo && canCombo) {
                            val list = parts.toMutableList()
                            if (slot < list.size) list[slot] = t else { list += t; slot = list.size - 1 }
                            s.setCombo(list, extra)
                        } else s.pick(t)
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleCombo(combo: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalWii.current
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (c.dark) Color(0xFF262C33) else c.bar).padding(2.dp)) {
        listOf(false to "Single", true to "Combo").forEach { (v, label) ->
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (combo == v) Color(0xFF0A2533) else c.textDim,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (combo == v) c.accent else Color.Transparent).clickable { onChange(v) }.padding(horizontal = 12.dp, vertical = 5.dp))
        }
    }
}

@Composable
private fun PlusSign(dim: Boolean = false) {
    Text("+", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = LocalWii.current.textDim.copy(alpha = if (dim) 0.5f else 1f), modifier = Modifier.padding(horizontal = 6.dp))
}

@Composable
private fun Slot(glyph: String, small: String, motion: Boolean, active: Boolean, w: Dp, h: Dp, empty: Boolean = false, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(14.dp)
    Box {
        Column(
            Modifier.size(w, h).focusRing(shape).clip(shape)
                .background(if (active) (if (c.dark) SelFill else c.accentSoft) else if (empty) Color.Transparent else (if (c.dark) Color(0xFF20252B) else c.card))
                .then(if (empty && !active) Modifier.dashedBorder(c.textDim, shape) else Modifier.border(if (active) 3.dp else 2.dp, if (active) SelBorder else c.cardBorder, shape))
                .clickable(onClick = onClick),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(glyph, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (motion) motionColor() else if (empty) c.textDim else c.text, lineHeight = 24.sp)
            if (small.isNotEmpty()) Text(small, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.textDim, lineHeight = 11.sp)
        }
        if (onRemove != null) Text("✕", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(18.dp).clip(CircleShape).background(Color(0xFF4A535E)).clickable(onClick = onRemove).padding(top = 1.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun Tile(t: PresetEdits.Tile, on: Boolean, w: Dp, h: Dp, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.size(w, h).focusRing(shape).clip(shape)
            .background(if (on) (if (c.dark) SelFill else c.accentSoft) else (if (c.dark) Color(0xFF20252B) else c.bg))
            .border(if (on) 3.dp else 2.dp, if (on) SelBorder else c.cardBorder, shape).clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val big = w >= 52.dp
        Text(t.glyph, fontSize = if (big) 21.sp else 19.sp, fontWeight = FontWeight.ExtraBold, color = if (t.motion) motionColor() else c.text, lineHeight = if (big) 23.sp else 21.sp, maxLines = 1)
        if (t.small.isNotEmpty()) Text(t.small, fontSize = if (big) 9.5.sp else 9.sp, fontWeight = FontWeight.Bold, color = if (on) c.accentText else c.textDim, lineHeight = 10.sp, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true, big: Boolean = false) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Text(
        text, fontSize = if (big) 13.sp else 11.5.sp, fontWeight = FontWeight.ExtraBold,
        color = (if (primary) Color(0xFF0A2533) else c.text).copy(alpha = if (enabled) 1f else 0.45f), maxLines = 1,
        modifier = Modifier.clip(shape).background(if (primary) c.accent.copy(alpha = if (enabled) 1f else 0.4f) else Color.Transparent)
            .border(2.dp, if (primary) c.accent.copy(alpha = if (enabled) 1f else 0.4f) else c.cardBorder, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = if (big) 16.dp else 10.dp, vertical = if (big) 8.dp else 5.dp),
    )
}

private fun Modifier.dashedBorder(color: Color, shape: RoundedCornerShape): Modifier = drawBehind {
    val stroke = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx())))
    drawOutline(shape.createOutline(size, layoutDirection, this), color, style = stroke)
}
