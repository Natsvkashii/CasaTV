package it.casatv.tv.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.media3.common.MediaItem
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import it.casatv.tv.CasaTvApplication
import it.casatv.tv.R
import it.casatv.tv.data.Channel
import it.casatv.tv.data.Facet
import it.casatv.tv.data.SyncStateResponse
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.MixRegionPickerDialog
import it.casatv.tv.ui.buildCountryCatalog
import it.casatv.tv.ui.components.ChannelLivePreviewBox
import it.casatv.tv.ui.components.ChannelLogo
import it.casatv.tv.ui.computeFilteredMixRegionCounts
import it.casatv.tv.ui.computeMixRegionCounts
import it.casatv.tv.ui.countMatchingMixChannels
import it.casatv.tv.ui.components.OsdAudioTrack
import it.casatv.tv.ui.components.OsdSubtitleTrack
import it.casatv.tv.ui.components.PlayerOsdMenu
import it.casatv.tv.ui.components.applyAudioTrack
import it.casatv.tv.ui.components.applySubtitleTrack
import it.casatv.tv.ui.components.disableSubtitles
import it.casatv.tv.ui.components.extractOsdAudioTracks
import it.casatv.tv.ui.components.extractOsdSubtitleTracks
import it.casatv.tv.ui.components.formatStreamInfo
import it.casatv.tv.ui.countryFacet
import it.casatv.tv.ui.countryFlag
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
                if (isLowRam) 2000 else 2500,
                if (isLowRam) 4000 else 5000,
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
    var hasPlayedCurrentChannel by remember { mutableStateOf(false) }
    var reconnectAttempt by remember { mutableIntStateOf(0) }
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
    val showRemoteHintsBanner by vm.showRemoteHintsBanner.collectAsStateWithLifecycle()
    val showMixRegionPicker by vm.showMixRegionPicker.collectAsStateWithLifecycle()
    val selectedMixRegions by vm.selectedMixRegions.collectAsStateWithLifecycle()
    val selectedMixLanguages by vm.selectedMixLanguages.collectAsStateWithLifecycle()
    val selectedMixGenres by vm.selectedMixGenres.collectAsStateWithLifecycle()
    val mixChannels by vm.mixChannels.collectAsStateWithLifecycle()
    val zapQueue by vm.zapQueue.collectAsStateWithLifecycle()
    var firstVideoFrameRendered by remember { mutableStateOf(false) }
    var audioSkipCount by remember { mutableIntStateOf(0) }
    var hintsBannerVisible by remember { mutableStateOf(false) }
    var hintsBannerTick by remember { mutableIntStateOf(0) }
    var clockTick by remember { mutableIntStateOf(0) }
    var syncState by remember { mutableStateOf<SyncStateResponse?>(null) }
    var inSyncByDelta by remember { mutableStateOf(false) }
    var lastZapTimeMs by remember { mutableLongStateOf(0L) }
    var quickDrawerOpen by remember { mutableStateOf(false) }
    var quickDrawerIndex by remember { mutableIntStateOf(0) }
    var lastDrawerMoveMs by remember { mutableLongStateOf(0L) }

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
    val hasPlayableVideoTrack = remember(tracksState) {
        tracksState.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSupported && it.length > 0 }
    }
    val hasVideoSize = videoSizeState.width > 0 && videoSizeState.height > 0
    val confirmedAudioOnly = hasPlayedCurrentChannel &&
        errorMessage == null &&
        !firstVideoFrameRendered &&
        !hasVideoSize &&
        ((hasAudioTrack && !hasPlayableVideoTrack) || (isLikelyAudio && !hasPlayableVideoTrack))

    val channelCountryCode = remember(channel.country) { normalizeCountryCode(channel.country) }
    val channelCountryName = remember(channelCountryCode, channel.countryName, strings.language) {
        channelCountryCode?.let { countryName(it, strings.language) }
            ?: channel.countryName.takeIf { it.isNotBlank() }
    }
    val channelFlag = remember(channel.flag, channelCountryCode) {
        channel.flag.takeIf { it.isNotBlank() }
            ?: channelCountryCode?.let { code -> countryFlag(code) }.orEmpty()
    }

    fun wakeControls() {
        controlsVisible = true
        hideTick++
        if (showRemoteHintsBanner) {
            hintsBannerVisible = true
            hintsBannerTick++
        }
    }

    fun cancelNumberEntry() {
        tuneJob?.cancel()
        tuneJob = null
        numberEntry = ""
    }

    fun openQuickDrawer(): Boolean {
        if (session.queue.isEmpty()) return false
        cancelNumberEntry()
        quickDrawerIndex = session.index.coerceIn(0, (session.queue.size - 1).coerceAtLeast(0))
        quickDrawerOpen = true
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

    fun startStream(url: String): Result<Unit> = runCatching {
        player.stop()
        player.clearMediaItems()
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMaxPlaybackSpeed(1.0f)
                    .setMinPlaybackSpeed(1.0f)
                    .setTargetOffsetMs(C.TIME_UNSET)
                    .build(),
            )
            .build()
        if (url.contains(".m3u8", ignoreCase = true) || url.contains("m3u8", ignoreCase = true)) {
            val hlsSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(defaultDataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorPolicy)
                .setAllowChunklessPreparation(false)
                .createMediaSource(mediaItem)
            player.setMediaSource(hlsSource)
        } else {
            player.setMediaItem(mediaItem)
        }
        player.prepare()
        player.playWhenReady = true
    }

    fun scheduleAutoReconnect(reason: String) {
        if (failingOver) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            if (isMixModeState && !hasPlayedCurrentChannel) {
                failingOver = true
                errorMessage = null
                try {
                    val moved = vm.tryNextSource()
                    if (!moved) {
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

            val backoffs = longArrayOf(700L, 1500L, 2500L, 4000L, 6000L, 8000L)
            if (reconnectAttempt < backoffs.size) {
                val delayMs = backoffs[reconnectAttempt]
                reconnectAttempt += 1
                reconnecting = true
                buffering = false
                errorMessage = null
                android.util.Log.i("CasaTV", "Riconnessione automatica ($reason), tentativo $reconnectAttempt/${backoffs.size} tra ${delayMs}ms")
                delay(delayMs)
                if (!isActive) return@launch
                if (reconnectAttempt == 3 && vm.tryNextSource()) {
                    return@launch
                }
                val url = currentStreamUrlState
                if (url == null) {
                    reconnecting = false
                    errorMessage = strings.t(
                        "Questo canale non ha un flusso valido o disponibile.",
                        "This channel has no valid or available stream.",
                        "Este canal no tiene un flujo válido o disponible.",
                    )
                    return@launch
                }
                val res = startStream(url)
                if (res.isFailure) {
                    scheduleAutoReconnect("start_failed")
                }
            } else {
                reconnecting = false
                buffering = false
                errorMessage = strings.t(
                    "Flusso temporaneamente interrotto. Riconnessione automatica in attesa…",
                    "Stream temporarily interrupted. Background reconnect waiting…",
                    "Flujo interrumpido temporalmente. Reconexión en segundo plano…",
                )
                delay(15_000L)
                if (!isActive || errorMessage == null) return@launch
                reconnectAttempt = 0
                val url = currentStreamUrlState ?: return@launch
                reconnecting = true
                errorMessage = null
                startStream(url)
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING && !reconnecting
                if (playbackState == Player.STATE_READY) {
                    hasPlayedCurrentChannel = true
                    reconnecting = false
                    errorMessage = null
                } else if (playbackState == Player.STATE_ENDED && (player.isCurrentMediaItemLive || hasPlayedCurrentChannel)) {
                    android.util.Log.w("CasaTV", "Flusso terminato inaspettatamente (STATE_ENDED), riconnessione...")
                    scheduleAutoReconnect("state_ended")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                android.util.Log.w("CasaTV", "Errore di riproduzione (${error.errorCodeName})", error)
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    runCatching {
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.playWhenReady = true
                    }
                    return
                }
                scheduleAutoReconnect(error.errorCodeName)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
                if (isPlaying) {
                    failingOver = false
                    reconnecting = false
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
        player.addListener(listener)
        player.setHandleAudioBecomingNoisy(true)
        onDispose {
            reconnectJob?.cancel()
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
                    scope.launch { runCatching { vm.syncPlayback("", false, 0L, 0L) } }
                }
                Lifecycle.Event.ON_RESUME -> if (errorMessage == null) {
                    runCatching {
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
            scope.launch { runCatching { vm.syncPlayback("", false, 0L, 0L) } }
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

            if (pState == Player.STATE_BUFFERING && errorMessage == null && !reconnecting && currentStreamUrlState != null) {
                if (bufferingSinceMs == 0L) bufferingSinceMs = now
                if (now - bufferingSinceMs > 12_000L) {
                    bufferingSinceMs = now
                    android.util.Log.w("CasaTV", "Buffering prolungato (>12s), avvio riconnessione automatica")
                    scheduleAutoReconnect("buffering_timeout")
                }
            } else {
                bufferingSinceMs = 0L
            }

            if (isReady && errorMessage == null && !reconnecting && currentStreamUrlState != null) {
                val pos = player.currentPosition
                if (pos == lastWatchPos) {
                    if (now - lastAdvanceMs > 6_000L) {
                        lastAdvanceMs = now
                        android.util.Log.w("CasaTV", "Flusso bloccato su pos=$pos per 6s, avvio riconnessione automatica")
                        scheduleAutoReconnect("stall_watchdog")
                    }
                } else {
                    lastWatchPos = pos
                    lastAdvanceMs = now
                }
            } else {
                lastWatchPos = -1L
                lastAdvanceMs = now
            }

            if (multiTvFollowerActive) {
                cushionBuilding = false
            } else if (isReady) {
                if (!liveSafetyBufferEnabled || !player.isCurrentMediaItemLive) {
                    cushionBuilding = false
                    if (kotlin.math.abs(player.playbackParameters.speed - 1.0f) > 0.002f) {
                        runCatching { player.playbackParameters = PlaybackParameters(1.0f, 1.0f) }
                    }
                } else {
                    val bufferedAheadMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
                    val windowDurationMs = run {
                        val timeline = player.currentTimeline
                        if (!timeline.isEmpty && player.currentMediaItemIndex in 0 until timeline.windowCount) {
                            val w = Timeline.Window()
                            timeline.getWindow(player.currentMediaItemIndex, w)
                            if (w.durationMs != C.TIME_UNSET && w.durationMs > 0L) w.durationMs else 0L
                        } else 0L
                    }
                    val effectiveTargetMs = if (windowDurationMs > 0L) {
                        minOf(8_000L, (windowDurationMs * 55L) / 100L).coerceAtLeast(4_000L)
                    } else {
                        8_000L
                    }
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

        while (isActive) {
            delay(1000)
            val now = System.currentTimeMillis()
            val isReady = player.playbackState == Player.STATE_READY && player.playWhenReady
            val currentPos = player.currentPosition

            if (isReady) {
                val isLive = player.isCurrentMediaItemLive
                val hlsProgress = if (isLive) getHlsSegmentProgress(player) else null

                val liveOffset = if (player.currentLiveOffset != C.TIME_UNSET && player.currentLiveOffset > 0L) {
                    player.currentLiveOffset
                } else 0L

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
                                "🔗 Sincronizzazione automatica attiva con $partner",
                                "🔗 Multi-TV sync active with $partner",
                                "🔗 Sincronización multi-TV activa con $partner",
                            ),
                        )
                    }

                    if (!resp.isLeader) {
                        val rawDeltaMs = if (hlsProgress != null && resp.targetSegmentSeq > 0L) {
                            val playlist = (player.currentManifest as? HlsManifest)?.mediaPlaylist
                            if (playlist != null) {
                                computeHlsDeltaMs(
                                    playlist = playlist,
                                    mySeq = hlsProgress.segmentSeq,
                                    myOffsetMs = hlsProgress.offsetMs,
                                    leaderSeq = resp.targetSegmentSeq,
                                    leaderOffsetMs = resp.targetSegmentOffsetMs,
                                )
                            } else {
                                resp.targetPositionMs - currentPos
                            }
                        } else {
                            resp.targetPositionMs - currentPos
                        }

                        // Outlier rejection during transient manifest segment rollover
                        val deltaSample = if (hasFilteredDelta && kotlin.math.abs(filteredDeltaMs) < 400.0 && kotlin.math.abs(rawDeltaMs - filteredDeltaMs) > 3000.0) {
                            filteredDeltaMs
                        } else {
                            rawDeltaMs.toDouble()
                        }

                        if (!hasFilteredDelta) {
                            filteredDeltaMs = deltaSample
                            hasFilteredDelta = true
                        } else {
                            // Responsive EMA filter (40% new sample, 60% history)
                            filteredDeltaMs = 0.40 * deltaSample + 0.60 * filteredDeltaMs
                        }

                        val effectiveDelta = filteredDeltaMs.toLong()
                        val absDelta = kotlin.math.abs(effectiveDelta)

                        inSyncByDelta = absDelta <= 25L
                        android.util.Log.d("CasaSync", "rawDelta=$rawDeltaMs, effDelta=$effectiveDelta, inSync=$inSyncByDelta, speed=${player.playbackParameters.speed}")

                        // Seeking is ONLY used for non-live VOD / movies when drift > 5s
                        // For live streams, speed modulation completely avoids buffer flushes and audio decoder stalls
                        if (!isLive && absDelta > 5000L && now - lastSeekTime > 15_000L) {
                            lastSeekTime = now
                            player.seekTo(resp.targetPositionMs.coerceIn(0L, (player.duration - 2000L).coerceAtLeast(0L)))
                            if (player.playbackParameters.speed != 1.0f) {
                                player.playbackParameters = PlaybackParameters(1.0f, 1.0f)
                            }
                            lastSpeedChangeTime = now
                            hasFilteredDelta = false
                        } else {
                            // Fast, safe speed adjustments:
                            // Slowing down (follower ahead) is very safe for audio DACs down to 0.94f
                            // Speeding up is capped at 1.036f (49,728 Hz) to avoid MediaTek hardware sink stall
                            val targetSpeed = when {
                                absDelta <= 20L -> 1.0f
                                effectiveDelta > 600L -> 1.036f
                                effectiveDelta > 200L -> 1.025f
                                effectiveDelta > 50L -> 1.012f
                                effectiveDelta > 20L -> 1.006f
                                effectiveDelta < -600L -> 0.94f
                                effectiveDelta < -200L -> 0.965f
                                effectiveDelta < -50L -> 0.985f
                                effectiveDelta < -20L -> 0.994f
                                else -> 1.0f
                            }

                            val currentSpeed = player.playbackParameters.speed
                            val speedDiff = kotlin.math.abs(currentSpeed - targetSpeed)

                            // Rate limit: change speed at most once every 1.0s, or immediately when entering sync (1.0f)
                            val timeSinceLastSpeedChange = now - lastSpeedChangeTime
                            if (speedDiff > 0.003f && (targetSpeed == 1.0f || timeSinceLastSpeedChange >= 1000L)) {
                                player.playbackParameters = PlaybackParameters(targetSpeed, 1.0f)
                                lastSpeedChangeTime = now
                            }
                        }
                    } else {
                        // Leader yields speed control to live safety cushion when enabled
                        inSyncByDelta = true
                        hasFilteredDelta = false
                    }
                } else {
                    prevSyncActive = false
                    multiTvFollowerActive = false
                    syncState = null
                    inSyncByDelta = false
                    hasFilteredDelta = false
                }
            }
        }
    }

    LaunchedEffect(session.channel.id, session.playToken, session.sourceIndex) {
        reconnectJob?.cancel()
        hasPlayedCurrentChannel = false
        firstVideoFrameRendered = false
        tracksState = Tracks.EMPTY
        videoSizeState = VideoSize.UNKNOWN
        reconnectAttempt = 0
        reconnecting = false
        errorMessage = null
        failingOver = false
        buffering = true
        delay(250)
        val url = playableStreamUrl(session.activeStreamUrl)
        if (url == null) {
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
            val started = startStream(url)
            if (started.isFailure) {
                android.util.Log.w("CasaTV", "Impossibile avviare il flusso $url", started.exceptionOrNull())
                buffering = false
                errorMessage = strings.t(
                    "Il formato di questo flusso non è supportato.",
                    "This stream format is not supported.",
                    "El formato de este flujo no es compatible.",
                )
            }
        }
        wakeControls()
    }

    LaunchedEffect(channel.id) {
        cancelNumberEntry()
        countryPickerOpen = false
        osdMenuOpen = false
        quickDrawerOpen = false
        wakeControls()
    }

    LaunchedEffect(showMixRegionPicker) {
        if (!showMixRegionPicker) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
        }
    }

    LaunchedEffect(controlsVisible, hideTick, numberEntry, countryPickerOpen, osdMenuOpen, quickDrawerOpen, showMixRegionPicker) {
        if (controlsVisible && numberEntry.isEmpty() && !countryPickerOpen && !osdMenuOpen && !quickDrawerOpen && !showMixRegionPicker) {
            delay(6000)
            controlsVisible = false
        }
    }

    LaunchedEffect(hintsBannerVisible, hintsBannerTick, showRemoteHintsBanner) {
        if (!showRemoteHintsBanner) {
            hintsBannerVisible = false
            return@LaunchedEffect
        }
        if (hintsBannerVisible) {
            delay(6000)
            hintsBannerVisible = false
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

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
    }

    BackHandler {
        when {
            quickDrawerOpen -> quickDrawerOpen = false
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
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                val code = native.keyCode
                val repeated = native.repeatCount > 0

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
                    val colLimits = listOf(
                        3,
                        audioTracks.size.coerceAtLeast(1),
                        1 + subtitleTracks.size,
                        2,
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
                                    if (osdCol == 0) {
                                        vm.toggleFavorite(channel)
                                    } else if (osdCol == 1) {
                                        osdMenuOpen = false
                                        openCountryPicker()
                                    }
                                }
                            }
                            wakeControls()
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
                            if (session.isMixMode) handleZapKey(1, repeated) else false
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
                    -> handleZapKey(1, repeated)

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    -> handleZapKey(-1, repeated)

                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> {
                        if (numberEntry.isNotEmpty()) {
                            confirmNumberEntry()
                        } else {
                            controlsVisible = !controlsVisible
                            hideTick++
                            if (controlsVisible && showRemoteHintsBanner) {
                                hintsBannerVisible = true
                                hintsBannerTick++
                            } else {
                                hintsBannerVisible = false
                            }
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!openQuickDrawer()) {
                            wakeControls()
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (session.isMixMode) {
                            handleZapKey(1, repeated)
                        } else {
                            wakeControls()
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
                view.keepScreenOn = isPlayingState
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = confirmedAudioOnly && !buffering && !failingOver && errorMessage == null,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            AudioOnlyLogoStage(
                channel = channel,
                countryDisplay = listOfNotNull(
                    channelFlag.takeIf { it.isNotBlank() },
                    channelCountryName,
                ).joinToString(" ").takeIf { it.isNotBlank() },
            )
        }

        if (buffering && errorMessage == null && !failingOver && !reconnecting) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    color = CasaGreen,
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
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xD80A1322))
                    .border(1.dp, CasaGreen.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    color = CasaGreen,
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

        AnimatedVisibility(
            visible = showRemoteHintsBanner && hintsBannerVisible && errorMessage == null && !osdMenuOpen && !quickDrawerOpen && !countryPickerOpen && !showMixRegionPicker,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(280)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlayerRemoteHintsBanner(isMixMode = session.isMixMode)
        }

        AnimatedVisibility(
            visible = controlsVisible && errorMessage == null && !osdMenuOpen && !quickDrawerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerBottomStrip(
                channel = channel,
                countryFlag = channelFlag,
                countryName = channelCountryName,
                clockTick = clockTick,
                syncState = syncState,
                inSync = inSyncByDelta,
                isMixMode = session.isMixMode,
                mixPosition = session.index + 1,
                mixTotal = session.queue.size,
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
                streamDetails = streamDetails,
                onClose = {
                    osdMenuOpen = false
                    wakeControls()
                },
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
            val mixPool = mixChannels.ifEmpty { zapQueue }.ifEmpty { session.queue }
            val hasExtraFilters = selectedMixLanguages.isNotEmpty() || selectedMixGenres.isNotEmpty()
            val regionCounts = remember(config?.facets?.countries, mixPool, hasExtraFilters, selectedMixLanguages, selectedMixGenres, mixIncludeAudioOnly) {
                if (hasExtraFilters && mixPool.isNotEmpty()) {
                    computeFilteredMixRegionCounts(
                        channels = mixPool,
                        includeAudioOnly = mixIncludeAudioOnly,
                        selectedLanguages = selectedMixLanguages,
                        selectedGenres = selectedMixGenres,
                    )
                } else {
                    computeMixRegionCounts(config?.facets?.countries.orEmpty())
                }
            }
            val matchingCount = remember(mixPool, hasExtraFilters, selectedMixRegions, selectedMixLanguages, selectedMixGenres, mixIncludeAudioOnly) {
                if (!hasExtraFilters || mixPool.isEmpty()) {
                    null
                } else {
                    countMatchingMixChannels(
                        channels = mixPool,
                        includeAudioOnly = mixIncludeAudioOnly,
                        selectedRegions = selectedMixRegions,
                        selectedLanguages = selectedMixLanguages,
                        selectedGenres = selectedMixGenres,
                    )
                }
            }
            val totalAllChannels = remember(config?.stats?.channels, session.queue.size) {
                config?.stats?.channels ?: session.queue.size
            }
            MixRegionPickerDialog(
                selectedRegions = selectedMixRegions,
                regionCounts = regionCounts,
                totalChannels = totalAllChannels,
                onToggleRegion = { vm.toggleMixRegion(it) },
                onStartMix = { vm.startMixFromSelectedRegions() },
                onDismiss = { vm.dismissMixRegionPicker() },
                selectedLanguages = selectedMixLanguages,
                selectedGenres = selectedMixGenres,
                matchingChannelCount = matchingCount,
                onToggleLanguage = { vm.toggleMixLanguage(it) },
                onToggleGenre = { vm.toggleMixGenre(it) },
                onClearFilters = { vm.clearMixFilters() },
            )
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
            .background(Color(0xF207101C))
            .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
            .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
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
                color = CasaGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = "${(selectedIndex + 1).coerceAtMost(queue.size)} / ${queue.size}",
                color = Color(0xCCEBEBF5),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = strings.t(
                "▲ ▼ sfoglia · OK sintonizza · ▶ chiudi",
                "▲ ▼ browse · OK tune · ▶ close",
                "▲ ▼ explorar · OK sintonizar · ▶ cerrar",
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
                queue,
                key = { idx, item -> "qd_${idx}_${item.id}" },
                contentType = { _, _ -> "drawer_channel" },
            ) { index, item ->
                val isCursor = index == selectedIndex
                val isPlaying = item.id == playingChannelId
                val cardBg = if (isCursor) Color(0xFF132944) else Color(0xFF0A1422)
                val cardBorder = if (isCursor) Color(0xFF00D4FF) else Color(0xFF1A2E47)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(drawerCardShape)
                        .background(cardBg)
                        .border(if (isCursor) 2.dp else 1.dp, cardBorder, drawerCardShape)
                        .clickable { onSelectChannel(index) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .fillMaxHeight()
                            .clip(drawerThumbShape)
                            .background(Color(0xFF060A10)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChannelLogo(
                            channel = item,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                        )
                        if (!isLowRam) {
                            ChannelLivePreviewBox(
                                streamUrl = item.streamUrl,
                                active = isCursor && !isPlaying,
                                dwellMs = 850L,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (item.number != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .clip(drawerNumShape)
                                    .background(Color(0xCC04080D))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = item.number.toString(),
                                    color = CasaGreen,
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
                                item.flag.takeIf { it.isNotBlank() },
                                item.categoryNames.firstOrNull() ?: item.countryName.takeIf { it.isNotBlank() },
                                item.quality.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
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
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF0D213B), Color(0xFF060D19), Color(0xFF03060C)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 236.dp, height = 158.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xE6101F35))
                    .border(1.5.dp, Color(0x5938BDF8), RoundedCornerShape(28.dp))
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
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x2638BDF8))
                        .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8)),
                    )
                    Text(
                        text = strings.t("SOLO AUDIO", "AUDIO ONLY", "SOLO AUDIO"),
                        color = Color(0xFF7DD3FC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
                if (subtitleParts.isNotBlank()) {
                    Text(
                        text = subtitleParts,
                        color = Color(0xFF94A3B8),
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
private fun PlayerRemoteHintsBanner(isMixMode: Boolean) {
    val strings = LocalAppStrings.current
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val containerBg = if (isLight) Color(0xF5FFFFFF) else Color(0xEB08111E)
    val containerBorder = if (isLight) CasaBorder else Color(0xFF1E3A5F)

    Row(
        modifier = Modifier
            .padding(top = 24.dp, start = 24.dp, end = 24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(containerBg)
            .border(1.dp, containerBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!isMixMode) {
            Hint("◀", strings.t("Lista", "List", "Lista"), isLight)
            Hint("▲ ▼", strings.t("Canali", "Channels", "Canales"), isLight)
            Hint("0-9", strings.t("Numero", "Number", "Número"), isLight)
        } else {
            Hint("◀", strings.t("Prec.", "Prev", "Ant."), isLight)
            Hint("▲ ▼ ▶", strings.t("Mix", "Mix", "Mix"), isLight)
        }
        Hint("MENU", strings.t("Opzioni", "Options", "Opciones"), isLight)
        Hint("BACK", strings.t("Esci", "Exit", "Salir"), isLight)
    }
}

@Composable
private fun PlayerBottomStrip(
    channel: Channel,
    countryFlag: String,
    countryName: String?,
    clockTick: Int,
    syncState: SyncStateResponse? = null,
    inSync: Boolean = false,
    isMixMode: Boolean = false,
    mixPosition: Int = 1,
    mixTotal: Int = 1,
) {
    val strings = LocalAppStrings.current
    val isLight = it.casatv.tv.ui.theme.CasaIsLight

    val containerBg = if (isLight) Color(0xF5FFFFFF) else Color(0xF208111E)
    val containerBorder = if (isLight) CasaBorder else Color(0xFF1E3A5F)
    val textColor = if (isLight) CasaText else Color(0xFFF8FAFC)
    val textDim = if (isLight) CasaDim else Color(0xFF94A3B8)
    val accentBlue = if (isLight) CasaBlue else Color(0xFF38BDF8)

    val tvLocalTime = remember(clockTick) { currentTimeText() }
    val countryLocalTime = remember(channel.country, clockTick) {
        countryTimeTextOrNull(channel.country)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 40.dp, end = 40.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(containerBg)
            .border(1.dp, containerBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Channel Info + Country Flag & Localized Country Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                if (isMixMode) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0284C7))
                            .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "MIX · $mixPosition/$mixTotal",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (channel.number != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CasaGreen)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = channel.number.toString(),
                            color = if (isLight) Color.White else Color(0xFF04101A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = channel.name,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (channel.favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (isLight) it.casatv.tv.ui.theme.CasaAmber else CasaGreen,
                        modifier = Modifier.size(14.dp),
                    )
                }
                val countryText = listOfNotNull(
                    countryFlag.takeIf { it.isNotBlank() },
                    countryName?.takeIf { it.isNotBlank() },
                ).joinToString(" ")
                if (countryText.isNotBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = countryText,
                        color = textColor.copy(alpha = 0.85f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                }
                if (channel.quality.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(accentBlue.copy(alpha = 0.14f))
                            .border(1.dp, accentBlue.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = channel.quality,
                            color = accentBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            // Dual Clocks (Channel Country Local Time + TV Local Time) & Multi-TV Sync Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (syncState?.syncActive == true) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (inSync) CasaGreen else Color(0xFFFFB300)),
                    )
                }
                if (countryLocalTime != null) {
                    val countryShortLabel = listOfNotNull(
                        countryFlag.takeIf { it.isNotBlank() },
                        countryName?.uppercase() ?: channel.country?.uppercase(),
                    ).joinToString(" ").ifBlank { strings.t("PAESE", "COUNTRY", "PAÍS") }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = countryShortLabel,
                            color = accentBlue,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 136.dp),
                        )
                        Text(
                            text = countryLocalTime,
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(26.dp)
                            .background(containerBorder),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = strings.t("ORA TV", "TV TIME", "HORA TV"),
                        color = textDim,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = tvLocalTime,
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}


@Composable
private fun Hint(key: String, action: String, isLight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isLight) Color(0x14000000) else Color(0x1E38BDF8))
                .border(1.dp, if (isLight) Color(0x29000000) else Color(0x4038BDF8), RoundedCornerShape(6.dp))
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
            color = if (isLight) CasaMuted else Color(0xFFCBD5E1),
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
                .clip(RoundedCornerShape(24.dp))
                .background(if (isLight) Color(0xF5FFFFFF) else Color(0xF20A1424))
                .border(1.dp, if (isLight) CasaBorder else Color(0xFF1E3A5F), RoundedCornerShape(24.dp))
                .padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings.t("VAI AL CANALE", "GO TO CHANNEL", "IR AL CANAL"),
                color = if (isLight) CasaBlue else CasaGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry,
                color = if (isLight) CasaText else Color.White,
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
                color = if (isLight) CasaMuted else Color(0xFFCBD5E1),
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
            .then(
                if (isLight) {
                    Modifier.background(it.casatv.tv.ui.theme.CasaBackground)
                } else {
                    Modifier.background(
                        Brush.radialGradient(
                            listOf(Color(0xFF0C1D33), Color(0xFF050A12)),
                        ),
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(if (isLight) Color(0xF5FFFFFF) else Color(0xCC0A1424))
                .border(1.dp, if (isLight) CasaBorder else Color(0xFF1E3A5F), RoundedCornerShape(22.dp))
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
                color = if (isLight) CasaText else Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = channel.name,
                color = if (isLight) it.casatv.tv.ui.theme.CasaBlue else CasaGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = if (isLight) CasaMuted else Color(0xFFCBD5E1),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = strings.t(
                    "▲ ▼  cambia canale      OK  riprova      BACK  esci",
                    "▲ ▼  change channel      OK  retry      BACK  exit",
                    "▲ ▼  cambiar canal      OK  reintentar      BACK  salir",
                ),
                color = if (isLight) CasaDim else Color(0xFF94A3B8),
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
                color = if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color(0xFF7DD3FC),
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
                .clip(RoundedCornerShape(18.dp))
                .background(if (isLight) Color(0xF5FFFFFF) else Color(0xF208111E))
                .border(1.dp, if (isLight) CasaBorder else Color(0xFF1E3A5F), RoundedCornerShape(18.dp))
                .padding(horizontal = 30.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings.t("PAESE DELLA MODALITÀ TV", "TV MODE COUNTRY", "PAÍS DEL MODO TV"),
                color = if (isLight) CasaBlue else CasaGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.t(
                    "I canali ▲ ▼ partiranno da questo paese",
                    "▲ ▼ zapping starts from this country",
                    "El zapping ▲ ▼ empezará desde este país",
                ),
                color = if (isLight) CasaMuted else Color(0x99EBEBF5),
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(14.dp))
            for (index in start until end) {
                val country = countries[index]
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .width(330.dp)
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) {
                                if (isLight) Color(0x1F0070F3) else CasaGreen.copy(alpha = 0.16f)
                            } else Color.Transparent
                        )
                        .border(
                            if (selected) 1.dp else 0.dp,
                            if (selected) (if (isLight) CasaBlue else CasaGreen) else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = country.flag, fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = country.name,
                        color = if (selected) (if (isLight) CasaBlue else Color.White) else (if (isLight) CasaText else Color(0xCCEBEBF5)),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Text(
                            text = "●",
                            color = if (isLight) CasaBlue else CasaGreen,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.t(
                    "▲ ▼ scegli · OK conferma · BACK annulla",
                    "▲ ▼ choose · OK confirm · BACK cancel",
                    "▲ ▼ elegir · OK confirmar · BACK cancelar",
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

    val segIdx = (seq - baseSeq).toInt()
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

private class ResilientLoadErrorHandlingPolicy(
    private val cushionEnabledProvider: () -> Boolean,
) : DefaultLoadErrorHandlingPolicy() {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        if (cushionEnabledProvider()) 8 else 4

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val ex = loadErrorInfo.exception
        if (ex is ParserException || ex is androidx.media3.datasource.HttpDataSource.CleartextNotPermittedException) {
            return C.TIME_UNSET
        }
        if (ex is HttpDataSource.InvalidResponseCodeException &&
            ex.responseCode in intArrayOf(401, 403, 404, 410) &&
            loadErrorInfo.errorCount >= 2
        ) {
            return C.TIME_UNSET
        }
        return (500L * loadErrorInfo.errorCount).coerceAtMost(3500L)
    }
}

