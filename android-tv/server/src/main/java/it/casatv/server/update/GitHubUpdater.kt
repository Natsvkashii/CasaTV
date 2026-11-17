package it.casatv.server.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import it.casatv.server.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

enum class UpdateFrequency(val days: Long, val labelIt: String, val labelEn: String) {
    DAILY(1L, "Ogni giorno", "Daily"),
    WEEKLY(7L, "Ogni settimana", "Weekly"),
    MONTHLY(30L, "Ogni mese", "Monthly"),
}

@Serializable
data class GitHubAsset(
    val name: String = "",
    val browser_download_url: String = "",
)

@Serializable
data class GitHubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

data class AvailableUpdate(
    val version: String,
    val downloadUrl: String,
    val notes: String,
)

class GitHubUpdater(private val context: Context) {
    private val prefs = context.getSharedPreferences("casatv_server_updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getFrequency(): UpdateFrequency {
        val raw = prefs.getString(KEY_FREQ, UpdateFrequency.WEEKLY.name) ?: UpdateFrequency.WEEKLY.name
        return runCatching { UpdateFrequency.valueOf(raw) }.getOrDefault(UpdateFrequency.WEEKLY)
    }

    fun setFrequency(freq: UpdateFrequency) {
        prefs.edit().putString(KEY_FREQ, freq.name).apply()
    }

    fun shouldAutoCheckNow(): Boolean {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        val intervalMs = getFrequency().days * 24L * 60L * 60L * 1000L
        return System.currentTimeMillis() - last >= intervalMs
    }

    suspend fun checkForUpdate(force: Boolean = false): Result<AvailableUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            if (!force && !shouldAutoCheckNow()) return@runCatching null
            val req = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CasaTV-Server/${BuildConfig.VERSION_NAME}")
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
                    it.name.contains("server", ignoreCase = true) && it.name.endsWith(".apk", ignoreCase = true)
                } ?: return@runCatching null
                return@runCatching AvailableUpdate(
                    version = remoteVersion,
                    downloadUrl = asset.browser_download_url,
                    notes = release.body.ifBlank { "Nuova versione $remoteVersion disponibile su GitHub" },
                )
            }

            if (code == 404) return@runCatching null

            val webReq = Request.Builder()
                .url("https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest")
                .header("User-Agent", "CasaTV-Server/${BuildConfig.VERSION_NAME}")
                .build()
            val finalUrl = client.newCall(webReq).execute().use { resp ->
                if (!resp.isSuccessful) error("GitHub HTTP $code")
                resp.request.url.toString()
            }
            val tag = finalUrl.substringAfter("/releases/tag/", "").substringBefore('?').trim()
            if (tag.isBlank()) return@runCatching null
            val remoteVersion = tag.removePrefix("v").removePrefix("V")
            if (!isNewerVersion(remoteVersion, BuildConfig.VERSION_NAME)) {
                return@runCatching null
            }
            AvailableUpdate(
                version = remoteVersion,
                downloadUrl = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/download/$tag/casatv-server-$tag.apk",
                notes = "Nuova versione $remoteVersion disponibile su GitHub",
            )
        }
    }

    suspend fun downloadAndInstall(url: String, onProgress: (Float) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(dir, "casatv-server-update.apk")
                if (apkFile.exists()) apkFile.delete()

                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Download fallito (HTTP ${resp.code})")
                    val body = resp.body ?: error("Corpo vuoto")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        apkFile.outputStream().use { output ->
                            val buf = ByteArray(16 * 1024)
                            var read: Int
                            var downloaded = 0L
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                downloaded += read
                                if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
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
