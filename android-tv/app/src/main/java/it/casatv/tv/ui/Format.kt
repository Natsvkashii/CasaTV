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

private val COUNTRY_PRIMARY_TIMEZONES: Map<String, String> = mapOf(
    "AD" to "Europe/Andorra",
    "AE" to "Asia/Dubai",
    "AF" to "Asia/Kabul",
    "AG" to "America/Antigua",
    "AL" to "Europe/Tirane",
    "AM" to "Asia/Yerevan",
    "AO" to "Africa/Luanda",
    "AR" to "America/Argentina/Buenos_Aires",
    "AT" to "Europe/Vienna",
    "AU" to "Australia/Sydney",
    "AW" to "America/Aruba",
    "AZ" to "Asia/Baku",
    "BA" to "Europe/Sarajevo",
    "BB" to "America/Barbados",
    "BD" to "Asia/Dhaka",
    "BE" to "Europe/Brussels",
    "BF" to "Africa/Ouagadougou",
    "BG" to "Europe/Sofia",
    "BH" to "Asia/Bahrain",
    "BI" to "Africa/Bujumbura",
    "BJ" to "Africa/Porto-Novo",
    "BM" to "Atlantic/Bermuda",
    "BN" to "Asia/Brunei",
    "BO" to "America/La_Paz",
    "BR" to "America/Sao_Paulo",
    "BS" to "America/Nassau",
    "BT" to "Asia/Thimphu",
    "BW" to "Africa/Gaborone",
    "BY" to "Europe/Minsk",
    "BZ" to "America/Belize",
    "CA" to "America/Toronto",
    "CD" to "Africa/Kinshasa",
    "CF" to "Africa/Bangui",
    "CG" to "Africa/Brazzaville",
    "CH" to "Europe/Zurich",
    "CI" to "Africa/Abidjan",
    "CL" to "America/Santiago",
    "CM" to "Africa/Douala",
    "CN" to "Asia/Shanghai",
    "CO" to "America/Bogota",
    "CR" to "America/Costa_Rica",
    "CU" to "America/Havana",
    "CV" to "Atlantic/Cape_Verde",
    "CW" to "America/Curacao",
    "CY" to "Asia/Nicosia",
    "CZ" to "Europe/Prague",
    "DE" to "Europe/Berlin",
    "DJ" to "Africa/Djibouti",
    "DK" to "Europe/Copenhagen",
    "DM" to "America/Dominica",
    "DO" to "America/Santo_Domingo",
    "DZ" to "Africa/Algiers",
    "EC" to "America/Guayaquil",
    "EE" to "Europe/Tallinn",
    "EG" to "Africa/Cairo",
    "ER" to "Africa/Asmara",
    "ES" to "Europe/Madrid",
    "ET" to "Africa/Addis_Ababa",
    "FI" to "Europe/Helsinki",
    "FJ" to "Pacific/Fiji",
    "FO" to "Atlantic/Faroe",
    "FR" to "Europe/Paris",
    "GA" to "Africa/Libreville",
    "GB" to "Europe/London",
    "UK" to "Europe/London",
    "GD" to "America/Grenada",
    "GE" to "Asia/Tbilisi",
    "GF" to "America/Cayenne",
    "GH" to "Africa/Accra",
    "GI" to "Europe/Gibraltar",
    "GL" to "America/Godthab",
    "GM" to "Africa/Banjul",
    "GN" to "Africa/Conakry",
    "GP" to "America/Guadeloupe",
    "GQ" to "Africa/Malabo",
    "GR" to "Europe/Athens",
    "GT" to "America/Guatemala",
    "GU" to "Pacific/Guam",
    "GW" to "Africa/Bissau",
    "GY" to "America/Guyana",
    "HK" to "Asia/Hong_Kong",
    "HN" to "America/Tegucigalpa",
    "HR" to "Europe/Zagreb",
    "HT" to "America/Port-au-Prince",
    "HU" to "Europe/Budapest",
    "ID" to "Asia/Jakarta",
    "IE" to "Europe/Dublin",
    "IL" to "Asia/Jerusalem",
    "IN" to "Asia/Kolkata",
    "IQ" to "Asia/Baghdad",
    "IR" to "Asia/Tehran",
    "IS" to "Atlantic/Reykjavik",
    "IT" to "Europe/Rome",
    "JM" to "America/Jamaica",
    "JO" to "Asia/Amman",
    "JP" to "Asia/Tokyo",
    "KE" to "Africa/Nairobi",
    "KG" to "Asia/Bishkek",
    "KH" to "Asia/Phnom_Penh",
    "KM" to "Indian/Comoro",
    "KN" to "America/St_Kitts",
    "KP" to "Asia/Pyongyang",
    "KR" to "Asia/Seoul",
    "KW" to "Asia/Kuwait",
    "KY" to "America/Cayman",
    "KZ" to "Asia/Almaty",
    "LA" to "Asia/Vientiane",
    "LB" to "Asia/Beirut",
    "LC" to "America/St_Lucia",
    "LI" to "Europe/Vaduz",
    "LK" to "Asia/Colombo",
    "LR" to "Africa/Monrovia",
    "LS" to "Africa/Maseru",
    "LT" to "Europe/Vilnius",
    "LU" to "Europe/Luxembourg",
    "LV" to "Europe/Riga",
    "LY" to "Africa/Tripoli",
    "MA" to "Africa/Casablanca",
    "MC" to "Europe/Monaco",
    "MD" to "Europe/Chisinau",
    "ME" to "Europe/Podgorica",
    "MG" to "Indian/Antananarivo",
    "MK" to "Europe/Skopje",
    "ML" to "Africa/Bamako",
    "MM" to "Asia/Yangon",
    "MN" to "Asia/Ulaanbaatar",
    "MO" to "Asia/Macau",
    "MQ" to "America/Martinique",
    "MR" to "Africa/Nouakchott",
    "MT" to "Europe/Malta",
    "MU" to "Indian/Mauritius",
    "MV" to "Indian/Maldives",
    "MW" to "Africa/Blantyre",
    "MX" to "America/Mexico_City",
    "MY" to "Asia/Kuala_Lumpur",
    "MZ" to "Africa/Maputo",
    "NA" to "Africa/Windhoek",
    "NC" to "Pacific/Noumea",
    "NE" to "Africa/Niamey",
    "NG" to "Africa/Lagos",
    "NI" to "America/Managua",
    "NL" to "Europe/Amsterdam",
    "NO" to "Europe/Oslo",
    "NP" to "Asia/Kathmandu",
    "NZ" to "Pacific/Auckland",
    "OM" to "Asia/Muscat",
    "PA" to "America/Panama",
    "PE" to "America/Lima",
    "PF" to "Pacific/Tahiti",
    "PG" to "Pacific/Port_Moresby",
    "PH" to "Asia/Manila",
    "PK" to "Asia/Karachi",
    "PL" to "Europe/Warsaw",
    "PR" to "America/Puerto_Rico",
    "PS" to "Asia/Gaza",
    "PT" to "Europe/Lisbon",
    "PY" to "America/Asuncion",
    "QA" to "Asia/Qatar",
    "RO" to "Europe/Bucharest",
    "RS" to "Europe/Belgrade",
    "RU" to "Europe/Moscow",
    "RW" to "Africa/Kigali",
    "SA" to "Asia/Riyadh",
    "SD" to "Africa/Khartoum",
    "SE" to "Europe/Stockholm",
    "SG" to "Asia/Singapore",
    "SI" to "Europe/Ljubljana",
    "SK" to "Europe/Bratislava",
    "SL" to "Africa/Freetown",
    "SM" to "Europe/San_Marino",
    "SN" to "Africa/Dakar",
    "SO" to "Africa/Mogadishu",
    "SR" to "America/Paramaribo",
    "SS" to "Africa/Juba",
    "SV" to "America/El_Salvador",
    "SY" to "Asia/Damascus",
    "SZ" to "Africa/Mbabane",
    "TD" to "Africa/Ndjamena",
    "TG" to "Africa/Lome",
    "TH" to "Asia/Bangkok",
    "TJ" to "Asia/Dushanbe",
    "TL" to "Asia/Dili",
    "TM" to "Asia/Ashgabat",
    "TN" to "Africa/Tunis",
    "TR" to "Europe/Istanbul",
    "TT" to "America/Port_of_Spain",
    "TW" to "Asia/Taipei",
    "TZ" to "Africa/Dar_es_Salaam",
    "UA" to "Europe/Kiev",
    "UG" to "Africa/Kampala",
    "US" to "America/New_York",
    "UY" to "America/Montevideo",
    "UZ" to "Asia/Tashkent",
    "VA" to "Europe/Vatican",
    "VC" to "America/St_Vincent",
    "VE" to "America/Caracas",
    "VG" to "America/Tortola",
    "VI" to "America/St_Thomas",
    "VN" to "Asia/Ho_Chi_Minh",
    "WS" to "Pacific/Apia",
    "XK" to "Europe/Belgrade",
    "YE" to "Asia/Aden",
    "ZA" to "Africa/Johannesburg",
    "ZM" to "Africa/Lusaka",
    "ZW" to "Africa/Harare",
)

fun resolveCountryZoneId(countryCode: String?): ZoneId? {
    val raw = countryCode?.trim()?.uppercase(Locale.ROOT).orEmpty()
    if (raw.isEmpty()) return null
    val norm = if (raw == "UK") "GB" else raw
    val zoneName = COUNTRY_PRIMARY_TIMEZONES[norm]
        ?: COUNTRY_PRIMARY_TIMEZONES[raw]
        ?: if (norm.length == 2 && norm.all { it in 'A'..'Z' }) {
            runCatching { android.icu.util.TimeZone.getAvailableIDs(norm)?.firstOrNull() }.getOrNull()
        } else null
    return zoneName?.let { runCatching { ZoneId.of(it) }.getOrNull() }
}

fun countryZoneId(countryCode: String?): ZoneId =
    resolveCountryZoneId(countryCode) ?: ZoneId.systemDefault()

fun countryTimeTextOrNull(countryCode: String?): String? {
    val zone = resolveCountryZoneId(countryCode) ?: return null
    return runCatching { LocalTime.now(zone).format(timeFormatter) }.getOrNull()
}

fun countryTimeText(countryCode: String?): String =
    countryTimeTextOrNull(countryCode) ?: currentTimeText()

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
