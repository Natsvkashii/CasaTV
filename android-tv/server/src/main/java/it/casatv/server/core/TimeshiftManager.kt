package it.casatv.server.core

import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class TimeshiftSessionSnapshot(
    val id: String,
    val deviceId: String,
    val channelId: String,
    val channelName: String,
    val country: String = "",
    val logo: String? = null,
    val status: String, // "recording" | "completed" | "limit_reached" | "error"
    val startedAtMs: Long,
    val endedAtMs: Long = 0L,
    val expiresAtMs: Long = startedAtMs + SEVEN_DAYS_MS,
    val recordedMs: Long,
    val maxDurationMs: Long,
    val maxHours: Int,
    val bytes: Long,
    val segmentCount: Int,
    val hasAudioTrack: Boolean,
    val error: String = "",
    val playlistUrl: String,
    val downloadUrl: String = "/api/recordings/$id/download",
) {
    companion object {
        const val SEVEN_DAYS_MS: Long = 7L * 24L * 3600L * 1000L
    }
}

data class RecordingDownloadPayload(
    val stream: InputStream,
    val totalBytes: Long?,
    val mimeType: String,
    val fileName: String,
)

/**
 * Persistent 7-day central-server recorder & timeshift engine.
 * Captures live HLS (including CMAF separate video/audio and fMP4 init maps) or
 * progressive MPEG-TS streams onto persistent app storage, retains each recording
 * for 7 days before automatic expiration cleanup, supports in-app HLS playback, and
 * streams complete single-file downloads to Web and Mobile/TV clients.
 */
class TimeshiftManager(
    context: Context,
    private val liveRelay: LiveRelayManager? = null,
) {

    private val legacyDir = File(context.applicationContext.filesDir, "timeshift")
    private val rootDir = File(context.applicationContext.filesDir, "recordings").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, SessionState>()
    private val metaWriteLock = Any()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    init {
        if (legacyDir.exists()) {
            runCatching { legacyDir.deleteRecursively() }
        }
        restoreSavedRecordings()
        purgeExpiredRecordings()
        scope.launch {
            while (isActive) {
                delay(3L * 3600L * 1000L)
                purgeExpiredRecordings()
            }
        }
    }

    private class TrackBuffer {
        val lock = Any()
        val segments = mutableListOf<TrackSegmentEntry>()
        val seenUrls = LinkedHashSet<String>()
        val downloadedMaps = HashMap<String, String>()
        val downloadedKeys = HashMap<String, String>()
        var targetDurationSec: Double = 6.0
        var recordedMs: Long = 0L
    }

    private class SessionState(
        val id: String,
        val deviceId: String,
        val channelId: String,
        val channelName: String,
        val country: String,
        val logo: String?,
        val streamUrl: String,
        val maxHours: Int,
        val maxDurationMs: Long,
        val dir: File,
        val startedAtMs: Long = System.currentTimeMillis(),
        val expiresAtMs: Long = startedAtMs + TimeshiftSessionSnapshot.SEVEN_DAYS_MS,
    ) {
        @Volatile
        var endedAtMs: Long = 0L
        @Volatile
        var status: String = "recording"
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
        var job: Job? = null
        @Volatile
        var lastMetaPersistAtMs: Long = 0L

        val bytes = AtomicLong(0L)
        val readyLatch = CountDownLatch(1)
        val videoTrack = TrackBuffer()
        val audioTrack = TrackBuffer()

        fun snapshot(): TimeshiftSessionSnapshot {
            val vCount: Int
            val vMs: Long
            synchronized(videoTrack.lock) {
                vCount = videoTrack.segments.size
                vMs = videoTrack.recordedMs
            }
            return TimeshiftSessionSnapshot(
                id = id,
                deviceId = deviceId,
                channelId = channelId,
                channelName = channelName,
                country = country,
                logo = logo,
                status = status,
                startedAtMs = startedAtMs,
                endedAtMs = endedAtMs,
                expiresAtMs = expiresAtMs,
                recordedMs = vMs,
                maxDurationMs = maxDurationMs,
                maxHours = maxHours,
                bytes = bytes.get(),
                segmentCount = vCount,
                hasAudioTrack = hasAudioTrack,
                error = error,
                playlistUrl = "/api/recordings/$id/master.m3u8",
                downloadUrl = "/api/recordings/$id/download",
            )
        }
    }

    private fun saveMeta(state: SessionState) {
        synchronized(metaWriteLock) {
            runCatching {
                val obj = buildJsonObject {
                    put("id", state.id)
                    put("deviceId", state.deviceId)
                    put("channelId", state.channelId)
                    put("channelName", state.channelName)
                    put("country", state.country)
                    state.logo?.let { put("logo", it) }
                    put("streamUrl", state.streamUrl)
                    put("maxHours", state.maxHours)
                    put("maxDurationMs", state.maxDurationMs)
                    put("startedAtMs", state.startedAtMs)
                    put("endedAtMs", state.endedAtMs)
                    put("expiresAtMs", state.expiresAtMs)
                    put("status", state.status)
                    put("error", state.error)
                    put("hasAudioTrack", state.hasAudioTrack)
                    put("bandwidth", state.bandwidth)
                    state.resolution?.let { put("resolution", it) }
                    state.codecs?.let { put("codecs", it) }
                    put("bytes", state.bytes.get())
                    put("videoTargetDurationSec", synchronized(state.videoTrack.lock) { state.videoTrack.targetDurationSec })
                    put("audioTargetDurationSec", synchronized(state.audioTrack.lock) { state.audioTrack.targetDurationSec })
                    put("videoSegments", encodeTrackSegments(state.videoTrack))
                    put("audioSegments", encodeTrackSegments(state.audioTrack))
                }
                val tmp = File(state.dir, "meta.json.tmp")
                tmp.writeText(obj.toString())
                val dst = File(state.dir, "meta.json")
                tmp.renameTo(dst)
                state.lastMetaPersistAtMs = System.currentTimeMillis()
            }
        }
    }

    private fun encodeTrackSegments(track: TrackBuffer): JsonArray {
        val copy = synchronized(track.lock) { track.segments.toList() }
        return buildJsonArray {
            for (seg in copy) {
                add(buildJsonObject {
                    put("fileName", seg.fileName)
                    put("durationSec", seg.durationSec)
                    put("discontinuityBefore", seg.discontinuityBefore)
                    seg.mapFileName?.let { put("mapFileName", it) }
                    seg.keyLine?.let { put("keyLine", it) }
                    seg.keyFileName?.let { put("keyFileName", it) }
                    seg.pdtIso?.let { put("pdtIso", it) }
                    put("canonicalUrl", seg.canonicalUrl)
                    seg.mapUrl?.let { put("mapUrl", it) }
                    seg.keyUrl?.let { put("keyUrl", it) }
                    seg.keyAttrRest?.let { put("keyAttrRest", it) }
                    put("byteLength", seg.byteLength)
                })
            }
        }
    }

    private fun maybePersistPeriodically(state: SessionState) {
        val now = System.currentTimeMillis()
        if (now - state.lastMetaPersistAtMs >= 10_000L) {
            saveMeta(state)
        }
    }

    private fun restoreSavedRecordings() {
        val now = System.currentTimeMillis()
        val dirs = rootDir.listFiles()?.filter { it.isDirectory }.orEmpty()
        for (dir in dirs) {
            val metaFile = File(dir, "meta.json")
            if (!metaFile.exists()) {
                runCatching { dir.deleteRecursively() }
                continue
            }
            val parsed = runCatching {
                Json.parseToJsonElement(metaFile.readText()).jsonObject
            }.getOrNull()
            if (parsed == null) {
                runCatching { dir.deleteRecursively() }
                continue
            }
            val id = parsed["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: dir.name
            val startedAt = parsed["startedAtMs"]?.jsonPrimitive?.longOrNull ?: now
            val expiresAt = parsed["expiresAtMs"]?.jsonPrimitive?.longOrNull ?: (startedAt + TimeshiftSessionSnapshot.SEVEN_DAYS_MS)
            if (now >= expiresAt) {
                runCatching { dir.deleteRecursively() }
                continue
            }
            val state = SessionState(
                id = id,
                deviceId = parsed["deviceId"]?.jsonPrimitive?.contentOrNull ?: "default",
                channelId = parsed["channelId"]?.jsonPrimitive?.contentOrNull ?: "",
                channelName = parsed["channelName"]?.jsonPrimitive?.contentOrNull ?: "Live TV",
                country = parsed["country"]?.jsonPrimitive?.contentOrNull ?: "",
                logo = parsed["logo"]?.jsonPrimitive?.contentOrNull,
                streamUrl = parsed["streamUrl"]?.jsonPrimitive?.contentOrNull ?: "",
                maxHours = parsed["maxHours"]?.jsonPrimitive?.intOrNull ?: 4,
                maxDurationMs = parsed["maxDurationMs"]?.jsonPrimitive?.longOrNull ?: (4L * 3_600_000L),
                dir = dir,
                startedAtMs = startedAt,
                expiresAtMs = expiresAt,
            )
            state.endedAtMs = parsed["endedAtMs"]?.jsonPrimitive?.longOrNull ?: now
            val rawStatus = parsed["status"]?.jsonPrimitive?.contentOrNull ?: "completed"
            state.status = if (rawStatus == "recording") "completed" else rawStatus
            state.error = parsed["error"]?.jsonPrimitive?.contentOrNull ?: ""
            state.hasAudioTrack = parsed["hasAudioTrack"]?.jsonPrimitive?.booleanOrNull ?: false
            state.bandwidth = parsed["bandwidth"]?.jsonPrimitive?.intOrNull ?: 6_000_000
            state.resolution = parsed["resolution"]?.jsonPrimitive?.contentOrNull
            state.codecs = parsed["codecs"]?.jsonPrimitive?.contentOrNull
            state.bytes.set(parsed["bytes"]?.jsonPrimitive?.longOrNull ?: 0L)
            state.videoTrack.targetDurationSec = parsed["videoTargetDurationSec"]?.jsonPrimitive?.doubleOrNull ?: 6.0
            state.audioTrack.targetDurationSec = parsed["audioTargetDurationSec"]?.jsonPrimitive?.doubleOrNull ?: 6.0
            decodeTrackSegments(parsed["videoSegments"], state.videoTrack, dir)
            decodeTrackSegments(parsed["audioSegments"], state.audioTrack, dir)
            val vCount = synchronized(state.videoTrack.lock) { state.videoTrack.segments.size }
            if (vCount == 0) {
                runCatching { dir.deleteRecursively() }
                continue
            }
            state.readyLatch.countDown()
            sessions[id] = state
            if (rawStatus == "recording") {
                saveMeta(state)
            }
        }
    }

    private fun decodeTrackSegments(elem: kotlinx.serialization.json.JsonElement?, track: TrackBuffer, dir: File) {
        val arr = (elem as? JsonArray) ?: return
        synchronized(track.lock) {
            for (item in arr) {
                val obj = item as? JsonObject ?: continue
                val fileName = obj["fileName"]?.jsonPrimitive?.contentOrNull ?: continue
                if (!File(dir, fileName).exists()) continue
                val dur = obj["durationSec"]?.jsonPrimitive?.doubleOrNull ?: 4.0
                track.segments.add(
                    TrackSegmentEntry(
                        fileName = fileName,
                        durationSec = dur,
                        discontinuityBefore = obj["discontinuityBefore"]?.jsonPrimitive?.booleanOrNull ?: false,
                        mapFileName = obj["mapFileName"]?.jsonPrimitive?.contentOrNull,
                        keyLine = obj["keyLine"]?.jsonPrimitive?.contentOrNull,
                        keyFileName = obj["keyFileName"]?.jsonPrimitive?.contentOrNull,
                        pdtIso = obj["pdtIso"]?.jsonPrimitive?.contentOrNull,
                        canonicalUrl = obj["canonicalUrl"]?.jsonPrimitive?.contentOrNull ?: fileName,
                        mapUrl = obj["mapUrl"]?.jsonPrimitive?.contentOrNull,
                        keyUrl = obj["keyUrl"]?.jsonPrimitive?.contentOrNull,
                        keyAttrRest = obj["keyAttrRest"]?.jsonPrimitive?.contentOrNull,
                        byteLength = obj["byteLength"]?.jsonPrimitive?.longOrNull ?: File(dir, fileName).length(),
                    ),
                )
                track.recordedMs += (dur * 1000.0).toLong()
            }
        }
    }

    fun purgeExpiredRecordings(): Int {
        val now = System.currentTimeMillis()
        var removed = 0
        sessions.values
            .filter { it.status != "recording" && now >= it.expiresAtMs }
            .forEach { state ->
                if (deleteSession(state.id)) removed++
            }
        return removed
    }

    fun listRecordings(): List<TimeshiftSessionSnapshot> {
        purgeExpiredRecordings()
        return sessions.values
            .map { it.snapshot() }
            .sortedByDescending { it.startedAtMs }
    }

    fun startSession(
        deviceId: String,
        channelId: String,
        channelName: String,
        streamUrl: String,
        maxHours: Int,
        maxSecondsOverride: Long? = null,
        country: String = "",
        logo: String? = null,
    ): TimeshiftSessionSnapshot {
        purgeExpiredRecordings()
        val cleanDeviceId = deviceId.trim().ifBlank { "default" }
        val cleanChannelId = channelId.trim()

        // If this channel is already actively recording, return the running recording session.
        sessions.values.firstOrNull { s ->
            s.channelId == cleanChannelId && s.status == "recording"
        }?.let { return it.snapshot() }

        val boundedHours = maxHours.coerceIn(1, 12)
        val maxDurationMs = if (maxSecondsOverride != null && maxSecondsOverride > 0L) {
            maxSecondsOverride * 1000L
        } else {
            boundedHours * 3_600_000L
        }

        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val dir = File(rootDir, id).apply { mkdirs() }
        val state = SessionState(
            id = id,
            deviceId = cleanDeviceId,
            channelId = cleanChannelId,
            channelName = channelName.ifBlank { "Live TV" },
            country = country.trim().uppercase(Locale.ROOT),
            logo = logo?.takeIf { it.isNotBlank() },
            streamUrl = streamUrl,
            maxHours = boundedHours,
            maxDurationMs = maxDurationMs,
            dir = dir,
        )
        sessions[id] = state
        saveMeta(state)

        val seed = liveRelay?.captureSeedForTimeshift(cleanChannelId, streamUrl)
        state.job = scope.launch {
            runRecorder(state, seed)
        }

        // Wait until first segment(s) are ready (instant when seeded from live relay)
        runCatching { state.readyLatch.await(8, TimeUnit.SECONDS) }
        saveMeta(state)
        return state.snapshot()
    }

    fun stopSession(sessionId: String): TimeshiftSessionSnapshot? {
        val state = sessions[sessionId] ?: return null
        if (state.status == "recording") {
            state.status = "completed"
            state.endedAtMs = System.currentTimeMillis()
            runCatching { state.job?.cancel() }
            runCatching { state.readyLatch.countDown() }
            saveMeta(state)
        }
        return state.snapshot()
    }

    fun getStatus(sessionId: String): TimeshiftSessionSnapshot? =
        sessions[sessionId]?.snapshot()

    fun findPending(deviceId: String, channelId: String): TimeshiftSessionSnapshot? {
        val cleanDev = deviceId.trim()
        val cleanCh = channelId.trim()
        if (cleanCh.isBlank()) return null
        return sessions.values
            .firstOrNull { s ->
                (cleanDev.isBlank() || s.deviceId == cleanDev) &&
                    s.channelId == cleanCh &&
                    s.status == "limit_reached" &&
                    synchronized(s.videoTrack.lock) { s.videoTrack.segments.isNotEmpty() }
            }
            ?.snapshot()
    }

    fun deleteSession(sessionId: String): Boolean {
        val removed = sessions.remove(sessionId)
        if (removed != null) {
            removed.status = "deleted"
            runCatching { removed.job?.cancel() }
            runCatching { removed.readyLatch.countDown() }
            runCatching { removed.dir.deleteRecursively() }
            return true
        }
        val orphan = File(rootDir, sessionId)
        if (orphan.exists() && orphan.parentFile == rootDir) {
            runCatching { orphan.deleteRecursively() }
            return true
        }
        return false
    }

    fun resolveSegmentFile(sessionId: String, fileName: String): File? {
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') || fileName.contains("..")) {
            return null
        }
        val state = sessions[sessionId] ?: return null
        val candidate = File(state.dir, fileName)
        return candidate.takeIf { it.exists() && it.isFile && it.parentFile == state.dir }
    }

    fun openDownloadStream(sessionId: String): RecordingDownloadPayload? {
        val state = sessions[sessionId] ?: return null
        val segsCopy = synchronized(state.videoTrack.lock) { state.videoTrack.segments.toList() }
        if (segsCopy.isEmpty()) return null

        val hasFmp4Map = segsCopy.any { !it.mapFileName.isNullOrBlank() }
        val hasEncrypted = segsCopy.any { !it.keyFileName.isNullOrBlank() }
        val streams = ArrayList<InputStream>(segsCopy.size + 4)
        var exactTotalBytes = 0L

        if (hasFmp4Map) {
            val firstMap = segsCopy.firstNotNullOfOrNull { it.mapFileName }
            if (firstMap != null) {
                val mapFile = File(state.dir, firstMap)
                if (mapFile.exists() && mapFile.length() > 0L) {
                    exactTotalBytes += mapFile.length()
                    streams.add(FileInputStream(mapFile))
                }
            }
        }

        segsCopy.forEachIndexed { idx, seg ->
            val segFile = File(state.dir, seg.fileName)
            if (!segFile.exists() || segFile.length() <= 0L) return@forEachIndexed
            val keyFile = seg.keyFileName?.let { File(state.dir, it) }?.takeIf { it.exists() }
            if (keyFile != null && seg.keyLine?.contains("AES-128", ignoreCase = true) == true) {
                val keyBytes = keyFile.readBytes()
                val ivHex = extractHlsAttr(seg.keyLine, "IV")?.removePrefix("0x")?.removePrefix("0X")
                val iv = ByteArray(16)
                if (!ivHex.isNullOrBlank() && ivHex.length <= 32) {
                    val padded = ivHex.padStart(32, '0')
                    for (b in 0 until 16) {
                        iv[b] = padded.substring(b * 2, b * 2 + 2).toInt(16).toByte()
                    }
                } else {
                    var seq = idx.toLong()
                    for (b in 15 downTo 8) {
                        iv[b] = (seq and 0xFFL).toByte()
                        seq = seq ushr 8
                    }
                }
                val decrypted = runCatching {
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
                    cipher.doFinal(segFile.readBytes())
                }.getOrNull()
                if (decrypted != null) {
                    streams.add(ByteArrayInputStream(decrypted))
                    return@forEachIndexed
                }
            }
            exactTotalBytes += segFile.length()
            streams.add(FileInputStream(segFile))
        }

        if (streams.isEmpty()) return null
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(state.startedAtMs))
        val safeName = state.channelName.replace(Regex("""[^A-Za-z0-9._-]+"""), "_").trim('_').ifBlank { "CasaTV" }.take(40)
        val ext = if (hasFmp4Map) "mp4" else "ts"
        val mime = if (hasFmp4Map) "video/mp4" else "video/mp2t"
        val combined = SequenceInputStream(Collections.enumeration(streams))
        return RecordingDownloadPayload(
            stream = combined,
            totalBytes = if (hasEncrypted) null else exactTotalBytes,
            mimeType = mime,
            fileName = "CasaTV_${safeName}_${dateStamp}.$ext",
        )
    }

    fun renderMasterPlaylist(sessionId: String): String? {
        val state = sessions[sessionId] ?: return null
        if (!state.hasAudioTrack) {
            return renderTrackPlaylist(sessionId, audio = false)
        }
        return buildString {
            append("#EXTM3U\n")
            append("#EXT-X-VERSION:6\n")
            append("#EXT-X-INDEPENDENT-SEGMENTS\n")
            append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Audio\",DEFAULT=YES,AUTOSELECT=YES,URI=\"audio.m3u8\"\n")
            append("#EXT-X-STREAM-INF:BANDWIDTH=${state.bandwidth}")
            state.resolution?.takeIf { it.isNotBlank() }?.let { append(",RESOLUTION=$it") }
            state.codecs?.takeIf { it.isNotBlank() }?.let { append(",CODECS=\"$it\"") }
            append(",AUDIO=\"audio\"\n")
            append("video.m3u8\n")
        }
    }

    fun renderTrackPlaylist(sessionId: String, audio: Boolean): String? {
        val state = sessions[sessionId] ?: return null
        val track = if (audio) state.audioTrack else state.videoTrack
        val segmentsCopy: List<TrackSegmentEntry>
        val targetDur: Int
        synchronized(track.lock) {
            segmentsCopy = track.segments.toList()
            targetDur = ceil(track.targetDurationSec).toInt().coerceAtLeast(2)
        }
        return buildString {
            append("#EXTM3U\n")
            append("#EXT-X-VERSION:6\n")
            append("#EXT-X-TARGETDURATION:$targetDur\n")
            append("#EXT-X-MEDIA-SEQUENCE:0\n")
            append("#EXT-X-PLAYLIST-TYPE:${if (state.status == "recording") "EVENT" else "VOD"}\n")
            append("#EXT-X-INDEPENDENT-SEGMENTS\n")
            var activeMap: String? = null
            var activeKey: String? = null
            for (seg in segmentsCopy) {
                if (seg.discontinuityBefore) {
                    append("#EXT-X-DISCONTINUITY\n")
                }
                if (seg.keyLine != activeKey && seg.keyLine != null) {
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
                val durStr = String.format(java.util.Locale.US, "%.3f", seg.durationSec.coerceAtLeast(0.1))
                append("#EXTINF:$durStr,\n")
                append("${seg.fileName}\n")
            }
            if (state.status != "recording") {
                append("#EXT-X-ENDLIST\n")
            }
        }
    }

    fun shutdown() {
        sessions.values.forEach { state ->
            if (state.status == "recording") {
                state.status = "completed"
                state.endedAtMs = System.currentTimeMillis()
                saveMeta(state)
            }
            runCatching { state.job?.cancel() }
            runCatching { state.readyLatch.countDown() }
        }
        runCatching { scope.cancel() }
    }

    /* ------------------------------------------------------------------ */
    /* Recorder Engine                                                     */
    /* ------------------------------------------------------------------ */

    private suspend fun runRecorder(state: SessionState, seed: RelaySeedSnapshot?) {
        try {
            if (seed != null && seedTimeshiftFromRelay(state, seed)) {
                val videoUrl = seed.videoUrl.toHttpUrlOrNull()
                val audioUrl = seed.audioUrl?.toHttpUrlOrNull()
                if (videoUrl != null) {
                    pollHlsLoop(state, videoUrl, audioUrl)
                    return
                }
            }

            val initial = fetchHlsTextWithFinalUrl(http, state.streamUrl)
            if (initial == null) {
                state.status = "error"
                state.error = "Stream non raggiungibile"
                return
            }
            val (finalUrl, bodyText) = initial
            if (bodyText.trimStart().startsWith("#EXTM3U")) {
                recordHls(state, finalUrl, bodyText)
            } else {
                recordProgressiveTs(state, state.streamUrl)
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            if (state.status == "recording") {
                state.status = "completed"
                state.endedAtMs = System.currentTimeMillis()
            }
            throw ce
        } catch (err: Exception) {
            Log.e("CasaTimeshift", "Recorder error for ${state.id}", err)
            if (state.status == "recording") {
                val segCount = synchronized(state.videoTrack.lock) { state.videoTrack.segments.size }
                state.status = if (segCount > 0) "completed" else "error"
                state.error = if (segCount > 0) "" else (err.message ?: "Errore registrazione")
                state.endedAtMs = System.currentTimeMillis()
            }
        } finally {
            state.readyLatch.countDown()
            if (state.status != "deleted") {
                if (state.status == "limit_reached" && state.endedAtMs == 0L) {
                    state.endedAtMs = System.currentTimeMillis()
                }
                saveMeta(state)
            }
        }
    }

    private fun seedTimeshiftFromRelay(state: SessionState, seed: RelaySeedSnapshot): Boolean {
        state.bandwidth = seed.bandwidth
        state.resolution = seed.resolution
        state.codecs = seed.codecs
        state.hasAudioTrack = seed.hasAudioTrack

        val vCopied = copyRelaySeedTrack(state, state.videoTrack, "v", seed.sourceDir, seed.videoSegments, seed.targetDurationSec)
        if (!vCopied) return false

        if (seed.hasAudioTrack) {
            val aCopied = copyRelaySeedTrack(state, state.audioTrack, "a", seed.sourceDir, seed.audioSegments, seed.targetDurationSec)
            if (!aCopied) {
                state.hasAudioTrack = false
            }
        }
        state.readyLatch.countDown()
        return true
    }

    private fun copyRelaySeedTrack(
        state: SessionState,
        track: TrackBuffer,
        prefix: String,
        sourceDir: File,
        entries: List<TrackSegmentEntry>,
        targetDuration: Double,
    ): Boolean {
        if (entries.isEmpty()) return false
        var copiedAny = false
        synchronized(track.lock) {
            track.targetDurationSec = targetDuration
        }
        for (entry in entries) {
            val srcSeg = File(sourceDir, entry.fileName)
            if (!srcSeg.exists() || srcSeg.length() == 0L) continue

            val newMapName = entry.mapFileName?.let { srcMapName ->
                val mapKey = entry.mapUrl ?: srcMapName
                val existingMap = synchronized(track.lock) { track.downloadedMaps[mapKey] }
                if (existingMap != null) {
                    existingMap
                } else {
                    val srcMapFile = File(sourceDir, srcMapName)
                    if (srcMapFile.exists()) {
                        val idx = synchronized(track.lock) { track.downloadedMaps.size }
                        val ext = guessMediaExtension(srcMapName, "mp4")
                        val dstName = "${prefix}_init_${idx}.$ext"
                        val dstFile = File(state.dir, dstName)
                        if (runCatching { srcMapFile.copyTo(dstFile, overwrite = true); true }.getOrDefault(false)) {
                            state.bytes.addAndGet(dstFile.length())
                            synchronized(track.lock) { track.downloadedMaps[mapKey] = dstName }
                            dstName
                        } else null
                    } else null
                }
            }

            val newKeyName = entry.keyFileName?.let { srcKeyName ->
                val kKey = entry.keyUrl ?: srcKeyName
                val existingKey = synchronized(track.lock) { track.downloadedKeys[kKey] }
                if (existingKey != null) {
                    existingKey
                } else {
                    val srcKeyFile = File(sourceDir, srcKeyName)
                    if (srcKeyFile.exists()) {
                        val idx = synchronized(track.lock) { track.downloadedKeys.size }
                        val dstName = "${prefix}_key_${idx}.key"
                        val dstFile = File(state.dir, dstName)
                        if (runCatching { srcKeyFile.copyTo(dstFile, overwrite = true); true }.getOrDefault(false)) {
                            state.bytes.addAndGet(dstFile.length())
                            synchronized(track.lock) { track.downloadedKeys[kKey] = dstName }
                            dstName
                        } else null
                    } else null
                }
            }

            val seqIdx = synchronized(track.lock) { track.segments.size }
            val segExt = guessMediaExtension(entry.fileName, if (newMapName != null) "mp4" else "ts")
            val dstSegName = String.format(java.util.Locale.US, "%s_%06d.%s", prefix, seqIdx, segExt)
            val dstSegFile = File(state.dir, dstSegName)
            if (!runCatching { srcSeg.copyTo(dstSegFile, overwrite = true); true }.getOrDefault(false)) {
                continue
            }
            val written = dstSegFile.length()
            state.bytes.addAndGet(written)
            val keyLineUpdated = if (newKeyName != null && entry.keyLine != null) {
                val extra = entry.keyAttrRest?.takeIf { it.isNotBlank() }?.let { ",$it" }.orEmpty()
                val method = extractHlsAttr(entry.keyLine, "METHOD") ?: "AES-128"
                "#EXT-X-KEY:METHOD=$method,URI=\"$newKeyName\"$extra"
            } else null

            synchronized(track.lock) {
                if (track.seenUrls.add(entry.canonicalUrl)) {
                    track.segments.add(
                        entry.copy(
                            fileName = dstSegName,
                            discontinuityBefore = track.segments.isNotEmpty() && entry.discontinuityBefore,
                            mapFileName = newMapName,
                            keyLine = keyLineUpdated,
                            keyFileName = newKeyName,
                            byteLength = written,
                        ),
                    )
                    track.recordedMs += (entry.durationSec * 1000.0).toLong()
                    copiedAny = true
                }
            }
        }
        return copiedAny
    }

    private suspend fun recordHls(
        state: SessionState,
        entryUrl: HttpUrl,
        entryText: String,
    ) {
        var videoUrl = entryUrl
        var audioUrl: HttpUrl? = null

        if (entryText.contains("#EXT-X-STREAM-INF")) {
            val selection = selectBestHlsVariant(entryUrl, entryText)
            if (selection == null) {
                state.status = "error"
                state.error = "Nessuna variante video valida nel flusso HLS"
                return
            }
            videoUrl = selection.first.url
            state.bandwidth = selection.first.bandwidth.coerceAtLeast(500_000)
            state.resolution = selection.first.resolution
            state.codecs = selection.first.codecs
            audioUrl = selection.second
            state.hasAudioTrack = audioUrl != null
        }

        val initialVideo = fetchHlsTextWithFinalUrl(http, videoUrl.toString())
        if (initialVideo == null) {
            state.status = "error"
            state.error = "Impossibile leggere la playlist video"
            return
        }
        val parsedVideo0 = parseHlsMediaPlaylist(initialVideo.first, initialVideo.second)
        val videoStartIdx0 = (parsedVideo0.segments.size - 2).coerceAtLeast(0)
        val videoFirstStartPdtMs = parsedVideo0.segments.getOrNull(videoStartIdx0)?.pdtEpochMs
        processParsedSegments(
            state = state,
            track = state.videoTrack,
            prefix = "v",
            segments = parsedVideo0.segments.drop(videoStartIdx0),
            targetDuration = parsedVideo0.targetDurationSec,
        )
        val firstVideoDone = synchronized(state.videoTrack.lock) { state.videoTrack.segments.isNotEmpty() }

        var firstAudioDone = true
        if (audioUrl != null) {
            firstAudioDone = false
            val initialAudio = fetchHlsTextWithFinalUrl(http, audioUrl.toString())
            if (initialAudio != null) {
                val parsedAudio0 = parseHlsMediaPlaylist(initialAudio.first, initialAudio.second)
                val audioStartIdx0 = pickAlignedHlsAudioStart(parsedAudio0.segments, videoFirstStartPdtMs)
                processParsedSegments(
                    state = state,
                    track = state.audioTrack,
                    prefix = "a",
                    segments = parsedAudio0.segments.drop(audioStartIdx0),
                    targetDuration = parsedAudio0.targetDurationSec,
                )
                firstAudioDone = synchronized(state.audioTrack.lock) { state.audioTrack.segments.isNotEmpty() }
            }
        }

        if (firstVideoDone && firstAudioDone) {
            state.readyLatch.countDown()
        }

        pollHlsLoop(state, videoUrl, audioUrl)
    }

    private suspend fun pollHlsLoop(
        state: SessionState,
        videoUrl: HttpUrl,
        audioUrl: HttpUrl?,
    ) {
        while (scope.isActive && state.status == "recording") {
            val recordedNow = synchronized(state.videoTrack.lock) { state.videoTrack.recordedMs }
            if (recordedNow >= state.maxDurationMs || !hasSufficientDiskSpace()) {
                state.status = "limit_reached"
                break
            }

            val pollDelayMs = ((state.videoTrack.targetDurationSec * 1000.0) * 0.5)
                .toLong()
                .coerceIn(1500L, 4000L)
            delay(pollDelayMs)

            if (state.status != "recording" || !scope.isActive) break

            val videoElapsedMs = supervisorScope {
                val vTask = async {
                    fetchHlsTextWithFinalUrl(http, videoUrl.toString())?.let { (vBase, vTxt) ->
                        val parsedV = parseHlsMediaPlaylist(vBase, vTxt)
                        processParsedSegments(
                            state = state,
                            track = state.videoTrack,
                            prefix = "v",
                            segments = parsedV.segments,
                            targetDuration = parsedV.targetDurationSec,
                        )
                    }
                }
                val aTask = async {
                    if (audioUrl != null) {
                        fetchHlsTextWithFinalUrl(http, audioUrl.toString())?.let { (aBase, aTxt) ->
                            val parsedA = parseHlsMediaPlaylist(aBase, aTxt)
                            processParsedSegments(
                                state = state,
                                track = state.audioTrack,
                                prefix = "a",
                                segments = parsedA.segments,
                                targetDuration = parsedA.targetDurationSec,
                            )
                        }
                    }
                }
                vTask.await()
                aTask.await()
                maybePersistPeriodically(state)
                synchronized(state.videoTrack.lock) { state.videoTrack.recordedMs }
            }

            if (synchronized(state.videoTrack.lock) { state.videoTrack.segments.isNotEmpty() } &&
                (audioUrl == null || synchronized(state.audioTrack.lock) { state.audioTrack.segments.isNotEmpty() })
            ) {
                state.readyLatch.countDown()
            }

            if (videoElapsedMs >= state.maxDurationMs || !hasSufficientDiskSpace()) {
                state.status = "limit_reached"
                break
            }
        }
    }

    private fun processParsedSegments(
        state: SessionState,
        track: TrackBuffer,
        prefix: String,
        segments: List<ParsedHlsSegment>,
        targetDuration: Double,
    ) {
        synchronized(track.lock) {
            track.targetDurationSec = targetDuration
        }
        for (seg in segments) {
            if (state.status != "recording" || !scope.isActive) return
            val alreadyRecorded = synchronized(track.lock) { track.recordedMs }
            if (prefix == "v" && alreadyRecorded >= state.maxDurationMs) {
                state.status = "limit_reached"
                return
            }

            val canonicalUrl = seg.url.toString()
            val shouldDownload = synchronized(track.lock) { !track.seenUrls.contains(canonicalUrl) }
            if (!shouldDownload) continue

            val mapFileName = seg.mapUrl?.let { mapHttpUrl ->
                val mapKey = mapHttpUrl.toString()
                val existing = synchronized(track.lock) { track.downloadedMaps[mapKey] }
                if (existing != null) {
                    existing
                } else {
                    val idx = synchronized(track.lock) { track.downloadedMaps.size }
                    val ext = guessMediaExtension(mapHttpUrl.encodedPath, defaultExt = "mp4")
                    val fname = "${prefix}_init_${idx}.$ext"
                    val bytesWritten = downloadBinaryAtomically(http, mapKey, File(state.dir, fname))
                    if (bytesWritten > 0L) {
                        state.bytes.addAndGet(bytesWritten)
                        synchronized(track.lock) { track.downloadedMaps[mapKey] = fname }
                        fname
                    } else null
                }
            }

            var localKeyName: String? = null
            val keyLine = if (seg.keyMethod != null && seg.keyMethod != "NONE" && seg.keyUri != null) {
                val kKey = seg.keyUri.toString()
                localKeyName = synchronized(track.lock) { track.downloadedKeys[kKey] } ?: run {
                    val idx = synchronized(track.lock) { track.downloadedKeys.size }
                    val fname = "${prefix}_key_${idx}.key"
                    val written = downloadBinaryAtomically(http, kKey, File(state.dir, fname))
                    if (written > 0L) {
                        state.bytes.addAndGet(written)
                        synchronized(track.lock) { track.downloadedKeys[kKey] = fname }
                        fname
                    } else null
                }
                if (localKeyName != null) {
                    val extra = seg.keyRest?.takeIf { it.isNotBlank() }?.let { ",$it" }.orEmpty()
                    "#EXT-X-KEY:METHOD=${seg.keyMethod},URI=\"$localKeyName\"$extra"
                } else null
            } else null

            val seqIndex = synchronized(track.lock) { track.segments.size }
            val segExt = guessMediaExtension(seg.url.encodedPath, defaultExt = if (mapFileName != null) "mp4" else "ts")
            val segFileName = String.format(java.util.Locale.US, "%s_%06d.%s", prefix, seqIndex, segExt)
            val written = downloadBinaryAtomically(http, canonicalUrl, File(state.dir, segFileName))
            if (written <= 0L) continue

            state.bytes.addAndGet(written)
            synchronized(track.lock) {
                if (track.seenUrls.add(canonicalUrl)) {
                    val hasExisting = track.segments.isNotEmpty()
                    track.segments.add(
                        TrackSegmentEntry(
                            fileName = segFileName,
                            durationSec = seg.durationSec,
                            discontinuityBefore = hasExisting && seg.discontinuityBefore,
                            mapFileName = mapFileName,
                            keyLine = keyLine,
                            keyFileName = localKeyName,
                            pdtIso = seg.pdtIso,
                            canonicalUrl = canonicalUrl,
                            mapUrl = seg.mapUrl?.toString(),
                            keyUrl = seg.keyUri?.toString(),
                            keyAttrRest = seg.keyRest,
                            byteLength = written,
                        ),
                    )
                    track.recordedMs += (seg.durationSec * 1000.0).toLong()
                }
            }
        }
    }

    private suspend fun recordProgressiveTs(state: SessionState, url: String) {
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
                state.error = "Flusso vuoto"
                return
            }
            val buffer = ByteArray(64 * 1024)
            var chunkIndex = 0
            val chunkDurationSec = 4.0
            val targetChunkBytes = 1_500_000L
            var currentFile = File(state.dir, String.format(java.util.Locale.US, "v_%06d.ts.tmp", chunkIndex))
            var out = FileOutputStream(currentFile)
            var chunkBytes = 0L
            var chunkStartMs = System.currentTimeMillis()
            try {
                while (scope.isActive && state.status == "recording") {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    out.write(buffer, 0, read)
                    chunkBytes += read
                    val elapsedChunkMs = System.currentTimeMillis() - chunkStartMs
                    if (elapsedChunkMs >= 4000L || chunkBytes >= targetChunkBytes) {
                        out.flush()
                        out.close()
                        val finalName = String.format(java.util.Locale.US, "v_%06d.ts", chunkIndex)
                        val dest = File(state.dir, finalName)
                        currentFile.renameTo(dest)
                        state.bytes.addAndGet(dest.length())
                        val durSec = (elapsedChunkMs.toDouble() / 1000.0).coerceIn(2.0, 8.0)
                        synchronized(state.videoTrack.lock) {
                            state.videoTrack.targetDurationSec = maxOf(state.videoTrack.targetDurationSec, durSec)
                            state.videoTrack.segments.add(
                                TrackSegmentEntry(
                                    fileName = finalName,
                                    durationSec = durSec,
                                    discontinuityBefore = false,
                                    mapFileName = null,
                                    keyLine = null,
                                    keyFileName = null,
                                    pdtIso = null,
                                    canonicalUrl = "$url#$chunkIndex",
                                    mapUrl = null,
                                    keyUrl = null,
                                    byteLength = dest.length(),
                                ),
                            )
                            state.videoTrack.recordedMs += (durSec * 1000.0).toLong()
                        }
                        state.readyLatch.countDown()
                        maybePersistPeriodically(state)
                        val recordedTotal = synchronized(state.videoTrack.lock) { state.videoTrack.recordedMs }
                        if (recordedTotal >= state.maxDurationMs || !hasSufficientDiskSpace()) {
                            state.status = "limit_reached"
                            break
                        }
                        chunkIndex++
                        currentFile = File(state.dir, String.format(java.util.Locale.US, "v_%06d.ts.tmp", chunkIndex))
                        out = FileOutputStream(currentFile)
                        chunkBytes = 0L
                        chunkStartMs = System.currentTimeMillis()
                    }
                }
            } finally {
                runCatching { out.flush() }
                runCatching { out.close() }
                if (currentFile.exists() && chunkBytes > 0L && state.status == "recording") {
                    val finalName = String.format(java.util.Locale.US, "v_%06d.ts", chunkIndex)
                    val dest = File(state.dir, finalName)
                    if (currentFile.renameTo(dest)) {
                        state.bytes.addAndGet(dest.length())
                        val durSec = ((System.currentTimeMillis() - chunkStartMs) / 1000.0).coerceIn(1.0, chunkDurationSec)
                        synchronized(state.videoTrack.lock) {
                            state.videoTrack.segments.add(
                                TrackSegmentEntry(
                                    fileName = finalName,
                                    durationSec = durSec,
                                    discontinuityBefore = false,
                                    mapFileName = null,
                                    keyLine = null,
                                    keyFileName = null,
                                    pdtIso = null,
                                    canonicalUrl = "$url#$chunkIndex",
                                    mapUrl = null,
                                    keyUrl = null,
                                    byteLength = dest.length(),
                                ),
                            )
                            state.videoTrack.recordedMs += (durSec * 1000.0).toLong()
                        }
                    }
                } else {
                    runCatching { currentFile.delete() }
                }
            }
        }
    }

    private fun hasSufficientDiskSpace(): Boolean {
        return runCatching {
            val stat = StatFs(rootDir.absolutePath)
            stat.availableBytes > MIN_FREE_DISK_BYTES
        }.getOrDefault(true)
    }

    companion object {
        private const val MIN_FREE_DISK_BYTES = 512L * 1024L * 1024L
    }
}
