package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.platform.DolphinExitService

/*
 * When Delphis crashes, Android also stops its helper (the accessibility service) and keeps it stopped until it's
 * switched off and on again. These pieces say so wherever the helper is needed, and take you to the switch.
 */

private val Amber = Color(0xFFF2B544)
private val Green = Color(0xFF5BD08A)

/**
 * True while the helper is turned on in Accessibility but Android has stopped it. Updates when it starts again
 * (callers also pass something that changes when Delphis comes back to the front, to re-read the setting).
 */
@Composable
fun helperStopped(context: android.content.Context, @Suppress("UNUSED_PARAMETER") recheck: Any? = null): Boolean =
    !DolphinExitService.runningState.value && DolphinExitService.isEnabled(context)

@Composable
private fun StatusLine(text: String, colour: Color, mark: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(18.dp).border(2.dp, colour, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Text(mark, fontSize = 10.sp, fontWeight = FontWeight.Black, color = colour)
        }
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = colour)
    }
}

/** "Helper is back on" (green), after it had stopped. */
@Composable
fun HelperBackLine() = StatusLine("Helper is back on", Green, "✓")

/**
 * The helper has stopped: what it means for [what] (e.g. "which is what loads your autosave") and the three steps.
 * [tip] is an optional last line (how to manage without it).
 */
@Composable
fun HelperStoppedInfo(what: String, tip: String? = null) {
    val c = LocalWii.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusLine("Delphis' helper has stopped", Amber, "!")
        Text(
            "Delphis closed unexpectedly and Android stopped its helper, $what.",
            fontSize = 12.5.sp, lineHeight = 16.sp, color = c.textDim,
        )
        listOf("Open Accessibility → Delphis", "Turn it off, then on again", "Come back here").forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(c.cardBorder), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = c.text)
                }
                Text(step, fontSize = 12.5.sp, lineHeight = 16.sp, color = c.textDim)
            }
        }
        if (tip != null) Text(tip, fontSize = 11.5.sp, lineHeight = 15.sp, color = c.textDim.copy(alpha = 0.85f))
    }
}

/** Home: a banner while the helper has stopped. L2 (or the button) opens Accessibility. Goes away once it runs. */
@Composable
fun HelperBanner(act: MainActivity) {
    if (!helperStopped(act, act.setupVersion)) return
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(shape).background(Color(0xFF3A2F17))
            .border(2.dp, Amber, shape).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(20.dp).border(2.dp, Amber, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Text("!", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Amber)
        }
        Column(Modifier.weight(1f)) {
            Text("Delphis' helper has stopped", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
            Text(
                "Autosave, closing games and the pill won't work until you turn it off and on again in Accessibility.",
                fontSize = 11.5.sp, lineHeight = 14.sp, color = Color(0xFFF5DFA8), maxLines = 2,
            )
        }
        PanelBtn("Fix", "L2", true) { DolphinExitService.openSettings(act) }
    }
}
