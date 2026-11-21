package io.github.hiramrg.delphis.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    SaveKind.EARLIER -> "Earlier"
    SaveKind.SLOT -> "Slot ${e.slot}"
}

/** How long X is held on a kept save to delete it. */
const val DELETE_HOLD_MS = 1000L

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
                    PinMark()
                    Spacer(Modifier.width(5.dp))
                    Text(saveLabel(e), fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 2, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(formatSaveTime(e.savedAt), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = c.textDim, maxLines = 1)
                } else {
                    Text(formatSaveTime(e.savedAt), fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(saveLabel(e), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = c.textDim, maxLines = 1)
                    if (e.kind == SaveKind.LATEST && latestTag != null) {
                        Text(latestTag, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (c.dark) Color(0xFF5BD08A) else Color(0xFF2E9E57), maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** What the "Keep this save" pop-up is showing: the save, the name being typed, the suggestion picked. */
class KeepState(val entry: SaveEntry) {
    var name by mutableStateOf(entry.name.orEmpty())
    /** The highlighted suggestion (-1 = none). */
    var chip by mutableIntStateOf(if (entry.name == null) 0 else -1)
    var deleteArmed by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    val renaming get() = entry.kind == SaveKind.KEPT
    init { if (entry.name == null) name = KeptSaves.SUGGESTIONS[0] }

    /** The D-pad: left / right through the suggestions (picking one fills in the name). */
    fun step(d: Int) {
        val n = KeptSaves.SUGGESTIONS.size
        chip = if (chip < 0) (if (d > 0) 0 else n - 1) else (chip + d).coerceIn(0, n - 1)
        name = KeptSaves.SUGGESTIONS[chip]
    }

    /** The name to keep it under: what was typed, or "Kept save" if that's empty. */
    val finalName get() = KeptSaves.cleanName(name).ifEmpty { "Kept save" }

    /** Words for a [KeepResult] that didn't work. */
    fun failed(r: KeepResult) {
        status = if (r == KeepResult.FULL) "You already keep ${KeptSaves.MAX} saves for this game. Delete one first (hold X on it, or Rename → Delete)."
        else "Couldn't keep it. Nothing was changed."
    }
}

/**
 * "Keep this save" / "Rename kept save": the picture and time, the name (X types it with the keyboard), the
 * suggestions (D-pad left / right), and for a kept save, Delete in the corner. Short enough for a phone's in-game
 * panel. Keys are handled by the caller.
 */
@Composable
fun KeepSavePanel(
    k: KeepState, keys: Boolean, field: FocusRequester, modifier: Modifier = Modifier,
    onCancel: () -> Unit, onType: () -> Unit, onKeep: () -> Unit, onDelete: () -> Unit, onTypingDone: () -> Unit = {},
) {
    val c = LocalWii.current
    val keyboard = LocalSoftwareKeyboardController.current
    PopupPanel(
        if (k.renaming) "Rename kept save" else "Keep this save", formatSaveTime(k.entry.savedAt), modifier.widthIn(max = 440.dp),
        corner = if (k.renaming) ({
            PanelBtn(if (k.deleteArmed) "Press again to delete" else "Delete", if (keys) "L2" else null, false, warn = true, enabled = !k.busy, onClick = onDelete)
        }) else null,
        buttons = {
            PanelBtn("Cancel", if (keys) "B" else null, false, onClick = onCancel)
            PanelBtn("Type", if (keys) "X" else null, false, onClick = onType)
            PanelBtn(if (k.renaming) "Save" else "Keep", if (keys) "A" else null, true, enabled = !k.busy, onClick = onKeep)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SavePicture(k.entry.picture, Modifier.size(80.dp, 45.dp))
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier.weight(1f).height(42.dp).clip(shape).background(c.accentSoft).border(2.dp, c.accent, shape).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (k.name.isEmpty()) Text("Name it, or pick one below", fontSize = 13.sp, color = c.textDim, maxLines = 1)
                BasicTextField(
                    k.name, { k.name = it.take(KeptSaves.NAME_MAX); k.chip = KeptSaves.SUGGESTIONS.indexOf(k.name) },
                    Modifier.fillMaxWidth().focusRequester(field),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); onTypingDone() }),
                )
            }
        }
        // The suggestions on one line; the highlighted one is scrolled into view.
        val chips = rememberLazyListState()
        LaunchedEffect(k.chip) { if (k.chip >= 0) runCatching { chips.animateScrollToItem(k.chip) } }
        LazyRow(state = chips, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(KeptSaves.SUGGESTIONS.size) { i -> val t = KeptSaves.SUGGESTIONS[i]; WiiChip(t, k.chip == i, { k.chip = i; k.name = t }) }
        }
        Text(
            k.status ?: "Kept saves stay at the top and new autosaves never replace them.",
            fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2,
            fontWeight = if (k.status != null) FontWeight.Bold else null, color = if (k.status != null) c.orange else c.textDim,
        )
    }
}

/** What the Saves lists can do with a save besides playing it (run off the main thread by them). */
class SaveActions(
    val keep: suspend (SaveEntry, String) -> KeepResult,
    val delete: suspend (SaveEntry) -> Boolean,
)

/**
 * Game page: "Continue from which save?". D-pad chooses, A plays from it, Y keeps it (or renames a kept one),
 * holding X deletes a kept one, B closes. Playing from an earlier save, a kept one or a slot puts it in the Quick
 * Save slot first; the save that was there is kept, so you can switch back.
 */
@Composable
fun SavesDialog(
    gameTitle: String?, entries: List<SaveEntry>, onDismiss: () -> Unit,
    actions: SaveActions? = null, onChanged: () -> Unit = {}, onPlay: (SaveEntry) -> Unit,
) {
    val c = LocalWii.current
    val focus = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    // Start on the save Play continues from.
    var index by remember { mutableIntStateOf(entries.indexOfFirst { it.kind == SaveKind.LATEST }.coerceAtLeast(0)) }
    var keep by remember { mutableStateOf<KeepState?>(if (io.github.hiramrg.delphis.platform.TestHooks.keepPopup) entries.firstOrNull()?.let { KeepState(it) } else null) }
    var status by remember { mutableStateOf<String?>(null) }
    var xDown by remember { mutableLongStateOf(0L) }
    val current = entries.getOrNull(index.coerceIn(0, (entries.size - 1).coerceAtLeast(0)))

    fun openKeep() { if (actions != null) current?.let { keep = KeepState(it); status = null } }
    fun doKeep() {
        val k = keep ?: return
        val a = actions ?: return
        if (k.busy) return
        k.busy = true
        scope.launch {
            val r = a.keep(k.entry, k.finalName)
            k.busy = false
            if (r == KeepResult.KEPT) { keep = null; status = if (k.renaming) "Renamed." else "Kept as “${k.finalName}”."; index = 0; onChanged() } else k.failed(r)
        }
    }
    fun doDelete(e: SaveEntry) {
        val a = actions ?: return
        scope.launch {
            val ok = a.delete(e)
            keep = null
            status = if (ok) "Deleted “${saveLabel(e)}”." else "Couldn't delete it."
            index = 0; onChanged()
        }
    }
    fun keepDelete() {
        val k = keep ?: return
        if (!k.deleteArmed) { k.deleteArmed = true; return }
        doDelete(k.entry)
    }

    ScaledDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    val code = e.key.nativeKeyCode
                    val k = keep
                    if (k != null) {
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (code) {
                            AndroidKey.KEYCODE_DPAD_LEFT -> { k.step(-1); true }
                            AndroidKey.KEYCODE_DPAD_RIGHT -> { k.step(1); true }
                            AndroidKey.KEYCODE_BUTTON_A -> { doKeep(); true }
                            AndroidKey.KEYCODE_BUTTON_B -> { keep = null; runCatching { focus.requestFocus() }; true }
                            AndroidKey.KEYCODE_BUTTON_X -> { runCatching { field.requestFocus() }; keyboard?.show(); true }
                            AndroidKey.KEYCODE_BUTTON_L2 -> { if (k.renaming) keepDelete(); true }
                            else -> false
                        }
                    } else {
                        // Holding X on a kept save deletes it (let go before the second: nothing happens).
                        if (code == AndroidKey.KEYCODE_BUTTON_X) {
                            if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) xDown = e.nativeKeyEvent.eventTime
                            if (e.type == KeyEventType.KeyDown && xDown > 0 && e.nativeKeyEvent.eventTime - xDown >= DELETE_HOLD_MS) {
                                xDown = 0
                                current?.takeIf { it.kind == SaveKind.KEPT }?.let { doDelete(it) }
                            }
                            if (e.type == KeyEventType.KeyUp) xDown = 0
                            return@onPreviewKeyEvent true
                        }
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (code) {
                            AndroidKey.KEYCODE_DPAD_DOWN -> { index = (index + 1).coerceAtMost(entries.lastIndex); true }
                            AndroidKey.KEYCODE_DPAD_UP -> { index = (index - 1).coerceAtLeast(0); true }
                            AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_BUTTON_START ->
                                { current?.let(onPlay); true }
                            AndroidKey.KEYCODE_BUTTON_Y -> { openKeep(); true }
                            AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BACK -> { onDismiss(); true }
                            else -> true
                        }
                    }
                },
        ) {
            val k = keep
            if (k != null) KeepSavePanel(
                k, keys = !PadGlyphs.touch, field = field, modifier = Modifier.width(440.dp),
                onCancel = { keep = null; runCatching { focus.requestFocus() } },
                onType = { runCatching { field.requestFocus() }; keyboard?.show() },
                onKeep = { doKeep() }, onDelete = { keepDelete() },
                onTypingDone = { runCatching { focus.requestFocus() } },
            ) else PopupPanel(
                listOfNotNull("Saves", gameTitle).joinToString(" · "), "Continue from which save?",
                Modifier.width(500.dp),
                buttons = {
                    PanelBtn("Close", "B", false, onClick = onDismiss)
                    if (actions != null) PanelBtn(if (current?.kind == SaveKind.KEPT) "Rename" else "Keep", "Y", false) { openKeep() }
                    PanelBtn("▶ Play from here", "A", true) { current?.let(onPlay) }
                },
            ) {
                Text(
                    status ?: (if (current?.kind == SaveKind.KEPT) "Hold X to delete this kept save. " else "") +
                        "Play starts from the one you pick. The others stay here, so you can switch back.",
                    fontSize = 12.sp, color = if (status != null) c.text else c.textDim, lineHeight = 15.sp, maxLines = 2,
                )
                SavesList(
                    entries, index, { index = it }, Modifier.fillMaxWidth().heightIn(max = 300.dp), latestTag = "Next Play",
                    onLongPress = if (actions != null) { i -> index = i; openKeep() } else null, small = true,
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
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
