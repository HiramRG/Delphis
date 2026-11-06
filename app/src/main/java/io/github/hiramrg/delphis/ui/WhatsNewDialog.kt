package io.github.hiramrg.delphis.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.hiramrg.delphis.core.WhatsNew

/** "What's new" card shown once after an update. A, B or the button closes it. */
@Composable
fun WhatsNewDialog(version: String, onDismiss: () -> Unit) {
    val c = LocalWii.current
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.width(480.dp).heightIn(max = 350.dp).clip(shape).background(c.card).border(2.dp, c.cardBorder, shape)
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key.nativeKeyCode) {
                        AndroidKey.KEYCODE_BUTTON_A, AndroidKey.KEYCODE_BUTTON_B, AndroidKey.KEYCODE_BACK,
                        AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER -> { onDismiss(); true }
                        else -> false // the D-pad scrolls the list
                    }
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("What's new in Delphis", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.accentText, modifier = Modifier.weight(1f))
                if (version.isNotBlank()) Text("Version $version", fontSize = 12.sp, color = c.textDim)
            }
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).fillMaxWidth().height(2.dp).background(c.cardBorder.copy(alpha = 0.5f)))
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                for (item in WhatsNew.items) {
                    Row {
                        Text("●", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (item.fix) c.green else c.accent)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(item.title) }
                                append(" "); append(item.text)
                            },
                            fontSize = 13.sp, lineHeight = 17.sp, color = c.text,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WiiButton("Ⓐ Got it", onDismiss, primary = true)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
