package it.casatv.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlin.math.abs
import it.casatv.server.ui.theme.CasaBorder
import it.casatv.server.ui.theme.CasaDim
import it.casatv.server.ui.theme.CasaGreen
import it.casatv.server.ui.theme.CasaMuted
import it.casatv.server.ui.theme.CasaPanel
import it.casatv.server.ui.theme.CasaPanelHigh
import it.casatv.server.ui.theme.CasaText
import java.util.Locale

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CasaPanel, RoundedCornerShape(8.dp))
            .border(1.dp, CasaBorder, RoundedCornerShape(8.dp))
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text.uppercase(Locale.getDefault()),
            color = CasaDim,
            fontSize = 11.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (trailing != null) {
            Text(trailing, color = CasaMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, color = CasaText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = CasaMuted, fontSize = 12.sp)
    }
}

@Composable
fun CasaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val background = when {
        danger -> Color(0x28E50914)
        primary -> Color.White
        else -> CasaPanelHigh
    }
    val foreground = when {
        !enabled -> CasaDim
        primary -> Color.Black
        danger -> Color(0xFFFFB4AE)
        else -> CasaText
    }
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(6.dp))
            .border(1.dp, if (primary) Color.White else CasaBorder, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                if (selected) Color.White else CasaPanelHigh,
                RoundedCornerShape(6.dp),
            )
            .border(
                1.dp,
                if (selected) Color.White else CasaBorder,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            color = if (selected) CasaGreen else CasaMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun CasaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = CasaMuted) },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = if (number) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = CasaText,
            unfocusedTextColor = CasaText,
            focusedBorderColor = CasaGreen,
            unfocusedBorderColor = CasaBorder,
            cursorColor = CasaGreen,
            focusedContainerColor = CasaPanelHigh,
            unfocusedContainerColor = CasaPanelHigh,
        ),
    )
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1.0) return String.format(Locale.getDefault(), "%.1f GB", gb)
    return String.format(Locale.getDefault(), "%.0f MB", bytes / 1_048_576.0)
}

@Composable
fun SpacerH(height: Int) {
    Spacer(Modifier.height(height.dp))
}

@Composable
fun Dot(color: Color, size: Int = 9) {
    Box(
        Modifier
            .size(size.dp)
            .background(color, RoundedCornerShape(50)),
    )
}

@Composable
fun KV(key: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = CasaMuted, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        Text(value, color = CasaText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private val SERVER_AVATAR_PALETTE: Map<String, Pair<Color, Color>> = mapOf(
    "blue" to (Color(0xFF1D4ED8) to Color(0xFF60A5FA)),
    "emerald" to (Color(0xFF047857) to Color(0xFF34D399)),
    "cyan" to (Color(0xFF0E7490) to Color(0xFF22D3EE)),
    "amber" to (Color(0xFFB45309) to Color(0xFFFBBF24)),
    "violet" to (Color(0xFF6D28D9) to Color(0xFFA78BFA)),
    "rose" to (Color(0xFFBE123C) to Color(0xFFFB7185)),
    "indigo" to (Color(0xFF4338CA) to Color(0xFF818CF8)),
    "slate" to (Color(0xFF334155) to Color(0xFF94A3B8)),
    "teal" to (Color(0xFF0F766E) to Color(0xFF2DD4BF)),
    "orange" to (Color(0xFFC2410C) to Color(0xFFFB923C)),
    "sky" to (Color(0xFF0369A1) to Color(0xFF38BDF8)),
    "lime" to (Color(0xFF3F6212) to Color(0xFFA3E635)),
)

@Composable
fun ServerProfileAvatarBadge(
    avatar: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    isGuest: Boolean = false,
) {
    val norm = avatar.orEmpty().trim().lowercase()
    val keys = remember { SERVER_AVATAR_PALETTE.keys.toList() }
    val pair = SERVER_AVATAR_PALETTE[norm] ?: SERVER_AVATAR_PALETTE[keys[abs((norm.ifBlank { name }).hashCode()) % keys.size]]
        ?: (Color(0xFF1D4ED8) to Color(0xFF60A5FA))
    val initial = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(pair.first, pair.second)))
            .border(1.dp, Color(0x44FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isGuest || initial == null || norm == "slate") {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = name,
                tint = Color.White,
                modifier = Modifier.size(size * 0.52f),
            )
        } else {
            Text(
                text = initial,
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ServerCountryFlagIcon(
    countryCode: String?,
    modifier: Modifier = Modifier,
    width: Dp = 20.dp,
    height: Dp = 14.dp,
) {
    val clean = remember(countryCode) {
        val norm = countryCode.orEmpty().trim().lowercase(Locale.ROOT).let { if (it == "uk") "gb" else it }
        if (norm.length == 2 && norm.all { it in 'a'..'z' }) norm else null
    }
    val context = LocalContext.current
    var failed by remember(clean) { mutableStateOf(false) }
    if (clean != null && !failed) {
        val req = remember(clean, context) {
            ImageRequest.Builder(context)
                .data("file:///android_asset/flags/$clean.svg")
                .decoderFactory(SvgDecoder.Factory())
                .build()
        }
        val shape = RoundedCornerShape(3.dp)
        Box(
            modifier = modifier
                .size(width = width, height = height)
                .clip(shape)
                .background(Color(0x22FFFFFF))
                .border(0.5.dp, Color(0x33FFFFFF), shape),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = req,
                contentDescription = clean.uppercase(Locale.ROOT),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onError = { failed = true },
            )
        }
    } else {
        Text(
            text = countryCode?.take(2)?.uppercase(Locale.ROOT).orEmpty().ifBlank { "—" },
            color = CasaMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier,
        )
    }
}
