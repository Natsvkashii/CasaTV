package it.casatv.mobile.network

import it.casatv.mobile.data.MobileChannel
import it.casatv.mobile.data.MobileStreamSource
import it.casatv.mobile.data.MobileVodCategoriesResponse
import it.casatv.mobile.data.MobileVodCategoryDto
import it.casatv.mobile.data.ServerChannelDto
import it.casatv.mobile.data.ServerChannelPage
import it.casatv.mobile.data.ServerHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

class CasaServerClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend inline fun <T> executeCall(req: Request, block: (Response) -> T): T {
        val call = client.newCall(req)
        val handle = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
            call.cancel()
        }
        return try {
            call.execute().use(block)
        } finally {
            handle?.dispose()
        }
    }

    suspend fun checkHealth(baseUrl: String): ServerHealth? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/healthz").build()
            executeCall(req) { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                json.decodeFromString<ServerHealth>(body)
            }
        }.getOrNull()
    }

    suspend fun fetchChannels(baseUrl: String, favoriteUrls: Set<String>): List<MobileChannel> =
        withContext(Dispatchers.IO) {
            val cleanBase = baseUrl.trimEnd('/')
            val all = mutableListOf<MobileChannel>()
            var offset = 0
            val pageSize = 1000
            val maxChannels = 4000

            while (all.size < maxChannels) {
                val url = "$cleanBase/api/channels?type=live&limit=$pageSize&offset=$offset&sort=number&paginated=1"
                val req = Request.Builder().url(url).build()
                val page = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body?.string() ?: error("Risposta vuota")
                    json.decodeFromString<ServerChannelPage>(body)
                }

                for (dto in page.items) {
                    if (dto.streamUrl.isBlank()) continue
                    all.add(dto.toMobileChannel(favoriteUrls))
                }

                if (!page.hasMore || page.items.isEmpty()) break
                offset += page.items.size
            }

            all
        }

    private fun ServerChannelDto.toMobileChannel(favoriteUrls: Set<String>): MobileChannel {
        val category = categoryNames.firstOrNull()
            ?: categories.firstOrNull()?.replaceFirstChar { it.uppercase() }
            ?: "Generale"
        return MobileChannel(
            id = "srv_$id",
            number = number,
            name = name,
            streamUrl = streamUrl,
            logo = logo,
            country = country,
            countryName = countryName,
            flag = flag,
            category = category,
            language = languages.firstOrNull(),
            quality = quality,
            epgId = epgId,
            playlistId = "casatv_server",
            favorite = favorite || favoriteUrls.contains(streamUrl),
            isFromServer = true,
            contentType = contentType,
            isVod = isVod,
            vodStreamUrl = vodStreamUrl,
            positionMs = positionMs,
            durationMs = durationMs,
            upNext = upNext,
            seriesTitle = seriesTitle,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle,
        )
    }

    suspend fun fetchVodSeriesDetail(
        baseUrl: String,
        channelId: String,
        favoriteUrls: Set<String>,
    ): Pair<it.casatv.mobile.data.MobileSeriesDetailDto, Map<Int, List<MobileChannel>>>? =
        withContext(Dispatchers.IO) {
            val cleanId = channelId.removePrefix("srv_").trim()
            if (cleanId.isEmpty()) return@withContext null
            runCatching {
                val url = "${baseUrl.trimEnd('/')}/api/vod/series/${java.net.URLEncoder.encode(cleanId, "UTF-8")}"
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string() ?: return@runCatching null
                    val parsed = json.decodeFromString<it.casatv.mobile.data.MobileSeriesDetailDto>(body)
                    val seasonsMap = parsed.seasons.associate { s ->
                        s.season to s.episodes.filter { it.streamUrl.isNotBlank() }.map { it.toMobileChannel(favoriteUrls) }
                    }
                    parsed to seasonsMap
                }
            }.getOrNull()
        }

    suspend fun fetchVodRelatedNext(
        baseUrl: String,
        channelId: String,
        favoriteUrls: Set<String>,
    ): MobileChannel? =
        withContext(Dispatchers.IO) {
            val cleanId = channelId.removePrefix("srv_").trim()
            if (cleanId.isEmpty()) return@withContext null
            runCatching {
                val url = "${baseUrl.trimEnd('/')}/api/vod/related/${java.net.URLEncoder.encode(cleanId, "UTF-8")}?limit=60"
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string() ?: return@runCatching null
                    val obj = org.json.JSONObject(body)
                    if (obj.optString("mode") != "episodes") return@runCatching null
                    val items = json.decodeFromString<ServerChannelPage>(body).items
                        .filter { it.streamUrl.isNotBlank() }
                        .map { it.toMobileChannel(favoriteUrls) }
                    val idx = items.indexOfFirst { it.id == "srv_$cleanId" || it.id == channelId }
                    if (idx >= 0 && idx + 1 < items.size) items[idx + 1] else null
                }
            }.getOrNull()
        }

    suspend fun fetchVodPage(
        baseUrl: String,
        country: String?,
        category: String?,
        genre: String? = null,
        platform: String? = null,
        lang: String? = null,
        type: String,
        limit: Int = 120,
        offset: Int = 0,
        favoriteUrls: Set<String>,
    ): Pair<List<MobileChannel>, Int> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trimEnd('/')
        val sb = StringBuilder("$cleanBase/api/channels?type=${java.net.URLEncoder.encode(type, "UTF-8")}&limit=$limit&offset=$offset&sort=name&paginated=1")
        if (!country.isNullOrBlank() && category.isNullOrBlank()) {
            sb.append("&country=").append(java.net.URLEncoder.encode(country, "UTF-8"))
        }
        if (!lang.isNullOrBlank()) {
            sb.append("&lang=").append(java.net.URLEncoder.encode(lang, "UTF-8"))
        }
        if (!genre.isNullOrBlank()) {
            sb.append("&genre=").append(java.net.URLEncoder.encode(genre, "UTF-8"))
        }
        if (!platform.isNullOrBlank()) {
            sb.append("&platform=").append(java.net.URLEncoder.encode(platform, "UTF-8"))
        }
        if (!category.isNullOrBlank()) {
            sb.append("&category=").append(java.net.URLEncoder.encode(category, "UTF-8"))
        }
        val req = Request.Builder().url(sb.toString()).build()
        val page = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("Risposta vuota")
            json.decodeFromString<ServerChannelPage>(body)
        }
        val list = page.items.filter { it.streamUrl.isNotBlank() }.map { it.toMobileChannel(favoriteUrls) }
        list to page.total
    }

    suspend fun fetchVodContinue(baseUrl: String, favoriteUrls: Set<String>): List<MobileChannel> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url("${baseUrl.trimEnd('/')}/api/vod/continue?limit=20").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching emptyList()
                    val body = resp.body?.string() ?: return@runCatching emptyList()
                    json.decodeFromString<ServerChannelPage>(body).items
                        .filter { it.streamUrl.isNotBlank() }
                        .map { it.toMobileChannel(favoriteUrls) }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun removeVodContinue(baseUrl: String, serverChannelId: String): Boolean =
        withContext(Dispatchers.IO) {
            val cleanId = serverChannelId.removePrefix("srv_").trim()
            if (cleanId.isEmpty()) return@withContext false
            runCatching {
                val encId = java.net.URLEncoder.encode(cleanId, "UTF-8").replace("+", "%20")
                val req = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/api/vod/continue/$encId")
                    .delete()
                    .build()
                executeCall(req) { it.isSuccessful }
            }.getOrDefault(false)
        }

    suspend fun fetchVodTaxonomy(
        baseUrl: String,
        country: String?,
        type: String,
        lang: String? = null,
    ): MobileVodCategoriesResponse =
        withContext(Dispatchers.IO) {
            runCatching {
                val cArg = country?.takeIf { it.isNotBlank() }?.let { "&country=${java.net.URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
                val lArg = lang?.takeIf { it.isNotBlank() }?.let { "&lang=${java.net.URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
                val url = "${baseUrl.trimEnd('/')}/api/vod/categories?type=${java.net.URLEncoder.encode(type, "UTF-8")}&limit=240$cArg$lArg"
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching MobileVodCategoriesResponse()
                    val body = resp.body?.string() ?: return@runCatching MobileVodCategoriesResponse()
                    json.decodeFromString<MobileVodCategoriesResponse>(body)
                }
            }.getOrDefault(MobileVodCategoriesResponse())
        }

    suspend fun fetchMixReady(
        baseUrl: String,
        favoriteUrls: Set<String>,
        regions: Set<String> = emptySet(),
        languages: Set<String> = emptySet(),
        genres: Set<String> = emptySet(),
        excludeIds: Collection<String> = emptyList(),
        includeAudioOnly: Boolean = false,
        limit: Int = 6,
    ): List<MobileChannel> = withContext(Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder("${baseUrl.trimEnd('/')}/api/mix/ready?limit=${limit.coerceIn(1, 20)}&prewarm=0&includeAudioOnly=${if (includeAudioOnly) 1 else 0}")
            val regs = regions.filter { it.isNotBlank() && it != "ALL_WORLD" }
            if (regs.isNotEmpty()) sb.append("&regions=").append(java.net.URLEncoder.encode(regs.joinToString(","), "UTF-8"))
            if (languages.isNotEmpty()) sb.append("&languages=").append(java.net.URLEncoder.encode(languages.joinToString(","), "UTF-8"))
            if (genres.isNotEmpty()) sb.append("&genres=").append(java.net.URLEncoder.encode(genres.joinToString(","), "UTF-8"))
            val ex = excludeIds.map { it.removePrefix("srv_") }.filter { it.isNotBlank() }.take(80)
            if (ex.isNotEmpty()) sb.append("&exclude=").append(java.net.URLEncoder.encode(ex.joinToString(","), "UTF-8"))
            val req = Request.Builder().url(sb.toString()).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val body = resp.body?.string() ?: return@runCatching emptyList()
                json.decodeFromString<ServerChannelPage>(body).items
                    .filter { it.streamUrl.isNotBlank() }
                    .map { it.toMobileChannel(favoriteUrls) }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun reportMixDead(baseUrl: String, channelId: String) = withContext(Dispatchers.IO) {
        val cleanId = channelId.removePrefix("srv_").trim()
        if (cleanId.isEmpty()) return@withContext
        runCatching {
            val body = buildJsonObject { put("id", cleanId) }.toString().toRequestBody(JSON_MEDIA_TYPE)
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/mix/report-dead")
                .post(body)
                .build()
            executeCall(req) { }
        }
    }

    suspend fun saveVodProgress(baseUrl: String, serverChannelId: String, positionMs: Long, durationMs: Long) =
        withContext(Dispatchers.IO) {
            val cleanId = serverChannelId.removePrefix("srv_").trim()
            if (cleanId.isEmpty()) return@withContext
            runCatching {
                val encId = java.net.URLEncoder.encode(cleanId, "UTF-8").replace("+", "%20")
                val body = """{"positionMs":${positionMs.coerceAtLeast(0L)},"durationMs":${durationMs.coerceAtLeast(0L)}}"""
                    .toRequestBody(JSON_MEDIA_TYPE)
                val req = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/api/channels/$encId/progress")
                    .post(body)
                    .build()
                executeCall(req) { }
            }
        }

    suspend fun setFavorite(baseUrl: String, serverChannelId: String, favorite: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val cleanId = serverChannelId.removePrefix("srv_").trim()
            if (cleanId.isEmpty()) return@withContext false
            runCatching {
                val cleanBase = baseUrl.trimEnd('/')
                val encId = java.net.URLEncoder.encode(cleanId, "UTF-8").replace("+", "%20")
                val body = """{"favorite":$favorite}""".toRequestBody(JSON_MEDIA_TYPE)
                val req = Request.Builder()
                    .url("$cleanBase/api/channels/$encId")
                    .patch(body)
                    .build()
                executeCall(req) { it.isSuccessful }
            }.getOrDefault(false)
        }

    suspend fun fetchChannelSources(baseUrl: String, channelId: String): List<MobileStreamSource> =
        withContext(Dispatchers.IO) {
            val cleanId = channelId.removePrefix("srv_").trim()
            if (cleanId.isEmpty()) return@withContext emptyList()
            runCatching {
                val encId = java.net.URLEncoder.encode(cleanId, "UTF-8").replace("+", "%20")
                val req = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/api/channels/$encId/streams")
                    .build()
                executeCall(req) { resp ->
                    if (!resp.isSuccessful) return@runCatching emptyList()
                    val body = resp.body?.string() ?: return@runCatching emptyList()
                    json.decodeFromString<MobileSourcesEnvelope>(body).sources
                }
            }.getOrDefault(emptyList())
        }

    suspend fun fetchServerM3u(baseUrl: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = baseUrl.trimEnd('/')
            val req = Request.Builder().url("$cleanBase/api/playlist/export.m3u").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()?.takeIf { it.contains("#EXTINF", ignoreCase = true) }
            }
        }.getOrNull()
    }

    suspend fun fetchRecordings(baseUrl: String): List<it.casatv.mobile.data.MobileRecordingDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url("${baseUrl.trimEnd('/')}/api/recordings").build()
                executeCall(req) { resp ->
                    if (!resp.isSuccessful) return@runCatching emptyList()
                    val body = resp.body?.string() ?: return@runCatching emptyList()
                    json.decodeFromString<it.casatv.mobile.data.MobileRecordingsListResponse>(body).recordings
                }
            }.getOrDefault(emptyList())
        }

    suspend fun startRecording(
        baseUrl: String,
        channelId: String,
        streamUrl: String,
        channelName: String,
        country: String? = null,
        logo: String? = null,
    ): it.casatv.mobile.data.MobileRecordingDto? = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("channelId", channelId.removePrefix("srv_"))
                put("streamUrl", streamUrl)
                put("channelName", channelName)
                country?.takeIf { it.isNotBlank() }?.let { put("country", it) }
                logo?.takeIf { it.isNotBlank() }?.let { put("logo", it) }
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/api/recordings/start").post(body).build()
            executeCall(req) { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val raw = resp.body?.string() ?: return@runCatching null
                json.decodeFromString<it.casatv.mobile.data.MobileRecordingSingleResponse>(raw).recording
            }
        }.getOrNull()
    }

    suspend fun stopRecording(baseUrl: String, id: String): it.casatv.mobile.data.MobileRecordingDto? =
        withContext(Dispatchers.IO) {
            runCatching {
                val empty = "".toRequestBody(JSON_MEDIA_TYPE)
                val req = Request.Builder().url("${baseUrl.trimEnd('/')}/api/recordings/$id/stop").post(empty).build()
                executeCall(req) { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val raw = resp.body?.string() ?: return@runCatching null
                    json.decodeFromString<it.casatv.mobile.data.MobileRecordingSingleResponse>(raw).recording
                }
            }.getOrNull()
        }

    suspend fun deleteRecording(baseUrl: String, id: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url("${baseUrl.trimEnd('/')}/api/recordings/$id").delete().build()
                executeCall(req) { it.isSuccessful }
            }.getOrDefault(false)
        }

    suspend fun reportChannelSource(
        baseUrl: String,
        channelId: String,
        url: String,
        event: String,
        latencyMs: Long = -1L,
    ) = withContext(Dispatchers.IO) {
        val cleanId = channelId.removePrefix("srv_").trim()
        val cleanUrl = url.trim()
        if (cleanId.isEmpty() || cleanUrl.isEmpty()) return@withContext
        runCatching {
            val encId = java.net.URLEncoder.encode(cleanId, "UTF-8").replace("+", "%20")
            val body = buildJsonObject {
                put("url", cleanUrl)
                put("event", event)
                if (latencyMs >= 0L) put("latencyMs", latencyMs)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
            val req = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/channels/$encId/source-report")
                .post(body)
                .build()
            executeCall(req) { }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class MobileSourcesEnvelope(
    val sources: List<MobileStreamSource> = emptyList(),
)
