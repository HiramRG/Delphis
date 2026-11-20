package io.github.hiramrg.delphis.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A slider that works the same with a controller as with touch:
 * - the whole slider is ONE stop for the cursor, with the normal focus ring;
 * - D-pad left / right move it by [step] and save right away (each press counts as finished);
 * - D-pad up / down are left alone, so they move the cursor to the row above / below instead of
 *   changing the value (Android's own slider grabs them, which trapped the cursor).
 */
@Composable
fun PadSlider(
    value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, step: Float = (valueRange.endInclusive - valueRange.start) / 20f,
    steps: Int = 0, onValueChangeFinished: (() -> Unit)? = null, thumbColor: Color? = null,
) {
    val c = LocalWii.current
    fun nudge(dir: Int) {
        val raw = value + dir * step
        // Snap to the step grid (counted from the start), so 0 → 5 → 10… and never 7.3.
        val snapped = valueRange.start + ((raw - valueRange.start) / step).roundToInt() * step
        val v = snapped.coerceIn(valueRange.start, valueRange.endInclusive)
        if (v != value) { onValueChange(v); onValueChangeFinished?.invoke() }
    }
    Box(
        modifier
            .focusRing(RoundedCornerShape(50), width = 2.5.dp)
            .onPreviewKeyEvent { e ->
                if (!enabled) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AndroidKey.KEYCODE_DPAD_LEFT -> { if (e.type == KeyEventType.KeyDown) nudge(-1); true }
                    AndroidKey.KEYCODE_DPAD_RIGHT -> { if (e.type == KeyEventType.KeyDown) nudge(1); true }
                    else -> false
                }
            }
            .focusable(enabled)
            .padding(horizontal = 6.dp),
    ) {
        Slider(
            value = value, onValueChange = onValueChange, enabled = enabled, valueRange = valueRange, steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            // Touch only: the Box above is the cursor stop, so the slider itself never takes focus.
            modifier = Modifier.focusProperties { canFocus = false },
            colors = SliderDefaults.colors(
                thumbColor = thumbColor ?: c.text, activeTrackColor = c.accent, inactiveTrackColor = c.cardBorder,
                activeTickColor = c.onAccent, inactiveTickColor = c.textDim,
            ),
        )
    }
}
