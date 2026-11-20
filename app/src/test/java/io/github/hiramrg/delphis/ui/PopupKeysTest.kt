package io.github.hiramrg.delphis.ui

import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The one button rule in pop-up windows: B closes only the pop-up (never its action), A does the pop-up's
 * main action. These pop-ups are separate windows, which is where the buttons used to arrive swapped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PopupKeysTest {
    private fun press(code: Int) {
        val dialog = ShadowDialog.getLatestDialog() ?: error("no pop-up open")
        val t = android.os.SystemClock.uptimeMillis()
        dialog.window!!.decorView.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        dialog.window!!.decorView.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Opens [content] (a pop-up), presses [code], and returns what happened. */
    private fun run(code: Int, content: @androidx.compose.runtime.Composable ((String) -> Unit) -> Unit): String {
        var result by mutableStateOf("nothing")
        val act = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        act.setContent { WiiTheme(true) { if (result == "nothing") content { result = it } } }
        shadowOf(Looper.getMainLooper()).idle()
        press(code)
        return result
    }

    @Test fun confirmDialog() {
        val dlg: @androidx.compose.runtime.Composable ((String) -> Unit) -> Unit = { done ->
            ConfirmDialog("Exit", "Exit Delphis?", null, confirm = "Exit", onCancel = { done("cancel") }) { done("confirm") }
        }
        assertEquals("B on Yes / Cancel", "cancel", run(KeyEvent.KEYCODE_BUTTON_B, dlg))
        assertEquals("A on Yes / Cancel", "confirm", run(KeyEvent.KEYCODE_BUTTON_A, dlg))
    }

    @Test fun saves() {
        val entry = io.github.hiramrg.delphis.platform.DolphinBridge.SaveEntry(
            io.github.hiramrg.delphis.platform.DolphinBridge.SaveKind.LATEST, System.currentTimeMillis(), 1000L,
        )
        val dlg: @androidx.compose.runtime.Composable ((String) -> Unit) -> Unit = { done ->
            SavesDialog("Game", listOf(entry), onDismiss = { done("close") }) { done("play") }
        }
        assertEquals("B on Saves", "close", run(KeyEvent.KEYCODE_BUTTON_B, dlg))
        assertEquals("A on Saves", "play", run(KeyEvent.KEYCODE_BUTTON_A, dlg))
    }

    @Test fun whatsNew() {
        val dlg: @androidx.compose.runtime.Composable ((String) -> Unit) -> Unit = { done -> WhatsNewDialog("1.0") { done("close") } }
        assertEquals("B on What's new", "close", run(KeyEvent.KEYCODE_BUTTON_B, dlg))
    }

    @Test fun unusedButtonDoesNothing() {
        val dlg: @androidx.compose.runtime.Composable ((String) -> Unit) -> Unit = { done ->
            ConfirmDialog("Exit", "Exit Delphis?", null, confirm = "Exit", onCancel = { done("cancel") }) { done("confirm") }
        }
        assertEquals("R1 on Yes / Cancel", "nothing", run(KeyEvent.KEYCODE_BUTTON_R1, dlg))
    }
}
