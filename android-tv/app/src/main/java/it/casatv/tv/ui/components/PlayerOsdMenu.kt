package it.casatv.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.theme.CasaAmber
import it.casatv.tv.ui.theme.CasaBlue
import it.casatv.tv.ui.theme.CasaBorder
import it.casatv.tv.ui.theme.CasaDim
import it.casatv.tv.ui.theme.CasaGreen
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaSurface
import it.casatv.tv.ui.theme.CasaText
import java.util.Locale

data class OsdAudioTrack(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean,
)

data class OsdSubtitleTrack(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean,
)

fun extractOsdAudioTracks(tracks: Tracks): List<OsdAudioTrack> {
    val result = mutableListOf<OsdAudioTrack>()
    var count = 1
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val lang = format.language?.takeIf { it.isNotBlank() }
            val langName = lang?.let {
                runCatching {
                    val loc = Locale(it)
                    loc.getDisplayLanguage(loc).ifBlank { loc.displayLanguage }
                }.getOrNull()
            }?.replaceFirstChar { it.uppercase() }

            val channelDesc = when (format.channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> if (format.channelCount > 0) "${format.channelCount} ch" else null
            }
            val mimeDesc = when {
                format.sampleMimeType?.contains("mp4a", ignoreCase = true) == true -> "AAC"
                format.sampleMimeType?.contains("ac3", ignoreCase = true) == true -> "AC3"
                format.sampleMimeType?.contains("eac3", ignoreCase = true) == true -> "E-AC3"
                format.sampleMimeType?.contains("opus", ignoreCase = true) == true -> "Opus"
                else -> null
            }
            val label = format.label?.takeIf { it.isNotBlank() }
            val title = listOfNotNull(
                label ?: langName ?: "Audio #$count",
                channelDesc,
                mimeDesc,
            ).joinToString(" · ")

            result.add(OsdAudioTrack(group, i, title, group.isTrackSelected(i)))
            count++
        }
    }
    return result
}

fun extractOsdSubtitleTracks(tracks: Tracks): List<OsdSubtitleTrack> {
    val result = mutableListOf<OsdSubtitleTrack>()
    var count = 1
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            val lang = format.language?.takeIf { it.isNotBlank() }
            val langName = lang?.let {
                runCatching {
                    val loc = Locale(it)
                    loc.getDisplayLanguage(loc).ifBlank { loc.displayLanguage }
                }.getOrNull()
            }?.replaceFirstChar { it.uppercase() }

            val label = format.label?.takeIf { it.isNotBlank() }
            val title = label ?: langName ?: "Sottotitoli #$count"

            result.add(OsdSubtitleTrack(group, i, title, group.isTrackSelected(i)))
            count++
        }
    }
    return result
}

fun applyAudioTrack(player: Player, track: OsdAudioTrack) {
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
        .build()
}

fun applySubtitleTrack(player: Player, track: OsdSubtitleTrack) {
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
        .build()
}

fun disableSubtitles(player: Player) {
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .build()
}

fun formatStreamInfo(videoSize: VideoSize, videoFormat: Format?): String {
    val parts = mutableListOf<String>()
    if (videoSize.width > 0 && videoSize.height > 0) {
        parts.add("${videoSize.width}×${videoSize.height}")
    }
    if (videoFormat != null) {
        if (videoFormat.frameRate > 0) {
            parts.add("${videoFormat.frameRate.toInt()} fps")
        }
        val codec = when {
            videoFormat.sampleMimeType?.contains("avc", ignoreCase = true) == true -> "H.264"
            videoFormat.sampleMimeType?.contains("hevc", ignoreCase = true) == true -> "H.265 / HEVC"
            videoFormat.sampleMimeType?.contains("vp9", ignoreCase = true) == true -> "VP9"
            videoFormat.sampleMimeType?.contains("av01", ignoreCase = true) == true -> "AV1"
            else -> videoFormat.sampleMimeType?.substringAfterLast("/")
        }
        codec?.let { parts.add(it) }
    }
    return parts.joinToString(" · ")
}

@Composable
fun PlayerOsdMenu(
    selectedRow: Int,
    selectedCol: Int,
    currentAspectMode: Int,
    onSelectAspectMode: (Int) -> Unit,
    audioTracks: List<OsdAudioTrack>,
    onSelectAudioTrack: (OsdAudioTrack) -> Unit,
    subtitleTracks: List<OsdSubtitleTrack>,
    subtitlesDisabled: Boolean,
    onDisableSubtitles: () -> Unit,
    onSelectSubtitleTrack: (OsdSubtitleTrack) -> Unit,
    isFavorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onOpenCountryPicker: (() -> Unit)? = null,
    onToggleInfo: (() -> Unit)? = null,
    streamDetails: String = "",
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current

    val isLight = it.casatv.tv.ui.theme.CasaIsLight

    Box(
        modifier = modifier
            .padding(bottom = 64.dp, start = 48.dp, end = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isLight) Color(0xF5FFFFFF) else Color(0xF208111E))
            .border(1.dp, if (isLight) CasaBorder else Color(0xFF1E3A5F), RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.t("OPZIONI AUDIO E VIDEO", "AUDIO & VIDEO OPTIONS", "OPCIONES DE AUDIO Y VÍDEO"),
                    color = CasaBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )
                if (streamDetails.isNotBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isLight) Color(0x14000000) else Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = streamDetails,
                            color = if (isLight) CasaDim else Color(0x99EBEBF5),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = strings.t(
                        "▲ ▼ righe · ◄ ► voci · OK seleziona · BACK chiudi",
                        "▲ ▼ rows · ◄ ► items · OK select · BACK close",
                        "▲ ▼ filas · ◄ ► opciones · OK seleccionar · BACK cerrar",
                    ),
                    color = if (isLight) CasaDim else Color(0x99EBEBF5),
                    fontSize = 10.sp,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isLight) Color(0x14000000) else Color.White.copy(alpha = 0.1f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Chiudi",
                        tint = if (isLight) CasaText else Color(0xCCEBEBF5),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Row 0: Formato Schermo (Aspect Ratio)
            OsdSectionRow(
                title = strings.t("FORMATO", "FORMAT", "FORMATO"),
                icon = "📺",
            ) {
                val modes = listOf(
                    Triple(
                        AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        strings.t("Adatta (16:9)", "Fit (16:9)", "Ajustar (16:9)"),
                        strings.t("Originale", "Original", "Original"),
                    ),
                    Triple(
                        AspectRatioFrameLayout.RESIZE_MODE_FILL,
                        strings.t("Riempi (Stretch)", "Fill (Stretch)", "Llenar (Stretch)"),
                        strings.t("Schermo intero", "Full screen", "Pantalla completa"),
                    ),
                    Triple(
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        strings.t("Zoom (Ritaglio)", "Zoom (Crop)", "Zoom (Recorte)"),
                        strings.t("Senza distorsioni", "No distortion", "Sin distorsión"),
                    ),
                )
                modes.forEachIndexed { col, (mode, label, desc) ->
                    val isFocused = selectedRow == 0 && selectedCol == col
                    val isSelected = currentAspectMode == mode
                    OsdItemButton(
                        label = label,
                        description = desc,
                        isSelected = isSelected,
                        isFocused = isFocused,
                        onClick = { onSelectAspectMode(mode) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Row 1: Audio Tracks
            OsdSectionRow(
                title = strings.t("AUDIO", "AUDIO", "AUDIO"),
                icon = "🔊",
            ) {
                if (audioTracks.isEmpty()) {
                    val isFocused = selectedRow == 1 && selectedCol == 0
                    OsdItemButton(
                        label = strings.t("Traccia predefinita", "Default track", "Pista predeterminada"),
                        description = strings.t("Unica traccia", "Single track", "Pista única"),
                        isSelected = true,
                        isFocused = isFocused,
                        onClick = {},
                    )
                } else {
                    audioTracks.forEachIndexed { col, track ->
                        val isFocused = selectedRow == 1 && selectedCol == col
                        OsdItemButton(
                            label = track.label,
                            description = if (track.isSelected) strings.t("Attiva", "Active", "Activa") else null,
                            isSelected = track.isSelected,
                            isFocused = isFocused,
                            onClick = { onSelectAudioTrack(track) },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Row 2: Subtitles
            OsdSectionRow(
                title = strings.t("SOTTOTITOLI", "SUBTITLES", "SUBTÍTULOS"),
                icon = "💬",
            ) {
                val isOffFocused = selectedRow == 2 && selectedCol == 0
                OsdItemButton(
                    label = strings.t("Disattivati", "Off", "Desactivados"),
                    description = if (subtitlesDisabled) strings.t("Nessun sottotitolo", "No subtitles", "Sin subtítulos") else null,
                    isSelected = subtitlesDisabled,
                    isFocused = isOffFocused,
                    onClick = onDisableSubtitles,
                )
                Spacer(Modifier.width(8.dp))

                if (subtitleTracks.isEmpty()) {
                    Box(
                        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = strings.t(
                                "Nessun sottotitolo disponibile nel flusso",
                                "No subtitles available in this stream",
                                "Sin subtítulos disponibles en este flujo",
                            ),
                            color = CasaDim,
                            fontSize = 10.sp,
                        )
                    }
                } else {
                    subtitleTracks.forEachIndexed { index, track ->
                        val col = index + 1
                        val isFocused = selectedRow == 2 && selectedCol == col
                        val isSelected = !subtitlesDisabled && track.isSelected
                        OsdItemButton(
                            label = track.label,
                            description = if (isSelected) strings.t("Attivo", "Active", "Activo") else null,
                            isSelected = isSelected,
                            isFocused = isFocused,
                            onClick = { onSelectSubtitleTrack(track) },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }

            // Row 3: Quick channel / playback actions
            if (onToggleFavorite != null || onOpenCountryPicker != null || onToggleInfo != null) {
                Spacer(Modifier.height(10.dp))
                OsdSectionRow(
                    title = strings.t("AZIONI", "ACTIONS", "ACCIONES"),
                    icon = "⚡",
                ) {
                    var colCount = 0
                    if (onToggleFavorite != null && isFavorite != null) {
                        val isFocused = selectedRow == 3 && selectedCol == colCount
                        OsdItemButton(
                            label = if (isFavorite) {
                                strings.t("Rimuovi da Preferiti", "Remove from Favorites", "Quitar de Favoritos")
                            } else {
                                strings.t("Aggiungi a Preferiti", "Add to Favorites", "Añadir a Favoritos")
                            },
                            description = if (isFavorite) "★ Preferito" else "☆ Non preferito",
                            isSelected = isFavorite,
                            isFocused = isFocused,
                            onClick = onToggleFavorite,
                        )
                        Spacer(Modifier.width(8.dp))
                        colCount++
                    }

                    if (onOpenCountryPicker != null) {
                        val isFocused = selectedRow == 3 && selectedCol == colCount
                        OsdItemButton(
                            label = strings.t("Cambia Paese TV", "Change TV Country", "Cambiar País TV"),
                            description = strings.t("Seleziona catalogo", "Select catalog", "Seleccionar catálogo"),
                            isSelected = false,
                            isFocused = isFocused,
                            onClick = onOpenCountryPicker,
                        )
                        Spacer(Modifier.width(8.dp))
                        colCount++
                    }

                    if (onToggleInfo != null) {
                        val isFocused = selectedRow == 3 && selectedCol == colCount
                        OsdItemButton(
                            label = strings.t("Trama & Dettagli", "Plot & Details", "Trama y Detalles"),
                            description = strings.t("Informazioni video", "Video info", "Información de vídeo"),
                            isSelected = false,
                            isFocused = isFocused,
                            onClick = onToggleInfo,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OsdSectionRow(
    title: String,
    icon: String,
    content: @Composable () -> Unit,
) {
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(130.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = icon, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                color = if (isLight) CasaMuted else Color(0x99EBEBF5),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun OsdItemButton(
    label: String,
    description: String? = null,
    isSelected: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
) {
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val shape = RoundedCornerShape(14.dp)
    val backgroundColor = when {
        isFocused -> if (isLight) CasaBlue else Color.White
        isSelected -> if (isLight) Color(0x1F0284C7) else Color(0x2E00D4FF)
        else -> if (isLight) Color(0x0A000000) else Color(0x140E1D33)
    }

    val borderColor = when {
        isFocused -> if (isLight) CasaBlue else Color.White
        isSelected -> CasaBlue
        else -> if (isLight) CasaBorder else Color(0xFF1E3A5F)
    }

    val borderWidth = if (isFocused) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .clip(shape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dotColor = when {
            isFocused -> if (isLight) Color.White else (if (isSelected) CasaBlue else Color.Black)
            isSelected -> CasaBlue
            else -> if (isLight) CasaDim else Color(0x99EBEBF5)
        }
        Text(
            text = if (isSelected) "●" else "○",
            color = dotColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            val labelColor = when {
                isFocused -> if (isLight) Color.White else Color.Black
                isSelected -> if (isLight) CasaBlue else Color.White
                else -> if (isLight) CasaText else Color(0xCCEBEBF5)
            }
            Text(
                text = label,
                color = labelColor,
                fontSize = 11.sp,
                fontWeight = if (isFocused || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                val descColor = when {
                    isFocused -> if (isLight) Color.White.copy(alpha = 0.85f) else Color(0x99000000)
                    isSelected -> CasaBlue
                    else -> if (isLight) CasaDim else Color(0x99EBEBF5)
                }
                Text(
                    text = description,
                    color = descColor,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
