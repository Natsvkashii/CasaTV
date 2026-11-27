package it.casatv.mobile.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.isActive

private val MobileZapDurationsSec = floatArrayOf(0.42f, 0.34f, 0.27f, 0.21f, 0.16f, 0.125f, 0.095f, 0.065f)
private const val M_STATIC_FLASH_SEC = 0.028f
private const val M_ACT1_END_SEC = 0.85f
private const val M_ACT2_END_SEC = 1.45f
private const val M_MIN_BEFORE_ZOOM_SEC = 3.45f
private const val M_MAX_WAIT_PRELOAD_SEC = 7.40f
private const val M_ACT4_ZOOM_DURATION_SEC = 0.80f

private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

private fun easeOutCubic(t: Float): Float {
    val u = 1f - clamp01(t)
    return 1f - u * u * u
}

private fun easeOutBack(t: Float, overshoot: Float = 1.35f): Float {
    val x = clamp01(t) - 1f
    return 1f + x * x * ((overshoot + 1f) * x + overshoot)
}

@Composable
fun MobileStartupSplash(
    preloadReady: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestReady by rememberUpdatedState(preloadReady)
    val latestFinished by rememberUpdatedState(onFinished)
    var elapsedNs by remember { mutableLongStateOf(0L) }

    val screenClipPath = remember { Path() }
    val wavePath = remember { Path() }
    val dashedStroke = remember { PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f) }

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
                zapState.avgFrameMs = zapState.avgFrameMs * 0.90f + (rawDtNs / 1_000_000f) * 0.10f

                if (elapsedSec >= M_ACT2_END_SEC && zapState.zoomStartSec < 0f) {
                    val slowDevice = zapState.avgFrameMs > 28f
                    val waitingOnData = !latestReady && elapsedSec < M_MAX_WAIT_PRELOAD_SEC
                    val readyToZoom = (latestReady || elapsedSec >= M_MAX_WAIT_PRELOAD_SEC) &&
                        elapsedSec >= M_MIN_BEFORE_ZOOM_SEC &&
                        zapState.zapCount >= 7

                    if (readyToZoom && zapState.staticRemainingSec <= 0f) {
                        zapState.zoomStartSec = elapsedSec
                    } else {
                        if (zapState.staticRemainingSec > 0f) {
                            zapState.staticRemainingSec = (zapState.staticRemainingSec - dtSec).coerceAtLeast(0f)
                        }
                        zapState.channelTimerSec += dtSec
                        var targetDur = MobileZapDurationsSec[zapState.zapCount.coerceAtMost(MobileZapDurationsSec.lastIndex)]
                        if (slowDevice) targetDur *= 1.25f
                        if (waitingOnData && elapsedSec >= 2.35f) targetDur = targetDur.coerceAtLeast(0.24f)
                        if (zapState.channelTimerSec >= targetDur) {
                            zapState.channelTimerSec = 0f
                            zapState.zapCount += 1
                            zapState.currentChannel = (zapState.currentChannel + 1) % 8
                            zapState.staticRemainingSec = M_STATIC_FLASH_SEC
                            zapState.knobAngleDeg += 36f
                        }
                    }
                }

                elapsedNs = accumNs
                if (zapState.zoomStartSec >= 0f &&
                    (elapsedSec - zapState.zoomStartSec) >= M_ACT4_ZOOM_DURATION_SEC
                ) {
                    zapState.done = true
                    latestFinished()
                }
            }
        }
    }

    val elapsedSec = elapsedNs / 1_000_000_000f
    val act1T = clamp01(elapsedSec / M_ACT1_END_SEC)
    val jumpAppearAlpha = clamp01(elapsedSec / 0.14f)
    val jumpYNorm = if (act1T < 0.42f) {
        (1f - easeOutCubic(act1T / 0.42f)) * 0.45f
    } else if (act1T < 0.72f) {
        -sin(((act1T - 0.42f) / 0.30f) * PI).toFloat() * 0.09f
    } else {
        val u = (act1T - 0.72f) / 0.28f
        sin(u * PI).toFloat() * 0.025f * (1f - u)
    }
    val impactSquash = if (act1T in 0.40f..0.75f) sin(((act1T - 0.40f) / 0.35f) * PI).toFloat() * 0.10f else 0f
    val bodyScale = if (act1T < 0.05f) 0.01f else easeOutBack(clamp01(act1T / 0.45f))
    val antBounceDeg = if (act1T in 0.38f..1.0f) {
        val u = (act1T - 0.38f) / 0.62f
        sin(u * PI * 3.0).toFloat() * 8.5f * (1f - u)
    } else if (act1T < 0.38f) -10f * (1f - act1T / 0.38f) else 0f
    val highSpeedZapJitter = if (zapState.zapCount >= 4 && zapState.zoomStartSec < 0f) sin(elapsedSec * 48.0).toFloat() * 1.4f else 0f

    val zoomRawT = if (zapState.zoomStartSec >= 0f) clamp01((elapsedSec - zapState.zoomStartSec) / M_ACT4_ZOOM_DURATION_SEC) else 0f
    val zoomFactor = when {
        zoomRawT <= 0f -> 1f
        zoomRawT < 0.11f -> 1f - 0.045f * sin((zoomRawT / 0.11f) * PI).toFloat()
        else -> 1f + 15.5f * clamp01((zoomRawT - 0.11f) / 0.89f).pow(2.6f)
    }
    val zoomCenterT = clamp01((zoomRawT - 0.11f) / 0.89f)
    val overlayAlpha = if (zoomRawT > 0.62f) 1f - clamp01((zoomRawT - 0.62f) / 0.38f) else 1f
    val wordmarkAlpha = when {
        zoomRawT > 0f -> (1f - zoomRawT * 4f).coerceAtLeast(0f)
        elapsedSec < M_ACT2_END_SEC -> clamp01((elapsedSec - 1.15f) / 0.45f)
        else -> 1f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer {
                        val tvPx = size.width
                        val unit = tvPx / 512f
                        val crtDx = (199f - 256f) * unit
                        val crtDy = (282.5f - 256f) * unit
                        val jitterPx = if (zapState.staticRemainingSec > 0f) sin(elapsedSec * 90.0).toFloat() * 1.6f else 0f
                        alpha = jumpAppearAlpha
                        transformOrigin = TransformOrigin.Center
                        scaleX = bodyScale * (1f + impactSquash) * zoomFactor
                        scaleY = bodyScale * (1f - impactSquash * 0.85f) * zoomFactor
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

                    if (elapsedSec < 0.25f) {
                        val sparkT = 1f - clamp01(elapsedSec / 0.25f)
                        drawCircle(white.copy(alpha = sparkT * 0.85f), (6f + sparkT * 10f) * u, px(256f, 282f))
                    }

                    val antPivot = px(256f, 175f)
                    rotate(antBounceDeg * 0.85f + highSpeedZapJitter, pivot = antPivot) {
                        drawLine(white, antPivot, px(175f, 75f), 11f * u, StrokeCap.Round)
                    }
                    rotate(-antBounceDeg + highSpeedZapJitter * 0.8f, pivot = antPivot) {
                        drawLine(white, antPivot, px(345f, 60f), 11f * u, StrokeCap.Round)
                    }

                    drawLine(white, px(148f, 390f), px(122f, 445f), 12f * u, StrokeCap.Round)
                    drawLine(white, px(186f, 390f), px(172f, 430f), 12f * u, StrokeCap.Round)
                    drawLine(white, px(326f, 390f), px(340f, 430f), 12f * u, StrokeCap.Round)
                    drawLine(white, px(364f, 390f), px(390f, 445f), 12f * u, StrokeCap.Round)

                    drawRoundRect(Color(0xFF060606), px(66f, 175f), Size(380f * u, 215f * u), CornerRadius(36f * u, 36f * u))
                    drawRoundRect(white, px(66f, 175f), Size(380f * u, 215f * u), CornerRadius(36f * u, 36f * u), style = Stroke(11f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    val crtLeft = ox + 90f * u
                    val crtTop = oy + 198f * u
                    val crtW = 218f * u
                    val crtH = 169f * u
                    val crtCorner = 26f * u
                    val crtCenter = Offset(crtLeft + crtW / 2f, crtTop + crtH / 2f)

                    screenClipPath.reset()
                    screenClipPath.addRoundRect(RoundRect(Rect(crtLeft, crtTop, crtLeft + crtW, crtTop + crtH), CornerRadius(crtCorner, crtCorner)))

                    clipPath(screenClipPath) {
                        drawRect(Color(0xFF030303), Offset(crtLeft, crtTop), Size(crtW, crtH))
                        when {
                            elapsedSec < M_ACT1_END_SEC -> Unit
                            elapsedSec < M_ACT2_END_SEC -> {
                                val pT = clamp01((elapsedSec - M_ACT1_END_SEC) / (M_ACT2_END_SEC - M_ACT1_END_SEC))
                                if (pT < 0.35f) {
                                    val beamW = (crtW * easeOutCubic(pT / 0.35f)).coerceAtLeast(6f * u)
                                    drawRect(white, Offset(crtCenter.x - beamW / 2f, crtCenter.y - 2f * u), Size(beamW, 4f * u))
                                } else {
                                    val openFrac = easeOutCubic((pT - 0.35f) / 0.65f)
                                    drawMobileScene(0, crtLeft, crtTop, crtW, crtH, u, elapsedSec, wavePath, dashedStroke, openFrac)
                                    val maskH = (crtH - crtH * openFrac) / 2f
                                    if (maskH > 1f) {
                                        drawRect(Color.Black, Offset(crtLeft, crtTop), Size(crtW, maskH))
                                        drawRect(Color.Black, Offset(crtLeft, crtTop + crtH - maskH), Size(crtW, maskH))
                                    }
                                    val flashAlpha = (1f - (pT - 0.35f) / 0.65f) * 0.65f
                                    drawCircle(Brush.radialGradient(listOf(white.copy(alpha = flashAlpha), Color.Transparent), crtCenter, crtW * 0.75f), crtW * 0.75f, crtCenter)
                                }
                            }
                            else -> {
                                if (zapState.staticRemainingSec > 0f) {
                                    val bands = 8
                                    val bh = crtH / bands
                                    for (i in 0 until bands) {
                                        val v = ((sin(elapsedSec * 213.7 + i * 47.3) + 1.0) * 0.5).toFloat()
                                        if (v > 0.35f) drawRect(white.copy(alpha = 0.25f + 0.55f * v), Offset(crtLeft, crtTop + i * bh), Size(crtW, bh * 0.55f))
                                    }
                                } else {
                                    drawMobileScene(zapState.currentChannel, crtLeft, crtTop, crtW, crtH, u, elapsedSec, wavePath, dashedStroke, 1f)
                                }
                            }
                        }
                        if (elapsedSec >= M_ACT1_END_SEC) {
                            var sy = crtTop + 8f * u
                            while (sy < crtTop + crtH) {
                                drawLine(Color.Black.copy(alpha = 0.22f), Offset(crtLeft, sy), Offset(crtLeft + crtW, sy), 1.5f * u)
                                sy += 8f * u
                            }
                        }
                    }

                    drawRoundRect(white, Offset(crtLeft, crtTop), Size(crtW, crtH), CornerRadius(crtCorner, crtCorner), style = Stroke(10f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    val dial1Center = px(377f, 230f)
                    drawCircle(white, 18f * u, dial1Center, style = Stroke(8f * u))
                    rotate(zapState.knobAngleDeg, pivot = dial1Center) {
                        drawLine(white, Offset(dial1Center.x, dial1Center.y - 10f * u), Offset(dial1Center.x, dial1Center.y + 4f * u), 6f * u, StrokeCap.Round)
                    }

                    val dial2Center = px(377f, 282f)
                    val pDeg = if (elapsedSec >= M_ACT1_END_SEC) -38f * easeOutCubic(clamp01((elapsedSec - M_ACT1_END_SEC) / 0.18f)) else 0f
                    drawCircle(white, 18f * u, dial2Center, style = Stroke(8f * u))
                    rotate(pDeg, pivot = dial2Center) {
                        drawLine(white, Offset(dial2Center.x + 8f * u, dial2Center.y), Offset(dial2Center.x - 4f * u, dial2Center.y), 6f * u, StrokeCap.Round)
                    }

                    drawLine(white, px(346f, 316f), px(408f, 316f), 7f * u, StrokeCap.Round)
                    drawLine(white, px(346f, 331f), px(408f, 331f), 7f * u, StrokeCap.Round)
                    drawLine(white, px(346f, 346f), px(408f, 346f), 7f * u, StrokeCap.Round)
                    drawLine(white, px(346f, 361f), px(408f, 361f), 7f * u, StrokeCap.Round)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "CasaTV",
                color = Color.White.copy(alpha = wordmarkAlpha * 0.92f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun DrawScope.drawMobileScene(
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
            drawRect(Color(0xFF141414), Offset(left, top), Size(width, height))
            drawLine(dim, Offset(left, cy), Offset(left + width, cy), 2.5f * u)
            drawLine(dim, Offset(cx, top), Offset(cx, top + height), 2.5f * u)
            drawCircle(white, 48f * u, Offset(cx, cy), style = Stroke(4f * u))
            drawCircle(white, 6f * u, Offset(cx, cy))
        }
        1 -> {
            drawRect(Color(0xFF0A0E16), Offset(left, top), Size(width, height))
            drawRect(Color.Black, Offset(left, top), Size(width, 26f * u))
            drawRect(Color.Black, Offset(left, top + height - 26f * u), Size(width, 26f * u))
            wavePath.reset()
            wavePath.moveTo(cx - 12f * u, cy - 16f * u)
            wavePath.lineTo(cx + 18f * u, cy)
            wavePath.lineTo(cx - 12f * u, cy + 16f * u)
            wavePath.close()
            drawPath(wavePath, white)
        }
        2 -> {
            drawRect(Color(0xFF0D1812), Offset(left, top), Size(width, height))
            wavePath.reset()
            wavePath.moveTo(left + 26f * u, top + height - 28f * u)
            wavePath.quadraticTo(cx, top + 24f * u, left + width - 26f * u, top + height - 28f * u)
            drawPath(wavePath, dim, style = Stroke(3.5f * u, pathEffect = dashedStroke, cap = StrokeCap.Round))
            val ballT = (elapsedSec * 2.2f) % 1f
            drawCircle(white, 9f * u, Offset((left + 26f * u) + (width - 52f * u) * ballT, (top + height - 28f * u) - sin(ballT * PI).toFloat() * (height * 0.55f)))
        }
        3 -> {
            drawRect(Color(0xFF181012), Offset(left, top), Size(width, height))
            drawCircle(white, 36f * u, Offset(cx, cy - 12f * u), style = Stroke(3.5f * u))
            drawRect(white, Offset(left + 20f * u, top + height - 34f * u), Size(width - 40f * u, 11f * u))
            drawCircle(Color(0xFFE50914), 5.5f * u, Offset(left + 28f * u, top + 22f * u))
        }
        4 -> {
            drawRect(Color(0xFF14111B), Offset(left, top), Size(width, height))
            val barW = 14f * u
            val gap = 10f * u
            val startX = cx - ((5 * barW + 4 * gap) / 2f)
            val bottomY = top + height - 34f * u
            for (i in 0 until 5) {
                val hFrac = 0.35f + 0.60f * ((sin(elapsedSec * 9.0 + i * 1.4) + 1.0) * 0.5).toFloat()
                val bh = (height * 0.56f) * hFrac
                drawRoundRect(white, Offset(startX + i * (barW + gap), bottomY - bh), Size(barW, bh), CornerRadius(4f * u, 4f * u))
            }
        }
        5 -> {
            drawRect(Color(0xFF0C161D), Offset(left, top), Size(width, height))
            drawCircle(white, 15f * u, Offset(left + width * 0.72f, top + 34f * u))
            wavePath.reset()
            wavePath.moveTo(left, cy + 16f * u)
            wavePath.quadraticTo(left + width * 0.35f, cy - 12f * u, cx + 10f * u, cy + 16f * u)
            wavePath.quadraticTo(left + width * 0.80f, cy + 36f * u, left + width, cy + 8f * u)
            drawPath(wavePath, white, style = Stroke(5f * u, cap = StrokeCap.Round))
        }
        6 -> {
            drawRect(Color(0xFF0D0F18), Offset(left, top), Size(width, height))
            drawCircle(dim, 50f * u, Offset(cx, cy), style = Stroke(2.5f * u))
            drawCircle(white, 9f * u, Offset(cx, cy))
            val ang = elapsedSec * 5.2f
            drawCircle(white, 6f * u, Offset(cx + cos(ang) * 50f * u, cy + sin(ang) * 50f * u))
        }
        else -> {
            drawRect(Color(0xFF101010), Offset(left, top), Size(width, height))
            val ringPhase = (elapsedSec * 2.4f) % 1f
            for (k in 0 until 3) {
                val p = (ringPhase + k * 0.33f) % 1f
                drawCircle(white.copy(alpha = (1f - p) * 0.9f), (12f + p * 64f) * u, Offset(cx, cy), style = Stroke(4.5f * u))
            }
            drawCircle(white, 10f * u, Offset(cx, cy))
        }
    }
}
