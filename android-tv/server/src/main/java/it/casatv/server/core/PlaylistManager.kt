package it.casatv.server.core

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(600, TimeUnit.SECONDS)
        .build()
    private val probeClient = baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
    private val mixProbeClient = baseClient.newBuilder()
        .connectTimeout(1800, TimeUnit.MILLISECONDS)
        .readTimeout(1800, TimeUnit.MILLISECONDS)
        .callTimeout(2800, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
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
        val existingByUrl = db.findPlaylistByUrl(url)
        val playlistId = existingByUrl?.id ?: ("pl_" + java.util.UUID.randomUUID().toString().take(8))
        val cleanName = name.trim().ifBlank {
            existingByUrl?.name?.takeIf { it.isNotBlank() }
                ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "Playlist ${db.listPlaylists().size + 1}" }
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
        val dlFile = try {
            downloadToTempFile(url)
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
        try {
            val entry = ingestStreamingFile(
                file = dlFile,
                playlistId = playlistId,
                name = cleanName,
                url = url,
            )
            entry
        } finally {
            runCatching { dlFile.delete() }
        }
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

    private fun readHeadSample(file: java.io.File, maxBytes: Int = 8192): String {
        return runCatching {
            file.inputStream().use { input ->
                val buf = ByteArray(maxBytes)
                val read = input.read(buf)
                if (read <= 0) "" else String(buf, 0, read, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    private fun copyStreamToTempFile(
        input: java.io.InputStream,
        prefix: String = "pl_in_",
        destDir: java.io.File = app.cacheDir,
        contentLength: Long = -1L,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): java.io.File {
        val temp = java.io.File.createTempFile(prefix, ".m3u", destDir)
        try {
            var total = 0L
            var lastNotify = 0L
            temp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    if (read > 0) {
                        out.write(buf, 0, read)
                        total += read
                        if (total - lastNotify >= 256L * 1024L) {
                            lastNotify = total
                            onProgress(total, contentLength)
                        }
                    }
                }
            }
            onProgress(total, contentLength)
            return temp
        } catch (t: Throwable) {
            runCatching { temp.delete() }
            throw t
        }
    }

    private fun ingestStreamingFile(
        file: java.io.File,
        playlistId: String,
        name: String,
        url: String,
    ): PlaylistEntry {
        val fingerprint = PlaylistImportEngine.computeFileFingerprint(file)
        val seed = PlaylistEntry(
            id = playlistId,
            name = name,
            url = url,
            channelCount = 0,
            enabled = true,
            status = "importing",
            lastError = "Importazione in corso…",
            contentHash = fingerprint,
        )
        val (_, initialNum) = db.preparePlaylistImport(seed)
        var nextNum = initialNum
        var total = 0
        M3uParser.streamParse(file, playlistId, batchSize = 2500, fastCountryOnly = false) { batch, _ ->
            val (linked, nextN) = db.upsertChannelBatch(playlistId, batch, total, nextNum)
            nextNum = nextN
            total += linked
        }
        if (total == 0) {
            val err = diagnoseEmptyPlaylistError(readHeadSample(file))
            db.finalizePlaylistImport(playlistId, 0, "error", err)
            return db.getPlaylist(playlistId) ?: seed.copy(status = "error", lastError = err)
        }
        val (status, lastError) = probeSamplesForPlaylist(playlistId, total)
        db.finalizePlaylistImport(playlistId, total, status, lastError)
        return db.getPlaylist(playlistId) ?: seed.copy(channelCount = total, status = status, lastError = lastError)
    }

    fun probeSamplesForPlaylist(playlistId: String, totalChannels: Int): Pair<String, String> {
        val samples = db.sampleChannelsForPlaylist(playlistId, 16)
        if (samples.isEmpty()) return "error" to "Nessun canale nella playlist"
        return probeSampleStreams(samples, totalChannels = totalChannels.coerceAtLeast(samples.size))
    }

    suspend fun addPlaylistFromFile(file: java.io.File, name: String): PlaylistEntry =
        withContext(Dispatchers.IO) {
            val displayName = name.trim().ifBlank { file.name }
            val cleanName = displayName.trim().ifBlank { "File M3U ${db.listPlaylists().size + 1}" }
            val fingerprint = PlaylistImportEngine.computeFileFingerprint(file)
            db.findPlaylistByContentHash(fingerprint)?.let { return@withContext it }
            val existingByName = db.findUploadedPlaylistByName(cleanName)
            val playlistId = existingByName?.id ?: ("pl_" + java.util.UUID.randomUUID().toString().take(8))
            try {
                ingestStreamingFile(file, playlistId, cleanName, "")
            } catch (error: Exception) {
                val msg = "Impossibile leggere il file: ${error.message ?: error.javaClass.simpleName}"
                db.finalizePlaylistImport(playlistId, 0, "error", msg)
                PlaylistEntry(
                    id = playlistId,
                    name = cleanName,
                    url = "",
                    channelCount = 0,
                    enabled = true,
                    status = "error",
                    lastError = msg,
                )
            }
        }

    suspend fun addPlaylistFromUri(uri: Uri): PlaylistEntry = withContext(Dispatchers.IO) {
        val name = resolveDisplayName(uri)
        val engine = PlaylistImportEngine.get(app)
        val temp = try {
            app.contentResolver.openInputStream(uri)?.use { stream ->
                copyStreamToTempFile(stream, "pl_uri_", destDir = engine.importsDir())
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
        if (temp.length() > 16L * 1024L * 1024L) {
            val job = engine.enqueueFile(temp, name)
            return@withContext db.getPlaylist(job.playlistId) ?: PlaylistEntry(
                id = job.playlistId,
                name = name,
                url = "",
                channelCount = 0,
                enabled = true,
                status = "importing",
                lastError = job.statusMessage,
            )
        }
        try {
            addPlaylistFromFile(temp, name)
        } finally {
            runCatching { temp.delete() }
        }
    }

    suspend fun checkPlaylist(id: String): PlaylistEntry? = withContext(Dispatchers.IO) {
        val p = db.getPlaylist(id) ?: return@withContext null
        val samples = db.sampleChannelsForPlaylist(id, 16)
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
        val dlFile = try {
            downloadToTempFile(p.url)
        } catch (error: Exception) {
            db.updatePlaylistStatus(id, "error", "Download fallito: ${error.message ?: error.javaClass.simpleName}")
            return@withContext db.getPlaylist(id)
        }
        try {
            return@withContext ingestStreamingFile(dlFile, p.id, p.name, p.url)
        } finally {
            runCatching { dlFile.delete() }
        }
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
        val dlFile = try {
            downloadToTempFile(url)
        } catch (error: Exception) {
            throw PlaylistException(PlaylistError.Download, error)
        }
        try {
            storeFile(dlFile, url = url, source = url)
        } finally {
            runCatching { dlFile.delete() }
        }
    }

    suspend fun importUri(uri: Uri): PlaylistImportResult = withContext(Dispatchers.IO) {
        val name = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "playlist.m3u" }
        val temp = try {
            app.contentResolver.openInputStream(uri)?.use { stream ->
                copyStreamToTempFile(stream, "pl_imp_")
            } ?: throw IOException("Stream non disponibile")
        } catch (error: Exception) {
            throw PlaylistException(PlaylistError.Download, error)
        }
        try {
            storeFile(temp, url = "", source = name)
        } finally {
            runCatching { temp.delete() }
        }
    }

    /** Used by the HTTP upload endpoint, which receives a temp file. */
    suspend fun importFile(file: java.io.File, name: String): PlaylistImportResult =
        withContext(Dispatchers.IO) {
            try {
                storeFile(file, url = "", source = name.ifBlank { file.name })
            } catch (error: Exception) {
                if (error is PlaylistException) throw error
                throw PlaylistException(PlaylistError.Download, error)
            }
        }

    /** Re-downloads the last URL. Returns null when the playlist came from a file. */
    suspend fun refresh(): PlaylistImportResult? {
        val url = Prefs.playlistUrl(app)
        if (url.isBlank()) return null
        return importUrl(url)
    }

    suspend fun refreshAllEnabled(): Int = withContext(Dispatchers.IO) {
        val enabledRemote = db.listPlaylists().filter {
            it.enabled && (it.url.startsWith("http://", ignoreCase = true) || it.url.startsWith("https://", ignoreCase = true))
        }
        if (enabledRemote.isEmpty()) {
            return@withContext refresh()?.count ?: 0
        }
        var updated = 0
        for (entry in enabledRemote) {
            runCatching { refreshPlaylist(entry.id) }
                .onSuccess { if (it != null && it.channelCount > 0) updated++ }
        }
        if (updated > 0) {
            Prefs.savePlaylist(app, Prefs.playlistUrl(app), Prefs.playlistSource(app), System.currentTimeMillis() / 1000)
        }
        updated
    }

    fun clear() {
        db.clearChannelsAndPlaylists()
        Prefs.clearPlaylist(app)
    }

    private fun storeFile(file: java.io.File, url: String, source: String): PlaylistImportResult {
        val playlistId = "pl_default"
        val entry = ingestStreamingFile(
            file = file,
            playlistId = playlistId,
            name = source.ifBlank { "Playlist Principale" },
            url = url,
        )
        if (entry.channelCount == 0) throw PlaylistException(PlaylistError.Empty)
        Prefs.savePlaylist(app, url, source, System.currentTimeMillis() / 1000)
        return PlaylistImportResult(count = entry.channelCount, source = source)
    }

    fun downloadUrlToTempFile(
        url: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): java.io.File {
        val first = executeDownloadToFile(url, CHROME_USER_AGENT, onProgress)
        if (first.code == 403 || first.code == 406) {
            first.file?.let { runCatching { it.delete() } }
            val second = executeDownloadToFile(url, VLC_USER_AGENT, onProgress)
            if (second.code !in 200..299 || second.file == null) {
                second.file?.let { runCatching { it.delete() } }
                throw IOException("HTTP ${second.code}")
            }
            return second.file
        }
        if (first.code !in 200..299 || first.file == null) {
            first.file?.let { runCatching { it.delete() } }
            throw IOException("HTTP ${first.code}")
        }
        return first.file
    }

    private fun downloadToTempFile(url: String): java.io.File =
        downloadUrlToTempFile(url)

    private data class DownloadFileResult(val code: Int, val file: java.io.File?)

    private fun executeDownloadToFile(
        url: String,
        userAgent: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): DownloadFileResult {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "audio/x-mpegurl, application/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
            .build()
        downloadClient.newCall(req).execute().use { res ->
            val code = res.code
            if (code !in 200..299) {
                return DownloadFileResult(code, null)
            }
            val body = res.body ?: return DownloadFileResult(code, null)
            val contentLength = body.contentLength()
            val temp = body.byteStream().use { stream ->
                copyStreamToTempFile(stream, "pl_dl_", contentLength = contentLength, onProgress = onProgress)
            }
            return DownloadFileResult(code, temp)
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
            val raw = ch.url.lowercase(Locale.ROOT)
            val path = raw.substringBefore('?')
            !path.endsWith(".mp4") &&
                !path.endsWith(".mkv") &&
                !path.endsWith(".avi") &&
                !path.endsWith(".mov") &&
                !path.endsWith(".m4v") &&
                !path.endsWith(".jpg") &&
                !path.endsWith(".jpeg") &&
                !path.endsWith(".png") &&
                !path.endsWith(".webp") &&
                !raw.contains("/movie/") &&
                !raw.contains("/series/")
        }
        val rawPool = if (preferred.isNotEmpty()) preferred else channels
        val distinctHostFirst = ArrayList<IptvChannel>(rawPool.size)
        val remainder = ArrayList<IptvChannel>(rawPool.size)
        val seenHosts = HashSet<String>()
        for (ch in rawPool) {
            val host = runCatching { URI(ch.url.trim()).host?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
            if (host.isNotBlank() && seenHosts.add(host)) {
                distinctHostFirst += ch
            } else {
                remainder += ch
            }
        }
        val pool = distinctHostFirst + remainder
        val targetCount = 16
        val samples = if (pool.size <= targetCount) {
            pool
        } else {
            val hostCandidates = distinctHostFirst.take(targetCount)
            if (hostCandidates.size >= targetCount) {
                hostCandidates
            } else {
                val chosen = LinkedHashSet<IptvChannel>(hostCandidates)
                val remNeeded = targetCount - chosen.size
                val remList = remainder
                if (remList.size <= remNeeded) {
                    chosen.addAll(remList)
                } else {
                    val step = (remList.size - 1).coerceAtLeast(1)
                    for (i in 0 until remNeeded) {
                        chosen += remList[(i * step) / (remNeeded - 1).coerceAtLeast(1)]
                    }
                }
                chosen.toList()
            }
        }
        val foundOk = AtomicBoolean(false)
        val lastReason = AtomicReference("")
        val sem = Semaphore(4)
        runBlocking(Dispatchers.IO) {
            supervisorScope {
                val jobs = samples.map { ch ->
                    async {
                        if (foundOk.get()) return@async
                        sem.withPermit {
                            if (foundOk.get()) return@withPermit
                            val err = try {
                                probeSingleStream(ch.url)
                            } catch (ce: CancellationException) {
                                throw ce
                            }
                            if (err == null) {
                                if (foundOk.compareAndSet(false, true)) {
                                    this@supervisorScope.coroutineContext.cancelChildren()
                                }
                            } else {
                                lastReason.compareAndSet("", err)
                                lastReason.set(err)
                            }
                        }
                    }
                }
                for (job in jobs) {
                    runCatching { job.await() }
                }
            }
        }
        if (foundOk.get()) {
            return "ok" to ""
        }
        val reason = lastReason.get().ifBlank { "timeout" }
        return "warn" to "Playlist valida ($totalChannels canali), ma i flussi di prova non rispondono ($reason)"
    }

    fun probeMixCandidates(
        candidates: List<Pair<String, String>>,
        maxWinners: Int = 3,
    ): Pair<List<String>, List<String>> {
        val batch = candidates.take(12)
        if (batch.isEmpty()) return emptyList<String>() to emptyList()
        val goal = maxWinners.coerceIn(1, 5)
        val winners = java.util.Collections.synchronizedList(mutableListOf<String>())
        val dead = java.util.Collections.synchronizedList(mutableListOf<String>())
        val sem = Semaphore(6)
        runBlocking(Dispatchers.IO) {
            supervisorScope {
                val jobs = batch.map { (id, rawUrl) ->
                    async {
                        if (winners.size >= goal) return@async
                        sem.withPermit {
                            if (winners.size >= goal) return@withPermit
                            val ok = try {
                                probeMixSingleStream(rawUrl)
                            } catch (ce: CancellationException) {
                                throw ce
                            }
                            if (ok) {
                                val added = synchronized(winners) {
                                    if (winners.size < goal && !winners.contains(id)) {
                                        winners.add(id)
                                        true
                                    } else {
                                        false
                                    }
                                }
                                if (added && winners.size >= goal) {
                                    this@supervisorScope.coroutineContext.cancelChildren()
                                }
                            } else {
                                dead.add(id)
                            }
                        }
                    }
                }
                for (job in jobs) {
                    runCatching { job.await() }
                }
            }
        }
        return winners.toList() to dead.toList()
    }

    private fun probeMixSingleStream(rawUrl: String): Boolean = probeMixSingleStreamWithLatency(rawUrl) != null

    fun probeMixSingleStreamWithLatency(rawUrl: String): Long? {
        val t0 = System.currentTimeMillis()
        val clean = rawUrl.trim()
        val scheme = clean.substringBefore("://", "").lowercase(Locale.ROOT)
        if (scheme == "rtsp" || scheme == "rtmp" || scheme == "rtmps") return 50L
        if (scheme != "http" && scheme != "https") return null
        val path = clean.substringBefore('?').lowercase(Locale.ROOT)
        if (path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi") ||
            path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".jpg") ||
            path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp") ||
            path.contains("/movie/") || path.contains("/series/")
        ) {
            return null
        }
        fun readSmallBodyPrefix(res: okhttp3.Response, call: okhttp3.Call, maxBytes: Int): ByteArray {
            val stream = res.body?.byteStream() ?: run {
                call.cancel()
                return ByteArray(0)
            }
            val buf = ByteArray(maxBytes)
            var total = 0
            while (total < maxBytes) {
                val r = try {
                    stream.read(buf, total, maxBytes - total)
                } catch (_: Exception) {
                    -1
                }
                if (r <= 0) break
                total += r
                if (maxBytes <= 512 && total >= 188) break
            }
            call.cancel()
            return buf.copyOf(total)
        }
        fun looksLikeHtml(bytes: ByteArray): Boolean {
            if (bytes.isEmpty()) return false
            val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.UTF_8).trimStart().lowercase(Locale.ROOT)
            return head.startsWith("<html") || head.startsWith("<!doctype") || head.startsWith("<head")
        }
        fun verifyFollowUp(targetUrl: okhttp3.HttpUrl, userAgent: String, isManifest: Boolean): Boolean {
            val req = Request.Builder()
                .url(targetUrl)
                .get()
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .build()
            val call = mixProbeClient.newCall(req)
            return try {
                call.execute().use { res ->
                    val code = res.code
                    val ctype = res.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                    if (code !in 200..399 || ctype.contains("text/html")) {
                        call.cancel()
                        return false
                    }
                    val bytes = readSmallBodyPrefix(res, call, if (isManifest) 2048 else 512)
                    if (looksLikeHtml(bytes)) return false
                    if (isManifest) {
                        val txt = String(bytes, Charsets.UTF_8)
                        txt.contains("#EXTM3U")
                    } else {
                        bytes.size >= 188
                    }
                }
            } catch (_: Exception) {
                false
            }
        }
        fun tryGet(userAgent: String): Pair<Boolean, Int?> {
            val req = Request.Builder()
                .url(clean)
                .get()
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .build()
            val call = mixProbeClient.newCall(req)
            return try {
                call.execute().use { res ->
                    val code = res.code
                    val ctype = res.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                    if (code !in 200..399 || ctype.contains("text/html")) {
                        call.cancel()
                        return false to code
                    }
                    val finalUrl = res.request.url
                    val finalPath = finalUrl.encodedPath.lowercase(Locale.ROOT)
                    val looksHls = path.endsWith(".m3u8") || finalPath.endsWith(".m3u8") || ctype.contains("mpegurl")
                    if (looksHls) {
                        val bytes = readSmallBodyPrefix(res, call, 6144)
                        val bodyText = String(bytes, Charsets.UTF_8)
                        if (!bodyText.contains("#EXTM3U")) {
                            return false to code
                        }
                        val firstUri = bodyText.lineSequence()
                            .map { it.trim() }
                            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                        if (firstUri == null) {
                            return true to code
                        }
                        val resolved = finalUrl.resolve(firstUri) ?: return false to code
                        val childIsPlaylist = firstUri.substringBefore('?').lowercase(Locale.ROOT).endsWith(".m3u8") ||
                            bodyText.contains("#EXT-X-STREAM-INF")
                        val childOk = verifyFollowUp(resolved, userAgent, isManifest = childIsPlaylist)
                        return childOk to code
                    }
                    val bytes = readSmallBodyPrefix(res, call, 512)
                    val okBytes = !looksLikeHtml(bytes) && bytes.size >= 188
                    okBytes to code
                }
            } catch (_: Exception) {
                false to null
            }
        }
        val (ok1, code1) = tryGet(CHROME_USER_AGENT)
        if (ok1) return (System.currentTimeMillis() - t0).coerceAtLeast(1L)
        if (code1 == null || code1 in setOf(400, 401, 403, 405, 406, 451)) {
            if (tryGet(VLC_USER_AGENT).first) {
                return (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            }
        }
        return null
    }

    private fun probeGetCode(cleanUrl: String, userAgent: String): Pair<Int?, String?> {
        val req = Request.Builder()
            .url(cleanUrl)
            .get()
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .build()
        val call = probeClient.newCall(req)
        return try {
            call.execute().use { res ->
                val code = res.code
                call.cancel()
                code to null
            }
        } catch (error: Exception) {
            null to error.javaClass.simpleName
        }
    }

    private fun probeSingleStream(rawUrl: String, isPlaylistUrl: Boolean = false): String? {
        val clean = rawUrl.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return "URL non HTTP"
        val (code, err) = probeGetCode(clean, CHROME_USER_AGENT)
        if (code != null && code in 200..399) return null
        if (code == null || code in setOf(400, 401, 403, 405, 406, 416, 451)) {
            val (vlcCode, vlcErr) = probeGetCode(clean, VLC_USER_AGENT)
            if (vlcCode != null && vlcCode in 200..399) return null
            return vlcErr ?: vlcCode?.let { "HTTP $it" } ?: err ?: "timeout"
        }
        return "HTTP $code"
    }

    private companion object {
        const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val VLC_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
    }
}
