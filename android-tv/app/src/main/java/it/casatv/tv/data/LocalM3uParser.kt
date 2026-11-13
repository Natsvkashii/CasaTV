package it.casatv.tv.data

import java.security.MessageDigest

object LocalM3uParser {
    private val ATTR_RE = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")
    private val TRAILING_TITLE_RE = Regex(""",\s*(.+)$""")

    fun parse(text: String): List<Channel> {
        val cleaned = text.removePrefix("\uFEFF")
        val lines = cleaned.split(Regex("""\r?\n"""))
        val channels = mutableListOf<Channel>()
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

            val lower = line.lowercase()
            val valid = lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("rtsp://") ||
                lower.startsWith("rtmp://") ||
                lower.startsWith("udp://")
            if (!valid) {
                pendingExtinf = null
                continue
            }

            val extinf = pendingExtinf ?: ""
            pendingExtinf = null

            val attrs = mutableMapOf<String, String>()
            for (match in ATTR_RE.findAll(extinf)) {
                val k = match.groupValues[1].lowercase()
                val v = match.groupValues[2].trim()
                if (v.isNotEmpty()) attrs[k] = v
            }

            val name = TRAILING_TITLE_RE.find(extinf)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: attrs["tvg-name"]
                ?: "Canale $autoNumber"
            val group = attrs["group-title"]?.split(';', ',')?.firstOrNull()?.trim().orEmpty()
                .ifBlank { "Generale" }
            val number = attrs["tvg-chno"]?.toIntOrNull() ?: autoNumber
            val country = attrs["tvg-country"]?.trim()?.uppercase()?.take(2)
            val quality = when {
                name.contains("4K", true) || name.contains("UHD", true) -> "4K"
                name.contains("FHD", true) || name.contains("1080", true) -> "FHD"
                name.contains("HD", true) || name.contains("720", true) -> "HD"
                else -> "SD"
            }
            val digest = MessageDigest.getInstance("SHA-1").digest(line.toByteArray())
                .take(6).joinToString("") { "%02x".format(it) }

            channels.add(
                Channel(
                    id = "local_$digest",
                    number = number,
                    name = name,
                    country = country,
                    categories = listOf(group.lowercase()),
                    categoryNames = listOf(group),
                    languages = listOfNotNull(attrs["tvg-language"]?.lowercase()?.take(2)),
                    quality = quality,
                    logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") },
                    streamUrl = line,
                    epgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                ),
            )
            autoNumber++
        }
        return channels.distinctBy { it.streamUrl }
    }
}
