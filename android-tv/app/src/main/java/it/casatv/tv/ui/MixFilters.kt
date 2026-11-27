package it.casatv.tv.ui

import it.casatv.tv.data.Channel
import it.casatv.tv.data.Facet
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

/** Curated neutral genres/categories, sorted dynamically A–Z by active locale. */
val MIX_GENRES: List<MixGenreOption> = listOf(
    MixGenreOption("action", "Azione", "Action", "Acción", setOf("action", "azione", "acción", "accion", "martial", "combat"), setOf("action", "azione")),
    MixGenreOption("adventure", "Avventura", "Adventure", "Aventuras", setOf("adventure", "avventura", "aventuras", "aventura"), setOf("adventure", "avventura")),
    MixGenreOption("animation", "Animazione", "Animation", "Animación", setOf("animation", "animazione", "animación", "cartoon", "cartoons", "animacion"), setOf("cartoon", "animation")),
    MixGenreOption("anime", "Anime", "Anime", "Anime", setOf("anime", "manga"), setOf("anime", "manga")),
    MixGenreOption("auto", "Auto & Motori", "Auto & Motors", "Motor", setOf("auto", "automotive", "motor", "motori", "motorsport", "racing"), setOf("motorsport", "racing", "motor")),
    MixGenreOption("kids", "Bambini", "Kids", "Infantil", setOf("kids", "child", "children", "bambini", "infantiles", "infantil", "junior", "ragazzi", "kinder"), setOf("kids", "junior", "bambini", "infantil", "cartoons")),
    MixGenreOption("movies", "Cinema & Film", "Movies & Film", "Cine y Películas", setOf("movie", "movies", "cinema", "cine", "películas", "peliculas", "film", "vod"), setOf("cinema", "movie", "movies", "film")),
    MixGenreOption("classic", "Classici & Retrò", "Classic & Retro", "Clásicos", setOf("classic", "classics", "classici", "retro", "clásicos", "vintage"), setOf("classic", "retro", "vintage")),
    MixGenreOption("comedy", "Commedia", "Comedy", "Comedia", setOf("comedy", "commedia", "comedia", "humor", "humour", "satire"), setOf("comedy", "commedia", "humor")),
    MixGenreOption("cooking", "Cucina & Food", "Cooking & Food", "Cocina y Gastronomía", setOf("cooking", "cucina", "cocina", "food", "culinary", "gastronomia", "recipes"), setOf("cooking", "food", "cucina", "cocina")),
    MixGenreOption("crime", "Crimine & Poliziesco", "Crime", "Crimen", setOf("crime", "crimine", "crimen", "poliziesco", "noir", "detective", "investigation"), setOf("crime", "noir", "detective")),
    MixGenreOption("culture", "Cultura & Arte", "Culture & Arts", "Cultura y Arte", setOf("culture", "cultura", "arts", "art", "arte", "theatre", "teatro"), setOf("culture", "cultura", "arts")),
    MixGenreOption("documentary", "Documentari", "Documentary", "Documentales", setOf("documentary", "documentaries", "documentari", "documentales", "doc", "docu", "wildlife"), setOf("documentary", "docu", "wildlife")),
    MixGenreOption("drama", "Dramma", "Drama", "Drama", setOf("drama", "dramma", "drammatico", "drame", "melodrama"), setOf("drama", "dramma")),
    MixGenreOption("business", "Economia & Finanza", "Business & Finance", "Economía y Finanzas", setOf("business", "economia", "finance", "finanza", "financial", "economy", "markets"), setOf("finance", "business", "economia")),
    MixGenreOption("education", "Educazione", "Education", "Educación", setOf("education", "educazione", "educación", "educational", "learning", "scuola", "school"), setOf("education", "learning")),
    MixGenreOption("family", "Famiglia", "Family", "Familia", setOf("family", "famiglia", "familia"), setOf("family", "famiglia")),
    MixGenreOption("fantasy", "Fantasy", "Fantasy", "Fantasía", setOf("fantasy", "fantasia", "fantasía", "mythology", "magic"), setOf("fantasy")),
    MixGenreOption("scifi", "Fantascienza", "Sci-Fi", "Ciencia Ficción", setOf("sci-fi", "scifi", "fantascienza", "science-fiction", "cyberpunk", "space"), setOf("sci-fi", "scifi", "fantascienza")),
    MixGenreOption("general", "Generali", "General", "General", setOf("general", "generali", "generale", "undefined", "eventuales", "variedades", "variety"), emptySet()),
    MixGenreOption("war", "Guerra", "War", "Bélico", setOf("war", "guerra", "belico", "bélico", "military"), setOf("war", "guerra")),
    MixGenreOption("horror", "Horror", "Horror", "Terror", setOf("horror", "orrore", "terror", "thriller", "paranormal", "zombie"), setOf("horror", "terror")),
    MixGenreOption("entertainment", "Intrattenimento", "Entertainment", "Entretenimiento", setOf("entertainment", "intrattenimento", "entretenimiento", "show", "reality", "variety"), setOf("entertainment", "reality")),
    MixGenreOption("legislative", "Istituzionale", "Legislative & Civic", "Institucional", setOf("legislative", "parliament", "government", "istituzionale", "civic", "public", "senato", "senate"), setOf("parliament", "civic")),
    MixGenreOption("lifestyle", "Lifestyle", "Lifestyle", "Estilo de vida", setOf("lifestyle", "home", "living", "wellness", "design"), setOf("lifestyle", "living")),
    MixGenreOption("local", "Locali & Regionali", "Local & Regional", "Locales y Regionales", setOf("local", "locali", "regional", "regionali", "autonómicas", "civic"), emptySet()),
    MixGenreOption("weather", "Meteo", "Weather", "Clima y Tiempo", setOf("weather", "meteo", "tiempo", "clima", "forecast"), setOf("weather", "meteo")),
    MixGenreOption("mystery", "Mistero", "Mystery", "Misterio", setOf("mystery", "mistero", "misterio", "giallo"), setOf("mystery", "mistero")),
    MixGenreOption("fashion", "Moda", "Fashion", "Moda", setOf("fashion", "moda", "style"), setOf("fashion", "moda")),
    MixGenreOption("music", "Musica", "Music", "Música", setOf("music", "musica", "música", "musicales", "musical", "concert", "hits", "opera"), setOf("music", "musica", "hits", "concert")),
    MixGenreOption("outdoor", "Natura & Outdoor", "Nature & Outdoor", "Naturaleza", setOf("outdoor", "nature", "natura", "wildlife", "fishing", "hunting"), setOf("nature", "wildlife", "outdoor")),
    MixGenreOption("news", "News & Informazione", "News & Information", "Noticias e Informativos", setOf("news", "informativos", "informazione", "noticias", "notizie", "aktuell", "journal", "info"), setOf("news", "notizie", "noticias")),
    MixGenreOption("religious", "Religione", "Religious", "Religión", setOf("religious", "religione", "religión", "religiosos", "christian", "islamic", "catholic", "gospel", "faith"), setOf("religious", "gospel", "faith")),
    MixGenreOption("romance", "Romantico", "Romance", "Romance", setOf("romance", "romantico", "romántico", "love", "amore", "sentimental"), setOf("romance")),
    MixGenreOption("science", "Scienza & Tech", "Science & Tech", "Ciencia y Tecnología", setOf("science", "scienza", "ciencia", "tech", "technology", "space"), setOf("science", "tech")),
    MixGenreOption("series", "Serie TV", "TV Series", "Series TV", setOf("series", "serie", "novelas", "telenovelas", "doramas", "sitcom"), setOf("series", "telenovelas")),
    MixGenreOption("shop", "Shopping", "Shopping", "Compras", setOf("shop", "shopping", "teleshop", "commerce", "deals"), setOf("shopping", "teleshop")),
    MixGenreOption("sports", "Sport", "Sports", "Deportes", setOf("sport", "sports", "deporte", "deportes", "deportivos", "football", "soccer", "calcio", "tennis", "basket", "boxing"), setOf("sport", "sports", "football", "soccer", "calcio")),
    MixGenreOption("history", "Storico", "History", "Historia", setOf("history", "storico", "storia", "historia", "historical", "biography"), setOf("history", "storia")),
    MixGenreOption("thriller", "Thriller", "Thriller", "Suspense", setOf("thriller", "suspense", "psychological"), setOf("thriller", "suspense")),
    MixGenreOption("travel", "Viaggi", "Travel", "Viajes", setOf("travel", "viaggi", "viajes", "tourism", "turismo"), setOf("travel", "tourism")),
    MixGenreOption("western", "Western", "Western", "Oeste", setOf("western", "oeste", "cowboy"), setOf("western")),
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

private fun isLangDelimiter(ch: Char): Boolean =
    ch == ' ' || ch == '(' || ch == ')' || ch == '/' || ch == '-' || ch == '_' || ch == ',' || ch == ';'

private fun containsWholeAlias(token: String, alias: String): Boolean {
    if (alias.length < 4) return false
    var from = 0
    while (true) {
        val idx = token.indexOf(alias, startIndex = from)
        if (idx < 0) return false
        val startOk = idx == 0 || isLangDelimiter(token[idx - 1])
        val endIdx = idx + alias.length
        val endOk = endIdx == token.length || isLangDelimiter(token[endIdx])
        if (startOk && endOk) return true
        from = idx + 1
    }
}

private fun matchesLangToken(token: String, aliases: Collection<String>): Boolean =
    token in aliases || aliases.any { alias -> containsWholeAlias(token, alias) }

private fun extractLanguageTokens(channel: Channel): List<String> =
    (channel.languages + channel.languageNames)
        .flatMap { it.lowercase(Locale.ROOT).split(';', ',', '/', '|') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }

fun matchesMixLanguages(channel: Channel, selectedCodes: Set<String>): Boolean {
    if (selectedCodes.isEmpty()) return true
    val selected = selectedCodes.mapNotNull { LANGUAGE_BY_CODE[it] }
    if (selected.isEmpty()) return true

    val chLangTokens = extractLanguageTokens(channel)
    if (chLangTokens.isNotEmpty()) {
        return selected.any { opt ->
            chLangTokens.any { token -> matchesLangToken(token, opt.aliases) }
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

private val MIX_LANG_BIT_BY_CODE: Map<String, Long> =
    MIX_LANGUAGES.mapIndexed { idx, opt -> opt.code to (1L shl idx) }.toMap()

private val MIX_GENRE_BIT_BY_ID: Map<String, Long> =
    MIX_GENRES.mapIndexed { idx, opt -> opt.id to (1L shl idx) }.toMap()

data class IndexedMixChannel(
    val channel: Channel,
    val region: MixRegion?,
    val isAudioOnly: Boolean,
    val langMask: Long,
    val genreMask: Long,
)

data class MixPickerStats(
    val regionCounts: Map<MixRegion, Int> = emptyMap(),
    val matchingCount: Int? = null,
)

fun languageCodesToMask(selectedCodes: Set<String>): Long {
    var mask = 0L
    selectedCodes.forEach { code -> MIX_LANG_BIT_BY_CODE[code]?.let { mask = mask or it } }
    return mask
}

fun genreIdsToMask(selectedGenreIds: Set<String>): Long {
    var mask = 0L
    selectedGenreIds.forEach { id -> MIX_GENRE_BIT_BY_ID[id]?.let { mask = mask or it } }
    return mask
}

fun computeChannelLanguageMask(channel: Channel): Long {
    val chLangTokens = extractLanguageTokens(channel)

    var mask = 0L
    if (chLangTokens.isNotEmpty()) {
        for (idx in MIX_LANGUAGES.indices) {
            val opt = MIX_LANGUAGES[idx]
            val matched = chLangTokens.any { token -> matchesLangToken(token, opt.aliases) }
            if (matched) mask = mask or (1L shl idx)
        }
        return mask
    }

    val normCountry = normalizeCountryCode(channel.country)?.let { if (it == "UK") "GB" else it }
        ?: return 0L
    for (idx in MIX_LANGUAGES.indices) {
        if (normCountry in MIX_LANGUAGES[idx].defaultCountries) {
            mask = mask or (1L shl idx)
        }
    }
    return mask
}

fun computeChannelGenreMask(channel: Channel): Long {
    val rawCats = (channel.categories + channel.categoryNames)
        .map { it.lowercase(Locale.ROOT).trim() }
        .filter { it.isNotEmpty() }
    val catTokens = rawCats
        .flatMap { it.split(CATEGORY_SPLIT_REGEX) }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val nameLower = " ${channel.name.lowercase(Locale.ROOT).replace(WORD_BOUNDARY_NON_ALNUM, " ").trim()} "

    var mask = 0L
    for (idx in MIX_GENRES.indices) {
        val genre = MIX_GENRES[idx]
        val matched = catTokens.any { tok ->
            tok in genre.categoryKeywords || genre.categoryKeywords.any { kw -> kw.length >= 4 && tok.contains(kw) }
        } || genre.nameKeywords.any { kw ->
            nameLower.contains(" $kw ")
        }
        if (matched) mask = mask or (1L shl idx)
    }
    return mask
}

fun buildMixIndex(channels: List<Channel>): List<IndexedMixChannel> {
    val out = ArrayList<IndexedMixChannel>(channels.size)
    for (ch in channels) {
        out.add(
            IndexedMixChannel(
                channel = ch,
                region = resolveMixRegion(ch.country),
                isAudioOnly = isLikelyAudioOnlyChannel(ch),
                langMask = computeChannelLanguageMask(ch),
                genreMask = computeChannelGenreMask(ch),
            ),
        )
    }
    return out
}

fun computeIndexedMixPickerStats(
    index: List<IndexedMixChannel>,
    fallbackCountries: List<Facet>,
    includeAudioOnly: Boolean,
    selectedRegions: Set<MixRegion>,
    selectedLangMask: Long,
    selectedGenreMask: Long,
): MixPickerStats {
    if ((selectedLangMask == 0L && selectedGenreMask == 0L) || index.isEmpty()) {
        return MixPickerStats(
            regionCounts = computeMixRegionCounts(fallbackCountries),
            matchingCount = null,
        )
    }
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
    val result = java.util.EnumMap<MixRegion, Int>(MixRegion::class.java)
    MixRegion.entries.forEach { reg ->
        if (reg != MixRegion.ALL_WORLD) {
            val c = regionCountsArr[reg.ordinal]
            if (c > 0) result[reg] = c
        }
    }
    return MixPickerStats(
        regionCounts = result,
        matchingCount = matchCount,
    )
}
