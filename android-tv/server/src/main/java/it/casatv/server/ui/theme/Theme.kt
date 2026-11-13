package it.casatv.server.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CasaBg = Color(0xFF070C10)
val CasaPanel = Color(0xFF0D171D)
val CasaPanelHigh = Color(0xFF122028)
val CasaBorder = Color(0xFF22333B)
val CasaGreen = Color(0xFF62E69A)
val CasaText = Color(0xFFF3F7F5)
val CasaMuted = Color(0xFF9BAEB3)
val CasaDim = Color(0xFF667B82)
val CasaWarning = Color(0xFFF2C14E)
val CasaDanger = Color(0xFFE4635B)

private val Colors = darkColorScheme(
    primary = CasaGreen,
    onPrimary = Color(0xFF04140B),
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
