package it.casatv.server.core

import android.content.Context
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val MIX_NON_247_RE = Regex("""\[\s*no(?:t)?\s*24\s*/\s*7\s*]|\bno(?:t)?\s*24\s*/\s*7\b""", RegexOption.IGNORE_CASE)
private val MIX_4K_RE = Regex("""(?:\b4k\b|\b2160p\b|\buhd\b)""", RegexOption.IGNORE_CASE)

/**
 * Continuously probes random live IPTV channels in the background so MIX zapping
 * starts instantaneously with verified low-latency working streams instead of
 * making the user wait for sequential failed tunes.
 */
class MixBackgroundPool(
    private val context: Context,
    private val db: ServerDatabase,
    private val playlist: PlaylistManager,
) {
    data class VerifiedEntry(
        val channel: IptvChannel,
        val region: String,
        val verifiedAtMs: Long,
        val latencyMs: Long,
        val audioOnly: Boolean,
    )

    private val verifiedPool = ConcurrentHashMap<String, VerifiedEntry>()
    private val deadCache = ConcurrentHashMap<String, Long>()
    private val started = AtomicBoolean(false)
    private val bgRefillRunning = AtomicBoolean(false)
    private val regionCursor = AtomicInteger(0)
    @Volatile
    private var poolScope: CoroutineScope? = null

    fun invalidate() {
        verifiedPool.clear()
        deadCache.clear()
    }

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        poolScope = scope
        scope.launch(Dispatchers.IO) {
            delay(5_500L)
            while (isActive) {
                val importing = runCatching { PlaylistImportEngine.get(context).hasActiveImport() }.getOrDefault(false)
                if (importing) {
                    delay(8_000L)
                    continue
                }
                pruneCaches()
                val currentSize = verifiedPool.size
                if (currentSize < TARGET_POOL_SIZE) {
                    runCatching { refillRound(batchSize = 12, targetRegions = nextRoundRobinRegions()) }
                        .onFailure { Log.w("CasaMix", "Background Mix refill error: ${it.message}") }
                    delay(if (verifiedPool.size < 24) 1_500L else 4_500L)
                } else {
                    delay(22_000L)
                }
            }
        }
    }

    fun poolSize(): Int {
        pruneCaches()
        return verifiedPool.size
    }

    fun reportDead(channelId: String) {
        val clean = channelId.trim()
        if (clean.isEmpty()) return
        verifiedPool.remove(clean)
        deadCache[clean] = System.currentTimeMillis()
        if (deadCache.size > MAX_DEAD_CACHE) {
            val cutoff = System.currentTimeMillis() - DEAD_TTL_MS
            deadCache.entries.removeIf { it.value < cutoff }
        }
    }

    fun recordProbeResults(winners: List<IptvChannel>, deadIds: List<String>) {
        val now = System.currentTimeMillis()
        for (id in deadIds) {
            verifiedPool.remove(id)
            deadCache[id] = now
        }
        for (ch in winners) {
            if (!isPlayableMixLiveChannel(ch)) continue
            verifiedPool[ch.id] = VerifiedEntry(
                channel = ch,
                region = resolveMixRegion(ch.country) ?: "OTHER",
                verifiedAtMs = now,
                latencyMs = 450L,
                audioOnly = isLikelyAudioOnly(ch),
            )
        }
    }

    suspend fun getReadyChannels(
        regions: Set<String> = emptySet(),
        languages: Set<String> = emptySet(),
        genres: Set<String> = emptySet(),
        excludeIds: Set<String> = emptySet(),
        includeAudioOnly: Boolean = false,
        limit: Int = 8,
    ): List<IptvChannel> {
        pruneCaches()
        val normRegions = regions.map { it.uppercase(Locale.ROOT) }.filter { it.isNotBlank() && it != "ALL_WORLD" }.toSet()
        val normLangs = languages.map { it.lowercase(Locale.ROOT) }.filter { it.isNotBlank() }.toSet()
        val normGenres = genres.map { it.lowercase(Locale.ROOT) }.filter { it.isNotBlank() }.toSet()

        fun matchingFromPool(): List<VerifiedEntry> {
            return verifiedPool.values.filter { entry ->
                if (entry.channel.id in excludeIds || deadCache.containsKey(entry.channel.id)) return@filter false
                if (!includeAudioOnly && entry.audioOnly) return@filter false
                if (normRegions.isNotEmpty() && entry.region !in normRegions) return@filter false
                if (normLangs.isNotEmpty() && !matchesLanguages(entry.channel, normLangs)) return@filter false
                if (normGenres.isNotEmpty() && !matchesGenres(entry.channel, normGenres)) return@filter false
                true
            }
        }

        var matches = matchingFromPool()
        if (matches.size < minOf(limit, 3)) {
            val targetCountries = resolveTargetCountries(normRegions, normLangs)
            val candidates = db.sampleLiveChannelsForMix(targetCountries, limit = 60)
                .filter { ch ->
                    ch.id !in excludeIds &&
                        !verifiedPool.containsKey(ch.id) &&
                        !deadCache.containsKey(ch.id) &&
                        isPlayableMixLiveChannel(ch) &&
                        (includeAudioOnly || !isLikelyAudioOnly(ch)) &&
                        (normLangs.isEmpty() || matchesLanguages(ch, normLangs)) &&
                        (normGenres.isEmpty() || matchesGenres(ch, normGenres))
                }
                .take(14)
            if (candidates.isNotEmpty()) {
                probeAndStoreBatch(candidates, maxWinners = minOf(limit, 4))
                matches = matchingFromPool()
            }
        } else if (verifiedPool.size < TARGET_POOL_SIZE && bgRefillRunning.compareAndSet(false, true)) {
            val targetScope = poolScope
            if (targetScope != null && targetScope.isActive) {
                targetScope.launch(Dispatchers.IO) {
                    try {
                        refillRound(batchSize = 8, targetRegions = normRegions.ifEmpty { nextRoundRobinRegions() })
                    } finally {
                        bgRefillRunning.set(false)
                    }
                }
            } else {
                bgRefillRunning.set(false)
            }
        }

        val fastTier = matches.filter { it.latencyMs <= 900L }.shuffled()
        val mediumTier = matches.filter { it.latencyMs > 900L }.shuffled()
        val combined = fastTier + mediumTier
        if (normRegions.isNotEmpty() || combined.size <= limit) {
            return combined.take(limit).map { it.channel }
        }
        // Global Mix: cap per-country items so playback never starts stuck in one country.
        val picked = ArrayList<IptvChannel>(limit)
        val countryCounts = HashMap<String, Int>()
        val overflow = ArrayList<IptvChannel>()
        for (entry in combined) {
            val cc = entry.channel.country.trim().uppercase(Locale.ROOT).ifBlank { "XX" }
            val cnt = countryCounts[cc] ?: 0
            if (cnt < 2) {
                picked.add(entry.channel)
                countryCounts[cc] = cnt + 1
                if (picked.size >= limit) break
            } else {
                overflow.add(entry.channel)
            }
        }
        if (picked.size < limit) {
            for (ch in overflow) {
                picked.add(ch)
                if (picked.size >= limit) break
            }
        }
        return picked
    }

    private fun nextRoundRobinRegions(): Set<String> {
        val keys = REGION_COUNTRIES.keys.toList()
        if (keys.isEmpty()) return emptySet()
        val cur = regionCursor.getAndIncrement()
        val a = keys[Math.floorMod(cur, keys.size)]
        val b = keys[Math.floorMod(cur + 1, keys.size)]
        return setOf(a, b)
    }

    private suspend fun refillRound(batchSize: Int, targetRegions: Set<String>) {
        val targetCountries = if (targetRegions.isEmpty()) {
            null
        } else {
            targetRegions.flatMapTo(LinkedHashSet()) { REGION_COUNTRIES[it].orEmpty() }
        }
        val candidates = db.sampleLiveChannelsForMix(targetCountries, limit = batchSize * 3)
            .filter { ch ->
                !verifiedPool.containsKey(ch.id) &&
                    !deadCache.containsKey(ch.id) &&
                    isPlayableMixLiveChannel(ch)
            }
            .take(batchSize)
        if (candidates.isEmpty()) return
        probeAndStoreBatch(candidates, maxWinners = batchSize)
    }

    private suspend fun probeAndStoreBatch(candidates: List<IptvChannel>, maxWinners: Int) {
        if (candidates.isEmpty()) return
        val sem = Semaphore(6)
        val winnersCount = java.util.concurrent.atomic.AtomicInteger(0)
        supervisorScope {
            val jobs = candidates.map { ch ->
                async {
                    if (winnersCount.get() >= maxWinners) return@async
                    sem.withPermit {
                        if (winnersCount.get() >= maxWinners) return@withPermit
                        val latency = try {
                            playlist.probeMixSingleStreamWithLatency(ch.url)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val now = System.currentTimeMillis()
                        if (latency != null) {
                            verifiedPool[ch.id] = VerifiedEntry(
                                channel = ch,
                                region = resolveMixRegion(ch.country) ?: "OTHER",
                                verifiedAtMs = now,
                                latencyMs = latency,
                                audioOnly = isLikelyAudioOnly(ch),
                            )
                            if (winnersCount.incrementAndGet() >= maxWinners) {
                                this@supervisorScope.coroutineContext.cancelChildren()
                            }
                        } else {
                            deadCache[ch.id] = now
                        }
                    }
                }
            }
            jobs.forEach { runCatching { it.await() } }
        }
        trimPoolToMax()
    }

    private fun pruneCaches() {
        val now = System.currentTimeMillis()
        verifiedPool.entries.removeIf { now - it.value.verifiedAtMs > VERIFIED_TTL_MS }
        if (deadCache.size > MAX_DEAD_CACHE / 2) {
            deadCache.entries.removeIf { now - it.value > DEAD_TTL_MS }
        }
    }

    private fun trimPoolToMax() {
        if (verifiedPool.size <= MAX_POOL_SIZE) return
        val oldest = verifiedPool.entries.sortedBy { it.value.verifiedAtMs }.take(verifiedPool.size - MAX_POOL_SIZE)
        for (entry in oldest) verifiedPool.remove(entry.key)
    }

    private fun resolveTargetCountries(normRegions: Set<String>, normLangs: Set<String>): Set<String>? {
        val regSet = if (normRegions.isNotEmpty()) {
            normRegions.flatMapTo(LinkedHashSet()) { REGION_COUNTRIES[it].orEmpty() }
        } else {
            null
        }
        val langSet = if (normLangs.isNotEmpty()) {
            normLangs.flatMapTo(LinkedHashSet()) { LANG_DEFAULT_COUNTRIES[it].orEmpty() }
        } else {
            null
        }
        return when {
            regSet != null && langSet != null -> regSet.intersect(langSet).ifEmpty { regSet }
            regSet != null -> regSet
            langSet != null && langSet.isNotEmpty() -> langSet
            else -> null
        }
    }

    private fun isPlayableMixLiveChannel(ch: IptvChannel): Boolean {
        if (ch.isVod) return false
        val nameLow = ch.name.lowercase(Locale.ROOT)
        if ("[geo-blocked]" in nameLow || "geo-blocked" in nameLow || "geoblocked" in nameLow ||
            "Ⓖ" in ch.name || "[not 24/7]" in nameLow || "[ no 24/7]" in nameLow ||
            "[offline]" in nameLow || "(offline)" in nameLow || MIX_NON_247_RE.containsMatchIn(ch.name) ||
            MIX_4K_RE.containsMatchIn(ch.name) || ch.quality.equals("4K", ignoreCase = true) ||
            ch.quality.equals("UHD", ignoreCase = true)
        ) {
            return false
        }
        val clean = ch.url.trim()
        val low = clean.lowercase(Locale.ROOT)
        if (!low.startsWith("http://") && !low.startsWith("https://")) return false
        val path = low.substringBefore('?').substringBefore('#')
        if (path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi") ||
            path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".jpg") ||
            path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp") ||
            "/movie/" in low || "/series/" in low
        ) {
            return false
        }
        return true
    }

    private fun isLikelyAudioOnly(ch: IptvChannel): Boolean {
        val low = ch.url.trim().lowercase(Locale.ROOT)
        if (AUDIO_EXT_RE.containsMatchIn(low)) return true
        val isContainer = ".m3u8" in low || ".mpd" in low || ".ts" in low
        if (!isContainer && AUDIO_EP_RE.containsMatchIn(low)) return true
        val catLow = ch.category.lowercase(Locale.ROOT)
        if (("radio" in catLow || "irratia" in catLow) && "tv" !in catLow && "video" !in catLow && "tele" !in catLow) {
            return true
        }
        val q = ch.quality.trim().uppercase(Locale.ROOT)
        val hasVideoHint = q in VIDEO_QUALITIES || VIDEO_NAME_RE.containsMatchIn(ch.name)
        return !hasVideoHint && RADIO_NAME_RE.containsMatchIn(ch.name)
    }

    private fun resolveMixRegion(rawCountry: String): String? {
        val clean = rawCountry.trim().uppercase(Locale.ROOT).let { if (it == "UK") "GB" else it }
        return COUNTRY_TO_REGION[clean]
    }

    private fun matchesLanguages(ch: IptvChannel, normLangs: Set<String>): Boolean {
        val chLang = ch.language.trim().lowercase(Locale.ROOT)
        if (chLang.isNotEmpty()) {
            val tokens = chLang.split(';', ',', '/', '|').map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isNotEmpty()) {
                return normLangs.any { code ->
                    val aliases = LANG_ALIASES[code] ?: setOf(code)
                    tokens.any { tok -> tok in aliases }
                }
            }
        }
        val c = ch.country.trim().uppercase(Locale.ROOT).let { if (it == "UK") "GB" else it }
        if (c.length != 2) return false
        return normLangs.any { code -> c in LANG_DEFAULT_COUNTRIES[code].orEmpty() }
    }

    private fun matchesGenres(ch: IptvChannel, normGenres: Set<String>): Boolean {
        val catLow = ch.category.lowercase(Locale.ROOT)
        val nameLow = " ${ch.name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()} "
        return normGenres.any { gId ->
            val kws = GENRE_KEYWORDS[gId] ?: return@any false
            kws.any { kw -> kw in catLow || " $kw " in nameLow }
        }
    }

    companion object {
        private const val TARGET_POOL_SIZE = 110
        private const val MAX_POOL_SIZE = 220
        private const val VERIFIED_TTL_MS = 20 * 60_000L
        private const val DEAD_TTL_MS = 30 * 60_000L
        private const val MAX_DEAD_CACHE = 4000

        private val AUDIO_EXT_RE = Regex("""\.(mp3|aac|aacp|ogg|oga|opus|flac|m4a|wav|wma|pls)(\?|#|$)""", RegexOption.IGNORE_CASE)
        private val AUDIO_EP_RE = Regex("""(/stream(\?|/|$)|;stream|\.(mp3|aac|ogg|opus|flac|m4a)(\?|$))""", RegexOption.IGNORE_CASE)
        private val VIDEO_QUALITIES = setOf("HD", "FHD", "4K", "UHD", "1080P", "720P")
        private val VIDEO_NAME_RE = Regex("""(^|[\s\-_|/(\[.])(tv|television|televisión|tele|vision|video|canal|channel|radiotv|hd|uhd|fhd|4k|1080p|720p|480p|360p)([\s\-_|/)\].]|$)""", RegexOption.IGNORE_CASE)
        private val RADIO_NAME_RE = Regex("""(^|[\s\-_|/(\[])(fm|am|radio|rádio|webradio|dab\+?)([\s\-_|/)\].]|$)""", RegexOption.IGNORE_CASE)

        val REGION_COUNTRIES: Map<String, Set<String>> = mapOf(
            "EUROPE" to setOf("AL","AD","AT","BE","BA","BG","BY","CH","CY","CZ","DE","DK","EE","ES","FI","FR","GB","UK","GR","HR","HU","IE","IS","IT","LI","LT","LU","LV","MC","MD","ME","MK","MT","NL","NO","PL","PT","RO","RS","SE","SI","SK","SM","UA","VA","XK","FO","GI"),
            "NORTH_AMERICA" to setOf("US","CA","BM","GL","PM"),
            "CENTRAL_AMERICA" to setOf("MX","BZ","CR","CU","DO","GT","HN","HT","JM","NI","PA","PR","SV","BS","BB","TT","AG","AW","CW","DM","GD","GP","KN","LC","MQ","SX","VC","VG","VI","KY","TC","BQ","AI","MS","BL","MF"),
            "SOUTH_AMERICA" to setOf("AR","BO","BR","CL","CO","EC","FK","GF","GY","PE","PY","SR","UY","VE"),
            "NORTH_AFRICA" to setOf("DZ","EG","LY","MA","SD","TN","EH"),
            "SOUTH_AFRICA" to setOf("AO","BF","BI","BJ","BW","CD","CF","CG","CI","CM","CV","DJ","ER","ET","GA","GH","GM","GN","GQ","GW","KE","KM","LR","LS","MG","ML","MR","MU","MW","MZ","NA","NE","NG","RE","RW","SC","SL","SN","SO","SS","ST","SZ","TD","TG","TZ","UG","YT","ZA","ZM","ZW"),
            "MIDDLE_EAST" to setOf("AE","BH","IQ","IR","IL","JO","KW","LB","OM","PS","QA","SA","SY","YE"),
            "EURASIA" to setOf("AM","AZ","GE","KZ","KG","RU","TJ","TM","TR","UZ"),
            "ASIA" to setOf("AF","BD","BN","BT","CN","HK","ID","IN","JP","KH","KP","KR","LA","LK","MM","MN","MO","MV","MY","NP","PH","PK","SG","TH","TL","TW","VN"),
            "AUSTRALIA" to setOf("AU","NZ"),
            "OCEANIA" to setOf("AS","CK","FJ","FM","GU","KI","MH","MP","NC","NF","NR","NU","PF","PG","PN","PW","SB","TK","TO","TV","VU","WF","WS"),
        )

        private val COUNTRY_TO_REGION: Map<String, String> = buildMap {
            REGION_COUNTRIES.forEach { (reg, codes) ->
                codes.forEach { put(it, reg) }
            }
        }

        private val LANG_ALIASES: Map<String, Set<String>> = mapOf(
            "sqi" to setOf("sqi", "sq", "alb", "albanese", "albanian", "shqip"),
            "ara" to setOf("ara", "ar", "arabo", "arabic"),
            "bul" to setOf("bul", "bg", "bulgaro", "bulgarian"),
            "ces" to setOf("ces", "cze", "cs", "ceco", "czech"),
            "zho" to setOf("zho", "chi", "zh", "cmn", "yue", "cinese", "chinese"),
            "kor" to setOf("kor", "ko", "coreano", "korean"),
            "hrv" to setOf("hrv", "hr", "scr", "croato", "croatian"),
            "dan" to setOf("dan", "da", "danese", "danish"),
            "heb" to setOf("heb", "he", "iw", "ebraico", "hebrew"),
            "tgl" to setOf("tgl", "tl", "fil", "tagalog", "filipino"),
            "fin" to setOf("fin", "fi", "finlandese", "finnish"),
            "fra" to setOf("fra", "fre", "fr", "francese", "french"),
            "kat" to setOf("kat", "geo", "ka", "georgiano", "georgian"),
            "jpn" to setOf("jpn", "ja", "giapponese", "japanese"),
            "ell" to setOf("ell", "gre", "el", "greco", "greek"),
            "hin" to setOf("hin", "hi", "hindi"),
            "ind" to setOf("ind", "id", "in", "indonesiano", "indonesian"),
            "eng" to setOf("eng", "en", "inglese", "english"),
            "ita" to setOf("ita", "it", "italiano", "italian"),
            "nor" to setOf("nor", "no", "nob", "nno", "norvegese", "norwegian"),
            "nld" to setOf("nld", "dut", "nl", "olandese", "dutch"),
            "fas" to setOf("fas", "per", "fa", "persiano", "persian", "farsi"),
            "pol" to setOf("pol", "pl", "polacco", "polish"),
            "por" to setOf("por", "pt", "portoghese", "portuguese"),
            "ron" to setOf("ron", "rum", "ro", "rumeno", "romanian"),
            "rus" to setOf("rus", "ru", "russo", "russian"),
            "srp" to setOf("srp", "sr", "scc", "serbo", "serbian"),
            "slk" to setOf("slk", "slo", "sk", "slovacco", "slovak"),
            "spa" to setOf("spa", "es", "spagnolo", "spanish", "castellano"),
            "swe" to setOf("swe", "sv", "svedese", "swedish"),
            "deu" to setOf("deu", "ger", "de", "tedesco", "german"),
            "tha" to setOf("tha", "th", "thai", "thailandese"),
            "tur" to setOf("tur", "tr", "turco", "turkish"),
            "ukr" to setOf("ukr", "uk", "ucraino", "ukrainian"),
            "hun" to setOf("hun", "hu", "ungherese", "hungarian"),
            "urd" to setOf("urd", "ur", "urdu"),
            "vie" to setOf("vie", "vi", "vietnamita", "vietnamese"),
        )

        private val LANG_DEFAULT_COUNTRIES: Map<String, Set<String>> = mapOf(
            "sqi" to setOf("AL", "XK"),
            "ara" to setOf("AE", "BH", "DZ", "EG", "IQ", "JO", "KW", "LB", "LY", "MA", "OM", "PS", "QA", "SA", "SD", "SY", "TN", "YE", "MR"),
            "bul" to setOf("BG"),
            "ces" to setOf("CZ"),
            "zho" to setOf("CN", "TW", "HK", "MO"),
            "kor" to setOf("KR", "KP"),
            "hrv" to setOf("HR"),
            "dan" to setOf("DK", "FO", "GL"),
            "heb" to setOf("IL"),
            "tgl" to setOf("PH"),
            "fin" to setOf("FI"),
            "fra" to setOf("FR", "MC", "BF", "CI", "CM", "CD", "CG", "GA", "GN", "ML", "NE", "SN", "TG", "PF", "NC", "GF", "GP", "MQ", "HT"),
            "kat" to setOf("GE"),
            "jpn" to setOf("JP"),
            "ell" to setOf("GR", "CY"),
            "hin" to setOf("IN"),
            "ind" to setOf("ID"),
            "eng" to setOf("US", "GB", "UK", "CA", "AU", "NZ", "IE", "JM", "BS", "BB", "TT", "BZ", "GY", "NG", "GH", "KE", "ZA", "SG", "PR"),
            "ita" to setOf("IT", "SM", "VA"),
            "nor" to setOf("NO"),
            "nld" to setOf("NL", "AW", "CW", "SR"),
            "fas" to setOf("IR", "AF", "TJ"),
            "pol" to setOf("PL"),
            "por" to setOf("PT", "BR", "AO", "MZ", "CV", "GW", "TL"),
            "ron" to setOf("RO", "MD"),
            "rus" to setOf("RU", "BY", "KZ", "KG"),
            "srp" to setOf("RS", "ME", "BA"),
            "slk" to setOf("SK"),
            "spa" to setOf("ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "GT", "CU", "BO", "DO", "HN", "PY", "SV", "NI", "CR", "PA", "UY", "PR"),
            "swe" to setOf("SE"),
            "deu" to setOf("DE", "AT", "CH", "LI", "LU"),
            "tha" to setOf("TH"),
            "tur" to setOf("TR"),
            "ukr" to setOf("UA"),
            "hun" to setOf("HU"),
            "urd" to setOf("PK"),
            "vie" to setOf("VN"),
        )

        private val GENRE_KEYWORDS: Map<String, List<String>> = mapOf(
            "action" to listOf("action", "azione", "acción", "accion", "martial", "combat"),
            "adventure" to listOf("adventure", "avventura", "aventuras", "aventura"),
            "animation" to listOf("animation", "animazione", "animación", "cartoon"),
            "anime" to listOf("anime", "manga"),
            "auto" to listOf("auto", "motor", "motori", "motorsport", "racing"),
            "kids" to listOf("kids", "children", "bambini", "infantil", "junior", "ragazzi", "kinder"),
            "movies" to listOf("movie", "cinema", "cine", "film"),
            "classic" to listOf("classic", "classici", "retro", "vintage"),
            "comedy" to listOf("comedy", "commedia", "comedia", "humor"),
            "cooking" to listOf("cooking", "cucina", "cocina", "food", "gastronomia"),
            "crime" to listOf("crime", "crimine", "crimen", "noir", "detective"),
            "culture" to listOf("culture", "cultura", "arte", "arts", "theatre"),
            "documentary" to listOf("documentary", "documentari", "documentales", "docu", "wildlife"),
            "drama" to listOf("drama", "dramma", "drammatico", "melodrama"),
            "business" to listOf("business", "economia", "finance", "finanza"),
            "education" to listOf("education", "educazione", "scuola", "learning"),
            "family" to listOf("family", "famiglia", "familia"),
            "fantasy" to listOf("fantasy", "fantasia", "mythology"),
            "scifi" to listOf("sci-fi", "scifi", "fantascienza", "science-fiction", "space"),
            "general" to listOf("general", "generali", "generale", "variedades"),
            "war" to listOf("war", "guerra", "belico", "military"),
            "horror" to listOf("horror", "orrore", "terror", "paranormal"),
            "entertainment" to listOf("entertainment", "intrattenimento", "entretenimiento", "show", "reality"),
            "legislative" to listOf("legislative", "parliament", "government", "istituzionale", "senato"),
            "lifestyle" to listOf("lifestyle", "home", "living", "wellness"),
            "local" to listOf("local", "locali", "regional", "regionali"),
            "weather" to listOf("weather", "meteo", "clima", "forecast"),
            "mystery" to listOf("mystery", "mistero", "misterio", "giallo"),
            "fashion" to listOf("fashion", "moda"),
            "music" to listOf("music", "musica", "música", "hits", "concert", "opera"),
            "outdoor" to listOf("outdoor", "nature", "natura", "wildlife"),
            "news" to listOf("news", "informativos", "informazione", "noticias", "notizie", "aktuell"),
            "religious" to listOf("religious", "religione", "religión", "religiosos", "christian", "catholic", "gospel"),
            "romance" to listOf("romance", "romantico", "romántico", "love", "sentimental"),
            "science" to listOf("science", "scienza", "ciencia", "tech"),
            "series" to listOf("series", "serie", "telenovelas", "doramas"),
            "shop" to listOf("shop", "shopping", "teleshop"),
            "sports" to listOf("sport", "sports", "deporte", "football", "soccer", "calcio", "tennis"),
            "history" to listOf("history", "storico", "storia", "historia", "biography"),
            "thriller" to listOf("thriller", "suspense", "psychological"),
            "travel" to listOf("travel", "viaggi", "viajes", "tourism"),
            "western" to listOf("western", "oeste", "cowboy"),
        )
    }
}
