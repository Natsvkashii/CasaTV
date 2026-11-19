package it.casatv.tv.ui.components

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import it.casatv.tv.R
import it.casatv.tv.ui.theme.CasaGreen
import kotlinx.coroutines.delay

/**
 * Renders a muted inline live broadcast preview inside a channel card box when
 * focus rests on the item for at least [dwellMs]. Uses fade-in / fade-out
 * alpha transitions and releases ExoPlayer immediately when unfocused.
 */
@OptIn(UnstableApi::class)
@Composable
fun ChannelLivePreviewBox(
    streamUrl: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    dwellMs: Long = 850L,
) {
    val cleanUrl = remember(streamUrl) {
        val trimmed = streamUrl.trim()
        val scheme = trimmed.substringBefore("://", "").lowercase()
        if (scheme == "http" || scheme == "https") trimmed else null
    }

    var shouldMountPlayer by remember(cleanUrl) { mutableStateOf(false) }
    var isReady by remember(cleanUrl) { mutableStateOf(false) }
    var failed by remember(cleanUrl) { mutableStateOf(false) }

    LaunchedEffect(active, cleanUrl) {
        if (active && cleanUrl != null) {
            failed = false
            delay(dwellMs)
            shouldMountPlayer = true
        } else {
            isReady = false
            if (shouldMountPlayer) {
                delay(260)
                shouldMountPlayer = false
            }
        }
    }

    val previewAlpha by animateFloatAsState(
        targetValue = if (active && isReady && !failed) 1f else 0f,
        animationSpec = tween(durationMillis = if (active && isReady && !failed) 420 else 250),
        label = "channel-preview-alpha",
    )

    if (!shouldMountPlayer && previewAlpha <= 0f) return
    val targetUrl = cleanUrl ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = previewAlpha },
    ) {
        if (shouldMountPlayer) {
            val context = LocalContext.current
            val previewPlayer = remember(targetUrl) {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(5000)
                    .setReadTimeoutMs(7000)
                val dataFactory = DefaultDataSource.Factory(context, httpFactory)
                val renderersFactory = DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1500, 6000, 500, 1000)
                    .build()

                ExoPlayer.Builder(context, renderersFactory)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataFactory))
                    .setLoadControl(loadControl)
                    .build()
                    .apply {
                        volume = 0f
                        trackSelectionParameters = trackSelectionParameters.buildUpon()
                            .setMaxVideoSize(854, 480)
                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)
                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                            .build()
                        val item = MediaItem.fromUri(targetUrl)
                        runCatching {
                            if (targetUrl.contains(".m3u8", ignoreCase = true) ||
                                targetUrl.contains("m3u8", ignoreCase = true)
                            ) {
                                setMediaSource(
                                    HlsMediaSource.Factory(dataFactory)
                                        .setAllowChunklessPreparation(true)
                                        .createMediaSource(item),
                                )
                            } else {
                                setMediaItem(item)
                            }
                            prepare()
                            playWhenReady = true
                        }.onFailure {
                            failed = true
                        }
                    }
            }

            DisposableEffect(previewPlayer) {
                val listener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        isReady = true
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            isReady = true
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        failed = true
                        isReady = false
                    }
                }
                previewPlayer.addListener(listener)
                onDispose {
                    previewPlayer.removeListener(listener)
                    runCatching {
                        previewPlayer.stop()
                        previewPlayer.clearMediaItems()
                        previewPlayer.release()
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        keepScreenOn = false
                        isFocusable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { view ->
                    view.player = previewPlayer
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                },
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xC8060B0E))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(CasaGreen),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
        }
    }
}
