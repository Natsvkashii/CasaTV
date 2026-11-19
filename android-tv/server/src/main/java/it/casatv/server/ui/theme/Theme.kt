package it.casatv.server.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CasaBg = Color(0xFF060B14)
val CasaPanel = Color(0xFF0A1322)
val CasaPanelHigh = Color(0xFF102036)
val CasaBorder = Color(0xFF1E3A5F)
val CasaGreen = Color(0xFF00D4FF)
val CasaText = Color(0xFFF8FAFC)
val CasaMuted = Color(0xFFCBD5E1)
val CasaDim = Color(0xFF94A3B8)
val CasaWarning = Color(0xFFFF9F0A)
val CasaDanger = Color(0xFFFF453A)

private val Colors = darkColorScheme(
    primary = CasaGreen,
    onPrimary = Color(0xFF04101A),
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
    MaterialTheme(colorScheme = Colors, content = content)
}
