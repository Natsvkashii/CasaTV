package it.casatv.mobile.ui

import it.casatv.mobile.data.MobileChannel
import java.text.Collator
import java.util.EnumMap
import java.util.Locale

data class MobileMixLanguageOption(
    val code: String,
    val titleIt: String,
    val titleEn: String,
    val titleEs: String,
    val aliases: Set<String>,
    val defaultCountries: Set<String> = emptySet(),
) {
    fun label(language: String): String = when (language.lowercase(Locale.ROOT).take(2)) {
        "en" -> titleEn
        "es" -> titleEs
        else -> titleIt
    }
}

data class MobileMixGenreOption(
    val id: String,
    val titleIt: String,
    val titleEn: String,
    val titleEs: String,
    val categoryKeywords: Set<String>,
    val nameKeywords: Set<String> = emptySet(),
) {
    fun label(language: String): String = when (language.lowercase(Locale.ROOT).take(2)) {
        "en" -> titleEn
        "es" -> titleEs
        else -> titleIt
    }
}

/** 37 curated global broadcast languages, sorted dynamically A–Z by active locale. */
val MOBILE_MIX_LANGUAGES: List<MobileMixLanguageOption> = listOf(
    MobileMixLanguageOption("sqi", "Albanese", "Albanian", "Albanés", setOf("sqi", "sq", "alb", "albanese", "albanian", "shqip"), setOf("AL", "XK")),
    MobileMixLanguageOption("ara", "Arabo", "Arabic", "Árabe", setOf("ara", "ar", "arabo", "arabic", "árabe"), setOf("AE", "BH", "DZ", "EG", "IQ", "JO", "KW", "LB", "LY", "MA", "OM", "PS", "QA", "SA", "SD", "SY", "TN", "YE", "MR")),
    MobileMixLanguageOption("bul", "Bulgaro", "Bulgarian", "Búlgaro", setOf("bul", "bg", "bulgaro", "bulgarian", "búlgaro"), setOf("BG")),
    MobileMixLanguageOption("ces", "Ceco", "Czech", "Checo", setOf("ces", "cze", "cs", "ceco", "czech", "checo"), setOf("CZ")),
    MobileMixLanguageOption("zho", "Cinese", "Chinese", "Chino", setOf("zho", "chi", "zh", "cmn", "yue", "cinese", "chinese", "chino"), setOf("CN", "TW", "HK", "MO")),
    MobileMixLanguageOption("kor", "Coreano", "Korean", "Coreano", setOf("kor", "ko", "coreano", "korean"), setOf("KR", "KP")),
    MobileMixLanguageOption("hrv", "Croato", "Croatian", "Croata", setOf("hrv", "hr", "scr", "croato", "croatian", "croata"), setOf("HR")),
    MobileMixLanguageOption("dan", "Danese", "Danish", "Danés", setOf("dan", "da", "danese", "danish", "danés"), setOf("DK", "FO", "GL")),
    MobileMixLanguageOption("heb", "Ebraico", "Hebrew", "Hebreo", setOf("heb", "he", "iw", "ebraico", "hebrew", "hebreo"), setOf("IL")),
    MobileMixLanguageOption("tgl", "Filippino / Tagalog", "Filipino / Tagalog", "Filipino / Tagalo", setOf("tgl", "tl", "fil", "tagalog", "filipino", "filippino"), setOf("PH")),
    MobileMixLanguageOption("fin", "Finlandese", "Finnish", "Finés", setOf("fin", "fi", "finlandese", "finnish", "finés"), setOf("FI")),
    MobileMixLanguageOption("fra", "Francese", "French", "Francés", setOf("fra", "fre", "fr", "francese", "french", "francés"), setOf("FR", "MC", "BF", "CI", "CM", "CD", "CG", "GA", "GN", "ML", "NE", "SN", "TG", "PF", "NC", "GF", "GP", "MQ", "HT")),
    MobileMixLanguageOption("kat", "Georgiano", "Georgian", "Georgiano", setOf("kat", "geo", "ka", "georgiano", "georgian"), setOf("GE")),
    MobileMixLanguageOption("jpn", "Giapponese", "Japanese", "Japonés", setOf("jpn", "ja", "giapponese", "japanese", "japonés"), setOf("JP")),
    MobileMixLanguageOption("ell", "Greco", "Greek", "Griego", setOf("ell", "gre", "el", "modern greek", "modern greek (1453-)", "greco", "greek", "griego"), setOf("GR", "CY")),
    MobileMixLanguageOption("hin", "Hindi", "Hindi", "Hindi", setOf("hin", "hi", "hindi"), setOf("IN")),
    MobileMixLanguageOption("ind", "Indonesiano", "Indonesian", "Indonesio", setOf("ind", "id", "in", "indonesiano", "indonesian", "indonesio"), setOf("ID")),
    MobileMixLanguageOption("eng", "Inglese", "English", "Inglés", setOf("eng", "en", "inglese", "english", "inglés"), setOf("US", "GB", "UK", "CA", "AU", "NZ", "IE", "JM", "BS", "BB", "TT", "BZ", "GY", "NG", "GH", "KE", "ZA", "SG", "PR")),
    MobileMixLanguageOption("ita", "Italiano", "Italian", "Italiano", setOf("ita", "it", "italiano", "italian"), setOf("IT", "SM", "VA")),
    MobileMixLanguageOption("nor", "Norvegese", "Norwegian", "Noruego", setOf("nor", "no", "nob", "nno", "norvegese", "norwegian", "noruego"), setOf("NO")),
    MobileMixLanguageOption("nld", "Olandese", "Dutch", "Neerlandés", setOf("nld", "dut", "nl", "olandese", "dutch", "neerlandés", "flemish"), setOf("NL", "AW", "CW", "SR")),
    MobileMixLanguageOption("fas", "Persiano", "Persian", "Persa", setOf("fas", "per", "fa", "persiano", "persian", "persa", "farsi"), setOf("IR", "AF", "TJ")),
    MobileMixLanguageOption("pol", "Polacco", "Polish", "Polaco", setOf("pol", "pl", "polacco", "polish", "polaco"), setOf("PL")),
    MobileMixLanguageOption("por", "Portoghese", "Portuguese", "Portugués", setOf("por", "pt", "portoghese", "portuguese", "portugués"), setOf("PT", "BR", "AO", "MZ", "CV", "GW", "TL")),
    MobileMixLanguageOption("ron", "Rumeno", "Romanian", "Rumano", setOf("ron", "rum", "ro", "rumeno", "romanian", "rumano"), setOf("RO", "MD")),
    MobileMixLanguageOption("rus", "Russo", "Russian", "Ruso", setOf("rus", "ru", "russo", "russian", "ruso"), setOf("RU", "BY", "KZ", "KG")),
    MobileMixLanguageOption("srp", "Serbo", "Serbian", "Serbio", setOf("srp", "sr", "scc", "serbo", "serbian", "serbio"), setOf("RS", "ME", "BA")),
    MobileMixLanguageOption("slk", "Slovacco", "Slovak", "Eslovaco", setOf("slk", "slo", "sk", "slovacco", "slovak", "eslovaco"), setOf("SK")),
    MobileMixLanguageOption("spa", "Spagnolo", "Spanish", "Español", setOf("spa", "es", "spagnolo", "spanish", "español", "castellano"), setOf("ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "GT", "CU", "BO", "DO", "HN", "PY", "SV", "NI", "CR", "PA", "UY", "PR")),
    MobileMixLanguageOption("swe", "Svedese", "Swedish", "Sueco", setOf("swe", "sv", "svedese", "swedish", "sueco"), setOf("SE")),
    MobileMixLanguageOption("deu", "Tedesco", "German", "Alemán", setOf("deu", "ger", "de", "tedesco", "german", "alemán"), setOf("DE", "AT", "CH", "LI", "LU")),
    MobileMixLanguageOption("tha", "Thailandese", "Thai", "Tailandés", setOf("tha", "th", "thai", "thailandese", "tailandés"), setOf("TH")),
    MobileMixLanguageOption("tur", "Turco", "Turkish", "Turco", setOf("tur", "tr", "turco", "turkish"), setOf("TR")),
    MobileMixLanguageOption("ukr", "Ucraino", "Ukrainian", "Ucraniano", setOf("ukr", "uk", "ucraino", "ukrainian", "ucraniano"), setOf("UA")),
    MobileMixLanguageOption("hun", "Ungherese", "Hungarian", "Húngaro", setOf("hun", "hu", "ungherese", "hungarian", "húngaro"), setOf("HU")),
    MobileMixLanguageOption("urd", "Urdu", "Urdu", "Urdu", setOf("urd", "ur", "urdu"), setOf("PK")),
    MobileMixLanguageOption("vie", "Vietnamita", "Vietnamese", "Vietnamita", setOf("vie", "vi", "vietnamita", "vietnamese"), setOf("VN")),
)

/** 29 curated genres/categories, sorted dynamically A–Z by active locale. */
val MOBILE_MIX_GENRES: List<MobileMixGenreOption> = listOf(
    MobileMixGenreOption("animation", "Animazione", "Animation", "Animación", setOf("animation", "animazione", "animación", "cartoon", "cartoons"), setOf("cartoon")),
    MobileMixGenreOption("anime", "Anime", "Anime", "Anime", setOf("anime"), setOf("anime", "crunchyroll")),
    MobileMixGenreOption("auto", "Auto & Motori", "Auto & Motors", "Motor", setOf("auto", "automotive", "motor", "motori", "motorsport", "racing"), setOf("motorsport", "top gear")),
    MobileMixGenreOption("kids", "Bambini", "Kids", "Infantil", setOf("kids", "child", "children", "bambini", "infantiles", "infantil", "junior"), setOf("nickelodeon", "disney", "boing", "k2", "frisbee", "super!", "rai yoyo", "rai gulp", "baby tv")),
    MobileMixGenreOption("movies", "Cinema & Film", "Movies & Film", "Cine y Películas", setOf("movie", "movies", "cinema", "cine", "películas", "peliculas", "film", "vod"), setOf("cinema", "movie", "movies", "hollywood", "paramount", "tcm")),
    MobileMixGenreOption("classic", "Classici & Retrò", "Classic & Retro", "Clásicos", setOf("classic", "classics", "classici", "retro", "clásicos"), setOf("classic")),
    MobileMixGenreOption("comedy", "Commedia", "Comedy", "Comedia", setOf("comedy", "commedia", "comedia", "humor", "humour"), setOf("comedy")),
    MobileMixGenreOption("cooking", "Cucina & Food", "Cooking & Food", "Cocina y Gastronomía", setOf("cooking", "cucina", "cocina", "food", "culinary", "gastronomia"), setOf("food network", "gambero rosso", "alma tv")),
    MobileMixGenreOption("culture", "Cultura & Arte", "Culture & Arts", "Cultura y Arte", setOf("culture", "cultura", "arts", "art", "arte", "history"), setOf("arte", "rai 5", "rai storia")),
    MobileMixGenreOption("documentary", "Documentari", "Documentary", "Documentales", setOf("documentary", "documentaries", "documentari", "documentales", "doc"), setOf("discovery", "national geographic", "nat geo", "history", "focus")),
    MobileMixGenreOption("business", "Economia & Finanza", "Business & Finance", "Economía y Finanzas", setOf("business", "economia", "finance", "finanza", "financial", "economy"), setOf("bloomberg", "cnbc", "class cnbc")),
    MobileMixGenreOption("education", "Educazione", "Education", "Educación", setOf("education", "educazione", "educación", "educational", "learning", "scuola"), setOf("rai scuola", "edu")),
    MobileMixGenreOption("family", "Famiglia", "Family", "Familia", setOf("family", "famiglia", "familia")),
    MobileMixGenreOption("general", "Generali", "General", "General", setOf("general", "generali", "generale", "undefined", "eventuales", "variedades", "variety")),
    MobileMixGenreOption("entertainment", "Intrattenimento", "Entertainment", "Entretenimiento", setOf("entertainment", "intrattenimento", "entretenimiento", "show", "doramas"), setOf("tv8", "nove", "real time", "dmax")),
    MobileMixGenreOption("legislative", "Istituzionale", "Legislative & Civic", "Institucional", setOf("legislative", "parliament", "government", "istituzionale", "civic", "public"), setOf("senato", "camera dei deputati", "parliament", "c-span")),
    MobileMixGenreOption("lifestyle", "Lifestyle", "Lifestyle", "Estilo de vida", setOf("lifestyle", "home", "living"), setOf("real time", "hgtv")),
    MobileMixGenreOption("local", "Locali & Regionali", "Local & Regional", "Locales y Regionales", setOf("local", "locali", "regional", "regionali", "autonómicas", "pluto", "stirr", "fast")),
    MobileMixGenreOption("weather", "Meteo", "Weather", "Clima y Tiempo", setOf("weather", "meteo", "tiempo", "clima"), setOf("weather", "meteo")),
    MobileMixGenreOption("fashion", "Moda", "Fashion", "Moda", setOf("fashion", "moda", "style"), setOf("fashion tv", "fashiontv")),
    MobileMixGenreOption("music", "Musica", "Music", "Música", setOf("music", "musica", "música", "musicales", "mtv", "vh1"), setOf("mtv", "vh1", "kiss", "vevo", "trace")),
    MobileMixGenreOption("outdoor", "Natura & Outdoor", "Nature & Outdoor", "Naturaleza", setOf("outdoor", "nature", "natura", "wildlife", "fishing", "hunting")),
    MobileMixGenreOption("news", "News & Informazione", "News & Information", "Noticias e Informativos", setOf("news", "informativos", "informazione", "noticias", "notizie", "aktuell"), setOf("news", "cnn", "bbc", "euronews", "rainews", "tgcom", "sky tg24", "al jazeera", "france 24", "dw", "nhk world")),
    MobileMixGenreOption("religious", "Religione", "Religious", "Religión", setOf("religious", "religione", "religión", "religiosos", "christian", "islamic", "catholic", "gospel"), setOf("tv2000", "ewtn", "padre pio", "telepace")),
    MobileMixGenreOption("science", "Scienza & Tech", "Science & Tech", "Ciencia y Tecnología", setOf("science", "scienza", "ciencia", "tech", "technology", "space"), setOf("nasa", "science")),
    MobileMixGenreOption("series", "Serie TV & Drama", "Series & Drama", "Series y Drama", setOf("series", "serie", "drama", "novelas", "telenovelas", "doramas"), setOf("series")),
    MobileMixGenreOption("shop", "Shopping", "Shopping", "Compras", setOf("shop", "shopping", "teleshop", "commerce"), setOf("qvc", "hsn", "jtv")),
    MobileMixGenreOption("sports", "Sport", "Sports", "Deportes", setOf("sport", "sports", "deporte", "deportes", "deportivos", "football", "soccer", "espn", "dazn"), setOf("espn", "fox sports", "sky sport", "eurosport", "dazn", "sportitalia", "rai sport", "beinsports", "tudn")),
    MobileMixGenreOption("travel", "Viaggi", "Travel", "Viajes", setOf("travel", "viaggi", "viajes", "tourism", "turismo"), setOf("travel")),
)

private val MOBILE_LANG_BY_CODE = MOBILE_MIX_LANGUAGES.associateBy { it.code }
private val MOBILE_GENRE_BY_ID = MOBILE_MIX_GENRES.associateBy { it.id }

fun sortedMobileMixLanguages(uiLanguage: String): List<MobileMixLanguageOption> {
    val locale = Locale.forLanguageTag(uiLanguage.ifBlank { "it" })
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return MOBILE_MIX_LANGUAGES.sortedWith { a, b -> collator.compare(a.label(uiLanguage), b.label(uiLanguage)) }
}

fun sortedMobileMixGenres(uiLanguage: String): List<MobileMixGenreOption> {
    val locale = Locale.forLanguageTag(uiLanguage.ifBlank { "it" })
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return MOBILE_MIX_GENRES.sortedWith { a, b -> collator.compare(a.label(uiLanguage), b.label(uiLanguage)) }
}

fun matchesMobileMixLanguages(channel: MobileChannel, selectedCodes: Set<String>): Boolean {
    if (selectedCodes.isEmpty()) return true
    val selected = selectedCodes.mapNotNull { MOBILE_LANG_BY_CODE[it] }
    if (selected.isEmpty()) return true

    val chLangTokens = channel.language.orEmpty()
        .lowercase(Locale.ROOT)
        .split(';', ',', '/', '|')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (chLangTokens.isNotEmpty()) {
        return selected.any { opt ->
            chLangTokens.any { token ->
                token in opt.aliases || opt.aliases.any { alias -> alias.length > 2 && token.contains(alias) }
            }
        }
    }

    val rawCountry = channel.country.orEmpty().trim().uppercase(Locale.ROOT)
    val normCountry = if (rawCountry == "UK") "GB" else rawCountry
    if (normCountry.length != 2) return false
    return selected.any { opt -> normCountry in opt.defaultCountries }
}

private val MOBILE_CAT_SPLIT_RE = Regex("""[;/,|]+""")
private val MOBILE_NON_ALNUM_RE = Regex("""[^a-z0-9]+""")

fun matchesMobileMixGenres(channel: MobileChannel, selectedGenreIds: Set<String>): Boolean {
    if (selectedGenreIds.isEmpty()) return true
    val selected = selectedGenreIds.mapNotNull { MOBILE_GENRE_BY_ID[it] }
    if (selected.isEmpty()) return true

    val catTokens = channel.category.lowercase(Locale.ROOT)
        .split(MOBILE_CAT_SPLIT_RE)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val nameLower = " ${channel.name.lowercase(Locale.ROOT).replace(MOBILE_NON_ALNUM_RE, " ").trim()} "

    return selected.any { genre ->
        catTokens.any { tok ->
            tok in genre.categoryKeywords || genre.categoryKeywords.any { kw -> kw.length >= 4 && tok.contains(kw) }
        } || genre.nameKeywords.any { kw ->
            nameLower.contains(" $kw ")
        }
    }
}

fun filterMobileMixPool(
    channels: List<MobileChannel>,
    includeAudioOnly: Boolean,
    selectedLanguages: Set<String>,
    selectedGenres: Set<String>,
): List<MobileChannel> = channels.filter { ch ->
    (includeAudioOnly || !isLikelyAudioOnlyChannel(ch)) &&
        matchesMobileMixLanguages(ch, selectedLanguages) &&
        matchesMobileMixGenres(ch, selectedGenres)
}

fun computeMobileFilteredMixRegionCounts(
    channels: List<MobileChannel>,
    includeAudioOnly: Boolean,
    selectedLanguages: Set<String>,
    selectedGenres: Set<String>,
): Map<MixRegion, Int> {
    val map = EnumMap<MixRegion, Int>(MixRegion::class.java)
    channels.forEach { ch ->
        if (!includeAudioOnly && isLikelyAudioOnlyChannel(ch)) return@forEach
        if (!matchesMobileMixLanguages(ch, selectedLanguages)) return@forEach
        if (!matchesMobileMixGenres(ch, selectedGenres)) return@forEach
        val region = resolveMixRegion(ch.country) ?: return@forEach
        map[region] = (map[region] ?: 0) + 1
    }
    return map
}
