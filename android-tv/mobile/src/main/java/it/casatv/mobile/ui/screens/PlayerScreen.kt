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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.media3.common.TrackSelectionOverride
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
import it.casatv.mobile.data.MobileRecordingDto
import it.casatv.mobile.data.MobileStreamSource
import it.casatv.mobile.service.MobilePlaybackService
import it.casatv.mobile.ui.LocalMobileStrings
import it.casatv.mobile.ui.channelInitials
import it.casatv.mobile.ui.components.CountryFlagIcon
import it.casatv.mobile.ui.components.resolveMobileFlagAssetCode
import it.casatv.mobile.ui.formatCountryTimeOrNull
import it.casatv.mobile.ui.formatLocalTime
import it.casatv.mobile.ui.isLikelyAudioOnlyChannel
import it.casatv.mobile.ui.resolveMobileCountryName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: MobileChannel,
    sources: List<MobileStreamSource> = emptyList(),
    activeSourceIndex: Int = 0,
    onSelectSource: (Int) -> Unit = {},
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
    onSaveVodProgress: (Long, Long) -> Unit = { _, _ -> },
    nextEpisode: MobileChannel? = null,
    onPlayNextEpisode: () -> Unit = {},
    onStartRecording: (suspend () -> MobileRecordingDto?)? = null,
    onStopRecording: (suspend (String) -> MobileRecordingDto?)? = null,
    onReportSourceEvent: (String, String) -> Unit = { _, _ -> },
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
    var reconnectPending by remember { mutableStateOf(false) }
    var hasPlayedCurrentChannel by remember { mutableStateOf(false) }
    var reconnectAttempt by remember { mutableIntStateOf(0) }
    var bgRetryCycle by remember { mutableIntStateOf(0) }
    var reconnectJob by remember { mutableStateOf<Job?>(null) }
    val softResyncTimes = remember { kotlin.collections.ArrayDeque<Long>() }
    var pausedByLifecycle by remember { mutableStateOf(false) }
    val tr: (String, String, String) -> String = { itText, enText, esText ->
        when (strings.lang.lowercase()) {
            "it" -> itText
            "es" -> esText
            else -> enText
        }
    }
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showTracksSheet by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var brightnessFraction by remember {
        val current = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (current in 0f..1f) current else 0.7f)
    }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isLandscape by remember { mutableStateOf(false) }
    var tracksState by remember { mutableStateOf(Tracks.EMPTY) }
    var videoSizeState by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var firstVideoFrameRendered by remember { mutableStateOf(false) }
    var audioSkipCount by remember { mutableIntStateOf(0) }
    var clockTick by remember { mutableIntStateOf(0) }
    val isVod = channel.isVodContent
    val latestChannel by rememberUpdatedState(channel)
    val latestIsVod by rememberUpdatedState(isVod)
    val latestNextEpisode by rememberUpdatedState(nextEpisode)
    val latestOnSaveVodProgress by rememberUpdatedState(onSaveVodProgress)
    var nextEpRemainingMs by remember(channel.id, nextEpisode?.id) { mutableLongStateOf(60_000L) }
    var nextEpDismissed by remember(channel.id, nextEpisode?.id) { mutableStateOf(false) }
    var nextEpTriggered by remember(channel.id, nextEpisode?.id) { mutableStateOf(false) }
    var activeRecording by remember(channel.id) { mutableStateOf<MobileRecordingDto?>(null) }
    var recHoldActive by remember(channel.id) { mutableStateOf(false) }
    var recHoldElapsedMs by remember(channel.id) { mutableLongStateOf(0L) }
    var recBusy by remember(channel.id) { mutableStateOf(false) }
    val latestOnStartRecording by rememberUpdatedState(onStartRecording)
    val latestOnStopRecording by rememberUpdatedState(onStopRecording)
    var useDirectFallback by remember(channel.streamUrl, channel.vodStreamUrl) { mutableStateOf(false) }
    val activePlayUrl = remember(channel.streamUrl, channel.vodStreamUrl, isVod, useDirectFallback) {
        if (isVod && !useDirectFallback && !channel.vodStreamUrl.isNullOrBlank()) channel.vodStreamUrl else channel.streamUrl
    }
    var vodPosMs by remember { mutableLongStateOf(channel.positionMs) }
    var vodDurMs by remember { mutableLongStateOf(channel.durationMs) }
    var vodSeekApplied by remember { mutableStateOf(false) }
    var vodSavedPosMs by remember { mutableLongStateOf(0L) }
    val currentStreamUrlState by rememberUpdatedState(activePlayUrl)

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

    val countryName = remember(channel.country, channel.countryName, strings.lang) {
        resolveMobileCountryName(channel.country, channel.countryName, strings.lang)
    }
    val countryDisplay = countryName

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

    val confirmedAudioOnlyRaw = castDeviceName == null &&
        hasPlayedCurrentChannel &&
        errorText == null &&
        !firstVideoFrameRendered &&
        !hasVideoSize &&
        ((hasAudioTrack && !hasPlayableVideoTrack) || (isLikelyAudio && !hasPlayableVideoTrack))
    var confirmedAudioOnly by remember(channel.streamUrl) { mutableStateOf(false) }

    LaunchedEffect(confirmedAudioOnlyRaw, channel.streamUrl) {
        if (!confirmedAudioOnlyRaw) {
            confirmedAudioOnly = false
        } else {
            delay(3000L)
            confirmedAudioOnly = true
        }
    }

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
            activity?.window?.let { win ->
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val lp = win.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = lp
            }
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

    fun startStream(url: String, isVodStream: Boolean = latestIsVod): Result<Unit> = runCatching {
        if (!isVodStream) {
            exoPlayer.playbackParameters = PlaybackParameters.DEFAULT
        } else {
            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed, 1.0f)
        }
        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        if (!isVodStream) {
            mediaItemBuilder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMaxPlaybackSpeed(1.03f)
                    .setMinPlaybackSpeed(0.97f)
                    .setTargetOffsetMs(C.TIME_UNSET)
                    .build(),
            )
        }
        val mediaItem = mediaItemBuilder.build()
        if (url.contains(".m3u8", ignoreCase = true) || url.contains("m3u8", ignoreCase = true)) {
            val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorPolicy)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
            exoPlayer.setMediaSource(hlsSource, true)
        } else {
            exoPlayer.setMediaItem(mediaItem, true)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun scheduleAutoReconnect(reason: String) {
        if (castDeviceNameState != null) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val curCh = latestChannel
            val vodNow = latestIsVod
            if (vodNow && !useDirectFallback && currentStreamUrlState != curCh.streamUrl && curCh.streamUrl.isNotBlank()) {
                val savedPos = runCatching { exoPlayer.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
                useDirectFallback = true
                reconnectPending = false
                reconnecting = false
                errorText = null
                val res = startStream(curCh.streamUrl, isVodStream = true)
                if (savedPos > 0L) runCatching { exoPlayer.seekTo(savedPos) }
                if (res.isFailure) scheduleAutoReconnect("direct_failover_failed")
                return@launch
            }
            val backoffs = longArrayOf(700L, 1500L, 2500L, 4000L, 6000L, 8000L)
            if (reconnectAttempt < backoffs.size) {
                val delayMs = backoffs[reconnectAttempt]
                reconnectAttempt += 1
                reconnectPending = true
                reconnecting = true
                isBuffering = false
                errorText = null
                delay(delayMs)
                if (!isActive) return@launch
                val url = currentStreamUrlState
                if (url.isBlank()) {
                    reconnectPending = false
                    reconnecting = false
                    errorText = tr(
                        "Questo canale non ha un flusso valido o disponibile.",
                        "This channel has no valid or available stream.",
                        "Este canal no tiene un flujo válido o disponible.",
                    )
                    return@launch
                }
                reconnectPending = false
                val res = startStream(url, isVodStream = vodNow)
                if (res.isFailure) {
                    scheduleAutoReconnect("start_failed")
                }
            } else if (bgRetryCycle < 4) {
                bgRetryCycle += 1
                reconnectPending = true
                reconnecting = false
                isBuffering = false
                errorText = tr(
                    "Flusso temporaneamente interrotto. Riconnessione automatica in attesa…",
                    "Stream temporarily interrupted. Background reconnect waiting…",
                    "Flujo interrumpido temporalmente. Reconexión en segundo plano…",
                )
                delay(15_000L)
                if (!isActive || errorText == null) {
                    reconnectPending = false
                    return@launch
                }
                reconnectAttempt = 0
                val url = currentStreamUrlState
                if (url.isBlank()) {
                    reconnectPending = false
                    return@launch
                }
                reconnectPending = false
                reconnecting = true
                errorText = null
                val res = startStream(url, isVodStream = vodNow)
                if (res.isFailure) {
                    scheduleAutoReconnect("background_retry_failed")
                }
            } else {
                reconnectPending = false
                reconnecting = false
                isBuffering = false
                errorText = tr(
                    "Flusso non raggiungibile al momento. Tocca Riprova.",
                    "Stream unreachable right now. Tap Retry.",
                    "Flujo no disponible en este momento. Pulsa Reintentar.",
                )
            }
        }
    }

    LaunchedEffect(channel.streamUrl, activePlayUrl, castDeviceName) {
        reconnectJob?.cancel()
        reconnectAttempt = 0
        bgRetryCycle = 0
        reconnectPending = false
        reconnecting = false
        softResyncTimes.clear()
        vodPosMs = channel.positionMs
        vodDurMs = channel.durationMs
        vodSeekApplied = false
        vodSavedPosMs = 0L
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
            val res = startStream(activePlayUrl, isVodStream = isVod)
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

            if (isReady && errorText == null && !reconnectPending) {
                if (readySinceMs == 0L) readySinceMs = now
                if (reconnectAttempt > 0 && now - readySinceMs >= 6_000L) {
                    reconnectAttempt = 0
                }
            } else {
                readySinceMs = 0L
            }

            if (pState == Player.STATE_BUFFERING && errorText == null && !reconnectPending && currentStreamUrlState.isNotBlank()) {
                if (bufferingSinceMs == 0L) bufferingSinceMs = now
                val bufLimitMs = if (latestIsVod) 30_000L else 20_000L
                if (now - bufferingSinceMs > bufLimitMs) {
                    bufferingSinceMs = now
                    scheduleAutoReconnect("buffering_timeout")
                }
            } else {
                bufferingSinceMs = 0L
            }

            if (isReady && errorText == null && !reconnectPending && currentStreamUrlState.isNotBlank()) {
                val pos = exoPlayer.currentPosition
                if (pos == lastWatchPos) {
                    if (now - lastAdvanceMs > 10_000L) {
                        lastAdvanceMs = now
                        while (softResyncTimes.isNotEmpty() && now - softResyncTimes.first() > 30_000L) {
                            softResyncTimes.removeFirst()
                        }
                        if (!latestIsVod && hasPlayedCurrentChannel && exoPlayer.isCurrentMediaItemLive && softResyncTimes.isEmpty()) {
                            softResyncTimes.addLast(now)
                            runCatching {
                                exoPlayer.seekToDefaultPosition()
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                            }
                        } else {
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

            if (isReady && !latestIsVod) {
                val windowDurationMs = run {
                    val timeline = exoPlayer.currentTimeline
                    if (!timeline.isEmpty && exoPlayer.currentMediaItemIndex in 0 until timeline.windowCount) {
                        val w = Timeline.Window()
                        timeline.getWindow(exoPlayer.currentMediaItemIndex, w)
                        if (w.durationMs != C.TIME_UNSET && w.durationMs > 0L) w.durationMs else 0L
                    } else 0L
                }
                val rawLiveOffset = exoPlayer.currentLiveOffset.takeIf { it != C.TIME_UNSET }
                val pos = exoPlayer.currentPosition
                val effectiveOffsetMs = rawLiveOffset ?: if (windowDurationMs > 0L && pos >= 0L) {
                    (windowDurationMs - pos).coerceAtLeast(0L)
                } else {
                    -1L
                }
                val windowSafeForCushion = windowDurationMs >= 35_000L &&
                    effectiveOffsetMs > 0L &&
                    effectiveOffsetMs < (windowDurationMs * 40L) / 100L
                if (!liveSafetyBufferEnabled || !exoPlayer.isCurrentMediaItemLive || !windowSafeForCushion) {
                    cushionBuilding = false
                    if (kotlin.math.abs(exoPlayer.playbackParameters.speed - 1.0f) > 0.002f) {
                        runCatching { exoPlayer.playbackParameters = PlaybackParameters(1.0f, 1.0f) }
                    }
                } else {
                    val bufferedAheadMs = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L)
                    val effectiveTargetMs = minOf(8_000L, (windowDurationMs * 25L) / 100L).coerceAtLeast(4_000L)
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

    LaunchedEffect(channel.id, activePlayUrl, isVod) {
        if (!isVod && activePlayUrl.isNotBlank()) {
            delay(30_000L)
            if (isActive && errorText == null && !isBuffering && hasPlayedCurrentChannel) {
                onReportSourceEvent("healthy", channel.streamUrl)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        var bufStartAt = 0L
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING && !reconnecting
                if (playbackState == Player.STATE_BUFFERING && hasPlayedCurrentChannel && !reconnecting && !latestIsVod) {
                    if (bufStartAt == 0L) bufStartAt = System.currentTimeMillis()
                } else {
                    if (playbackState == Player.STATE_READY && bufStartAt > 0L && !latestIsVod) {
                        val bufMs = System.currentTimeMillis() - bufStartAt
                        if (bufMs >= 2_500L) {
                            onReportSourceEvent("stall", latestChannel.streamUrl)
                        }
                    }
                    bufStartAt = 0L
                }
                if (playbackState == Player.STATE_READY) {
                    hasPlayedCurrentChannel = true
                    bgRetryCycle = 0
                    reconnecting = false
                    reconnectPending = false
                    errorText = null
                    val curCh = latestChannel
                    if (latestIsVod && !vodSeekApplied) {
                        vodSeekApplied = true
                        val dur = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                        if (dur > 0L) vodDurMs = dur
                        if (curCh.positionMs > 15_000L && (dur <= 0L || curCh.positionMs < dur - 15_000L)) {
                            runCatching { exoPlayer.seekTo(curCh.positionMs) }
                            vodPosMs = curCh.positionMs
                        }
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    if (latestIsVod) {
                        val dur = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: vodDurMs
                        if (dur > 0L) latestOnSaveVodProgress(dur, dur)
                        if (latestNextEpisode != null && !nextEpTriggered) {
                            nextEpTriggered = true
                            onPlayNextEpisode()
                        }
                        return
                    }
                    if (exoPlayer.isCurrentMediaItemLive || hasPlayedCurrentChannel) {
                        scheduleAutoReconnect("state_ended")
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    reconnecting = false
                    reconnectPending = false
                    bgRetryCycle = 0
                    errorText = null
                    hasPlayedCurrentChannel = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                if (!latestIsVod && latestChannel.streamUrl.isNotBlank()) {
                    onReportSourceEvent("error", latestChannel.streamUrl)
                }
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    val now = System.currentTimeMillis()
                    while (softResyncTimes.isNotEmpty() && now - softResyncTimes.first() > 30_000L) {
                        softResyncTimes.removeFirst()
                    }
                    if (softResyncTimes.size < 2) {
                        softResyncTimes.addLast(now)
                        isBuffering = true
                        runCatching {
                            exoPlayer.seekToDefaultPosition()
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        }
                        return
                    }
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
            if (latestIsVod) {
                val p = runCatching { exoPlayer.currentPosition }.getOrDefault(vodPosMs).coerceAtLeast(0L)
                val d = runCatching { exoPlayer.duration }.getOrDefault(vodDurMs).takeIf { it != C.TIME_UNSET && it > 0L } ?: vodDurMs
                if (p > 3_000L) latestOnSaveVodProgress(p, d)
            }
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
                    if (!backgroundPlayEnabled && !isInPipMode && castDeviceName == null && exoPlayer.playWhenReady) {
                        pausedByLifecycle = true
                        runCatching { exoPlayer.pause() }
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (pausedByLifecycle && castDeviceName == null && errorText == null && !exoPlayer.isPlaying) {
                        pausedByLifecycle = false
                        runCatching {
                            if (!latestIsVod) exoPlayer.seekToDefaultPosition()
                            exoPlayer.play()
                        }
                    } else {
                        pausedByLifecycle = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isVod, activePlayUrl, castDeviceName, nextEpisode?.id) {
        if (!isVod || castDeviceName != null) return@LaunchedEffect
        while (isActive) {
            val fastTick = nextEpisode != null && !nextEpDismissed && vodDurMs > 65_000L && (vodDurMs - vodPosMs) <= 65_000L
            delay(if (fastTick) 120L else 500L)
            val pos = runCatching { exoPlayer.currentPosition }.getOrDefault(0L).coerceAtLeast(0L)
            val dur = runCatching { exoPlayer.duration }.getOrDefault(C.TIME_UNSET).takeIf { it != C.TIME_UNSET && it > 0L } ?: vodDurMs
            vodPosMs = pos
            if (dur > 0L) {
                vodDurMs = dur
                if (nextEpisode != null && dur > 65_000L) {
                    val rem = (dur - pos).coerceAtLeast(0L)
                    nextEpRemainingMs = rem
                    if (rem <= 250L && !nextEpTriggered && !nextEpDismissed) {
                        nextEpTriggered = true
                        onPlayNextEpisode()
                    }
                }
            }
            if (pos > 3_000L && kotlin.math.abs(pos - vodSavedPosMs) >= 10_000L) {
                vodSavedPosMs = pos
                onSaveVodProgress(pos, dur)
            }
        }
    }

    LaunchedEffect(showControls, isPlaying, channel.streamUrl, isInPipMode, showTracksSheet) {
        if (showControls && isPlaying && !isInPipMode && !showTracksSheet) {
            delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(recHoldActive) {
        if (!recHoldActive) {
            recHoldElapsedMs = 0L
            return@LaunchedEffect
        }
        val started = android.os.SystemClock.elapsedRealtime()
        while (isActive && recHoldActive) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            recHoldElapsedMs = elapsed
            if (elapsed >= 3_000L) {
                recHoldActive = false
                recHoldElapsedMs = 0L
                val starter = latestOnStartRecording
                if (starter != null && !recBusy && activeRecording == null) {
                    recBusy = true
                    val startedRec = runCatching { starter() }.getOrNull()
                    activeRecording = startedRec
                    recBusy = false
                }
                break
            }
            delay(50L)
        }
    }

    LaunchedEffect(playbackSpeed, exoPlayer) {
        runCatching { exoPlayer.setPlaybackParameters(PlaybackParameters(playbackSpeed)) }
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
                    .background(Color(0xF0000000))
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.CastConnected,
                    contentDescription = null,
                    tint = Color.White,
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
                    color = Color(0xFFA3A3A3),
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
                            bgRetryCycle = 0
                            reconnectPending = false
                            reconnecting = false
                            errorText = null
                            isBuffering = true
                            val res = startStream(activePlayUrl, isVodStream = isVod)
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

        if (showControls && isLocked && !isInPipMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 26.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xD814161D))
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(999.dp))
                    .clickable { isLocked = false }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    text = tr("Sblocca schermo", "Unlock screen", "Desbloquear pantalla"),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (showControls && !isLocked && !isInPipMode) {
            val episodeSubText = remember(channel, countryDisplay) {
                val se = if (channel.seasonNumber != null && channel.episodeNumber != null) {
                    val epTitle = channel.episodeTitle?.takeIf { it.isNotBlank() }?.let { " \"$it\"" } ?: ""
                    "S${channel.seasonNumber}:E${channel.episodeNumber}$epTitle"
                } else {
                    channel.subtitle.takeIf { it.isNotBlank() }
                }
                listOfNotNull(se, countryDisplay.takeIf { it.isNotBlank() && !isVod }).joinToString(" · ")
            }
            val displayHeaderTitle = channel.seriesTitle?.takeIf { it.isNotBlank() } ?: channel.name

            // Minimal top scrim
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xCC000000), Color(0x66000000), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        MobilePlaybackService.stop(context)
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        onBack()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(6.dp))
                if (isMixMode) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF262626), RoundedCornerShape(4.dp))
                            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
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
                        text = displayHeaderTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (episodeSubText.isNotBlank() || (!isVod && resolveMobileFlagAssetCode(channel.country) != null)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (!isVod && resolveMobileFlagAssetCode(channel.country) != null) {
                                CountryFlagIcon(
                                    countryCode = channel.country,
                                    width = 16.dp,
                                    height = 11.dp,
                                    cornerRadius = 2.dp,
                                )
                            }
                            if (episodeSubText.isNotBlank()) {
                                Text(
                                    text = episodeSubText,
                                    color = Color.White.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = { isLocked = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = tr("Blocca", "Lock", "Bloquear"),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { showCastPickerDialog(context, castContext) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (castDeviceName != null) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = "Chromecast",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (channel.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Preferito",
                        tint = if (channel.favorite) Color(0xFFE50914) else Color.White,
                    )
                }
                IconButton(
                    onClick = {
                        MobilePlaybackService.stop(context)
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        onBack()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
                }
            }

            // Left vertical brightness bar (matches mobile reference)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.BrightnessHigh,
                    contentDescription = tr("Luminosità", "Brightness", "Brillo"),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp),
                )
                Box(
                    modifier = Modifier.size(width = 32.dp, height = 136.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Slider(
                        value = brightnessFraction,
                        onValueChange = { v ->
                            val clamped = v.coerceIn(0.05f, 1f)
                            brightnessFraction = clamped
                            activity?.window?.let { win ->
                                val lp = win.attributes
                                lp.screenBrightness = clamped
                                win.attributes = lp
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0x4DFFFFFF),
                        ),
                        modifier = Modifier
                            .width(136.dp)
                            .height(28.dp)
                            .graphicsLayer {
                                rotationZ = -90f
                                transformOrigin = TransformOrigin.Center
                            },
                    )
                }
            }

            // Clean center transport: ↺10 / Play-Pause / 10↻ (or Prev / Play / Next for Live)
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val seekVodDelta: (Long) -> Unit = { deltaMs ->
                    val dur = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: vodDurMs
                    val maxPos = if (dur > 0L) dur else (exoPlayer.currentPosition + deltaMs.coerceAtLeast(0L))
                    val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, maxPos.coerceAtLeast(0L))
                    runCatching { exoPlayer.seekTo(target) }
                    vodPosMs = target
                }
                if (isVod) {
                    IconButton(onClick = { seekVodDelta(-10_000L) }, modifier = Modifier.size(54.dp)) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "-10s",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            audioSkipCount = 0
                            onPreviousChannel()
                        },
                        enabled = canGoPrevious,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Canale precedente",
                            tint = if (canGoPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val activeCast = runCatching { castContext?.sessionManager?.currentCastSession }.getOrNull()
                        if (castDeviceName != null && activeCast?.remoteMediaClient != null) {
                            activeCast.remoteMediaClient?.togglePlayback()
                        } else {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                if (isVod) {
                                    val p = runCatching { exoPlayer.currentPosition }.getOrDefault(vodPosMs)
                                    val d = runCatching { exoPlayer.duration }.getOrDefault(vodDurMs).takeIf { it != C.TIME_UNSET && it > 0L } ?: vodDurMs
                                    if (p > 3_000L) onSaveVodProgress(p, d)
                                }
                            } else {
                                exoPlayer.play()
                            }
                        }
                    },
                    modifier = Modifier.size(68.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp),
                    )
                }

                if (isVod) {
                    IconButton(onClick = { seekVodDelta(10_000L) }, modifier = Modifier.size(54.dp)) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "+10s",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            audioSkipCount = 0
                            onNextChannel()
                        },
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Canale seguente",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }

            // Bottom full-bleed scrim
            val localTime = remember(clockTick) { formatLocalTime() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xAA000000), Color(0xE6000000)),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .align(Alignment.BottomCenter),
            ) {
                if (isVod && vodDurMs > 0L) {
                    val remainingMs = (vodDurMs - vodPosMs).coerceAtLeast(0L)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Slider(
                            value = (vodPosMs.toFloat() / vodDurMs.coerceAtLeast(1000L).toFloat()).coerceIn(0f, 1f),
                            onValueChange = { frac ->
                                val target = (frac * vodDurMs).toLong().coerceIn(0L, vodDurMs)
                                vodPosMs = target
                                runCatching { exoPlayer.seekTo(target) }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFE50914),
                                activeTrackColor = Color(0xFFE50914),
                                inactiveTrackColor = Color(0x4DFFFFFF),
                            ),
                            modifier = Modifier.weight(1f).height(24.dp),
                        )
                        Text(
                            text = formatMobileHms(remainingMs),
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isVod) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    playbackSpeed = when {
                                        playbackSpeed < 1.0f -> 1.0f
                                        playbackSpeed < 1.25f -> 1.25f
                                        playbackSpeed < 1.5f -> 1.5f
                                        else -> 0.75f
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                text = "${if (playbackSpeed == 1f) "1x" else "${playbackSpeed}x"}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "LIVE · $localTime",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            if (latestOnStartRecording != null) {
                                val rec = activeRecording
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (rec != null) Color(0xFF262626) else Color(0xFF141414))
                                        .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                                        .then(
                                            if (rec != null) {
                                                Modifier.clickable {
                                                    val stopFn = latestOnStopRecording
                                                    if (stopFn != null && !recBusy) {
                                                        recBusy = true
                                                        scope.launch {
                                                            runCatching { stopFn(rec.id) }
                                                            activeRecording = null
                                                            recBusy = false
                                                        }
                                                    }
                                                }
                                            } else {
                                                Modifier.pointerInput(channel.id, rec?.id) {
                                                    detectTapGestures(
                                                        onPress = {
                                                            if (!recBusy && activeRecording == null) {
                                                                recHoldActive = true
                                                                try {
                                                                    tryAwaitRelease()
                                                                } finally {
                                                                    recHoldActive = false
                                                                    recHoldElapsedMs = 0L
                                                                }
                                                            }
                                                        },
                                                    )
                                                }
                                            },
                                        )
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = if (rec != null) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = if (rec != null) tr("Stop REC", "Stop REC", "Detener REC") else tr("REC (3s)", "REC (3s)", "REC (3s)"),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showTracksSheet = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(
                            text = tr("Audio e sottotitoli", "Audio & Subtitles", "Audio y subtítulos"),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (nextEpisode != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    nextEpDismissed = true
                                    nextEpTriggered = true
                                    onPlayNextEpisode()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                text = tr("Prossimo ep.", "Next Ep.", "Siguiente ep."),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onToggleBackgroundPlay(!backgroundPlayEnabled) },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "Background",
                                tint = if (backgroundPlayEnabled) Color.White else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(onClick = onEnterPip, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(18.dp))
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
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Aspect", tint = Color.White, modifier = Modifier.size(18.dp))
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
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Top-left active REC indicator (minimal flat pill)
        if (!isVod && activeRecording != null && !isInPipMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 14.dp, start = 16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xD9000000))
                    .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444)),
                )
                Text(
                    text = "REC",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
        }

        // Centered dimmed 3-second hold-to-record countdown overlay
        if (!isVod && recHoldActive && recHoldElapsedMs >= 1_000L && !isInPipMode) {
            val secLeft = kotlin.math.ceil((3_000L - recHoldElapsedMs).coerceAtLeast(0L) / 1000.0).toInt().coerceIn(1, 2)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB8000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "$secLeft",
                        color = Color.White,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = tr(
                            "Continua a tenere premuto per registrare (salvata per 7 giorni)",
                            "Keep holding to start recording (saved for 7 days)",
                            "Mantén pulsado para grabar (guardada 7 días)",
                        ),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // 1-minute counterclockwise circular next-episode countdown in bottom-right corner
        val nextEpCountdownVisible = isVod && nextEpisode != null && !nextEpDismissed && !nextEpTriggered &&
            vodDurMs > 65_000L && nextEpRemainingMs in 0L..60_000L && !isInPipMode
        if (nextEpCountdownVisible && nextEpisode != null) {
            val secInt = kotlin.math.ceil(nextEpRemainingMs / 1000.0).toInt().coerceIn(0, 60)
            val fraction = (nextEpRemainingMs.coerceIn(0L, 60_000L).toFloat() / 60_000f)
            val sweepAngle = -360f * fraction
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = if (showControls) 98.dp else 22.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC000000))
                    .clickable {
                        nextEpDismissed = true
                        nextEpTriggered = true
                        onPlayNextEpisode()
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier.size(60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (secInt > 0) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeW = 4.dp.toPx()
                            drawArc(
                                color = Color.White.copy(alpha = 0.22f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeW),
                            )
                            drawArc(
                                color = Color.White,
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                            )
                        }
                    }
                    Text(
                        text = "$secInt",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    text = tr(
                        "Tocca per il prossimo episodio",
                        "Tap for next episode",
                        "Toca para el siguiente episodio",
                    ),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Audio | Subtitles | Sources overlay
        if (showTracksSheet && !isInPipMode) {
            val audioGroups = remember(tracksState) {
                tracksState.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
            }
            val textGroups = remember(tracksState) {
                tracksState.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000))
                    .clickable { showTracksSheet = false },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .background(Color(0xF514161D))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                        .navigationBarsPadding()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        if (audioGroups.isEmpty()) {
                            Text("Predefinito", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                                itemsIndexed(audioGroups) { gIdx, group ->
                                    val fmt = group.getTrackFormat(0)
                                    val langLabel = fmt.language?.uppercase() ?: "Traccia ${gIdx + 1}"
                                    val label = listOfNotNull(fmt.label, langLabel).distinct().joinToString(" · ")
                                    val selected = group.isSelected
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) Color(0x28FFFFFF) else Color.Transparent)
                                            .clickable {
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                                    .build()
                                                showTracksSheet = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (selected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        Text(
                                            text = label,
                                            color = if (selected) Color.White else Color(0xFF94A3B8),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("Sottotitoli", "Subtitles", "Subtítulos"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        val anyTextSelected = textGroups.any { it.isSelected }
                        LazyColumn(modifier = Modifier.fillMaxHeight()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (!anyTextSelected) Color(0x28FFFFFF) else Color.Transparent)
                                        .clickable {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                            showTracksSheet = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (!anyTextSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        text = tr("Disattivati", "Off", "Desactivados"),
                                        color = if (!anyTextSelected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                            itemsIndexed(textGroups) { gIdx, group ->
                                val fmt = group.getTrackFormat(0)
                                val langLabel = fmt.language?.uppercase() ?: "Sub ${gIdx + 1}"
                                val label = listOfNotNull(fmt.label, langLabel).distinct().joinToString(" · ")
                                val selected = group.isSelected
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) Color(0x28FFFFFF) else Color.Transparent)
                                        .clickable {
                                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                                .build()
                                            showTracksSheet = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (selected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        text = label,
                                        color = if (selected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    }

                    if (sources.size > 1) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = tr("Sorgenti disponibili (${sources.size})", "Available sources (${sources.size})", "Fuentes disponibles (${sources.size})"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                                itemsIndexed(sources) { idx, src ->
                                    val isSel = idx == activeSourceIndex
                                    val title = src.quality.ifBlank { src.label }.ifBlank { "Sorgente ${idx + 1}" }
                                    val meta = listOfNotNull(
                                        src.protocol.takeIf { it.isNotBlank() },
                                        src.host.takeIf { it.isNotBlank() },
                                        src.latencyMs?.let { "${it} ms" },
                                    ).joinToString(" · ")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) Color(0x28FFFFFF) else Color.Transparent)
                                            .clickable {
                                                showTracksSheet = false
                                                onSelectSource(idx)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (isSel) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        Text(
                                            text = if (meta.isNotBlank()) "$title · $meta" else title,
                                            color = if (isSel) Color.White else Color(0xFFA3A3A3),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (src.recommended) {
                                            Text(
                                                text = tr("Migliore", "Best", "Mejor"),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
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

private fun formatMobileHms(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
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
                            .background(Color(0x26FFFFFF))
                            .border(1.dp, Color(0x66FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = channelInitials(channel.name),
                            color = Color.White,
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
                            .background(Color(0x26FFFFFF))
                            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                        Text(
                            text = audioLabel,
                            color = Color.White,
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
        if (cushionEnabledProvider()) 4 else 3

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
