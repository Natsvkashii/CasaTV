package it.casatv.server.core

import android.content.Context
import android.util.Log
import android.util.LruCache
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

data class CachedLogo(
    val bytes: ByteArray,
    val mimeType: String,
    val etag: String,
    val fetchedAtMs: Long,
)

/**
 * Persistent server-side channel logo cache.
 * Stores downloaded logos in `filesDir/logos` (<sha256>.bin + .meta) so TV, mobile,
 * and browser clients fetch logos directly from the LAN server instead of hitting the internet.
 */
class LogoCache private constructor(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "logos").apply { mkdirs() }
    private val baseClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val interactiveClient = baseClient.newBuilder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(4500, TimeUnit.MILLISECONDS)
        .callTimeout(6500, TimeUnit.MILLISECONDS)
        .build()
    private val prefetchClient = baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val interactiveDownloadPermits = Semaphore(10)

    private val memory = object : LruCache<String, CachedLogo>(MAX_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: CachedLogo): Int = value.bytes.size.coerceAtLeast(1)
    }
    private val inFlight = ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<CachedLogo?>>()
    private val negativeCache = ConcurrentHashMap<String, Long>()
    private val pruneRunning = AtomicBoolean(false)
    @Volatile
    private var lastPruneAtMs = 0L
    @Volatile
    private var cachedStatsCount = 0
    @Volatile
    private var cachedStatsBytes = 0L
    @Volatile
    private var cachedStatsAtMs = 0L
    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val prefetchDispatcher = Dispatchers.IO.limitedParallelism(3)

    fun etagFor(url: String): String = sha256Hex(url).take(24)

    fun isCachedOnDisk(url: String): Boolean {
        if (url.isBlank()) return false
        val bin = File(dir, "${sha256Hex(url)}.bin")
        return bin.exists() && bin.length() > 0L
    }

    fun getOrFetch(rawUrl: String, interactive: Boolean = true): CachedLogo? {
        val url = rawUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return null
        }
        val hash = sha256Hex(url)
        val now = System.currentTimeMillis()
        memory.get(hash)?.let { mem ->
            if (now - mem.fetchedAtMs < FRESH_TTL_MS) {
                return mem
            }
        }

        val stale = readFromDisk(hash)
        if (stale != null && now - stale.fetchedAtMs < FRESH_TTL_MS) {
            memory.put(hash, stale)
            return stale
        }

        val blockedUntil = negativeCache[hash]
        if (blockedUntil != null && now < blockedUntil) {
            return stale
        }

        val myFuture = java.util.concurrent.CompletableFuture<CachedLogo?>()
        val existing = inFlight.putIfAbsent(hash, myFuture)
        if (existing != null) {
            val waitMs = if (interactive) 2500L else 10_000L
            return runCatching { existing.get(waitMs, TimeUnit.MILLISECONDS) }.getOrNull() ?: stale
        }

        var triggerAsyncPrune = false
        var finalResult: CachedLogo? = stale
        var permitAcquired = false
        try {
            val innerNow = System.currentTimeMillis()
            if (interactive) {
                permitAcquired = runCatching {
                    interactiveDownloadPermits.tryAcquire(300, TimeUnit.MILLISECONDS)
                }.getOrDefault(false)
                if (!permitAcquired) {
                    return stale
                }
            }

            finalResult = when (val outcome = download(url, hash, innerNow, interactive)) {
                is DownloadOutcome.Success -> {
                    negativeCache.remove(hash)
                    writeToDisk(hash, outcome.logo)
                    memory.put(hash, outcome.logo)
                    cachedStatsAtMs = 0L
                    if (innerNow - lastPruneAtMs >= PRUNE_COOLDOWN_MS) {
                        triggerAsyncPrune = true
                    }
                    outcome.logo
                }
                is DownloadOutcome.Failure -> {
                    val negTtl = if (outcome.permanent) NEGATIVE_PERMANENT_MS else NEGATIVE_TRANSIENT_MS
                    if (negativeCache.size > 2000) {
                        negativeCache.entries.removeIf { (_, until) -> until <= innerNow }
                    }
                    negativeCache[hash] = innerNow + negTtl
                    if (stale != null) {
                        memory.put(hash, stale)
                    }
                    stale
                }
            }
            return finalResult
        } finally {
            if (permitAcquired) {
                interactiveDownloadPermits.release()
            }
            inFlight.remove(hash, myFuture)
            if (!myFuture.isDone) {
                myFuture.complete(finalResult)
            }
            if (triggerAsyncPrune) {
                backgroundScope.launch { maybeTriggerPrune() }
            }
        }
    }

    fun stats(): Pair<Int, Long> {
        val now = System.currentTimeMillis()
        if (now - cachedStatsAtMs < STATS_TTL_MS) {
            return cachedStatsCount to cachedStatsBytes
        }
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".bin") } ?: return 0 to 0L
        var bytes = 0L
        for (f in files) {
            bytes += f.length()
        }
        cachedStatsCount = files.size
        cachedStatsBytes = bytes
        cachedStatsAtMs = now
        return files.size to bytes
    }

    fun prefetchUrls(urls: List<String>, scope: CoroutineScope): Job {
        return scope.launch(prefetchDispatcher) {
            var downloaded = 0
            var skipped = 0
            val now = System.currentTimeMillis()
            val pending = urls.asSequence()
                .map { it.trim() }
                .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
                .distinct()
                .filter { url ->
                    val hash = sha256Hex(url)
                    val blockedUntil = negativeCache[hash]
                    if (isCachedOnDisk(url) || (blockedUntil != null && now < blockedUntil)) {
                        skipped++
                        false
                    } else {
                        true
                    }
                }
                .toList()

            coroutineScope {
                pending.chunked(3).forEach { batch ->
                    batch.map { url ->
                        async {
                            if (getOrFetch(url, interactive = false) != null) 1 else 0
                        }
                    }.awaitAll().forEach { downloaded += it }
                }
            }
            if (downloaded > 0) {
                Log.i("CasaTV", "LogoCache pre-warm: scaricati=$downloaded, giàInCache=$skipped")
            }
        }
    }

    private fun readFromDisk(hash: String): CachedLogo? {
        val bin = File(dir, "$hash.bin")
        val meta = File(dir, "$hash.meta")
        if (!bin.exists() || !meta.exists()) return null
        val len = bin.length()
        if (len <= 0L || len > MAX_LOGO_BYTES) return null
        return runCatching {
            val parts = meta.readText(Charsets.UTF_8).trim().split('|', limit = 2)
            val fetchedAt = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val mime = parts.getOrNull(1)?.takeIf { it.startsWith("image/") } ?: "image/png"
            val bytes = bin.readBytes()
            if (bytes.isEmpty()) return null
            CachedLogo(
                bytes = bytes,
                mimeType = mime,
                etag = hash.take(24),
                fetchedAtMs = fetchedAt,
            )
        }.getOrNull()
    }

    private fun writeToDisk(hash: String, logo: CachedLogo) {
        runCatching {
            val binTmp = File(dir, "$hash.bin.tmp")
            val binDest = File(dir, "$hash.bin")
            binTmp.writeBytes(logo.bytes)
            if (!binTmp.renameTo(binDest)) {
                binDest.writeBytes(logo.bytes)
                binTmp.delete()
            }

            val metaTmp = File(dir, "$hash.meta.tmp")
            val metaDest = File(dir, "$hash.meta")
            metaTmp.writeText("${logo.fetchedAtMs}|${logo.mimeType}", Charsets.UTF_8)
            if (!metaTmp.renameTo(metaDest)) {
                metaDest.writeText("${logo.fetchedAtMs}|${logo.mimeType}", Charsets.UTF_8)
                metaTmp.delete()
            }
        }
    }

    private fun maybeTriggerPrune() {
        val now = System.currentTimeMillis()
        if (now - lastPruneAtMs < PRUNE_COOLDOWN_MS) return
        if (!pruneRunning.compareAndSet(false, true)) return
        try {
            lastPruneAtMs = now
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".bin") } ?: return
            var totalBytes = 0L
            for (f in files) totalBytes += f.length()
            val dynamicCap = minOf(MAX_DISK_BYTES, (dir.usableSpace / 4L).coerceAtLeast(64L * 1024L * 1024L))
            if (totalBytes <= dynamicCap) return

            val targetBytes = (dynamicCap * 85L) / 100L
            val sorted = files.sortedBy { it.lastModified() }
            for (f in sorted) {
                if (totalBytes <= targetBytes) break
                val len = f.length()
                val meta = File(dir, f.name.removeSuffix(".bin") + ".meta")
                if (f.delete()) {
                    totalBytes -= len
                    meta.delete()
                }
            }
        } finally {
            pruneRunning.set(false)
        }
    }

    private sealed interface DownloadOutcome {
        data class Success(val logo: CachedLogo) : DownloadOutcome
        data class Failure(val permanent: Boolean) : DownloadOutcome
    }

    private fun download(url: String, hash: String, now: Long, interactive: Boolean): DownloadOutcome {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/svg+xml,*/*")
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                .build()
            val httpClient = if (interactive) interactiveClient else prefetchClient
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    val permanent = res.code in intArrayOf(400, 401, 403, 404, 410, 451)
                    return DownloadOutcome.Failure(permanent)
                }
                val contentLength = res.body?.contentLength() ?: -1L
                if (contentLength > MAX_LOGO_BYTES) {
                    return DownloadOutcome.Failure(permanent = true)
                }
                val body = res.body ?: return DownloadOutcome.Failure(permanent = false)
                val bytes = body.byteStream().use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_LOGO_BYTES) {
                            return DownloadOutcome.Failure(permanent = true)
                        }
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                if (bytes.isEmpty()) return DownloadOutcome.Failure(permanent = true)
                val headerMime = res.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
                val mime = detectImageMime(bytes)
                    ?: headerMime?.takeIf { it.startsWith("image/") }
                    ?: return DownloadOutcome.Failure(permanent = true)
                DownloadOutcome.Success(
                    CachedLogo(
                        bytes = bytes,
                        mimeType = mime,
                        etag = hash.take(24),
                        fetchedAtMs = now,
                    ),
                )
            }
        } catch (_: Exception) {
            DownloadOutcome.Failure(permanent = false)
        }
    }

    private fun detectImageMime(bytes: ByteArray): String? {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            return "image/jpeg"
        }
        if (bytes.size >= 6) {
            val head4 = String(bytes, 0, 4, Charsets.US_ASCII)
            if (head4 == "GIF8") return "image/gif"
            if (head4 == "RIFF" && bytes.size >= 12 &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
            ) {
                return "image/webp"
            }
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x00.toByte() &&
            bytes[1] == 0x00.toByte() &&
            bytes[2] == 0x01.toByte() &&
            bytes[3] == 0x00.toByte()
        ) {
            return "image/x-icon"
        }
        if (bytes.size >= 12 &&
            String(bytes, 4, 8, Charsets.US_ASCII).startsWith("ftyp")
        ) {
            val brand = String(bytes, 8, 4, Charsets.US_ASCII)
            if (brand == "avif" || brand == "avis") return "image/avif"
        }
        val textSample = String(bytes, 0, bytes.size.coerceAtMost(400), Charsets.UTF_8).trimStart()
        if (textSample.startsWith("<svg", ignoreCase = true) ||
            (textSample.startsWith("<?xml", ignoreCase = true) && textSample.contains("<svg", ignoreCase = true))
        ) {
            return "image/svg+xml"
        }
        return null
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }

    companion object {
        private const val MAX_MEMORY_BYTES = 12 * 1024 * 1024
        private const val MAX_LOGO_BYTES = 3 * 1024 * 1024
        private const val MAX_DISK_BYTES = 512L * 1024L * 1024L
        private const val FRESH_TTL_MS = 90L * 24L * 3600L * 1000L
        private const val NEGATIVE_PERMANENT_MS = 6L * 3600L * 1000L
        private const val NEGATIVE_TRANSIENT_MS = 2L * 60L * 1000L
        private const val PRUNE_COOLDOWN_MS = 5L * 60L * 1000L
        private const val STATS_TTL_MS = 30_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        @Volatile
        private var instance: LogoCache? = null

        fun getInstance(context: Context): LogoCache =
            instance ?: synchronized(this) {
                instance ?: LogoCache(context).also { instance = it }
            }
    }
}
