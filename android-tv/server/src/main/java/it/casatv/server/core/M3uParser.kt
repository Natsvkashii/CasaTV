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

    fun parse(text: String, playlistId: String = "default"): List<IptvChannel> {
        val result = ArrayList<IptvChannel>()
        val seen = HashSet<String>()
        var attrs: Map<String, String> = emptyMap()
        var title = ""
        var group = ""
        var pending = false

        fun flush(url: String) {
            val cleanUrl = sanitizeUrl(url)
            if (cleanUrl.isBlank() || !isPlayable(cleanUrl)) return
            val name = (title.ifBlank { attrs["tvg-name"].orEmpty() }).trim()
            if (name.isBlank()) return
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
                country = inferCountry(attrs, tvgId, categoryRaw, name),
                tvgId = tvgId,
                quality = qualityOf(attrs["tvg-quality"].orEmpty(), name),
                number = result.size + 1,
                addedAt = System.currentTimeMillis() / 1000,
                playlistId = playlistId,
            )
        }

        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
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
                pending -> {
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
        val map = LinkedHashMap<String, String>()
        val quoted = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")
        quoted.findAll(part).forEach { match -> map[match.groupValues[1].lowercase()] = match.groupValues[2] }
        val bare = Regex("""([A-Za-z0-9_-]+)\s*=\s*([^\s"]+)""")
        bare.findAll(part).forEach { match ->
            val key = match.groupValues[1].lowercase()
            if (key !in map) map[key] = match.groupValues[2]
        }
        return map
    }

    private fun sanitizeUrl(url: String): String =
        url.substringBefore('|').trim()

    private fun isPlayable(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun firstToken(value: String?): String =
        value.orEmpty().split(';', ',', '|', '/').firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    /**
     * Country codes in custom playlists are often missing or written as a
     * country name. Infer them from `tvg-country`, the `tv.id@SD` suffix,
     * `group-title` (e.g. "Italy") and a trailing "(IT)" in the channel name.
     */
    private fun inferCountry(
        attrs: Map<String, String>,
        tvgId: String,
        group: String,
        name: String,
    ): String {
        parseCountry(attrs["tvg-country"])?.let { return it }

        val baseId = tvgId.substringBefore('@').trim()
        Regex("""\.([A-Za-z]{2})$""").find(baseId)?.groupValues?.getOrNull(1)
            ?.let { code -> normalizeCountry(code)?.let { return it } }

        parseCountry(group)?.let { return it }

        Regex("""[\(\[]([A-Za-z]{2})[\)\]]\s*$""").find(name)?.groupValues?.getOrNull(1)
            ?.let { code -> normalizeCountry(code)?.let { return it } }

        return ""
    }

    private fun parseCountry(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        value.split(',', ';', '|').forEach { token ->
            val clean = token.trim().trim('"', '\'')
            if (clean.isBlank()) return@forEach
            normalizeCountry(clean)?.let { return it }
            COUNTRY_NAMES[normalizeName(clean)]?.let { return it }
        }
        return null
    }

    private fun normalizeCountry(raw: String): String? {
        val code = raw.trim().uppercase(Locale.ROOT)
        if (code.length != 2 || ISO_COUNTRIES.none { it == code }) return null
        return code
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
        return decomposed
            .replace(Regex("""\p{M}+"""), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
    }

    private val ISO_COUNTRIES: Set<String> by lazy {
        Locale.getISOCountries().toSet()
    }

    private val COUNTRY_NAMES: Map<String, String> by lazy {
        val names = HashMap<String, String>()
        val locales = listOf(Locale.ENGLISH, Locale.ITALIAN, Locale.forLanguageTag("es"))
        Locale.getISOCountries().forEach { code ->
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
            return if (declared.endsWith("p", true)) declared.lowercase()
            else if (declared.all { it.isDigit() }) "${declared}p" else declared
        }
        val match = Regex("""\b(2160|1080|720|480|360)\s*p\b""", RegexOption.IGNORE_CASE).find(name)
        if (match != null) return "${match.groupValues[1]}p"
        return when {
            Regex("""\b(4k|uhd)\b""", RegexOption.IGNORE_CASE).containsMatchIn(name) -> "2160p"
            Regex("""\bfhd\b""", RegexOption.IGNORE_CASE).containsMatchIn(name) -> "1080p"
            Regex("""\bhd\b""", RegexOption.IGNORE_CASE).containsMatchIn(name) -> "720p"
            else -> ""
        }
    }

    private fun idFor(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return "m3u-" + digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
