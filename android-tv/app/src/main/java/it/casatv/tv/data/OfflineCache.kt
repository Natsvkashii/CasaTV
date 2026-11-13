package it.casatv.tv.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class OfflineCacheData(
    val config: ServerConfig? = null,
    val serverUrls: List<ServerUrl> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null,
    val channels: List<Channel> = emptyList(),
    val homeRows: List<HomeRow> = emptyList(),
    val favorites: List<Channel> = emptyList(),
    val lastSyncTime: Long = 0L,
    val primaryServerUrl: String = "",
)

class OfflineCache(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
    }

    private val cacheFile: File
        get() = File(context.filesDir, "casatv_offline_cache.json")

    @Volatile
    var cachedData: OfflineCacheData = OfflineCacheData()
        private set

    init {
        try {
            if (cacheFile.exists() && cacheFile.length() < 512 * 1024) {
                loadFromDisk()
            } else if (cacheFile.exists()) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    loadFromDisk()
                }
            }
        } catch (e: Exception) {
            Log.e("CasaTV", "Failed to init offline cache", e)
        }
    }

    fun hasValidCache(): Boolean {
        return cachedData.channels.isNotEmpty() || cachedData.config != null
    }

    fun loadFromDisk(): OfflineCacheData {
        return try {
            if (cacheFile.exists()) {
                val text = cacheFile.readText(Charsets.UTF_8)
                if (text.isNotBlank()) {
                    val data = json.decodeFromString<OfflineCacheData>(text)
                    cachedData = data
                    data
                } else cachedData
            } else cachedData
        } catch (e: Exception) {
            Log.e("CasaTV", "Failed to load offline cache from disk", e)
            cachedData
        }
    }

    suspend fun saveToDisk(data: OfflineCacheData) = withContext(Dispatchers.IO) {
        cachedData = data
        try {
            val text = json.encodeToString(data)
            val tmpFile = File(context.filesDir, "casatv_offline_cache.json.tmp")
            tmpFile.writeText(text, Charsets.UTF_8)
            if (tmpFile.renameTo(cacheFile)) {
                Log.i("CasaTV", "Offline cache salvata: ${data.channels.size} canali, ${data.serverUrls.size} URL server")
            } else {
                tmpFile.copyTo(cacheFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            Log.e("CasaTV", "Failed to write offline cache", e)
        }
    }

    suspend fun update(
        config: ServerConfig? = null,
        serverUrls: List<ServerUrl>? = null,
        profiles: List<Profile>? = null,
        activeProfileId: String? = null,
        channels: List<Channel>? = null,
        homeRows: List<HomeRow>? = null,
        favorites: List<Channel>? = null,
        primaryServerUrl: String? = null,
    ) {
        val current = cachedData
        val updated = current.copy(
            config = config ?: current.config,
            serverUrls = serverUrls ?: (config?.urls?.takeIf { it.isNotEmpty() } ?: current.serverUrls),
            profiles = profiles ?: current.profiles,
            activeProfileId = activeProfileId ?: current.activeProfileId,
            channels = channels ?: current.channels,
            homeRows = homeRows ?: current.homeRows,
            favorites = favorites ?: current.favorites,
            lastSyncTime = System.currentTimeMillis(),
            primaryServerUrl = primaryServerUrl ?: current.primaryServerUrl,
        )
        saveToDisk(updated)
    }

    fun getChannels(
        country: String? = null,
        category: String? = null,
        query: String? = null,
        favoriteOnly: Boolean = false,
        favoriteIds: Set<String> = emptySet(),
    ): List<Channel> {
        var list = cachedData.channels
        if (favoriteOnly) {
            list = list.filter { it.favorite || it.id in favoriteIds }
        }
        if (!country.isNullOrBlank()) {
            list = list.filter { it.country.equals(country, ignoreCase = true) }
        }
        if (!category.isNullOrBlank()) {
            list = list.filter { ch ->
                ch.categories.any { it.equals(category, ignoreCase = true) } ||
                ch.categoryNames.any { it.equals(category, ignoreCase = true) }
            }
        }
        if (!query.isNullOrBlank()) {
            list = list.filter { it.name.contains(query, ignoreCase = true) }
        }
        return list
    }

    fun findChannelByNumber(number: Int): Channel? {
        return cachedData.channels.firstOrNull { it.number == number }
    }

    fun findChannelById(id: String): Channel? {
        return cachedData.channels.firstOrNull { it.id == id }
    }
}
