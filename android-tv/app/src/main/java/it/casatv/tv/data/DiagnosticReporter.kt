package it.casatv.tv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object TvDiagnosticCodes {
    const val TV_HTTP_404 = "TV_HTTP_404"
    const val TV_HTTP_403 = "TV_HTTP_403"
    const val TV_HTTP_5XX = "TV_HTTP_5XX"
    const val TV_HTTP_OTHER = "TV_HTTP_OTHER"
    const val TV_NET_TIMEOUT = "TV_NET_TIMEOUT"
    const val TV_BEHIND_LIVE = "TV_BEHIND_LIVE"
    const val TV_CODEC_ERR = "TV_CODEC_ERR"
    const val TV_FORMAT_ERR = "TV_FORMAT_ERR"
    const val TV_DRM_ERR = "TV_DRM_ERR"
    const val TV_STATE_ENDED = "TV_STATE_ENDED"
    const val TV_PLAYBACK_ERR = "TV_PLAYBACK_ERR"
    const val TV_BUF_SHORT = "TV_BUF_SHORT"
    const val TV_BUF_TIMEOUT = "TV_BUF_TIMEOUT"
    const val TV_STALL_RESYNC = "TV_STALL_RESYNC"
    const val TV_STALL_TIMEOUT = "TV_STALL_TIMEOUT"
    const val TV_SOFT_RESYNC = "TV_SOFT_RESYNC"
    const val TV_SEG_SLOW = "TV_SEG_SLOW"
    const val TV_FRAMES_DROPPED = "TV_FRAMES_DROPPED"
    const val TV_AUDIO_UNDERRUN = "TV_AUDIO_UNDERRUN"
    const val TV_RECONNECT = "TV_RECONNECT"
    const val TV_FAILOVER_SRC = "TV_FAILOVER_SRC"
    const val TV_RECONNECT_EXHAUSTED = "TV_RECONNECT_EXHAUSTED"
    const val TV_RELAY_FALLBACK = "TV_RELAY_FALLBACK"
    const val TV_LOAD_ERROR = "TV_LOAD_ERROR"
}

data class NetworkSnapshot(
    val netType: String,
    val wifiRssiDbm: Int?,
    val wifiLinkMbps: Int?,
    val wifiFreqMhz: Int?,
)

class DiagnosticReporter(
    private val appContext: Context,
    private val api: CasaApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueLock = Any()
    private val queue = ArrayDeque<JsonObject>()
    private val throttles = ConcurrentHashMap<String, Long>()
    private val flushMutex = Mutex()
    private var flushJob: Job? = null

    fun close() {
        scope.cancel()
    }

    fun report(
        code: String,
        severity: String = "warn",
        channelId: String = "",
        channelName: String = "",
        playbackMode: String = "",
        relayKey: String = "",
        streamUrl: String = "",
        message: String = "",
        details: Map<String, Any?> = emptyMap(),
        throttleMs: Long = 0L,
    ) {
        val now = System.currentTimeMillis()
        if (throttleMs > 0L) {
            val key = "$code|$channelId"
            val prev = throttles[key]
            if (prev != null && now - prev < throttleMs) {
                return
            }
            if (throttles.size > 256) {
                throttles.entries.removeIf { now - it.value > 300_000L }
            }
            throttles[key] = now
        }

        val detailsCopy = if (details.isEmpty()) emptyMap() else LinkedHashMap(details)
        scope.launch {
            val net = collectNetworkSnapshot(appContext)
            val detailsObj = buildJsonObject {
                put("netType", net.netType)
                net.wifiRssiDbm?.let { put("wifiRssiDbm", it) }
                net.wifiLinkMbps?.let { put("wifiLinkMbps", it) }
                net.wifiFreqMhz?.let { put("wifiFreqMhz", it) }
                for ((k, v) in detailsCopy) {
                    when (v) {
                        null -> put(k, JsonNull)
                        is Boolean -> put(k, JsonPrimitive(v))
                        is Int -> put(k, JsonPrimitive(v))
                        is Long -> put(k, JsonPrimitive(v))
                        is Float -> put(k, JsonPrimitive(v.toDouble()))
                        is Double -> put(k, JsonPrimitive(v))
                        is Number -> put(k, JsonPrimitive(v.toDouble()))
                        else -> put(k, JsonPrimitive(v.toString()))
                    }
                }
            }

            val event = buildJsonObject {
                put("tsMs", now)
                put("source", "tv")
                put("severity", severity.lowercase())
                put("code", code)
                if (channelId.isNotBlank()) put("channelId", channelId)
                if (channelName.isNotBlank()) put("channelName", channelName)
                if (playbackMode.isNotBlank()) put("playbackMode", playbackMode)
                if (relayKey.isNotBlank()) put("relayKey", relayKey)
                if (streamUrl.isNotBlank()) put("streamUrl", streamUrl)
                if (message.isNotBlank()) put("message", message)
                put("details", detailsObj)
            }

            val queueSize: Int
            synchronized(queueLock) {
                while (queue.size >= MAX_QUEUE_SIZE) {
                    queue.removeFirst()
                }
                queue.addLast(event)
                queueSize = queue.size
            }

            val urgent = severity.equals("error", ignoreCase = true) ||
                severity.equals("fatal", ignoreCase = true) ||
                queueSize >= 10
            scheduleFlush(urgent)
        }
    }

    private fun scheduleFlush(urgent: Boolean) {
        val delayMs = if (urgent) 500L else 4_000L
        synchronized(queueLock) {
            if (urgent) {
                flushJob?.cancel()
                flushJob = null
            } else if (flushJob?.isActive == true) {
                return
            }
            flushJob = scope.launch {
                delay(delayMs)
                flushLocked()
            }
        }
    }

    fun flushAsync() {
        scope.launch {
            flushLocked()
        }
    }

    private suspend fun flushLocked() {
        flushMutex.withLock {
            while (true) {
                val batch = synchronized(queueLock) {
                    if (queue.isEmpty()) emptyList()
                    else buildList {
                        repeat(minOf(MAX_BATCH_SIZE, queue.size)) {
                            add(queue.removeFirst())
                        }
                    }
                }
                if (batch.isEmpty()) break
                val ok = api.sendDiagnosticEvents(JsonArray(batch))
                if (!ok) {
                    synchronized(queueLock) {
                        for (item in batch.asReversed()) {
                            if (queue.size < MAX_QUEUE_SIZE) {
                                queue.addFirst(item)
                            }
                        }
                    }
                    break
                }
            }
        }
    }

    companion object {
        private const val MAX_QUEUE_SIZE = 200
        private const val MAX_BATCH_SIZE = 50

        fun collectNetworkSnapshot(context: Context): NetworkSnapshot = runCatching {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = if (network != null) cm.getNetworkCapabilities(network) else null
            val netType = when {
                caps == null -> "none"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                else -> "other"
            }
            if (netType == "wifi") {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val info = runCatching { wm?.connectionInfo }.getOrNull()
                val rssi = info?.rssi?.takeIf { it in -119..-1 && it != -127 }
                val linkMbps = info?.linkSpeed?.takeIf { it > 0 }
                val freqMhz = info?.frequency?.takeIf { it > 0 }
                NetworkSnapshot(netType, rssi, linkMbps, freqMhz)
            } else {
                NetworkSnapshot(netType, null, null, null)
            }
        }.getOrDefault(NetworkSnapshot("unknown", null, null, null))

        fun classifyPlaybackError(error: PlaybackException): Pair<String, Int?> {
            var cur: Throwable? = error.cause
            var depth = 0
            while (cur != null && depth < 6) {
                if (cur is HttpDataSource.InvalidResponseCodeException) {
                    val code = cur.responseCode
                    val diagCode = when (code) {
                        404, 410 -> TvDiagnosticCodes.TV_HTTP_404
                        401, 403 -> TvDiagnosticCodes.TV_HTTP_403
                        in 500..599 -> TvDiagnosticCodes.TV_HTTP_5XX
                        else -> TvDiagnosticCodes.TV_HTTP_OTHER
                    }
                    return diagCode to code
                }
                cur = cur.cause
                depth++
            }
            val diagCode = when (error.errorCode) {
                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> TvDiagnosticCodes.TV_BEHIND_LIVE
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                PlaybackException.ERROR_CODE_TIMEOUT -> TvDiagnosticCodes.TV_NET_TIMEOUT
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> TvDiagnosticCodes.TV_CODEC_ERR
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> TvDiagnosticCodes.TV_FORMAT_ERR
                PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
                PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
                PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
                PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
                PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
                PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
                PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> TvDiagnosticCodes.TV_DRM_ERR
                else -> TvDiagnosticCodes.TV_PLAYBACK_ERR
            }
            return diagCode to null
        }
    }
}
