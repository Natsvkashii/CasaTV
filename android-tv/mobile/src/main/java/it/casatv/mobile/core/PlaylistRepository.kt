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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(4000, TimeUnit.MILLISECONDS)
        .callTimeout(5000, TimeUnit.MILLISECONDS)
        .followRedirects(true)
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
                val request = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", "CasaTV-Mobile/1.0")
                    .build()
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("Download fallito (HTTP ${resp.code})")
                    resp.body?.string() ?: error("Risposta vuota")
                }
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
                    lastError = "Nessun canale IPTV valido trovato (#EXTM3U / PLS)",
                )
                db.savePlaylistRecord(failed)
                return@runCatching failed
            }

            val (status, lastError) = probeSampleStreams(channels.map { it.streamUrl })
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
                    lastError = "Nessun canale IPTV valido trovato nel file (#EXTINF / PLS)",
                )
                db.savePlaylistRecord(failed)
                return@runCatching failed
            }

            val (status, lastError) = probeSampleStreams(channels.map { it.streamUrl })
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
                val request = Request.Builder()
                    .url(playlist.source)
                    .header("User-Agent", "CasaTV-Mobile/1.0")
                    .build()
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.body?.string() ?: error("Risposta vuota")
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Errore aggiornamento"
                db.updatePlaylistStatus(playlist.id, "error", msg)
                error(msg)
            }
            val channels = M3uParser.parse(body, playlist.id)
            if (channels.isEmpty()) {
                val msg = "Nessun canale trovato"
                db.updatePlaylistStatus(playlist.id, "error", msg)
                error(msg)
            }
            val (status, lastError) = probeSampleStreams(channels.map { it.streamUrl })
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
            val sampleUrls = db.sampleChannelUrls(playlist.id, 3)
            if (sampleUrls.isEmpty() || playlist.channelCount <= 0) {
                val err = playlist.lastError.ifBlank { "Nessun canale valido nella playlist" }
                db.updatePlaylistStatus(playlist.id, "error", err)
                return@runCatching playlist.copy(status = "error", lastError = err, updatedAt = System.currentTimeMillis())
            }
            val (status, lastError) = probeSampleStreams(sampleUrls)
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

    private fun probeSampleStreams(urls: List<String>): Pair<String, String> {
        if (urls.isEmpty()) return "error" to "Nessun canale nella playlist"
        val samples = if (urls.size <= 3) urls else listOf(urls[0], urls[urls.size / 2], urls.last())
        var lastErr = ""
        for (raw in samples) {
            val clean = raw.trim()
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                lastErr = "URL non HTTP"
                continue
            }
            val ok = try {
                val headReq = Request.Builder().url(clean).head().header("User-Agent", "Mozilla/5.0 CasaTV/1.0").build()
                val headCode = probeClient.newCall(headReq).execute().use { it.code }
                if (headCode in 200..399) {
                    true
                } else {
                    val getReq = Request.Builder()
                        .url(clean)
                        .get()
                        .header("User-Agent", "Mozilla/5.0 CasaTV/1.0")
                        .header("Range", "bytes=0-1024")
                        .build()
                    val getCode = probeClient.newCall(getReq).execute().use { it.code }
                    if (getCode in 200..399) {
                        true
                    } else {
                        lastErr = "HTTP $getCode"
                        false
                    }
                }
            } catch (e: Exception) {
                lastErr = e.javaClass.simpleName
                false
            }
            if (ok) return "ok" to ""
        }
        return "error" to "Flussi non raggiungibili ($lastErr)"
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
