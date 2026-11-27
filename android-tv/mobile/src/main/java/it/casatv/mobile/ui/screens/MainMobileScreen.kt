package it.casatv.mobile.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import it.casatv.mobile.data.ConnectionMode
import it.casatv.mobile.data.CountryFacet
import it.casatv.mobile.data.LocalPlaylist
import it.casatv.mobile.data.MobileChannel
import it.casatv.mobile.data.MobileRecordingDto
import it.casatv.mobile.data.MobileStreamSource
import it.casatv.mobile.ui.LocalMobileStrings
import it.casatv.mobile.ui.MixRegion
import it.casatv.mobile.ui.MixRegionPickerDialog
import it.casatv.mobile.ui.MobileUiState
import it.casatv.mobile.ui.components.CountryFlagIcon
import it.casatv.mobile.ui.components.resolveMobileFlagAssetCode
import it.casatv.mobile.ui.components.resolveMobileFlagAssetCode
import it.casatv.mobile.update.UpdateFrequency
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class MobileLogoContrast(val isDark: Boolean, val isVeryLight: Boolean)

private object MobileLogoContrastCache {
    private val cache = ConcurrentHashMap<String, MobileLogoContrast>()
    fun peek(url: String?): MobileLogoContrast? = url?.let { cache[it] }

    suspend fun analyze(url: String, drawable: Drawable): MobileLogoContrast {
        cache[url]?.let { return it }
        val res = withContext(Dispatchers.Default) {
            runCatching {
                val sz = 24
                val bmpOut = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = AndroidCanvas(bmpOut)
                    if (drawable is BitmapDrawable) {
                        val b = drawable.bitmap
                        val safe = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
                        if (safe != null) {
                            canvas.drawBitmap(safe, android.graphics.Rect(0, 0, safe.width, safe.height), android.graphics.Rect(0, 0, sz, sz), null)
                            if (safe !== b) safe.recycle()
                        }
                    } else {
                        drawable.setBounds(0, 0, sz, sz)
                        drawable.draw(canvas)
                    }
                    val px = IntArray(sz * sz)
                    bmpOut.getPixels(px, 0, sz, 0, 0, sz, sz)
                    var n = 0; var sum = 0.0; var dark = 0; var bright = 0
                    for (p in px) {
                        val a = (p ushr 24) and 0xFF
                        if (a >= 64) {
                            val lum = 0.299 * ((p ushr 16) and 0xFF) + 0.587 * ((p ushr 8) and 0xFF) + 0.114 * (p and 0xFF)
                            n++; sum += lum
                            if (lum < 85.0) dark++
                            if (lum > 205.0) bright++
                        }
                    }
                    if (n < 10) MobileLogoContrast(false, false) else {
                        val mean = (sum / n).toFloat()
                        MobileLogoContrast(
                            mean < 102f || (dark.toFloat() / n >= 0.62f && mean < 125f),
                            mean > 205f || (bright.toFloat() / n >= 0.72f && mean > 185f),
                        )
                    }
                } finally { bmpOut.recycle() }
            }.getOrDefault(MobileLogoContrast(false, false))
        }
        cache[url] = res
        return res
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMobileScreen(
    state: MobileUiState,
    onSearchChange: (String) -> Unit,
    onSelectCountry: (String?) -> Unit,
    onOpenChannel: (MobileChannel) -> Unit,
    onOpenEpisodeDirectly: (MobileChannel) -> Unit = onOpenChannel,
    onFetchChannelSources: suspend (MobileChannel) -> List<MobileStreamSource> = { emptyList() },
    onOpenChannelWithSource: (MobileChannel, List<MobileStreamSource>, Int) -> Unit = { ch, _, _ -> onOpenChannel(ch) },
    onDismissSeriesDetail: () -> Unit = {},
    onToggleFavorite: (MobileChannel) -> Unit,
    onSetVodMode: (Boolean) -> Unit = {},
    onSetVodType: (String) -> Unit = {},
    onSetVodCategory: (String?) -> Unit = {},
    onSetVodGenre: (String?) -> Unit = {},
    onSetVodPlatform: (String?) -> Unit = {},
    onSetVodLanguage: (String?) -> Unit = {},
    onRemoveVodContinue: (String) -> Unit = {},
    onLoadMoreVod: () -> Unit = {},
    onOpenMixPicker: () -> Unit,
    onToggleMixRegion: (MixRegion) -> Unit,
    onToggleMixLanguage: (String) -> Unit,
    onToggleMixGenre: (String) -> Unit,
    onClearMixFilters: () -> Unit,
    onStartMixFromRegions: () -> Unit,
    onDismissMixPicker: () -> Unit,
    onImportUrl: (name: String, url: String) -> Unit,
    onImportFile: (Uri) -> Unit,
    onUploadFileToServer: (Uri) -> Unit,
    onCheckPlaylist: (LocalPlaylist) -> Unit,
    onTogglePlaylist: (LocalPlaylist, Boolean) -> Unit,
    onRefreshPlaylist: (LocalPlaylist) -> Unit,
    onDeletePlaylist: (LocalPlaylist) -> Unit,
    onDismissMessage: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAutoServer: (Boolean) -> Unit,
    onSetAutoSaveServerM3u: (Boolean) -> Unit,
    onSetBackgroundPlay: (Boolean) -> Unit,
    onSetPipOnLeave: (Boolean) -> Unit,
    onSetLiveSafetyBuffer: (Boolean) -> Unit,
    onSetMixIncludeAudioOnly: (Boolean) -> Unit,
    onSearchServerNow: () -> Unit,
    onSetUpdateFrequency: (UpdateFrequency) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onLoadRecordings: () -> Unit = {},
    onPlayRecording: (MobileRecordingDto) -> Unit = {},
    onDownloadRecording: (MobileRecordingDto) -> Unit = {},
    onStopRecording: suspend (String) -> Any? = { null },
    onDeleteRecording: (String) -> Unit = {},
) {
    val strings = LocalMobileStrings.current
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showRecordingsDialog by remember { mutableStateOf(false) }
    var contextMenuChannel by remember { mutableStateOf<MobileChannel?>(null) }

    val selectedCountryFacet = remember(state.selectedCountry, state.countries) {
        state.countries.firstOrNull { it.code == state.selectedCountry }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CasaTV",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        ConnectionBadge(
                            mode = state.connectionMode,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                },
                actions = {
                    if (state.connectionMode != ConnectionMode.LOCAL_ONLY) {
                        IconButton(
                            onClick = {
                                showRecordingsDialog = true
                                onLoadRecordings()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = if (strings.lang == "it") "Registrazioni" else "Recordings",
                            )
                        }
                    }
                    if (!(selectedTab == 0 && state.vodMode)) {
                        Surface(
                            onClick = { showCountryPicker = true },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CountryFlagIcon(
                                    countryCode = selectedCountryFacet?.code,
                                    width = 20.dp,
                                    height = 14.dp,
                                    cornerRadius = 2.dp,
                                    fallbackTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = selectedCountryFacet?.code ?: strings.allCountriesShort,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                    label = {
                        Text(
                            strings.tabChannels,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp,
                        )
                    },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenMixPicker,
                    icon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
                    label = {
                        Text(
                            strings.tabMix,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp,
                        )
                    },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = {
                        Text(
                            strings.tabFavorites,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp,
                        )
                    },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                    label = {
                        Text(
                            strings.tabPlaylists,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp,
                        )
                    },
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = {
                        Text(
                            strings.tabSettings,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp,
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                1 -> FavoritesTab(
                    favorites = state.favoriteChannels,
                    onOpenChannel = onOpenChannel,
                    onToggleFavorite = onToggleFavorite,
                    onLongPressChannel = { contextMenuChannel = it },
                )
                2 -> PlaylistsScreen(
                    state = state,
                    onImportUrl = onImportUrl,
                    onImportFile = onImportFile,
                    onUploadFileToServer = onUploadFileToServer,
                    onCheckPlaylist = onCheckPlaylist,
                    onTogglePlaylist = onTogglePlaylist,
                    onRefreshPlaylist = onRefreshPlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                    onDismissMessage = onDismissMessage,
                )
                3 -> SettingsScreen(
                    state = state,
                    onSetLanguage = onSetLanguage,
                    onSetDarkTheme = onSetDarkTheme,
                    onSetAutoServer = onSetAutoServer,
                    onSetAutoSaveServerM3u = onSetAutoSaveServerM3u,
                    onSetBackgroundPlay = onSetBackgroundPlay,
                    onSetPipOnLeave = onSetPipOnLeave,
                    onSetLiveSafetyBuffer = onSetLiveSafetyBuffer,
                    onSetMixIncludeAudioOnly = onSetMixIncludeAudioOnly,
                    onSearchServerNow = onSearchServerNow,
                    onSetUpdateFrequency = onSetUpdateFrequency,
                    onCheckUpdatesNow = onCheckUpdatesNow,
                    onInstallUpdate = onInstallUpdate,
                )
                else -> ChannelsTab(
                    state = state,
                    onSearchChange = onSearchChange,
                    onSelectCountry = onSelectCountry,
                    onOpenChannel = onOpenChannel,
                    onOpenEpisodeDirectly = onOpenEpisodeDirectly,
                    onLongPressChannel = { contextMenuChannel = it },
                    onToggleFavorite = onToggleFavorite,
                    onSetVodMode = onSetVodMode,
                    onSetVodType = onSetVodType,
                    onSetVodCategory = onSetVodCategory,
                    onSetVodGenre = onSetVodGenre,
                    onSetVodPlatform = onSetVodPlatform,
                    onSetVodLanguage = onSetVodLanguage,
                    onRemoveVodContinue = onRemoveVodContinue,
                    onLoadMoreVod = onLoadMoreVod,
                    onGoToPlaylists = { selectedTab = 2 },
                )
            }
        }
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            countries = state.countries,
            totalChannels = state.allChannels.size,
            selectedCountry = state.selectedCountry,
            onSelectCountry = { code ->
                onSelectCountry(code)
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false },
        )
    }

    state.activeSeriesDetail?.let { detail ->
        val seasonsMap = state.activeSeriesSeasons
        val seasonNumbers = remember(detail, seasonsMap) { seasonsMap.keys.sorted() }
        val initialSeason = remember(detail, seasonsMap) {
            seasonsMap.entries.firstOrNull { (_, eps) ->
                eps.any { it.id.removePrefix("srv_") == detail.resumeEpisodeId }
            }?.key ?: seasonNumbers.firstOrNull() ?: 1
        }
        var selectedSeason by remember(detail) { mutableIntStateOf(initialSeason) }
        var seasonDropdownOpen by remember { mutableStateOf(false) }
        val episodes = seasonsMap[selectedSeason] ?: seasonsMap.values.firstOrNull().orEmpty()
        val resumeEp = remember(detail, seasonsMap) {
            seasonsMap.values.flatten().firstOrNull { it.id.removePrefix("srv_") == detail.resumeEpisodeId }
                ?: seasonsMap.values.flatten().firstOrNull()
        }
        Dialog(
            onDismissRequest = onDismissSeriesDetail,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = detail.showTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${seasonNumbers.size} ${if (seasonNumbers.size == 1) "Stagione" else "Stagioni"} · ${detail.totalEpisodes} episodi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (seasonNumbers.size > 1) {
                            Box {
                                OutlinedButton(onClick = { seasonDropdownOpen = true }) {
                                    Text("Stagione $selectedSeason")
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                DropdownMenu(expanded = seasonDropdownOpen, onDismissRequest = { seasonDropdownOpen = false }) {
                                    seasonNumbers.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text("Stagione $s") },
                                            onClick = {
                                                selectedSeason = s
                                                seasonDropdownOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onDismissSeriesDetail) {
                            Icon(Icons.Default.Close, contentDescription = "Chiudi")
                        }
                    }
                    if (resumeEp != null) {
                        val code = "S${(resumeEp.seasonNumber ?: 1).toString().padStart(2, '0')}E${(resumeEp.episodeNumber ?: 1).toString().padStart(2, '0')}"
                        Button(
                            onClick = {
                                onDismissSeriesDetail()
                                onOpenEpisodeDirectly(resumeEp)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (resumeEp.positionMs > 15_000L) "Riprendi $code" else if (resumeEp.upNext) "Prossimo $code" else "Riproduci $code")
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(episodes, key = { "msep_${it.id}" }) { ep ->
                            val isRes = ep.id.removePrefix("srv_") == detail.resumeEpisodeId
                            val epNum = ep.episodeNumber ?: 1
                            val title = ep.episodeTitle?.takeIf { it.isNotBlank() } ?: "Episodio $epNum"
                            Card(
                                onClick = {
                                    onDismissSeriesDetail()
                                    onOpenEpisodeDirectly(ep)
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                modifier = if (isRes) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                } else {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "$epNum",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val sub = if (ep.upNext) "Prossimo episodio" else if (ep.positionMs > 15_000L) "In corso" else ep.name
                                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRecordingsDialog) {
        RecordingsFullscreenDialog(
            recordings = state.recordings,
            loading = state.recordingsLoading,
            lang = strings.lang,
            onPlay = { rec ->
                showRecordingsDialog = false
                onPlayRecording(rec)
            },
            onDownload = onDownloadRecording,
            onStop = { recId ->
                scope.launch {
                    onStopRecording(recId)
                    onLoadRecordings()
                }
            },
            onDelete = onDeleteRecording,
            onRefresh = onLoadRecordings,
            onDismiss = { showRecordingsDialog = false },
        )
    }

    state.availableUpdate?.let { upd ->
        AlertDialog(
            onDismissRequest = onDismissUpdate,
            title = { Text(strings.get("update_dialog_title", upd.version)) },
            text = {
                Column {
                    Text(upd.notes)
                    if (state.updateProgress != null) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.updateProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onInstallUpdate,
                    enabled = state.updateProgress == null,
                ) {
                    Text(if (state.updateProgress != null) strings.downloading else strings.updateNow)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUpdate) {
                    Text(strings.later)
                }
            },
        )
    }

    if (state.showMixRegionPicker) {
        val stats = state.mixPickerStats
        val totalWorldCount = remember(state.allChannels.size, stats.regionCounts) {
            val sumRegions = stats.regionCounts.values.sum()
            if (sumRegions > 0) sumRegions else state.allChannels.size
        }
        MixRegionPickerDialog(
            language = state.language,
            selectedRegions = state.selectedMixRegions,
            regionCounts = stats.regionCounts,
            totalChannels = totalWorldCount,
            onToggleRegion = onToggleMixRegion,
            onStartMix = onStartMixFromRegions,
            onDismiss = onDismissMixPicker,
            selectedLanguages = state.selectedMixLanguages,
            selectedGenres = state.selectedMixGenres,
            matchingChannelCount = stats.matchingCount,
            onToggleLanguage = onToggleMixLanguage,
            onToggleGenre = onToggleMixGenre,
            onClearFilters = onClearMixFilters,
        )
    }

    contextMenuChannel?.let { targetCh ->
        var menuSources by remember(targetCh.id) { mutableStateOf<List<MobileStreamSource>>(emptyList()) }
        var loadingSources by remember(targetCh.id) {
            mutableStateOf(targetCh.isFromServer && !targetCh.isVodContent)
        }
        var showSourcesList by remember(targetCh.id) { mutableStateOf(false) }
        var showInfoBox by remember(targetCh.id) { mutableStateOf(false) }

        LaunchedEffect(targetCh.id) {
            if (targetCh.isFromServer && !targetCh.isVodContent) {
                loadingSources = true
                menuSources = onFetchChannelSources(targetCh)
                loadingSources = false
            }
        }

        ModalBottomSheet(
            onDismissRequest = { contextMenuChannel = null },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (resolveMobileFlagAssetCode(targetCh.country) != null) {
                        CountryFlagIcon(
                            countryCode = targetCh.country,
                            width = 24.dp,
                            height = 17.dp,
                            cornerRadius = 3.dp,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = targetCh.seriesTitle?.takeIf { it.isNotBlank() } ?: targetCh.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(
                                targetCh.countryName.takeIf { it.isNotBlank() },
                                targetCh.subtitle.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (showSourcesList) {
                    Text(
                        text = if (strings.lang == "it") "Scegli sorgente" else "Choose source",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (loadingSources) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(if (strings.lang == "it") "Carico sorgenti…" else "Loading sources…")
                        }
                    } else if (menuSources.isEmpty()) {
                        Text(
                            text = if (strings.lang == "it") "Una sola sorgente disponibile." else "Only one source available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(menuSources.indices.toList()) { idx ->
                                val src = menuSources[idx]
                                val label = src.quality.ifBlank { src.label }.ifBlank { "Sorgente ${idx + 1}" }
                                val sub = listOfNotNull(
                                    src.protocol.takeIf { it.isNotBlank() },
                                    src.host.takeIf { it.isNotBlank() },
                                    src.latencyMs?.let { "${it} ms" },
                                ).joinToString(" · ")
                                Card(
                                    onClick = {
                                        contextMenuChannel = null
                                        onOpenChannelWithSource(targetCh, menuSources, idx)
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(label, fontWeight = FontWeight.SemiBold)
                                            if (sub.isNotBlank()) {
                                                Text(
                                                    sub,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        if (src.recommended) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { showSourcesList = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (strings.lang == "it") "Indietro" else "Back")
                    }
                } else if (showInfoBox) {
                    Text(
                        text = targetCh.streamUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { showInfoBox = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (strings.lang == "it") "Indietro" else "Back")
                    }
                } else {
                    Button(
                        onClick = {
                            contextMenuChannel = null
                            onOpenChannel(targetCh)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (strings.lang == "it") "Riproduci" else "Play")
                    }
                    OutlinedButton(
                        onClick = {
                            onToggleFavorite(targetCh)
                            contextMenuChannel = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (targetCh.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (targetCh.favorite) {
                                if (strings.lang == "it") "Rimuovi dai preferiti" else "Remove from favorites"
                            } else {
                                if (strings.lang == "it") "Aggiungi ai preferiti" else "Add to favorites"
                            },
                        )
                    }
                    if (!targetCh.isVodContent && targetCh.isFromServer) {
                        OutlinedButton(
                            onClick = { showSourcesList = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            val srcCountSuffix = if (menuSources.size > 1) " (${menuSources.size})" else ""
                            Text((if (strings.lang == "it") "Scegli sorgente" else "Choose source") + srcCountSuffix)
                        }
                    }
                    if (targetCh.id in state.vodContinue.map { it.id }) {
                        OutlinedButton(
                            onClick = {
                                onRemoveVodContinue(targetCh.id)
                                contextMenuChannel = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (strings.lang == "it") "Rimuovi da Continua a guardare" else "Remove from Continue Watching")
                        }
                    }
                    OutlinedButton(
                        onClick = { showInfoBox = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (strings.lang == "it") "Dettagli" else "Details")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerDialog(
    countries: List<CountryFacet>,
    totalChannels: Int,
    selectedCountry: String?,
    onSelectCountry: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalMobileStrings.current
    var query by remember { mutableStateOf("") }
    val filteredCountries = remember(query, countries) {
        val q = query.trim().lowercase()
        val sorted = countries.sortedBy { it.name.lowercase() }
        if (q.isEmpty()) sorted
        else sorted.filter {
            it.name.lowercase().contains(q) || it.code.lowercase().contains(q)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(strings.selectCountry, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(strings.searchCountry) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (query.isBlank()) {
                        item {
                            CountryOptionCard(
                                countryCode = null,
                                name = strings.allCountries,
                                count = totalChannels,
                                selected = selectedCountry == null,
                                onClick = { onSelectCountry(null) },
                            )
                        }
                    }
                    items(filteredCountries, key = { it.code }) { facet ->
                        CountryOptionCard(
                            countryCode = facet.code,
                            name = facet.name,
                            count = facet.count,
                            selected = selectedCountry == facet.code,
                            onClick = { onSelectCountry(facet.code) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryOptionCard(
    countryCode: String?,
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountryFlagIcon(
                countryCode = countryCode,
                width = 26.dp,
                height = 18.dp,
                cornerRadius = 3.dp,
                fallbackTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectionBadge(
    mode: ConnectionMode,
    modifier: Modifier = Modifier,
) {
    val strings = LocalMobileStrings.current
    val (label, bg, fg) = when (mode) {
        ConnectionMode.SERVER_CONNECTED -> Triple(
            strings.modeServerWifi,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ConnectionMode.SEARCHING_WIFI -> Triple(
            strings.modeSearchingWifi,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConnectionMode.LOCAL_ONLY -> Triple(
            strings.modeAutonomous,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ChannelsTab(
    state: MobileUiState,
    onSearchChange: (String) -> Unit,
    onSelectCountry: (String?) -> Unit,
    onOpenChannel: (MobileChannel) -> Unit,
    onOpenEpisodeDirectly: (MobileChannel) -> Unit = onOpenChannel,
    onLongPressChannel: (MobileChannel) -> Unit = {},
    onToggleFavorite: (MobileChannel) -> Unit,
    onSetVodMode: (Boolean) -> Unit,
    onSetVodType: (String) -> Unit,
    onSetVodCategory: (String?) -> Unit,
    onSetVodGenre: (String?) -> Unit,
    onSetVodPlatform: (String?) -> Unit,
    onSetVodLanguage: (String?) -> Unit,
    onRemoveVodContinue: (String) -> Unit,
    onLoadMoreVod: () -> Unit,
    onGoToPlaylists: () -> Unit,
) {
    val strings = LocalMobileStrings.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !state.vodMode,
                onClick = { onSetVodMode(false) },
                label = { Text(strings.tabChannels) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = state.vodMode,
                onClick = { onSetVodMode(true) },
                label = {
                    val vodLbl = when (strings.lang.lowercase()) {
                        "it" -> "Film & Serie"
                        "es" -> "Cine y Series"
                        else -> "Movies & Series"
                    }
                    Text(vodLbl)
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (state.vodMode && state.searchQuery.isBlank()) {
            var languageMenuExpanded by remember { mutableStateOf(false) }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                val opts = listOf(
                    "vod" to (if (strings.lang == "it") "Tutti" else "All"),
                    "movie" to (if (strings.lang == "it") "Film" else "Movies"),
                    "series" to (if (strings.lang == "it") "Serie TV" else "Series"),
                )
                items(opts) { (k, lbl) ->
                    FilterChip(
                        selected = state.vodType == k,
                        onClick = { onSetVodType(k) },
                        label = { Text(lbl) },
                    )
                }
                if (state.vodLanguages.isNotEmpty()) {
                    item {
                        Box {
                            val activeLang = state.vodLanguages.firstOrNull { it.code == state.vodLanguage }
                            FilterChip(
                                selected = state.vodLanguage != null,
                                onClick = { languageMenuExpanded = true },
                                leadingIcon = {
                                    CountryFlagIcon(
                                        countryCode = activeLang?.flag,
                                        width = 16.dp,
                                        height = 11.dp,
                                        cornerRadius = 2.dp,
                                        fallbackTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingIcon = {
                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = {
                                    Text(
                                        activeLang?.name
                                            ?: if (strings.lang == "it") "Lingua" else "Language",
                                    )
                                },
                            )
                            DropdownMenu(
                                expanded = languageMenuExpanded,
                                onDismissRequest = { languageMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    text = { Text(if (strings.lang == "it") "Tutte le lingue" else "All languages") },
                                    onClick = {
                                        languageMenuExpanded = false
                                        onSetVodLanguage(null)
                                    },
                                )
                                state.vodLanguages.forEach { langItem ->
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            CountryFlagIcon(
                                                countryCode = langItem.flag,
                                                width = 18.dp,
                                                height = 12.dp,
                                                cornerRadius = 2.dp,
                                            )
                                        },
                                        text = { Text("${langItem.name} (${langItem.count})") },
                                        onClick = {
                                            languageMenuExpanded = false
                                            onSetVodLanguage(langItem.code)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.vodGenres.isNotEmpty()) {
                    item {
                        FilterChip(
                            selected = state.vodGenre == null,
                            onClick = { onSetVodGenre(null) },
                            label = { Text(if (strings.lang == "it") "Tutti i generi" else "All genres") },
                        )
                    }
                    items(state.vodGenres.take(20), key = { it.id }) { gen ->
                        FilterChip(
                            selected = state.vodGenre == gen.id,
                            onClick = { onSetVodGenre(if (state.vodGenre == gen.id) null else gen.id) },
                            label = { Text(gen.name) },
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.vodContinue.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (strings.lang == "it") "Continua a guardare" else "Continue Watching",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                        ) {
                            items(state.vodContinue, key = { "mcont_${it.id}" }) { ch ->
                                ChannelTileCard(
                                    channel = ch,
                                    isVod = true,
                                    onClick = { onOpenEpisodeDirectly(ch) },
                                    onLongClick = { onLongPressChannel(ch) },
                                    onToggleFavorite = { onToggleFavorite(ch) },
                                    modifier = Modifier.width(114.dp),
                                )
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = if (strings.lang == "it") "Catalogo (${state.vodTotal})" else "Catalog (${state.vodTotal})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                }
                gridItems(state.vodItems, key = { "mvod_${it.id}" }) { ch ->
                    ChannelTileCard(
                        channel = if (ch.contentType == "series" && !ch.seriesTitle.isNullOrBlank()) ch.copy(name = ch.seriesTitle) else ch,
                        isVod = true,
                        onClick = { onOpenChannel(ch) },
                        onLongClick = { onLongPressChannel(ch) },
                        onToggleFavorite = { onToggleFavorite(ch) },
                    )
                }
                if (state.vodItems.size < state.vodTotal) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Button(
                            onClick = onLoadMoreVod,
                            enabled = !state.vodLoading,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Text(if (strings.lang == "it") "Carica altri titoli (${state.vodItems.size}/${state.vodTotal})" else "Load more (${state.vodItems.size}/${state.vodTotal})")
                        }
                    }
                }
            }
            return@Column
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(strings.searchChannel) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val activeCountries = remember(state.countries) {
            state.countries.filter { it.count > 0 }
        }
        if (activeCountries.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCountry == null,
                        onClick = { onSelectCountry(null) },
                        label = { Text(strings.get("all_filter", state.allChannels.size)) },
                    )
                }
                items(activeCountries, key = { it.code }) { facet ->
                    FilterChip(
                        selected = state.selectedCountry == facet.code,
                        onClick = {
                            onSelectCountry(if (state.selectedCountry == facet.code) null else facet.code)
                        },
                        leadingIcon = {
                            CountryFlagIcon(
                                countryCode = facet.code,
                                width = 16.dp,
                                height = 11.dp,
                                cornerRadius = 2.dp,
                            )
                        },
                        label = { Text("${facet.name} (${facet.count})") },
                    )
                }
            }
        }

        val channels = state.filteredChannels
        if (channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (state.allChannels.isEmpty()) {
                            strings.noChannels
                        } else {
                            strings.noSearchMatch
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.allChannels.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = strings.emptyChannelsHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onGoToPlaylists) {
                            Text(strings.loadM3uButton)
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(channels, key = { it.id }) { channel ->
                    ChannelTileCard(
                        channel = channel,
                        isVod = channel.isVodContent,
                        onClick = { onOpenChannel(channel) },
                        onLongClick = { onLongPressChannel(channel) },
                        onToggleFavorite = { onToggleFavorite(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    favorites: List<MobileChannel>,
    onOpenChannel: (MobileChannel) -> Unit,
    onToggleFavorite: (MobileChannel) -> Unit,
    onLongPressChannel: (MobileChannel) -> Unit = {},
) {
    val strings = LocalMobileStrings.current
    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = strings.noFavorites,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.noFavoritesHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(favorites, key = { it.id }) { channel ->
                ChannelTileCard(
                    channel = channel,
                    isVod = channel.isVodContent,
                    onClick = { onOpenChannel(channel) },
                    onLongClick = { onLongPressChannel(channel) },
                    onToggleFavorite = { onToggleFavorite(channel) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelTileCard(
    channel: MobileChannel,
    isVod: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = if (isVod) RoundedCornerShape(8.dp) else RoundedCornerShape(10.dp)
    val displayTitle = (if (channel.contentType == "series" && !channel.seriesTitle.isNullOrBlank()) {
        channel.seriesTitle
    } else {
        channel.name
    }).replace(Regex("""^\s*(?:\([^)]{1,10}\)|\[[^\]]{1,10}\])\s*"""), "").trim().ifBlank { channel.name }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkSurface = MaterialTheme.colorScheme.background.red < 0.25f
    var logoFailed by remember(channel.logo, channel.id) { mutableStateOf(false) }
    var logoLoaded by remember(channel.logo, channel.id) { mutableStateOf(false) }
    var isVodLandscapeStill by remember(channel.logo, channel.id) { mutableStateOf(false) }
    var contrast by remember(channel.logo, channel.id) {
        mutableStateOf(if (!isVod) MobileLogoContrastCache.peek(channel.logo) else null)
    }

    val tileBg = if (isVod) {
        if (isDarkSurface) Color(0xFF151519) else Color(0xFFE9E9EE)
    } else {
        when {
            isDarkSurface && contrast?.isDark == true -> Color(0xFFEDEDED)
            !isDarkSurface && contrast?.isVeryLight == true -> Color(0xFF18181B)
            isDarkSurface -> Color(0xFF161618)
            else -> Color(0xFFF2F2F5)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (isVod) 2f / 3f else 1f)
            .clip(shape)
            .background(tileBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val logoUrl = channel.logo
        if (logoUrl.isNullOrBlank() || logoFailed || !logoLoaded) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkSurface) Color(0xFFF2F2F5) else Color(0xFF111114),
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (!logoUrl.isNullOrBlank() && !logoFailed) {
            val req = remember(logoUrl, isVod) {
                ImageRequest.Builder(context)
                    .data(logoUrl)
                    .allowHardware(isVod)
                    .precision(Precision.INEXACT)
                    .apply { if (isVod) size(300, 450) else size(220, 220) }
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = displayTitle,
                contentScale = if (isVod) ContentScale.Crop else ContentScale.Fit,
                modifier = if (isVod) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(12.dp),
                onSuccess = { state ->
                    logoLoaded = true
                    if (!isVod) {
                        val peeked = MobileLogoContrastCache.peek(logoUrl)
                        if (peeked != null) contrast = peeked
                        else scope.launch { contrast = MobileLogoContrastCache.analyze(logoUrl, state.result.drawable) }
                    } else {
                        val d = state.result.drawable
                        if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0 && d.intrinsicWidth > d.intrinsicHeight * 1.08f) {
                            isVodLandscapeStill = true
                        }
                    }
                },
                onError = { logoFailed = true },
            )
            if (isVod && isVodLandscapeStill) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000), Color(0xF0000000)),
                            ),
                        )
                        .padding(start = 7.dp, end = if (channel.contentType == "series") 26.dp else 7.dp, top = 18.dp, bottom = 5.dp),
                ) {
                    Text(
                        text = displayTitle,
                        color = Color.White,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        val vodFlags = remember(channel.languageFlags, channel.country, isVod) {
            if (!isVod) {
                emptyList()
            } else {
                val explicit = channel.languageFlags.mapNotNull(::resolveMobileFlagAssetCode).distinct().take(5)
                if (explicit.isNotEmpty()) explicit else listOfNotNull(resolveMobileFlagAssetCode(channel.country))
            }
        }
        if (isVod && (vodFlags.isNotEmpty() || channel.contentType == "series")) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vodFlags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        vodFlags.forEach { cc ->
                            CountryFlagIcon(
                                countryCode = cc,
                                width = 10.dp,
                                height = 7.dp,
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
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "TV",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }

        if (channel.favorite) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Preferito",
                    tint = Color(0xFFFF5C5C),
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        if (isVod && channel.durationMs > 0L && channel.positionMs > 15_000L) {
            val frac = (channel.positionMs.toFloat() / channel.durationMs.toFloat()).coerceIn(0.04f, 0.98f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0x40FFFFFF)),
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
}

@Composable
private fun RecordingsFullscreenDialog(
    recordings: List<MobileRecordingDto>,
    loading: Boolean,
    lang: String,
    onPlay: (MobileRecordingDto) -> Unit,
    onDownload: (MobileRecordingDto) -> Unit,
    onStop: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == "it") "Registrazioni (7 giorni)" else "Recordings (7 days)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (lang == "it") "Tieni premuto REC 3 secondi su un canale live" else "Hold REC for 3s on any live channel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onRefresh) {
                        Text(if (lang == "it") "Aggiorna" else "Refresh")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Chiudi")
                    }
                }
                if (loading && recordings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (recordings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (lang == "it") "Nessuna registrazione salvata." else "No saved recordings.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(recordings, key = { it.id }) { rec ->
                            val isActive = rec.status == "recording" || rec.status == "starting"
                            val daysLeft = ((rec.expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L) / 86_400_000L).toInt()
                            val sizeMb = rec.bytes / (1024.0 * 1024.0)
                            val durMin = (rec.recordedMs / 60_000L).toInt()
                            Card(
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (isActive) {
                                            Icon(
                                                imageVector = Icons.Default.FiberManualRecord,
                                                contentDescription = null,
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(12.dp),
                                            )
                                        }
                                        Text(
                                            text = rec.channelName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (isActive) "LIVE REC" else "${daysLeft}g rimasti",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = "${durMin}m · ${String.format(java.util.Locale.US, "%.1f", sizeMb)} MB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onPlay(rec) }) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (lang == "it") "Guarda" else "Watch")
                                        }
                                        OutlinedButton(onClick = { onDownload(rec) }) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (lang == "it") "Scarica" else "Download")
                                        }
                                        if (isActive) {
                                            OutlinedButton(onClick = { onStop(rec.id) }) {
                                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        IconButton(onClick = { onDelete(rec.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Elimina")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
