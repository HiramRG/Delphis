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
 * Which controller the hints are drawn for: Player 1's (the first in the Players window, the one Dolphin uses as
 * player 1). Every controller works by its own letters (A confirms, B goes back), so a hint "Ⓐ Play" is drawn as
 * Player 1's A: green A on Xbox, ✕ on PlayStation, A on a Switch controller or the Thor. During a game the in-game
 * menu keeps the Player 1 the game started with. One screen with no controller: touch, no button icons.
 */
object PadGlyphs {
    /** null = no controller (touch controls). The Thor (two screens) always uses its own letters unless an external controller is Player 1. */
    var labels by mutableStateOf<io.github.hiramrg.delphis.core.ButtonLabels?>(io.github.hiramrg.delphis.core.ButtonLabels.NINTENDO)
        internal set
    val touch: Boolean get() = labels == null

    /** The controls map draws the Thor (true) or a plain controller (one screen: phones, tablets, other handhelds). */
    var thorBody by mutableStateOf(true)
        internal set

    /** Which controller the map draws off the Thor (null = Thor or touch). */
    var brand by mutableStateOf<PadBrand?>(null)
        internal set

    /** Set by the activities: needed to read the Players order and what the "press A" check learned. */
    var prefs: io.github.hiramrg.delphis.platform.Prefs? = null
    /** The game whose Players order applies (the game page's game); null = the order controllers connected in. */
    var gameKey: String? = null
    /** During a game: the controller that was Player 1 when it started (its key), kept until the game ends. */
    private var pinned: String? = null
    private var oneScreenNow = false

    /** Player 1 now: (key, vendor, built in) of the first connected controller in the Players order. */
    private fun playerOne(): Triple<String, Int, Boolean>? {
        val pr = prefs ?: return io.github.hiramrg.delphis.platform.DeviceInfo.gamepads().firstOrNull()?.let { Triple(it.descriptor ?: "", it.vendorId, false) }
        val pads = runCatching { io.github.hiramrg.delphis.platform.Pads.detect(pr) }.getOrDefault(emptyList())
        if (pads.isEmpty()) return null
        val devices = io.github.hiramrg.delphis.platform.DeviceInfo.gamepads()
        fun vendor(key: String) = devices.firstOrNull { (it.descriptor ?: "id${it.id}") == key }?.vendorId ?: 0
        pinned?.let { k -> pads.firstOrNull { it.key == k }?.let { return Triple(it.key, vendor(it.key), it.builtIn) } }
        val setup = gameKey?.let { pr.playerSetup(it) } ?: io.github.hiramrg.delphis.core.PlayerSetup(emptyList(), emptySet())
        val p1 = io.github.hiramrg.delphis.core.Players.arranged(pads, setup).firstOrNull { it.key !in setup.out } ?: pads.first()
        return Triple(p1.key, vendor(p1.key), p1.builtIn)
    }

    /** Reads Player 1 again; call when controllers come and go, the Players order changes or a game starts / ends. */
    fun update(oneScreen: Boolean) {
        oneScreenNow = oneScreen
        thorBody = !oneScreen
        val fake = io.github.hiramrg.delphis.platform.TestHooks.padVendor
        val p1 = if (fake != null) Triple("test", fake, false) else playerOne()
        val (key, vendor, builtIn) = p1 ?: Triple("", 0, true)
        val nintendo = vendor == 0x057E || (vendor != 0x045E && vendor != 0x054C && prefs?.padNintendoLayout(key) == true)
        when {
            // The Thor itself (or a handheld's own controls), or two screens with nothing else as Player 1.
            p1 == null && !oneScreen || builtIn && !oneScreen -> { brand = null; labels = io.github.hiramrg.delphis.core.ButtonLabels.NINTENDO }
            p1 == null -> { brand = null; labels = null }
            else -> {
                brand = if (!oneScreen && builtIn) null else PadBrand.forVendor(vendor)
                labels = when {
                    builtIn -> io.github.hiramrg.delphis.core.ButtonLabels.NINTENDO
                    nintendo -> io.github.hiramrg.delphis.core.ButtonLabels.NINTENDO
                    vendor == 0x054C -> io.github.hiramrg.delphis.core.ButtonLabels.PLAYSTATION
                    else -> io.github.hiramrg.delphis.core.ButtonLabels.XBOX
                }
            }
        }
    }

    /** A game started with [padKey] as Player 1: the in-game menu keeps its icons until [unpin]. */
    fun pin(padKey: String?) { pinned = padKey; update(oneScreenNow) }
    fun unpin() { if (pinned != null) { pinned = null; update(oneScreenNow) } }

    /** [padText] with a controller, [touchText] without one. */
    fun or(padText: String, touchText: String): String = if (touch) touchText else padText

    /**
     * Delphis' button [key] ("A", "L1", "⊖"…: letters, as every controller is read by its own letters) as Player 1's
     * controller prints it: PlayStation ✕ ○ □ △, Xbox LB / View / Menu, Switch L / ZL / − / +.
     */
    fun letter(key: String): String = when (labels) {
        io.github.hiramrg.delphis.core.ButtonLabels.XBOX -> when (key) {
            "L1" -> "LB"; "R1" -> "RB"; "L2" -> "LT"; "R2" -> "RT"; "L3" -> "LS"; "R3" -> "RS"; "⊖" -> "View"; "⊕" -> "Menu"
            else -> key
        }
        io.github.hiramrg.delphis.core.ButtonLabels.PLAYSTATION -> when (key) { "A" -> "✕"; "B" -> "○"; "X" -> "□"; "Y" -> "△"; "⊖" -> "Create"; "⊕" -> "Options"; else -> key }
        else -> if (brand == PadBrand.SWITCH) when (key) { "L1" -> "L"; "R1" -> "R"; "L2" -> "ZL"; "R2" -> "ZR"; else -> key } else key
    }

    /**
     * A place on the controller (the Thor's letter for that place: A = right, B = bottom, X = top, Y = left) as
     * Player 1's controller prints the button there. Used by the controls map and its list, which show places.
     */
    fun atPlace(thorLetter: String): String = when (labels) {
        io.github.hiramrg.delphis.core.ButtonLabels.XBOX -> when (thorLetter) { "A" -> "B"; "B" -> "A"; "X" -> "Y"; "Y" -> "X"; else -> letter(thorLetter) }
        io.github.hiramrg.delphis.core.ButtonLabels.PLAYSTATION -> when (thorLetter) { "A" -> "○"; "B" -> "✕"; "X" -> "△"; "Y" -> "□"; else -> letter(thorLetter) }
        else -> letter(thorLetter)
    }

    /** Player 1's colour for a face button on the controls badges (null = the Thor's own colours). */
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

    /** Delphis' button [key] as Player 1's controller prints it, with that button's colour (null = no colour). */
    @Composable
    fun face(key: String): Pair<String, Color?> {
        val dark = LocalWii.current.dark
        val colour = when (labels) {
            io.github.hiramrg.delphis.core.ButtonLabels.XBOX -> when (key) {
                "A" -> Color(0xFF3FA864); "B" -> Color(0xFFE0503A); "X" -> Color(0xFF3A7DE0); "Y" -> if (dark) Color(0xFFE0C23A) else Color(0xFFB8960E)
                else -> null
            }
            io.github.hiramrg.delphis.core.ButtonLabels.PLAYSTATION -> when (key) {
                "A" -> Color(0xFF5B8DEF); "B" -> Color(0xFFE0503A); "X" -> Color(0xFFD877C0); "Y" -> Color(0xFF3FB8A0)
                else -> null
            }
            else -> null
        }
        return letter(key) to colour
    }
}

/** This control's name as the connected controller prints it (the Thor's letters on the Thor). */
val io.github.hiramrg.delphis.core.ThorControl.padLabel: String get() = PadGlyphs.atPlace(shortLabel)

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
            val d = androidx.compose.ui.platform.LocalDensity.current
            val fontPx = with(d) { size.toPx() } * when { label in SHAPE_GLYPHS -> 0.62f; round -> 0.56f; else -> 0.5f }
            // A pill fits its word with 4 dp each side; a circle is as wide as it is tall.
            val width = if (round) size else maxOf(size, with(d) { (glyphWidth(label, fontPx) / density).dp } + 8.dp)
            Canvas(Modifier.size(width, size)) {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color, topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(this.size.height / 2f), style = Stroke(stroke),
                )
                drawCenteredGlyph(label, this.size.width / 2f, this.size.height / 2f, fontPx, tint)
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
