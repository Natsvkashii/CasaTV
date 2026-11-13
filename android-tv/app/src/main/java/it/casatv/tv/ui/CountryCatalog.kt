package it.casatv.tv.ui

import it.casatv.tv.data.Facet
import java.util.Locale

/**
 * Country helpers shared by the Home, TV mode, Channels, Settings and player
 * pickers.
 *
 * The server only exposes the countries that actually occur in the catalog. A
 * fresh custom M3U playlist often has no `tvg-country` at all, which used to
 * leave the picker completely empty. We therefore merge the server facets with
 * the full ISO-3166 list, keeping the channel counts where available. The list
 * is never empty, while countries with channels are shown first.
 */
fun buildCountryCatalog(facets: List<Facet>, language: String): List<Facet> {
    val counts = HashMap<String, Int>()
    facets.forEach { facet ->
        val code = normalizeCountryCode(facet.code) ?: return@forEach
        counts[code] = (counts[code] ?: 0) + facet.count
    }

    val ordered = LinkedHashSet<String>()
    // Countries that actually have channels in the catalog, sorted alphabetically A-Z
    facets
        .mapNotNull { normalizeCountryCode(it.code) }
        .distinct()
        .sortedBy { countryName(it, language).lowercase(Locale.ROOT) }
        .forEach { ordered += it }

    // Remaining ISO countries, also sorted alphabetically A-Z
    Locale.getISOCountries()
        .filterNot { it in ordered }
        .sortedBy { countryName(it, language).lowercase(Locale.ROOT) }
        .forEach { ordered += it }

    return ordered.map { code ->
        Facet(
            code = code,
            name = countryName(code, language),
            flag = countryFlag(code),
            count = counts[code] ?: 0,
        )
    }
}

fun countryFacet(code: String?, countries: List<Facet>): Facet? {
    val normalized = normalizeCountryCode(code) ?: return null
    return countries.firstOrNull { it.code == normalized }
}

/**
 * Languages offered by the channel filter. The server only reports languages
 * that occur in the playlist; a fresh import can therefore expose just one or
 * two pills. Merge those facets with a stable list of the main languages so the
 * filter always offers a useful choice. Codes are ISO 639-2 to match both the
 * Android and Node servers.
 */
private val MAIN_LANGUAGE_CODES = listOf(
    "eng", // English
    "spa", // Spanish
    "ita", // Italian
    "fra", // French
    "deu", // German
    "por", // Portuguese
    "rus", // Russian
    "ara", // Arabic
    "zho", // Chinese
    "jpn", // Japanese
    "kor", // Korean
    "hin", // Hindi
    "nld", // Dutch
    "tur", // Turkish
    "pol", // Polish
    "ukr", // Ukrainian
)

fun buildLanguageCatalog(facets: List<Facet>, language: String): List<Facet> {
    val counts = HashMap<String, Int>()
    facets.forEach { facet ->
        val code = normalizeLanguageCode(facet.code) ?: return@forEach
        counts[code] = (counts[code] ?: 0) + facet.count
    }

    val ordered = LinkedHashSet<String>()
    MAIN_LANGUAGE_CODES.forEach { ordered += it }
    facets
        .mapNotNull { facet -> normalizeLanguageCode(facet.code)?.let { it to facet } }
        .sortedWith(
            compareByDescending<Pair<String, Facet>> { it.second.count }
                .thenBy { languageName(it.first, language).lowercase(Locale.ROOT) },
        )
        .forEach { (code, _) -> ordered += code }

    return ordered.map { code ->
        Facet(
            code = code,
            name = languageName(code, language),
            count = counts[code] ?: 0,
        )
    }
}

fun normalizeLanguageCode(raw: String?): String? {
    val code = raw.orEmpty().trim().lowercase(Locale.ROOT)
    if (code.length !in 2..3 || code.any { it !in 'a'..'z' }) return null
    return code
}

fun languageName(code: String?, language: String): String {
    val normalized = normalizeLanguageCode(code) ?: return code.orEmpty()
    val locale = Locale.forLanguageTag(language.ifBlank { "en" })
    val parsed = Locale.forLanguageTag(normalized)
    val display = parsed.getDisplayLanguage(locale)
    return display.ifBlank { normalized.replaceFirstChar { it.uppercase(locale) } }
}

fun normalizeCountryCode(raw: String?): String? {
    val code = raw.orEmpty().trim().uppercase(Locale.ROOT)
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return null
    return code
}

fun countryFlag(code: String?): String {
    val normalized = normalizeCountryCode(code) ?: return "🌍"
    return String(
        Character.toChars(0x1F1E6 + (normalized[0] - 'A')) +
            Character.toChars(0x1F1E6 + (normalized[1] - 'A')),
    )
}

fun countryName(code: String?, language: String): String {
    val normalized = normalizeCountryCode(code) ?: return "🌍"
    val locale = Locale.forLanguageTag(language.ifBlank { "en" })
    val display = Locale(locale.language.ifBlank { "en" }, normalized)
        .getDisplayCountry(locale)
    return display.ifBlank { normalized }
}
