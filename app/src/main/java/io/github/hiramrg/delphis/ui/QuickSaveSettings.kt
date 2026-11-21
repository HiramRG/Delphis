package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.platform.QuickNames

/**
 * Settings → Controls: the quick save / load buttons while playing (Select + L1 saves, Select + R1 loads) and
 * whether loading needs the buttons held for a second. Applies from the next game.
 */
@Composable
fun QuickSaveCard(act: MainActivity) {
    val c = LocalWii.current
    val prefs = act.prefs
    val names = remember { QuickNames.forConnected() }
    var on by remember { mutableStateOf(prefs.quickSaveButtons) }
    var hold by remember { mutableStateOf(prefs.quickLoadHold) }
    CapsLabel("Quick save buttons")
    GroupCard {
        ToggleRow("Save and load while playing", "${names.save} saves, ${if (hold) "hold" else "press"} ${names.load} to load. A small pop-up at the top of the game confirms it.", on) {
            on = it; prefs.quickSaveButtons = it
        }
        if (on) {
            RowDivider()
            Column(Modifier.padding(horizontal = 8.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Load needs", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
                Text("Holding for a second stops loads by accident. Loading autosaves your progress first, so you can go back to it.", fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp)
                Segmented(listOf(false to "One press", true to "Holding 1 s"), hold, { hold = it; prefs.quickLoadHold = it })
            }
            RowDivider()
            Text(
                "Games never see ${names.select} + ${names.l1} / ${names.r1} while this is on. Turn it off if a game needs that combination.",
                fontSize = 12.sp, color = c.textDim, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            )
        }
    }
}
