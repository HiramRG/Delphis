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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
)

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
    CompositionLocalProvider(LocalWii provides c) {
        MaterialTheme(colorScheme = scheme) {
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
            .border(if (selected) 2.5.dp else 2.dp, if (selected) c.accent else c.cardBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Rounded pill button: [primary] = Wii blue, otherwise card-coloured with a grey frame. */
@Composable
fun WiiButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, fontSize: TextUnit = 16.sp) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier.heightIn(min = 44.dp).clip(shape)
            .background(if (primary) c.accent else c.card)
            .border(2.dp, if (primary) c.accentBorder else c.cardBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = (if (primary) c.onAccent else c.text).copy(alpha = if (enabled) 1f else 0.4f),
            fontWeight = FontWeight.ExtraBold, fontSize = fontSize, maxLines = 1, textAlign = TextAlign.Center,
        )
    }
}

/** Small selectable pill (filters, sort, modes). */
@Composable
fun WiiChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, textAlign = TextAlign.Center,
        color = if (selected) c.onAccent else c.text,
        modifier = modifier.clip(shape).background(if (selected) c.accent else c.card)
            .border(2.dp, if (selected) c.accentBorder else c.cardBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** The small "Ⓐ Play · Ⓧ Edit" line at the bottom of screens. */
@Composable
fun HintLine(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.sp, color = LocalWii.current.textDim, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())
}
