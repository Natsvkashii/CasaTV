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

/** Curated neutral genres/categories, sorted dynamically A–Z by active locale. */
val MOBILE_MIX_GENRES: List<MobileMixGenreOption> = listOf(
    MobileMixGenreOption("action", "Azione", "Action", "Acción", setOf("action", "azione", "acción", "accion", "martial", "combat"), setOf("action", "azione")),
    MobileMixGenreOption("adventure", "Avventura", "Adventure", "Aventuras", setOf("adventure", "avventura", "aventuras", "aventura"), setOf("adventure", "avventura")),
    MobileMixGenreOption("animation", "Animazione", "Animation", "Animación", setOf("animation", "animazione", "animación", "cartoon", "cartoons", "animacion"), setOf("cartoon", "animation")),
    MobileMixGenreOption("anime", "Anime", "Anime", "Anime", setOf("anime", "manga"), setOf("anime", "manga")),
    MobileMixGenreOption("auto", "Auto & Motori", "Auto & Motors", "Motor", setOf("auto", "automotive", "motor", "motori", "motorsport", "racing"), setOf("motorsport", "racing", "motor")),
    MobileMixGenreOption("kids", "Bambini", "Kids", "Infantil", setOf("kids", "child", "children", "bambini", "infantiles", "infantil", "junior", "ragazzi", "kinder"), setOf("kids", "junior", "bambini", "infantil", "cartoons")),
    MobileMixGenreOption("movies", "Cinema & Film", "Movies & Film", "Cine y Películas", setOf("movie", "movies", "cinema", "cine", "películas", "peliculas", "film", "vod"), setOf("cinema", "movie", "movies", "film")),
    MobileMixGenreOption("classic", "Classici & Retrò", "Classic & Retro", "Clásicos", setOf("classic", "classics", "classici", "retro", "clásicos", "vintage"), setOf("classic", "retro", "vintage")),
    MobileMixGenreOption("comedy", "Commedia", "Comedy", "Comedia", setOf("comedy", "commedia", "comedia", "humor", "humour", "satire"), setOf("comedy", "commedia", "humor")),
    MobileMixGenreOption("cooking", "Cucina & Food", "Cooking & Food", "Cocina y Gastronomía", setOf("cooking", "cucina", "cocina", "food", "culinary", "gastronomia", "recipes"), setOf("cooking", "food", "cucina", "cocina")),
    MobileMixGenreOption("crime", "Crimine & Poliziesco", "Crime", "Crimen", setOf("crime", "crimine", "crimen", "poliziesco", "noir", "detective", "investigation"), setOf("crime", "noir", "detective")),
    MobileMixGenreOption("culture", "Cultura & Arte", "Culture & Arts", "Cultura y Arte", setOf("culture", "cultura", "arts", "art", "arte", "theatre", "teatro"), setOf("culture", "cultura", "arts")),
    MobileMixGenreOption("documentary", "Documentari", "Documentary", "Documentales", setOf("documentary", "documentaries", "documentari", "documentales", "doc", "docu", "wildlife"), setOf("documentary", "docu", "wildlife")),
    MobileMixGenreOption("drama", "Dramma", "Drama", "Drama", setOf("drama", "dramma", "drammatico", "drame", "melodrama"), setOf("drama", "dramma")),
    MobileMixGenreOption("business", "Economia & Finanza", "Business & Finance", "Economía y Finanzas", setOf("business", "economia", "finance", "finanza", "financial", "economy", "markets"), setOf("finance", "business", "economia")),
    MobileMixGenreOption("education", "Educazione", "Education", "Educación", setOf("education", "educazione", "educación", "educational", "learning", "scuola", "school"), setOf("education", "learning")),
    MobileMixGenreOption("family", "Famiglia", "Family", "Familia", setOf("family", "famiglia", "familia"), setOf("family", "famiglia")),
    MobileMixGenreOption("fantasy", "Fantasy", "Fantasy", "Fantasía", setOf("fantasy", "fantasia", "fantasía", "mythology", "magic"), setOf("fantasy")),
    MobileMixGenreOption("scifi", "Fantascienza", "Sci-Fi", "Ciencia Ficción", setOf("sci-fi", "scifi", "fantascienza", "science-fiction", "cyberpunk", "space"), setOf("sci-fi", "scifi", "fantascienza")),
    MobileMixGenreOption("general", "Generali", "General", "General", setOf("general", "generali", "generale", "undefined", "eventuales", "variedades", "variety"), emptySet()),
    MobileMixGenreOption("war", "Guerra", "War", "Bélico", setOf("war", "guerra", "belico", "bélico", "military"), setOf("war", "guerra")),
    MobileMixGenreOption("horror", "Horror", "Horror", "Terror", setOf("horror", "orrore", "terror", "thriller", "paranormal", "zombie"), setOf("horror", "terror")),
    MobileMixGenreOption("entertainment", "Intrattenimento", "Entertainment", "Entretenimiento", setOf("entertainment", "intrattenimento", "entretenimiento", "show", "reality", "variety"), setOf("entertainment", "reality")),
    MobileMixGenreOption("legislative", "Istituzionale", "Legislative & Civic", "Institucional", setOf("legislative", "parliament", "government", "istituzionale", "civic", "public", "senato", "senate"), setOf("parliament", "civic")),
    MobileMixGenreOption("lifestyle", "Lifestyle", "Lifestyle", "Estilo de vida", setOf("lifestyle", "home", "living", "wellness", "design"), setOf("lifestyle", "living")),
    MobileMixGenreOption("local", "Locali & Regionali", "Local & Regional", "Locales y Regionales", setOf("local", "locali", "regional", "regionali", "autonómicas", "civic"), emptySet()),
    MobileMixGenreOption("weather", "Meteo", "Weather", "Clima y Tiempo", setOf("weather", "meteo", "tiempo", "clima", "forecast"), setOf("weather", "meteo")),
    MobileMixGenreOption("mystery", "Mistero", "Mystery", "Misterio", setOf("mystery", "mistero", "misterio", "giallo"), setOf("mystery", "mistero")),
    MobileMixGenreOption("fashion", "Moda", "Fashion", "Moda", setOf("fashion", "moda", "style"), setOf("fashion", "moda")),
    MobileMixGenreOption("music", "Musica", "Music", "Música", setOf("music", "musica", "música", "musicales", "musical", "concert", "hits", "opera"), setOf("music", "musica", "hits", "concert")),
    MobileMixGenreOption("outdoor", "Natura & Outdoor", "Nature & Outdoor", "Naturaleza", setOf("outdoor", "nature", "natura", "wildlife", "fishing", "hunting"), setOf("nature", "wildlife", "outdoor")),
    MobileMixGenreOption("news", "News & Informazione", "News & Information", "Noticias e Informativos", setOf("news", "informativos", "informazione", "noticias", "notizie", "aktuell", "journal", "info"), setOf("news", "notizie", "noticias")),
    MobileMixGenreOption("religious", "Religione", "Religious", "Religión", setOf("religious", "religione", "religión", "religiosos", "christian", "islamic", "catholic", "gospel", "faith"), setOf("religious", "gospel", "faith")),
    MobileMixGenreOption("romance", "Romantico", "Romance", "Romance", setOf("romance", "romantico", "romántico", "love", "amore", "sentimental"), setOf("romance")),
    MobileMixGenreOption("science", "Scienza & Tech", "Science & Tech", "Ciencia y Tecnología", setOf("science", "scienza", "ciencia", "tech", "technology", "space"), setOf("science", "tech")),
    MobileMixGenreOption("series", "Serie TV", "TV Series", "Series TV", setOf("series", "serie", "novelas", "telenovelas", "doramas", "sitcom"), setOf("series", "telenovelas")),
    MobileMixGenreOption("shop", "Shopping", "Shopping", "Compras", setOf("shop", "shopping", "teleshop", "commerce", "deals"), setOf("shopping", "teleshop")),
    MobileMixGenreOption("sports", "Sport", "Sports", "Deportes", setOf("sport", "sports", "deporte", "deportes", "deportivos", "football", "soccer", "calcio", "tennis", "basket", "boxing"), setOf("sport", "sports", "football", "soccer", "calcio")),
    MobileMixGenreOption("history", "Storico", "History", "Historia", setOf("history", "storico", "storia", "historia", "historical", "biography"), setOf("history", "storia")),
    MobileMixGenreOption("thriller", "Thriller", "Thriller", "Suspense", setOf("thriller", "suspense", "psychological"), setOf("thriller", "suspense")),
    MobileMixGenreOption("travel", "Viaggi", "Travel", "Viajes", setOf("travel", "viaggi", "viajes", "tourism", "turismo"), setOf("travel", "tourism")),
    MobileMixGenreOption("western", "Western", "Western", "Oeste", setOf("western", "oeste", "cowboy"), setOf("western")),
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

private fun isMobileLangDelimiter(ch: Char): Boolean =
    ch == ' ' || ch == '(' || ch == ')' || ch == '/' || ch == '-' || ch == '_' || ch == ',' || ch == ';'

private fun containsWholeMobileAlias(token: String, alias: String): Boolean {
    if (alias.length < 4) return false
    var from = 0
    while (true) {
        val idx = token.indexOf(alias, startIndex = from)
        if (idx < 0) return false
        val startOk = idx == 0 || isMobileLangDelimiter(token[idx - 1])
        val endIdx = idx + alias.length
        val endOk = endIdx == token.length || isMobileLangDelimiter(token[endIdx])
        if (startOk && endOk) return true
        from = idx + 1
    }
}

private fun matchesMobileLangToken(token: String, aliases: Collection<String>): Boolean =
    token in aliases || aliases.any { alias -> containsWholeMobileAlias(token, alias) }

private fun extractMobileLangTokens(channel: MobileChannel): List<String> =
    channel.language.orEmpty()
        .lowercase(Locale.ROOT)
        .split(';', ',', '/', '|')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

fun matchesMobileMixLanguages(channel: MobileChannel, selectedCodes: Set<String>): Boolean {
    if (selectedCodes.isEmpty()) return true
    val selected = selectedCodes.mapNotNull { MOBILE_LANG_BY_CODE[it] }
    if (selected.isEmpty()) return true

    val chLangTokens = extractMobileLangTokens(channel)
    if (chLangTokens.isNotEmpty()) {
        return selected.any { opt ->
            chLangTokens.any { token -> matchesMobileLangToken(token, opt.aliases) }
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

private val MOBILE_LANG_BIT_BY_CODE: Map<String, Long> =
    MOBILE_MIX_LANGUAGES.mapIndexed { idx, opt -> opt.code to (1L shl idx) }.toMap()

private val MOBILE_GENRE_BIT_BY_ID: Map<String, Long> =
    MOBILE_MIX_GENRES.mapIndexed { idx, opt -> opt.id to (1L shl idx) }.toMap()

data class MobileIndexedMixChannel(
    val channel: MobileChannel,
    val region: MixRegion?,
    val isAudioOnly: Boolean,
    val langMask: Long,
    val genreMask: Long,
)

data class MobileMixPickerStats(
    val regionCounts: Map<MixRegion, Int> = emptyMap(),
    val matchingCount: Int? = null,
)

fun mobileLanguageCodesToMask(selectedCodes: Set<String>): Long {
    var mask = 0L
    selectedCodes.forEach { code -> MOBILE_LANG_BIT_BY_CODE[code]?.let { mask = mask or it } }
    return mask
}

fun mobileGenreIdsToMask(selectedGenreIds: Set<String>): Long {
    var mask = 0L
    selectedGenreIds.forEach { id -> MOBILE_GENRE_BIT_BY_ID[id]?.let { mask = mask or it } }
    return mask
}

fun computeMobileChannelLanguageMask(channel: MobileChannel): Long {
    val chLangTokens = extractMobileLangTokens(channel)

    var mask = 0L
    if (chLangTokens.isNotEmpty()) {
        for (idx in MOBILE_MIX_LANGUAGES.indices) {
            val opt = MOBILE_MIX_LANGUAGES[idx]
            val matched = chLangTokens.any { token -> matchesMobileLangToken(token, opt.aliases) }
            if (matched) mask = mask or (1L shl idx)
        }
        return mask
    }

    val rawCountry = channel.country.orEmpty().trim().uppercase(Locale.ROOT)
    val normCountry = if (rawCountry == "UK") "GB" else rawCountry
    if (normCountry.length != 2) return 0L
    for (idx in MOBILE_MIX_LANGUAGES.indices) {
        if (normCountry in MOBILE_MIX_LANGUAGES[idx].defaultCountries) {
            mask = mask or (1L shl idx)
        }
    }
    return mask
}

fun computeMobileChannelGenreMask(channel: MobileChannel): Long {
    val catTokens = channel.category.lowercase(Locale.ROOT)
        .split(MOBILE_CAT_SPLIT_RE)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val nameLower = " ${channel.name.lowercase(Locale.ROOT).replace(MOBILE_NON_ALNUM_RE, " ").trim()} "

    var mask = 0L
    for (idx in MOBILE_MIX_GENRES.indices) {
        val genre = MOBILE_MIX_GENRES[idx]
        val matched = catTokens.any { tok ->
            tok in genre.categoryKeywords || genre.categoryKeywords.any { kw -> kw.length >= 4 && tok.contains(kw) }
        } || genre.nameKeywords.any { kw ->
            nameLower.contains(" $kw ")
        }
        if (matched) mask = mask or (1L shl idx)
    }
    return mask
}

fun buildMobileMixIndex(channels: List<MobileChannel>): List<MobileIndexedMixChannel> {
    val out = ArrayList<MobileIndexedMixChannel>(channels.size)
    for (ch in channels) {
        out.add(
            MobileIndexedMixChannel(
                channel = ch,
                region = resolveMixRegion(ch.country),
                isAudioOnly = isLikelyAudioOnlyChannel(ch),
                langMask = computeMobileChannelLanguageMask(ch),
                genreMask = computeMobileChannelGenreMask(ch),
            ),
        )
    }
    return out
}

fun computeMobileIndexedMixPickerStats(
    index: List<MobileIndexedMixChannel>,
    includeAudioOnly: Boolean,
    selectedRegions: Set<MixRegion>,
    selectedLangMask: Long,
    selectedGenreMask: Long,
): MobileMixPickerStats {
    val allWorld = selectedRegions.isEmpty() || MixRegion.ALL_WORLD in selectedRegions
    val regionCountsArr = IntArray(MixRegion.entries.size)
    var matchCount = 0
    for (i in index.indices) {
        val entry = index[i]
        if (!includeAudioOnly && entry.isAudioOnly) continue
        if (selectedLangMask != 0L && (entry.langMask and selectedLangMask) == 0L) continue
        if (selectedGenreMask != 0L && (entry.genreMask and selectedGenreMask) == 0L) continue
        val reg = entry.region
        if (reg != null) {
            regionCountsArr[reg.ordinal]++
        }
        if (allWorld || (reg != null && reg in selectedRegions)) {
            matchCount++
        }
    }
    val result = EnumMap<MixRegion, Int>(MixRegion::class.java)
    MixRegion.entries.forEach { reg ->
        if (reg != MixRegion.ALL_WORLD) {
            val c = regionCountsArr[reg.ordinal]
            if (c > 0) result[reg] = c
        }
    }
    return MobileMixPickerStats(
        regionCounts = result,
        matchingCount = matchCount,
    )
}
