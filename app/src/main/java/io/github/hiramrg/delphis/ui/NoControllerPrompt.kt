package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One screen: "No controller found · Connect a controller first?" when Delphis opens without one.
 * Big touch buttons (there's no controller to press with). When a controller connects while it's open,
 * it turns green, names it and closes by itself (see MainActivity).
 */
@Composable
fun NoControllerPrompt(connected: String?, onTouch: (dontShow: Boolean) -> Unit, onBluetooth: (dontShow: Boolean) -> Unit) {
    val c = LocalWii.current
    var dontShow by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { onTouch(dontShow) },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (connected != null) {
            PopupPanel("✓ Controller connected", connected, Modifier.widthIn(max = 460.dp)) {
                Text("Ready to play. This closes on its own.", fontSize = 12.5.sp, color = c.textDim, lineHeight = 16.sp)
                var progress by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(Unit) {
                    val start = System.currentTimeMillis()
                    while (progress < 1f) { progress = ((System.currentTimeMillis() - start) / 2000f).coerceAtMost(1f); kotlinx.coroutines.delay(16) }
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.cardBorder)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(c.green))
                }
            }
        } else {
            PopupPanel("No controller found", "Connect a controller first?", Modifier.widthIn(max = 520.dp)) {
                Text(
                    "Games play best with one. Pair it in Bluetooth settings, or just turn it on if it's already paired. " +
                        "Without one, games use Dolphin's touch controls.",
                    fontSize = 12.5.sp, color = c.textDim, lineHeight = 17.sp,
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelBtn("Use touch controls", null, false, Modifier.weight(1f)) { onTouch(dontShow) }
                    PanelBtn("Open Bluetooth settings", null, true, Modifier.weight(1.25f)) { onBluetooth(dontShow) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { DontAskRow("Don't show this again", dontShow) { dontShow = it } }
            }
        }
    }
}
