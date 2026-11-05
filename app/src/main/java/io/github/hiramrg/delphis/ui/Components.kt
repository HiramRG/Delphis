package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hiramrg.delphis.core.ThorControl

/** Palette from the reference mockup: glowing blue on black, coloured face buttons. */
object Glow {
    val Blue = Color(0xFF3D9BFF)
    val BlueLight = Color(0xFF6CB6FF)
    val Navy = Color(0xFF12305C)
    val NavyBorder = Color(0xFF2C5FA8)
    val Panel = Color(0xFF0D1626)
    val Yellow = Color(0xFFFFD43B)
    val Purple = Color(0xFFB57BFF)
    val Red = Color(0xFFFF5A5A)
    val Green = Color(0xFF6BE05A)
    val Amber = Color(0xFFFFC21A)
    val Sky = Color(0xFF4AA8FF)
    val TextOnBlue = Color(0xFF071324)
}

/** Colour of the "(experimental)" tag, so experimental presets stand out. */
val ExperimentalOrange = Color(0xFFFF9F1C)

/** [text] with every "(experimental)" shown in bold [orange] (the theme's orange: darker on light, brighter on dark). */
fun withOrangeTags(text: String, orange: Color = ExperimentalOrange): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val tag = "(experimental)"
        var i = 0
        while (true) {
            val j = text.indexOf(tag, i)
            if (j < 0) { append(text.substring(i)); break }
            append(text.substring(i, j))
            pushStyle(androidx.compose.ui.text.SpanStyle(color = orange, fontWeight = FontWeight.Bold))
            append(tag)
            pop()
            i = j + tag.length
        }
    }

/** Colour of each Thor control in the legend and badges. */
fun controlColor(c: ThorControl): Color = when (c) {
    ThorControl.FACE_EAST -> Glow.Red
    ThorControl.FACE_SOUTH -> Glow.Amber
    ThorControl.FACE_NORTH -> Glow.Sky
    ThorControl.FACE_WEST -> Glow.Green
    ThorControl.RIGHT_STICK, ThorControl.R3, ThorControl.GYRO -> Glow.Purple
    else -> Glow.Blue
}

private fun Modifier.glow(color: Color, radius: Dp, shape: RoundedCornerShape) =
    shadow(radius, shape, clip = false, ambientColor = color, spotColor = color)

/** Wii-blue main action button ("Change preset", "Play"). */
@Composable
fun PrimaryGlowButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, fontSize: TextUnit = 16.sp) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier.alpha(if (enabled) 1f else 0.4f).clip(shape).background(c.accent).border(2.dp, c.accentBorder, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = c.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, maxLines = 1)
    }
}

/** Card-coloured button with a grey frame ("Edit preset"). */
@Composable
fun SecondaryGlowButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, fontSize: TextUnit = 16.sp) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier.alpha(if (enabled) 1f else 0.4f).clip(shape).background(c.card)
            .border(2.dp, c.cardBorder, shape).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = c.text, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = c.text, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, maxLines = 1)
    }
}

/** Card-coloured pill with a blue label ("← Main menu"). */
@Composable
fun OutlinePillButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, fontSize: TextUnit = 14.sp) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(c.card).border(2.dp, c.cardBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = c.accentText, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = c.accentText, fontWeight = FontWeight.Bold, fontSize = fontSize, maxLines = 1)
    }
}

/** Round icon-only button (the in-game "back to main menu"). */
@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.size(width = 46.dp, height = 40.dp).clip(shape).background(c.card).border(2.dp, c.cardBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = c.accentText, modifier = Modifier.size(22.dp)) }
}

/** Yellow brightness pill: sun icon + the current 0-100 value. Opens the slider. */
@Composable
fun BrightnessPill(percent: Int, open: Boolean, onClick: () -> Unit) {
    val c = LocalWii.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(40.dp).clip(shape).background(c.card).border(2.dp, if (open) Glow.Yellow else c.cardBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.LightMode, "Screen brightness", tint = Glow.Yellow, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("$percent", color = Glow.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.widthIn(min = 24.dp))
    }
}

/** A small icon that hints at what an action does (only when one clearly fits). */
fun actionIcon(action: String): ImageVector? {
    val a = action.lowercase()
    return when {
        "home" in a -> Icons.Filled.Home
        "pause" in a -> Icons.Filled.Pause
        "recenter" in a -> Icons.Filled.CenterFocusStrong
        "pointer" in a || "aim" in a -> Icons.Filled.GpsFixed
        "star bit" in a || "shoot" in a -> Icons.Filled.Star
        "crouch" in a || "ground pound" in a -> Icons.Filled.ArrowCircleDown
        "camera" in a -> Icons.Filled.Videocam
        "look behind" in a -> Icons.Filled.Visibility
        "steer" in a || "move" in a -> Icons.Filled.OpenWith
        "spin" in a || "shake" in a || "trick" in a || "roll" in a -> Icons.Filled.Autorenew
        "item" in a -> Icons.Filled.CardGiftcard
        "accelerate" in a -> Icons.Filled.Speed
        "drift" in a || "brake" in a -> Icons.Filled.TurnSlightRight
        "fire" in a || "run" in a -> Icons.Filled.LocalFireDepartment
        "tilt" in a -> Icons.Filled.ScreenRotation
        "jump" in a || "float" in a -> Icons.Filled.ArrowCircleUp
        else -> null
    }
}
