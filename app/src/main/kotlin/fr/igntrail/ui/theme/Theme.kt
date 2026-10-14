package fr.igntrail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object TrailColors {
    val Green = Color(0xFF1B7F4A)
    val Blue = Color(0xFF1E6FE8)
    val Orange = Color(0xFFF57C1F)
    val Red = Color(0xFFE53935)
    val NorthRed = Color(0xFFE53935)
}

/** Couleurs des panneaux et boutons flottants posés sur la carte (§15.1). */
@Immutable
data class PanelColors(
    val container: Color,
    val content: Color,
    val secondary: Color,
    val divider: Color,
)

private val DarkPanels = PanelColors(
    container = Color(0xE6202327),
    content = Color(0xFFF2F4F5),
    secondary = Color(0xFFB0B6BC),
    divider = Color(0x33FFFFFF),
)

private val LightPanels = PanelColors(
    container = Color(0xF2FFFFFF),
    content = Color(0xFF1C1F22),
    secondary = Color(0xFF5B636B),
    divider = Color(0x1F000000),
)

val LocalPanelColors = staticCompositionLocalOf { DarkPanels }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4CC38A),
    onPrimary = Color(0xFF00391F),
    secondary = TrailColors.Blue,
    background = Color(0xFF15181B),
    surface = Color(0xFF1C1F23),
    surfaceContainer = Color(0xFF1C1F23),
)

private val LightScheme = lightColorScheme(
    primary = TrailColors.Green,
    onPrimary = Color.White,
    secondary = TrailColors.Blue,
    background = Color(0xFFF6F7F5),
    surface = Color.White,
    surfaceContainer = Color(0xFFF1F3F0),
)

@Composable
fun IgnTrailTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPanelColors provides if (darkTheme) DarkPanels else LightPanels) {
        MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
    }
}
