package it.casatv.server.core

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Parser for M3U/M3U8 playlists. Only the flat `#EXTINF` format used by IPTV
 * providers is supported; media playlists (HLS segments) are not.
 *
 * The parser is deliberately tolerant: attributes may be quoted or not,
 * `#EXTGRP` and `#EXTVLCOPT` are honoured/ignored, `|User-Agent=...` options are
 * stripped from the URL, and anything that is not an http(s) stream is skipped.
 */
object M3uParser {

    private val QUOTED_ATTR_RE = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")
    private val BARE_ATTR_RE = Regex("""([A-Za-z0-9_-]+)\s*=\s*([^\s"]+)""")
    private val PAREN_TOKEN_RE = Regex("""[\(\[]([^\)\]]+)[\)\]]""")
    private val TRAILING_PIPE_COUNTRY_RE = Regex("""\|\s*([A-Za-z]{2})\s*$""")
    private val DIACRITICS_RE = Regex("""\p{M}+""")
    private val NON_ALNUM_RE = Regex("""[^a-z0-9]+""")
    private val QUALITY_P_RE = Regex("""\b(2160|1080|720|480|360)\s*p\b""", RegexOption.IGNORE_CASE)
    private val QUALITY_4K_RE = Regex("""\b(4k|uhd)\b""", RegexOption.IGNORE_CASE)
    private val QUALITY_FHD_RE = Regex("""\bfhd\b""", RegexOption.IGNORE_CASE)
    private val QUALITY_HD_RE = Regex("""\bhd\b""", RegexOption.IGNORE_CASE)

    private val IGNORED_PAREN_CODES = setOf("SD", "HD", "TV", "FM", "AM", "FHD", "UHD", "4K", "2K", "8K")
    private val NON_GEO_TLDS = setOf(
        "com", "net", "org", "tv", "io", "ai", "app", "dev", "me", "co", "info", "biz",
        "xyz", "live", "stream", "online", "site", "cloud", "cdn", "edu", "gov", "int",
        "mil", "pro", "cc", "ws", "fm", "am", "to", "ly", "gg", "nu", "sh", "la", "tk",
        "ml", "ga", "cf", "gq",
    )

    fun parse(text: String, playlistId: String = "default"): List<IptvChannel> {
        val rawText = text.removePrefix("\uFEFF")
        val normalizedText = if (isPlsContent(rawText)) convertPlsToM3u(rawText) else rawText
        if (isHlsMediaOrMasterManifest(normalizedText)) {
            return emptyList()
        }
        val result = ArrayList<IptvChannel>(1024)
        val seen = HashSet<String>(1024)
        var attrs: Map<String, String> = emptyMap()
        var title = ""
        var group = ""
        var pending = false

        fun flush(url: String) {
            val cleanUrl = sanitizeUrl(url)
            if (cleanUrl.isBlank() || !isPlayable(cleanUrl)) return
            val fallbackName = attrs["tvg-id"].orEmpty().substringBefore('.').trim().ifBlank {
                cleanUrl.substringAfterLast('/').substringBefore('?').substringBeforeLast('.').trim()
            }.ifBlank { "Canale ${result.size + 1}" }
            val name = title.ifBlank { attrs["tvg-name"].orEmpty() }.trim().ifBlank { fallbackName }
            val id = idFor(cleanUrl)
            if (!seen.add(id)) return
            val categoryRaw = (group.ifBlank { attrs["group-title"].orEmpty() }).trim()
            val tvgId = attrs["tvg-id"].orEmpty().trim()
            result += IptvChannel(
                id = id,
                name = name,
                url = cleanUrl,
                logo = attrs["tvg-logo"]?.trim()?.takeIf { it.startsWith("http") },
                category = categoryRaw.ifBlank { GENERAL_CATEGORY },
                language = normalizeLanguage(attrs["tvg-language"].orEmpty()),
                country = inferCountry(attrs, tvgId, categoryRaw, name, cleanUrl),
                tvgId = tvgId,
                quality = qualityOf(attrs["tvg-quality"].orEmpty(), name),
                number = result.size + 1,
                addedAt = System.currentTimeMillis() / 1000,
                playlistId = playlistId,
            )
        }

        normalizedText.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val payload = line.substringAfter(':', "")
                    val comma = commaIndex(payload)
                    val attrPart = if (comma >= 0) payload.substring(0, comma) else payload
                    title = if (comma >= 0) payload.substring(comma + 1).trim() else ""
                    attrs = parseAttributes(attrPart)
                    pending = true
                }
                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    group = line.substringAfter(':', "").trim()
                }
                line.startsWith("#") -> Unit
                pending || isPlayable(sanitizeUrl(line)) -> {
                    flush(line)
                    pending = false
                    attrs = emptyMap()
                    title = ""
                    group = ""
                }
            }
        }
        return result
    }

    const val GENERAL_CATEGORY = "general"

    private fun commaIndex(payload: String): Int {
        var inQuotes = false
        payload.forEachIndexed { index, ch ->
            when (ch) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return index
            }
        }
        return -1
    }

    private fun parseAttributes(part: String): Map<String, String> {
        val map = LinkedHashMap<String, String>(8)
        QUOTED_ATTR_RE.findAll(part).forEach { match ->
            map[match.groupValues[1].lowercase(Locale.ROOT)] = match.groupValues[2]
        }
        BARE_ATTR_RE.findAll(part).forEach { match ->
            val key = match.groupValues[1].lowercase(Locale.ROOT)
            if (key !in map) map[key] = match.groupValues[2]
        }
        return map
    }

    private fun sanitizeUrl(url: String): String =
        url.substringBefore('|').trim()

    private fun isPlayable(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("rtsp://") ||
            lower.startsWith("rtmp://") ||
            lower.startsWith("rtmps://")
    }

    private fun firstToken(value: String?): String =
        value.orEmpty().split(';', ',', '|', '/').firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    /**
     * Country codes in playlists may appear in `tvg-country`, `tv.id@SD` suffixes,
     * `group-title`, parenthesized country names/codes in the title, or stream ccTLD.
     */
    private fun inferCountry(
        attrs: Map<String, String>,
        tvgId: String,
        group: String,
        name: String,
        url: String,
    ): String {
        parseCountry(attrs["tvg-country"])?.let { return it }

        extractTvgIdCountry(tvgId)?.let { return it }

        parseCountry(group)?.let { return it }

        PAREN_TOKEN_RE.findAll(name).forEach { match ->
            parseCountry(match.groupValues[1], skipTechnicalCodes = true)?.let { return it }
        }

        TRAILING_PIPE_COUNTRY_RE.find(name)?.let { match ->
            parseCountry(match.groupValues[1], skipTechnicalCodes = true)?.let { return it }
        }

        return extractHostCcTld(url).orEmpty()
    }

    private fun extractTvgIdCountry(tvgId: String): String? {
        if (tvgId.isEmpty()) return null
        val atIdx = tvgId.indexOf('@')
        var end = if (atIdx >= 0) atIdx else tvgId.length
        while (end > 0 && tvgId[end - 1].isWhitespace()) end--
        if (end >= 4 && tvgId[end - 3] == '.') {
            val c1 = tvgId[end - 2].uppercaseChar()
            val c2 = tvgId[end - 1].uppercaseChar()
            if (c1 in 'A'..'Z' && c2 in 'A'..'Z') {
                return normalizeCountry("$c1$c2")
            }
        }
        return null
    }

    private fun extractHostCcTld(streamUrl: String): String? {
        val schemeIdx = streamUrl.indexOf("://")
        if (schemeIdx < 0) return null
        val hostStart = schemeIdx + 3
        val slashIdx = streamUrl.indexOf('/', hostStart)
        val hostEnd = if (slashIdx >= 0) slashIdx else streamUrl.length
        val colonIdx = streamUrl.indexOf(':', hostStart)
        val cleanEnd = if (colonIdx in hostStart until hostEnd) colonIdx else hostEnd
        if (cleanEnd - hostStart < 4) return null
        val host = streamUrl.substring(hostStart, cleanEnd).lowercase(Locale.ROOT)
        if (host == "jmp2.uk" || host.endsWith(".jmp2.uk")) return null
        val dotIdx = host.lastIndexOf('.')
        if (dotIdx < 0 || host.length - dotIdx - 1 != 2) return null
        val tld = host.substring(dotIdx + 1)
        if (tld in NON_GEO_TLDS) return null
        return normalizeCountry(tld)
    }

    private fun parseCountry(raw: String?, skipTechnicalCodes: Boolean = false): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        value.split(',', ';', '|').forEach { token ->
            val clean = token.trim().trim('"', '\'')
            if (clean.isBlank()) return@forEach
            val upper = clean.uppercase(Locale.ROOT)
            if (!skipTechnicalCodes || upper !in IGNORED_PAREN_CODES) {
                normalizeCountry(upper)?.let { return it }
            }
            if (clean.length > 2) {
                COUNTRY_NAMES[normalizeName(clean)]?.let { return it }
            }
        }
        return null
    }

    private fun normalizeCountry(raw: String): String? {
        val trimmed = raw.trim().uppercase(Locale.ROOT)
        val code = if (trimmed == "UK") "GB" else trimmed
        return if (code.length == 2 && code in ISO_COUNTRIES) code else null
    }

    fun normalizeLanguage(raw: String): String {
        val token = firstToken(raw).lowercase(Locale.ROOT)
        if (token.isBlank() || token in IGNORED_LANGUAGE_CODES) return ""
        if (token.length == 3 && token.all { it in 'a'..'z' }) return token
        LANGUAGE_CODES[token]?.let { return it }
        LANGUAGE_CODES[normalizeName(token)]?.let { return it }
        return if (token.length <= 20) token else ""
    }

    private val IGNORED_LANGUAGE_CODES = setOf("und", "mul", "zxx", "mis", "xxx", "n/a")

    private fun normalizeName(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return DIACRITICS_RE.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .let { NON_ALNUM_RE.replace(it, " ") }
            .trim()
    }

    private val ISO_COUNTRIES: Set<String> by lazy {
        Locale.getISOCountries().toHashSet()
    }

    private val COUNTRY_NAMES: Map<String, String> by lazy {
        val names = HashMap<String, String>(900)
        val locales = listOf(Locale.ENGLISH, Locale.ITALIAN, Locale.forLanguageTag("es"))
        ISO_COUNTRIES.forEach { code ->
            names[normalizeName(code)] = code
            locales.forEach { locale ->
                val label = Locale(locale.language, code).getDisplayCountry(locale)
                if (label.isNotBlank()) names[normalizeName(label)] = code
            }
        }
        names.putAll(
            mapOf(
                "uk" to "GB",
                "great britain" to "GB",
                "england" to "GB",
                "usa" to "US",
                "united states of america" to "US",
                "south korea" to "KR",
                "north korea" to "KP",
                "russia" to "RU",
                "czech republic" to "CZ",
                "ivory coast" to "CI",
            ),
        )
        names
    }

    private val LANGUAGE_CODES: Map<String, String> by lazy {
        val names = HashMap<String, String>()
        val locales = listOf(Locale.ENGLISH, Locale.ITALIAN, Locale.forLanguageTag("es"))
        Locale.getISOLanguages().forEach { tag ->
            val iso3 = runCatching { Locale(tag).getISO3Language() }.getOrNull()
                ?.takeIf { it.length == 3 } ?: return@forEach
            names[tag] = iso3
            locales.forEach { display ->
                val label = Locale(tag).getDisplayLanguage(display)
                if (label.isNotBlank()) names[normalizeName(label)] = iso3
            }
        }
        names
    }

    private fun qualityOf(attr: String, name: String): String {
        val declared = attr.trim()
        if (declared.isNotBlank()) {
            return if (declared.endsWith("p", true)) declared.lowercase(Locale.ROOT)
            else if (declared.all { it.isDigit() }) "${declared}p" else declared
        }
        val match = QUALITY_P_RE.find(name)
        if (match != null) return "${match.groupValues[1]}p"
        return when {
            QUALITY_4K_RE.containsMatchIn(name) -> "2160p"
            QUALITY_FHD_RE.containsMatchIn(name) -> "1080p"
            QUALITY_HD_RE.containsMatchIn(name) -> "720p"
            else -> ""
        }
    }

    private fun idFor(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return "m3u-" + digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun isHlsMediaOrMasterManifest(text: String): Boolean {
        val sample = text.take(16_384)
        val hasIptvExtinf = sample.contains("#EXTINF:-1", ignoreCase = true)
        if ((sample.contains("#EXT-X-TARGETDURATION", ignoreCase = true) ||
                sample.contains("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true)) &&
            !hasIptvExtinf
        ) {
            return true
        }
        if (sample.contains("#EXT-X-STREAM-INF", ignoreCase = true) &&
            !sample.contains("#EXTINF:", ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    private fun isPlsContent(text: String): Boolean {
        val head = text.trimStart().take(1024)
        return head.startsWith("[playlist]", ignoreCase = true) ||
            (head.contains("NumberOfEntries=", ignoreCase = true) && head.contains("File1=", ignoreCase = true))
    }

    private fun convertPlsToM3u(text: String): String {
        val files = LinkedHashMap<Int, String>()
        val titles = LinkedHashMap<Int, String>()
        val fileRe = Regex("""^File(\d+)\s*=\s*(.+)$""", RegexOption.IGNORE_CASE)
        val titleRe = Regex("""^Title(\d+)\s*=\s*(.+)$""", RegexOption.IGNORE_CASE)
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            fileRe.matchEntire(line)?.let { m ->
                files[m.groupValues[1].toIntOrNull() ?: return@let] = m.groupValues[2].trim()
                return@forEach
            }
            titleRe.matchEntire(line)?.let { m ->
                titles[m.groupValues[1].toIntOrNull() ?: return@let] = m.groupValues[2].trim()
            }
        }
        if (files.isEmpty()) return ""
        val sb = StringBuilder("#EXTM3U\n")
        files.entries.sortedBy { it.key }.forEach { (idx, url) ->
            val title = titles[idx]?.takeIf { it.isNotBlank() }
                ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "Channel $idx" }
            sb.append("#EXTINF:-1,").append(title.replace(',', ' ')).append('\n')
            sb.append(url).append('\n')
        }
        return sb.toString()
    }
}
