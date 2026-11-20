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
import it.casatv.server.core.IptvChannel
import it.casatv.server.core.M3uParser
import it.casatv.server.core.NetworkUtils
import it.casatv.server.core.PlaylistError
import it.casatv.server.core.PlaylistException
import it.casatv.server.core.PlaylistManager
import it.casatv.server.core.Prefs
import it.casatv.server.core.ProfileRow
import it.casatv.server.core.PROFILE_AVATARS
import it.casatv.server.core.PROFILE_LANGUAGES
import it.casatv.server.core.ServerDatabase
import it.casatv.server.core.countryFlag
import it.casatv.server.core.countryName
import it.casatv.server.core.languageLabel
import it.casatv.server.core.languageName
import it.casatv.server.core.textOf
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clientTouchCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val startedAtSeconds = System.currentTimeMillis() / 1000
    private var lastCpuTicks = -1L
    private var lastCpuAt = 0L

    init {
        runCatching { setAsyncRunner(BoundedAsyncRunner()) }
        runCatching {
            db.ensureGuestProfile()
            db.clearGuestProfile()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) return noContent()
        val path = session.uri.substringBefore('?').ifBlank { "/" }
        return try {
            when {
                path == "/healthz" -> health()
                path == "/favicon.ico" -> serveStaticOrHtml(session, "favicon.svg")
                path.startsWith("/api/") -> api(session, path.removePrefix("/api").trim('/'))
                else -> serveStaticOrHtml(session, path)
            }
        } catch (error: Exception) {
            errorResponse(Response.Status.INTERNAL_ERROR, error.message ?: "Errore interno")
        }
    }

    private fun serveStaticOrHtml(session: IHTTPSession, rawPath: String): Response {
        val clean = rawPath.removePrefix("/").ifBlank { "index.html" }
        val candidate = if (clean.contains('.')) clean else "index.html"
        return try {
            val assetPath = "web/$candidate"
            val input = context.assets.open(assetPath)
            val mime = when {
                candidate.endsWith(".html") -> "text/html; charset=utf-8"
                candidate.endsWith(".css") -> "text/css; charset=utf-8"
                candidate.endsWith(".js") -> "application/javascript; charset=utf-8"
                candidate.endsWith(".svg") -> "image/svg+xml"
                candidate.endsWith(".png") -> "image/png"
                candidate.endsWith(".json") -> "application/json; charset=utf-8"
                else -> "application/octet-stream"
            }
            newChunkedResponse(Response.Status.OK, mime, input).apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Cache-Control", "no-cache")
            }
        } catch (_: Exception) {
            htmlPage(session)
        }
    }

    /* ------------------------------------------------------------------ */
    /* Routing                                                             */
    /* ------------------------------------------------------------------ */

    private fun api(session: IHTTPSession, path: String): Response {
        touchClientFromHeaders(session)
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
        runCatching {
            val resolvedProfileId = profileId.ifBlank { activeProfile(session).id }
            db.touchClient(
                id = deviceId,
                name = session.header("x-device-name").ifBlank { "Android TV" },
                platform = session.header("x-device-platform").ifBlank { "Android TV" },
                appVersion = session.header("x-app-version"),
                profileId = resolvedProfileId,
                state = state,
                channel = channel,
                ip = session.remoteIpAddress ?: "",
            )
        }
    }

    /* ------------------------------------------------------------------ */
    /* Core CasaTV API                                                     */
    /* ------------------------------------------------------------------ */

    private fun health(): Response {
        val total = db.channelCount()
        return jsonResponse(
            buildJsonObject {
                put("ok", true)
                put("name", nameProvider())
                put("version", BuildConfig.VERSION_NAME)
                put("channels", total)
                put("streams", total)
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
            db.channelCountsByLanguage().forEach { (rawCode, count) ->
                val code = M3uParser.normalizeLanguage(rawCode)
                if (code.isNotBlank()) {
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
    }

    private fun facets(session: IHTTPSession): Response {
        val lang = activeProfile(session).language
        return jsonResponse(facetsJson(lang))
    }

    private fun config(session: IHTTPSession): Response {
        val profile = activeProfile(session)
        val lang = profile.language
        val total = db.channelCount()
        val favorites = db.favoriteIds(profile.id).size
        val base = baseUrl(session)
        return jsonResponse(
            buildJsonObject {
                put("name", nameProvider())
                put("version", BuildConfig.VERSION_NAME)
                put("serverTime", System.currentTimeMillis() / 1000)
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
                    put("channels", total)
                    put("allChannels", total)
                    put("streams", total)
                    put("onlineStreams", total)
                    put("favorites", favorites)
                    put("hidden", 0)
                    put("epgPrograms", 0)
                    put("countries", db.channelCountryCount())
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
                putJsonObject("catalog") {
                    put("source", Prefs.playlistSource(context).ifBlank { "playlist" })
                    put("defaultSource", Prefs.playlistUrl(context))
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
        val favorites = db.favoriteIds(profile.id)
        val recentIds = db.recentIds(profile.id, 20)
        val iptv = db.listChannels()
        val iptvById = iptv.associateBy { it.id }

        val rows = buildJsonArray {
            if (favorites.isNotEmpty()) {
                val favJson = buildJsonArray {
                    iptv.filter { it.id in favorites }.take(20).forEach { ch ->
                        add(iptvChannelJson(ch, ch.number, lang, true))
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

            val mostWatchedIds = db.mostWatchedIds(profile.id, 16)
            if (mostWatchedIds.isNotEmpty()) {
                val mostWatchedJson = buildJsonArray {
                    mostWatchedIds.forEach { id ->
                        val ch = iptvById[id]
                        if (ch != null) {
                            add(iptvChannelJson(ch, ch.number, lang, ch.id in favorites))
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
                        val ch = iptvById[id]
                        if (ch != null) {
                            add(iptvChannelJson(ch, ch.number, lang, ch.id in favorites))
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

            val countryChannels = iptv.filter { it.country.equals(profile.country, ignoreCase = true) }
            if (countryChannels.isNotEmpty()) {
                val cName = countryName(profile.country, lang)
                add(
                    buildJsonObject {
                        put("id", "recommended")
                        put("title", strings.t("Consigliati per te", "Recommended for you", "Recomendado para ti"))
                        put("subtitle", strings.t("Scelti per $cName", "Picked for $cName", "Elegido para $cName"))
                        put("type", "channels")
                        put("total", countryChannels.size)
                        putJsonArray("channels") {
                            countryChannels.take(20).forEach { channel ->
                                add(
                                    iptvChannelJson(
                                        channel = channel,
                                        number = channel.number,
                                        language = lang,
                                        favorite = channel.id in favorites,
                                    ),
                                )
                            }
                        }
                    },
                )
            }

            iptv.groupBy { it.category }
                .entries
                .sortedByDescending { it.value.size }
                .take(6)
                .forEach { (category, channels) ->
                    add(
                        buildJsonObject {
                            put("id", "category:$category")
                            put("title", categoryName(category, lang))
                            put(
                                "subtitle",
                                strings.t(
                                    "${channels.size} canali dalla playlist",
                                    "${channels.size} channels from your playlist",
                                    "${channels.size} canales de tu lista",
                                ),
                            )
                            put("type", "channels")
                            put("total", channels.size)
                            putJsonArray("channels") {
                                channels.take(20).forEach { channel ->
                                    add(
                                        iptvChannelJson(
                                            channel = channel,
                                            number = channel.number,
                                            language = lang,
                                            favorite = channel.id in favorites,
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
        }
        return jsonResponse(buildJsonObject { put("rows", rows) })
    }

    private fun channels(session: IHTTPSession, parts: List<String>): Response {
        val profile = activeProfile(session)
        val lang = profile.language
        val favorites = db.favoriteIds(profile.id)

        if (parts.size >= 2 && parts[1] == "by-number") {
            val number = parts.getOrNull(2)?.toIntOrNull()
                ?: return errorResponse(Response.Status.BAD_REQUEST, "Numero non valido")
            val channel = db.getChannelByNumber(number)
                ?: return errorResponse(Response.Status.NOT_FOUND, "Canale non trovato")
            return jsonResponse(
                iptvChannelJson(channel, channel.number, lang, channel.id in favorites),
            )
        }

        if (parts.size >= 2) {
            val id = parts[1]
            val iptvChannel = db.getChannel(id)
                ?: return errorResponse(Response.Status.NOT_FOUND, "Canale non trovato")
            return when {
                parts.getOrNull(2) == "streams" ->
                    jsonResponse(iptvSourcesJson(iptvChannel))
                parts.getOrNull(2) == "epg" -> jsonResponse(emptyEpgJson(iptvChannel.id))
                parts.getOrNull(2) == "played" && session.method == Method.POST -> {
                    db.markPlayed(profile.id, iptvChannel.id)
                    jsonResponse(buildJsonObject { put("ok", true) })
                }
                parts.size == 2 && session.method == Method.PATCH -> {
                    val favorite = session.jsonBody().bool("favorite") ?: false
                    db.setFavorite(profile.id, iptvChannel.id, favorite)
                    jsonResponse(
                        iptvChannelJson(iptvChannel, iptvChannel.number, lang, favorite),
                    )
                }
                parts.size == 2 -> jsonResponse(
                    iptvChannelJson(iptvChannel, iptvChannel.number, lang, iptvChannel.id in favorites),
                )
                else -> errorResponse(Response.Status.NOT_FOUND, "Endpoint non trovato")
            }
        }

        val iptv = db.listChannels()
        var filtered = iptv
        if (session.parm("favorite") == "1") filtered = filtered.filter { it.id in favorites }
        val category = session.parm("category")
        if (!category.isNullOrBlank()) {
            filtered = filtered.filter { it.category.equals(category, ignoreCase = true) }
        }
        val country = session.parm("country")
        if (!country.isNullOrBlank()) {
            filtered = filtered.filter { it.country.equals(country, ignoreCase = true) }
        }
        val language = session.parm("language")
        if (!language.isNullOrBlank()) {
            val requested = language
                .split(',')
                .map { M3uParser.normalizeLanguage(it) }
                .filter { it.isNotBlank() }
                .toSet()
            if (requested.isNotEmpty()) {
                filtered = filtered.filter { ch ->
                    M3uParser.normalizeLanguage(ch.language) in requested
                }
            }
        }
        val query = session.parm("q")
        if (!query.isNullOrBlank()) {
            filtered = filtered.filter { it.name.contains(query, ignoreCase = true) }
        }
        filtered = when (session.parm("sort")) {
            "name" -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered.sortedBy { it.number }
        }
        val limit = session.parm("limit").toIntOrNull()?.coerceIn(1, 15000) ?: 100
        val offset = session.parm("offset").toIntOrNull()?.coerceAtLeast(0) ?: 0
        val total = filtered.size
        val slice = filtered.drop(offset).take(limit)
        return jsonResponse(
            buildJsonObject {
                putJsonArray("items") {
                    slice.forEach { channel ->
                        add(
                            iptvChannelJson(
                                channel = channel,
                                number = channel.number,
                                language = lang,
                                favorite = channel.id in favorites,
                            ),
                        )
                    }
                }
                put("total", total)
                put("limit", limit)
                put("offset", offset)
                put("hasMore", offset + slice.size < total)
            },
        )
    }

    /* ------------------------------------------------------------------ */
    /* Profiles                                                            */
    /* ------------------------------------------------------------------ */

    private fun profiles(session: IHTTPSession, parts: List<String>): Response {
        val lang = activeProfile(session).language
        if (parts.size >= 3 && parts[1] == "guest" && parts[2] == "clear") {
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

        val id = parts[1]
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

    private fun playlistRoutes(session: IHTTPSession, parts: List<String>): Response {
        val lang = activeProfile(session).language
        val strings = textOf(lang)

        if (parts.size >= 2 && (parts[1] == "export.m3u" || parts[1] == "m3u") && session.method == Method.GET) {
            val channels = db.listChannels().sortedBy { it.number }
            val sb = StringBuilder("#EXTM3U\n")
            for (ch in channels) {
                val attrs = buildString {
                    append(""" tvg-name="${ch.name.replace("\"", "'")}"""")
                    if (!ch.logo.isNullOrBlank()) append(""" tvg-logo="${ch.logo}"""")
                    if (ch.country.isNotBlank()) append(""" tvg-country="${ch.country}"""")
                    if (ch.language.isNotBlank()) append(""" tvg-language="${ch.language}"""")
                    if (ch.number > 0) append(""" tvg-chno="${ch.number}"""")
                    if (ch.category.isNotBlank()) append(""" group-title="${ch.category.replace("\"", "'")}"""")
                }
                sb.append("#EXTINF:-1$attrs,${ch.name}\n")
                sb.append("${ch.url}\n")
            }
            return newFixedLengthResponse(Response.Status.OK, "audio/x-mpegurl; charset=utf-8", sb.toString()).apply {
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
                jsonResponse(playlistJson(result, lang), Response.Status.CREATED)
            } catch (error: PlaylistException) {
                errorResponse(Response.Status.BAD_REQUEST, playlistErrorText(error.reason, strings))
            } catch (error: Exception) {
                errorResponse(Response.Status.BAD_REQUEST, error.message ?: "Import non riuscito")
            } finally {
                temp.delete()
            }
        }

        if (parts.size >= 2 && parts[1] == "refresh" && session.method == Method.POST) {
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
                jsonResponse(playlistJson(result, lang))
            } catch (error: PlaylistException) {
                errorResponse(Response.Status.BAD_REQUEST, playlistErrorText(error.reason, strings))
            }
        }

        if (parts.size == 1) {
            return when (session.method) {
                Method.GET -> jsonResponse(playlistJson(null, lang))
                Method.DELETE -> {
                    playlist.clear()
                    jsonResponse(playlistJson(null, lang))
                }
                Method.POST -> {
                    val url = session.jsonBody().string("url").trim()
                    if (url.isBlank()) {
                        return errorResponse(Response.Status.BAD_REQUEST, "URL mancante")
                    }
                    try {
                        val result = runBlocking { playlist.importUrl(url) }
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

    private fun playlistsRoutes(session: IHTTPSession, parts: List<String>): Response {
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
                    if (url.isBlank() && content.isBlank()) {
                        return errorResponse(Response.Status.BAD_REQUEST, "URL o contenuto file mancante")
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
            return try {
                val entry = runBlocking { playlist.addPlaylistFromFile(temp, customName) }
                jsonResponse(
                    buildJsonObject {
                        put("ok", true)
                        put("playlist", playlistEntryJson(entry))
                    },
                    Response.Status.CREATED,
                )
            } catch (e: Exception) {
                errorResponse(Response.Status.BAD_REQUEST, e.message ?: "Errore caricamento file M3U")
            } finally {
                temp.delete()
            }
        }

        if (parts.size >= 2) {
            val id = parts[1]
            if (parts.size == 2 && session.method == Method.DELETE) {
                playlist.deletePlaylist(id)
                return jsonResponse(buildJsonObject { put("ok", true) })
            }
            if (parts.size >= 3 && parts[2] == "check" && session.method == Method.POST) {
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
                return try {
                    val updated = runBlocking { playlist.refreshPlaylist(id) }
                        ?: return errorResponse(Response.Status.NOT_FOUND, "Playlist non trovata")
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
        val profile = activeProfile(session)
        val totalChannels = db.channelCount()
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
                        put("rss", (memory.first * memory.second / 100).toLong())
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
                    put("channels", totalChannels)
                    put("allChannels", totalChannels)
                    put("streams", totalChannels)
                    put("onlineStreams", totalChannels)
                    put("favorites", favorites)
                    put("hidden", 0)
                    put("epgPrograms", 0)
                    put("countries", db.channelCountryCount())
                }
                putJsonArray("devices") {
                    activeClients.forEach { add(clientJson(it)) }
                }
                put("deviceCount", activeClients.size)
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

    private fun updateJson(current: String, session: IHTTPSession? = null): JsonObject {
        val dynamic = validDynamicTvApk()
        val latest = dynamic?.second ?: TV_LATEST_VERSION
        val hasApk = dynamic != null || runCatching {
            context.assets.open("updates/casatv-tv.apk").close()
            true
        }.getOrDefault(false)
        val base = session?.let { baseUrl(it) } ?: ""
        val isUpdateAvailable = current.isNotBlank() && compareVersions(latest, current) > 0
        return buildJsonObject {
            put("latest", latest)
            putOrNull("current", current.takeIf { it.isNotBlank() })
            put("updateAvailable", isUpdateAvailable)
            put("downloadUrl", "$base/api/update/casatv-tv.apk")
            put("hasApk", hasApk)
            put("changelog", "Aggiornamento CasaTV per Android TV v$latest")
            put("minVersion", "1.0.0")
        }
    }

    private fun updateRoutes(session: IHTTPSession, parts: List<String>): Response {
        if (parts.size >= 2 && (parts[1] == "casatv-tv.apk" || parts[1] == "tv.apk" || parts[1] == "upload") && (session.method == Method.POST || session.method == Method.PUT)) {
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
                temp.delete()
                return errorResponse(Response.Status.BAD_REQUEST, "APK non valido per Android TV (${pkgInfo?.packageName ?: "sconosciuto"})")
            }
            val updateDir = File(context.filesDir, "updates")
            updateDir.mkdirs()
            val target = File(updateDir, "casatv-tv.apk")
            temp.copyTo(target, overwrite = true)
            temp.delete()
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
        return try {
            val assetInput = context.assets.open("updates/casatv-tv.apk")
            val size = assetInput.available().toLong()
            val resp = if (size > 0) {
                newFixedLengthResponse(Response.Status.OK, "application/vnd.android.package-archive", assetInput, size)
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
            db.touchClient(
                id = deviceId,
                name = body.string("name").ifBlank { session.header("x-device-name") }.ifBlank { "Dispositivo" },
                platform = body.string("platform").ifBlank { session.header("x-device-platform") }.ifBlank { "Android TV" },
                appVersion = body.string("appVersion").ifBlank { session.header("x-app-version") },
                profileId = body.string("profileId").ifBlank { profile.id },
                state = body.string("state").ifBlank { session.header("x-playback-state") }.ifBlank { "idle" },
                channel = body.string("channel").ifBlank { session.header("x-playback-channel") },
                ip = session.remoteIpAddress ?: "",
            )
            val client = db.listClients().firstOrNull { it.id == deviceId }
            return jsonResponse(
                buildJsonObject {
                    put("ok", true)
                    put("client", client?.let { clientJson(it) } ?: JsonNull)
                    put("serverTime", System.currentTimeMillis() / 1000)
                    put("version", BuildConfig.VERSION_NAME)
                    put("lastIngest", Prefs.playlistLastImport(context).takeIf { it > 0 }?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("update", updateJson(body.string("appVersion").ifBlank { session.header("x-app-version") }))
                },
            )
        }
        if (parts.size >= 2 && session.method == Method.DELETE) {
            db.removeClient(parts[1])
            clientTouchCache.remove(parts[1])
            return jsonResponse(buildJsonObject { put("removed", true) })
        }
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
            val liveOffsetMs = body.long("liveOffsetMs") ?: 0L
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

            channelSyncHub.forEach { (chId, devices) ->
                if (chId != channelId) devices.remove(deviceId)
            }

            val channelDevices = channelSyncHub.computeIfAbsent(channelId) { ConcurrentHashMap() }
            channelDevices.entries.removeIf { (_, dev) -> now - dev.lastReportAt > 4000L }

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

            val peers = channelDevices.values.sortedBy { it.firstSeenAt }
            val leader = peers.firstOrNull() ?: entry
            val isLeader = (leader.deviceId == deviceId)
            val syncActive = peers.size >= 2

            val elapsedSinceLeaderReport = (now - leader.lastReportAt).coerceAtLeast(0L)
            val projectedPositionMs = leader.positionMs + elapsedSinceLeaderReport
            val dur = leader.segmentDurationMs.coerceAtLeast(1000L)
            val rawProjectedOffset = leader.segmentOffsetMs + elapsedSinceLeaderReport
            val projectedSeq = leader.segmentSeq
            val projectedOffset = rawProjectedOffset

            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("serverTimeMs", now)
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
            val now = System.currentTimeMillis()
            val channelDevices = channelSyncHub[channelId] ?: ConcurrentHashMap()
            channelDevices.entries.removeIf { (_, dev) -> now - dev.lastReportAt > 4000L }
            val peers = channelDevices.values.sortedBy { it.firstSeenAt }
            val leader = peers.firstOrNull()
            return jsonResponse(buildJsonObject {
                put("ok", true)
                put("channelId", channelId)
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
            val url = Prefs.playlistUrl(context)
            if (url.isNotBlank()) {
                bgScope.launch {
                    runCatching { playlist.refresh() }
                }
                return jsonResponse(
                    buildJsonObject {
                        put("ok", true)
                        put(
                            "message",
                            strings.t(
                                "Aggiornamento della playlist M3U avviato in background",
                                "M3U playlist refresh started in the background",
                                "Actualización de la lista M3U iniciada en segundo plano",
                            ),
                        )
                    },
                )
            }
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
            "verify-pin" -> {
                if (session.method != Method.POST) return errorResponse(Response.Status.METHOD_NOT_ALLOWED, "Metodo non consentito")
                val body = session.jsonBody()
                val pin = body.string("pin").trim()
                val currentPin = Prefs.adminPin(context)
                if (pin == currentPin) {
                    jsonResponse(buildJsonObject { put("ok", true); put("message", "PIN corretto") })
                } else {
                    jsonResponse(buildJsonObject { put("ok", false); put("error", "PIN non valido") }, Response.Status.UNAUTHORIZED)
                }
            }
            "change-pin" -> {
                if (session.method != Method.POST) return errorResponse(Response.Status.METHOD_NOT_ALLOWED, "Metodo non consentito")
                val body = session.jsonBody()
                val oldPin = body.string("oldPin").trim()
                val newPin = body.string("newPin").trim()
                val currentPin = Prefs.adminPin(context)
                if (oldPin != currentPin) {
                    return jsonResponse(buildJsonObject { put("ok", false); put("error", "Il PIN attuale non è corretto") }, Response.Status.UNAUTHORIZED)
                }
                if (!newPin.matches(Regex("^\\d{4}$"))) {
                    return jsonResponse(buildJsonObject { put("ok", false); put("error", "Il nuovo PIN deve contenere esattamente 4 cifre numeriche") }, Response.Status.BAD_REQUEST)
                }
                Prefs.setAdminPin(context, newPin)
                jsonResponse(buildJsonObject { put("ok", true); put("message", "PIN aggiornato con successo") })
            }
            else -> {
                if (session.method == Method.PATCH) {
                    val body = session.jsonBody()
                    val newName = body.string("server_name").trim()
                    if (newName.isNotBlank()) Prefs.setServerName(context, newName)
                    val newPin = body.string("admin_pin").trim()
                    if (newPin.matches(Regex("^\\d{4}$"))) Prefs.setAdminPin(context, newPin)
                    jsonResponse(buildJsonObject {
                        put("ok", true)
                        putJsonObject("settings") {
                            put("server_name", Prefs.serverName(context))
                        }
                    })
                } else {
                    jsonResponse(buildJsonObject {
                        put("ok", true)
                        putJsonObject("settings") {
                            put("server_name", Prefs.serverName(context))
                            put("port", Prefs.port(context))
                            put("auto_start", Prefs.autoStart(context))
                        }
                    })
                }
            }
        }
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

    private fun iptvChannelJson(
        channel: IptvChannel,
        number: Int,
        language: String,
        favorite: Boolean,
    ): JsonObject {
        val strings = textOf(language)
        val categoryName = categoryName(channel.category, language)
        val languageCode = M3uParser.normalizeLanguage(channel.language)
        return buildJsonObject {
            put("id", channel.id)
            put("number", number)
            put("name", channel.name)
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
            put("quality", channel.quality.ifBlank { "SD" })
            putOrNull("logo", channel.logo)
            put("favorite", favorite)
            put("hidden", false)
            put("enabled", true)
            put("status", "online")
            put("streamsCount", 1)
            put("onlineStreams", 1)
            put("isNsfw", false)
            put("streamUrl", channel.url)
            putOrNull("epgId", channel.tvgId.takeIf { it.isNotBlank() })
            put("now", JsonNull)
            put("next", JsonNull)
        }
    }

    private fun iptvSourcesJson(channel: IptvChannel): JsonObject {
        val alternatives = db.listAlternativeChannels(channel)
        return buildJsonObject {
            put("channel", channel.name)
            putJsonArray("sources") {
                alternatives.forEach { alt ->
                    add(
                        buildJsonObject {
                            put("id", "src-${alt.id}")
                            put("quality", alt.quality.ifBlank { "HD" })
                            put("feed", JsonNull)
                            put("status", "online")
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

    private fun htmlPage(session: IHTTPSession): Response {
        val language = languageProvider()
        val strings = textOf(language)
        val base = baseUrl(session)
        val devices = db.listClients()
        val addresses = NetworkUtils.addresses()
            .joinToString("") { address ->
                "<li><code>http://${address.address}:$listeningPort</code> <span>${address.label}</span></li>"
            }
        val html = """
            <!doctype html>
            <html lang="$language">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>${nameProvider()} · CasaTV Server</title>
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
                .row { display: flex; gap: 26px; flex-wrap: wrap; }
                .row div { min-width: 110px; }
                .num { font-size: 26px; font-weight: 700; }
                .label { color: #CBD5E1; font-size: 12px; }
                a { color: #00D4FF; }
                footer { color: #94A3B8; font-size: 12px; margin-top: 26px; }
              </style>
            </head>
            <body>
              <main>
                <h1>${nameProvider()} <span>Server</span></h1>
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
                  <div class="row">
                    <div><div class="num">${db.channelCount()}</div><div class="label">${strings.t("Canali", "Channels", "Canales")}</div></div>
                    <div><div class="num">${devices.size}</div><div class="label">${strings.t("TV collegate", "Connected TVs", "TV conectadas")}</div></div>
                  </div>
                </div>
                <div class="card">
                  <h2>${strings.t("API", "API", "API")}</h2>
                  <ul>
                    <li><a href="/api/channels">/api/channels</a></li>
                    <li><a href="/api/system">/api/system</a></li>
                    <li><a href="/api/version">/api/version</a></li>
                    <li><a href="/healthz">/healthz</a></li>
                  </ul>
                </div>
                <footer>${nameProvider()} · ${BuildConfig.VERSION_NAME} · $base</footer>
              </main>
            </body>
            </html>
        """.trimIndent()
        return textResponse(html, "text/html; charset=utf-8")
    }

    companion object {
        /** TV app version this server is built for; shown by the update check. */
        const val TV_LATEST_VERSION = "26.11.016"
    }
}

private class BoundedAsyncRunner(
    coreThreads: Int = 4,
    maxThreads: Int = 24,
    keepAliveSeconds: Long = 30L,
) : NanoHTTPD.AsyncRunner {
    private val executor = java.util.concurrent.ThreadPoolExecutor(
        coreThreads,
        maxThreads,
        keepAliveSeconds,
        java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.SynchronousQueue(),
        java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy(),
    )
    private val runningHandlers = java.util.Collections.synchronizedList(ArrayList<NanoHTTPD.ClientHandler>())

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
