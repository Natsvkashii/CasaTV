package it.casatv.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.casatv.tv.data.Channel
import it.casatv.tv.data.RecordingDto
import it.casatv.tv.data.SeriesDetailResponse
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.components.CountryFlagIcon
import it.casatv.tv.ui.components.EmptyBox
import it.casatv.tv.ui.components.TvButton
import it.casatv.tv.ui.theme.CasaBackground
import it.casatv.tv.ui.theme.CasaBorder
import it.casatv.tv.ui.theme.CasaIsLight
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaRed
import it.casatv.tv.ui.theme.CasaSurface
import it.casatv.tv.ui.theme.CasaSurfaceHigh
import it.casatv.tv.ui.theme.CasaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SeriesDetailScreen(
    detail: SeriesDetailResponse,
    onDismiss: () -> Unit,
    onPlayEpisode: (Channel, List<Channel>) -> Unit,
) {
    val strings = LocalAppStrings.current
    val isLight = CasaIsLight
    val focusFrame = if (isLight) Color.Black else Color.White
    val seasons = detail.seasons
    val allEpisodes = remember(detail) { seasons.flatMap { it.episodes } }
    val resumeEp = remember(detail) {
        allEpisodes.firstOrNull { it.id == detail.resumeEpisodeId } ?: allEpisodes.firstOrNull()
    }
    val initialSeason = remember(detail) {
        seasons.firstOrNull { s -> s.episodes.any { it.id == detail.resumeEpisodeId } }?.season
            ?: seasons.firstOrNull()?.season ?: 1
    }
    var selectedSeason by remember(detail) { mutableIntStateOf(initialSeason) }
    val visibleEpisodes = remember(detail, selectedSeason) {
        seasons.firstOrNull { it.season == selectedSeason }?.episodes ?: seasons.firstOrNull()?.episodes.orEmpty()
    }
    val primaryFocus = remember { FocusRequester() }

    LaunchedEffect(detail.showTitle) {
        delay(80)
        runCatching { primaryFocus.requestFocus() }
    }

    BackHandler { onDismiss() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CasaBackground)
            .padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Top navigation bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvButton(
                text = strings.t("Indietro", "Back", "Volver"),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                primary = false,
                onClick = onDismiss,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = strings.t("SERIE TV", "TV SERIES", "SERIE DE TV"),
                color = CasaMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            )
        }

        // Full-width Series Hero Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CasaSurface)
                    .border(1.dp, CasaBorder, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (!detail.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = detail.logo,
                        contentDescription = detail.showTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = detail.showTitle.take(2).uppercase(),
                        color = CasaText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = detail.showTitle,
                    color = CasaText,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!detail.country.isNullOrBlank()) {
                        CountryFlagIcon(countryCode = detail.country, width = 20.dp, height = 14.dp, cornerRadius = 2.dp)
                    }
                    val meta = listOfNotNull(
                        detail.countryName.takeIf { it.isNotBlank() },
                        detail.category.takeIf { it.isNotBlank() },
                        "${seasons.size} ${if (seasons.size == 1) strings.t("Stagione", "Season", "Temporada") else strings.t("Stagioni", "Seasons", "Temporadas")}",
                        "${detail.totalEpisodes} ${strings.t("episodi", "episodes", "episodios")}",
                    ).joinToString(" · ")
                    Text(
                        text = meta,
                        color = CasaMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (resumeEp != null) {
                    val epCode = "S${(resumeEp.seasonNumber ?: 1).toString().padStart(2, '0')}E${(resumeEp.episodeNumber ?: 1).toString().padStart(2, '0')}"
                    val playLabel = when {
                        resumeEp.positionMs > 15_000L -> "${strings.t("Riprendi", "Resume", "Reanudar")} $epCode"
                        resumeEp.upNext -> "${strings.t("Prossimo", "Up Next", "Siguiente")} $epCode"
                        else -> "${strings.t("Riproduci", "Play", "Reproducir")} $epCode"
                    }
                    Row {
                        TvButton(
                            text = playLabel,
                            icon = Icons.Filled.PlayArrow,
                            primary = true,
                            onClick = { onPlayEpisode(resumeEp, allEpisodes) },
                            modifier = Modifier.focusRequester(primaryFocus),
                        )
                    }
                }
            }
        }

        // Season pills
        if (seasons.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(seasons, key = { "sea_${it.season}" }) { s ->
                    TvButton(
                        text = "${strings.t("Stagione", "Season", "Temporada")} ${s.season} (${s.episodes.size})",
                        primary = selectedSeason == s.season,
                        onClick = { selectedSeason = s.season },
                    )
                }
            }
        }

        // Fullscreen episode list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(visibleEpisodes, key = { _, ep -> "ep_${ep.id}" }) { idx, ep ->
                val epNum = ep.episodeNumber ?: (idx + 1)
                val epTitle = ep.episodeTitle?.takeIf { it.isNotBlank() }
                    ?: "${strings.t("Episodio", "Episode", "Episodio")} $epNum"
                val isResume = ep.id == detail.resumeEpisodeId
                val pct = if (ep.durationMs > 0L && ep.positionMs > 15_000L) {
                    (ep.positionMs.toFloat() / ep.durationMs.toFloat()).coerceIn(0.03f, 0.98f)
                } else 0f
                val sub = when {
                    ep.upNext -> strings.t("Prossimo episodio", "Up next", "Siguiente episodio")
                    pct > 0f -> "${strings.t("In corso", "In progress", "En progreso")} · ${(pct * 100).toInt()}%"
                    else -> ep.name
                }
                val epInteraction = remember { MutableInteractionSource() }
                val epFocused by epInteraction.collectIsFocusedAsState()
                val rowBg = if (epFocused) focusFrame else CasaSurface
                val rowFg = if (epFocused) (if (isLight) Color.White else Color.Black) else CasaText
                val rowSub = if (epFocused) rowFg.copy(alpha = 0.72f) else CasaMuted

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(rowBg)
                        .border(
                            width = if (epFocused) 2.5.dp else if (isResume) 1.5.dp else 1.dp,
                            color = if (epFocused) focusFrame else if (isResume) CasaText else CasaBorder,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .clickable(interactionSource = epInteraction, indication = null) {
                            onPlayEpisode(ep, allEpisodes)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = epNum.toString(),
                        color = rowFg,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(34.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = epTitle,
                            color = rowFg,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = sub,
                            color = rowSub,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (pct > 0f) {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (epFocused) rowFg.copy(alpha = 0.25f) else CasaBorder),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(pct)
                                        .background(rowFg),
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = rowFg,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val loading by vm.recordingsLoading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.loadRecordings()
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CasaBackground)
            .padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvButton(
                text = strings.t("Indietro", "Back", "Volver"),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                primary = false,
                onClick = onBack,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.t("Registrazioni salvate", "Saved Recordings", "Grabaciones guardadas"),
                    color = CasaText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = strings.t(
                        "Le registrazioni vengono conservate sul server per 7 giorni ed eliminate automaticamente.",
                        "Recordings are kept on the server for 7 days and deleted automatically.",
                        "Las grabaciones se guardan en el servidor 7 días y se eliminan automáticamente.",
                    ),
                    color = CasaMuted,
                    fontSize = 12.sp,
                )
            }
            TvButton(
                text = strings.t("Aggiorna", "Refresh", "Actualizar"),
                icon = Icons.Filled.Refresh,
                primary = false,
                onClick = { vm.loadRecordings() },
            )
        }

        if (loading && recordings.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CasaText, strokeWidth = 3.dp)
            }
        } else if (recordings.isEmpty()) {
            EmptyBox(
                title = strings.t("Nessuna registrazione", "No recordings", "Sin grabaciones"),
                message = strings.t(
                    "Durante la visione di un canale in diretta, tieni premuto il tasto OK per 3 secondi per avviare una registrazione.",
                    "While watching live TV, hold OK for 3 seconds to start recording.",
                    "Al ver TV en directo, mantén pulsado OK durante 3 segundos para grabar.",
                ),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(recordings, key = { it.id }) { rec ->
                    RecordingRowCard(
                        rec = rec,
                        onPlay = { vm.playRecording(rec) },
                        onDownload = { vm.downloadRecording(rec) },
                        onStop = { scope.launch { vm.stopLiveRecording(rec.id) } },
                        onDelete = { vm.deleteRecording(rec.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingRowCard(
    rec: RecordingDto,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val isLiveRec = rec.status == "recording"
    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val startedLabel = remember(rec.startedAtMs) { dateFmt.format(Date(rec.startedAtMs)) }
    val durSec = (rec.recordedMs / 1000L).coerceAtLeast(0L)
    val durLabel = String.format(Locale.US, "%d:%02d", durSec / 60L, durSec % 60L)
    val sizeMb = rec.bytes / (1024.0 * 1024.0)
    val remHours = (rec.remainingMs / 3_600_000L).coerceAtLeast(0L)
    val ttlLabel = if (remHours >= 24L) {
        val d = remHours / 24L
        strings.t("Scade tra $d giorni", "Expires in $d days", "Caduca en $d días")
    } else {
        strings.t("Scade tra $remHours ore", "Expires in $remHours hours", "Caduca en $remHours horas")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CasaSurface)
            .border(1.dp, if (isLiveRec) CasaRed else CasaBorder, RoundedCornerShape(6.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CasaSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (isLiveRec) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(CasaRed))
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = CasaText)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = rec.channelName.ifBlank { "Live TV" },
                    color = CasaText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isLiveRec) {
                    Text(
                        text = "REC",
                        color = CasaRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Text(
                text = String.format(Locale.US, "%s · %s · %.1f MB · %s", startedLabel, durLabel, sizeMb, ttlLabel),
                color = CasaMuted,
                fontSize = 11.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvButton(
                text = strings.t("Riproduci", "Play", "Ver"),
                icon = Icons.Filled.PlayArrow,
                primary = true,
                onClick = onPlay,
            )
            TvButton(
                text = strings.t("Scarica", "Download", "Descargar"),
                icon = Icons.Filled.KeyboardArrowDown,
                primary = false,
                onClick = onDownload,
            )
            if (isLiveRec) {
                TvButton(
                    text = strings.t("Ferma", "Stop", "Parar"),
                    icon = Icons.Filled.Close,
                    primary = false,
                    onClick = onStop,
                )
            }
            TvButton(
                text = strings.t("Elimina", "Delete", "Borrar"),
                icon = Icons.Filled.Delete,
                primary = false,
                onClick = onDelete,
            )
        }
    }
}
