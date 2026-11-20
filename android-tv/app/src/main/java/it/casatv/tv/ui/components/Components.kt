package it.casatv.tv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import kotlinx.coroutines.delay

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
                gridMinCardWidth = 155.dp,
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
                gridMinCardWidth = 200.dp,
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
                gridMinCardWidth = 175.dp,
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

@Composable
fun GridChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    showCountryFlag: Boolean = false,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = remember { RoundedCornerShape(20.dp) }
    val isLight = it.casatv.tv.ui.theme.CasaIsLight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(168.dp)
            .zIndex(if (!isLowRam && focused) 2f else 0f)
            .clip(shape)
            .background(if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaSurface)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaBorder,
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MENU
                ) {
                    onFavorite()
                    true
                } else {
                    false
                }
            },
    ) {
        ChannelArtwork(
            channel = channel,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            compact = true,
            showCountryFlag = showCountryFlag,
            focused = focused,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (showCountryFlag) {
                    val flag = channel.flag.ifBlank { countryFlag(channel.country) }
                    if (flag.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (focused) (if (isLight) Color(0x33FFFFFF) else Color(0x22000000)) else (if (isLight) Color(0x14000000) else Color(0x33FFFFFF))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = flag, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(
                    text = channel.name,
                    color = if (focused) (if (isLight) Color.White else Color.Black) else CasaText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = channel.now?.title?.let {
                    "${strings.t("Ora", "Now")}: $it"
                } ?: localizedSubtitle(channel, strings.language),
                color = if (focused) (if (isLight) Color(0xD9FFFFFF) else Color(0x99000000)) else CasaMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChannelArtwork(
    channel: Channel,
    modifier: Modifier = Modifier,
    compact: Boolean,
    showCountryFlag: Boolean = false,
    focused: Boolean = false,
) {
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val context = LocalContext.current
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    val lightBrush = remember { Brush.linearGradient(listOf(Color(0xFFE2E8F0), Color(0xFFEDF2F7))) }
    val darkBrush = remember { Brush.linearGradient(listOf(Color(0xFF0F2238), Color(0xFF07111E))) }
    val boxModifier = if (isLowRam) {
        modifier.background(if (isLight) Color(0xFFE8EEF5) else Color(0xFF0A1728))
    } else {
        modifier.background(if (isLight) lightBrush else darkBrush)
    }
    Box(
        modifier = boxModifier,
    ) {
        ChannelLogo(
            channel = channel,
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
        if (channel.number != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isLight) Color(0xF2FFFFFF) else Color(0xCC060D18))
                    .border(1.dp, if (isLight) CasaBorder else Color.Transparent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = channel.number.toString(),
                    color = if (isLight) it.casatv.tv.ui.theme.CasaGreenDark else CasaGreen,
                    fontSize = if (compact) 9.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCountryFlag) {
                val flag = channel.flag.ifBlank { countryFlag(channel.country) }
                if (flag.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 24.dp else 28.dp)
                            .clip(CircleShape)
                            .background(if (isLight) Color(0xF2FFFFFF) else Color(0xE6081220))
                            .border(1.dp, if (isLight) CasaBorder else Color(0xFF1E3A5F), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = flag, fontSize = if (compact) 13.sp else 15.sp)
                    }
                }
            }
            if (channel.favorite) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 20.dp else 23.dp)
                        .clip(CircleShape)
                        .background(if (isLight) Color(0xF2FFFFFF) else Color(0xCC060D18))
                        .border(1.dp, if (isLight) CasaBorder else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = LocalAppStrings.current.t("Preferito", "Favorite"),
                        tint = if (isLight) it.casatv.tv.ui.theme.CasaAmber else CasaGreen,
                        modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isLight) Color(0xF2FFFFFF) else Color(0xCC060D18))
                .border(1.dp, if (isLight) CasaBorder else Color.Transparent, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(statusColor(channel.status)),
            )
            if (!compact) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = statusLabel(channel.status),
                    color = if (isLight) CasaText else CasaMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun ChannelLogo(
    channel: Channel,
    modifier: Modifier = Modifier,
) {
    var failed by remember(channel.logo, channel.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    if (channel.logo.isNullOrBlank() || failed) {
        FallbackLogo(channel.name, modifier)
    } else {
        val request = remember(channel.logo, isLowRam) {
            ImageRequest.Builder(context)
                .data(channel.logo)
                .precision(Precision.INEXACT)
                .apply {
                    if (isLowRam) {
                        size(192, 128)
                        crossfade(false)
                        allowRgb565(true)
                    } else {
                        size(320, 200)
                        crossfade(180)
                    }
                }
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = channel.name,
            contentScale = ContentScale.Fit,
            modifier = modifier,
            onError = { failed = true },
        )
    }
}

@Composable
private fun FallbackLogo(name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(CasaGreen.copy(alpha = 0.12f))
                .border(1.dp, CasaGreen.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials(name),
                color = CasaGreen,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }
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
    val shape = RoundedCornerShape(16.dp)
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val background = when {
        !enabled -> if (isLight) Color(0x0D000000) else Color(0x0DFFFFFF)
        focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        primary -> if (isLight) Color(0x1F0070F3) else Color(0x24FFFFFF)
        else -> if (isLight) CasaSurface else Color(0x14FFFFFF)
    }
    val contentColor = when {
        !enabled -> if (isLight) Color(0x40000000) else Color(0x40EBEBF5)
        focused -> if (isLight) Color.White else Color.Black
        primary -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        else -> if (isLight) CasaText else Color.White
    }
    val borderColor = when {
        !enabled -> Color.Transparent
        focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        primary -> if (isLight) Color(0x660070F3) else Color(0x33FFFFFF)
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
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .widthIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 15.sp,
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
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) CasaSurfaceHigh else CasaSurface.copy(alpha = 0.85f))
            .border(
                if (highlighted) 2.dp else 1.dp,
                if (highlighted) CasaGreen else CasaBorder,
                RoundedCornerShape(50),
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
            tint = if (highlighted) CasaGreen else CasaDim,
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

@Composable
fun UniversalSearchResultCard(
    result: GlobalSearchResult,
    onClick: () -> Unit,
    onFavorite: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)

    val meta = when (result) {
        is GlobalSearchResult.ChannelResult -> {
            val ch = result.channel
            val flag = ch.flag.ifBlank { countryFlag(ch.country) }.ifBlank { "🌍" }
            val sub = ch.now?.title?.let { "${strings.t("Ora", "Now")}: $it" }
                ?: localizedSubtitle(ch, strings.language)
            SearchCardMeta(badge = flag, title = ch.name, subtitle = sub, poster = ch.logo, isChannel = true)
        }
    }

    val isLight = it.casatv.tv.ui.theme.CasaIsLight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(168.dp)
            .zIndex(if (focused) 2f else 0f)
            .clip(shape)
            .background(if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaSurface)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaBorder,
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .onPreviewKeyEvent { event ->
                if (
                    meta.isChannel &&
                    event.type == KeyEventType.KeyUp &&
                    event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MENU
                ) {
                    onFavorite()
                    true
                } else {
                    false
                }
            },
    ) {
        if (result is GlobalSearchResult.ChannelResult) {
            ChannelArtwork(
                channel = result.channel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                compact = true,
                showCountryFlag = false,
                focused = focused,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (focused) (if (isLight) Color(0x33FFFFFF) else Color(0x22000000)) else (if (isLight) Color(0x14000000) else Color(0x33FFFFFF))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = meta.badge, fontSize = 11.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = meta.title,
                    color = if (focused) (if (isLight) Color.White else Color.Black) else CasaText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta.subtitle,
                color = if (focused) (if (isLight) Color(0xD9FFFFFF) else Color(0x99000000)) else CasaMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class SearchCardMeta(
    val badge: String,
    val title: String,
    val subtitle: String,
    val poster: String?,
    val isChannel: Boolean,
)
