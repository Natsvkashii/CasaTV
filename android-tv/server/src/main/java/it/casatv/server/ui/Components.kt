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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .background(CasaPanel, RoundedCornerShape(16.dp))
            .border(1.dp, CasaBorder, RoundedCornerShape(16.dp))
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
        danger -> Color(0x33E4635B)
        primary -> CasaGreen
        else -> CasaPanelHigh
    }
    val foreground = when {
        !enabled -> CasaDim
        primary -> Color(0xFF04140B)
        danger -> Color(0xFFFFB4AE)
        else -> CasaText
    }
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(12.dp))
            .border(1.dp, if (primary) CasaGreen else CasaBorder, RoundedCornerShape(12.dp))
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
                if (selected) CasaGreen.copy(alpha = 0.16f) else CasaPanelHigh,
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                if (selected) CasaGreen else CasaBorder,
                RoundedCornerShape(10.dp),
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
