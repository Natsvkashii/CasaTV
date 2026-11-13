package it.casatv.tv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import it.casatv.tv.data.GlobalSearchResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import it.casatv.tv.R
import it.casatv.tv.data.Channel
import it.casatv.tv.data.Profile
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainTab
import it.casatv.tv.ui.countryFlag
import it.casatv.tv.ui.formatNumber
import it.casatv.tv.ui.initials
import it.casatv.tv.ui.localizedSubtitle
import it.casatv.tv.ui.theme.CasaAmber
import it.casatv.tv.ui.theme.CasaBackground
import it.casatv.tv.ui.theme.CasaBackgroundDeep
import it.casatv.tv.ui.theme.CasaBorder
import it.casatv.tv.ui.theme.CasaDim
import it.casatv.tv.ui.theme.CasaGreen
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaRed
import it.casatv.tv.ui.theme.CasaSurface
import it.casatv.tv.ui.theme.CasaSurfaceHigh
import it.casatv.tv.ui.theme.CasaText

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
fun CasaLogo(compact: Boolean = false) {
    if (compact) {
        Image(
            painter = painterResource(R.drawable.ic_brand),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_brand),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "CasaTV",
                    color = CasaText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
                Text(
                    text = "IPTV LOCALE",
                    color = CasaDim,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                )
            }
        }
    }
}

@Composable
fun NavRail(
    selected: MainTab,
    serverName: String,
    serverUrl: String,
    profile: Profile?,
    isServerDisconnected: Boolean = false,
    onSwitchProfile: () -> Unit,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isRailFocused by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val scale = rememberTvUiScale()

    LaunchedEffect(isRailFocused) {
        if (isRailFocused) {
            delay(160) // Prevent flickering open/close on transient focus pass-throughs
            if (isRailFocused) isExpanded = true
        } else {
            isExpanded = false
        }
    }

    val width by animateDpAsState(
        targetValue = if (isExpanded) 220.dp else if (scale.isCompact) 58.dp else 68.dp,
        animationSpec = tween(120),
        label = "nav-width",
    )
    val railShape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .onFocusChanged { isRailFocused = it.hasFocus }
            .clip(railShape)
            .background(CasaSurface)
            .border(1.dp, CasaBorder, railShape)
            .padding(horizontal = if (isExpanded) 12.dp else 6.dp, vertical = if (scale.isCompact) 12.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CasaLogo(compact = !isExpanded)
        if (isServerDisconnected) {
            Spacer(Modifier.height(6.dp))
            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x26FF5555))
                        .border(1.dp, Color(0x66FF5555), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5555)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Server scollegato",
                        color = Color(0xFFFF7777),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(if (scale.isCompact) 24.dp else 28.dp)
                        .clip(CircleShape)
                        .background(Color(0x26FF5555))
                        .border(1.dp, Color(0x66FF5555), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5555)),
                    )
                }
            }
        }
        Spacer(Modifier.height(if (scale.isCompact) 14.dp else 18.dp))
        val primaryTabs = listOf(MainTab.Home, MainTab.Tv)
        primaryTabs.forEach { tab ->
            NavRailItem(
                tab = tab,
                selected = selected == tab,
                isExpanded = isExpanded,
                onClick = { onSelect(tab) },
            )
            Spacer(Modifier.height(if (scale.isCompact) 4.dp else 6.dp))
        }
        Spacer(Modifier.weight(1f))
        NavRailItem(
            tab = MainTab.Settings,
            selected = selected == MainTab.Settings,
            isExpanded = isExpanded,
            onClick = { onSelect(MainTab.Settings) },
        )
        Spacer(Modifier.height(if (scale.isCompact) 6.dp else 8.dp))
        if (profile != null) {
            ProfileChip(
                profile = profile,
                isExpanded = isExpanded,
                onClick = onSwitchProfile,
            )
            Spacer(Modifier.height(if (scale.isCompact) 6.dp else 8.dp))
        }
        if (isExpanded) {
            ServerCard(
                serverName = serverName,
                serverUrl = serverUrl,
                isDisconnected = isServerDisconnected,
            )
        }
    }
}

@Composable
private fun NavRailItem(
    tab: MainTab,
    selected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val background = when {
        focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        selected -> if (isLight) Color(0x1F0070F3) else Color(0x26FFFFFF)
        else -> Color.Transparent
    }
    val contentColor = when {
        focused -> if (isLight) Color.White else Color.Black
        selected -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        else -> CasaMuted
    }

    Row(
        modifier = Modifier
            .then(if (isExpanded) Modifier.fillMaxWidth() else Modifier.size(46.dp))
            .clip(shape)
            .background(background)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else if (selected) (if (isLight) Color(0x330070F3) else Color(0x33FFFFFF)) else Color.Transparent,
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = if (isExpanded) 12.dp else 0.dp, vertical = if (isExpanded) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label(LocalAppStrings.current.language),
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        if (isExpanded) {
            Spacer(Modifier.width(11.dp))
            Text(
                text = tab.label(LocalAppStrings.current.language),
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProfileChip(
    profile: Profile,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val strings = LocalAppStrings.current
    val shape = if (isExpanded) RoundedCornerShape(16.dp) else CircleShape
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    Row(
        modifier = Modifier
            .then(if (isExpanded) Modifier.fillMaxWidth() else Modifier.size(48.dp))
            .clip(shape)
            .background(if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else if (isLight) CasaSurface else Color(0x1AFFFFFF))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaBorder,
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = if (isExpanded) 10.dp else 0.dp, vertical = if (isExpanded) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (isExpanded) 32.dp else 40.dp)
                .clip(CircleShape)
                .border(2.dp, if (focused) (if (isLight) Color.White else Color.Black) else if (isLight) CasaBorder else Color.White.copy(alpha = 0.85f), CircleShape)
                .background(if (isLight) CasaSurfaceHigh else Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = profile.avatar, fontSize = if (isExpanded) 16.sp else 22.sp)
        }
        if (isExpanded) {
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    color = if (focused) (if (isLight) Color.White else Color.Black) else CasaText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = strings.t("Profilo", "Profile", "Perfil"),
                    color = if (focused) (if (isLight) Color(0xCCFFFFFF) else Color(0x99000000)) else CasaDim,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ServerCard(
    serverName: String,
    serverUrl: String,
    isDisconnected: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    val dotColor = if (isDisconnected) Color(0xFFFF5555) else CasaGreen
    val borderColor = if (isDisconnected) Color(0x66FF5555) else CasaBorder
    val bgColor = if (isDisconnected) Color(0x1AFF5555) else Color(0x14FFFFFF)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (isDisconnected) "${serverName.ifBlank { "CasaTV" }} (Offline)" else serverName.ifBlank { "CasaTV" },
                color = if (isDisconnected) Color(0xFFFF8888) else CasaText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (isDisconnected) "Flussi memorizzati attivi" else serverUrl.removePrefix("http://").removePrefix("https://"),
            color = if (isDisconnected) Color(0xFFFFBBAA) else CasaMuted,
            fontSize = 9.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CasaGreen),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = eyebrow.uppercase(),
                    color = CasaGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = CasaText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CasaMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun HomeChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 215.dp,
    cardHeight: Dp = 215.dp,
    artworkHeight: Dp = 120.dp,
) {
    val strings = LocalAppStrings.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    val isLight = it.casatv.tv.ui.theme.CasaIsLight

    Column(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .zIndex(if (focused) 2f else 0f)
            .clip(shape)
            .background(if (focused) (if (isLight) CasaSurface else Color.White) else CasaSurface)
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
                .height(artworkHeight),
            compact = false,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                text = channel.name,
                color = if (focused) (if (isLight) CasaText else Color.Black) else CasaText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = channel.now?.title?.let {
                    "${strings.t("Ora", "Now")}: $it"
                } ?: localizedSubtitle(channel, strings.language),
                color = if (focused) (if (isLight) CasaMuted else Color(0x99000000)) else if (channel.now != null) CasaMuted else CasaDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.categoryNames.firstOrNull() ?: channel.countryName,
                    color = if (focused) (if (isLight) CasaMuted else Color(0x80000000)) else CasaDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (focused) (if (isLight) Color(0x1F0070F3) else Color(0x1F000000)) else (if (isLight) Color(0x14000000) else Color(0x14FFFFFF)))
                        .border(1.dp, if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue.copy(alpha = 0.5f) else Color(0x33000000)) else CasaBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = channel.quality,
                        color = if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.Black) else CasaMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
        }
    }
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
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    val isLight = it.casatv.tv.ui.theme.CasaIsLight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(168.dp)
            .zIndex(if (focused) 2f else 0f)
            .clip(shape)
            .background(if (focused) (if (isLight) CasaSurface else Color.White) else CasaSurface)
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
                                .background(if (focused) (if (isLight) Color(0x1F0070F3) else Color(0x22000000)) else (if (isLight) Color(0x14000000) else Color(0x33FFFFFF))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = flag, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(
                    text = channel.name,
                    color = if (focused) (if (isLight) CasaText else Color.Black) else CasaText,
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
                color = if (focused) (if (isLight) CasaMuted else Color(0x99000000)) else CasaMuted,
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
) {
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    Box(
        modifier = modifier
            .background(
                if (isLight) {
                    Brush.linearGradient(
                        listOf(Color(0xFFE2E8F0), Color(0xFFEDF2F7)),
                    )
                } else {
                    Brush.linearGradient(
                        listOf(Color(0xFF17272D), Color(0xFF0C151A)),
                    )
                },
            ),
    ) {
        ChannelLogo(
            channel = channel,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 14.dp else 20.dp),
        )
        if (channel.number != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isLight) Color(0xF2FFFFFF) else Color(0xB3060B0E))
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
                            .background(if (isLight) Color(0xF2FFFFFF) else Color(0xE60A131A))
                            .border(1.dp, if (isLight) CasaBorder else Color(0x66FFFFFF), CircleShape),
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
                        .background(if (isLight) Color(0xF2FFFFFF) else Color(0xB3060B0E))
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
                .background(if (isLight) Color(0xF2FFFFFF) else Color(0xB3060B0E))
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
    if (channel.logo.isNullOrBlank() || failed) {
        FallbackLogo(channel.name, modifier)
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(channel.logo)
                .crossfade(180)
                .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                .build(),
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
fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(9999.dp)
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val background = when {
        focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        selected -> it.casatv.tv.ui.theme.CasaBlue
        else -> if (isLight) CasaSurface else Color(0x14FFFFFF)
    }
    val contentColor = when {
        focused -> if (isLight) Color.White else Color.Black
        selected -> Color.White
        else -> CasaMuted
    }
    val borderColor = when {
        focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        selected -> it.casatv.tv.ui.theme.CasaBlue
        else -> CasaBorder
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun InfoPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CasaSurface.copy(alpha = 0.8f))
            .border(1.dp, CasaBorder, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = CasaMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
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
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isFocused) CasaSurfaceHigh else CasaSurface.copy(alpha = 0.85f))
            .border(
                1.dp,
                if (isFocused) CasaGreen else CasaBorder,
                RoundedCornerShape(50),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = if (isFocused) CasaGreen else CasaDim,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
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
                    keyboard?.hide()
                    onSearch()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
            )
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
                    .clickable { onClear() },
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
            .background(if (focused) (if (isLight) CasaSurface else Color.White) else CasaSurface)
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
                        .background(if (focused) (if (isLight) Color(0x1F0070F3) else Color(0x22000000)) else (if (isLight) Color(0x14000000) else Color(0x33FFFFFF))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = meta.badge, fontSize = 11.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = meta.title,
                    color = if (focused) (if (isLight) CasaText else Color.Black) else CasaText,
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
                color = if (focused) (if (isLight) CasaMuted else Color(0x99000000)) else CasaMuted,
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
