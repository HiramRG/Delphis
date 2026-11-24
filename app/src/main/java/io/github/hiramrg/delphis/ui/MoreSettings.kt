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
import androidx.compose.ui.draw.alpha
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
    var battery by remember { mutableStateOf(prefs.lowBatteryLevel) }
    // Whether this Dolphin saves with Delphis' hidden save button (true / false / not tried yet).
    val quiet = remember(act.setupVersion) { act.bridge.silentSaves() }
    val can = quiet != false
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { CapsLabel("Automatic saves while you play") }
        when (quiet) {
            true -> StatusPill("✓ Saves in the background", c.green)
            false -> StatusPill("! Needs a newer Dolphin", c.orange)
            null -> {}
        }
    }
    GroupCard {
        Box(Modifier.alpha(if (can) 1f else 0.45f)) {
            ToggleRow("Save when $deviceWord sleeps", "Closing it or pressing power during a game saves first. Diagnostics shows whether it worked each time.", sleep && can) {
                if (can) { sleep = it; prefs.saveOnSleep = it }
            }
        }
        RowDivider()
        Box(Modifier.alpha(if (can) 1f else 0.45f)) {
            ChoiceRow(
                "Save when the battery gets low", "Once per charge, when the battery reaches this level and isn't charging.",
                listOf(0 to "Off", 15 to "15%", 10 to "10%", 5 to "5%"), if (can) battery else 0,
            ) { if (can) { battery = it; prefs.lowBatteryLevel = it } }
        }
        RowDivider()
        Text(
            when (quiet) {
                true -> "Dolphin saves on its own: the game keeps running and nothing opens on screen."
                false -> "This Dolphin can only save through its menu, which would interrupt the game. Update Dolphin to a development build from November 2026 or newer (or the next release) to turn these on."
                null -> "These save in the background with Dolphin's own save button, so the game is never interrupted. It needs a Dolphin development build from November 2026 or newer: Delphis checks the first time you save during a game."
            },
            fontSize = 12.sp, lineHeight = 15.sp, color = when (quiet) { true -> c.green; false -> c.orange; null -> c.textDim },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
    CapsLabel("Always")
    GroupCard {
        SettingRow("When you open Delphis' menu, load a save or quit", "The game is already paused then, so nothing is interrupted. Games with autosave turned off on their page are skipped.") {
            StatusPill("✓ On", c.green)
        }
    }
    Text(
        "These are autosaves like the one when you quit: Delphis keeps the 3 newest earlier ones, and kept saves are never touched.",
        fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 6.dp),
    )
}

/** A small rounded status tag (green ✓ / orange !). */
@Composable
private fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp),
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
