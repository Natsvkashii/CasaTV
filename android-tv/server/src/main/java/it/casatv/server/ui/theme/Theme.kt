package it.casatv.server.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CasaBg = Color(0xFF000000)
val CasaPanel = Color(0xFF111111)
val CasaPanelHigh = Color(0xFF1A1A1A)
val CasaBorder = Color(0xFF2B2B2B)
val CasaGreen = Color(0xFFFFFFFF)
val CasaText = Color(0xFFFFFFFF)
val CasaMuted = Color(0xFFA3A3A3)
val CasaDim = Color(0xFF707070)
val CasaWarning = Color(0xFFF59E0B)
val CasaDanger = Color(0xFFE50914)

private val DarkColors = darkColorScheme(
    primary = CasaGreen,
    onPrimary = Color(0xFF000000),
    secondary = CasaText,
    background = CasaBg,
    onBackground = CasaText,
    surface = CasaPanel,
    onSurface = CasaText,
    surfaceVariant = CasaPanelHigh,
    onSurfaceVariant = CasaMuted,
    outline = CasaBorder,
    error = CasaDanger,
)

@Composable
fun ServerTheme(
    @Suppress("UNUSED_PARAMETER") dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
