package it.casatv.server.core

import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Resolves missing or broken VOD cover posters from TVMaze (series) and
 * iTunes Search API (movies & series fallback) with persistent SQLite caching
 * and concurrent in-flight request deduplication. No API key required.
 */
class VodPosterResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .callTimeout(5000, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val inFlight = ConcurrentHashMap<String, CompletableFuture<String?>>()

    data class CleanQuery(
        val title: String,
        val year: String?,
    )

    fun normalizeTitleForLookup(rawName: String, seriesTitle: String? = null): CleanQuery {
        var working = seriesTitle?.takeIf { it.isNotBlank() }?.trim() ?: rawName.trim()
        // Strip leading country/provider tags such as "(DE) ", "[IT] ", "IT | ", "FR: ", "DE - "
        for (i in 0 until 3) {
            val stripped = LEADING_TAG_RE.replaceFirst(working, "").trim()
            if (stripped == working || stripped.isBlank()) break
            working = stripped
        }
        val yearMatch = YEAR_RE.find(working)
        val year = yearMatch?.groupValues?.getOrNull(1)
        // Cut trailing episode identifiers when resolving a series poster from episode title
        working = EPISODE_SUFFIX_RE.replaceFirst(working, "").trim()
        working = TRAILER_NOISE_RE.replace(working, " ")
        working = BRACKET_TAG_RE.replace(working, " ")
        working = QUALITY_TAG_RE.replace(working, " ")
        working = SEPARATOR_CLEAN_RE.replace(working, " ").replace(Regex("""[-–—\s]+$"""), "").trim()
        if (working.length < 2) {
            working = BRACKET_TAG_RE.replace(rawName, " ").replace(SEPARATOR_CLEAN_RE, " ").trim()
        }
        return CleanQuery(title = working, year = year)
    }

    fun cacheKey(isSeries: Boolean, cleanTitle: String): String =
        "${if (isSeries) "s" else "m"}:${cleanTitle.lowercase(Locale.ROOT).trim()}"

    fun isRecentlyMissed(db: ServerDatabase, channel: IptvChannel): Boolean {
        val isSeries = channel.contentType == "series"
        val parsedSeries = if (isSeries) M3uParser.extractSeriesBaseTitle(channel.name) else null
        val q = normalizeTitleForLookup(channel.name, parsedSeries)
        if (q.title.length < 2) return true
        val key = cacheKey(isSeries, q.title)
        val cached = db.getVodPosterCache(key) ?: return false
        val (url, checkedAt) = cached
        return url.isBlank() && (System.currentTimeMillis() - checkedAt) < NEGATIVE_TTL_MS
    }

    fun resolvePosterUrl(db: ServerDatabase, channel: IptvChannel): String? {
        val isSeries = channel.contentType == "series"
        val parsedSeries = if (isSeries) M3uParser.extractSeriesBaseTitle(channel.name) else null
        val q = normalizeTitleForLookup(channel.name, parsedSeries)
        if (q.title.length < 2) return null

        val key = cacheKey(isSeries, q.title)
        val now = System.currentTimeMillis()
        db.getVodPosterCache(key)?.let { (url, checkedAt) ->
            if (url.isNotBlank()) return url
            if (now - checkedAt < NEGATIVE_TTL_MS) return null
        }

        val myFuture = CompletableFuture<String?>()
        val existing = inFlight.putIfAbsent(key, myFuture)
        if (existing != null) {
            return runCatching { existing.get(5500, TimeUnit.MILLISECONDS) }.getOrNull()
        }

        var resolved: String? = null
        try {
            val baseSubtitle = if (q.title.contains(" - ")) q.title.substringBefore(" - ").trim().takeIf { it.length >= 2 } else null
            resolved = if (isSeries) {
                fetchFromTvMaze(q.title)
                    ?: baseSubtitle?.let { fetchFromTvMaze(it) }
                    ?: fetchFromItunes(q.title, q.year, media = "tvShow")
                    ?: baseSubtitle?.let { fetchFromItunes(it, q.year, media = "tvShow") }
                    ?: fetchFromItunes(q.title, q.year, media = null)
            } else {
                fetchFromItunes(q.title, q.year, media = "movie")
                    ?: baseSubtitle?.let { fetchFromItunes(it, q.year, media = "movie") }
                    ?: fetchFromItunes(q.title, q.year, media = null)
                    ?: fetchFromTvMaze(q.title)
            }
            db.putVodPosterCache(key, resolved ?: "")
            if (!resolved.isNullOrBlank()) {
                db.updateChannelLogo(channel.id, resolved, parsedSeries.orEmpty())
            }
            return resolved
        } finally {
            inFlight.remove(key, myFuture)
            if (!myFuture.isDone) myFuture.complete(resolved)
        }
    }

    private fun fetchFromTvMaze(title: String): String? {
        val encoded = runCatching { URLEncoder.encode(title, "UTF-8") }.getOrNull() ?: return null
        val url = "https://api.tvmaze.com/singlesearch/shows?q=$encoded"
        val body = httpGetText(url) ?: return null
        return runCatching {
            val obj = JSONObject(body)
            val img = obj.optJSONObject("image") ?: return@runCatching null
            img.optString("medium").takeIf { it.startsWith("http") }
                ?: img.optString("original").takeIf { it.startsWith("http") }
        }.getOrNull()
    }

    private fun fetchFromItunes(title: String, year: String?, media: String?): String? {
        val encoded = runCatching { URLEncoder.encode(title, "UTF-8") }.getOrNull() ?: return null
        val mediaPart = if (media != null) "&media=$media" else ""
        val url = "https://itunes.apple.com/search?term=$encoded$mediaPart&limit=5"
        val body = httpGetText(url) ?: return null
        return runCatching {
            val obj = JSONObject(body)
            val results = obj.optJSONArray("results") ?: return@runCatching null
            if (results.length() == 0) return@runCatching null
            val normTarget = simplifyMatch(title)
            var bestArtwork: String? = null
            var bestScore = Int.MIN_VALUE
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val art = item.optString("artworkUrl100").takeIf { it.startsWith("http") }
                    ?: item.optString("artworkUrl60").takeIf { it.startsWith("http") }
                    ?: continue
                val candName = item.optString("trackName")
                    .ifBlank { item.optString("collectionName") }
                val normCand = simplifyMatch(candName)
                var score = 0
                if (normCand == normTarget) score += 100
                else if (normCand.contains(normTarget) || normTarget.contains(normCand)) score += 40
                else score -= 30
                if (!year.isNullOrBlank() && item.optString("releaseDate").startsWith(year)) {
                    score += 30
                }
                if (score > bestScore) {
                    bestScore = score
                    bestArtwork = upgradeItunesArtwork(art)
                }
            }
            if (bestScore >= 10) bestArtwork else null
        }.getOrNull()
    }

    private fun upgradeItunesArtwork(url: String): String =
        url.replace(Regex("""/\d+x\d+bb\.(jpg|jpeg|png|webp)$""", RegexOption.IGNORE_CASE), "/600x900bb.$1")

    private fun simplifyMatch(s: String): String =
        s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun httpGetText(url: String): String? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CasaTV/26.11")
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            resp.body?.string()?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    companion object {
        private const val NEGATIVE_TTL_MS = 24L * 3600_000L
        private val LEADING_TAG_RE = Regex("""^(?:\[[^\]]{1,12}\]|\([^)]{1,12}\)|[A-Za-z0-9]{2,6}\s*[:|\-])\s*""")
        private val EPISODE_SUFFIX_RE = Regex("""(?i)\s+(?:S\d{1,2}\s*E\d{1,3}|\d{1,2}x\d{1,3}|ep\.?\s*\d+|stagione\s*\d+|season\s*\d+).*$""")
        private val TRAILER_NOISE_RE = Regex("""(?i)\b(?:altyaz[ıi]l[ıi]|dublaj|t[uü]rk[cç]e\s*dublaj|sub\s*ita|dual\s*audio)\b""")
        private val YEAR_RE = Regex("""\b((?:19|20)\d{2})\b""")
        private val BRACKET_TAG_RE = Regex("""\([^)]*\)|\[[^\]]*]""")
        private val QUALITY_TAG_RE = Regex("""(?i)\b(?:4k|uhd|fhd|hd|sd|1080p|720p|2160p|hevc|h264|h265|multi|sub|subbed|dub|ita|eng|de|fr|es|tr)\b""")
        private val SEPARATOR_CLEAN_RE = Regex("""[|_•·]+|\s+""")

        fun parseImageDimensions(bytes: ByteArray): Pair<Int, Int>? {
            if (bytes.size < 30) return null
            // PNG
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
                val w = ((bytes[16].toInt() and 0xFF) shl 24) or ((bytes[17].toInt() and 0xFF) shl 16) or ((bytes[18].toInt() and 0xFF) shl 8) or (bytes[19].toInt() and 0xFF)
                val h = ((bytes[20].toInt() and 0xFF) shl 24) or ((bytes[21].toInt() and 0xFF) shl 16) or ((bytes[22].toInt() and 0xFF) shl 8) or (bytes[23].toInt() and 0xFF)
                return if (w > 0 && h > 0) w to h else null
            }
            // WebP (RIFF....WEBP)
            if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
            ) {
                val c0 = bytes[12].toInt().toChar()
                val c1 = bytes[13].toInt().toChar()
                val c2 = bytes[14].toInt().toChar()
                val c3 = bytes[15].toInt().toChar()
                val chunk = "$c0$c1$c2$c3"
                return when (chunk) {
                    "VP8 " -> {
                        val w = ((bytes[26].toInt() and 0xFF) or ((bytes[27].toInt() and 0x3F) shl 8))
                        val h = ((bytes[28].toInt() and 0xFF) or ((bytes[29].toInt() and 0x3F) shl 8))
                        if (w > 0 && h > 0) w to h else null
                    }
                    "VP8L" -> {
                        val b0 = bytes[21].toInt() and 0xFF
                        val b1 = bytes[22].toInt() and 0xFF
                        val b2 = bytes[23].toInt() and 0xFF
                        val b3 = bytes[24].toInt() and 0xFF
                        val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                        val w = (bits and 0x3FFF) + 1
                        val h = ((bits ushr 14) and 0x3FFF) + 1
                        if (w > 0 && h > 0) w to h else null
                    }
                    "VP8X" -> {
                        val w = 1 + ((bytes[24].toInt() and 0xFF) or ((bytes[25].toInt() and 0xFF) shl 8) or ((bytes[26].toInt() and 0xFF) shl 16))
                        val h = 1 + ((bytes[27].toInt() and 0xFF) or ((bytes[28].toInt() and 0xFF) shl 8) or ((bytes[29].toInt() and 0xFF) shl 16))
                        if (w > 0 && h > 0) w to h else null
                    }
                    else -> null
                }
            }
            // JPEG
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                var i = 2
                while (i + 9 < bytes.size) {
                    if (bytes[i] != 0xFF.toByte()) break
                    val marker = bytes[i + 1].toInt() and 0xFF
                    if (marker == 0xD8 || marker == 0xD9 || marker == 0x01 || marker in 0xD0..0xD7) {
                        i += 2
                        continue
                    }
                    val segLen = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                    if (segLen < 2) break
                    if (marker in intArrayOf(0xC0, 0xC1, 0xC2, 0xC3)) {
                        val h = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                        val w = ((bytes[i + 7].toInt() and 0xFF) shl 8) or (bytes[i + 8].toInt() and 0xFF)
                        return if (w > 0 && h > 0) w to h else null
                    }
                    i += 2 + segLen
                }
            }
            return null
        }

        fun isLandscapeStill(bytes: ByteArray): Boolean {
            val dims = parseImageDimensions(bytes) ?: return false
            val w = dims.first
            val h = dims.second
            return w > 0 && h > 0 && w.toFloat() > h.toFloat() * 1.08f
        }
    }
}
