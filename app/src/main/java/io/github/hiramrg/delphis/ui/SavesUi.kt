package io.github.hiramrg.delphis.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.github.hiramrg.delphis.core.KeptSaves
import io.github.hiramrg.delphis.platform.DolphinBridge.KeepResult
import io.github.hiramrg.delphis.platform.DolphinBridge.SaveEntry
import io.github.hiramrg.delphis.platform.DolphinBridge.SaveKind
import kotlinx.coroutines.launch

/** "Latest" / "Earlier" / "Slot 3" / the kept save's name. */
fun saveLabel(e: SaveEntry): String = when (e.kind) {
    SaveKind.KEPT -> e.name ?: "Kept save"
    SaveKind.LATEST -> "Latest"
    SaveKind.EARLIER -> if (e.beforeLoading) "Before loading" else "Earlier"
    SaveKind.SLOT -> "Slot ${e.slot}"
}

/** How long X is held on a kept save to delete it. */
const val DELETE_HOLD_MS = 1000L // (no longer used for deleting: Delete is in the save pop-up)

/** A save's picture, or a plain tile with ↺ when it has none (Dolphin's own slots, Android 10). */
@Composable
fun SavePicture(file: java.io.File?, modifier: Modifier = Modifier) {
    val c = LocalWii.current
    val img by produceState<ImageBitmap?>(null, file?.path) {
        value = file?.let { f ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
        }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(if (c.dark) Color(0xFF1D2127) else c.bg), contentAlignment = Alignment.Center) {
        val shown = img
        if (shown != null) Image(shown, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text("↺", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim)
    }
}

/** The kept saves' pin. */
@Composable
fun PinMark(size: Dp = 13.dp) {
    val gold = Color(0xFFF2B544)
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawCircle(gold, radius = w * 0.26f, center = Offset(w * 0.5f, w * 0.3f))
        val p = Path().apply { moveTo(w * 0.3f, w * 0.42f); lineTo(w * 0.7f, w * 0.42f); lineTo(w * 0.5f, w * 0.68f); close() }
        drawPath(p, gold)
        drawLine(gold, Offset(w * 0.5f, w * 0.62f), Offset(w * 0.5f, w * 0.98f), strokeWidth = w * 0.11f)
    }
}

/**
 * The Saves list: saves you kept, then the autosaves (latest, then the earlier ones Delphis keeps), then Dolphin's
 * own save slots, each with its picture. [cursor] is the highlighted row (the D-pad moves it, the caller handles the
 * keys); a tap picks a row, a long press [onLongPress] (keep / rename). [latestTag] is shown beside the latest
 * autosave ("Next Play" on the game page).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavesList(
    entries: List<SaveEntry>, cursor: Int, onTap: (Int) -> Unit, modifier: Modifier = Modifier,
    latestTag: String? = null, rowHeight: Dp = 34.dp, onLongPress: ((Int) -> Unit)? = null, small: Boolean = false,
) {
    val c = LocalWii.current
    // Rows with their group titles in between; a title is a null entry.
    val items = buildList<Pair<String?, Int>> {
        val kept = entries.indices.filter { entries[it].kind == SaveKind.KEPT }
        val autos = entries.indices.filter { entries[it].kind == SaveKind.LATEST || entries[it].kind == SaveKind.EARLIER }
        val slots = entries.indices.filter { entries[it].kind == SaveKind.SLOT }
        if (kept.isNotEmpty()) { add("Kept" to -1); kept.forEach { add(null to it) } }
        if (autos.isNotEmpty()) { add("Autosaves" to -1); autos.forEach { add(null to it) } }
        if (slots.isNotEmpty()) { add("Dolphin save slots" to -1); slots.forEach { add(null to it) } }
    }
    val state = rememberLazyListState()
    LaunchedEffect(cursor) {
        val at = items.indexOfFirst { it.second == cursor }
        if (at < 0) return@LaunchedEffect
        // Keep the row (and its group title right above) in view.
        val first = state.firstVisibleItemIndex
        val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first
        if (at <= first) state.animateScrollToItem((at - 1).coerceAtLeast(0))
        else if (at >= last) state.animateScrollToItem((at - (last - first) + 1).coerceAtLeast(0))
    }
    val thumbW = if (small) 52.dp else 60.dp
    val thumbH = if (small) 29.dp else 34.dp
    LazyColumn(modifier, state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(items.size) { i ->
            val (title, index) = items[i]
            if (title != null) {
                CapsLabel(title, Modifier.padding(start = 4.dp, top = if (i == 0) 0.dp else 4.dp))
                return@items
            }
            val e = entries[index]
            val selected = index == cursor
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.fillMaxWidth().heightIn(min = rowHeight).clip(shape)
                    .background(if (selected) c.accentSoft else c.card.copy(alpha = 0.6f))
                    .border(if (selected) 3.dp else 1.dp, if (selected) c.accent else c.cardBorder, shape)
                    .combinedClickable(onLongClick = onLongPress?.let { f -> { f(index) } }) { onTap(index) }
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SavePicture(e.picture, Modifier.size(thumbW, thumbH))
                Spacer(Modifier.width(10.dp))
                if (e.kind == SaveKind.KEPT) {
                    // The name gets the whole width (two lines at most), the time goes under it.
                    PinMark()
                    Spacer(Modifier.width(5.dp))
                    Column(Modifier.weight(1f)) {
                        Text(saveLabel(e), fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 2, lineHeight = 16.sp)
                        Text(formatSaveTime(e.savedAt), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.textDim, maxLines = 1, lineHeight = 13.sp)
                    }
                    if (e.current) Text("Loaded", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (c.dark) Color(0xFF5BD08A) else Color(0xFF2E9E57), maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text(formatSaveTime(e.savedAt), fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(saveLabel(e), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = c.textDim, maxLines = 1)
                    if (e.loaded || e.current) {
                        Text("Loaded", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (c.dark) Color(0xFF5BD08A) else Color(0xFF2E9E57), maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    } else if (e.isLatest && latestTag != null) {
                        Text(latestTag, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (c.dark) Color(0xFF5BD08A) else Color(0xFF2E9E57), maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** A ring drawn when [on] (the controller's cursor inside a pop-up), like the focus ring: breathing, with a gap. */
fun Modifier.litRing(on: Boolean, shape: androidx.compose.ui.graphics.Shape, width: Dp = 3.dp): Modifier = composed {
    if (!on) return@composed this
    val ring = breathingAccent()
    val gap = LocalWii.current.bg
    this.drawWithContent {
        drawContent()
        val w = width.toPx(); val g = 2.5.dp.toPx()
        fun stroke(inset: Float, sw: Float, col: Color) {
            val sz = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
            if (sz.width <= 0f || sz.height <= 0f) return
            val outline = shape.createOutline(sz, layoutDirection, this)
            translate(inset, inset) { drawOutline(outline, col, style = androidx.compose.ui.graphics.drawscope.Stroke(sw)) }
        }
        stroke(w / 2f, w, ring)
        stroke(w + g / 2f, g, gap)
    }
}

/** The green shield before "Safe to load". */
@Composable
fun ShieldMark(size: Dp = 16.dp) {
    val green = Color(0xFF3FBF6F)
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val p = Path().apply {
            moveTo(w * 0.5f, w * 0.04f); lineTo(w * 0.88f, w * 0.18f); lineTo(w * 0.88f, w * 0.46f)
            cubicTo(w * 0.88f, w * 0.72f, w * 0.72f, w * 0.88f, w * 0.5f, w * 0.97f)
            cubicTo(w * 0.28f, w * 0.88f, w * 0.12f, w * 0.72f, w * 0.12f, w * 0.46f)
            lineTo(w * 0.12f, w * 0.18f); close()
        }
        drawPath(p, green)
        val tick = Path().apply { moveTo(w * 0.32f, w * 0.5f); lineTo(w * 0.45f, w * 0.63f); lineTo(w * 0.68f, w * 0.36f) }
        drawPath(tick, Color(0xFF0A2533), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.1f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

/** The line above the Saves buttons: "Safe to load: where you are now is saved first.", or [status] for a few seconds. */
@Composable
fun SafeToLoadLine(status: Pair<String, Boolean>?, modifier: Modifier = Modifier) {
    val c = LocalWii.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (status == null) {
            ShieldMark()
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold, color = c.text)); append("Safe to load:"); pop()
                    append(" where you are now is saved first.")
                },
                fontSize = 11.5.sp, lineHeight = 14.sp, maxLines = 2, color = c.textDim,
            )
        } else Text(status.first, fontSize = 11.5.sp, lineHeight = 14.sp, maxLines = 2, fontWeight = FontWeight.Bold, color = if (status.second) c.orange else c.text)
    }
}

/** What the Saves lists can do with a save besides playing it (run off the main thread by them). */
class SaveActions(
    val keep: suspend (SaveEntry, String) -> KeepResult,
    val delete: suspend (SaveEntry) -> Boolean,
)

/**
 * Naming a save in Delphis' own keyboard (Android's keyboard can't open on the Thor's bottom screen): the name,
 * suggestions (your names for this game first, then ones for any game), three rows of keys and the buttons.
 * [row] / [col] is the controller's ring: row 0 suggestions, 1–3 keys, 4 buttons.
 */
class NameState(val entry: SaveEntry, yourNames: List<String>) {
    val renaming get() = entry.kind == SaveKind.KEPT
    /** (name, it's one of yours). */
    val suggestions: List<Pair<String, Boolean>> = run {
        val mine = yourNames.map { KeptSaves.cleanName(it) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(6)
        mine.map { it to true } + KeptSaves.SUGGESTIONS.filter { s -> mine.none { it.equals(s, ignoreCase = true) } }.map { it to false }
    }
    var name by mutableStateOf(entry.name.orEmpty())
    var shift by mutableStateOf(name.isEmpty())
    var numbers by mutableStateOf(false)
    var row by mutableIntStateOf(if (renaming || suggestions.isEmpty()) 4 else 0)
    var col by mutableIntStateOf(if (renaming || suggestions.isEmpty()) 3 else 0)

    fun keys(r: Int): List<String> = when (r) {
        1 -> (if (numbers) "1234567890" else "QWERTYUIOP").map { it.toString() }
        2 -> (if (numbers) "-'!?&.,:#" else "ASDFGHJKL").map { it.toString() } + BACKSPACE
        3 -> (if (numbers) "()/+@*\"" else "ZXCVBNM").map { it.toString() } + SPACE
        else -> emptyList()
    }
    private fun size(r: Int) = when (r) { 0 -> suggestions.size; 4 -> 4; else -> keys(r).size }

    /** The D-pad: [dr] rows, [dc] columns (moving between rows keeps about the same place across). */
    fun move(dr: Int, dc: Int) {
        if (dc != 0) { col = (col + dc).coerceIn(0, size(row) - 1); return }
        var nr = row + dr
        if (nr == 0 && suggestions.isEmpty()) nr += dr
        if (nr !in 0..4) return
        val frac = if (size(row) > 1) col.toFloat() / (size(row) - 1) else 0f
        row = nr
        col = kotlin.math.round(frac * (size(nr) - 1)).toInt().coerceIn(0, size(nr) - 1)
    }

    fun press(label: String) = when (label) {
        BACKSPACE -> backspace()
        SPACE -> type(' ')
        else -> label.forEach { type(it) }
    }

    fun type(ch: Char) {
        if (name.length >= KeptSaves.NAME_MAX) return
        if (ch == ' ' && (name.isEmpty() || name.endsWith(' '))) return
        name += if (ch.isLetter()) (if (shift) ch.uppercaseChar() else ch.lowercaseChar()) else ch
        if (ch.isLetter()) shift = false
    }

    fun backspace() { name = name.dropLast(1); if (name.isEmpty()) shift = true }

    fun pick(i: Int) { suggestions.getOrNull(i)?.let { name = it.first; shift = false } }

    /** What a key shows (letters follow Shift). */
    fun shown(label: String) = if (label.length == 1 && label[0].isLetter()) (if (shift) label.uppercase() else label.lowercase()) else label

    /** The name to keep it under: what was typed, or "Kept save" if that's empty. */
    val finalName get() = KeptSaves.cleanName(name).ifEmpty { "Kept save" }

    companion object { const val BACKSPACE = "⌫"; const val SPACE = "Space" }
}

/**
 * The save pop-up and what comes after it: tap (or A on) a save → its pop-up (Load, Keep / Rename, Delete for a
 * kept save, Back) → the delete check or the naming keyboard. Shared by the in-game Saves tab and the game page,
 * so touch and buttons work the same way everywhere. [load] plays / loads the save; [done] reports what happened.
 */
class SaveFlow(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val actions: SaveActions,
    private val yourNames: () -> List<String>,
    private val load: (SaveEntry) -> Unit,
    private val done: (message: String, problem: Boolean) -> Unit,
) {
    enum class Step { SAVE, DELETE, NAME }
    enum class Btn { DELETE, BACK, KEEP, LOAD }

    var entry by mutableStateOf<SaveEntry?>(null); private set
    var step by mutableStateOf(Step.SAVE); private set
    /** The controller's ring on the pop-up's buttons. */
    var ring by mutableIntStateOf(0)
    var naming by mutableStateOf<NameState?>(null); private set
    var busy by mutableStateOf(false); private set
    var problem by mutableStateOf<String?>(null)
    val open get() = entry != null

    fun buttons(e: SaveEntry): List<Btn> = buildList { if (e.kind == SaveKind.KEPT) add(Btn.DELETE); add(Btn.BACK); add(Btn.KEEP); add(Btn.LOAD) }

    fun show(e: SaveEntry) { entry = e; step = Step.SAVE; naming = null; problem = null; ring = buttons(e).indexOf(Btn.LOAD) }
    fun close() { entry = null; naming = null; step = Step.SAVE; problem = null }

    /** B: one step back (naming / delete check → the save pop-up → closed). */
    fun back() {
        val e = entry ?: return
        when (step) {
            Step.SAVE -> close()
            Step.DELETE -> { step = Step.SAVE; ring = buttons(e).indexOf(Btn.DELETE) }
            Step.NAME -> { step = Step.SAVE; naming = null; problem = null; ring = buttons(e).indexOf(Btn.KEEP) }
        }
    }

    fun press(b: Btn) {
        val e = entry ?: return
        if (busy) return
        when (b) {
            Btn.DELETE -> { step = Step.DELETE; ring = 0 }
            Btn.BACK -> close()
            Btn.KEEP -> { naming = NameState(e, runCatching(yourNames).getOrDefault(emptyList())); problem = null; step = Step.NAME }
            Btn.LOAD -> { close(); load(e) }
        }
    }

    fun confirmDelete() {
        val e = entry ?: return
        if (busy) return
        busy = true
        scope.launch {
            val ok = runCatching { actions.delete(e) }.getOrDefault(false)
            busy = false
            close()
            done(if (ok) "Deleted “${saveLabel(e)}”." else "Couldn't delete it.", !ok)
        }
    }

    fun keep() {
        val n = naming ?: return
        val e = entry ?: return
        if (busy) return
        busy = true
        scope.launch {
            val r = runCatching { actions.keep(e, n.finalName) }.getOrDefault(KeepResult.FAILED)
            busy = false
            if (r == KeepResult.KEPT) {
                close()
                done(if (n.renaming) "Renamed to “${n.finalName}”." else "Kept as “${n.finalName}”. New autosaves never replace it.", false)
            } else problem = if (r == KeepResult.FULL) "You already keep ${KeptSaves.MAX} saves for this game. Delete one first." else "Couldn't keep it. Nothing was changed."
        }
    }

    /** The naming panel's buttons: Cancel · 123 / ABC · Aa · Keep. */
    fun nameButton(i: Int) {
        val n = naming ?: return
        when (i) { 0 -> back(); 1 -> n.numbers = !n.numbers; 2 -> n.shift = !n.shift; else -> keep() }
    }

    /** A controller button while the pop-up is open; true when it was used. */
    fun key(code: Int): Boolean {
        val e = entry ?: return false
        when (step) {
            Step.SAVE -> {
                val bs = buttons(e)
                when (code) {
                    AndroidKey.KEYCODE_DPAD_LEFT -> ring = (ring - 1).coerceAtLeast(0)
                    AndroidKey.KEYCODE_DPAD_RIGHT -> ring = (ring + 1).coerceAtMost(bs.lastIndex)
                    AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER -> press(bs[ring.coerceIn(0, bs.lastIndex)])
                    AndroidKey.KEYCODE_BUTTON_START -> press(Btn.LOAD)
                    AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BACK -> close()
                    AndroidKey.KEYCODE_BUTTON_Y -> press(Btn.KEEP)
                    AndroidKey.KEYCODE_BUTTON_X -> if (e.kind == SaveKind.KEPT) press(Btn.DELETE)
                    AndroidKey.KEYCODE_DPAD_UP, AndroidKey.KEYCODE_DPAD_DOWN -> {}
                    else -> return false
                }
            }
            Step.DELETE -> when (code) {
                AndroidKey.KEYCODE_DPAD_LEFT -> ring = 0
                AndroidKey.KEYCODE_DPAD_RIGHT -> ring = 1
                AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER -> if (ring == 1) confirmDelete() else back()
                AndroidKey.KEYCODE_BUTTON_X -> confirmDelete()
                AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BACK -> back()
                AndroidKey.KEYCODE_DPAD_UP, AndroidKey.KEYCODE_DPAD_DOWN -> {}
                else -> return false
            }
            Step.NAME -> {
                val n = naming ?: return false
                when (code) {
                    AndroidKey.KEYCODE_DPAD_UP -> n.move(-1, 0)
                    AndroidKey.KEYCODE_DPAD_DOWN -> n.move(1, 0)
                    AndroidKey.KEYCODE_DPAD_LEFT -> n.move(0, -1)
                    AndroidKey.KEYCODE_DPAD_RIGHT -> n.move(0, 1)
                    AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER -> when (n.row) {
                        0 -> n.pick(n.col)
                        4 -> nameButton(n.col)
                        else -> n.keys(n.row).getOrNull(n.col)?.let { n.press(it) }
                    }
                    AndroidKey.KEYCODE_BUTTON_X -> n.backspace()
                    AndroidKey.KEYCODE_BUTTON_Y -> n.type(' ')
                    AndroidKey.KEYCODE_BUTTON_START -> keep()
                    AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BACK -> back()
                    else -> return false
                }
            }
        }
        return true
    }

    /** Every controller button the pop-up answers, for the in-game panel's key map. */
    val keyCodes = listOf(
        AndroidKey.KEYCODE_DPAD_UP, AndroidKey.KEYCODE_DPAD_DOWN, AndroidKey.KEYCODE_DPAD_LEFT, AndroidKey.KEYCODE_DPAD_RIGHT,
        AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BUTTON_X, AndroidKey.KEYCODE_BUTTON_Y, AndroidKey.KEYCODE_BUTTON_START,
    )
}

/** "Autosave · latest" / "Kept save" / "Dolphin save slot 3". */
private fun saveCaps(e: SaveEntry) = when (e.kind) {
    SaveKind.KEPT -> "Kept save"
    SaveKind.LATEST -> "Autosave · latest"
    SaveKind.EARLIER -> if (e.beforeLoading) "Autosave · before loading" else "Autosave · earlier"
    SaveKind.SLOT -> "Dolphin save slot ${e.slot}"
}

private fun saveAbout(e: SaveEntry) = when (e.kind) {
    SaveKind.KEPT -> "Stays until you delete it."
    SaveKind.LATEST -> "Your latest autosave. New autosaves replace it, unless you keep it."
    SaveKind.EARLIER -> if (e.beforeLoading) "Where you were just before you loaded a save. Keep it to hold on to it." else "Delphis keeps the 3 newest earlier autosaves. Keep it to hold on to it."
    SaveKind.SLOT -> "Made in Dolphin's own menu."
}

/**
 * Whichever panel [flow] is on: the save pop-up, the delete check or the naming keyboard. [keys]: a controller is
 * in use (button icons and the ring). [loadLabel]: "Load" in game, "Play from here" on the game page.
 */
@Composable
fun SaveFlowPanel(flow: SaveFlow, keys: Boolean, loadLabel: String, modifier: Modifier = Modifier) {
    val e = flow.entry ?: return
    when (flow.step) {
        SaveFlow.Step.SAVE -> SavePopup(flow, e, keys, loadLabel, modifier)
        SaveFlow.Step.DELETE -> DeleteCheck(flow, e, keys, modifier)
        SaveFlow.Step.NAME -> flow.naming?.let { NamePanel(flow, it, keys, modifier) }
    }
}

@Composable
private fun SavePopup(flow: SaveFlow, e: SaveEntry, keys: Boolean, loadLabel: String, modifier: Modifier) {
    val c = LocalWii.current
    val bs = flow.buttons(e)
    // A kept save's Delete sits apart, in the top corner (the ring reaches it first, going left).
    val del = bs.indexOf(SaveFlow.Btn.DELETE)
    PopupPanel(
        saveCaps(e), if (e.kind == SaveKind.KEPT) saveLabel(e) else formatSaveTime(e.savedAt), modifier.widthIn(max = 480.dp),
        corner = if (del < 0) null else ({
            PanelBtn("Delete", if (keys) "X" else null, false, Modifier.litRing(keys && flow.ring == del, ButtonShape), warn = true, enabled = !flow.busy) { flow.press(SaveFlow.Btn.DELETE) }
        }),
        buttons = {
            bs.forEachIndexed { i, b ->
                val lit = keys && flow.ring == i
                val m = Modifier.litRing(lit, ButtonShape)
                when (b) {
                    SaveFlow.Btn.DELETE -> {}
                    SaveFlow.Btn.BACK -> PanelBtn(if (keys) "Back" else "Cancel", if (keys) "B" else null, false, m) { flow.press(b) }
                    SaveFlow.Btn.KEEP -> PanelBtn(if (e.kind == SaveKind.KEPT) "Rename" else "Keep", if (keys) "Y" else null, false, m) { flow.press(b) }
                    SaveFlow.Btn.LOAD -> PanelBtn("▶ $loadLabel", if (keys) "A" else null, true, m) { flow.press(b) }
                }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SavePicture(e.picture, Modifier.size(144.dp, 81.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (e.kind == SaveKind.KEPT) Text(formatSaveTime(e.savedAt), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1)
                Text(saveAbout(e), fontSize = 12.sp, lineHeight = 15.sp, color = c.textDim)
            }
        }
    }
}

@Composable
private fun DeleteCheck(flow: SaveFlow, e: SaveEntry, keys: Boolean, modifier: Modifier) {
    val c = LocalWii.current
    PopupPanel(
        "Delete kept save", "Delete “${saveLabel(e)}”?", modifier.widthIn(max = 440.dp),
        buttons = {
            PanelBtn("Cancel", if (keys) "B" else null, false, Modifier.litRing(keys && flow.ring == 0, ButtonShape)) { flow.back() }
            PanelBtn("Delete", if (keys) "X" else null, false, Modifier.litRing(keys && flow.ring == 1, ButtonShape), warn = true, enabled = !flow.busy) { flow.confirmDelete() }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SavePicture(e.picture, Modifier.size(96.dp, 54.dp))
            Text("This can't be undone. Your autosaves aren't touched.", fontSize = 12.5.sp, lineHeight = 16.sp, color = c.textDim, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun NamePanel(flow: SaveFlow, n: NameState, keys: Boolean, modifier: Modifier) {
    val c = LocalWii.current
    PopupPanel(
        null, if (n.renaming) "Rename kept save" else "Keep this save", modifier.widthIn(max = 460.dp),
        buttons = {
            val labels = listOf("Cancel", if (n.numbers) "ABC" else "123", "Aa", if (n.renaming) "Save" else "Keep")
            labels.forEachIndexed { i, l ->
                val key = if (!keys) null else when (i) { 0 -> "B"; 3 -> "⊕"; else -> null }
                PanelBtn(l, key, i == 3, Modifier.litRing(keys && n.row == 4 && n.col == i, ButtonShape), enabled = i != 3 || !flow.busy) { flow.nameButton(i) }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SavePicture(n.entry.picture, Modifier.size(70.dp, 39.dp))
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.weight(1f).height(40.dp).clip(shape).background(c.accentSoft).border(2.dp, c.accent, shape).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (n.name.isEmpty()) Text("Type a name, or pick one below", fontSize = 13.sp, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                else Text(n.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Box(Modifier.padding(start = 2.dp).width(2.dp).height(20.dp).background(c.accent))
            }
        }
        if (n.suggestions.isNotEmpty()) {
            val chips = rememberLazyListState()
            LaunchedEffect(n.row, n.col) { if (n.row == 0) runCatching { chips.animateScrollToItem((n.col - 1).coerceAtLeast(0)) } }
            LazyRow(state = chips, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(n.suggestions.size) { i ->
                    val (t, mine) = n.suggestions[i]
                    NameChip(t, mine, picked = n.name == t, lit = keys && n.row == 0 && n.col == i) { n.pick(i) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (r in 1..3) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                n.keys(r).forEachIndexed { i, k ->
                    val w = if (k == NameState.SPACE) 3f else 1f
                    KeyCap(n.shown(k), lit = keys && n.row == r && n.col == i, Modifier.weight(w)) { n.press(k) }
                }
            }
        }
        flow.problem?.let { Text(it, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = c.orange) }
    }
}

@Composable
private fun NameChip(text: String, mine: Boolean, picked: Boolean, lit: Boolean, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(32.dp).litRing(lit, shape).clip(shape)
            .then(if (picked) Modifier.background(c.accent) else Modifier.background(c.card).border(2.dp, c.cardBorder, shape))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val fg = if (picked) c.onAccent else c.text
        if (mine) PersonMark(fg)
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1)
    }
}

/** Marks a suggestion as a name you used before. */
@Composable
private fun PersonMark(color: Color) {
    Canvas(Modifier.size(11.dp)) {
        val w = size.width
        drawCircle(color, radius = w * 0.24f, center = Offset(w * 0.5f, w * 0.28f))
        drawArc(color, 180f, 180f, true, topLeft = Offset(w * 0.08f, w * 0.58f), size = androidx.compose.ui.geometry.Size(w * 0.84f, w * 0.84f))
    }
}

@Composable
private fun KeyCap(label: String, lit: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier.height(30.dp).litRing(lit, shape).clip(shape).background(if (c.dark) Color(0xFF353C45) else c.bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1) }
}

/**
 * Game page: "Continue from which save?". D-pad chooses, A (or a tap) opens the save's pop-up: Play from here,
 * Keep / Rename, Delete for a kept save. B closes. Playing from an earlier save, a kept one or a slot puts it in
 * the Quick Save slot first; the save that was there is kept, so you can switch back.
 */
@Composable
fun SavesDialog(
    gameTitle: String?, entries: List<SaveEntry>, onDismiss: () -> Unit,
    actions: SaveActions? = null, yourNames: () -> List<String> = { emptyList() }, onChanged: () -> Unit = {},
    screenshots: Int = 0, onScreenshots: (() -> Unit)? = null, onPlay: (SaveEntry) -> Unit,
) {
    val c = LocalWii.current
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val keys = !PadGlyphs.touch
    // Start on the save Play continues from.
    var index by remember { mutableIntStateOf(entries.indexOfFirst { it.isLatest }.coerceAtLeast(0)) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val flow = remember(actions) {
        SaveFlow(scope, actions ?: SaveActions({ _, _ -> KeepResult.FAILED }, { false }), yourNames, onPlay) { msg, bad -> status = msg to bad; index = 0; onChanged() }
    }
    LaunchedEffect(Unit) { testOpen(flow, entries) }
    val current = entries.getOrNull(index.coerceIn(0, (entries.size - 1).coerceAtLeast(0)))

    ScaledDialog(onDismissRequest = { if (flow.open) flow.back() else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val code = e.key.nativeKeyCode
                    if (flow.open) return@onPreviewKeyEvent flow.key(code) || true
                    when (code) {
                        AndroidKey.KEYCODE_DPAD_DOWN -> { index = (index + 1).coerceAtMost(entries.lastIndex); true }
                        AndroidKey.KEYCODE_DPAD_UP -> { index = (index - 1).coerceAtLeast(0); true }
                        AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER -> { current?.let { if (actions != null) flow.show(it) else onPlay(it) }; true }
                        AndroidKey.KEYCODE_BUTTON_START -> { current?.let(onPlay); true }
                        AndroidKey.KEYCODE_BUTTON_Y -> { if (screenshots > 0) onScreenshots?.invoke(); true }
                        AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BACK -> { onDismiss(); true }
                        else -> true
                    }
                },
        ) {
            if (flow.open) SaveFlowPanel(flow, keys, "Play from here", Modifier.width(500.dp))
            else PopupPanel(
                listOfNotNull("Saves", gameTitle).joinToString(" · "), "Continue from which save?",
                Modifier.width(500.dp),
                buttons = {
                    PanelBtn("Close", if (keys) "B" else null, false, onClick = onDismiss)
                    if (screenshots > 0 && onScreenshots != null) PanelBtn("📷 Screenshots ($screenshots)", if (keys) "Y" else null, false, onClick = onScreenshots)
                    PanelBtn("Open", if (keys) "A" else null, true) { current?.let { if (actions != null) flow.show(it) else onPlay(it) } }
                },
            ) {
                Text(
                    status?.first ?: "Pick a save to play from it, keep it or delete it. The others stay here, so you can switch back.",
                    fontSize = 12.sp, color = if (status?.second == true) c.orange else if (status != null) c.text else c.textDim, lineHeight = 15.sp, maxLines = 2,
                )
                SavesList(
                    entries, if (keys) index else -1, { i -> index = i; entries.getOrNull(i)?.let { if (actions != null) flow.show(it) else onPlay(it) } },
                    Modifier.fillMaxWidth().heightIn(max = 300.dp), latestTag = "Next Play", small = true,
                )
            }
        }
    }
    LaunchedEffect(flow.open) { runCatching { focus.requestFocus() } }
}

/** The layout check opens the pop-up (1), the delete check (2) or the naming keyboard (3) on the first save. */
fun testOpen(flow: SaveFlow, entries: List<SaveEntry>) {
    val step = io.github.hiramrg.delphis.platform.TestHooks.savePopup
    val e = entries.firstOrNull() ?: return
    if (step <= 0 || flow.open) return
    flow.show(e)
    if (step == 2 && e.kind == SaveKind.KEPT) flow.press(SaveFlow.Btn.DELETE)
    if (step == 3) flow.press(SaveFlow.Btn.KEEP)
}

/** Shown in the Saves tab when the game has none yet. */
@Composable
fun NoSavesYet(modifier: Modifier = Modifier) {
    val c = LocalWii.current
    Column(modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("No saves yet", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
        Text(
            "Save now makes one, and Delphis autosaves when you quit. Saves made in Dolphin's own menu show up here too.",
            fontSize = 12.sp, color = c.textDim, lineHeight = 16.sp,
        )
    }
}
