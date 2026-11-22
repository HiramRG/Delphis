package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/*
 * No emoji or symbol characters reach the screen: phones draw them from their own fonts, so they looked
 * different on every brand, and some (▣ ✦ ✎ …) showed as crossed boxes on a Samsung tablet. Each one is
 * drawn as a Material icon from inside Delphis instead, in the text's colour, wherever a string has it.
 * Delphis' Text (below) does this for every Text in the app; HintText does the same for hint lines.
 */

/** Every symbol Delphis uses, and the icon drawn for it. */
val SYMBOL_ICONS: Map<String, ImageVector> = mapOf(
    "🏆" to Icons.Filled.EmojiEvents, "🎮" to Icons.Filled.SportsEsports, "🕹" to Icons.Filled.VideogameAsset,
    "⚙" to Icons.Filled.Settings, "💥" to Icons.Filled.BugReport, "🕒" to Icons.Filled.Schedule, "💾" to Icons.Filled.Save,
    "👆" to Icons.Filled.TouchApp, "☝" to Icons.Filled.Swipe, "🗑" to Icons.Filled.Delete, "⚠" to Icons.Filled.Warning,
    "ℹ" to Icons.Filled.Info, "☀" to Icons.Filled.LightMode, "🔍" to Icons.Filled.ZoomIn, "💣" to Icons.Filled.LocalFireDepartment,
    "✦" to Icons.Filled.AutoAwesome, "▣" to Icons.Filled.HighQuality, "▭" to Icons.Filled.AspectRatio, "✎" to Icons.Filled.Edit,
    "↶" to Icons.AutoMirrored.Filled.Undo, "↺" to Icons.Filled.Restore, "↻" to Icons.Filled.RestartAlt,
    "⇄" to Icons.Filled.SwapHoriz, "⇅" to Icons.Filled.SwapVert, "▲" to Icons.Filled.KeyboardArrowUp, "▼" to Icons.Filled.KeyboardArrowDown,
    "＋" to Icons.Filled.Add, "✓" to Icons.Filled.Check, "✕" to Icons.Filled.Close, "★" to Icons.Filled.Star, "☆" to Icons.Filled.StarBorder,
    "●" to Icons.Filled.FiberManualRecord, "▶" to Icons.Filled.PlayArrow, "◀" to Icons.AutoMirrored.Filled.ArrowBack,
    "⋯" to Icons.Filled.MoreHoriz, "⋮" to Icons.Filled.MoreVert, "☰" to Icons.Filled.Menu, "⌂" to Icons.Filled.Home,
    "⌕" to Icons.Filled.Search, "▤" to Icons.Filled.SearchOff, "⦸" to Icons.Filled.CloudOff, "▦" to Icons.Filled.Map,
    "◎" to Icons.Filled.Mouse, "◉" to Icons.Filled.RadioButtonChecked, "✥" to Icons.Filled.OpenWith, "⤴" to Icons.Filled.North,
    "⤒" to Icons.Filled.VerticalAlignTop, "⤓" to Icons.Filled.VerticalAlignBottom, "⟲" to Icons.Filled.RotateLeft, "⟳" to Icons.Filled.RotateRight,
    "⌫" to Icons.AutoMirrored.Filled.Backspace, "📷" to Icons.Filled.PhotoCamera,
)

/** Icons with a colour of their own (a warning stays amber whatever the text colour). */
private val SYMBOL_COLOURS = mapOf("⚠" to Color(0xFFF5B342))

/** PlayStation's shapes in running text (key names): drawn as shapes, like on the buttons. */
private val SHAPES = setOf("○", "□", "△")

/** Characters that are only text styling and never drawn (the emoji variation selector). */
private const val VS16 = '️'

/** True if [text] has a symbol Delphis draws as an icon. */
fun hasSymbols(text: String): Boolean = text.any { it == VS16 || it.isSurrogate() || SYMBOL_ICONS.containsKey(it.toString()) || it.toString() in SHAPES }

/** Splits [text] into plain runs and symbols (each symbol one entry). */
fun symbolRuns(text: String): List<Pair<Boolean, String>> {
    val out = mutableListOf<Pair<Boolean, String>>()
    val buf = StringBuilder()
    var i = 0
    while (i < text.length) {
        if (text[i] == VS16) { i++; continue }
        val cp = text.codePointAt(i)
        val ch = String(Character.toChars(cp))
        if (ch in SYMBOL_ICONS || ch in SHAPES) {
            if (buf.isNotEmpty()) { out += false to buf.toString(); buf.clear() }
            out += true to ch
        } else buf.append(ch)
        i += Character.charCount(cp)
    }
    if (buf.isNotEmpty()) out += false to buf.toString()
    return out
}

/** The icon slot for symbol [s], sized to the text: an icon a little taller than capitals, in [tint]. */
fun symbolInline(s: String, fontSize: TextUnit, tint: Color): InlineTextContent {
    val w = (fontSize.value * 1.25f).sp
    return InlineTextContent(Placeholder(w, w, PlaceholderVerticalAlign.TextCenter)) {
        val colour = SYMBOL_COLOURS[s] ?: tint
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val icon = SYMBOL_ICONS[s]
            if (icon != null) Icon(icon, null, tint = colour, modifier = Modifier.fillMaxSize())
            else Canvas(Modifier.fillMaxSize()) { drawCenteredGlyph(if (s == "○") "○" else s, size.width / 2f, size.height / 2f, size.height * 0.8f, colour) }
        }
    }
}

/**
 * Delphis' Text: the same as Material's, except that emoji and symbols in [text] are drawn as icons from
 * inside the app (see [SYMBOL_ICONS]). Every screen uses this one.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    if (!hasSymbols(text)) {
        androidx.compose.material3.Text(
text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style,
        )
        return
    }
    val size = if (fontSize.isSpecified) fontSize else if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val tint = if (color != Color.Unspecified) color else if (style.color != Color.Unspecified) style.color else LocalContentColor.current
    val inline = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        symbolRuns(text).forEachIndexed { n, (isSymbol, s) ->
            if (!isSymbol) append(s) else { val id = "s$n"; appendInlineContent(id, s); inline[id] = symbolInline(s, size, tint) }
        }
    }
    androidx.compose.material3.Text(
text = annotated, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, inlineContent = inline, onTextLayout = onTextLayout ?: {}, style = style,
    )
}

/** The same for styled text (and text that brings its own inline icons): symbols in plain runs aren't converted here. */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, inlineContent = inlineContent, onTextLayout = onTextLayout, style = style,
)

/** [text] without its symbols, for places that can't draw icons (Android views, canvas labels). */
fun plainText(text: String): String = symbolRuns(text).filter { !it.first }.joinToString("") { it.second }
    .replace(Regex("""\s{2,}"""), " ").trim()
