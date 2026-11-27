package it.casatv.tv.ui.screens

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import it.casatv.tv.CasaTvApplication
import it.casatv.tv.R
import it.casatv.tv.data.Channel
import it.casatv.tv.data.DiagnosticReporter
import it.casatv.tv.data.Facet
import it.casatv.tv.data.RecordingDto
import it.casatv.tv.data.StreamSource
import it.casatv.tv.data.SyncStateResponse
import it.casatv.tv.data.TimeshiftSessionDto
import it.casatv.tv.data.TvDiagnosticCodes
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.TvMixRegionPickerHost
import it.casatv.tv.ui.buildCountryCatalog
import it.casatv.tv.ui.components.CasaIcons
import it.casatv.tv.ui.components.ChannelLivePreviewBox
import it.casatv.tv.ui.components.ChannelLogo
import it.casatv.tv.ui.components.CountryFlagIcon
import it.casatv.tv.ui.components.OsdAudioTrack
import it.casatv.tv.ui.components.OsdSubtitleTrack
import it.casatv.tv.ui.components.PlayerOsdMenu
import it.casatv.tv.ui.components.applyAudioTrack
import it.casatv.tv.ui.components.applySubtitleTrack
import it.casatv.tv.ui.components.disableSubtitles
import it.casatv.tv.ui.components.extractOsdAudioTracks
import it.casatv.tv.ui.components.extractOsdSubtitleTracks
import it.casatv.tv.ui.components.formatStreamInfo
import it.casatv.tv.ui.components.resolveFlagAssetCode
import it.casatv.tv.ui.countryFacet
import it.casatv.tv.ui.countryName
import it.casatv.tv.ui.countryTimeTextOrNull
import it.casatv.tv.ui.currentTimeText
import it.casatv.tv.ui.formatTime
import it.casatv.tv.ui.isLikelyAudioOnlyChannel
import it.casatv.tv.ui.normalizeCountryCode
import it.casatv.tv.ui.programProgress
import it.casatv.tv.ui.programRemaining
import it.casatv.tv.ui.theme.CasaAmber
import it.casatv.tv.ui.theme.CasaBlue
import it.casatv.tv.ui.theme.CasaBorder
import it.casatv.tv.ui.theme.CasaDim
import it.casatv.tv.ui.theme.CasaGreen
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaSurface
import it.casatv.tv.ui.theme.CasaText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class TimeshiftStage {
    LIVE,
    STARTING,
    PAUSED_RECORDING,
    PLAYING_TIMESHIFT,
    PAUSED_LIMIT_REACHED,
    PLAYING_LIMIT_REACHED,
}

private class TimeshiftCleanupHolder(
    var session: TimeshiftSessionDto? = null,
    var watchedAfterLimitPrompt: Boolean = false,
    var relayChannelId: String? = null,
)

private class PlayerDiagState {
    @Volatile var lastVideoBitrateKbps: Int = 0
    @Volatile var lastVideoCodec: String = ""
    @Volatile var lastAudioCodec: String = ""
    @Volatile var lastBandwidthKbps: Int = 0
    @Volatile var midPlayBufferingSinceMs: Long = 0L
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(vm: MainViewModel) {
    val playback by vm.playback.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val tvCountry by vm.tvCountry.collectAsStateWithLifecycle()
    val aspectMode by vm.aspectRatioMode.collectAsStateWithLifecycle()
    val session = playback ?: return
    val channel = session.channel
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val strings = LocalAppStrings.current

    val countries = remember(config?.facets?.countries, strings.language) {
        buildCountryCatalog(config?.facets?.countries.orEmpty(), strings.language)
    }
    val activeCountryCode = tvCountry ?: profile?.country ?: "IT"
    val activeCountry = countryFacet(activeCountryCode, countries)
    val pickerCountries = countries

    val defaultDataSourceFactory = remember(context) {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(20000)
        androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
    }
    val renderersFactory = remember(context) {
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    }
    val liveSafetyBufferEnabled by vm.liveSafetyBufferEnabled.collectAsStateWithLifecycle()
    val safetyBufferEnabledState by rememberUpdatedState(liveSafetyBufferEnabled)
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    val loadErrorPolicy = remember {
        ResilientLoadErrorHandlingPolicy { safetyBufferEnabledState }
    }
    val mediaSourceFactory = remember(context, defaultDataSourceFactory, loadErrorPolicy) {
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(defaultDataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorPolicy)
    }
    val loadControl = remember(isLowRam) {
        androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (isLowRam) 15000 else 20000,
                if (isLowRam) 30000 else 50000,
                if (isLowRam) 1500 else 2000,
                if (isLowRam) 2000 else 2500,
            )
            .setBackBuffer(10000, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
    val player = remember {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setViewportSizeToPhysicalDisplaySize(context, true)
                    .build()
            }
    }
    var buffering by remember { mutableStateOf(true) }
    var isPlayingState by remember { mutableStateOf(false) }
    var failingOver by remember { mutableStateOf(false) }
    var reconnecting by remember { mutableStateOf(false) }
    var reconnectPending by remember { mutableStateOf(false) }
    var hasPlayedCurrentChannel by remember { mutableStateOf(false) }
    var reconnectAttempt by remember { mutableIntStateOf(0) }
    var bgRetryCycle by remember { mutableIntStateOf(0) }
    val softResyncTimes = remember { kotlin.collections.ArrayDeque<Long>() }
    var reconnectJob by remember { mutableStateOf<Job?>(null) }
    var multiTvFollowerActive by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val currentStreamUrlState by rememberUpdatedState(playableStreamUrl(session.activeStreamUrl))
    val isMixModeState by rememberUpdatedState(session.isMixMode)
    var controlsVisible by remember { mutableStateOf(true) }
    var hideTick by remember { mutableIntStateOf(0) }
    var numberEntry by remember { mutableStateOf("") }
    var tuneJob by remember { mutableStateOf<Job?>(null) }
    var countryPickerOpen by remember { mutableStateOf(false) }
    var countryIndex by remember { mutableIntStateOf(0) }
    var osdMenuOpen by remember { mutableStateOf(false) }
    var osdRow by remember { mutableIntStateOf(0) }
    var osdCol by remember { mutableIntStateOf(0) }
    var tracksState by remember { mutableStateOf(Tracks.EMPTY) }
    var videoSizeState by remember { mutableStateOf(VideoSize.UNKNOWN) }
    val multiTvSyncEnabled by vm.multiTvSync.collectAsStateWithLifecycle()
    val mixIncludeAudioOnly by vm.mixIncludeAudioOnly.collectAsStateWithLifecycle()
    val showMixRegionPicker by vm.showMixRegionPicker.collectAsStateWithLifecycle()
    val serverRelayEnabled by vm.serverRelayEnabled.collectAsStateWithLifecycle()
    var activeRelayKey by remember { mutableStateOf<String?>(null) }
    var activeRelayUrl by remember { mutableStateOf<String?>(null) }
    var activeRelayChannelId by remember { mutableStateOf<String?>(null) }
    val isServerDisconnected by vm.isServerDisconnected.collectAsStateWithLifecycle()
    var autonomousDirectMode by remember { mutableStateOf(false) }
    var firstVideoFrameRendered by remember { mutableStateOf(false) }
    var audioSkipCount by remember { mutableIntStateOf(0) }
    var clockTick by remember { mutableIntStateOf(0) }
    var syncState by remember { mutableStateOf<SyncStateResponse?>(null) }
    var inSyncByDelta by remember { mutableStateOf(false) }
    var lastZapTimeMs by remember { mutableLongStateOf(0L) }
    var quickDrawerOpen by remember { mutableStateOf(false) }
    var quickDrawerIndex by remember { mutableIntStateOf(0) }
    var rightDrawerOpen by remember { mutableStateOf(false) }
    var rightDrawerIndex by remember { mutableIntStateOf(0) }
    var lastDrawerMoveMs by remember { mutableLongStateOf(0L) }

    var timeshiftStage by remember { mutableStateOf(TimeshiftStage.LIVE) }
    var timeshiftSession by remember { mutableStateOf<TimeshiftSessionDto?>(null) }
    var timeshiftLoadedForId by remember { mutableStateOf<String?>(null) }
    var pendingRecording by remember { mutableStateOf<TimeshiftSessionDto?>(null) }
    var pendingPromptIndex by remember { mutableIntStateOf(0) }
    var watchedAfterLimitPrompt by remember { mutableStateOf(false) }
    var pauseOverlayIndex by remember { mutableIntStateOf(0) }
    var timeshiftPosMs by remember { mutableLongStateOf(0L) }
    var timeshiftDurMs by remember { mutableLongStateOf(0L) }
    var timeshiftStartJob by remember { mutableStateOf<Job?>(null) }
    val activeChannelIdState by rememberUpdatedState(channel.id)
    val cleanupHolder = remember { TimeshiftCleanupHolder() }
    cleanupHolder.session = timeshiftSession
    cleanupHolder.watchedAfterLimitPrompt = watchedAfterLimitPrompt
    cleanupHolder.relayChannelId = activeRelayChannelId
    var vodPosMs by remember { mutableLongStateOf(channel.positionMs) }
    var vodDurMs by remember { mutableLongStateOf(channel.durationMs) }
    var vodSeekApplied by remember { mutableStateOf(false) }
    var vodRelatedMode by remember(channel.id) { mutableStateOf("channels") }
    var vodRelatedItems by remember(channel.id) { mutableStateOf<List<Channel>>(emptyList()) }
    var carouselOpen by remember { mutableStateOf(true) }
    var carouselCursorIndex by remember { mutableIntStateOf(0) }
    var vodSavedPosMs by remember { mutableLongStateOf(0L) }
    var nextEpPromptVisible by remember { mutableStateOf(false) }
    var nextEpRemainingMs by remember { mutableLongStateOf(60_000L) }
    var nextEpDismissed by remember { mutableStateOf(false) }
    var nextEpLaunched by remember { mutableStateOf(false) }
    var okHoldActive by remember { mutableStateOf(false) }
    var okHoldStartedAtMs by remember { mutableLongStateOf(0L) }
    var okHoldElapsedMs by remember { mutableLongStateOf(0L) }
    var okHoldCompleted by remember { mutableStateOf(false) }
    var activeLiveRecording by remember { mutableStateOf<RecordingDto?>(null) }
    val nextEpisodeCandidate = remember(vodRelatedMode, vodRelatedItems, channel.id) {
        if (!channel.isVodContent || vodRelatedMode != "episodes" || vodRelatedItems.size <= 1) null
        else {
            val idx = vodRelatedItems.indexOfFirst { it.id == channel.id }
            if (idx >= 0 && idx + 1 < vodRelatedItems.size) vodRelatedItems[idx + 1] else null
        }
    }
    val latestNextEp by rememberUpdatedState(nextEpisodeCandidate)
    val latestNextEpDismissed by rememberUpdatedState(nextEpDismissed)
    val isVod = channel.isVodContent
    val diagState = remember { PlayerDiagState() }
    val latestChannel by rememberUpdatedState(channel)
    val latestSession by rememberUpdatedState(session)
    val latestRelayKey by rememberUpdatedState(activeRelayKey)
    val latestTimeshiftStage by rememberUpdatedState(timeshiftStage)
    val latestVideoSize by rememberUpdatedState(videoSizeState)
    val latestHasPlayed by rememberUpdatedState(hasPlayedCurrentChannel)
    val latestReconnecting by rememberUpdatedState(reconnecting)

    fun reportChannelDiag(
        code: String,
        severity: String = "warn",
        message: String = "",
        extraDetails: Map<String, Any?> = emptyMap(),
        throttleMs: Long = 0L,
        streamUrlOverride: String? = null,
    ) {
        val ch = latestChannel
        val sess = latestSession
        val rKey = latestRelayKey.orEmpty()
        val stage = latestTimeshiftStage
        val mode = when {
            stage != TimeshiftStage.LIVE -> "timeshift"
            rKey.isNotBlank() -> "relay"
            else -> "direct"
        }
        val vSize = latestVideoSize
        val vFmt = runCatching { player.videoFormat }.getOrNull()
        val aFmt = runCatching { player.audioFormat }.getOrNull()
        val resW = vSize.width.takeIf { it > 0 } ?: vFmt?.width?.takeIf { it > 0 } ?: 0
        val resH = vSize.height.takeIf { it > 0 } ?: vFmt?.height?.takeIf { it > 0 } ?: 0
        val bufAheadMs = runCatching {
            (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
        }.getOrNull()
        val liveOffsetMs = runCatching {
            player.currentLiveOffset.takeIf { it != C.TIME_UNSET }
        }.getOrNull()
        val windowDurMs = runCatching {
            val tl = player.currentTimeline
            val idx = player.currentMediaItemIndex
            if (!tl.isEmpty && idx in 0 until tl.windowCount) {
                val w = Timeline.Window()
                tl.getWindow(idx, w)
                w.durationMs.takeIf { it != C.TIME_UNSET && it > 0L }
            } else null
        }.getOrNull()
        val merged = LinkedHashMap<String, Any?>()
        if (resW > 0 && resH > 0) merged["resolution"] = "${resW}x${resH}"
        val vKbps = vFmt?.bitrate?.takeIf { it > 0 }?.div(1000)
            ?: diagState.lastVideoBitrateKbps.takeIf { it > 0 }
        vKbps?.let { merged["videoBitrateKbps"] = it }
        (vFmt?.codecs?.takeIf { it.isNotBlank() } ?: diagState.lastVideoCodec.takeIf { it.isNotBlank() })
            ?.let { merged["videoCodec"] = it }
        (aFmt?.codecs?.takeIf { it.isNotBlank() } ?: diagState.lastAudioCodec.takeIf { it.isNotBlank() })
            ?.let { merged["audioCodec"] = it }
        bufAheadMs?.let { merged["bufferAheadMs"] = it }
        liveOffsetMs?.let { merged["liveOffsetMs"] = it }
        windowDurMs?.let { merged["windowDurationMs"] = it }
        diagState.lastBandwidthKbps.takeIf { it > 0 }?.let { merged["exoBandwidthKbps"] = it }
        merged["sourceIndex"] = sess.sourceIndex
        merged["totalSources"] = sess.sources.size
        merged.putAll(extraDetails)
        vm.reportDiagnostic(
            code = code,
            severity = severity,
            channelId = ch.id,
            channelName = ch.name,
            playbackMode = mode,
            relayKey = rKey,
            streamUrl = streamUrlOverride ?: sess.activeStreamUrl,
            message = message,
            details = merged,
            throttleMs = throttleMs,
        )
    }

    val isTimeshiftPaused = timeshiftStage == TimeshiftStage.STARTING ||
        timeshiftStage == TimeshiftStage.PAUSED_RECORDING ||
        timeshiftStage == TimeshiftStage.PAUSED_LIMIT_REACHED
    val isTimeshiftActive = timeshiftStage != TimeshiftStage.LIVE
    val keepTvAwake = isPlayingState || isTimeshiftPaused || (timeshiftSession?.status == "recording") || (activeLiveRecording != null)

    DisposableEffect(keepTvAwake, context) {
        val window = (context as? android.app.Activity)?.window
        if (keepTvAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            timeshiftStartJob?.cancel()
            val held = cleanupHolder.session
            if (held != null && (held.status != "limit_reached" || cleanupHolder.watchedAfterLimitPrompt)) {
                vm.deleteTimeshiftAsync(held.id)
            }
            cleanupHolder.relayChannelId?.let { chId ->
                vm.releaseLiveRelayAsync(chId)
            }
        }
    }

    val audioTracks = remember(tracksState) { extractOsdAudioTracks(tracksState) }
    val subtitleTracks = remember(tracksState) { extractOsdSubtitleTracks(tracksState) }
    val subtitlesDisabled = remember(tracksState, player.trackSelectionParameters) {
        player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
            !tracksState.isTypeSelected(C.TRACK_TYPE_TEXT)
    }
    val streamDetails = remember(videoSizeState, player.videoFormat) {
        formatStreamInfo(videoSizeState, player.videoFormat)
    }
    val isLikelyAudio = remember(channel.id, channel.name, channel.streamUrl, channel.categories, channel.categoryNames, channel.quality) {
        isLikelyAudioOnlyChannel(channel)
    }
    val hasAudioTrack = remember(tracksState) {
        tracksState.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
    }
    val hasAnyVideoTrack = remember(tracksState) {
        tracksState.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }
    }
    val hasVideoSize = videoSizeState.width > 0 && videoSizeState.height > 0
    val confirmedAudioOnlyRaw = hasPlayedCurrentChannel &&
        errorMessage == null &&
        !reconnecting &&
        !firstVideoFrameRendered &&
        !hasVideoSize &&
        !hasAnyVideoTrack &&
        (hasAudioTrack || isLikelyAudio)
    var confirmedAudioOnly by remember(channel.id, session.playToken) { mutableStateOf(false) }

    LaunchedEffect(confirmedAudioOnlyRaw, channel.id, session.playToken) {
        if (!confirmedAudioOnlyRaw) {
            confirmedAudioOnly = false
        } else {
            delay(3000L)
            confirmedAudioOnly = true
        }
    }

    val channelCountryCode = remember(channel.country) { normalizeCountryCode(channel.country) }
    val channelCountryName = remember(channelCountryCode, channel.countryName, strings.language) {
        channelCountryCode?.let { countryName(it, strings.language) }
            ?: channel.countryName.takeIf { it.isNotBlank() }
    }

    fun wakeControls() {
        controlsVisible = true
        hideTick++
    }

    fun cancelNumberEntry() {
        tuneJob?.cancel()
        tuneJob = null
        numberEntry = ""
    }

    fun openQuickDrawer(): Boolean {
        if (session.queue.isEmpty()) return false
        cancelNumberEntry()
        rightDrawerOpen = false
        quickDrawerIndex = session.index.coerceIn(0, (session.queue.size - 1).coerceAtLeast(0))
        quickDrawerOpen = true
        controlsVisible = true
        hideTick++
        return true
    }

    fun openRightDrawer(): Boolean {
        if (!isVod) {
            if (session.sources.isEmpty()) return false
            cancelNumberEntry()
            quickDrawerOpen = false
            rightDrawerIndex = session.sourceIndex.coerceIn(0, (session.sources.size - 1).coerceAtLeast(0))
            rightDrawerOpen = true
            controlsVisible = true
            hideTick++
            return true
        }
        if (vodRelatedItems.isEmpty()) return false
        cancelNumberEntry()
        quickDrawerOpen = false
        rightDrawerIndex = vodRelatedItems.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        rightDrawerOpen = true
        controlsVisible = true
        hideTick++
        return true
    }

    fun openCountryPicker() {
        if (pickerCountries.isEmpty()) {
            vm.showMessage(strings.t("Nessun paese disponibile", "No countries available", "Ningún país disponible"))
            return
        }
        countryIndex = pickerCountries.indexOfFirst { it.code == activeCountryCode }
            .coerceAtLeast(0)
        countryPickerOpen = true
        wakeControls()
    }

    fun applyNumberEntry(value: String) {
        val number = value.toIntOrNull()
        if (number != null) {
            scope.launch {
                val found = vm.tuneToNumber(number)
                if (found == null) {
                    vm.showMessage(
                        strings.t("Canale $number non trovato", "Channel $number not found", "Canal $number no encontrado"),
                    )
                }
                wakeControls()
            }
        }
        numberEntry = ""
    }

    fun scheduleTune(value: String) {
        tuneJob?.cancel()
        tuneJob = scope.launch {
            delay(1900)
            tuneJob = null
            applyNumberEntry(value)
        }
    }

    fun confirmNumberEntry() {
        val value = numberEntry
        tuneJob?.cancel()
        tuneJob = null
        numberEntry = ""
        applyNumberEntry(value)
    }

    fun zap(offset: Int) {
        audioSkipCount = 0
        cancelNumberEntry()
        wakeControls()
        vm.playRelative(offset)
    }

    fun handleZapKey(offset: Int, repeated: Boolean): Boolean {
        val now = System.currentTimeMillis()
        if (repeated && now - lastZapTimeMs < 180L) return true
        lastZapTimeMs = now
        zap(offset)
        return true
    }

    fun selectCountry() {
        val country = pickerCountries.getOrNull(countryIndex) ?: return
        countryPickerOpen = false
        vm.setTvCountry(country.code)
        wakeControls()
    }

    fun startStream(url: String, isVodStream: Boolean = false): Result<Unit> = runCatching {
        diagState.midPlayBufferingSinceMs = 0L
        player.playbackParameters = PlaybackParameters.DEFAULT
        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        if (!isVodStream) {
            mediaItemBuilder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .setTargetOffsetMs(C.TIME_UNSET)
                    .build(),
            )
        }
        val mediaItem = mediaItemBuilder.build()
        if (url.contains(".m3u8", ignoreCase = true) || url.contains("m3u8", ignoreCase = true)) {
            val hlsSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(defaultDataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorPolicy)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
            player.setMediaSource(hlsSource, true)
        } else {
            player.setMediaItem(mediaItem, true)
        }
        player.prepare()
        player.playWhenReady = true
    }

    fun startTimeshiftMedia(hlsUrl: String): Result<Unit> = runCatching {
        player.stop()
        player.clearMediaItems()
        val hlsSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(defaultDataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorPolicy)
            .setAllowChunklessPreparation(false)
            .createMediaSource(MediaItem.fromUri(hlsUrl))
        player.setMediaSources(listOf(hlsSource), 0, 0L)
        player.prepare()
        player.playWhenReady = true
    }

    suspend fun tuneAndStartLive(
        targetChannel: Channel,
        directUrl: String,
        shouldContinue: () -> Boolean,
    ) {
        activeRelayKey = null
        activeRelayUrl = null
        val disconnectedNow = vm.isServerDisconnected.value
        var playUrl = directUrl
        autonomousDirectMode = disconnectedNow
        if (targetChannel.isVodContent) {
            playUrl = if (disconnectedNow || directUrl != targetChannel.streamUrl) {
                directUrl
            } else {
                targetChannel.vodStreamUrl?.takeIf { it.isNotBlank() }?.let {
                    vm.resolveServerMediaUrl(it)
                } ?: directUrl
            }
        } else if (serverRelayEnabled && !disconnectedNow) {
            val tuned = vm.tuneLiveRelay(targetChannel, directUrl, pinSource = latestSession.pinSource)
            if (!shouldContinue()) return
            if (tuned != null && tuned.mode == "relay" && tuned.playlistUrl.isNotBlank()) {
                activeRelayKey = tuned.relayKey
                activeRelayChannelId = targetChannel.id
                val relayFullUrl = vm.resolveServerMediaUrl(tuned.playlistUrl)
                activeRelayUrl = relayFullUrl
                playUrl = relayFullUrl
                autonomousDirectMode = false
                android.util.Log.i(
                    "CasaTV",
                    "Live Relay attivo per ${targetChannel.name}: key=${tuned.relayKey} viewers=${tuned.viewers}",
                )
            } else {
                val reason = tuned?.reason ?: "timeout"
                autonomousDirectMode = vm.isServerDisconnected.value
                android.util.Log.i(
                    "CasaTV",
                    "Live direct per ${targetChannel.name}: mode=${tuned?.mode ?: "unavailable"} reason=$reason",
                )
                if (reason != "disabled" && !vm.isServerDisconnected.value) {
                    reportChannelDiag(
                        code = TvDiagnosticCodes.TV_RELAY_FALLBACK,
                        severity = "warn",
                        message = "Live Relay non avviato ($reason), ripiegamento diretto",
                        extraDetails = mapOf("reason" to reason),
                        streamUrlOverride = directUrl,
                    )
                }
            }
        }
        if (!shouldContinue()) return
        val started = startStream(playUrl, isVodStream = targetChannel.isVodContent)
        if (started.isFailure && playUrl != directUrl) {
            activeRelayKey = null
            activeRelayUrl = null
            autonomousDirectMode = true
            if (!vm.isServerDisconnected.value) vm.releaseLiveRelayAsync(targetChannel.id)
            activeRelayChannelId = null
            val fallback = startStream(directUrl, isVodStream = targetChannel.isVodContent)
            if (fallback.isFailure) {
                val err = fallback.exceptionOrNull()
                android.util.Log.w("CasaTV", "Impossibile avviare il flusso $directUrl", err)
                reportChannelDiag(
                    code = TvDiagnosticCodes.TV_FORMAT_ERR,
                    severity = "error",
                    message = "Avvio flusso fallito dopo fallback diretto: ${err?.message ?: "unsupported"}",
                    extraDetails = mapOf("cause" to (err?.javaClass?.simpleName ?: "")),
                    streamUrlOverride = directUrl,
                )
                buffering = false
                errorMessage = strings.t(
                    "Il formato di questo flusso non è supportato.",
                    "This stream format is not supported.",
                    "El formato de este flujo no es compatible.",
                )
            }
        } else if (started.isFailure) {
            val err = started.exceptionOrNull()
            android.util.Log.w("CasaTV", "Impossibile avviare il flusso $playUrl", err)
            reportChannelDiag(
                code = TvDiagnosticCodes.TV_FORMAT_ERR,
                severity = "error",
                message = "Avvio flusso fallito: ${err?.message ?: "unsupported"}",
                extraDetails = mapOf("cause" to (err?.javaClass?.simpleName ?: "")),
                streamUrlOverride = playUrl,
            )
            buffering = false
            errorMessage = strings.t(
                "Il formato di questo flusso non è supportato.",
                "This stream format is not supported.",
                "El formato de este flujo no es compatible.",
            )
        }
    }

    fun startCurrentLivePlayback() {
        val targetCh = latestChannel
        val url = currentStreamUrlState
        if (url == null) {
            vm.restartPlaybackSource()
            return
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            tuneAndStartLive(targetCh, url) { isActive && activeChannelIdState == targetCh.id }
        }
    }

    fun resetTimeshiftState(deleteFromServerIfNeeded: Boolean) {
        timeshiftStartJob?.cancel()
        timeshiftStartJob = null
        val held = timeshiftSession
        if (deleteFromServerIfNeeded && held != null &&
            (held.status != "limit_reached" || watchedAfterLimitPrompt)
        ) {
            vm.deleteTimeshiftAsync(held.id)
        }
        timeshiftSession = null
        timeshiftLoadedForId = null
        timeshiftStage = TimeshiftStage.LIVE
        watchedAfterLimitPrompt = false
        pendingRecording = null
        timeshiftPosMs = 0L
        timeshiftDurMs = 0L
    }

    fun returnToLive() {
        val held = timeshiftSession ?: pendingRecording
        timeshiftStartJob?.cancel()
        timeshiftStartJob = null
        if (held != null) {
            vm.deleteTimeshiftAsync(held.id)
        }
        timeshiftSession = null
        timeshiftLoadedForId = null
        timeshiftStage = TimeshiftStage.LIVE
        watchedAfterLimitPrompt = false
        pendingRecording = null
        timeshiftPosMs = 0L
        timeshiftDurMs = 0L
        errorMessage = null
        buffering = true
        startCurrentLivePlayback()
        wakeControls()
    }

    fun seekVodBy(deltaMs: Long) {
        if (!isVod) return
        val rawDur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: vodDurMs
        val maxPos = if (rawDur > 0L) rawDur else (player.currentPosition + deltaMs.coerceAtLeast(0L))
        val target = (player.currentPosition + deltaMs).coerceIn(0L, maxPos.coerceAtLeast(0L))
        runCatching { player.seekTo(target) }
        vodPosMs = target
        wakeControls()
    }

    fun pauseChannel() {
        if (isVod) {
            runCatching {
                if (player.isPlaying) {
                    player.pause()
                    isPlayingState = false
                } else {
                    player.play()
                    isPlayingState = true
                }
            }
            val curPos = player.currentPosition.coerceAtLeast(0L)
            val curDur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: vodDurMs
            if (curPos > 3_000L) {
                vm.saveVodProgressAsync(channel.id, curPos, curDur)
                vodSavedPosMs = curPos
            }
            wakeControls()
            return
        }
        if (pendingRecording != null) return
        if (timeshiftStage == TimeshiftStage.STARTING ||
            timeshiftStage == TimeshiftStage.PAUSED_RECORDING ||
            timeshiftStage == TimeshiftStage.PAUSED_LIMIT_REACHED
        ) {
            return
        }
        val activeTs = timeshiftSession
        if (activeTs != null &&
            (timeshiftStage == TimeshiftStage.PLAYING_TIMESHIFT || timeshiftStage == TimeshiftStage.PLAYING_LIMIT_REACHED)
        ) {
            runCatching { player.pause() }
            isPlayingState = false
            pauseOverlayIndex = 0
            controlsVisible = false
            timeshiftStage = if (activeTs.status == "limit_reached") {
                TimeshiftStage.PAUSED_LIMIT_REACHED
            } else {
                TimeshiftStage.PAUSED_RECORDING
            }
            return
        }

        val streamUrl = currentStreamUrlState ?: return
        val startedForChannelId = channel.id
        val snapshotChannel = channel
        runCatching { player.pause() }
        isPlayingState = false
        controlsVisible = false
        pauseOverlayIndex = 0
        timeshiftStage = TimeshiftStage.STARTING
        timeshiftStartJob?.cancel()
        timeshiftStartJob = scope.launch {
            val res = vm.startTimeshift(snapshotChannel, streamUrl)
            val dto = res.getOrNull()
            if (!isActive || activeChannelIdState != startedForChannelId || timeshiftStage != TimeshiftStage.STARTING) {
                if (dto != null) vm.deleteTimeshiftAsync(dto.id)
                return@launch
            }
            if (dto == null || dto.status == "error") {
                if (dto != null) vm.deleteTimeshiftAsync(dto.id)
                timeshiftStage = TimeshiftStage.LIVE
                runCatching { player.play() }
                vm.showMessage(
                    strings.t(
                        "Impossibile registrare in pausa sul server",
                        "Could not start server pause recording",
                        "No se pudo iniciar la grabación en pausa",
                    ),
                )
            } else {
                timeshiftSession = dto
                timeshiftLoadedForId = null
                watchedAfterLimitPrompt = false
                timeshiftPosMs = 0L
                timeshiftDurMs = dto.recordedMs
                timeshiftStage = if (dto.status == "limit_reached") {
                    TimeshiftStage.PAUSED_LIMIT_REACHED
                } else {
                    TimeshiftStage.PAUSED_RECORDING
                }
            }
        }
    }

    fun resumeChannel() {
        val ts = timeshiftSession ?: return
        if (timeshiftStage != TimeshiftStage.PAUSED_RECORDING && timeshiftStage != TimeshiftStage.PAUSED_LIMIT_REACHED) {
            return
        }
        errorMessage = null
        timeshiftStage = if (ts.status == "limit_reached") {
            TimeshiftStage.PLAYING_LIMIT_REACHED
        } else {
            TimeshiftStage.PLAYING_TIMESHIFT
        }
        if (timeshiftLoadedForId == ts.id) {
            runCatching {
                player.prepare()
                player.play()
            }
            wakeControls()
        } else {
            buffering = true
            val hlsUrl = vm.resolveServerMediaUrl(ts.playlistUrl)
            val res = startTimeshiftMedia(hlsUrl)
            if (res.isSuccess) {
                timeshiftLoadedForId = ts.id
                wakeControls()
            } else {
                timeshiftStage = if (ts.status == "limit_reached") {
                    TimeshiftStage.PAUSED_LIMIT_REACHED
                } else {
                    TimeshiftStage.PAUSED_RECORDING
                }
                buffering = false
                vm.showMessage(strings.t("Errore nella ripresa", "Failed to resume recording", "Error al reanudar"))
            }
        }
    }

    fun acceptPendingRecording() {
        val pending = pendingRecording ?: return
        pendingRecording = null
        watchedAfterLimitPrompt = true
        timeshiftSession = pending
        timeshiftDurMs = pending.recordedMs
        timeshiftPosMs = 0L
        timeshiftStage = TimeshiftStage.PLAYING_LIMIT_REACHED
        buffering = true
        val hlsUrl = vm.resolveServerMediaUrl(pending.playlistUrl)
        val res = startTimeshiftMedia(hlsUrl)
        if (res.isSuccess) {
            timeshiftLoadedForId = pending.id
            wakeControls()
        } else {
            vm.deleteTimeshiftAsync(pending.id)
            returnToLive()
        }
    }

    fun declinePendingRecording() {
        val pending = pendingRecording ?: return
        pendingRecording = null
        vm.deleteTimeshiftAsync(pending.id)
        buffering = true
        startCurrentLivePlayback()
        wakeControls()
    }

    fun scheduleAutoReconnect(reason: String, immediateSourceFailover: Boolean = false) {
        if (failingOver) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            if (isMixModeState && !hasPlayedCurrentChannel) {
                failingOver = true
                errorMessage = null
                try {
                    val moved = vm.tryNextSource()
                    if (moved) {
                        reportChannelDiag(
                            code = TvDiagnosticCodes.TV_FAILOVER_SRC,
                            severity = "warn",
                            message = "Cambio sorgente automatico in Mix ($reason)",
                            extraDetails = mapOf("reason" to reason, "phase" to "mix_initial"),
                        )
                    } else {
                        reportChannelDiag(
                            code = TvDiagnosticCodes.TV_RECONNECT_EXHAUSTED,
                            severity = "error",
                            message = "Tutte le sorgenti non raggiungibili in Mix ($reason)",
                            extraDetails = mapOf("reason" to reason),
                        )
                        buffering = false
                        reconnecting = false
                        errorMessage = strings.t(
                            "Tutte le sorgenti di questo canale non sono raggiungibili in questo momento.",
                            "All sources for this channel are unreachable right now.",
                            "Todas las fuentes de este canal no están disponibles en este momento.",
                        )
                    }
                } finally {
                    failingOver = false
                }
                return@launch
            }

            // Immediate autonomous direct-stream failover when server relay or VOD proxy drops
            val currentUri = runCatching {
                player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
            }.getOrDefault("")
            val isVodNow = latestChannel.isVodContent
            val isServerProxiedVod = isVodNow && currentUri.contains("/api/vod/stream/")
            if ((activeRelayUrl != null || isServerProxiedVod) && currentStreamUrlState != null) {
                val savedVodPos = if (isVodNow) runCatching { player.currentPosition }.getOrDefault(0L).coerceAtLeast(0L) else 0L
                val oldRelayCh = activeRelayChannelId
                activeRelayKey = null
                activeRelayUrl = null
                activeRelayChannelId = null
                autonomousDirectMode = true
                if (!vm.isServerDisconnected.value) {
                    oldRelayCh?.let { vm.releaseLiveRelayAsync(it) }
                }
                reconnectAttempt += 1
                reconnectPending = false
                reconnecting = false
                errorMessage = null
                android.util.Log.i("CasaTV", "Failover autonomo immediato su flusso diretto (${reason})")
                val res = startStream(currentStreamUrlState!!, isVodStream = isVodNow)
                if (isVodNow && savedVodPos > 0L) {
                    runCatching { player.seekTo(savedVodPos) }
                }
                if (res.isFailure) {
                    scheduleAutoReconnect("direct_failover_failed")
                }
                return@launch
            }

            if (immediateSourceFailover && vm.tryNextSource()) {
                reportChannelDiag(
                    code = TvDiagnosticCodes.TV_FAILOVER_SRC,
                    severity = "warn",
                    message = "Passaggio immediato a sorgente alternativa ($reason)",
                    extraDetails = mapOf("attempt" to 1, "reason" to reason, "immediate" to true),
                )
                return@launch
            }

            val backoffs = longArrayOf(700L, 1500L, 2500L, 4000L, 6000L, 8000L)
            if (reconnectAttempt < backoffs.size) {
                val delayMs = backoffs[reconnectAttempt]
                reconnectAttempt += 1
                reconnectPending = true
                reconnecting = true
                buffering = false
                errorMessage = null
                android.util.Log.i("CasaTV", "Riconnessione automatica ($reason), tentativo $reconnectAttempt/${backoffs.size} tra ${delayMs}ms")
                if (!vm.isServerDisconnected.value) {
                    reportChannelDiag(
                        code = TvDiagnosticCodes.TV_RECONNECT,
                        severity = "warn",
                        message = "Riconnessione automatica ($reason) tentativo $reconnectAttempt/${backoffs.size}",
                        extraDetails = mapOf(
                            "attempt" to reconnectAttempt,
                            "maxAttempts" to backoffs.size,
                            "reason" to reason,
                            "delayMs" to delayMs,
                        ),
                    )
                }
                delay(delayMs)
                if (!isActive) return@launch
                if (reconnectAttempt == 3 && vm.tryNextSource()) {
                    reconnectPending = false
                    reportChannelDiag(
                        code = TvDiagnosticCodes.TV_FAILOVER_SRC,
                        severity = "warn",
                        message = "Passaggio a sorgente alternativa al tentativo 3 ($reason)",
                        extraDetails = mapOf("attempt" to reconnectAttempt, "reason" to reason),
                    )
                    return@launch
                }
                val url = currentStreamUrlState
                if (url == null) {
                    reconnectPending = false
                    reconnecting = false
                    errorMessage = strings.t(
                        "Questo canale non ha un flusso valido o disponibile.",
                        "This channel has no valid or available stream.",
                        "Este canal no tiene un flujo válido o disponible.",
                    )
                    return@launch
                }
                reconnectPending = false
                val res = startStream(url, isVodStream = isVodNow)
                if (res.isFailure) {
                    scheduleAutoReconnect("start_failed")
                }
            } else if (bgRetryCycle < 4) {
                bgRetryCycle += 1
                reconnectPending = true
                reconnecting = false
                buffering = false
                errorMessage = strings.t(
                    "Flusso temporaneamente interrotto. Riconnessione automatica in attesa…",
                    "Stream temporarily interrupted. Background reconnect waiting…",
                    "Flujo interrumpido temporalmente. Reconexión en segundo plano…",
                )
                delay(15_000L)
                if (!isActive || errorMessage == null) {
                    reconnectPending = false
                    return@launch
                }
                reconnectAttempt = 0
                activeRelayKey = null
                activeRelayUrl = null
                val url = currentStreamUrlState ?: run {
                    reconnectPending = false
                    return@launch
                }
                reconnectPending = false
                reconnecting = true
                errorMessage = null
                val res = startStream(url, isVodStream = isVodNow)
                if (res.isFailure) {
                    scheduleAutoReconnect("background_retry_failed")
                }
            } else {
                reconnectPending = false
                reconnecting = false
                buffering = false
                reportChannelDiag(
                    code = TvDiagnosticCodes.TV_RECONNECT_EXHAUSTED,
                    severity = "error",
                    message = "Riconnessione fallita dopo tutti i tentativi ($reason)",
                    extraDetails = mapOf("reason" to reason, "bgRetryCycles" to bgRetryCycle),
                )
                errorMessage = strings.t(
                    "Flusso non raggiungibile al momento. Premi OK per riprovare.",
                    "Stream unreachable right now. Press OK to retry.",
                    "Flujo no disponible en este momento. Pulsa OK para reintentar.",
                )
            }
        }
    }

    DisposableEffect(player) {
        val analyticsListener = object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long,
            ) {
                if (bitrateEstimate > 0L) {
                    diagState.lastBandwidthKbps = (bitrateEstimate / 1000L).toInt()
                }
            }

            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                if (format.bitrate > 0) diagState.lastVideoBitrateKbps = format.bitrate / 1000
                format.codecs?.takeIf { it.isNotBlank() }?.let { diagState.lastVideoCodec = it }
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                format.codecs?.takeIf { it.isNotBlank() }?.let { diagState.lastAudioCodec = it }
            }

            override fun onLoadCompleted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
            ) {
                val uriStr = loadEventInfo.uri.toString()
                val isProgressiveVod = latestChannel.isVodContent && !uriStr.contains(".m3u8", ignoreCase = true) && !uriStr.endsWith(".ts", ignoreCase = true)
                if (!isProgressiveVod && loadEventInfo.loadDurationMs >= 4_000L && loadEventInfo.bytesLoaded > 0L) {
                    val loadKbps = ((loadEventInfo.bytesLoaded * 8L) / loadEventInfo.loadDurationMs.coerceAtLeast(1L)).toInt()
                    reportChannelDiag(
                        code = TvDiagnosticCodes.TV_SEG_SLOW,
                        severity = "warn",
                        message = "Download segmento lento (${loadEventInfo.loadDurationMs}ms)",
                        extraDetails = mapOf(
                            "loadDurationMs" to loadEventInfo.loadDurationMs,
                            "bytesLoaded" to loadEventInfo.bytesLoaded,
                            "loadKbps" to loadKbps,
                            "dataType" to mediaLoadData.dataType,
                            "uri" to uriStr.take(240),
                        ),
                        throttleMs = 15_000L,
                    )
                }
            }

            override fun onLoadError(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
                error: java.io.IOException,
                wasCanceled: Boolean,
            ) {
                val httpStatus = (error as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                reportChannelDiag(
                    code = TvDiagnosticCodes.TV_LOAD_ERROR,
                    severity = "warn",
                    message = "Errore caricamento segmento/playlist: ${error.message ?: error.javaClass.simpleName}",
                    extraDetails = mapOf(
                        "httpStatusCode" to httpStatus,
                        "errorType" to error.javaClass.simpleName,
                        "wasCanceled" to wasCanceled,
                        "dataType" to mediaLoadData.dataType,
                        "uri" to loadEventInfo.uri.toString().take(240),
                    ),
                    throttleMs = 10_000L,
                )
            }

            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long,
            ) {
                if (droppedFrames >= 15) {
                    reportChannelDiag(
                        code = TvDiagnosticCodes.TV_FRAMES_DROPPED,
                        severity = "warn",
                        message = "Persi $droppedFrames frame video in ${elapsedMs}ms",
                        extraDetails = mapOf(
                            "droppedFrames" to droppedFrames,
                            "elapsedMs" to elapsedMs,
                        ),
                        throttleMs = 15_000L,
                    )
                }
            }

            override fun onAudioUnderrun(
                eventTime: AnalyticsListener.EventTime,
                bufferSize: Int,
                bufferSizeMs: Long,
                elapsedSinceLastFeedMs: Long,
            ) {
                reportChannelDiag(
                    code = TvDiagnosticCodes.TV_AUDIO_UNDERRUN,
                    severity = "warn",
                    message = "Svuotamento buffer audio (underrun)",
                    extraDetails = mapOf(
                        "bufferSize" to bufferSize,
                        "bufferSizeMs" to bufferSizeMs,
                        "elapsedSinceLastFeedMs" to elapsedSinceLastFeedMs,
                    ),
                    throttleMs = 15_000L,
                )
            }
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING && !reconnecting
                if (playbackState == Player.STATE_BUFFERING) {
                    if (hasPlayedCurrentChannel && !reconnecting && latestTimeshiftStage == TimeshiftStage.LIVE) {
                        if (diagState.midPlayBufferingSinceMs == 0L) {
                            diagState.midPlayBufferingSinceMs = System.currentTimeMillis()
                        }
                    } else {
                        diagState.midPlayBufferingSinceMs = 0L
                    }
                } else {
                    val bufStarted = diagState.midPlayBufferingSinceMs
                    if (playbackState == Player.STATE_READY && bufStarted > 0L) {
                        val bufDurMs = System.currentTimeMillis() - bufStarted
                        if (bufDurMs >= 2_500L && !reconnecting && latestTimeshiftStage == TimeshiftStage.LIVE) {
                            val srcUrl = currentStreamUrlState.orEmpty()
                            if (srcUrl.isNotBlank() && !latestChannel.isVodContent) {
                                vm.reportChannelSourceAsync(latestChannel.id, srcUrl, "stall")
                            }
                            reportChannelDiag(
                                code = TvDiagnosticCodes.TV_BUF_SHORT,
                                severity = "warn",
                                message = "Pausa di buffering in riproduzione (${bufDurMs}ms)",
                                extraDetails = mapOf("bufferingDurationMs" to bufDurMs),
                                throttleMs = 12_000L,
                            )
                        }
                    }
                    diagState.midPlayBufferingSinceMs = 0L
                }
                if (playbackState == Player.STATE_READY) {
                    hasPlayedCurrentChannel = true
                    bgRetryCycle = 0
                    reconnecting = false
                    reconnectPending = false
                    errorMessage = null
                    if (latestChannel.isVodContent && !vodSeekApplied) {
                        vodSeekApplied = true
                        val resumeMs = latestChannel.positionMs
                        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                        if (dur > 0L) vodDurMs = dur
                        if (resumeMs > 15_000L && (dur <= 0L || resumeMs < dur - 15_000L)) {
                            runCatching { player.seekTo(resumeMs) }
                            vodPosMs = resumeMs
                        }
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    if (latestChannel.isVodContent) {
                        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: vodDurMs
                        if (dur > 0L) vm.saveVodProgressAsync(latestChannel.id, dur, dur)
                        val nextEpVal = latestNextEp
                        if (nextEpVal != null && !latestNextEpDismissed) {
                            nextEpLaunched = true
                            nextEpPromptVisible = false
                            vm.startPlayback(nextEpVal, vodRelatedItems)
                        }
                        return
                    }
                    when (latestTimeshiftStage) {
                        TimeshiftStage.LIVE -> if (player.isCurrentMediaItemLive || hasPlayedCurrentChannel) {
                            android.util.Log.w("CasaTV", "Flusso terminato inaspettatamente (STATE_ENDED), riconnessione...")
                            reportChannelDiag(
                                code = TvDiagnosticCodes.TV_STATE_ENDED,
                                severity = "warn",
                                message = "Flusso live terminato inaspettatamente (STATE_ENDED)",
                            )
                            scheduleAutoReconnect("state_ended")
                        }
                        TimeshiftStage.PLAYING_LIMIT_REACHED -> {
                            vm.showMessage(
                                strings.t(
                                    "Fine della registrazione: ritorno alla diretta",
                                    "End of recording: returning to live",
                                    "Fin de la grabación: volviendo al directo",
                                ),
                            )
                            returnToLive()
                        }
                        else -> Unit
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                diagState.midPlayBufferingSinceMs = 0L
                android.util.Log.w("CasaTV", "Errore di riproduzione (${error.errorCodeName})", error)
                if (pendingRecording != null ||
                    latestTimeshiftStage == TimeshiftStage.STARTING ||
                    latestTimeshiftStage == TimeshiftStage.PAUSED_RECORDING ||
                    latestTimeshiftStage == TimeshiftStage.PAUSED_LIMIT_REACHED
                ) {
                    return
                }
                if (latestTimeshiftStage == TimeshiftStage.PLAYING_TIMESHIFT ||
                    latestTimeshiftStage == TimeshiftStage.PLAYING_LIMIT_REACHED
                ) {
                    vm.showMessage(
                        strings.t(
                            "Errore nella registrazione, ritorno alla diretta",
                            "Recording playback error, returning to live",
                            "Error en la grabación, volviendo al directo",
                        ),
                    )
                    returnToLive()
                    return
                }
                val (diagCode, httpStatusCode) = DiagnosticReporter.classifyPlaybackError(error)
                val canSoftResync = error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                    (hasPlayedCurrentChannel && player.isCurrentMediaItemLive && isLiveSegmentHttpNotFound(error))
                var willSoftResync = false
                if (canSoftResync) {
                    val now = System.currentTimeMillis()
                    while (softResyncTimes.isNotEmpty() && now - softResyncTimes.first() > 30_000L) {
                        softResyncTimes.removeFirst()
                    }
                    willSoftResync = softResyncTimes.size < 2
                }
                reportChannelDiag(
                    code = diagCode,
                    severity = if (willSoftResync) "warn" else "error",
                    message = "Errore ExoPlayer ${error.errorCodeName}: ${error.cause?.message ?: error.message ?: ""}",
                    extraDetails = mapOf(
                        "exoErrorCode" to error.errorCodeName,
                        "httpStatusCode" to httpStatusCode,
                        "cause" to (error.cause?.javaClass?.simpleName ?: ""),
                        "action" to if (willSoftResync) "soft_resync" else "reconnect",
                    ),
                )
                if (willSoftResync) {
                    softResyncTimes.addLast(System.currentTimeMillis())
                    android.util.Log.i("CasaTV", "Soft live-edge resync (${error.errorCodeName})")
                    reportChannelDiag(
                        code = TvDiagnosticCodes.TV_SOFT_RESYNC,
                        severity = "info",
                        message = "Soft live-edge resync su ${error.errorCodeName}",
                        extraDetails = mapOf("exoErrorCode" to error.errorCodeName),
                        throttleMs = 5_000L,
                    )
                    buffering = true
                    runCatching {
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.playWhenReady = true
                    }
                    return
                }
                val isPermanentSourceError = (httpStatusCode != null && httpStatusCode in intArrayOf(401, 403, 404, 410)) ||
                    error.cause is java.net.UnknownHostException ||
                    error.cause?.cause is java.net.UnknownHostException
                val srcUrl = currentStreamUrlState.orEmpty()
                if (!latestChannel.isVodContent && srcUrl.isNotBlank()) {
                    vm.reportChannelSourceAsync(latestChannel.id, srcUrl, "error")
                }
                scheduleAutoReconnect(error.errorCodeName, immediateSourceFailover = isPermanentSourceError)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
                if (isPlaying) {
                    failingOver = false
                    reconnecting = false
                    reconnectPending = false
                    bgRetryCycle = 0
                    errorMessage = null
                    hasPlayedCurrentChannel = true
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksState = tracks
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoSizeState = videoSize
                if (videoSize.width > 0 && videoSize.height > 0) {
                    audioSkipCount = 0
                }
            }

            override fun onRenderedFirstFrame() {
                firstVideoFrameRendered = true
                audioSkipCount = 0
            }
        }
        player.addAnalyticsListener(analyticsListener)
        player.addListener(listener)
        player.setHandleAudioBecomingNoisy(true)
        onDispose {
            reconnectJob?.cancel()
            vm.diagnostics.flushAsync()
            player.removeAnalyticsListener(analyticsListener)
            player.removeListener(listener)
            runCatching {
                player.stop()
                player.clearMediaItems()
                player.release()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    isPlayingState = false
                    runCatching { player.pause() }
                }
                Lifecycle.Event.ON_STOP -> {
                    isPlayingState = false
                    runCatching { player.stop() }
                    vm.clearSyncPlaybackAsync()
                }
                Lifecycle.Event.ON_RESUME -> if (
                    errorMessage == null &&
                    pendingRecording == null &&
                    timeshiftStage != TimeshiftStage.STARTING &&
                    timeshiftStage != TimeshiftStage.PAUSED_RECORDING &&
                    timeshiftStage != TimeshiftStage.PAUSED_LIMIT_REACHED
                ) {
                    runCatching {
                        if (!latestChannel.isVodContent && latestTimeshiftStage == TimeshiftStage.LIVE) {
                            player.seekToDefaultPosition()
                        }
                        player.prepare()
                        player.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            isPlayingState = false
            runCatching { player.pause() }
            vm.clearSyncPlaybackAsync()
        }
    }

    // Live Safety Cushion (gradual imperceptible delay up to ~8s capped to live window)
    // + Stall/Buffering watchdog for silent background reconnect.
    LaunchedEffect(player, channel.id, session.playToken, liveSafetyBufferEnabled) {
        var lastWatchPos = -1L
        var lastAdvanceMs = System.currentTimeMillis()
        var bufferingSinceMs = 0L
        var readySinceMs = 0L
        var lastCushionSpeedChangeMs = 0L
        var cushionBuilding = false

        while (isActive) {
            delay(1000)
            if (timeshiftStage != TimeshiftStage.LIVE || pendingRecording != null) {
                cushionBuilding = false
                readySinceMs = 0L
                bufferingSinceMs = 0L
                lastWatchPos = -1L
                lastAdvanceMs = System.currentTimeMillis()
                if (kotlin.math.abs(player.playbackParameters.speed - 1.0f) > 0.002f) {
                    runCatching { player.playbackParameters = PlaybackParameters(1.0f, 1.0f) }
                }
                continue
            }
            val now = System.currentTimeMillis()
            val pState = player.playbackState
            val isReady = pState == Player.STATE_READY && player.playWhenReady

            if (isReady && errorMessage == null && !reconnecting) {
                if (readySinceMs == 0L) readySinceMs = now
                if (reconnectAttempt > 0 && now - readySinceMs >= 6_000L) {
                    reconnectAttempt = 0
                }
            } else {
                readySinceMs = 0L
            }

            if (pState == Player.STATE_BUFFERING && errorMessage == null && !reconnectPending && currentStreamUrlState != null) {
                if (bufferingSinceMs == 0L) bufferingSinceMs = now
                if (now - bufferingSinceMs > 20_000L) {
                    val elapsedBufMs = now - bufferingSinceMs
                    bufferingSinceMs = now
                    android.util.Log.w("CasaTV", "Buffering prolungato (>20s), avvio riconnessione automatica")
                    val srcUrl = currentStreamUrlState.orEmpty()
                    if (!latestChannel.isVodContent && srcUrl.isNotBlank()) {
                        vm.reportChannelSourceAsync(latestChannel.id, srcUrl, "stall")
                    }
                    reportChannelDiag(
                        code = TvDiagnosticCodes.TV_BUF_TIMEOUT,
                        severity = "error",
                        message = "Buffering prolungato (${elapsedBufMs}ms), avvio riconnessione",
                        extraDetails = mapOf("elapsedMs" to elapsedBufMs),
                    )
                    scheduleAutoReconnect("buffering_timeout")
                }
            } else {
                bufferingSinceMs = 0L
            }

            if (isReady && errorMessage == null && !reconnectPending && currentStreamUrlState != null) {
                val pos = player.currentPosition
                if (pos == lastWatchPos) {
                    if (now - lastAdvanceMs > 10_000L) {
                        val elapsedStallMs = now - lastAdvanceMs
                        lastAdvanceMs = now
                        while (softResyncTimes.isNotEmpty() && now - softResyncTimes.first() > 30_000L) {
                            softResyncTimes.removeFirst()
                        }
                        if (hasPlayedCurrentChannel && player.isCurrentMediaItemLive && softResyncTimes.isEmpty()) {
                            softResyncTimes.addLast(now)
                            android.util.Log.i("CasaTV", "Stall su live pos=$pos per 10s, soft resync al live edge")
                            reportChannelDiag(
                                code = TvDiagnosticCodes.TV_STALL_RESYNC,
                                severity = "warn",
                                message = "Stall video su live pos=$pos (${elapsedStallMs}ms), tentato soft resync",
                                extraDetails = mapOf("positionMs" to pos, "elapsedMs" to elapsedStallMs),
                            )
                            runCatching {
                                player.seekToDefaultPosition()
                                player.prepare()
                                player.playWhenReady = true
                            }
                        } else {
                            android.util.Log.w("CasaTV", "Flusso bloccato su pos=$pos per 10s, avvio riconnessione automatica")
                            reportChannelDiag(
                                code = TvDiagnosticCodes.TV_STALL_TIMEOUT,
                                severity = "error",
                                message = "Video bloccato su pos=$pos (${elapsedStallMs}ms), avviata riconnessione",
                                extraDetails = mapOf("positionMs" to pos, "elapsedMs" to elapsedStallMs),
                            )
                            scheduleAutoReconnect("stall_watchdog")
                        }
                    }
                } else {
                    lastWatchPos = pos
                    lastAdvanceMs = now
                }
            } else {
                lastWatchPos = -1L
                lastAdvanceMs = now
            }

            if (multiTvFollowerActive || syncState?.syncActive == true) {
                cushionBuilding = false
                if (multiTvFollowerActive.not() && kotlin.math.abs(player.playbackParameters.speed - 1.0f) > 0.002f) {
                    runCatching { player.playbackParameters = PlaybackParameters(1.0f, 1.0f) }
                }
            } else if (isReady) {
                val windowDurationMs = run {
                    val timeline = player.currentTimeline
                    if (!timeline.isEmpty && player.currentMediaItemIndex in 0 until timeline.windowCount) {
                        val w = Timeline.Window()
                        timeline.getWindow(player.currentMediaItemIndex, w)
                        if (w.durationMs != C.TIME_UNSET && w.durationMs > 0L) w.durationMs else 0L
                    } else 0L
                }
                val rawLiveOffset = player.currentLiveOffset.takeIf { it != C.TIME_UNSET }
                val pos = player.currentPosition
                val effectiveOffsetMs = rawLiveOffset ?: if (windowDurationMs > 0L && pos >= 0L) {
                    (windowDurationMs - pos).coerceAtLeast(0L)
                } else {
                    -1L
                }
                val windowSafeForCushion = windowDurationMs >= 35_000L &&
                    effectiveOffsetMs > 0L &&
                    effectiveOffsetMs < (windowDurationMs * 40L) / 100L
                val disableCushionFor1080pLowRam = isLowRam &&
                    (videoSizeState.width >= 1920 || (player.videoFormat?.width ?: 0) >= 1920)

                if (!liveSafetyBufferEnabled || !player.isCurrentMediaItemLive || !windowSafeForCushion || disableCushionFor1080pLowRam) {
                    cushionBuilding = false
                    if (kotlin.math.abs(player.playbackParameters.speed - 1.0f) > 0.002f) {
                        runCatching { player.playbackParameters = PlaybackParameters(1.0f, 1.0f) }
                    }
                } else {
                    val bufferedAheadMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
                    val effectiveTargetMs = minOf(8_000L, (windowDurationMs * 25L) / 100L).coerceAtLeast(4_000L)
                    val lowWatermarkMs = (effectiveTargetMs - 2_500L).coerceAtLeast(2_000L)
                    if (!cushionBuilding && bufferedAheadMs < lowWatermarkMs) {
                        cushionBuilding = true
                    } else if (cushionBuilding && bufferedAheadMs >= effectiveTargetMs) {
                        cushionBuilding = false
                    }
                    val targetSpeed = if (cushionBuilding) 0.97f else 1.0f
                    val currentSpeed = player.playbackParameters.speed
                    if (kotlin.math.abs(currentSpeed - targetSpeed) > 0.003f &&
                        (targetSpeed == 1.0f || now - lastCushionSpeedChangeMs >= 3_000L)
                    ) {
                        runCatching {
                            player.playbackParameters = PlaybackParameters(targetSpeed, 1.0f)
                            lastCushionSpeedChangeMs = now
                            android.util.Log.d("CasaTV", "LiveBuffer speed=$targetSpeed ahead=${bufferedAheadMs}ms target=${effectiveTargetMs}ms")
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(player, channel.id, multiTvSyncEnabled) {
        if (!multiTvSyncEnabled) {
            multiTvFollowerActive = false
            syncState = null
            inSyncByDelta = false
            if (!liveSafetyBufferEnabled) {
                runCatching { player.playbackParameters = PlaybackParameters(1.0f, 1.0f) }
            }
            return@LaunchedEffect
        }
        var prevSyncActive = false
        var filteredDeltaMs = 0.0
        var hasFilteredDelta = false
        var lastSpeedChangeTime = 0L
        var lastSeekTime = 0L
        var pollIntervalMs = 250L

        while (isActive) {
            delay(pollIntervalMs)
            val now = System.currentTimeMillis()
            val isReady = player.playbackState == Player.STATE_READY && player.playWhenReady
            val currentPos = player.currentPosition

            if (isReady && timeshiftStage == TimeshiftStage.LIVE && pendingRecording == null && !isServerDisconnected) {
                val isLive = player.isCurrentMediaItemLive
                val hlsProgress = if (isLive) getHlsSegmentProgress(player) else null

                val liveOffset = if (player.currentLiveOffset != C.TIME_UNSET && player.currentLiveOffset > 0L) {
                    player.currentLiveOffset
                } else 0L

                val reqStartElapsed = SystemClock.elapsedRealtime()
                val resp = runCatching {
                    vm.syncPlayback(
                        channelId = channel.id,
                        isLive = isLive,
                        positionMs = currentPos,
                        liveOffsetMs = liveOffset,
                        segmentSeq = hlsProgress?.segmentSeq ?: 0L,
                        segmentOffsetMs = hlsProgress?.offsetMs ?: 0L,
                        segmentDurationMs = hlsProgress?.durationMs ?: 0L,
                    )
                }.getOrNull()
                val halfRttMs = ((SystemClock.elapsedRealtime() - reqStartElapsed) / 2L).coerceIn(0L, 400L)

                if (resp != null && resp.syncActive) {
                    syncState = resp
                    multiTvFollowerActive = !resp.isLeader
                    if (!prevSyncActive) {
                        prevSyncActive = true
                        val partner = if (resp.isLeader) {
                            resp.peers.firstOrNull { !it.isLeader }?.name ?: strings.t("Altra TV", "Other TV", "Otra TV")
                        } else {
                            resp.leaderName.ifBlank { strings.t("Altra TV", "Other TV", "Otra TV") }
                        }
                        vm.showMessage(
                            strings.t(
                                "Sincronizzazione attiva con $partner",
                                "Multi-TV sync active with $partner",
                                "Sincronización multi-TV activa con $partner",
                            ),
                        )
                    }

                    if (!resp.isLeader) {
                        val compensatedSegOffset = resp.targetSegmentOffsetMs + halfRttMs
                        val compensatedPosMs = resp.targetPositionMs + halfRttMs
                        val playlist = if (isLive) (player.currentManifest as? HlsManifest)?.mediaPlaylist else null
                        val seqCompatible = hlsProgress != null &&
                            resp.targetSegmentSeq > 0L &&
                            playlist != null &&
                            kotlin.math.abs(resp.targetSegmentSeq - hlsProgress.segmentSeq) <= 120L
                        val rawDeltaMs: Long = when {
                            !isLive -> compensatedPosMs - currentPos
                            seqCompatible -> computeHlsDeltaMs(
                                playlist = playlist!!,
                                mySeq = hlsProgress!!.segmentSeq,
                                myOffsetMs = hlsProgress.offsetMs,
                                leaderSeq = resp.targetSegmentSeq,
                                leaderOffsetMs = compensatedSegOffset,
                            )
                            liveOffset > 0L && resp.targetLiveOffsetMs > 0L -> {
                                ((liveOffset - resp.targetLiveOffsetMs) + halfRttMs).coerceIn(-30_000L, 30_000L)
                            }
                            else -> 0L
                        }

                        // Safe seek only for large drift (>2.5s) with generous cooldown and live-edge safety margin
                        val absRaw = kotlin.math.abs(rawDeltaMs)
                        val seekCooldownMs = if (isLive) 12_000L else 5_000L
                        val seekTriggerMs = if (isLive) 2_500L else 1_200L
                        val seekReady = (now - lastSeekTime) >= seekCooldownMs && absRaw in seekTriggerMs..60_000L
                        var soughtNow = false
                        if (seekReady) {
                            if (!isLive && player.duration > 4_000L) {
                                lastSeekTime = now
                                runCatching {
                                    player.seekTo(compensatedPosMs.coerceIn(0L, (player.duration - 2_000L).coerceAtLeast(0L)))
                                    player.playbackParameters = PlaybackParameters(1.0f, 1.0f)
                                }
                                hasFilteredDelta = false
                                lastSpeedChangeTime = now
                                soughtNow = true
                            } else if (isLive && playlist != null) {
                                val playlistDurationMs = playlist.durationUs / 1000L
                                if (playlistDurationMs >= 14_000L) {
                                    val targetPos = if (seqCompatible) {
                                        getPlaylistTimelinePositionMs(
                                            playlist = playlist,
                                            seq = resp.targetSegmentSeq,
                                            offsetMs = compensatedSegOffset,
                                        )
                                    } else {
                                        currentPos + rawDeltaMs
                                    }
                                    val maxSafePos = (playlistDurationMs - 8_000L).coerceAtLeast(1_000L)
                                    val clampedTarget = targetPos.coerceIn(1_000L, maxSafePos)
                                    if (kotlin.math.abs(clampedTarget - currentPos) >= 2_000L) {
                                        lastSeekTime = now
                                        runCatching {
                                            player.seekTo(clampedTarget)
                                            player.playbackParameters = PlaybackParameters(1.0f, 1.0f)
                                        }
                                        android.util.Log.d("CasaSync", "Instant seek live: pos=$currentPos -> target=$clampedTarget (win=${playlistDurationMs}ms, rawDelta=$rawDeltaMs, seqOk=$seqCompatible)")
                                        hasFilteredDelta = false
                                        lastSpeedChangeTime = now
                                        soughtNow = true
                                    }
                                }
                            }
                        }

                        if (!soughtNow) {
                            val deltaSample = if (hasFilteredDelta && kotlin.math.abs(filteredDeltaMs) < 400.0 && kotlin.math.abs(rawDeltaMs - filteredDeltaMs) > 3000.0) {
                                filteredDeltaMs
                            } else {
                                rawDeltaMs.toDouble()
                            }
                            filteredDeltaMs = if (!hasFilteredDelta) {
                                hasFilteredDelta = true
                                deltaSample
                            } else {
                                0.50 * deltaSample + 0.50 * filteredDeltaMs
                            }

                            val effectiveDelta = filteredDeltaMs.toLong()
                            val absDelta = kotlin.math.abs(effectiveDelta)
                            inSyncByDelta = if (inSyncByDelta) absDelta < 140L else absDelta <= 50L

                            val bufferedAheadSyncMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
                            val rawTargetSpeed = when {
                                inSyncByDelta -> 1.0f
                                effectiveDelta > 1_000L -> 1.05f
                                effectiveDelta > 300L -> 1.035f
                                effectiveDelta > 120L -> 1.02f
                                effectiveDelta > 50L -> 1.01f
                                effectiveDelta < -1_000L -> 0.95f
                                effectiveDelta < -300L -> 0.965f
                                effectiveDelta < -120L -> 0.98f
                                effectiveDelta < -50L -> 0.99f
                                else -> 1.0f
                            }
                            val targetSpeed = if (rawTargetSpeed > 1.0f && bufferedAheadSyncMs < 4_000L) 1.0f else rawTargetSpeed
                            val currentSpeed = player.playbackParameters.speed
                            if (kotlin.math.abs(currentSpeed - targetSpeed) > 0.003f &&
                                (targetSpeed == 1.0f || now - lastSpeedChangeTime >= 700L)
                            ) {
                                runCatching { player.playbackParameters = PlaybackParameters(targetSpeed, 1.0f) }
                                lastSpeedChangeTime = now
                            }
                        }
                        pollIntervalMs = if (inSyncByDelta) 1_000L else 500L
                    } else {
                        inSyncByDelta = true
                        hasFilteredDelta = false
                        if (kotlin.math.abs(player.playbackParameters.speed - 1.0f) > 0.002f) {
                            runCatching { player.playbackParameters = PlaybackParameters(1.0f, 1.0f) }
                        }
                        pollIntervalMs = 1_000L
                    }
                } else {
                    pollIntervalMs = 2_000L
                    prevSyncActive = false
                    multiTvFollowerActive = false
                    syncState = null
                    inSyncByDelta = false
                    hasFilteredDelta = false
                }
            } else {
                pollIntervalMs = 2_000L
            }
        }
    }

    LaunchedEffect(timeshiftSession?.id, timeshiftStage) {
        val activeTs = timeshiftSession ?: return@LaunchedEffect
        var tick = 0
        while (isActive && timeshiftSession?.id == activeTs.id) {
            timeshiftPosMs = if (timeshiftLoadedForId == activeTs.id) {
                player.currentPosition.coerceAtLeast(0L)
            } else 0L
            val playerDur = if (timeshiftLoadedForId == activeTs.id) {
                player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
            } else 0L
            val latestDur = timeshiftSession?.recordedMs ?: activeTs.recordedMs
            timeshiftDurMs = maxOf(latestDur, playerDur, timeshiftPosMs)

            if (tick % 3 == 0 && (timeshiftSession?.status ?: activeTs.status) == "recording") {
                val polled = vm.pollTimeshiftStatus(activeTs.id)
                if (polled != null && timeshiftSession?.id == polled.id) {
                    timeshiftSession = polled
                    timeshiftDurMs = maxOf(polled.recordedMs, timeshiftDurMs)
                    if (polled.status == "limit_reached") {
                        when (timeshiftStage) {
                            TimeshiftStage.PAUSED_RECORDING -> {
                                timeshiftStage = TimeshiftStage.PAUSED_LIMIT_REACHED
                            }
                            TimeshiftStage.PLAYING_TIMESHIFT -> {
                                timeshiftStage = TimeshiftStage.PLAYING_LIMIT_REACHED
                            }
                            else -> Unit
                        }
                    } else if (polled.status == "error") {
                        vm.showMessage(
                            strings.t(
                                "Registrazione interrotta sul server",
                                "Server recording stopped",
                                "Grabación detenida en el servidor",
                            ),
                        )
                    }
                }
            }
            tick++
            delay(1000L)
        }
    }

    LaunchedEffect(session.channel.id, session.playToken, session.sourceIndex) {
        reconnectJob?.cancel()
        activeRelayKey = null
        activeRelayUrl = null
        val prevRelayCh = activeRelayChannelId
        if (prevRelayCh != null && prevRelayCh != session.channel.id) {
            vm.releaseLiveRelayAsync(prevRelayCh)
            activeRelayChannelId = null
        }
        resetTimeshiftState(deleteFromServerIfNeeded = true)
        hasPlayedCurrentChannel = false
        firstVideoFrameRendered = false
        tracksState = Tracks.EMPTY
        videoSizeState = VideoSize.UNKNOWN
        reconnectAttempt = 0
        bgRetryCycle = 0
        softResyncTimes.clear()
        reconnectPending = false
        reconnecting = false
        vodPosMs = session.channel.positionMs
        vodDurMs = session.channel.durationMs
        vodSeekApplied = false
        vodSavedPosMs = 0L
        nextEpPromptVisible = false
        nextEpRemainingMs = 60_000L
        nextEpDismissed = false
        nextEpLaunched = false
        errorMessage = null
        failingOver = false
        buffering = true
        delay(250)
        val pending = if (session.sourceIndex < 0 && !session.channel.isVodContent && !vm.isServerDisconnected.value) vm.checkPendingTimeshift(session.channel.id) else null
        if (pending != null) {
            activeRelayChannelId?.let { vm.releaseLiveRelayAsync(it) }
            activeRelayChannelId = null
            runCatching {
                player.stop()
                player.clearMediaItems()
            }
            buffering = false
            isPlayingState = false
            pendingRecording = pending
            pendingPromptIndex = 0
            wakeControls()
            return@LaunchedEffect
        }
        val url = playableStreamUrl(session.activeStreamUrl)
        if (url == null) {
            activeRelayChannelId?.let { vm.releaseLiveRelayAsync(it) }
            activeRelayChannelId = null
            runCatching {
                player.stop()
                player.clearMediaItems()
            }
            buffering = false
            errorMessage = strings.t(
                "Questo canale non ha un flusso valido o disponibile.",
                "This channel has no valid or available stream.",
                "Este canal no tiene un flujo válido o disponible.",
            )
        } else {
            val channelSnap = session.channel
            tuneAndStartLive(channelSnap, url) { isActive && activeChannelIdState == channelSnap.id }
        }
        wakeControls()
    }

    LaunchedEffect(channel.id, isVod) {
        if (isVod) {
            val res = vm.fetchVodRelated(channel.id)
            vodRelatedMode = res.mode
            vodRelatedItems = res.items
            carouselCursorIndex = res.items.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        } else {
            val q = session.queue
            val idx = q.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
            val start = (idx - 2).coerceAtLeast(0)
            val slice = q.subList(start, (start + 18).coerceAtMost(q.size))
            vodRelatedMode = "channels"
            vodRelatedItems = slice
            carouselCursorIndex = slice.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        }
    }

    LaunchedEffect(isVod, channel.id, session.playToken) {
        nextEpPromptVisible = false
        nextEpDismissed = false
        nextEpLaunched = false
        nextEpRemainingMs = 60_000L
        if (!isVod) return@LaunchedEffect
        while (isActive) {
            val pos = runCatching { player.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
            val dur = runCatching { player.duration }.getOrDefault(C.TIME_UNSET)
                .takeIf { it != C.TIME_UNSET && it > 0L } ?: vodDurMs
            vodPosMs = pos
            if (dur > 0L) vodDurMs = dur
            if (pos > 3_000L && kotlin.math.abs(pos - vodSavedPosMs) >= 10_000L) {
                vodSavedPosMs = pos
                vm.saveVodProgressAsync(channel.id, pos, dur)
            }
            val cand = nextEpisodeCandidate
            val rem = if (dur > 0L) (dur - pos).coerceAtLeast(0L) else Long.MAX_VALUE
            if (cand != null && !nextEpDismissed && dur >= 65_000L && rem <= 60_000L) {
                nextEpRemainingMs = rem.coerceIn(0L, 60_000L)
                nextEpPromptVisible = true
                if (rem <= 250L && !nextEpLaunched) {
                    nextEpLaunched = true
                    nextEpPromptVisible = false
                    vm.startPlayback(cand, vodRelatedItems)
                    return@LaunchedEffect
                }
            } else {
                nextEpPromptVisible = false
            }
            delay(if (rem <= 65_000L) 100L else 500L)
        }
    }

    LaunchedEffect(okHoldActive, okHoldStartedAtMs, channel.id) {
        if (!okHoldActive || okHoldCompleted) return@LaunchedEffect
        while (isActive && okHoldActive && !okHoldCompleted) {
            val elapsed = (SystemClock.uptimeMillis() - okHoldStartedAtMs).coerceAtLeast(0L)
            okHoldElapsedMs = elapsed
            if (elapsed >= 3000L) {
                okHoldCompleted = true
                okHoldActive = false
                val curRec = activeLiveRecording
                if (curRec != null && curRec.channelId == channel.id && curRec.status == "recording") {
                    vm.stopLiveRecording(curRec.id)
                        .onSuccess {
                            activeLiveRecording = null
                            vm.showMessage(
                                strings.t(
                                    "Registrazione fermata e salvata per 7 giorni",
                                    "Recording stopped and saved for 7 days",
                                    "Grabación detenida y guardada 7 días",
                                ),
                            )
                        }
                        .onFailure {
                            vm.showMessage(strings.t("Errore arresto registrazione", "Failed to stop recording"))
                        }
                } else {
                    val streamUrl = currentStreamUrlState ?: channel.streamUrl
                    vm.startLiveRecording(channel, streamUrl)
                        .onSuccess { dto ->
                            activeLiveRecording = dto
                            vm.showMessage(
                                strings.t(
                                    "Registrazione avviata · Salvata per 7 giorni",
                                    "Recording started · Saved for 7 days",
                                    "Grabación iniciada · Guardada 7 días",
                                ),
                            )
                        }
                        .onFailure { err ->
                            vm.showMessage(err.message ?: strings.t("Impossibile avviare la registrazione", "Could not start recording"))
                        }
                }
                break
            }
            delay(50L)
        }
    }

    LaunchedEffect(activeLiveRecording?.id) {
        val recId = activeLiveRecording?.id ?: return@LaunchedEffect
        while (isActive) {
            delay(5000L)
            val updated = vm.fetchRecordingStatus(recId) ?: break
            activeLiveRecording = if (updated.status == "recording") updated else null
            if (updated.status != "recording") break
        }
    }

    DisposableEffect(isVod, channel.id) {
        onDispose {
            if (isVod) {
                val pos = runCatching { player.currentPosition }.getOrDefault(vodPosMs).coerceAtLeast(0L)
                val dur = runCatching { player.duration }.getOrDefault(vodDurMs)
                    .takeIf { it != C.TIME_UNSET && it > 0L } ?: vodDurMs
                if (pos > 3_000L) {
                    vm.saveVodProgressAsync(channel.id, pos, dur)
                }
            }
        }
    }

    LaunchedEffect(isServerDisconnected) {
        if (!isServerDisconnected) return@LaunchedEffect
        val directUrl = currentStreamUrlState ?: return@LaunchedEffect
        val currentUri = runCatching {
            player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
        }.getOrDefault("")
        val isVodNow = latestChannel.isVodContent
        val usingRelay = (activeRelayUrl != null && activeRelayChannelId == activeChannelIdState) || currentUri.contains("/api/relay/")
        val usingVodProxy = isVodNow && currentUri.contains("/api/vod/stream/")
        if (!usingRelay && !usingVodProxy) {
            autonomousDirectMode = true
            return@LaunchedEffect
        }
        val savedVodPos = if (isVodNow) runCatching { player.currentPosition }.getOrDefault(0L).coerceAtLeast(0L) else 0L
        reconnectJob?.cancel()
        reconnectJob = null
        activeRelayKey = null
        activeRelayUrl = null
        activeRelayChannelId = null
        autonomousDirectMode = true
        reconnectPending = false
        reconnecting = false
        errorMessage = null
        android.util.Log.i("CasaTV", "Server scollegato: passaggio autonomo immediato al flusso diretto per ${latestChannel.name}")
        val res = startStream(directUrl, isVodStream = isVodNow)
        if (isVodNow && savedVodPos > 0L) {
            runCatching { player.seekTo(savedVodPos) }
        }
        if (res.isFailure) {
            scheduleAutoReconnect("server_disconnected_direct_fail")
        }
    }

    LaunchedEffect(channel.id) {
        cancelNumberEntry()
        okHoldActive = false
        okHoldElapsedMs = 0L
        okHoldCompleted = false
        countryPickerOpen = false
        osdMenuOpen = false
        quickDrawerOpen = false
        rightDrawerOpen = false
        wakeControls()
    }

    LaunchedEffect(showMixRegionPicker) {
        if (!showMixRegionPicker) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }

    LaunchedEffect(
        controlsVisible,
        hideTick,
        numberEntry,
        countryPickerOpen,
        osdMenuOpen,
        quickDrawerOpen,
        rightDrawerOpen,
        showMixRegionPicker,
        isTimeshiftPaused,
        pendingRecording,
    ) {
        if (controlsVisible &&
            numberEntry.isEmpty() &&
            !countryPickerOpen &&
            !osdMenuOpen &&
            !quickDrawerOpen &&
            !rightDrawerOpen &&
            !showMixRegionPicker &&
            !isTimeshiftPaused &&
            pendingRecording == null
        ) {
            delay(6000)
            controlsVisible = false
        }
    }

    LaunchedEffect(confirmedAudioOnly, session.channel.id, session.playToken, session.isMixMode, mixIncludeAudioOnly) {
        if (confirmedAudioOnly && session.isMixMode && !mixIncludeAudioOnly && audioSkipCount < 10) {
            audioSkipCount += 1
            vm.skipAudioOnlyMixChannel(session.channel.id)
        }
    }

    LaunchedEffect(session.isMixMode) {
        if (!session.isMixMode) audioSkipCount = 0
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(15_000L)
            clockTick++
        }
    }

    // Report clean sustained live playback as healthy after 30s without buffering
    LaunchedEffect(session.channel.id, session.playToken) {
        if (!session.channel.isVodContent) {
            delay(30_000L)
            val srcUrl = currentStreamUrlState.orEmpty()
            if (isActive && srcUrl.isNotBlank() && errorMessage == null && !buffering && hasPlayedCurrentChannel) {
                vm.reportChannelSourceAsync(session.channel.id, srcUrl, "healthy")
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
    }

    BackHandler {
        when {
            nextEpPromptVisible -> {
                nextEpDismissed = true
                nextEpPromptVisible = false
            }
            quickDrawerOpen -> quickDrawerOpen = false
            rightDrawerOpen -> rightDrawerOpen = false
            countryPickerOpen -> countryPickerOpen = false
            osdMenuOpen -> osdMenuOpen = false
            numberEntry.isNotEmpty() -> {
                cancelNumberEntry()
                wakeControls()
            }
            else -> vm.stopPlayback()
        }
    }

    fun handleDigit(digit: Int): Boolean {
        if (numberEntry.length >= 4) return true
        val next = numberEntry + digit
        numberEntry = next
        wakeControls()
        scheduleTune(next)
        return true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (showMixRegionPicker) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                val code = native.keyCode
                val repeated = native.repeatCount > 0

                val isCenterKey = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                    code == AndroidKeyEvent.KEYCODE_ENTER ||
                    code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER

                // 3-second hold OK on live TV to start/stop 7-day recording (tap < 1s toggles controls)
                if (isCenterKey &&
                    !isVod &&
                    pendingRecording == null &&
                    !quickDrawerOpen &&
                    !rightDrawerOpen &&
                    !countryPickerOpen &&
                    !osdMenuOpen &&
                    !isTimeshiftPaused &&
                    errorMessage == null &&
                    numberEntry.isEmpty()
                ) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (!repeated) {
                                okHoldActive = true
                                okHoldCompleted = false
                                okHoldElapsedMs = 0L
                                okHoldStartedAtMs = SystemClock.uptimeMillis()
                            }
                            return@onPreviewKeyEvent true
                        }
                        KeyEventType.KeyUp -> {
                            if (okHoldActive) {
                                val elapsed = SystemClock.uptimeMillis() - okHoldStartedAtMs
                                val wasShortTap = !okHoldCompleted && elapsed < 1000L
                                val wasCancelled = !okHoldCompleted && elapsed >= 1000L
                                okHoldActive = false
                                okHoldElapsedMs = 0L
                                okHoldCompleted = false
                                if (wasShortTap) {
                                    controlsVisible = !controlsVisible
                                    if (controlsVisible) hideTick++
                                } else if (wasCancelled) {
                                    vm.showMessage(
                                        strings.t(
                                            "Registrazione annullata",
                                            "Recording cancelled",
                                            "Grabación cancelada",
                                        ),
                                    )
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                        else -> return@onPreviewKeyEvent true
                    }
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (pendingRecording != null) {
                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> pendingPromptIndex = 0
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> pendingPromptIndex = 1
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            if (pendingPromptIndex == 0) {
                                acceptPendingRecording()
                            } else {
                                declinePendingRecording()
                            }
                        }
                        AndroidKeyEvent.KEYCODE_BACK -> vm.stopPlayback()
                    }
                    return@onPreviewKeyEvent true
                }

                if (nextEpPromptVisible && nextEpisodeCandidate != null && !quickDrawerOpen && !rightDrawerOpen && !osdMenuOpen) {
                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            val nextEp = nextEpisodeCandidate
                            nextEpLaunched = true
                            nextEpPromptVisible = false
                            vm.startPlayback(nextEp, vodRelatedItems)
                            return@onPreviewKeyEvent true
                        }
                        AndroidKeyEvent.KEYCODE_BACK -> {
                            nextEpDismissed = true
                            nextEpPromptVisible = false
                            return@onPreviewKeyEvent true
                        }
                    }
                }

                if (quickDrawerOpen) {
                    val lastIdx = (session.queue.size - 1).coerceAtLeast(0)
                    val now = System.currentTimeMillis()
                    when (code) {
                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                        -> {
                            quickDrawerOpen = false
                            wakeControls()
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        -> {
                            if (repeated && now - lastDrawerMoveMs < 65L) return@onPreviewKeyEvent true
                            lastDrawerMoveMs = now
                            quickDrawerIndex = (quickDrawerIndex - 1).coerceAtLeast(0)
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        -> {
                            if (repeated && now - lastDrawerMoveMs < 65L) return@onPreviewKeyEvent true
                            lastDrawerMoveMs = now
                            quickDrawerIndex = (quickDrawerIndex + 1).coerceAtMost(lastIdx)
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            val chosen = quickDrawerIndex
                            quickDrawerOpen = false
                            vm.playChannelAtQueueIndex(chosen)
                            wakeControls()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (rightDrawerOpen) {
                    val lastIdx = if (isVod) {
                        (vodRelatedItems.size - 1).coerceAtLeast(0)
                    } else {
                        (session.sources.size - 1).coerceAtLeast(0)
                    }
                    val now = System.currentTimeMillis()
                    when (code) {
                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                        -> {
                            rightDrawerOpen = false
                            wakeControls()
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        -> {
                            if (repeated && now - lastDrawerMoveMs < 65L) return@onPreviewKeyEvent true
                            lastDrawerMoveMs = now
                            rightDrawerIndex = (rightDrawerIndex - 1).coerceAtLeast(0)
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        -> {
                            if (repeated && now - lastDrawerMoveMs < 65L) return@onPreviewKeyEvent true
                            lastDrawerMoveMs = now
                            rightDrawerIndex = (rightDrawerIndex + 1).coerceAtMost(lastIdx)
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            if (isVod) {
                                val chosen = vodRelatedItems.getOrNull(rightDrawerIndex)
                                rightDrawerOpen = false
                                if (chosen != null) vm.startPlayback(chosen, vodRelatedItems)
                            } else {
                                val chosen = rightDrawerIndex
                                rightDrawerOpen = false
                                if (chosen in session.sources.indices) {
                                    vm.selectSource(chosen)
                                }
                            }
                            wakeControls()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                // The country picker is driven by the remote itself, so it never
                // steals focus from channel zapping.
                if (countryPickerOpen) {
                    when (code) {
                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_INFO,
                        AndroidKeyEvent.KEYCODE_GUIDE,
                        -> countryPickerOpen = false

                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        -> countryIndex = (countryIndex - 1).coerceAtLeast(0)

                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        -> countryIndex = (countryIndex + 1).coerceAtMost(pickerCountries.lastIndex)

                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> selectCountry()
                    }
                    return@onPreviewKeyEvent true
                }

                if (osdMenuOpen) {
                    val hasSourcesAction = !isVod && session.sources.isNotEmpty()
                    val actionsCount = (if (hasSourcesAction) 1 else 0) + 2 // Sources?, Fav, Country
                    val colLimits = listOf(
                        3,
                        audioTracks.size.coerceAtLeast(1),
                        1 + subtitleTracks.size,
                        actionsCount,
                    )
                    when (code) {
                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_MENU -> {
                            osdMenuOpen = false
                            wakeControls()
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            osdRow = (osdRow - 1).coerceAtLeast(0)
                            osdCol = osdCol.coerceAtMost((colLimits.getOrNull(osdRow) ?: 1) - 1)
                            wakeControls()
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            osdRow = (osdRow + 1).coerceAtMost(colLimits.lastIndex)
                            osdCol = osdCol.coerceAtMost((colLimits.getOrNull(osdRow) ?: 1) - 1)
                            wakeControls()
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            osdCol = (osdCol - 1).coerceAtLeast(0)
                            wakeControls()
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val maxCols = colLimits.getOrNull(osdRow) ?: 1
                            osdCol = (osdCol + 1).coerceAtMost(maxCols - 1)
                            wakeControls()
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            when (osdRow) {
                                0 -> {
                                    val mode = when (osdCol) {
                                        1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    vm.setAspectRatioMode(mode)
                                }

                                1 -> {
                                    audioTracks.getOrNull(osdCol)?.let { applyAudioTrack(player, it) }
                                }

                                2 -> {
                                    if (osdCol == 0) {
                                        disableSubtitles(player)
                                    } else {
                                        subtitleTracks.getOrNull(osdCol - 1)?.let { applySubtitleTrack(player, it) }
                                    }
                                }

                                3 -> {
                                    var actionIdx = 0
                                    if (hasSourcesAction) {
                                        if (osdCol == actionIdx) {
                                            osdMenuOpen = false
                                            openRightDrawer()
                                            return@onPreviewKeyEvent true
                                        }
                                        actionIdx++
                                    }
                                    if (osdCol == actionIdx) {
                                        vm.toggleFavorite(channel)
                                    } else if (osdCol == actionIdx + 1) {
                                        osdMenuOpen = false
                                        openCountryPicker()
                                    }
                                }
                            }
                            if (osdMenuOpen) wakeControls()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (code == AndroidKeyEvent.KEYCODE_BACK) {
                    if (numberEntry.isNotEmpty()) {
                        cancelNumberEntry()
                        wakeControls()
                    } else {
                        vm.stopPlayback()
                    }
                    return@onPreviewKeyEvent true
                }

                if (isTimeshiftPaused) {
                    when (code) {
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                        AndroidKeyEvent.KEYCODE_SPACE,
                        AndroidKeyEvent.KEYCODE_PROG_BLUE,
                        -> resumeChannel()

                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> pauseOverlayIndex = 0
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> pauseOverlayIndex = 1
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            if (pauseOverlayIndex == 0) resumeChannel() else returnToLive()
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                        -> handleZapKey(1, repeated)

                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        -> handleZapKey(-1, repeated)
                    }
                    return@onPreviewKeyEvent true
                }

                if (
                    isVod && (
                        code == AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                            code == AndroidKeyEvent.KEYCODE_MEDIA_PAUSE ||
                            code == AndroidKeyEvent.KEYCODE_MEDIA_PLAY ||
                            code == AndroidKeyEvent.KEYCODE_SPACE ||
                            code == AndroidKeyEvent.KEYCODE_PROG_BLUE
                        )
                ) {
                    if (repeated) return@onPreviewKeyEvent true
                    pauseChannel()
                    return@onPreviewKeyEvent true
                }

                if (
                    isVod &&
                    (code == AndroidKeyEvent.KEYCODE_MEDIA_REWIND || code == AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
                ) {
                    seekVodBy(if (code == AndroidKeyEvent.KEYCODE_MEDIA_REWIND) -30_000L else 30_000L)
                    return@onPreviewKeyEvent true
                }

                if (
                    isTimeshiftActive &&
                    (code == AndroidKeyEvent.KEYCODE_MEDIA_REWIND || code == AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
                ) {
                    val deltaMs = if (code == AndroidKeyEvent.KEYCODE_MEDIA_REWIND) -15_000L else 15_000L
                    val maxSeek = timeshiftDurMs.coerceAtLeast(player.currentPosition)
                    val targetPos = (player.currentPosition + deltaMs).coerceIn(0L, maxSeek)
                    runCatching { player.seekTo(targetPos) }
                    timeshiftPosMs = targetPos
                    wakeControls()
                    return@onPreviewKeyEvent true
                }

                if (
                    code == AndroidKeyEvent.KEYCODE_INFO ||
                    code == AndroidKeyEvent.KEYCODE_GUIDE ||
                    code == AndroidKeyEvent.KEYCODE_PROG_RED
                ) {
                    if (session.isMixMode) {
                        vm.openMixSection()
                    } else {
                        openCountryPicker()
                    }
                    return@onPreviewKeyEvent true
                }

                if (
                    code == AndroidKeyEvent.KEYCODE_MENU ||
                    code == AndroidKeyEvent.KEYCODE_SETTINGS
                ) {
                    osdMenuOpen = true
                    osdRow = 0
                    osdCol = 0
                    wakeControls()
                    return@onPreviewKeyEvent true
                }

                if (code == AndroidKeyEvent.KEYCODE_PROG_YELLOW) {
                    osdMenuOpen = true
                    osdRow = 0
                    osdCol = 0
                    wakeControls()
                    return@onPreviewKeyEvent true
                }

                if (code == AndroidKeyEvent.KEYCODE_MEDIA_AUDIO_TRACK) {
                    osdMenuOpen = true
                    osdRow = 1
                    osdCol = 0
                    wakeControls()
                    return@onPreviewKeyEvent true
                }

                if (code == AndroidKeyEvent.KEYCODE_CAPTIONS) {
                    osdMenuOpen = true
                    osdRow = 2
                    osdCol = 0
                    wakeControls()
                    return@onPreviewKeyEvent true
                }

                if (code == AndroidKeyEvent.KEYCODE_PROG_GREEN) {
                    vm.toggleFavorite(channel)
                    wakeControls()
                    return@onPreviewKeyEvent true
                }

                val digit = when (code) {
                    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 ->
                        code - AndroidKeyEvent.KEYCODE_0
                    in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 ->
                        code - AndroidKeyEvent.KEYCODE_NUMPAD_0
                    else -> -1
                }
                if (digit >= 0) {
                    if (repeated) return@onPreviewKeyEvent true
                    return@onPreviewKeyEvent handleDigit(digit)
                }

                if (errorMessage != null) {
                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        -> {
                            errorMessage = null
                            buffering = true
                            vm.restartPlaybackSource()
                            wakeControls()
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                        -> handleZapKey(1, repeated)

                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        -> handleZapKey(-1, repeated)

                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (session.isMixMode) handleZapKey(1, repeated) else openRightDrawer()
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            openQuickDrawer()
                        }

                        else -> false
                    }
                } else when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                    -> {
                        if (isVod) {
                            seekVodBy(300_000L)
                            vm.showMessage(strings.t("+5 min · Scena successiva", "+5 min · Next scene"))
                            true
                        } else {
                            handleZapKey(1, repeated)
                        }
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    -> {
                        if (isVod) {
                            if (!openRightDrawer()) wakeControls()
                            true
                        } else {
                            handleZapKey(-1, repeated)
                        }
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> {
                        if (numberEntry.isNotEmpty()) {
                            confirmNumberEntry()
                        } else if (!repeated) {
                            if (controlsVisible) {
                                pauseChannel()
                            } else {
                                wakeControls()
                            }
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isVod) {
                            seekVodBy(if (repeated) -30_000L else -10_000L)
                            true
                        } else if (!openQuickDrawer()) {
                            wakeControls()
                            true
                        } else {
                            true
                        }
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isVod) {
                            seekVodBy(if (repeated) 30_000L else 10_000L)
                            true
                        } else if (session.isMixMode) {
                            handleZapKey(1, repeated)
                        } else {
                            if (!openRightDrawer()) wakeControls()
                            true
                        }
                    }

                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    keepScreenOn = false
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.resizeMode = aspectMode
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = aspectMode
                view.keepScreenOn = keepTvAwake
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = confirmedAudioOnly && !buffering && !failingOver && !reconnecting && errorMessage == null,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            AudioOnlyLogoStage(
                channel = channel,
                countryDisplay = channelCountryName?.takeIf { it.isNotBlank() },
            )
        }

        if (buffering && errorMessage == null && !failingOver && !reconnecting) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(38.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = strings.t("Sintonizzazione…", "Tuning…", "Sintonizando…"),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (reconnecting && errorMessage == null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xE60A0A0A))
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = strings.t("Riconnessione in corso…", "Reconnecting…", "Reconectando…"),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Minimal REC indicator when channel is being recorded for 7 days
        activeLiveRecording?.takeIf { it.status == "recording" }?.let { rec ->
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 22.dp, start = 28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC000000))
                    .border(1.dp, Color(0xFFE50914), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE50914)))
                Text(
                    text = "REC · ${formatDurationHms(rec.recordedMs)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && errorMessage == null && !osdMenuOpen && !quickDrawerOpen && !rightDrawerOpen && !isTimeshiftPaused && pendingRecording == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlayerTopOverlay(
                channel = channel,
                countryCode = channelCountryCode ?: channel.country,
                countryName = channelCountryName,
                clockTick = clockTick,
                syncState = syncState,
                inSync = inSyncByDelta,
                isMixMode = session.isMixMode,
                mixPosition = session.index + 1,
                mixTotal = session.queue.size,
                isServerRelay = activeRelayKey != null,
                autonomousDirect = autonomousDirectMode,
                inTimeshiftPlayback = timeshiftStage == TimeshiftStage.PLAYING_TIMESHIFT ||
                    timeshiftStage == TimeshiftStage.PLAYING_LIMIT_REACHED,
                isVod = isVod,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && errorMessage == null && !osdMenuOpen && !quickDrawerOpen && !rightDrawerOpen && !isTimeshiftPaused && pendingRecording == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerBottomStrip(
                channel = channel,
                isMixMode = session.isMixMode,
                timeshiftStage = timeshiftStage,
                timeshiftPosMs = timeshiftPosMs,
                timeshiftDurMs = timeshiftDurMs,
                isVod = isVod,
                isPlaying = isPlayingState,
                vodPosMs = vodPosMs,
                vodDurMs = vodDurMs,
            )
        }

        AnimatedVisibility(
            visible = quickDrawerOpen && session.queue.isNotEmpty(),
            enter = fadeIn(tween(220)) + slideInHorizontally { -it },
            exit = fadeOut(tween(180)) + slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            QuickChannelDrawer(
                queue = session.queue,
                selectedIndex = quickDrawerIndex,
                playingChannelId = channel.id,
                isMixMode = session.isMixMode,
                onSelectChannel = { chosenIdx ->
                    quickDrawerOpen = false
                    vm.playChannelAtQueueIndex(chosenIdx)
                    wakeControls()
                },
            )
        }

        AnimatedVisibility(
            visible = rightDrawerOpen && (if (isVod) vodRelatedItems.isNotEmpty() else session.sources.isNotEmpty()),
            enter = fadeIn(tween(220)) + slideInHorizontally { it },
            exit = fadeOut(tween(180)) + slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            if (isVod) {
                QuickRelatedDrawer(
                    mode = vodRelatedMode,
                    items = vodRelatedItems,
                    selectedIndex = rightDrawerIndex,
                    playingChannelId = channel.id,
                    onSelectItem = { chosen ->
                        rightDrawerOpen = false
                        vm.startPlayback(chosen, vodRelatedItems)
                        wakeControls()
                    },
                )
            } else {
                QuickSourcesDrawer(
                    channelName = channel.name,
                    sources = session.sources,
                    selectedIndex = rightDrawerIndex,
                    activeSourceIndex = session.sourceIndex,
                    onSelectSource = { idx ->
                        rightDrawerOpen = false
                        vm.selectSource(idx)
                        wakeControls()
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = osdMenuOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerOsdMenu(
                selectedRow = osdRow,
                selectedCol = osdCol,
                currentAspectMode = aspectMode,
                onSelectAspectMode = { vm.setAspectRatioMode(it) },
                audioTracks = audioTracks,
                onSelectAudioTrack = { applyAudioTrack(player, it) },
                subtitleTracks = subtitleTracks,
                subtitlesDisabled = subtitlesDisabled,
                onDisableSubtitles = { disableSubtitles(player) },
                onSelectSubtitleTrack = { applySubtitleTrack(player, it) },
                isFavorite = channel.favorite,
                onToggleFavorite = { vm.toggleFavorite(channel) },
                onOpenCountryPicker = {
                    osdMenuOpen = false
                    openCountryPicker()
                },
                hasSources = !isVod && session.sources.isNotEmpty(),
                onOpenSources = {
                    osdMenuOpen = false
                    openRightDrawer()
                },
                streamDetails = streamDetails,
                onClose = {
                    osdMenuOpen = false
                    wakeControls()
                },
            )
        }

        // 1-Minute Counterclockwise Circular Next-Episode Countdown (bottom-right)
        AnimatedVisibility(
            visible = nextEpPromptVisible && nextEpisodeCandidate != null && !quickDrawerOpen && !rightDrawerOpen && errorMessage == null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 36.dp),
        ) {
            nextEpisodeCandidate?.let { nextEp ->
                val secInt = kotlin.math.ceil(nextEpRemainingMs / 1000.0).toInt().coerceIn(0, 60)
                val sweepFrac = (nextEpRemainingMs / 60_000f).coerceIn(0f, 1f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            nextEpLaunched = true
                            nextEpPromptVisible = false
                            vm.startPlayback(nextEp, vodRelatedItems)
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(Color(0xB8000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (secInt > 0 && sweepFrac > 0.005f) {
                            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                drawArc(
                                    color = Color.White,
                                    startAngle = -90f,
                                    sweepAngle = 360f * sweepFrac,
                                    useCenter = false,
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Butt),
                                )
                            }
                        }
                        Text(
                            text = secInt.toString(),
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = strings.t(
                                "Premi OK per il prossimo episodio",
                                "Press OK for next episode",
                                "Pulsa OK para el siguiente episodio",
                            ),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Centered dimmed 3-second hold OK recording prompt (>1s held)
        if (okHoldActive && okHoldElapsedMs >= 1000L && !isVod) {
            val isStopping = activeLiveRecording?.let {
                it.channelId == channel.id && it.status == "recording"
            } == true
            val secsLeft = kotlin.math.ceil((3000L - okHoldElapsedMs).coerceAtLeast(0L) / 1000.0).toInt().coerceIn(1, 2)
            val progress = ((okHoldElapsedMs - 1000L) / 2000f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 48.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            drawArc(
                                color = Color(0xFFE50914),
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Butt),
                            )
                        }
                        Text(
                            text = "$secsLeft",
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        text = if (isStopping) {
                            strings.t(
                                "Interruzione registrazione tra $secsLeft s",
                                "Stopping recording in ${secsLeft}s",
                                "Deteniendo grabación en ${secsLeft}s",
                            )
                        } else {
                            strings.t(
                                "Avvio registrazione del canale tra $secsLeft s",
                                "Starting channel recording in ${secsLeft}s",
                                "Iniciando grabación del canal en ${secsLeft}s",
                            )
                        },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (isStopping) {
                            strings.t(
                                "Continua a tenere premuto OK per fermare e salvare · Rilascia per continuare",
                                "Keep holding OK to stop & save · Release to continue",
                                "Mantén OK para detener y guardar · Suelta para continuar",
                            )
                        } else {
                            strings.t(
                                "Tieni premuto OK per registrare e salvare 7 giorni · Rilascia per annullare",
                                "Keep holding OK to record for 7 days · Release to cancel",
                                "Mantén OK para grabar 7 días · Suelta para cancelar",
                            )
                        },
                        color = Color(0xCCFFFFFF),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (isTimeshiftPaused && pendingRecording == null && errorMessage == null) {
            TimeshiftPauseOverlay(
                channel = channel,
                stage = timeshiftStage,
                session = timeshiftSession,
                recordedMs = timeshiftDurMs,
                selectedIndex = pauseOverlayIndex,
                onResume = { resumeChannel() },
                onReturnLive = { returnToLive() },
            )
        }

        pendingRecording?.let { pending ->
            TimeshiftPendingPromptOverlay(
                channel = channel,
                session = pending,
                selectedIndex = pendingPromptIndex,
                onWatchRecording = { acceptPendingRecording() },
                onGoLive = { declinePendingRecording() },
            )
        }

        if (numberEntry.isNotEmpty()) {
            NumberEntryOverlay(numberEntry)
        }

        if (errorMessage != null) {
            PlaybackErrorScreen(
                channel = channel,
                message = errorMessage.orEmpty(),
                country = activeCountry,
                countryCode = activeCountryCode,
            )
        }

        if (countryPickerOpen) {
            CountrySelectorOverlay(
                countries = pickerCountries,
                selectedIndex = countryIndex,
            )
        }

        if (showMixRegionPicker) {
            TvMixRegionPickerHost(vm)
        }
    }
}

@Composable
private fun QuickSourcesDrawer(
    channelName: String,
    sources: List<StreamSource>,
    selectedIndex: Int,
    activeSourceIndex: Int,
    onSelectSource: (Int) -> Unit,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    val drawerCardShape = remember { RoundedCornerShape(6.dp) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (sources.isNotEmpty() && selectedIndex in sources.indices) {
            val targetScroll = (selectedIndex - 2).coerceAtLeast(0)
            runCatching { listState.scrollToItem(targetScroll) }
        }
    }

    LaunchedEffect(selectedIndex, sources.size) {
        if (sources.isEmpty() || selectedIndex !in sources.indices) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        if (itemInfo == null) {
            val targetScroll = (selectedIndex - 2).coerceAtLeast(0)
            runCatching { listState.scrollToItem(targetScroll) }
            return@LaunchedEffect
        }
        val marginPx = (itemInfo.size * 0.45f).toInt()
        val minVisibleTop = layoutInfo.viewportStartOffset + marginPx
        val maxVisibleBottom = layoutInfo.viewportEndOffset - marginPx
        val delta = when {
            itemInfo.offset < minVisibleTop -> (itemInfo.offset - minVisibleTop).toFloat()
            itemInfo.offset + itemInfo.size > maxVisibleBottom ->
                (itemInfo.offset + itemInfo.size - maxVisibleBottom).toFloat()
            else -> 0f
        }
        if (delta != 0f) {
            runCatching {
                if (isLowRam) listState.scrollBy(delta)
                else listState.animateScrollBy(delta, tween(durationMillis = 120))
            }
        }
    }

    Column(
        modifier = Modifier
            .width(370.dp)
            .fillMaxHeight()
            .background(Color(0xF50A0A0A))
            .border(1.dp, Color(0xFF2B2B2B))
            .padding(top = 20.dp, bottom = 18.dp, start = 16.dp, end = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = strings.t("SORGENTI CANALE", "CHANNEL SOURCES", "FUENTES DEL CANAL"),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = "${(selectedIndex + 1).coerceAtMost(sources.size)} / ${sources.size}",
                color = Color(0xFFA3A3A3),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = strings.t(
                "SU / GIÙ sfoglia · OK cambia sorgente · SX chiudi",
                "UP / DOWN browse · OK switch source · LEFT close",
                "ARRIBA / ABAJO explorar · OK cambiar · IZQ cerrar",
            ),
            color = Color(0xFFA3A3A3),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(sources, key = { idx, src -> "rsrc_${idx}_${src.id}" }) { index, src ->
                val isCursor = index == selectedIndex
                val isActive = index == activeSourceIndex
                val cardBg = if (isCursor) Color(0xFF222222) else Color(0xFF121212)
                val cardBorder = when {
                    isCursor -> Color.White
                    isActive -> Color(0xFF737373)
                    else -> Color(0xFF2B2B2B)
                }
                val host = src.host.ifBlank {
                    runCatching { java.net.URI(src.url).host }.getOrNull().orEmpty()
                }
                val proto = src.protocol.ifBlank {
                    if (src.url.startsWith("https://", ignoreCase = true)) "HTTPS" else "HTTP"
                }
                val title = buildString {
                    append(src.name.ifBlank { channelName })
                    if (src.recommended) append(" · ${strings.t("Consigliata", "Recommended", "Recomendada")}")
                }
                val subtitle = listOfNotNull(
                    if (isActive) strings.t("ATTIVA", "ACTIVE", "ACTIVA") else null,
                    src.quality.ifBlank { "HD" },
                    proto.takeIf { it.isNotBlank() },
                    host.takeIf { it.isNotBlank() },
                    src.latencyMs?.let { "${it} ms" },
                ).joinToString(" · ")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(drawerCardShape)
                        .background(cardBg)
                        .border(if (isCursor) 2.5.dp else 1.dp, cardBorder, drawerCardShape)
                        .clickable { onSelectSource(index) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (isCursor || isActive) Color.White else Color(0xFFA3A3A3),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isCursor || isActive) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = subtitle,
                            color = if (isActive) Color.White else Color(0xFFA3A3A3),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickRelatedDrawer(
    mode: String,
    items: List<Channel>,
    selectedIndex: Int,
    playingChannelId: String,
    onSelectItem: (Channel) -> Unit,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    val drawerCardShape = remember { RoundedCornerShape(14.dp) }
    val drawerThumbShape = remember { RoundedCornerShape(9.dp) }
    val drawerNumShape = remember { RoundedCornerShape(4.dp) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (items.isNotEmpty() && selectedIndex in items.indices) {
            val targetScroll = (selectedIndex - 2).coerceAtLeast(0)
            runCatching { listState.scrollToItem(targetScroll) }
        }
    }

    LaunchedEffect(selectedIndex, items.size) {
        if (items.isEmpty() || selectedIndex !in items.indices) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        if (itemInfo == null) {
            val targetScroll = (selectedIndex - 2).coerceAtLeast(0)
            runCatching { listState.scrollToItem(targetScroll) }
            return@LaunchedEffect
        }
        val marginPx = (itemInfo.size * 0.45f).toInt()
        val minVisibleTop = layoutInfo.viewportStartOffset + marginPx
        val maxVisibleBottom = layoutInfo.viewportEndOffset - marginPx
        val delta = when {
            itemInfo.offset < minVisibleTop -> (itemInfo.offset - minVisibleTop).toFloat()
            itemInfo.offset + itemInfo.size > maxVisibleBottom ->
                (itemInfo.offset + itemInfo.size - maxVisibleBottom).toFloat()
            else -> 0f
        }
        if (delta != 0f) {
            runCatching {
                if (isLowRam) listState.scrollBy(delta)
                else listState.animateScrollBy(delta, tween(durationMillis = 120))
            }
        }
    }

    Column(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .background(Color(0xF50A0A0A))
            .border(1.dp, Color(0xFF2B2B2B))
            .padding(top = 20.dp, bottom = 18.dp, start = 14.dp, end = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = when (mode) {
                    "episodes" -> strings.t("EPISODI", "EPISODES", "EPISODIOS")
                    "similar" -> strings.t("FILM SIMILI", "SIMILAR MOVIES", "PELÍCULAS SIMILARES")
                    else -> strings.t("ALTRI CANALI", "OTHER CHANNELS", "OTROS CANALES")
                },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = "${(selectedIndex + 1).coerceAtMost(items.size)} / ${items.size}",
                color = Color(0xCCEBEBF5),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = strings.t(
                "SU / GIÙ sfoglia · OK sintonizza · SX chiudi",
                "UP / DOWN browse · OK tune · LEFT close",
                "ARRIBA / ABAJO explorar · OK sintonizar · IZQ cerrar",
            ),
            color = Color(0x99EBEBF5),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items,
                key = { idx, item -> "rd_${idx}_${item.id}" },
                contentType = { _, _ -> "drawer_channel" },
            ) { index, item ->
                val isCursor = index == selectedIndex
                val isPlaying = item.id == playingChannelId
                val cardBg = if (isCursor) Color(0xFF222222) else Color(0xFF121212)
                val cardBorder = if (isCursor) Color.White else Color(0xFF2B2B2B)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(cardBg)
                        .border(if (isCursor) 2.dp else 1.dp, cardBorder, RoundedCornerShape(6.dp))
                        .clickable { onSelectItem(item) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(86.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0A0A0A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChannelLogo(
                            channel = item,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                        )
                        if (item.number != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .clip(drawerNumShape)
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = item.number.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isCursor || isPlaying) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        val subtitle = when {
                            isPlaying -> strings.t("In riproduzione", "Now playing", "Reproduciendo")
                            item.now != null -> item.now.title
                            else -> listOfNotNull(
                                item.categoryNames.firstOrNull() ?: item.countryName.takeIf { it.isNotBlank() },
                                item.quality.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (resolveFlagAssetCode(item.country) != null) {
                                CountryFlagIcon(
                                    countryCode = item.country,
                                    width = 14.dp,
                                    height = 10.dp,
                                    cornerRadius = 1.5.dp,
                                )
                            }
                            Text(
                                text = subtitle,
                                color = if (isPlaying) Color.White else Color(0xFFA3A3A3),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChannelDrawer(
    queue: List<Channel>,
    selectedIndex: Int,
    playingChannelId: String,
    isMixMode: Boolean,
    onSelectChannel: (Int) -> Unit,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    val drawerCardShape = remember { RoundedCornerShape(14.dp) }
    val drawerThumbShape = remember { RoundedCornerShape(9.dp) }
    val drawerNumShape = remember { RoundedCornerShape(4.dp) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (queue.isNotEmpty() && selectedIndex in queue.indices) {
            val targetScroll = (selectedIndex - 2).coerceAtLeast(0)
            runCatching { listState.scrollToItem(targetScroll) }
        }
    }

    LaunchedEffect(selectedIndex, queue.size) {
        if (queue.isEmpty() || selectedIndex !in queue.indices) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        if (itemInfo == null) {
            val targetScroll = (selectedIndex - 2).coerceAtLeast(0)
            runCatching { listState.scrollToItem(targetScroll) }
            return@LaunchedEffect
        }
        val marginPx = (itemInfo.size * 0.45f).toInt()
        val minVisibleTop = layoutInfo.viewportStartOffset + marginPx
        val maxVisibleBottom = layoutInfo.viewportEndOffset - marginPx
        val delta = when {
            itemInfo.offset < minVisibleTop -> (itemInfo.offset - minVisibleTop).toFloat()
            itemInfo.offset + itemInfo.size > maxVisibleBottom ->
                (itemInfo.offset + itemInfo.size - maxVisibleBottom).toFloat()
            else -> 0f
        }
        if (delta != 0f) {
            runCatching {
                if (isLowRam) {
                    listState.scrollBy(delta)
                } else {
                    listState.animateScrollBy(delta, tween(durationMillis = 120))
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .width(380.dp)
            .fillMaxHeight()
            .background(Color(0xF50A0A0A))
            .border(1.dp, Color(0xFF2B2B2B))
            .padding(top = 20.dp, bottom = 18.dp, start = 16.dp, end = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (isMixMode) {
                    strings.t("MIX · VISITATI", "MIX · HISTORY", "MIX · HISTORIAL")
                } else {
                    strings.t("CANALI RAPIDI", "QUICK CHANNELS", "CANALES RÁPIDOS")
                },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = "${(selectedIndex + 1).coerceAtMost(queue.size)} / ${queue.size}",
                color = Color(0xFFA3A3A3),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = strings.t(
                "SU / GIÙ sfoglia · OK sintonizza · DX chiudi",
                "UP / DOWN browse · OK tune · RIGHT close",
                "ARRIBA / ABAJO explorar · OK sintonizar · DER cerrar",
            ),
            color = Color(0xFFA3A3A3),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                queue,
                key = { idx, item -> "qd_${idx}_${item.id}" },
                contentType = { _, _ -> "drawer_channel" },
            ) { index, item ->
                val isCursor = index == selectedIndex
                val isPlaying = item.id == playingChannelId
                val cardBg = if (isCursor) Color(0xFF222222) else Color(0xFF121212)
                val cardBorder = if (isCursor) Color.White else Color(0xFF2B2B2B)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(cardBg)
                        .border(if (isCursor) 2.dp else 1.dp, cardBorder, RoundedCornerShape(6.dp))
                        .clickable { onSelectChannel(index) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0A0A0A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChannelLogo(
                            channel = item,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                        )
                        if (item.number != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .clip(drawerNumShape)
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = item.number.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isCursor || isPlaying) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (item.favorite) {
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = CasaGreen,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        val subtitle = when {
                            isPlaying -> strings.t("In onda ora", "Now playing", "En emisión")
                            item.now != null -> item.now.title
                            else -> listOfNotNull(
                                item.categoryNames.firstOrNull() ?: item.countryName.takeIf { it.isNotBlank() },
                                item.quality.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (resolveFlagAssetCode(item.country) != null) {
                                CountryFlagIcon(
                                    countryCode = item.country,
                                    width = 14.dp,
                                    height = 10.dp,
                                    cornerRadius = 1.5.dp,
                                )
                            }
                            Text(
                                text = subtitle,
                                color = if (isPlaying) CasaGreen else Color(0x99EBEBF5),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}




@Composable
private fun AudioOnlyLogoStage(
    channel: Channel,
    countryDisplay: String? = null,
) {
    val strings = LocalAppStrings.current
    val categoryLabel = channel.categoryNames.firstOrNull()
        ?: channel.categories.firstOrNull()?.replaceFirstChar { it.uppercase() }
    val subtitleParts = listOfNotNull(
        countryDisplay,
        categoryLabel?.takeIf { it.isNotBlank() && !it.equals(countryDisplay, ignoreCase = true) },
    ).joinToString(" · ")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 180.dp, height = 180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF111111))
                    .border(1.dp, Color(0xFF2B2B2B), RoundedCornerShape(8.dp))
                    .padding(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                ChannelLogo(
                    channel = channel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 680.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1A1A1A))
                        .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                    Text(
                        text = strings.t("SOLO AUDIO", "AUDIO ONLY", "SOLO AUDIO"),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
                if (subtitleParts.isNotBlank()) {
                    Text(
                        text = subtitleParts,
                        color = Color(0xFFA3A3A3),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTopOverlay(
    channel: Channel,
    countryCode: String?,
    countryName: String?,
    clockTick: Int,
    syncState: SyncStateResponse?,
    inSync: Boolean,
    isMixMode: Boolean,
    mixPosition: Int,
    mixTotal: Int,
    isServerRelay: Boolean,
    autonomousDirect: Boolean,
    inTimeshiftPlayback: Boolean,
    isVod: Boolean,
) {
    val strings = LocalAppStrings.current
    val tvLocalTime = remember(clockTick) { currentTimeText() }
    val countryLocalTime = remember(channel.country, clockTick) { countryTimeTextOrNull(channel.country) }
    val resolvedCountryCode = remember(countryCode, channel.country) { normalizeCountryCode(countryCode ?: channel.country) }
    val categoryLabel = channel.categoryNames.firstOrNull() ?: channel.categories.firstOrNull()
    val mainTitle = if (channel.contentType == "series" && !channel.seriesTitle.isNullOrBlank()) {
        channel.seriesTitle
    } else {
        channel.name
    }
    val subLine = if (channel.contentType == "series" && channel.seasonNumber != null && channel.episodeNumber != null) {
        val epT = channel.episodeTitle?.takeIf { it.isNotBlank() }?.let { " \"$it\"" }.orEmpty()
        "S${channel.seasonNumber}:E${channel.episodeNumber}$epT"
    } else {
        listOfNotNull(
            channel.number?.toString(),
            countryName?.takeIf { it.isNotBlank() },
            categoryLabel?.takeIf { it.isNotBlank() },
            channel.quality.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC03060D), Color(0x6603060D), Color.Transparent)))
            .padding(top = 22.dp, bottom = 26.dp, start = 36.dp, end = 36.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mainTitle,
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subLine.isNotBlank() || resolvedCountryCode != null) {
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (resolvedCountryCode != null && resolveFlagAssetCode(resolvedCountryCode) != null) {
                        CountryFlagIcon(
                            countryCode = resolvedCountryCode,
                            width = 18.dp,
                            height = 12.dp,
                            cornerRadius = 2.dp,
                        )
                    }
                    if (subLine.isNotBlank()) {
                        Text(
                            text = subLine,
                            color = Color(0xCCE2E8F0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isMixMode) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("MIX · $mixPosition/$mixTotal", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
            if (autonomousDirect) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x33F59E0B))
                        .border(1.dp, Color(0x99F59E0B), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(strings.t("AUTONOMO", "DIRECT", "DIRECTO"), color = Color(0xFFFCD34D), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else if (isServerRelay && !inTimeshiftPlayback && !isVod) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC111111))
                        .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("SERVER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (syncState?.syncActive == true && !inTimeshiftPlayback && !isVod) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (inSync) Color.White else Color(0xFFF59E0B)),
                )
            }
            if (countryLocalTime != null) {
                if (resolvedCountryCode != null && resolveFlagAssetCode(resolvedCountryCode) != null) {
                    CountryFlagIcon(
                        countryCode = resolvedCountryCode,
                        width = 16.dp,
                        height = 11.dp,
                        cornerRadius = 2.dp,
                    )
                }
                Text(
                    text = countryLocalTime,
                    color = Color(0xFFD4D4D8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text("·", color = Color(0x66FFFFFF), fontSize = 12.sp)
            }
            Text(
                text = tvLocalTime,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun PlayerBottomStrip(
    channel: Channel,
    isMixMode: Boolean = false,
    timeshiftStage: TimeshiftStage = TimeshiftStage.LIVE,
    timeshiftPosMs: Long = 0L,
    timeshiftDurMs: Long = 0L,
    isVod: Boolean = false,
    isPlaying: Boolean = true,
    vodPosMs: Long = 0L,
    vodDurMs: Long = 0L,
) {
    val strings = LocalAppStrings.current
    val inTimeshiftPlayback = timeshiftStage == TimeshiftStage.PLAYING_TIMESHIFT ||
        timeshiftStage == TimeshiftStage.PLAYING_LIMIT_REACHED

    val effectiveDur = when {
        isVod -> vodDurMs.coerceAtLeast(vodPosMs).coerceAtLeast(1000L)
        inTimeshiftPlayback -> timeshiftDurMs.coerceAtLeast(timeshiftPosMs).coerceAtLeast(1000L)
        else -> 1L
    }
    val progressFraction = when {
        isVod -> (vodPosMs.toFloat() / effectiveDur.toFloat()).coerceIn(0f, 1f)
        inTimeshiftPlayback -> (timeshiftPosMs.toFloat() / effectiveDur.toFloat()).coerceIn(0f, 1f)
        else -> 1f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x9903060D), Color(0xE803060D))))
            .padding(top = 28.dp, bottom = 18.dp, start = 36.dp, end = 36.dp),
    ) {
        // Minimal Slim Scrubber Track + Right Time Label
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x40FFFFFF)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White),
                )
            }
            if (!isVod && !inTimeshiftPlayback) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE50914)),
                    )
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                Text(
                    text = when {
                        isVod && vodDurMs > 0L -> "${formatDurationHms(vodPosMs)} / ${formatDurationHms(vodDurMs)}"
                        isVod -> formatDurationHms(vodPosMs)
                        else -> "${formatDurationHms(timeshiftPosMs)} / ${formatDurationHms(effectiveDur)}"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Clean Single Bottom Control Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (isPlaying) CasaIcons.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                if (channel.favorite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
                val bottomName = if (channel.contentType == "series" && channel.seasonNumber != null && channel.episodeNumber != null) {
                    "${channel.seriesTitle ?: channel.name} · S${channel.seasonNumber}:E${channel.episodeNumber}"
                } else {
                    channel.name
                }
                Text(
                    text = bottomName,
                    color = Color(0xE6FFFFFF),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(16.dp))

            Text(
                text = when {
                    isVod -> strings.t(
                        "SX / DX ±10s · SU +5m · GIÙ Episodi/Simili · MENU Audio/Sub",
                        "LEFT / RIGHT ±10s · UP +5m · DOWN Episodes/Similar · MENU Audio/Sub",
                    )
                    inTimeshiftPlayback -> strings.t(
                        "SX / DX ±15s · OK Pausa · SU / GIÙ Live/Canali · MENU Opzioni",
                        "LEFT / RIGHT ±15s · OK Pause · UP / DOWN Live/Channels · MENU Options",
                    )
                    isMixMode -> strings.t(
                        "SX Lista · DX Prossimo Mix · SU / GIÙ Mix · MENU Opzioni",
                        "LEFT List · RIGHT Next Mix · UP / DOWN Mix · MENU Options",
                    )
                    else -> strings.t(
                        "SX Lista · DX Sorgenti · Tieni OK 3s Registra · MENU Audio/Sub",
                        "LEFT List · RIGHT Sources · Hold OK 3s Record · MENU Audio/Sub",
                        "IZQ Lista · DER Fuentes · Mantén OK 3s Grabar · MENU Audio/Sub",
                    )
                },
                color = Color(0xFFD4D4D8),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TimeshiftPauseOverlay(
    channel: Channel,
    stage: TimeshiftStage,
    session: TimeshiftSessionDto?,
    recordedMs: Long,
    selectedIndex: Int,
    onResume: () -> Unit,
    onReturnLive: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val isStarting = stage == TimeshiftStage.STARTING
    val isLimit = stage == TimeshiftStage.PAUSED_LIMIT_REACHED || session?.status == "limit_reached"
    val maxHours = session?.maxHours ?: 2
    val sizeMb = ((session?.bytes ?: 0L).toDouble() / (1024.0 * 1024.0))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 440.dp, max = 600.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xF5000000))
                .border(1.5.dp, if (isLimit) CasaAmber else Color(0xFF2E2E2E), RoundedCornerShape(12.dp))
                .padding(horizontal = 32.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isStarting -> Color.White
                                isLimit -> CasaAmber
                                else -> Color(0xFFEF4444)
                            },
                        ),
                )
                Text(
                    text = when {
                        isStarting -> strings.t("AVVIO REGISTRAZIONE SUL SERVER…", "STARTING SERVER RECORDING…", "INICIANDO GRABACIÓN…")
                        isLimit -> strings.t(
                            "PAUSA · LIMITE DI $maxHours ORE RAGGIUNTO",
                            "PAUSED · $maxHours HOUR LIMIT REACHED",
                            "EN PAUSA · LÍMITE DE $maxHours HORAS",
                        )
                        else -> strings.t("CANALE IN PAUSA · REGISTRAZIONE SUL SERVER", "CHANNEL PAUSED · RECORDING ON SERVER", "CANAL EN PAUSA · GRABANDO EN SERVIDOR")
                    },
                    color = if (isLimit) CasaAmber else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    isStarting -> strings.t(
                        "Preparazione buffer sul server centrale… La TV resterà accesa.",
                        "Preparing buffer on the central server… Your TV will stay awake.",
                        "Preparando grabación en el servidor central…",
                    )
                    isLimit -> strings.t(
                        "Registrazione messa in pausa sul server (${formatDurationHms(recordedMs)} · ${String.format(java.util.Locale.US, "%.1f", sizeMb)} MB).",
                        "Recording paused at server limit (${formatDurationHms(recordedMs)} · ${String.format(java.util.Locale.US, "%.1f", sizeMb)} MB).",
                        "Grabación pausada al llegar al límite (${formatDurationHms(recordedMs)}).",
                    )
                    else -> strings.t(
                        "Registrato: ${formatDurationHms(recordedMs)} · Max $maxHours ${if (maxHours == 1) "ora" else "ore"} · TV sempre attiva",
                        "Recorded: ${formatDurationHms(recordedMs)} · Max ${maxHours}h · TV awake",
                        "Grabado: ${formatDurationHms(recordedMs)} · Máx ${maxHours}h · TV encendida",
                    )
                },
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            if (!isStarting) {
                Spacer(Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeshiftDialogButton(
                        text = strings.t("Riprendi da qui", "Resume playback", "Reanudar aquí"),
                        focused = selectedIndex == 0,
                        primaryTint = CasaGreen,
                        onClick = onResume,
                    )
                    TimeshiftDialogButton(
                        text = strings.t("Torna alla diretta", "Return to live", "Volver al directo"),
                        focused = selectedIndex == 1,
                        primaryTint = Color.White,
                        onClick = onReturnLive,
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(
                    color = CasaGreen,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun TimeshiftPendingPromptOverlay(
    channel: Channel,
    session: TimeshiftSessionDto,
    selectedIndex: Int,
    onWatchRecording: () -> Unit,
    onGoLive: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val sizeMb = (session.bytes.toDouble() / (1024.0 * 1024.0))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9030712)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 460.dp, max = 640.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF808111E))
                .border(1.5.dp, CasaAmber, RoundedCornerShape(24.dp))
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings.t("REGISTRAZIONE DISPONIBILE SUL SERVER", "SAVED RECORDING AVAILABLE", "GRABACIÓN DISPONIBLE EN EL SERVIDOR"),
                color = CasaAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.t(
                    "La registrazione precedente ha raggiunto il limite (${formatDurationHms(session.recordedMs)} · ${String.format(java.util.Locale.US, "%.1f", sizeMb)} MB). Vuoi vederla o preferisci la diretta (cancellando la registrazione)?",
                    "A previous pause recording reached the limit (${formatDurationHms(session.recordedMs)} · ${String.format(java.util.Locale.US, "%.1f", sizeMb)} MB). Watch it or go live and delete it?",
                    "Una grabación previa alcanzó el límite (${formatDurationHms(session.recordedMs)}). ¿Quieres verla o volver al directo (y borrarla)?",
                ),
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeshiftDialogButton(
                    text = strings.t("Guarda registrazione", "Watch recording", "Ver grabación"),
                    focused = selectedIndex == 0,
                    primaryTint = CasaGreen,
                    onClick = onWatchRecording,
                )
                TimeshiftDialogButton(
                    text = strings.t("Diretta (Cancella)", "Go live (Delete)", "Directo (Borrar)"),
                    focused = selectedIndex == 1,
                    primaryTint = Color(0xFFEF4444),
                    onClick = onGoLive,
                )
            }
        }
    }
}

@Composable
private fun TimeshiftDialogButton(
    text: String,
    focused: Boolean,
    primaryTint: Color,
    onClick: () -> Unit,
) {
    val bg = if (focused) primaryTint else Color(0x18FFFFFF)
    val fg = if (focused && (primaryTint == CasaGreen || primaryTint == CasaAmber)) Color(0xFF04101A) else Color.White
    val border = if (focused) Color.White else Color(0x33FFFFFF)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun formatDurationHms(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }
}


@Composable
private fun Hint(key: String, action: String, isLight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isLight) Color(0x14000000) else Color(0x1FFFFFFF))
                .border(1.dp, CasaBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = key,
                color = if (isLight) CasaText else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = action,
            color = if (isLight) CasaMuted else Color(0xFFA3A3A3),
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun NumberEntryOverlay(entry: String) {
    val strings = LocalAppStrings.current
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isLight) Color(0xF5FFFFFF) else Color(0xF20A0A0A))
                .border(1.dp, CasaBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings.t("VAI AL CANALE", "GO TO CHANNEL", "IR AL CANAL"),
                color = CasaMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry,
                color = CasaText,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.t(
                    "Attendi o premi OK per confermare",
                    "Wait or press OK to confirm",
                    "Espera o pulsa OK para confirmar",
                ),
                color = CasaMuted,
                fontSize = 11.sp,
            )
        }
    }
}


/**
 * TV-mode error state: a non-blocking screen styled according to theme. Zapping, MENU,
 * INFO and BACK keep working because the player owns the key handling.
 */
@Composable
private fun PlaybackErrorScreen(
    channel: Channel,
    message: String,
    country: Facet?,
    countryCode: String,
) {
    val strings = LocalAppStrings.current
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(it.casatv.tv.ui.theme.CasaBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isLight) Color(0xF5FFFFFF) else Color(0xFF0E0E0E))
                .border(1.dp, CasaBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 48.dp, vertical = 34.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = if (isLight) it.casatv.tv.ui.theme.CasaRed else CasaAmber.copy(alpha = 0.9f),
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = strings.t("Canale non disponibile", "Channel unavailable", "Canal no disponible"),
                color = CasaText,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = channel.name,
                color = CasaText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = CasaMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = strings.t(
                    "SU / GIÙ  cambia canale      OK  riprova      BACK  esci",
                    "UP / DOWN  change channel      OK  retry      BACK  exit",
                    "ARRIBA / ABAJO  cambiar canal      OK  reintentar      BACK  salir",
                ),
                color = CasaDim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = strings.t(
                    "INFO  cambia paese TV · ora: ${country?.name ?: countryCode}",
                    "INFO  change TV country · now: ${country?.name ?: countryCode}",
                    "INFO  cambiar país TV · ahora: ${country?.name ?: countryCode}",
                ),
                color = CasaMuted,
                fontSize = 10.sp,
            )
        }
    }
}


/**
 * ExoPlayer crashes or throws on a few malformed playlist URLs. Accept only
 * absolute http(s) streams; everything else becomes the normal error overlay.
 */
private fun playableStreamUrl(raw: String): String? {
    val value = raw.trim()
    if (value.isBlank()) return null
    return runCatching {
        val uri = android.net.Uri.parse(value)
        when (uri.scheme?.lowercase()) {
            "http", "https", "rtsp", "rtmp", "rtmps" -> value
            else -> null
        }
    }.getOrNull()
}

@Composable
private fun CountrySelectorOverlay(
    countries: List<Facet>,
    selectedIndex: Int,
) {
    val strings = LocalAppStrings.current
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val start = (selectedIndex - 3).coerceAtLeast(0)
    val end = (selectedIndex + 4).coerceAtMost(countries.size)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isLight) Color(0xF5FFFFFF) else Color(0xF50A0A0A))
                .border(1.dp, CasaBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 30.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings.t("PAESE DELLA MODALITÀ TV", "TV MODE COUNTRY", "PAÍS DEL MODO TV"),
                color = CasaText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.t(
                    "I canali SU / GIÙ partiranno da questo paese",
                    "UP / DOWN zapping starts from this country",
                    "El zapping ARRIBA / ABAJO empezará desde este país",
                ),
                color = CasaMuted,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(14.dp))
            val focusBg = if (isLight) Color.Black else Color.White
            val focusFg = if (isLight) Color.White else Color.Black
            for (index in start until end) {
                val country = countries[index]
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .width(330.dp)
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) focusBg else Color.Transparent)
                        .border(
                            if (selected) 2.dp else 0.dp,
                            if (selected) focusBg else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CountryFlagIcon(
                        countryCode = country.code,
                        width = 22.dp,
                        height = 15.dp,
                        cornerRadius = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = country.name,
                        color = if (selected) focusFg else CasaText,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.t(
                    "SU / GIÙ scegli · OK conferma · BACK annulla",
                    "UP / DOWN choose · OK confirm · BACK cancel",
                    "ARRIBA / ABAJO elegir · OK confirmar · BACK cancelar",
                ),
                color = if (isLight) CasaDim else Color(0x99EBEBF5),
                fontSize = 10.sp,
            )
        }
    }
}

private data class HlsSegmentProgress(
    val segmentSeq: Long,
    val offsetMs: Long,
    val durationMs: Long,
)

private fun getHlsSegmentProgress(player: Player): HlsSegmentProgress? {
    val manifest = player.currentManifest as? HlsManifest ?: return null
    val playlist = manifest.mediaPlaylist
    val currentPosUs = player.currentPosition * 1000L
    val seq = playlist.mediaSequence
    val segments = playlist.segments
    if (segments.isEmpty()) return null

    val segIdx = segments.indexOfLast { it.relativeStartTimeUs <= currentPosUs }
        .coerceIn(0, segments.lastIndex)
    val segment = segments[segIdx]
    val offsetMs = ((currentPosUs - segment.relativeStartTimeUs).coerceAtLeast(0L)) / 1000L
    val durationMs = (segment.durationUs / 1000L).coerceAtLeast(1000L)
    return HlsSegmentProgress(
        segmentSeq = seq + segIdx,
        offsetMs = offsetMs,
        durationMs = durationMs,
    )
}

private fun getPlaylistTimelinePositionMs(
    playlist: HlsMediaPlaylist,
    seq: Long,
    offsetMs: Long,
): Long {
    val baseSeq = playlist.mediaSequence
    val segments = playlist.segments
    if (segments.isEmpty()) return offsetMs

    val segIdx = (seq - baseSeq).coerceIn(-10_000L, 10_000L).toInt()
    var segStartTimeMs = 0L
    if (segIdx >= 0) {
        val maxIdx = segIdx.coerceAtMost(segments.size)
        for (i in 0 until maxIdx) {
            segStartTimeMs += (segments[i].durationUs / 1000L)
        }
        if (segIdx > segments.size) {
            val avgDur = if (segments.isNotEmpty()) segments.last().durationUs / 1000L else 5000L
            segStartTimeMs += (segIdx - segments.size).toLong() * avgDur
        }
    } else {
        val avgDur = if (segments.isNotEmpty()) segments.first().durationUs / 1000L else 5000L
        segStartTimeMs = segIdx.toLong() * avgDur
    }

    return segStartTimeMs + offsetMs
}

private fun computeHlsDeltaMs(
    playlist: HlsMediaPlaylist,
    mySeq: Long,
    myOffsetMs: Long,
    leaderSeq: Long,
    leaderOffsetMs: Long,
): Long {
    val myPos = getPlaylistTimelinePositionMs(playlist, mySeq, myOffsetMs)
    val leaderPos = getPlaylistTimelinePositionMs(playlist, leaderSeq, leaderOffsetMs)
    return leaderPos - myPos
}

private fun isLiveSegmentHttpNotFound(error: PlaybackException): Boolean {
    var cause: Throwable? = error.cause
    var depth = 0
    while (cause != null && depth < 4) {
        if (cause is HttpDataSource.InvalidResponseCodeException &&
            (cause.responseCode == 404 || cause.responseCode == 410)
        ) {
            return true
        }
        cause = cause.cause
        depth += 1
    }
    return false
}

private class ResilientLoadErrorHandlingPolicy(
    private val cushionEnabledProvider: () -> Boolean,
) : DefaultLoadErrorHandlingPolicy() {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        if (cushionEnabledProvider()) 4 else 3

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val ex = loadErrorInfo.exception
        if (ex is ParserException || ex is androidx.media3.datasource.HttpDataSource.CleartextNotPermittedException) {
            return C.TIME_UNSET
        }
        if (ex is HttpDataSource.InvalidResponseCodeException && ex.responseCode in intArrayOf(401, 403, 404, 410)) {
            val isSingleMedia404 = ex.responseCode in intArrayOf(404, 410) &&
                loadErrorInfo.loadEventInfo.dataSpec.uri.toString().let {
                    it.endsWith(".ts", ignoreCase = true) || it.endsWith(".mp4", ignoreCase = true) || it.endsWith(".m4s", ignoreCase = true)
                } &&
                loadErrorInfo.errorCount == 1
            if (isSingleMedia404) return 400L
            if (ex.responseCode in intArrayOf(404, 410) || loadErrorInfo.errorCount >= 2) {
                return C.TIME_UNSET
            }
        }
        return (500L * loadErrorInfo.errorCount).coerceAtMost(3500L)
    }
}

