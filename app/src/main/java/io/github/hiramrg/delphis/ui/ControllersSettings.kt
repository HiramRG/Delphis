package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.hiramrg.delphis.ui.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.PlayerSetup
import io.github.hiramrg.delphis.core.Players
import io.github.hiramrg.delphis.platform.DeviceInfo
import io.github.hiramrg.delphis.platform.ShortcutNames
import io.github.hiramrg.delphis.platform.rememberPads

/**
 * Settings → Controllers (one screen: phones, tablets, one-screen handhelds). Every connected controller with
 * what it is, its player, whether its buttons were checked; "Add a controller"; touch controls; and the
 * button that opens Delphis' menu in games, with a live test.
 */
@Composable
fun ControllersSettings(act: MainActivity) {
    val c = LocalWii.current
    val prefs = act.prefs
    val pads = rememberPads(prefs)
    val playing = Players.assign(pads, PlayerSetup())
    @Suppress("UNUSED_VARIABLE") val v = act.setupVersion
    CapsLabel("Connected")
    GroupCard {
        if (pads.isEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("No controller connected", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
                Text(if (prefs.touchWhenNoController) "Games use Dolphin's touch controls." else "Touch controls are off: connect a controller to play.", fontSize = 12.sp, color = c.textDim)
            }
        }
        pads.forEachIndexed { i, pad ->
            if (i > 0) RowDivider()
            val n = playing.indexOf(pad).takeIf { it >= 0 }?.plus(1)
            val device = remember(pad.key) { DeviceInfo.gamepads().firstOrNull { (it.descriptor ?: "id${it.id}") == pad.key } }
            val brand = if (pad.builtIn) null else PadBrand.forVendor(device?.vendorId)
            val checked = pad.builtIn || pad.name == prefs.calibratedDeviceName
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (c.dark) Color(0xFF1D2127) else c.bg), contentAlignment = Alignment.Center) {
                    PadGlyph(brand?.colour ?: c.accentText, 30.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(pad.longName, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 2, lineHeight = 17.sp, modifier = Modifier.weight(1f, fill = false))
                        if (n != null) Text(
                            "Player $n", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = c.onAccent, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Text(if (pad.builtIn) "This device's own controls" else (brand?.label?.let { "$it controller · " } ?: "") + "connected", fontSize = 11.5.sp, color = c.textDim)
                    Text(
                        if (checked) "✓ Buttons checked" else "! Buttons not checked yet", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (checked) c.green else c.orange,
                    )
                }
                if (!pad.builtIn) PanelBtn(if (checked) "Check again" else "Check", null, !checked) { act.padCheck() }
            }
        }
        RowDivider()
        SettingRow("Add a controller", "Step by step, for Xbox, PlayStation, Switch and others.", onClick = { act.openAddPad() }) {
            Text("Add ›", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText)
        }
    }
    QuickSaveCard(act)
    var touch by remember { mutableStateOf(prefs.touchWhenNoController) }
    GroupCard {
        ToggleRow("Touch controls when no controller is connected", "Dolphin's on-screen buttons. They go away when a controller is connected at the start of a game.", touch) {
            touch = it; prefs.touchWhenNoController = it
        }
    }

    // The button that opens Delphis' menu during a game, named for the connected controller.
    val names = remember(pads) { ShortcutNames.forConnected() }
    var shortcut by remember { mutableIntStateOf(prefs.menuShortcut) }
    RumbleCard(act)
    CapsLabel("In-game menu")
    GroupCard {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Open Delphis' menu during a game", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
            Text(
                "\"Both\" is best: if this device keeps the ${names.home} for itself, ${names.combo} still works " +
                    "(whichever you press second goes only to Delphis). The pill at the top of the game always works too.",
                fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp,
            )
            Segmented(listOf(0 to names.home, 1 to names.combo, 2 to "Both"), shortcut, { shortcut = it; prefs.menuShortcut = it })
            val test = act.shortcutTest
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(if (c.dark) Color(0xFF1D2127) else c.bg).border(1.5.dp, c.cardBorder, shape).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PanelBtn(if (test == "waiting") "Waiting…" else "Test it", null, true, enabled = test != "waiting") { act.startShortcutTest() }
                Text(
                    when (test) {
                        null -> "Checks that your controller's button reaches Delphis the way it does in a game."
                        "waiting" -> "Press the ${names.home} now (or ${names.combo})…"
                        else -> test
                    },
                    fontSize = 12.sp, fontWeight = if (test != null && test != "waiting") FontWeight.ExtraBold else FontWeight.Normal,
                    color = when { test == null || test == "waiting" -> c.text; act.shortcutTestOk -> c.green; else -> c.orange },
                    lineHeight = 15.sp, modifier = Modifier.weight(1f),
                )
            }
        }
    }
    LaunchedEffect(act.shortcutTest) { if (act.shortcutTest != null) shortcut = prefs.menuShortcut }
}

/**
 * Settings → Controllers (one screen) / Controls (the Thor): where the game's rumble goes, and a button to
 * feel the motor. Off the Thor: Controller / This device / Off; on the Thor: Thor / Off.
 */
@Composable
fun RumbleCard(act: MainActivity) {
    val c = LocalWii.current
    val prefs = act.prefs
    var mode by remember { mutableStateOf(prefs.rumble) }
    var result by remember { mutableStateOf<String?>(null) }
    val one = act.oneScreen
    val deviceWord = if (one) (if (androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600) "Tablet" else "Phone") else "Thor"
    val R = io.github.hiramrg.delphis.core.Rumble.Mode.PAD
    CapsLabel("Rumble")
    GroupCard {
        SettingRow(
            "Rumble",
            if (one) "When the game shakes the Wii Remote or GameCube controller, feel it." else "When the game shakes the Wii Remote or GameCube controller, feel it in the Thor.",
        ) {
            val opts = if (one) listOf(R to "Controller", io.github.hiramrg.delphis.core.Rumble.Mode.DEVICE to deviceWord, io.github.hiramrg.delphis.core.Rumble.Mode.OFF to "Off")
            else listOf(io.github.hiramrg.delphis.core.Rumble.Mode.DEVICE to "Thor", io.github.hiramrg.delphis.core.Rumble.Mode.OFF to "Off")
            // The Thor has no "Controller" choice: its own controls are the device, so that counts as "Thor".
            val shown = if (!one && mode == R) io.github.hiramrg.delphis.core.Rumble.Mode.DEVICE else mode
            Segmented(opts, shown, { mode = it; prefs.rumble = it; result = null })
        }
        RowDivider()
        SettingRow(
            "Try it",
            result ?: when (mode) {
                R -> DeviceInfo.gamepads().firstOrNull()?.name ?: "No controller connected"
                io.github.hiramrg.delphis.core.Rumble.Mode.DEVICE -> if (one) "This ${deviceWord.lowercase()}'s own motor" else "The Thor's built-in motor"
                else -> "Rumble is off"
            },
        ) { PanelBtn("Vibrate", null, true) { result = io.github.hiramrg.delphis.platform.RumbleTest.buzz(act, mode) } }
    }
    Text(
        "Only games that use rumble vibrate. " + if (one) "Some Bluetooth controllers can't vibrate on Android; \"Try it\" shows whether yours can." else "An extra controller for player 2–4 rumbles too when it has a motor.",
        fontSize = 11.5.sp, lineHeight = 15.sp, color = c.textDim, modifier = Modifier.padding(horizontal = 6.dp),
    )
}
