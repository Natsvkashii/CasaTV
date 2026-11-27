package it.casatv.server.core

import java.text.Normalizer
import java.util.Locale

data class VodGenreDef(
    val id: String,
    val itName: String,
    val enName: String,
    val esName: String,
    val matchers: List<Regex>,
) {
    fun label(language: String): String = when (language.lowercase(Locale.ROOT).take(2)) {
        "en" -> enName
        "es" -> esName
        else -> itName
    }
}

data class VodPlatformDef(
    val id: String,
    val name: String,
    val matchers: List<Regex>,
)

data class VodFacetItem(
    val id: String,
    val name: String,
    val count: Int,
)

data class VodLanguageDef(
    val code: String,
    val flag: String,
    val itName: String,
    val enName: String,
    val esName: String,
    val countries: Set<String>,
    val tagRegex: Regex,
) {
    fun label(uiLanguage: String): String = when (uiLanguage.lowercase(Locale.ROOT).take(2)) {
        "en" -> enName
        "es" -> esName
        else -> itName
    }
}

data class VodLanguageFacet(
    val code: String,
    val name: String,
    val flag: String,
    val count: Int,
)

data class VodTaxonomyResult(
    val genres: List<VodFacetItem>,
    val platforms: List<VodFacetItem>,
    val genreRawCategories: Map<String, Set<String>>,
    val platformRawCategories: Map<String, Set<String>>,
)

/**
 * Classifies M3U VOD folder names into canonical user-facing Genres,
 * and provides normalized title deduplication keys for VOD items.
 */
object VodTaxonomy {

    private fun wordRegex(vararg tokens: String): List<Regex> =
        tokens.map { tok -> Regex("""(?i)$tok""") }

    val GENRES: List<VodGenreDef> = listOf(
        VodGenreDef(
            id = "action",
            itName = "Azione",
            enName = "Action",
            esName = "Acción",
            matchers = wordRegex("azione", "action", "acci[oó]n", "martial", "combattimento", "arti\\s*marziali"),
        ),
        VodGenreDef(
            id = "adventure",
            itName = "Avventura",
            enName = "Adventure",
            esName = "Aventuras",
            matchers = wordRegex("avventur", "adventur", "aventur"),
        ),
        VodGenreDef(
            id = "animation",
            itName = "Animazione",
            enName = "Animation",
            esName = "Animación",
            matchers = wordRegex("animazion", "animation", "animaci[oó]n", "carton", "cartoon", "dibujos"),
        ),
        VodGenreDef(
            id = "anime",
            itName = "Anime",
            enName = "Anime",
            esName = "Anime",
            matchers = wordRegex("\\banime", "\\bmanga\\b"),
        ),
        VodGenreDef(
            id = "biography",
            itName = "Biografico",
            enName = "Biography",
            esName = "Biografía",
            matchers = wordRegex("biograf", "biograph", "biopic"),
        ),
        VodGenreDef(
            id = "comedy",
            itName = "Commedia",
            enName = "Comedy",
            esName = "Comedia",
            matchers = wordRegex("commed", "comed", "com[eé]d", "kom[oö]d", "\\bhumor\\b", "cabaret", "stand-?up", "satir"),
        ),
        VodGenreDef(
            id = "crime",
            itName = "Crimine & Poliziesco",
            enName = "Crime",
            esName = "Crimen",
            matchers = wordRegex("crimin", "\\bcrime\\b", "poliz", "mafia", "\\bnoir\\b", "krimi", "gangster", "detective"),
        ),
        VodGenreDef(
            id = "documentary",
            itName = "Documentari",
            enName = "Documentary",
            esName = "Documentales",
            matchers = wordRegex("document", "\\bdoku\\b", "\\bdocu\\b", "\\bnatura\\b", "\\bnature\\b", "wildlife"),
        ),
        VodGenreDef(
            id = "drama",
            itName = "Dramma",
            enName = "Drama",
            esName = "Drama",
            matchers = wordRegex("dramm", "drama", "drame", "melodram"),
        ),
        VodGenreDef(
            id = "family",
            itName = "Famiglia",
            enName = "Family",
            esName = "Familia",
            matchers = wordRegex("famigl", "famil"),
        ),
        VodGenreDef(
            id = "fantasy",
            itName = "Fantasy",
            enName = "Fantasy",
            esName = "Fantasía",
            matchers = wordRegex("fantas", "magic", "magia", "mitolog", "mytholog"),
        ),
        VodGenreDef(
            id = "history",
            itName = "Storico",
            enName = "History",
            esName = "Historia",
            matchers = wordRegex("storic", "storia", "historia", "history", "\\bepic"),
        ),
        VodGenreDef(
            id = "horror",
            itName = "Horror",
            enName = "Horror",
            esName = "Terror",
            matchers = wordRegex("horror", "orror", "\\bterror\\b", "horreur", "splatter", "slasher", "zombie", "paranormal"),
        ),
        VodGenreDef(
            id = "kids",
            itName = "Bambini",
            enName = "Kids",
            esName = "Infantil",
            matchers = wordRegex("bambin", "\\bkids\\b", "child", "enfant", "\\bkinder", "ni[nñ]os", "ragazzi", "\\bjunior\\b", "infantil"),
        ),
        VodGenreDef(
            id = "music",
            itName = "Musica & Musical",
            enName = "Music & Musical",
            esName = "Música y Musical",
            matchers = wordRegex("music", "concert", "musical", "\\bopera\\b", "teatro", "th[eé][aâ]tre"),
        ),
        VodGenreDef(
            id = "mystery",
            itName = "Mistero",
            enName = "Mystery",
            esName = "Misterio",
            matchers = wordRegex("mister", "myster", "myst[eè]r", "\\bgiallo\\b"),
        ),
        VodGenreDef(
            id = "reality_show",
            itName = "Show & Reality",
            enName = "Shows & Reality",
            esName = "Programas y Reality",
            matchers = wordRegex("tv-?show", "reality", "tele\\s*realit", "spectacle", "talk\\s*show", "variet"),
        ),
        VodGenreDef(
            id = "romance",
            itName = "Romantico",
            enName = "Romance",
            esName = "Romance",
            matchers = wordRegex("romanz", "romanc", "romantic", "\\bamore\\b", "\\blove\\b", "sentimental"),
        ),
        VodGenreDef(
            id = "scifi",
            itName = "Fantascienza",
            enName = "Sci-Fi",
            esName = "Ciencia Ficción",
            matchers = wordRegex("sci-?fi", "fantascienz", "ciencia\\s*fic", "science-?fiction", "\\bspace\\b", "spazio", "cyberpunk", "distopi", "dystopi"),
        ),
        VodGenreDef(
            id = "sport",
            itName = "Sport",
            enName = "Sports",
            esName = "Deportes",
            matchers = wordRegex("sport", "deport", "calcio", "football", "soccer", "motor", "racing", "tennis"),
        ),
        VodGenreDef(
            id = "thriller",
            itName = "Thriller",
            enName = "Thriller",
            esName = "Suspense",
            matchers = wordRegex("thriller", "suspens", "psicolog", "psycholog"),
        ),
        VodGenreDef(
            id = "war",
            itName = "Guerra",
            enName = "War",
            esName = "Bélico",
            matchers = wordRegex("guerra", "\\bwar\\b", "b[eé]lic", "militar"),
        ),
        VodGenreDef(
            id = "western",
            itName = "Western",
            enName = "Western",
            esName = "Oeste",
            matchers = wordRegex("western", "\\boeste\\b", "cowboy"),
        ),
        VodGenreDef(
            id = "new_4k",
            itName = "Novità & 4K",
            enName = "New & 4K",
            esName = "Novedades y 4K",
            matchers = wordRegex("\\b4k\\b", "\\buhd\\b", "novit", "nuevos", "\\bneue\\b", "r[eé]cem", "recement", "top\\s*202", "lanzamiento", "classic"),
        ),
    )

    val PLATFORMS: List<VodPlatformDef> = emptyList()

    private val GENRE_BY_ID = GENRES.associateBy { it.id }
    private val PLATFORM_BY_ID = PLATFORMS.associateBy { it.id }

    val LANGUAGES: List<VodLanguageDef> = listOf(
        VodLanguageDef("it", "IT", "Italiano", "Italian", "Italiano", setOf("IT", "SM", "VA"), Regex("""(?i)(?:^|[\[\(\s|_-])(it|ita|italian|italiano)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("en", "GB", "Inglese", "English", "Inglés", setOf("US", "GB", "UK", "CA", "AU", "NZ", "IE"), Regex("""(?i)(?:^|[\[\(\s|_-])(en|eng|us|uk|english)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("es", "ES", "Spagnolo", "Spanish", "Español", setOf("ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "UY", "PY", "BO", "CR", "PA", "DO", "GT", "HN", "SV", "NI", "CU", "PR", "LAT"), Regex("""(?i)(?:^|[\[\(\s|_-])(es|esp|spa|lat|latino|castellano|spanish)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("fr", "FR", "Francese", "French", "Francés", setOf("FR", "BE", "MC", "LU"), Regex("""(?i)(?:^|[\[\(\s|_-])(fr|fra|fre|french|francese|vf|vostfr)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("de", "DE", "Tedesco", "German", "Alemán", setOf("DE", "AT", "CH"), Regex("""(?i)(?:^|[\[\(\s|_-])(de|deu|ger|german|deutsch)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("pt", "PT", "Portoghese", "Portuguese", "Portugués", setOf("PT", "BR"), Regex("""(?i)(?:^|[\[\(\s|_-])(pt|por|br|portuguese)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("ja", "JP", "Giapponese", "Japanese", "Japonés", setOf("JP"), Regex("""(?i)(?:^|[\[\(\s|_-])(ja|jp|jpn|japanese)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("ko", "KR", "Coreano", "Korean", "Coreano", setOf("KR"), Regex("""(?i)(?:^|[\[\(\s|_-])(ko|kr|kor|korean)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("ar", "SA", "Arabo", "Arabic", "Árabe", setOf("SA", "AE", "EG", "MA", "DZ", "TN", "LB", "JO", "KW", "QA", "IQ"), Regex("""(?i)(?:^|[\[\(\s|_-])(ar|ara|arabic|arab)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("tr", "TR", "Turco", "Turkish", "Turco", setOf("TR"), Regex("""(?i)(?:^|[\[\(\s|_-])(tr|tur|turkish)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("ru", "RU", "Russo", "Russian", "Ruso", setOf("RU", "BY", "KZ"), Regex("""(?i)(?:^|[\[\(\s|_-])(ru|rus|russian)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("pl", "PL", "Polacco", "Polish", "Polaco", setOf("PL"), Regex("""(?i)(?:^|[\[\(\s|_-])(pl|pol|polish)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("nl", "NL", "Olandese", "Dutch", "Neerlandés", setOf("NL"), Regex("""(?i)(?:^|[\[\(\s|_-])(nl|nld|dut|dutch)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("ro", "RO", "Rumeno", "Romanian", "Rumano", setOf("RO", "MD"), Regex("""(?i)(?:^|[\[\(\s|_-])(ro|ron|rum|romanian)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("hi", "IN", "Hindi / Indiano", "Hindi", "Hindi", setOf("IN"), Regex("""(?i)(?:^|[\[\(\s|_-])(hi|hin|hindi|in)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("zh", "CN", "Cinese", "Chinese", "Chino", setOf("CN", "TW", "HK"), Regex("""(?i)(?:^|[\[\(\s|_-])(zh|zho|chi|cn|chinese)(?:[\]\)\s|_-]|$)""")),
        VodLanguageDef("multi", "", "Multilingua", "Multi-language", "Multilenguaje", emptySet(), Regex("""(?i)multi[-_\s]?(?:lang|audio|sub)""")),
    )

    private val LANGUAGE_BY_CODE = LANGUAGES.associateBy { it.code }

    fun resolveLanguageDef(langCode: String?): VodLanguageDef? {
        val norm = langCode?.trim()?.lowercase(Locale.ROOT)?.take(5).orEmpty()
        if (norm.isEmpty()) return null
        return LANGUAGE_BY_CODE[norm] ?: LANGUAGE_BY_CODE[norm.take(2)]
    }

    fun resolveLanguageCountries(langCode: String?): Set<String> =
        resolveLanguageDef(langCode)?.countries ?: emptySet()

    fun resolveLanguageRawCategories(rawCats: List<Pair<String, Int>>, langCode: String?): Set<String> {
        val def = resolveLanguageDef(langCode) ?: return emptySet()
        val out = LinkedHashSet<String>()
        for ((rawCat, _) in rawCats) {
            if (def.tagRegex.containsMatchIn(rawCat)) {
                out += rawCat
            }
        }
        return out
    }

    fun classifyVodLanguages(
        byCountry: Map<String, Int>,
        rawCats: List<Pair<String, Int>>,
        uiLanguage: String,
    ): List<VodLanguageFacet> {
        val out = ArrayList<VodLanguageFacet>(LANGUAGES.size)
        for (def in LANGUAGES) {
            var count = 0
            for (cc in def.countries) {
                count += byCountry[cc] ?: 0
            }
            if (def.countries.isEmpty() || count == 0) {
                for ((cat, catCnt) in rawCats) {
                    if (def.tagRegex.containsMatchIn(cat)) {
                        count += catCnt
                    }
                }
            }
            if (count > 0) {
                out += VodLanguageFacet(
                    code = def.code,
                    name = def.label(uiLanguage),
                    flag = def.flag,
                    count = count,
                )
            }
        }
        return out.sortedByDescending { it.count }
    }

    fun classifyRawCategories(
        rawCats: List<Pair<String, Int>>,
        language: String,
    ): VodTaxonomyResult {
        val genreCounts = LinkedHashMap<String, Int>()
        val genreRawMap = LinkedHashMap<String, LinkedHashSet<String>>()
        val platformCounts = LinkedHashMap<String, Int>()
        val platformRawMap = LinkedHashMap<String, LinkedHashSet<String>>()

        for ((rawCat, count) in rawCats) {
            val clean = rawCat.trim()
            if (clean.isEmpty() || count <= 0) continue

            val matchedPlatforms = PLATFORMS.filter { p -> p.matchers.any { it.containsMatchIn(clean) } }
            for (plat in matchedPlatforms) {
                platformCounts[plat.id] = (platformCounts[plat.id] ?: 0) + count
                platformRawMap.getOrPut(plat.id) { LinkedHashSet() }.add(rawCat)
            }

            // Explicit genre matching against normalized category labels.
            val matchedGenres = GENRES.filter { g -> g.matchers.any { it.containsMatchIn(clean) } }
            for (genre in matchedGenres) {
                genreCounts[genre.id] = (genreCounts[genre.id] ?: 0) + count
                genreRawMap.getOrPut(genre.id) { LinkedHashSet() }.add(rawCat)
            }
        }

        val genres = GENRES.mapNotNull { def ->
            val cnt = genreCounts[def.id] ?: 0
            if (cnt > 0) VodFacetItem(def.id, def.label(language), cnt) else null
        }.sortedByDescending { it.count }

        val platforms = PLATFORMS.mapNotNull { def ->
            val cnt = platformCounts[def.id] ?: 0
            if (cnt > 0) VodFacetItem(def.id, def.name, cnt) else null
        }.sortedByDescending { it.count }

        return VodTaxonomyResult(
            genres = genres,
            platforms = platforms,
            genreRawCategories = genreRawMap,
            platformRawCategories = platformRawMap,
        )
    }

    fun resolveRawCategoriesForFilter(
        rawCats: List<Pair<String, Int>>,
        genreId: String? = null,
        platformId: String? = null,
    ): Set<String> {
        val normGenre = genreId?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val normPlatform = platformId?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normGenre.isEmpty() && normPlatform.isEmpty()) return emptySet()

        if (normGenre.startsWith("raw:")) {
            return setOf(genreId!!.substringAfter("raw:"))
        }

        val genreDef = GENRE_BY_ID[normGenre]
        val platformDef = PLATFORM_BY_ID[normPlatform]

        val out = LinkedHashSet<String>()
        for ((rawCat, _) in rawCats) {
            val clean = rawCat.trim()
            if (clean.isEmpty()) continue
            val gOk = genreDef == null || genreDef.matchers.any { it.containsMatchIn(clean) }
            val pOk = platformDef == null || platformDef.matchers.any { it.containsMatchIn(clean) }
            if (gOk && pOk) out += rawCat
        }
        return out
    }

    private val LEADING_TAG_RE = Regex("""^\s*(?:\[[^\]]{1,22}\]|\([^)]{1,22}\)|[A-Za-z]{2,4}\s*[\|:\-])\s*""")
    private val TRAILING_TAG_RE = Regex("""(?:\s*(?:\[[^\]]{1,24}\]|\((?:4k|uhd|fhd|hd|sd|ita|eng|esp|fr|de|sub|multi|dub|vostfr|vf)[^)]*\)|-\s*(?:19|20)\d{2}))+$""", RegexOption.IGNORE_CASE)
    private val DIACRITICS_RE = Regex("""\p{M}+""")
    private val NON_ALNUM_RE = Regex("""[^a-z0-9]+""")

    fun normalizeShowKey(rawTitle: String): String {
        var title = rawTitle.trim()
        repeat(2) {
            title = LEADING_TAG_RE.replaceFirst(title, "").trim()
        }
        title = TRAILING_TAG_RE.replaceFirst(title, "").trim()
        if (title.isBlank()) title = rawTitle.trim()
        val nfd = Normalizer.normalize(title, Normalizer.Form.NFD)
        val ascii = DIACRITICS_RE.replace(nfd, "").lowercase(Locale.ROOT)
        return NON_ALNUM_RE.replace(ascii, " ").trim()
    }

    fun normalizeSeriesEpisodeKey(channel: IptvChannel): String {
        val info = M3uParser.parseSeriesEpisode(channel.name) ?: return normalizeVodDedupKey(channel) + ":id:" + channel.id
        return "series:${normalizeShowKey(info.showTitle)}:s${info.season}e${info.episode}"
    }

    fun normalizeVodDedupKey(channel: IptvChannel): String {
        if (channel.contentType == "series") {
            val epInfo = M3uParser.parseSeriesEpisode(channel.name)
            if (epInfo != null && epInfo.showTitle.isNotBlank()) {
                val showNorm = normalizeShowKey(epInfo.showTitle)
                if (showNorm.isNotEmpty()) return "series:$showNorm"
            }
        }
        val norm = normalizeShowKey(channel.name)
        return "${channel.contentType}:$norm"
    }

    fun vodCandidateScore(channel: IptvChannel): Int {
        var score = 0
        if (channel.contentType == "series") {
            val ep = M3uParser.parseSeriesEpisode(channel.name)
            if (ep != null) {
                score += when {
                    ep.season == 1 && ep.episode == 1 -> 60
                    ep.season == 1 -> 30 - ep.episode.coerceAtMost(25)
                    else -> -(ep.season * 10 + ep.episode).coerceAtMost(200)
                }
            }
        }
        val cleanPath = channel.url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        score += when {
            cleanPath.endsWith(".mp4") -> 40
            cleanPath.endsWith(".webm") -> 30
            cleanPath.endsWith(".m4v") -> 20
            cleanPath.endsWith(".mov") -> 15
            cleanPath.endsWith(".mkv") -> 5
            else -> 0
        }
        val q = channel.quality.uppercase(Locale.ROOT)
        score += when {
            "4K" in q || "UHD" in q || "2160" in q -> 12
            "FHD" in q || "1080" in q -> 8
            "HD" in q || "720" in q -> 4
            else -> 0
        }
        if (channel.url.trim().startsWith("https://", ignoreCase = true)) score += 5
        if (!channel.logo.isNullOrBlank()) score += 5
        return score
    }

    private val ISO3_TO_LANG: Map<String, String> = mapOf(
        "ita" to "it", "italian" to "it", "italiano" to "it",
        "eng" to "en", "english" to "en", "inglese" to "en",
        "spa" to "es", "esp" to "es", "spanish" to "es", "spagnolo" to "es", "castellano" to "es", "lat" to "es", "latino" to "es",
        "fra" to "fr", "fre" to "fr", "french" to "fr", "francese" to "fr",
        "deu" to "de", "ger" to "de", "german" to "de", "deutsch" to "de", "tedesco" to "de",
        "por" to "pt", "portuguese" to "pt", "portoghese" to "pt", "pob" to "pt",
        "jpn" to "ja", "japanese" to "ja", "giapponese" to "ja",
        "kor" to "ko", "korean" to "ko", "coreano" to "ko",
        "ara" to "ar", "arabic" to "ar", "arabo" to "ar",
        "tur" to "tr", "turkish" to "tr", "turco" to "tr",
        "rus" to "ru", "russian" to "ru", "russo" to "ru",
        "pol" to "pl", "polish" to "pl", "polacco" to "pl",
        "nld" to "nl", "dut" to "nl", "dutch" to "nl", "olandese" to "nl",
        "ron" to "ro", "rum" to "ro", "romanian" to "ro", "rumeno" to "ro",
        "hin" to "hi", "hindi" to "hi", "indiano" to "hi",
        "zho" to "zh", "chi" to "zh", "chinese" to "zh", "cinese" to "zh",
    )

    fun detectVodFlagCodes(channel: IptvChannel): List<String> {
        val out = LinkedHashSet<String>(4)
        val rawLang = channel.language.trim().lowercase(Locale.ROOT)
        if (rawLang.isNotEmpty()) {
            rawLang.split(',', '/', '|', ';', ' ').forEach { tok ->
                val clean = tok.trim()
                if (clean.isNotEmpty()) {
                    val mappedCode = ISO3_TO_LANG[clean] ?: clean
                    val def = resolveLanguageDef(mappedCode)
                    if (def != null && def.flag.isNotBlank()) {
                        out += def.flag.lowercase(Locale.ROOT)
                    }
                }
            }
        }
        val nameAndCat = buildString {
            append(channel.name)
            if (channel.category.isNotBlank()) {
                append(' ')
                append(channel.category)
            }
        }
        for (def in LANGUAGES) {
            if (def.flag.isBlank()) continue
            if (def.tagRegex.containsMatchIn(nameAndCat)) {
                out += def.flag.lowercase(Locale.ROOT)
                if (out.size >= 5) break
            }
        }
        if (out.size < 5) {
            val cc = channel.country.trim().uppercase(Locale.ROOT).let { if (it == "UK") "GB" else it }
            if (cc.length == 2 && cc.all { it in 'A'..'Z' } && cc != "XX" && cc != "ZZ") {
                val langDef = LANGUAGES.firstOrNull { it.flag.isNotBlank() && cc in it.countries }
                val flag = langDef?.flag?.lowercase(Locale.ROOT) ?: cc.lowercase(Locale.ROOT)
                out += flag
            }
        }
        return out.take(5)
    }
}
