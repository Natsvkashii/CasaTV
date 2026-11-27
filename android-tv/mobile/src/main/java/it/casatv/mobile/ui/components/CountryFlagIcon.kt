package it.casatv.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import java.util.Locale

fun resolveMobileFlagAssetCode(raw: String?): String? {
    val clean = raw.orEmpty().trim().lowercase(Locale.ROOT)
    val mapped = when (clean) {
        "uk" -> "gb"
        else -> clean
    }
    if (mapped.length != 2 || mapped.any { it !in 'a'..'z' }) return null
    return mapped
}

@Composable
fun CountryFlagIcon(
    countryCode: String?,
    modifier: Modifier = Modifier,
    width: Dp = 20.dp,
    height: Dp = 14.dp,
    cornerRadius: Dp = 3.dp,
    fallbackTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val assetCode = remember(countryCode) { resolveMobileFlagAssetCode(countryCode) }
    val context = LocalContext.current
    var failed by remember(assetCode) { mutableStateOf(false) }

    if (assetCode != null && !failed) {
        val request = remember(assetCode, context) {
            ImageRequest.Builder(context)
                .data("file:///android_asset/flags/$assetCode.svg")
                .decoderFactory(SvgDecoder.Factory())
                .build()
        }
        val shape = RoundedCornerShape(cornerRadius)
        Box(
            modifier = modifier
                .size(width = width, height = height)
                .clip(shape)
                .background(Color(0x22000000))
                .border(0.5.dp, Color(0x33888888), shape),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = request,
                contentDescription = assetCode.uppercase(Locale.ROOT),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
        }
    } else {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = null,
            tint = fallbackTint,
            modifier = modifier.size(minOf(width, height).coerceAtLeast(14.dp)),
        )
    }
}
