package io.github.hiramrg.delphis.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.hiramrg.delphis.platform.DolphinBridge.AutosaveBackup

/** "Today 14:32", "Yesterday 09:05" or "3 Nov 2026 21:40". */
fun formatSaveTime(t: Long): String {
    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(t))
    val day = java.util.Calendar.getInstance().apply { timeInMillis = t }
    val today = java.util.Calendar.getInstance()
    fun same(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    if (same(day, today)) return "Today $time"
    if (same(day, (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) })) return "Yesterday $time"
    return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(t)) + " " + time
}

fun formatSize(bytes: Long): String =
    if (bytes >= 1024L * 1024) "%.0f MB".format(bytes / (1024.0 * 1024)) else "${(bytes / 1024).coerceAtLeast(1)} KB"

/**
 * Lists a game's earlier autosaves. D-pad chooses, A restores, B closes. Restoring swaps the
 * chosen save into Dolphin's Quick Save slot; the save that was there is kept, so it can be undone.
 */
@Composable
fun EarlierAutosavesDialog(backups: List<AutosaveBackup>, onDismiss: () -> Unit, onRestore: (AutosaveBackup) -> Unit) {
    val c = LocalWii.current
    val focus = remember { FocusRequester() }
    var index by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PopupPanel(
            "Earlier autosaves", "Go back to an earlier autosave?",
            Modifier.width(440.dp)
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key.nativeKeyCode) {
                        AndroidKey.KEYCODE_DPAD_DOWN -> { index = (index + 1).coerceAtMost(backups.lastIndex); true }
                        AndroidKey.KEYCODE_DPAD_UP -> { index = (index - 1).coerceAtLeast(0); true }
                        AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER ->
                            { backups.getOrNull(index)?.let(onRestore); true }
                        AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BACK -> { onDismiss(); true }
                        else -> true
                    }
                },
            buttons = {
                PanelBtn("Close", "B", false, onClick = onDismiss)
                PanelBtn("Restore", "A", true) { backups.getOrNull(index)?.let(onRestore) }
            },
        ) {
            Text(
                "Pick one to continue from it next time you play. Your current autosave is kept here too, so you can switch back.",
                fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                backups.forEachIndexed { i, b ->
                    val selected = i == index
                    val rowShape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(rowShape)
                            .background(if (selected) c.accentSoft else c.card.copy(alpha = 0.6f))
                            .border(if (selected) 3.dp else 1.dp, if (selected) c.accent else c.cardBorder, rowShape)
                            .clickable { index = i; onRestore(b) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("↺", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
                        Spacer(Modifier.width(10.dp))
                        Text(formatSaveTime(b.savedAt), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f))
                        Text(formatSize(b.size), fontSize = 12.sp, color = c.textDim)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
