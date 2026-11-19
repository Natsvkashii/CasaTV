package it.casatv.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class CasaPalette(
    val isLight: Boolean,
    val background: Color,
    val backgroundDeep: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
    val blue: Color,
    val green: Color,
    val greenDark: Color,
    val red: Color,
    val amber: Color,
)

val LightCasaPalette = CasaPalette(
    isLight = true,
    background = Color(0xFFF0F6FC),       // Cool sky-tinted paper (comfortable for TV, zero glare)
    backgroundDeep = Color(0xFFE2EDF8),   // Slightly deeper ocean-mist tone for panels
    surface = Color(0xFFFFFFFF),          // Crisp white cards
    surfaceHigh = Color(0xFFE6F0FA),      // Hover / elevated cool blue surface
    border = Color(0xFFBFD4E8),           // Crisp ocean-slate borders
    text = Color(0xFF091526),             // Deep midnight ink for maximum readability
    muted = Color(0xFF334E68),            // High-contrast ocean-slate secondary text
    dim = Color(0xFF627D98),              // Muted steel-blue tertiary text
    blue = Color(0xFF0284C7),             // Mappamondo luminous azure-cyan primary accent
    green = Color(0xFF0284C7),            // Unified cyan-blue accent token across TV & Mobile
    greenDark = Color(0xFF0369A1),
    red = Color(0xFFDC2626),
    amber = Color(0xFFD97706),
)

val DarkCasaPalette = CasaPalette(
    isLight = false,
    background = Color(0xFF060B14),       // Mappamondo deep obsidian ocean background
    backgroundDeep = Color(0xFF040810),   // Deeper obsidian canvas edge
    surface = Color(0xE60A1322),          // Dark obsidian-navy card surface
    surfaceHigh = Color(0xF2102036),      // Focused / elevated obsidian-cyan panel
    border = Color(0xFF1E3A5F),           // Mappamondo oceanic blue-slate border
    text = Color(0xFFF8FAFC),             // Crisp frost white text
    muted = Color(0xFFCBD5E1),            // High-contrast cool silver secondary text
    dim = Color(0xFF94A3B8),              // Cool slate muted metadata
    blue = Color(0xFF0284C7),             // Mappamondo deep azure fill
    green = Color(0xFF00D4FF),            // Mappamondo luminous cyan primary accent
    greenDark = Color(0xFF0284C7),
    red = Color(0xFFFF453A),
    amber = Color(0xFFFF9F0A),
)

val LocalCasaPalette = compositionLocalOf { LightCasaPalette }

val CasaIsLight: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.isLight

val CasaBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.background

val CasaBackgroundDeep: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.backgroundDeep

val CasaSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.surface

val CasaSurfaceHigh: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.surfaceHigh

val CasaBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.border

val CasaBlue: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.blue

val CasaGreen: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.green

val CasaGreenDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.greenDark

val CasaText: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.text

val CasaMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.muted

val CasaDim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.dim

val CasaRed: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.red

val CasaAmber: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalCasaPalette.current.amber

private val DarkMaterialColorScheme = darkColorScheme(
    primary = Color(0xFF00D4FF),
    onPrimary = Color(0xFF04101A),
    primaryContainer = Color(0xFF0C3459),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF04101A),
    tertiary = Color(0xFF7DD3FC),
    background = Color(0xFF060B14),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xE60A1322),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xF2102036),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF1E3A5F),
    error = Color(0xFFFF453A),
    onError = Color(0xFF2B0505),
)

private val LightMaterialColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFF0369A1),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF0EA5E9),
    background = Color(0xFFF0F6FC),
    onBackground = Color(0xFF091526),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF091526),
    surfaceVariant = Color(0xFFE6F0FA),
    onSurfaceVariant = Color(0xFF334E68),
    outline = Color(0xFFBFD4E8),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
)

private val CasaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 54.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun CasaTvTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkCasaPalette else LightCasaPalette
    val materialScheme = if (darkTheme) DarkMaterialColorScheme else LightMaterialColorScheme

    CompositionLocalProvider(LocalCasaPalette provides palette) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = CasaTypography,
            content = content,
        )
    }
}
