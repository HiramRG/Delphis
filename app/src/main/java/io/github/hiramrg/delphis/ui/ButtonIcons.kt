package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * One set of button icons for every screen (the style of the game page's Edit / Play buttons):
 * a thin grey outline, 18 dp tall — circles for A B X Y, pills for L1 R1 L3 R3 ⊖ Start, and a D-pad
 * drawn with the same outline. On a blue button the outline takes the button's text colour.
 * Buttons put the icon after the word ("Done Ⓐ"); hint lines put it before ("Ⓐ Play").
 */

/** Glyphs used in hint strings, and the button each stands for. */
private val GLYPHS = mapOf("Ⓐ" to "A", "Ⓑ" to "B", "Ⓧ" to "X", "Ⓨ" to "Y", "✚" to "DPAD", "⊖" to "⊖")
// "Select" isn't here: the word stays text ("Ⓐ Select"); the Select button is written ⊖.
private val WORD_KEYS = setOf("L1", "R1", "L2", "R2", "L3", "R3", "Start")

/** A button icon: [key] is "A", "L1", "⊖", "DPAD" (D-pad) or "DPAD_LR" (D-pad, left / right). */
@Composable
fun KeyIcon(key: String, color: Color = LocalWii.current.textDim, size: Dp = 18.dp) {
    when (key) {
        "DPAD", "DPAD_LR" -> DpadIcon(color, size, leftRight = key == "DPAD_LR")
        else -> {
            val round = key.length == 1
            Box(
                Modifier.height(size).then(if (round) Modifier.width(size) else Modifier.widthIn(min = size))
                    .border(1.5.dp, color, RoundedCornerShape(50)).padding(horizontal = if (round) 0.dp else 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    key, color = color, maxLines = 1, fontWeight = FontWeight.Black,
                    fontSize = (size.value * if (key == "⊖") 0.62f else 0.53f).sp,
                    style = TextStyle(fontFamily = AppFont, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                )
            }
        }
    }
}

/** Kept for existing callers: the same icon, dark on blue buttons. */
@Composable
fun KeyBadge(key: String, dark: Boolean = false) = KeyIcon(key, if (dark) Color(0xFF0A2533).copy(alpha = 0.75f) else LocalWii.current.textDim)

@Composable
private fun DpadIcon(color: Color, size: Dp, leftRight: Boolean) {
    Canvas(Modifier.size(size)) {
        val u = this.size.minDimension / 18f
        val p = Path().apply {
            moveTo(6.6f * u, 1.5f * u); lineTo(11.4f * u, 1.5f * u); lineTo(11.4f * u, 6.6f * u); lineTo(16.5f * u, 6.6f * u)
            lineTo(16.5f * u, 11.4f * u); lineTo(11.4f * u, 11.4f * u); lineTo(11.4f * u, 16.5f * u); lineTo(6.6f * u, 16.5f * u)
            lineTo(6.6f * u, 11.4f * u); lineTo(1.5f * u, 11.4f * u); lineTo(1.5f * u, 6.6f * u); lineTo(6.6f * u, 6.6f * u); close()
        }
        drawPath(p, color, style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
        if (leftRight) {
            drawPath(Path().apply { moveTo(3.2f * u, 9f * u); lineTo(5.2f * u, 7.4f * u); lineTo(5.2f * u, 10.6f * u); close() }, color)
            drawPath(Path().apply { moveTo(14.8f * u, 9f * u); lineTo(12.8f * u, 7.4f * u); lineTo(12.8f * u, 10.6f * u); close() }, color)
        }
    }
}

/** Splits "Ⓐ Play · L1 Library" into text and icons. */
private fun tokens(text: String): List<Pair<Boolean, String>> {
    val out = mutableListOf<Pair<Boolean, String>>()
    val buf = StringBuilder()
    var i = 0
    while (i < text.length) {
        val ch = text.substring(i, i + 1)
        val glyph = GLYPHS[ch]
        val word = WORD_KEYS.firstOrNull { w ->
            text.startsWith(w, i) && (i == 0 || !text[i - 1].isLetterOrDigit()) &&
                (i + w.length == text.length || !text[i + w.length].isLetterOrDigit())
        }
        when {
            glyph != null -> { if (buf.isNotEmpty()) { out += false to buf.toString(); buf.clear() }; out += true to glyph; i++ }
            word != null -> { if (buf.isNotEmpty()) { out += false to buf.toString(); buf.clear() }; out += true to word; i += word.length }
            else -> { buf.append(text[i]); i++ }
        }
    }
    if (buf.isNotEmpty()) out += false to buf.toString()
    return out
}

/** True if [text] has button glyphs (Ⓐ, ✚, L1…) that [HintText] draws as icons. */
fun hasKeys(text: String) = tokens(text).any { it.first }

/** Text with its button glyphs drawn as icons: "Ⓐ Play · Ⓧ Game page · L1 Library". */
@Composable
fun HintText(
    text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp,
    color: Color = LocalWii.current.textDim, fontWeight: FontWeight? = null, textAlign: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    val parts = tokens(text)
    // The icon is sized from the text size the same way as its slot, so it never spills onto the words.
    val iconDp = with(androidx.compose.ui.platform.LocalDensity.current) { (fontSize * 1.4f).toDp() }
    val inline = mutableMapOf<String, InlineTextContent>()
    val annotated: AnnotatedString = buildAnnotatedString {
        parts.forEachIndexed { n, (isKey, s) ->
            if (!isKey) append(s)
            else {
                val id = "k$n"
                appendInlineContent(id, s)
                val w = if (s.length == 1 || s.startsWith("DPAD")) 1.5f else 0.42f * s.length + 0.9f
                inline[id] = InlineTextContent(Placeholder((fontSize.value * w).sp, (fontSize.value * 1.5f).sp, PlaceholderVerticalAlign.TextCenter)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KeyIcon(s, color, iconDp) }
                }
            }
        }
    }
    Text(annotated, modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign, letterSpacing = letterSpacing, inlineContent = inline, maxLines = 2, onTextLayout = { TextFit.check(text, it) })
}

/** Splits a button label "Ⓐ Play" into ("Play", "A"); labels without a button glyph give null. */
fun splitButtonLabel(text: String): Pair<String, String>? {
    val first = text.take(1)
    val key = GLYPHS[first] ?: return null
    return text.drop(1).trim() to key
}
