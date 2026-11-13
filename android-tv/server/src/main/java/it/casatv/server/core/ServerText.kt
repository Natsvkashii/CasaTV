package it.casatv.server.core

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * Server-side copy in Italian, English and Spanish. The language is chosen on
 * first launch and can be changed from Settings; every screen and the HTTP
 * responses use the same active language.
 */
class Strings(val language: String = "it") {
    private val code: String = when (language.take(2).lowercase()) {
        "en" -> "en"
        "es" -> "es"
        else -> "it"
    }

    fun t(italian: String, english: String, spanish: String): String = when (code) {
        "en" -> english
        "es" -> spanish
        else -> italian
    }
}

val LocalStrings = staticCompositionLocalOf { Strings("it") }

fun textOf(language: String): Strings = Strings(language)

data class ProfileLanguageOption(val code: String, val name: String)

val PROFILE_LANGUAGES = listOf(
    ProfileLanguageOption("it", "Italiano"),
    ProfileLanguageOption("en", "English"),
    ProfileLanguageOption("es", "Español"),
)

val PROFILE_AVATARS = listOf(
    "🦊", "🐼", "🐯", "🐨", "🦁", "🐸", "🐵", "🐺", "🦄", "🐙",
    "🐳", "🦉", "🐻", "🐧", "🦖", "🐝", "🐬", "🦋", "🐰", "🐲",
)

private val COUNTRIES = mapOf(
    "IT" to Triple("Italia", "Italy", "Italia"),
    "ES" to Triple("Spagna", "Spain", "España"),
    "FR" to Triple("Francia", "France", "Francia"),
    "DE" to Triple("Germania", "Germany", "Alemania"),
    "GB" to Triple("Regno Unito", "United Kingdom", "Reino Unido"),
    "US" to Triple("Stati Uniti", "United States", "Estados Unidos"),
    "PT" to Triple("Portogallo", "Portugal", "Portugal"),
    "MX" to Triple("Messico", "Mexico", "México"),
    "AR" to Triple("Argentina", "Argentina", "Argentina"),
    "BR" to Triple("Brasile", "Brazil", "Brasil"),
    "CH" to Triple("Svizzera", "Switzerland", "Suiza"),
    "AT" to Triple("Austria", "Austria", "Austria"),
    "NL" to Triple("Paesi Bassi", "Netherlands", "Países Bajos"),
    "BE" to Triple("Belgio", "Belgium", "Bélgica"),
    "CA" to Triple("Canada", "Canada", "Canadá"),
    "CL" to Triple("Cile", "Chile", "Chile"),
    "CO" to Triple("Colombia", "Colombia", "Colombia"),
    "AU" to Triple("Australia", "Australia", "Australia"),
)

fun countryName(code: String, language: String): String {
    val entry = COUNTRIES[code.uppercase()]
    if (entry != null) {
        return when (language.take(2).lowercase()) {
            "en" -> entry.second
            "es" -> entry.third
            else -> entry.first
        }
    }
    val normalized = code.trim().uppercase()
    if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) return code
    val locale = Locale.forLanguageTag(language.ifBlank { "en" })
    val display = Locale(locale.language.ifBlank { "en" }, normalized)
        .getDisplayCountry(locale)
    return display.ifBlank { normalized }
}

fun countryFlag(code: String): String {
    if (code.length != 2 || code.any { !it.isLetter() }) return "🌍"
    return String(
        Character.toChars(0x1F1E6 + (code[0].uppercaseChar() - 'A')) +
            Character.toChars(0x1F1E6 + (code[1].uppercaseChar() - 'A')),
    )
}

fun languageLabel(code: String): String =
    PROFILE_LANGUAGES.firstOrNull { it.code == code.take(2).lowercase() }?.name
        ?: PROFILE_LANGUAGES.first().name

/** Human label for ISO 639-1/639-2 language codes used by M3U playlists. */
fun languageName(code: String, language: String): String {
    val raw = code.trim()
    if (raw.isBlank()) return ""
    val locale = when (language.take(2).lowercase()) {
        "en" -> Locale.ENGLISH
        "es" -> Locale.forLanguageTag("es")
        else -> Locale.ITALIAN
    }
    val parsed = if (raw.length == 2) {
        Locale(raw.lowercase())
    } else {
        Locale(raw.lowercase())
    }
    val display = runCatching { parsed.getDisplayLanguage(locale) }.getOrNull().orEmpty()
    return if (display.isBlank() || display.equals(raw, ignoreCase = true)) {
        raw.replaceFirstChar { it.uppercase(locale) }
    } else {
        display
    }
}

val COUNTRY_CODES: List<String> = Locale.getISOCountries().sorted()
