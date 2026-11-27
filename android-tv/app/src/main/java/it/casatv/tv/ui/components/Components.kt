package it.casatv.tv.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import it.casatv.tv.CasaTvApplication
import it.casatv.tv.data.Channel
import it.casatv.tv.data.GlobalSearchResult
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.countryFlag
import it.casatv.tv.ui.formatNumber
import it.casatv.tv.ui.initials
import it.casatv.tv.ui.localizedSubtitle
import it.casatv.tv.ui.theme.CasaAmber
import it.casatv.tv.ui.theme.CasaBackground
import it.casatv.tv.ui.theme.CasaBorder
import it.casatv.tv.ui.theme.CasaDim
import it.casatv.tv.ui.theme.CasaGreen
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaRed
import it.casatv.tv.ui.theme.CasaSurface
import it.casatv.tv.ui.theme.CasaSurfaceHigh
import it.casatv.tv.ui.theme.CasaText
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LogoContrastResult(
    val isDark: Boolean,
    val isVeryLight: Boolean,
)

object LogoContrastCache {
    private val cache = ConcurrentHashMap<String, LogoContrastResult>()

    fun peek(url: String?): LogoContrastResult? = url?.let { cache[it] }

    suspend fun analyzeDrawable(url: String, drawable: Drawable): LogoContrastResult {
        cache[url]?.let { return it }
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val sampleSize = 24
                val sample = Bitmap.createBitmap(sampleSize, sampleSize, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = AndroidCanvas(sample)
                    if (drawable is BitmapDrawable) {
                        val bmp = drawable.bitmap
                        val safeBmp = if (bmp.config == Bitmap.Config.HARDWARE) {
                            bmp.copy(Bitmap.Config.ARGB_8888, false)
                        } else {
                            bmp
                        }
                        if (safeBmp != null) {
                            val srcRect = android.graphics.Rect(0, 0, safeBmp.width, safeBmp.height)
                            val dstRect = android.graphics.Rect(0, 0, sampleSize, sampleSize)
                            canvas.drawBitmap(safeBmp, srcRect, dstRect, null)
                            if (safeBmp !== bmp) safeBmp.recycle()
                        }
                    } else {
                        drawable.setBounds(0, 0, sampleSize, sampleSize)
                        drawable.draw(canvas)
                    }
                    val pixels = IntArray(sampleSize * sampleSize)
                    sample.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
                    var opaqueCount = 0
                    var lumSum = 0.0
                    var darkCount = 0
                    var brightCount = 0
                    for (px in pixels) {
                        val a = (px ushr 24) and 0xFF
                        if (a >= 64) {
                            val r = (px ushr 16) and 0xFF
                            val g = (px ushr 8) and 0xFF
                            val b = px and 0xFF
                            val lum = 0.299 * r + 0.587 * g + 0.114 * b
                            opaqueCount += 1
                            lumSum += lum
                            if (lum < 85.0) darkCount += 1
                            if (lum > 205.0) brightCount += 1
                        }
                    }
                    if (opaqueCount < 10) {
                        LogoContrastResult(isDark = false, isVeryLight = false)
                    } else {
                        val meanLum = (lumSum / opaqueCount).toFloat()
                        val darkFrac = darkCount.toFloat() / opaqueCount
                        val brightFrac = brightCount.toFloat() / opaqueCount
                        LogoContrastResult(
                            isDark = meanLum < 102f || (darkFrac >= 0.62f && meanLum < 125f),
                            isVeryLight = meanLum > 205f || (brightFrac >= 0.72f && meanLum > 185f),
                        )
                    }
                } finally {
                    sample.recycle()
                }
            }.getOrDefault(LogoContrastResult(isDark = false, isVeryLight = false))
        }
        cache[url] = result
        return result
    }
}

data class TvUiScale(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isCompact: Boolean,
    val isLarge: Boolean,
    val heroHeight: Dp,
    val homeCardWidth: Dp,
    val homeCardHeight: Dp,
    val homeArtworkHeight: Dp,
    val gridMinCardWidth: Dp,
    val paddingHorizontal: Dp,
    val titleFontSize: TextUnit,
    val heroTitleFontSize: TextUnit,
)

@Composable
fun rememberTvUiScale(): TvUiScale {
    val configuration = LocalConfiguration.current
    val w = configuration.screenWidthDp
    val h = configuration.screenHeightDp
    val isCompact = h < 600 || w < 960
    val isLarge = h >= 850 && w >= 1400

    return remember(w, h) {
        if (isCompact) {
            TvUiScale(
                screenWidthDp = w,
                screenHeightDp = h,
                isCompact = true,
                isLarge = false,
                heroHeight = 160.dp,
                homeCardWidth = 180.dp,
                homeCardHeight = 180.dp,
                homeArtworkHeight = 100.dp,
                gridMinCardWidth = 136.dp,
                paddingHorizontal = 12.dp,
                titleFontSize = 16.sp,
                heroTitleFontSize = 22.sp,
            )
        } else if (isLarge) {
            TvUiScale(
                screenWidthDp = w,
                screenHeightDp = h,
                isCompact = false,
                isLarge = true,
                heroHeight = 270.dp,
                homeCardWidth = 245.dp,
                homeCardHeight = 245.dp,
                homeArtworkHeight = 140.dp,
                gridMinCardWidth = 164.dp,
                paddingHorizontal = 22.dp,
                titleFontSize = 20.sp,
                heroTitleFontSize = 30.sp,
            )
        } else {
            TvUiScale(
                screenWidthDp = w,
                screenHeightDp = h,
                isCompact = false,
                isLarge = false,
                heroHeight = 210.dp,
                homeCardWidth = 215.dp,
                homeCardHeight = 215.dp,
                homeArtworkHeight = 120.dp,
                gridMinCardWidth = 146.dp,
                paddingHorizontal = 16.dp,
                titleFontSize = 18.sp,
                heroTitleFontSize = 26.sp,
            )
        }
    }
}

@Composable
fun BackgroundGlow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CasaBackground),
    )
}

private val CardShape = RoundedCornerShape(4.dp)
private val BadgeShape = RoundedCornerShape(3.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    showCountryFlag: Boolean = false,
    displayTitle: String? = null,
    badgeText: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hasFlag = remember(channel.country) { resolveFlagAssetCode(channel.country) != null }
    var pressSeq by remember { mutableStateOf(0) }
    var isCenterDown by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }

    LaunchedEffect(pressSeq) {
        if (isCenterDown && onLongClick != null) {
            delay(420L)
            if (isCenterDown && !longPressFired) {
                longPressFired = true
                isCenterDown = false
                onLongClick()
            }
        }
    }

    val isVod = channel.isVodContent
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val frameFocusColor = if (isLight) Color.Black else Color.White
    val tileShape = if (isVod) RoundedCornerShape(8.dp) else RoundedCornerShape(10.dp)
    val effectiveTitle = displayTitle ?: if (channel.contentType == "series" && !channel.seriesTitle.isNullOrBlank()) {
        channel.seriesTitle
    } else {
        channel.name
    }

    // Borderless tile grid: pure poster/logo tile with ZERO text underneath
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (isVod) (2f / 3f) else 1f)
            .zIndex(if (!isLowRam && focused) 2f else 0f)
            .graphicsLayer {
                if (!isLowRam && focused) {
                    scaleX = 1.04f
                    scaleY = 1.04f
                }
            }
            .onFocusChanged {
                if (!it.isFocused) {
                    isCenterDown = false
                    longPressFired = false
                }
            }
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    code == AndroidKeyEvent.KEYCODE_MENU && event.type == KeyEventType.KeyUp -> {
                        (onLongClick ?: onFavorite).invoke()
                        true
                    }
                    onLongClick != null && (
                        code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                            code == AndroidKeyEvent.KEYCODE_ENTER ||
                            code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                        ) -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (event.nativeKeyEvent.repeatCount == 0) {
                                    isCenterDown = true
                                    longPressFired = false
                                    pressSeq += 1
                                } else if (
                                    !longPressFired &&
                                    (event.nativeKeyEvent.isLongPress ||
                                        (event.nativeKeyEvent.eventTime - event.nativeKeyEvent.downTime) >= 420L)
                                ) {
                                    longPressFired = true
                                    isCenterDown = false
                                    onLongClick()
                                }
                                true
                            }
                            KeyEventType.KeyUp -> {
                                val fireClick = isCenterDown && !longPressFired
                                isCenterDown = false
                                longPressFired = false
                                if (fireClick) onClick()
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
            .clip(tileShape)
            .background(CasaSurface)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        ChannelArtwork(
            channel = channel,
            effectiveTitle = effectiveTitle,
            badgeText = badgeText,
            modifier = Modifier.fillMaxSize(),
            compact = true,
            focused = if (isLowRam) false else focused,
            isVod = isVod,
        )
        if (focused) {
            val outerRadius = if (isVod) 8.dp else 10.dp
            val innerRadius = if (isVod) 5.dp else 7.dp
            val contrastGapColor = if (isLight) Color.White else Color.Black
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, frameFocusColor, RoundedCornerShape(outerRadius))
                    .padding(3.dp)
                    .border(2.5.dp, contrastGapColor, RoundedCornerShape(innerRadius)),
            )
        }
    }
}

@Composable
private fun ChannelArtwork(
    channel: Channel,
    effectiveTitle: String = channel.seriesTitle ?: channel.name,
    badgeText: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean,
    focused: Boolean = false,
    isVod: Boolean = channel.isVodContent,
) {
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val context = LocalContext.current
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    var contrast by remember(channel.logo, channel.id) {
        mutableStateOf(if (!isVod) LogoContrastCache.peek(channel.logo) else null)
    }

    val tileBg = if (isVod) {
        if (isLight) Color(0xFFEAEAEF) else Color(0xFF141417)
    } else {
        when {
            !isLight && contrast?.isDark == true -> Color(0xFFEDEDED)
            isLight && contrast?.isVeryLight == true -> Color(0xFF18181B)
            isLight -> Color(0xFFF2F2F5)
            else -> Color(0xFF161618)
        }
    }

    Box(
        modifier = modifier.background(tileBg),
    ) {
        if (isVod) {
            PosterArtwork(
                channel = channel,
                displayTitle = effectiveTitle,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ChannelLogo(
                channel = channel,
                displayTitle = effectiveTitle,
                onContrastDetected = { contrast = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (compact) 14.dp else 20.dp),
            )
            if (!isLowRam) {
                ChannelLivePreviewBox(
                    streamUrl = channel.streamUrl,
                    active = focused,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (isVod) {
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(BadgeShape)
                        .background(Color(0xD9000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            val vodFlags = remember(channel.languageFlags, channel.country, isVod) {
                if (!isVod) {
                    emptyList()
                } else {
                    val explicit = channel.languageFlags.mapNotNull(::resolveFlagAssetCode).distinct().take(5)
                    if (explicit.isNotEmpty()) explicit else listOfNotNull(resolveFlagAssetCode(channel.country))
                }
            }
            if (vodFlags.isNotEmpty() || channel.contentType == "series") {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (vodFlags.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            vodFlags.forEach { cc ->
                                CountryFlagIcon(
                                    countryCode = cc,
                                    width = 11.dp,
                                    height = 8.dp,
                                    cornerRadius = 1.5.dp,
                                )
                            }
                        }
                    }
                    if (channel.contentType == "series") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xC8000000))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "TV",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.4.sp,
                            )
                        }
                    }
                }
            }
            if (channel.durationMs > 0L && channel.positionMs > 15_000L) {
                val frac = (channel.positionMs.toFloat() / channel.durationMs.toFloat()).coerceIn(0.03f, 0.98f)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x44FFFFFF)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac)
                            .background(Color.White),
                    )
                }
            }
        }
        if (channel.favorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xB8000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = LocalAppStrings.current.t("Preferito", "Favorite"),
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

@Composable
private fun PosterArtwork(
    channel: Channel,
    displayTitle: String = channel.seriesTitle ?: channel.name,
    modifier: Modifier = Modifier,
) {
    var failed by remember(channel.logo, channel.id) { mutableStateOf(false) }
    var loaded by remember(channel.logo, channel.id) { mutableStateOf(false) }
    var isLandscapeStill by remember(channel.logo, channel.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    if (channel.logo.isNullOrBlank() || failed) {
        TypographicTileFallback(title = displayTitle, modifier = modifier)
    } else {
        val request = remember(channel.logo, isLowRam) {
            ImageRequest.Builder(context)
                .data(channel.logo)
                .precision(Precision.INEXACT)
                .apply {
                    if (isLowRam) {
                        size(220, 330)
                        crossfade(false)
                        allowRgb565(true)
                    } else {
                        size(360, 540)
                        crossfade(160)
                    }
                }
                .build()
        }
        Box(modifier = modifier) {
            if (!loaded) {
                TypographicTileFallback(title = displayTitle, modifier = Modifier.fillMaxSize())
            }
            AsyncImage(
                model = request,
                contentDescription = displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    loaded = true
                    val d = state.result.drawable
                    val w = d.intrinsicWidth
                    val h = d.intrinsicHeight
                    if (w > 0 && h > 0 && w > h * 1.08f) {
                        isLandscapeStill = true
                    }
                },
                onError = { failed = true },
            )
            if (isLandscapeStill) {
                val cleanTitle = remember(displayTitle) {
                    displayTitle.replace(Regex("""^\s*(?:\([^)]{1,10}\)|\[[^\]]{1,10}\])\s*"""), "").trim().ifBlank { displayTitle }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000), Color(0xF0000000)),
                            ),
                        )
                        .padding(start = 8.dp, end = if (channel.contentType == "series") 32.dp else 8.dp, top = 22.dp, bottom = 7.dp),
                ) {
                    Text(
                        text = cleanTitle,
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelLogo(
    channel: Channel,
    modifier: Modifier = Modifier,
    displayTitle: String = channel.name,
    onContrastDetected: ((LogoContrastResult) -> Unit)? = null,
) {
    var failed by remember(channel.logo, channel.id) { mutableStateOf(false) }
    var loaded by remember(channel.logo, channel.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    val scope = rememberCoroutineScope()
    val logoUrl = channel.logo
    if (logoUrl.isNullOrBlank() || failed) {
        TypographicTileFallback(title = displayTitle, modifier = modifier)
    } else {
        val request = remember(logoUrl, isLowRam) {
            ImageRequest.Builder(context)
                .data(logoUrl)
                .allowHardware(false)
                .precision(Precision.INEXACT)
                .apply {
                    if (isLowRam) {
                        size(192, 128)
                        crossfade(false)
                    } else {
                        size(280, 180)
                        crossfade(150)
                    }
                }
                .build()
        }
        val onImageError = remember(logoUrl, channel.id) {
            { _: coil.compose.AsyncImagePainter.State.Error -> failed = true }
        }
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            if (!loaded) {
                TypographicTileFallback(
                    title = displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    transparentBg = true,
                )
            }
            AsyncImage(
                model = request,
                contentDescription = displayTitle,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    loaded = true
                    if (onContrastDetected != null) {
                        val peeked = LogoContrastCache.peek(logoUrl)
                        if (peeked != null) {
                            onContrastDetected(peeked)
                        } else {
                            scope.launch {
                                val res = LogoContrastCache.analyzeDrawable(logoUrl, state.result.drawable)
                                onContrastDetected(res)
                            }
                        }
                    }
                },
                onError = onImageError,
            )
        }
    }
}

@Composable
private fun TypographicTileFallback(
    title: String,
    modifier: Modifier = Modifier,
    transparentBg: Boolean = false,
) {
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val cleanTitle = remember(title) {
        title.replace(Regex("""^\s*(?:\([^)]{1,10}\)|\[[^\]]{1,10}\])\s*"""), "").trim().ifBlank { title }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (transparentBg) Modifier else Modifier.background(if (isLight) Color(0xFFE6E6EC) else Color(0xFF1A1A1F)),
            )
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cleanTitle,
            color = if (isLight) Color(0xFF111114) else Color(0xFFF2F2F5),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(6.dp)
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val background = when {
        !enabled -> CasaSurface
        focused -> if (isLight) Color.Black else Color.White
        primary -> if (isLight) Color(0x14000000) else Color(0x22FFFFFF)
        else -> CasaSurface
    }
    val contentColor = when {
        !enabled -> CasaDim
        focused -> if (isLight) Color.White else Color.Black
        else -> CasaText
    }
    val borderColor = when {
        !enabled -> Color.Transparent
        focused -> if (isLight) Color.Black else Color.White
        primary -> CasaText.copy(alpha = 0.45f)
        else -> CasaBorder
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .widthIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun LoadingBox(
    text: String = "Carico i canali…",
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = CasaGreen,
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(text, color = CasaMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun ErrorBox(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 440.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = CasaAmber,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.t("Qualcosa non ha funzionato", "Something went wrong"),
                style = MaterialTheme.typography.titleLarge,
                color = CasaText,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = CasaMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            TvButton(
                text = strings.t("Riprova", "Retry"),
                onClick = onRetry,
                primary = true,
                icon = Icons.Filled.Refresh,
            )
        }
    }
}

@Composable
fun EmptyBox(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = CasaGreen.copy(alpha = 0.75f),
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = CasaText,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = CasaMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
                TvButton(text = actionLabel, onClick = onAction, primary = true)
            }
        }
    }
}

@Composable
fun statusColor(status: String): Color = when (status) {
    "online" -> CasaGreen
    "offline" -> CasaRed
    else -> CasaDim
}

@Composable
fun statusLabel(status: String): String = when (status) {
    "online" -> LocalAppStrings.current.t("Online", "Online")
    "offline" -> LocalAppStrings.current.t("Offline", "Offline")
    else -> LocalAppStrings.current.t("Da verificare", "Unverified")
}

@Composable
fun channelCountLabel(count: Int): String {
    val strings = LocalAppStrings.current
    return if (count == 1) {
        strings.t("1 canale", "1 channel")
    } else {
        strings.t(
            "${formatNumber(count, strings.language)} canali",
            "${formatNumber(count, strings.language)} channels",
            "${formatNumber(count, strings.language)} canales",
        )
    }
}

@Composable
fun TvUniversalSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit = {},
    onClear: () -> Unit = {},
    loading: Boolean = false,
    placeholderText: String = "",
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var isEditing by remember { mutableStateOf(false) }
    var fieldHadFocus by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val rowInteraction = remember { MutableInteractionSource() }
    val rowFocused by rowInteraction.collectIsFocusedAsState()
    val highlighted = isEditing || rowFocused

    LaunchedEffect(isEditing) {
        if (isEditing) {
            fieldHadFocus = false
            delay(40)
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
        } else {
            fieldHadFocus = false
            keyboard?.hide()
        }
    }

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlighted) CasaSurfaceHigh else CasaSurface)
            .border(
                if (highlighted) 2.dp else 1.dp,
                if (highlighted) CasaText else CasaBorder,
                RoundedCornerShape(6.dp),
            )
            .clickable(
                enabled = !isEditing,
                interactionSource = rowInteraction,
                indication = null,
                onClick = { isEditing = true },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = if (highlighted) CasaText else CasaDim,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (isEditing) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholderText,
                        color = CasaDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = CasaText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    cursorBrush = SolidColor(CasaGreen),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        isEditing = false
                        keyboard?.hide()
                        onSearch()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                fieldHadFocus = true
                            } else if (fieldHadFocus) {
                                isEditing = false
                            }
                        },
                )
            } else {
                Text(
                    text = query.ifEmpty { placeholderText },
                    color = if (query.isEmpty()) CasaDim else CasaText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (loading) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = CasaGreen,
                strokeWidth = 2.dp,
            )
        } else if (query.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable {
                        isEditing = false
                        onClear()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = CasaMuted,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UniversalSearchResultCard(
    result: GlobalSearchResult,
    onClick: () -> Unit,
    onFavorite: () -> Unit = {},
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    when (result) {
        is GlobalSearchResult.ChannelResult -> {
            val ch = result.channel
            val title = if (ch.contentType == "series" && !ch.seriesTitle.isNullOrBlank()) ch.seriesTitle else ch.name
            GridChannelCard(
                channel = ch,
                onClick = onClick,
                onFavorite = onFavorite,
                modifier = modifier,
                showCountryFlag = true,
                displayTitle = title,
                onLongClick = onLongClick,
            )
        }
    }
}
