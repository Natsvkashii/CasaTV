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
    background = Color(0xFFFFFFFF),
    backgroundDeep = Color(0xFFF4F4F5),
    surface = Color(0xFFF4F4F5),
    surfaceHigh = Color(0xFFE4E4E7),
    border = Color(0xFFD4D4D8),
    text = Color(0xFF000000),
    muted = Color(0xFF52525B),
    dim = Color(0xFF71717A),
    blue = Color(0xFF000000),
    green = Color(0xFF16A34A),
    greenDark = Color(0xFF15803D),
    red = Color(0xFFDC2626),
    amber = Color(0xFFD97706),
)

val DarkCasaPalette = CasaPalette(
    isLight = false,
    background = Color(0xFF000000),
    backgroundDeep = Color(0xFF000000),
    surface = Color(0xFF111111),
    surfaceHigh = Color(0xFF1B1B1B),
    border = Color(0xFF2B2B2B),
    text = Color(0xFFFFFFFF),
    muted = Color(0xFFA3A3A3),
    dim = Color(0xFF707070),
    blue = Color(0xFFFFFFFF),
    green = Color(0xFF22C55E),
    greenDark = Color(0xFF16A34A),
    red = Color(0xFFE50914),
    amber = Color(0xFFF59E0B),
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
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFD4D4D8),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFFA3A3A3),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1B1B1B),
    onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF2B2B2B),
    error = Color(0xFFE50914),
    onError = Color(0xFFFFFFFF),
)

private val LightMaterialColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF3F3F46),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF52525B),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFF4F4F5),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE4E4E7),
    onSurfaceVariant = Color(0xFF52525B),
    outline = Color(0xFFD4D4D8),
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
