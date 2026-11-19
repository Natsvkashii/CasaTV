package it.casatv.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CasaCyan = Color(0xFF00D4FF)
private val CasaAzure = Color(0xFF0284C7)

private val DarkColors = darkColorScheme(
    primary = CasaCyan,
    onPrimary = Color(0xFF04101A),
    primaryContainer = Color(0xFF0C3459),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF04101A),
    background = Color(0xFF060B14),
    onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF0A1322),
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF112238),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF1E3A5F),
    error = Color(0xFFFF5C5C),
)

private val LightColors = lightColorScheme(
    primary = CasaAzure,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFF0369A1),
    onSecondary = Color.White,
    background = Color(0xFFF0F6FC),
    onBackground = Color(0xFF091526),
    surface = Color.White,
    onSurface = Color(0xFF091526),
    surfaceVariant = Color(0xFFE2EDF8),
    onSurfaceVariant = Color(0xFF334E68),
    outline = Color(0xFFBFD4E8),
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
