package it.casatv.mobile.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import it.casatv.mobile.data.LocalPlaylist
import it.casatv.mobile.data.MobileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class PlaylistRepository(
    private val context: Context,
    private val db: LocalDatabase,
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getPlaylists(): List<LocalPlaylist> = withContext(Dispatchers.IO) {
        db.getPlaylists()
    }

    suspend fun getLocalChannels(): List<MobileChannel> = withContext(Dispatchers.IO) {
        db.getEnabledChannels()
    }

    suspend fun getFavoriteUrls(): Set<String> = withContext(Dispatchers.IO) {
        db.getFavoriteUrls()
    }

    suspend fun setFavorite(streamUrl: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        db.setFavorite(streamUrl, favorite)
    }

    suspend fun togglePlaylist(playlistId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.setPlaylistEnabled(playlistId, enabled)
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        db.deletePlaylist(playlistId)
    }

    suspend fun importFromUrl(name: String, url: String): Result<LocalPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = url.trim()
            val playlistId = UUID.randomUUID().toString().take(12)
            val fallbackName = name.trim().ifEmpty {
                Uri.parse(cleanUrl).lastPathSegment?.substringBeforeLast('.')?.ifBlank { "Lista M3U" }
                    ?: "Lista M3U"
            }
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                val failed = LocalPlaylist(
                    id = playlistId,
                    name = fallbackName,
                    source = cleanUrl,
                    isLocalFile = false,
                    enabled = true,
                    channelCount = 0,
                    status = "error",
                    lastError = "L'URL deve iniziare con http:// o https://",
                )
                db.savePlaylistRecord(failed)
                return@runCatching failed
            }
            val body = try {
                downloadM3u(cleanUrl)
            } catch (e: Exception) {
                val failed = LocalPlaylist(
                    id = playlistId,
                    name = fallbackName,
                    source = cleanUrl,
                    isLocalFile = false,
                    enabled = true,
                    channelCount = 0,
                    status = "error",
                    lastError = e.message ?: "Errore download",
                )
                db.savePlaylistRecord(failed)
                return@runCatching failed
            }

            val channels = M3uParser.parse(body, playlistId)
            if (channels.isEmpty()) {
                val failed = LocalPlaylist(
                    id = playlistId,
                    name = fallbackName,
                    source = cleanUrl,
                    isLocalFile = false,
                    enabled = true,
                    channelCount = 0,
                    status = "error",
                    lastError = diagnoseEmptyPlaylistError(body),
                )
                db.savePlaylistRecord(failed)
                return@runCatching failed
            }

            val (status, lastError) = probeSampleStreams(channels.map { it.streamUrl }, channels.size)
            val playlist = LocalPlaylist(
                id = playlistId,
                name = fallbackName,
                source = cleanUrl,
                isLocalFile = false,
                enabled = true,
                channelCount = channels.size,
                updatedAt = System.currentTimeMillis(),
                status = status,
                lastError = lastError,
            )
            db.upsertPlaylistAndChannels(playlist, channels)
            playlist
        }
    }

    suspend fun importFromUri(uri: Uri, customName: String? = null): Result<LocalPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = customName?.trim()?.takeIf { it.isNotEmpty() } ?: resolveFileName(uri)
            val playlistId = UUID.randomUUID().toString().take(12)
            val text = try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Impossibile leggere il file selezionato")
            } catch (e: Exception) {
                val failed = LocalPlaylist(
                    id = playlistId,
                    name = fileName,
                    source = uri.toString(),
                    isLocalFile = true,
                    enabled = true,
                    channelCount = 0,
                    status = "error",
                    lastError = e.message ?: "Errore lettura file",
                )
                db.savePlaylistRecord(failed)
                return@runCatching failed
            }

            val channels = M3uParser.parse(text, playlistId)
            if (channels.isEmpty()) {
                val failed = LocalPlaylist(
                    id = playlistId,
                    name = fileName,
                    source = uri.toString(),
                    isLocalFile = true,
                    enabled = true,
                    channelCount = 0,
                    status = "error",
                    lastError = diagnoseEmptyPlaylistError(text),
                )
                db.savePlaylistRecord(failed)
                return@runCatching failed
            }

            val (status, lastError) = probeSampleStreams(channels.map { it.streamUrl }, channels.size)
            val playlist = LocalPlaylist(
                id = playlistId,
                name = fileName,
                source = uri.toString(),
                isLocalFile = true,
                enabled = true,
                channelCount = channels.size,
                updatedAt = System.currentTimeMillis(),
                status = status,
                lastError = lastError,
            )
            db.upsertPlaylistAndChannels(playlist, channels)
            playlist
        }
    }

    suspend fun importFromM3uText(
        id: String,
        name: String,
        source: String,
        m3uText: String,
    ): Result<LocalPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val channels = M3uParser.parse(m3uText, id)
            require(channels.isNotEmpty()) { "Nessun canale valido nella lista M3U del server" }
            val playlist = LocalPlaylist(
                id = id,
                name = name,
                source = source,
                isLocalFile = false,
                enabled = true,
                channelCount = channels.size,
                updatedAt = System.currentTimeMillis(),
                status = "ok",
                lastError = "",
            )
            db.upsertPlaylistAndChannels(playlist, channels)
            playlist
        }
    }

    suspend fun refreshUrlPlaylist(playlist: LocalPlaylist): Result<LocalPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            if (playlist.isLocalFile) {
                return@runCatching checkPlaylist(playlist).getOrThrow()
            }
            val body = try {
                downloadM3u(playlist.source)
            } catch (e: Exception) {
                val msg = e.message ?: "Errore aggiornamento"
                db.updatePlaylistStatus(playlist.id, "error", msg)
                error(msg)
            }
            val channels = M3uParser.parse(body, playlist.id)
            if (channels.isEmpty()) {
                val msg = diagnoseEmptyPlaylistError(body)
                db.updatePlaylistStatus(playlist.id, "error", msg)
                error(msg)
            }
            val (status, lastError) = probeSampleStreams(channels.map { it.streamUrl }, channels.size)
            val updated = playlist.copy(
                channelCount = channels.size,
                updatedAt = System.currentTimeMillis(),
                status = status,
                lastError = lastError,
            )
            db.upsertPlaylistAndChannels(updated, channels)
            updated
        }
    }

    suspend fun checkPlaylist(playlist: LocalPlaylist): Result<LocalPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val sampleUrls = db.sampleChannelUrls(playlist.id, 6)
            if (sampleUrls.isEmpty()) {
                if (!playlist.isLocalFile &&
                    (playlist.source.startsWith("http://") || playlist.source.startsWith("https://"))
                ) {
                    return@runCatching refreshUrlPlaylist(playlist).getOrThrow()
                }
                val err = playlist.lastError.ifBlank { "Nessun canale valido nella playlist" }
                db.updatePlaylistStatus(playlist.id, "error", err)
                return@runCatching playlist.copy(status = "error", lastError = err, updatedAt = System.currentTimeMillis())
            }
            var (status, lastError) = probeSampleStreams(sampleUrls, playlist.channelCount.coerceAtLeast(sampleUrls.size))
            if (status != "ok" && !playlist.isLocalFile &&
                (playlist.source.startsWith("http://") || playlist.source.startsWith("https://"))
            ) {
                val linkErr = probeSingleUrl(playlist.source, isPlaylistUrl = true)
                if (linkErr != null) {
                    status = "error"
                    lastError = "Link playlist non raggiungibile ($linkErr)"
                }
            }
            db.updatePlaylistStatus(playlist.id, status, lastError)
            playlist.copy(status = status, lastError = lastError, updatedAt = System.currentTimeMillis())
        }
    }

    suspend fun uploadFileToServer(serverUrl: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveRawFileName(uri)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Impossibile leggere il file selezionato")
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", fileName)
                .addFormDataPart(
                    "file",
                    fileName,
                    bytes.toRequestBody("application/octet-stream".toMediaType()),
                )
                .build()
            val encodedName = java.net.URLEncoder.encode(fileName, Charsets.UTF_8.name()).replace("+", "%20")
            val req = Request.Builder()
                .url("${serverUrl.trimEnd('/')}/api/playlists/upload?name=$encodedName")
                .post(multipart)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val err = runCatching { JSONObject(raw).optString("error") }.getOrNull().orEmpty()
                    error(err.ifBlank { "HTTP ${resp.code}" })
                }
                val plObj = runCatching { JSONObject(raw).optJSONObject("playlist") }.getOrNull()
                val status = plObj?.optString("status", "ok") ?: "ok"
                val count = plObj?.optInt("channelCount", 0) ?: 0
                val lastError = plObj?.optString("lastError", "").orEmpty()
                if (status == "error" || count == 0) {
                    error("Caricato su server ma non funziona: ${lastError.ifBlank { "nessun canale" }}")
                }
                "File '$fileName' caricato sul server ($count canali)"
            }
        }
    }

    private fun downloadM3u(url: String): String {
        val first = executeDownload(url, CHROME_USER_AGENT)
        return if (first.first == 403 || first.first == 406) {
            val retry = executeDownload(url, VLC_USER_AGENT)
            if (retry.first !in 200..299) error("Download fallito (HTTP ${retry.first})")
            retry.second
        } else {
            if (first.first !in 200..299) error("Download fallito (HTTP ${first.first})")
            first.second
        }
    }

    private fun executeDownload(url: String, userAgent: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "audio/x-mpegurl, application/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
            .build()
        httpClient.newCall(request).execute().use { resp ->
            val code = resp.code
            if (!resp.isSuccessful) return code to ""
            return code to (resp.body?.string() ?: error("Risposta vuota"))
        }
    }

    private fun diagnoseEmptyPlaylistError(text: String): String {
        val trimmed = text.trimStart()
        if (trimmed.isEmpty()) return "Playlist vuota o non leggibile"
        val sample = trimmed.take(4096).lowercase(java.util.Locale.ROOT)
        if (sample.contains("<html") ||
            sample.contains("<!doctype") ||
            sample.contains("<body") ||
            sample.contains("<title") ||
            sample.contains("<b>warning</b>") ||
            sample.contains("<b>fatal error</b>") ||
            sample.contains("fopen(") ||
            sample.contains("failed to open stream")
        ) {
            return "Il link non restituisce una lista M3U (il server remoto ha risposto con una pagina HTML o un errore)"
        }
        if ((sample.contains("#ext-x-targetduration") ||
                sample.contains("#ext-x-media-sequence") ||
                sample.contains("#ext-x-stream-inf")) &&
            !sample.contains("#extinf:-1")
        ) {
            return "Questo link è un singolo flusso HLS, non una playlist di canali"
        }
        return "Nessun canale IPTV valido trovato (#EXTM3U / PLS)"
    }

    private fun probeSampleStreams(
        urls: List<String>,
        totalChannels: Int = urls.size,
    ): Pair<String, String> {
        if (urls.isEmpty()) return "error" to "Nessun canale nella playlist"
        val preferred = urls.filter { raw ->
            val u = raw.substringBefore('?').lowercase(java.util.Locale.ROOT)
            !u.endsWith(".mp4") && !u.endsWith(".jpg") && !u.endsWith(".png")
        }
        val pool = if (preferred.isNotEmpty()) preferred else urls
        val targetCount = 6
        val samples = if (pool.size <= targetCount) {
            pool
        } else {
            val step = (pool.size - 1).coerceAtLeast(1)
            val indices = LinkedHashSet<Int>(targetCount)
            for (i in 0 until targetCount) {
                indices += (i * step) / (targetCount - 1)
            }
            indices.map { pool[it] }
        }
        var lastErr = ""
        for (raw in samples) {
            val err = probeSingleUrl(raw)
            if (err == null) return "ok" to ""
            lastErr = err
        }
        return "warn" to "Playlist valida ($totalChannels canali), ma i flussi di prova non rispondono ($lastErr)"
    }

    private fun probeSingleUrl(raw: String, isPlaylistUrl: Boolean = false): String? {
        val clean = raw.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return "URL non HTTP"
        return try {
            val headReq = Request.Builder().url(clean).head().header("User-Agent", CHROME_USER_AGENT).build()
            val headCode = probeClient.newCall(headReq).execute().use { it.code }
            if (headCode in 200..399) return null
            val isManifest = isPlaylistUrl || clean.substringBefore('?').lowercase(java.util.Locale.ROOT).let {
                it.endsWith(".m3u8") || it.endsWith(".m3u")
            }
            val getReqBuilder = Request.Builder()
                .url(clean)
                .get()
                .header("User-Agent", CHROME_USER_AGENT)
            if (!isManifest) {
                getReqBuilder.header("Range", "bytes=0-1024")
            }
            val getCode = probeClient.newCall(getReqBuilder.build()).execute().use { it.code }
            if (getCode in 200..399) null else "HTTP $getCode"
        } catch (e: Exception) {
            e.javaClass.simpleName
        }
    }

    private companion object {
        const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val VLC_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
    }

    private fun resolveRawFileName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = c.getString(idx)?.trim()
                    if (!name.isNullOrEmpty()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "playlist.m3u" }
    }

    private fun resolveFileName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = c.getString(idx)?.substringBeforeLast('.')?.trim()
                    if (!name.isNullOrEmpty()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank { "File M3U" }
            ?: "File M3U"
    }
}
