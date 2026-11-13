package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.composed
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Wii-inspired colours: light grey pinstripes, white "channel" cards, sky blue highlights.
 * The dark variant keeps the same shapes on slate. Controller colours (A/B/X/Y, sticks...) live in
 * [controlColor] and are the same in both.
 */
data class WiiColors(
    val dark: Boolean,
    val bg: Color, val stripe: Color,
    val card: Color, val cardBorder: Color,
    val text: Color, val textDim: Color,
    val accent: Color, val accentBorder: Color, val onAccent: Color, val accentSoft: Color, val accentText: Color,
    val orange: Color,
    val bar: Color, val barBorder: Color,
    val sidebar: Color,
    /** "(suggested)" tag on the first preset of each game. */
    val green: Color = Color(0xFF2E7D32),
    // Channel buttons (soft white gradient, thin grey rim; primary = blue word + blue rim).
    val btnTop: Color = Color(0xFFFFFFFF), val btnMid: Color = Color(0xFFF4F6F8), val btnBottom: Color = Color(0xFFE6EAEE),
    val btnBorder: Color = Color(0xFFC3CCD5), val btnText: Color = Color(0xFF4A535E),
    val btnPrimaryText: Color = Color(0xFF0F7FB8), val btnPrimaryBorder: Color = Color(0xFF8CD3EF),
    val btnShine: Color = Color(0xFFFFFFFF),
)

val LightWii = WiiColors(
    dark = false,
    bg = Color(0xFFF3F5F7), stripe = Color(0xFFE9EDF1),
    card = Color(0xFFFFFFFF), cardBorder = Color(0xFFCDD4DC),
    text = Color(0xFF3A3F45), textDim = Color(0xFF6B737D),
    accent = Color(0xFF34BEED), accentBorder = Color(0xFF1A9BD7), onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE6F6FD), accentText = Color(0xFF0F7FB8),
    orange = Color(0xFFC96400),
    bar = Color(0xFFDDE2E7), barBorder = Color(0xFFC3CAD2),
    sidebar = Color(0xFFFFFFFF),
)

val DarkWii = WiiColors(
    dark = true,
    bg = Color(0xFF1D2127), stripe = Color(0xFF191C21),
    card = Color(0xFF2A3038), cardBorder = Color(0xFF4A535E),
    text = Color(0xFFE8ECF0), textDim = Color(0xFFA3ADB8),
    accent = Color(0xFF34BEED), accentBorder = Color(0xFF1A9BD7), onAccent = Color(0xFF0A2533),
    accentSoft = Color(0xFF1E3A48), accentText = Color(0xFF5CCBF2),
    orange = Color(0xFFF08A24),
    bar = Color(0xFF15181C), barBorder = Color(0xFF2E343C),
    sidebar = Color(0xFF22272D),
    green = Color(0xFF6BCB77),
    btnTop = Color(0xFF39414B), btnMid = Color(0xFF2C333B), btnBottom = Color(0xFF262C33),
    btnBorder = Color(0xFF525C67), btnText = Color(0xFFE8ECF0),
    btnPrimaryText = Color(0xFF5CCBF2), btnPrimaryBorder = Color(0xFF3B88A6),
    btnShine = Color(0x12FFFFFF),
)

/** M PLUS 1p, the rounded Japanese-style face closest to the Wii Menu's lettering (OFL, bundled). */
val AppFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(io.github.hiramrg.delphis.R.font.mplus1p_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(io.github.hiramrg.delphis.R.font.mplus1p_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(io.github.hiramrg.delphis.R.font.mplus1p_bold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(io.github.hiramrg.delphis.R.font.mplus1p_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(io.github.hiramrg.delphis.R.font.mplus1p_extrabold, FontWeight.ExtraBold),
    androidx.compose.ui.text.font.Font(io.github.hiramrg.delphis.R.font.mplus1p_black, FontWeight.Black),
)

/**
 * M PLUS 1p is ~11% wider than Roboto at the same size, so all text is drawn 10% smaller: every label
 * takes the same width it did before and no layout moves. (The controls map keeps its own sizing.)
 */
const val TEXT_SCALE = 0.9f

private fun appTypography(): androidx.compose.material3.Typography {
    val t = androidx.compose.material3.Typography()
    fun f(s: androidx.compose.ui.text.TextStyle) = s.copy(fontFamily = AppFont)
    return t.copy(
        displayLarge = f(t.displayLarge), displayMedium = f(t.displayMedium), displaySmall = f(t.displaySmall),
        headlineLarge = f(t.headlineLarge), headlineMedium = f(t.headlineMedium), headlineSmall = f(t.headlineSmall),
        titleLarge = f(t.titleLarge), titleMedium = f(t.titleMedium), titleSmall = f(t.titleSmall),
        bodyLarge = f(t.bodyLarge), bodyMedium = f(t.bodyMedium), bodySmall = f(t.bodySmall),
        labelLarge = f(t.labelLarge), labelMedium = f(t.labelMedium), labelSmall = f(t.labelSmall),
    )
}
private val AppTypography by lazy { appTypography() }

/** Option A "channel" button corners: 12 dp for buttons, 10 dp for chips. */
val ButtonShape = RoundedCornerShape(12.dp)
val ChipShape = RoundedCornerShape(10.dp)

/**
 * The Wii channel button look: soft top-to-bottom gradient, a thin rim, a white shine along the top
 * edge and a small shadow. [primary] (or [lit]) gives the blue rim; the word colour is up to the caller
 * ([channelText]).
 */
fun Modifier.channel(shape: androidx.compose.ui.graphics.Shape, primary: Boolean = false, lit: Boolean = false): Modifier = composed {
    val c = LocalWii.current
    this.shadow(1.5.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
        .clip(shape)
        .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to c.btnTop, 0.55f to c.btnMid, 1f to c.btnBottom))
        .drawBehind {
            val y = 1.5.dp.toPx() + 0.5.dp.toPx()
            val inset = size.height * 0.3f
            drawLine(c.btnShine, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
        }
        .border(1.5.dp, if (primary || lit) c.btnPrimaryBorder else c.btnBorder, shape)
}

/** The word colour on a channel button. */
@Composable
fun channelText(primary: Boolean, enabled: Boolean = true): Color {
    val c = LocalWii.current
    return (if (primary) c.btnPrimaryText else c.btnText).copy(alpha = if (enabled) 1f else 0.4f)
}

/** The cursor colour: Wii blue that slowly breathes between deep and bright (1.6 s). */
@Composable
fun breathingAccent(): Color {
    val c = LocalWii.current
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "breath")
    val f by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "breathF",
    )
    return androidx.compose.ui.graphics.lerp(c.accentBorder, Color(0xFF7FDBFF), f)
}

val LocalWii = staticCompositionLocalOf { LightWii }

@Composable
fun WiiTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkWii else LightWii
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.text,
        secondary = c.accentText, tertiary = c.orange,
        background = c.bg, onBackground = c.text, surface = c.card, onSurface = c.text,
        surfaceVariant = c.card, onSurfaceVariant = c.textDim, surfaceContainer = c.card, surfaceContainerHigh = c.card,
        outline = c.cardBorder,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.text,
        secondary = c.accentText, tertiary = c.orange,
        background = c.bg, onBackground = c.text, surface = c.card, onSurface = c.text,
        surfaceVariant = c.accentSoft, onSurfaceVariant = c.textDim, surfaceContainer = c.card, surfaceContainerHigh = c.card,
        outline = c.cardBorder,
    )
    val d = androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(LocalWii provides c, androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * TEXT_SCALE)) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            Surface(Modifier.fillMaxSize().wiiStripes(c), color = Color.Transparent, contentColor = c.text, content = content)
        }
    }
}

/** The Wii Menu's faint horizontal pinstripes (a 1 dp line every 4 dp). */
fun Modifier.wiiStripes(c: WiiColors) = drawBehind {
    drawRect(c.bg)
    val step = 4.dp.toPx()
    val line = 1.dp.toPx()
    var y = step - line
    while (y < size.height) {
        drawRect(c.stripe, Offset(0f, y), Size(size.width, line))
        y += step
    }
}

/** A white (or slate) rounded "channel" card; [selected] adds the Wii blue frame and glow. */
@Composable
fun WiiCard(
    modifier: Modifier = Modifier, selected: Boolean = false, radius: Dp = 20.dp, padding: Dp = 12.dp,
    onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .then(if (selected) Modifier.shadow(10.dp, shape, ambientColor = c.accent, spotColor = c.accent) else Modifier)
            .clip(shape).background(c.card)
            .border(if (selected) 2.5.dp else 2.dp, if (selected) breathingAccent() else c.cardBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Rounded pill button: [primary] = Wii blue, otherwise card-coloured with a grey frame. */
@Composable
fun WiiButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, fontSize: TextUnit = 16.sp) {
    val c = LocalWii.current
    val shape = ButtonShape
    Box(
        modifier.heightIn(min = 44.dp).focusRing(shape).channel(shape, primary)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val fg = channelText(primary, enabled)
        // "Ⓐ Play" → "Play" followed by the A icon (the shared button-icon style).
        val split = splitButtonLabel(text)
        if (split == null) Text(text, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, maxLines = 1, textAlign = TextAlign.Center, onTextLayout = { TextFit.check(text, it) })
        else androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Text(split.first, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, maxLines = 1, textAlign = TextAlign.Center, onTextLayout = { TextFit.check(text, it) })
            KeyIcon(split.second, if (primary) fg.copy(alpha = 0.75f) else c.textDim)
        }
    }
}

/** Small selectable pill (filters, sort, modes). */
@Composable
fun WiiChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalWii.current
    val shape = ChipShape
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, textAlign = TextAlign.Center,
        color = if (selected) c.onAccent else c.btnText,
        onTextLayout = { TextFit.check(text, it) },
        modifier = modifier.focusRing(shape).then(if (selected) Modifier.clip(shape).background(c.accent).border(2.dp, c.accentBorder, shape) else Modifier.channel(shape))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** The small "Ⓐ Play · Ⓧ Edit" line at the bottom of screens. */
@Composable
fun HintLine(text: String, modifier: Modifier = Modifier) {
    HintText(text, modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}


/**
 * Channel zoom: opening a page grows it in from slightly smaller (like opening a Wii channel); going
 * back Home settles it in from slightly bigger. Quick (220 ms) so it never slows anything down.
 */
@Composable
fun ChannelZoom(screen: Any, content: @Composable () -> Unit) {
    val anim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    var from by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var first by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val home = screen is Screen.Home
    androidx.compose.runtime.LaunchedEffect(screen) {
        if (first) { first = false; return@LaunchedEffect }
        from = if (home) 1.05f else 0.93f
        anim.snapTo(0f)
        anim.animateTo(1f, androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        val t = anim.value
        val s = from + (1f - from) * t
        scaleX = s; scaleY = s; alpha = 0.35f + 0.65f * t
    }) { content() }
}
