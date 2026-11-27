package it.casatv.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val AVATAR_PALETTE: Map<String, Pair<Color, Color>> = mapOf(
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

private val AVATAR_KEYS = AVATAR_PALETTE.keys.toList()

fun avatarColors(token: String?, seed: String = ""): Pair<Color, Color> {
    val norm = token.orEmpty().trim().lowercase()
    AVATAR_PALETTE[norm]?.let { return it }
    val hash = abs((norm.ifBlank { seed }).hashCode())
    val key = AVATAR_KEYS[hash % AVATAR_KEYS.size]
    return AVATAR_PALETTE[key] ?: (Color(0xFF1D4ED8) to Color(0xFF60A5FA))
}

@Composable
fun ProfileAvatarBadge(
    avatar: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    isGuest: Boolean = false,
) {
    val (c1, c2) = avatarColors(avatar, name)
    val initial = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c1, c2)))
            .border(1.dp, Color(0x44FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isGuest || initial == null || avatar?.trim()?.lowercase() == "slate") {
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
