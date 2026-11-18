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
fun NoControllerPrompt(
    connected: String?, needsCheck: Boolean, onCheck: () -> Unit, onLater: () -> Unit,
    onTouch: (dontShow: Boolean) -> Unit, onBluetooth: (dontShow: Boolean) -> Unit,
) {
    val c = LocalWii.current
    var dontShow by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { onTouch(dontShow) },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (connected != null && needsCheck) {
            // A controller Delphis hasn't checked yet: one press per button tells Delphis and Dolphin exactly what it sends.
            PopupPanel("✓ Controller connected", connected, Modifier.widthIn(max = 500.dp)) {
                Text(
                    "Let's check that it works: press each button once when asked (about a minute). " +
                        "Some controllers, like the Switch Pro Controller, name their buttons differently on Android; after the check, Delphis' menus and your games read it right.",
                    fontSize = 12.5.sp, color = c.textDim, lineHeight = 17.sp,
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelBtn("Later", null, false, Modifier.weight(1f), onClick = onLater)
                    PanelBtn("Check my controller", null, true, Modifier.weight(1.4f), onClick = onCheck)
                }
            }
        } else if (connected != null) {
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

/**
 * One screen, "close games" is off: without it there's no Delphis pill, no ⊖ + Start and no way to quit a
 * game from Delphis. Android blocks it for apps installed from an APK until "Allow restricted settings"
 * is turned on in the app's info page, so both steps are spelled out.
 */
@Composable
fun OneScreenHelperPrompt(forPlay: Boolean, onNotNow: () -> Unit, onAppInfo: () -> Unit, onAccessibility: () -> Unit) {
    val c = LocalWii.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onNotNow,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PopupPanel("Closing games", "Turn on \"close games\"", Modifier.widthIn(max = 560.dp)) {
            Text(
                "Delphis needs it to show its pill over the game, open its menu with ⊖ + Start and quit games. " +
                    "Without it, the only way out of a game is Dolphin's own menu (swipe back from the edge).",
                fontSize = 12.5.sp, color = c.textDim, lineHeight = 17.sp,
            )
            Text("1 · App info → ⋮ (top right) → Allow restricted settings", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.text, lineHeight = 17.sp)
            Text("Android asks for this for apps installed from an APK. If ⋮ has no such option, skip to step 2.", fontSize = 12.sp, color = c.textDim, lineHeight = 16.sp)
            Text("2 · Accessibility → Installed apps → Delphis – close games → On", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.text, lineHeight = 17.sp)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelBtn(if (forPlay) "Play anyway" else "Not now", null, false, Modifier.weight(1f), onClick = onNotNow)
                PanelBtn("1 · App info", null, false, Modifier.weight(1f), onClick = onAppInfo)
                PanelBtn("2 · Accessibility", null, true, Modifier.weight(1.1f), onClick = onAccessibility)
            }
        }
    }
}
