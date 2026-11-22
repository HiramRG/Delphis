package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.github.hiramrg.delphis.platform.Backup
import io.github.hiramrg.delphis.platform.QuickNames

/** "the Thor" on the Thor, "your device" elsewhere. */
private val deviceWord get() = if (android.os.Build.MODEL.contains("Thor", ignoreCase = true)) "the Thor" else "your device"

/** A setting with a few choices: title and explanation on the left, the choices under them. */
@Composable
private fun <T> ChoiceRow(title: String, sub: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = LocalWii.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
        Text(sub, fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp)
        Segmented(options, selected, onSelect)
    }
}

/** Settings → Saves: every automatic save in one place. */
@Composable
fun SavesSettings(act: MainActivity) {
    val c = LocalWii.current
    val prefs = act.prefs
    var sleep by remember { mutableStateOf(prefs.saveOnSleep) }
    var every by remember { mutableStateOf(prefs.timedSaveMinutes) }
    var toast by remember { mutableStateOf(prefs.timedSaveToast) }
    var battery by remember { mutableStateOf(prefs.lowBatteryLevel) }
    CapsLabel("Automatic saves")
    GroupCard {
        ToggleRow(
            "Save when $deviceWord sleeps",
            "Closing it or pressing power during a game saves first. Still being tested: Diagnostics shows whether it worked each time.", sleep,
        ) { sleep = it; prefs.saveOnSleep = it }
        RowDivider()
        ChoiceRow(
            "Also save every", "While you play, without leaving the game (it pauses for about a second). Covers a battery that runs out, a crash or a sleep that came too fast.",
            listOf(0 to "Off", 10 to "10 min", 20 to "20 min", 30 to "30 min"), every,
        ) { every = it; prefs.timedSaveMinutes = it }
        if (every > 0) {
            RowDivider()
            ToggleRow("Show a pop-up for timed saves", "A small \"Saved\" at the top of the game for 2 seconds.", toast) { toast = it; prefs.timedSaveToast = it }
        }
        RowDivider()
        ChoiceRow(
            "Save when the battery gets low", "Once per charge, when the battery reaches this level and isn't charging.",
            listOf(0 to "Off", 15 to "15%", 10 to "10%", 5 to "5%"), battery,
        ) { battery = it; prefs.lowBatteryLevel = it }
    }
    Text(
        "These are autosaves like the one when you quit: Delphis keeps the 3 newest earlier ones, and kept saves are never touched. Games with autosave turned off on their page are skipped.",
        fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 6.dp),
    )
}

/** Settings → Controls: the screenshot button and size (after the quick save buttons). */
@Composable
fun ScreenshotCard(act: MainActivity) {
    val prefs = act.prefs
    val names = remember { QuickNames.forConnected() }
    val y = when (names.select) { "Create" -> "△"; else -> "Y" }
    var on by remember { mutableStateOf(prefs.screenshotButton) }
    var gameOnly by remember { mutableStateOf(prefs.screenshotGameOnly) }
    CapsLabel("Screenshots")
    GroupCard {
        ToggleRow(
            "Screenshot button", "${names.select} + $y takes a screenshot while playing" + (if (act.oneScreen) "" else ", or tap 📷 on the bottom screen") +
                ". Games never see ${names.select} + $y while this is on.", on,
        ) { on = it; prefs.screenshotButton = it }
        RowDivider()
        ChoiceRow(
            "Screenshot size", "Full screen, or only the game picture (no black bars at the sides). They go to Photos, in Pictures/Delphis.",
            listOf(false to "Full screen", true to "Game only"), gameOnly,
        ) { gameOnly = it; prefs.screenshotGameOnly = it }
    }
}

/** Settings → Backup: make a backup, restore one, a reminder. */
@Composable
fun BackupSettings(act: MainActivity) {
    val c = LocalWii.current
    val prefs = act.prefs
    var remind by remember { mutableStateOf(prefs.backupReminder) }
    val last = remember(act.setupVersion) { prefs.lastBackupAt }
    CapsLabel("Back up and move")
    GroupCard {
        SettingRow("Make a backup", if (last == 0L) "Last backup: never" else "Last backup: ${formatSaveTime(last)}", onClick = { if (!act.backupBusy) act.backupPrompt = true }) {
            PanelBtn(if (act.backupBusy) "Working…" else "Back up", null, true, enabled = !act.backupBusy) { act.backupPrompt = true }
        }
        RowDivider()
        SettingRow("Restore from a backup", "Pick a Delphis backup file. You see what's inside and choose what to bring back.", onClick = { if (!act.backupBusy) act.pickBackup() }) {
            PanelBtn("Restore", null, false, enabled = !act.backupBusy) { act.pickBackup() }
        }
        RowDivider()
        ToggleRow("Remind me to back up", "A notice on Home when the last backup is more than a month old.", remind) { remind = it; prefs.backupReminder = it }
    }
    Text(
        "One file with your presets, kept saves, settings, favorites and play time: for a new device, after reinstalling, or to copy your setup from the Thor to a tablet. Save it wherever you like (Downloads, an SD card, Google Drive).",
        fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 6.dp),
    )
    if (act.backupPrompt) BackupDialog(act)
    act.restorePreview?.let { RestoreDialog(act, it.second) }
}

/** One part of a backup, with its tick. */
@Composable
private fun PartRow(title: String, sub: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().focusRing(shape, fill = true).clip(shape).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val box = RoundedCornerShape(6.dp)
        Box(
            Modifier.size(22.dp).clip(box).then(if (on) Modifier.background(c.accent) else Modifier.border(2.dp, c.textDim, box)),
            contentAlignment = Alignment.Center,
        ) { if (on) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Black, color = c.onAccent) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 2, lineHeight = 16.sp)
            Text(sub, fontSize = 11.5.sp, color = c.textDim, maxLines = 2, lineHeight = 14.sp)
        }
    }
}

/** Start presses a pop-up's main button (A ticks the row the cursor is on). */
private fun Modifier.startPresses(action: () -> Unit) = onPreviewKeyEvent { e ->
    if (e.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_BUTTON_START) { if (e.type == KeyEventType.KeyDown) action(); true } else false
}

@Composable
private fun BackupDialog(act: MainActivity) {
    val c = LocalWii.current
    val infos by produceState<List<Backup.PartInfo>?>(null) { value = act.backupSummary() }
    var chosen by remember { mutableStateOf(setOf(Backup.Part.PRESETS, Backup.Part.KEPT, Backup.Part.SETTINGS, Backup.Part.PLAY)) }
    val first = remember { FocusRequester() }
    ScaledDialog(onDismissRequest = { act.backupPrompt = false }, properties = DialogProperties(usePlatformDefaultWidth = false), translateKeys = true) {
        PopupPanel(
            "Make a backup", "What to include", Modifier.width(500.dp).startPresses { if (chosen.isNotEmpty() && infos != null) act.startBackup(chosen) },
            buttons = {
                val total = infos?.filter { it.part in chosen }?.sumOf { it.bytes }
                Text(total?.let { "≈ " + formatSize(it) } ?: "", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.text, modifier = Modifier.align(Alignment.CenterVertically))
                Spacer(Modifier.weight(1f))
                PanelBtn("Cancel", "B", false) { act.backupPrompt = false }
                PanelBtn("Choose where to save", "⊕", true, enabled = chosen.isNotEmpty() && infos != null) { act.startBackup(chosen) }
            },
        ) {
            val list = infos
            if (list == null) Text("Looking at what's there…", fontSize = 12.5.sp, color = c.textDim)
            else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                list.forEachIndexed { i, info ->
                    val on = info.part in chosen
                    PartRow(info.part.label, info.detail, on, if (i == 0) Modifier.focusRequester(first) else Modifier) {
                        chosen = if (on) chosen - info.part else chosen + info.part
                    }
                }
            }
            Text("Not included: your RetroAchievements sign-in (sign in again after restoring) and your games themselves.", fontSize = 11.5.sp, color = c.textDim, lineHeight = 14.sp)
        }
    }
    LaunchedEffect(infos) { if (infos != null) runCatching { first.requestFocus() } }
}

@Composable
private fun RestoreDialog(act: MainActivity, preview: Backup.Preview) {
    val c = LocalWii.current
    var chosen by remember(preview) { mutableStateOf(preview.parts.map { it.part }.toSet()) }
    var replace by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    ScaledDialog(onDismissRequest = { act.restorePreview = null }, properties = DialogProperties(usePlatformDefaultWidth = false), translateKeys = true) {
        PopupPanel(
            "Restore · " + Backup.fileName(preview.madeAt).removeSuffix(".zip"),
            "Made on ${preview.device.ifBlank { "a device" }}, ${formatSaveTime(preview.madeAt)}", Modifier.width(520.dp).startPresses { if (chosen.isNotEmpty()) act.runRestore(chosen, replace) },
            buttons = {
                PanelBtn("Cancel", "B", false) { act.restorePreview = null }
                PanelBtn("Restore", "⊕", true, enabled = chosen.isNotEmpty()) { act.runRestore(chosen, replace) }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                preview.parts.forEachIndexed { i, info ->
                    val on = info.part in chosen
                    val sub = when (info.part) {
                        Backup.Part.KEPT -> info.detail + " · saves of games you don't have yet show up when you add them"
                        Backup.Part.PLAY -> info.detail + " · merged with what's here"
                        else -> info.detail
                    }
                    PartRow(info.part.label, sub, on, if (i == 0) Modifier.focusRequester(first) else Modifier) { chosen = if (on) chosen - info.part else chosen + info.part }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("When something is in both:", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.text, modifier = Modifier.weight(1f))
                Segmented(listOf(false to "Keep what's here", true to "Use the backup's"), replace, { replace = it })
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
