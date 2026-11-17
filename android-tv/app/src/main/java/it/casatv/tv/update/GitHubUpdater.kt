package it.casatv.tv.update

import android.content.Context
import it.casatv.tv.BuildConfig
import it.casatv.tv.data.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class TvUpdateFrequency(val days: Long, val label: String) {
    DAILY(1L, "Ogni giorno"),
    WEEKLY(7L, "Ogni settimana"),
    MONTHLY(30L, "Ogni mese"),
}

@Serializable
data class GitHubAsset(
    val name: String = "",
    val browser_download_url: String = "",
)

@Serializable
data class GitHubRelease(
    val tag_name: String = "",
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

class GitHubUpdater(context: Context) {
    private val prefs = context.getSharedPreferences("casatv_tv_updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun getFrequency(): TvUpdateFrequency {
        val raw = prefs.getString(KEY_FREQ, TvUpdateFrequency.WEEKLY.name) ?: TvUpdateFrequency.WEEKLY.name
        return runCatching { TvUpdateFrequency.valueOf(raw) }.getOrDefault(TvUpdateFrequency.WEEKLY)
    }

    fun setFrequency(freq: TvUpdateFrequency) {
        prefs.edit().putString(KEY_FREQ, freq.name).apply()
    }

    fun shouldAutoCheckNow(): Boolean {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        val intervalMs = getFrequency().days * 24L * 60L * 60L * 1000L
        return System.currentTimeMillis() - last >= intervalMs
    }

    suspend fun checkForUpdate(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!force && !shouldAutoCheckNow()) return@withContext null
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CasaTV-TV/${BuildConfig.VERSION_NAME}")
                .build()
            val (code, body) = client.newCall(req).execute().use { resp ->
                resp.code to resp.body?.string()
            }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            if (code == 200 && !body.isNullOrBlank()) {
                val release = json.decodeFromString<GitHubRelease>(body)
                val remoteVersion = release.tag_name.trim().removePrefix("v").removePrefix("V")
                if (!isNewerVersion(remoteVersion, BuildConfig.VERSION_NAME)) {
                    return@runCatching null
                }
                val asset = release.assets.firstOrNull {
                    it.name.contains("tv", ignoreCase = true) && it.name.endsWith(".apk", ignoreCase = true)
                } ?: return@runCatching null
                return@runCatching UpdateInfo(
                    latest = remoteVersion,
                    current = BuildConfig.VERSION_NAME,
                    updateAvailable = true,
                    downloadUrl = asset.browser_download_url,
                    changelog = release.body.ifBlank { "Aggiornamento CasaTV TV v$remoteVersion da GitHub" },
                )
            }

            if (code == 404) return@runCatching null

            val webReq = Request.Builder()
                .url("https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest")
                .header("User-Agent", "CasaTV-TV/${BuildConfig.VERSION_NAME}")
                .build()
            val finalUrl = client.newCall(webReq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.request.url.toString()
            }
            val tag = finalUrl.substringAfter("/releases/tag/", "").substringBefore('?').trim()
            if (tag.isBlank()) return@runCatching null
            val remoteVersion = tag.removePrefix("v").removePrefix("V")
            if (!isNewerVersion(remoteVersion, BuildConfig.VERSION_NAME)) {
                return@runCatching null
            }
            UpdateInfo(
                latest = remoteVersion,
                current = BuildConfig.VERSION_NAME,
                updateAvailable = true,
                downloadUrl = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/download/$tag/casatv-tv-$tag.apk",
                changelog = "Aggiornamento CasaTV TV v$remoteVersion da GitHub",
            )
        }.getOrNull()
    }

    companion object {
        private const val KEY_FREQ = "update_frequency"
        private const val KEY_LAST_CHECK = "last_update_check_ms"

        fun isNewerVersion(remote: String, current: String): Boolean {
            if (remote.isBlank() || current.isBlank()) return false
            val r = remote.split('.').mapNotNull { it.toIntOrNull() }
            val c = current.split('.').mapNotNull { it.toIntOrNull() }
            for (i in 0 until maxOf(r.size, c.size)) {
                val rv = r.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (rv != cv) return rv > cv
            }
            return false
        }
    }
}
