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
    background = Color(0xFFF3F5F9),       // Soft, modern, light background (comfortable for TV, zero glare)
    backgroundDeep = Color(0xFFE5E9F0),   // Slightly deeper tone for side rails and sub-panels
    surface = Color(0xFFFFFFFF),          // Crisp, clean white cards
    surfaceHigh = Color(0xFFEAEFF6),      // Hover / elevated surface
    border = Color(0xFFCAD4E0),           // Crisp, clearly defined 1px/2px borders
    text = Color(0xFF0F172A),             // Deep slate navy/charcoal for maximum readability
    muted = Color(0xFF334155),            // High-contrast slate secondary text (fully legible from 3m away)
    dim = Color(0xFF64748B),              // Slate dim text with excellent legibility
    blue = Color(0xFF0070F3),             // Apple / Vercel vibrant TV Focus blue
    green = Color(0xFF16A34A),            // Rich emerald green
    greenDark = Color(0xFF15803D),
    red = Color(0xFFDC2626),
    amber = Color(0xFFD97706),
)

val DarkCasaPalette = CasaPalette(
    isLight = false,
    background = Color(0xFF000000),
    backgroundDeep = Color(0xFF080B10),
    surface = Color(0xE01C1C1E),
    surfaceHigh = Color(0xEB2C2C2E),
    border = Color(0x2EFFFFFF),
    text = Color(0xFFFFFFFF),
    muted = Color(0xCCEBEBF5),            // Improved contrast for dark mode
    dim = Color(0x99EBEBF5),              // Improved contrast for dark mode
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    greenDark = Color(0xFF1B6E33),
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
    primary = Color(0xFF30D158),
    onPrimary = Color(0xFF032112),
    primaryContainer = Color(0xFF1B6E33),
    onPrimaryContainer = Color(0xFFD8FFE7),
    secondary = Color(0xFFFF9F0A),
    onSecondary = Color(0xFF231A05),
    tertiary = Color(0xFF84D9FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xE01C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xEB2C2C2E),
    onSurfaceVariant = Color(0xCCEBEBF5),
    outline = Color(0x2EFFFFFF),
    error = Color(0xFFFF453A),
    onError = Color(0xFF2B0505),
)

private val LightMaterialColorScheme = lightColorScheme(
    primary = Color(0xFF16A34A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF14532D),
    secondary = Color(0xFFD97706),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEAEFF6),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCAD4E0),
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
