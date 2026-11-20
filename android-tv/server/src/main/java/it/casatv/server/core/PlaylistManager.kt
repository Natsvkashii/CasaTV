package it.casatv.server.core

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
    private val baseClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val downloadClient = baseClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    private val probeClient = baseClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

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
                lastError = diagnoseEmptyPlaylistError(text),
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
                lastError = diagnoseEmptyPlaylistError(text),
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
        val samples = db.sampleChannelsForPlaylist(id, 6)
        if (samples.isEmpty()) {
            if (p.url.isNotBlank()) {
                return@withContext refreshPlaylist(id)
            }
            val err = p.lastError.ifBlank { "Nessun canale valido nella playlist" }
            db.updatePlaylistStatus(id, "error", err)
            return@withContext db.getPlaylist(id)
        }
        var (status, lastError) = probeSampleStreams(samples, totalChannels = p.channelCount.coerceAtLeast(samples.size))
        if (status != "ok" && p.url.isNotBlank()) {
            val playlistUrlCheck = probeSingleStream(p.url, isPlaylistUrl = true)
            if (playlistUrlCheck != null) {
                status = "error"
                lastError = "Link playlist non raggiungibile ($playlistUrlCheck)"
            }
        }
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
            db.updatePlaylistStatus(id, "error", diagnoseEmptyPlaylistError(text))
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
        val (status, lastError) = probeSampleStreams(channels)
        val entry = PlaylistEntry(
            id = playlistId,
            name = source.ifBlank { "Playlist Principale" },
            url = url,
            channelCount = channels.size,
            enabled = true,
            status = status,
            lastError = lastError,
        )
        db.addChannelsForPlaylist(entry, channels)
        Prefs.savePlaylist(app, url, source, System.currentTimeMillis() / 1000)
        return PlaylistImportResult(count = channels.size, source = source)
    }

    private fun download(url: String): String {
        val first = executeDownload(url, CHROME_USER_AGENT)
        val text = if (first.code == 403 || first.code == 406) {
            executeDownload(url, VLC_USER_AGENT).also {
                if (it.code !in 200..299) throw IOException("HTTP ${it.code}")
            }.text
        } else {
            if (first.code !in 200..299) throw IOException("HTTP ${first.code}")
            first.text
        }
        return text
    }

    private data class DownloadResult(val code: Int, val text: String)

    private fun executeDownload(url: String, userAgent: String): DownloadResult {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "audio/x-mpegurl, application/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
            .build()
        downloadClient.newCall(req).execute().use { res ->
            val code = res.code
            if (code !in 200..299) {
                return DownloadResult(code, "")
            }
            val body = res.body ?: return DownloadResult(code, "")
            val len = body.contentLength()
            if (len > MAX_BYTES) throw IOException("Playlist troppo grande")
            val text = body.byteStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                val out = ByteArrayOutputStream()
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
            return DownloadResult(code, text)
        }
    }

    private fun diagnoseEmptyPlaylistError(text: String): String {
        val trimmed = text.trimStart()
        if (trimmed.isEmpty()) return "Playlist vuota o non leggibile"
        val sample = trimmed.take(4096).lowercase(Locale.ROOT)
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
        return "Nessun canale IPTV valido trovato (verifica il formato M3U/M3U8/PLS)"
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

    private fun probeSampleStreams(
        channels: List<IptvChannel>,
        totalChannels: Int = channels.size,
    ): Pair<String, String> {
        if (channels.isEmpty()) return "error" to "Nessun canale nella playlist"
        val preferred = channels.filter { ch ->
            val u = ch.url.substringBefore('?').lowercase(Locale.ROOT)
            !u.endsWith(".mp4") && !u.endsWith(".jpg") && !u.endsWith(".png")
        }
        val pool = if (preferred.isNotEmpty()) preferred else channels
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
        var lastReason = ""
        for (ch in samples) {
            val codeOrErr = probeSingleStream(ch.url)
            if (codeOrErr == null) {
                return "ok" to ""
            }
            lastReason = codeOrErr
        }
        return "warn" to "Playlist valida ($totalChannels canali), ma i flussi di prova non rispondono ($lastReason)"
    }

    private fun probeSingleStream(rawUrl: String, isPlaylistUrl: Boolean = false): String? {
        val clean = rawUrl.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return "URL non HTTP"
        return try {
            val headReq = Request.Builder()
                .url(clean)
                .head()
                .header("User-Agent", CHROME_USER_AGENT)
                .build()
            val headCode = probeClient.newCall(headReq).execute().use { it.code }
            if (headCode in 200..399) return null

            val isManifest = isPlaylistUrl || clean.substringBefore('?').lowercase(Locale.ROOT).endsWith(".m3u8") ||
                clean.substringBefore('?').lowercase(Locale.ROOT).endsWith(".m3u")
            val getReqBuilder = Request.Builder()
                .url(clean)
                .get()
                .header("User-Agent", CHROME_USER_AGENT)
            if (!isManifest) {
                getReqBuilder.header("Range", "bytes=0-1024")
            }
            val getCode = probeClient.newCall(getReqBuilder.build()).execute().use { it.code }
            if (getCode in 200..399) null else "HTTP $getCode"
        } catch (error: Exception) {
            error.javaClass.simpleName
        }
    }

    private companion object {
        const val MAX_BYTES = 64L * 1024 * 1024
        const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val VLC_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
