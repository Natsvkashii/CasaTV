package it.casatv.server.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

data class ImportJobSnapshot(
    val jobId: String,
    val playlistId: String,
    val name: String,
    val sourceType: String,
    val phase: String, // queued | downloading | splitting | importing | categorizing | probing | done | error
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val percent: Int = 0,
    val partsTotal: Int = 1,
    val partsDone: Int = 0,
    val channelsImported: Int = 0,
    val categorizedCount: Int = 0,
    val statusMessage: String = "",
    val error: String = "",
    val startedAt: Long = System.currentTimeMillis() / 1000,
    val finishedAt: Long? = null,
    val duplicateSkipped: Boolean = false,
    val updatedExisting: Boolean = false,
)

/**
 * High-throughput background queue for ingesting multiple giant (200-300+ MB) M3U playlists:
 * 1. Virtual offset slicing (~24 MB slices strictly on `\n#EXTINF` boundaries, zero disk duplication).
 * 2. Multi-core parallel parsing + full country inference across up to 4 CPU cores.
 * 3. Dedicated single SQLite writer consuming 10,000-row batches with bounded backpressure.
 * 4. Automatic restart recovery of already-uploaded `.m3u` files and interrupted probing playlists.
 */
class PlaylistImportEngine private constructor(context: Context) {

    private val app = context.applicationContext
    private val db = ServerDatabase.get(app)
    private val playlistManager = PlaylistManager(app)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val queue = Channel<QueuedImport>(Channel.UNLIMITED)

    private val jobs = ConcurrentHashMap<String, ImportJobSnapshot>()
    private val recentJobOrder = ArrayDeque<String>()

    @Volatile
    private var activeJobId: String? = null

    private sealed interface QueuedImport {
        val jobId: String
        val playlistId: String
        val name: String

        data class FileJob(
            override val jobId: String,
            override val playlistId: String,
            override val name: String,
            val file: File,
        ) : QueuedImport

        data class UrlJob(
            override val jobId: String,
            override val playlistId: String,
            override val name: String,
            val url: String,
        ) : QueuedImport
    }

    init {
        workerScope.launch {
            runCatching { db.ensurePerformanceIndexes() }
                .onFailure { Log.w("CasaImport", "ensurePerformanceIndexes failed: ${it.message}") }
            runCatching { cleanupStaleArtifacts() }
            runCatching { recoverPendingImports() }
                .onFailure { Log.w("CasaImport", "recoverPendingImports failed: ${it.message}") }
            workerScope.launch {
                runCatching { db.warmCatalogStats() }
                    .onFailure { Log.w("CasaImport", "warmCatalogStats failed: ${it.message}") }
            }
            workerScope.launch {
                runCatching { recheckWarnPlaylists() }
                    .onFailure { Log.w("CasaImport", "recheckWarnPlaylists failed: ${it.message}") }
            }
            workerScope.launch {
                runCatching { db.buildSearchIndexIncrementally() }
                    .onFailure { Log.w("CasaImport", "buildSearchIndexIncrementally failed: ${it.message}") }
                runCatching { db.classifyContentTypesIncrementally() }
                    .onFailure { Log.w("CasaImport", "classifyContentTypesIncrementally failed: ${it.message}") }
                runCatching { db.rebuildLiveCanonicalMapSync() }
                    .onFailure { Log.w("CasaImport", "rebuildLiveCanonicalMapSync failed: ${it.message}") }
                runCatching { db.checkpointWal(truncate = true) }
                    .onFailure { Log.w("CasaImport", "checkpointWal failed: ${it.message}") }
            }
            for (item in queue) {
                activeJobId = item.jobId
                runCatching { processJob(item) }
                    .onFailure { err ->
                        Log.e("CasaImport", "Import job ${item.jobId} crashed: ${err.message}", err)
                        val msg = err.message ?: err.javaClass.simpleName
                        db.finalizePlaylistImport(item.playlistId, 0, "error", msg)
                        updateJob(item.jobId) {
                            it.copy(
                                phase = "error",
                                error = msg,
                                statusMessage = msg,
                                finishedAt = System.currentTimeMillis() / 1000,
                            )
                        }
                    }
                activeJobId = null
            }
        }
    }

    fun importsDir(): File = File(app.cacheDir, "m3u_imports").apply { mkdirs() }

    fun hasActiveImport(): Boolean = activeJobId != null || jobs.values.any { it.phase == "queued" }

    private fun metaFileFor(file: File): File = File(file.parentFile ?: importsDir(), "${file.name}.meta")

    private fun cleanupStaleArtifacts() {
        val cutoff = System.currentTimeMillis() - 2L * 3600_000L
        importsDir().listFiles()?.forEach { f ->
            when {
                f.isDirectory && f.name.startsWith("parts_") -> runCatching { f.deleteRecursively() }
                f.isFile && f.name.endsWith(".part") && f.lastModified() < cutoff -> runCatching { f.delete() }
            }
        }
    }

    private suspend fun recoverPendingImports() {
        val importingRows = db.listPlaylists()
            .filter { it.status == "importing" }
            .sortedBy { it.addedAt }

        val finalizedIds = mutableSetOf<String>()
        val finalizedNames = mutableSetOf<String>()

        for (row in importingRows) {
            if (row.channelCount > 0) {
                val links = db.countPlaylistChannelLinks(row.id).coerceAtLeast(row.channelCount)
                val (status, lastError) = playlistManager.probeSamplesForPlaylist(row.id, links)
                db.finalizePlaylistImport(row.id, links, status, lastError)
                finalizedIds.add(row.id)
                if (row.name.isNotBlank()) finalizedNames.add(row.name.trim())
                Log.i("CasaImport", "Ripristinata e finalizzata playlist '${row.name}' ($links canali)")
            }
        }

        val emptyImporting = importingRows
            .filter { it.channelCount == 0 && it.url.isBlank() }
            .toMutableList()

        val leftoverFiles = importsDir().listFiles()
            ?.filter { it.isFile && it.name.endsWith(".m3u") && it.length() > 0L }
            ?.sortedBy { it.lastModified() }
            .orEmpty()

        for (m3uFile in leftoverFiles) {
            val meta = metaFileFor(m3uFile)
            var recoveredId: String? = null
            var recoveredName: String = m3uFile.nameWithoutExtension
            if (meta.exists()) {
                val lines = runCatching { meta.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
                if (lines.isNotEmpty() && lines[0].isNotBlank()) recoveredId = lines[0].trim()
                if (lines.size >= 2 && lines[1].isNotBlank()) recoveredName = lines[1].trim()
                if (recoveredId != null) {
                    emptyImporting.removeAll { it.id == recoveredId }
                }
            } else if (emptyImporting.isNotEmpty()) {
                val match = emptyImporting.removeAt(0)
                recoveredId = match.id
                recoveredName = match.name.ifBlank { recoveredName }
            }
            if ((recoveredId != null && recoveredId in finalizedIds) || recoveredName in finalizedNames) {
                runCatching { m3uFile.delete() }
                runCatching { meta.delete() }
                Log.i("CasaImport", "Scartato file già finalizzato '${m3uFile.name}'")
                continue
            }
            enqueueFile(m3uFile, recoveredName, existingPlaylistId = recoveredId)
            Log.i("CasaImport", "Ripreso in coda file '$recoveredName' (${formatMb(m3uFile.length())})")
        }
    }

    fun enqueueFile(file: File, name: String, existingPlaylistId: String? = null): ImportJobSnapshot {
        val cleanName = name.trim().ifBlank { file.name.ifBlank { "Playlist M3U" } }
        val fingerprint = computeFileFingerprint(file)
        if (existingPlaylistId.isNullOrBlank()) {
            val dupByHash = db.findPlaylistByContentHash(fingerprint)
            if (dupByHash != null) {
                runCatching { file.delete() }
                runCatching { metaFileFor(file).delete() }
                val jobId = "imp_" + UUID.randomUUID().toString().take(8)
                val now = System.currentTimeMillis() / 1000
                val snap = ImportJobSnapshot(
                    jobId = jobId,
                    playlistId = dupByHash.id,
                    name = dupByHash.name.ifBlank { cleanName },
                    sourceType = "file",
                    phase = "done",
                    totalBytes = file.length().coerceAtLeast(0L),
                    bytesProcessed = file.length().coerceAtLeast(0L),
                    percent = 100,
                    channelsImported = dupByHash.channelCount,
                    categorizedCount = dupByHash.channelCount,
                    statusMessage = "Playlist identica già presente: '${dupByHash.name}'",
                    finishedAt = now,
                    duplicateSkipped = true,
                )
                registerJob(snap)
                Log.i("CasaImport", "File duplicato ignorato '$cleanName' -> già presente '${dupByHash.name}' (${dupByHash.id})")
                return snap
            }
        }
        val existingByName = if (existingPlaylistId.isNullOrBlank()) db.findUploadedPlaylistByName(cleanName) else null
        val playlistId = existingPlaylistId?.takeIf { it.isNotBlank() }
            ?: existingByName?.id
            ?: ("pl_" + UUID.randomUUID().toString().take(8))
        val updatedExisting = existingPlaylistId.isNullOrBlank() && existingByName != null
        val jobId = "imp_" + UUID.randomUUID().toString().take(8)
        val total = file.length().coerceAtLeast(0L)
        runCatching { metaFileFor(file).writeText("$playlistId\n$cleanName", Charsets.UTF_8) }
        val snap = ImportJobSnapshot(
            jobId = jobId,
            playlistId = playlistId,
            name = cleanName,
            sourceType = "file",
            phase = "queued",
            totalBytes = total,
            statusMessage = if (updatedExisting) "Aggiornamento playlist in coda…" else "In coda per l'importazione…",
            updatedExisting = updatedExisting,
        )
        registerJob(snap)
        db.savePlaylistRecord(
            PlaylistEntry(
                id = playlistId,
                name = cleanName,
                url = "",
                channelCount = existingByName?.channelCount ?: 0,
                enabled = existingByName?.enabled ?: true,
                status = "importing",
                lastError = "In coda…",
                contentHash = fingerprint,
            ),
        )
        queue.trySend(QueuedImport.FileJob(jobId, playlistId, cleanName, file))
        return snap
    }

    fun enqueueUrl(name: String, url: String, existingPlaylistId: String? = null): ImportJobSnapshot {
        val cleanUrl = url.trim()
        val existingByUrl = if (existingPlaylistId.isNullOrBlank()) db.findPlaylistByUrl(cleanUrl) else null
        val playlistId = existingPlaylistId?.takeIf { it.isNotBlank() }
            ?: existingByUrl?.id
            ?: ("pl_" + UUID.randomUUID().toString().take(8))
        val updatedExisting = existingPlaylistId.isNullOrBlank() && existingByUrl != null
        val cleanName = name.trim().ifBlank {
            existingByUrl?.name?.takeIf { it.isNotBlank() }
                ?: cleanUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Playlist M3U" }
        }
        val jobId = "imp_" + UUID.randomUUID().toString().take(8)
        val snap = ImportJobSnapshot(
            jobId = jobId,
            playlistId = playlistId,
            name = cleanName,
            sourceType = "url",
            phase = "queued",
            statusMessage = if (updatedExisting) "Aggiornamento playlist esistente in coda…" else "In coda per il download…",
            updatedExisting = updatedExisting,
        )
        registerJob(snap)
        db.savePlaylistRecord(
            PlaylistEntry(
                id = playlistId,
                name = cleanName,
                url = cleanUrl,
                channelCount = existingByUrl?.channelCount ?: 0,
                enabled = existingByUrl?.enabled ?: true,
                status = "importing",
                lastError = if (updatedExisting) "Aggiornamento in coda…" else "In coda per il download…",
            ),
        )
        queue.trySend(QueuedImport.UrlJob(jobId, playlistId, cleanName, cleanUrl))
        return snap
    }

    data class StatusSnapshot(
        val activeJob: ImportJobSnapshot?,
        val queuedCount: Int,
        val recentJobs: List<ImportJobSnapshot>,
    )

    fun statusSnapshot(): StatusSnapshot {
        val active = activeJobId?.let { jobs[it] }
        val queued = jobs.values.count { it.phase == "queued" }
        val recent = synchronized(recentJobOrder) {
            recentJobOrder.mapNotNull { jobs[it] }.reversed()
        }
        return StatusSnapshot(activeJob = active, queuedCount = queued, recentJobs = recent)
    }

    private fun registerJob(snap: ImportJobSnapshot) {
        jobs[snap.jobId] = snap
        synchronized(recentJobOrder) {
            recentJobOrder.addLast(snap.jobId)
            while (recentJobOrder.size > 15) {
                val oldId = recentJobOrder.removeFirst()
                if (oldId != activeJobId) jobs.remove(oldId)
            }
        }
    }

    private fun updateJob(jobId: String, transform: (ImportJobSnapshot) -> ImportJobSnapshot) {
        jobs.computeIfPresent(jobId) { _, cur -> transform(cur) }
    }

    private suspend fun processJob(item: QueuedImport) {
        val jobId = item.jobId
        val playlistId = item.playlistId
        var sourceUrl = ""
        var workingFile: File? = null
        val tStartMs = System.currentTimeMillis()

        try {
            workingFile = when (item) {
                is QueuedImport.FileJob -> item.file
                is QueuedImport.UrlJob -> {
                    sourceUrl = item.url
                    if (!sourceUrl.startsWith("http://", true) && !sourceUrl.startsWith("https://", true)) {
                        throw IllegalArgumentException("Indirizzo non valido (deve iniziare con http:// o https://)")
                    }
                    updateJob(jobId) {
                        it.copy(phase = "downloading", statusMessage = "Download playlist in corso…")
                    }
                    db.updatePlaylistImportProgress(playlistId, 0, "Download in corso…")
                    playlistManager.downloadUrlToTempFile(sourceUrl) { downloaded, total ->
                        val pct = if (total > 0L) ((downloaded * 20L) / total).toInt().coerceIn(0, 20) else 5
                        updateJob(jobId) {
                            it.copy(
                                phase = "downloading",
                                bytesProcessed = downloaded,
                                totalBytes = total,
                                percent = pct,
                                statusMessage = if (total > 0L) {
                                    "Download ${formatMb(downloaded)} / ${formatMb(total)}"
                                } else {
                                    "Download ${formatMb(downloaded)}"
                                },
                            )
                        }
                    }
                }
            }

            val totalBytes = workingFile.length().coerceAtLeast(1L)
            val cpuCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            updateJob(jobId) {
                it.copy(
                    phase = "splitting",
                    totalBytes = totalBytes,
                    bytesProcessed = 0L,
                    percent = if (item is QueuedImport.UrlJob) 22 else 3,
                    statusMessage = "Indicizzazione sezioni (${formatMb(totalBytes)})…",
                )
            }

            val slices = computeVirtualSlices(workingFile, cpuCores)
            val partsTotal = slices.size.coerceAtLeast(1)
            val workerCount = cpuCores.coerceAtMost(partsTotal)
            val fingerprint = computeFileFingerprint(workingFile)

            val baseEntry = PlaylistEntry(
                id = playlistId,
                name = item.name,
                url = sourceUrl,
                channelCount = 0,
                enabled = true,
                status = "importing",
                lastError = "Importazione multi-core (${workerCount}x)…",
                contentHash = fingerprint,
            )
            val (_, initialNextNum) = db.preparePlaylistImport(baseEntry)
            var nextNum = initialNextNum
            var totalChannels = 0
            val startImportPct = if (item is QueuedImport.UrlJob) 24 else 4
            val endImportPct = 95

            val firstSample = workingFile.inputStream().bufferedReader(Charsets.UTF_8).use { r ->
                val buf = CharArray(16_384)
                val n = r.read(buf)
                if (n <= 0) "" else String(buf, 0, n)
            }.removePrefix("\uFEFF")

            val partsDoneCounter = AtomicInteger(0)
            val bytesReadCounter = AtomicLong(0L)
            var lastProgressDbCount = 0
            var lastWalCheckpointCount = 0

            updateJob(jobId) {
                it.copy(
                    phase = "importing",
                    partsTotal = partsTotal,
                    partsDone = 0,
                    percent = startImportPct,
                    statusMessage = "Elaborazione multi-core (${workerCount}x) avviata…",
                )
            }

            if (M3uParser.isHlsMediaOrMasterManifest(firstSample)) {
                totalChannels = 0
            } else if (M3uParser.isPlsContent(firstSample) && totalBytes <= 16L * 1024L * 1024L) {
                M3uParser.streamParse(workingFile, playlistId, batchSize = BATCH_SIZE, fastCountryOnly = false) { batch, bytesApprox ->
                    val (linked, nextN) = db.upsertChannelBatch(
                        playlistId = playlistId,
                        batch = batch,
                        startPosition = totalChannels,
                        startNumber = nextNum,
                    )
                    nextNum = nextN
                    totalChannels += linked
                    val pct = startImportPct + ((bytesApprox.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0) * (endImportPct - startImportPct)).toInt()
                    updateJob(jobId) {
                        it.copy(
                            phase = "importing",
                            bytesProcessed = bytesApprox,
                            percent = pct,
                            partsDone = 1,
                            channelsImported = totalChannels,
                            statusMessage = "$totalChannels canali importati…",
                        )
                    }
                }
            } else {
                val sliceChannel = Channel<LongRange>(Channel.UNLIMITED)
                for (slice in slices) {
                    sliceChannel.trySend(slice)
                }
                sliceChannel.close()

                val batchChannel = Channel<Pair<List<IptvChannel>, Long>>(capacity = 8)
                coroutineScope {
                    val producers = ArrayList<Job>(workerCount)
                    repeat(workerCount) {
                        producers += launch(Dispatchers.Default) {
                            for (slice in sliceChannel) {
                                M3uParser.streamParseRange(
                                    file = workingFile,
                                    startOffset = slice.first,
                                    endOffset = slice.last + 1L,
                                    playlistId = playlistId,
                                    batchSize = BATCH_SIZE,
                                    fastCountryOnly = false,
                                ) { batch, bytesDelta ->
                                    batchChannel.send(batch to bytesDelta)
                                }
                                partsDoneCounter.incrementAndGet()
                            }
                        }
                    }
                    launch(Dispatchers.IO) {
                        producers.joinAll()
                        batchChannel.close()
                    }

                    for ((batch, bytesDelta) in batchChannel) {
                        if (batch.isNotEmpty()) {
                            val (linked, nextN) = db.upsertChannelBatch(
                                playlistId = playlistId,
                                batch = batch,
                                startPosition = totalChannels,
                                startNumber = nextNum,
                            )
                            nextNum = nextN
                            totalChannels += linked
                            delay(5L)
                        }
                        val overallBytes = bytesReadCounter.addAndGet(bytesDelta).coerceAtMost(totalBytes)
                        val doneParts = partsDoneCounter.get().coerceAtLeast(1).coerceAtMost(partsTotal)
                        val ratio = (overallBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
                        val pct = startImportPct + (ratio * (endImportPct - startImportPct)).toInt()
                        val msg = "Elaborazione ${workerCount}x · Parte $doneParts/$partsTotal · $totalChannels canali"
                        updateJob(jobId) {
                            it.copy(
                                phase = "importing",
                                bytesProcessed = overallBytes,
                                percent = pct,
                                partsTotal = partsTotal,
                                partsDone = partsDoneCounter.get(),
                                channelsImported = totalChannels,
                                categorizedCount = totalChannels,
                                statusMessage = msg,
                            )
                        }
                        if (totalChannels - lastProgressDbCount >= 25_000) {
                            lastProgressDbCount = totalChannels
                            db.updatePlaylistImportProgress(playlistId, totalChannels, msg)
                        }
                        if (totalChannels - lastWalCheckpointCount >= 100_000) {
                            lastWalCheckpointCount = totalChannels
                            db.checkpointWal(truncate = false)
                        }
                    }
                }
            }

            if (totalChannels == 0) {
                val errMsg = "Nessun canale IPTV valido trovato nella playlist"
                db.finalizePlaylistImport(playlistId, 0, "error", errMsg)
                updateJob(jobId) {
                    it.copy(
                        phase = "error",
                        percent = 100,
                        error = errMsg,
                        statusMessage = errMsg,
                        finishedAt = System.currentTimeMillis() / 1000,
                    )
                }
                return
            }

            updateJob(jobId) {
                it.copy(
                    phase = "probing",
                    percent = 97,
                    partsDone = partsTotal,
                    statusMessage = "Verifica flussi campione…",
                )
            }
            val (status, lastError) = playlistManager.probeSamplesForPlaylist(playlistId, totalChannels)
            db.finalizePlaylistImport(playlistId, totalChannels, status, lastError)
            if (totalChannels > 0) {
                workerScope.launch {
                    runCatching { db.buildSearchIndexIncrementally() }
                        .onFailure { Log.w("CasaImport", "buildSearchIndexIncrementally failed: ${it.message}") }
                    runCatching { db.classifyContentTypesIncrementally() }
                        .onFailure { Log.w("CasaImport", "classifyContentTypesIncrementally failed: ${it.message}") }
                    runCatching { db.rebuildLiveCanonicalMapSync(force = true) }
                        .onFailure { Log.w("CasaImport", "rebuildLiveCanonicalMapSync failed: ${it.message}") }
                    runCatching { db.checkpointWal(truncate = true) }
                        .onFailure { Log.w("CasaImport", "checkpointWal failed: ${it.message}") }
                }
            }
            val elapsedSec = (System.currentTimeMillis() - tStartMs) / 1000.0
            updateJob(jobId) {
                it.copy(
                    phase = "done",
                    bytesProcessed = totalBytes,
                    percent = 100,
                    partsDone = partsTotal,
                    channelsImported = totalChannels,
                    categorizedCount = totalChannels,
                    statusMessage = "Completato: $totalChannels canali in ${String.format(java.util.Locale.US, "%.1f", elapsedSec)}s",
                    error = if (status == "error") lastError else "",
                    finishedAt = System.currentTimeMillis() / 1000,
                )
            }
            Log.i("CasaImport", "Importazione ${workerCount}x completata '${item.name}': $totalChannels canali, $partsTotal fette in ${String.format(java.util.Locale.US, "%.1f", elapsedSec)}s")
        } finally {
            workingFile?.let { f ->
                runCatching { metaFileFor(f).delete() }
                runCatching { f.delete() }
            }
        }
    }

    /**
     * Computes byte slices strictly before `\n#EXTINF` headers so multiple CPU cores
     * can parse the file concurrently with zero physical file copying.
     */
    private fun computeVirtualSlices(sourceFile: File, workerCount: Int): List<LongRange> {
        val length = sourceFile.length()
        if (length <= SPLIT_THRESHOLD_BYTES) {
            return listOf(0L until length)
        }
        val firstSample = sourceFile.inputStream().bufferedReader(Charsets.UTF_8).use { r ->
            val buf = CharArray(4096)
            val n = r.read(buf)
            if (n <= 0) "" else String(buf, 0, n)
        }.removePrefix("\uFEFF").trimStart()
        if (M3uParser.isPlsContent(firstSample)) {
            return listOf(0L until length)
        }

        val targetStep = if (length < SLICE_TARGET_BYTES * workerCount.toLong()) {
            (length / workerCount.toLong()).coerceAtLeast(2L * 1024L * 1024L)
        } else {
            SLICE_TARGET_BYTES
        }

        val slices = ArrayList<LongRange>((length / targetStep).toInt() + 2)
        var start = 0L
        RandomAccessFile(sourceFile, "r").use { raf ->
            while (start < length) {
                val target = start + targetStep
                if (target >= length - 256L * 1024L) {
                    slices += start until length
                    break
                }
                val boundary = findNextExtinfStart(raf, target, length)
                if (boundary <= start || boundary >= length) {
                    slices += start until length
                    break
                }
                slices += start until boundary
                start = boundary
            }
        }
        return slices.ifEmpty { listOf(0L until length) }
    }

    private fun findNextExtinfStart(raf: RandomAccessFile, fromOffset: Long, length: Long): Long {
        val overlap = 16
        val buf = ByteArray(16_384)
        var pos = fromOffset.coerceAtLeast(0L)
        val needleLower = byteArrayOf(
            '#'.code.toByte(),
            'e'.code.toByte(),
            'x'.code.toByte(),
            't'.code.toByte(),
            'i'.code.toByte(),
            'n'.code.toByte(),
            'f'.code.toByte(),
        )
        while (pos < length) {
            raf.seek(pos)
            val read = raf.read(buf)
            if (read <= needleLower.size + 1) return -1L
            val limit = read - needleLower.size
            for (i in 0 until limit) {
                if (buf[i] == '\n'.code.toByte()) {
                    var j = 0
                    var matched = true
                    while (j < needleLower.size) {
                        val b = buf[i + 1 + j].toInt()
                        val lower = if (b in 'A'.code..'Z'.code) b + 32 else b
                        if (lower != needleLower[j].toInt()) {
                            matched = false
                            break
                        }
                        j++
                    }
                    if (matched) {
                        return pos + i.toLong() + 1L
                    }
                }
            }
            if (pos + read >= length) break
            pos += (read - overlap).coerceAtLeast(1)
        }
        return -1L
    }

    private suspend fun recheckWarnPlaylists() {
        val warnRows = db.listPlaylists().filter { it.status == "warn" && it.channelCount > 0 }
        for (row in warnRows) {
            val updated = playlistManager.checkPlaylist(row.id)
            val newStatus = updated?.status ?: "unknown"
            val detail = updated?.lastError?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            Log.i("CasaImport", "Rivalutata playlist '${row.name}' (${row.channelCount} canali): warn -> $newStatus$detail")
        }
    }

    private fun formatMb(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    companion object {
        private const val SPLIT_THRESHOLD_BYTES = 8L * 1024L * 1024L
        private const val SLICE_TARGET_BYTES = 24L * 1024L * 1024L
        private const val BATCH_SIZE = 10_000

        @Volatile
        private var instance: PlaylistImportEngine? = null

        fun computeFileFingerprint(file: File): String {
            return runCatching {
                val len = file.length().coerceAtLeast(0L)
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val lenBytes = len.toString().toByteArray(Charsets.UTF_8)
                md.update(lenBytes)
                val sliceSize = 65_536
                val offsets = if (len <= sliceSize.toLong() * 3L) {
                    listOf(0L)
                } else {
                    listOf(0L, (len / 2L) - (sliceSize / 2), (len - sliceSize).coerceAtLeast(0L))
                }
                val buf = ByteArray(sliceSize)
                RandomAccessFile(file, "r").use { raf ->
                    for (off in offsets) {
                        raf.seek(off)
                        val toRead = if (offsets.size == 1) len.toInt().coerceAtMost(buf.size) else sliceSize
                        var readTotal = 0
                        while (readTotal < toRead) {
                            val n = raf.read(buf, readTotal, toRead - readTotal)
                            if (n <= 0) break
                            readTotal += n
                        }
                        if (readTotal > 0) md.update(buf, 0, readTotal)
                    }
                }
                val hex = md.digest().take(8).joinToString("") { "%02x".format(it) }
                "$len-$hex"
            }.getOrElse { "${file.length()}-${file.name}" }
        }

        fun get(context: Context): PlaylistImportEngine =
            instance ?: synchronized(this) {
                instance ?: PlaylistImportEngine(context).also { instance = it }
            }
    }
}
