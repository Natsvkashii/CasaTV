package it.casatv.tv.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import it.casatv.tv.R
import it.casatv.tv.data.Channel
import it.casatv.tv.data.Facet
import it.casatv.tv.data.SyncStateResponse
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.buildCountryCatalog
import it.casatv.tv.ui.components.ChannelLogo
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
import it.casatv.tv.ui.currentTimeText
import it.casatv.tv.ui.formatTime
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
    val mediaSourceFactory = remember(context, defaultDataSourceFactory) {
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(defaultDataSourceFactory)
    }
    val loadControl = remember {
        androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                20000,
                50000,
                2500,
                5000,
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
    var errorMessage by remember { mutableStateOf<String?>(null) }
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
    var syncState by remember { mutableStateOf<SyncStateResponse?>(null) }
    var inSyncByDelta by remember { mutableStateOf(false) }

    val audioTracks = remember(tracksState) { extractOsdAudioTracks(tracksState) }
    val subtitleTracks = remember(tracksState) { extractOsdSubtitleTracks(tracksState) }
    val subtitlesDisabled = remember(tracksState, player.trackSelectionParameters) {
        player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
            !tracksState.isTypeSelected(C.TRACK_TYPE_TEXT)
    }
    val streamDetails = remember(videoSizeState, player.videoFormat) {
        formatStreamInfo(videoSizeState, player.videoFormat)
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
        cancelNumberEntry()
        wakeControls()
        vm.playRelative(offset)
    }

    fun selectCountry() {
        val country = pickerCountries.getOrNull(countryIndex) ?: return
        countryPickerOpen = false
        vm.setTvCountry(country.code)
        wakeControls()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                android.util.Log.w("CasaTV", "Errore di riproduzione", error)
                if (failingOver) return
                failingOver = true
                errorMessage = null
                scope.launch {
                    try {
                        // Try every source the server knows for this channel before
                        // showing the non-blocking black error screen.
                        val moved = vm.tryNextSource()
                        if (!moved) {
                            buffering = false
                            errorMessage = strings.t(
                                "Tutte le sorgenti di questo canale non sono raggiungibili in questo momento.",
                                "All sources for this channel are unreachable right now.",
                                "Todas las fuentes de este canal no están disponibles en este momento.",
                            )
                        }
                    } finally {
                        failingOver = false
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
                if (isPlaying) {
                    failingOver = false
                    errorMessage = null
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksState = tracks
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoSizeState = videoSize
            }
        }
        player.addListener(listener)
        player.setHandleAudioBecomingNoisy(true)
        onDispose {
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

    LaunchedEffect(player, channel.id, multiTvSyncEnabled) {
        if (!multiTvSyncEnabled) {
            syncState = null
            inSyncByDelta = false
            runCatching { player.playbackParameters = PlaybackParameters(1.0f) }
            return@LaunchedEffect
        }
        var prevSyncActive = false
        var filteredDeltaMs = 0.0
        var hasFilteredDelta = false
        var lastSpeedChangeTime = 0L
        var lastSeekTime = 0L
        var lastPlaybackPos = 0L
        var lastPosChangeTime = System.currentTimeMillis()

        while (isActive) {
            delay(1000)
            val now = System.currentTimeMillis()
            val isReady = player.playbackState == Player.STATE_READY && player.playWhenReady
            val currentPos = player.currentPosition

            // Stall watchdog: only if player is in READY state but position hasn't advanced for > 10.0s
            if (isReady && !buffering) {
                if (currentPos == lastPlaybackPos) {
                    if (now - lastPosChangeTime > 10_000L) {
                        android.util.Log.w("CasaSync", "Playback stall detected (pos $currentPos unchanged for 10s)! Re-triggering play...")
                        lastPosChangeTime = now
                        runCatching {
                            player.play()
                        }
                    }
                } else {
                    lastPlaybackPos = currentPos
                    lastPosChangeTime = now
                }
            } else {
                lastPosChangeTime = now
            }

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
                                player.playbackParameters = PlaybackParameters(1.0f)
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
                                player.playbackParameters = PlaybackParameters(targetSpeed)
                                lastSpeedChangeTime = now
                            }
                        }
                    } else {
                        // Leader always plays at normal 1.0f speed
                        inSyncByDelta = true
                        hasFilteredDelta = false
                        if (player.playbackParameters.speed != 1.0f) {
                            player.playbackParameters = PlaybackParameters(1.0f)
                        }
                    }
                } else {
                    prevSyncActive = false
                    syncState = null
                    inSyncByDelta = false
                    hasFilteredDelta = false
                    if (player.playbackParameters.speed != 1.0f) {
                        player.playbackParameters = PlaybackParameters(1.0f)
                    }
                }
            }
        }
    }

    LaunchedEffect(session.channel.id, session.playToken, session.sourceIndex) {
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
            val started = runCatching {
                player.stop()
                player.clearMediaItems()
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setMaxPlaybackSpeed(1.0f)
                            .setMinPlaybackSpeed(1.0f)
                            .setTargetOffsetMs(C.TIME_UNSET)
                            .build()
                    )
                    .build()
                if (url.contains(".m3u8", ignoreCase = true) || url.contains("m3u8", ignoreCase = true)) {
                    val hlsSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(defaultDataSourceFactory)
                        .setAllowChunklessPreparation(false)
                        .createMediaSource(mediaItem)
                    player.setMediaSource(hlsSource)
                } else {
                    player.setMediaItem(mediaItem)
                }
                player.prepare()
                player.playWhenReady = true
            }
            if (started.isFailure) {
                // A malformed URL or an unsupported container must never take
                // down the whole app: show the normal channel error screen.
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
        wakeControls()
    }

    LaunchedEffect(controlsVisible, hideTick, numberEntry, countryPickerOpen, osdMenuOpen) {
        if (controlsVisible && numberEntry.isEmpty() && !countryPickerOpen && !osdMenuOpen) {
            delay(6000)
            controlsVisible = false
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
    }

    BackHandler {
        when {
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
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                val code = native.keyCode
                val repeated = native.repeatCount > 0

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
                    openCountryPicker()
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
                        -> {
                            if (repeated) return@onPreviewKeyEvent true
                            zap(1)
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        -> {
                            if (repeated) return@onPreviewKeyEvent true
                            zap(-1)
                            true
                        }

                        else -> false
                    }
                } else when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                    -> {
                        if (repeated) return@onPreviewKeyEvent true
                        zap(1)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                    AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    -> {
                        if (repeated) return@onPreviewKeyEvent true
                        zap(-1)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> {
                        if (numberEntry.isNotEmpty()) {
                            confirmNumberEntry()
                        } else {
                            controlsVisible = !controlsVisible
                            hideTick++
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        wakeControls()
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        wakeControls()
                        true
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

        if (buffering && errorMessage == null && !failingOver) {
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

        AnimatedVisibility(
            visible = controlsVisible && errorMessage == null && !osdMenuOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerBottomStrip(
                channel = channel,
                syncState = syncState,
                inSync = inSyncByDelta,
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
    }
}




@Composable
private fun PlayerBottomStrip(
    channel: Channel,
    syncState: SyncStateResponse? = null,
    inSync: Boolean = false,
) {
    val strings = LocalAppStrings.current
    val isLight = it.casatv.tv.ui.theme.CasaIsLight

    val containerBg = if (isLight) Color(0xF5FFFFFF) else Color(0xF0141416)
    val containerBorder = if (isLight) CasaBorder else Color(0x33FFFFFF)
    val textColor = if (isLight) CasaText else Color.White
    val textMuted = if (isLight) CasaMuted else Color(0xCCEBEBF5)
    val textDim = if (isLight) CasaDim else Color(0x99EBEBF5)

    Box(
        modifier = Modifier
            .padding(bottom = 32.dp, start = 40.dp, end = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerBg)
            .border(1.dp, containerBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Channel Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                if (channel.number != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CasaGreen)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = channel.number.toString(),
                            color = Color(0xFF04150C),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
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
                    overflow = TextOverflow.Ellipsis,
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
                if (!channel.flag.isNullOrBlank() || !channel.quality.isNullOrBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = listOfNotNull(channel.flag, channel.quality).joinToString(" · "),
                        color = textDim,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Key Hints
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Hint("▲ ▼", strings.t("Canali", "Channels", "Canales"), isLight)
                Hint("0-9", strings.t("Numero", "Number", "Número"), isLight)
                Hint("MENU", strings.t("Opzioni", "Options", "Opciones"), isLight)
                Hint("BACK", strings.t("Esci", "Exit", "Salir"), isLight)
            }

            Spacer(Modifier.width(16.dp))

            // Clock & Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (syncState?.syncActive == true) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (inSync) CasaGreen else Color(0xFFFFB300)),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = currentTimeText(),
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
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
                .background(if (isLight) Color(0x14000000) else Color(0x26FFFFFF))
                .border(1.dp, if (isLight) Color(0x29000000) else Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = key,
                color = if (isLight) CasaText else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = action,
            color = if (isLight) CasaMuted else Color(0xCCEBEBF5),
            fontSize = 11.sp,
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
                .background(if (isLight) Color(0xF5FFFFFF) else Color(0xEB2C2C2E))
                .border(1.dp, if (isLight) CasaBorder else Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings.t("VAI AL CANALE", "GO TO CHANNEL", "IR AL CANAL"),
                color = CasaBlue,
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
                color = if (isLight) CasaMuted else Color(0xCCEBEBF5),
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
            .background(if (isLight) it.casatv.tv.ui.theme.CasaBackground else Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = if (isLight) it.casatv.tv.ui.theme.CasaRed else CasaAmber.copy(alpha = 0.85f),
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(16.dp))
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
                color = if (isLight) CasaMuted else Color(0xCCEBEBF5),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = strings.t(
                    "▲ ▼  cambia canale      OK  riprova      BACK  esci",
                    "▲ ▼  change channel      OK  retry      BACK  exit",
                    "▲ ▼  cambiar canal      OK  reintentar      BACK  salir",
                ),
                color = if (isLight) CasaDim else Color(0x99EBEBF5),
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
                color = if (isLight) it.casatv.tv.ui.theme.CasaBlue else CasaGreen.copy(alpha = 0.75f),
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
            "http", "https" -> value
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
                .background(if (isLight) Color(0xF5FFFFFF) else Color(0xF20A1218))
                .border(1.dp, if (isLight) CasaBorder else CasaGreen.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
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

