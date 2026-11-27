package it.casatv.tv.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
        .connectTimeout(1800, TimeUnit.MILLISECONDS)
        .readTimeout(1800, TimeUnit.MILLISECONDS)
        .callTimeout(2800, TimeUnit.MILLISECONDS)
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

    @Volatile
    var adminPin: String? = null

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
            path = "/api/profiles/$id",
            method = "PATCH",
            body = body,
        )
    }

    suspend fun deleteProfile(id: String) {
        request(
            base = baseUrl,
            path = "/api/profiles/$id",
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
                body = EMPTY_BODY,
                query = emptyMap(),
            )
        }.isSuccess

    suspend fun channelSources(id: String): ChannelSources =
        getJson(baseUrl, "/api/channels/$id/streams")

    suspend fun reportChannelSource(
        channelId: String,
        url: String,
        event: String,
        latencyMs: Long = -1L,
    ) {
        if (baseUrl.isBlank() || channelId.isBlank() || url.isBlank()) return
        val body = buildJsonObject {
            put("url", url)
            put("event", event)
            if (latencyMs >= 0L) put("latencyMs", latencyMs)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        runCatching {
            request(baseUrl, "/api/channels/$channelId/source-report", "POST", body)
        }
    }

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
                call.cancel()
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
        if (scheme == "rtsp" || scheme == "rtmp" || scheme == "rtmps") return@withContext true
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
        genre: String? = null,
        platform: String? = null,
        language: String? = null,
        lang: String? = null,
        favorite: Boolean = false,
        query: String? = null,
        type: String? = null,
        limit: Int = 100,
        offset: Int = 0,
        sort: String = "number",
        paginated: Boolean = false,
    ): ChannelPage {
        val params = linkedMapOf(
            "limit" to limit.coerceIn(1, 5000).toString(),
            "offset" to offset.coerceAtLeast(0).toString(),
            "sort" to sort,
        )
        country?.takeIf { it.isNotBlank() }?.let { params["country"] = it }
        category?.takeIf { it.isNotBlank() }?.let { params["category"] = it }
        genre?.takeIf { it.isNotBlank() }?.let { params["genre"] = it }
        platform?.takeIf { it.isNotBlank() }?.let { params["platform"] = it }
        language?.takeIf { it.isNotBlank() }?.let { params["language"] = it }
        lang?.takeIf { it.isNotBlank() }?.let { params["lang"] = it }
        query?.takeIf { it.isNotBlank() }?.let { params["q"] = it }
        type?.takeIf { it.isNotBlank() }?.let { params["type"] = it }
        if (favorite) params["favorite"] = "1"
        if (paginated) params["paginated"] = "1"
        return getJson(baseUrl, "/api/channels", params)
    }

    suspend fun saveVodProgress(id: String, positionMs: Long, durationMs: Long) {
        val body = buildJsonObject {
            put("positionMs", positionMs.coerceAtLeast(0L))
            put("durationMs", durationMs.coerceAtLeast(0L))
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        runCatching {
            request(baseUrl, "/api/channels/$id/progress", "POST", body)
        }
    }

    suspend fun vodContinue(limit: Int = 20): ChannelPage =
        getJson(baseUrl, "/api/vod/continue", mapOf("limit" to limit.toString()))

    suspend fun removeVodContinue(id: String): Boolean =
        runCatching {
            request(baseUrl, "/api/vod/continue/$id", "DELETE", EMPTY_BODY)
            true
        }.getOrDefault(false)

    suspend fun listRecordings(): RecordingsListResponse =
        getJson(baseUrl, "/api/recordings")

    suspend fun startRecording(
        channelId: String,
        streamUrl: String,
        channelName: String,
        country: String? = null,
        logo: String? = null,
    ): RecordingSingleResponse {
        val body = buildJsonObject {
            put("deviceId", deviceId.orEmpty())
            put("channelId", channelId)
            put("streamUrl", streamUrl)
            put("channelName", channelName)
            country?.takeIf { it.isNotBlank() }?.let { put("country", it) }
            logo?.takeIf { it.isNotBlank() }?.let { put("logo", it) }
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        return sendJson(base = baseUrl, path = "/api/recordings/start", method = "POST", body = body)
    }

    suspend fun stopRecording(id: String): RecordingSingleResponse =
        sendJson(base = baseUrl, path = "/api/recordings/$id/stop", method = "POST", body = EMPTY_BODY)

    suspend fun recordingStatus(id: String): RecordingSingleResponse =
        getJson(baseUrl, "/api/recordings/$id/status")

    suspend fun deleteRecording(id: String): Boolean =
        runCatching {
            request(baseUrl, "/api/recordings/$id", "DELETE", EMPTY_BODY)
            true
        }.getOrDefault(false)

    suspend fun vodCategories(
        country: String? = null,
        type: String = "vod",
        lang: String? = null,
    ): VodCategoriesResponse {
        val params = linkedMapOf("type" to type, "limit" to "240")
        country?.takeIf { it.isNotBlank() }?.let { params["country"] = it }
        lang?.takeIf { it.isNotBlank() }?.let { params["lang"] = it }
        return getJson(baseUrl, "/api/vod/categories", params)
    }

    suspend fun vodRelated(id: String, limit: Int = 60): VodRelatedResponse =
        getJson(baseUrl, "/api/vod/related/$id", mapOf("limit" to limit.toString()))

    suspend fun vodSeriesDetail(id: String): SeriesDetailResponse =
        getJson(baseUrl, "/api/vod/series/$id")

    suspend fun channel(id: String): Channel =
        getJson(baseUrl, "/api/channels/$id")

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
            path = "/api/channels/$id",
            method = "PATCH",
            body = body,
        )
    }

    suspend fun markPlayed(id: String) {
        request(
            base = baseUrl,
            path = "/api/channels/$id/played",
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
        client.newCall(request).execute().use { response ->
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

    fun resolveUrl(pathOrUrl: String): String {
        val clean = pathOrUrl.trim()
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
        val base = baseUrl.trimEnd('/')
        val path = "/" + clean.trimStart('/')
        return "$base$path"
    }

    suspend fun startTimeshift(
        channelId: String,
        channelName: String,
        streamUrl: String,
        maxHours: Int,
    ): TimeshiftSessionDto {
        val body = buildJsonObject {
            put("deviceId", deviceId.orEmpty())
            put("channelId", channelId)
            put("channelName", channelName)
            put("streamUrl", streamUrl)
            put("maxHours", maxHours.coerceIn(1, 12))
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val resp = sendJson<TimeshiftSessionResponse>(
            base = baseUrl,
            path = "/api/timeshift/start",
            method = "POST",
            body = body,
        )
        return resp.session ?: throw ApiException(500, "Sessione timeshift non valida")
    }

    suspend fun timeshiftStatus(sessionId: String): TimeshiftSessionDto? =
        runCatching {
            getJson<TimeshiftSessionResponse>(baseUrl, "/api/timeshift/status/$sessionId").session
        }.getOrNull()

    suspend fun pendingTimeshift(channelId: String): TimeshiftSessionDto? =
        kotlinx.coroutines.withTimeoutOrNull(3000L) {
            runCatching {
                val decoded = getJson<TimeshiftPendingResponse>(
                    base = baseUrl,
                    path = "/api/timeshift/pending",
                    query = mapOf(
                        "channelId" to channelId,
                        "deviceId" to deviceId.orEmpty(),
                    ),
                )
                if (decoded.hasPending) decoded.session else null
            }.onFailure { err ->
                android.util.Log.w("CasaTV", "pendingTimeshift check failed: ${err.message}")
            }.getOrNull()
        }

    suspend fun deleteTimeshift(sessionId: String): Boolean =
        runCatching {
            request(
                base = baseUrl,
                path = "/api/timeshift/$sessionId",
                method = "DELETE",
                body = EMPTY_BODY,
            )
            true
        }.getOrDefault(false)

    suspend fun updateTimeshiftMaxHours(hours: Int): Boolean =
        runCatching {
            val body = buildJsonObject {
                put("timeshift_max_hours", hours.coerceIn(1, 12))
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
            request(
                base = baseUrl,
                path = "/api/settings",
                method = "PATCH",
                body = body,
            )
            true
        }.getOrDefault(false)

    suspend fun tuneLiveRelay(
        channelId: String,
        channelName: String,
        streamUrl: String,
        pinSource: Boolean = false,
    ): LiveRelayTuneResponse? =
        kotlinx.coroutines.withTimeoutOrNull(6_000L) {
            runCatching {
                val body = buildJsonObject {
                    put("deviceId", deviceId.orEmpty())
                    put("channelId", channelId)
                    put("channelName", channelName)
                    put("streamUrl", streamUrl)
                    put("pinSource", pinSource)
                }.toString().toRequestBody(JSON_MEDIA_TYPE)
                sendJson<LiveRelayTuneResponse>(
                    base = baseUrl,
                    path = "/api/relay/tune",
                    method = "POST",
                    body = body,
                )
            }.onFailure { err ->
                android.util.Log.w("CasaTV", "tuneLiveRelay fallback to direct: ${err.message}")
            }.getOrNull()
        }

    suspend fun releaseLiveRelay(channelId: String? = null) {
        runCatching {
            val body = buildJsonObject {
                put("deviceId", deviceId.orEmpty())
                if (!channelId.isNullOrBlank()) put("channelId", channelId)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
            request(
                base = baseUrl,
                path = "/api/relay/release",
                method = "POST",
                body = body,
            )
        }
    }

    suspend fun prewarmLiveRelay(
        channelId: String,
        channelName: String,
        streamUrl: String,
    ) {
        if (baseUrl.isBlank() || channelId.isBlank() || streamUrl.isBlank()) return
        kotlinx.coroutines.withTimeoutOrNull(2000L) {
            runCatching {
                val body = buildJsonObject {
                    put("deviceId", deviceId.orEmpty())
                    put("channelId", channelId)
                    put("channelName", channelName)
                    put("streamUrl", streamUrl)
                }.toString().toRequestBody(JSON_MEDIA_TYPE)
                request(
                    base = baseUrl,
                    path = "/api/relay/prewarm",
                    method = "POST",
                    body = body,
                )
            }
        }
    }

    suspend fun fetchMixReady(
        regions: Set<String> = emptySet(),
        languages: Set<String> = emptySet(),
        genres: Set<String> = emptySet(),
        excludeIds: Collection<String> = emptyList(),
        includeAudioOnly: Boolean = false,
        limit: Int = 6,
        prewarm: Boolean = true,
    ): MixReadyResponse? {
        if (baseUrl.isBlank()) return null
        return kotlinx.coroutines.withTimeoutOrNull(4500L) {
            runCatching {
                val query = linkedMapOf(
                    "limit" to limit.coerceIn(1, 20).toString(),
                    "prewarm" to (if (prewarm) "1" else "0"),
                    "includeAudioOnly" to (if (includeAudioOnly) "1" else "0"),
                )
                val regs = regions.filter { it.isNotBlank() && it != "ALL_WORLD" }
                if (regs.isNotEmpty()) query["regions"] = regs.joinToString(",")
                if (languages.isNotEmpty()) query["languages"] = languages.joinToString(",")
                if (genres.isNotEmpty()) query["genres"] = genres.joinToString(",")
                val ex = excludeIds.filter { it.isNotBlank() }.take(80)
                if (ex.isNotEmpty()) query["exclude"] = ex.joinToString(",")
                getJson<MixReadyResponse>(baseUrl, "/api/mix/ready", query)
            }.getOrNull()
        }
    }

    suspend fun reportMixDead(channelId: String) {
        if (baseUrl.isBlank() || channelId.isBlank()) return
        runCatching {
            val body = buildJsonObject { put("id", channelId) }.toString().toRequestBody(JSON_MEDIA_TYPE)
            request(base = baseUrl, path = "/api/mix/report-dead", method = "POST", body = body)
        }
    }

    suspend fun probeMixCandidates(
        candidates: List<Channel>,
        maxWinners: Int = 3,
        prewarm: Boolean = true,
    ): MixProbeResult? {
        if (baseUrl.isBlank() || candidates.isEmpty()) return null
        return kotlinx.coroutines.withTimeoutOrNull(4500L) {
            runCatching {
                val candArray = kotlinx.serialization.json.JsonArray(
                    candidates.take(12).map { ch ->
                        buildJsonObject {
                            put("id", ch.id)
                            put("name", ch.name)
                            put("streamUrl", ch.streamUrl)
                        }
                    },
                )
                val body = buildJsonObject {
                    put("deviceId", deviceId.orEmpty())
                    put("maxWinners", maxWinners.coerceIn(1, 5))
                    put("prewarm", prewarm)
                    put("candidates", candArray)
                }.toString().toRequestBody(JSON_MEDIA_TYPE)
                val raw = request(
                    base = baseUrl,
                    path = "/api/mix/probe",
                    method = "POST",
                    body = body,
                )
                val obj = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject ?: return@runCatching null
                val winners = (obj["winners"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }
                    .orEmpty()
                val dead = (obj["dead"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }
                    .orEmpty()
                val prewarmedId = (obj["prewarmedId"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                MixProbeResult(winners = winners, dead = dead, prewarmedId = prewarmedId)
            }.getOrNull()
        }
    }

    suspend fun updateServerRelayEnabled(enabled: Boolean): Boolean =
        runCatching {
            val body = buildJsonObject {
                put("server_relay_enabled", enabled)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
            request(
                base = baseUrl,
                path = "/api/settings",
                method = "PATCH",
                body = body,
            )
            true
        }.getOrDefault(false)

    suspend fun sendDiagnosticEvents(events: JsonArray): Boolean {
        if (events.isEmpty() || baseUrl.isBlank()) return false
        return runCatching {
            val body = buildJsonObject {
                put("events", events)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
            request(
                base = baseUrl,
                path = "/api/diagnostics/events",
                method = "POST",
                body = body,
            )
            true
        }.getOrDefault(false)
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
            requestBuilder.header("X-Device-Name", sanitizeHeaderValue(deviceName).ifBlank { "Android TV" })
            requestBuilder.header("X-Device-Platform", sanitizeHeaderValue(devicePlatform).ifBlank { "Android TV" })
            requestBuilder.header("X-Playback-State", sanitizeHeaderValue(playbackState).ifBlank { "idle" })
            if (playbackChannel.isNotBlank()) {
                requestBuilder.header("X-Playback-Channel", sanitizeHeaderValue(playbackChannel).ifBlank { "Channel" })
            }
        }
        adminPin?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("X-Admin-Pin", it)
        }

        val call = client.newCall(requestBuilder.build())
        val cancelHandle = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
            call.cancel()
        }
        try {
            call.execute().use { response ->
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
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun sanitizeHeaderValue(raw: String, maxLen: Int = 120): String {
        val nfkd = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKD)
        val ascii = buildString(nfkd.length) {
            for (ch in nfkd) {
                when {
                    ch.code in 0x20..0x7E -> append(ch)
                    ch.isWhitespace() -> append(' ')
                }
            }
        }.replace(Regex("\\s+"), " ").trim()
        return ascii.take(maxLen)
    }

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
            base = baseUrl,
            path = "/api/playlists/upload",
            method = "POST",
            body = multipart,
            query = mapOf("name" to name.ifBlank { fileName }),
        )
        json.decodeFromString<PlaylistActionResponse>(raw).playlist
    }.getOrNull()

    suspend fun checkPlaylist(id: String): PlaylistDto? = runCatching {
        val raw = request(baseUrl, "/api/playlists/$id/check", "POST", EMPTY_BODY)
        json.decodeFromString<PlaylistActionResponse>(raw).playlist
    }.getOrNull()

    suspend fun deletePlaylist(id: String): Boolean = runCatching {
        request(baseUrl, "/api/playlists/$id", "DELETE", EMPTY_BODY)
        true
    }.getOrDefault(false)

    suspend fun refreshPlaylist(id: String): PlaylistDto? = runCatching {
        val raw = request(baseUrl, "/api/playlists/$id/refresh", "POST", EMPTY_BODY)
        json.decodeFromString<PlaylistActionResponse>(raw).playlist
    }.getOrNull()

    suspend fun getAdminAuthStatus(): Pair<Boolean, Boolean> {
        val raw = request(baseUrl, "/api/settings/auth-status", "GET", null)
        val el = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject ?: return false to false
        val authorized = (el["authorized"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
        val isDefault = (el["isDefaultPin"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
        return authorized to isDefault
    }

    suspend fun verifyAdminPin(pin: String): Result<Boolean> = runCatching {
        val body = buildJsonObject { put("pin", pin.trim()) }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val raw = request(baseUrl, "/api/settings/verify-pin", "POST", body)
        val el = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
        (el?.get("mustChangePassword") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
    }

    suspend fun changeAdminPin(oldPin: String, newPin: String): Result<Unit> = runCatching {
        val body = buildJsonObject {
            put("oldPin", oldPin.trim())
            put("newPin", newPin.trim())
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        request(baseUrl, "/api/settings/change-pin", "POST", body)
        Unit
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        private val PORT_SUFFIX_REGEX = Regex(":\\d+$")
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
            val hasPort = PORT_SUFFIX_REGEX.containsMatchIn(authority)
            val builder = parsed.newBuilder().encodedPath("/")
            if (parsed.scheme == "http" && !hasPort) builder.port(8787)
            return builder.build().toString().trimEnd('/')
        }

        fun apiRoot(hint: String): HttpUrl =
            normalizeBaseUrl(hint).toHttpUrl()
    }
}

data class MixProbeResult(
    val winners: List<String>,
    val dead: List<String>,
    val prewarmedId: String?,
)
