package it.casatv.mobile.ui

import it.casatv.mobile.data.MobileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val MOBILE_AUDIO_EXT_REGEX =
    Regex("""\.(mp3|aac|aacp|ogg|oga|opus|flac|m4a|wav|wma|pls)(\?|#|$)""", RegexOption.IGNORE_CASE)

private val MOBILE_AUDIO_ENDPOINT_REGEX =
    Regex("""(/stream(\?|/|$)|;stream|\.(mp3|aac|ogg|opus|flac|m4a)(\?|$))""", RegexOption.IGNORE_CASE)

private val MOBILE_VIDEO_QUALITY_SET = setOf("HD", "FHD", "4K", "UHD", "1080P", "720P")

private val MOBILE_VIDEO_NAME_HINT_REGEX = Regex(
    """(?i)(^|[\s\-_|/(\[.])(tv|television|televisión|televisió|tele|bfm|vision|visió|video|vídeo|canal|channel|radiotv|radio-tv|visual\s*radio|radiovision|hd|uhd|fhd|4k|1080p|720p|576p|480p|360p|240p)([\s\-_|/)\].]|$)""",
)

private val MOBILE_RADIO_NAME_HINT_REGEX = Regex(
    """(?i)(^|[\s\-_|/(\[])(fm|am|radio|rádio|rádió|irratia|dab\+?|webradio)([\s\-_|/)\].]|$)""",
)

fun isLikelyAudioOnlyChannel(channel: MobileChannel): Boolean {
    val urlLower = channel.streamUrl.trim().lowercase(Locale.ROOT)
    if (MOBILE_AUDIO_EXT_REGEX.containsMatchIn(urlLower)) return true
    val isContainerManifest = urlLower.contains(".m3u8") || urlLower.contains(".mpd") || urlLower.contains(".ts")
    if (!isContainerManifest && MOBILE_AUDIO_ENDPOINT_REGEX.containsMatchIn(urlLower)) return true

    val catLower = channel.category.lowercase(Locale.ROOT)
    if ((catLower.contains("radio") || catLower.contains("irratia")) &&
        !catLower.contains("tv") &&
        !catLower.contains("video") &&
        !catLower.contains("tele")
    ) {
        return true
    }

    val qualityUpper = channel.quality.trim().uppercase(Locale.ROOT)
    val hasVideoHint = qualityUpper in MOBILE_VIDEO_QUALITY_SET || MOBILE_VIDEO_NAME_HINT_REGEX.containsMatchIn(channel.name)
    return !hasVideoHint && MOBILE_RADIO_NAME_HINT_REGEX.containsMatchIn(channel.name)
}

fun channelInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "TV"
        words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase(Locale.ROOT)
        else -> words[0].take(2).uppercase(Locale.ROOT)
    }
}

fun resolveMobileCountryName(code: String?, fallback: String, language: String): String {
    val cleanFallback = fallback.trim()
    val normRaw = code.orEmpty().trim().uppercase(Locale.ROOT)
    val norm = if (normRaw == "UK") "GB" else normRaw
    if (norm.length != 2 || norm.any { it !in 'A'..'Z' }) return cleanFallback
    val locale = Locale.forLanguageTag(language.ifBlank { "en" })
    val localized = Locale(locale.language.ifBlank { "en" }, norm).getDisplayCountry(locale)
    return localized.takeIf { it.isNotBlank() } ?: cleanFallback
}

fun resolveMobileCountryFlag(code: String?, fallbackFlag: String): String = ""

private val MOBILE_COUNTRY_TIMEZONES: Map<String, String> = mapOf(
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

fun formatLocalTime(): String =
    SimpleDateFormat("HH:mm", Locale.ROOT).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date())

fun formatCountryTimeOrNull(countryCode: String?): String? {
    val raw = countryCode?.trim()?.uppercase(Locale.ROOT).orEmpty()
    if (raw.isEmpty()) return null
    val norm = if (raw == "UK") "GB" else raw
    val zoneId = MOBILE_COUNTRY_TIMEZONES[norm]
        ?: MOBILE_COUNTRY_TIMEZONES[raw]
        ?: if (norm.length == 2 && norm.all { it in 'A'..'Z' }) {
            runCatching { android.icu.util.TimeZone.getAvailableIDs(norm)?.firstOrNull() }.getOrNull()
        } else null
    if (zoneId.isNullOrBlank()) return null
    val tz = TimeZone.getTimeZone(zoneId)
    if (tz.id == "GMT" && zoneId != "GMT" && zoneId != "UTC" && !zoneId.startsWith("Africa/") && !zoneId.startsWith("Atlantic/")) {
        return null
    }
    return SimpleDateFormat("HH:mm", Locale.ROOT).apply {
        timeZone = tz
    }.format(Date())
}
