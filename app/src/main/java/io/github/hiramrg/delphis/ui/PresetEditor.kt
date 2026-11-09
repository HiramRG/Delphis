package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.github.hiramrg.delphis.core.ActionCatalog
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.core.ThorControl

private val controlNames = mapOf(
    ThorControl.L2 to "L2 (lower left trigger)", ThorControl.L1 to "L1 (upper left shoulder)",
    ThorControl.R2 to "R2 (lower right trigger)", ThorControl.R1 to "R1 (upper right shoulder)",
    ThorControl.LEFT_STICK to "Left stick", ThorControl.L3 to "Left stick click",
    ThorControl.DPAD to "D-pad", ThorControl.FACE_NORTH to "X button", ThorControl.FACE_WEST to "Y button",
    ThorControl.FACE_EAST to "A button", ThorControl.FACE_SOUTH to "B button",
    ThorControl.RIGHT_STICK to "Right stick", ThorControl.R3 to "Right stick click",
    ThorControl.SELECT to "Select", ThorControl.START to "Start",
    ThorControl.GYRO to "Gyro (turn the Thor)",
)

/**
 * Lets the user change what any Thor control does. Shows the original preset's choice for each
 * control; tap one to pick something else. [saveLabel] is the confirm button text.
 */
@Composable
fun PresetEditor(
    base: Preset,
    customId: String,
    saveLabel: String,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onSave: (Preset) -> Unit,
) {
    val choices = remember(base.id) { mutableStateMapOf<ThorControl, ActionCatalog.Option>() }
    val draft = ActionCatalog.applyEdits(base, choices, customId)

    BoxWithConstraints(modifier) {
        val wide = maxWidth > maxHeight * 1.05f
        val list: @Composable (Modifier) -> Unit = { m ->
            LazyColumn(m, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Text("Tap a control to change what it does", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Rows with nothing to pick (e.g. gyro on a GameCube preset) are hidden.
                items(ActionCatalog.EDITABLE.filter { ActionCatalog.optionsFor(it, base.controller).size > 1 }, key = { it.name }) { control ->
                    val current = draft.mappings.firstOrNull { it.control == control }
                    EditRow(control, current?.action ?: "Nothing", ActionCatalog.optionsFor(control, base.controller)) { choices[control] = it }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
        val actions: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { choices.clear() }, enabled = choices.isNotEmpty()) { Text("Undo changes") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                PrimaryGlowButton(saveLabel, androidx.compose.material.icons.Icons.Filled.Save, onClick = { onSave(draft) }, enabled = choices.isNotEmpty(), fontSize = 14.sp)
            }
        }
        Column(Modifier.fillMaxSize()) {
            Text(withOrangeTags("Editing: ${base.name.removeSuffix(" (Custom)")}", LocalWii.current.orange), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (wide) {
                Row(Modifier.weight(1f)) {
                    list(Modifier.weight(0.5f).fillMaxHeight())
                    Spacer(Modifier.width(8.dp))
                    ControlsOverview(draft, Modifier.weight(0.5f).fillMaxHeight())
                }
            } else {
                list(Modifier.weight(1f).fillMaxWidth())
            }
            actions()
        }
    }
}

@Composable
private fun EditRow(control: ThorControl, current: String, options: List<ActionCatalog.Option>, onPick: (ActionCatalog.Option) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { open = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlBadge(control, 26.dp)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(controlNames[control] ?: control.shortLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(current, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (current == "Nothing") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
            Text("Change ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        // Non-focusable so opening it on the bottom screen doesn't pull gamepad focus away from Dolphin.
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, properties = PopupProperties(focusable = false)) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(withOrangeTags(opt.label, LocalWii.current.orange)) }, onClick = { onPick(opt); open = false })
            }
        }
    }
}
