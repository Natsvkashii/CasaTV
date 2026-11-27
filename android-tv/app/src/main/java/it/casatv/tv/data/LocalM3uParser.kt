package it.casatv.tv.data

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

object LocalM3uParser {
    private val ATTR_RE = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")
    private val PAREN_TOKEN_RE = Regex("""[\(\[]([^\)\]]+)[\)\]]""")
    private val DIACRITICS_RE = Regex("""\p{M}+""")
    private val NON_ALNUM_RE = Regex("""[^a-z0-9]+""")
    private val LANG_SPLIT_RE = Regex("""[;/,|]+""")
    private val Q_4K_RE = Regex("""\b(4K|UHD|2160P?)\b""", RegexOption.IGNORE_CASE)
    private val Q_FHD_RE = Regex("""\b(FHD|1080[PI]?)\b""", RegexOption.IGNORE_CASE)
    private val Q_HD_RE = Regex("""\b(HD|720P?)\b""", RegexOption.IGNORE_CASE)

    private val IGNORED_PAREN_CODES = setOf("SD", "HD", "TV", "FM", "AM", "FHD", "UHD", "4K", "2K", "8K")
    private val NON_GEO_TLDS = setOf(
        "com", "net", "org", "tv", "io", "ai", "app", "dev", "me", "co", "info", "biz",
        "xyz", "live", "stream", "online", "site", "cloud", "cdn", "edu", "gov", "int",
        "mil", "pro", "cc", "ws", "fm", "am", "to", "ly", "gg", "nu", "sh", "la", "tk",
        "ml", "ga", "cf", "gq",
    )

    private val ISO_COUNTRIES: Set<String> by lazy {
        Locale.getISOCountries().toHashSet()
    }

    private val COUNTRY_NAMES: Map<String, String> by lazy {
        val names = HashMap<String, String>(900)
        val locales = listOf(Locale.ENGLISH, Locale.ITALIAN, Locale.forLanguageTag("es"))
        ISO_COUNTRIES.forEach { code ->
            names[normalizeName(code)] = code
            locales.forEach { loc ->
                val label = Locale(loc.language, code).getDisplayCountry(loc)
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

    fun parse(text: String): List<Channel> {
        val rawText = text.removePrefix("\uFEFF")
        val cleaned = if (isPlsContent(rawText)) convertPlsToM3u(rawText) else rawText
        if (isHlsMediaOrMasterManifest(cleaned)) {
            return emptyList()
        }
        val channels = ArrayList<Channel>(512)
        val seenUrls = HashSet<String>(512)
        val sha1 = MessageDigest.getInstance("SHA-1")
        var pendingExtinf: String? = null
        var pendingExtgrp: String? = null
        var autoNumber = 1
        val attrs = HashMap<String, String>(8)

        cleaned.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingExtinf = line
                return@forEach
            }
            if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                pendingExtgrp = line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
                return@forEach
            }
            if (line.startsWith("#")) return@forEach

            val lower = line.lowercase(Locale.ROOT)
            val valid = lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("rtsp://") ||
                lower.startsWith("rtmp://") ||
                lower.startsWith("udp://")
            if (!valid || !seenUrls.add(line)) {
                pendingExtinf = null
                pendingExtgrp = null
                return@forEach
            }

            val extinf = pendingExtinf ?: ""
            val extgrp = pendingExtgrp
            pendingExtinf = null
            pendingExtgrp = null

            attrs.clear()
            for (match in ATTR_RE.findAll(extinf)) {
                val k = match.groupValues[1].lowercase(Locale.ROOT)
                val v = match.groupValues[2].trim()
                if (v.isNotEmpty()) attrs[k] = v
            }

            val name = extractExtinfTitle(extinf)
                ?.takeIf { it.isNotEmpty() }
                ?: attrs["tvg-name"]
                ?: "Canale $autoNumber"
            val group = (attrs["group-title"] ?: extgrp)
                ?.split(';', ',')
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
                .ifBlank { "Generale" }
            val number = attrs["tvg-chno"]?.toIntOrNull() ?: autoNumber
            val epgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
            val country = inferCountry(
                tvgCountry = attrs["tvg-country"],
                epgId = epgId,
                groupTitle = attrs["group-title"] ?: extgrp.orEmpty(),
                name = name,
                streamUrl = line,
            )
            val quality = when {
                Q_4K_RE.containsMatchIn(name) -> "4K"
                Q_FHD_RE.containsMatchIn(name) -> "FHD"
                Q_HD_RE.containsMatchIn(name) -> "HD"
                else -> "SD"
            }
            val parsedLanguages = attrs["tvg-language"]
                ?.split(LANG_SPLIT_RE)
                ?.map { it.trim().lowercase(Locale.ROOT) }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            sha1.reset()
            val rawDigest = sha1.digest(line.toByteArray())
            val digest = buildString(12) {
                for (i in 0 until minOf(6, rawDigest.size)) {
                    val b = rawDigest[i].toInt() and 0xFF
                    append(HEX_CHARS[b ushr 4])
                    append(HEX_CHARS[b and 0x0F])
                }
            }

            channels.add(
                Channel(
                    id = "local_$digest",
                    number = number,
                    name = name,
                    country = country,
                    categories = listOf(group.lowercase(Locale.ROOT)),
                    categoryNames = listOf(group),
                    languages = parsedLanguages,
                    quality = quality,
                    logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") },
                    streamUrl = line,
                    epgId = epgId,
                ),
            )
            autoNumber++
        }
        return channels
    }

    private fun extractExtinfTitle(extinf: String): String? {
        var inQuotes = false
        var commaIdx = -1
        for (i in extinf.indices) {
            val ch = extinf[i]
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == ',' && !inQuotes) commaIdx = i
        }
        if (commaIdx < 0) return null
        return extinf.substring(commaIdx + 1).trim().takeIf { it.isNotEmpty() }
    }

    private fun normalizeName(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return DIACRITICS_RE.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .let { NON_ALNUM_RE.replace(it, " ") }
            .trim()
    }

    private fun normalizeIso(raw: String): String? {
        val clean = raw.trim().uppercase(Locale.ROOT)
        val mapped = if (clean == "UK") "GB" else clean
        return if (mapped.length == 2 && mapped in ISO_COUNTRIES) mapped else null
    }

    private fun parseCountryList(raw: String?, allowShortCode: Boolean): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        value.split(',', ';', '|').forEach { token ->
            val clean = token.trim().trim('"', '\'')
            if (clean.isBlank()) return@forEach
            val upper = clean.uppercase(Locale.ROOT)
            if (allowShortCode && upper !in IGNORED_PAREN_CODES) {
                normalizeIso(upper)?.let { return it }
            }
            if (clean.length > 2) {
                COUNTRY_NAMES[normalizeName(clean)]?.let { return it }
            }
        }
        return null
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    private fun extractTvgIdCountry(epgId: String?): String? {
        val raw = epgId ?: return null
        val atIdx = raw.indexOf('@')
        var end = if (atIdx >= 0) atIdx else raw.length
        while (end > 0 && raw[end - 1].isWhitespace()) end--
        if (end >= 4 && raw[end - 3] == '.') {
            val c1 = raw[end - 2].uppercaseChar()
            val c2 = raw[end - 1].uppercaseChar()
            if (c1 in 'A'..'Z' && c2 in 'A'..'Z') {
                val code = "$c1$c2"
                val mapped = if (code == "UK") "GB" else code
                if (mapped in ISO_COUNTRIES) return mapped
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
        val dotIdx = host.lastIndexOf('.')
        if (dotIdx < 0 || host.length - dotIdx - 1 != 2) return null
        val tld = host.substring(dotIdx + 1)
        if (tld in NON_GEO_TLDS) return null
        val iso = if (tld == "uk") "GB" else tld.uppercase(Locale.ROOT)
        return if (iso in ISO_COUNTRIES) iso else null
    }

    private fun inferCountry(
        tvgCountry: String?,
        epgId: String?,
        groupTitle: String,
        name: String,
        streamUrl: String,
    ): String? {
        parseCountryList(tvgCountry, allowShortCode = true)?.let { return it }
        extractTvgIdCountry(epgId)?.let { return it }
        parseCountryList(groupTitle, allowShortCode = false)?.let { return it }
        PAREN_TOKEN_RE.findAll(name).forEach { match ->
            parseCountryList(match.groupValues[1], allowShortCode = true)?.let { return it }
        }
        return extractHostCcTld(streamUrl)
    }

    private fun isHlsMediaOrMasterManifest(text: String): Boolean {
        val sample = text.take(16_384)
        return sample.contains("#EXT-X-TARGETDURATION", ignoreCase = true) ||
            sample.contains("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true) ||
            sample.contains("#EXT-X-STREAM-INF", ignoreCase = true)
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
                ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "Canale $idx" }
            sb.append("#EXTINF:-1,").append(title.replace(',', ' ')).append('\n')
            sb.append(url).append('\n')
        }
        return sb.toString()
    }
}
