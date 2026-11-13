package it.casatv.mobile.network

import it.casatv.mobile.data.MobileChannel
import it.casatv.mobile.data.ServerChannelPage
import it.casatv.mobile.data.ServerHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
        .build()

    suspend fun checkHealth(baseUrl: String): ServerHealth? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/healthz").build()
            client.newCall(req).execute().use { resp ->
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
            val pageSize = 2000

            while (true) {
                val url = "$cleanBase/api/channels?limit=$pageSize&offset=$offset&sort=number"
                val req = Request.Builder().url(url).build()
                val page = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body?.string() ?: error("Risposta vuota")
                    json.decodeFromString<ServerChannelPage>(body)
                }

                for (dto in page.items) {
                    if (dto.streamUrl.isBlank()) continue
                    val category = dto.categoryNames.firstOrNull()
                        ?: dto.categories.firstOrNull()?.replaceFirstChar { it.uppercase() }
                        ?: "Generale"
                    all.add(
                        MobileChannel(
                            id = "srv_${dto.id}",
                            number = dto.number,
                            name = dto.name,
                            streamUrl = dto.streamUrl,
                            logo = dto.logo,
                            country = dto.country,
                            countryName = dto.countryName,
                            flag = dto.flag,
                            category = category,
                            language = dto.languages.firstOrNull(),
                            quality = dto.quality,
                            epgId = dto.epgId,
                            playlistId = "casatv_server",
                            favorite = dto.favorite || favoriteUrls.contains(dto.streamUrl),
                            isFromServer = true,
                        ),
                    )
                }

                if (!page.hasMore || page.items.isEmpty()) break
                offset += page.items.size
            }

            all
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
}
