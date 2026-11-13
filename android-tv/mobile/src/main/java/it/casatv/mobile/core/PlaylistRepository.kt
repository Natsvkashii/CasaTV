package it.casatv.mobile.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import it.casatv.mobile.data.LocalPlaylist
import it.casatv.mobile.data.MobileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
            require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                "L'URL deve iniziare con http:// o https://"
            }
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "CasaTV-Mobile/1.0")
                .build()
            val body = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("Download fallito (HTTP ${resp.code})")
                }
                resp.body?.string() ?: error("Risposta vuota")
            }

            val playlistId = UUID.randomUUID().toString().take(12)
            val channels = M3uParser.parse(body, playlistId)
            require(channels.isNotEmpty()) {
                "Nessun canale valido trovato nel file M3U"
            }

            val finalName = name.trim().ifEmpty {
                Uri.parse(cleanUrl).lastPathSegment?.substringBeforeLast('.')?.ifBlank { "Lista M3U" }
                    ?: "Lista M3U"
            }
            val playlist = LocalPlaylist(
                id = playlistId,
                name = finalName,
                source = cleanUrl,
                isLocalFile = false,
                enabled = true,
                channelCount = channels.size,
                updatedAt = System.currentTimeMillis(),
            )
            db.upsertPlaylistAndChannels(playlist, channels)
            playlist
        }
    }

    suspend fun importFromUri(uri: Uri, customName: String? = null): Result<LocalPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Impossibile leggere il file selezionato")

            val fileName = customName?.trim()?.takeIf { it.isNotEmpty() } ?: resolveFileName(uri)
            val playlistId = UUID.randomUUID().toString().take(12)
            val channels = M3uParser.parse(text, playlistId)
            require(channels.isNotEmpty()) {
                "Nessun canale valido trovato nel file M3U"
            }

            val playlist = LocalPlaylist(
                id = playlistId,
                name = fileName,
                source = uri.toString(),
                isLocalFile = true,
                enabled = true,
                channelCount = channels.size,
                updatedAt = System.currentTimeMillis(),
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
            )
            db.upsertPlaylistAndChannels(playlist, channels)
            playlist
        }
    }

    suspend fun refreshUrlPlaylist(playlist: LocalPlaylist): Result<LocalPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            require(!playlist.isLocalFile) { "I file locali vanno reimportati manualmente" }
            val request = Request.Builder()
                .url(playlist.source)
                .header("User-Agent", "CasaTV-Mobile/1.0")
                .build()
            val body = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string() ?: error("Risposta vuota")
            }
            val channels = M3uParser.parse(body, playlist.id)
            require(channels.isNotEmpty()) { "Nessun canale trovato" }
            val updated = playlist.copy(
                channelCount = channels.size,
                updatedAt = System.currentTimeMillis(),
            )
            db.upsertPlaylistAndChannels(updated, channels)
            updated
        }
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
