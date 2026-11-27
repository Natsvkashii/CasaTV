package it.casatv.server.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class RelayTuneResult(
    val mode: String, // "relay" or "direct"
    val relayKey: String = "",
    val playlistUrl: String = "",
    val url: String = "",
    val viewers: Int = 0,
    val hasAudioTrack: Boolean = false,
    val reason: String = "",
)

data class RelayStatusItem(
    val key: String,
    val channelId: String,
    val channelName: String,
    val status: String,
    val viewers: Int,
    val segmentCount: Int,
    val hasAudioTrack: Boolean,
    val bytes: Long,
    val uptimeSeconds: Long,
)

internal data class RelaySeedSnapshot(
    val sourceDir: File,
    val videoUrl: String,
    val audioUrl: String?,
    val bandwidth: Int,
    val resolution: String?,
    val codecs: String?,
    val hasAudioTrack: Boolean,
    val targetDurationSec: Double,
    val videoSegments: List<TrackSegmentEntry>,
    val audioSegments: List<TrackSegmentEntry>,
)

/**
 * Central Single-Origin Live Relay for CasaTV.
 * Pulls each watched live channel once from upstream into a rolling ring buffer
 * on the dedicated server phone and serves identical LAN HLS segments to every
 * TV and Web client watching that channel, with automatic ABR step-down and
 * upstream mirror failover.
 */
class LiveRelayManager(context: Context) {

    private val rootDir = File(context.applicationContext.filesDir, "relay").apply { mkdirs() }
    private val diag = ServerDiagnosticLogger.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val relays = ConcurrentHashMap<String, RelayChannelState>()
    private val relayOpLock = Any()
    private val preferredCandidateCache = ConcurrentHashMap<String, Pair<String, Long>>()
    @Volatile
    private var db: ServerDatabase? = null

    fun attachDatabase(database: ServerDatabase) {
        db = database
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private class RelayTrackBuffer {
        val lock = Any()
        val segments = ArrayDeque<TrackSegmentEntry>()
        val seenUrls = LinkedHashSet<String>()
        val seenUrlQueue = ArrayDeque<String>()
        val downloadedMaps = HashMap<String, String>()
        val downloadedKeys = HashMap<String, String>()
        var targetDurationSec: Double = 6.0
        var mediaSequence: Long = 0L
        var discontinuitySequence: Long = 0L
        var nextFileSeq: Long = 0L
        var lastPdtEndMs: Long? = null

        fun clearForReset() {
            synchronized(lock) {
                segments.clear()
                seenUrls.clear()
                seenUrlQueue.clear()
                downloadedMaps.clear()
                downloadedKeys.clear()
                targetDurationSec = 6.0
                mediaSequence = 0L
                discontinuitySequence = 0L
                nextFileSeq = 0L
                lastPdtEndMs = null
            }
        }
    }

    private data class IngestStats(
        val addedCount: Int,
        val failedCount: Int,
        val slowCount: Int,
        val verySlowCount: Int,
        val maxRatio: Double,
    )

    private class RelayChannelState(
        val key: String,
        val channelId: String,
        val channelName: String,
        val streamUrl: String,
        val candidateUrls: List<String>,
        val dir: File,
        val canonicalGroupKey: String = "",
    ) {
        val startedAtMs: Long = System.currentTimeMillis()
        @Volatile
        var lastAccessAtMs: Long = System.currentTimeMillis()
        @Volatile
        var status: String = "starting" // "starting" | "active" | "error"
        @Volatile
        var fallbackDirect: Boolean = false
        @Volatile
        var error: String = ""
        @Volatile
        var hasAudioTrack: Boolean = false
        @Volatile
        var bandwidth: Int = 6_000_000
        @Volatile
        var resolution: String? = null
        @Volatile
        var codecs: String? = null
        @Volatile
        var resolvedVideoUrl: String? = null
        @Volatile
        var resolvedAudioUrl: String? = null
        @Volatile
        var hasLowVariant: Boolean = false
        @Volatile
        var lowBandwidth: Int = 2_000_000
        @Volatile
        var lowResolution: String? = null
        @Volatile
        var lowCodecs: String? = null
        @Volatile
        var resolvedLowVideoUrl: String? = null
        @Volatile
        var highLastAccessAtMs: Long = 0L
        @Volatile
        var lowLastAccessAtMs: Long = 0L
        @Volatile
        var highPaused: Boolean = false
        @Volatile
        var lowPaused: Boolean = false
        @Volatile
        var lowVideoUrl: String? = null
        @Volatile
        var isHls: Boolean = true
        @Volatile
        var job: Job? = null

        val viewers = ConcurrentHashMap.newKeySet<String>()
        val bytes = AtomicLong(0L)
        val readyLatch = CountDownLatch(1)
        val videoTrack = RelayTrackBuffer()
        val videoLowTrack = RelayTrackBuffer()
        val audioTrack = RelayTrackBuffer()
        val lowWarmupStarted = AtomicBoolean(false)
        @Volatile
        var lowReadyLatch = CountDownLatch(1)
        val pendingDeleteLock = Any()
        val pendingDeletes = ArrayDeque<Pair<Long, File>>()
        @Volatile
        var progressiveCandidateUrl: String? = null
    }

    init {
        scope.launch {
            while (isActive) {
                delay(2_000L)
                val now = System.currentTimeMillis()
                relays.values.forEach { state ->
                    val expiredFiles = mutableListOf<File>()
                    synchronized(state.pendingDeleteLock) {
                        while (state.pendingDeletes.isNotEmpty() && state.pendingDeletes.first().first <= now) {
                            expiredFiles.add(state.pendingDeletes.removeFirst().second)
                        }
                    }
                    expiredFiles.forEach { f -> runCatching { f.delete() } }

                    val idleMs = now - state.lastAccessAtMs
                    val emptyIdle = state.viewers.isEmpty() && idleMs > EMPTY_IDLE_TIMEOUT_MS
                    val prewarmOnlyIdle = state.viewers.isNotEmpty() &&
                        state.viewers.all { it.startsWith("prewarm-") } &&
                        idleMs > PREWARM_IDLE_TIMEOUT_MS
                    val hardIdle = idleMs > HARD_IDLE_TIMEOUT_MS
                    val stalledError = state.status == "error" && idleMs > 5_000L
                    if (emptyIdle || prewarmOnlyIdle || hardIdle || stalledError) {
                        Log.i("CasaRelay", "Closing idle relay ${state.key} (${state.channelName}, viewers=${state.viewers.size}, idle=${idleMs}ms)")
                        stopRelay(state.key)
                    }
                }
                preferredCandidateCache.entries.removeIf { (_, entry) ->
                    now - entry.second > PREFERRED_CACHE_TTL_MS
                }
            }
        }
    }

    private class StreamHealthEntry(
        @Volatile var successCount: Int = 0,
        @Volatile var failCount: Int = 0,
        @Volatile var stallCount: Int = 0,
        @Volatile var lastSuccessMs: Long = 0L,
        @Volatile var lastFailMs: Long = 0L,
        @Volatile var lastStallMs: Long = 0L,
        @Volatile var lastStartupMs: Long = 0L,
        @Volatile var lastFailReason: String = "",
    )

    private val streamHealth = ConcurrentHashMap<String, StreamHealthEntry>()

    private fun normalizeHealthKey(url: String): String = url.trim()

    fun recordStreamSuccess(url: String, startupMs: Long = 0L) {
        val key = normalizeHealthKey(url)
        if (key.isBlank()) return
        val now = System.currentTimeMillis()
        val entry = streamHealth.computeIfAbsent(key) { StreamHealthEntry() }
        entry.successCount += 1
        entry.lastSuccessMs = now
        if (startupMs > 0L) entry.lastStartupMs = startupMs
        entry.lastFailReason = ""
    }

    fun recordStreamStall(url: String, groupKey: String? = null) {
        val key = normalizeHealthKey(url)
        if (key.isBlank()) return
        val now = System.currentTimeMillis()
        val entry = streamHealth.computeIfAbsent(key) { StreamHealthEntry() }
        val recent = entry.lastStallMs > 0L && (now - entry.lastStallMs) <= HEALTH_TTL_MS
        entry.stallCount = if (recent) (entry.stallCount + 1).coerceAtMost(20) else 1
        entry.lastStallMs = now
        entry.lastFailReason = "stall"
        db?.recordSourceFeedback(groupKey, key, "stall", entry.lastStartupMs.takeIf { it > 0L } ?: -1L)
    }

    fun recordStreamFailure(url: String, reason: String) {
        val key = normalizeHealthKey(url)
        if (key.isBlank()) return
        val now = System.currentTimeMillis()
        val entry = streamHealth.computeIfAbsent(key) { StreamHealthEntry() }
        entry.failCount += 1
        entry.lastFailMs = now
        entry.lastFailReason = reason.take(64)
        if (streamHealth.size > MAX_HEALTH_ENTRIES) {
            val cutoff = now - HEALTH_TTL_MS
            streamHealth.entries.removeIf { maxOf(it.value.lastSuccessMs, it.value.lastFailMs, it.value.lastStallMs) < cutoff }
        }
    }

    fun markPreferredSource(channelId: String, groupKey: String?, streamUrl: String) {
        val cleanUrl = normalizeHealthKey(streamUrl)
        if (cleanUrl.isBlank()) return
        val now = System.currentTimeMillis()
        val relayKey = computeRelayKey(channelId.trim(), cleanUrl)
        preferredCandidateCache[relayKey] = cleanUrl to now
        if (!groupKey.isNullOrBlank()) {
            preferredCandidateCache["grp:$groupKey"] = cleanUrl to now
        }
        val entry = streamHealth.computeIfAbsent(cleanUrl) { StreamHealthEntry() }
        entry.stallCount = 0
        entry.failCount = 0
        entry.lastStallMs = 0L
        entry.lastFailReason = ""
    }

    fun healthScoreAdjustment(url: String): Int {
        val entry = streamHealth[normalizeHealthKey(url)] ?: return 0
        val now = System.currentTimeMillis()
        val recentStall = entry.lastStallMs > 0L && (now - entry.lastStallMs) <= HEALTH_TTL_MS && entry.stallCount > 0
        val stallPenalty = if (recentStall) (entry.stallCount * 75).coerceAtMost(350) else 0
        val recSuccess = entry.lastSuccessMs > 0L && (now - entry.lastSuccessMs) <= HEALTH_TTL_MS && entry.lastSuccessMs >= entry.lastFailMs
        val recFail = entry.lastFailMs > 0L && (now - entry.lastFailMs) <= HEALTH_TTL_MS && entry.lastFailMs > entry.lastSuccessMs
        val latencyPenalty = when {
            entry.lastStartupMs > 2500L -> 45
            entry.lastStartupMs > 1800L -> 25
            entry.lastStartupMs > 1200L -> 10
            else -> 0
        }
        return when {
            recFail -> {
                val ageMs = now - entry.lastFailMs
                val basePenalty = if (entry.lastFailReason.startsWith("http_40") || entry.lastFailReason == "geo" || entry.lastFailReason == "no_working_source") 160 else 85
                (if (ageMs < 10 * 60_000L) basePenalty else basePenalty / 2) + stallPenalty + latencyPenalty
            }
            recentStall -> stallPenalty + latencyPenalty
            recSuccess -> {
                val speedBonus = if (entry.lastStartupMs in 1L..1500L) -18 else 0
                -35 + speedBonus + latencyPenalty
            }
            else -> stallPenalty + latencyPenalty
        }
    }

    fun streamHealthInfo(url: String): Pair<String, Long?> {
        val cleanUrl = normalizeHealthKey(url)
        val entry = streamHealth[cleanUrl]
        val dbStalls = db?.sourceStallCount(cleanUrl) ?: 0
        if (entry == null) {
            return (if (dbStalls > 0) "degraded" else "online") to null
        }
        val now = System.currentTimeMillis()
        if (maxOf(entry.lastSuccessMs, entry.lastFailMs, entry.lastStallMs) < now - HEALTH_TTL_MS) {
            return (if (dbStalls > 0) "degraded" else "online") to null
        }
        val latency = entry.lastStartupMs.takeIf { it > 0L }
        val totalStalls = maxOf(entry.stallCount, dbStalls)
        return when {
            entry.lastFailMs > entry.lastSuccessMs -> {
                val status = if (entry.lastFailReason.startsWith("http_40") || entry.failCount >= 2) "offline" else "degraded"
                status to latency
            }
            totalStalls > 0 && entry.lastStallMs >= entry.lastSuccessMs -> "degraded" to latency
            else -> "verified" to latency
        }
    }

    private fun staticUrlReliabilityScore(url: String): Int {
        val low = url.trim().lowercase(Locale.ROOT)
        var score = 0
        if (low.startsWith("https://")) score -= 20 else if (low.startsWith("http://")) score += 20
        return score
    }

    private fun probeQuickHttpReachable(url: String): Boolean {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", STREAM_USER_AGENT)
            .header("Range", "bytes=0-255")
            .get()
            .build()
        return runCatching {
            val probeHttp = http.newBuilder()
                .connectTimeout(2500, TimeUnit.MILLISECONDS)
                .readTimeout(2500, TimeUnit.MILLISECONDS)
                .callTimeout(3200, TimeUnit.MILLISECONDS)
                .build()
            probeHttp.newCall(req).execute().use { resp ->
                if (resp.code in 200..299 || resp.code == 206) {
                    recordStreamSuccess(url, 300L)
                    true
                } else {
                    recordStreamFailure(url, "http_${resp.code}")
                    false
                }
            }
        }.getOrElse {
            recordStreamFailure(url, "net_err")
            false
        }
    }

    fun tune(
        deviceId: String,
        channelId: String,
        channelName: String,
        streamUrl: String,
        fallbackUrls: List<String> = emptyList(),
        pinSource: Boolean = false,
        canonicalGroupKey: String = "",
    ): RelayTuneResult {
        val cleanUrl = streamUrl.trim()
        val lower = cleanUrl.lowercase(Locale.ROOT)
        if ((!lower.startsWith("http://") && !lower.startsWith("https://")) || lower.contains(".mpd")) {
            return RelayTuneResult(
                mode = "direct",
                url = cleanUrl,
                reason = "unsupported_protocol",
            )
        }
        val hasHlsFallback = !pinSource && fallbackUrls.any { it.contains(".m3u8", ignoreCase = true) }
        if (AUDIO_RADIO_RE.containsMatchIn(cleanUrl) && !lower.contains(".m3u8") && !hasHlsFallback) {
            val resolvedAudio = if (!pinSource && fallbackUrls.isNotEmpty()) {
                val allAudio = (listOf(cleanUrl) + fallbackUrls.map { it.trim() }.filter { it.isNotBlank() && it != cleanUrl })
                    .distinct()
                    .sortedBy { healthScoreAdjustment(it) }
                allAudio.firstOrNull { cand -> probeQuickHttpReachable(cand) } ?: cleanUrl
            } else {
                cleanUrl
            }
            return RelayTuneResult(
                mode = "direct",
                url = resolvedAudio,
                reason = "audio_radio",
            )
        }

        val cleanDev = deviceId.trim().ifBlank { "default" }
        val prewarmKey = "prewarm-$cleanDev"
        val cleanCh = channelId.trim()
        val cleanCanon = canonicalGroupKey.trim()
        val key = computeRelayKey(cleanCh, cleanUrl)

        relays.values.forEach { other ->
            val sameCanon = cleanCanon.isNotBlank() && other.canonicalGroupKey == cleanCanon
            if (other.key != key && !( !pinSource && sameCanon )) {
                other.viewers.remove(cleanDev)
                other.viewers.remove(prewarmKey)
            }
        }

        val existing = synchronized(relayOpLock) {
            val directMatch = relays[key]?.takeIf { it.status != "error" && !it.fallbackDirect }
            val canonMatch = if (directMatch == null && !pinSource && cleanCanon.isNotBlank()) {
                relays.values.firstOrNull {
                    it.canonicalGroupKey == cleanCanon && it.status != "error" && !it.fallbackDirect
                }
            } else null
            (directMatch ?: canonMatch)?.also { ex ->
                ex.viewers.remove(prewarmKey)
                ex.viewers.add(cleanDev)
                ex.lastAccessAtMs = System.currentTimeMillis()
            }
        }
        if (existing != null) {
            if (existing.readyLatch.count > 0) {
                runCatching { existing.readyLatch.await(STARTUP_AWAIT_MS, TimeUnit.MILLISECONDS) }
            }
            if (!existing.fallbackDirect && existing.status != "error" &&
                synchronized(existing.videoTrack.lock) { existing.videoTrack.segments.isNotEmpty() }
            ) {
                existing.lastAccessAtMs = System.currentTimeMillis()
                return RelayTuneResult(
                    mode = "relay",
                    relayKey = existing.key,
                    playlistUrl = "/api/relay/${existing.key}/master.m3u8",
                    viewers = existing.viewers.size,
                    hasAudioTrack = existing.hasAudioTrack,
                )
            }
        }

        val state: RelayChannelState = synchronized(relayOpLock) {
            val existingInner = relays[key]?.takeIf { it.status != "error" && !it.fallbackDirect }
                ?: if (!pinSource && cleanCanon.isNotBlank()) {
                    relays.values.firstOrNull { it.canonicalGroupKey == cleanCanon && it.status != "error" && !it.fallbackDirect }
                } else null
            if (existingInner != null) {
                existingInner.viewers.remove(prewarmKey)
                existingInner.viewers.add(cleanDev)
                existingInner.lastAccessAtMs = System.currentTimeMillis()
                return@synchronized existingInner
            }

            if (relays.size >= MAX_ACTIVE_RELAYS) {
                relays.values
                    .filter { it.key != key }
                    .minByOrNull { it.lastAccessAtMs }
                    ?.let { victim -> stopRelay(victim.key) }
            }

            val orderedCandidates = buildOrderedCandidates(key, cleanCanon, cleanUrl, fallbackUrls, pinSource)

            stopRelay(key)
            val dir = File(rootDir, "${key}_${System.nanoTime()}").apply { mkdirs() }
            val created = RelayChannelState(
                key = key,
                channelId = cleanCh,
                channelName = channelName.ifBlank { "Live TV" },
                streamUrl = cleanUrl,
                candidateUrls = orderedCandidates,
                dir = dir,
                canonicalGroupKey = cleanCanon,
            )
            created.viewers.add(cleanDev)
            relays[key] = created
            created.job = scope.launch {
                runRelay(created)
            }
            created
        }

        runCatching { state.readyLatch.await(STARTUP_AWAIT_MS, TimeUnit.MILLISECONDS) }
        val hasSegs = synchronized(state.videoTrack.lock) { state.videoTrack.segments.isNotEmpty() }
        if (state.fallbackDirect || state.status == "error" || !hasSegs) {
            val reason = state.error.ifBlank { if (state.fallbackDirect) "drm_or_unsupported" else "startup_timeout" }
            if (reason != "drm_or_unsupported") {
                diag.log(
                    code = DiagnosticCodes.SRV_RELAY_START_FAIL,
                    severity = "error",
                    channelId = cleanCh,
                    channelName = state.channelName,
                    relayKey = key,
                    streamUrl = cleanUrl,
                    deviceId = cleanDev,
                    message = "Avvio Live Relay fallito ($reason)",
                    details = mapOf(
                        "reason" to reason,
                        "candidateCount" to state.candidateUrls.size,
                    ),
                    throttleMs = 10_000L,
                )
            }
            stopRelay(key, onlyIfState = state)
            return RelayTuneResult(
                mode = "direct",
                url = cleanUrl,
                reason = reason,
            )
        }

        state.lastAccessAtMs = System.currentTimeMillis()
        return RelayTuneResult(
            mode = "relay",
            relayKey = key,
            playlistUrl = "/api/relay/$key/master.m3u8",
            viewers = state.viewers.size,
            hasAudioTrack = state.hasAudioTrack,
        )
    }

    private fun buildOrderedCandidates(
        key: String,
        canonicalGroupKey: String,
        cleanUrl: String,
        fallbackUrls: List<String>,
        pinSource: Boolean,
    ): List<String> {
        if (pinSource) {
            return buildList {
                add(cleanUrl)
                for (alt in fallbackUrls) {
                    val cleanAlt = alt.trim()
                    val lowerAlt = cleanAlt.lowercase(Locale.ROOT)
                    if (cleanAlt.isNotBlank() &&
                        cleanAlt != cleanUrl &&
                        (lowerAlt.startsWith("http://") || lowerAlt.startsWith("https://")) &&
                        !lowerAlt.contains(".mpd")
                    ) {
                        add(cleanAlt)
                    }
                }
            }.distinct()
        }
        val rawCandidates = buildList {
            add(cleanUrl)
            for (alt in fallbackUrls) {
                val cleanAlt = alt.trim()
                val lowerAlt = cleanAlt.lowercase(Locale.ROOT)
                if (cleanAlt.isNotBlank() &&
                    cleanAlt != cleanUrl &&
                    (lowerAlt.startsWith("http://") || lowerAlt.startsWith("https://")) &&
                    !lowerAlt.contains(".mpd")
                ) {
                    add(cleanAlt)
                }
            }
        }.distinct()

        val groupPreferred = if (canonicalGroupKey.isNotBlank()) preferredCandidateCache["grp:$canonicalGroupKey"] else null
        val preferred = groupPreferred ?: preferredCandidateCache[key]
        val usePreferred = preferred != null &&
            System.currentTimeMillis() - preferred.second <= PREFERRED_CACHE_TTL_MS &&
            preferred.first in rawCandidates

        val scored = rawCandidates.sortedBy { cand ->
            val prefBonus = if (usePreferred && preferred?.first == cand) -180 else 0
            val dbAdj = db?.persistentSourceScoreAdjustment(canonicalGroupKey, cand) ?: 0
            staticUrlReliabilityScore(cand) + healthScoreAdjustment(cand) + dbAdj + prefBonus
        }
        return scored
    }

    fun prewarm(
        deviceId: String,
        channelId: String,
        channelName: String,
        streamUrl: String,
        fallbackUrls: List<String> = emptyList(),
    ): Boolean {
        val cleanUrl = streamUrl.trim()
        val lower = cleanUrl.lowercase(Locale.ROOT)
        if ((!lower.startsWith("http://") && !lower.startsWith("https://")) || lower.contains(".mpd")) {
            return false
        }
        val hasHlsFallback = fallbackUrls.any { it.contains(".m3u8", ignoreCase = true) }
        if (AUDIO_RADIO_RE.containsMatchIn(cleanUrl) && !lower.contains(".m3u8") && !hasHlsFallback) {
            return false
        }

        val cleanDev = deviceId.trim().ifBlank { "default" }
        val prewarmKey = "prewarm-$cleanDev"
        val cleanCh = channelId.trim()
        val key = computeRelayKey(cleanCh, cleanUrl)

        relays.values.forEach { other ->
            if (other.key != key) {
                other.viewers.remove(prewarmKey)
            }
        }

        synchronized(relayOpLock) {
            val existing = relays[key]
            if (existing != null && existing.status != "error" && !existing.fallbackDirect) {
                existing.viewers.add(prewarmKey)
                existing.lastAccessAtMs = System.currentTimeMillis()
                return true
            }

            if (relays.size >= MAX_ACTIVE_RELAYS) {
                val evictable = relays.values
                    .filter { s -> s.key != key && (s.viewers.isEmpty() || s.viewers.all { it.startsWith("prewarm-") }) }
                    .minByOrNull { it.lastAccessAtMs }
                    ?: return false
                stopRelay(evictable.key)
            }

            val orderedCandidates = buildOrderedCandidates(key, "", cleanUrl, fallbackUrls, pinSource = false)

            stopRelay(key)
            val dir = File(rootDir, "${key}_${System.nanoTime()}").apply { mkdirs() }
            val state = RelayChannelState(
                key = key,
                channelId = cleanCh,
                channelName = channelName.ifBlank { "Live TV" },
                streamUrl = cleanUrl,
                candidateUrls = orderedCandidates,
                dir = dir,
            )
            state.viewers.add(prewarmKey)
            relays[key] = state
            state.job = scope.launch {
                runRelay(state)
            }
            return true
        }
    }

    fun release(deviceId: String, channelId: String? = null) {
        val cleanDev = deviceId.trim().ifBlank { return }
        val prewarmKey = "prewarm-$cleanDev"
        val cleanCh = channelId?.trim().orEmpty()
        relays.values.forEach { state ->
            if (cleanCh.isBlank() || state.channelId == cleanCh) {
                state.viewers.remove(cleanDev)
                state.viewers.remove(prewarmKey)
            }
        }
    }

    fun touchViewer(deviceId: String, channelId: String) {
        val cleanDev = deviceId.trim()
        val cleanCh = channelId.trim()
        if (cleanCh.isBlank()) return
        val now = System.currentTimeMillis()
        relays.values.forEach { state ->
            if (state.channelId == cleanCh && state.status != "error") {
                state.lastAccessAtMs = now
                if (cleanDev.isNotBlank()) {
                    state.viewers.add(cleanDev)
                }
            }
        }
    }

    fun relayMetadata(key: String): Pair<String, String>? =
        relays[key]?.let { it.channelId to it.channelName }

    fun listActive(): List<RelayStatusItem> {
        val now = System.currentTimeMillis()
        return relays.values.map { state ->
            val highCount = synchronized(state.videoTrack.lock) { state.videoTrack.segments.size }
            val lowCount = synchronized(state.videoLowTrack.lock) { state.videoLowTrack.segments.size }
            val segCount = maxOf(highCount, lowCount)
            RelayStatusItem(
                key = state.key,
                channelId = state.channelId,
                channelName = state.channelName,
                status = state.status,
                viewers = state.viewers.size,
                segmentCount = segCount,
                hasAudioTrack = state.hasAudioTrack,
                bytes = state.bytes.get(),
                uptimeSeconds = ((now - state.startedAtMs) / 1000L).coerceAtLeast(0L),
            )
        }
    }

    fun renderMasterPlaylist(key: String, viewerDeviceId: String = ""): String? {
        val state = relays[key] ?: return null
        state.lastAccessAtMs = System.currentTimeMillis()
        if (viewerDeviceId.isNotBlank()) state.viewers.add(viewerDeviceId.trim())
        val lowResHeight = state.lowResolution?.substringAfter('x', "")?.toIntOrNull() ?: 0
        val exposeLowVariant = state.hasLowVariant &&
            state.lowBandwidth >= 1_200_000 &&
            (lowResHeight == 0 || lowResHeight >= 540)
        if (!state.hasAudioTrack && !exposeLowVariant) {
            return renderTrackPlaylist(key, audio = false, low = false, viewerDeviceId = viewerDeviceId)
        }
        val highBw = state.bandwidth.coerceAtLeast(if (exposeLowVariant) state.lowBandwidth + 500_000 else 500_000)
        return buildString {
            append("#EXTM3U\n")
            append("#EXT-X-VERSION:6\n")
            append("#EXT-X-INDEPENDENT-SEGMENTS\n")
            if (state.hasAudioTrack) {
                append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Audio\",DEFAULT=YES,AUTOSELECT=YES,URI=\"audio.m3u8\"\n")
            }
            append("#EXT-X-STREAM-INF:BANDWIDTH=$highBw")
            state.resolution?.takeIf { it.isNotBlank() }?.let { append(",RESOLUTION=$it") }
            state.codecs?.takeIf { it.isNotBlank() }?.let { append(",CODECS=\"$it\"") }
            if (state.hasAudioTrack) append(",AUDIO=\"audio\"")
            append("\n")
            append("video.m3u8\n")
            if (exposeLowVariant) {
                append("#EXT-X-STREAM-INF:BANDWIDTH=${state.lowBandwidth}")
                state.lowResolution?.takeIf { it.isNotBlank() }?.let { append(",RESOLUTION=$it") }
                state.lowCodecs?.takeIf { it.isNotBlank() }?.let { append(",CODECS=\"$it\"") }
                if (state.hasAudioTrack) append(",AUDIO=\"audio\"")
                append("\n")
                append("video_low.m3u8\n")
            }
        }
    }

    fun renderTrackPlaylist(key: String, audio: Boolean, low: Boolean = false, viewerDeviceId: String = ""): String? {
        val state = relays[key] ?: return null
        val now = System.currentTimeMillis()
        state.lastAccessAtMs = now
        if (viewerDeviceId.isNotBlank()) state.viewers.add(viewerDeviceId.trim())
        val useLow = !audio && low && state.hasLowVariant && !state.lowVideoUrl.isNullOrBlank()
        if (!audio) {
            if (useLow) {
                state.lowLastAccessAtMs = now
                if (state.lowWarmupStarted.compareAndSet(false, true)) {
                    val job = state.job
                    if (job == null || job.isActive) {
                        scope.launch { warmupLowTrack(state) }
                    } else {
                        state.lowReadyLatch.countDown()
                    }
                }
                val lowEmpty = synchronized(state.videoLowTrack.lock) { state.videoLowTrack.segments.isEmpty() }
                if (lowEmpty) {
                    runCatching { state.lowReadyLatch.await(2500L, TimeUnit.MILLISECONDS) }
                }
            } else {
                state.highLastAccessAtMs = now
            }
        }
        val track = when {
            audio -> state.audioTrack
            useLow -> state.videoLowTrack
            else -> state.videoTrack
        }
        val segmentsCopy: List<TrackSegmentEntry>
        val targetDur: Int
        val mediaSeq: Long
        val discSeq: Long
        synchronized(track.lock) {
            segmentsCopy = track.segments.toList()
            targetDur = ceil(track.targetDurationSec).toInt().coerceAtLeast(2)
            mediaSeq = track.mediaSequence
            discSeq = track.discontinuitySequence
        }
        val usesMap = segmentsCopy.any { it.mapFileName != null }
        val hlsVersion = if (usesMap) 6 else 4
        return buildString {
            append("#EXTM3U\n")
            append("#EXT-X-VERSION:$hlsVersion\n")
            append("#EXT-X-TARGETDURATION:$targetDur\n")
            append("#EXT-X-MEDIA-SEQUENCE:$mediaSeq\n")
            if (discSeq > 0L) {
                append("#EXT-X-DISCONTINUITY-SEQUENCE:$discSeq\n")
            }
            if (usesMap) {
                append("#EXT-X-INDEPENDENT-SEGMENTS\n")
            }
            var activeMap: String? = null
            var activeKey: String? = null
            for (seg in segmentsCopy) {
                if (seg.discontinuityBefore) {
                    append("#EXT-X-DISCONTINUITY\n")
                }
                if (seg.keyLine == null && activeKey != null) {
                    append("#EXT-X-KEY:METHOD=NONE\n")
                    activeKey = null
                } else if (seg.keyLine != null && seg.keyLine != activeKey) {
                    activeKey = seg.keyLine
                    append("${seg.keyLine}\n")
                }
                if (seg.mapFileName != null && seg.mapFileName != activeMap) {
                    activeMap = seg.mapFileName
                    append("#EXT-X-MAP:URI=\"${seg.mapFileName}\"\n")
                }
                if (!seg.pdtIso.isNullOrBlank()) {
                    append("#EXT-X-PROGRAM-DATE-TIME:${seg.pdtIso}\n")
                }
                val durStr = String.format(Locale.US, "%.3f", seg.durationSec.coerceAtLeast(0.1))
                append("#EXTINF:$durStr,\n")
                append("${seg.fileName}\n")
            }
        }
    }

    fun resolveSegmentFile(key: String, fileName: String): File? {
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) {
            return null
        }
        val state = relays[key] ?: return null
        state.lastAccessAtMs = System.currentTimeMillis()
        val candidate = File(state.dir, fileName)
        return candidate.takeIf { it.exists() && it.isFile && it.parentFile == state.dir }
    }

    internal fun captureSeedForTimeshift(channelId: String, streamUrl: String): RelaySeedSnapshot? {
        val key = computeRelayKey(channelId.trim(), streamUrl.trim())
        val state = relays[key] ?: relays.values.firstOrNull { it.channelId == channelId && it.status == "active" }
            ?: return null
        if (!state.isHls || state.fallbackDirect || state.status == "error") return null
        val vUrl = state.resolvedVideoUrl ?: return null
        val vSegs: List<TrackSegmentEntry>
        val targetDur: Double
        synchronized(state.videoTrack.lock) {
            if (state.videoTrack.segments.isEmpty()) return null
            vSegs = state.videoTrack.segments.toList().takeLast(2)
            targetDur = state.videoTrack.targetDurationSec
        }
        val aSegs: List<TrackSegmentEntry> = if (state.hasAudioTrack) {
            synchronized(state.audioTrack.lock) {
                state.audioTrack.segments.toList().takeLast(3)
            }
        } else emptyList()
        return RelaySeedSnapshot(
            sourceDir = state.dir,
            videoUrl = vUrl,
            audioUrl = state.resolvedAudioUrl,
            bandwidth = state.bandwidth,
            resolution = state.resolution,
            codecs = state.codecs,
            hasAudioTrack = state.hasAudioTrack && aSegs.isNotEmpty(),
            targetDurationSec = targetDur,
            videoSegments = vSegs,
            audioSegments = aSegs,
        )
    }

    /**
     * Cleans stale relay directories left on disk from previous runs.
     * Must only be called once the owning HTTP server socket has bound successfully.
     */
    fun clearStaleFiles() {
        rootDir.listFiles()?.forEach { child ->
            if (!relays.containsKey(child.name)) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    fun shutdown() {
        relays.values.forEach { state ->
            runCatching { state.job?.cancel() }
            runCatching { state.readyLatch.countDown() }
            runCatching { state.lowReadyLatch.countDown() }
            runCatching { state.dir.deleteRecursively() }
        }
        relays.clear()
        runCatching { scope.cancel() }
    }

    private fun stopRelay(key: String, onlyIfState: RelayChannelState? = null) {
        val removed = synchronized(relayOpLock) {
            val current = relays[key] ?: return
            if (onlyIfState != null && current !== onlyIfState) return
            relays.remove(key) ?: return
        }
        runCatching { removed.job?.cancel() }
        runCatching { removed.readyLatch.countDown() }
        runCatching { removed.lowReadyLatch.countDown() }
        scope.launch {
            delay(FILE_DELETE_GRACE_MS)
            runCatching { removed.dir.deleteRecursively() }
        }
    }

    private fun enqueuePendingDelete(state: RelayChannelState, file: File) {
        val deleteAt = System.currentTimeMillis() + FILE_DELETE_GRACE_MS
        synchronized(state.pendingDeleteLock) {
            state.pendingDeletes.addLast(deleteAt to file)
        }
    }

    private fun clearStartupState(state: RelayChannelState) {
        state.videoTrack.clearForReset()
        state.videoLowTrack.clearForReset()
        state.audioTrack.clearForReset()
        state.bytes.set(0L)
        state.hasLowVariant = false
        state.lowBandwidth = 2_000_000
        state.lowResolution = null
        state.lowCodecs = null
        state.resolvedLowVideoUrl = null
        state.highLastAccessAtMs = 0L
        state.lowLastAccessAtMs = 0L
        state.highPaused = false
        state.lowPaused = false
        state.lowVideoUrl = null
        state.lowWarmupStarted.set(false)
        state.lowReadyLatch = CountDownLatch(1)
        synchronized(state.pendingDeleteLock) {
            state.pendingDeletes.clear()
        }
        state.dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private suspend fun warmupLowTrack(state: RelayChannelState) {
        var ingestedOk = false
        val currentLatch = state.lowReadyLatch
        try {
            val urlStr = state.lowVideoUrl ?: return
            val fetched = fetchHlsTextWithFinalUrl(http, urlStr)
            if (fetched == null) {
                diag.log(
                    code = DiagnosticCodes.SRV_LOW_WARMUP_FAIL,
                    severity = "warn",
                    channelId = state.channelId,
                    channelName = state.channelName,
                    relayKey = state.key,
                    streamUrl = urlStr,
                    message = "Fetch variante video_low fallito",
                    throttleMs = 15_000L,
                )
                return
            }
            if (isPlaceholderOrDeadRedirect(fetched.first) || detectUnsupportedHlsDrm(fetched.second)) return
            val parsed = parseHlsMediaPlaylist(fetched.first, fetched.second)
            if (parsed.segments.isEmpty()) return
            val startupTail = if (parsed.targetDurationSec <= 4.5) 4 else 3
            val skipCount = (parsed.segments.size - startupTail).coerceAtLeast(0)
            if (skipCount > 0) {
                markSegmentsSeen(state.videoLowTrack, parsed.segments.take(skipCount))
            }
            val tail = parsed.segments.drop(skipCount)
            val vEndSeq = synchronized(state.videoTrack.lock) {
                state.videoTrack.mediaSequence + state.videoTrack.segments.size
            }
            val hadExistingLow = synchronized(state.videoLowTrack.lock) {
                if (state.videoLowTrack.segments.isEmpty()) {
                    if (vEndSeq >= tail.size) {
                        state.videoLowTrack.mediaSequence = (vEndSeq - tail.size).coerceAtLeast(0L)
                    }
                    false
                } else {
                    true
                }
            }
            val stats = ingestRelaySegments(
                state = state,
                track = state.videoLowTrack,
                prefix = "vl",
                segments = tail,
                targetDuration = parsed.targetDurationSec,
                downloadClient = http,
                forceFirstDiscontinuity = hadExistingLow,
            )
            ingestedOk = stats.addedCount > 0
        } catch (ce: CancellationException) {
            throw ce
        } catch (err: Exception) {
            Log.w("CasaRelay", "Low variant warmup failed for ${state.channelName}: ${err.message}")
            diag.log(
                code = DiagnosticCodes.SRV_LOW_WARMUP_FAIL,
                severity = "warn",
                channelId = state.channelId,
                channelName = state.channelName,
                relayKey = state.key,
                streamUrl = state.lowVideoUrl.orEmpty(),
                message = "Eccezione warmup variante leggera: ${err.message}",
                throttleMs = 15_000L,
            )
        } finally {
            if (!ingestedOk) {
                state.lowReadyLatch = CountDownLatch(1)
                state.lowWarmupStarted.set(false)
            }
            currentLatch.countDown()
        }
    }

    /* ------------------------------------------------------------------ */
    /* Upstream Receiver Loop                                              */
    /* ------------------------------------------------------------------ */

    private class ActiveHlsUpstream(
        val candidateUrl: String,
        val master: HlsMasterSelection?,
        var variantIndex: Int,
        var videoUrl: HttpUrl,
        var audioUrl: HttpUrl?,
        var lowVideoUrl: HttpUrl?,
        var parsedVideo: ParsedMediaPlaylist,
        var parsedAudio: ParsedMediaPlaylist?,
        val startupStats: IngestStats,
    )

    private fun isPlaceholderOrDeadRedirect(url: HttpUrl): Boolean {
        val lower = url.toString().lowercase(Locale.ROOT)
        return "video_no_available" in lower ||
            "video_non_disponibile" in lower ||
            lower.endsWith(".mp4")
    }

    private class CandidateInitOutcome(
        val upstream: ActiveHlsUpstream? = null,
        val slowOnly: Boolean = false,
    )

    private suspend fun tryInitializeCandidate(
        state: RelayChannelState,
        candidateUrl: String,
        hasUntriedFallback: Boolean,
        preserveTrackSequence: Boolean,
        expectedHasAudioTrack: Boolean?,
    ): CandidateInitOutcome {
        val initial = fetchHlsTextWithFinalUrl(http, candidateUrl) ?: return CandidateInitOutcome()
        val (finalUrl, bodyText) = initial
        if (isPlaceholderOrDeadRedirect(finalUrl)) {
            Log.w("CasaRelay", "Skipping placeholder redirect $finalUrl for ${state.channelName}")
            diag.log(
                code = DiagnosticCodes.SRV_UPSTREAM_PLACEHOLDER,
                severity = "warn",
                channelId = state.channelId,
                channelName = state.channelName,
                relayKey = state.key,
                streamUrl = finalUrl.toString(),
                message = "Sorgente scartata per redirect a segnaposto",
                details = mapOf("candidateUrl" to candidateUrl, "finalUrl" to finalUrl.toString()),
            )
            return CandidateInitOutcome()
        }
        if (!bodyText.trimStart().startsWith("#EXTM3U")) {
            if (bodyText.isEmpty() && state.progressiveCandidateUrl == null) {
                state.progressiveCandidateUrl = finalUrl.toString()
            }
            return CandidateInitOutcome()
        }
        if (detectUnsupportedHlsDrm(bodyText)) {
            state.error = "drm"
            diag.log(
                code = DiagnosticCodes.SRV_RELAY_DRM,
                severity = "warn",
                channelId = state.channelId,
                channelName = state.channelName,
                relayKey = state.key,
                streamUrl = candidateUrl,
                message = "Rilevato DRM HLS non supportato sul relay",
                throttleMs = 15_000L,
            )
            return CandidateInitOutcome()
        }

        val master = if (bodyText.contains("#EXT-X-STREAM-INF")) {
            parseHlsMaster(finalUrl, bodyText) ?: return CandidateInitOutcome()
        } else null

        var variantIdx = if (master != null) {
            master.variants.indexOfLast { it.bandwidth <= INITIAL_MAX_BANDWIDTH_BPS }.let { idx ->
                if (idx >= 0) idx else 0
            }
        } else 0

        val probeHttp = if (hasUntriedFallback) {
            http.newBuilder().callTimeout(3500, TimeUnit.MILLISECONDS).build()
        } else {
            http
        }

        while (true) {
            val chosen = master?.variants?.getOrNull(variantIdx)
            val videoUrl = chosen?.url ?: finalUrl
            val audioUrl = chosen?.audioGroupId?.let { master.audioByGroup[it] }
            val hasAudio = audioUrl != null
            val lowCandidateIdx = if (master != null && chosen != null && variantIdx > 0) {
                val sameAudioBelow = (0 until variantIdx).filter { i ->
                    master.variants[i].audioGroupId == chosen.audioGroupId
                }
                sameAudioBelow.lastOrNull { master.variants[it].bandwidth <= 1_900_000 }
                    ?: sameAudioBelow.lastOrNull { master.variants[it].bandwidth <= 2_600_000 }
                    ?: sameAudioBelow.lastOrNull { master.variants[it].bandwidth <= 3_800_000 }
            } else {
                null
            }
            val lowChosen = lowCandidateIdx?.let { master?.variants?.getOrNull(it) }

            if (expectedHasAudioTrack != null && hasAudio != expectedHasAudioTrack) {
                Log.i("CasaRelay", "Skipping candidate $candidateUrl (hasAudio=$hasAudio != expected=$expectedHasAudioTrack)")
                return CandidateInitOutcome()
            }

            val initialVideo = fetchHlsTextWithFinalUrl(http, videoUrl.toString()) ?: return CandidateInitOutcome()
            if (isPlaceholderOrDeadRedirect(initialVideo.first)) {
                diag.log(
                    code = DiagnosticCodes.SRV_UPSTREAM_PLACEHOLDER,
                    severity = "warn",
                    channelId = state.channelId,
                    channelName = state.channelName,
                    relayKey = state.key,
                    streamUrl = initialVideo.first.toString(),
                    message = "Variante video reindirizzata a segnaposto .mp4",
                    details = mapOf("candidateUrl" to candidateUrl),
                )
                return CandidateInitOutcome()
            }
            if (detectUnsupportedHlsDrm(initialVideo.second)) {
                state.error = "drm"
                diag.log(
                    code = DiagnosticCodes.SRV_RELAY_DRM,
                    severity = "warn",
                    channelId = state.channelId,
                    channelName = state.channelName,
                    relayKey = state.key,
                    streamUrl = videoUrl.toString(),
                    message = "DRM non supportato su variante video",
                    throttleMs = 15_000L,
                )
                return CandidateInitOutcome()
            }
            val parsedV0 = parseHlsMediaPlaylist(initialVideo.first, initialVideo.second)
            val totalDur = parsedV0.segments.sumOf { it.durationSec }
            if (parsedV0.hasEndList && totalDur < 25.0) {
                Log.w("CasaRelay", "Skipping short non-live clip (${totalDur}s) at $videoUrl for ${state.channelName}")
                diag.log(
                    code = DiagnosticCodes.SRV_UPSTREAM_PLACEHOLDER,
                    severity = "warn",
                    channelId = state.channelId,
                    channelName = state.channelName,
                    relayKey = state.key,
                    streamUrl = videoUrl.toString(),
                    message = "Clip non live troppo corta scartata (${String.format(Locale.US, "%.1f", totalDur)}s)",
                    details = mapOf("durationSec" to totalDur, "candidateUrl" to candidateUrl),
                )
                return CandidateInitOutcome()
            }
            if (parsedV0.segments.isEmpty()) return CandidateInitOutcome()

            val startupTail = when {
                parsedV0.targetDurationSec >= 8.0 -> 1
                parsedV0.targetDurationSec <= 3.0 -> 3
                else -> 2
            }
            val vStartIdx = (parsedV0.segments.size - startupTail).coerceAtLeast(0)
            val vStartPdt = parsedV0.segments.getOrNull(vStartIdx)?.pdtEpochMs
            val vStartSeq = parsedV0.mediaSequence + vStartIdx

            val parsedA0 = if (audioUrl != null) {
                fetchHlsTextWithFinalUrl(http, audioUrl.toString())?.let {
                    parseHlsMediaPlaylist(it.first, it.second)
                }
            } else null

            val prevBandwidth = state.bandwidth
            val prevResolution = state.resolution
            val prevCodecs = state.codecs
            val prevHasAudioTrack = state.hasAudioTrack
            val prevResolvedVideoUrl = state.resolvedVideoUrl
            val prevResolvedAudioUrl = state.resolvedAudioUrl
            val prevHasLowVariant = state.hasLowVariant
            val prevLowBandwidth = state.lowBandwidth
            val prevLowResolution = state.lowResolution
            val prevLowCodecs = state.lowCodecs
            val prevResolvedLowVideoUrl = state.resolvedLowVideoUrl
            val prevLowVideoUrl = state.lowVideoUrl

            if (!preserveTrackSequence) {
                clearStartupState(state)
            }
            state.bandwidth = (chosen?.bandwidth ?: 4_500_000).coerceAtLeast(500_000)
            state.resolution = chosen?.resolution
            state.codecs = chosen?.codecs
            state.hasAudioTrack = hasAudio
            state.resolvedVideoUrl = videoUrl.toString()
            state.resolvedAudioUrl = audioUrl?.toString()
            state.hasLowVariant = lowChosen != null
            state.lowBandwidth = (lowChosen?.bandwidth ?: 2_000_000).coerceAtLeast(300_000)
            state.lowResolution = lowChosen?.resolution
            state.lowCodecs = lowChosen?.codecs
            state.resolvedLowVideoUrl = lowChosen?.url?.toString()
            state.lowVideoUrl = lowChosen?.url?.toString()
            if (preserveTrackSequence) {
                state.highPaused = false
                if (state.lowLastAccessAtMs > 0L && lowChosen != null) {
                    state.lowPaused = true
                } else {
                    state.lowPaused = false
                    state.lowWarmupStarted.set(false)
                    state.lowReadyLatch = CountDownLatch(1)
                }
            }

            if (vStartIdx > 0) {
                markSegmentsSeen(state.videoTrack, parsedV0.segments.take(vStartIdx))
            }
            val vSlice = parsedV0.segments.drop(vStartIdx)
            val aSlice = if (parsedA0 != null) {
                val aStartIdx = pickAlignedHlsAudioStart(
                    audioSegments = parsedA0.segments,
                    videoStartPdtMs = vStartPdt,
                    fallbackTailCount = startupTail,
                    videoStartSeq = vStartSeq,
                    audioBaseSeq = parsedA0.mediaSequence,
                )
                if (aStartIdx > 0) {
                    markSegmentsSeen(state.audioTrack, parsedA0.segments.take(aStartIdx))
                }
                parsedA0.segments.drop(aStartIdx)
            } else emptyList()

            val (vStats, _) = supervisorScope {
                val vTask = async {
                    ingestRelaySegments(
                        state = state,
                        track = state.videoTrack,
                        prefix = "v",
                        segments = vSlice,
                        targetDuration = parsedV0.targetDurationSec,
                        downloadClient = if (hasUntriedFallback) probeHttp else http,
                        forceFirstDiscontinuity = preserveTrackSequence,
                    )
                }
                val aTask = async {
                    if (parsedA0 != null && aSlice.isNotEmpty()) {
                        ingestRelaySegments(
                            state = state,
                            track = state.audioTrack,
                            prefix = "a",
                            segments = aSlice,
                            targetDuration = parsedA0.targetDurationSec,
                            downloadClient = http,
                            forceFirstDiscontinuity = preserveTrackSequence,
                        )
                    } else null
                }
                vTask.await() to aTask.await()
            }

            if (vStats.addedCount > 0 && !(hasUntriedFallback && vStats.maxRatio > 0.90)) {
                return CandidateInitOutcome(
                    upstream = ActiveHlsUpstream(
                        candidateUrl = candidateUrl,
                        master = master,
                        variantIndex = variantIdx,
                        videoUrl = videoUrl,
                        audioUrl = audioUrl,
                        lowVideoUrl = lowChosen?.url,
                        parsedVideo = parsedV0,
                        parsedAudio = parsedA0,
                        startupStats = vStats,
                    ),
                )
            }

            if (preserveTrackSequence && vStats.addedCount == 0) {
                state.bandwidth = prevBandwidth
                state.resolution = prevResolution
                state.codecs = prevCodecs
                state.hasAudioTrack = prevHasAudioTrack
                state.resolvedVideoUrl = prevResolvedVideoUrl
                state.resolvedAudioUrl = prevResolvedAudioUrl
                state.hasLowVariant = prevHasLowVariant
                state.lowBandwidth = prevLowBandwidth
                state.lowResolution = prevLowResolution
                state.lowCodecs = prevLowCodecs
                state.resolvedLowVideoUrl = prevResolvedLowVideoUrl
                state.lowVideoUrl = prevLowVideoUrl
            }

            if (master != null && variantIdx > 0) {
                Log.i("CasaRelay", "Stepping down startup ABR on ${state.channelName}: variant $variantIdx -> ${variantIdx - 1}")
                val nextVar = master.variants[variantIdx - 1]
                diag.log(
                    code = DiagnosticCodes.SRV_ABR_STEPDOWN,
                    severity = "warn",
                    channelId = state.channelId,
                    channelName = state.channelName,
                    relayKey = state.key,
                    streamUrl = nextVar.url.toString(),
                    message = "Riduzione ABR in avvio (variante $variantIdx -> ${variantIdx - 1})",
                    details = mapOf(
                        "phase" to "startup",
                        "oldVariant" to variantIdx,
                        "newVariant" to (variantIdx - 1),
                        "bandwidth" to nextVar.bandwidth,
                        "resolution" to (nextVar.resolution ?: ""),
                    ),
                )
                variantIdx -= 1
                continue
            }

            if (hasUntriedFallback) {
                Log.w("CasaRelay", "Startup candidate slow/failed ($candidateUrl, ratio=${String.format(Locale.US, "%.2f", vStats.maxRatio)}), failing over for ${state.channelName}")
                diag.log(
                    code = DiagnosticCodes.SRV_UPSTREAM_SLOW,
                    severity = "warn",
                    channelId = state.channelId,
                    channelName = state.channelName,
                    relayKey = state.key,
                    streamUrl = candidateUrl,
                    message = "Sorgente lenta o fallita in avvio, passo a fallback",
                    details = mapOf(
                        "phase" to "startup",
                        "maxRatio" to vStats.maxRatio,
                        "addedCount" to vStats.addedCount,
                        "failedCount" to vStats.failedCount,
                        "upstreamUrl" to candidateUrl,
                    ),
                    throttleMs = 20_000L,
                )
                return CandidateInitOutcome(slowOnly = true)
            }

            if (synchronized(state.videoTrack.lock) { state.videoTrack.segments.isNotEmpty() }) {
                return CandidateInitOutcome(
                    upstream = ActiveHlsUpstream(
                        candidateUrl = candidateUrl,
                        master = master,
                        variantIndex = variantIdx,
                        videoUrl = videoUrl,
                        audioUrl = audioUrl,
                        lowVideoUrl = lowChosen?.url,
                        parsedVideo = parsedV0,
                        parsedAudio = parsedA0,
                        startupStats = vStats,
                    ),
                )
            }
            return CandidateInitOutcome()
        }
    }

    private suspend fun runRelay(state: RelayChannelState) {
        try {
            var chosenIdx = -1
            var upstream: ActiveHlsUpstream? = null
            var slowButValidIdx: Int? = null
            for (idx in state.candidateUrls.indices) {
                if (!scope.isActive || relays[state.key] !== state) return
                val candUrl = state.candidateUrls[idx]
                val hasMore = idx < state.candidateUrls.lastIndex
                val candStartMs = System.currentTimeMillis()
                val outcome = tryInitializeCandidate(
                    state = state,
                    candidateUrl = candUrl,
                    hasUntriedFallback = hasMore,
                    preserveTrackSequence = false,
                    expectedHasAudioTrack = null,
                )
                if (outcome.upstream != null) {
                    recordStreamSuccess(candUrl, System.currentTimeMillis() - candStartMs)
                    upstream = outcome.upstream
                    chosenIdx = idx
                    break
                }
                if (!outcome.slowOnly) {
                    recordStreamFailure(candUrl, state.error.ifBlank { "init_failed" })
                }
                if (outcome.slowOnly && slowButValidIdx == null) {
                    slowButValidIdx = idx
                }
            }

            if (upstream == null && slowButValidIdx != null && scope.isActive && relays[state.key] === state) {
                val candUrl = state.candidateUrls[slowButValidIdx]
                val candStartMs = System.currentTimeMillis()
                val retryOutcome = tryInitializeCandidate(
                    state = state,
                    candidateUrl = candUrl,
                    hasUntriedFallback = false,
                    preserveTrackSequence = false,
                    expectedHasAudioTrack = null,
                )
                if (retryOutcome.upstream != null) {
                    recordStreamSuccess(candUrl, System.currentTimeMillis() - candStartMs)
                    upstream = retryOutcome.upstream
                    chosenIdx = slowButValidIdx
                } else {
                    recordStreamFailure(candUrl, state.error.ifBlank { "timeout" })
                }
            }

            if (upstream == null) {
                val progUrl = state.progressiveCandidateUrl
                if (!progUrl.isNullOrBlank()) {
                    state.isHls = false
                    clearStartupState(state)
                    runProgressiveRelay(state, progUrl)
                    return
                }
                // Non-HLS progressive MPEG-TS fallback only for primary URL when not placeholder MP4.
                val primaryText = fetchHlsTextWithFinalUrl(http, state.streamUrl)
                if (primaryText != null &&
                    !primaryText.second.trimStart().startsWith("#EXTM3U") &&
                    !isPlaceholderOrDeadRedirect(primaryText.first)
                ) {
                    state.isHls = false
                    clearStartupState(state)
                    runProgressiveRelay(state, primaryText.first.toString())
                    return
                }
                if (state.error == "drm") {
                    state.fallbackDirect = true
                } else {
                    if (state.error.isBlank() && primaryText == null) {
                        val probeErr = runCatching {
                            val req = Request.Builder()
                                .url(state.streamUrl)
                                .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                                .header("Range", "bytes=0-511")
                                .build()
                            http.newCall(req).execute().use { resp ->
                                if (resp.code >= 400) {
                                    val bodyLow = runCatching {
                                        resp.body?.byteStream()?.use { bs ->
                                            val buf = ByteArray(200)
                                            val n = bs.read(buf)
                                            if (n > 0) String(buf, 0, n, Charsets.UTF_8).lowercase(Locale.ROOT) else ""
                                        }.orEmpty()
                                    }.getOrDefault("")
                                    if ("max connections" in bodyLow) "http_${resp.code}_max_connections"
                                    else "http_${resp.code}"
                                } else {
                                    ""
                                }
                            }
                        }.getOrDefault("")
                        if (probeErr.isNotBlank()) state.error = probeErr
                    }
                    state.status = "error"
                    state.error = state.error.ifBlank { "no_working_source" }
                }
                return
            }

            val initialUpstream: ActiveHlsUpstream = upstream
            state.isHls = true
            state.status = "active"
            state.readyLatch.countDown()
            if (chosenIdx > 0 || state.candidateUrls[chosenIdx] != state.streamUrl) {
                preferredCandidateCache[state.key] = initialUpstream.candidateUrl to System.currentTimeMillis()
                Log.i("CasaRelay", "Auto-selected fast mirror for ${state.channelName}: ${initialUpstream.candidateUrl}")
                diag.log(
                    code = DiagnosticCodes.SRV_MIRROR_FAILOVER,
                    severity = "warn",
                    channelId = state.channelId,
                    channelName = state.channelName,
                    relayKey = state.key,
                    streamUrl = initialUpstream.candidateUrl,
                    message = "Selezionato specchio alternativo in avvio",
                    details = mapOf(
                        "phase" to "startup",
                        "primaryUrl" to state.streamUrl,
                        "selectedUrl" to initialUpstream.candidateUrl,
                        "candidateIndex" to chosenIdx,
                        "candidateCount" to state.candidateUrls.size,
                    ),
                )
            }

            var active: ActiveHlsUpstream = initialUpstream
            val triedCandidates = hashSetOf(chosenIdx)
            var consecutiveFailures = 0
            var consecutiveSlowPollCycles = 0

            while (scope.isActive && relays[state.key] === state && state.status != "error") {
                val trackDurSec = if (state.highPaused && !state.lowPaused && state.hasLowVariant) {
                    state.videoLowTrack.targetDurationSec
                } else {
                    state.videoTrack.targetDurationSec
                }
                val pollDelayMs = ((trackDurSec * 1000.0) * 0.25)
                    .toLong()
                    .coerceIn(800L, 1800L)
                delay(pollDelayMs)
                if (relays[state.key] !== state || !scope.isActive) break

                val now = System.currentTimeMillis()
                val lowRecentlyUsed = active.lowVideoUrl != null &&
                    state.lowLastAccessAtMs > 0L &&
                    now - state.lowLastAccessAtMs <= 20_000L
                val highRecentlyUsed = state.highLastAccessAtMs > 0L &&
                    now - state.highLastAccessAtMs <= 20_000L
                val pollHigh = active.lowVideoUrl == null || !lowRecentlyUsed || highRecentlyUsed
                val pollLow = active.lowVideoUrl != null && lowRecentlyUsed && state.lowWarmupStarted.get()
                if (!pollHigh) {
                    state.highPaused = true
                }

                val (hStats, lStats) = supervisorScope {
                    val hTask = async {
                        if (!pollHigh) return@async null
                        val wasPaused = state.highPaused
                        if (wasPaused) state.highPaused = false
                        val fetched = fetchHlsTextWithFinalUrl(http, active.videoUrl.toString()) ?: return@async null
                        val parsed = parseHlsMediaPlaylist(fetched.first, fetched.second)
                        val segs = if (wasPaused) {
                            val dropCount = (parsed.segments.size - 2).coerceAtLeast(0)
                            if (dropCount > 0) markSegmentsSeen(state.videoTrack, parsed.segments.take(dropCount))
                            parsed.segments.drop(dropCount)
                        } else {
                            parsed.segments
                        }
                        ingestRelaySegments(
                            state = state,
                            track = state.videoTrack,
                            prefix = "v",
                            segments = segs,
                            targetDuration = parsed.targetDurationSec,
                            downloadClient = http,
                            forceFirstDiscontinuity = wasPaused,
                        )
                    }
                    val lTask = async {
                        val lowUrl = active.lowVideoUrl ?: return@async null
                        if (!pollLow) {
                            if (state.lowWarmupStarted.get() && state.lowLastAccessAtMs > 0L) {
                                state.lowPaused = true
                            }
                            return@async null
                        }
                        val wasPaused = state.lowPaused
                        if (wasPaused) state.lowPaused = false
                        val fetched = fetchHlsTextWithFinalUrl(http, lowUrl.toString()) ?: return@async null
                        val parsed = parseHlsMediaPlaylist(fetched.first, fetched.second)
                        val segs = if (wasPaused) {
                            val dropCount = (parsed.segments.size - 2).coerceAtLeast(0)
                            if (dropCount > 0) markSegmentsSeen(state.videoLowTrack, parsed.segments.take(dropCount))
                            parsed.segments.drop(dropCount)
                        } else {
                            parsed.segments
                        }
                        ingestRelaySegments(
                            state = state,
                            track = state.videoLowTrack,
                            prefix = "vl",
                            segments = segs,
                            targetDuration = parsed.targetDurationSec,
                            downloadClient = http,
                            forceFirstDiscontinuity = wasPaused,
                        )
                    }
                    val aTask = async {
                        val aUrl = active.audioUrl ?: return@async
                        val fetched = fetchHlsTextWithFinalUrl(http, aUrl.toString()) ?: return@async
                        val parsed = parseHlsMediaPlaylist(fetched.first, fetched.second)
                        ingestRelaySegments(
                            state = state,
                            track = state.audioTrack,
                            prefix = "a",
                            segments = parsed.segments,
                            targetDuration = parsed.targetDurationSec,
                            downloadClient = http,
                        )
                    }
                    val hRes = hTask.await()
                    val lRes = lTask.await()
                    aTask.await()
                    hRes to lRes
                }

                val activeStats = when {
                    pollHigh && pollLow -> hStats ?: lStats
                    pollLow -> lStats
                    else -> hStats
                }

                if (activeStats == null) {
                    consecutiveFailures += 1
                    if (consecutiveFailures == 4) {
                        diag.log(
                            code = DiagnosticCodes.SRV_UPSTREAM_FAIL,
                            severity = "error",
                            channelId = state.channelId,
                            channelName = state.channelName,
                            relayKey = state.key,
                            streamUrl = active.videoUrl.toString(),
                            message = "4 fallimenti consecutivi nel polling upstream",
                            details = mapOf("consecutiveFailures" to consecutiveFailures),
                            throttleMs = 20_000L,
                        )
                    }
                } else {
                    consecutiveFailures = 0
                    if (activeStats.addedCount > 0 && (activeStats.slowCount > 0 || activeStats.failedCount > 0)) {
                        consecutiveSlowPollCycles += 1
                        if (consecutiveSlowPollCycles >= 2) {
                            diag.log(
                                code = DiagnosticCodes.SRV_UPSTREAM_SLOW,
                                severity = "warn",
                                channelId = state.channelId,
                                channelName = state.channelName,
                                relayKey = state.key,
                                streamUrl = active.videoUrl.toString(),
                                message = "Rallentamento prolungato download upstream",
                                details = mapOf(
                                    "phase" to "steady",
                                    "cycles" to consecutiveSlowPollCycles,
                                    "maxRatio" to activeStats.maxRatio,
                                    "slowCount" to activeStats.slowCount,
                                    "failedCount" to activeStats.failedCount,
                                    "upstreamUrl" to active.videoUrl.toString(),
                                ),
                                throttleMs = 20_000L,
                            )
                        }
                    } else if (activeStats.addedCount > 0) {
                        consecutiveSlowPollCycles = 0
                    } else if (activeStats.failedCount > 0) {
                        consecutiveFailures += 1
                    }
                }

                // Dynamic ABR step-down on sustained slow segments
                val master = active.master
                if (pollHigh && consecutiveSlowPollCycles >= 2 && master != null && active.variantIndex > 0) {
                    val nextVarIdx = active.variantIndex - 1
                    val nextVar = master.variants[nextVarIdx]
                    val nextAudio = nextVar.audioGroupId?.let { master.audioByGroup[it] }
                    val curHasAudio = active.audioUrl != null
                    if ((nextAudio != null) == curHasAudio) {
                        Log.i("CasaRelay", "Dynamic ABR step-down for ${state.channelName}: variant ${active.variantIndex} -> $nextVarIdx (${nextVar.bandwidth} bps)")
                        diag.log(
                            code = DiagnosticCodes.SRV_ABR_STEPDOWN,
                            severity = "warn",
                            channelId = state.channelId,
                            channelName = state.channelName,
                            relayKey = state.key,
                            streamUrl = nextVar.url.toString(),
                            message = "Riduzione ABR dinamica (variante ${active.variantIndex} -> $nextVarIdx)",
                            details = mapOf(
                                "phase" to "steady",
                                "oldVariant" to active.variantIndex,
                                "newVariant" to nextVarIdx,
                                "bandwidth" to nextVar.bandwidth,
                                "resolution" to (nextVar.resolution ?: ""),
                            ),
                        )
                        active.variantIndex = nextVarIdx
                        active.videoUrl = nextVar.url
                        active.audioUrl = nextAudio
                        val nextLowIdx = if (nextVarIdx > 0) {
                            val sameAudioBelow = (0 until nextVarIdx).filter { i ->
                                master.variants[i].audioGroupId == nextVar.audioGroupId
                            }
                            sameAudioBelow.lastOrNull { master.variants[it].bandwidth <= 1_900_000 }
                                ?: sameAudioBelow.lastOrNull { master.variants[it].bandwidth <= 2_600_000 }
                                ?: sameAudioBelow.lastOrNull { master.variants[it].bandwidth <= 3_800_000 }
                        } else {
                            null
                        }
                        val nextLowVar = nextLowIdx?.let { master.variants.getOrNull(it) }
                        active.lowVideoUrl = nextLowVar?.url
                        state.bandwidth = nextVar.bandwidth.coerceAtLeast(500_000)
                        state.resolution = nextVar.resolution
                        state.codecs = nextVar.codecs
                        state.resolvedVideoUrl = nextVar.url.toString()
                        state.resolvedAudioUrl = nextAudio?.toString()
                        state.hasLowVariant = nextLowVar != null
                        state.lowBandwidth = (nextLowVar?.bandwidth ?: 2_000_000).coerceAtLeast(300_000)
                        state.lowResolution = nextLowVar?.resolution
                        state.lowCodecs = nextLowVar?.codecs
                        state.resolvedLowVideoUrl = nextLowVar?.url?.toString()
                        state.lowVideoUrl = nextLowVar?.url?.toString()
                        if (nextLowVar != null && state.lowLastAccessAtMs > 0L) {
                            state.lowPaused = true
                        }
                        val vFetch = fetchHlsTextWithFinalUrl(http, active.videoUrl.toString())
                        if (vFetch != null) {
                            val parsedV = parseHlsMediaPlaylist(vFetch.first, vFetch.second)
                            val vDrop = (parsedV.segments.size - 1).coerceAtLeast(0)
                            if (vDrop > 0) markSegmentsSeen(state.videoTrack, parsedV.segments.take(vDrop))
                            val tail = parsedV.segments.drop(vDrop)
                            val tailPdt = tail.firstOrNull()?.pdtEpochMs
                            ingestRelaySegments(state, state.videoTrack, "v", tail, parsedV.targetDurationSec, forceFirstDiscontinuity = true)
                            active.audioUrl?.let { aU ->
                                fetchHlsTextWithFinalUrl(http, aU.toString())?.let { aFetch ->
                                    val parsedA = parseHlsMediaPlaylist(aFetch.first, aFetch.second)
                                    val aIdx = pickAlignedHlsAudioStart(parsedA.segments, tailPdt, fallbackTailCount = 1)
                                    if (aIdx > 0) markSegmentsSeen(state.audioTrack, parsedA.segments.take(aIdx))
                                    ingestRelaySegments(state, state.audioTrack, "a", parsedA.segments.drop(aIdx), parsedA.targetDurationSec, forceFirstDiscontinuity = true)
                                }
                            }
                        }
                        consecutiveSlowPollCycles = 0
                        continue
                    }
                }

                // Mid-stream failover to an alternate mirror if primary stalls hard
                val needFailover = consecutiveFailures >= 4 || consecutiveSlowPollCycles >= 2
                if (needFailover) {
                    if (consecutiveSlowPollCycles >= 2) {
                        recordStreamStall(active.candidateUrl, state.canonicalGroupKey)
                    } else {
                        recordStreamFailure(active.candidateUrl, "midstream_fail")
                    }
                    var switched: ActiveHlsUpstream? = null
                    for (candIdx in state.candidateUrls.indices) {
                        if (!triedCandidates.add(candIdx)) continue
                        val candUrl = state.candidateUrls[candIdx]
                        val outcome = tryInitializeCandidate(
                            state = state,
                            candidateUrl = candUrl,
                            hasUntriedFallback = false,
                            preserveTrackSequence = true,
                            expectedHasAudioTrack = state.hasAudioTrack,
                        )
                        if (outcome.upstream != null) {
                            recordStreamSuccess(candUrl, 500L)
                            switched = outcome.upstream
                            preferredCandidateCache[state.key] = outcome.upstream.candidateUrl to System.currentTimeMillis()
                            Log.i("CasaRelay", "Mid-stream upstream failover for ${state.channelName} -> $candUrl")
                            diag.log(
                                code = DiagnosticCodes.SRV_MIRROR_FAILOVER,
                                severity = "warn",
                                channelId = state.channelId,
                                channelName = state.channelName,
                                relayKey = state.key,
                                streamUrl = candUrl,
                                message = "Failover mid-stream su specchio alternativo",
                                details = mapOf(
                                    "phase" to "midstream",
                                    "primaryUrl" to active.candidateUrl,
                                    "selectedUrl" to candUrl,
                                    "candidateIndex" to candIdx,
                                    "candidateCount" to state.candidateUrls.size,
                                ),
                            )
                            break
                        } else {
                            recordStreamFailure(candUrl, "midstream_fail")
                        }
                    }
                    if (switched != null) {
                        active = switched
                        consecutiveFailures = 0
                        consecutiveSlowPollCycles = 0
                        continue
                    }
                }

                if (consecutiveFailures >= 8) {
                    recordStreamFailure(active.candidateUrl, "upstream_lost")
                    state.status = "error"
                    state.error = "upstream_lost"
                    diag.log(
                        code = DiagnosticCodes.SRV_UPSTREAM_FAIL,
                        severity = "error",
                        channelId = state.channelId,
                        channelName = state.channelName,
                        relayKey = state.key,
                        streamUrl = active.videoUrl.toString(),
                        message = "Sorgente upstream persa dopo 8 fallimenti (upstream_lost)",
                        details = mapOf("consecutiveFailures" to consecutiveFailures),
                        throttleMs = 20_000L,
                    )
                    break
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (err: Exception) {
            Log.w("CasaRelay", "Relay error for ${state.key}: ${err.message}")
            state.status = "error"
            state.error = err.message ?: "relay_error"
            diag.log(
                code = DiagnosticCodes.SRV_UPSTREAM_FAIL,
                severity = "error",
                channelId = state.channelId,
                channelName = state.channelName,
                relayKey = state.key,
                streamUrl = state.resolvedVideoUrl ?: state.streamUrl,
                message = "Eccezione relay: ${err.message ?: "relay_error"}",
                throttleMs = 15_000L,
            )
        } finally {
            state.readyLatch.countDown()
            state.lowReadyLatch.countDown()
        }
    }

    private fun markSegmentsSeen(
        track: RelayTrackBuffer,
        skipped: List<ParsedHlsSegment>,
    ) {
        if (skipped.isEmpty()) return
        synchronized(track.lock) {
            for (seg in skipped) {
                val url = seg.url.toString()
                if (track.seenUrls.add(url)) {
                    track.seenUrlQueue.addLast(url)
                    while (track.seenUrlQueue.size > MAX_SEEN_URLS) {
                        track.seenUrls.remove(track.seenUrlQueue.removeFirst())
                    }
                }
            }
        }
    }

    private suspend fun ingestRelaySegments(
        state: RelayChannelState,
        track: RelayTrackBuffer,
        prefix: String,
        segments: List<ParsedHlsSegment>,
        targetDuration: Double,
        downloadClient: OkHttpClient = http,
        forceFirstDiscontinuity: Boolean = false,
    ): IngestStats {
        val toDownload = synchronized(track.lock) {
            track.targetDurationSec = targetDuration
            val cutoffPdtMs = if (!forceFirstDiscontinuity) track.lastPdtEndMs?.minus(5_000L) else null
            segments.filter { seg ->
                val url = seg.url.toString()
                if (track.seenUrls.contains(url)) return@filter false
                if (cutoffPdtMs != null && seg.pdtEpochMs != null && seg.pdtEpochMs < cutoffPdtMs) {
                    if (track.seenUrls.add(url)) {
                        track.seenUrlQueue.addLast(url)
                        while (track.seenUrlQueue.size > MAX_SEEN_URLS) {
                            track.seenUrls.remove(track.seenUrlQueue.removeFirst())
                        }
                    }
                    return@filter false
                }
                true
            }
        }
        if (toDownload.isEmpty()) {
            return IngestStats(0, 0, 0, 0, 0.0)
        }

        var addedCount = 0
        var failedCount = 0
        var slowCount = 0
        var verySlowCount = 0
        var maxRatio = 0.0
        var applyDisc = forceFirstDiscontinuity

        for (batch in toDownload.chunked(3)) {
            if (!scope.isActive || relays[state.key] !== state) break

            // Ensure unique init maps & encryption keys for this batch are fetched once before parallel downloads.
            val batchMaps = HashMap<String, String>()
            val batchKeys = HashMap<String, Pair<String, String>>()
            for (seg in batch) {
                seg.mapUrl?.let { mapHttpUrl ->
                    val mKey = mapHttpUrl.toString()
                    if (!batchMaps.containsKey(mKey)) {
                        val existing = synchronized(track.lock) { track.downloadedMaps[mKey] }
                        val fname = existing ?: run {
                            val idx = synchronized(track.lock) {
                                val c = track.nextFileSeq
                                track.nextFileSeq += 1L
                                c
                            }
                            val ext = guessMediaExtension(mapHttpUrl.encodedPath, defaultExt = "mp4")
                            val newName = "${prefix}_init_${idx}.$ext"
                            val res = downloadBinaryAtomicallyTimed(downloadClient, mKey, File(state.dir, newName))
                            if (res.bytes > 0L) {
                                state.bytes.addAndGet(res.bytes)
                                synchronized(track.lock) { track.downloadedMaps[mKey] = newName }
                                newName
                            } else null
                        }
                        if (fname != null) batchMaps[mKey] = fname
                    }
                }
                if (seg.keyMethod != null && seg.keyMethod != "NONE" && seg.keyUri != null) {
                    val kKey = seg.keyUri.toString()
                    if (!batchKeys.containsKey(kKey)) {
                        val existing = synchronized(track.lock) { track.downloadedKeys[kKey] }
                        val localName = existing ?: run {
                            val idx = synchronized(track.lock) {
                                val c = track.nextFileSeq
                                track.nextFileSeq += 1L
                                c
                            }
                            val newName = "${prefix}_key_${idx}.key"
                            val res = downloadBinaryAtomicallyTimed(downloadClient, kKey, File(state.dir, newName))
                            if (res.bytes > 0L) {
                                state.bytes.addAndGet(res.bytes)
                                synchronized(track.lock) { track.downloadedKeys[kKey] = newName }
                                newName
                            } else null
                        }
                        if (localName != null) {
                            val extra = seg.keyRest?.takeIf { it.isNotBlank() }?.let { ",$it" }.orEmpty()
                            batchKeys[kKey] = localName to "#EXT-X-KEY:METHOD=${seg.keyMethod},URI=\"$localName\"$extra"
                        }
                    }
                }
            }

            // Pre-assign sequence numbers in exact playlist order, then download concurrently.
            val planned = batch.map { seg ->
                val mapUrlStr = seg.mapUrl?.toString()
                val mapFileName = mapUrlStr?.let { batchMaps[it] }
                val keyUrlStr = seg.keyUri?.toString()
                val keyPair = keyUrlStr?.let { batchKeys[it] }
                val seqNum = synchronized(track.lock) {
                    val cur = track.nextFileSeq
                    track.nextFileSeq += 1L
                    cur
                }
                val segExt = guessMediaExtension(seg.url.encodedPath, defaultExt = if (mapFileName != null) "mp4" else "ts")
                val segFileName = String.format(Locale.US, "%s_%06d.%s", prefix, seqNum, segExt)
                Triple(seg, segFileName, Triple(mapFileName, keyPair?.first, keyPair?.second))
            }

            val results = supervisorScope {
                planned.map { item ->
                    async {
                        val res = downloadBinaryAtomicallyTimed(
                            http = downloadClient,
                            url = item.first.url.toString(),
                            dest = File(state.dir, item.second),
                        )
                        item to res
                    }
                }.awaitAll()
            }

            for ((item, dlRes) in results) {
                val (seg, segFileName, mediaMeta) = item
                val (mapFileName, localKeyName, keyLine) = mediaMeta
                val canonicalUrl = seg.url.toString()
                if (dlRes.bytes <= 0L) {
                    failedCount += 1
                    runCatching { File(state.dir, segFileName).delete() }
                    continue
                }

                val ratio = dlRes.elapsedMs.toDouble() / (seg.durationSec.coerceAtLeast(1.0) * 1000.0)
                if (ratio > maxRatio) maxRatio = ratio
                if (ratio > 0.85) slowCount += 1
                if (ratio > 1.15) verySlowCount += 1

                state.bytes.addAndGet(dlRes.bytes)
                val evictedFiles = mutableListOf<File>()
                var orphanFile: File? = null
                var pdtGapMs: Long? = null
                synchronized(track.lock) {
                    if (track.seenUrls.add(canonicalUrl)) {
                        track.seenUrlQueue.addLast(canonicalUrl)
                        while (track.seenUrlQueue.size > MAX_SEEN_URLS) {
                            track.seenUrls.remove(track.seenUrlQueue.removeFirst())
                        }
                        val hasExisting = track.segments.isNotEmpty()
                        val gap = if (seg.pdtEpochMs != null && track.lastPdtEndMs != null) {
                            kotlin.math.abs(seg.pdtEpochMs - track.lastPdtEndMs!!)
                        } else {
                            0L
                        }
                        val pdtGapDisc = hasExisting && gap > 2500L
                        if (pdtGapDisc) pdtGapMs = gap
                        track.segments.addLast(
                            TrackSegmentEntry(
                                fileName = segFileName,
                                durationSec = seg.durationSec,
                                discontinuityBefore = hasExisting && (seg.discontinuityBefore || applyDisc || pdtGapDisc),
                                mapFileName = mapFileName,
                                keyLine = keyLine,
                                keyFileName = localKeyName,
                                pdtIso = seg.pdtIso,
                                canonicalUrl = canonicalUrl,
                                mapUrl = seg.mapUrl?.toString(),
                                keyUrl = seg.keyUri?.toString(),
                                keyAttrRest = seg.keyRest,
                                byteLength = dlRes.bytes,
                            ),
                        )
                        if (seg.pdtEpochMs != null) {
                            track.lastPdtEndMs = seg.pdtEpochMs + (seg.durationSec * 1000.0).toLong()
                        }
                        applyDisc = false
                        addedCount += 1
                        pruneTrackBufferLocked(state, track, MAX_HLS_RING_SEGMENTS, evictedFiles)
                    } else {
                        orphanFile = File(state.dir, segFileName)
                    }
                }
                pdtGapMs?.let { gapVal ->
                    diag.log(
                        code = DiagnosticCodes.SRV_PDT_GAP,
                        severity = "info",
                        channelId = state.channelId,
                        channelName = state.channelName,
                        relayKey = state.key,
                        streamUrl = canonicalUrl,
                        message = "Salto PDT ${gapVal}ms su traccia $prefix, inserita discontinuità",
                        details = mapOf("gapMs" to gapVal, "prefix" to prefix),
                        throttleMs = 15_000L,
                    )
                }
                evictedFiles.forEach { f -> enqueuePendingDelete(state, f) }
                orphanFile?.let { f -> runCatching { f.delete() } }
            }
        }
        return IngestStats(
            addedCount = addedCount,
            failedCount = failedCount,
            slowCount = slowCount,
            verySlowCount = verySlowCount,
            maxRatio = maxRatio,
        )
    }

    private suspend fun runProgressiveRelay(state: RelayChannelState, url: String) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", STREAM_USER_AGENT)
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                state.status = "error"
                state.error = "HTTP ${resp.code}"
                return
            }
            val input = resp.body?.byteStream() ?: run {
                state.status = "error"
                state.error = "empty_stream"
                return
            }
            state.resolvedVideoUrl = url
            val buffer = ByteArray(64 * 1024)
            val carry = ByteArray(188 * 2)
            var carryLen = 0
            var synced = false
            var chunkIdx = 0L
            val targetChunkBytes = 1_200_000L
            var tmpFile = File(state.dir, String.format(Locale.US, "v_%06d.ts.tmp", chunkIdx))
            var out = FileOutputStream(tmpFile)
            var chunkBytes = 0L
            var chunkStartMs = System.currentTimeMillis()
            var hitEof = false
            try {
                while (scope.isActive && relays[state.key] === state) {
                    val read = input.read(buffer)
                    if (read < 0) {
                        hitEof = true
                        break
                    }
                    if (read == 0) continue

                    var startOff = 0
                    var availLen = read
                    val combined = if (carryLen > 0) {
                        ByteArray(carryLen + read).also {
                            System.arraycopy(carry, 0, it, 0, carryLen)
                            System.arraycopy(buffer, 0, it, carryLen, read)
                        }
                    } else buffer
                    if (carryLen > 0) {
                        availLen = carryLen + read
                        carryLen = 0
                    }

                    if (!synced) {
                        var syncPos = -1
                        for (i in 0 until availLen) {
                            if (combined[i] == 0x47.toByte()) {
                                if (i + 188 < availLen && combined[i + 188] != 0x47.toByte()) continue
                                syncPos = i
                                break
                            }
                        }
                        if (syncPos < 0) continue
                        startOff = syncPos
                        synced = true
                    }

                    val usableLen = availLen - startOff
                    val remainder = usableLen % 188
                    val writeLen = usableLen - remainder
                    if (writeLen > 0) {
                        out.write(combined, startOff, writeLen)
                        chunkBytes += writeLen
                    }
                    if (remainder > 0) {
                        System.arraycopy(combined, startOff + writeLen, carry, 0, remainder)
                        carryLen = remainder
                    }

                    val elapsedMs = System.currentTimeMillis() - chunkStartMs
                    val flushMs = if (chunkIdx == 0L) 750L else 3_000L
                    val flushBytes = if (chunkIdx == 0L) 320_000L else targetChunkBytes
                    val minDur = if (chunkIdx == 0L) 0.75 else 1.5
                    if (chunkBytes > 0L && (elapsedMs >= flushMs || chunkBytes >= flushBytes)) {
                        out.flush()
                        out.close()
                        val finalName = String.format(Locale.US, "v_%06d.ts", chunkIdx)
                        val dest = File(state.dir, finalName)
                        if (!tmpFile.renameTo(dest)) {
                            tmpFile.copyTo(dest, overwrite = true)
                            tmpFile.delete()
                        }
                        val len = dest.length()
                        state.bytes.addAndGet(len)
                        val durSec = (elapsedMs.toDouble() / 1000.0).coerceIn(minDur, 6.0)
                        val toDelete = mutableListOf<File>()
                        synchronized(state.videoTrack.lock) {
                            state.videoTrack.targetDurationSec = maxOf(state.videoTrack.targetDurationSec, durSec)
                            state.videoTrack.segments.addLast(
                                TrackSegmentEntry(
                                    fileName = finalName,
                                    durationSec = durSec,
                                    discontinuityBefore = false,
                                    mapFileName = null,
                                    keyLine = null,
                                    keyFileName = null,
                                    pdtIso = null,
                                    canonicalUrl = "$url#$chunkIdx",
                                    mapUrl = null,
                                    keyUrl = null,
                                    byteLength = len,
                                ),
                            )
                            pruneTrackBufferLocked(state, state.videoTrack, MAX_TS_RING_SEGMENTS, toDelete)
                        }
                        toDelete.forEach { f -> enqueuePendingDelete(state, f) }
                        state.status = "active"
                        state.readyLatch.countDown()

                        chunkIdx++
                        tmpFile = File(state.dir, String.format(Locale.US, "v_%06d.ts.tmp", chunkIdx))
                        out = FileOutputStream(tmpFile)
                        chunkBytes = 0L
                        chunkStartMs = System.currentTimeMillis()
                    }
                }
                if (hitEof && relays[state.key] === state && state.status != "error") {
                    state.status = "error"
                    state.error = "upstream_eof"
                }
            } finally {
                runCatching { out.flush() }
                runCatching { out.close() }
                runCatching { tmpFile.delete() }
            }
        }
    }

    private fun pruneTrackBufferLocked(
        state: RelayChannelState,
        track: RelayTrackBuffer,
        maxSegments: Int,
        outFilesToDelete: MutableList<File>,
    ) {
        while (track.segments.size > maxSegments) {
            val oldest = track.segments.removeFirst()
            track.mediaSequence += 1L
            if (oldest.discontinuityBefore) {
                track.discontinuitySequence += 1L
            }
            if (oldest.byteLength > 0L) {
                state.bytes.addAndGet(-oldest.byteLength)
            }
            outFilesToDelete.add(File(state.dir, oldest.fileName))

            oldest.mapFileName?.let { mapFile ->
                if (track.segments.none { it.mapFileName == mapFile }) {
                    track.downloadedMaps.entries.removeIf { it.value == mapFile }
                    outFilesToDelete.add(File(state.dir, mapFile))
                }
            }
            oldest.keyFileName?.let { keyFile ->
                if (track.segments.none { it.keyFileName == keyFile }) {
                    track.downloadedKeys.entries.removeIf { it.value == keyFile }
                    outFilesToDelete.add(File(state.dir, keyFile))
                }
            }
        }
    }

    private fun computeRelayKey(channelId: String, streamUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$channelId|$streamUrl".toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(12)
        return "r_$hex"
    }

    companion object {
        private const val MAX_ACTIVE_RELAYS = 6
        private const val MAX_HLS_RING_SEGMENTS = 24
        private const val MAX_TS_RING_SEGMENTS = 24
        private const val MAX_SEEN_URLS = 200
        private const val EMPTY_IDLE_TIMEOUT_MS = 15_000L
        private const val PREWARM_IDLE_TIMEOUT_MS = 45_000L
        private const val HARD_IDLE_TIMEOUT_MS = 120_000L
        private const val FILE_DELETE_GRACE_MS = 60_000L
        private const val STARTUP_AWAIT_MS = 5_500L
        private const val INITIAL_MAX_BANDWIDTH_BPS = 7_200_000
        private const val PREFERRED_CACHE_TTL_MS = 30 * 60 * 1000L
        private const val HEALTH_TTL_MS = 30 * 60 * 1000L
        private const val MAX_HEALTH_ENTRIES = 2000
        private val AUDIO_RADIO_RE = Regex(
            """(/stream(\?|/|$)|;stream|\.(mp3|aac|ogg|opus|flac|m4a)(\?|$))""",
            RegexOption.IGNORE_CASE,
        )
    }
}
