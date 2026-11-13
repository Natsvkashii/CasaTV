package it.casatv.tv.ui

import it.casatv.tv.data.NowProgram
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)
private val dateFormatterIt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val dateFormatterEn = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)
private val dateFormatterEs = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES"))

fun formatTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "--:--"
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(timeFormatter)
}

fun currentTimeText(): String = LocalTime.now().format(timeFormatter)

fun countryZoneId(countryCode: String?): ZoneId {
    if (countryCode.isNullOrBlank()) return ZoneId.systemDefault()
    return when (countryCode.uppercase(Locale.ROOT)) {
        "IT", "VA", "SM" -> ZoneId.of("Europe/Rome")
        "ES" -> ZoneId.of("Europe/Madrid")
        "FR" -> ZoneId.of("Europe/Paris")
        "DE" -> ZoneId.of("Europe/Berlin")
        "GB", "UK" -> ZoneId.of("Europe/London")
        "US" -> ZoneId.of("America/New_York")
        "EC" -> ZoneId.of("America/Guayaquil")
        "AR" -> ZoneId.of("America/Argentina/Buenos_Aires")
        "BR" -> ZoneId.of("America/Sao_Paulo")
        "MX" -> ZoneId.of("America/Mexico_City")
        "RU" -> ZoneId.of("Europe/Moscow")
        "IN" -> ZoneId.of("Asia/Kolkata")
        "JP" -> ZoneId.of("Asia/Tokyo")
        "CN" -> ZoneId.of("Asia/Shanghai")
        "AU" -> ZoneId.of("Australia/Sydney")
        "CH" -> ZoneId.of("Europe/Zurich")
        "PT" -> ZoneId.of("Europe/Lisbon")
        "NL" -> ZoneId.of("Europe/Amsterdam")
        "GR" -> ZoneId.of("Europe/Athens")
        else -> ZoneId.systemDefault()
    }
}

fun countryTimeText(countryCode: String?): String {
    return runCatching {
        LocalTime.now(countryZoneId(countryCode)).format(timeFormatter)
    }.getOrDefault(currentTimeText())
}

private fun languageCode(language: String): String {
    val code = language.lowercase(Locale.ROOT).take(2)
    return if (code == "en" || code == "es") code else "it"
}

fun currentDateText(language: String = "it"): String {
    val code = languageCode(language)
    val formatter = when (code) {
        "en" -> dateFormatterEn
        "es" -> dateFormatterEs
        else -> dateFormatterIt
    }
    val locale = when (code) {
        "en" -> Locale.ENGLISH
        "es" -> Locale("es", "ES")
        else -> Locale.ITALIAN
    }
    val value = LocalDate.now().format(formatter)
    return value.replaceFirstChar { it.titlecase(locale) }
}

fun greeting(language: String = "it"): String {
    val code = languageCode(language)
    return when (LocalTime.now().hour) {
        in 5..11 -> when (code) {
            "en" -> "Good morning"
            "es" -> "Buenos días"
            else -> "Buongiorno"
        }
        in 12..17 -> when (code) {
            "en" -> "Good afternoon"
            "es" -> "Buenas tardes"
            else -> "Buon pomeriggio"
        }
        else -> when (code) {
            "en" -> "Good evening"
            "es" -> "Buenas noches"
            else -> "Buonasera"
        }
    }
}

fun formatNumber(value: Int, language: String = "it"): String {
    val text = value.toString()
    val separator = if (languageCode(language) == "en") ',' else '.'
    val builder = StringBuilder()
    for ((index, char) in text.withIndex()) {
        if (index > 0 && (text.length - index) % 3 == 0) builder.append(separator)
        builder.append(char)
    }
    return builder.toString()
}

fun programProgress(program: NowProgram, now: Long = System.currentTimeMillis() / 1000): Float {
    if (program.start <= 0 || program.stop <= program.start) return 0f
    return ((now - program.start).toFloat() / (program.stop - program.start).toFloat())
        .coerceIn(0f, 1f)
}

fun programRemaining(program: NowProgram, now: Long = System.currentTimeMillis() / 1000): String {
    val remaining = max(0, program.stop - now)
    val minutes = (remaining + 59) / 60
    return "$minutes min"
}

/** "2 min fa" / "2 min ago" / "hace 2 min". */
fun relativeTime(epochSeconds: Long?, language: String = "it"): String {
    if (epochSeconds == null || epochSeconds <= 0) return "—"
    val code = languageCode(language)
    val delta = System.currentTimeMillis() / 1000 - epochSeconds
    if (delta < 0) return if (code == "en") "now" else if (code == "es") "ahora" else "ora"
    val minutes = delta / 60
    val hours = minutes / 60
    val days = hours / 24
    val (value, unit) = when {
        days >= 1 -> days to when (code) {
            "en" -> if (days == 1L) "day" else "days"
            "es" -> if (days == 1L) "día" else "días"
            else -> if (days == 1L) "giorno" else "giorni"
        }
        hours >= 1 -> hours to when (code) {
            "en" -> if (hours == 1L) "hour" else "hours"
            "es" -> if (hours == 1L) "hora" else "horas"
            else -> if (hours == 1L) "ora" else "ore"
        }
        minutes >= 1 -> minutes to when (code) {
            "en" -> if (minutes == 1L) "min" else "min"
            "es" -> "min"
            else -> "min"
        }
        else -> 0L to when (code) {
            "en" -> "now"
            "es" -> "now"
            else -> "ora"
        }
    }
    return when (code) {
        "en" -> if (value == 0L) "now" else "$value $unit ago"
        "es" -> if (value == 0L) "ahora" else "hace $value $unit"
        else -> if (value == 0L) "ora" else "$value $unit fa"
    }
}

fun initials(name: String): String {
    val cleaned = name.trim()
    if (cleaned.isEmpty()) return "TV"
    val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (words.size >= 2) {
        "${words[0].first()}${words[1].first()}".uppercase(Locale.ITALIAN)
    } else {
        cleaned.take(2).uppercase(Locale.ITALIAN)
    }
}
