package io.github.hiramrg.delphis.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.hiramrg.delphis.core.Pad
import io.github.hiramrg.delphis.core.PlayerSetup
import io.github.hiramrg.delphis.core.Players

/** Player colours, like the Wii Remote's lights: P1 blue, P2 red, P3 green, P4 yellow. */
val PlayerColors = listOf(Color(0xFF3D7BE0), Color(0xFFE0413A), Color(0xFF4FAE3F), Color(0xFFE2A51C))

@Composable
fun PlayerBadge(n: Int?, size: Int = 26) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (n != null) PlayerColors[(n - 1) % 4] else Color(0xFF4A535E)),
        contentAlignment = Alignment.Center,
    ) { Text(if (n != null) "P$n" else "—", fontSize = (size * 0.42f).sp, fontWeight = FontWeight.Black, color = Color.White) }
}

@Composable
fun PlayerPill(n: Int, pad: Pad) {
    val c = LocalWii.current
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (c.dark) c.bg else c.card)
            .border(2.dp, c.cardBorder, RoundedCornerShape(16.dp)).padding(start = 3.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlayerBadge(n, 24)
        Text(pad.shortName, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
    }
}

/** "PLAYERS  (P1 Thor) (P2 Xbox Wireless)   ⊖ Change" — shown when a controller besides the Thor is connected. */
@Composable
fun PlayersRow(players: List<Pad>, onChange: (() -> Unit)?, modifier: Modifier = Modifier, label: Boolean = true) {
    val c = LocalWii.current
    Row(
        modifier.then(if (onChange != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onChange) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (label) Text("PLAYERS", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = c.textDim, letterSpacing = 1.sp)
        players.forEachIndexed { i, p -> PlayerPill(i + 1, p) }
        if (onChange != null && label) {
            Spacer(Modifier.weight(1f))
            Text("⊖ Change", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.textDim)
        }
    }
}

/**
 * Who plays this game: "Thor sits out" switch, then every connected controller in player order.
 * D-pad chooses, A picks a controller up / drops it (to reorder) or flips the switch, X plays / sits
 * out, B closes. Touch: tap a controller to play / sit out, ▲ ▼ to move it. Saved per game on every change.
 */
@Composable
fun PlayersDialog(title: String, pads: List<Pad>, setup: PlayerSetup, onChange: (PlayerSetup) -> Unit, onDismiss: () -> Unit) {
    val c = LocalWii.current
    val focus = remember { FocusRequester() }
    val arranged = Players.arranged(pads, setup)
    val playing = Players.assign(pads, setup)
    val thor = pads.firstOrNull { it.builtIn }
    val rows = (if (thor != null) 1 else 0) + arranged.size
    var index by remember { mutableIntStateOf(0) }
    var holding by remember { mutableStateOf(false) }

    fun save(order: List<Pad>, out: Set<String>) = onChange(PlayerSetup(order.map { it.key }, out))
    fun toggle(p: Pad) = save(arranged, if (p.key in setup.out) setup.out - p.key else setup.out + p.key)
    fun moveBy(p: Pad, d: Int) {
        val i = arranged.indexOf(p); val j = i + d
        if (i < 0 || j !in arranged.indices) return
        save(arranged.toMutableList().apply { add(j, removeAt(i)) }, setup.out)
        index += d
    }
    fun padAt(i: Int) = arranged.getOrNull(i - if (thor != null) 1 else 0)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.width(470.dp).clip(shape).background(c.card).border(2.dp, c.cardBorder, shape)
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val pad = padAt(index)
                    when (e.key.nativeKeyCode) {
                        AndroidKey.KEYCODE_DPAD_DOWN -> { if (holding && pad != null) moveBy(pad, 1) else index = (index + 1).coerceAtMost(rows - 1); true }
                        AndroidKey.KEYCODE_DPAD_UP -> { if (holding && pad != null) moveBy(pad, -1) else index = (index - 1).coerceAtLeast(0); true }
                        AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER -> {
                            if (pad == null) thor?.let { toggle(it) } else holding = !holding; true
                        }
                        AndroidKey.KEYCODE_BUTTON_X -> { (pad ?: thor)?.let { toggle(it) }; true }
                        AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BACK -> { if (holding) holding = false else onDismiss(); true }
                        else -> true
                    }
                }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Players · $title", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                WiiButton("Ⓑ Done", onDismiss, fontSize = 13.sp)
            }
            val rowShape = RoundedCornerShape(14.dp)
            if (thor != null) {
                val sel = index == 0
                Row(
                    Modifier.fillMaxWidth().clip(rowShape).background(c.bg.copy(alpha = 0.4f))
                        .border(if (sel) 3.dp else 2.dp, if (sel) c.accent else c.cardBorder, rowShape)
                        .clickable { index = 0; toggle(thor) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Thor sits out", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Only the other controllers play; the Thor still works in Delphis' menus", fontSize = 11.sp, color = c.textDim)
                    }
                    Switch(thor.key in setup.out, { toggle(thor) }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
                }
            }
            arranged.forEachIndexed { i, p ->
                val row = i + if (thor != null) 1 else 0
                val sel = index == row
                val n = playing.indexOf(p).takeIf { it >= 0 }?.plus(1)
                Row(
                    Modifier.fillMaxWidth().clip(rowShape).background(if (holding && sel) c.accentSoft else c.bg.copy(alpha = 0.4f))
                        .border(if (sel) 3.dp else 2.dp, if (sel) c.accent else c.cardBorder, rowShape)
                        .clickable { index = row; toggle(p) }
                        .alpha(if (n != null) 1f else 0.55f).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlayerBadge(n, 30)
                    Column(Modifier.weight(1f)) {
                        Text(p.longName, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                n == null && p.key in setup.out -> "Sitting out"
                                n == null -> "Not playing (4 players max)"
                                p.builtIn -> "Built-in controls"
                                else -> "Buttons by position, like the Thor's"
                            }, fontSize = 11.sp, color = c.textDim,
                        )
                    }
                    if (holding && sel) Text("⇅ moving", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.accentText)
                    Text("▲", fontSize = 15.sp, color = c.textDim, modifier = Modifier.clip(CircleShape).clickable { index = row; moveBy(p, -1) }.padding(6.dp))
                    Text("▼", fontSize = 15.sp, color = c.textDim, modifier = Modifier.clip(CircleShape).clickable { index = row; moveBy(p, 1) }.padding(6.dp))
                }
            }
            HintLine("✚ choose · Ⓐ pick up / drop to reorder · Ⓧ play / sit out · up to 4 players")
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
