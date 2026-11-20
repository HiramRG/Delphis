package io.github.hiramrg.delphis.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/*
 * Button letters drawn exactly in the middle of their circle or pill. Text laid out by Compose is centred by its
 * line box (ascent + descent + font padding), which leaves a letter a little high or low and depends on the font.
 * Here the letter's own ink is measured and centred, both ways. PlayStation's shapes and the Thor / Switch
 * − / + are drawn as shapes, so they don't depend on the device's fonts either.
 */

/** Glyphs drawn as shapes instead of text. */
val SHAPE_GLYPHS = setOf("✕", "○", "□", "△", "⊖", "⊕", "−", "+")

private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 900, false)
    textAlign = android.graphics.Paint.Align.LEFT
}
private val bounds = android.graphics.Rect()

/** How wide [text] is at [fontPx] (for sizing pills around it). */
fun glyphWidth(text: String, fontPx: Float): Float =
    if (text in SHAPE_GLYPHS) fontPx else synchronized(paint) { paint.textSize = fontPx; paint.measureText(text) }

/**
 * Draws [text] centred on ([cx], [cy]) at [fontPx]. A single character is centred by its own ink; a word is
 * centred across by its ink and up-and-down by the capital letters' height, so "Options" sits where "Menu" does.
 */
fun DrawScope.drawCenteredGlyph(text: String, cx: Float, cy: Float, fontPx: Float, color: Color) {
    if (text in SHAPE_GLYPHS) { drawShapeGlyph(text, cx, cy, fontPx, color); return }
    drawIntoCanvas { canvas ->
        synchronized(paint) {
            paint.textSize = fontPx
            paint.color = color.toArgb()
            paint.getTextBounds(text, 0, text.length, bounds)
            val x = cx - (bounds.left + bounds.width() / 2f)
            val y = if (text.length == 1) cy - (bounds.top + bounds.height() / 2f) else {
                val cap = android.graphics.Rect().also { paint.getTextBounds("H", 0, 1, it) }
                cy - (cap.top + cap.height() / 2f)
            }
            canvas.nativeCanvas.drawText(text, x, y, paint)
        }
    }
}

/** ✕ ○ □ △ (PlayStation) and − + (⊖ ⊕ on the Thor and Switch) as shapes, [size] tall, centred on ([cx], [cy]). */
private fun DrawScope.drawShapeGlyph(g: String, cx: Float, cy: Float, size: Float, color: Color) {
    val r = size * 0.42f
    val stroke = Stroke(width = size * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (g) {
        "✕" -> {
            val a = r * 0.78f
            drawLine(color, Offset(cx - a, cy - a), Offset(cx + a, cy + a), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(cx - a, cy + a), Offset(cx + a, cy - a), stroke.width, StrokeCap.Round)
        }
        "○" -> drawCircle(color, r * 0.74f, Offset(cx, cy), style = stroke)
        "□" -> {
            val a = r * 0.76f
            drawRect(color, Offset(cx - a, cy - a), androidx.compose.ui.geometry.Size(2 * a, 2 * a), style = stroke)
        }
        "△" -> {
            // Centred by its own height (the triangle's middle, not its centroid, sits on cy).
            val h = r * 1.62f; val half = h / 1.732f
            val p = Path().apply { moveTo(cx, cy - h / 2f); lineTo(cx + half, cy + h / 2f); lineTo(cx - half, cy + h / 2f); close() }
            drawPath(p, color, style = stroke)
        }
        "⊖", "−" -> drawLine(color, Offset(cx - r * 0.8f, cy), Offset(cx + r * 0.8f, cy), stroke.width, StrokeCap.Round)
        "⊕", "+" -> {
            drawLine(color, Offset(cx - r * 0.8f, cy), Offset(cx + r * 0.8f, cy), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(cx, cy - r * 0.8f), Offset(cx, cy + r * 0.8f), stroke.width, StrokeCap.Round)
        }
    }
}
