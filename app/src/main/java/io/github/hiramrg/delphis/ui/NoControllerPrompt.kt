package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.hiramrg.delphis.ui.Text
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
    connected: String?, needsCheck: Boolean, vendor: Int, dontShow: Boolean, onDontShow: (Boolean) -> Unit,
    onCheck: () -> Unit, onLater: () -> Unit, onTouch: () -> Unit, onBluetooth: () -> Unit,
) {
    val c = LocalWii.current
    // Drawn inside Delphis' own window (not a dialog window), so controller keys go through Delphis' key handling.
    OverlayPanel(onOutside = { if (connected != null && needsCheck) onLater() else if (connected == null) onTouch() }) {
        if (connected != null && needsCheck) {
            // A controller Delphis hasn't checked yet: what its buttons do in Delphis, then one press per button.
            val nintendo = vendor == 0x057E
            val sony = vendor == 0x054C
            PopupPanel("✓ Controller connected", connected, Modifier.widthIn(max = 660.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Delphis reads its buttons by their letters. Let's check it once: press each button when asked (about a minute).",
                            fontSize = 12.5.sp, color = c.textDim, lineHeight = 17.sp,
                        )
                        Text(
                            when {
                                sony -> "✕ selects, ○ goes back."
                                else -> "A selects, B goes back."
                            },
                            fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text, lineHeight = 17.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PanelBtn("Later", "B", false, Modifier.weight(1f), onClick = onLater)
                            PanelBtn("Check my controller", "A", true, Modifier.weight(1.5f), onClick = onCheck)
                        }
                    }
                    ButtonGuide(nintendo, sony)
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
                    PanelBtn("Use touch controls", "B", false, Modifier.weight(1f)) { onTouch() }
                    PanelBtn("Add a controller", "A", true, Modifier.weight(1.25f)) { onBluetooth() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { DontAskRow("Don't show this again", dontShow) { onDontShow(it) } }
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
    OverlayPanel(onOutside = onNotNow) {
        PopupPanel("Closing games", "Turn on \"close games\"", Modifier.widthIn(max = 560.dp)) {
            Text(
                "Delphis needs it to show its pill over the game, open its menu with your controller's Home button and quit games. " +
                    "Without it, the only way out of a game is Dolphin's own menu (swipe back from the edge).",
                fontSize = 12.5.sp, color = c.textDim, lineHeight = 17.sp,
            )
            Text("1 · App info → ⋮ (top right) → Allow restricted settings", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.text, lineHeight = 17.sp)
            Text("Android asks for this for apps installed from an APK. If ⋮ has no such option, skip to step 2.", fontSize = 12.sp, color = c.textDim, lineHeight = 16.sp)
            Text("2 · Accessibility → Installed apps → Delphis – close games → On", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.text, lineHeight = 17.sp)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelBtn(if (forPlay) "Play anyway" else "Not now", "B", false, Modifier.weight(1f), onClick = onNotNow)
                PanelBtn("1 · App info", "Y", false, Modifier.weight(1f), onClick = onAppInfo)
                PanelBtn("2 · Accessibility", "A", true, Modifier.weight(1.1f), onClick = onAccessibility)
            }
        }
    }
}

/** The face buttons as the controller labels them, and what each does in Delphis (the Thor's positions). */
@Composable
private fun ButtonGuide(nintendo: Boolean, sony: Boolean) {
    val c = LocalWii.current
    // top, right, bottom, left: the controller's own label, its colour, the role in Delphis
    val buttons = when {
        nintendo -> listOf(Triple("X", c.text, "X"), Triple("A", c.accentText, "Select"), Triple("B", c.text, "Back"), Triple("Y", c.text, "Y"))
        sony -> listOf(Triple("△", Color(0xFF3FB8A0), "△"), Triple("○", Color(0xFFE0503A), "Back"), Triple("✕", Color(0xFF5B8DEF), "Select"), Triple("□", Color(0xFFD877C0), "□"))
        else -> listOf(Triple("Y", Color(0xFFE0C23A), "Y"), Triple("B", Color(0xFFE0503A), "Back"), Triple("A", Color(0xFF3FA864), "Select"), Triple("X", Color(0xFF3A7DE0), "X"))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CapsLabel("In Delphis")
        Box(Modifier.size(width = 190.dp, height = 156.dp)) {
            val spots = listOf(Alignment.TopCenter, Alignment.CenterEnd, Alignment.BottomCenter, Alignment.CenterStart)
            buttons.forEachIndexed { i, (label, colour, role) ->
                Column(Modifier.align(spots[i]).width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Canvas(Modifier.size(38.dp)) {
                        drawCircle(colour, size.minDimension / 2f - 1.25.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
                        drawCenteredGlyph(label, size.width / 2f, size.height / 2f, 16.sp.toPx(), colour)
                    }
                    Text(role, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = if ("Select" in role) c.accentText else c.text, maxLines = 1)
                }
            }
        }
        Text("⌂ Home button · Delphis' menu", fontSize = 10.5.sp, color = c.textDim)
    }
}

/** One screen: Delphis' Home opened while a game still runs in the background (e.g. after Android's Home). */
@Composable
fun RunningGamePrompt(title: String, onBack: () -> Unit, onQuit: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalWii.current
    OverlayPanel(onOutside = onDismiss) {
        PopupPanel("Still playing", "$title is still open", Modifier.widthIn(max = 480.dp)) {
            Text(
                "It's paused in the background. Go back to it, or quit it (with its autosave) before playing something else.",
                fontSize = 12.5.sp, color = c.textDim, lineHeight = 17.sp,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelBtn("Quit game", "X", false, Modifier.weight(1f), onClick = onQuit)
                PanelBtn("Back to game", "A", true, Modifier.weight(1.3f), onClick = onBack)
            }
        }
    }
}

/**
 * One screen: a controller of a brand Delphis doesn't know. One press of its A tells Delphis where its A is
 * (bottom like Xbox, or right like Nintendo), so A always confirms and B always goes back on it.
 */
@Composable
fun LabelCheckPanel(name: String, onSkip: () -> Unit) {
    val c = LocalWii.current
    OverlayPanel(onOutside = onSkip) {
        PopupPanel("New controller", name, Modifier.widthIn(max = 460.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Press the button labelled A on it, so Delphis knows which button confirms.",
                    fontSize = 13.sp, color = c.text, lineHeight = 18.sp,
                )
                Text("Asked once for this controller.", fontSize = 11.5.sp, color = c.textDim)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { PanelBtn("Skip", null, false, onClick = onSkip) }
            }
        }
    }
}
