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
        val playlistId = "pl_" + java.util.UUID.randomUUID().toString().take(8)
        val cleanName = name.trim().ifBlank {
            url.substringAfterLast('/').substringBefore('?').ifBlank { "Playlist ${db.listPlaylists().size + 1}" }
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val failed = PlaylistEntry(
                id = playlistId,
                name = cleanName,
                url = url,
                channelCount = 0,
                enabled = true,
                status = "error",
                lastError = "Indirizzo non valido (deve iniziare con http:// o https://)",
            )
            db.savePlaylistRecord(failed)
            return@withContext failed
        }
        val text = try {
            download(url)
        } catch (error: Exception) {
            val failed = PlaylistEntry(
                id = playlistId,
                name = cleanName,
                url = url,
                channelCount = 0,
                enabled = true,
                status = "error",
                lastError = "Download fallito: ${error.message ?: error.javaClass.simpleName}",
            )
            db.savePlaylistRecord(failed)
            return@withContext failed
        }
        val channels = M3uParser.parse(text, playlistId)
        if (channels.isEmpty()) {
            val failed = PlaylistEntry(
                id = playlistId,
                name = cleanName,
                url = url,
                channelCount = 0,
                enabled = true,
                status = "error",
                lastError = "Nessun canale IPTV valido trovato (verifica il formato M3U/M3U8/PLS)",
            )
            db.savePlaylistRecord(failed)
            return@withContext failed
        }
        val (status, lastError) = probeSampleStreams(channels)
        val entry = PlaylistEntry(
            id = playlistId,
            name = cleanName,
            url = url,
            channelCount = channels.size,
            enabled = true,
            status = status,
            lastError = lastError,
        )
        db.addChannelsForPlaylist(entry, channels)
        entry
    }

    suspend fun addPlaylistFromText(
        name: String,
        text: String,
        sourceLabel: String = "",
    ): PlaylistEntry = withContext(Dispatchers.IO) {
        val playlistId = "pl_" + java.util.UUID.randomUUID().toString().take(8)
        val cleanName = name.trim().ifBlank {
            sourceLabel.trim().ifBlank { "File M3U ${db.listPlaylists().size + 1}" }
        }
        val channels = if (text.isBlank()) emptyList() else M3uParser.parse(text, playlistId)
        if (channels.isEmpty()) {
            val failed = PlaylistEntry(
                id = playlistId,
                name = cleanName,
                url = "",
                channelCount = 0,
                enabled = true,
                status = "error",
                lastError = "Nessun canale IPTV valido trovato nel file (richiesto #EXTM3U / #EXTINF o PLS con stream http/https)",
            )
            db.savePlaylistRecord(failed)
            return@withContext failed
        }
        val (status, lastError) = probeSampleStreams(channels)
        val entry = PlaylistEntry(
            id = playlistId,
            name = cleanName,
            url = "",
            channelCount = channels.size,
            enabled = true,
            status = status,
            lastError = lastError,
        )
        db.addChannelsForPlaylist(entry, channels)
        entry
    }

    suspend fun addPlaylistFromFile(file: java.io.File, name: String): PlaylistEntry =
        withContext(Dispatchers.IO) {
            val displayName = name.trim().ifBlank { file.name }
            val text = try {
                file.readText(Charsets.UTF_8)
            } catch (error: Exception) {
                val failed = PlaylistEntry(
                    id = "pl_" + java.util.UUID.randomUUID().toString().take(8),
                    name = displayName,
                    url = "",
                    channelCount = 0,
                    enabled = true,
                    status = "error",
                    lastError = "Impossibile leggere il file: ${error.message ?: error.javaClass.simpleName}",
                )
                db.savePlaylistRecord(failed)
                return@withContext failed
            }
            addPlaylistFromText(displayName, text, displayName)
        }

    suspend fun addPlaylistFromUri(uri: Uri): PlaylistEntry = withContext(Dispatchers.IO) {
        val name = resolveDisplayName(uri)
        val text = try {
            app.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IOException("Stream non disponibile")
        } catch (error: Exception) {
            val failed = PlaylistEntry(
                id = "pl_" + java.util.UUID.randomUUID().toString().take(8),
                name = name,
                url = "",
                channelCount = 0,
                enabled = true,
                status = "error",
                lastError = "Impossibile leggere il file: ${error.message ?: error.javaClass.simpleName}",
            )
            db.savePlaylistRecord(failed)
            return@withContext failed
        }
        addPlaylistFromText(name, text, name)
    }

    suspend fun checkPlaylist(id: String): PlaylistEntry? = withContext(Dispatchers.IO) {
        val p = db.getPlaylist(id) ?: return@withContext null
        val samples = db.sampleChannelsForPlaylist(id, 3)
        if (samples.isEmpty() || p.channelCount <= 0) {
            val err = p.lastError.ifBlank { "Nessun canale valido nella playlist" }
            db.updatePlaylistStatus(id, "error", err)
            return@withContext db.getPlaylist(id)
        }
        val (status, lastError) = probeSampleStreams(samples)
        db.updatePlaylistStatus(id, status, lastError)
        db.getPlaylist(id)
    }

    suspend fun refreshPlaylist(id: String): PlaylistEntry? = withContext(Dispatchers.IO) {
        val p = db.getPlaylist(id) ?: return@withContext null
        if (p.url.isBlank()) return@withContext checkPlaylist(id)
        val text = try {
            download(p.url)
        } catch (error: Exception) {
            db.updatePlaylistStatus(id, "error", "Download fallito: ${error.message ?: error.javaClass.simpleName}")
            return@withContext db.getPlaylist(id)
        }
        val channels = M3uParser.parse(text, p.id)
        if (channels.isEmpty()) {
            db.updatePlaylistStatus(id, "error", "Nessun canale IPTV valido trovato durante l'aggiornamento")
            return@withContext db.getPlaylist(id)
        }
        val (status, lastError) = probeSampleStreams(channels)
        db.addChannelsForPlaylist(p.copy(channelCount = channels.size, status = status, lastError = lastError), channels)
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

    private fun resolveDisplayName(uri: Uri): String {
        runCatching {
            app.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val n = c.getString(idx)?.trim()
                        if (!n.isNullOrEmpty()) return n
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "playlist.m3u" }
    }

    private fun probeSampleStreams(channels: List<IptvChannel>): Pair<String, String> {
        if (channels.isEmpty()) return "error" to "Nessun canale nella playlist"
        val samples = if (channels.size <= 3) {
            channels
        } else {
            listOf(channels[0], channels[channels.size / 2], channels[channels.size - 1])
        }
        var lastReason = ""
        for (ch in samples) {
            val codeOrErr = probeSingleStream(ch.url)
            if (codeOrErr == null) {
                return "ok" to ""
            }
            lastReason = codeOrErr
        }
        return "error" to "Flussi non raggiungibili ($lastReason)"
    }

    private fun probeSingleStream(rawUrl: String): String? {
        val clean = rawUrl.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return "URL non HTTP"
        return try {
            val headConn = (URL(clean).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 4000
                instanceFollowRedirects = true
                requestMethod = "HEAD"
                setRequestProperty("User-Agent", "Mozilla/5.0 CasaTV/1.0")
            }
            val code = try {
                headConn.responseCode
            } finally {
                headConn.disconnect()
            }
            if (code in 200..399) return null
            val getConn = (URL(clean).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 4000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 CasaTV/1.0")
                setRequestProperty("Range", "bytes=0-1024")
            }
            val getCode = try {
                getConn.responseCode
            } finally {
                getConn.disconnect()
            }
            if (getCode in 200..399) null else "HTTP $getCode"
        } catch (error: Exception) {
            error.javaClass.simpleName
        }
    }

    private companion object {
        const val MAX_BYTES = 64L * 1024 * 1024
    }
}
