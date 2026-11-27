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
import java.io.File
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
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

class GitHubUpdater(context: Context) {
    private val prefs = context.getSharedPreferences("casatv_tv_updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Dedicated client without callTimeout cap so large APK downloads from GitHub CDN succeed on slower TV Wi-Fi.
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
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

    private fun pickTvAsset(assets: List<GitHubAsset>): GitHubAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        return apks.firstOrNull {
            it.name.contains("casatv-android-tv", ignoreCase = true) ||
                it.name.contains("casatv-tv", ignoreCase = true)
        } ?: apks.firstOrNull {
            it.name.contains("tv", ignoreCase = true) &&
                !it.name.contains("mobile", ignoreCase = true) &&
                !it.name.contains("server", ignoreCase = true)
        } ?: apks.firstOrNull {
            !it.name.contains("mobile", ignoreCase = true) &&
                !it.name.contains("server", ignoreCase = true)
        }
    }

    private fun releaseToUpdateInfo(release: GitHubRelease): UpdateInfo? {
        if (release.draft) return null
        val remoteVersion = release.tag_name.trim().removePrefix("v").removePrefix("V")
        if (!isNewerVersion(remoteVersion, BuildConfig.VERSION_NAME)) return null
        val asset = pickTvAsset(release.assets) ?: return null
        return UpdateInfo(
            latest = remoteVersion,
            current = BuildConfig.VERSION_NAME,
            updateAvailable = true,
            downloadUrl = asset.browser_download_url,
            changelog = release.body.ifBlank { "Aggiornamento CasaTV TV v$remoteVersion da GitHub" },
        )
    }

    private fun fetchGitHubApi(path: String): Pair<Int, String?> {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/$path")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CasaTV-TV/${BuildConfig.VERSION_NAME}")
            .build()
        return client.newCall(req).execute().use { resp ->
            resp.code to resp.body?.string()
        }
    }

    suspend fun checkForUpdate(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!force && !shouldAutoCheckNow()) return@withContext null
        runCatching {
            val (code, body) = fetchGitHubApi("releases/latest")
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            if (code == 200 && !body.isNullOrBlank()) {
                val release = json.decodeFromString<GitHubRelease>(body)
                val info = releaseToUpdateInfo(release)
                if (info != null) return@runCatching info
                val remoteVersion = release.tag_name.trim().removePrefix("v").removePrefix("V")
                if (!isNewerVersion(remoteVersion, BuildConfig.VERSION_NAME)) {
                    return@runCatching null
                }
            }

            // Also inspect recent releases (handles pre-releases or latest releases missing the asset tag).
            val (listCode, listBody) = fetchGitHubApi("releases?per_page=5")
            if (listCode == 200 && !listBody.isNullOrBlank()) {
                val releases = json.decodeFromString<List<GitHubRelease>>(listBody)
                for (release in releases) {
                    val info = releaseToUpdateInfo(release)
                    if (info != null) return@runCatching info
                }
                return@runCatching null
            }

            if (code == 404 && listCode == 404) return@runCatching null

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
                downloadUrl = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/download/$tag/casatv-android-tv-$tag.apk",
                changelog = "Aggiornamento CasaTV TV v$remoteVersion da GitHub",
            )
        }.getOrNull()
    }

    suspend fun downloadApk(
        url: String,
        destination: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "CasaTV-TV/${BuildConfig.VERSION_NAME}")
            .build()
        downloadClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Download fallito: HTTP ${resp.code}")
            val body = resp.body ?: error("Risposta vuota da GitHub")
            val total = body.contentLength()
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        onProgress(1f)
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
