package it.casatv.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CasaGreen = Color(0xFF39D353)
private val CasaGreenDark = Color(0xFF1F9D38)

private val DarkColors = darkColorScheme(
    primary = CasaGreen,
    onPrimary = Color(0xFF06160A),
    primaryContainer = Color(0xFF133A1D),
    onPrimaryContainer = Color(0xFFA7F5B4),
    secondary = Color(0xFF8FB996),
    onSecondary = Color(0xFF0E1D12),
    background = Color(0xFF0C0E10),
    onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF14181C),
    onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF1E242B),
    onSurfaceVariant = Color(0xFF9AA4B2),
    outline = Color(0xFF2B333D),
    error = Color(0xFFFF5C5C),
)

private val LightColors = lightColorScheme(
    primary = CasaGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F7DC),
    onPrimaryContainer = Color(0xFF0A3312),
    secondary = Color(0xFF47664E),
    onSecondary = Color.White,
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE7ECF0),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D8E0),
    error = Color(0xFFDC2626),
)

@Composable
fun CasaTvMobileTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
