package it.casatv.server.server

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import it.casatv.server.BuildConfig
import it.casatv.server.core.ClientRow
import it.casatv.server.core.DiagnosticCodes
import it.casatv.server.core.DiagnosticLogInput
import it.casatv.server.core.IptvChannel
import it.casatv.server.core.LogoCache
import it.casatv.server.core.M3uParser
import it.casatv.server.core.MixBackgroundPool
import it.casatv.server.core.NetworkUtils
import it.casatv.server.core.PlaylistError
import it.casatv.server.core.PlaylistException
import it.casatv.server.core.PlaylistManager
import it.casatv.server.core.Prefs
import it.casatv.server.core.ProfileRow
import it.casatv.server.core.PROFILE_AVATARS
import it.casatv.server.core.PROFILE_LANGUAGES
import it.casatv.server.core.ServerDatabase
import it.casatv.server.core.ServerDiagnosticLogger
import it.casatv.server.core.LiveRelayManager
import it.casatv.server.core.RelayTuneResult
import it.casatv.server.core.TimeshiftManager
import it.casatv.server.core.TimeshiftSessionSnapshot
import it.casatv.server.core.VodTaxonomy
import it.casatv.server.core.countryFlag
import it.casatv.server.core.countryName
import it.casatv.server.core.languageLabel
import it.casatv.server.core.languageName
import it.casatv.server.core.textOf
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * HTTP server exposed by the phone. It is wire-compatible with the CasaTV
 * Android TV client (profiles, config, home, channels, system) for high-performance
 * live IPTV streaming.
 */
class CasaHttpServer(
    private val context: Context,
    private val nameProvider: () -> String,
    private val languageProvider: () -> String,
    port: Int = Prefs.port(context),
) : NanoHTTPD("0.0.0.0", port) {

    private val db = ServerDatabase.get(context)
    private val playlist = PlaylistManager(context)
    private val mixPool = MixBackgroundPool(context, db, playlist)
    private val logoCache = LogoCache.getInstance(context)
    private val liveRelay = LiveRelayManager(context)
    private val timeshift = TimeshiftManager(context, liveRelay)
    private val diag = ServerDiagnosticLogger.get(context)
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vodHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
    private val clientTouchCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val vodResolvedUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val vodRemuxer = it.casatv.server.core.VodHlsRemuxer()
    private val vodPosterResolver = it.casatv.server.core.VodPosterResolver()
    private val staticAssetCache = ConcurrentHashMap<String, ByteArray>()
    private val startedAtSeconds = System.currentTimeMillis() / 1000
    private var lastCpuTicks = -1L
    private var lastCpuAt = 0L
    @Volatile
    private var cachedAssetTvApkVersion: String? = null
    @Volatile
    private var assetTvApkChecked: Boolean = false
    private val startedOnce = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        liveRelay.attachDatabase(db)
        runCatching { setAsyncRunner(BoundedAsyncRunner()) }
        bgScope.launch {
            runCatching {
                db.ensureGuestProfile()
                db.clearGuestProfile()
            }
        }
    }

    override fun start(timeout: Int, daemon: Boolean) {
        super.start(timeout, daemon)
        if (startedOnce.compareAndSet(false, true)) {
            mixPool.start(bgScope)
            bgScope.launch {
                runCatching { liveRelay.clearStaleFiles() }
                runCatching { db.pruneOldDiagnosticLogs() }
                runCatching { db.ensurePerformanceIndexes() }
                runCatching { it.casatv.server.core.PlaylistImportEngine.get(context) }
                if (Prefs.countryReclassVersion(context) < 2) {
                    runCatching {
                        val updated = db.reclassifyChannelCountries()
                        Prefs.setCountryReclassVersion(context, 2)
                        android.util.Log.i("CasaTV", "Country reclassification v2 updated $updated channels")
                    }
                }
                runCatching { prewarmLocalCatalogArtwork() }
            }
        }
    }

    private fun prewarmLocalCatalogArtwork() {
        bgScope.launch {
            runCatching {
                kotlinx.coroutines.delay(4_000L)
                val profile = db.defaultProfile(languageProvider())
                val favIds = db.favoriteIds(profile.id)
                val (liveSlice, _) = db.queryChannels(
                    country = profile.country.takeIf { it.isNotBlank() },
                    dedupLive = true,
                    limit = 250,
                    offset = 0,
                )
                val favChannels = favIds.take(100).let { ids ->
                    if (ids.isEmpty()) emptyList() else db.getChannelsByIds(ids).values.toList()
                }
                val liveLogos = (favChannels.filter { !it.isVod } + liveSlice)
                    .mapNotNull { it.logo?.takeIf { u -> u.isNotBlank() } }
                    .distinct()
                if (liveLogos.isNotEmpty()) {
                    logoCache.prefetchUrls(liveLogos, bgScope)
                }

                val (moviesSlice, _) = db.queryChannels(contentType = "movie", dedupVod = true, limit = 80, offset = 0)
                val (seriesSlice, _) = db.queryChannels(contentType = "series", dedupVod = true, limit = 80, offset = 0)
                prewarmVodArtworkAsync(favChannels.filter { it.isVod } + moviesSlice + seriesSlice)
            }
        }
    }

    private fun prewarmVodArtworkAsync(items: List<IptvChannel>) {
        if (items.isEmpty()) return
        val vodList = items.filter { it.isVod }.take(120)
        if (vodList.isEmpty()) return
        bgScope.launch {
            for (ch in vodList) {
                runCatching {
                    val raw = ch.logo?.takeIf { it.isNotBlank() }
                    val cached = raw?.let { logoCache.getOrFetch(it, interactive = false) }
                    if (cached == null || it.casatv.server.core.VodPosterResolver.isLandscapeStill(cached.bytes)) {
                        val sibling = if (cached == null) db.findSiblingVodLogo(ch) else null
                        val sibImg = sibling?.let { logoCache.getOrFetch(it, interactive = false) }
                        if (sibImg != null && !it.casatv.server.core.VodPosterResolver.isLandscapeStill(sibImg.bytes)) {
                            db.updateChannelLogo(ch.id, sibling)
                        } else if (!vodPosterResolver.isRecentlyMissed(db, ch)) {
                            val resolved = vodPosterResolver.resolvePosterUrl(db, ch)
                            if (!resolved.isNullOrBlank()) {
                                logoCache.getOrFetch(resolved, interactive = false)
                            }
                        }
                    }
                }
                kotlinx.coroutines.delay(35L)
            }
        }
    }

    override fun createClientHandler(finalAccept: java.net.Socket, inputStream: InputStream): ClientHandler {
        runCatching { finalAccept.tcpNoDelay = true }
        val pb = java.io.PushbackInputStream(inputStream, 1)
        return object : ClientHandler(pb, finalAccept) {
            override fun run() {
                val first = try {
                    pb.read()
                } catch (_: Exception) {
                    -1
                }
                if (first < 0) {
                    runCatching { finalAccept.close() }
                    asyncRunner.closed(this)
                    return
                }
                if (first == 0x16) {
                    // Reject TLS ClientHello immediately so Safari/Brave HTTPS-Upgrade falls back to http:// in < 5ms.
                    runCatching {
                        val body = "Plain HTTP required: use http:// instead.\n".toByteArray(Charsets.US_ASCII)
                        val respHeader = (
                            "HTTP/1.1 400 Bad Request\r\n" +
                                "Content-Type: text/plain; charset=utf-8\r\n" +
                                "Connection: close\r\n" +
                                "Content-Length: ${body.size}\r\n\r\n"
                            ).toByteArray(Charsets.US_ASCII)
                        val out = finalAccept.getOutputStream()
                        out.write(respHeader)
                        out.write(body)
                        out.flush()
                    }
                    runCatching { finalAccept.close() }
                    asyncRunner.closed(this)
                    return
                }
                runCatching { pb.unread(first) }.onFailure {
                    runCatching { finalAccept.close() }
                    asyncRunner.closed(this)
                    return
                }
                super.run()
            }
        }
    }

    override fun useGzipWhenAccepted(r: Response): Boolean {
        if (r.getHeader("x-no-gzip") == "1") return false
        return super.useGzipWhenAccepted(r)
    }

    override fun stop() {
        runCatching { vodRemuxer.shutdown() }
        runCatching { timeshift.shutdown() }
        runCatching { liveRelay.shutdown() }
        runCatching { bgScope.cancel() }
        super.stop()
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) return noContent()
        val path = session.uri.substringBefore('?').ifBlank { "/" }
        return try {
            when {
                path == "/healthz" -> health()
                path == "/favicon.ico" -> serveStaticOrHtml(session, "favicon.svg")
                path.startsWith("/logo/") -> serveLogo(session, path.removePrefix("/logo/"))
                path.startsWith("/api/") -> api(session, path.removePrefix("/api").trim('/'))
                else -> serveStaticOrHtml(session, path)
            }
        } catch (error: Exception) {
            errorResponse(Response.Status.INTERNAL_ERROR, error.message ?: "Errore interno")
        }
    }

    private fun serveStaticOrHtml(session: IHTTPSession, rawPath: String): Response {
        val clean = rawPath.removePrefix("/").ifBlank { "index.html" }
        if (clean.contains("..")) {
            return errorResponse(Response.Status.FORBIDDEN, "Percorso non valido")
        }
        val candidate = if (clean.contains('.')) clean else "index.html"
        return try {
            val assetPath = "web/$candidate"
            val bytes = staticAssetCache[assetPath] ?: context.assets.open(assetPath).use { it.readBytes() }.also {
                staticAssetCache[assetPath] = it
            }
            val mime = when {
                candidate.endsWith(".html") -> "text/html; charset=utf-8"
                candidate.endsWith(".css") -> "text/css; charset=utf-8"
                candidate.endsWith(".js") -> "application/javascript; charset=utf-8"
                candidate.endsWith(".svg") -> "image/svg+xml"
                candidate.endsWith(".png") -> "image/png"
                candidate.endsWith(".json") -> "application/json; charset=utf-8"
                else -> "application/octet-stream"
            }
            val cacheHeader = if (candidate.startsWith("vendor/") || candidate.startsWith("flags/")) {
                "public, max-age=604800, immutable"
            } else {
                "no-cache"
            }
            newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Cache-Control", cacheHeader)
            }
        } catch (_: Exception) {
            if (clean.contains('.')) {
                errorResponse(Response.Status.NOT_FOUND, "Risorsa non trovata")
            } else {
                htmlPage(session)
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Routing                                                             */
    /* ------------------------------------------------------------------ */

    private fun api(session: IHTTPSession, path: String): Response {
        if (!path.startsWith("relay/") || path == "relay/tune") {
            touchClientFromHeaders(session)
        }
        val parts = path.split('/').filter { it.isNotBlank() }
        return when (parts.firstOrNull()) {
            null -> errorResponse(Response.Status.NOT_FOUND, "Endpoint non trovato")
            "config" -> config(session)
            "facets" -> facets(session)
            "profiles" -> profiles(session, parts)
            "home" -> home(session)
            "channels" -> channels(session, parts)
            "playlist" -> playlistRoutes(session, parts)
            "playlists" -> playlistsRoutes(session, parts)
            "system" -> system(session)
            "version" -> version(session)
            "update" -> updateRoutes(session, parts)
            "clients" -> clients(session, parts)
            "epg" -> epgRoutes(session, parts)
            "sync" -> syncRoutes(session, parts)
            "relay" -> relayRoutes(session, parts)
            "vod" -> vodRoutes(session, parts)
            "mix" -> mixRoutes(session, parts)
            "timeshift" -> timeshiftRoutes(session, parts)
            "recordings" -> recordingsRoutes(session, parts)
            "diagnostics" -> diagnosticsRoutes(session, parts)
            "refresh", "scan" -> maintenance(session, parts)
            "settings" -> settingsRoutes(session, parts)
            else -> errorResponse(Response.Status.NOT_FOUND, "Endpoint non trovato")
        }
    }

    private fun touchClientFromHeaders(session: IHTTPSession) {
        val deviceId = session.header("x-device-id").trim()
        if (deviceId.isBlank()) return
        val state = session.header("x-playback-state").ifBlank { "idle" }
        val channel = session.header("x-playback-channel")
        val profileId = session.header("x-profile-id")
        val signature = "$state|$channel|$profileId"
        val now = System.currentTimeMillis()
        val cached = clientTouchCache[deviceId]
        if (cached != null && cached.first == signature && now - cached.second < 10_000L) {
            return
        }
        clientTouchCache[deviceId] = signature to now
        val deviceName = session.header("x-device-name").ifBlank { "Android TV" }
        val platform = session.header("x-device-platform").ifBlank { "Android TV" }
        val appVersion = session.header("x-app-version")
        val ip = session.remoteIpAddress ?: ""
        val fallbackLang = if (profileId.isBlank()) languageProvider() else ""
        bgScope.launch {
            runCatching {
                val resolvedProfileId = profileId.ifBlank { db.defaultProfile(fallbackLang).id }
                db.touchClient(
                    id = deviceId,
                    name = deviceName,
                    platform = platform,
                    appVersion = appVersion,
                    profileId = resolvedProfileId,
                    state = state,
                    channel = channel,
                    ip = ip,
                )
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Core CasaTV API                                                     */
    /* ------------------------------------------------------------------ */

    private fun suppliedAdminPin(session: IHTTPSession): String =
        session.header("x-admin-pin").ifBlank { session.parm("pin") }.trim()

    private fun isAdminAuthorized(session: IHTTPSession): Boolean =
        suppliedAdminPin(session) == Prefs.adminPin(context)

    private fun requireAdmin(session: IHTTPSession): Response? {
        if (isAdminAuthorized(session)) return null
        return jsonResponse(
            buildJsonObject {
                put("ok", false)
                put("error", "Password amministratore richiesta")
                put("requiresAdminAuth", true)
                put("mustChangePassword", Prefs.isAdminPinDefault(context))
            },
            Response.Status.UNAUTHORIZED,
        )
    }

    private fun health(): Response {
        val summary = db.fastCatalogStatsSummary()
        return jsonResponse(
            buildJsonObject {
                put("ok", true)
                put("name", nameProvider())
                put("version", BuildConfig.VERSION_NAME)
                put("channels", summary.liveChannels)
                put("streams", summary.liveStreams)
                put("movies", summary.movies)
                put("series", summary.series)
                put("vodTotal", summary.movies + summary.series)
                put("totalItems", summary.totalItems)
                put("epg", false)
            },
        )
    }

    private fun facetsJson(lang: String): JsonObject = buildJsonObject {
        putJsonArray("countries") {
            db.channelCountsByCountry()
                .map { (code, count) -> Triple(code, countryName(code, lang), count) }
                .sortedBy { it.second.lowercase(java.util.Locale.ROOT) }
                .forEach { (code, name, count) ->
                    add(
                        buildJsonObject {
                            put("code", code)
                            put("name", name)
                            put("flag", countryFlag(code))
                            put("count", count)
                        },
                    )
                }
        }
        putJsonArray("categories") {
            db.channelCountsByCategory().forEach { (category, count) ->
                add(
                    buildJsonObject {
                        put("id", category)
                        put("name", categoryName(category, lang))
                        put("count", count)
                    },
                )
            }
        }
        putJsonArray("languages") {
            val normalizedLangs = LinkedHashMap<String, Int>()
            db.channelCountsByLanguage().forEach { (rawCode, count) ->
                val code = M3uParser.normalizeLanguage(rawCode)
                if (code.isNotBlank()) {
                    normalizedLangs[code] = (normalizedLangs[code] ?: 0) + count
                }
            }
            normalizedLangs.entries
                .sortedByDescending { it.value }
                .forEach { (code, count) ->
                    add(
                        buildJsonObject {
                            put("code", code)
                            put("name", languageName(code, lang))
                            put("count", count)
                        },
                    )
                }
        }
    }

    private fun facets(session: IHTTPSession): Response {
        val lang = activeProfile(session).language
        return jsonResponse(facetsJson(lang))
    }

    private fun config(session: IHTTPSession): Response {
        val profile = activeProfile(session)
        val lang = profile.language
        val summary = db.catalogStatsSummary()
        val favorites = db.favoriteIds(profile.id).size
        val base = baseUrl(session)
        return jsonResponse(
            buildJsonObject {
                put("name", nameProvider())
                put("version", BuildConfig.VERSION_NAME)
                put("serverTime", System.currentTimeMillis() / 1000)
                put("timeshiftMaxHours", Prefs.timeshiftMaxHours(context))
                put("serverRelayEnabled", Prefs.serverRelayEnabled(context))
                put("baseUrl", base)
                putJsonArray("urls") {
                    NetworkUtils.addresses().forEach { address ->
                        add(
                            buildJsonObject {
                                put("name", address.label)
                                put("address", address.address)
                                put("url", "http://${address.address}:$listeningPort")
                            },
                        )
                    }
                }
                putJsonObject("stats") {
                    put("channels", summary.liveChannels)
                    put("allChannels", summary.liveChannels)
                    put("streams", summary.liveStreams)
                    put("onlineStreams", summary.liveStreams)
                    put("movies", summary.movies)
                    put("series", summary.series)
                    put("vodTotal", summary.movies + summary.series)
                    put("totalItems", summary.totalItems)
                    put("favorites", favorites)
                    put("hidden", 0)
                    put("epgPrograms", 0)
                    put("countries", summary.countries)
                }
                put("facets", facetsJson(lang))
                put("defaultCountry", profile.country)
                put("activeProfile", profileJson(profile, lang))
                putJsonObject("epg") {
                    put("sources", 0)
                    put("programs", 0)
                    put("matchedChannels", 0)
                    put("active", false)
                }
                put("adminDefaultPin", Prefs.isAdminPinDefault(context))
                putJsonObject("catalog") {
                    val adminOk = isAdminAuthorized(session)
                    val srcRaw = Prefs.playlistSource(context)
                    val cleanSrc = if (!adminOk && (srcRaw.startsWith("http://", true) || srcRaw.startsWith("https://", true))) {
                        "playlist"
                    } else {
                        srcRaw.ifBlank { "playlist" }
                    }
                    put("source", cleanSrc)
                    put("defaultSource", if (adminOk) Prefs.playlistUrl(context) else "")
                    put(
                        "lastIngest",
                        Prefs.playlistLastImport(context).takeIf { it > 0 }?.let { JsonPrimitive(it) } ?: JsonNull,
                    )
                }
            },
        )
    }

    private fun home(session: IHTTPSession): Response {
        val profile = activeProfile(session)
        val lang = profile.language
        val strings = textOf(lang)
        val base = baseUrl(session)
        val favorites = db.favoriteIds(profile.id)
        val recentIds = db.recentIds(profile.id, 20)
        val mostWatchedIds = db.mostWatchedIds(profile.id, 16)
        val lookupIds = (favorites.take(20) + recentIds + mostWatchedIds).distinct()
        val byId = db.getChannelsByIds(lookupIds)

        val rows = buildJsonArray {
            if (favorites.isNotEmpty()) {
                val favJson = buildJsonArray {
                    favorites.asSequence()
                        .mapNotNull { byId[it] }
                        .take(20)
                        .forEach { ch ->
                            add(iptvChannelJson(ch, ch.number, lang, true, base))
                        }
                }
                if (favJson.isNotEmpty()) {
                    add(
                        buildJsonObject {
                            put("id", "favorites")
                            put("title", strings.t("Preferiti", "Favorites", "Favoritos"))
                            put("type", "channels")
                            put("total", favorites.size)
                            put("channels", favJson)
                        },
                    )
                }
            }

            if (mostWatchedIds.isNotEmpty()) {
                val mostWatchedJson = buildJsonArray {
                    mostWatchedIds.forEach { id ->
                        val ch = byId[id]
                        if (ch != null) {
                            add(iptvChannelJson(ch, ch.number, lang, ch.id in favorites, base))
                        }
                    }
                }
                if (mostWatchedJson.isNotEmpty()) {
                    add(
                        buildJsonObject {
                            put("id", "most-watched")
                            put("title", strings.t("I tuoi più visti", "Most Watched", "Más vistos"))
                            put("subtitle", strings.t("I canali che guardi più spesso", "Channels you watch most often", "Los canales que ves con más frecuencia"))
                            put("type", "channels")
                            put("total", mostWatchedJson.size)
                            put("channels", mostWatchedJson)
                        },
                    )
                }
            }

            if (recentIds.isNotEmpty()) {
                val recentJson = buildJsonArray {
                    recentIds.forEach { id ->
                        val ch = byId[id]
                        if (ch != null) {
                            add(iptvChannelJson(ch, ch.number, lang, ch.id in favorites, base))
                        }
                    }
                }
                if (recentJson.isNotEmpty()) {
                    add(
                        buildJsonObject {
                            put("id", "recent")
                            put("title", strings.t("Continua a guardare", "Continue watching", "Seguir viendo"))
                            put("type", "channels")
                            put("total", recentJson.size)
                            put("channels", recentJson)
                        },
                    )
                }
            }

            val (countryChannels, countryTotal) = db.queryChannels(country = profile.country, limit = 20)
            if (countryChannels.isNotEmpty()) {
                val cName = countryName(profile.country, lang)
                add(
                    buildJsonObject {
                        put("id", "recommended")
                        put("title", strings.t("Consigliati per te", "Recommended for you", "Recomendado para ti"))
                        put("subtitle", strings.t("Scelti per $cName", "Picked for $cName", "Elegido para $cName"))
                        put("type", "channels")
                        put("total", countryTotal)
                        putJsonArray("channels") {
                            countryChannels.forEach { channel ->
                                add(
                                    iptvChannelJson(
                                        channel = channel,
                                        number = channel.number,
                                        language = lang,
                                        favorite = channel.id in favorites,
                                        baseUrl = base,
                                    ),
                                )
                            }
                        }
                    },
                )
            }

            db.channelCountsByCategory()
                .take(6)
                .forEach { (category, catTotal) ->
                    val (catChannels, _) = db.queryChannels(category = category, limit = 20)
                    if (catChannels.isNotEmpty()) {
                        add(
                            buildJsonObject {
                                put("id", "category:$category")
                                put("title", categoryName(category, lang))
                                put(
                                    "subtitle",
                                    strings.t(
                                        "$catTotal canali dalla playlist",
                                        "$catTotal channels from your playlist",
                                        "$catTotal canales de tu lista",
                                    ),
                                )
                                put("type", "channels")
                                put("total", catTotal)
                                putJsonArray("channels") {
                                    catChannels.forEach { channel ->
                                        add(
                                            iptvChannelJson(
                                                channel = channel,
                                                number = channel.number,
                                                language = lang,
                                                favorite = channel.id in favorites,
                                                baseUrl = base,
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
        }
        return jsonResponse(buildJsonObject { put("rows", rows) })
    }

    private fun channels(session: IHTTPSession, parts: List<String>): Response {
        val profile = activeProfile(session)
        val lang = profile.language
        val base = baseUrl(session)
        val favorites = db.favoriteIds(profile.id)

        if (parts.size == 2 && parts[1] == "reclassify" && session.method == Method.POST) {
            requireAdmin(session)?.let { return it }
            session.drainBodyIfPresent()
            val updated = db.reclassifyChannelCountries()
            Prefs.setCountryReclassVersion(context, 2)
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("updated", updated)
            })
        }

        if (parts.size >= 2 && parts[1] == "by-number") {
            val number = parts.getOrNull(2)?.toIntOrNull()
                ?: return errorResponse(Response.Status.BAD_REQUEST, "Numero non valido")
            val channel = db.getChannelByNumber(number)
                ?: return errorResponse(Response.Status.NOT_FOUND, "Canale non trovato")
            return jsonResponse(
                iptvChannelJson(channel, channel.number, lang, channel.id in favorites, base),
            )
        }

        if (parts.size >= 2) {
            val id = decodeSegment(parts[1])
            val iptvChannel = db.getChannel(id)
                ?: return errorResponse(Response.Status.NOT_FOUND, "Canale non trovato")
            return when {
                parts.getOrNull(2) == "streams" ->
                    jsonResponse(iptvSourcesJson(iptvChannel))
                parts.getOrNull(2) == "source-report" && session.method == Method.POST -> {
                    val body = session.jsonBody()
                    val url = body.string("url").trim().ifBlank { iptvChannel.url.trim() }
                    val event = body.string("event").trim().ifBlank { "stall" }
                    val latencyMs = body.long("latencyMs") ?: -1L
                    val groupKey = db.canonicalGroupKeyForChannel(iptvChannel)
                    db.recordSourceFeedback(groupKey, url, event, latencyMs)
                    when (event.lowercase(Locale.ROOT)) {
                        "manual_select" -> liveRelay.markPreferredSource(iptvChannel.id, groupKey, url)
                        "stall", "buffering" -> liveRelay.recordStreamStall(url, groupKey)
                        "error" -> liveRelay.recordStreamFailure(url, "client_error")
                        "healthy" -> liveRelay.recordStreamSuccess(url, latencyMs.coerceAtLeast(0L))
                    }
                    jsonResponse(buildJsonObject {
                        put("ok", true)
                        put("groupKey", groupKey)
                        putOrNull("preferredUrl", db.getPreferredSource(groupKey))
                    })
                }
                parts.getOrNull(2) == "epg" -> jsonResponse(emptyEpgJson(iptvChannel.id))
                parts.getOrNull(2) == "played" && session.method == Method.POST -> {
                    session.drainBodyIfPresent()
                    bgScope.launch { runCatching { db.markPlayed(profile.id, iptvChannel.id) } }
                    jsonResponse(buildJsonObject { put("ok", true) })
                }
                parts.getOrNull(2) == "progress" && session.method == Method.POST -> {
                    val body = session.jsonBody()
                    val posMs = (body.long("positionMs") ?: 0L).coerceAtLeast(0L)
                    val durMs = (body.long("durationMs") ?: 0L).coerceAtLeast(0L)
                    val nearDone = durMs > 0L && posMs >= (durMs * 92L) / 100L
                    if (nearDone) {
                        runCatching { db.saveVodProgress(profile.id, iptvChannel.id, posMs, durMs) }
                    } else {
                        bgScope.launch {
                            runCatching { db.saveVodProgress(profile.id, iptvChannel.id, posMs, durMs) }
                        }
                    }
                    jsonResponse(buildJsonObject { put("ok", true) })
                }
                parts.size == 2 && session.method == Method.PATCH -> {
                    val favorite = session.jsonBody().bool("favorite") ?: false
                    db.setFavorite(profile.id, iptvChannel.id, favorite)
                    jsonResponse(
                        iptvChannelJson(iptvChannel, iptvChannel.number, lang, favorite, base),
                    )
                }
                parts.size == 2 -> jsonResponse(
                    iptvChannelJson(iptvChannel, iptvChannel.number, lang, iptvChannel.id in favorites, base),
                )
                else -> errorResponse(Response.Status.NOT_FOUND, "Endpoint non trovato")
            }
        }

        val favoriteOnly = session.parm("favorite") == "1"
        val category = session.parm("category").takeIf { it.isNotBlank() }
        val genre = session.parm("genre").takeIf { it.isNotBlank() }
        val platform = session.parm("platform").takeIf { it.isNotBlank() }
        val contentType = session.parm("type").takeIf { it.isNotBlank() }
        val isVodRequest = contentType in setOf("vod", "movie", "series")
        val rawCountry = session.parm("country").takeIf { it.isNotBlank() }
        // VOD catalog is global (no country scoping) and filtered by spoken language instead.
        val country = if (isVodRequest) null else rawCountry
        val vodLangParam = session.parm("lang").takeIf { it.isNotBlank() }
        val languageParam = session.parm("language")
        val languageCodes = if (languageParam.isNotBlank()) {
            languageParam
                .split(',')
                .map { M3uParser.normalizeLanguage(it) }
                .filter { it.isNotBlank() }
                .toSet()
        } else {
            emptySet()
        }
        val query = session.parm("q").takeIf { it.isNotBlank() }
        val sortByName = session.parm("sort") == "name"
        val limit = session.parm("limit").toIntOrNull()?.coerceIn(1, 15000) ?: 100
        val offset = session.parm("offset").toIntOrNull()?.coerceAtLeast(0) ?: 0
        val paginated = session.parm("paginated") == "1"
        val dedupParam = session.parm("dedup") != "0"
        val dedupVod = dedupParam && isVodRequest && query == null
        val dedupLive = dedupParam && !favoriteOnly && !isVodRequest
        val ua = session.header("user-agent")
        val vodLangCountries = if (isVodRequest && vodLangParam != null) {
            VodTaxonomy.resolveLanguageCountries(vodLangParam)
        } else {
            emptySet()
        }
        val globalVodRawCats = if (isVodRequest && (vodLangParam != null || (category == null && (genre != null || platform != null)))) {
            db.listVodCategories(null, contentType ?: "vod", limit = 300)
        } else {
            emptyList()
        }
        val vodLangCategories = if (isVodRequest && vodLangParam != null) {
            VodTaxonomy.resolveLanguageRawCategories(globalVodRawCats, vodLangParam)
        } else {
            emptySet()
        }
        if (isVodRequest && vodLangParam != null && vodLangCountries.isEmpty() && vodLangCategories.isEmpty()) {
            return jsonResponse(
                buildJsonObject {
                    putJsonArray("items") {}
                    put("total", 0)
                    put("limit", limit)
                    put("offset", offset)
                    put("hasMore", false)
                },
            )
        }
        val resolvedCategories = if (category == null && (genre != null || platform != null)) {
            val rawCats = if (isVodRequest) {
                if (vodLangParam != null) {
                    db.listVodCategories(null, contentType ?: "vod", limit = 240, vodLangCountries, vodLangCategories)
                } else {
                    globalVodRawCats
                }
            } else {
                db.listVodCategories(country, contentType ?: "vod", limit = 200)
            }
            VodTaxonomy.resolveRawCategoriesForFilter(rawCats, genre, platform)
        } else {
            emptySet()
        }
        if (category == null && (genre != null || platform != null) && resolvedCategories.isEmpty()) {
            return jsonResponse(
                buildJsonObject {
                    putJsonArray("items") {}
                    put("total", 0)
                    put("limit", limit)
                    put("offset", offset)
                    put("hasMore", false)
                },
            )
        }
        val (slice, total) = db.queryChannels(
            country = country,
            category = category,
            categories = resolvedCategories,
            languageCodes = languageCodes,
            vodLangCountries = vodLangCountries,
            vodLangCategories = vodLangCategories,
            query = query,
            favoriteOnlyProfileId = if (favoriteOnly) profile.id else null,
            contentType = contentType,
            sortByName = sortByName,
            dedupVod = dedupVod,
            dedupLive = dedupLive,
            limit = limit,
            offset = offset,
        )
        val progressMap = if (contentType in setOf("vod", "movie", "series") || slice.any { it.isVod }) {
            db.getVodProgressMap(profile.id, slice.map { it.id })
        } else {
            emptyMap()
        }
        var hasMore = offset + slice.size < total
        if (!paginated && query == null) {
            if (ua.startsWith("CasaTV-Android/", ignoreCase = true)) {
                if (limit == 1000) {
                    hasMore = hasMore && (offset + slice.size < 2000)
                } else if (limit == 4000) {
                    hasMore = hasMore && (offset + slice.size < 8000)
                }
            } else if (ua.startsWith("okhttp/", ignoreCase = true) && limit == 2000) {
                hasMore = hasMore && (offset + slice.size < 4000)
            }
        }
        if (slice.isNotEmpty()) {
            val nextLogos = slice.asSequence().mapNotNull { it.logo }.take(80).toList()
            if (nextLogos.isNotEmpty()) {
                logoCache.prefetchUrls(nextLogos, bgScope)
            }
            if (isVodRequest) {
                prewarmVodArtworkAsync(slice.filter { it.isVod }.take(30))
            }
        }
        return jsonResponse(
            buildJsonObject {
                putJsonArray("items") {
                    slice.forEach { channel ->
                        val prog = progressMap[channel.id]
                        add(
                            iptvChannelJson(
                                channel = channel,
                                number = channel.number,
                                language = lang,
                                favorite = channel.id in favorites,
                                baseUrl = base,
                                positionMs = prog?.first ?: 0L,
                                durationMs = prog?.second ?: 0L,
                            ),
                        )
                    }
                }
                put("total", total)
                put("limit", limit)
                put("offset", offset)
                put("hasMore", hasMore)
            },
        )
    }

    /* ------------------------------------------------------------------ */
    /* Profiles                                                            */
    /* ------------------------------------------------------------------ */

    private fun profiles(session: IHTTPSession, parts: List<String>): Response {
        val lang = activeProfile(session).language
        if (parts.size >= 3 && parts[1] == "guest" && parts[2] == "clear") {
            session.drainBodyIfPresent()
            db.clearGuestProfile()
            return jsonResponse(buildJsonObject { put("ok", true); put("message", "Dati profilo ospite azzerati") })
        }
        if (parts.size == 1) {
            return when (session.method) {
                Method.GET -> jsonResponse(
                    buildJsonObject {
                        putJsonArray("profiles") {
                            db.listProfiles().forEach { add(profileJson(it, lang)) }
                        }
                        putJsonArray("avatars") {
                            PROFILE_AVATARS.forEach { avatar -> add(avatar) }
                        }
                        putJsonArray("languages") {
                            PROFILE_LANGUAGES.forEach { option ->
                                add(
                                    buildJsonObject {
                                        put("code", option.code)
                                        put("name", option.name)
                                    },
                                )
                            }
                        }
                    },
                )
                Method.POST -> {
                    val body = session.jsonBody()
                    val profile = db.createProfile(
                        name = body.string("name", "Profilo"),
                        avatar = body.string("avatar", PROFILE_AVATARS.first()),
                        language = body.string("language", languageProvider()),
                        country = body.string("country", "IT"),
                    )
                    jsonResponse(profileJson(profile, lang), Response.Status.CREATED)
                }
                else -> errorResponse(Response.Status.METHOD_NOT_ALLOWED, "Metodo non consentito")
            }
        }

        val id = decodeSegment(parts[1])
        val existing = db.getProfile(id)
            ?: return errorResponse(Response.Status.NOT_FOUND, "Profilo non trovato")
        return when (session.method) {
            Method.GET -> jsonResponse(profileJson(existing, lang))
            Method.PATCH -> {
                val body = session.jsonBody()
                val updated = db.updateProfile(
                    id = id,
                    name = body.string("name", existing.name),
                    avatar = body.string("avatar", existing.avatar),
                    language = body.string("language", existing.language),
                    country = body.string("country", existing.country),
                ) ?: return errorResponse(Response.Status.NOT_FOUND, "Profilo non trovato")
                jsonResponse(profileJson(updated, updated.language))
            }
            Method.DELETE -> {
                val removed = db.deleteProfile(id)
                if (!removed) {
                    errorResponse(
                        Response.Status.CONFLICT,
                        "Deve restare almeno un profilo",
                    )
                } else {
                    jsonResponse(buildJsonObject { put("ok", true) })
                }
            }
            else -> errorResponse(Response.Status.METHOD_NOT_ALLOWED, "Metodo non consentito")
        }
    }

    /* ------------------------------------------------------------------ */
    /* M3U playlists                                                       */
    /* ------------------------------------------------------------------ */

    private fun mixRoutes(session: IHTTPSession, parts: List<String>): Response {
        val sub = parts.getOrNull(1)
        val profile = activeProfile(session)
        val lang = profile.language
        val base = baseUrl(session)
        val favorites = db.favoriteIds(profile.id)

        if (sub == "ready" && session.method == Method.GET) {
            val regions = session.parm("regions").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val languages = session.parm("languages").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val genres = session.parm("genres").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val exclude = session.parm("exclude").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val includeAudio = session.parm("includeAudioOnly") == "1" || session.parm("includeAudioOnly") == "true"
            val limit = session.parm("limit").toIntOrNull()?.coerceIn(1, 20) ?: 8
            val prewarm = session.parm("prewarm") != "0"
            val deviceId = session.header("x-device-id").ifBlank { "dev-${session.remoteIpAddress ?: "unknown"}" }

            val ready = runBlocking {
                mixPool.getReadyChannels(
                    regions = regions,
                    languages = languages,
                    genres = genres,
                    excludeIds = exclude,
                    includeAudioOnly = includeAudio,
                    limit = limit,
                )
            }
            var prewarmedId = ""
            if (prewarm && Prefs.serverRelayEnabled(context) && ready.isNotEmpty()) {
                val first = ready.first()
                prewarmedId = first.id
                bgScope.launch {
                    runCatching {
                        val fallbackUrls = db.listAlternativeChannels(first, limit = 4)
                            .map { it.url.trim() }
                            .filter { it.isNotBlank() && it != first.url }
                        liveRelay.prewarm(
                            deviceId = deviceId,
                            channelId = first.id,
                            channelName = first.name,
                            streamUrl = first.url,
                            fallbackUrls = fallbackUrls,
                        )
                    }
                }
            }
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("poolSize", mixPool.poolSize())
                if (prewarmedId.isNotBlank()) put("prewarmedId", prewarmedId)
                putJsonArray("items") {
                    ready.forEach { ch ->
                        add(iptvChannelJson(ch, ch.number, lang, ch.id in favorites, base))
                    }
                }
            })
        }

        if (sub == "report-dead" && session.method == Method.POST) {
            val body = session.jsonBody()
            val id = body.string("id").ifBlank { parts.getOrNull(2).orEmpty() }.trim()
            if (id.isNotBlank()) mixPool.reportDead(id)
            return jsonResponse(buildJsonObject { put("ok", true) })
        }

        if (sub == "probe" && session.method == Method.POST) {
            val body = session.jsonBody()
            val deviceId = body.string("deviceId").ifBlank { session.header("x-device-id") }
                .ifBlank { "dev-${session.remoteIpAddress ?: "unknown"}" }
            val maxWinners = (body["maxWinners"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()?.coerceIn(1, 5) ?: 3
            val prewarm = body.bool("prewarm") ?: true
            val arr = body["candidates"] as? kotlinx.serialization.json.JsonArray
            val parsed = buildList {
                arr?.forEach { el ->
                    val obj = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val id = obj.string("id").trim()
                    val name = obj.string("name").trim()
                    val url = obj.string("streamUrl").ifBlank { obj.string("url") }.trim()
                    if (id.isNotBlank() && url.isNotBlank()) {
                        add(Triple(id, name, url))
                    }
                }
            }.take(12)

            val (winners, dead) = playlist.probeMixCandidates(
                candidates = parsed.map { it.first to it.third },
                maxWinners = maxWinners,
            )
            bgScope.launch {
                runCatching {
                    val winnerChannels = if (winners.isNotEmpty()) db.getChannelsByIds(winners).values.toList() else emptyList()
                    mixPool.recordProbeResults(winnerChannels, dead)
                }
            }
            var prewarmedId = ""
            if (prewarm && Prefs.serverRelayEnabled(context) && winners.isNotEmpty()) {
                val firstWinnerId = winners.first()
                val match = parsed.firstOrNull { it.first == firstWinnerId }
                if (match != null) {
                    prewarmedId = match.first
                    bgScope.launch {
                        runCatching {
                            val fallbackUrls = db.getChannel(match.first)?.let { ch ->
                                db.listAlternativeChannels(ch, limit = 4)
                                    .map { it.url.trim() }
                                    .filter { it.isNotBlank() && it != match.third }
                            }.orEmpty()
                            liveRelay.prewarm(
                                deviceId = deviceId,
                                channelId = match.first,
                                channelName = match.second,
                                streamUrl = match.third,
                                fallbackUrls = fallbackUrls,
                            )
                        }
                    }
                }
            }
            return jsonResponse(buildJsonObject {
                put("ok", true)
                putJsonArray("winners") { winners.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                putJsonArray("dead") { dead.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                if (prewarmedId.isNotBlank()) put("prewarmedId", prewarmedId)
            })
        }
        return errorResponse(Response.Status.NOT_FOUND, "Endpoint mix non trovato")
    }

    private fun playlistRoutes(session: IHTTPSession, parts: List<String>): Response {
        requireAdmin(session)?.let { return it }
        val lang = activeProfile(session).language
        val strings = textOf(lang)

        if (parts.size >= 2 && (parts[1] == "export.m3u" || parts[1] == "m3u") && session.method == Method.GET) {
            val exportFile = File.createTempFile("casatv_export_", ".m3u", context.cacheDir)
            db.exportChannelsToFile(exportFile)
            val stream = object : FileInputStream(exportFile) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        runCatching { exportFile.delete() }
                    }
                }
            }
            return newFixedLengthResponse(Response.Status.OK, "audio/x-mpegurl; charset=utf-8", stream, exportFile.length()).apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Content-Disposition", "attachment; filename=\"casatv-server.m3u\"")
            }
        }

        if (parts.size >= 2 && parts[1] == "upload" && session.method == Method.POST) {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val path = files["file"]
                ?: files.entries.firstOrNull { it.key != "postData" }?.value
                ?: return errorResponse(Response.Status.BAD_REQUEST, "Nessun file ricevuto")
            val temp = File(path)
            if (!temp.exists() || temp.length() == 0L) {
                return errorResponse(Response.Status.BAD_REQUEST, "File vuoto")
            }
            val original = session.parms["file"]?.takeIf { it.isNotBlank() } ?: temp.name
            return try {
                val result = runBlocking { playlist.importFile(temp, original) }
                mixPool.invalidate()
                jsonResponse(playlistJson(result, lang), Response.Status.CREATED)
            } catch (error: PlaylistException) {
                errorResponse(Response.Status.BAD_REQUEST, playlistErrorText(error.reason, strings))
            } catch (error: Exception) {
                errorResponse(Response.Status.BAD_REQUEST, error.message ?: "Import non riuscito")
            }
        }

        if (parts.size >= 2 && parts[1] == "refresh" && session.method == Method.POST) {
            session.drainBodyIfPresent()
            return try {
                val result = runBlocking { playlist.refresh() }
                    ?: return errorResponse(
                        Response.Status.BAD_REQUEST,
                        strings.t(
                            "La playlist è stata importata da un file: scegli di nuovo il file",
                            "This playlist came from a file: pick the file again",
                            "Esta lista se importó desde un archivo: vuelve a elegir el archivo",
                        ),
                    )
                jsonResponse(playlistJson(result, lang)).also { mixPool.invalidate() }
            } catch (error: PlaylistException) {
                errorResponse(Response.Status.BAD_REQUEST, playlistErrorText(error.reason, strings))
            }
        }

        if (parts.size == 1) {
            return when (session.method) {
                Method.GET -> jsonResponse(playlistJson(null, lang))
                Method.DELETE -> {
                    playlist.clear()
                    mixPool.invalidate()
                    jsonResponse(playlistJson(null, lang))
                }
                Method.POST -> {
                    val url = session.jsonBody().string("url").trim()
                    if (url.isBlank()) {
                        return errorResponse(Response.Status.BAD_REQUEST, "URL mancante")
                    }
                    try {
                        val result = runBlocking { playlist.importUrl(url) }
                        mixPool.invalidate()
                        jsonResponse(playlistJson(result, lang))
                    } catch (error: PlaylistException) {
                        errorResponse(Response.Status.BAD_REQUEST, playlistErrorText(error.reason, strings))
                    }
                }
                else -> errorResponse(Response.Status.METHOD_NOT_ALLOWED, "Metodo non supportato")
            }
        }

        return errorResponse(Response.Status.NOT_FOUND, "Endpoint non trovato")
    }

    private fun playlistEntryJson(p: it.casatv.server.core.PlaylistEntry): JsonObject {
        val status = if (p.channelCount == 0 && p.status == "ok") "error" else p.status
        return buildJsonObject {
            put("id", p.id)
            put("name", p.name)
            put("url", p.url)
            put("sourceType", if (p.url.isBlank()) "file" else "url")
            put("enabled", p.enabled)
            put("channelCount", p.channelCount)
            put("status", status)
            put("lastError", p.lastError)
            put("lastImport", p.updatedAt)
        }
    }

    private fun importJobJson(job: it.casatv.server.core.ImportJobSnapshot): JsonObject = buildJsonObject {
        put("jobId", job.jobId)
        put("playlistId", job.playlistId)
        put("name", job.name)
        put("sourceType", job.sourceType)
        put("phase", job.phase)
        put("bytesProcessed", job.bytesProcessed)
        put("totalBytes", job.totalBytes)
        put("percent", job.percent)
        put("partsTotal", job.partsTotal)
        put("partsDone", job.partsDone)
        put("channelsImported", job.channelsImported)
        put("categorizedCount", job.categorizedCount)
        put("statusMessage", job.statusMessage)
        put("error", job.error)
        put("startedAt", job.startedAt)
        putOrNull("finishedAt", job.finishedAt)
    }

    private fun playlistsRoutes(session: IHTTPSession, parts: List<String>): Response {
        requireAdmin(session)?.let { return it }
        val engine = it.casatv.server.core.PlaylistImportEngine.get(context)

        if (parts.size >= 2 && parts[1] == "import-status" && session.method == Method.GET) {
            val snap = engine.statusSnapshot()
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("activeJob", snap.activeJob?.let { importJobJson(it) } ?: JsonNull)
                put("queuedCount", snap.queuedCount)
                putJsonArray("recentJobs") {
                    snap.recentJobs.forEach { add(importJobJson(it)) }
                }
            })
        }

        if (parts.size >= 2 && parts[1] == "deduplicate" && session.method == Method.POST) {
            session.drainBodyIfPresent()
            val res = db.deduplicatePlaylists()
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("removedCount", res.removedCount)
                putJsonArray("removedNames") {
                    res.removedNames.forEach { add(JsonPrimitive(it)) }
                }
                put("remainingCount", res.remainingCount)
            })
        }

        if (parts.size >= 2 && parts[1] == "upload-chunk" && session.method == Method.POST) {
            val uploadId = session.parm("uploadId").replace(Regex("[^A-Za-z0-9_-]"), "").take(64)
            if (uploadId.length < 4) {
                return errorResponse(Response.Status.BAD_REQUEST, "uploadId non valido")
            }
            val offset = session.parm("offset").toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val importsDir = engine.importsDir()
            val partFile = File(importsDir, "$uploadId.part")
            if (offset == 0L) {
                val cutoff = System.currentTimeMillis() - 2L * 3600_000L
                runCatching {
                    importsDir.listFiles()?.forEach { f ->
                        if (f.isFile && f.name.endsWith(".part") && f != partFile && f.lastModified() < cutoff) {
                            f.delete()
                        }
                    }
                }
                if (partFile.exists()) {
                    runCatching { partFile.delete() }
                }
            }

            val contentType = session.header("content-type").lowercase()
            var written = 0L
            if (contentType.startsWith("multipart/form-data")) {
                try {
                    val files = HashMap<String, String>()
                    session.parseBody(files)
                    val tmpPath = files["chunk"] ?: files.entries.firstOrNull { it.key != "postData" }?.value
                    if (tmpPath.isNullOrBlank()) {
                        return errorResponse(Response.Status.BAD_REQUEST, "Chunk mancante")
                    }
                    val tmpFile = File(tmpPath)
                    val buf = ByteArray(64 * 1024)
                    java.io.FileInputStream(tmpFile).use { input ->
                        java.io.RandomAccessFile(partFile, "rw").use { raf ->
                            raf.seek(offset)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                if (n > 0) {
                                    raf.write(buf, 0, n)
                                    written += n.toLong()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CasaImport", "upload-chunk multipart error ($uploadId @ $offset): ${e.message}")
                    val isTimeout = e is java.net.SocketTimeoutException ||
                        e.message?.contains("timeout", ignoreCase = true) == true
                    val status = if (isTimeout) Response.Status.REQUEST_TIMEOUT else Response.Status.INTERNAL_ERROR
                    return errorResponse(status, "Errore ricezione chunk: ${e.message ?: "timeout"}")
                }
            } else {
                val contentLength = session.header("content-length").toLongOrNull() ?: 0L
                if (contentLength <= 0L) {
                    return errorResponse(Response.Status.BAD_REQUEST, "Dimensione chunk mancante")
                }
                val buf = ByteArray(64 * 1024)
                var remaining = contentLength
                try {
                    java.io.RandomAccessFile(partFile, "rw").use { raf ->
                        raf.seek(offset)
                        val input = session.inputStream
                        while (remaining > 0L) {
                            val toRead = minOf(remaining, buf.size.toLong()).toInt()
                            val n = input.read(buf, 0, toRead)
                            if (n < 0) break
                            if (n > 0) {
                                raf.write(buf, 0, n)
                                written += n.toLong()
                                remaining -= n.toLong()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CasaImport", "upload-chunk error ($uploadId @ $offset): ${e.message}")
                    val isTimeout = e is java.net.SocketTimeoutException ||
                        e.message?.contains("timeout", ignoreCase = true) == true
                    val status = if (isTimeout) Response.Status.REQUEST_TIMEOUT else Response.Status.INTERNAL_ERROR
                    return errorResponse(status, "Errore ricezione chunk: ${e.message ?: "timeout"}")
                }
                if (written != contentLength) {
                    android.util.Log.w("CasaImport", "upload-chunk incompleto ($uploadId @ $offset): $written/$contentLength byte")
                    return errorResponse(Response.Status.BAD_REQUEST, "Chunk incompleto ($written/$contentLength byte)")
                }
            }

            val boundary = 32L * 1024L * 1024L
            if (offset == 0L || offset == 2L * 1024L * 1024L || (offset / boundary) != ((offset + written) / boundary)) {
                android.util.Log.i("CasaImport", "upload-chunk $uploadId: ${partFile.length() / (1024L * 1024L)} MB ricevuti (offset=$offset, chunk=$written B)")
            }
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("uploadId", uploadId)
                put("receivedBytes", written)
                put("fileLength", partFile.length())
            }).apply {
                addHeader("X-No-Gzip", "1")
                addHeader("Connection", "close")
                addHeader("Cache-Control", "no-store")
            }
        }

        if (parts.size >= 2 && parts[1] == "upload-finish" && session.method == Method.POST) {
            val body = session.jsonBody()
            val uploadId = body.string("uploadId").ifBlank { session.parm("uploadId") }
                .replace(Regex("[^A-Za-z0-9_-]"), "").take(64)
            val customName = body.string("name").ifBlank { session.parm("name") }.ifBlank { "Playlist M3U" }
            if (uploadId.length < 4) {
                return errorResponse(Response.Status.BAD_REQUEST, "uploadId non valido")
            }
            val partFile = File(engine.importsDir(), "$uploadId.part")
            if (!partFile.exists() || partFile.length() == 0L) {
                return errorResponse(Response.Status.BAD_REQUEST, "File caricato vuoto o mancante")
            }
            val finalFile = File(engine.importsDir(), "$uploadId.m3u")
            if (finalFile.exists()) runCatching { finalFile.delete() }
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                runCatching { partFile.delete() }
            }
            val job = engine.enqueueFile(finalFile, customName)
            android.util.Log.i("CasaImport", "upload-finish $uploadId '$customName': ${finalFile.length() / (1024L * 1024L)} MB -> job ${job.jobId} (dup=${job.duplicateSkipped}, upd=${job.updatedExisting})")
            val entry = db.getPlaylist(job.playlistId)
            return jsonResponse(
                buildJsonObject {
                    put("ok", true)
                    put("async", !job.duplicateSkipped)
                    put("duplicate", job.duplicateSkipped)
                    put("updatedExisting", job.updatedExisting)
                    put("job", importJobJson(job))
                    if (entry != null) put("playlist", playlistEntryJson(entry))
                },
                if (job.duplicateSkipped) Response.Status.OK else Response.Status.ACCEPTED,
            ).apply {
                addHeader("X-No-Gzip", "1")
                addHeader("Connection", "close")
                addHeader("Cache-Control", "no-store")
            }
        }

        if (parts.size == 1) {
            return when (session.method) {
                Method.GET -> {
                    val list = playlist.listPlaylists()
                    jsonResponse(
                        buildJsonObject {
                            put("ok", true)
                            putJsonArray("playlists") {
                                list.forEach { p -> add(playlistEntryJson(p)) }
                            }
                        },
                    )
                }
                Method.POST -> {
                    val body = session.jsonBody()
                    val url = body.string("url").trim()
                    val content = body.string("content")
                    val name = body.string("name").trim()
                    val useAsync = body.bool("async") ?: false
                    if (url.isBlank() && content.isBlank()) {
                        return errorResponse(Response.Status.BAD_REQUEST, "URL o contenuto file mancante")
                    }
                    if (useAsync && content.isBlank()) {
                        val job = engine.enqueueUrl(name, url)
                        val entry = db.getPlaylist(job.playlistId)
                        return jsonResponse(
                            buildJsonObject {
                                put("ok", true)
                                put("async", true)
                                put("updatedExisting", job.updatedExisting)
                                put("job", importJobJson(job))
                                if (entry != null) put("playlist", playlistEntryJson(entry))
                            },
                            Response.Status.ACCEPTED,
                        )
                    }
                    try {
                        val entry = runBlocking {
                            if (content.isNotBlank()) playlist.addPlaylistFromText(name, content, name)
                            else playlist.addPlaylist(name, url)
                        }
                        jsonResponse(
                            buildJsonObject {
                                put("ok", true)
                                put("playlist", playlistEntryJson(entry))
                            },
                            Response.Status.CREATED,
                        )
                    } catch (e: Exception) {
                        errorResponse(Response.Status.BAD_REQUEST, e.message ?: "Errore import playlist")
                    }
                }
                else -> errorResponse(Response.Status.METHOD_NOT_ALLOWED, "Metodo non supportato")
            }
        }

        if (parts.size >= 2 && parts[1] == "upload" && session.method == Method.POST) {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val path = files["file"]
                ?: files.entries.firstOrNull { it.key != "postData" }?.value
                ?: return errorResponse(Response.Status.BAD_REQUEST, "Nessun file ricevuto")
            val temp = File(path)
            val customName = session.parm("name")
                .ifBlank { session.parms["file"]?.takeIf { it.isNotBlank() } ?: temp.name }
            if (temp.length() > 16L * 1024L * 1024L) {
                val staged = File(engine.importsDir(), "up_${System.currentTimeMillis()}.m3u")
                temp.copyTo(staged, overwrite = true)
                val job = engine.enqueueFile(staged, customName)
                val entry = db.getPlaylist(job.playlistId)
                return jsonResponse(
                    buildJsonObject {
                        put("ok", true)
                        put("async", true)
                        put("job", importJobJson(job))
                        if (entry != null) put("playlist", playlistEntryJson(entry))
                    },
                    Response.Status.ACCEPTED,
                )
            }
            return try {
                val entry = runBlocking { playlist.addPlaylistFromFile(temp, customName) }
                mixPool.invalidate()
                jsonResponse(
                    buildJsonObject {
                        put("ok", true)
                        put("playlist", playlistEntryJson(entry))
                    },
                    Response.Status.CREATED,
                )
            } catch (e: Exception) {
                errorResponse(Response.Status.BAD_REQUEST, e.message ?: "Errore caricamento file M3U")
            }
        }

        if (parts.size >= 2) {
            val id = decodeSegment(parts[1])
            if (parts.size == 2 && session.method == Method.DELETE) {
                playlist.deletePlaylist(id)
                mixPool.invalidate()
                return jsonResponse(buildJsonObject { put("ok", true) })
            }
            if (parts.size >= 3 && parts[2] == "check" && session.method == Method.POST) {
                session.drainBodyIfPresent()
                val updated = runBlocking { playlist.checkPlaylist(id) }
                    ?: return errorResponse(Response.Status.NOT_FOUND, "Playlist non trovata")
                return jsonResponse(
                    buildJsonObject {
                        put("ok", true)
                        put("playlist", playlistEntryJson(updated))
                    },
                )
            }
            if (parts.size >= 3 && parts[2] == "refresh" && session.method == Method.POST) {
                session.drainBodyIfPresent()
                return try {
                    val updated = runBlocking { playlist.refreshPlaylist(id) }
                        ?: return errorResponse(Response.Status.NOT_FOUND, "Playlist non trovata")
                    mixPool.invalidate()
                    jsonResponse(
                        buildJsonObject {
                            put("ok", true)
                            put("channelCount", updated.channelCount)
                            put("playlist", playlistEntryJson(updated))
                        },
                    )
                } catch (e: Exception) {
                    errorResponse(Response.Status.BAD_REQUEST, e.message ?: "Errore aggiornamento playlist")
                }
            }
            if (parts.size >= 3 && parts[2] == "toggle" && session.method == Method.POST) {
                val body = session.jsonBody()
                val enabled = body.bool("enabled") ?: true
                playlist.togglePlaylist(id, enabled)
                mixPool.invalidate()
                return jsonResponse(buildJsonObject { put("ok", true) })
            }
        }

        return errorResponse(Response.Status.NOT_FOUND, "Endpoint non trovato")
    }

    private fun playlistJson(
        result: it.casatv.server.core.PlaylistImportResult?,
        language: String,
    ): JsonObject {
        val status = playlist.status()
        return buildJsonObject {
            put("ok", true)
            if (result != null) put("imported", result.count)
            put("count", status.count)
            put("source", status.source)
            put("url", status.url)
            put("lastImport", status.lastImport)
            putJsonArray("categories") {
                status.categories.forEach { (category, count) ->
                    add(
                        buildJsonObject {
                            put("id", category)
                            put("name", categoryName(category, language))
                            put("count", count)
                        },
                    )
                }
            }
        }
    }

    private fun playlistErrorText(reason: PlaylistError, strings: it.casatv.server.core.Strings): String =
        when (reason) {
            PlaylistError.InvalidUrl -> strings.t(
                "L'indirizzo deve iniziare con http:// o https://",
                "The address must start with http:// or https://",
                "La dirección debe empezar por http:// o https://",
            )
            PlaylistError.Download -> strings.t(
                "Impossibile scaricare la playlist",
                "Unable to download the playlist",
                "No se pudo descargar la lista",
            )
            PlaylistError.Empty -> strings.t(
                "Nessun canale trovato nella playlist",
                "No channels found in the playlist",
                "No se encontraron canales en la lista",
            )
        }

    /* ------------------------------------------------------------------ */
    /* System, devices, version                                            */
    /* ------------------------------------------------------------------ */

    private fun system(session: IHTTPSession): Response {
        requireAdmin(session)?.let { return it }
        val profile = activeProfile(session)
        val summary = db.catalogStatsSummary()
        val memory = memoryUsage()
        val disk = diskUsage()
        val cpuPercent = cpuPercent()
        val alerts = buildJsonArray {
            fun check(kind: String, label: String, value: Double) {
                if (value >= 75.0) {
                    add(
                        buildJsonObject {
                            put("level", if (value >= 90.0) "critical" else "warning")
                            put("kind", kind)
                            put("title", label)
                            put("message", "$label: ${value.toInt()}%")
                            put("value", value)
                        },
                    )
                }
            }
            check("cpu", "CPU", cpuPercent)
            check("memory", "RAM", memory.second)
            check("disk", textOf(profile.language).t("Disco", "Disk", "Disco"), disk.second)
        }
        val critical = alerts.any { element ->
            element is JsonObject && element["level"]?.toString()?.contains("critical") == true
        }
        val health = when {
            critical -> "critical"
            alerts.isNotEmpty() -> "warning"
            else -> "ok"
        }
        val favorites = db.favoriteIds(profile.id).size
        val activeClients = db.listClients()
        return jsonResponse(
            buildJsonObject {
                put("ok", true)
                put("name", nameProvider())
                put("version", BuildConfig.VERSION_NAME)
                put("serverTime", System.currentTimeMillis() / 1000)
                put("startedAt", startedAtSeconds)
                put("uptimeSeconds", System.currentTimeMillis() / 1000 - startedAtSeconds)
                put("health", health)
                put("alerts", alerts)
                putJsonObject("resources") {
                    putJsonObject("cpu") {
                        put("cores", Runtime.getRuntime().availableProcessors())
                        put("load1", cpuPercent / 100.0 * Runtime.getRuntime().availableProcessors())
                        put("percent", cpuPercent)
                    }
                    putJsonObject("memory") {
                        put("total", memory.first)
                        put("free", (memory.first - (memory.first * memory.second / 100).toLong()))
                        put("used", (memory.first * memory.second / 100).toLong())
                        put("percent", memory.second)
                    }
                    putJsonObject("disk") {
                        put("total", disk.first)
                        put("free", (disk.first - (disk.first * disk.second / 100).toLong()))
                        put("used", (disk.first * disk.second / 100).toLong())
                        put("percent", disk.second)
                    }
                    putJsonObject("process") {
                        put("rss", android.os.Debug.getPss() * 1024L)
                        put("heapUsed", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                        put("heapTotal", Runtime.getRuntime().totalMemory())
                    }
                }
                putJsonObject("lastUpdate") {
                    put(
                        "catalog",
                        Prefs.playlistLastImport(context).takeIf { it > 0 }?.let { JsonPrimitive(it) } ?: JsonNull,
                    )
                    put("epg", JsonNull)
                    put("ingest", JsonNull)
                }
                putJsonObject("stats") {
                    put("channels", summary.liveChannels)
                    put("allChannels", summary.liveChannels)
                    put("streams", summary.liveStreams)
                    put("onlineStreams", summary.liveStreams)
                    put("movies", summary.movies)
                    put("series", summary.series)
                    put("vodTotal", summary.movies + summary.series)
                    put("totalItems", summary.totalItems)
                    put("favorites", favorites)
                    put("hidden", 0)
                    put("epgPrograms", 0)
                    put("countries", summary.countries)
                }
                putJsonArray("devices") {
                    activeClients.forEach { add(clientJson(it)) }
                }
                put("deviceCount", activeClients.size)
                putJsonObject("logoCache") {
                    val (count, bytes) = logoCache.stats()
                    put("files", count)
                    put("bytes", bytes)
                }
                put("update", updateJson(session.header("x-app-version"), session))
            },
        )
    }

    private fun version(session: IHTTPSession): Response =
        jsonResponse(updateJson(session.header("x-app-version"), session))

    private fun validDynamicTvApk(): Pair<File, String>? {
        val file = java.io.File(context.filesDir, "updates/casatv-tv.apk")
        if (!file.exists()) return null
        val pkgInfo = runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }.getOrNull()
        val versionName = pkgInfo?.versionName?.takeIf { it.isNotBlank() }
        if (pkgInfo?.packageName != "it.casatv.tv" || versionName == null || compareVersions(versionName, TV_LATEST_VERSION) < 0) {
            runCatching { file.delete() }
            return null
        }
        return file to versionName
    }

    private fun validAssetTvApkVersion(): String? {
        if (assetTvApkChecked) return cachedAssetTvApkVersion
        synchronized(this) {
            if (assetTvApkChecked) return cachedAssetTvApkVersion
            val detected = runCatching {
                val tmp = File(context.cacheDir, "asset-tv-check.apk")
                context.assets.open("updates/casatv-tv.apk").use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                val info = context.packageManager.getPackageArchiveInfo(tmp.absolutePath, 0)
                tmp.delete()
                if (info?.packageName == "it.casatv.tv") {
                    info.versionName?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }.getOrNull()
            cachedAssetTvApkVersion = detected
            assetTvApkChecked = true
            return detected
        }
    }

    private fun updateJson(current: String, session: IHTTPSession? = null): JsonObject {
        val dynamic = validDynamicTvApk()
        val assetVersion = if (dynamic == null) validAssetTvApkVersion() else null
        val availableVersion = dynamic?.second ?: assetVersion
        val hasApk = availableVersion != null
        val latest = availableVersion ?: TV_LATEST_VERSION
        val base = session?.let { baseUrl(it) } ?: ""
        val isUpdateAvailable = hasApk && current.isNotBlank() && compareVersions(latest, current) > 0
        return buildJsonObject {
            put("latest", latest)
            putOrNull("current", current.takeIf { it.isNotBlank() })
            put("updateAvailable", isUpdateAvailable)
            putOrNull("downloadUrl", if (hasApk) "$base/api/update/casatv-tv.apk" else null)
            put("hasApk", hasApk)
            put("changelog", "Aggiornamento CasaTV per Android TV v$latest")
            put("minVersion", "1.0.0")
        }
    }

    private fun updateRoutes(session: IHTTPSession, parts: List<String>): Response {
        if (parts.size >= 2 && (parts[1] == "casatv-tv.apk" || parts[1] == "tv.apk" || parts[1] == "upload") && (session.method == Method.POST || session.method == Method.PUT)) {
            requireAdmin(session)?.let {
                return it.apply { addHeader("Connection", "close") }
            }
            val files = HashMap<String, String>()
            session.parseBody(files)
            val path = files["file"]
                ?: files.entries.firstOrNull { it.key != "postData" }?.value
                ?: return errorResponse(Response.Status.BAD_REQUEST, "Nessun file ricevuto")
            val temp = File(path)
            val pkgInfo = runCatching {
                context.packageManager.getPackageArchiveInfo(temp.absolutePath, 0)
            }.getOrNull()
            if (pkgInfo?.packageName != "it.casatv.tv") {
                return errorResponse(Response.Status.BAD_REQUEST, "APK non valido per Android TV (${pkgInfo?.packageName ?: "sconosciuto"})")
            }
            val updateDir = File(context.filesDir, "updates")
            updateDir.mkdirs()
            val staged = File(updateDir, "casatv-tv.apk.tmp")
            val target = File(updateDir, "casatv-tv.apk")
            temp.copyTo(staged, overwrite = true)
            if (!staged.renameTo(target)) {
                staged.copyTo(target, overwrite = true)
                staged.delete()
            }
            return jsonResponse(buildJsonObject { put("ok", true); put("size", target.length()) })
        }
        if (parts.size >= 2 && (parts[1] == "casatv-tv.apk" || parts[1] == "tv.apk")) {
            return serveUpdateApk(session)
        }
        return version(session)
    }

    private fun serveUpdateApk(session: IHTTPSession): Response {
        val dynamic = validDynamicTvApk()?.first
        if (dynamic != null && dynamic.exists()) {
            val input = dynamic.inputStream()
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/vnd.android.package-archive",
                input,
                dynamic.length(),
            ).apply {
                addHeader("Content-Disposition", "attachment; filename=\"casatv-tv.apk\"")
                addHeader("Access-Control-Allow-Origin", "*")
            }
        }
        if (validAssetTvApkVersion() == null) {
            return errorResponse(Response.Status.NOT_FOUND, "Aggiornamento APK TV non trovato")
        }
        return try {
            val assetSize = runCatching {
                context.assets.openFd("updates/casatv-tv.apk").use { it.length }
            }.getOrDefault(-1L)
            val assetInput = context.assets.open("updates/casatv-tv.apk")
            val resp = if (assetSize > 0) {
                newFixedLengthResponse(Response.Status.OK, "application/vnd.android.package-archive", assetInput, assetSize)
            } else {
                newChunkedResponse(Response.Status.OK, "application/vnd.android.package-archive", assetInput)
            }
            resp.apply {
                addHeader("Content-Disposition", "attachment; filename=\"casatv-tv.apk\"")
                addHeader("Access-Control-Allow-Origin", "*")
            }
        } catch (_: Exception) {
            errorResponse(Response.Status.NOT_FOUND, "Aggiornamento APK TV non trovato")
        }
    }

    private fun clients(session: IHTTPSession, parts: List<String>): Response {
        if (parts.size >= 2 && parts[1] == "heartbeat" && session.method == Method.POST) {
            val body = session.jsonBody()
            val profile = activeProfile(session)
            val deviceId = body.string("deviceId").ifBlank { session.header("x-device-id") }
                .ifBlank { "device-${session.remoteIpAddress ?: "unknown"}" }
            val name = body.string("name").ifBlank { session.header("x-device-name") }.ifBlank { "Dispositivo" }
            val platform = body.string("platform").ifBlank { session.header("x-device-platform") }.ifBlank { "Android TV" }
            val appVersion = body.string("appVersion").ifBlank { session.header("x-app-version") }
            val profileId = body.string("profileId").ifBlank { profile.id }
            val state = body.string("state").ifBlank { session.header("x-playback-state") }.ifBlank { "idle" }
            val channel = body.string("channel").ifBlank { session.header("x-playback-channel") }
            val ip = session.remoteIpAddress ?: ""
            val now = System.currentTimeMillis() / 1000
            bgScope.launch {
                runCatching {
                    db.touchClient(
                        id = deviceId,
                        name = name,
                        platform = platform,
                        appVersion = appVersion,
                        profileId = profileId,
                        state = state,
                        channel = channel,
                        ip = ip,
                    )
                }
            }
            val client = ClientRow(
                id = deviceId,
                name = name,
                platform = platform,
                appVersion = appVersion,
                profileId = profileId,
                state = state,
                channel = channel,
                ip = ip,
                firstSeen = now,
                lastSeen = now,
            )
            return jsonResponse(
                buildJsonObject {
                    put("ok", true)
                    put("client", clientJson(client))
                    put("serverTime", now)
                    put("version", BuildConfig.VERSION_NAME)
                    put("lastIngest", Prefs.playlistLastImport(context).takeIf { it > 0 }?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("update", updateJson(appVersion, session))
                },
            )
        }
        if (parts.size >= 2 && session.method == Method.DELETE) {
            requireAdmin(session)?.let { return it }
            val id = decodeSegment(parts[1])
            db.removeClient(id)
            clientTouchCache.remove(id)
            return jsonResponse(buildJsonObject { put("removed", true) })
        }
        requireAdmin(session)?.let { return it }
        return jsonResponse(
            buildJsonObject {
                putJsonArray("clients") { db.listClients().forEach { add(clientJson(it)) } }
            },
        )
    }

    private data class SyncDeviceEntry(
        val deviceId: String,
        val deviceName: String,
        val isLive: Boolean,
        val positionMs: Long,
        val liveOffsetMs: Long,
        val segmentSeq: Long = 0L,
        val segmentOffsetMs: Long = 0L,
        val segmentDurationMs: Long = 0L,
        val firstSeenAt: Long,
        val lastReportAt: Long,
    )

    private val channelSyncHub = ConcurrentHashMap<String, ConcurrentHashMap<String, SyncDeviceEntry>>()

    private fun syncRoutes(session: IHTTPSession, parts: List<String>): Response {
        if (session.method == Method.POST) {
            val body = session.jsonBody()
            val deviceId = body.string("deviceId").ifBlank { session.header("x-device-id") }
                .ifBlank { "dev-${session.remoteIpAddress ?: "unknown"}" }
            val deviceName = body.string("deviceName").ifBlank { session.header("x-device-name") }
                .ifBlank { "Android TV" }
            val channelId = body.string("channelId").trim()
            val isLive = body.bool("isLive") ?: true
            val positionMs = body.long("positionMs") ?: 0L
            val liveOffsetMs = body.long("liveOffsetMs") ?: -1L
            val segmentSeq = body.long("segmentSeq") ?: 0L
            val segmentOffsetMs = body.long("segmentOffsetMs") ?: 0L
            val segmentDurationMs = body.long("segmentDurationMs") ?: 0L
            val now = System.currentTimeMillis()

            if (channelId.isBlank()) {
                channelSyncHub.values.forEach { devices -> devices.remove(deviceId) }
                return jsonResponse(buildJsonObject {
                    put("ok", true)
                    put("syncActive", false)
                    put("serverTimeMs", now)
                })
            }

            val syncGroupKey = if (isLive) {
                db.canonicalGroupKeyForChannelId(channelId) ?: channelId
            } else {
                "vod:$channelId"
            }

            channelSyncHub.entries.removeIf { (grpKey, devices) ->
                if (grpKey != syncGroupKey) devices.remove(deviceId)
                devices.isEmpty()
            }

            val channelDevices = channelSyncHub.computeIfAbsent(syncGroupKey) { ConcurrentHashMap() }
            channelDevices.entries.removeIf { (_, dev) -> now - dev.lastReportAt > 8000L }
            if (isLive) {
                liveRelay.touchViewer(deviceId, channelId)
            }

            val existing = channelDevices[deviceId]
            val entry = if (existing != null) {
                existing.copy(
                    deviceName = deviceName,
                    isLive = isLive,
                    positionMs = positionMs,
                    liveOffsetMs = liveOffsetMs,
                    segmentSeq = segmentSeq,
                    segmentOffsetMs = segmentOffsetMs,
                    segmentDurationMs = segmentDurationMs,
                    lastReportAt = now,
                )
            } else {
                SyncDeviceEntry(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    isLive = isLive,
                    positionMs = positionMs,
                    liveOffsetMs = liveOffsetMs,
                    segmentSeq = segmentSeq,
                    segmentOffsetMs = segmentOffsetMs,
                    segmentDurationMs = segmentDurationMs,
                    firstSeenAt = now,
                    lastReportAt = now,
                )
            }
            channelDevices[deviceId] = entry

            val sortedByAge = channelDevices.values.sortedBy { it.firstSeenAt }
            // Elect the safest slightly-behind active peer (closest to 10s cushion behind live edge,
            // within 6s..24s) so ahead peers seek backward into cached LAN segments without starving.
            val leader = if (isLive && sortedByAge.size >= 2) {
                val safeCandidates = sortedByAge.filter { it.segmentSeq > 0L || it.positionMs > 0L }
                    .ifEmpty { sortedByAge }
                val healthyCushion = safeCandidates.filter { it.liveOffsetMs in 6_000L..24_000L }
                healthyCushion.minByOrNull { kotlin.math.abs(it.liveOffsetMs - 10_000L) }
                    ?: safeCandidates.filter { it.liveOffsetMs > 0L }.maxByOrNull { it.liveOffsetMs.coerceAtMost(24_000L) }
                    ?: sortedByAge.first()
            } else {
                sortedByAge.firstOrNull() ?: entry
            }
            val peers = sortedByAge
            val isLeader = (leader.deviceId == deviceId)
            val syncActive = peers.size >= 2

            val elapsedSinceLeaderReport = (now - leader.lastReportAt).coerceIn(0L, 4_000L)
            val projectedPositionMs = leader.positionMs + elapsedSinceLeaderReport
            val projectedSeq = leader.segmentSeq
            val projectedOffset = leader.segmentOffsetMs + elapsedSinceLeaderReport

            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("serverTimeMs", now)
                put("syncGroupKey", syncGroupKey)
                put("syncActive", syncActive)
                put("isLeader", isLeader)
                put("peerCount", peers.size)
                put("leaderId", leader.deviceId)
                put("leaderName", leader.deviceName)
                put("targetLiveOffsetMs", leader.liveOffsetMs)
                put("targetPositionMs", projectedPositionMs)
                put("targetSegmentSeq", projectedSeq)
                put("targetSegmentOffsetMs", projectedOffset)
                put("targetSegmentDurationMs", leader.segmentDurationMs)
                put("leaderUpdatedAgoMs", elapsedSinceLeaderReport)
                putJsonArray("peers") {
                    peers.forEach { dev ->
                        add(buildJsonObject {
                            put("id", dev.deviceId)
                            put("name", dev.deviceName)
                            put("isLeader", dev.deviceId == leader.deviceId)
                        })
                    }
                }
            })
        }

        if (session.method == Method.GET) {
            val channelId = parts.getOrNull(1).orEmpty()
            val syncGroupKey = db.canonicalGroupKeyForChannelId(channelId) ?: channelId
            val now = System.currentTimeMillis()
            val channelDevices = channelSyncHub[syncGroupKey] ?: channelSyncHub[channelId] ?: ConcurrentHashMap()
            channelDevices.entries.removeIf { (_, dev) -> now - dev.lastReportAt > 8000L }
            val peers = channelDevices.values.sortedBy { it.firstSeenAt }
            val leader = peers.firstOrNull()
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("channelId", channelId)
                put("syncGroupKey", syncGroupKey)
                put("syncActive", peers.size >= 2)
                put("peerCount", peers.size)
                put("leaderName", leader?.deviceName ?: "")
            })
        }

        return errorResponse(Response.Status.METHOD_NOT_ALLOWED, "Metodo non consentito")
    }

    private fun epgRoutes(session: IHTTPSession, parts: List<String>): Response {
        if (parts.getOrNull(1) == "guide") {
            val nowSec = System.currentTimeMillis() / 1000
            return jsonResponse(
                buildJsonObject {
                    put("from", nowSec)
                    put("to", nowSec)
                    putJsonArray("channels") {}
                    putJsonArray("programs") {}
                },
            )
        }
        return maintenance(session, parts)
    }

    private fun maintenance(session: IHTTPSession, parts: List<String>): Response {
        session.drainBodyIfPresent()
        val strings = textOf(activeProfile(session).language)
        if (parts.lastOrNull() == "status") {
            return jsonResponse(
                buildJsonObject {
                    put("running", false)
                    put("state", "idle")
                    put("message", strings.t("Nessuna operazione in corso", "Nothing running", "Ninguna operación en curso"))
                },
            )
        }
        if (parts.firstOrNull() == "refresh" && session.method == Method.POST) {
            requireAdmin(session)?.let { return it }
            bgScope.launch {
                runCatching { playlist.refreshAllEnabled() }
            }
            return jsonResponse(
                buildJsonObject {
                    put("ok", true)
                    put(
                        "message",
                        strings.t(
                            "Aggiornamento delle playlist M3U avviato in background",
                            "M3U playlist refresh started in the background",
                            "Actualización de las listas M3U iniciada en segundo plano",
                        ),
                    )
                },
            )
        }
        return jsonResponse(
            buildJsonObject {
                put("ok", true)
                put("message", strings.t("Nessuna operazione in corso", "Nothing running", "Ninguna operación en curso"))
            },
        )
    }

    private fun settingsRoutes(session: IHTTPSession, parts: List<String>): Response {
        val sub = parts.getOrNull(1)
        return when (sub) {
            "auth-status" -> {
                if (session.method != Method.GET) return errorResponse(Response.Status.METHOD_NOT_ALLOWED, "Metodo non consentito")
                jsonResponse(buildJsonObject {
                    put("ok", true)
                    put("requiresAuth", true)
                    put("isDefaultPin", Prefs.isAdminPinDefault(context))
                    put("authorized", isAdminAuthorized(session))
                })
            }
            "verify-pin" -> {
                if (session.method != Method.POST) return errorResponse(Response.Status.METHOD_NOT_ALLOWED, "Metodo non consentito")
                val body = session.jsonBody()
                val pin = body.string("pin").trim()
                val currentPin = Prefs.adminPin(context)
                if (pin == currentPin) {
                    jsonResponse(buildJsonObject {
                        put("ok", true)
                        put("mustChangePassword", Prefs.isAdminPinDefault(context))
                        put("message", "Accesso autorizzato")
                    })
                } else {
                    jsonResponse(buildJsonObject {
                        put("ok", false)
                        put("error", "Password non valida")
                    }, Response.Status.UNAUTHORIZED)
                }
            }
            "change-pin" -> {
                if (session.method != Method.POST) return errorResponse(Response.Status.METHOD_NOT_ALLOWED, "Metodo non consentito")
                val body = session.jsonBody()
                val oldPin = body.string("oldPin").trim()
                val newPin = body.string("newPin").trim()
                val currentPin = Prefs.adminPin(context)
                if (oldPin != currentPin) {
                    return jsonResponse(buildJsonObject {
                        put("ok", false)
                        put("error", "La password attuale non è corretta")
                    }, Response.Status.UNAUTHORIZED)
                }
                if (newPin.length !in 4..32) {
                    return jsonResponse(buildJsonObject {
                        put("ok", false)
                        put("error", "La nuova password deve avere tra 4 e 32 caratteri")
                    }, Response.Status.BAD_REQUEST)
                }
                if (newPin == Prefs.DEFAULT_ADMIN_PIN) {
                    return jsonResponse(buildJsonObject {
                        put("ok", false)
                        put("error", "Scegli una password diversa da 0000")
                    }, Response.Status.BAD_REQUEST)
                }
                Prefs.setAdminPin(context, newPin)
                jsonResponse(buildJsonObject {
                    put("ok", true)
                    put("mustChangePassword", false)
                    put("message", "Password aggiornata con successo")
                })
            }
            else -> {
                requireAdmin(session)?.let { return it }
                if (session.method == Method.PATCH) {
                    val body = session.jsonBody()
                    val newName = body.string("server_name").trim()
                    if (newName.isNotBlank()) Prefs.setServerName(context, newName)
                    body.int("timeshift_max_hours")?.let { hours ->
                        Prefs.setTimeshiftMaxHours(context, hours)
                    }
                    body.bool("server_relay_enabled")?.let { enabled ->
                        Prefs.setServerRelayEnabled(context, enabled)
                    }
                    jsonResponse(buildJsonObject {
                        put("ok", true)
                        putJsonObject("settings") {
                            put("server_name", Prefs.serverName(context))
                            put("timeshift_max_hours", Prefs.timeshiftMaxHours(context))
                            put("server_relay_enabled", Prefs.serverRelayEnabled(context))
                        }
                    })
                } else {
                    jsonResponse(buildJsonObject {
                        put("ok", true)
                        putJsonObject("settings") {
                            put("server_name", Prefs.serverName(context))
                            put("port", Prefs.port(context))
                            put("auto_start", Prefs.autoStart(context))
                            put("timeshift_max_hours", Prefs.timeshiftMaxHours(context))
                            put("server_relay_enabled", Prefs.serverRelayEnabled(context))
                        }
                    })
                }
            }
        }
    }

    private fun timeshiftRoutes(session: IHTTPSession, parts: List<String>): Response {
        val sub = parts.getOrNull(1)
        if (sub == "start" && session.method == Method.POST) {
            val body = session.jsonBody()
            val deviceId = body.string("deviceId").ifBlank { session.header("x-device-id") }
                .ifBlank { "dev-${session.remoteIpAddress ?: "unknown"}" }
            val channelId = body.string("channelId").trim()
            val channelName = body.string("channelName").trim()
            val streamUrl = body.string("streamUrl").trim()
            if (channelId.isBlank() || streamUrl.isBlank()) {
                return errorResponse(Response.Status.BAD_REQUEST, "Parametri canale o flusso mancanti")
            }
            val maxHours = (body.int("maxHours") ?: Prefs.timeshiftMaxHours(context)).coerceIn(1, 12)
            val maxSeconds = body.long("maxSeconds")
            val snap = timeshift.startSession(
                deviceId = deviceId,
                channelId = channelId,
                channelName = channelName,
                streamUrl = streamUrl,
                maxHours = maxHours,
                maxSecondsOverride = maxSeconds,
            )
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("session", timeshiftJson(snap))
            })
        }
        if (sub == "status" && parts.size >= 3) {
            val snap = timeshift.getStatus(decodeSegment(parts[2]))
                ?: return errorResponse(Response.Status.NOT_FOUND, "Registrazione non trovata")
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("session", timeshiftJson(snap))
            })
        }
        if (sub == "pending") {
            val deviceId = session.parm("deviceId").ifBlank { session.header("x-device-id") }
            val channelId = session.parm("channelId")
            val pending = timeshift.findPending(deviceId, channelId)
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("hasPending", pending != null)
                if (pending != null) {
                    put("session", timeshiftJson(pending))
                }
            })
        }
        if (sub != null && ((parts.size == 2 && session.method == Method.DELETE) || (parts.size >= 3 && parts[2] == "delete" && session.method == Method.POST))) {
            session.drainBodyIfPresent()
            val deleted = timeshift.deleteSession(decodeSegment(sub))
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("deleted", deleted)
            })
        }
        if (sub != null && parts.size >= 3 && session.method == Method.GET) {
            val id = decodeSegment(sub)
            val fileName = decodeSegment(parts[2])
            if (isHlsPlaylistFile(fileName)) {
                val m3u8 = when (fileName) {
                    "video.m3u8" -> timeshift.renderTrackPlaylist(id, audio = false)
                    "audio.m3u8" -> timeshift.renderTrackPlaylist(id, audio = true)
                    else -> timeshift.renderMasterPlaylist(id)
                } ?: return errorResponse(Response.Status.NOT_FOUND, "Playlist non trovata")
                return serveHlsPlaylistResponse(m3u8)
            }
            val segFile = timeshift.resolveSegmentFile(id, fileName)
                ?: return errorResponse(Response.Status.NOT_FOUND, "Segmento non trovato")
            return serveMediaSegmentResponse(segFile, fileName)
        }
        return errorResponse(Response.Status.NOT_FOUND, "Endpoint timeshift non trovato")
    }

    private fun recordingsRoutes(session: IHTTPSession, parts: List<String>): Response {
        val sub = parts.getOrNull(1)
        if (sub == null && session.method == Method.GET) {
            val items = timeshift.listRecordings()
            return jsonResponse(buildJsonObject {
                put("ok", true)
                putJsonArray("recordings") {
                    items.forEach { add(recordingJson(it)) }
                }
            })
        }
        if (sub == "start" && session.method == Method.POST) {
            val body = session.jsonBody()
            val deviceId = body.string("deviceId").ifBlank { session.header("x-device-id") }
                .ifBlank { "dev-${session.remoteIpAddress ?: "unknown"}" }
            val channelId = body.string("channelId").trim()
            val dbCh = if (channelId.isNotBlank()) db.getChannel(channelId) else null
            val channelName = body.string("channelName").ifBlank { dbCh?.name.orEmpty() }.trim()
            val streamUrl = body.string("streamUrl").ifBlank { dbCh?.url.orEmpty() }.trim()
            val country = body.string("country").ifBlank { dbCh?.country.orEmpty() }.trim()
            val logo = body.string("logo").takeIf { it.isNotBlank() } ?: dbCh?.logo
            if (channelId.isBlank() || streamUrl.isBlank()) {
                return errorResponse(Response.Status.BAD_REQUEST, "Parametri canale o flusso mancanti")
            }
            val maxHours = (body.int("maxHours") ?: Prefs.timeshiftMaxHours(context)).coerceIn(1, 12)
            val maxSeconds = body.long("maxSeconds")
            val snap = timeshift.startSession(
                deviceId = deviceId,
                channelId = channelId,
                channelName = channelName,
                streamUrl = streamUrl,
                maxHours = maxHours,
                maxSecondsOverride = maxSeconds,
                country = country,
                logo = logo,
            )
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("recording", recordingJson(snap))
            })
        }
        if (sub != null) {
            val id = decodeSegment(sub)
            val action = parts.getOrNull(2)?.let { decodeSegment(it) }
            if (action == null && session.method == Method.GET) {
                val snap = timeshift.getStatus(id)
                    ?: return errorResponse(Response.Status.NOT_FOUND, "Registrazione non trovata")
                return jsonResponse(buildJsonObject {
                    put("ok", true)
                    put("recording", recordingJson(snap))
                })
            }
            if (action == null && session.method == Method.DELETE) {
                session.drainBodyIfPresent()
                val deleted = timeshift.deleteSession(id)
                return jsonResponse(buildJsonObject {
                    put("ok", true)
                    put("deleted", deleted)
                })
            }
            if (action == "status" && session.method == Method.GET) {
                val snap = timeshift.getStatus(id)
                    ?: return errorResponse(Response.Status.NOT_FOUND, "Registrazione non trovata")
                return jsonResponse(buildJsonObject {
                    put("ok", true)
                    put("recording", recordingJson(snap))
                })
            }
            if (action == "stop" && session.method == Method.POST) {
                session.drainBodyIfPresent()
                val snap = timeshift.stopSession(id)
                    ?: return errorResponse(Response.Status.NOT_FOUND, "Registrazione non trovata")
                return jsonResponse(buildJsonObject {
                    put("ok", true)
                    put("recording", recordingJson(snap))
                })
            }
            if (action == "download" && session.method == Method.GET) {
                val payload = timeshift.openDownloadStream(id)
                    ?: return errorResponse(Response.Status.NOT_FOUND, "Nessun file scaricabile")
                val resp = if (payload.totalBytes != null && payload.totalBytes > 0L) {
                    newFixedLengthResponse(Response.Status.OK, payload.mimeType, payload.stream, payload.totalBytes)
                } else {
                    newChunkedResponse(Response.Status.OK, payload.mimeType, payload.stream)
                }
                return resp.apply {
                    addHeader("Access-Control-Allow-Origin", "*")
                    addHeader("Content-Disposition", "attachment; filename=\"${payload.fileName}\"")
                    addHeader("Cache-Control", "no-store")
                    addHeader("x-no-gzip", "1")
                }
            }
            if (action != null && session.method == Method.GET) {
                if (isHlsPlaylistFile(action)) {
                    val m3u8 = when (action) {
                        "video.m3u8" -> timeshift.renderTrackPlaylist(id, audio = false)
                        "audio.m3u8" -> timeshift.renderTrackPlaylist(id, audio = true)
                        else -> timeshift.renderMasterPlaylist(id)
                    } ?: return errorResponse(Response.Status.NOT_FOUND, "Playlist non trovata")
                    return serveHlsPlaylistResponse(m3u8)
                }
                val segFile = timeshift.resolveSegmentFile(id, action)
                    ?: return errorResponse(Response.Status.NOT_FOUND, "Segmento non trovato")
                return serveMediaSegmentResponse(segFile, action)
            }
        }
        return errorResponse(Response.Status.NOT_FOUND, "Endpoint registrazioni non trovato")
    }

    private fun relayRoutes(session: IHTTPSession, parts: List<String>): Response {
        val sub = parts.getOrNull(1)
        if (sub == "tune" && session.method == Method.POST) {
            val body = session.jsonBody()
            val deviceId = body.string("deviceId").ifBlank { session.header("x-device-id") }
                .ifBlank { "dev-${session.remoteIpAddress ?: "unknown"}" }
            val channelId = body.string("channelId").trim()
            val channelName = body.string("channelName").trim()
            val streamUrl = body.string("streamUrl").trim()
            if (channelId.isBlank() || streamUrl.isBlank()) {
                return errorResponse(Response.Status.BAD_REQUEST, "Parametri canale o flusso mancanti")
            }
            val dbCh = db.getChannel(channelId)
            val isVodStream = dbCh?.isVod == true || M3uParser.inferContentType(streamUrl, channelName) != "live"
            if (isVodStream) {
                val base = baseUrl(session)
                val encodedId = URLEncoder.encode(channelId, "UTF-8").replace("+", "%20")
                val vodUrl = if (dbCh != null) "$base/api/vod/stream/$encodedId" else streamUrl
                return jsonResponse(buildJsonObject {
                    put("ok", true)
                    put("mode", "direct")
                    put("relayKey", "")
                    put("playlistUrl", "")
                    put("url", vodUrl)
                    put("viewers", 1)
                    put("hasAudioTrack", true)
                    put("reason", "vod")
                })
            }
            val pinSource = body.bool("pinSource") ?: false
            val canonicalGroupKey = dbCh?.let { db.canonicalGroupKeyForChannel(it) }
                ?: db.canonicalGroupKeyForChannelId(channelId).orEmpty()
            if (pinSource && streamUrl.isNotBlank()) {
                db.recordSourceFeedback(canonicalGroupKey, streamUrl, "manual_select")
                liveRelay.markPreferredSource(channelId, canonicalGroupKey, streamUrl)
            }
            val result: RelayTuneResult = if (!Prefs.serverRelayEnabled(context)) {
                RelayTuneResult(mode = "direct", url = streamUrl, reason = "disabled")
            } else {
                val alternatives = dbCh?.let { ch ->
                    db.listAlternativeChannels(
                        channel = ch,
                        limit = 16,
                        sortSelfByScore = !pinSource,
                        dynamicScoreAdjustment = liveRelay::healthScoreAdjustment,
                    )
                }.orEmpty()
                val chosenPrimary = if (pinSource || alternatives.isEmpty()) {
                    streamUrl
                } else {
                    val bestCand = alternatives.firstOrNull()?.url?.trim().orEmpty()
                    bestCand.ifBlank { streamUrl }
                }
                val fallbackUrls = alternatives
                    .map { it.url.trim() }
                    .filter { it.isNotBlank() && it != chosenPrimary }
                liveRelay.tune(
                    deviceId = deviceId,
                    channelId = channelId,
                    channelName = channelName,
                    streamUrl = chosenPrimary,
                    fallbackUrls = fallbackUrls,
                    pinSource = pinSource,
                    canonicalGroupKey = canonicalGroupKey,
                )
            }
            if (result.mode == "relay") {
                bgScope.launch { runCatching { db.clearPlaylistWarnForChannel(channelId) } }
            }
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("mode", result.mode)
                put("relayKey", result.relayKey)
                put("playlistUrl", result.playlistUrl)
                put("url", result.url)
                put("viewers", result.viewers)
                put("hasAudioTrack", result.hasAudioTrack)
                put("reason", result.reason)
            })
        }
        if (sub == "prewarm" && session.method == Method.POST) {
            val body = session.jsonBody()
            val deviceId = body.string("deviceId").ifBlank { session.header("x-device-id") }
                .ifBlank { "dev-${session.remoteIpAddress ?: "unknown"}" }
            val channelId = body.string("channelId").trim()
            val channelName = body.string("channelName").trim()
            val streamUrl = body.string("streamUrl").trim()
            if (channelId.isBlank() || streamUrl.isBlank()) {
                return jsonResponse(buildJsonObject { put("ok", false); put("queued", false) })
            }
            val dbCh = db.getChannel(channelId)
            if (dbCh?.isVod == true || M3uParser.inferContentType(streamUrl, channelName) != "live") {
                return jsonResponse(buildJsonObject { put("ok", true); put("queued", false) })
            }
            if (!Prefs.serverRelayEnabled(context)) {
                return jsonResponse(buildJsonObject { put("ok", true); put("queued", false) })
            }
            bgScope.launch {
                runCatching {
                    val fallbackUrls = db.getChannel(channelId)?.let { ch ->
                        db.listAlternativeChannels(ch, limit = 4)
                            .map { it.url.trim() }
                            .filter { it.isNotBlank() && it != streamUrl }
                    }.orEmpty()
                    liveRelay.prewarm(
                        deviceId = deviceId,
                        channelId = channelId,
                        channelName = channelName,
                        streamUrl = streamUrl,
                        fallbackUrls = fallbackUrls,
                    )
                }
            }
            return jsonResponse(buildJsonObject { put("ok", true); put("queued", true) })
        }
        if (sub == "release" && session.method == Method.POST) {
            val body = session.jsonBody()
            val deviceId = body.string("deviceId").ifBlank { session.header("x-device-id") }
                .ifBlank { "dev-${session.remoteIpAddress ?: "unknown"}" }
            val channelId = body.string("channelId").trim().takeIf { it.isNotBlank() }
            liveRelay.release(deviceId, channelId)
            return jsonResponse(buildJsonObject { put("ok", true) })
        }
        if (sub == "status" && session.method == Method.GET) {
            requireAdmin(session)?.let { return it }
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("enabled", Prefs.serverRelayEnabled(context))
                putJsonArray("relays") {
                    liveRelay.listActive().forEach { item ->
                        add(buildJsonObject {
                            put("key", item.key)
                            put("channelId", item.channelId)
                            put("channelName", item.channelName)
                            put("status", item.status)
                            put("viewers", item.viewers)
                            put("segmentCount", item.segmentCount)
                            put("hasAudioTrack", item.hasAudioTrack)
                            put("bytes", item.bytes)
                            put("uptimeSeconds", item.uptimeSeconds)
                        })
                    }
                }
            })
        }
        if (sub != null && parts.size >= 3 && session.method == Method.GET) {
            val key = decodeSegment(sub)
            val fileName = decodeSegment(parts[2])
            val viewerDev = session.header("x-device-id")
            if (isHlsPlaylistFile(fileName)) {
                val m3u8 = when (fileName) {
                    "video.m3u8" -> liveRelay.renderTrackPlaylist(key, audio = false, low = false, viewerDeviceId = viewerDev)
                    "video_low.m3u8" -> liveRelay.renderTrackPlaylist(key, audio = false, low = true, viewerDeviceId = viewerDev)
                    "audio.m3u8" -> liveRelay.renderTrackPlaylist(key, audio = true, viewerDeviceId = viewerDev)
                    else -> liveRelay.renderMasterPlaylist(key, viewerDeviceId = viewerDev)
                }
                if (m3u8 == null) {
                    val meta = liveRelay.relayMetadata(key)
                    diag.log(
                        code = DiagnosticCodes.SRV_SEG_404,
                        severity = "warn",
                        channelId = meta?.first.orEmpty(),
                        channelName = meta?.second.orEmpty(),
                        relayKey = key,
                        deviceId = viewerDev,
                        deviceName = session.header("x-device-name"),
                        clientIp = session.remoteIpAddress ?: "",
                        message = "Playlist relay non trovata ($fileName)",
                        details = mapOf("file" to fileName),
                        throttleMs = 15_000L,
                    )
                    return errorResponse(Response.Status.NOT_FOUND, "Relay non trovato")
                }
                return serveHlsPlaylistResponse(m3u8)
            }
            val segFile = liveRelay.resolveSegmentFile(key, fileName)
            if (segFile == null) {
                val meta = liveRelay.relayMetadata(key)
                diag.log(
                    code = DiagnosticCodes.SRV_SEG_404,
                    severity = "warn",
                    channelId = meta?.first.orEmpty(),
                    channelName = meta?.second.orEmpty(),
                    relayKey = key,
                    deviceId = viewerDev,
                    deviceName = session.header("x-device-name"),
                    clientIp = session.remoteIpAddress ?: "",
                    message = "Segmento relay non trovato ($fileName)",
                    details = mapOf("file" to fileName),
                    throttleMs = 15_000L,
                )
                return errorResponse(Response.Status.NOT_FOUND, "Segmento relay non trovato")
            }
            return serveMediaSegmentResponse(segFile, fileName)
        }
        return errorResponse(Response.Status.NOT_FOUND, "Endpoint relay non trovato")
    }

    private fun putVodResolvedCache(id: String, url: String) {
        val now = System.currentTimeMillis()
        if (vodResolvedUrlCache.size > 256) {
            vodResolvedUrlCache.entries.removeIf { now - it.value.second >= 15 * 60_000L }
            if (vodResolvedUrlCache.size > 256) {
                vodResolvedUrlCache.entries
                    .sortedBy { it.value.second }
                    .take(64)
                    .forEach { vodResolvedUrlCache.remove(it.key) }
            }
        }
        vodResolvedUrlCache[id] = url to now
    }

    private fun resolveWorkingVodUrl(ch: IptvChannel): String? {
        val primaryUrl = ch.url.trim()
        if (primaryUrl.isBlank()) return null
        val nowMs = System.currentTimeMillis()
        vodResolvedUrlCache[ch.id]?.takeIf { nowMs - it.second < 15 * 60_000L }?.first?.let { return it }
        val candidates = LinkedHashSet<String>(6).apply {
            add(primaryUrl)
            runCatching { db.listAlternativeChannels(ch) }.getOrDefault(emptyList()).forEach { alt ->
                val u = alt.url.trim()
                if (u.isNotBlank()) add(u)
            }
        }.take(5)
        for (candUrl in candidates) {
            val probeReq = okhttp3.Request.Builder()
                .url(candUrl)
                .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                .header("Accept", "*/*")
                .header("Range", "bytes=0-1")
                .build()
            val ok = runCatching {
                vodHttpClient.newCall(probeReq).execute().use { resp -> resp.code < 400 }
            }.getOrDefault(false)
            if (ok) {
                putVodResolvedCache(ch.id, candUrl)
                return candUrl
            }
        }
        return primaryUrl
    }

    private fun vodRoutes(session: IHTTPSession, parts: List<String>): Response {
        val sub = parts.getOrNull(1)
        val profile = activeProfile(session)
        val lang = profile.language
        val base = baseUrl(session)
        val favorites = db.favoriteIds(profile.id)

        if (sub == "continue" && session.method == Method.GET) {
            val limit = session.parm("limit").toIntOrNull()?.coerceIn(1, 60) ?: 24
            val rows = db.listContinueWatching(profile.id, limit)
            return jsonResponse(
                buildJsonObject {
                    putJsonArray("items") {
                        rows.forEach { (ch, posMs, durMs) ->
                            add(iptvChannelJson(ch, ch.number, lang, ch.id in favorites, base, posMs, durMs))
                        }
                    }
                },
            )
        }

        if (sub == "continue" && session.method == Method.DELETE) {
            val id = decodeSegment(parts.getOrNull(2).orEmpty())
            if (id.isBlank()) return errorResponse(Response.Status.BAD_REQUEST, "ID mancante")
            db.clearVodProgress(profile.id, id)
            return jsonResponse(buildJsonObject { put("ok", true) })
        }

        if (sub == "categories" && session.method == Method.GET) {
            val vodLangParam = session.parm("lang").takeIf { it.isNotBlank() }
            val contentType = session.parm("type").ifBlank { "vod" }
            val limit = session.parm("limit").toIntOrNull()?.coerceIn(1, 300) ?: 240
            val globalRawCats = db.listVodCategories(null, contentType, 300)
            val byCountry = db.listVodCountryCounts(contentType)
            val vodLanguages = VodTaxonomy.classifyVodLanguages(byCountry, globalRawCats, lang)
            val scopedRawCats = if (vodLangParam != null) {
                val langCountries = VodTaxonomy.resolveLanguageCountries(vodLangParam)
                val langCats = VodTaxonomy.resolveLanguageRawCategories(globalRawCats, vodLangParam)
                if (langCountries.isEmpty() && langCats.isEmpty()) {
                    emptyList()
                } else {
                    db.listVodCategories(null, contentType, limit, langCountries, langCats)
                }
            } else {
                globalRawCats.take(limit)
            }
            val tax = VodTaxonomy.classifyRawCategories(scopedRawCats, lang)
            val genres = when {
                tax.genres.isNotEmpty() -> tax.genres
                vodLangParam != null -> {
                    val globalGenres = VodTaxonomy.classifyRawCategories(globalRawCats, lang).genres
                    if (globalGenres.isNotEmpty()) globalGenres
                    else globalRawCats.take(12).map { (cat, cnt) ->
                        it.casatv.server.core.VodFacetItem("raw:$cat", categoryName(cat, lang), cnt)
                    }
                }
                else -> scopedRawCats.filter { (cat, _) ->
                    VodTaxonomy.PLATFORMS.none { p -> p.matchers.any { it.containsMatchIn(cat) } }
                }.take(12).map { (cat, cnt) ->
                    it.casatv.server.core.VodFacetItem("raw:$cat", categoryName(cat, lang), cnt)
                }
            }
            return jsonResponse(
                buildJsonObject {
                    putJsonArray("languages") {
                        vodLanguages.forEach { l ->
                            add(
                                buildJsonObject {
                                    put("code", l.code)
                                    put("name", l.name)
                                    put("flag", l.flag)
                                    put("count", l.count)
                                },
                            )
                        }
                    }
                    putJsonArray("genres") {
                        genres.forEach { g ->
                            add(
                                buildJsonObject {
                                    put("id", g.id)
                                    put("name", g.name)
                                    put("count", g.count)
                                },
                            )
                        }
                    }
                    putJsonArray("platforms") {
                        tax.platforms.forEach { p ->
                            add(
                                buildJsonObject {
                                    put("id", p.id)
                                    put("name", p.name)
                                    put("count", p.count)
                                },
                            )
                        }
                    }
                    putJsonArray("categories") {
                        genres.forEach { g ->
                            add(
                                buildJsonObject {
                                    put("id", g.id)
                                    put("name", g.name)
                                    put("count", g.count)
                                },
                            )
                        }
                    }
                },
            )
        }

        if (sub == "series" && session.method == Method.GET) {
            val id = decodeSegment(parts.getOrNull(2).orEmpty())
            val detailPair = db.getSeriesDetail(profile.id, id)
                ?: return errorResponse(Response.Status.NOT_FOUND, "Serie non trovata")
            val (detail, progMap) = detailPair
            val rep = detail.representative
            val encodedRepId = URLEncoder.encode(rep.id, "UTF-8").replace("+", "%20")
            val repLogo = rep.logo?.takeIf { it.isNotBlank() }?.let { "$base/logo/$encodedRepId" }
            return jsonResponse(
                buildJsonObject {
                    put("showTitle", detail.showTitle)
                    putOrNull("logo", repLogo)
                    putOrNull("country", rep.country.takeIf { it.isNotBlank() })
                    put("countryName", if (rep.country.isNotBlank()) countryName(rep.country, lang) else "")
                    put("flag", if (rep.country.isNotBlank()) countryFlag(rep.country) else "")
                    put("category", categoryName(rep.category, lang))
                    put("totalSeasons", detail.seasons.size)
                    put("totalEpisodes", detail.totalEpisodes)
                    putOrNull("resumeEpisodeId", detail.resumeEpisodeId)
                    putJsonArray("seasons") {
                        detail.seasons.forEach { (seasonNum, eps) ->
                            add(
                                buildJsonObject {
                                    put("season", seasonNum)
                                    putJsonArray("episodes") {
                                        eps.forEach { ep ->
                                            val prog = progMap[ep.id]
                                            add(
                                                iptvChannelJson(
                                                    channel = ep,
                                                    number = ep.number,
                                                    language = lang,
                                                    favorite = ep.id in favorites,
                                                    baseUrl = base,
                                                    positionMs = prog?.first ?: 0L,
                                                    durationMs = prog?.second ?: 0L,
                                                ),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }

        if (sub == "related" && session.method == Method.GET) {
            val id = decodeSegment(parts.getOrNull(2).orEmpty())
            val ch = db.getChannel(id) ?: return errorResponse(Response.Status.NOT_FOUND, "Contenuto non trovato")
            val limit = session.parm("limit").toIntOrNull()?.coerceIn(1, 80) ?: 36
            val (mode, items) = db.listRelatedVodOrChannels(ch, limit)
            val progressMap = if (ch.isVod) db.getVodProgressMap(profile.id, items.map { it.id }) else emptyMap()
            return jsonResponse(
                buildJsonObject {
                    put("mode", mode)
                    putJsonArray("items") {
                        items.forEach { item ->
                            val prog = progressMap[item.id]
                            add(
                                iptvChannelJson(
                                    channel = item,
                                    number = item.number,
                                    language = lang,
                                    favorite = item.id in favorites,
                                    baseUrl = base,
                                    positionMs = prog?.first ?: 0L,
                                    durationMs = prog?.second ?: 0L,
                                ),
                            )
                        }
                    }
                },
            )
        }

        if (sub == "hls" && session.method == Method.GET) {
            val id = decodeSegment(parts.getOrNull(2).orEmpty())
            val file = parts.getOrNull(3).orEmpty()
            val ch = db.getChannel(id) ?: return errorResponse(Response.Status.NOT_FOUND, "Contenuto non trovato")
            val resolvedUrl = vodRemuxer.getActiveSessionUrl(id)
                ?: resolveWorkingVodUrl(ch)
                ?: return errorResponse(Response.Status.BAD_REQUEST, "Nessuna sorgente VOD disponibile")
            if (file == "playlist.m3u8") {
                val m3u8 = vodRemuxer.getPlaylist(id, resolvedUrl)
                    ?: return errorResponse(Response.Status.BAD_REQUEST, "Impossibile preparare flusso HLS VOD")
                val bytes = m3u8.toByteArray(Charsets.UTF_8)
                return newFixedLengthResponse(Response.Status.OK, "application/vnd.apple.mpegurl", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
                    addHeader("Access-Control-Allow-Origin", "*")
                    addHeader("Cache-Control", "no-cache")
                }
            }
            if (file.startsWith("seg_") && file.endsWith(".ts")) {
                val segIdx = file.removePrefix("seg_").removeSuffix(".ts").toIntOrNull()
                    ?: return errorResponse(Response.Status.BAD_REQUEST, "Segmento non valido")
                val bytes = vodRemuxer.getSegment(id, resolvedUrl, segIdx)
                    ?: return errorResponse(Response.Status.NOT_FOUND, "Segmento non disponibile")
                return newFixedLengthResponse(Response.Status.OK, "video/mp2t", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
                    addHeader("Access-Control-Allow-Origin", "*")
                    addHeader("Cache-Control", "private, max-age=120")
                    addHeader("x-no-gzip", "1")
                }
            }
            return errorResponse(Response.Status.NOT_FOUND, "Risorsa HLS VOD non trovata")
        }

        if (sub == "stream" && (session.method == Method.GET || session.method == Method.HEAD)) {
            val id = decodeSegment(parts.getOrNull(2).orEmpty())
            val ch = db.getChannel(id) ?: return errorResponse(Response.Status.NOT_FOUND, "Contenuto non trovato")
            val primaryUrl = ch.url.trim()
            if (primaryUrl.isBlank()) return errorResponse(Response.Status.NOT_FOUND, "URL flusso non trovato")

            val nowMs = System.currentTimeMillis()
            val cachedEntry = vodResolvedUrlCache[id]?.takeIf { nowMs - it.second < 15 * 60_000L }?.first
            val rangeHeader = session.header("range").trim()
            var chosenResp: okhttp3.Response? = null
            var resolvedUrl = primaryUrl
            var lastCode = 0
            var lastErr: String? = null
            val triedUrls = LinkedHashSet<String>(6)

            fun openUpstream(url: String, rangeHdr: String): okhttp3.Response? {
                val reqBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                    .header("Accept", "*/*")
                if (rangeHdr.isNotBlank()) reqBuilder.header("Range", rangeHdr)
                if (session.method == Method.HEAD) reqBuilder.head()
                return try {
                    val r = vodHttpClient.newCall(reqBuilder.build()).execute()
                    if (r.code < 400) {
                        r
                    } else {
                        lastCode = r.code
                        r.close()
                        null
                    }
                } catch (e: Exception) {
                    lastErr = e.message ?: "connessione fallita"
                    null
                }
            }

            if (!cachedEntry.isNullOrBlank()) {
                triedUrls.add(cachedEntry)
                val r = openUpstream(cachedEntry, rangeHeader)
                if (r != null) {
                    chosenResp = r
                    resolvedUrl = cachedEntry
                    putVodResolvedCache(id, cachedEntry)
                } else {
                    vodResolvedUrlCache.remove(id)
                }
            }

            if (chosenResp == null && lastCode != 416) {
                val fallbackCandidates = LinkedHashSet<String>(6).apply {
                    add(primaryUrl)
                    runCatching { db.listAlternativeChannels(ch) }.getOrDefault(emptyList()).forEach { alt ->
                        val u = alt.url.trim()
                        if (u.isNotBlank()) add(u)
                    }
                }.filter { it !in triedUrls }.take(5)

                for (candUrl in fallbackCandidates) {
                    triedUrls.add(candUrl)
                    val r = openUpstream(candUrl, rangeHeader)
                    if (r != null) {
                        chosenResp = r
                        resolvedUrl = candUrl
                        putVodResolvedCache(id, candUrl)
                        break
                    }
                    if (lastCode == 416) break
                }
            }

            val upstreamResp = chosenResp ?: run {
                if (lastCode >= 400) {
                    val errStatus = when (lastCode) {
                        401 -> Response.Status.UNAUTHORIZED
                        403 -> Response.Status.FORBIDDEN
                        404 -> Response.Status.NOT_FOUND
                        416 -> Response.Status.RANGE_NOT_SATISFIABLE
                        else -> Response.Status.BAD_REQUEST
                    }
                    return errorResponse(errStatus, "Sorgente VOD ha risposto HTTP $lastCode")
                }
                return errorResponse(Response.Status.BAD_REQUEST, "Errore sorgente VOD: ${lastErr ?: "nessuna sorgente disponibile"}")
            }

            val code = upstreamResp.code
            val cleanPath = resolvedUrl.substringBefore('?').substringBefore('#').lowercase(java.util.Locale.ROOT)
            val mime = when {
                cleanPath.endsWith(".mp4") || cleanPath.endsWith(".m4v") -> "video/mp4"
                cleanPath.endsWith(".mkv") -> "video/x-matroska"
                cleanPath.endsWith(".webm") -> "video/webm"
                cleanPath.endsWith(".avi") -> "video/x-msvideo"
                cleanPath.endsWith(".mov") -> "video/quicktime"
                else -> upstreamResp.header("Content-Type")?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                    ?: "video/mp4"
            }
            val contentLength = upstreamResp.header("Content-Length")?.toLongOrNull() ?: -1L
            val contentRange = upstreamResp.header("Content-Range")
            val nanoStatus = when (code) {
                206 -> Response.Status.PARTIAL_CONTENT
                else -> Response.Status.OK
            }

            if (session.method == Method.HEAD) {
                upstreamResp.close()
                return newFixedLengthResponse(nanoStatus, mime, ByteArrayInputStream(ByteArray(0)), 0L).apply {
                    addHeader("Access-Control-Allow-Origin", "*")
                    addHeader("Access-Control-Expose-Headers", "Content-Range, Accept-Ranges, Content-Length")
                    addHeader("Accept-Ranges", "bytes")
                    if (contentLength >= 0L) addHeader("Content-Length", contentLength.toString())
                    if (!contentRange.isNullOrBlank()) addHeader("Content-Range", contentRange)
                    addHeader("x-no-gzip", "1")
                }
            }

            val bodyStream = upstreamResp.body?.byteStream()
            if (bodyStream == null) {
                upstreamResp.close()
                return errorResponse(Response.Status.INTERNAL_ERROR, "Stream vuoto dalla sorgente")
            }

            val parsedRangeStart = contentRange
                ?.substringAfter("bytes ", "")
                ?.substringBefore('-', "")
                ?.trim()
                ?.toLongOrNull()
                ?: if (rangeHeader.startsWith("bytes=", ignoreCase = true)) {
                    rangeHeader.substringAfter('=').substringBefore('-').trim().toLongOrNull() ?: 0L
                } else {
                    0L
                }

            val resilientStream = ResilientVodInputStream(
                initialResp = upstreamResp,
                initialStream = bodyStream,
                initialUrl = resolvedUrl,
                rangeStart = parsedRangeStart,
                expectedLength = contentLength,
                channel = ch,
                client = vodHttpClient,
                candidateSupplier = {
                    LinkedHashSet<String>(6).apply {
                        add(resolvedUrl)
                        add(primaryUrl)
                        runCatching { db.listAlternativeChannels(ch) }.getOrDefault(emptyList()).forEach { alt ->
                            val u = alt.url.trim()
                            if (u.isNotBlank()) add(u)
                        }
                    }.toList()
                },
            )
            val resp = if (contentLength >= 0L) {
                newFixedLengthResponse(nanoStatus, mime, resilientStream, contentLength)
            } else {
                newChunkedResponse(nanoStatus, mime, resilientStream)
            }
            return resp.apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Access-Control-Expose-Headers", "Content-Range, Accept-Ranges, Content-Length")
                addHeader("Accept-Ranges", "bytes")
                if (!contentRange.isNullOrBlank()) addHeader("Content-Range", contentRange)
                addHeader("Cache-Control", "no-store")
                addHeader("x-no-gzip", "1")
            }
        }

        return errorResponse(Response.Status.NOT_FOUND, "Endpoint VOD non trovato")
    }

    private fun isHlsPlaylistFile(fileName: String): Boolean =
        fileName == "master.m3u8" || fileName == "playlist.m3u8" || fileName == "video.m3u8" || fileName == "video_low.m3u8" || fileName == "audio.m3u8"

    private fun serveHlsPlaylistResponse(m3u8: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/vnd.apple.mpegurl", m3u8).apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            addHeader("Keep-Alive", "timeout=55, max=200")
        }

    private fun serveMediaSegmentResponse(segFile: File, fileName: String): Response {
        val mime = when {
            fileName.endsWith(".mp4") || fileName.endsWith(".m4s") || fileName.endsWith(".m4v") || fileName.endsWith(".m4a") -> "video/mp4"
            fileName.endsWith(".ts") -> "video/mp2t"
            fileName.endsWith(".aac") -> "audio/aac"
            else -> "application/octet-stream"
        }
        return newFixedLengthResponse(
            Response.Status.OK,
            mime,
            java.io.BufferedInputStream(FileInputStream(segFile), 64 * 1024),
            segFile.length(),
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Cache-Control", "public, max-age=3600")
            addHeader("Keep-Alive", "timeout=55, max=200")
            addHeader("x-no-gzip", "1")
        }
    }

    private fun timeshiftJson(s: TimeshiftSessionSnapshot): JsonObject = buildJsonObject {
        put("id", s.id)
        put("deviceId", s.deviceId)
        put("channelId", s.channelId)
        put("channelName", s.channelName)
        put("country", s.country)
        s.logo?.let { put("logo", it) }
        put("status", s.status)
        put("startedAtMs", s.startedAtMs)
        put("endedAtMs", s.endedAtMs)
        put("expiresAtMs", s.expiresAtMs)
        put("remainingMs", (s.expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L))
        put("recordedMs", s.recordedMs)
        put("maxDurationMs", s.maxDurationMs)
        put("maxHours", s.maxHours)
        put("bytes", s.bytes)
        put("segmentCount", s.segmentCount)
        put("hasAudioTrack", s.hasAudioTrack)
        put("error", s.error)
        put("playlistUrl", s.playlistUrl)
        put("playUrl", s.playlistUrl)
        put("downloadUrl", s.downloadUrl)
    }

    private fun recordingJson(s: TimeshiftSessionSnapshot): JsonObject = timeshiftJson(s)

    /* ------------------------------------------------------------------ */
    /* Diagnostics API (/api/diagnostics/...)                              */
    /* ------------------------------------------------------------------ */

    private fun isBufferingOrPlaybackCode(code: String): Boolean {
        val up = code.uppercase(Locale.ROOT)
        return "BUF" in up || "STALL" in up || "RECONNECT" in up || "UNDERRUN" in up ||
            "DROP" in up || "HLS" in up || "LOAD_ERROR" in up || "ERR" in up
    }

    private fun inferBufferingCause(details: org.json.JSONObject): String {
        val reason = details.optString("reason", "")
        val sinceSyncSeekMs = details.optLong("sinceSyncSeekMs", -1L)
        val httpStatus = details.optInt("httpStatus", details.optInt("httpStatusCode", 0))
        val relayStatus = details.optString("relayStatus", "")
        val exoBw = details.optInt("exoBandwidthKbps", 0)
        val vBitrate = details.optInt("videoBitrateKbps", 0)
        val stallCount = details.optInt("sourceStallCount", 0)
        val serverImportActive = details.optBoolean("serverImportActive", false)
        val bufAheadMs = details.optLong("bufferAheadMs", -1L)
        val dropped = details.optInt("droppedFrames", 0)
        val hlsDetails = details.optString("hlsDetails", "")
        return when {
            reason == "post_sync_seek" || (sinceSyncSeekMs in 1L..6_000L) ->
                "Seek di sincronizzazione Multi-TV"
            httpStatus >= 400 ->
                "Errore HTTP upstream ($httpStatus) dal provider"
            relayStatus == "error" || relayStatus == "stalled" || relayStatus == "dead" ->
                "Flusso upstream interrotto o sorgente non rispondente"
            hlsDetails == "fragLoadTimeOut" || hlsDetails == "fragLoadError" || hlsDetails == "manifestLoadError" ->
                "Segmento HLS lento o non raggiungibile ($hlsDetails)"
            exoBw > 0 && vBitrate > 0 && exoBw < (vBitrate * 0.85) ->
                "Banda insufficiente (${exoBw} kbps < ${vBitrate} kbps richiesti)"
            stallCount >= 2 ->
                "Sorgente upstream instabile ($stallCount stalli registrati)"
            serverImportActive && bufAheadMs in 0L..500L ->
                "Contesa I/O durante importazione playlist sul server"
            bufAheadMs in 0L..500L ->
                "Buffer esaurito (sorgente upstream lenta o congestione rete)"
            dropped >= 15 ->
                "Sovraccarico decoder video sul dispositivo ($dropped frame persi)"
            else ->
                "Buffering temporaneo / ricaricamento segmenti"
        }
    }

    private fun enrichDiagnosticEvent(input: DiagnosticLogInput): DiagnosticLogInput {
        if (!isBufferingOrPlaybackCode(input.code)) return input
        val details = runCatching {
            if (input.detailsJson.isNotBlank() && input.detailsJson != "{}") {
                org.json.JSONObject(input.detailsJson)
            } else {
                org.json.JSONObject()
            }
        }.getOrElse { org.json.JSONObject() }
        val importActive = runCatching {
            it.casatv.server.core.PlaylistImportEngine.get(context).hasActiveImport()
        }.getOrDefault(false)
        details.put("serverImportActive", importActive)
        if (input.streamUrl.isNotBlank()) {
            val (rStatus, rLatency) = runCatching { liveRelay.streamHealthInfo(input.streamUrl) }.getOrElse { "unknown" to null }
            val stalls = runCatching { db.sourceStallCount(input.streamUrl) }.getOrDefault(0)
            details.put("relayStatus", rStatus)
            if (rLatency != null) details.put("relayLatencyMs", rLatency)
            details.put("sourceStallCount", stalls)
        }
        val cause = details.optString("probableCause", "").ifBlank { inferBufferingCause(details) }
        details.put("probableCause", cause)
        val enrichedJson = details.toString()
        val devLabel = input.deviceName.ifBlank { input.deviceId.ifBlank { input.source } }
        android.util.Log.w(
            "CasaBufDiag",
            "[${input.code}] dev='$devLabel' ch='${input.channelName}' mode=${input.playbackMode.ifBlank { "-" }} cause='$cause' msg='${input.message}' details=$enrichedJson",
        )
        return input.copy(detailsJson = enrichedJson)
    }

    private fun diagnosticsRoutes(session: IHTTPSession, parts: List<String>): Response {
        val sub = parts.getOrNull(1)
        if ((sub == "events" || sub == "log") && session.method == Method.POST) {
            val body = session.jsonBody()
            val items = when (val arr = body["events"]) {
                is JsonArray -> arr.mapNotNull { it as? JsonObject }.take(100)
                else -> if (body.string("code").isNotBlank()) listOf(body) else emptyList()
            }
            val now = System.currentTimeMillis()
            val minAllowed = now - 365L * 86_400_000L
            val maxAllowed = now + 3_600_000L
            val hdrDevId = session.header("x-device-id")
            val hdrDevName = session.header("x-device-name")
            val hdrAppVer = session.header("x-app-version")
            val hdrIp = session.remoteIpAddress ?: ""
            val inputs = items.mapNotNull { obj ->
                val code = obj.string("code").trim()
                if (code.isBlank()) return@mapNotNull null
                val rawTs = obj.long("tsMs") ?: now
                val tsMs = if (rawTs <= 0L) now else rawTs.coerceIn(minAllowed, maxAllowed)
                val detailsElem = obj["details"]
                val detailsJson = when (detailsElem) {
                    is JsonObject -> detailsElem.toString()
                    is JsonPrimitive -> obj.string("detailsJson").ifBlank { detailsElem.content }
                    else -> obj.string("detailsJson").ifBlank { "{}" }
                }
                enrichDiagnosticEvent(
                    DiagnosticLogInput(
                        tsMs = tsMs,
                        source = obj.string("source").ifBlank { "tv" },
                        severity = obj.string("severity").ifBlank { "warn" },
                        code = code,
                        deviceId = obj.string("deviceId").ifBlank { hdrDevId },
                        deviceName = obj.string("deviceName").ifBlank { hdrDevName },
                        appVersion = obj.string("appVersion").ifBlank { hdrAppVer },
                        clientIp = obj.string("clientIp").ifBlank { hdrIp },
                        channelId = obj.string("channelId"),
                        channelName = obj.string("channelName"),
                        playbackMode = obj.string("playbackMode"),
                        relayKey = obj.string("relayKey"),
                        streamUrl = obj.string("streamUrl"),
                        message = obj.string("message"),
                        detailsJson = detailsJson,
                    ),
                )
            }
            val accepted = db.insertDiagnosticLogs(inputs)
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("accepted", accepted)
            })
        }
        if (sub == "buffering" && session.method == Method.GET) {
            requireAdmin(session)?.let { return it }
            val now = System.currentTimeMillis()
            val hours = session.parm("hours").toIntOrNull()?.coerceIn(1, 24 * 30) ?: 24
            val limit = session.parm("limit").toIntOrNull()?.coerceIn(1, 200) ?: 100
            val sinceMs = now - hours.toLong() * 3_600_000L
            val allRecent = db.queryDiagnosticLogs(
                sinceMs = sinceMs,
                limit = 250,
                offset = 0,
            )
            val bufRows = allRecent.filter { isBufferingOrPlaybackCode(it.code) }.take(limit)
            val causeCounts = LinkedHashMap<String, Int>()
            val channelCounts = LinkedHashMap<String, Int>()
            val eventObjects = bufRows.map { row ->
                val detailsObj = runCatching {
                    if (row.detailsJson.isNotBlank() && row.detailsJson != "{}") org.json.JSONObject(row.detailsJson)
                    else org.json.JSONObject()
                }.getOrElse { org.json.JSONObject() }
                val cause = detailsObj.optString("probableCause", "").ifBlank { inferBufferingCause(detailsObj) }
                causeCounts[cause] = (causeCounts[cause] ?: 0) + 1
                val chKey = row.channelName.ifBlank { row.channelId.ifBlank { "Sconosciuto" } }
                channelCounts[chKey] = (channelCounts[chKey] ?: 0) + 1
                val parsedDetails: JsonElement = runCatching {
                    ApiJson.parseToJsonElement(row.detailsJson)
                }.getOrElse { JsonPrimitive(row.detailsJson) }
                buildJsonObject {
                    put("id", row.id)
                    put("tsMs", row.tsMs)
                    put("tsIso", Instant.ofEpochMilli(row.tsMs).toString())
                    put("source", row.source)
                    put("severity", row.severity)
                    put("code", row.code)
                    put("deviceName", row.deviceName.ifBlank { row.deviceId.ifBlank { row.source } })
                    put("channelName", row.channelName)
                    put("channelId", row.channelId)
                    put("playbackMode", row.playbackMode)
                    put("message", row.message)
                    put("probableCause", cause)
                    put("details", parsedDetails)
                }
            }
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("hours", hours)
                put("count", eventObjects.size)
                putJsonArray("summaryByCause") {
                    causeCounts.entries.sortedByDescending { it.value }.forEach { (c, n) ->
                        add(buildJsonObject { put("cause", c); put("count", n) })
                    }
                }
                putJsonArray("summaryByChannel") {
                    channelCounts.entries.sortedByDescending { it.value }.take(15).forEach { (ch, n) ->
                        add(buildJsonObject { put("channel", ch); put("count", n) })
                    }
                }
                putJsonArray("events") { eventObjects.forEach { add(it) } }
            })
        }
        if (sub == "logs" && session.method == Method.GET) {
            requireAdmin(session)?.let { return it }
            val now = System.currentTimeMillis()
            val hours = session.parm("hours").toIntOrNull()?.coerceIn(1, 24 * 30)
            val days = session.parm("days").toIntOrNull()?.coerceIn(1, 30)
                ?: ServerDatabase.DIAGNOSTIC_RETENTION_DAYS
            val sinceMs = if (hours != null) {
                now - hours.toLong() * 3_600_000L
            } else {
                now - days.toLong() * 86_400_000L
            }
            val rows = db.queryDiagnosticLogs(
                sinceMs = sinceMs,
                untilMs = session.parm("untilMs").toLongOrNull(),
                code = session.parm("code").takeIf { it.isNotBlank() },
                channel = session.parm("channel").takeIf { it.isNotBlank() },
                device = session.parm("device").takeIf { it.isNotBlank() },
                severity = session.parm("severity").takeIf { it.isNotBlank() },
                source = session.parm("source").takeIf { it.isNotBlank() },
                limit = session.parm("limit").toIntOrNull() ?: 100,
                offset = session.parm("offset").toIntOrNull() ?: 0,
            )
            if (session.parm("format").equals("text", ignoreCase = true)) {
                val lines = buildString {
                    for (row in rows) {
                        val tsIso = Instant.ofEpochMilli(row.tsMs).toString()
                        val devLabel = row.deviceName.ifBlank { row.deviceId.ifBlank { row.source } }
                        val chLabel = listOfNotNull(
                            row.channelName.takeIf { it.isNotBlank() },
                            row.channelId.takeIf { it.isNotBlank() }?.let { "($it)" },
                        ).joinToString(" ")
                        append("[$tsIso] [${row.severity.uppercase()}] [${row.code}] ")
                        append("dev=\"$devLabel\" ")
                        if (chLabel.isNotBlank()) append("ch=\"$chLabel\" ")
                        if (row.playbackMode.isNotBlank()) append("mode=${row.playbackMode} ")
                        if (row.message.isNotBlank()) append("msg=\"${row.message}\" ")
                        if (row.detailsJson.isNotBlank() && row.detailsJson != "{}") append("details=${row.detailsJson}")
                        append('\n')
                    }
                }
                return textResponse(lines, "text/plain; charset=utf-8")
            }
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("retentionDays", ServerDatabase.DIAGNOSTIC_RETENTION_DAYS)
                put("count", rows.size)
                putJsonArray("logs") {
                    rows.forEach { row ->
                        val parsedDetails: JsonElement = runCatching {
                            ApiJson.parseToJsonElement(row.detailsJson)
                        }.getOrElse { JsonPrimitive(row.detailsJson) }
                        add(buildJsonObject {
                            put("id", row.id)
                            put("tsMs", row.tsMs)
                            put("tsIso", Instant.ofEpochMilli(row.tsMs).toString())
                            put("source", row.source)
                            put("severity", row.severity)
                            put("code", row.code)
                            put("deviceId", row.deviceId)
                            put("deviceName", row.deviceName)
                            put("appVersion", row.appVersion)
                            put("clientIp", row.clientIp)
                            put("channelId", row.channelId)
                            put("channelName", row.channelName)
                            put("playbackMode", row.playbackMode)
                            put("relayKey", row.relayKey)
                            put("streamUrl", row.streamUrl)
                            put("message", row.message)
                            put("details", parsedDetails)
                        })
                    }
                }
            })
        }
        if (sub == "logs" && session.method == Method.DELETE) {
            requireAdmin(session)?.let { return it }
            val device = session.parm("device").trim()
            if (device.isBlank()) {
                return errorResponse(Response.Status.BAD_REQUEST, "Parametro device obbligatorio")
            }
            val deleted = db.deleteDiagnosticLogsForDevice(device)
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("deleted", deleted)
            })
        }
        if (sub == "summary" && session.method == Method.GET) {
            requireAdmin(session)?.let { return it }
            val now = System.currentTimeMillis()
            val hours = session.parm("hours").toIntOrNull()?.coerceIn(1, 24 * 30)
            val days = session.parm("days").toIntOrNull()?.coerceIn(1, 30)
                ?: ServerDatabase.DIAGNOSTIC_RETENTION_DAYS
            val sinceMs = if (hours != null) {
                now - hours.toLong() * 3_600_000L
            } else {
                now - days.toLong() * 86_400_000L
            }
            val summary = db.diagnosticSummary(sinceMs)
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("retentionDays", summary.retentionDays)
                put("total", summary.totalCount)
                putOrNull("oldestTsMs", summary.oldestTsMs)
                putOrNull("oldestTsIso", summary.oldestTsMs?.let { Instant.ofEpochMilli(it).toString() })
                putOrNull("newestTsMs", summary.newestTsMs)
                putOrNull("newestTsIso", summary.newestTsMs?.let { Instant.ofEpochMilli(it).toString() })
                putJsonObject("bySeverity") {
                    summary.bySeverity.forEach { (sev, cnt) -> put(sev, cnt) }
                }
                putJsonArray("topCodes") {
                    summary.topCodes.forEach { st ->
                        add(buildJsonObject {
                            put("code", st.code)
                            put("count", st.count)
                            put("lastTsMs", st.lastTsMs)
                            put("lastTsIso", Instant.ofEpochMilli(st.lastTsMs).toString())
                        })
                    }
                }
                putJsonArray("topChannels") {
                    summary.topChannels.forEach { ch ->
                        add(buildJsonObject {
                            put("channelId", ch.channelId)
                            put("channelName", ch.channelName)
                            put("errorCount", ch.errorCount)
                            put("warnCount", ch.warnCount)
                            put("totalCount", ch.totalCount)
                            put("topCode", ch.topCode)
                            put("lastTsMs", ch.lastTsMs)
                            put("lastTsIso", Instant.ofEpochMilli(ch.lastTsMs).toString())
                        })
                    }
                }
                putJsonArray("byDevice") {
                    summary.byDevice.forEach { dev ->
                        add(buildJsonObject {
                            put("deviceId", dev.deviceId)
                            put("deviceName", dev.deviceName)
                            put("appVersion", dev.appVersion)
                            put("errorCount", dev.errorCount)
                            put("warnCount", dev.warnCount)
                            put("totalCount", dev.totalCount)
                            put("lastTsMs", dev.lastTsMs)
                            put("lastTsIso", Instant.ofEpochMilli(dev.lastTsMs).toString())
                        })
                    }
                }
            })
        }
        if (sub == "codes" && session.method == Method.GET) {
            requireAdmin(session)?.let { return it }
            val catalog = DiagnosticCodes.codeCatalog()
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("retentionDays", ServerDatabase.DIAGNOSTIC_RETENTION_DAYS)
                putJsonArray("codes") {
                    catalog.forEach { (code, pair) ->
                        add(buildJsonObject {
                            put("code", code)
                            put("defaultSeverity", pair.first)
                            put("description", pair.second)
                        })
                    }
                }
            })
        }
        if (sub == "prune" && session.method == Method.POST) {
            requireAdmin(session)?.let { return it }
            val body = session.jsonBody()
            val days = session.parm("days").toIntOrNull()
                ?: body.int("days")
                ?: ServerDatabase.DIAGNOSTIC_RETENTION_DAYS
            val safeDays = days.coerceAtLeast(1)
            val deleted = db.pruneOldDiagnosticLogs(safeDays)
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("retentionDays", safeDays)
                put("deleted", deleted)
            })
        }
        return errorResponse(Response.Status.NOT_FOUND, "Endpoint diagnostica non trovato")
    }

    /* ------------------------------------------------------------------ */
    /* JSON builders                                                       */
    /* ------------------------------------------------------------------ */

    private fun profileJson(profile: ProfileRow, language: String): JsonObject = buildJsonObject {
        put("id", profile.id)
        put("name", profile.name)
        put("avatar", profile.avatar)
        put("language", profile.language)
        put("languageName", languageLabel(profile.language))
        put("country", profile.country)
        put("countryName", countryName(profile.country, language))
        put("flag", countryFlag(profile.country))
        put("isGuest", profile.id == "guest")
        put("createdAt", profile.createdAt)
        put("updatedAt", profile.updatedAt)
    }

    private fun serveLogo(session: IHTTPSession, rawId: String): Response {
        val channelId = runCatching {
            URLDecoder.decode(rawId.trim('/'), "UTF-8")
        }.getOrDefault(rawId.trim('/'))
        if (channelId.isBlank()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Canale non trovato").apply {
                addHeader("Access-Control-Allow-Origin", "*")
            }
        }
        val channel = db.getChannel(channelId)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Canale non trovato").apply {
                addHeader("Access-Control-Allow-Origin", "*")
            }

        var rawLogo = channel.logo?.takeIf { it.isNotBlank() }
        var cached = rawLogo?.let {
            val expectedEtag = "\"${logoCache.etagFor(it)}\""
            if (!channel.isVod && session.header("if-none-match") == expectedEtag && logoCache.isCachedOnDisk(it)) {
                return newFixedLengthResponse(Response.Status.NOT_MODIFIED, "image/png", "").apply {
                    addHeader("Access-Control-Allow-Origin", "*")
                    addHeader("Cache-Control", "public, max-age=2592000, immutable")
                    addHeader("ETag", expectedEtag)
                }
            }
            logoCache.getOrFetch(it)
        }

        if (channel.isVod && cached == null) {
            val siblingUrl = db.findSiblingVodLogo(channel)
            if (!siblingUrl.isNullOrBlank() && siblingUrl != rawLogo) {
                rawLogo = siblingUrl
                cached = logoCache.getOrFetch(siblingUrl)
            }
        }

        if (channel.isVod && (cached == null || it.casatv.server.core.VodPosterResolver.isLandscapeStill(cached.bytes))) {
            val resolvedUrl = vodPosterResolver.resolvePosterUrl(db, channel)
            if (!resolvedUrl.isNullOrBlank() && resolvedUrl != rawLogo) {
                val upgraded = logoCache.getOrFetch(resolvedUrl)
                if (upgraded != null && (!it.casatv.server.core.VodPosterResolver.isLandscapeStill(upgraded.bytes) || cached == null)) {
                    rawLogo = resolvedUrl
                    cached = upgraded
                }
            }
        }

        if (cached == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Logo non disponibile").apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Cache-Control", "public, max-age=300")
            }
        }

        val isStill = channel.isVod && it.casatv.server.core.VodPosterResolver.isLandscapeStill(cached.bytes)
        return newFixedLengthResponse(
            Response.Status.OK,
            cached.mimeType,
            ByteArrayInputStream(cached.bytes),
            cached.bytes.size.toLong(),
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Cache-Control", if (isStill) "public, max-age=3600" else "public, max-age=2592000, immutable")
            addHeader("ETag", "\"${cached.etag}\"")
            addHeader("X-Casa-Artwork-Kind", if (isStill) "still" else "poster")
        }
    }

    private fun iptvChannelJson(
        channel: IptvChannel,
        number: Int,
        language: String,
        favorite: Boolean,
        baseUrl: String,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
    ): JsonObject {
        val strings = textOf(language)
        val categoryName = categoryName(channel.category, language)
        val languageCode = M3uParser.normalizeLanguage(channel.language)
        val encodedId = URLEncoder.encode(channel.id, "UTF-8").replace("+", "%20")
        val hasDirectLogo = !channel.logo.isNullOrBlank()
        val cachedLogoUrl = if (hasDirectLogo || (channel.isVod && !vodPosterResolver.isRecentlyMissed(db, channel))) {
            "$baseUrl/logo/$encodedId"
        } else {
            null
        }
        val vodStreamUrl = if (channel.isVod) "$baseUrl/api/vod/stream/$encodedId" else null
        val upNext = positionMs == 1L
        val effectivePosMs = if (upNext) 0L else positionMs
        val seriesInfo = if (channel.contentType == "series") M3uParser.parseSeriesEpisode(channel.name) else null
        val fallbackSeriesTitle = if (channel.contentType == "series") {
            seriesInfo?.showTitle?.takeIf { it.isNotBlank() }
                ?: M3uParser.extractSeriesBaseTitle(channel.name)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return buildJsonObject {
            put("id", channel.id)
            put("number", number)
            put("name", channel.name)
            put("contentType", channel.contentType)
            put("isVod", channel.isVod)
            putOrNull("vodStreamUrl", vodStreamUrl)
            put("positionMs", effectivePosMs)
            put("durationMs", durationMs)
            put("upNext", upNext)
            putOrNull("seriesTitle", fallbackSeriesTitle)
            seriesInfo?.let {
                put("seasonNumber", it.season)
                put("episodeNumber", it.episode)
                if (it.episodeTitle.isNotBlank()) put("episodeTitle", it.episodeTitle)
            }
            putOrNull("country", channel.country.takeIf { it.isNotBlank() })
            put("countryName", if (channel.country.isNotBlank()) countryName(channel.country, language) else "")
            put("flag", if (channel.country.isNotBlank()) countryFlag(channel.country) else "")
            putJsonArray("categories") { add(channel.category) }
            putJsonArray("categoryNames") { add(categoryName) }
            putJsonArray("languages") {
                if (languageCode.isNotBlank()) add(languageCode)
            }
            putJsonArray("languageNames") {
                if (languageCode.isNotBlank()) add(languageName(languageCode, language))
            }
            val vodLanguageFlags = if (channel.isVod) {
                (channel.availableLanguageFlags.ifEmpty { VodTaxonomy.detectVodFlagCodes(channel) })
                    .map { it.lowercase(Locale.ROOT) }
                    .filter { it.length == 2 }
                    .distinct()
                    .take(5)
            } else {
                emptyList()
            }
            putJsonArray("languageFlags") {
                vodLanguageFlags.forEach { add(it) }
            }
            put("quality", channel.quality.ifBlank { "SD" })
            putOrNull("logo", cachedLogoUrl)
            put("favorite", favorite)
            put("hidden", false)
            put("enabled", true)
            put("status", "online")
            put("streamsCount", channel.sourceCount)
            put("onlineStreams", channel.sourceCount)
            put("isNsfw", false)
            put("streamUrl", channel.url)
            putOrNull("epgId", channel.tvgId.takeIf { it.isNotBlank() })
            put("now", JsonNull)
            put("next", JsonNull)
        }
    }

    private fun iptvSourcesJson(channel: IptvChannel): JsonObject {
        val groupKey = db.canonicalGroupKeyForChannel(channel)
        val preferredUrl = db.getPreferredSource(groupKey)
        val alternatives = db.listAlternativeChannels(
            channel = channel,
            limit = 16,
            sortSelfByScore = true,
            dynamicScoreAdjustment = liveRelay::healthScoreAdjustment,
        )
        return buildJsonObject {
            put("channel", channel.name)
            putJsonArray("sources") {
                alternatives.forEachIndexed { idx, alt ->
                    val uri = runCatching { java.net.URI(alt.url.trim()) }.getOrNull()
                    val host = uri?.host.orEmpty()
                    val proto = uri?.scheme?.uppercase(Locale.ROOT).orEmpty()
                    val (healthStatus, latencyMs) = liveRelay.streamHealthInfo(alt.url)
                    val stalls = db.sourceStallCount(alt.url)
                    add(
                        buildJsonObject {
                            put("id", "src-${alt.id}")
                            put("channelId", alt.id)
                            put("name", alt.name)
                            put("quality", alt.quality.ifBlank { "HD" })
                            put("host", host)
                            put("protocol", proto)
                            if (host.isNotBlank()) put("feed", host) else put("feed", JsonNull)
                            put("status", healthStatus)
                            if (latencyMs != null) put("latencyMs", latencyMs) else put("latencyMs", JsonNull)
                            put("recommended", idx == 0)
                            put("preferred", alt.url.trim() == preferredUrl)
                            put("stalls", stalls)
                            put("url", alt.url)
                        },
                    )
                }
            }
        }
    }

    private fun emptyEpgJson(channelId: String): JsonObject = buildJsonObject {
        put("channelId", channelId)
        put("now", JsonNull)
        put("next", JsonNull)
        putJsonArray("upcoming") {}
    }

    private fun categoryName(category: String, language: String): String {
        if (category.equals(M3uParser.GENERAL_CATEGORY, ignoreCase = true) || category.isBlank()) {
            return textOf(language).t("Generali", "General", "General")
        }
        return category
    }

    private fun clientJson(client: ClientRow): JsonObject = buildJsonObject {
        put("id", client.id)
        put("name", client.name)
        put("platform", client.platform)
        put("appVersion", client.appVersion)
        put("profileId", client.profileId)
        put("profileName", db.getProfile(client.profileId)?.name.orEmpty())
        put("state", client.state)
        put("channel", client.channel)
        put("ip", client.ip)
        put("firstSeen", client.firstSeen)
        put("lastSeen", client.lastSeen)
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private fun activeProfile(session: IHTTPSession): ProfileRow {
        val requested = session.header("x-profile-id")
        return db.getProfile(requested) ?: db.defaultProfile(languageProvider())
    }

    private fun baseUrl(session: IHTTPSession): String {
        val host = session.header("host")
        return if (host.isNotBlank() && !host.startsWith("0.0.0.0")) {
            "http://$host"
        } else {
            NetworkUtils.baseUrl(NetworkUtils.primaryIp(), listeningPort)
        }
    }

    private fun memoryUsage(): Pair<Long, Double> {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            val total = info.totalMem
            val used = total - info.availMem
            val percent = if (total > 0) used.toDouble() / total * 100 else 0.0
            total to (Math.round(percent * 10) / 10.0)
        } catch (_: Exception) {
            0L to 0.0
        }
    }

    private fun diskUsage(): Pair<Long, Double> {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            val used = total - free
            val percent = if (total > 0) used.toDouble() / total * 100 else 0.0
            total to (Math.round(percent * 10) / 10.0)
        } catch (_: Exception) {
            0L to 0.0
        }
    }

    /**
     * Android denies apps access to /proc/loadavg, so CPU usage is sampled from
     * this process' own /proc/self/stat counters. It is less precise than a
     * system-wide figure but works without root on every supported version.
     */
    @Synchronized
    private fun cpuPercent(): Double {
        val ticks = processCpuTicks()
        val now = System.currentTimeMillis()
        if (ticks == null) {
            lastCpuTicks = -1
            return systemLoadPercent()
        }
        var percent = 0.0
        if (lastCpuTicks >= 0 && now > lastCpuAt) {
            val tickMs = 1000.0 / clockTicksPerSecond()
            val cpuMs = (ticks - lastCpuTicks) * tickMs
            val wallMs = (now - lastCpuAt).toDouble()
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            percent = (cpuMs / wallMs * 100.0 / cores).coerceIn(0.0, 100.0)
        }
        lastCpuTicks = ticks
        lastCpuAt = now
        return Math.round(percent * 10) / 10.0
    }

    private fun processCpuTicks(): Long? = try {
        val stat = File("/proc/self/stat").readText()
        val fields = stat.substringAfter(") ").trim().split(" ")
        // fields[0] = state, fields[11] = utime, fields[12] = stime
        fields[11].toLong() + fields[12].toLong()
    } catch (_: Exception) {
        null
    }

    private fun clockTicksPerSecond(): Double = try {
        android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK).toDouble()
    } catch (_: Throwable) {
        100.0
    }

    private fun systemLoadPercent(): Double {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val load = try {
            File("/proc/loadavg").readText().trim().split(Regex("\\s+")).firstOrNull()
                ?.toDoubleOrNull() ?: 0.0
        } catch (_: Exception) {
            0.0
        }
        return min(100.0, Math.round(load / cores * 1000) / 10.0)
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until max(left.size, right.size)) {
            val diff = (left.getOrNull(index) ?: 0) - (right.getOrNull(index) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    private fun decodeSegment(raw: String): String =
        runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)

    private fun escapeHtmlText(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun htmlPage(session: IHTTPSession): Response {
        val language = languageProvider()
        val strings = textOf(language)
        val safeName = escapeHtmlText(nameProvider())
        val safeBase = escapeHtmlText(baseUrl(session))
        val addresses = NetworkUtils.addresses()
            .joinToString("") { address ->
                "<li><code>http://${escapeHtmlText(address.address)}:$listeningPort</code> <span>${escapeHtmlText(address.label)}</span></li>"
            }
        val html = """
            <!doctype html>
            <html lang="${escapeHtmlText(language)}">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>$safeName · CasaTV Server</title>
              <style>
                :root { color-scheme: dark; }
                body { margin: 0; background: #060B14; color: #F8FAFC;
                       font-family: system-ui, -apple-system, sans-serif; }
                main { max-width: 720px; margin: 0 auto; padding: 48px 24px; }
                h1 { font-size: 34px; margin: 0 0 6px; }
                h1 span { color: #00D4FF; }
                p.lead { color: #CBD5E1; margin: 0 0 28px; }
                .card { background: #0A1322; border: 1px solid #1E3A5F;
                        border-radius: 14px; padding: 18px 20px; margin-bottom: 14px; }
                .card h2 { font-size: 12px; letter-spacing: 1.2px; color: #94A3B8;
                           text-transform: uppercase; margin: 0 0 10px; }
                ul { margin: 0; padding-left: 18px; line-height: 1.8; }
                code { color: #00D4FF; }
                .num { font-size: 26px; font-weight: 700; }
                .label { color: #CBD5E1; font-size: 12px; }
                a { color: #00D4FF; }
                footer { color: #94A3B8; font-size: 12px; margin-top: 26px; }
              </style>
            </head>
            <body>
              <main>
                <h1>$safeName <span>Server</span></h1>
                <p class="lead">${strings.t(
                    "Server attivo sul telefono. Apri CasaTV sulla TV e collegati a uno di questi indirizzi.",
                    "Server running on your phone. Open CasaTV on the TV and connect to one of these addresses.",
                    "Servidor activo en el teléfono. Abre CasaTV en la TV y conéctate a una de estas direcciones.",
                )}</p>
                <div class="card">
                  <h2>${strings.t("Indirizzi", "Addresses", "Direcciones")}</h2>
                  <ul>$addresses</ul>
                </div>
                <div class="card">
                  <h2>${strings.t("Canali IPTV", "IPTV Channels", "Canales IPTV")}</h2>
                  <div><div class="num">${db.fastChannelCount()}</div><div class="label">${strings.t("Canali", "Channels", "Canales")}</div></div>
                </div>
                <div class="card">
                  <h2>${strings.t("API", "API", "API")}</h2>
                  <ul>
                    <li><a href="/api/channels">/api/channels</a></li>
                    <li><a href="/api/version">/api/version</a></li>
                    <li><a href="/healthz">/healthz</a></li>
                  </ul>
                </div>
                <footer>$safeName · ${BuildConfig.VERSION_NAME} · $safeBase</footer>
              </main>
            </body>
            </html>
        """.trimIndent()
        return textResponse(html, "text/html; charset=utf-8")
    }

    companion object {
        /** TV app version this server is built for; shown by the update check. */
        const val TV_LATEST_VERSION = BuildConfig.VERSION_NAME
    }
}

private class BoundedAsyncRunner(
    coreThreads: Int = 16,
    maxThreads: Int = 192,
    keepAliveSeconds: Long = 20L,
) : NanoHTTPD.AsyncRunner {
    private val runningHandlers = java.util.Collections.synchronizedList(ArrayList<NanoHTTPD.ClientHandler>())
    private val threadCounter = java.util.concurrent.atomic.AtomicInteger(1)
    private val executor = java.util.concurrent.ThreadPoolExecutor(
        coreThreads,
        maxThreads,
        keepAliveSeconds,
        java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.SynchronousQueue(),
        java.util.concurrent.ThreadFactory { runnable ->
            Thread(runnable, "CasaHTTP-Worker-${threadCounter.getAndIncrement()}").apply { isDaemon = true }
        },
        java.util.concurrent.RejectedExecutionHandler { runnable, _ ->
            val handler = runnable as? NanoHTTPD.ClientHandler
            if (handler != null) {
                runningHandlers.remove(handler)
                runCatching { handler.close() }
            }
        },
    )

    override fun closeAll() {
        for (handler in ArrayList(runningHandlers)) {
            runCatching { handler.close() }
        }
        runningHandlers.clear()
        executor.shutdown()
    }

    override fun closed(clientHandler: NanoHTTPD.ClientHandler) {
        runningHandlers.remove(clientHandler)
    }

    override fun exec(code: NanoHTTPD.ClientHandler) {
        runningHandlers.add(code)
        executor.execute(code)
    }
}

private class ResilientVodInputStream(
    initialResp: okhttp3.Response,
    initialStream: InputStream,
    initialUrl: String,
    private val rangeStart: Long,
    private val expectedLength: Long,
    private val channel: it.casatv.server.core.IptvChannel,
    private val client: okhttp3.OkHttpClient,
    private val candidateSupplier: () -> List<String>,
) : InputStream() {

    private var currentResp: okhttp3.Response? = initialResp
    private var currentStream: InputStream? = initialStream
    private var currentUrl: String = initialUrl
    private var bytesDelivered: Long = 0L
    private var resumeAttempts: Int = 0
    @Volatile
    private var closed: Boolean = false

    private val endExclusive: Long = if (expectedLength > 0L) rangeStart + expectedLength else -1L

    override fun read(): Int {
        val b = ByteArray(1)
        val n = read(b, 0, 1)
        return if (n <= 0) -1 else (b[0].toInt() and 0xFF)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (closed) return -1
        if (len == 0) return 0
        while (!closed) {
            val stream = currentStream ?: return -1
            try {
                val n = stream.read(b, off, len)
                if (n > 0) {
                    bytesDelivered += n
                    return n
                }
                if (n < 0) {
                    if (endExclusive > 0L && bytesDelivered < expectedLength && tryResumeUpstream()) {
                        continue
                    }
                    return -1
                }
                return 0
            } catch (e: java.io.IOException) {
                if (!closed && endExclusive > 0L && bytesDelivered < expectedLength && tryResumeUpstream()) {
                    continue
                }
                throw e
            }
        }
        return -1
    }

    private fun tryResumeUpstream(): Boolean {
        if (resumeAttempts >= 4 || closed) return false
        resumeAttempts++
        closeActive()
        val backoffMs = if (resumeAttempts == 1) 250L else 600L
        runCatching { Thread.sleep(backoffMs) }
        if (closed) return false

        val nextOffset = rangeStart + bytesDelivered
        val rangeHdr = "bytes=$nextOffset-${endExclusive - 1}"
        val urls = candidateSupplier()
        val ordered = LinkedHashSet<String>(urls.size + 1).apply {
            add(currentUrl)
            addAll(urls)
        }
        for (url in ordered) {
            if (closed) return false
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                .header("Accept", "*/*")
                .header("Range", rangeHdr)
                .build()
            val resp = runCatching { client.newCall(req).execute() }.getOrNull() ?: continue
            if (resp.code == 206 || (resp.code in 200..299 && nextOffset == 0L)) {
                val body = resp.body?.byteStream()
                if (body != null) {
                    currentResp = resp
                    currentStream = body
                    currentUrl = url
                    android.util.Log.i("CasaVOD", "Resumed VOD proxy stream ${channel.name} at offset=$nextOffset (attempt=$resumeAttempts)")
                    return true
                }
            }
            runCatching { resp.close() }
        }
        return false
    }

    private fun closeActive() {
        runCatching { currentStream?.close() }
        runCatching { currentResp?.close() }
        currentStream = null
        currentResp = null
    }

    override fun close() {
        closed = true
        closeActive()
    }
}
