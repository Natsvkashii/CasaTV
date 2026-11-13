package it.casatv.server.core

import android.content.Context
import android.net.Uri
import it.casatv.server.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PlaylistError { Empty, InvalidUrl, Download }

class PlaylistException(val reason: PlaylistError, cause: Throwable? = null) :
    Exception(reason.name, cause)

/**
 * Downloads and stores M3U playlists. Every import replaces the previous one, so
 * refreshing a playlist also removes channels the provider dropped.
 */
class PlaylistManager(context: Context) {

    private val app = context.applicationContext
    private val db = ServerDatabase.get(app)

    fun status(): PlaylistStatus = PlaylistStatus(
        count = db.channelCount(),
        source = Prefs.playlistSource(app),
        url = Prefs.playlistUrl(app),
        lastImport = Prefs.playlistLastImport(app),
        categories = db.channelCountsByCategory(),
    )

    fun listPlaylists(): List<PlaylistEntry> = db.listPlaylists()

    suspend fun addPlaylist(name: String, rawUrl: String): PlaylistEntry = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw PlaylistException(PlaylistError.InvalidUrl)
        }
        val text = try {
            download(url)
        } catch (error: Exception) {
            throw PlaylistException(PlaylistError.Download, error)
        }
        val cleanName = name.trim().ifBlank {
            url.substringAfterLast('/').substringBefore('?').ifBlank { "Playlist ${db.listPlaylists().size + 1}" }
        }
        val playlistId = "pl_" + java.util.UUID.randomUUID().toString().take(8)
        val channels = M3uParser.parse(text, playlistId)
        if (channels.isEmpty()) throw PlaylistException(PlaylistError.Empty)
        val entry = PlaylistEntry(
            id = playlistId,
            name = cleanName,
            url = url,
            channelCount = channels.size,
            enabled = true,
        )
        db.addChannelsForPlaylist(entry, channels)
        entry
    }

    suspend fun refreshPlaylist(id: String): PlaylistEntry? = withContext(Dispatchers.IO) {
        val p = db.getPlaylist(id) ?: return@withContext null
        if (p.url.isBlank()) return@withContext null
        val text = try {
            download(p.url)
        } catch (error: Exception) {
            throw PlaylistException(PlaylistError.Download, error)
        }
        val channels = M3uParser.parse(text, p.id)
        db.addChannelsForPlaylist(p.copy(channelCount = channels.size), channels)
        db.getPlaylist(id)
    }

    fun deletePlaylist(id: String) {
        db.deletePlaylist(id)
    }

    fun togglePlaylist(id: String, enabled: Boolean) {
        db.togglePlaylist(id, enabled)
    }

    suspend fun importUrl(rawUrl: String): PlaylistImportResult = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw PlaylistException(PlaylistError.InvalidUrl)
        }
        val text = try {
            download(url)
        } catch (error: Exception) {
            throw PlaylistException(PlaylistError.Download, error)
        }
        store(text, url = url, source = url)
    }

    suspend fun importUri(uri: Uri): PlaylistImportResult = withContext(Dispatchers.IO) {
        val name = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "playlist.m3u" }
        val text = try {
            app.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IOException("Stream non disponibile")
        } catch (error: Exception) {
            throw PlaylistException(PlaylistError.Download, error)
        }
        store(text, url = "", source = name)
    }

    /** Used by the HTTP upload endpoint, which receives a temp file. */
    suspend fun importFile(file: java.io.File, name: String): PlaylistImportResult =
        withContext(Dispatchers.IO) {
            val text = try {
                file.readText(Charsets.UTF_8)
            } catch (error: Exception) {
                throw PlaylistException(PlaylistError.Download, error)
            }
            store(text, url = "", source = name.ifBlank { file.name })
        }

    /** Re-downloads the last URL. Returns null when the playlist came from a file. */
    suspend fun refresh(): PlaylistImportResult? {
        val url = Prefs.playlistUrl(app)
        if (url.isBlank()) return null
        return importUrl(url)
    }

    fun clear() {
        db.clearChannels()
        Prefs.clearPlaylist(app)
    }

    private fun store(text: String, url: String, source: String): PlaylistImportResult {
        val playlistId = "pl_default"
        val channels = M3uParser.parse(text, playlistId)
        if (channels.isEmpty()) throw PlaylistException(PlaylistError.Empty)
        val entry = PlaylistEntry(
            id = playlistId,
            name = source.ifBlank { "Playlist Principale" },
            url = url,
            channelCount = channels.size,
            enabled = true,
        )
        db.addChannelsForPlaylist(entry, channels)
        Prefs.savePlaylist(app, url, source, System.currentTimeMillis() / 1000)
        return PlaylistImportResult(count = channels.size, source = source)
    }

    private fun download(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "CasaTV-Server/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "audio/x-mpegurl, application/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val length = connection.contentLengthLong
            if (length > MAX_BYTES) throw IOException("Playlist troppo grande")
            return connection.inputStream.use { stream ->
                val buffer = ByteArray(64 * 1024)
                val out = java.io.ByteArrayOutputStream()
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) throw IOException("Playlist troppo grande")
                    out.write(buffer, 0, read)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_BYTES = 64L * 1024 * 1024
    }
}
