package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The controller families Delphis knows how to pair and name. */
enum class PadBrand(val label: String, val colour: Color, val pairing: List<String>) {
    XBOX("Xbox", Color(0xFF3FA864), listOf(
        "Turn it on with the Xbox button.",
        "Hold the small pair button on top (next to the charging port) until the Xbox button blinks fast.",
        "In Bluetooth, tap \"Xbox Wireless Controller\".",
    )),
    PLAYSTATION("PlayStation", Color(0xFF5B8DEF), listOf(
        "Hold Create (Share on a PS4 controller) and the PS button together.",
        "Let go when the light bar blinks fast.",
        "In Bluetooth, tap \"Wireless Controller\" or \"DualSense\".",
    )),
    SWITCH("Switch Pro", Color(0xFFE0503A), listOf(
        "Hold the small sync button on top, next to the charging port.",
        "Let go when the green lights run back and forth.",
        "In Bluetooth, tap \"Pro Controller\".",
    )),
    EIGHTBITDO("8BitDo", Color(0xFFA3ADB8), listOf(
        "Turn it on in Android (X-input) mode: hold Start + X on most models.",
        "Hold the pair button for 3 seconds until the lights blink fast.",
        "In Bluetooth, tap its name.",
    )),
    OTHER("Other", Color(0xFFA3ADB8), listOf(
        "Turn it on and put it in pairing mode (usually a button you hold until a light blinks fast; see its manual).",
        "In Bluetooth, tap its name.",
        "Already paired before? Just turn it on.",
    ));

    companion object {
        fun forVendor(vendorId: Int?): PadBrand = when (vendorId) {
            0x045e -> XBOX
            0x054c -> PLAYSTATION
            0x057e -> SWITCH
            0x2dc8 -> EIGHTBITDO
            else -> OTHER
        }
    }
}

/** A plain controller outline in [colour] (no maker's logo). */
@Composable
fun PadGlyph(colour: Color, width: Dp = 30.dp) {
    Canvas(Modifier.size(width, width * 0.68f)) {
        val u = size.width / 44f
        val body = Path().apply {
            moveTo(10 * u, 3 * u); lineTo(34 * u, 3 * u)
            cubicTo(39 * u, 3 * u, 42 * u, 7 * u, 43 * u, 12 * u); lineTo(44 * u, 22 * u)
            cubicTo(44 * u, 27 * u, 39 * u, 29 * u, 36 * u, 26 * u); lineTo(31 * u, 21 * u); lineTo(13 * u, 21 * u); lineTo(8 * u, 26 * u)
            cubicTo(5 * u, 29 * u, 0f, 27 * u, 0f, 22 * u); lineTo(1 * u, 12 * u)
            cubicTo(2 * u, 7 * u, 5 * u, 3 * u, 10 * u, 3 * u); close()
        }
        drawPath(body, colour, style = Stroke(2.4f * u, join = StrokeJoin.Round))
        drawCircle(colour, 2.2f * u, Offset(33 * u, 11 * u))
        drawLine(colour, Offset(9 * u, 11 * u), Offset(16 * u, 11 * u), 2.4f * u, StrokeCap.Round)
        drawLine(colour, Offset(12.5f * u, 7.5f * u), Offset(12.5f * u, 14.5f * u), 2.4f * u, StrokeCap.Round)
    }
}

/**
 * "Add a controller", step by step (one screen): 1 which controller, 2 how to put it in pairing mode (with
 * Bluetooth settings), 3 it connected → its player and the button check. Delphis moves to step 3 by itself
 * as soon as Android reports the controller (MainActivity's input-device listener).
 */
@Composable
fun AddControllerPanel(act: MainActivity) {
    val c = LocalWii.current
    val brand = PadBrand.entries[act.addPadBrand.coerceIn(0, PadBrand.entries.lastIndex)]
    OverlayPanel(onOutside = { act.addPadClose() }) {
        val step = act.addPadStep
        val title = when (step) { 1 -> "Which controller is it?"; 2 -> "Put it in pairing mode"; else -> "✓ " + (act.addPadFound ?: "Controller connected") }
        PopupPanel("Add a controller · step $step of 3", title, Modifier.widthIn(max = 560.dp)) {
            when (step) {
                1 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PadBrand.entries.forEachIndexed { i, b ->
                            val on = i == act.addPadBrand
                            val shape = RoundedCornerShape(14.dp)
                            Column(
                                Modifier.weight(1f).clip(shape).background(if (on) c.accentSoft else if (c.dark) Color(0xFF1D2127) else c.bg)
                                    .border(if (on) 3.dp else 2.dp, if (on) c.accent else c.cardBorder, shape)
                                    .clickable { act.addPadBrand = i }.padding(vertical = 9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                PadGlyph(b.colour, 32.dp)
                                Text(b.label, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 2, lineHeight = 14.sp)
                            }
                        }
                    }
                    Text("Already paired before? Just turn it on: Delphis sees it right away.", fontSize = 12.sp, color = c.textDim, lineHeight = 16.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PanelBtn("Cancel", "B", false, Modifier.weight(1f)) { act.addPadClose() }
                        PanelBtn("Next", "A", true, Modifier.weight(1.4f)) { act.addPadStep = 2 }
                    }
                }
                2 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(112.dp, 84.dp).clip(RoundedCornerShape(14.dp)).background(if (c.dark) Color(0xFF1D2127) else c.bg), contentAlignment = Alignment.Center) {
                            PadGlyph(brand.colour, 80.dp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            brand.pairing.forEachIndexed { i, line ->
                                Text("${i + 1}. $line", fontSize = 12.5.sp, color = c.text, lineHeight = 16.sp)
                            }
                        }
                    }
                    Text("Delphis waits here and moves on by itself when the controller connects.", fontSize = 12.sp, color = c.textDim, lineHeight = 16.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PanelBtn("Back", "B", false, Modifier.weight(1f)) { act.addPadStep = 1 }
                        PanelBtn("Open Bluetooth", "A", true, Modifier.weight(1.4f)) { act.addPadBluetooth() }
                    }
                }
                else -> {
                    val n = act.addPadPlayer
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.green.copy(alpha = 0.14f)).border(1.5.dp, c.green, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Black, color = c.green)
                        Text("Connected · it plays as Player $n", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
                    }
                    Text(
                        "Now press each button once (about a minute) so the menus and your games read it right. " +
                            "Delphis uses buttons by position: the right one selects, the bottom one goes back.",
                        fontSize = 12.5.sp, color = c.text, lineHeight = 17.sp,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PanelBtn("Later", "B", false, Modifier.weight(1f)) { act.addPadClose() }
                        PanelBtn("Check buttons", "A", true, Modifier.weight(1.4f)) { act.addPadClose(); act.padCheck() }
                    }
                }
            }
        }
    }
}
