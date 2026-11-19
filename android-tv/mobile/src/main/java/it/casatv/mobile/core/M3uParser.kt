package it.casatv.mobile.core

import it.casatv.mobile.data.MobileChannel
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object M3uParser {
    private val ATTR_RE = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")
    private val TRAILING_TITLE_RE = Regex(""",\s*(.+)$""")
    private val PAREN_TOKEN_RE = Regex("""[\(\[]([^\)\]]+)[\)\]]""")
    private val DIACRITICS_RE = Regex("""\p{M}+""")
    private val NON_ALNUM_RE = Regex("""[^a-z0-9]+""")

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

    private val countryMetaCache = ConcurrentHashMap<String, Pair<String, String>>()

    fun parse(text: String, playlistId: String = "default"): List<MobileChannel> {
        val cleaned = text.removePrefix("\uFEFF")
        val channels = ArrayList<MobileChannel>(1024)
        val seenUrls = HashSet<String>(1024)

        var pendingExtinf: String? = null
        var autoNumber = 1
        val attrs = HashMap<String, String>(8)

        cleaned.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingExtinf = line
                return@forEach
            }

            if (line.startsWith("#")) return@forEach

            val url = sanitizeUrl(line)
            if (url == null || !seenUrls.add(url)) {
                pendingExtinf = null
                return@forEach
            }

            val extinf = pendingExtinf ?: ""
            pendingExtinf = null

            attrs.clear()
            for (match in ATTR_RE.findAll(extinf)) {
                val key = match.groupValues[1].lowercase(Locale.ROOT)
                val value = match.groupValues[2].trim()
                if (value.isNotEmpty()) attrs[key] = value
            }

            val titleMatch = TRAILING_TITLE_RE.find(extinf)
            val rawName = titleMatch?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: attrs["tvg-name"]
                ?: "Canale $autoNumber"

            val groupTitle = attrs["group-title"]?.trim().orEmpty()
            val logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") }
            val epgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }

            val parsedNumber = attrs["tvg-chno"]?.toIntOrNull()
                ?: attrs["channel-number"]?.toIntOrNull()
                ?: autoNumber

            val countryCode = inferCountry(
                tvgCountry = attrs["tvg-country"],
                epgId = epgId,
                groupTitle = groupTitle,
                name = rawName,
                streamUrl = url,
            )
            val (countryName, flag) = countryMeta(countryCode)
            val quality = inferQuality(rawName)
            val category = inferCategory(groupTitle)

            val id = "${playlistId}_${shortHash(url)}"
            channels.add(
                MobileChannel(
                    id = id,
                    number = parsedNumber,
                    name = rawName,
                    streamUrl = url,
                    logo = logo,
                    country = countryCode,
                    countryName = countryName,
                    flag = flag,
                    category = category,
                    language = attrs["tvg-language"]?.lowercase(Locale.ROOT)?.take(2),
                    quality = quality,
                    epgId = epgId,
                    playlistId = playlistId,
                ),
            )
            autoNumber++
        }

        return channels
    }

    private fun sanitizeUrl(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase(Locale.ROOT)
        val valid = lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("rtsp://") ||
            lower.startsWith("rtmp://") ||
            lower.startsWith("udp://")
        return if (valid) trimmed else null
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

    private fun parseCountryList(raw: String?, allowShortCode: Boolean = true): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        value.split(',', ';', '|').forEach { token ->
            val clean = token.trim().trim('"', '\'')
            if (clean.isBlank()) return@forEach
            val upper = clean.uppercase(Locale.ROOT)
            if (allowShortCode && upper !in IGNORED_PAREN_CODES) {
                normalizeIso(upper)?.let { return it }
            }
            if (!allowShortCode && clean.length == 2 && upper !in IGNORED_PAREN_CODES) {
                normalizeIso(upper)?.let { return it }
            }
            if (clean.length > 2) {
                COUNTRY_NAMES[normalizeName(clean)]?.let { return it }
            }
        }
        return null
    }

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
        if (host == "jmp2.uk" || host.endsWith(".jmp2.uk")) return null
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

    fun countryMeta(code: String?, language: String = "en"): Pair<String, String> {
        val raw = code?.trim()?.uppercase(Locale.ROOT) ?: return "—" to "🌐"
        val upper = if (raw == "UK") "GB" else raw
        if (upper.length != 2 || upper[0] !in 'A'..'Z' || upper[1] !in 'A'..'Z') return upper to "🌐"
        val langTag = language.ifBlank { "en" }.lowercase(Locale.ROOT).take(2)
        val cacheKey = "$upper:$langTag"
        return countryMetaCache.getOrPut(cacheKey) {
            val flag = String(
                Character.toChars(0x1F1E6 + (upper[0] - 'A')) +
                    Character.toChars(0x1F1E6 + (upper[1] - 'A')),
            )
            val locale = Locale.forLanguageTag(langTag)
            val name = Locale("", upper).getDisplayCountry(locale).ifBlank { upper }
            name to flag
        }
    }

    private fun inferCategory(groupTitle: String): String {
        val clean = groupTitle.trim()
        if (clean.isEmpty()) return "Generale"
        return clean.split(';', ',').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "Generale"
    }

    private fun inferQuality(name: String): String {
        val upper = name.uppercase(Locale.ROOT)
        return when {
            upper.contains("4K") || upper.contains("UHD") -> "4K"
            upper.contains("FHD") || upper.contains("1080") -> "FHD"
            upper.contains("HD") || upper.contains("720") -> "HD"
            else -> "SD"
        }
    }

    private fun shortHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
