package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/**
 * Which controller the hints are drawn for. Delphis works by position (the Thor's: A right, B bottom, X top,
 * Y left), so on an Xbox or PlayStation controller the hint shows the button in that place as the controller
 * prints it (Delphis' A is Xbox B / PlayStation ○). One screen with no controller: touch, no button icons.
 */
object PadGlyphs {
    /** null = no controller (touch controls). The Thor (two screens) always uses its own letters. */
    var labels by mutableStateOf<io.github.hiramrg.delphis.core.ButtonLabels?>(io.github.hiramrg.delphis.core.ButtonLabels.NINTENDO)
        internal set
    val touch: Boolean get() = labels == null

    /** Reads the connected controller again (one screen); call when controllers come and go. */
    fun update(oneScreen: Boolean) {
        labels = if (!oneScreen) io.github.hiramrg.delphis.core.ButtonLabels.NINTENDO
        else io.github.hiramrg.delphis.platform.DeviceInfo.gamepads().firstOrNull()?.let { io.github.hiramrg.delphis.core.ButtonLabels.forVendor(it.vendorId) }
    }

    /** [padText] with a controller, [touchText] without one. */
    fun or(padText: String, touchText: String): String = if (touch) touchText else padText

    /** Delphis' button [key] (the Thor's letters: "A", "L1"…) as the connected controller prints it. */
    fun letter(key: String): String = when (labels) {
        io.github.hiramrg.delphis.core.ButtonLabels.XBOX -> when (key) {
            "A" -> "B"; "B" -> "A"; "X" -> "Y"; "Y" -> "X"
            "L1" -> "LB"; "R1" -> "RB"; "L2" -> "LT"; "R2" -> "RT"; "L3" -> "LS"; "R3" -> "RS"
            else -> key
        }
        io.github.hiramrg.delphis.core.ButtonLabels.PLAYSTATION -> when (key) { "A" -> "○"; "B" -> "✕"; "X" -> "△"; "Y" -> "□"; else -> key }
        else -> key
    }

    /** The connected controller's colour for a face button on the controls badges (null = the Thor's own colours). */
    fun badgeColor(c: io.github.hiramrg.delphis.core.ThorControl): Color? = when (labels) {
        io.github.hiramrg.delphis.core.ButtonLabels.XBOX -> when (c) {
            io.github.hiramrg.delphis.core.ThorControl.FACE_EAST -> Glow.Red
            io.github.hiramrg.delphis.core.ThorControl.FACE_SOUTH -> Glow.Green
            io.github.hiramrg.delphis.core.ThorControl.FACE_NORTH -> Glow.Amber
            io.github.hiramrg.delphis.core.ThorControl.FACE_WEST -> Glow.Sky
            else -> null
        }
        io.github.hiramrg.delphis.core.ButtonLabels.PLAYSTATION -> when (c) {
            io.github.hiramrg.delphis.core.ThorControl.FACE_EAST -> Glow.Red
            io.github.hiramrg.delphis.core.ThorControl.FACE_SOUTH -> Glow.Sky
            io.github.hiramrg.delphis.core.ThorControl.FACE_NORTH -> Glow.Green
            io.github.hiramrg.delphis.core.ThorControl.FACE_WEST -> Color(0xFFD877C0)
            else -> null
        }
        else -> null
    }

    /** Delphis' button [key] as the connected controller prints it, with that button's colour (null = no colour). */
    @Composable
    fun face(key: String): Pair<String, Color?> {
        val dark = LocalWii.current.dark
        val colour = when (labels) {
            io.github.hiramrg.delphis.core.ButtonLabels.XBOX -> when (key) {
                "A" -> Color(0xFFE0503A); "B" -> Color(0xFF3FA864); "X" -> if (dark) Color(0xFFE0C23A) else Color(0xFFB8960E); "Y" -> Color(0xFF3A7DE0)
                else -> null
            }
            io.github.hiramrg.delphis.core.ButtonLabels.PLAYSTATION -> when (key) {
                "A" -> Color(0xFFE0503A); "B" -> Color(0xFF5B8DEF); "X" -> Color(0xFF3FB8A0); "Y" -> Color(0xFFD877C0)
                else -> null
            }
            else -> null
        }
        return letter(key) to colour
    }
}

/** This control's name as the connected controller prints it (the Thor's letters on the Thor). */
val io.github.hiramrg.delphis.core.ThorControl.padLabel: String get() = PadGlyphs.letter(shortLabel)

/**
 * A button icon: [key] is "A", "L1", "⊖", "DPAD" (D-pad) or "DPAD_LR" (D-pad, left / right), in Delphis' (the
 * Thor's) letters; it's drawn as the connected controller prints that button (see [PadGlyphs]), and not at all
 * with touch controls.
 */
@Composable
fun KeyIcon(key: String, color: Color = LocalWii.current.textDim, size: Dp = 18.dp) {
    if (PadGlyphs.touch) return
    when (key) {
        "DPAD", "DPAD_LR" -> DpadIcon(color, size, leftRight = key == "DPAD_LR")
        else -> {
            val (label, own) = PadGlyphs.face(key)
            // The controller's colour for its letters, unless the icon sits on a coloured button.
            val c = LocalWii.current
            val tint = if (own != null && (color == c.textDim || color == c.text)) own else color
            val round = label.length == 1
            Box(
                Modifier.height(size).then(if (round) Modifier.width(size) else Modifier.widthIn(min = size))
                    .border(1.5.dp, color, RoundedCornerShape(50)).padding(horizontal = if (round) 0.dp else 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = tint, maxLines = 1, fontWeight = FontWeight.Black,
                    fontSize = (size.value * when (label) { "⊖" -> 0.62f; "○", "✕", "△", "□" -> 0.6f; else -> 0.53f }).sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
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

/** [text] without its button glyphs, for touch controls. */
fun touchWords(text: String): String = tokens(text).filter { !it.first }.joinToString("") { it.second }
    .replace(Regex("""\s+"""), " ").replace(Regex("""(\s*·\s*)+"""), " · ").trim().trim('·', ' ')

/** True if [text] has button glyphs (Ⓐ, ✚, L1…) that [HintText] draws as icons. */
fun hasKeys(text: String) = tokens(text).any { it.first }

/** Text with its button glyphs drawn as icons: "Ⓐ Play · Ⓧ Game page · L1 Library". */
@Composable
fun HintText(
    text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp,
    color: Color = LocalWii.current.textDim, fontWeight: FontWeight? = null, textAlign: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified, lineHeight: TextUnit = TextUnit.Unspecified,
) {
    // Touch controls: no button icons; what's left is tidied ("↺ Earlier autosaves · R2" → "↺ Earlier autosaves").
    if (PadGlyphs.touch) {
        val words = touchWords(text)
        if (words.any { it.isLetter() }) Text(
            words, modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign,
            letterSpacing = letterSpacing, lineHeight = lineHeight, maxLines = 2, onTextLayout = { TextFit.check(words, it) },
        )
        return
    }
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
    Text(annotated, modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign, letterSpacing = letterSpacing, lineHeight = lineHeight, inlineContent = inline, maxLines = 2, onTextLayout = { TextFit.check(text, it) })
}

/** Splits a button label "Ⓐ Play" into ("Play", "A"); labels without a button glyph give null. */
fun splitButtonLabel(text: String): Pair<String, String>? {
    val first = text.take(1)
    val key = GLYPHS[first] ?: return null
    return text.drop(1).trim() to key
}
