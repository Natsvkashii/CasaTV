package it.casatv.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFD4D4D8),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF111111),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF2B2B2B),
    error = Color(0xFFE50914),
)

private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF3F3F46),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF4F4F5),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE4E4E7),
    onSurfaceVariant = Color(0xFF52525B),
    outline = Color(0xFFD4D4D8),
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
