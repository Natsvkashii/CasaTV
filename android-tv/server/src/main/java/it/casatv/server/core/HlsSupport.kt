package it.casatv.server.core

import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal const val STREAM_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

internal data class HlsVariant(
    val url: HttpUrl,
    val bandwidth: Int,
    val resolution: String?,
    val codecs: String?,
    val audioGroupId: String?,
)

internal data class ParsedHlsSegment(
    val url: HttpUrl,
    val durationSec: Double,
    val discontinuityBefore: Boolean,
    val mapUrl: HttpUrl?,
    val keyMethod: String?,
    val keyUri: HttpUrl?,
    val keyRest: String?,
    val pdtIso: String?,
    val pdtEpochMs: Long?,
)

internal data class ParsedMediaPlaylist(
    val baseUrl: HttpUrl,
    val targetDurationSec: Double,
    val segments: List<ParsedHlsSegment>,
    val mediaSequence: Long = 0L,
    val hasEndList: Boolean = false,
    val hasMap: Boolean = false,
)

internal data class HlsMasterSelection(
    val variants: List<HlsVariant>,
    val audioByGroup: Map<String, HttpUrl>,
)

internal data class DownloadResult(
    val bytes: Long,
    val elapsedMs: Long,
)

internal data class TrackSegmentEntry(
    val fileName: String,
    val durationSec: Double,
    val discontinuityBefore: Boolean,
    val mapFileName: String?,
    val keyLine: String?,
    val keyFileName: String?,
    val pdtIso: String?,
    val canonicalUrl: String,
    val mapUrl: String?,
    val keyUrl: String?,
    val keyAttrRest: String? = null,
    val byteLength: Long = 0L,
)

private val QUOTED_ATTR_CACHE = mutableMapOf<String, Regex>()
private val UNQUOTED_ATTR_CACHE = mutableMapOf<String, Regex>()

internal fun extractHlsAttr(line: String, key: String): String? {
    val quotedRegex = synchronized(QUOTED_ATTR_CACHE) {
        QUOTED_ATTR_CACHE.getOrPut(key) { Regex("""\b${Regex.escape(key)}="([^"]*)"""") }
    }
    quotedRegex.find(line)?.let { return it.groupValues[1] }
    val unquotedRegex = synchronized(UNQUOTED_ATTR_CACHE) {
        UNQUOTED_ATTR_CACHE.getOrPut(key) { Regex("""\b${Regex.escape(key)}=([^,\s]+)""") }
    }
    return unquotedRegex.find(line)?.groupValues?.get(1)
}

internal fun detectUnsupportedHlsDrm(text: String): Boolean {
    if (!text.contains("#EXT-X-KEY", ignoreCase = true) &&
        !text.contains("#EXT-X-SESSION-KEY", ignoreCase = true)
    ) {
        return false
    }
    val lower = text.lowercase(java.util.Locale.ROOT)
    return lower.contains("sample-aes") ||
        lower.contains("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed") ||
        lower.contains("com.apple.streamingkeydelivery") ||
        lower.contains("com.microsoft.playready")
}

internal fun parseHlsMaster(
    baseUrl: HttpUrl,
    text: String,
): HlsMasterSelection? {
    val audioRenditions = mutableListOf<Triple<String, Boolean, Pair<String, HttpUrl>>>()
    val variants = mutableListOf<HlsVariant>()
    val lines = text.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        if (line.startsWith("#EXT-X-MEDIA:") && line.contains("TYPE=AUDIO")) {
            val group = extractHlsAttr(line, "GROUP-ID") ?: ""
            val uri = extractHlsAttr(line, "URI")
            val lang = extractHlsAttr(line, "LANGUAGE") ?: ""
            val isDef = line.contains("DEFAULT=YES") || line.contains("AUTOSELECT=YES")
            if (!uri.isNullOrBlank()) {
                baseUrl.resolve(uri)?.let { resolved ->
                    audioRenditions.add(Triple(group, isDef, lang to resolved))
                }
            }
        } else if (line.startsWith("#EXT-X-STREAM-INF:")) {
            val bw = extractHlsAttr(line, "BANDWIDTH")?.toIntOrNull()
                ?: extractHlsAttr(line, "AVERAGE-BANDWIDTH")?.toIntOrNull()
                ?: 2_000_000
            val res = extractHlsAttr(line, "RESOLUTION")
            val codecs = extractHlsAttr(line, "CODECS")
            val audioGroup = extractHlsAttr(line, "AUDIO")
            var j = i + 1
            while (j < lines.size && (lines[j].isBlank() || lines[j].trim().startsWith("#"))) {
                j++
            }
            if (j < lines.size) {
                val uri = lines[j].trim()
                baseUrl.resolve(uri)?.let { resolved ->
                    variants.add(HlsVariant(resolved, bw, res, codecs, audioGroup))
                }
                i = j
            }
        }
        i++
    }
    if (variants.isEmpty()) return null
    val audioByGroup = HashMap<String, HttpUrl>()
    audioRenditions.groupBy { it.first }.forEach { (grp, matches) ->
        val picked = matches.firstOrNull { it.third.first.equals("ita", ignoreCase = true) }?.third?.second
            ?: matches.firstOrNull { it.second }?.third?.second
            ?: matches.firstOrNull()?.third?.second
        if (picked != null) audioByGroup[grp] = picked
    }
    return HlsMasterSelection(
        variants = variants.sortedBy { it.bandwidth },
        audioByGroup = audioByGroup,
    )
}

internal fun selectBestHlsVariant(
    baseUrl: HttpUrl,
    text: String,
    maxBandwidth: Int = 7_200_000,
): Pair<HlsVariant, HttpUrl?>? {
    val master = parseHlsMaster(baseUrl, text) ?: return null
    val chosen = master.variants.filter { it.bandwidth <= maxBandwidth }.maxByOrNull { it.bandwidth }
        ?: master.variants.first()
    val audioUrl = chosen.audioGroupId?.let { master.audioByGroup[it] }
    return chosen to audioUrl
}

internal fun parseHlsMediaPlaylist(
    baseUrl: HttpUrl,
    text: String,
): ParsedMediaPlaylist {
    var targetDuration = 6.0
    var mediaSequence = 0L
    var hasEndList = false
    var hasMap = false
    var currentMapUrl: HttpUrl? = null
    var keyMethod: String? = null
    var keyUri: HttpUrl? = null
    var keyRest: String? = null
    var pendingDur: Double? = null
    var pendingDiscontinuity = false
    var pendingPdtIso: String? = null
    var pendingPdtMs: Long? = null
    val result = mutableListOf<ParsedHlsSegment>()

    for (rawLine in text.lines()) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue
        when {
            line.startsWith("#EXT-X-TARGETDURATION:") -> {
                targetDuration = line.substringAfter(':').toDoubleOrNull() ?: 6.0
            }
            line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                mediaSequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
            }
            line.startsWith("#EXT-X-ENDLIST") -> {
                hasEndList = true
            }
            line.startsWith("#EXT-X-MAP:") -> {
                extractHlsAttr(line, "URI")?.let { uri ->
                    currentMapUrl = baseUrl.resolve(uri)
                    if (currentMapUrl != null) hasMap = true
                }
            }
            line.startsWith("#EXT-X-KEY:") -> {
                val method = extractHlsAttr(line, "METHOD")
                if (method == "NONE") {
                    keyMethod = null
                    keyUri = null
                    keyRest = null
                } else if (method != null) {
                    keyMethod = method
                    keyUri = extractHlsAttr(line, "URI")?.let { baseUrl.resolve(it) }
                    keyRest = extractHlsAttr(line, "IV")?.let { "IV=$it" }
                }
            }
            line.startsWith("#EXT-X-DISCONTINUITY") && !line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE") -> {
                pendingDiscontinuity = true
            }
            line.startsWith("#EXT-X-PROGRAM-DATE-TIME:") -> {
                val iso = line.substringAfter(':').trim()
                pendingPdtIso = iso
                pendingPdtMs = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
                    .getOrElse { runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() }
            }
            line.startsWith("#EXTINF:") -> {
                val numStr = line.substringAfter(':').substringBefore(',').trim()
                pendingDur = numStr.toDoubleOrNull() ?: targetDuration
            }
            line.startsWith("#") -> Unit
            else -> {
                val resolved = baseUrl.resolve(line)
                if (resolved != null) {
                    val segSeq = mediaSequence + result.size
                    val effectiveKeyRest = if (keyMethod.equals("AES-128", ignoreCase = true) &&
                        (keyRest.isNullOrBlank() || !keyRest.contains("IV=", ignoreCase = true))
                    ) {
                        val implicitIv = String.format(java.util.Locale.US, "IV=0x%032x", segSeq)
                        if (keyRest.isNullOrBlank()) implicitIv else "$keyRest,$implicitIv"
                    } else {
                        keyRest
                    }
                    result.add(
                        ParsedHlsSegment(
                            url = resolved,
                            durationSec = pendingDur ?: targetDuration,
                            discontinuityBefore = pendingDiscontinuity,
                            mapUrl = currentMapUrl,
                            keyMethod = keyMethod,
                            keyUri = keyUri,
                            keyRest = effectiveKeyRest,
                            pdtIso = pendingPdtIso,
                            pdtEpochMs = pendingPdtMs,
                        ),
                    )
                }
                pendingDur = null
                pendingDiscontinuity = false
                pendingPdtIso = null
                pendingPdtMs = null
            }
        }
    }
    return ParsedMediaPlaylist(
        baseUrl = baseUrl,
        targetDurationSec = targetDuration,
        segments = result,
        mediaSequence = mediaSequence,
        hasEndList = hasEndList,
        hasMap = hasMap,
    )
}

internal fun pickAlignedHlsAudioStart(
    audioSegments: List<ParsedHlsSegment>,
    videoStartPdtMs: Long?,
    fallbackTailCount: Int = 2,
    videoStartSeq: Long? = null,
    audioBaseSeq: Long = 0L,
): Int {
    if (audioSegments.isEmpty()) return 0
    if (videoStartSeq != null && audioBaseSeq > 0L) {
        val seqOffset = (videoStartSeq - audioBaseSeq).toInt()
        if (seqOffset in audioSegments.indices) {
            return seqOffset
        }
    }
    if (videoStartPdtMs == null) {
        return (audioSegments.size - fallbackTailCount).coerceAtLeast(0)
    }
    val idx = audioSegments.indexOfFirst { seg ->
        seg.pdtEpochMs != null && seg.pdtEpochMs >= videoStartPdtMs - 1500L
    }
    return if (idx >= 0) idx else (audioSegments.size - fallbackTailCount).coerceAtLeast(0)
}

internal fun guessMediaExtension(path: String, defaultExt: String): String {
    val ext = path.substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "m4s", "m4v", "m4a", "ts", "aac", "key" -> ext
        else -> defaultExt
    }
}

internal fun fetchHlsTextWithFinalUrl(http: OkHttpClient, url: String): Pair<HttpUrl, String>? {
    val req = Request.Builder()
        .url(url)
        .header("User-Agent", STREAM_USER_AGENT)
        .get()
        .build()
    return runCatching {
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body ?: return@use resp.request.url to ""
            val ct = resp.header("Content-Type")?.lowercase(java.util.Locale.ROOT).orEmpty()
            val stream = body.byteStream()
            val buf = ByteArray(16 * 1024)
            val firstRead = stream.read(buf)
            if (firstRead <= 0) return@use resp.request.url to ""
            val probeText = String(buf, 0, firstRead, Charsets.UTF_8).trimStart()
            val looksHls = probeText.startsWith("#EXTM3U", ignoreCase = true) ||
                probeText.startsWith("#EXT-X-", ignoreCase = true)
            val hasIcyHeaders = resp.header("icy-metaint") != null || resp.header("icy-name") != null
            val hasMpegAudioSync = firstRead >= 2 &&
                buf[0] == 0xFF.toByte() &&
                (buf[1].toInt() and 0xE0) == 0xE0
            val isBinaryStream = buf[0] == 0x47.toByte() ||
                "video/mp2t" in ct ||
                "video/mp4" in ct ||
                "application/octet-stream" in ct ||
                ct.startsWith("audio/") ||
                ct.startsWith("video/") ||
                hasIcyHeaders ||
                hasMpegAudioSync
            if (!looksHls && isBinaryStream) {
                return@use resp.request.url to ""
            }
            val out = java.io.ByteArrayOutputStream(firstRead)
            out.write(buf, 0, firstRead)
            val maxBytes = 512 * 1024
            while (out.size() < maxBytes) {
                val n = stream.read(buf, 0, minOf(buf.size, maxBytes - out.size()))
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            resp.request.url to out.toString(Charsets.UTF_8.name())
        }
    }.getOrNull()
}

internal fun downloadBinaryAtomically(http: OkHttpClient, url: String, dest: File): Long =
    downloadBinaryAtomicallyTimed(http, url, dest).bytes

internal fun downloadBinaryAtomicallyTimed(http: OkHttpClient, url: String, dest: File): DownloadResult {
    val tmp = File(dest.parentFile, "${dest.name}.tmp")
    val req = Request.Builder()
        .url(url)
        .header("User-Agent", STREAM_USER_AGENT)
        .get()
        .build()
    val startedNs = System.nanoTime()
    return runCatching {
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val elapsed = ((System.nanoTime() - startedNs) / 1_000_000L).coerceAtLeast(0L)
                return@use DownloadResult(0L, elapsed)
            }
            val body = resp.body ?: run {
                val elapsed = ((System.nanoTime() - startedNs) / 1_000_000L).coerceAtLeast(0L)
                return@use DownloadResult(0L, elapsed)
            }
            tmp.outputStream().use { out ->
                body.byteStream().use { input ->
                    input.copyTo(out, 16 * 1024)
                }
                out.flush()
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            val elapsed = ((System.nanoTime() - startedNs) / 1_000_000L).coerceAtLeast(0L)
            DownloadResult(dest.length(), elapsed)
        }
    }.onFailure {
        runCatching { tmp.delete() }
    }.getOrElse {
        val elapsed = ((System.nanoTime() - startedNs) / 1_000_000L).coerceAtLeast(0L)
        DownloadResult(0L, elapsed)
    }
}
