package io.github.hiramrg.delphis.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.window.DialogProperties
import io.github.hiramrg.delphis.platform.DeviceInfo
import io.github.hiramrg.delphis.platform.Prefs

/**
 * Interface size (Settings → Display, one screen). Delphis is laid out for the Thor's top screen, about
 * 700 × 393 dp in landscape. Automatic draws everything bigger on a big screen (a tablet) so it fills it the
 * way it fills the Thor's: same proportions, nothing cut. It never draws smaller than Normal, so phones and
 * the Thor are unchanged. (No "Large": anything bigger than the screen's Thor-shaped size would cut text.)
 */
object UiScale {
    const val BASE_W = 700f
    const val BASE_H = 393f

    /** The factor for a [wDp] × [hDp] screen; [mode] 1 (Normal) is always 1. */
    fun factor(mode: Int, wDp: Float, hDp: Float): Float {
        if (mode != 0 || wDp <= 0f || hDp <= 0f) return 1f
        val w = maxOf(wDp, hDp)
        val h = minOf(wDp, hDp)
        val f = minOf(w / BASE_W, h / BASE_H)
        // A few percent isn't worth a different size from the Thor's.
        return if (f < 1.1f) 1f else f.coerceAtMost(3f)
    }

    /** The factor for this device's whole screen (one screen only; the Thor's two screens are never scaled). */
    fun forScreen(context: android.content.Context, prefs: Prefs = Prefs(context)): Float {
        if (!runCatching { DeviceInfo.oneScreen(context) }.getOrDefault(false)) return 1f
        val dm = context.resources.displayMetrics
        return factor(prefs.uiSize, dm.widthPixels / dm.density, dm.heightPixels / dm.density)
    }

    /** "×2.0" for Settings. */
    fun label(f: Float): String = "×" + String.format(java.util.Locale.US, "%.1f", f)
}

/** The Interface size factor in use (dialogs and pop-ups read it to draw at the same size). */
val LocalUiScale = compositionLocalOf { 1f }

/** Draws [content] [f] times bigger (Interface size). */
@Composable
fun ScaledUi(f: Float, content: @Composable () -> Unit) {
    if (f == 1f) { CompositionLocalProvider(LocalUiScale provides 1f, content = content); return }
    val d = LocalDensity.current
    CompositionLocalProvider(LocalUiScale provides f, LocalDensity provides Density(d.density * f, d.fontScale), content = content)
}

/** A dialog drawn at the Interface size (a dialog window starts again from Android's own size). */
@Composable
fun ScaledDialog(
    onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(),
    /** Read controller buttons by their letters, like the rest of Delphis (off only where the dialog does it itself). */
    translateKeys: Boolean = true,
    content: @Composable () -> Unit,
) {
    val f = LocalUiScale.current
    Dialog(onDismissRequest, properties) {
        val body: @Composable () -> Unit = { if (translateKeys) PadKeysBox(onBack = onDismissRequest, content = content) else content() }
        if (f == 1f) body()
        else {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalUiScale provides f, LocalDensity provides Density(d.density * f, d.fontScale), content = body)
        }
    }
}

/**
 * A pop-up window (dialog, menu) gets the controller's buttons straight from Android, without the letter
 * translation the screens use, so on a Switch controller its A / B were swapped. This reads each button
 * as the screens do, presses the highlighted item for an unused A, closes with an unused B ([onBack]),
 * and never lets a controller button reach Android's own meaning.
 */
@Composable
fun PadKeysBox(onBack: () -> Unit, content: @Composable () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember { Prefs(ctx) }
    val oneScreen = androidx.compose.runtime.remember { DeviceInfo.oneScreen(ctx) }
    val inside = androidx.compose.runtime.remember { booleanArrayOf(false) }
    val back = androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.onPreviewKeyEvent { e ->
            if (inside[0]) return@onPreviewKeyEvent false
            val raw = e.nativeKeyEvent
            if (!io.github.hiramrg.delphis.platform.PadKeys.isPadButton(raw)) return@onPreviewKeyEvent false
            val t = io.github.hiramrg.delphis.platform.PadKeys.translate(raw, prefs, oneScreen)
            inside[0] = true
            try {
                if (!view.dispatchKeyEvent(t)) {
                    val fb = io.github.hiramrg.delphis.platform.PadKeys.fallback(t)
                    when {
                        fb == null -> Unit
                        fb.keyCode == android.view.KeyEvent.KEYCODE_BACK -> { if (!view.dispatchKeyEvent(fb) && fb.action == android.view.KeyEvent.ACTION_UP) back.value() }
                        else -> view.dispatchKeyEvent(fb)
                    }
                }
            } finally { inside[0] = false }
            true
        },
        propagateMinConstraints = true,
    ) { content() }
}
