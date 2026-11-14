package it.casatv.mobile.core

import it.casatv.mobile.data.MobileChannel
import java.security.MessageDigest
import java.util.Locale

object M3uParser {
    private val ATTR_RE = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")
    private val TRAILING_TITLE_RE = Regex(""",\s*(.+)$""")

    fun parse(text: String, playlistId: String = "default"): List<MobileChannel> {
        val cleaned = text.removePrefix("\uFEFF")
        val lines = cleaned.split(Regex("""\r?\n"""))
        val channels = mutableListOf<MobileChannel>()

        var pendingExtinf: String? = null
        var autoNumber = 1

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingExtinf = line
                continue
            }

            if (line.startsWith("#")) continue

            val url = sanitizeUrl(line)
            if (url == null) {
                pendingExtinf = null
                continue
            }

            val extinf = pendingExtinf ?: ""
            pendingExtinf = null

            val attrs = mutableMapOf<String, String>()
            for (match in ATTR_RE.findAll(extinf)) {
                val key = match.groupValues[1].lowercase()
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

            val countryCode = inferCountry(attrs["tvg-country"], groupTitle, rawName)
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
                    language = attrs["tvg-language"]?.lowercase()?.take(2),
                    quality = quality,
                    epgId = epgId,
                    playlistId = playlistId,
                ),
            )
            autoNumber++
        }

        return channels.distinctBy { it.streamUrl }
    }

    private fun sanitizeUrl(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        val valid = lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("rtsp://") ||
            lower.startsWith("rtmp://") ||
            lower.startsWith("udp://")
        return if (valid) trimmed else null
    }

    private fun inferCountry(tvgCountry: String?, groupTitle: String, name: String): String? {
        val direct = tvgCountry?.trim()?.uppercase()
        if (!direct.isNullOrBlank() && direct.length in 2..3) {
            return if (direct == "UK") "GB" else direct
        }
        val combined = "$groupTitle $name".uppercase()
        return when {
            combined.contains("[IT]") || combined.contains("ITALIA") || combined.contains("ITALY") -> "IT"
            combined.contains("[ES]") || combined.contains("SPAIN") || combined.contains("ESPAÑA") -> "ES"
            combined.contains("[FR]") || combined.contains("FRANCE") -> "FR"
            combined.contains("[DE]") || combined.contains("GERMANY") || combined.contains("DEUTSCH") -> "DE"
            combined.contains("[UK]") || combined.contains("[GB]") -> "GB"
            combined.contains("[US]") || combined.contains("USA") -> "US"
            combined.contains("[CH]") || combined.contains("SVIZZERA") -> "CH"
            combined.contains("[BR]") || combined.contains("BRASIL") -> "BR"
            combined.contains("[PT]") || combined.contains("PORTUGAL") -> "PT"
            else -> null
        }
    }

    fun countryMeta(code: String?): Pair<String, String> {
        val raw = code?.trim()?.uppercase(Locale.ROOT) ?: return "Altro" to "🌐"
        val upper = if (raw == "UK") "GB" else raw
        if (upper.length != 2 || upper.any { it !in 'A'..'Z' }) return upper to "🌐"
        val flag = String(
            Character.toChars(0x1F1E6 + (upper[0] - 'A')) +
                Character.toChars(0x1F1E6 + (upper[1] - 'A')),
        )
        val italian = Locale.forLanguageTag("it")
        val name = Locale("", upper).getDisplayCountry(italian).ifBlank { upper }
        return name to flag
    }

    private fun inferCategory(groupTitle: String): String {
        val clean = groupTitle.trim()
        if (clean.isEmpty()) return "Generale"
        return clean.split(';', ',').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "Generale"
    }

    private fun inferQuality(name: String): String {
        val upper = name.uppercase()
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
