package io.github.hiramrg.delphis.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.github.hiramrg.delphis.platform.DolphinBridge.SaveEntry
import io.github.hiramrg.delphis.platform.DolphinBridge.SaveKind

/** "Latest" / "Earlier" / "Slot 3". */
fun saveLabel(e: SaveEntry): String = when (e.kind) {
    SaveKind.LATEST -> "Latest"
    SaveKind.EARLIER -> "Earlier"
    SaveKind.SLOT -> "Slot ${e.slot}"
}

/**
 * The Saves list: autosaves (latest, then the earlier ones Delphis keeps), then Dolphin's own save slots.
 * [cursor] is the highlighted row (the D-pad moves it, the caller handles the keys); a tap picks a row.
 * [latestTag] is shown beside the latest autosave ("Next Play" on the game page).
 */
@Composable
fun SavesList(
    entries: List<SaveEntry>, cursor: Int, onTap: (Int) -> Unit, modifier: Modifier = Modifier,
    latestTag: String? = null, rowHeight: Dp = 34.dp,
) {
    val c = LocalWii.current
    // Rows with their group titles in between; a title is a null entry.
    val items = buildList<Pair<String?, Int>> {
        val autos = entries.indices.filter { entries[it].kind != SaveKind.SLOT }
        val slots = entries.indices.filter { entries[it].kind == SaveKind.SLOT }
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
                    .clickable { onTap(index) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("↺", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
                Spacer(Modifier.width(9.dp))
                Text(formatSaveTime(e.savedAt), fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(saveLabel(e), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = c.textDim, maxLines = 1)
                if (e.kind == SaveKind.LATEST && latestTag != null) {
                    Text(latestTag, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (c.dark) Color(0xFF5BD08A) else Color(0xFF2E9E57), maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/**
 * Game page: "Continue from which save?". D-pad chooses, A plays from it, B closes. Playing from an earlier save or
 * a slot puts it in the Quick Save slot first; the save that was there is kept, so you can switch back.
 */
@Composable
fun SavesDialog(gameTitle: String?, entries: List<SaveEntry>, onDismiss: () -> Unit, onPlay: (SaveEntry) -> Unit) {
    val c = LocalWii.current
    val focus = remember { FocusRequester() }
    var index by remember { mutableIntStateOf(0) }
    ScaledDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PopupPanel(
            listOfNotNull("Saves", gameTitle).joinToString(" · "), "Continue from which save?",
            Modifier.width(460.dp)
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key.nativeKeyCode) {
                        AndroidKey.KEYCODE_DPAD_DOWN -> { index = (index + 1).coerceAtMost(entries.lastIndex); true }
                        AndroidKey.KEYCODE_DPAD_UP -> { index = (index - 1).coerceAtLeast(0); true }
                        AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_BUTTON_START ->
                            { entries.getOrNull(index)?.let(onPlay); true }
                        AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BACK -> { onDismiss(); true }
                        else -> true
                    }
                },
            buttons = {
                PanelBtn("Close", "B", false, onClick = onDismiss)
                PanelBtn("▶ Play from here", "A", true) { entries.getOrNull(index)?.let(onPlay) }
            },
        ) {
            Text(
                "Play starts from the one you pick. The others stay here, so you can switch back.",
                fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp,
            )
            SavesList(entries, index, { index = it }, Modifier.fillMaxWidth().heightIn(max = 300.dp), latestTag = "Next Play")
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

