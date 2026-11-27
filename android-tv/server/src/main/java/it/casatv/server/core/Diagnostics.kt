package it.casatv.server.core

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

object DiagnosticCodes {
    // Android TV client codes
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

    // Server / Live Relay codes
    const val SRV_RELAY_START_FAIL = "SRV_RELAY_START_FAIL"
    const val SRV_RELAY_DRM = "SRV_RELAY_DRM"
    const val SRV_UPSTREAM_PLACEHOLDER = "SRV_UPSTREAM_PLACEHOLDER"
    const val SRV_UPSTREAM_SLOW = "SRV_UPSTREAM_SLOW"
    const val SRV_UPSTREAM_FAIL = "SRV_UPSTREAM_FAIL"
    const val SRV_ABR_STEPDOWN = "SRV_ABR_STEPDOWN"
    const val SRV_MIRROR_FAILOVER = "SRV_MIRROR_FAILOVER"
    const val SRV_PDT_GAP = "SRV_PDT_GAP"
    const val SRV_LOW_WARMUP_FAIL = "SRV_LOW_WARMUP_FAIL"
    const val SRV_SEG_404 = "SRV_SEG_404"
    const val SRV_WATCHDOG_RESTART = "SRV_WATCHDOG_RESTART"

    fun codeCatalog(): Map<String, Pair<String, String>> = linkedMapOf(
        TV_HTTP_404 to ("warn" to "Segmento o playlist HLS non trovato (HTTP 404/410)"),
        TV_HTTP_403 to ("error" to "Accesso negato / geo-blocco / token scaduto (HTTP 401/403)"),
        TV_HTTP_5XX to ("error" to "Errore server sorgente (HTTP 5xx)"),
        TV_HTTP_OTHER to ("error" to "Altro errore HTTP"),
        TV_NET_TIMEOUT to ("error" to "Timeout o errore di rete/socket lato TV"),
        TV_BEHIND_LIVE to ("warn" to "Player rimasto indietro rispetto alla finestra live HLS"),
        TV_CODEC_ERR to ("error" to "Errore decoder hardware/software audio o video"),
        TV_FORMAT_ERR to ("error" to "Formato flusso non supportato o errore parsing playlist"),
        TV_DRM_ERR to ("error" to "Errore DRM o flusso cifrato non riproducibile"),
        TV_STATE_ENDED to ("warn" to "Fine inattesa di un flusso live (STATE_ENDED)"),
        TV_PLAYBACK_ERR to ("error" to "Altro errore ExoPlayer (PlaybackException)"),
        TV_BUF_SHORT to ("warn" to "Pausa di buffering in riproduzione (>= 2.5s)"),
        TV_BUF_TIMEOUT to ("error" to "Buffering prolungato (> 20s), avvio riconnessione"),
        TV_STALL_RESYNC to ("warn" to "Video bloccato per 10s, tentato soft resync al live edge"),
        TV_STALL_TIMEOUT to ("error" to "Video bloccato per 10s, avviata riconnessione completa"),
        TV_SOFT_RESYNC to ("info" to "Soft resync al live edge eseguito su errore live transitorio"),
        TV_SEG_SLOW to ("warn" to "Download lento di un segmento/chunk lato TV (>= 4s)"),
        TV_FRAMES_DROPPED to ("warn" to "Perdita frame video elevata (>= 15 frame)"),
        TV_AUDIO_UNDERRUN to ("warn" to "Svuotamento buffer audio (AudioTrack underrun)"),
        TV_RECONNECT to ("warn" to "Tentativo di riconnessione automatica avviato"),
        TV_FAILOVER_SRC to ("warn" to "Passaggio automatico a sorgente alternativa del canale"),
        TV_RECONNECT_EXHAUSTED to ("error" to "Tentativi di riconnessione esauriti, canale non riproducibile"),
        TV_RELAY_FALLBACK to ("warn" to "Live Relay non riuscito, ripiegamento su flusso diretto"),
        TV_LOAD_ERROR to ("warn" to "Errore transitorio di caricamento segmento/playlist (AnalyticsListener)"),
        SRV_RELAY_START_FAIL to ("error" to "Avvio Live Relay fallito su tutte le sorgenti candidate"),
        SRV_RELAY_DRM to ("warn" to "DRM non supportato rilevato su HLS sorgente, delegato diretto alla TV"),
        SRV_UPSTREAM_PLACEHOLDER to ("warn" to "Sorgente reindirizzata a segnaposto .mp4 o clip < 25s scartata"),
        SRV_UPSTREAM_SLOW to ("warn" to "Download segmenti upstream lento (ratio > 0.85 / cicli lenti)"),
        SRV_UPSTREAM_FAIL to ("error" to "Sorgente upstream irraggiungibile o persa (upstream_lost)"),
        SRV_ABR_STEPDOWN to ("warn" to "Riduzione automatica profilo ABR sul relay"),
        SRV_MIRROR_FAILOVER to ("warn" to "Selezione automatica o failover su specchio alternativo"),
        SRV_PDT_GAP to ("info" to "Salto temporale PDT > 2.5s, inserita #EXT-X-DISCONTINUITY"),
        SRV_LOW_WARMUP_FAIL to ("warn" to "Warmup variante leggera video_low.m3u8 fallito"),
        SRV_SEG_404 to ("warn" to "Richiesta client a segmento/playlist relay restituita con HTTP 404"),
        SRV_WATCHDOG_RESTART to ("error" to "Riavvio automatico del server HTTP da Watchdog"),
    )
}

data class DiagnosticLogInput(
    val tsMs: Long,
    val source: String,
    val severity: String,
    val code: String,
    val deviceId: String = "",
    val deviceName: String = "",
    val appVersion: String = "",
    val clientIp: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val playbackMode: String = "",
    val relayKey: String = "",
    val streamUrl: String = "",
    val message: String = "",
    val detailsJson: String = "{}",
)

data class DiagnosticLogRow(
    val id: Long,
    val tsMs: Long,
    val source: String,
    val severity: String,
    val code: String,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val clientIp: String,
    val channelId: String,
    val channelName: String,
    val playbackMode: String,
    val relayKey: String,
    val streamUrl: String,
    val message: String,
    val detailsJson: String,
)

data class DiagnosticCodeStat(
    val code: String,
    val count: Int,
    val lastTsMs: Long,
)

data class DiagnosticChannelStat(
    val channelId: String,
    val channelName: String,
    val errorCount: Int,
    val warnCount: Int,
    val totalCount: Int,
    val topCode: String,
    val lastTsMs: Long,
)

data class DiagnosticDeviceStat(
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val errorCount: Int,
    val warnCount: Int,
    val totalCount: Int,
    val lastTsMs: Long,
)

data class DiagnosticSummary(
    val retentionDays: Int,
    val totalCount: Int,
    val oldestTsMs: Long?,
    val newestTsMs: Long?,
    val bySeverity: Map<String, Int>,
    val topCodes: List<DiagnosticCodeStat>,
    val topChannels: List<DiagnosticChannelStat>,
    val byDevice: List<DiagnosticDeviceStat>,
)

class ServerDiagnosticLogger private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val throttles = ConcurrentHashMap<String, Long>()

    fun log(
        code: String,
        severity: String = "warn",
        channelId: String = "",
        channelName: String = "",
        relayKey: String = "",
        streamUrl: String = "",
        deviceId: String = "server",
        deviceName: String = "CasaTV Server",
        clientIp: String = "",
        message: String = "",
        details: Map<String, Any?> = emptyMap(),
        throttleMs: Long = 0L,
    ) {
        val now = System.currentTimeMillis()
        if (throttleMs > 0L) {
            val throttleKey = "$code|$channelId|$relayKey"
            val prev = throttles[throttleKey]
            if (prev != null && now - prev < throttleMs) {
                return
            }
            if (throttles.size > 512) {
                throttles.entries.removeIf { now - it.value > 300_000L }
            }
            throttles[throttleKey] = now
        }
        val detailsJson = buildJsonObject {
            for ((k, v) in details) {
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
        }.toString()
        val entry = DiagnosticLogInput(
            tsMs = now,
            source = "server",
            severity = severity,
            code = code,
            deviceId = deviceId.ifBlank { "server" },
            deviceName = deviceName.ifBlank { "CasaTV Server" },
            appVersion = it.casatv.server.BuildConfig.VERSION_NAME,
            clientIp = clientIp,
            channelId = channelId,
            channelName = channelName,
            playbackMode = if (relayKey.isNotBlank()) "relay" else "",
            relayKey = relayKey,
            streamUrl = streamUrl,
            message = message,
            detailsJson = detailsJson,
        )
        scope.launch {
            runCatching {
                ServerDatabase.get(appContext).insertDiagnosticLogs(listOf(entry))
            }.onFailure { err ->
                Log.w("CasaDiag", "Errore scrittura log server ($code): ${err.message}")
            }
        }
    }

    companion object {
        @Volatile
        private var instance: ServerDiagnosticLogger? = null

        fun get(context: Context): ServerDiagnosticLogger =
            instance ?: synchronized(this) {
                instance ?: ServerDiagnosticLogger(context).also { instance = it }
            }
    }
}
