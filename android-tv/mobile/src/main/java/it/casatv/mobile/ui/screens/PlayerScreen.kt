package it.casatv.mobile.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.media.MediaRouteSelector
import coil.compose.AsyncImage
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import it.casatv.mobile.data.MobileChannel
import it.casatv.mobile.service.MobilePlaybackService
import it.casatv.mobile.ui.LocalMobileStrings
import it.casatv.mobile.ui.channelInitials
import it.casatv.mobile.ui.formatCountryTimeOrNull
import it.casatv.mobile.ui.formatLocalTime
import it.casatv.mobile.ui.isLikelyAudioOnlyChannel
import it.casatv.mobile.ui.resolveMobileCountryFlag
import it.casatv.mobile.ui.resolveMobileCountryName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: MobileChannel,
    isMixMode: Boolean = false,
    mixIndex: Int = 0,
    mixTotal: Int = 0,
    isInPipMode: Boolean = false,
    backgroundPlayEnabled: Boolean = true,
    liveSafetyBufferEnabled: Boolean = true,
    mixIncludeAudioOnly: Boolean = false,
    onToggleBackgroundPlay: (Boolean) -> Unit = {},
    onEnterPip: () -> Unit = {},
    onPreviousChannel: () -> Unit = {},
    onNextChannel: () -> Unit = {},
    onAudioOnlyDetectedInMix: (String) -> Boolean = { false },
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val strings = LocalMobileStrings.current

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val safetyBufferEnabledState by rememberUpdatedState(liveSafetyBufferEnabled)
    val scope = rememberCoroutineScope()
    var reconnecting by remember { mutableStateOf(false) }
    var hasPlayedCurrentChannel by remember { mutableStateOf(false) }
    var reconnectAttempt by remember { mutableIntStateOf(0) }
    var reconnectJob by remember { mutableStateOf<Job?>(null) }
    val currentStreamUrlState by rememberUpdatedState(channel.streamUrl)
    val tr: (String, String, String) -> String = { itText, enText, esText ->
        when (strings.lang.lowercase()) {
            "it" -> itText
            "es" -> esText
            else -> enText
        }
    }
    var showControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isLandscape by remember { mutableStateOf(false) }
    var tracksState by remember { mutableStateOf(Tracks.EMPTY) }
    var videoSizeState by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var firstVideoFrameRendered by remember { mutableStateOf(false) }
    var audioSkipCount by remember { mutableIntStateOf(0) }
    var clockTick by remember { mutableIntStateOf(0) }

    val isLikelyAudio = remember(channel.id, channel.name, channel.streamUrl, channel.category, channel.quality) {
        isLikelyAudioOnlyChannel(channel)
    }
    val hasAudioTrack = remember(tracksState) {
        tracksState.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
    }
    val hasPlayableVideoTrack = remember(tracksState) {
        tracksState.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSupported && it.length > 0 }
    }
    val hasVideoSize = videoSizeState.width > 0 && videoSizeState.height > 0

    val countryFlag = remember(channel.country, channel.flag) {
        resolveMobileCountryFlag(channel.country, channel.flag)
    }
    val countryName = remember(channel.country, channel.countryName, strings.lang) {
        resolveMobileCountryName(channel.country, channel.countryName, strings.lang)
    }
    val countryDisplay = remember(countryFlag, countryName) {
        listOfNotNull(
            countryFlag.takeIf { it.isNotBlank() },
            countryName.takeIf { it.isNotBlank() },
        ).joinToString(" ")
    }

    val castContext = remember(context) {
        runCatching { CastContext.getSharedInstance(context) }.getOrNull()
    }
    var castDeviceName by remember(castContext) {
        mutableStateOf(
            runCatching { castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName }
                .getOrNull(),
        )
    }
    val castDeviceNameState by rememberUpdatedState(castDeviceName)

    val confirmedAudioOnly = castDeviceName == null &&
        hasPlayedCurrentChannel &&
        errorText == null &&
        !firstVideoFrameRendered &&
        !hasVideoSize &&
        ((hasAudioTrack && !hasPlayableVideoTrack) || (isLikelyAudio && !hasPlayableVideoTrack))

    val canGoPrevious = !isMixMode || mixIndex > 0

    BackHandler(enabled = !isInPipMode) {
        MobilePlaybackService.stop(context)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onBack()
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            MobilePlaybackService.stop(context)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(castContext) {
        val manager = castContext?.sessionManager
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                castDeviceName = session.castDevice?.friendlyName ?: "Chromecast"
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                castDeviceName = null
            }
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionEnded(session: CastSession, error: Int) {
                castDeviceName = null
            }
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                castDeviceName = session.castDevice?.friendlyName ?: "Chromecast"
            }
            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                castDeviceName = null
            }
            override fun onSessionSuspended(session: CastSession, reason: Int) {}
        }
        runCatching { manager?.addSessionManagerListener(listener, CastSession::class.java) }
        onDispose {
            runCatching { manager?.removeSessionManagerListener(listener, CastSession::class.java) }
        }
    }

    val loadErrorPolicy = remember {
        MobileResilientLoadErrorHandlingPolicy { safetyBufferEnabledState }
    }
    val dataSourceFactory = remember(context) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(20000)
        DefaultDataSource.Factory(context, httpFactory)
    }
    val exoPlayer = remember {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorPolicy)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 40_000, 2_000, 4_000)
            .setBackBuffer(10_000, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build()
    }

    fun startStream(url: String): Result<Unit> = runCatching {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.playbackParameters = PlaybackParameters(1.0f, 1.0f)
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
            val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorPolicy)
                .setAllowChunklessPreparation(false)
                .createMediaSource(mediaItem)
            exoPlayer.setMediaSource(hlsSource)
        } else {
            exoPlayer.setMediaItem(mediaItem)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun scheduleAutoReconnect(reason: String) {
        if (castDeviceNameState != null) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoffs = longArrayOf(700L, 1500L, 2500L, 4000L, 6000L, 8000L)
            if (reconnectAttempt < backoffs.size) {
                val delayMs = backoffs[reconnectAttempt]
                reconnectAttempt += 1
                reconnecting = true
                isBuffering = false
                errorText = null
                delay(delayMs)
                if (!isActive) return@launch
                val url = currentStreamUrlState
                if (url.isBlank()) {
                    reconnecting = false
                    errorText = tr(
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
                isBuffering = false
                errorText = tr(
                    "Flusso temporaneamente interrotto. Riconnessione automatica in attesa…",
                    "Stream temporarily interrupted. Background reconnect waiting…",
                    "Flujo interrumpido temporalmente. Reconexión en segundo plano…",
                )
                delay(15_000L)
                if (!isActive || errorText == null) return@launch
                reconnectAttempt = 0
                val url = currentStreamUrlState
                if (url.isBlank()) return@launch
                reconnecting = true
                errorText = null
                startStream(url)
            }
        }
    }

    LaunchedEffect(channel.streamUrl, castDeviceName) {
        reconnectJob?.cancel()
        reconnectAttempt = 0
        reconnecting = false
        hasPlayedCurrentChannel = false
        firstVideoFrameRendered = false
        tracksState = Tracks.EMPTY
        videoSizeState = VideoSize.UNKNOWN
        errorText = null
        showControls = true
        val activeCast = runCatching { castContext?.sessionManager?.currentCastSession }.getOrNull()
        if (castDeviceName != null && activeCast != null) {
            isBuffering = false
            exoPlayer.pause()
            exoPlayer.stop()
            loadChannelOnCast(activeCast, channel)
        } else {
            isBuffering = true
            val res = startStream(channel.streamUrl)
            if (res.isFailure) {
                scheduleAutoReconnect("start_failed")
            }
        }
    }

    LaunchedEffect(exoPlayer, channel.streamUrl, castDeviceName, liveSafetyBufferEnabled) {
        if (castDeviceName != null) {
            runCatching { exoPlayer.playbackParameters = PlaybackParameters(1.0f, 1.0f) }
            return@LaunchedEffect
        }
        var lastWatchPos = -1L
        var lastAdvanceMs = System.currentTimeMillis()
        var bufferingSinceMs = 0L
        var readySinceMs = 0L
        var lastCushionSpeedChangeMs = 0L
        var cushionBuilding = false

        while (isActive) {
            delay(1000)
            val now = System.currentTimeMillis()
            val pState = exoPlayer.playbackState
            val isReady = pState == Player.STATE_READY && exoPlayer.playWhenReady

            if (isReady && errorText == null && !reconnecting) {
                if (readySinceMs == 0L) readySinceMs = now
                if (reconnectAttempt > 0 && now - readySinceMs >= 6_000L) {
                    reconnectAttempt = 0
                }
            } else {
                readySinceMs = 0L
            }

            if (pState == Player.STATE_BUFFERING && errorText == null && !reconnecting && currentStreamUrlState.isNotBlank()) {
                if (bufferingSinceMs == 0L) bufferingSinceMs = now
                if (now - bufferingSinceMs > 12_000L) {
                    bufferingSinceMs = now
                    scheduleAutoReconnect("buffering_timeout")
                }
            } else {
                bufferingSinceMs = 0L
            }

            if (isReady && errorText == null && !reconnecting && currentStreamUrlState.isNotBlank()) {
                val pos = exoPlayer.currentPosition
                if (pos == lastWatchPos) {
                    if (now - lastAdvanceMs > 6_000L) {
                        lastAdvanceMs = now
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

            if (isReady) {
                if (!liveSafetyBufferEnabled || !exoPlayer.isCurrentMediaItemLive) {
                    cushionBuilding = false
                    if (kotlin.math.abs(exoPlayer.playbackParameters.speed - 1.0f) > 0.002f) {
                        runCatching { exoPlayer.playbackParameters = PlaybackParameters(1.0f, 1.0f) }
                    }
                } else {
                    val bufferedAheadMs = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L)
                    val windowDurationMs = run {
                        val timeline = exoPlayer.currentTimeline
                        if (!timeline.isEmpty && exoPlayer.currentMediaItemIndex in 0 until timeline.windowCount) {
                            val w = Timeline.Window()
                            timeline.getWindow(exoPlayer.currentMediaItemIndex, w)
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
                    val currentSpeed = exoPlayer.playbackParameters.speed
                    if (kotlin.math.abs(currentSpeed - targetSpeed) > 0.003f &&
                        (targetSpeed == 1.0f || now - lastCushionSpeedChangeMs >= 3_000L)
                    ) {
                        runCatching {
                            exoPlayer.playbackParameters = PlaybackParameters(targetSpeed, 1.0f)
                            lastCushionSpeedChangeMs = now
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(channel.name, channel.subtitle, backgroundPlayEnabled, castDeviceName) {
        if (backgroundPlayEnabled && castDeviceName == null) {
            MobilePlaybackService.startOrUpdate(context, channel.name, channel.subtitle)
        } else {
            MobilePlaybackService.stop(context)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING && !reconnecting
                if (playbackState == Player.STATE_READY) {
                    hasPlayedCurrentChannel = true
                    reconnecting = false
                    errorText = null
                } else if (playbackState == Player.STATE_ENDED && (exoPlayer.isCurrentMediaItemLive || hasPlayedCurrentChannel)) {
                    scheduleAutoReconnect("state_ended")
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    reconnecting = false
                    errorText = null
                    hasPlayedCurrentChannel = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    runCatching {
                        exoPlayer.seekToDefaultPosition()
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }
                    return
                }
                scheduleAutoReconnect(error.errorCodeName)
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
        exoPlayer.addListener(listener)
        exoPlayer.setHandleAudioBecomingNoisy(true)
        onDispose {
            reconnectJob?.cancel()
            exoPlayer.removeListener(listener)
            runCatching {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            }
        }
    }

    DisposableEffect(lifecycleOwner, backgroundPlayEnabled, isInPipMode, castDeviceName) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (!backgroundPlayEnabled && !isInPipMode && castDeviceName == null) {
                        runCatching { exoPlayer.pause() }
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (castDeviceName == null && errorText == null && !exoPlayer.isPlaying) {
                        runCatching { exoPlayer.play() }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(showControls, isPlaying, channel.streamUrl, isInPipMode) {
        if (showControls && isPlaying && !isInPipMode) {
            delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(confirmedAudioOnly, channel.streamUrl, isMixMode, mixIncludeAudioOnly) {
        if (confirmedAudioOnly && isMixMode && !mixIncludeAudioOnly && audioSkipCount < 10) {
            audioSkipCount += 1
            onAudioOnlyDetectedInMix(channel.streamUrl)
        }
    }

    LaunchedEffect(isMixMode) {
        if (!isMixMode) audioSkipCount = 0
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(15_000L)
            clockTick++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (!isInPipMode) showControls = !showControls
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    player = exoPlayer
                    this.resizeMode = resizeMode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = confirmedAudioOnly && !isBuffering && errorText == null && castDeviceName == null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            MobileAudioOnlyStage(
                channel = channel,
                countryDisplay = countryDisplay,
                audioLabel = tr("SOLO AUDIO", "AUDIO ONLY", "SOLO AUDIO"),
                compactPip = isInPipMode,
            )
        }

        if (castDeviceName != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE0050A12))
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.CastConnected,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = channel.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Chromecast · $castDeviceName",
                    color = Color(0xFF7DD3FC),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (isBuffering && errorText == null && castDeviceName == null && !reconnecting) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (reconnecting && errorText == null && castDeviceName == null && !isInPipMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xD80A1322))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = tr("Riconnessione in corso…", "Reconnecting…", "Reconectando…"),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (errorText != null && !isInPipMode && castDeviceName == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = errorText ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onPreviousChannel,
                        enabled = canGoPrevious,
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Canale precedente",
                            tint = if (canGoPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                        )
                    }
                    Button(
                        onClick = {
                            reconnectJob?.cancel()
                            reconnectAttempt = 0
                            reconnecting = false
                            errorText = null
                            isBuffering = true
                            val res = startStream(channel.streamUrl)
                            if (res.isFailure) {
                                scheduleAutoReconnect("manual_retry_failed")
                            }
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(strings.retry)
                    }
                    OutlinedButton(onClick = onNextChannel) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Canale seguente",
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        if (showControls && !isInPipMode) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        MobilePlaybackService.stop(context)
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        onBack()
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        tint = Color.White,
                    )
                }
                if (isMixMode) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "MIX · ${mixIndex + 1}/${mixTotal.coerceAtLeast(1)}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            countryDisplay.takeIf { it.isNotBlank() },
                            channel.subtitle.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { showCastPickerDialog(context, castContext) },
                ) {
                    Icon(
                        imageVector = if (castDeviceName != null) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = "Chromecast",
                        tint = if (castDeviceName != null) Color(0xFF38BDF8) else Color.White,
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (channel.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Preferito",
                        tint = if (channel.favorite) Color(0xFFFF5C5C) else Color.White,
                    )
                }
            }

            // Center Previous / Play-Pause / Next
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        audioSkipCount = 0
                        onPreviousChannel()
                    },
                    enabled = canGoPrevious,
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Canale precedente",
                        tint = if (canGoPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(
                    onClick = {
                        val activeCast = runCatching { castContext?.sessionManager?.currentCastSession }.getOrNull()
                        if (castDeviceName != null && activeCast?.remoteMediaClient != null) {
                            activeCast.remoteMediaClient?.togglePlayback()
                        } else {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(
                    onClick = {
                        audioSkipCount = 0
                        onNextChannel()
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Canale seguente",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            // Bottom bar: Country Name + Dual Clocks (left) and Player Controls (right)
            val localTime = remember(clockTick) { formatLocalTime() }
            val countryTime = remember(channel.country, clockTick) { formatCountryTimeOrNull(channel.country) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (countryTime != null) {
                        val countryTimeLabel = listOfNotNull(
                            countryFlag.takeIf { it.isNotBlank() },
                            countryName.takeIf { it.isNotBlank() }?.uppercase() ?: channel.country?.uppercase(),
                        ).joinToString(" ").ifBlank { tr("PAESE", "COUNTRY", "PAÍS") }
                        Column {
                            Text(
                                text = countryTimeLabel,
                                color = Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp),
                            )
                            Text(
                                text = countryTime,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(Color.White.copy(alpha = 0.2f)),
                        )
                    }
                    Column {
                        Text(
                            text = tr("ORA LOCALE", "LOCAL TIME", "HORA LOCAL"),
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            text = localTime,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onToggleBackgroundPlay(!backgroundPlayEnabled) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "Riproduzione in background",
                        tint = if (backgroundPlayEnabled) Color(0xFF00D4FF) else Color.White.copy(alpha = 0.65f),
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    IconButton(onClick = onEnterPip) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture in Picture",
                            tint = Color.White,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.AspectRatio,
                        contentDescription = "Formato schermo",
                        tint = Color.White,
                    )
                }
                IconButton(
                    onClick = {
                        isLandscape = !isLandscape
                        activity?.requestedOrientation = if (isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.ScreenRotation,
                        contentDescription = "Ruota schermo",
                        tint = Color.White,
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun MobileAudioOnlyStage(
    channel: MobileChannel,
    countryDisplay: String,
    audioLabel: String,
    compactPip: Boolean,
) {
    var logoFailed by remember(channel.logo, channel.id) { mutableStateOf(false) }
    val catParts = listOfNotNull(
        countryDisplay.takeIf { it.isNotBlank() },
        channel.category.takeIf { it.isNotBlank() && !it.equals(countryDisplay, ignoreCase = true) },
    ).joinToString(" · ")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF0D213B), Color(0xFF060D19), Color(0xFF03060C)),
                ),
            )
            .padding(if (compactPip) 12.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(
                        width = if (compactPip) 96.dp else 184.dp,
                        height = if (compactPip) 68.dp else 124.dp,
                    )
                    .clip(RoundedCornerShape(if (compactPip) 14.dp else 24.dp))
                    .background(Color(0xE6101F35))
                    .border(1.5.dp, Color(0x5938BDF8), RoundedCornerShape(if (compactPip) 14.dp else 24.dp))
                    .padding(if (compactPip) 10.dp else 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logo.isNullOrBlank() && !logoFailed) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onError = { logoFailed = true },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(if (compactPip) 40.dp else 64.dp)
                            .clip(CircleShape)
                            .background(Color(0x2638BDF8))
                            .border(1.dp, Color(0x6638BDF8), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = channelInitials(channel.name),
                            color = Color(0xFF38BDF8),
                            fontSize = if (compactPip) 15.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (!compactPip) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = channel.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0x2638BDF8))
                            .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8)),
                        )
                        Text(
                            text = audioLabel,
                            color = Color(0xFF7DD3FC),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                    }
                    if (catParts.isNotBlank()) {
                        Text(
                            text = catParts,
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun showCastPickerDialog(context: Context, castContext: CastContext?) {
    runCatching {
        val themedCtx = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        val currentSession = castContext?.sessionManager?.currentCastSession
        if (currentSession != null) {
            MediaRouteControllerDialog(themedCtx).show()
            return
        }
        val selector = castContext?.mergedSelector ?: MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
                ),
            )
            .build()
        MediaRouteChooserDialog(themedCtx).apply {
            routeSelector = selector
            show()
        }
    }
}

private fun loadChannelOnCast(castSession: CastSession, channel: MobileChannel) {
    val client = castSession.remoteMediaClient ?: return
    val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
        putString(MediaMetadata.KEY_TITLE, channel.name)
        putString(MediaMetadata.KEY_SUBTITLE, channel.subtitle)
        channel.logo?.takeIf { it.isNotBlank() }?.let { logoUrl ->
            addImage(WebImage(Uri.parse(logoUrl)))
        }
    }
    val urlLower = channel.streamUrl.lowercase()
    val contentType = when {
        urlLower.contains(".m3u8") || urlLower.contains("m3u8") -> "application/x-mpegURL"
        urlLower.contains(".mpd") -> "application/dash+xml"
        else -> "video/mp2t"
    }
    val mediaInfo = MediaInfo.Builder(channel.streamUrl)
        .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
        .setContentType(contentType)
        .setMetadata(metadata)
        .build()
    val request = MediaLoadRequestData.Builder()
        .setMediaInfo(mediaInfo)
        .setAutoplay(true)
        .build()
    runCatching { client.load(request) }
}

private class MobileResilientLoadErrorHandlingPolicy(
    private val cushionEnabledProvider: () -> Boolean,
) : DefaultLoadErrorHandlingPolicy() {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        if (cushionEnabledProvider()) 8 else 4

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val ex = loadErrorInfo.exception
        if (ex is ParserException || ex is HttpDataSource.CleartextNotPermittedException) {
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
