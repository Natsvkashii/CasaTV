package it.casatv.tv.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ApiException(
    val status: Int,
    message: String,
) : Exception(message)

class CasaApi {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .callTimeout(4000, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    @Volatile
    var baseUrl: String = ""
        private set

    @Volatile
    var profileId: String? = null
        private set

    @Volatile
    var deviceId: String? = null

    @Volatile
    var deviceName: String = "Android TV"

    @Volatile
    var devicePlatform: String = "Android TV"

    @Volatile
    var playbackState: String = "idle"

    @Volatile
    var playbackChannel: String = ""

    fun use(baseUrl: String) {
        this.baseUrl = normalizeBaseUrl(baseUrl)
    }

    fun useProfile(id: String?) {
        profileId = id?.takeIf { it.isNotBlank() }
    }

    fun setPlayback(state: String, channel: String = "") {
        playbackState = state
        playbackChannel = channel
    }

    suspend fun health(base: String = baseUrl): Health =
        getJson(base, "/healthz")

    suspend fun config(base: String = baseUrl): ServerConfig =
        getJson(base, "/api/config")

    suspend fun profiles(): ProfilesResponse =
        getJson(baseUrl, "/api/profiles")

    suspend fun createProfile(
        name: String,
        avatar: String,
        language: String,
        country: String,
    ): Profile {
        val body = buildJsonObject {
            put("name", name)
            put("avatar", avatar)
            put("language", language)
            put("country", country)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return sendJson(
            base = baseUrl,
            path = "/api/profiles",
            method = "POST",
            body = body,
        )
    }

    suspend fun updateProfile(
        id: String,
        name: String,
        avatar: String,
        language: String,
        country: String,
    ): Profile {
        val body = buildJsonObject {
            put("name", name)
            put("avatar", avatar)
            put("language", language)
            put("country", country)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return sendJson(
            base = baseUrl,
            path = "/api/profiles/${encodePath(id)}",
            method = "PATCH",
            body = body,
        )
    }

    suspend fun deleteProfile(id: String) {
        request(
            base = baseUrl,
            path = "/api/profiles/${encodePath(id)}",
            method = "DELETE",
            body = null,
            query = emptyMap(),
        )
    }

    suspend fun clearGuestProfile(base: String = baseUrl): Boolean =
        runCatching {
            request(
                base = base,
                path = "/api/profiles/guest/clear",
                method = "POST",
                body = "{}".toRequestBody(JSON_MEDIA_TYPE),
                query = emptyMap(),
            )
        }.isSuccess

    suspend fun channelSources(id: String): ChannelSources =
        getJson(baseUrl, "/api/channels/${encodePath(id)}/streams")

    private data class ProbeSample(
        val finalUrl: HttpUrl,
        val contentType: String,
        val bytes: ByteArray,
    )

    private fun fetchProbeSample(
        url: String,
        maxBytes: Int,
        job: kotlinx.coroutines.Job?,
    ): ProbeSample? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", EXO_USER_AGENT)
            .get()
            .build()
        val call = probeClient.newCall(req)
        val handle = job?.invokeOnCompletion { call.cancel() }
        return try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) return null
                val stream = resp.body?.byteStream() ?: return null
                val buffer = ByteArray(maxBytes)
                var total = 0
                while (total < maxBytes) {
                    val read = stream.read(buffer, total, maxBytes - total)
                    if (read <= 0) break
                    total += read
                }
                if (total <= 0) return null
                ProbeSample(
                    finalUrl = resp.request.url,
                    contentType = resp.header("Content-Type").orEmpty(),
                    bytes = buffer.copyOf(total),
                )
            }
        } finally {
            handle?.dispose()
        }
    }

    /**
     * Quickly verifies whether [streamUrl] responds with a playable HLS manifest
     * or non-HTML media stream. For HLS playlists, also probes the first child
     * variant/segment URL so dead manifests are filtered out.
     */
    suspend fun isStreamAvailable(streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        val clean = streamUrl.trim()
        val scheme = clean.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return@withContext false

        val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        try {
            val sample = fetchProbeSample(clean, 4096, job) ?: return@withContext false
            val text = sample.bytes.toString(Charsets.UTF_8)
            val isDash = clean.contains(".mpd", ignoreCase = true) ||
                sample.finalUrl.toString().contains(".mpd", ignoreCase = true) ||
                sample.contentType.contains("dash+xml", ignoreCase = true) ||
                text.contains("<MPD", ignoreCase = true)

            if (isDash) {
                return@withContext text.contains("<MPD", ignoreCase = true)
            }

            val isHls = clean.contains(".m3u8", ignoreCase = true) ||
                sample.finalUrl.toString().contains(".m3u8", ignoreCase = true) ||
                sample.contentType.contains("mpegurl", ignoreCase = true) ||
                text.trimStart().startsWith("#EXTM3U")

            if (!isHls) {
                return@withContext !sample.contentType.contains("text/html", ignoreCase = true) &&
                    sample.bytes.size >= 188
            }

            if (!text.contains("#EXTM3U")) return@withContext false
            val firstEntry = text.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: return@withContext true

            val childUrl = sample.finalUrl.resolve(firstEntry) ?: return@withContext false
            val childSample = fetchProbeSample(childUrl.toString(), 1024, job) ?: return@withContext false
            val childIsM3u8 = childUrl.toString().contains(".m3u8", ignoreCase = true) ||
                childSample.contentType.contains("mpegurl", ignoreCase = true)
            if (childIsM3u8) {
                childSample.bytes.toString(Charsets.UTF_8).contains("#EXTM3U")
            } else {
                !childSample.contentType.contains("text/html", ignoreCase = true)
            }
        } catch (err: kotlinx.coroutines.CancellationException) {
            throw err
        } catch (_: Exception) {
            false
        }
    }

    suspend fun home(limit: Int = 22): HomeResponse =
        getJson(baseUrl, "/api/home", mapOf("limit" to limit.toString()))

    suspend fun channels(
        country: String? = null,
        category: String? = null,
        language: String? = null,
        favorite: Boolean = false,
        query: String? = null,
        limit: Int = 100,
        offset: Int = 0,
        sort: String = "number",
    ): ChannelPage {
        val params = linkedMapOf(
            "limit" to limit.coerceIn(1, 5000).toString(),
            "offset" to offset.coerceAtLeast(0).toString(),
            "sort" to sort,
        )
        country?.takeIf { it.isNotBlank() }?.let { params["country"] = it }
        category?.takeIf { it.isNotBlank() }?.let { params["category"] = it }
        language?.takeIf { it.isNotBlank() }?.let { params["language"] = it }
        query?.takeIf { it.isNotBlank() }?.let { params["q"] = it }
        if (favorite) params["favorite"] = "1"
        return getJson(baseUrl, "/api/channels", params)
    }

    suspend fun channel(id: String): Channel =
        getJson(baseUrl, "/api/channels/${encodePath(id)}")

    suspend fun channelByNumber(number: Int): Channel =
        getJson(baseUrl, "/api/channels/by-number/$number")

    suspend fun epgGuide(country: String? = null, hours: Int = 6, limit: Int = 100): EpgGuideResponse {
        val params = linkedMapOf(
            "hours" to hours.toString(),
            "limit" to limit.toString(),
        )
        country?.takeIf { it.isNotBlank() }?.let { params["country"] = it }
        return getJson(baseUrl, "/api/epg/guide", params)
    }

    suspend fun setFavorite(id: String, favorite: Boolean): Channel {
        val body = """{"favorite":$favorite}""".toRequestBody(JSON_MEDIA_TYPE)
        return sendJson(
            base = baseUrl,
            path = "/api/channels/${encodePath(id)}",
            method = "PATCH",
            body = body,
        )
    }

    suspend fun markPlayed(id: String) {
        request(
            base = baseUrl,
            path = "/api/channels/${encodePath(id)}/played",
            method = "POST",
            body = EMPTY_BODY,
            query = emptyMap(),
        )
    }

    suspend fun refreshCatalog() {
        request(
            base = baseUrl,
            path = "/api/refresh",
            method = "POST",
            body = EMPTY_BODY,
            query = emptyMap(),
        )
    }

    suspend fun system(): SystemStatus =
        getJson(baseUrl, "/api/system")

    suspend fun versionInfo(): UpdateInfo =
        getJson(baseUrl, "/api/version")

    suspend fun downloadFile(
        url: String,
        destination: java.io.File,
        onProgress: ((Float) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        val targetUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            val base = baseUrl.trimEnd('/')
            val path = "/" + url.trimStart('/')
            "$base$path"
        }
        val request = Request.Builder().url(targetUrl).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IllegalStateException("Download fallito: HTTP ${response.code}")
        val body = response.body ?: throw IllegalStateException("Risposta vuota dal server")
        val contentLength = body.contentLength()
        destination.parentFile?.mkdirs()
        destination.outputStream().use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        onProgress?.invoke((bytesRead.toFloat() / contentLength).coerceIn(0f, 1f))
                    }
                }
                output.flush()
            }
        }
    }

    /** Registers this device and keeps it in the server's connected list. */
    suspend fun heartbeat() {
        val body = buildJsonObject {
            put("deviceId", deviceId.orEmpty())
            put("name", deviceName)
            put("platform", devicePlatform)
            put("appVersion", it.casatv.tv.BuildConfig.VERSION_NAME)
            put("profileId", profileId.orEmpty())
            put("state", playbackState)
            put("channel", playbackChannel)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        request(
            base = baseUrl,
            path = "/api/clients/heartbeat",
            method = "POST",
            body = body,
            query = emptyMap(),
        )
    }

    suspend fun syncPlayback(
        channelId: String,
        isLive: Boolean,
        positionMs: Long,
        liveOffsetMs: Long,
        segmentSeq: Long = 0L,
        segmentOffsetMs: Long = 0L,
        segmentDurationMs: Long = 0L,
    ): SyncStateResponse {
        val body = buildJsonObject {
            put("deviceId", deviceId.orEmpty())
            put("deviceName", deviceName)
            put("channelId", channelId)
            put("isLive", isLive)
            put("positionMs", positionMs)
            put("liveOffsetMs", liveOffsetMs)
            put("segmentSeq", segmentSeq)
            put("segmentOffsetMs", segmentOffsetMs)
            put("segmentDurationMs", segmentDurationMs)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return sendJson(
            base = baseUrl,
            path = "/api/sync",
            method = "POST",
            body = body,
        )
    }

    suspend fun refreshEpg() {
        request(
            base = baseUrl,
            path = "/api/epg/refresh",
            method = "POST",
            body = EMPTY_BODY,
            query = emptyMap(),
        )
    }

    private suspend inline fun <reified T> getJson(
        base: String,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): T {
        val body = request(base, path, "GET", null, query)
        // Decoding a large 500-channel page is CPU heavy: keep it off Main so
        // the profile gate and Home stay responsive after profile selection.
        return withContext(Dispatchers.Default) { json.decodeFromString(body) }
    }

    private suspend inline fun <reified T> sendJson(
        base: String,
        path: String,
        method: String,
        body: RequestBody?,
        query: Map<String, String> = emptyMap(),
    ): T {
        val response = request(base, path, method, body, query)
        return withContext(Dispatchers.Default) { json.decodeFromString(response) }
    }

    private suspend fun request(
        base: String,
        path: String,
        method: String,
        body: RequestBody?,
        query: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val root = base.toHttpUrlOrNull()
            ?: throw ApiException(0, "Indirizzo del server non valido")
        val builder = root.newBuilder()
        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { part ->
            builder.addPathSegment(part)
        }
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val url = builder.build()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "CasaTV-Android/${it.casatv.tv.BuildConfig.VERSION_NAME}")
            .header("X-App-Version", it.casatv.tv.BuildConfig.VERSION_NAME)
            .method(method, body)
        profileId?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("X-Profile-Id", it)
        }
        deviceId?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("X-Device-Id", it)
            requestBuilder.header("X-Device-Name", deviceName)
            requestBuilder.header("X-Device-Platform", devicePlatform)
            requestBuilder.header("X-Playback-State", playbackState)
            if (playbackChannel.isNotBlank()) {
                requestBuilder.header("X-Playback-Channel", playbackChannel)
            }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    json.decodeFromString<ApiError>(raw).error
                }.getOrNull().orEmpty().ifBlank {
                    when (response.code) {
                        404 -> "Risorsa non trovata"
                        502 -> "Il canale non è raggiungibile in questo momento"
                        else -> "Errore del server (${response.code})"
                    }
                }
                throw ApiException(response.code, message)
            }
            raw
        }
    }

    private fun encodePath(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    suspend fun getPlaylists(): List<PlaylistDto> = runCatching {
        getJson<PlaylistsResponse>(baseUrl, "/api/playlists").playlists
    }.getOrDefault(emptyList())

    suspend fun addPlaylist(name: String, url: String): PlaylistDto? = runCatching {
        val body = buildJsonObject {
            put("name", name)
            put("url", url)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val raw = request(baseUrl, "/api/playlists", "POST", body)
        json.decodeFromString<PlaylistActionResponse>(raw).playlist
    }.getOrNull()

    suspend fun uploadPlaylistFile(
        name: String,
        fileName: String,
        bytes: ByteArray,
    ): PlaylistDto? = runCatching {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", name)
            .addFormDataPart(
                "file",
                fileName.ifBlank { "playlist.m3u" },
                bytes.toRequestBody("application/octet-stream".toMediaType()),
            )
            .build()
        val raw = request(
            baseUrl,
            "/api/playlists/upload?name=${encodePath(name.ifBlank { fileName })}",
            "POST",
            multipart,
        )
        json.decodeFromString<PlaylistActionResponse>(raw).playlist
    }.getOrNull()

    suspend fun checkPlaylist(id: String): PlaylistDto? = runCatching {
        val raw = request(baseUrl, "/api/playlists/${encodePath(id)}/check", "POST", EMPTY_BODY)
        json.decodeFromString<PlaylistActionResponse>(raw).playlist
    }.getOrNull()

    suspend fun deletePlaylist(id: String): Boolean = runCatching {
        request(baseUrl, "/api/playlists/${encodePath(id)}", "DELETE", EMPTY_BODY)
        true
    }.getOrDefault(false)

    suspend fun refreshPlaylist(id: String): PlaylistDto? = runCatching {
        val raw = request(baseUrl, "/api/playlists/${encodePath(id)}/refresh", "POST", EMPTY_BODY)
        json.decodeFromString<PlaylistActionResponse>(raw).playlist
    }.getOrNull()

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        private const val EXO_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun normalizeBaseUrl(input: String): String {
            var raw = input.trim()
            require(raw.isNotBlank()) { "Inserisci l’indirizzo del server" }
            if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
                raw = "http://$raw"
            }
            val parsed = raw.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Indirizzo del server non valido")
            val authority = raw.substringAfter("://").substringBefore('/')
            val hasPort = Regex(":\\d+$").containsMatchIn(authority)
            val builder = parsed.newBuilder().encodedPath("/")
            if (!hasPort) builder.port(8787)
            return builder.build().toString().trimEnd('/')
        }

        fun apiRoot(hint: String): HttpUrl =
            normalizeBaseUrl(hint).toHttpUrl()
    }
}
