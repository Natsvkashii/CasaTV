package it.casatv.tv.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.casatv.tv.CasaTvApplication
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.isActive

private val ZapDurationsSec = floatArrayOf(0.42f, 0.34f, 0.27f, 0.21f, 0.16f, 0.125f, 0.095f, 0.065f)
private const val STATIC_FLASH_SEC = 0.028f
private const val ACT1_END_SEC = 0.85f
private const val ACT2_END_SEC = 1.45f
private const val MIN_BEFORE_ZOOM_SEC = 3.45f
private const val MAX_WAIT_PRELOAD_SEC = 7.40f
private const val ACT4_ZOOM_DURATION_SEC = 0.80f

private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

private fun easeOutCubic(t: Float): Float {
    val u = 1f - clamp01(t)
    return 1f - u * u * u
}

private fun easeOutBack(t: Float, overshoot: Float = 1.6f): Float {
    val x = clamp01(t) - 1f
    return 1f + x * x * ((overshoot + 1f) * x + overshoot)
}

/**
 * Director-choreographed 4-act startup animation:
 * 1) TV logo leaps in from zero with squash & stretch and rabbit-ear follow-through.
 * 2) CRT electron beam powers on from horizontal line to phosphor glow.
 * 3) Channels switch in a progressively accelerating rhythm, adapting speed if
 *    the device is slow or preload data is still arriving (guaranteed >= 4.2s total).
 * 4) Exponential zoom centered directly inside the CRT screen into Profile selection.
 */
@Composable
fun StartupSplashScreen(
    preloadReady: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isLowRam = remember(context) { CasaTvApplication.isLowRamDevice(context) }
    val latestReady by rememberUpdatedState(preloadReady)
    val latestFinished by rememberUpdatedState(onFinished)

    var elapsedNs by remember { mutableLongStateOf(0L) }

    val screenClipPath = remember { Path() }
    val wavePath = remember { Path() }
    val dashedStroke = remember {
        PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    // Channel zapping timeline state inside loop (non-allocating)
    val zapState = remember {
        object {
            var currentChannel = 0
            var channelTimerSec = 0f
            var zapCount = 0
            var staticRemainingSec = 0f
            var knobAngleDeg = 0f
            var zoomStartSec = -1f
            var avgFrameMs = 16.6f
            var done = false
        }
    }

    LaunchedEffect(Unit) {
        var lastFrameNs = 0L
        var accumNs = 0L
        while (isActive && !zapState.done) {
            withFrameNanos { frameNs ->
                if (lastFrameNs == 0L) {
                    lastFrameNs = frameNs
                    return@withFrameNanos
                }
                val rawDtNs = (frameNs - lastFrameNs).coerceIn(1_000_000L, 60_000_000L)
                lastFrameNs = frameNs
                accumNs += rawDtNs
                val dtSec = rawDtNs / 1_000_000_000f
                val elapsedSec = accumNs / 1_000_000_000f
                val frameMs = rawDtNs / 1_000_000f
                zapState.avgFrameMs = zapState.avgFrameMs * 0.90f + frameMs * 0.10f

                if (elapsedSec >= ACT2_END_SEC && zapState.zoomStartSec < 0f) {
                    val slowDevice = isLowRam || zapState.avgFrameMs > 28f
                    val waitingOnData = !latestReady && elapsedSec < MAX_WAIT_PRELOAD_SEC
                    val readyToZoom = (latestReady || elapsedSec >= MAX_WAIT_PRELOAD_SEC) &&
                        elapsedSec >= MIN_BEFORE_ZOOM_SEC &&
                        zapState.zapCount >= 7

                    if (readyToZoom && zapState.staticRemainingSec <= 0f) {
                        zapState.zoomStartSec = elapsedSec
                    } else {
                        if (zapState.staticRemainingSec > 0f) {
                            zapState.staticRemainingSec = (zapState.staticRemainingSec - dtSec).coerceAtLeast(0f)
                        }
                        zapState.channelTimerSec += dtSec
                        val baseIdx = zapState.zapCount.coerceAtMost(ZapDurationsSec.lastIndex)
                        var targetDur = ZapDurationsSec[baseIdx]
                        if (slowDevice) targetDur *= 1.25f
                        // Gently stretch channel pacing if server/network preload isn't done yet
                        if (waitingOnData && elapsedSec >= 2.35f) {
                            targetDur = targetDur.coerceAtLeast(0.24f)
                        }
                        if (zapState.channelTimerSec >= targetDur) {
                            zapState.channelTimerSec = 0f
                            zapState.zapCount += 1
                            zapState.currentChannel = (zapState.currentChannel + 1) % 8
                            zapState.staticRemainingSec = STATIC_FLASH_SEC
                            zapState.knobAngleDeg += 36f
                        }
                    }
                }

                elapsedNs = accumNs
                if (zapState.zoomStartSec >= 0f &&
                    (elapsedSec - zapState.zoomStartSec) >= ACT4_ZOOM_DURATION_SEC
                ) {
                    zapState.done = true
                    latestFinished()
                }
            }
        }
    }

    val elapsedSec = elapsedNs / 1_000_000_000f

    // Act I: Jump & squash/stretch physics
    val act1T = clamp01(elapsedSec / ACT1_END_SEC)
    val jumpAppearAlpha = clamp01(elapsedSec / 0.14f)
    val jumpYNorm = if (act1T < 0.42f) {
        val u = act1T / 0.42f
        (1f - easeOutCubic(u)) * 0.45f
    } else if (act1T < 0.72f) {
        val u = (act1T - 0.42f) / 0.30f
        -sin(u * PI).toFloat() * 0.09f
    } else {
        val u = (act1T - 0.72f) / 0.28f
        sin(u * PI).toFloat() * 0.025f * (1f - u)
    }

    val impactSquash = if (act1T in 0.40f..0.75f) {
        val u = (act1T - 0.40f) / 0.35f
        sin(u * PI).toFloat() * 0.10f
    } else {
        0f
    }
    val bodyScale = easeOutBack(clamp01(act1T / 0.45f), 1.35f).let { s ->
        if (act1T < 0.05f) 0.01f else s
    }

    // Antenna follow-through & zap micro-tremor
    val antBounceDeg = if (act1T in 0.38f..1.0f) {
        val u = (act1T - 0.38f) / 0.62f
        sin(u * PI * 3.0).toFloat() * 8.5f * (1f - u)
    } else if (act1T < 0.38f) {
        -10f * (1f - act1T / 0.38f)
    } else {
        0f
    }
    val highSpeedZapJitter = if (zapState.zapCount >= 4 && zapState.zoomStartSec < 0f) {
        sin(elapsedSec * 48.0).toFloat() * 1.4f
    } else {
        0f
    }

    // Act IV: Zoom into CRT screen center
    val zoomRawT = if (zapState.zoomStartSec >= 0f) {
        clamp01((elapsedSec - zapState.zoomStartSec) / ACT4_ZOOM_DURATION_SEC)
    } else {
        0f
    }
    val zoomFactor = when {
        zoomRawT <= 0f -> 1f
        zoomRawT < 0.11f -> {
            val backT = zoomRawT / 0.11f
            1f - 0.045f * sin(backT * PI).toFloat()
        }
        else -> {
            val zt = clamp01((zoomRawT - 0.11f) / 0.89f)
            1f + 15.5f * zt.pow(2.6f)
        }
    }
    val zoomCenterT = clamp01((zoomRawT - 0.11f) / 0.89f)
    val overlayAlpha = if (zoomRawT > 0.62f) {
        1f - clamp01((zoomRawT - 0.62f) / 0.38f)
    } else {
        1f
    }
    val wordmarkAlpha = when {
        zoomRawT > 0f -> (1f - zoomRawT * 4f).coerceAtLeast(0f)
        elapsedSec < ACT2_END_SEC -> clamp01((elapsedSec - 1.15f) / 0.45f)
        else -> 1f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .graphicsLayer {
                        val tvPx = size.width
                        val unit = tvPx / 512f
                        // Vector logo CRT center relative to 512x512 canvas center (256, 256):
                        // CRT bounds = (90..308, 198..367) => center (199, 282.5)
                        val crtDx = (199f - 256f) * unit
                        val crtDy = (282.5f - 256f) * unit

                        val jitterPx = if (zapState.staticRemainingSec > 0f) {
                            sin(elapsedSec * 90.0).toFloat() * 1.8f
                        } else {
                            0f
                        }
                        val squashX = bodyScale * (1f + impactSquash) * zoomFactor
                        val squashY = bodyScale * (1f - impactSquash * 0.85f) * zoomFactor

                        alpha = jumpAppearAlpha
                        transformOrigin = TransformOrigin.Center
                        scaleX = squashX
                        scaleY = squashY
                        translationX = -crtDx * (zoomFactor - 1f + zoomCenterT) + jitterPx
                        translationY = (jumpYNorm * tvPx) - crtDy * (zoomFactor - 1f + zoomCenterT)
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val u = size.minDimension / 512f
                    val ox = (size.width - 512f * u) / 2f
                    val oy = (size.height - 512f * u) / 2f
                    fun px(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)

                    val white = Color.White

                    // Act I Spark before body appears
                    if (elapsedSec < 0.25f) {
                        val sparkT = 1f - clamp01(elapsedSec / 0.25f)
                        drawCircle(
                            color = Color.White.copy(alpha = sparkT * 0.85f),
                            radius = (6f + sparkT * 10f) * u,
                            center = px(256f, 282f),
                        )
                    }

                    // 1. V-Antennas with follow-through spring around base pivot (256, 175)
                    val antPivot = px(256f, 175f)
                    rotate(degrees = antBounceDeg * 0.85f + highSpeedZapJitter, pivot = antPivot) {
                        drawLine(
                            color = white,
                            start = antPivot,
                            end = px(175f, 75f),
                            strokeWidth = 11f * u,
                            cap = StrokeCap.Round,
                        )
                    }
                    rotate(degrees = -antBounceDeg + highSpeedZapJitter * 0.8f, pivot = antPivot) {
                        drawLine(
                            color = white,
                            start = antPivot,
                            end = px(345f, 60f),
                            strokeWidth = 11f * u,
                            cap = StrokeCap.Round,
                        )
                    }

                    // 2. 4 Support legs
                    val legStroke = 12f * u
                    drawLine(white, px(148f, 390f), px(122f, 445f), legStroke, StrokeCap.Round)
                    drawLine(white, px(186f, 390f), px(172f, 430f), legStroke, StrokeCap.Round)
                    drawLine(white, px(326f, 390f), px(340f, 430f), legStroke, StrokeCap.Round)
                    drawLine(white, px(364f, 390f), px(390f, 445f), legStroke, StrokeCap.Round)

                    // 3. Outer TV Shell squircle
                    drawRoundRect(
                        color = Color(0xFF060606),
                        topLeft = px(66f, 175f),
                        size = Size(380f * u, 215f * u),
                        cornerRadius = CornerRadius(36f * u, 36f * u),
                    )
                    drawRoundRect(
                        color = white,
                        topLeft = px(66f, 175f),
                        size = Size(380f * u, 215f * u),
                        cornerRadius = CornerRadius(36f * u, 36f * u),
                        style = Stroke(width = 11f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )

                    // 4. Inner CRT Screen background & dynamic channel content
                    val crtLeft = ox + 90f * u
                    val crtTop = oy + 198f * u
                    val crtW = 218f * u
                    val crtH = 169f * u
                    val crtCorner = 26f * u
                    val crtCenter = Offset(crtLeft + crtW / 2f, crtTop + crtH / 2f)

                    screenClipPath.reset()
                    screenClipPath.addRoundRect(
                        RoundRect(
                            rect = Rect(crtLeft, crtTop, crtLeft + crtW, crtTop + crtH),
                            cornerRadius = CornerRadius(crtCorner, crtCorner),
                        ),
                    )

                    clipPath(screenClipPath) {
                        drawRect(Color(0xFF030303), Offset(crtLeft, crtTop), Size(crtW, crtH))

                        when {
                            elapsedSec < ACT1_END_SEC -> {
                                // Tube dark & waiting before power-on
                            }
                            elapsedSec < ACT2_END_SEC -> {
                                // Act II: CRT power-on horizontal scan beam -> bloom open
                                val pT = clamp01((elapsedSec - ACT1_END_SEC) / (ACT2_END_SEC - ACT1_END_SEC))
                                if (pT < 0.35f) {
                                    val wFrac = easeOutCubic(pT / 0.35f)
                                    val beamW = (crtW * wFrac).coerceAtLeast(6f * u)
                                    val beamH = (4f * u)
                                    drawRect(
                                        color = white,
                                        topLeft = Offset(crtCenter.x - beamW / 2f, crtCenter.y - beamH / 2f),
                                        size = Size(beamW, beamH),
                                    )
                                } else {
                                    val openFrac = easeOutCubic((pT - 0.35f) / 0.65f)
                                    val hOpen = crtH * openFrac
                                    drawChannelScene(
                                        channelIndex = 0,
                                        left = crtLeft,
                                        top = crtTop,
                                        width = crtW,
                                        height = crtH,
                                        u = u,
                                        elapsedSec = elapsedSec,
                                        wavePath = wavePath,
                                        dashedStroke = dashedStroke,
                                        fade = openFrac,
                                    )
                                    // Black shutters opening vertically from center
                                    val maskH = (crtH - hOpen) / 2f
                                    if (maskH > 1f) {
                                        drawRect(Color.Black, Offset(crtLeft, crtTop), Size(crtW, maskH))
                                        drawRect(Color.Black, Offset(crtLeft, crtTop + crtH - maskH), Size(crtW, maskH))
                                    }
                                    // Phosphor startup flash
                                    val flashAlpha = (1f - (pT - 0.35f) / 0.65f) * 0.65f
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(Color.White.copy(alpha = flashAlpha), Color.Transparent),
                                            center = crtCenter,
                                            radius = crtW * 0.75f,
                                        ),
                                        radius = crtW * 0.75f,
                                        center = crtCenter,
                                    )
                                }
                            }
                            else -> {
                                // Act III & IV: Live minimal channels + static flash between channels
                                if (zapState.staticRemainingSec > 0f) {
                                    drawStaticNoise(
                                        left = crtLeft,
                                        top = crtTop,
                                        width = crtW,
                                        height = crtH,
                                        seed = elapsedSec,
                                        u = u,
                                    )
                                } else {
                                    drawChannelScene(
                                        channelIndex = zapState.currentChannel,
                                        left = crtLeft,
                                        top = crtTop,
                                        width = crtW,
                                        height = crtH,
                                        u = u,
                                        elapsedSec = elapsedSec,
                                        wavePath = wavePath,
                                        dashedStroke = dashedStroke,
                                        fade = 1f,
                                    )
                                    // Minimal CRT channel OSD bars in top-right (geometric bars for CH 01..08)
                                    drawChannelOsdPip(
                                        channelIndex = zapState.currentChannel,
                                        right = crtLeft + crtW - 14f * u,
                                        top = crtTop + 14f * u,
                                        u = u,
                                    )
                                }
                            }
                        }

                        // Subtle CRT scanlines (reduced density on low-RAM TVs)
                        if (elapsedSec >= ACT1_END_SEC) {
                            val lineStep = if (isLowRam) 14f * u else 7f * u
                            var sy = crtTop + lineStep
                            while (sy < crtTop + crtH) {
                                drawLine(
                                    color = Color.Black.copy(alpha = 0.24f),
                                    start = Offset(crtLeft, sy),
                                    end = Offset(crtLeft + crtW, sy),
                                    strokeWidth = 1.6f * u,
                                )
                                sy += lineStep
                            }
                        }
                    }

                    // CRT Screen border
                    drawRoundRect(
                        color = white,
                        topLeft = Offset(crtLeft, crtTop),
                        size = Size(crtW, crtH),
                        cornerRadius = CornerRadius(crtCorner, crtCorner),
                        style = Stroke(width = 10f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )

                    // 5. Right control panel: Upper channel dial (rotates +36deg per zap)
                    val dial1Center = px(377f, 230f)
                    drawCircle(
                        color = white,
                        radius = 18f * u,
                        center = dial1Center,
                        style = Stroke(width = 8f * u),
                    )
                    rotate(degrees = zapState.knobAngleDeg, pivot = dial1Center) {
                        drawLine(
                            color = white,
                            start = Offset(dial1Center.x, dial1Center.y - 10f * u),
                            end = Offset(dial1Center.x, dial1Center.y + 4f * u),
                            strokeWidth = 6f * u,
                            cap = StrokeCap.Round,
                        )
                    }

                    // 6. Lower power dial (clicks when Act II powers on)
                    val dial2Center = px(377f, 282f)
                    val powerClickDeg = if (elapsedSec >= ACT1_END_SEC) {
                        -38f * easeOutCubic(clamp01((elapsedSec - ACT1_END_SEC) / 0.18f))
                    } else 0f
                    drawCircle(
                        color = white,
                        radius = 18f * u,
                        center = dial2Center,
                        style = Stroke(width = 8f * u),
                    )
                    rotate(degrees = powerClickDeg, pivot = dial2Center) {
                        drawLine(
                            color = white,
                            start = Offset(dial2Center.x + 8f * u, dial2Center.y),
                            end = Offset(dial2Center.x - 4f * u, dial2Center.y),
                            strokeWidth = 6f * u,
                            cap = StrokeCap.Round,
                        )
                    }

                    // 7. Speaker grill slits (4 horizontal clean lines)
                    val spkStroke = 7f * u
                    val spkPulse = if (zapState.staticRemainingSec > 0f) 0.55f else 1.0f
                    val spkColor = white.copy(alpha = spkPulse)
                    drawLine(spkColor, px(346f, 316f), px(408f, 316f), spkStroke, StrokeCap.Round)
                    drawLine(spkColor, px(346f, 331f), px(408f, 331f), spkStroke, StrokeCap.Round)
                    drawLine(spkColor, px(346f, 346f), px(408f, 346f), spkStroke, StrokeCap.Round)
                    drawLine(spkColor, px(346f, 361f), px(408f, 361f), spkStroke, StrokeCap.Round)
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "CasaTV",
                color = Color.White.copy(alpha = wordmarkAlpha * 0.92f),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun DrawScope.drawStaticNoise(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    seed: Float,
    u: Float,
) {
    val bands = 8
    val bandH = height / bands
    for (i in 0 until bands) {
        val v = ((sin(seed * 213.7 + i * 47.3) + 1.0) * 0.5).toFloat()
        if (v > 0.35f) {
            drawRect(
                color = Color.White.copy(alpha = 0.25f + 0.55f * v),
                topLeft = Offset(left + ((v * 37f) % 19f) * u, top + i * bandH),
                size = Size(width * (0.45f + v * 0.5f), bandH * 0.55f),
            )
        }
    }
}

private fun DrawScope.drawChannelOsdPip(
    channelIndex: Int,
    right: Float,
    top: Float,
    u: Float,
) {
    // 8 minimal dot-pip indicator for CH 01..08 inside CRT top-right
    val total = 4
    val dotR = 2.6f * u
    val gap = 8.5f * u
    for (i in 0 until total) {
        val active = i == (channelIndex % total)
        drawCircle(
            color = Color.White.copy(alpha = if (active) 0.95f else 0.32f),
            radius = if (active) dotR * 1.25f else dotR,
            center = Offset(right - (total - 1 - i) * gap, top),
        )
    }
}

private fun DrawScope.drawChannelScene(
    channelIndex: Int,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    u: Float,
    elapsedSec: Float,
    wavePath: Path,
    dashedStroke: PathEffect,
    fade: Float,
) {
    val cx = left + width / 2f
    val cy = top + height / 2f
    val white = Color.White.copy(alpha = fade)
    val dim = Color.White.copy(alpha = 0.32f * fade)

    when (channelIndex % 8) {
        0 -> {
            // CH 01: Classic minimal Monoscopio (crosshair + concentric circles)
            drawRect(Color(0xFF141414), Offset(left, top), Size(width, height))
            drawLine(dim, Offset(left, cy), Offset(left + width, cy), 2.5f * u)
            drawLine(dim, Offset(cx, top), Offset(cx, top + height), 2.5f * u)
            drawCircle(white, 48f * u, Offset(cx, cy), style = Stroke(4f * u))
            drawCircle(dim, 24f * u, Offset(cx, cy), style = Stroke(3f * u))
            drawCircle(white, 6f * u, Offset(cx, cy))
        }
        1 -> {
            // CH 02: Cinema letterbox + sweeping anamorphic flare
            drawRect(Color(0xFF0A0E16), Offset(left, top), Size(width, height))
            drawRect(Color.Black, Offset(left, top), Size(width, 28f * u))
            drawRect(Color.Black, Offset(left, top + height - 28f * u), Size(width, 28f * u))
            val sweepX = left + ((elapsedSec * 1.4f) % 1f) * width
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, white, Color.Transparent),
                    startX = sweepX - 55f * u,
                    endX = sweepX + 55f * u,
                ),
                start = Offset(left + 12f * u, cy),
                end = Offset(left + width - 12f * u, cy),
                strokeWidth = 6f * u,
                cap = StrokeCap.Round,
            )
            // Minimal play triangle
            wavePath.reset()
            wavePath.moveTo(cx - 12f * u, cy - 18f * u)
            wavePath.lineTo(cx + 18f * u, cy)
            wavePath.lineTo(cx - 12f * u, cy + 18f * u)
            wavePath.close()
            drawPath(wavePath, white)
        }
        2 -> {
            // CH 03: Sport trajectory arc + fast moving ball
            drawRect(Color(0xFF0D1812), Offset(left, top), Size(width, height))
            drawLine(dim, Offset(left + 20f * u, top + height - 28f * u), Offset(left + width - 20f * u, top + height - 28f * u), 3f * u, StrokeCap.Round)
            wavePath.reset()
            wavePath.moveTo(left + 28f * u, top + height - 30f * u)
            wavePath.quadraticTo(cx, top + 24f * u, left + width - 28f * u, top + height - 30f * u)
            drawPath(wavePath, dim, style = Stroke(3.5f * u, pathEffect = dashedStroke, cap = StrokeCap.Round))
            val ballT = (elapsedSec * 2.2f) % 1f
            val bx = (left + 28f * u) + (width - 56f * u) * ballT
            val by = (top + height - 30f * u) - sin(ballT * PI).toFloat() * (height * 0.56f)
            drawCircle(white, 9.5f * u, Offset(bx, by))
        }
        3 -> {
            // CH 04: News globe + bottom ticker bar
            drawRect(Color(0xFF181012), Offset(left, top), Size(width, height))
            val globeR = 38f * u
            drawCircle(white, globeR, Offset(cx, cy - 12f * u), style = Stroke(3.5f * u))
            drawOval(
                color = dim,
                topLeft = Offset(cx - globeR * 0.45f, cy - 12f * u - globeR),
                size = Size(globeR * 0.9f, globeR * 2f),
                style = Stroke(2.5f * u),
            )
            drawLine(dim, Offset(cx - globeR, cy - 12f * u), Offset(cx + globeR, cy - 12f * u), 2.5f * u)
            // Scrolling ticker bars
            drawRect(white, Offset(left + 20f * u, top + height - 36f * u), Size(width - 40f * u, 12f * u))
            drawCircle(Color(0xFFE50914), 6f * u, Offset(left + 28f * u, top + 22f * u))
        }
        4 -> {
            // CH 05: Music minimal 5-bar equalizer
            drawRect(Color(0xFF14111B), Offset(left, top), Size(width, height))
            val bars = 5
            val barW = 14f * u
            val gap = 10f * u
            val startX = cx - ((bars * barW + (bars - 1) * gap) / 2f)
            val bottomY = top + height - 34f * u
            for (i in 0 until bars) {
                val hFrac = (0.35f + 0.60f * ((sin(elapsedSec * 9.0 + i * 1.4) + 1.0) * 0.5).toFloat())
                val bh = (height * 0.58f) * hFrac
                drawRoundRect(
                    color = white,
                    topLeft = Offset(startX + i * (barW + gap), bottomY - bh),
                    size = Size(barW, bh),
                    cornerRadius = CornerRadius(4f * u, 4f * u),
                )
            }
        }
        5 -> {
            // CH 06: Nature horizon waves + moon
            drawRect(Color(0xFF0C161D), Offset(left, top), Size(width, height))
            drawCircle(white, 16f * u, Offset(left + width * 0.72f, top + 36f * u))
            wavePath.reset()
            wavePath.moveTo(left, cy + 16f * u)
            wavePath.quadraticTo(left + width * 0.35f, cy - 12f * u, cx + 10f * u, cy + 16f * u)
            wavePath.quadraticTo(left + width * 0.80f, cy + 36f * u, left + width, cy + 8f * u)
            drawPath(wavePath, white, style = Stroke(5f * u, cap = StrokeCap.Round))
            drawLine(dim, Offset(left + 22f * u, cy + 38f * u), Offset(left + width - 22f * u, cy + 38f * u), 3.5f * u, StrokeCap.Round)
        }
        6 -> {
            // CH 07: Sci-fi concentric orbits + orbiting dot
            drawRect(Color(0xFF0D0F18), Offset(left, top), Size(width, height))
            drawCircle(dim, 54f * u, Offset(cx, cy), style = Stroke(2.5f * u))
            drawCircle(dim, 32f * u, Offset(cx, cy), style = Stroke(2.5f * u))
            drawCircle(white, 10f * u, Offset(cx, cy))
            val ang = elapsedSec * 5.2f
            drawCircle(white, 6f * u, Offset(cx + cos(ang) * 54f * u, cy + sin(ang) * 54f * u))
            drawCircle(white, 5f * u, Offset(cx + cos(-ang * 1.6f) * 32f * u, cy + sin(-ang * 1.6f) * 32f * u))
        }
        else -> {
            // CH 08: Live broadcast signal rings expanding before zoom!
            drawRect(Color(0xFF101010), Offset(left, top), Size(width, height))
            val ringPhase = (elapsedSec * 2.4f) % 1f
            for (k in 0 until 3) {
                val p = (ringPhase + k * 0.33f) % 1f
                drawCircle(
                    color = white.copy(alpha = (1f - p) * 0.9f),
                    radius = (12f + p * 66f) * u,
                    center = Offset(cx, cy),
                    style = Stroke(width = 4.5f * u),
                )
            }
            drawCircle(white, 11f * u, Offset(cx, cy))
        }
    }
}
