package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp
import io.github.hiramrg.delphis.core.EmulatedController
import io.github.hiramrg.delphis.core.Preset
import io.github.hiramrg.delphis.core.Target
import io.github.hiramrg.delphis.core.ThorControl

/** What a preset's illustration shows: how you'd be holding the Wii controller. */
enum class PresetPicture { NUNCHUK, SIDEWAYS, WHEEL_STICK, WHEEL_TILT, CLASSIC, GAMECUBE, GYRO, PRECISION }

fun Preset.picture(): PresetPicture {
    val gyro = mappings.firstNotNullOfOrNull { it.target as? Target.GyroPointer }
    return when {
        gyro != null -> if (gyro.precision) PresetPicture.PRECISION else PresetPicture.GYRO
        controller == EmulatedController.WIIMOTE_WHEEL ->
            if (mappings.any { it.control == ThorControl.MOTION }) PresetPicture.WHEEL_TILT else PresetPicture.WHEEL_STICK
        controller == EmulatedController.WIIMOTE_SIDEWAYS -> PresetPicture.SIDEWAYS
        controller == EmulatedController.CLASSIC -> PresetPicture.CLASSIC
        controller == EmulatedController.GAMECUBE -> PresetPicture.GAMECUBE
        else -> PresetPicture.NUNCHUK
    }
}

/** The preset's illustration (64×48), in the current theme's colours. */
@Composable
fun PresetIcon(preset: Preset, modifier: Modifier) {
    val c = LocalWii.current
    val kind = preset.picture()
    val image = remember(kind, c.dark) { presetVector(kind, outline = c.text, body = c.card, accent = c.accent, light = c.cardBorder) }
    Image(image, contentDescription = null, modifier = modifier)
}

// --------------------------------------------------------------------------------------- drawing

private fun rr(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r},$y H${x + w - r} A$r,$r 0 0 1 ${x + w},${y + r} V${y + h - r} A$r,$r 0 0 1 ${x + w - r},${y + h} " +
        "H${x + r} A$r,$r 0 0 1 $x,${y + h - r} V${y + r} A$r,$r 0 0 1 ${x + r},$y Z"

private fun circ(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy A$r,$r 0 1 1 ${cx + r},$cy A$r,$r 0 1 1 ${cx - r},$cy Z"

private fun presetVector(kind: PresetPicture, outline: Color, body: Color, accent: Color, light: Color): ImageVector {
    val b = ImageVector.Builder(defaultWidth = 64.dp, defaultHeight = 48.dp, viewportWidth = 64f, viewportHeight = 48f)

    fun fill(d: String, color: Color, alpha: Float = 1f) = b.addPath(addPathNodes(d), fill = SolidColor(color), fillAlpha = alpha)
    fun shape(d: String, fillColor: Color? = body, width: Float = 1.8f) = b.addPath(
        addPathNodes(d), fill = fillColor?.let { SolidColor(it) }, stroke = SolidColor(outline),
        strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    )
    fun line(d: String, color: Color, width: Float, alpha: Float = 1f) = b.addPath(
        addPathNodes(d), stroke = SolidColor(color), strokeAlpha = alpha,
        strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    )
    fun remote(x: Float, y: Float, rotate: Float = 0f, scale: Float = 1f) = b.group(rotate = rotate, scaleX = scale, scaleY = scale, translationX = x, translationY = y) {
        shape(rr(-6f, -19f, 12f, 38f, 6f))
        fill(rr(-3.2f, -13.5f, 6.4f, 2.2f, 0.6f), outline)
        fill(rr(-1.1f, -15.6f, 2.2f, 6.4f, 0.6f), outline)
        fill(circ(0f, -4f, 2.6f), accent)
        fill(circ(0f, 7f, 1.3f), light)
        fill(circ(0f, 11f, 1.3f), light)
    }
    fun nunchuk(x: Float, y: Float, rotate: Float) = b.group(rotate = rotate, translationX = x, translationY = y) {
        shape("M-6 -12 C-6 -18 6 -18 6 -12 L5 10 C4.5 16 -4.5 16 -5 10 Z")
        fill(circ(0f, -10f, 3.4f), outline)
    }

    when (kind) {
        PresetPicture.NUNCHUK -> {
            listOf(25f to 10.5f, 29f to 8.4f, 33.5f to 8.4f, 37.5f to 10.6f).forEach { (x, y) -> fill(circ(x, y, 0.9f), outline) }
            nunchuk(20f, 26f, -12f)
            remote(44f, 24f, 10f)
        }
        PresetPicture.SIDEWAYS -> {
            remote(32f, 24f, -90f, 1.15f)
            line("M10 40 H18 M46 40 H54", light, 2f)
        }
        PresetPicture.WHEEL_STICK -> {
            line(circ(32f, 24f, 19f), outline, 3.2f)
            line(circ(32f, 24f, 15.5f), accent, 1.2f)
            remote(32f, 24f, -90f, 0.78f)
        }
        PresetPicture.WHEEL_TILT -> {
            line(circ(32f, 24f, 17f), outline, 3.2f)
            remote(32f, 24f, -90f, 0.7f)
            line("M8 12 A26 26 0 0 0 8 36", accent, 2.4f)
            line("M56 12 A26 26 0 0 1 56 36", accent, 2.4f)
            line("M5 33 L8 37 L12 34.5", accent, 2.2f)
        }
        PresetPicture.CLASSIC -> {
            shape("M10 16 C10 11 15 10 20 10 H44 C49 10 54 11 54 16 L56 32 C57 39 50 41 46 36 L42 32 H22 L18 36 C14 41 7 39 8 32 Z")
            fill(rr(15f, 18.5f, 9f, 3f, 0.8f), outline)
            fill(rr(18f, 15.5f, 3f, 9f, 0.8f), outline)
            fill(circ(44f, 16f, 2.1f), accent)
            fill(circ(40f, 20f, 2.1f), outline)
            fill(circ(48f, 20f, 2.1f), outline)
            fill(circ(44f, 24f, 2.1f), outline)
            shape(circ(26f, 29f, 3f), light, 1f)
            shape(circ(38f, 29f, 3f), light, 1f)
        }
        PresetPicture.GAMECUBE -> {
            shape("M12 18 C12 11 18 9 24 10 L40 10 C46 9 52 11 52 18 C54 26 54 34 49 37 C45 39 42 35 40 31 H24 C22 35 19 39 15 37 C10 34 10 26 12 18 Z")
            fill(circ(21f, 19f, 4f), outline)
            fill(circ(44f, 20f, 4.4f), accent)
            fill(circ(37.5f, 25f, 2.2f), outline)
            fill(circ(50f, 15f, 2f), outline)
            fill(rr(25.5f, 25.5f, 6f, 2f, 0.6f), outline)
            fill(rr(27.5f, 23.5f, 2f, 6f, 0.6f), outline)
        }
        PresetPicture.GYRO -> {
            remote(26f, 26f, -18f)
            line("M38 10 A14 14 0 0 1 50 22", accent, 2.4f)
            line("M40 4 A22 22 0 0 1 57 21", accent, 2.4f, alpha = 0.55f)
            line(circ(52f, 36f, 3.2f), outline, 1.8f)
            fill(circ(52f, 36f, 1f), outline)
        }
        PresetPicture.PRECISION -> {
            remote(24f, 26f, -18f)
            line(circ(47f, 24f, 10f), accent, 2.2f)
            line("M47 10 V17 M47 31 V38 M33 24 H40 M54 24 H61", accent, 2.2f)
            fill(circ(47f, 24f, 1.8f), outline)
        }
    }
    return b.build()
}
