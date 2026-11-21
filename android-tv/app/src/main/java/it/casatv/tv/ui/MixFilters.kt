package it.casatv.tv.ui

import it.casatv.tv.data.Channel
import java.text.Collator
import java.util.Locale

data class MixLanguageOption(
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

data class MixGenreOption(
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
val MIX_LANGUAGES: List<MixLanguageOption> = listOf(
    MixLanguageOption("sqi", "Albanese", "Albanian", "Albanés", setOf("sqi", "sq", "alb", "albanese", "albanian", "shqip"), setOf("AL", "XK")),
    MixLanguageOption("ara", "Arabo", "Arabic", "Árabe", setOf("ara", "ar", "arabo", "arabic", "árabe"), setOf("AE", "BH", "DZ", "EG", "IQ", "JO", "KW", "LB", "LY", "MA", "OM", "PS", "QA", "SA", "SD", "SY", "TN", "YE", "MR")),
    MixLanguageOption("bul", "Bulgaro", "Bulgarian", "Búlgaro", setOf("bul", "bg", "bulgaro", "bulgarian", "búlgaro"), setOf("BG")),
    MixLanguageOption("ces", "Ceco", "Czech", "Checo", setOf("ces", "cze", "cs", "ceco", "czech", "checo"), setOf("CZ")),
    MixLanguageOption("zho", "Cinese", "Chinese", "Chino", setOf("zho", "chi", "zh", "cmn", "yue", "cinese", "chinese", "chino"), setOf("CN", "TW", "HK", "MO")),
    MixLanguageOption("kor", "Coreano", "Korean", "Coreano", setOf("kor", "ko", "coreano", "korean"), setOf("KR", "KP")),
    MixLanguageOption("hrv", "Croato", "Croatian", "Croata", setOf("hrv", "hr", "scr", "croato", "croatian", "croata"), setOf("HR")),
    MixLanguageOption("dan", "Danese", "Danish", "Danés", setOf("dan", "da", "danese", "danish", "danés"), setOf("DK", "FO", "GL")),
    MixLanguageOption("heb", "Ebraico", "Hebrew", "Hebreo", setOf("heb", "he", "iw", "ebraico", "hebrew", "hebreo"), setOf("IL")),
    MixLanguageOption("tgl", "Filippino / Tagalog", "Filipino / Tagalog", "Filipino / Tagalo", setOf("tgl", "tl", "fil", "tagalog", "filipino", "filippino"), setOf("PH")),
    MixLanguageOption("fin", "Finlandese", "Finnish", "Finés", setOf("fin", "fi", "finlandese", "finnish", "finés"), setOf("FI")),
    MixLanguageOption("fra", "Francese", "French", "Francés", setOf("fra", "fre", "fr", "francese", "french", "francés"), setOf("FR", "MC", "BF", "CI", "CM", "CD", "CG", "GA", "GN", "ML", "NE", "SN", "TG", "PF", "NC", "GF", "GP", "MQ", "HT")),
    MixLanguageOption("kat", "Georgiano", "Georgian", "Georgiano", setOf("kat", "geo", "ka", "georgiano", "georgian"), setOf("GE")),
    MixLanguageOption("jpn", "Giapponese", "Japanese", "Japonés", setOf("jpn", "ja", "giapponese", "japanese", "japonés"), setOf("JP")),
    MixLanguageOption("ell", "Greco", "Greek", "Griego", setOf("ell", "gre", "el", "modern greek", "modern greek (1453-)", "greco", "greek", "griego"), setOf("GR", "CY")),
    MixLanguageOption("hin", "Hindi", "Hindi", "Hindi", setOf("hin", "hi", "hindi"), setOf("IN")),
    MixLanguageOption("ind", "Indonesiano", "Indonesian", "Indonesio", setOf("ind", "id", "in", "indonesiano", "indonesian", "indonesio"), setOf("ID")),
    MixLanguageOption("eng", "Inglese", "English", "Inglés", setOf("eng", "en", "inglese", "english", "inglés"), setOf("US", "GB", "UK", "CA", "AU", "NZ", "IE", "JM", "BS", "BB", "TT", "BZ", "GY", "NG", "GH", "KE", "ZA", "SG", "JM", "PR")),
    MixLanguageOption("ita", "Italiano", "Italian", "Italiano", setOf("ita", "it", "italiano", "italian"), setOf("IT", "SM", "VA")),
    MixLanguageOption("nor", "Norvegese", "Norwegian", "Noruego", setOf("nor", "no", "nob", "nno", "norvegese", "norwegian", "noruego"), setOf("NO")),
    MixLanguageOption("nld", "Olandese", "Dutch", "Neerlandés", setOf("nld", "dut", "nl", "olandese", "dutch", "neerlandés", "flemish"), setOf("NL", "AW", "CW", "SR")),
    MixLanguageOption("fas", "Persiano", "Persian", "Persa", setOf("fas", "per", "fa", "persiano", "persian", "persa", "farsi"), setOf("IR", "AF", "TJ")),
    MixLanguageOption("pol", "Polacco", "Polish", "Polaco", setOf("pol", "pl", "polacco", "polish", "polaco"), setOf("PL")),
    MixLanguageOption("por", "Portoghese", "Portuguese", "Portugués", setOf("por", "pt", "portoghese", "portuguese", "portugués"), setOf("PT", "BR", "AO", "MZ", "CV", "GW", "TL")),
    MixLanguageOption("ron", "Rumeno", "Romanian", "Rumano", setOf("ron", "rum", "ro", "rumeno", "romanian", "rumano"), setOf("RO", "MD")),
    MixLanguageOption("rus", "Russo", "Russian", "Ruso", setOf("rus", "ru", "russo", "russian", "ruso"), setOf("RU", "BY", "KZ", "KG")),
    MixLanguageOption("srp", "Serbo", "Serbian", "Serbio", setOf("srp", "sr", "scc", "serbo", "serbian", "serbio"), setOf("RS", "ME", "BA")),
    MixLanguageOption("slk", "Slovacco", "Slovak", "Eslovaco", setOf("slk", "slo", "sk", "slovacco", "slovak", "eslovaco"), setOf("SK")),
    MixLanguageOption("spa", "Spagnolo", "Spanish", "Español", setOf("spa", "es", "spagnolo", "spanish", "español", "castellano"), setOf("ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "GT", "CU", "BO", "DO", "HN", "PY", "SV", "NI", "CR", "PA", "UY", "PR")),
    MixLanguageOption("swe", "Svedese", "Swedish", "Sueco", setOf("swe", "sv", "svedese", "swedish", "sueco"), setOf("SE")),
    MixLanguageOption("deu", "Tedesco", "German", "Alemán", setOf("deu", "ger", "de", "tedesco", "german", "alemán"), setOf("DE", "AT", "CH", "LI", "LU")),
    MixLanguageOption("tha", "Thailandese", "Thai", "Tailandés", setOf("tha", "th", "thai", "thailandese", "tailandés"), setOf("TH")),
    MixLanguageOption("tur", "Turco", "Turkish", "Turco", setOf("tur", "tr", "turco", "turkish"), setOf("TR")),
    MixLanguageOption("ukr", "Ucraino", "Ukrainian", "Ucraniano", setOf("ukr", "uk", "ucraino", "ukrainian", "ucraniano"), setOf("UA")),
    MixLanguageOption("hun", "Ungherese", "Hungarian", "Húngaro", setOf("hun", "hu", "ungherese", "hungarian", "húngaro"), setOf("HU")),
    MixLanguageOption("urd", "Urdu", "Urdu", "Urdu", setOf("urd", "ur", "urdu"), setOf("PK")),
    MixLanguageOption("vie", "Vietnamita", "Vietnamese", "Vietnamita", setOf("vie", "vi", "vietnamita", "vietnamese"), setOf("VN")),
)

/** 29 curated genres/categories, sorted dynamically A–Z by active locale. */
val MIX_GENRES: List<MixGenreOption> = listOf(
    MixGenreOption("animation", "Animazione", "Animation", "Animación", setOf("animation", "animazione", "animación", "cartoon", "cartoons"), setOf("cartoon")),
    MixGenreOption("anime", "Anime", "Anime", "Anime", setOf("anime"), setOf("anime", "crunchyroll")),
    MixGenreOption("auto", "Auto & Motori", "Auto & Motors", "Motor", setOf("auto", "automotive", "motor", "motori", "motorsport", "racing"), setOf("motorsport", "top gear")),
    MixGenreOption("kids", "Bambini", "Kids", "Infantil", setOf("kids", "child", "children", "bambini", "infantiles", "infantil", "junior"), setOf("nickelodeon", "disney", "boing", "k2", "frisbee", "super!", "rai yoyo", "rai gulp", "baby tv")),
    MixGenreOption("movies", "Cinema & Film", "Movies & Film", "Cine y Películas", setOf("movie", "movies", "cinema", "cine", "películas", "peliculas", "film", "vod"), setOf("cinema", "movie", "movies", "hollywood", "paramount", "tcm")),
    MixGenreOption("classic", "Classici & Retrò", "Classic & Retro", "Clásicos", setOf("classic", "classics", "classici", "retro", "clásicos"), setOf("classic")),
    MixGenreOption("comedy", "Commedia", "Comedy", "Comedia", setOf("comedy", "commedia", "comedia", "humor", "humour"), setOf("comedy")),
    MixGenreOption("cooking", "Cucina & Food", "Cooking & Food", "Cocina y Gastronomía", setOf("cooking", "cucina", "cocina", "food", "culinary", "gastronomia"), setOf("food network", "gambero rosso", "alma tv")),
    MixGenreOption("culture", "Cultura & Arte", "Culture & Arts", "Cultura y Arte", setOf("culture", "cultura", "arts", "art", "arte", "history"), setOf("arte", "rai 5", "rai storia")),
    MixGenreOption("documentary", "Documentari", "Documentary", "Documentales", setOf("documentary", "documentaries", "documentari", "documentales", "doc"), setOf("discovery", "national geographic", "nat geo", "history", "focus")),
    MixGenreOption("business", "Economia & Finanza", "Business & Finance", "Economía y Finanzas", setOf("business", "economia", "finance", "finanza", "financial", "economy"), setOf("bloomberg", "cnbc", "class cnbc")),
    MixGenreOption("education", "Educazione", "Education", "Educación", setOf("education", "educazione", "educación", "educational", "learning", "scuola"), setOf("rai scuola", "edu")),
    MixGenreOption("family", "Famiglia", "Family", "Familia", setOf("family", "famiglia", "familia")),
    MixGenreOption("general", "Generali", "General", "General", setOf("general", "generali", "generale", "undefined", "eventuales", "variedades", "variety")),
    MixGenreOption("entertainment", "Intrattenimento", "Entertainment", "Entretenimiento", setOf("entertainment", "intrattenimento", "entretenimiento", "show", "doramas"), setOf("tv8", "nove", "real time", "dmax")),
    MixGenreOption("legislative", "Istituzionale", "Legislative & Civic", "Institucional", setOf("legislative", "parliament", "government", "istituzionale", "civic", "public"), setOf("senato", "camera dei deputati", "parliament", "c-span")),
    MixGenreOption("lifestyle", "Lifestyle", "Lifestyle", "Estilo de vida", setOf("lifestyle", "home", "living"), setOf("real time", "hgtv")),
    MixGenreOption("local", "Locali & Regionali", "Local & Regional", "Locales y Regionales", setOf("local", "locali", "regional", "regionali", "autonómicas", "pluto", "stirr", "fast")),
    MixGenreOption("weather", "Meteo", "Weather", "Clima y Tiempo", setOf("weather", "meteo", "tiempo", "clima"), setOf("weather", "meteo")),
    MixGenreOption("fashion", "Moda", "Fashion", "Moda", setOf("fashion", "moda", "style"), setOf("fashion tv", "fashiontv")),
    MixGenreOption("music", "Musica", "Music", "Música", setOf("music", "musica", "música", "musicales", "mtv", "vh1"), setOf("mtv", "vh1", "kiss", "vevo", "trace")),
    MixGenreOption("outdoor", "Natura & Outdoor", "Nature & Outdoor", "Naturaleza", setOf("outdoor", "nature", "natura", "wildlife", "fishing", "hunting")),
    MixGenreOption("news", "News & Informazione", "News & Information", "Noticias e Informativos", setOf("news", "informativos", "informazione", "noticias", "notizie", "aktuell"), setOf("news", "cnn", "bbc", "euronews", "rainews", "tgcom", "sky tg24", "al jazeera", "france 24", "dw", "nhk world")),
    MixGenreOption("religious", "Religione", "Religious", "Religión", setOf("religious", "religione", "religión", "religiosos", "christian", "islamic", "catholic", "gospel"), setOf("tv2000", "ewtn", "padre pio", "telepace")),
    MixGenreOption("science", "Scienza & Tech", "Science & Tech", "Ciencia y Tecnología", setOf("science", "scienza", "ciencia", "tech", "technology", "space"), setOf("nasa", "science")),
    MixGenreOption("series", "Serie TV & Drama", "Series & Drama", "Series y Drama", setOf("series", "serie", "drama", "novelas", "telenovelas", "doramas"), setOf("series")),
    MixGenreOption("shop", "Shopping", "Shopping", "Compras", setOf("shop", "shopping", "teleshop", "commerce"), setOf("qvc", "hsn", "jtv")),
    MixGenreOption("sports", "Sport", "Sports", "Deportes", setOf("sport", "sports", "deporte", "deportes", "deportivos", "football", "soccer", "espn", "dazn"), setOf("espn", "fox sports", "sky sport", "eurosport", "dazn", "sportitalia", "rai sport", "beinsports", "tudn")),
    MixGenreOption("travel", "Viaggi", "Travel", "Viajes", setOf("travel", "viaggi", "viajes", "tourism", "turismo"), setOf("travel")),
)

private val LANGUAGE_BY_CODE: Map<String, MixLanguageOption> = MIX_LANGUAGES.associateBy { it.code }
private val GENRE_BY_ID: Map<String, MixGenreOption> = MIX_GENRES.associateBy { it.id }

fun sortedMixLanguages(uiLanguage: String): List<MixLanguageOption> {
    val locale = Locale.forLanguageTag(uiLanguage.ifBlank { "it" })
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return MIX_LANGUAGES.sortedWith { a, b -> collator.compare(a.label(uiLanguage), b.label(uiLanguage)) }
}

fun sortedMixGenres(uiLanguage: String): List<MixGenreOption> {
    val locale = Locale.forLanguageTag(uiLanguage.ifBlank { "it" })
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return MIX_GENRES.sortedWith { a, b -> collator.compare(a.label(uiLanguage), b.label(uiLanguage)) }
}

fun matchesMixLanguages(channel: Channel, selectedCodes: Set<String>): Boolean {
    if (selectedCodes.isEmpty()) return true
    val selected = selectedCodes.mapNotNull { LANGUAGE_BY_CODE[it] }
    if (selected.isEmpty()) return true

    val chLangTokens = (channel.languages + channel.languageNames)
        .flatMap { it.lowercase(Locale.ROOT).split(';', ',', '/', '|') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (chLangTokens.isNotEmpty()) {
        return selected.any { opt ->
            chLangTokens.any { token ->
                token in opt.aliases || opt.aliases.any { alias -> alias.length > 2 && token.contains(alias) }
            }
        }
    }

    val normCountry = normalizeCountryCode(channel.country)?.let { if (it == "UK") "GB" else it }
        ?: return false
    return selected.any { opt -> normCountry in opt.defaultCountries }
}

private val CATEGORY_SPLIT_REGEX = Regex("""[;/,|]+""")
private val WORD_BOUNDARY_NON_ALNUM = Regex("""[^a-z0-9]+""")

fun matchesMixGenres(channel: Channel, selectedGenreIds: Set<String>): Boolean {
    if (selectedGenreIds.isEmpty()) return true
    val selected = selectedGenreIds.mapNotNull { GENRE_BY_ID[it] }
    if (selected.isEmpty()) return true

    val rawCats = (channel.categories + channel.categoryNames)
        .map { it.lowercase(Locale.ROOT).trim() }
        .filter { it.isNotEmpty() }
    val catTokens = rawCats
        .flatMap { it.split(CATEGORY_SPLIT_REGEX) }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val nameLower = " ${channel.name.lowercase(Locale.ROOT).replace(WORD_BOUNDARY_NON_ALNUM, " ").trim()} "

    return selected.any { genre ->
        catTokens.any { tok ->
            tok in genre.categoryKeywords || genre.categoryKeywords.any { kw -> kw.length >= 4 && tok.contains(kw) }
        } || genre.nameKeywords.any { kw ->
            nameLower.contains(" $kw ")
        }
    }
}

fun computeFilteredMixRegionCounts(
    channels: List<Channel>,
    includeAudioOnly: Boolean,
    selectedLanguages: Set<String>,
    selectedGenres: Set<String>,
): Map<MixRegion, Int> {
    val result = java.util.EnumMap<MixRegion, Int>(MixRegion::class.java)
    channels.forEach { ch ->
        if (!includeAudioOnly && isLikelyAudioOnlyChannel(ch)) return@forEach
        if (!matchesMixLanguages(ch, selectedLanguages)) return@forEach
        if (!matchesMixGenres(ch, selectedGenres)) return@forEach
        val region = resolveMixRegion(ch.country) ?: return@forEach
        result[region] = (result[region] ?: 0) + 1
    }
    return result
}

fun countMatchingMixChannels(
    channels: List<Channel>,
    includeAudioOnly: Boolean,
    selectedRegions: Set<MixRegion>,
    selectedLanguages: Set<String>,
    selectedGenres: Set<String>,
): Int {
    var count = 0
    channels.forEach { ch ->
        if (!includeAudioOnly && isLikelyAudioOnlyChannel(ch)) return@forEach
        if (!matchesMixRegions(ch.country, selectedRegions)) return@forEach
        if (!matchesMixLanguages(ch, selectedLanguages)) return@forEach
        if (!matchesMixGenres(ch, selectedGenres)) return@forEach
        count++
    }
    return count
}
