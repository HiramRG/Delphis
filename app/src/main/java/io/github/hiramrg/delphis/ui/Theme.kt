package io.github.hiramrg.delphis.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Pure-black dark theme: on the Thor's AMOLED panels black pixels are off, which saves battery. */
private val Colors = darkColorScheme(
    primary = Color(0xFF3D9BFF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF12305C),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFFFFD166),
    tertiary = Color(0xFFFF8A65),
    background = Color.Black,
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF0E1014),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF1C2028),
    onSurfaceVariant = Color(0xFFAEB4C2),
    surfaceContainer = Color(0xFF14171D),
    surfaceContainerHigh = Color(0xFF1C2028),
    outline = Color(0xFF3A3F4B),
)

@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        // Surface sets the default content color so plain Text is readable on black.
        Surface(Modifier.fillMaxSize(), color = Colors.background, contentColor = Colors.onBackground, content = content)
    }
}
