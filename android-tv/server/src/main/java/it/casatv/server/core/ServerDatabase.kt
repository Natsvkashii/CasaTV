package it.casatv.server.core

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Single SQLite database of the Android server. Profiles live here (not on the
 * TV), so the same profile can be used by every TV that connects.
 */
class ServerDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    private val writeLock = Any()

    private data class CatalogStatsCache(
        val channelCount: Int, // Unique Live TV channels
        val liveStreamCount: Int,
        val movieCount: Int,
        val seriesCount: Int,
        val totalStreamCount: Int,
        val countryCount: Int,
        val byCountry: List<Pair<String, Int>>,
        val byLanguage: List<Pair<String, Int>>,
        val byCategory: List<Pair<String, Int>>,
    )

    @Volatile
    private var statsCache: CatalogStatsCache? = null
    @Volatile
    private var staleStatsCache: CatalogStatsCache? = null
    @Volatile
    private var lastKnownChannelCount: Int = 0
    @Volatile
    private var disabledPlaylistsCache: Boolean? = null
    @Volatile
    private var cachedDefaultProfile: ProfileRow? = null
    @Volatile
    private var ftsSearchReady: Boolean = false
    @Volatile
    private var vodClassifyReady: Boolean = false
    @Volatile
    private var liveCanonReady: Boolean = false
    @Volatile
    private var vodGlobalIndexesReady: Boolean = false
    private val vodGlobalIndexesLock = Any()
    private val liveCanonLock = Any()
    private val vodCategoryCache = ConcurrentHashMap<String, Pair<Long, List<Pair<String, Int>>>>()
    private val vodCountryCountCache = ConcurrentHashMap<String, Pair<Long, Map<String, Int>>>()
    private val vodCategoryComputeLock = Any()
    private val statsComputeLock = Any()
    private val statsRefreshing = AtomicBoolean(false)
    private val liveCanonRebuilding = AtomicBoolean(false)
    private val statsGeneration = AtomicLong(0L)
    @Volatile
    private var lastClientPruneAt: Long = 0L

    data class PersistedSourceHealth(
        val stallCount: Int,
        val failCount: Int,
        val successCount: Int,
        val avgLatencyMs: Long,
        val lastStallAt: Long,
        val updatedAt: Long,
    )

    private val sourcePrefsCache = ConcurrentHashMap<String, String>()
    private val sourceHealthCache = ConcurrentHashMap<String, PersistedSourceHealth>()
    @Volatile
    private var sourceMetaLoaded = false
    private val sourceMetaLoadLock = Any()

    fun invalidateCatalogCache() {
        statsGeneration.incrementAndGet()
        statsCache = null
        disabledPlaylistsCache = null
        vodCategoryCache.clear()
        vodCountryCountCache.clear()
        runCatching {
            synchronized(writeLock) {
                writableDatabase.execSQL("DELETE FROM search_meta WHERE key IN ('catalog_stats_v1', 'catalog_stats_v2')")
            }
        }
    }

    private fun hasDisabledPlaylists(): Boolean {
        disabledPlaylistsCache?.let { return it }
        val disabled = readableDatabase.rawQuery(
            "SELECT 1 FROM playlists WHERE enabled = 0 LIMIT 1",
            null,
        ).use { it.moveToFirst() }
        disabledPlaylistsCache = disabled
        return disabled
    }

    private fun enabledChannelWhere(): String =
        if (hasDisabledPlaylists()) ENABLED_CHANNEL_WHERE else "1 = 1"

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        runCatching { db.enableWriteAheadLogging() }
        runCatching { db.execSQL("PRAGMA synchronous = NORMAL") }
        runCatching { db.execSQL("PRAGMA temp_store = MEMORY") }
        runCatching { db.execSQL("PRAGMA cache_size = -65536") }
        runCatching { db.execSQL("PRAGMA mmap_size = 268435456") }
        runCatching { db.execSQL("PRAGMA busy_timeout = 10000") }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE profiles (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                avatar TEXT NOT NULL,
                language TEXT NOT NULL,
                country TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE library (
                id TEXT PRIMARY KEY,
                kind TEXT NOT NULL,
                title TEXT NOT NULL,
                year INTEGER,
                plot TEXT NOT NULL DEFAULT '',
                genre TEXT NOT NULL DEFAULT '',
                runtime INTEGER,
                rating REAL,
                poster_path TEXT,
                series TEXT NOT NULL DEFAULT '',
                season INTEGER,
                episode INTEGER,
                saga TEXT NOT NULL DEFAULT '',
                collection TEXT NOT NULL DEFAULT '',
                file_name TEXT NOT NULL,
                file_path TEXT NOT NULL,
                mime TEXT NOT NULL,
                size INTEGER NOT NULL,
                quality TEXT NOT NULL DEFAULT '',
                metadata_source TEXT NOT NULL DEFAULT '',
                added_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE profile_library (
                profile_id TEXT NOT NULL,
                item_id TEXT NOT NULL,
                favorite INTEGER NOT NULL DEFAULT 0,
                last_played_at INTEGER,
                PRIMARY KEY (profile_id, item_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE clients (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                platform TEXT NOT NULL DEFAULT '',
                app_version TEXT NOT NULL DEFAULT '',
                profile_id TEXT NOT NULL DEFAULT '',
                state TEXT NOT NULL DEFAULT 'idle',
                channel TEXT NOT NULL DEFAULT '',
                ip TEXT NOT NULL DEFAULT '',
                first_seen INTEGER NOT NULL,
                last_seen INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_library_kind ON library(kind)")
        createChannelsTable(db)
        createSearchIndexTables(db)
        createLiveCanonicalTable(db)
        createDiagnosticLogsTable(db)
        createExtraTables(db)
    }

    private fun createSearchIndexTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_meta (
                key TEXT PRIMARY KEY,
                val TEXT NOT NULL
            )
            """.trimIndent(),
        )
        runCatching {
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS channels_fts USING fts4(content=\"channels\", name, tokenize=unicode61)",
            )
        }
    }

    private fun createLiveCanonicalTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS live_canonical_map (
                channel_id TEXT PRIMARY KEY,
                group_key TEXT NOT NULL,
                is_primary INTEGER NOT NULL DEFAULT 0,
                source_count INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_live_canon_group ON live_canonical_map(group_key)") }
        runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_live_canon_primary ON live_canonical_map(is_primary, channel_id)") }
    }

    private fun resetSearchIndexLocked(db: SQLiteDatabase) {
        ftsSearchReady = false
        liveCanonReady = false
        runCatching { db.execSQL("DROP TABLE IF EXISTS channels_fts") }
        createSearchIndexTables(db)
        createLiveCanonicalTable(db)
        db.execSQL("INSERT OR REPLACE INTO search_meta(key, val) VALUES ('fts_last_rowid', '0')")
        db.execSQL("INSERT OR REPLACE INTO search_meta(key, val) VALUES ('fts_ready', '0')")
        db.execSQL("DELETE FROM search_meta WHERE key IN ('catalog_stats_v1', 'catalog_stats_v2', 'live_canon_version', 'live_canon_max_rowid')")
    }

    fun buildSearchIndexIncrementally() {
        val t0 = System.currentTimeMillis()
        val ftsAvailable = synchronized(writeLock) {
            val db = writableDatabase
            createSearchIndexTables(db)
            runCatching {
                db.rawQuery("SELECT 1 FROM sqlite_master WHERE name = 'channels_fts' AND type = 'table'", null)
                    .use { it.moveToFirst() }
            }.getOrDefault(false)
        }
        if (!ftsAvailable) return

        val (lastRowId, maxRowId) = readableDatabase.let { db ->
            val last = runCatching {
                db.rawQuery("SELECT val FROM search_meta WHERE key = 'fts_last_rowid'", null)
                    .use { if (it.moveToFirst()) it.getString(0).toLongOrNull() ?: 0L else 0L }
            }.getOrDefault(0L)
            val max = db.rawQuery("SELECT COALESCE(MAX(rowid), 0) FROM channels", null)
                .use { if (it.moveToFirst()) it.getLong(0) else 0L }
            last to max
        }

        if (maxRowId <= 0L) {
            ftsSearchReady = false
            return
        }
        if (lastRowId >= maxRowId) {
            ftsSearchReady = true
            android.util.Log.i("CasaImport", "FTS4 search index ready ($maxRowId rowids)")
            return
        }

        var cursor = lastRowId
        var totalIndexed = 0L
        val chunkSize = 50_000L
        while (cursor < maxRowId) {
            val end = (cursor + chunkSize).coerceAtMost(maxRowId)
            val inserted = synchronized(writeLock) {
                val db = writableDatabase
                db.beginTransaction()
                try {
                    db.execSQL(
                        """
                        INSERT INTO channels_fts(docid, name)
                        SELECT rowid, name FROM channels
                        WHERE rowid > ? AND rowid <= ?
                        """.trimIndent(),
                        arrayOf(cursor.toString(), end.toString()),
                    )
                    val changes = db.compileStatement("SELECT changes()").use { it.simpleQueryForLong() }
                    db.execSQL(
                        "INSERT OR REPLACE INTO search_meta(key, val) VALUES ('fts_last_rowid', ?)",
                        arrayOf(end.toString()),
                    )
                    db.execSQL(
                        "INSERT OR REPLACE INTO search_meta(key, val) VALUES ('fts_ready', ?)",
                        arrayOf(if (end >= maxRowId) "1" else "0"),
                    )
                    db.setTransactionSuccessful()
                    changes
                } finally {
                    db.endTransaction()
                }
            }
            totalIndexed += inserted
            cursor = end
            if (cursor < maxRowId && inserted > 0L) {
                try {
                    Thread.sleep(10L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        if (cursor >= maxRowId) {
            ftsSearchReady = true
            val elapsed = System.currentTimeMillis() - t0
            android.util.Log.i("CasaImport", "FTS4 search index built up to rowid $maxRowId ($totalIndexed indexed) in $elapsed ms")
            if (totalIndexed > 0L) checkpointWal(truncate = false)
        }
    }

    fun classifyContentTypesIncrementally() {
        val t0 = System.currentTimeMillis()
        val dbRead = readableDatabase
        val m3u8FixDone = runCatching {
            dbRead.rawQuery("SELECT val FROM search_meta WHERE key = 'vod_m3u8_fix_v1'", null)
                .use { if (it.moveToFirst()) it.getString(0) == "1" else false }
        }.getOrDefault(false)
        if (!m3u8FixDone) {
            val brokenIds = runCatching {
                dbRead.rawQuery(
                    "SELECT id FROM channels WHERE content_type IN ('movie', 'series') AND (url LIKE '%.m3u8%' OR url LIKE '%.ts' OR url LIKE '%.ts?%')",
                    null,
                ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            }.getOrDefault(emptyList())
            if (brokenIds.isNotEmpty()) {
                synchronized(writeLock) {
                    val wdb = writableDatabase
                    wdb.beginTransaction()
                    try {
                        brokenIds.chunked(400).forEach { chunk ->
                            val ph = chunk.joinToString(",") { "?" }
                            wdb.execSQL("UPDATE channels SET content_type = 'live' WHERE id IN ($ph)", chunk.toTypedArray())
                        }
                        wdb.setTransactionSuccessful()
                    } finally {
                        wdb.endTransaction()
                    }
                }
                invalidateCatalogCache()
                Log.i("CasaImport", "Reclassified ${brokenIds.size} .m3u8/.ts rows back to live")
            }
            synchronized(writeLock) {
                writableDatabase.execSQL("INSERT OR REPLACE INTO search_meta(key, val) VALUES ('vod_m3u8_fix_v1', '1')")
            }
        }
        val ready = runCatching {
            dbRead.rawQuery("SELECT val FROM search_meta WHERE key = 'vod_classify_ready'", null)
                .use { if (it.moveToFirst()) it.getString(0) == "1" else false }
        }.getOrDefault(false)
        val lastRowId = runCatching {
            dbRead.rawQuery("SELECT val FROM search_meta WHERE key = 'vod_classify_last_rowid'", null)
                .use { if (it.moveToFirst()) it.getString(0).toLongOrNull() ?: 0L else 0L }
        }.getOrDefault(0L)
        val maxRowId = dbRead.rawQuery("SELECT COALESCE(MAX(rowid), 0) FROM channels", null)
            .use { if (it.moveToFirst()) it.getLong(0) else 0L }

        if (maxRowId <= 0L) return
        if (ready && lastRowId >= maxRowId) {
            vodClassifyReady = true
            synchronized(writeLock) {
                val wdb = writableDatabase
                runCatching { wdb.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_type_country_number ON channels(content_type, country, number)") }
                runCatching { wdb.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_type_cat_number ON channels(content_type, category, number)") }
                runCatching { wdb.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_type_country_cat ON channels(content_type, country, category)") }
            }
            ensureVodGlobalIndexes()
            Log.i("CasaImport", "VOD content_type index ready ($maxRowId rowids)")
            return
        }

        var cursor = lastRowId
        var sinceCheckpoint = 0L
        var sinceLog = 0L
        val chunkSize = 50_000L
        while (cursor < maxRowId) {
            val end = (cursor + chunkSize).coerceAtMost(maxRowId)
            val updated = synchronized(writeLock) {
                val db = writableDatabase
                db.beginTransaction()
                try {
                    db.execSQL(
                        """
                        UPDATE channels
                        SET content_type = CASE
                            WHEN url LIKE '%.m3u8%' OR url LIKE '%.ts' OR url LIKE '%.ts?%' THEN 'live'
                            WHEN url LIKE '%/series/%' THEN 'series'
                            WHEN url LIKE '%/movie/%' THEN 'movie'
                            WHEN url LIKE '%.mkv' OR url LIKE '%.mkv?%'
                              OR url LIKE '%.mp4' OR url LIKE '%.mp4?%'
                              OR url LIKE '%.avi' OR url LIKE '%.avi?%'
                              OR url LIKE '%.m4v' OR url LIKE '%.m4v?%'
                              OR url LIKE '%.mov' OR url LIKE '%.mov?%'
                              OR url LIKE '%.webm' OR url LIKE '%.webm?%' THEN 'movie'
                            ELSE 'live'
                        END
                        WHERE rowid > ? AND rowid <= ? AND (content_type IS NULL OR content_type = '')
                        """.trimIndent(),
                        arrayOf(cursor.toString(), end.toString()),
                    )
                    val changes = db.compileStatement("SELECT changes()").use { it.simpleQueryForLong() }
                    db.execSQL(
                        "INSERT OR REPLACE INTO search_meta(key, val) VALUES ('vod_classify_last_rowid', ?)",
                        arrayOf(end.toString()),
                    )
                    db.setTransactionSuccessful()
                    changes
                } finally {
                    db.endTransaction()
                }
            }
            val step = (end - cursor).coerceAtLeast(0L)
            sinceCheckpoint += step
            sinceLog += step
            cursor = end
            if (sinceLog >= 1_000_000L) {
                sinceLog = 0L
                Log.i("CasaImport", "VOD content_type backfill progress: $cursor/$maxRowId rowids")
            }
            if (sinceCheckpoint >= 250_000L) {
                sinceCheckpoint = 0L
                checkpointWal(truncate = false)
            }
            if (cursor < maxRowId && updated > 0L) {
                try {
                    Thread.sleep(10L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        if (cursor >= maxRowId) {
            synchronized(writeLock) {
                val wdb = writableDatabase
                wdb.execSQL("INSERT OR REPLACE INTO search_meta(key, val) VALUES ('vod_classify_ready', '1')")
                runCatching { wdb.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_type_country_number ON channels(content_type, country, number)") }
                runCatching { wdb.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_type_cat_number ON channels(content_type, category, number)") }
                runCatching { wdb.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_type_country_cat ON channels(content_type, country, category)") }
            }
            ensureVodGlobalIndexes()
            vodClassifyReady = true
            invalidateCatalogCache()
            Log.i("CasaImport", "VOD content_type classification ready ($maxRowId rowids) in ${System.currentTimeMillis() - t0}ms")
            checkpointWal(truncate = true)
        }
    }

    private fun canonicalLiveGroupKeyAndCountry(name: String, rawCountry: String, tvgId: String): Pair<String, String> {
        var working = name.trim()
        var inferredCountry = rawCountry.trim().uppercase(Locale.ROOT).let {
            if (it == "UK") "GB" else if (it.length == 2 && it.all { ch -> ch in 'A'..'Z' }) it else ""
        }
        // Strip repeated leading provider/playlist prefixes like "UEFA-IT| ", "IT: ", "IT| ", "VIP: "
        for (i in 0 until 4) {
            val match = LIVE_LEADING_TAG_RE.find(working) ?: break
            val tag = (match.groupValues[1].ifBlank { match.groupValues[2] }.ifBlank { match.groupValues[3] }).trim().uppercase(Locale.ROOT)
            if (inferredCountry.isEmpty()) {
                val cc = tag.substringAfterLast('-').let { if (it == "UK") "GB" else it }
                if (cc.length == 2 && cc.all { it in 'A'..'Z' } && cc in VALID_ISO_SET) {
                    inferredCountry = cc
                }
            }
            working = working.substring(match.value.length).trimStart()
        }
        working = working
            .replace(LIVE_BRACKET_RE, " ")
            .replace("Ⓖ", " ")
            .replace("Ⓥ", " ")
            .replace(LIVE_SYMBOL_RE, " ")
            .replace(LIVE_QUALITY_TOKEN_RE, " ")
            .replace(LIVE_PUNCT_RE, " ")
            .replace(LIVE_SPACE_RE, " ")
            .trim()
            .lowercase(Locale.ROOT)

        // Strip trailing 2-letter country suffix when redundant (e.g. "channel 1 it" -> "channel 1")
        if (working.length > 4 && working[working.length - 3] == ' ') {
            val suffix = working.takeLast(2).uppercase(Locale.ROOT)
            val normSuffix = if (suffix == "UK") "GB" else suffix
            if (normSuffix in VALID_ISO_SET && (inferredCountry.isEmpty() || inferredCountry == normSuffix)) {
                if (inferredCountry.isEmpty()) inferredCountry = normSuffix
                working = working.dropLast(3).trim()
            }
        }
        if (working.isBlank()) {
            val tvgBase = tvgId.substringBefore('@').trim().lowercase(Locale.ROOT)
            working = tvgBase.ifBlank { name.trim().lowercase(Locale.ROOT) }
        }
        val effectiveCountry = inferredCountry.ifBlank { "XX" }
        return "$effectiveCountry|$working" to effectiveCountry
    }

    private fun livePrimaryScore(
        name: String,
        url: String,
        logo: String?,
        quality: String,
        number: Int,
    ): Int {
        val lowName = name.lowercase(Locale.ROOT)
        val lowUrl = url.trim().lowercase(Locale.ROOT)
        var score = 0
        if (lowUrl.startsWith("https://")) score -= 25 else if (lowUrl.startsWith("http://")) score += 20
        if ("geo-blocked" in lowName || "Ⓖ" in name || "not 24/7" in lowName) score += 120
        if (':' !in name && '|' !in name && "vip" !in lowName && '[' !in name) score -= 18
        if (!logo.isNullOrBlank()) score -= 10
        when {
            "1080" in lowName || quality.equals("1080p", true) || quality.equals("FHD", true) -> score -= 6
            "720" in lowName || "hd" in lowName || quality.equals("HD", true) -> score -= 4
            "4k" in lowName || "uhd" in lowName || "2160" in lowName || quality.equals("4K", true) -> score += 8
            quality.equals("SD", true) -> score += 4
        }
        if (number in 1..999) score -= 2
        return score
    }

    private fun triggerBackgroundLiveCanonicalRebuild() {
        if (!liveCanonRebuilding.compareAndSet(false, true)) return
        Thread(
            {
                try {
                    rebuildLiveCanonicalMapSync()
                } catch (err: Throwable) {
                    Log.w("CasaDB", "Background live canonical rebuild failed: ${err.message}")
                } finally {
                    liveCanonRebuilding.set(false)
                }
            },
            "CasaLiveCanonicalRebuild",
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun rebuildLiveCanonicalMapSync(force: Boolean = false) {
        synchronized(liveCanonLock) {
            val t0 = System.currentTimeMillis()
            val dbRead = readableDatabase
            val maxRowId = dbRead.rawQuery("SELECT COALESCE(MAX(rowid), 0) FROM channels", null).use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
            if (maxRowId <= 0L) {
                liveCanonReady = false
                return
            }
            if (!force) {
                val ver = runCatching {
                    dbRead.rawQuery("SELECT val FROM search_meta WHERE key = 'live_canon_version'", null)
                        .use { if (it.moveToFirst()) it.getString(0) else "" }
                }.getOrDefault("")
                val savedMax = runCatching {
                    dbRead.rawQuery("SELECT val FROM search_meta WHERE key = 'live_canon_max_rowid'", null)
                        .use { if (it.moveToFirst()) it.getString(0).toLongOrNull() ?: -1L else -1L }
                }.getOrDefault(-1L)
                val hasRows = runCatching {
                    dbRead.rawQuery("SELECT 1 FROM live_canonical_map LIMIT 1", null).use { it.moveToFirst() }
                }.getOrDefault(false)
                if (ver == LIVE_CANON_VERSION && savedMax == maxRowId && hasRows) {
                    liveCanonReady = true
                    return
                }
            }

            class GroupBest(
                var bestId: String,
                var bestScore: Int,
                var bestNumber: Int,
                var count: Int,
            )

            val enabledWhere = enabledChannelWhere()
            val liveWhere = if (vodClassifyReady) {
                "c.content_type = 'live'"
            } else {
                "(c.content_type NOT IN ('movie', 'series') AND c.url NOT LIKE '%/movie/%' AND c.url NOT LIKE '%/series/%')"
            }
            val groups = HashMap<String, GroupBest>(65_536)
            val entries = ArrayList<Pair<String, String>>(120_000)

            dbRead.rawQuery(
                "SELECT c.id, c.name, c.url, c.logo, c.country, c.tvg_id, c.quality, c.number FROM channels c WHERE $liveWhere AND $enabledWhere",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1).orEmpty()
                    val url = cursor.getString(2).orEmpty()
                    val logo = cursor.getString(3)
                    val country = cursor.getString(4).orEmpty()
                    val tvgId = cursor.getString(5).orEmpty()
                    val quality = cursor.getString(6).orEmpty()
                    val number = cursor.getInt(7)

                    val (gKey, _) = canonicalLiveGroupKeyAndCountry(name, country, tvgId)
                    val sc = livePrimaryScore(name, url, logo, quality, number)
                    entries.add(id to gKey)
                    val existing = groups[gKey]
                    if (existing == null) {
                        groups[gKey] = GroupBest(id, sc, number, 1)
                    } else {
                        existing.count += 1
                        if (sc < existing.bestScore || (sc == existing.bestScore && number < existing.bestNumber)) {
                            existing.bestId = id
                            existing.bestScore = sc
                            existing.bestNumber = number
                        }
                    }
                }
            }

            synchronized(writeLock) {
                val wdb = writableDatabase
                createLiveCanonicalTable(wdb)
                wdb.beginTransaction()
                try {
                    wdb.delete("live_canonical_map", null, null)
                    wdb.setTransactionSuccessful()
                } finally {
                    wdb.endTransaction()
                }

                var i = 0
                val n = entries.size
                while (i < n) {
                    val end = (i + 10_000).coerceAtMost(n)
                    wdb.beginTransaction()
                    try {
                        wdb.compileStatement(
                            "INSERT OR REPLACE INTO live_canonical_map(channel_id, group_key, is_primary, source_count) VALUES (?, ?, ?, ?)",
                        ).use { stmt ->
                            for (idx in i until end) {
                                val (id, gKey) = entries[idx]
                                val grp = groups[gKey]
                                val isPrim = if (grp != null && grp.bestId == id) 1L else 0L
                                val cnt = (grp?.count ?: 1).toLong()
                                stmt.clearBindings()
                                stmt.bindString(1, id)
                                stmt.bindString(2, gKey)
                                stmt.bindLong(3, isPrim)
                                stmt.bindLong(4, cnt)
                                stmt.executeInsert()
                            }
                        }
                        wdb.setTransactionSuccessful()
                    } finally {
                        wdb.endTransaction()
                    }
                    i = end
                }

                wdb.beginTransaction()
                try {
                    wdb.execSQL("INSERT OR REPLACE INTO search_meta(key, val) VALUES ('live_canon_version', ?)", arrayOf(LIVE_CANON_VERSION))
                    wdb.execSQL("INSERT OR REPLACE INTO search_meta(key, val) VALUES ('live_canon_max_rowid', ?)", arrayOf(maxRowId.toString()))
                    wdb.setTransactionSuccessful()
                } finally {
                    wdb.endTransaction()
                }
            }

            liveCanonReady = true
            invalidateCatalogCache()
            Log.i("CasaDB", "Live canonical map rebuilt: ${groups.size} unique channels from ${entries.size} live streams in ${System.currentTimeMillis() - t0}ms")
            runCatching { refreshCatalogStatsSync() }
        }
    }

    fun checkpointWal(truncate: Boolean = false) {
        runCatching {
            val mode = if (truncate) "TRUNCATE" else "PASSIVE"
            val t0 = System.currentTimeMillis()
            val (busy, logPages, checkpointed) = synchronized(writeLock) {
                writableDatabase.rawQuery("PRAGMA wal_checkpoint($mode)", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        Triple(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2))
                    } else {
                        Triple(0, 0, 0)
                    }
                }
            }
            val elapsed = System.currentTimeMillis() - t0
            if (truncate || logPages > 1000) {
                Log.i(
                    "CasaDB",
                    "WAL checkpoint($mode) in ${elapsed}ms (busy=$busy, log=$logPages, checkpointed=$checkpointed)",
                )
            }
        }.onFailure {
            Log.w("CasaDB", "WAL checkpoint failed: ${it.message}")
        }
    }

    private fun createChannelsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS channels (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                url TEXT NOT NULL,
                logo TEXT,
                category TEXT NOT NULL DEFAULT 'general',
                language TEXT NOT NULL DEFAULT '',
                country TEXT NOT NULL DEFAULT '',
                tvg_id TEXT NOT NULL DEFAULT '',
                quality TEXT NOT NULL DEFAULT '',
                number INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                content_type TEXT NOT NULL DEFAULT 'live'
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_number ON channels(number)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_category ON channels(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_country ON channels(country)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_tvg_id ON channels(tvg_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_name_nocase ON channels(name COLLATE NOCASE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_language ON channels(language)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_country_number ON channels(country, number)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_category_number ON channels(category, number)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_clients_last_seen ON clients(last_seen)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_profile_library_fav ON profile_library(profile_id, favorite)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_profile_library_fav_item ON profile_library(profile_id, favorite, item_id)")
        createPlaylistsTable(db)
        createPlaylistChannelsTable(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_playlist_position ON playlist_channels(playlist_id, position, channel_id)")
    }

    /**
     * Creates heavy multi-million-row covering indexes on a background thread
     * instead of blocking SQLiteOpenHelper.onOpen() during Main Thread startup.
     */
    fun ensurePerformanceIndexes() = synchronized(writeLock) {
        val t0 = System.currentTimeMillis()
        val db = writableDatabase
        runCatching {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pc_playlist_position ON playlist_channels(playlist_id, position, channel_id)")
        }
        runCatching {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_profile_library_fav_item ON profile_library(profile_id, favorite, item_id)")
        }
        android.util.Log.i("CasaImport", "Performance indexes verified in ${System.currentTimeMillis() - t0} ms")
    }

    /**
     * Ensures global and partial VOD covering indexes exist without blocking callers on a single long lock.
     */
    fun ensureVodGlobalIndexes() {
        if (vodGlobalIndexesReady) return
        synchronized(vodGlobalIndexesLock) {
            if (vodGlobalIndexesReady) return
            val targetIndexes = listOf(
                "idx_channels_type_number" to "CREATE INDEX IF NOT EXISTS idx_channels_type_number ON channels(content_type, number)",
                "idx_channels_type_name_number" to "CREATE INDEX IF NOT EXISTS idx_channels_type_name_number ON channels(content_type, name COLLATE NOCASE, number)",
                "idx_channels_vod_name_number" to "CREATE INDEX IF NOT EXISTS idx_channels_vod_name_number ON channels(name COLLATE NOCASE, number) WHERE content_type IN ('movie', 'series')",
                "idx_channels_vod_number" to "CREATE INDEX IF NOT EXISTS idx_channels_vod_number ON channels(number) WHERE content_type IN ('movie', 'series')",
            )
            val existing = runCatching {
                readableDatabase.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND name IN ('idx_channels_type_number','idx_channels_type_name_number','idx_channels_vod_name_number','idx_channels_vod_number')",
                    null,
                ).use { c ->
                    buildSet {
                        while (c.moveToNext()) add(c.getString(0))
                    }
                }
            }.getOrDefault(emptySet())
            if (existing.size == targetIndexes.size) {
                vodGlobalIndexesReady = true
                return
            }
            val started = System.currentTimeMillis()
            for ((name, sql) in targetIndexes) {
                if (name in existing) continue
                val tIndex = System.currentTimeMillis()
                synchronized(writeLock) {
                    runCatching { writableDatabase.execSQL(sql) }
                        .onFailure { Log.w("CasaImport", "Failed creating index $name: ${it.message}") }
                }
                Log.i("CasaImport", "Created VOD index $name in ${System.currentTimeMillis() - tIndex} ms")
            }
            val finalCount = runCatching {
                readableDatabase.rawQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name IN ('idx_channels_type_number','idx_channels_type_name_number','idx_channels_vod_name_number','idx_channels_vod_number')",
                    null,
                ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            }.getOrDefault(0)
            if (finalCount == targetIndexes.size) {
                vodGlobalIndexesReady = true
                Log.i("CasaImport", "All global VOD indexes ready in ${System.currentTimeMillis() - started} ms")
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createChannelsTable(db)
    }

    private fun ensureColumn(db: SQLiteDatabase, table: String, column: String, alterSql: String) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            var found = false
            while (cursor.moveToNext()) {
                if (nameIdx >= 0 && cursor.getString(nameIdx).equals(column, ignoreCase = true)) {
                    found = true
                    break
                }
            }
            found
        }
        if (!exists) {
            runCatching { db.execSQL(alterSql) }
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureColumn(
            db,
            "profile_library",
            "watch_count",
            "ALTER TABLE profile_library ADD COLUMN watch_count INTEGER NOT NULL DEFAULT 0",
        )
        ensureColumn(
            db,
            "profile_library",
            "position_ms",
            "ALTER TABLE profile_library ADD COLUMN position_ms INTEGER NOT NULL DEFAULT 0",
        )
        ensureColumn(
            db,
            "profile_library",
            "duration_ms",
            "ALTER TABLE profile_library ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0",
        )
        ensureColumn(
            db,
            "channels",
            "playlist_id",
            "ALTER TABLE channels ADD COLUMN playlist_id TEXT NOT NULL DEFAULT 'default'",
        )
        ensureColumn(
            db,
            "channels",
            "content_type",
            "ALTER TABLE channels ADD COLUMN content_type TEXT NOT NULL DEFAULT ''",
        )
        createPlaylistsTable(db)
        ensureColumn(
            db,
            "playlists",
            "status",
            "ALTER TABLE playlists ADD COLUMN status TEXT NOT NULL DEFAULT 'ok'",
        )
        ensureColumn(
            db,
            "playlists",
            "last_error",
            "ALTER TABLE playlists ADD COLUMN last_error TEXT NOT NULL DEFAULT ''",
        )
        ensureColumn(
            db,
            "playlists",
            "content_hash",
            "ALTER TABLE playlists ADD COLUMN content_hash TEXT NOT NULL DEFAULT ''",
        )
        createPlaylistChannelsTable(db)
        createSearchIndexTables(db)
        createLiveCanonicalTable(db)
        createDiagnosticLogsTable(db)
        createExtraTables(db)
        runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_tvg_id ON channels(tvg_id)") }
        runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_name_nocase ON channels(name COLLATE NOCASE)") }
        runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_language ON channels(language)") }
        runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_country_number ON channels(country, number)") }
        runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_category_number ON channels(category, number)") }
        runCatching {
            val idxCount = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name IN ('idx_channels_type_number','idx_channels_type_name_number','idx_channels_vod_name_number','idx_channels_vod_number')",
                null,
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (idxCount == 4) vodGlobalIndexesReady = true
        }
        runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_clients_last_seen ON clients(last_seen)") }
        runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_profile_library_fav ON profile_library(profile_id, favorite)") }
        statsCache = null
        disabledPlaylistsCache = null
        vodClassifyReady = runCatching {
            db.rawQuery("SELECT val FROM search_meta WHERE key = 'vod_classify_ready'", null)
                .use { if (it.moveToFirst()) it.getString(0) == "1" else false }
        }.getOrDefault(false)
        liveCanonReady = runCatching {
            val ver = db.rawQuery("SELECT val FROM search_meta WHERE key = 'live_canon_version'", null)
                .use { if (it.moveToFirst()) it.getString(0) else "" }
            ver == LIVE_CANON_VERSION && db.rawQuery("SELECT 1 FROM live_canonical_map LIMIT 1", null).use { it.moveToFirst() }
        }.getOrDefault(false)
        runCatching {
            val linkCount = db.rawQuery("SELECT COUNT(*) FROM playlist_channels", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
            if (linkCount == 0) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO playlist_channels(playlist_id, channel_id, position)
                    SELECT playlist_id, id, number FROM channels
                    WHERE playlist_id IS NOT NULL AND playlist_id != ''
                    """.trimIndent(),
                )
            }
        }
    }

    private fun createPlaylistsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                url TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                channel_count INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'ok',
                last_error TEXT NOT NULL DEFAULT '',
                content_hash TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
    }

    private fun createPlaylistChannelsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlist_channels (
                playlist_id TEXT NOT NULL,
                channel_id TEXT NOT NULL,
                position INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (playlist_id, channel_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playlist_channels_channel ON playlist_channels(channel_id)")
        runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_playlist_id ON channels(playlist_id)") }
    }

    private fun createDiagnosticLogsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS diagnostic_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts_ms INTEGER NOT NULL,
                source TEXT NOT NULL DEFAULT 'tv',
                severity TEXT NOT NULL DEFAULT 'warn',
                code TEXT NOT NULL,
                device_id TEXT NOT NULL DEFAULT '',
                device_name TEXT NOT NULL DEFAULT '',
                app_version TEXT NOT NULL DEFAULT '',
                client_ip TEXT NOT NULL DEFAULT '',
                channel_id TEXT NOT NULL DEFAULT '',
                channel_name TEXT NOT NULL DEFAULT '',
                playback_mode TEXT NOT NULL DEFAULT '',
                relay_key TEXT NOT NULL DEFAULT '',
                stream_url TEXT NOT NULL DEFAULT '',
                message TEXT NOT NULL DEFAULT '',
                details_json TEXT NOT NULL DEFAULT '{}'
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_diag_ts ON diagnostic_logs(ts_ms)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_diag_code_ts ON diagnostic_logs(code, ts_ms)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_diag_channel_ts ON diagnostic_logs(channel_id, ts_ms)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_diag_device_ts ON diagnostic_logs(device_id, ts_ms)")
    }

    private fun createExtraTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS channel_source_prefs (
                group_key TEXT PRIMARY KEY,
                preferred_url TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS source_health (
                url TEXT PRIMARY KEY,
                stall_count INTEGER NOT NULL DEFAULT 0,
                fail_count INTEGER NOT NULL DEFAULT 0,
                success_count INTEGER NOT NULL DEFAULT 0,
                avg_latency_ms INTEGER NOT NULL DEFAULT 0,
                last_stall_at INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vod_posters (
                norm_title TEXT PRIMARY KEY,
                poster_url TEXT NOT NULL,
                checked_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun ensureSourceMetaLoaded() {
        if (sourceMetaLoaded) return
        synchronized(sourceMetaLoadLock) {
            if (sourceMetaLoaded) return
            runCatching {
                val db = readableDatabase
                db.rawQuery("SELECT group_key, preferred_url FROM channel_source_prefs", null).use { c ->
                    while (c.moveToNext()) {
                        val g = c.getString(0)?.trim().orEmpty()
                        val u = c.getString(1)?.trim().orEmpty()
                        if (g.isNotBlank() && u.isNotBlank()) sourcePrefsCache[g] = u
                    }
                }
                db.rawQuery(
                    "SELECT url, stall_count, fail_count, success_count, avg_latency_ms, last_stall_at, updated_at FROM source_health",
                    null,
                ).use { c ->
                    while (c.moveToNext()) {
                        val u = c.getString(0)?.trim().orEmpty()
                        if (u.isNotBlank()) {
                            sourceHealthCache[u] = PersistedSourceHealth(
                                stallCount = c.getInt(1),
                                failCount = c.getInt(2),
                                successCount = c.getInt(3),
                                avgLatencyMs = c.getLong(4),
                                lastStallAt = c.getLong(5),
                                updatedAt = c.getLong(6),
                            )
                        }
                    }
                }
            }
            sourceMetaLoaded = true
        }
    }

    fun canonicalGroupKeyForChannel(channel: IptvChannel): String {
        if (channel.isVod) return "vod:${channel.id}"
        if (liveCanonReady) {
            val fromMap = runCatching {
                readableDatabase.rawQuery(
                    "SELECT group_key FROM live_canonical_map WHERE channel_id = ? LIMIT 1",
                    arrayOf(channel.id),
                ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            if (!fromMap.isNullOrBlank()) return fromMap
        }
        return canonicalLiveGroupKeyAndCountry(channel.name, channel.country, channel.tvgId).first
            .ifBlank { channel.id }
    }

    fun canonicalGroupKeyForChannelId(channelId: String): String? {
        if (channelId.isBlank()) return null
        if (liveCanonReady) {
            val fromMap = runCatching {
                readableDatabase.rawQuery(
                    "SELECT group_key FROM live_canonical_map WHERE channel_id = ? LIMIT 1",
                    arrayOf(channelId),
                ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            if (!fromMap.isNullOrBlank()) return fromMap
        }
        val ch = getChannel(channelId) ?: return null
        return canonicalGroupKeyForChannel(ch)
    }

    fun getPreferredSource(groupKey: String): String? {
        if (groupKey.isBlank()) return null
        ensureSourceMetaLoaded()
        return sourcePrefsCache[groupKey]
    }

    fun setPreferredSource(groupKey: String, url: String) {
        val cleanGroup = groupKey.trim()
        val cleanUrl = url.trim()
        if (cleanGroup.isBlank() || cleanUrl.isBlank()) return
        ensureSourceMetaLoaded()
        val now = System.currentTimeMillis()
        sourcePrefsCache[cleanGroup] = cleanUrl
        synchronized(writeLock) {
            runCatching {
                val db = writableDatabase
                createExtraTables(db)
                val cv = ContentValues().apply {
                    put("group_key", cleanGroup)
                    put("preferred_url", cleanUrl)
                    put("updated_at", now)
                }
                db.insertWithOnConflict("channel_source_prefs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun clearPreferredSource(groupKey: String) {
        val cleanGroup = groupKey.trim()
        if (cleanGroup.isBlank()) return
        ensureSourceMetaLoaded()
        sourcePrefsCache.remove(cleanGroup)
        synchronized(writeLock) {
            runCatching {
                writableDatabase.delete("channel_source_prefs", "group_key = ?", arrayOf(cleanGroup))
            }
        }
    }

    fun recordSourceFeedback(
        groupKey: String?,
        url: String,
        event: String,
        latencyMs: Long = -1L,
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        ensureSourceMetaLoaded()
        val now = System.currentTimeMillis()
        val cleanEvent = event.trim().lowercase(Locale.ROOT)
        val prev = sourceHealthCache[cleanUrl] ?: PersistedSourceHealth(0, 0, 0, 0L, 0L, 0L)
        val next = when (cleanEvent) {
            "manual_select" -> {
                if (!groupKey.isNullOrBlank()) setPreferredSource(groupKey, cleanUrl)
                prev.copy(
                    stallCount = 0,
                    failCount = 0,
                    successCount = (prev.successCount + 1).coerceAtMost(50),
                    avgLatencyMs = if (latencyMs > 0L) latencyMs else prev.avgLatencyMs,
                    lastStallAt = 0L,
                    updatedAt = now,
                )
            }
            "stall", "buffering" -> {
                val recentStall = prev.lastStallAt > 0L && (now - prev.lastStallAt) < 15 * 60_000L
                val newStalls = if (recentStall) (prev.stallCount + 1).coerceAtMost(20) else 1
                if (!groupKey.isNullOrBlank() && newStalls >= 2 && sourcePrefsCache[groupKey] == cleanUrl) {
                    clearPreferredSource(groupKey)
                }
                prev.copy(
                    stallCount = newStalls,
                    lastStallAt = now,
                    avgLatencyMs = if (latencyMs > 0L) latencyMs else prev.avgLatencyMs,
                    updatedAt = now,
                )
            }
            "error" -> {
                val newFails = (prev.failCount + 1).coerceAtMost(20)
                if (!groupKey.isNullOrBlank() && newFails >= 2 && sourcePrefsCache[groupKey] == cleanUrl) {
                    clearPreferredSource(groupKey)
                }
                prev.copy(
                    failCount = newFails,
                    updatedAt = now,
                )
            }
            "healthy" -> {
                val emaLatency = when {
                    latencyMs <= 0L -> prev.avgLatencyMs
                    prev.avgLatencyMs <= 0L -> latencyMs
                    else -> ((prev.avgLatencyMs * 7L) + (latencyMs * 3L)) / 10L
                }
                prev.copy(
                    stallCount = (prev.stallCount - 1).coerceAtLeast(0),
                    failCount = 0,
                    successCount = (prev.successCount + 1).coerceAtMost(50),
                    avgLatencyMs = emaLatency,
                    updatedAt = now,
                )
            }
            else -> prev.copy(updatedAt = now)
        }
        sourceHealthCache[cleanUrl] = next
        synchronized(writeLock) {
            runCatching {
                val db = writableDatabase
                createExtraTables(db)
                val cv = ContentValues().apply {
                    put("url", cleanUrl)
                    put("stall_count", next.stallCount)
                    put("fail_count", next.failCount)
                    put("success_count", next.successCount)
                    put("avg_latency_ms", next.avgLatencyMs)
                    put("last_stall_at", next.lastStallAt)
                    put("updated_at", next.updatedAt)
                }
                db.insertWithOnConflict("source_health", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun persistentSourceScoreAdjustment(groupKey: String?, url: String): Int {
        ensureSourceMetaLoaded()
        val cleanUrl = url.trim()
        val entry = sourceHealthCache[cleanUrl]
        val now = System.currentTimeMillis()
        val recentStall = entry != null && entry.lastStallAt > 0L && (now - entry.lastStallAt) < 24L * 3600_000L
        var adj = 0
        val prefUrl = if (!groupKey.isNullOrBlank()) sourcePrefsCache[groupKey] else null
        if (prefUrl == cleanUrl && (!recentStall || (entry?.stallCount ?: 0) == 0)) {
            adj -= 250
        }
        if (entry != null) {
            if (recentStall && entry.stallCount > 0) {
                adj += (entry.stallCount * 80).coerceAtMost(400)
            }
            if (entry.failCount > 0 && (now - entry.updatedAt) < 12L * 3600_000L) {
                adj += (entry.failCount * 60).coerceAtMost(300)
            }
            when {
                entry.avgLatencyMs > 2500L -> adj += 45
                entry.avgLatencyMs > 1800L -> adj += 25
                entry.avgLatencyMs > 1200L -> adj += 10
            }
            if (entry.successCount > 0 && (!recentStall || entry.stallCount == 0) && entry.failCount == 0) {
                adj -= (15 + entry.successCount * 8).coerceAtMost(60)
            }
        }
        return adj
    }

    fun sourceStallCount(url: String): Int {
        ensureSourceMetaLoaded()
        return sourceHealthCache[url.trim()]?.stallCount ?: 0
    }

    fun findSiblingVodLogo(channel: IptvChannel): String? {
        if (!channel.isVod) return null
        val baseTitle = if (channel.contentType == "series") {
            M3uParser.extractSeriesBaseTitle(channel.name)?.takeIf { it.isNotBlank() }
        } else null
        val lookup = (baseTitle ?: channel.name).trim()
        if (lookup.length < 2) return null
        val db = readableDatabase
        val likePrefix = escapeSqlLikeLiteral(lookup) + "%"
        return runCatching {
            db.rawQuery(
                "SELECT logo FROM channels WHERE content_type = ? AND name LIKE ? ESCAPE '\\' AND logo IS NOT NULL AND logo != '' LIMIT 1",
                arrayOf(channel.contentType, likePrefix),
            ).use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
        }.getOrNull()
    }

    fun getVodPosterCache(normTitle: String): Pair<String, Long>? {
        val cleanKey = normTitle.trim()
        if (cleanKey.isBlank()) return null
        return runCatching {
            readableDatabase.rawQuery(
                "SELECT poster_url, checked_at FROM vod_posters WHERE norm_title = ? LIMIT 1",
                arrayOf(cleanKey),
            ).use { c ->
                if (c.moveToFirst()) c.getString(0).orEmpty() to c.getLong(1) else null
            }
        }.getOrNull()
    }

    fun putVodPosterCache(normTitle: String, posterUrl: String) {
        val cleanKey = normTitle.trim()
        if (cleanKey.isBlank()) return
        val now = System.currentTimeMillis()
        synchronized(writeLock) {
            runCatching {
                val db = writableDatabase
                createExtraTables(db)
                val cv = ContentValues().apply {
                    put("norm_title", cleanKey)
                    put("poster_url", posterUrl.trim())
                    put("checked_at", now)
                }
                db.insertWithOnConflict("vod_posters", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun updateChannelLogo(channelId: String, logoUrl: String, seriesBaseTitle: String = "") {
        val cleanUrl = logoUrl.trim()
        if (channelId.isBlank() || cleanUrl.isBlank()) return
        synchronized(writeLock) {
            runCatching {
                val db = writableDatabase
                val cv = ContentValues().apply { put("logo", cleanUrl) }
                db.update("channels", cv, "id = ?", arrayOf(channelId))
                if (seriesBaseTitle.trim().length >= 3) {
                    val likePrefix = escapeSqlLikeLiteral(seriesBaseTitle.trim()) + "%"
                    db.execSQL(
                        "UPDATE channels SET logo = ? WHERE content_type = 'series' AND name LIKE ? ESCAPE '\\' AND (logo IS NULL OR logo = '')",
                        arrayOf(cleanUrl, likePrefix),
                    )
                }
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Profiles                                                            */
    /* ------------------------------------------------------------------ */

    fun listProfiles(): List<ProfileRow> {
        return readableDatabase.rawQuery(
            "SELECT * FROM profiles ORDER BY CASE WHEN id = 'guest' THEN 1 ELSE 0 END ASC, created_at ASC, name COLLATE NOCASE ASC",
            null,
        ).use { cursor -> cursor.mapAll(::readProfile) }
    }

    fun getProfile(id: String?): ProfileRow? {
        if (id.isNullOrBlank()) return null
        return readableDatabase.rawQuery(
            "SELECT * FROM profiles WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) readProfile(cursor) else null }
    }

    fun defaultProfile(language: String = "it"): ProfileRow {
        cachedDefaultProfile?.let { return it }
        listProfiles().firstOrNull { it.id != "guest" }?.let { found ->
            cachedDefaultProfile = found
            return found
        }
        return synchronized(writeLock) {
            cachedDefaultProfile?.let { return@synchronized it }
            val created = listProfiles().firstOrNull { it.id != "guest" } ?: createProfile(
                name = if (language.take(2) == "en") "Main" else if (language.take(2) == "es") "Principal" else "Principale",
                avatar = PROFILE_AVATARS.first(),
                language = language.take(2),
                country = "IT",
            )
            cachedDefaultProfile = created
            created
        }
    }

    fun createProfile(name: String, avatar: String, language: String, country: String): ProfileRow =
        synchronized(writeLock) {
            val now = System.currentTimeMillis() / 1000
            val base = name.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "profilo" }
                .take(22)
            val id = "$base-${UUID.randomUUID().toString().take(8)}"
            val values = ContentValues().apply {
                put("id", id)
                put("name", name.trim().ifBlank { "Profilo" }.take(28))
                put("avatar", avatar.ifBlank { PROFILE_AVATARS.first() })
                put("language", normalizeLanguage(language))
                put("country", normalizeCountry(country))
                put("created_at", now)
                put("updated_at", now)
            }
            writableDatabase.insertOrThrow("profiles", null, values)
            cachedDefaultProfile = null
            getProfile(id)!!
        }

    fun updateProfile(
        id: String,
        name: String,
        avatar: String,
        language: String,
        country: String,
    ): ProfileRow? = synchronized(writeLock) {
        val existing = getProfile(id) ?: return null
        val values = ContentValues().apply {
            put("name", name.trim().ifBlank { existing.name }.take(28))
            put("avatar", avatar.ifBlank { existing.avatar })
            put("language", normalizeLanguage(language))
            put("country", normalizeCountry(country))
            put("updated_at", System.currentTimeMillis() / 1000)
        }
        writableDatabase.update("profiles", values, "id = ?", arrayOf(id))
        cachedDefaultProfile = null
        getProfile(id)
    }

    fun deleteProfile(id: String): Boolean = synchronized(writeLock) {
        if (id == "guest") return false
        val count = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM profiles WHERE id <> 'guest'",
            null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        if (count <= 1) return false
        val removed = writableDatabase.delete("profiles", "id = ?", arrayOf(id)) > 0
        writableDatabase.delete("profile_library", "profile_id = ?", arrayOf(id))
        if (removed) cachedDefaultProfile = null
        removed
    }

    fun ensureGuestProfile() = synchronized(writeLock) {
        val exists = readableDatabase.rawQuery(
            "SELECT id FROM profiles WHERE id = 'guest' LIMIT 1",
            null,
        ).use { it.moveToFirst() }
        if (!exists) {
            val now = System.currentTimeMillis() / 1000
            writableDatabase.execSQL(
                """
                INSERT INTO profiles(id, name, avatar, language, country, created_at, updated_at)
                VALUES ('guest', 'Ospite', 'slate', 'it', 'IT', ?, ?)
                """.trimIndent(),
                arrayOf(now, now),
            )
        }
    }

    fun clearGuestProfile() = synchronized(writeLock) {
        writableDatabase.delete("profile_library", "profile_id = ?", arrayOf("guest"))
    }

    fun setFavorite(profileId: String, itemId: String, favorite: Boolean) {
        synchronized(writeLock) {
            val db = writableDatabase
            db.execSQL(
                """
                INSERT INTO profile_library(profile_id, item_id, favorite)
                VALUES (?, ?, ?)
                ON CONFLICT(profile_id, item_id) DO UPDATE SET favorite = excluded.favorite
                """.trimIndent(),
                arrayOf(profileId, itemId, if (favorite) 1 else 0),
            )
        }
    }

    fun favoriteIds(profileId: String): Set<String> {
        return readableDatabase.rawQuery(
            "SELECT item_id FROM profile_library WHERE profile_id = ? AND favorite = 1",
            arrayOf(profileId),
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    fun markPlayed(profileId: String, itemId: String) {
        synchronized(writeLock) {
            writableDatabase.execSQL(
                """
                INSERT INTO profile_library(profile_id, item_id, favorite, last_played_at, watch_count)
                VALUES (?, ?, 0, ?, 1)
                ON CONFLICT(profile_id, item_id)
                DO UPDATE SET last_played_at = excluded.last_played_at,
                              watch_count = COALESCE(profile_library.watch_count, 0) + 1
                """.trimIndent(),
                arrayOf(profileId, itemId, System.currentTimeMillis() / 1000),
            )
        }
    }

    fun mostWatchedIds(profileId: String, limit: Int = 16): List<String> {
        return readableDatabase.rawQuery(
            """
            SELECT item_id FROM profile_library
            WHERE profile_id = ? AND (watch_count > 0 OR last_played_at IS NOT NULL)
            ORDER BY COALESCE(watch_count, 0) DESC, last_played_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(profileId, limit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    fun recentIds(profileId: String, limit: Int = 20): List<String> {
        return readableDatabase.rawQuery(
            """
            SELECT item_id FROM profile_library
            WHERE profile_id = ? AND last_played_at IS NOT NULL AND last_played_at > 0
            ORDER BY last_played_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(profileId, limit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* IPTV channels (M3U playlists)                                       */
    /* ------------------------------------------------------------------ */

    fun warmCatalogStats() {
        runCatching { refreshCatalogStatsSync() }
            .onFailure { Log.w("CasaDB", "warmCatalogStats failed: ${it.message}") }
    }

    private fun triggerBackgroundStatsRefresh() {
        if (!statsRefreshing.compareAndSet(false, true)) return
        Thread(
            {
                try {
                    refreshCatalogStatsSync()
                } catch (err: Throwable) {
                    Log.w("CasaDB", "Background catalog stats refresh failed: ${err.message}")
                } finally {
                    statsRefreshing.set(false)
                }
            },
            "CasaCatalogStatsRefresh",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun computeCatalogStats(): CatalogStatsCache {
        statsCache?.let { return it }
        staleStatsCache?.let { stale ->
            triggerBackgroundStatsRefresh()
            return stale
        }
        loadPersistedCatalogStats()?.let { persisted ->
            statsCache = persisted
            staleStatsCache = persisted
            lastKnownChannelCount = persisted.channelCount
            triggerBackgroundStatsRefresh()
            return persisted
        }
        return refreshCatalogStatsSync()
    }

    private fun refreshCatalogStatsSync(): CatalogStatsCache {
        synchronized(statsComputeLock) {
            statsCache?.let { return it }
            val startedMs = System.currentTimeMillis()
            val db = readableDatabase
            var freshStats: CatalogStatsCache? = null
            for (attempt in 0..1) {
                val genBefore = statsGeneration.get()
                val enabledWhere = enabledChannelWhere()
                var liveStreams = 0
                var movies = 0
                var series = 0
                var other = 0
                db.rawQuery("SELECT c.content_type, COUNT(*) FROM channels c WHERE $enabledWhere GROUP BY c.content_type", null).use { c ->
                    while (c.moveToNext()) {
                        val ct = c.getString(0).orEmpty().trim().lowercase(Locale.ROOT)
                        val cnt = c.getInt(1)
                        when (ct) {
                            "movie" -> movies += cnt
                            "series" -> series += cnt
                            "live", "" -> liveStreams += cnt
                            else -> other += cnt
                        }
                    }
                }
                val totalStreams = liveStreams + movies + series + other
                val useCanon = liveCanonReady && !hasDisabledPlaylists()
                val liveFilterWhere = if (vodClassifyReady) {
                    "c.content_type = 'live'"
                } else {
                    "(c.content_type NOT IN ('movie', 'series') AND c.url NOT LIKE '%/movie/%' AND c.url NOT LIKE '%/series/%')"
                }

                val uniqueLiveCount = if (useCanon) {
                    db.rawQuery("SELECT COUNT(*) FROM live_canonical_map WHERE is_primary = 1", null).use {
                        if (it.moveToFirst()) it.getInt(0) else 0
                    }.let { if (it > 0) it else liveStreams }
                } else {
                    if (totalStreams > 0) triggerBackgroundLiveCanonicalRebuild()
                    liveStreams
                }

                val countryCounts = LinkedHashMap<String, Int>()
                val countrySql = if (useCanon) {
                    "SELECT c.country, COUNT(*) FROM live_canonical_map m INNER JOIN channels c ON c.id = m.channel_id WHERE m.is_primary = 1 AND c.country != '' AND $enabledWhere GROUP BY c.country"
                } else {
                    "SELECT c.country, COUNT(*) FROM channels c WHERE c.country != '' AND $liveFilterWhere AND $enabledWhere GROUP BY c.country"
                }
                db.rawQuery(countrySql, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val code = cursor.getString(0)?.trim()?.uppercase(Locale.US).orEmpty()
                        if (code.isNotEmpty()) {
                            countryCounts[code] = (countryCounts[code] ?: 0) + cursor.getInt(1)
                        }
                    }
                }
                val byCountry = countryCounts.entries
                    .sortedByDescending { it.value }
                    .map { it.key to it.value }

                val langSql = if (useCanon) {
                    "SELECT c.language, COUNT(*) FROM live_canonical_map m INNER JOIN channels c ON c.id = m.channel_id WHERE m.is_primary = 1 AND c.language != '' AND $enabledWhere GROUP BY c.language"
                } else {
                    "SELECT c.language, COUNT(*) FROM channels c WHERE c.language != '' AND $liveFilterWhere AND $enabledWhere GROUP BY c.language"
                }
                val byLang = db.rawQuery(langSql, null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val lang = cursor.getString(0).orEmpty()
                            if (lang.isNotEmpty()) add(lang to cursor.getInt(1))
                        }
                    }.sortedByDescending { it.second }.take(120)
                }

                val catSql = if (useCanon) {
                    "SELECT c.category, COUNT(*) FROM live_canonical_map m INNER JOIN channels c ON c.id = m.channel_id WHERE m.is_primary = 1 AND $enabledWhere GROUP BY c.category"
                } else {
                    "SELECT c.category, COUNT(*) FROM channels c WHERE $liveFilterWhere AND $enabledWhere GROUP BY c.category"
                }
                val byCategory = db.rawQuery(catSql, null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add((cursor.getString(0) ?: "") to cursor.getInt(1))
                        }
                    }.sortedByDescending { it.second }
                }

                val candidate = CatalogStatsCache(
                    channelCount = uniqueLiveCount,
                    liveStreamCount = liveStreams,
                    movieCount = movies,
                    seriesCount = series,
                    totalStreamCount = totalStreams,
                    countryCount = byCountry.size,
                    byCountry = byCountry,
                    byLanguage = byLang,
                    byCategory = byCategory,
                )
                freshStats = candidate
                if (statsGeneration.get() == genBefore || attempt == 1) {
                    lastKnownChannelCount = uniqueLiveCount
                    if (statsGeneration.get() == genBefore) {
                        statsCache = candidate
                    }
                    staleStatsCache = candidate
                    if (totalStreams > 0 && useCanon && statsGeneration.get() == genBefore) {
                        savePersistedCatalogStats(candidate)
                    }
                    val elapsedMs = System.currentTimeMillis() - startedMs
                    Log.i(
                        "CasaDB",
                        "Catalog stats refreshed in ${elapsedMs}ms (liveChannels=$uniqueLiveCount, liveStreams=$liveStreams, movies=$movies, series=$series, countries=${byCountry.size})",
                    )
                    break
                }
            }
            return freshStats!!
        }
    }

    private fun loadPersistedCatalogStats(): CatalogStatsCache? = runCatching {
        val raw = readableDatabase.rawQuery(
            "SELECT val FROM search_meta WHERE key = ?",
            arrayOf("catalog_stats_v2"),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: return null
        val root = JSONObject(raw)
        val count = root.optInt("channelCount", 0)
        if (count <= 0) return null
        fun readPairs(arrName: String): List<Pair<String, Int>> {
            val arr = root.optJSONArray(arrName) ?: return emptyList()
            return buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONArray(i) ?: continue
                    add(item.optString(0, "") to item.optInt(1, 0))
                }
            }
        }
        val byCountry = readPairs("byCountry")
        val byLanguage = readPairs("byLanguage")
        val byCategory = readPairs("byCategory")
        CatalogStatsCache(
            channelCount = count,
            liveStreamCount = root.optInt("liveStreamCount", count),
            movieCount = root.optInt("movieCount", 0),
            seriesCount = root.optInt("seriesCount", 0),
            totalStreamCount = root.optInt("totalStreamCount", count),
            countryCount = byCountry.size,
            byCountry = byCountry,
            byLanguage = byLanguage,
            byCategory = byCategory,
        )
    }.getOrNull()

    private fun savePersistedCatalogStats(stats: CatalogStatsCache) {
        runCatching {
            fun encodePairs(pairs: List<Pair<String, Int>>): JSONArray = JSONArray().apply {
                for ((k, v) in pairs) {
                    put(JSONArray().apply {
                        put(k)
                        put(v)
                    })
                }
            }
            val payload = JSONObject().apply {
                put("channelCount", stats.channelCount)
                put("liveStreamCount", stats.liveStreamCount)
                put("movieCount", stats.movieCount)
                put("seriesCount", stats.seriesCount)
                put("totalStreamCount", stats.totalStreamCount)
                put("byCountry", encodePairs(stats.byCountry))
                put("byLanguage", encodePairs(stats.byLanguage))
                put("byCategory", encodePairs(stats.byCategory))
            }.toString()
            synchronized(writeLock) {
                val db = writableDatabase
                createSearchIndexTables(db)
                db.execSQL(
                    "INSERT OR REPLACE INTO search_meta(key, val) VALUES ('catalog_stats_v2', ?)",
                    arrayOf(payload),
                )
            }
        }.onFailure {
            Log.w("CasaDB", "savePersistedCatalogStats failed: ${it.message}")
        }
    }

    fun catalogStatsSummary(): CatalogStatsSummary {
        val s = computeCatalogStats()
        return CatalogStatsSummary(
            liveChannels = s.channelCount,
            liveStreams = s.liveStreamCount,
            movies = s.movieCount,
            series = s.seriesCount,
            totalItems = s.totalStreamCount,
            countries = s.countryCount,
            byCountry = s.byCountry,
            byLanguage = s.byLanguage,
            byCategory = s.byCategory,
        )
    }

    /** Non-blocking summary for /healthz watchdog probes during large DB transactions. */
    fun fastCatalogStatsSummary(): CatalogStatsSummary {
        val s = statsCache ?: staleStatsCache?.also { triggerBackgroundStatsRefresh() }
        if (s != null) {
            return CatalogStatsSummary(
                liveChannels = s.channelCount,
                liveStreams = s.liveStreamCount,
                movies = s.movieCount,
                series = s.seriesCount,
                totalItems = s.totalStreamCount,
                countries = s.countryCount,
                byCountry = s.byCountry,
                byLanguage = s.byLanguage,
                byCategory = s.byCategory,
            )
        }
        triggerBackgroundStatsRefresh()
        return CatalogStatsSummary(
            liveChannels = lastKnownChannelCount,
            liveStreams = lastKnownChannelCount,
            movies = 0,
            series = 0,
            totalItems = lastKnownChannelCount,
            countries = 0,
            byCountry = emptyList(),
            byLanguage = emptyList(),
            byCategory = emptyList(),
        )
    }

    fun listChannels(): List<IptvChannel> {
        val enabledWhere = enabledChannelWhere()
        return readableDatabase.rawQuery(
            "SELECT c.* FROM channels c WHERE $enabledWhere ORDER BY c.number ASC",
            null,
        ).use { cursor -> cursor.mapAll(::readChannel) }
    }

    fun getChannelsByIds(ids: Collection<String>): Map<String, IptvChannel> {
        val unique = ids.asSequence().filter { it.isNotBlank() }.distinct().toList()
        if (unique.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, IptvChannel>(unique.size)
        val db = readableDatabase
        for (chunk in unique.chunked(400)) {
            val placeholders = chunk.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT c.* FROM channels c WHERE c.id IN ($placeholders)",
                chunk.toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val ch = readChannel(cursor)
                    out[ch.id] = ch
                }
            }
        }
        return out
    }

    fun exportChannelsToFile(dest: java.io.File) {
        val enabledWhere = enabledChannelWhere()
        val db = readableDatabase
        dest.bufferedWriter(Charsets.UTF_8, 65_536).use { out ->
            out.write("#EXTM3U\n")
            var lastRowId = 0L
            while (true) {
                var batchMaxRowId = lastRowId
                val batch = db.rawQuery(
                    "SELECT c.rowid AS _rid, c.* FROM channels c WHERE c.rowid > ? AND $enabledWhere ORDER BY c.rowid ASC LIMIT 5000",
                    arrayOf(lastRowId.toString()),
                ).use { cursor ->
                    val ridIdx = cursor.getColumnIndex("_rid")
                    buildList {
                        while (cursor.moveToNext()) {
                            if (ridIdx >= 0) batchMaxRowId = maxOf(batchMaxRowId, cursor.getLong(ridIdx))
                            add(readChannel(cursor))
                        }
                    }
                }
                if (batch.isEmpty()) break
                lastRowId = batchMaxRowId
                for (ch in batch) {
                    out.write("#EXTINF:-1")
                    out.write(""" tvg-name="${ch.name.replace("\"", "'")}"""")
                    if (!ch.logo.isNullOrBlank()) out.write(""" tvg-logo="${ch.logo}"""")
                    if (ch.country.isNotBlank()) out.write(""" tvg-country="${ch.country}"""")
                    if (ch.language.isNotBlank()) out.write(""" tvg-language="${ch.language}"""")
                    if (ch.number > 0) out.write(""" tvg-chno="${ch.number}"""")
                    if (ch.category.isNotBlank()) out.write(""" group-title="${ch.category.replace("\"", "'")}"""")
                    out.write(",${ch.name}\n")
                    out.write("${ch.url}\n")
                }
            }
        }
    }

    private fun contentTypeClause(contentType: String?): String? {
        return when (contentType?.trim()?.lowercase(java.util.Locale.ROOT)) {
            null, "", "all" -> null
            "live" -> if (vodClassifyReady) {
                "c.content_type = 'live'"
            } else {
                "(c.content_type NOT IN ('movie', 'series') AND c.url NOT LIKE '%/movie/%' AND c.url NOT LIKE '%/series/%')"
            }
            "vod" -> if (vodClassifyReady) {
                "c.content_type IN ('movie', 'series')"
            } else {
                "(c.content_type IN ('movie', 'series') OR c.url LIKE '%/movie/%' OR c.url LIKE '%/series/%')"
            }
            "movie" -> if (vodClassifyReady) {
                "c.content_type = 'movie'"
            } else {
                "(c.content_type = 'movie' OR c.url LIKE '%/movie/%')"
            }
            "series" -> if (vodClassifyReady) {
                "c.content_type = 'series'"
            } else {
                "(c.content_type = 'series' OR c.url LIKE '%/series/%')"
            }
            else -> null
        }
    }

    fun queryChannels(
        country: String? = null,
        category: String? = null,
        categories: Collection<String> = emptyList(),
        languageCodes: Set<String> = emptySet(),
        vodLangCountries: Collection<String> = emptyList(),
        vodLangCategories: Collection<String> = emptyList(),
        query: String? = null,
        favoriteOnlyProfileId: String? = null,
        contentType: String? = null,
        sortByName: Boolean = false,
        dedupVod: Boolean = false,
        dedupLive: Boolean = false,
        limit: Int = 100,
        offset: Int = 0,
    ): Pair<List<IptvChannel>, Int> {
        if (!query.isNullOrBlank()) {
            return searchChannelsFast(
                country = country,
                category = category,
                languageCodes = languageCodes,
                vodLangCountries = vodLangCountries,
                vodLangCategories = vodLangCategories,
                query = query.trim(),
                favoriteOnlyProfileId = favoriteOnlyProfileId,
                contentType = contentType,
                sortByName = sortByName,
                dedupLive = dedupLive,
                limit = limit,
                offset = offset,
            )
        }
        val joinArgs = ArrayList<String>(2)
        val useLiveCanonJoin = dedupLive &&
            liveCanonReady &&
            favoriteOnlyProfileId.isNullOrBlank() &&
            (contentType.equals("live", ignoreCase = true) || contentType.isNullOrBlank() || contentType.equals("all", ignoreCase = true))
        val joinSql = buildString {
            if (!favoriteOnlyProfileId.isNullOrBlank()) {
                joinArgs += favoriteOnlyProfileId
                append("INNER JOIN profile_library pl ON pl.item_id = c.id AND pl.profile_id = ? AND pl.favorite = 1 ")
            }
            if (useLiveCanonJoin) {
                append("INNER JOIN live_canonical_map lcm ON lcm.channel_id = c.id AND lcm.is_primary = 1 ")
            }
        }.trim()
        val selectCols = if (useLiveCanonJoin) "c.*, lcm.source_count AS source_count" else "c.*"
        val clauses = ArrayList<String>(8)
        val args = ArrayList<String>(32)
        clauses += enabledChannelWhere()
        contentTypeClause(contentType)?.let { clauses += it }
        if (!category.isNullOrBlank()) {
            clauses += "c.category = ?"
            args += category
        } else if (categories.isNotEmpty()) {
            val cleanCats = categories.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(60)
            if (cleanCats.isNotEmpty()) {
                val ph = cleanCats.joinToString(",") { "?" }
                clauses += "c.category IN ($ph)"
                args.addAll(cleanCats)
            }
        }
        if (!country.isNullOrBlank()) {
            clauses += "c.country = ?"
            args += country.trim().uppercase(java.util.Locale.ROOT)
        }
        val cleanLangCountries = vodLangCountries.map { it.trim().uppercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }.distinct().take(40)
        val cleanLangCats = vodLangCategories.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(80)
        if (cleanLangCountries.isNotEmpty() || cleanLangCats.isNotEmpty()) {
            val parts = ArrayList<String>(2)
            if (cleanLangCountries.isNotEmpty()) {
                val ph = cleanLangCountries.joinToString(",") { "?" }
                parts += "c.country IN ($ph)"
                args.addAll(cleanLangCountries)
            }
            if (cleanLangCats.isNotEmpty()) {
                val ph = cleanLangCats.joinToString(",") { "?" }
                parts += "c.category IN ($ph)"
                args.addAll(cleanLangCats)
            }
            clauses += "(${parts.joinToString(" OR ")})"
        }
        val whereSql = clauses.joinToString(" AND ")
        val isVodType = contentType.equals("vod", ignoreCase = true) ||
            contentType.equals("movie", ignoreCase = true) ||
            contentType.equals("series", ignoreCase = true)
        val hasMultiBranchFilter = cleanLangCountries.isNotEmpty() || cleanLangCats.isNotEmpty() || categories.size > 1
        val canStreamOrderFromIndex = !isVodType || (
            !hasMultiBranchFilter && (
                !country.isNullOrBlank() ||
                    !category.isNullOrBlank() ||
                    vodGlobalIndexesReady
                )
            )
        val orderSql = when {
            !canStreamOrderFromIndex -> ""
            sortByName -> "ORDER BY c.name COLLATE NOCASE ASC, c.number ASC"
            else -> "ORDER BY c.number ASC"
        }
        val allArgs = joinArgs + args
        val db = readableDatabase

        if (languageCodes.isNotEmpty()) {
            val matched = ArrayList<IptvChannel>(minOf(offset + limit + 1, 256))
            var hasMoreLang = false
            val scanSql = "SELECT $selectCols FROM channels c $joinSql WHERE $whereSql ${if (orderSql.isNotEmpty()) orderSql else "ORDER BY c.number ASC"}"
            db.rawQuery(scanSql, allArgs.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val ch = readChannel(cursor)
                    if (M3uParser.normalizeLanguage(ch.language) in languageCodes) {
                        if (matched.size < offset + limit) {
                            matched.add(ch)
                        } else {
                            hasMoreLang = true
                            break
                        }
                    }
                }
            }
            val items = matched.drop(offset).take(limit)
            val totalHint = if (hasMoreLang) offset + items.size + 1 else matched.size
            return items to totalHint
        }

        val skipFullCount = (
            !vodClassifyReady ||
                (isVodType && favoriteOnlyProfileId.isNullOrBlank() && (country.isNullOrBlank() || hasMultiBranchFilter))
            ) &&
            !contentType.isNullOrBlank() &&
            !contentType.equals("all", ignoreCase = true)

        if (dedupVod) {
            // Single-type queries ("movie" / "series") hit idx_channels_type_name_number directly in <400ms.
            // Combined "vod" requests (IN ('movie','series')) force SQLite into slow scans when sorting by name,
            // so merge two indexed single-type cursors on demand when global VOD filters aren't bypassed.
            if (contentType.equals("vod", ignoreCase = true) && favoriteOnlyProfileId.isNullOrBlank()) {
                return queryVodMergedTwoCursors(
                    country = country,
                    category = category,
                    categories = categories,
                    vodLangCountries = vodLangCountries,
                    vodLangCategories = vodLangCategories,
                    sortByName = sortByName,
                    canStreamOrderFromIndex = canStreamOrderFromIndex,
                    orderSql = orderSql,
                    limit = limit,
                    offset = offset,
                )
            }
            val rawTotal = if (skipFullCount) {
                0
            } else {
                db.rawQuery("SELECT COUNT(*) FROM channels c $joinSql WHERE $whereSql", allArgs.toTypedArray()).use {
                    if (it.moveToFirst()) it.getInt(0) else 0
                }
            }
            val neededUnique = offset + limit + 1
            val scanCap = maxOf((offset + limit) * 14, 4800).coerceAtMost(14_000)
            val batchSize = 600
            val uniqueMap = LinkedHashMap<String, IptvChannel>(neededUnique.coerceAtMost(2000))
            val langsByKey = HashMap<String, LinkedHashSet<String>>(neededUnique.coerceAtMost(2000))
            var rawOffset = 0
            var rawExhausted = false
            while (uniqueMap.size < neededUnique && rawOffset < scanCap) {
                val pagedArgs = (allArgs + batchSize.toString() + rawOffset.toString()).toTypedArray()
                val batch = db.rawQuery(
                    "SELECT $selectCols FROM channels c $joinSql WHERE $whereSql $orderSql LIMIT ? OFFSET ?",
                    pagedArgs,
                ).use { cursor -> cursor.mapAll(::readChannel) }
                rawOffset += batch.size
                for (ch in batch) {
                    val key = VodTaxonomy.normalizeVodDedupKey(ch)
                    val flagSet = langsByKey.getOrPut(key) { LinkedHashSet(3) }
                    if (flagSet.size < 5) {
                        flagSet.addAll(VodTaxonomy.detectVodFlagCodes(ch))
                    }
                    val prev = uniqueMap[key]
                    if (prev == null || VodTaxonomy.vodCandidateScore(ch) > VodTaxonomy.vodCandidateScore(prev)) {
                        uniqueMap[key] = ch
                    }
                }
                if (batch.size < batchSize) {
                    rawExhausted = true
                    break
                }
            }
            val allUnique = uniqueMap.entries.map { (key, ch) ->
                val flags = langsByKey[key]?.take(5).orEmpty()
                if (flags.isNotEmpty()) ch.copy(availableLanguageFlags = flags) else ch
            }
            val items = allUnique.drop(offset).take(limit)
            val hasMore = allUnique.size > offset + items.size || !rawExhausted
            val estimatedTotal = if (rawExhausted) {
                allUnique.size
            } else if (rawTotal > 0 && rawOffset > 0) {
                val ratio = allUnique.size.toDouble() / rawOffset.toDouble()
                (rawTotal.toDouble() * ratio).toInt().coerceAtLeast(offset + items.size + if (hasMore) 1 else 0)
            } else {
                offset + items.size + if (hasMore) 1 else 0
            }
            return items to estimatedTotal
        }

        if (skipFullCount) {
            val pagedArgs = (allArgs + (limit + 1).toString() + offset.toString()).toTypedArray()
            val items = db.rawQuery(
                "SELECT $selectCols FROM channels c $joinSql WHERE $whereSql $orderSql LIMIT ? OFFSET ?",
                pagedArgs,
            ).use { cursor -> cursor.mapAll(::readChannel) }
            val totalHint = if (items.size > limit) offset + limit + 1 else offset + items.size
            return items.take(limit) to totalHint
        }

        val total = db.rawQuery("SELECT COUNT(*) FROM channels c $joinSql WHERE $whereSql", allArgs.toTypedArray()).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        val pagedArgs = (allArgs + limit.toString() + offset.toString()).toTypedArray()
        val items = db.rawQuery(
            "SELECT $selectCols FROM channels c $joinSql WHERE $whereSql $orderSql LIMIT ? OFFSET ?",
            pagedArgs,
        ).use { cursor -> cursor.mapAll(::readChannel) }
        return items to total
    }

    private fun searchChannelsFast(
        country: String? = null,
        category: String? = null,
        languageCodes: Set<String> = emptySet(),
        vodLangCountries: Collection<String> = emptyList(),
        vodLangCategories: Collection<String> = emptyList(),
        query: String,
        favoriteOnlyProfileId: String? = null,
        contentType: String? = null,
        sortByName: Boolean = false,
        dedupLive: Boolean = false,
        limit: Int = 100,
        offset: Int = 0,
    ): Pair<List<IptvChannel>, Int> {
        val db = readableDatabase
        val lowerQuery = query.lowercase(java.util.Locale.ROOT)
        val joinArgs = ArrayList<String>(2)
        val joinSql = if (!favoriteOnlyProfileId.isNullOrBlank()) {
            joinArgs += favoriteOnlyProfileId
            "INNER JOIN profile_library pl ON pl.item_id = c.id AND pl.profile_id = ? AND pl.favorite = 1"
        } else {
            ""
        }
        val extraClauses = ArrayList<String>(6)
        val extraArgs = ArrayList<String>(24)
        extraClauses += enabledChannelWhere()
        contentTypeClause(contentType)?.let { extraClauses += it }
        if (!category.isNullOrBlank()) {
            extraClauses += "c.category = ?"
            extraArgs += category
        }
        if (!country.isNullOrBlank()) {
            extraClauses += "c.country = ?"
            extraArgs += country.trim().uppercase(java.util.Locale.ROOT)
        }
        val cleanLangCountries = vodLangCountries.map { it.trim().uppercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }.distinct().take(40)
        val cleanLangCats = vodLangCategories.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(80)
        if (cleanLangCountries.isNotEmpty() || cleanLangCats.isNotEmpty()) {
            val parts = ArrayList<String>(2)
            if (cleanLangCountries.isNotEmpty()) {
                val ph = cleanLangCountries.joinToString(",") { "?" }
                parts += "c.country IN ($ph)"
                extraArgs.addAll(cleanLangCountries)
            }
            if (cleanLangCats.isNotEmpty()) {
                val ph = cleanLangCats.joinToString(",") { "?" }
                parts += "c.category IN ($ph)"
                extraArgs.addAll(cleanLangCats)
            }
            extraClauses += "(${parts.joinToString(" OR ")})"
        }
        val extraWhereSql = extraClauses.joinToString(" AND ")

        val dedupVodSearch = contentType.equals("vod", ignoreCase = true) ||
            contentType.equals("movie", ignoreCase = true) ||
            contentType.equals("series", ignoreCase = true)

        fun dedupSearchList(items: List<IptvChannel>): List<IptvChannel> {
            if (items.size <= 1) return items
            if (dedupVodSearch) {
                val map = LinkedHashMap<String, IptvChannel>(items.size)
                for (ch in items) {
                    val key = VodTaxonomy.normalizeVodDedupKey(ch)
                    val prev = map[key]
                    if (prev == null || VodTaxonomy.vodCandidateScore(ch) > VodTaxonomy.vodCandidateScore(prev)) {
                        map[key] = ch
                    }
                }
                return map.values.toList()
            }
            val orderSlots = LinkedHashMap<String, IptvChannel>(items.size)
            val liveGroups = LinkedHashMap<String, MutableList<IptvChannel>>(items.size)
            for (ch in items) {
                if (ch.isVod) {
                    val vKey = "vod:" + VodTaxonomy.normalizeVodDedupKey(ch)
                    val prev = orderSlots[vKey]
                    if (prev == null || VodTaxonomy.vodCandidateScore(ch) > VodTaxonomy.vodCandidateScore(prev)) {
                        orderSlots[vKey] = ch
                    }
                } else if (dedupLive) {
                    val (gKey, _) = canonicalLiveGroupKeyAndCountry(ch.name, ch.country, ch.tvgId)
                    val slotKey = "live:$gKey"
                    if (!orderSlots.containsKey(slotKey)) {
                        orderSlots[slotKey] = ch
                    }
                    liveGroups.getOrPut(slotKey) { ArrayList(2) }.add(ch)
                } else {
                    orderSlots["raw:${ch.id}"] = ch
                }
            }
            return orderSlots.entries.map { (slotKey, ch) ->
                if (!dedupLive || ch.isVod) {
                    ch
                } else {
                    val group = liveGroups[slotKey] ?: listOf(ch)
                    val best = group.minWith(
                        compareBy<IptvChannel>(
                            { livePrimaryScore(it.name, it.url, it.logo, it.quality, it.number) },
                            { it.number },
                        ),
                    )
                    if (group.size > best.sourceCount) best.copy(sourceCount = group.size) else best
                }
            }
        }

        val unscopedSearch = country.isNullOrBlank() &&
            category.isNullOrBlank() &&
            cleanLangCountries.isEmpty() &&
            cleanLangCats.isEmpty() &&
            favoriteOnlyProfileId.isNullOrBlank() &&
            languageCodes.isEmpty()

        val tokens = SEARCH_TOKEN_REGEX.findAll(lowerQuery).map { it.value }.toList()
        if (ftsSearchReady && unscopedSearch && tokens.isNotEmpty() && tokens.any { it.length >= 2 }) {
            val ftsMatch = tokens.joinToString(" ") { "$it*" }
            val fetchCap = if (offset == 0) maxOf(limit * 6, 500).coerceAtMost(1000) else offset + limit * 4 + 1
            val ftsArgs = (joinArgs + ftsMatch + extraArgs + fetchCap.toString()).toTypedArray()
            val ftsAttempt = runCatching {
                val rawRows = db.rawQuery(
                    """
                    SELECT c.* FROM channels_fts f
                    CROSS JOIN channels c ON c.rowid = f.docid
                    WHERE channels_fts MATCH ? AND $extraWhereSql
                    LIMIT ?
                    """.trimIndent(),
                    ftsArgs,
                ).use { cursor -> cursor.mapAll(::readChannel) }

                val dedup = LinkedHashMap<String, IptvChannel>(rawRows.size)
                for (ch in rawRows) {
                    val lname = ch.name.lowercase(java.util.Locale.ROOT)
                    if (tokens.all { tok -> tok in lname }) {
                        dedup.putIfAbsent(ch.id, ch)
                    }
                }
                val rankedRaw = dedup.values.sortedWith { a, b ->
                    val la = a.name.lowercase(java.util.Locale.ROOT)
                    val lb = b.name.lowercase(java.util.Locale.ROOT)
                    val pA = la.startsWith(lowerQuery)
                    val pB = lb.startsWith(lowerQuery)
                    if (pA != pB) return@sortedWith if (pA) -1 else 1
                    val eA = lowerQuery in la
                    val eB = lowerQuery in lb
                    if (eA != eB) return@sortedWith if (eA) -1 else 1
                    if (sortByName) {
                        la.compareTo(lb).let { if (it != 0) it else a.number.compareTo(b.number) }
                    } else {
                        a.number.compareTo(b.number)
                    }
                }
                val ranked = dedupSearchList(rankedRaw)
                val slice = ranked.drop(offset).take(limit)
                val hasMore = rawRows.size >= fetchCap || ranked.size > offset + slice.size
                rawRows.size to (slice to (if (hasMore) offset + slice.size + 1 else offset + slice.size))
            }.getOrNull()

            if (ftsAttempt != null) {
                val (_, pair) = ftsAttempt
                if (pair.first.isNotEmpty() || offset > 0 || tokens.size > 1 || lowerQuery.length < 3) {
                    return pair
                }
            }
        }

        // B-tree prefix fast path for single-word prefix searches when FTS is warming up or single-token fallback needed.
        if (offset == 0 && unscopedSearch && lowerQuery.length >= 2) {
            val upper = nocasePrefixUpperBound(lowerQuery)
            val prefixCap = maxOf(limit * 6, 160).coerceAtMost(600)
            val prefixArgs = (joinArgs + extraArgs + lowerQuery + upper + prefixCap.toString()).toTypedArray()
            val rawPrefix = db.rawQuery(
                """
                SELECT c.* FROM channels c $joinSql
                WHERE $extraWhereSql AND c.name >= ? COLLATE NOCASE AND c.name < ? COLLATE NOCASE
                LIMIT ?
                """.trimIndent(),
                prefixArgs,
            ).use { cursor -> cursor.mapAll(::readChannel) }
            val filtered = dedupSearchList(rawPrefix.filter { it.name.lowercase(java.util.Locale.ROOT).startsWith(lowerQuery) })
            if (filtered.size >= limit) {
                val sorted = if (sortByName) {
                    filtered.sortedWith(compareBy<IptvChannel>({ it.name.lowercase(java.util.Locale.ROOT) }, { it.number }))
                } else {
                    filtered.sortedBy { it.number }
                }
                val items = sorted.take(limit)
                val totalHint = if (rawPrefix.size >= prefixCap || filtered.size > limit) limit + 1 else items.size
                return items to totalHint
            }
        }

        // Bounded LIKE fallback without SELECT COUNT(*) or full-table ORDER BY.
        val escaped = escapeSqlLikeLiteral(query)
        val likeArgs = joinArgs + extraArgs + "%$escaped%"
        val likeWhere = "$extraWhereSql AND c.name LIKE ? ESCAPE '\\' COLLATE NOCASE"
        val fetchLimit = when {
            languageCodes.isNotEmpty() -> (limit * 4).coerceAtMost(2000)
            else -> (limit * 6).coerceAtMost(800)
        }
        val queryArgs = (likeArgs + fetchLimit.toString() + offset.toString()).toTypedArray()
        val raw = db.rawQuery(
            "SELECT c.* FROM channels c $joinSql WHERE $likeWhere LIMIT ? OFFSET ?",
            queryArgs,
        ).use { cursor -> cursor.mapAll(::readChannel) }
        val filtered = dedupSearchList(
            if (languageCodes.isNotEmpty()) {
                raw.filter { M3uParser.normalizeLanguage(it.language) in languageCodes }
            } else {
                raw
            },
        )
        val sorted = if (sortByName) {
            filtered.sortedWith(compareBy<IptvChannel>({ it.name.lowercase(java.util.Locale.ROOT) }, { it.number }))
        } else {
            filtered.sortedBy { it.number }
        }
        val items = sorted.take(limit)
        val hasMore = when {
            languageCodes.isNotEmpty() || dedupLive || dedupVodSearch -> filtered.size > limit || raw.size >= fetchLimit
            else -> raw.size > limit
        }
        val totalHint = if (hasMore) offset + items.size + 1 else offset + items.size
        return items to totalHint
    }

    fun channelCount(): Int = computeCatalogStats().channelCount

    /** Non-blocking count for /healthz watchdog probes during large DB transactions. */
    fun fastChannelCount(): Int {
        statsCache?.let { return it.channelCount }
        staleStatsCache?.let {
            triggerBackgroundStatsRefresh()
            return it.channelCount
        }
        triggerBackgroundStatsRefresh()
        return lastKnownChannelCount
    }

    fun getChannel(id: String?): IptvChannel? {
        if (id.isNullOrBlank()) return null
        return readableDatabase.rawQuery(
            "SELECT c.* FROM channels c WHERE c.id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) readChannel(cursor) else null }
    }

    fun getChannelByNumber(number: Int): IptvChannel? {
        if (number <= 0) return null
        val enabledWhere = enabledChannelWhere()
        return readableDatabase.rawQuery(
            "SELECT c.* FROM channels c WHERE c.number = ? AND $enabledWhere LIMIT 1",
            arrayOf(number.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) readChannel(cursor) else null }
    }

    private fun normalizeAlternativeName(name: String): String {
        return name.lowercase(java.util.Locale.ROOT)
            .replace(Regex("""\([^)]*\)|\[[^\]]*]"""), " ")
            .replace("Ⓖ", " ")
            .replace("Ⓥ", " ")
            .replace(Regex("""\b(?:uhd|fhd|hd|sd)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun alternativeReliabilityScore(candidate: IptvChannel, targetKey: String): Int {
        val lowerName = candidate.name.lowercase(java.util.Locale.ROOT)
        val lowerUrl = candidate.url.trim().lowercase(java.util.Locale.ROOT)
        var score = 0
        if (lowerUrl.startsWith("https://")) score -= 20 else if (lowerUrl.startsWith("http://")) score += 20
        if (targetKey.isNotBlank() && normalizeAlternativeName(candidate.name) == targetKey) score -= 15
        if ("geo-blocked" in lowerName || "Ⓖ" in candidate.name) {
            score += 100
        }
        return score
    }

    private fun escapeSqlLikeLiteral(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun nocasePrefixUpperBound(lowerPrefix: String): String =
        lowerPrefix.dropLast(1) + (lowerPrefix.last().code + 1).toChar()

    fun listAlternativeChannels(
        channel: IptvChannel,
        limit: Int = 12,
        sortSelfByScore: Boolean = true,
        dynamicScoreAdjustment: ((String) -> Int)? = null,
    ): List<IptvChannel> {
        val targetKey = normalizeAlternativeName(channel.name)
        val (canonKey, _) = canonicalLiveGroupKeyAndCountry(channel.name, channel.country, channel.tvgId)
        val baseTvgId = channel.tvgId.substringBefore('@').trim()
        val db = readableDatabase
        val candidateMap = LinkedHashMap<String, IptvChannel>(48)
        candidateMap[channel.id] = channel

        if (liveCanonReady && !channel.isVod) {
            val dbGroupKey = runCatching {
                db.rawQuery(
                    "SELECT group_key FROM live_canonical_map WHERE channel_id = ? LIMIT 1",
                    arrayOf(channel.id),
                ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: canonKey
            db.rawQuery(
                "SELECT c.* FROM live_canonical_map m INNER JOIN channels c ON c.id = m.channel_id WHERE m.group_key = ? LIMIT 40",
                arrayOf(dbGroupKey),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val ch = readChannel(cursor)
                    candidateMap.putIfAbsent(ch.id, ch)
                }
            }
        }
        if (channel.tvgId.isNotBlank()) {
            db.rawQuery(
                "SELECT c.* FROM channels c WHERE c.tvg_id = ? LIMIT 25",
                arrayOf(channel.tvgId),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val ch = readChannel(cursor)
                    candidateMap.putIfAbsent(ch.id, ch)
                }
            }
        }
        if (baseTvgId.isNotBlank() && !baseTvgId.equals(channel.tvgId, ignoreCase = false)) {
            db.rawQuery(
                "SELECT c.* FROM channels c WHERE c.tvg_id = ? LIMIT 25",
                arrayOf(baseTvgId),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val ch = readChannel(cursor)
                    candidateMap.putIfAbsent(ch.id, ch)
                }
            }
        }
        if (candidateMap.size < limit && targetKey.length >= 3) {
            val lowerPrefix = targetKey.lowercase(java.util.Locale.ROOT)
            val upperBound = nocasePrefixUpperBound(lowerPrefix)
            db.rawQuery(
                "SELECT c.* FROM channels c WHERE c.name >= ? COLLATE NOCASE AND c.name < ? COLLATE NOCASE LIMIT 40",
                arrayOf(lowerPrefix, upperBound),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val ch = readChannel(cursor)
                    candidateMap.putIfAbsent(ch.id, ch)
                }
            }
        }

        val checkEnabled = hasDisabledPlaylists()
        val effectiveGroupKey = if (!channel.isVod) {
            if (liveCanonReady) {
                runCatching {
                    db.rawQuery(
                        "SELECT group_key FROM live_canonical_map WHERE channel_id = ? LIMIT 1",
                        arrayOf(channel.id),
                    ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull() ?: canonKey
            } else {
                canonKey
            }
        } else {
            "vod:${channel.id}"
        }
        val filtered = candidateMap.values
            .asSequence()
            .filter { cand ->
                if (cand.id == channel.id) return@filter true
                val (candCanon, _) = canonicalLiveGroupKeyAndCountry(cand.name, cand.country, cand.tvgId)
                val sameCanon = candCanon == canonKey
                if (!sameCanon && channel.country.isNotBlank() && cand.country.isNotBlank() &&
                    !cand.country.equals(channel.country, ignoreCase = true)
                ) {
                    return@filter false
                }
                val candTvgBase = cand.tvgId.substringBefore('@').trim()
                val sameBaseTvg = baseTvgId.isNotBlank() && candTvgBase.equals(baseTvgId, ignoreCase = true)
                val sameNormName = targetKey.isNotBlank() && normalizeAlternativeName(cand.name) == targetKey
                if (!sameCanon && !sameBaseTvg && !sameNormName) return@filter false
                if (!checkEnabled) return@filter true
                db.rawQuery(
                    """
                    SELECT 1 FROM playlist_channels pc
                    JOIN playlists p ON p.id = pc.playlist_id
                    WHERE pc.channel_id = ? AND p.enabled = 1
                    LIMIT 1
                    """.trimIndent(),
                    arrayOf(cand.id),
                ).use { it.moveToFirst() }
            }
            .sortedWith(
                compareBy<IptvChannel>(
                    { cand ->
                        if (!sortSelfByScore && cand.id == channel.id) Int.MIN_VALUE / 2
                        else alternativeReliabilityScore(cand, targetKey) +
                            persistentSourceScoreAdjustment(effectiveGroupKey, cand.url) +
                            (dynamicScoreAdjustment?.invoke(cand.url) ?: 0)
                    },
                    { it.number },
                ),
            )

        val seenUrls = LinkedHashSet<String>()
        val result = mutableListOf<IptvChannel>()
        for (alt in filtered) {
            if (result.size >= limit) break
            val u = alt.url.trim()
            if (u.isNotBlank() && seenUrls.add(u)) {
                result.add(alt)
            }
        }
        return result.ifEmpty { listOf(channel) }
    }

    fun channelCountryCount(): Int = computeCatalogStats().countryCount

    fun channelCountsByCountry(): List<Pair<String, Int>> = computeCatalogStats().byCountry

    fun channelCountsByLanguage(): List<Pair<String, Int>> = computeCatalogStats().byLanguage

    /** Replaces the whole playlist: channels removed upstream disappear here too. */
    fun replaceChannels(channels: List<IptvChannel>) {
        synchronized(writeLock) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("channels", null, null)
                resetSearchIndexLocked(db)
                channels.forEachIndexed { index, channel ->
                    val values = ContentValues().apply {
                        put("id", channel.id)
                        put("name", channel.name)
                        put("url", channel.url)
                        put("logo", channel.logo)
                        put("category", channel.category)
                        put("language", channel.language)
                        put("country", channel.country)
                        put("tvg_id", channel.tvgId)
                        put("quality", channel.quality)
                        put("number", index + 1)
                        put("added_at", channel.addedAt)
                    }
                    db.insertWithOnConflict("channels", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                invalidateCatalogCache()
            }
        }
    }

    fun clearChannels() {
        synchronized(writeLock) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("channels", null, null)
                resetSearchIndexLocked(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                staleStatsCache = null
                invalidateCatalogCache()
            }
        }
    }

    fun clearChannelsAndPlaylists() {
        synchronized(writeLock) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("playlist_channels", null, null)
                db.delete("playlists", null, null)
                db.delete("channels", null, null)
                resetSearchIndexLocked(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                staleStatsCache = null
                invalidateCatalogCache()
            }
        }
    }

    fun channelCountsByCategory(): List<Pair<String, Int>> = computeCatalogStats().byCategory

    /* ------------------------------------------------------------------ */
    /* Connected devices                                                   */
    /* ------------------------------------------------------------------ */

    fun touchClient(
        id: String,
        name: String,
        platform: String,
        appVersion: String,
        profileId: String,
        state: String,
        channel: String,
        ip: String,
    ) {
        synchronized(writeLock) {
            val now = System.currentTimeMillis() / 1000
            val db = writableDatabase
            db.execSQL(
                """
                INSERT INTO clients(id, name, platform, app_version, profile_id, state, channel, ip, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    platform = excluded.platform,
                    app_version = excluded.app_version,
                    profile_id = excluded.profile_id,
                    state = excluded.state,
                    channel = excluded.channel,
                    ip = excluded.ip,
                    last_seen = excluded.last_seen
                """.trimIndent(),
                arrayOf(id, name, platform, appVersion, profileId, state, channel, ip, now, now),
            )
            // Entries older than the TTL are not "connected" any more.
            if (now - lastClientPruneAt >= 60L) {
                lastClientPruneAt = now
                db.delete("clients", "last_seen < ?", arrayOf((now - CLIENT_TTL_SECONDS).toString()))
            }
        }
    }

    fun listClients(): List<ClientRow> {
        val now = System.currentTimeMillis() / 1000
        return readableDatabase.rawQuery(
            "SELECT * FROM clients WHERE last_seen >= ? ORDER BY last_seen DESC",
            arrayOf((now - CLIENT_TTL_SECONDS).toString()),
        ).use { cursor -> cursor.mapAll(::readClient) }
    }

    fun removeClient(id: String) {
        synchronized(writeLock) {
            writableDatabase.delete("clients", "id = ?", arrayOf(id))
        }
    }

    /* ------------------------------------------------------------------ */
    /* Mapping                                                             */
    /* ------------------------------------------------------------------ */

    private fun readProfile(cursor: Cursor): ProfileRow = ProfileRow(
        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
        avatar = cursor.getString(cursor.getColumnIndexOrThrow("avatar")),
        language = cursor.getString(cursor.getColumnIndexOrThrow("language")),
        country = cursor.getString(cursor.getColumnIndexOrThrow("country")),
        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
    )

    private fun readClient(cursor: Cursor): ClientRow = ClientRow(
        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
        platform = cursor.getString(cursor.getColumnIndexOrThrow("platform")),
        appVersion = cursor.getString(cursor.getColumnIndexOrThrow("app_version")),
        profileId = cursor.getString(cursor.getColumnIndexOrThrow("profile_id")),
        state = cursor.getString(cursor.getColumnIndexOrThrow("state")),
        channel = cursor.getString(cursor.getColumnIndexOrThrow("channel")),
        ip = cursor.getString(cursor.getColumnIndexOrThrow("ip")),
        firstSeen = cursor.getLong(cursor.getColumnIndexOrThrow("first_seen")),
        lastSeen = cursor.getLong(cursor.getColumnIndexOrThrow("last_seen")),
    )

    private fun normalizeLanguage(value: String): String =
        when (value.take(2).lowercase()) {
            "en" -> "en"
            "es" -> "es"
            else -> "it"
        }

    private fun normalizeCountry(value: String): String {
        val code = value.trim().uppercase()
        return if (code.length == 2 && code.all { it.isLetter() }) code else "IT"
    }

    private fun readChannel(cursor: Cursor): IptvChannel {
        val pCol = cursor.getColumnIndex("playlist_id")
        val playlistId = if (pCol >= 0) cursor.getString(pCol) ?: "default" else "default"
        val ctCol = cursor.getColumnIndex("content_type")
        val scCol = cursor.getColumnIndex("source_count")
        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
        val url = cursor.getString(cursor.getColumnIndexOrThrow("url"))
        val category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
        val rawCt = if (ctCol >= 0) cursor.getString(ctCol).orEmpty().trim() else ""
        val contentType = rawCt.ifBlank { M3uParser.inferContentType(url, name, category) }
        val sourceCount = if (scCol >= 0) cursor.getInt(scCol).coerceAtLeast(1) else 1
        return IptvChannel(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = name,
            url = url,
            logo = cursor.getString(cursor.getColumnIndexOrThrow("logo")),
            category = category,
            language = cursor.getString(cursor.getColumnIndexOrThrow("language")),
            country = cursor.getString(cursor.getColumnIndexOrThrow("country")),
            tvgId = cursor.getString(cursor.getColumnIndexOrThrow("tvg_id")),
            quality = cursor.getString(cursor.getColumnIndexOrThrow("quality")),
            number = cursor.getInt(cursor.getColumnIndexOrThrow("number")),
            addedAt = cursor.getLong(cursor.getColumnIndexOrThrow("added_at")),
            playlistId = playlistId,
            contentType = contentType,
            sourceCount = sourceCount,
        )
    }

    data class SeriesDetailResult(
        val showTitle: String,
        val representative: IptvChannel,
        val seasons: Map<Int, List<IptvChannel>>,
        val resumeEpisodeId: String?,
        val totalEpisodes: Int,
    )

    fun saveVodProgress(profileId: String, itemId: String, positionMs: Long, durationMs: Long) {
        val isCompleted = durationMs > 0L && positionMs >= (durationMs * 92L) / 100L
        val normalizedPos = if (isCompleted) 0L else positionMs.coerceAtLeast(0L)
        val normalizedDur = durationMs.coerceAtLeast(0L)
        val nowSec = System.currentTimeMillis() / 1000
        val currentChannel = getChannel(itemId)
        val isSeries = currentChannel?.contentType == "series"
        val nextEpisode = if (isCompleted && isSeries && currentChannel != null) {
            findNextSeriesEpisode(currentChannel)
        } else {
            null
        }
        val hasUpNextMarkers = if (isSeries && currentChannel != null && (isCompleted || normalizedPos >= 15_000L)) {
            readableDatabase.rawQuery(
                "SELECT 1 FROM profile_library WHERE profile_id = ? AND position_ms = 1 LIMIT 1",
                arrayOf(profileId),
            ).use { it.moveToFirst() }
        } else {
            false
        }
        val staleSeriesEpisodeIds = if (hasUpNextMarkers && currentChannel != null) {
            listSeriesEpisodes(currentChannel, limit = 600)
                .map { it.id }
                .filter { it != itemId && it != nextEpisode?.id }
        } else {
            emptyList()
        }

        synchronized(writeLock) {
            writableDatabase.beginTransaction()
            try {
                writableDatabase.execSQL(
                    """
                    INSERT INTO profile_library(profile_id, item_id, favorite, last_played_at, watch_count, position_ms, duration_ms)
                    VALUES (?, ?, 0, ?, 1, ?, ?)
                    ON CONFLICT(profile_id, item_id)
                    DO UPDATE SET last_played_at = excluded.last_played_at,
                                  position_ms = excluded.position_ms,
                                  duration_ms = CASE WHEN excluded.duration_ms > 0 THEN excluded.duration_ms ELSE profile_library.duration_ms END
                    """.trimIndent(),
                    arrayOf(profileId, itemId, nowSec, normalizedPos, normalizedDur),
                )
                if (staleSeriesEpisodeIds.isNotEmpty()) {
                    staleSeriesEpisodeIds.chunked(300).forEach { chunk ->
                        val ph = chunk.joinToString(",") { "?" }
                        writableDatabase.execSQL(
                            "UPDATE profile_library SET position_ms = 0 WHERE profile_id = ? AND position_ms = 1 AND item_id IN ($ph)",
                            (listOf(profileId) + chunk).toTypedArray(),
                        )
                    }
                }
                if (nextEpisode != null) {
                    // Sentinel position_ms = 1 marks the next episode as "Up Next" in Continue Watching.
                    writableDatabase.execSQL(
                        """
                        INSERT INTO profile_library(profile_id, item_id, favorite, last_played_at, watch_count, position_ms, duration_ms)
                        VALUES (?, ?, 0, ?, 1, 1, 0)
                        ON CONFLICT(profile_id, item_id)
                        DO UPDATE SET last_played_at = excluded.last_played_at,
                                      position_ms = CASE WHEN profile_library.position_ms >= 15000 THEN profile_library.position_ms ELSE 1 END
                        """.trimIndent(),
                        arrayOf(profileId, nextEpisode.id, nowSec + 1L),
                    )
                }
                writableDatabase.setTransactionSuccessful()
            } finally {
                writableDatabase.endTransaction()
            }
        }
    }

    fun clearVodProgress(profileId: String, itemId: String) {
        synchronized(writeLock) {
            writableDatabase.execSQL(
                "UPDATE profile_library SET position_ms = 0, duration_ms = 0 WHERE profile_id = ? AND item_id = ?",
                arrayOf(profileId, itemId),
            )
        }
    }

    fun getVodProgressMap(profileId: String, itemIds: Collection<String>, includeUpNext: Boolean = false): Map<String, Pair<Long, Long>> {
        if (itemIds.isEmpty()) return emptyMap()
        val result = HashMap<String, Pair<Long, Long>>(itemIds.size)
        val db = readableDatabase
        val posCond = if (includeUpNext) "(position_ms >= 15000 OR position_ms = 1)" else "position_ms > 0"
        itemIds.distinct().chunked(400).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val args = (listOf(profileId) + chunk).toTypedArray()
            db.rawQuery(
                "SELECT item_id, position_ms, duration_ms FROM profile_library WHERE profile_id = ? AND $posCond AND item_id IN ($placeholders)",
                args,
            ).use { c ->
                while (c.moveToNext()) {
                    result[c.getString(0)] = c.getLong(1) to c.getLong(2)
                }
            }
        }
        return result
    }

    fun listContinueWatching(profileId: String, limit: Int = 24): List<Triple<IptvChannel, Long, Long>> {
        val db = readableDatabase
        val enabledWhere = enabledChannelWhere()
        val vodWhere = contentTypeClause("vod") ?: "1 = 1"
        return db.rawQuery(
            """
            SELECT c.*, pl.position_ms, pl.duration_ms
            FROM profile_library pl
            INNER JOIN channels c ON c.id = pl.item_id
            WHERE pl.profile_id = ? AND (pl.position_ms >= 15000 OR pl.position_ms = 1) AND $vodWhere AND $enabledWhere
            ORDER BY pl.last_played_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(profileId, limit.toString()),
        ).use { c ->
            val posIdx = c.getColumnIndex("position_ms")
            val durIdx = c.getColumnIndex("duration_ms")
            buildList {
                while (c.moveToNext()) {
                    add(
                        Triple(
                            readChannel(c),
                            if (posIdx >= 0) c.getLong(posIdx) else 0L,
                            if (durIdx >= 0) c.getLong(durIdx) else 0L,
                        ),
                    )
                }
            }
        }
    }

    private fun queryVodMergedTwoCursors(
        country: String? = null,
        category: String? = null,
        categories: Collection<String> = emptyList(),
        vodLangCountries: Collection<String> = emptyList(),
        vodLangCategories: Collection<String> = emptyList(),
        sortByName: Boolean,
        canStreamOrderFromIndex: Boolean,
        orderSql: String,
        limit: Int,
        offset: Int,
    ): Pair<List<IptvChannel>, Int> {
        val db = readableDatabase
        val baseClauses = ArrayList<String>(6)
        val baseArgs = ArrayList<String>(24)
        baseClauses += enabledChannelWhere()
        if (!category.isNullOrBlank()) {
            baseClauses += "c.category = ?"
            baseArgs += category
        } else if (categories.isNotEmpty()) {
            val cleanCats = categories.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(60)
            if (cleanCats.isNotEmpty()) {
                val ph = cleanCats.joinToString(",") { "?" }
                baseClauses += "c.category IN ($ph)"
                baseArgs.addAll(cleanCats)
            }
        }
        if (!country.isNullOrBlank()) {
            baseClauses += "c.country = ?"
            baseArgs += country.trim().uppercase(java.util.Locale.ROOT)
        }
        val cleanLangCountries = vodLangCountries.map { it.trim().uppercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }.distinct().take(40)
        val cleanLangCats = vodLangCategories.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(80)
        if (cleanLangCountries.isNotEmpty() || cleanLangCats.isNotEmpty()) {
            val parts = ArrayList<String>(2)
            if (cleanLangCountries.isNotEmpty()) {
                val ph = cleanLangCountries.joinToString(",") { "?" }
                parts += "c.country IN ($ph)"
                baseArgs.addAll(cleanLangCountries)
            }
            if (cleanLangCats.isNotEmpty()) {
                val ph = cleanLangCats.joinToString(",") { "?" }
                parts += "c.category IN ($ph)"
                baseArgs.addAll(cleanLangCats)
            }
            baseClauses += "(${parts.joinToString(" OR ")})"
        }
        val effectiveOrder = if (canStreamOrderFromIndex && orderSql.isNotBlank()) orderSql else "ORDER BY c.number ASC"
        val movieWhere = (listOfNotNull(contentTypeClause("movie")) + baseClauses).joinToString(" AND ")
        val seriesWhere = (listOfNotNull(contentTypeClause("series")) + baseClauses).joinToString(" AND ")
        val neededUnique = offset + limit + 1
        val scanCap = maxOf((offset + limit) * 14, 4800).coerceAtMost(12_000)
        val batchSize = 400

        class StreamCursor(val whereSql: String) {
            var buf: List<IptvChannel> = emptyList()
            var idx: Int = 0
            var dbOffset: Int = 0
            var exhausted: Boolean = false

            fun peek(): IptvChannel? {
                if (idx >= buf.size && !exhausted) {
                    val pagedArgs = (baseArgs + batchSize.toString() + dbOffset.toString()).toTypedArray()
                    val rows = db.rawQuery(
                        "SELECT c.* FROM channels c WHERE $whereSql $effectiveOrder LIMIT ? OFFSET ?",
                        pagedArgs,
                    ).use { c -> c.mapAll(::readChannel) }
                    dbOffset += rows.size
                    buf = rows
                    idx = 0
                    if (rows.size < batchSize) exhausted = true
                }
                return if (idx < buf.size) buf[idx] else null
            }

            fun pop(): IptvChannel? {
                val v = peek() ?: return null
                idx++
                return v
            }
        }

        val movieCur = StreamCursor(movieWhere)
        val seriesCur = StreamCursor(seriesWhere)
        val uniqueMap = LinkedHashMap<String, IptvChannel>(neededUnique.coerceAtMost(2000))
        val langsByKey = HashMap<String, LinkedHashSet<String>>(neededUnique.coerceAtMost(2000))
        var totalScanned = 0
        while (uniqueMap.size < neededUnique && totalScanned < scanCap) {
            val m = movieCur.peek()
            val s = seriesCur.peek()
            if (m == null && s == null) break
            val takeMovie = when {
                m == null -> false
                s == null -> true
                sortByName -> {
                    val cmp = m.name.lowercase(java.util.Locale.ROOT).compareTo(s.name.lowercase(java.util.Locale.ROOT))
                    if (cmp != 0) cmp <= 0 else m.number <= s.number
                }
                else -> m.number <= s.number
            }
            val next = (if (takeMovie) movieCur.pop() else seriesCur.pop()) ?: break
            totalScanned++
            val key = VodTaxonomy.normalizeVodDedupKey(next)
            val flagSet = langsByKey.getOrPut(key) { LinkedHashSet(3) }
            if (flagSet.size < 5) {
                flagSet.addAll(VodTaxonomy.detectVodFlagCodes(next))
            }
            val prev = uniqueMap[key]
            if (prev == null || VodTaxonomy.vodCandidateScore(next) > VodTaxonomy.vodCandidateScore(prev)) {
                uniqueMap[key] = next
            }
        }
        val allUnique = uniqueMap.entries.map { (key, ch) ->
            val flags = langsByKey[key]?.take(5).orEmpty()
            if (flags.isNotEmpty()) ch.copy(availableLanguageFlags = flags) else ch
        }
        val items = allUnique.drop(offset).take(limit)
        val bothExhausted = movieCur.exhausted && movieCur.idx >= movieCur.buf.size &&
            seriesCur.exhausted && seriesCur.idx >= seriesCur.buf.size
        val hasMore = allUnique.size > offset + items.size || !bothExhausted
        val totalHint = if (bothExhausted) allUnique.size else offset + items.size + if (hasMore) 1 else 0
        return items to totalHint
    }

    fun listVodCountryCounts(contentType: String = "vod"): Map<String, Int> {
        val normType = contentType.trim().lowercase(java.util.Locale.ROOT).ifBlank { "vod" }
        val now = System.currentTimeMillis()
        vodCountryCountCache[normType]?.let { (ts, cached) ->
            if (now - ts < 300_000L) return cached
        }
        if (normType == "vod") {
            val merged = LinkedHashMap<String, Int>()
            listVodCountryCounts("movie").forEach { (k, v) -> merged[k] = (merged[k] ?: 0) + v }
            listVodCountryCounts("series").forEach { (k, v) -> merged[k] = (merged[k] ?: 0) + v }
            vodCountryCountCache["vod"] = System.currentTimeMillis() to merged
            return merged
        }
        synchronized(vodCategoryComputeLock) {
            val nowLocked = System.currentTimeMillis()
            vodCountryCountCache[normType]?.let { (ts, cached) ->
                if (nowLocked - ts < 300_000L) return cached
            }
            val db = readableDatabase
            val clauses = ArrayList<String>(3)
            clauses += enabledChannelWhere()
            contentTypeClause(normType)?.let { clauses += it }
            val whereSql = clauses.joinToString(" AND ")
            val map = LinkedHashMap<String, Int>()
            db.rawQuery(
                "SELECT c.country, COUNT(*) FROM channels c WHERE c.country != '' AND $whereSql GROUP BY c.country",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    val code = c.getString(0)?.trim()?.uppercase(java.util.Locale.ROOT).orEmpty()
                    if (code.isNotEmpty()) {
                        map[code] = (map[code] ?: 0) + c.getInt(1)
                    }
                }
            }
            vodCountryCountCache[normType] = nowLocked to map
            return map
        }
    }

    fun listVodCategories(
        country: String? = null,
        contentType: String = "vod",
        limit: Int = 60,
        countriesFilter: Collection<String> = emptyList(),
        rawCategoriesMatch: Collection<String> = emptyList(),
    ): List<Pair<String, Int>> {
        val normCountry = country?.trim()?.uppercase(java.util.Locale.ROOT).orEmpty()
        val normType = contentType.trim().lowercase(java.util.Locale.ROOT).ifBlank { "vod" }
        val cleanCountries = countriesFilter.map { it.trim().uppercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }.distinct().sorted().take(40)
        val cleanLangCats = rawCategoriesMatch.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().take(80)
        val langCatsHash = cleanLangCats.joinToString("\u0001").hashCode()
        val key = "$normType|$normCountry|${cleanCountries.joinToString(",")}|$langCatsHash|$limit"
        val now = System.currentTimeMillis()
        vodCategoryCache[key]?.let { (ts, cached) ->
            if (now - ts < 300_000L) return cached
        }
        if (!vodClassifyReady && normCountry.isEmpty() && cleanCountries.isEmpty() && cleanLangCats.isEmpty()) {
            return computeCatalogStats().byCategory.filter { it.first.isNotBlank() }.take(limit)
        }
        if (normType == "vod") {
            val merged = LinkedHashMap<String, Int>()
            listVodCategories(country, "movie", maxOf(limit, 240), countriesFilter, rawCategoriesMatch)
                .forEach { (cat, cnt) -> merged[cat] = (merged[cat] ?: 0) + cnt }
            listVodCategories(country, "series", maxOf(limit, 240), countriesFilter, rawCategoriesMatch)
                .forEach { (cat, cnt) -> merged[cat] = (merged[cat] ?: 0) + cnt }
            val sorted = merged.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
            vodCategoryCache[key] = System.currentTimeMillis() to sorted
            return sorted
        }
        synchronized(vodCategoryComputeLock) {
            val nowLocked = System.currentTimeMillis()
            vodCategoryCache[key]?.let { (ts, cached) ->
                if (nowLocked - ts < 300_000L) return cached
            }
            val db = readableDatabase
            val clauses = ArrayList<String>(4)
            val args = ArrayList<String>(20)
            clauses += enabledChannelWhere()
            contentTypeClause(normType)?.let { clauses += it }
            if (normCountry.isNotEmpty()) {
                clauses += "c.country = ?"
                args += normCountry
            }
            if (cleanCountries.isNotEmpty() || cleanLangCats.isNotEmpty()) {
                val parts = ArrayList<String>(2)
                if (cleanCountries.isNotEmpty()) {
                    val ph = cleanCountries.joinToString(",") { "?" }
                    parts += "c.country IN ($ph)"
                    args.addAll(cleanCountries)
                }
                if (cleanLangCats.isNotEmpty()) {
                    val ph = cleanLangCats.joinToString(",") { "?" }
                    parts += "c.category IN ($ph)"
                    args.addAll(cleanLangCats)
                }
                clauses += "(${parts.joinToString(" OR ")})"
            }
            val whereSql = clauses.joinToString(" AND ")
            val list = db.rawQuery(
                "SELECT c.category, COUNT(*) FROM channels c WHERE c.category != '' AND $whereSql GROUP BY c.category",
                args.toTypedArray(),
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val cat = c.getString(0).orEmpty().trim()
                        if (cat.isNotEmpty()) add(cat to c.getInt(1))
                    }
                }.sortedByDescending { it.second }.take(limit)
            }
            vodCategoryCache[key] = nowLocked to list
            return list
        }
    }

    fun listSeriesEpisodes(channel: IptvChannel, limit: Int = 600): List<IptvChannel> {
        val db = readableDatabase
        val enabledWhere = enabledChannelWhere()
        val parsedTarget = M3uParser.parseSeriesEpisode(channel.name)
        val targetShowNorm = VodTaxonomy.normalizeShowKey(parsedTarget?.showTitle ?: channel.name)
        val rawBase = M3uParser.extractSeriesBaseTitle(channel.name)
        val cleanShowTitle = parsedTarget?.showTitle?.takeIf { it.length >= 2 }

        val candidates = ArrayList<IptvChannel>()
        fun runPrefixQuery(prefix: String, restrictCategory: Boolean) {
            val clauses = ArrayList<String>(4)
            val args = ArrayList<String>(6)
            clauses += enabledWhere
            contentTypeClause("series")?.let { clauses += it }
            if (restrictCategory && channel.category.isNotBlank()) {
                clauses += "c.category = ?"
                args += channel.category
            }
            val low = prefix.trim().lowercase(java.util.Locale.ROOT)
            val upper = low.takeIf { it.isNotEmpty() && it.last() != Char.MAX_VALUE }
                ?.let { it.dropLast(1) + (it.last().code + 1).toChar() }
            if (upper != null) {
                clauses += "c.name >= ? COLLATE NOCASE AND c.name < ? COLLATE NOCASE"
                args += low
                args += upper
            } else {
                val escaped = escapeSqlLikeLiteral(prefix)
                clauses += "c.name LIKE ? ESCAPE '\\' COLLATE NOCASE"
                args += "$escaped%"
            }
            args += (limit * 2).coerceAtLeast(300).toString()
            db.rawQuery(
                "SELECT c.* FROM channels c WHERE ${clauses.joinToString(" AND ")} ORDER BY c.name COLLATE NOCASE ASC LIMIT ?",
                args.toTypedArray(),
            ).use { candidates.addAll(it.mapAll(::readChannel)) }
        }

        if (!rawBase.isNullOrBlank()) {
            runPrefixQuery(rawBase, restrictCategory = channel.category.isNotBlank())
        }
        if (candidates.size <= 1 && !cleanShowTitle.isNullOrBlank() && cleanShowTitle != rawBase) {
            runPrefixQuery(cleanShowTitle, restrictCategory = false)
        }
        if (candidates.size <= 1 && !rawBase.isNullOrBlank() && channel.category.isNotBlank()) {
            runPrefixQuery(rawBase, restrictCategory = false)
        }
        if (candidates.isEmpty()) {
            return listOf(channel)
        }

        val unique = LinkedHashMap<String, IptvChannel>()
        val scoredList = candidates.filter { ep ->
            val info = M3uParser.parseSeriesEpisode(ep.name)
            val norm = VodTaxonomy.normalizeShowKey(info?.showTitle ?: ep.name)
            norm == targetShowNorm || ep.id == channel.id
        }
        for (ep in scoredList.ifEmpty { listOf(channel) }) {
            val k = VodTaxonomy.normalizeSeriesEpisodeKey(ep)
            val prev = unique[k]
            if (prev == null || VodTaxonomy.vodCandidateScore(ep) > VodTaxonomy.vodCandidateScore(prev)) {
                unique[k] = ep
            }
        }
        return unique.values
            .sortedWith(
                compareBy<IptvChannel>(
                    { M3uParser.parseSeriesEpisode(it.name)?.season ?: 1 },
                    { M3uParser.parseSeriesEpisode(it.name)?.episode ?: Int.MAX_VALUE },
                    { it.name.lowercase(Locale.ROOT) },
                ),
            )
            .take(limit)
    }

    fun findNextSeriesEpisode(channel: IptvChannel): IptvChannel? {
        val episodes = listSeriesEpisodes(channel, limit = 600)
        if (episodes.size <= 1) return null
        val curKey = VodTaxonomy.normalizeSeriesEpisodeKey(channel)
        val idx = episodes.indexOfFirst { it.id == channel.id || VodTaxonomy.normalizeSeriesEpisodeKey(it) == curKey }
        if (idx < 0 || idx + 1 >= episodes.size) return null
        return episodes[idx + 1]
    }

    fun getSeriesDetail(profileId: String, channelId: String): Pair<SeriesDetailResult, Map<String, Pair<Long, Long>>>? {
        val rep = getChannel(channelId) ?: return null
        val parsed = M3uParser.parseSeriesEpisode(rep.name)
        val showTitle = parsed?.showTitle?.takeIf { it.isNotBlank() } ?: rep.name
        val episodes = listSeriesEpisodes(rep, limit = 600)
        val progMap = getVodProgressMap(profileId, episodes.map { it.id }, includeUpNext = true)
        val seasons = java.util.TreeMap<Int, MutableList<IptvChannel>>()
        for (ep in episodes) {
            val s = M3uParser.parseSeriesEpisode(ep.name)?.season ?: 1
            seasons.getOrPut(s) { ArrayList() }.add(ep)
        }
        val inProgOrUpNext = episodes.firstOrNull { ep ->
            val pos = progMap[ep.id]?.first ?: 0L
            pos >= 15_000L || pos == 1L
        }
        val resumeId = inProgOrUpNext?.id ?: episodes.firstOrNull()?.id
        return SeriesDetailResult(
            showTitle = showTitle,
            representative = rep,
            seasons = seasons,
            resumeEpisodeId = resumeId,
            totalEpisodes = episodes.size,
        ) to progMap
    }

    fun listRelatedVodOrChannels(channel: IptvChannel, limit: Int = 36): Pair<String, List<IptvChannel>> {
        val db = readableDatabase
        val enabledWhere = enabledChannelWhere()
        fun dedupVodList(raw: List<IptvChannel>): List<IptvChannel> {
            val map = LinkedHashMap<String, IptvChannel>(raw.size)
            for (item in raw) {
                val k = VodTaxonomy.normalizeVodDedupKey(item)
                val prev = map[k]
                if (prev == null || VodTaxonomy.vodCandidateScore(item) > VodTaxonomy.vodCandidateScore(prev)) {
                    map[k] = item
                }
            }
            return map.values.take(limit)
        }
        if (channel.contentType == "series") {
            val episodes = listSeriesEpisodes(channel, limit = maxOf(limit, 240))
            if (episodes.size > 1) return "episodes" to episodes.take(limit)
        }
        val clauses = ArrayList<String>(4)
        val args = ArrayList<String>(4)
        clauses += enabledWhere
        if (channel.isVod) {
            contentTypeClause(channel.contentType)?.let { clauses += it }
        } else {
            contentTypeClause("live")?.let { clauses += it }
        }
        if (channel.category.isNotBlank()) {
            clauses += "c.category = ?"
            args += channel.category
        } else if (channel.country.isNotBlank()) {
            clauses += "c.country = ?"
            args += channel.country.uppercase(java.util.Locale.ROOT)
        }
        val fetchLim = if (channel.isVod) limit * 3 else limit
        args += fetchLim.toString()
        val items = db.rawQuery(
            "SELECT c.* FROM channels c WHERE ${clauses.joinToString(" AND ")} ORDER BY c.number ASC LIMIT ?",
            args.toTypedArray(),
        ).use { it.mapAll(::readChannel) }
        val finalItems = if (channel.isVod) dedupVodList(items) else items
        return (if (channel.isVod) "similar" else "channels") to finalItems
    }

    fun sampleLiveChannelsForMix(countries: Set<String>? = null, limit: Int = 80): List<IptvChannel> {
        val db = readableDatabase
        val liveWhere = contentTypeClause("live") ?: "1 = 1"
        val enabledWhere = enabledChannelWhere()
        val statsCountries = computeCatalogStats().byCountry
        val candidateCountries = if (!countries.isNullOrEmpty()) {
            val normSet = countries.mapTo(HashSet()) { it.trim().uppercase(Locale.ROOT) }
            val matched = statsCountries.filter { it.first in normSet && it.second > 0 }
            if (matched.isNotEmpty()) matched else normSet.map { it to 50 }
        } else {
            statsCountries.filter { it.second >= 5 }
        }.shuffled().take(10)

        if (candidateCountries.isEmpty()) return emptyList()
        val perCountry = (limit / candidateCountries.size).coerceIn(5, 16)
        val rng = java.util.Random()
        val out = ArrayList<IptvChannel>(limit)
        for ((code, cnt) in candidateCountries) {
            val maxOff = (minOf(cnt, 360) - perCountry).coerceAtLeast(0)
            val off = if (maxOff > 0) rng.nextInt(maxOff + 1) else 0
            val batch = if (liveCanonReady && !hasDisabledPlaylists()) {
                db.rawQuery(
                    "SELECT c.*, lcm.source_count AS source_count FROM live_canonical_map lcm INNER JOIN channels c ON c.id = lcm.channel_id WHERE lcm.is_primary = 1 AND c.country = ? AND $enabledWhere LIMIT ? OFFSET ?",
                    arrayOf(code, perCountry.toString(), off.toString()),
                ).use { it.mapAll(::readChannel) }
            } else {
                db.rawQuery(
                    "SELECT c.* FROM channels c WHERE $liveWhere AND c.country = ? AND $enabledWhere LIMIT ? OFFSET ?",
                    arrayOf(code, perCountry.toString(), off.toString()),
                ).use { it.mapAll(::readChannel) }
            }
            out.addAll(batch)
        }
        return out.shuffled().take(limit)
    }

    fun listPlaylists(): List<PlaylistEntry> {
        return readableDatabase.rawQuery("SELECT * FROM playlists ORDER BY added_at ASC", null).use { cursor ->
            cursor.mapAll(::readPlaylist)
        }
    }

    private fun readPlaylist(cursor: Cursor): PlaylistEntry {
        val statusCol = cursor.getColumnIndex("status")
        val errCol = cursor.getColumnIndex("last_error")
        val hashCol = cursor.getColumnIndex("content_hash")
        return PlaylistEntry(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
            enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1,
            channelCount = cursor.getInt(cursor.getColumnIndexOrThrow("channel_count")),
            addedAt = cursor.getLong(cursor.getColumnIndexOrThrow("added_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
            status = if (statusCol >= 0) cursor.getString(statusCol).orEmpty().ifBlank { "ok" } else "ok",
            lastError = if (errCol >= 0) cursor.getString(errCol).orEmpty() else "",
            contentHash = if (hashCol >= 0) cursor.getString(hashCol).orEmpty() else "",
        )
    }

    fun getPlaylist(id: String): PlaylistEntry? {
        return readableDatabase.rawQuery("SELECT * FROM playlists WHERE id = ? LIMIT 1", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) readPlaylist(cursor) else null
        }
    }

    private fun normalizePlaylistUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return runCatching {
            val u = java.net.URI(trimmed)
            val scheme = u.scheme?.lowercase(java.util.Locale.ROOT).orEmpty()
            val host = u.host?.lowercase(java.util.Locale.ROOT).orEmpty()
            val port = if (u.port > 0) ":${u.port}" else ""
            val path = (u.rawPath ?: "").trimEnd('/').ifEmpty { "" }
            val query = u.rawQuery?.let { "?$it" }.orEmpty()
            if (scheme.isNotBlank() && host.isNotBlank()) "$scheme://$host$port$path$query" else trimmed
        }.getOrElse { trimmed }
    }

    fun findPlaylistByUrl(rawUrl: String): PlaylistEntry? {
        val norm = normalizePlaylistUrl(rawUrl)
        if (norm.isBlank()) return null
        return listPlaylists().firstOrNull { p ->
            p.url.isNotBlank() && normalizePlaylistUrl(p.url) == norm
        }
    }

    fun findPlaylistByContentHash(hash: String): PlaylistEntry? {
        val clean = hash.trim()
        if (clean.isBlank()) return null
        return listPlaylists().firstOrNull { p ->
            p.contentHash == clean && p.channelCount > 0 && p.status != "error"
        }
    }

    fun findUploadedPlaylistByName(name: String): PlaylistEntry? {
        val clean = name.trim()
        if (clean.isBlank()) return null
        return listPlaylists().firstOrNull { p ->
            p.url.isBlank() && p.name.equals(clean, ignoreCase = true)
        }
    }

    fun savePlaylistRecord(playlist: PlaylistEntry) = synchronized(writeLock) {
        val cv = ContentValues().apply {
            put("id", playlist.id)
            put("name", playlist.name)
            put("url", playlist.url)
            put("enabled", if (playlist.enabled) 1 else 0)
            put("channel_count", playlist.channelCount)
            put("added_at", playlist.addedAt)
            put("updated_at", playlist.updatedAt)
            put("status", playlist.status)
            put("last_error", playlist.lastError)
            put("content_hash", playlist.contentHash)
        }
        writableDatabase.insertWithOnConflict("playlists", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        invalidateCatalogCache()
    }

    fun updatePlaylistStatus(id: String, status: String, lastError: String) = synchronized(writeLock) {
        val cv = ContentValues().apply {
            put("status", status)
            put("last_error", lastError)
            put("updated_at", System.currentTimeMillis() / 1000)
        }
        writableDatabase.update("playlists", cv, "id = ?", arrayOf(id))
    }

    fun clearPlaylistWarnForChannel(channelId: String) {
        val warnIds = readableDatabase.rawQuery(
            """
            SELECT DISTINCT p.id FROM playlists p
            WHERE p.status = 'warn' AND (
                p.id IN (SELECT pc.playlist_id FROM playlist_channels pc WHERE pc.channel_id = ?)
                OR p.id IN (SELECT c.playlist_id FROM channels c WHERE c.id = ?)
            )
            """.trimIndent(),
            arrayOf(channelId, channelId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        if (warnIds.isEmpty()) return
        val now = System.currentTimeMillis() / 1000
        synchronized(writeLock) {
            val db = writableDatabase
            for (plId in warnIds) {
                db.execSQL(
                    "UPDATE playlists SET status = 'ok', last_error = '', updated_at = ? WHERE id = ? AND status = 'warn'",
                    arrayOf(now, plId),
                )
            }
        }
    }

    fun countPlaylistChannelLinks(playlistId: String): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM playlist_channels WHERE playlist_id = ?",
            arrayOf(playlistId),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun sampleChannelsForPlaylist(playlistId: String, limit: Int = 16): List<IptvChannel> {
        val db = readableDatabase
        val bounds = db.rawQuery(
            "SELECT MIN(position), MAX(position) FROM playlist_channels WHERE playlist_id = ?",
            arrayOf(playlistId),
        ).use { c ->
            if (c.moveToFirst() && !c.isNull(0) && !c.isNull(1)) {
                c.getLong(0) to c.getLong(1)
            } else {
                null
            }
        }

        val target = limit.coerceIn(1, 24)
        val sampleCount = (target * 2).coerceAtMost(32)
        val out = ArrayList<IptvChannel>(sampleCount)
        val seenIds = HashSet<String>()
        if (bounds != null) {
            val (minPos, maxPos) = bounds
            val span = (maxPos - minPos).coerceAtLeast(0L)
            val positions = LinkedHashSet<Long>(sampleCount)
            for (d in listOf(0L, 1L, 2L, 5L, 15L)) {
                positions += (minPos + d).coerceAtMost(maxPos)
            }
            for (i in 0 until sampleCount) {
                val pos = if (sampleCount <= 1 || span == 0L) minPos else minPos + (i.toLong() * span / (sampleCount - 1).toLong())
                positions += pos
            }
            for (pos in positions) {
                db.rawQuery(
                    """
                    SELECT c.* FROM playlist_channels pc
                    JOIN channels c ON c.id = pc.channel_id
                    WHERE pc.playlist_id = ? AND pc.position >= ?
                    ORDER BY pc.position ASC
                    LIMIT 1
                    """.trimIndent(),
                    arrayOf(playlistId, pos.toString()),
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val ch = readChannel(cursor)
                        if (seenIds.add(ch.id)) out += ch
                    }
                }
            }
        } else {
            db.rawQuery(
                "SELECT * FROM channels WHERE playlist_id = ? LIMIT ?",
                arrayOf(playlistId, sampleCount.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val ch = readChannel(cursor)
                    if (seenIds.add(ch.id)) out += ch
                }
            }
        }
        if (out.isEmpty()) return emptyList()
        val preferred = out.filter { ch ->
            val raw = ch.url.lowercase(java.util.Locale.ROOT)
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
        val pool = if (preferred.isNotEmpty()) preferred else out
        val distinctHostFirst = ArrayList<IptvChannel>(pool.size)
        val remainder = ArrayList<IptvChannel>(pool.size)
        val seenHosts = HashSet<String>()
        for (ch in pool) {
            val host = runCatching { java.net.URI(ch.url.trim()).host?.lowercase(java.util.Locale.ROOT) }.getOrNull().orEmpty()
            if (host.isNotBlank() && seenHosts.add(host)) {
                distinctHostFirst += ch
            } else {
                remainder += ch
            }
        }
        return (distinctHostFirst + remainder).take(target)
    }

    private fun computeSampleOffsets(total: Int, count: Int): List<Int> {
        if (total <= 0) return emptyList()
        val desired = count.coerceAtMost(total)
        if (desired <= 1) return listOf(0)
        val step = (total - 1).coerceAtLeast(1)
        val offsets = LinkedHashSet<Int>(desired)
        for (i in 0 until desired) {
            offsets += (i.toLong() * step.toLong() / (desired - 1).toLong()).toInt()
        }
        return offsets.toList()
    }

    fun togglePlaylist(id: String, enabled: Boolean) = synchronized(writeLock) {
        val cv = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
            put("updated_at", System.currentTimeMillis() / 1000)
        }
        writableDatabase.update("playlists", cv, "id = ?", arrayOf(id))
        invalidateCatalogCache()
    }

    data class DeduplicateResult(
        val removedCount: Int,
        val removedNames: List<String>,
        val remainingCount: Int,
    )

    fun deletePlaylist(id: String, skipOrphanScan: Boolean = false) {
        while (true) {
            val deleted = synchronized(writeLock) {
                val db = writableDatabase
                db.beginTransaction()
                try {
                    db.execSQL(
                        """
                        DELETE FROM playlist_channels
                        WHERE rowid IN (
                            SELECT rowid FROM playlist_channels
                            WHERE playlist_id = ?
                            LIMIT 25000
                        )
                        """.trimIndent(),
                        arrayOf(id),
                    )
                    val changes = db.compileStatement("SELECT changes()").use { it.simpleQueryForLong().toInt() }
                    db.setTransactionSuccessful()
                    changes
                } finally {
                    db.endTransaction()
                }
            }
            if (deleted < 25_000) break
            try {
                Thread.sleep(5L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        synchronized(writeLock) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("playlists", "id = ?", arrayOf(id))
                if (!skipOrphanScan) {
                    val deletedCount = db.delete(
                        "channels",
                        "NOT EXISTS (SELECT 1 FROM playlist_channels pc WHERE pc.channel_id = channels.id) AND (playlist_id = ? OR NOT EXISTS (SELECT 1 FROM playlists p WHERE p.id = channels.playlist_id))",
                        arrayOf(id),
                    )
                    if (deletedCount > 0) resetSearchIndexLocked(db)
                    db.execSQL(
                        """
                        UPDATE channels
                        SET playlist_id = (SELECT pc.playlist_id FROM playlist_channels pc WHERE pc.channel_id = channels.id LIMIT 1)
                        WHERE playlist_id = ?
                          AND EXISTS (SELECT 1 FROM playlist_channels pc WHERE pc.channel_id = channels.id)
                        """.trimIndent(),
                        arrayOf(id),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                invalidateCatalogCache()
            }
        }
    }

    private fun samplePlaylistChannelIds(playlistId: String): List<String> {
        val db = readableDatabase
        val out = LinkedHashSet<String>(120)
        db.rawQuery(
            "SELECT channel_id FROM playlist_channels WHERE playlist_id = ? ORDER BY position ASC LIMIT 60",
            arrayOf(playlistId),
        ).use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        db.rawQuery(
            "SELECT channel_id FROM playlist_channels WHERE playlist_id = ? ORDER BY position DESC LIMIT 60",
            arrayOf(playlistId),
        ).use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        return out.toList()
    }

    private fun countSharedChannelIds(playlistId: String, channelIds: List<String>): Int {
        if (channelIds.isEmpty()) return 0
        val db = readableDatabase
        var total = 0
        for (chunk in channelIds.chunked(120)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val args = (listOf(playlistId) + chunk).toTypedArray()
            total += db.rawQuery(
                "SELECT COUNT(*) FROM playlist_channels WHERE playlist_id = ? AND channel_id IN ($placeholders)",
                args,
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }
        return total
    }

    fun deduplicatePlaylists(): DeduplicateResult {
        val all = listPlaylists().filter { it.status != "importing" }
        val rankComparator = compareByDescending<PlaylistEntry> { it.enabled }
            .thenByDescending { it.status == "ok" }
            .thenByDescending { it.channelCount }
            .thenBy { it.addedAt }

        val toRemove = LinkedHashMap<String, PlaylistEntry>()

        // Pass 1: exact normalized remote URL duplicates
        all.filter { it.url.isNotBlank() }
            .groupBy { normalizePlaylistUrl(it.url) }
            .values
            .forEach { group ->
                if (group.size > 1) {
                    val sorted = group.sortedWith(rankComparator)
                    sorted.drop(1).forEach { toRemove[it.id] = it }
                }
            }

        // Pass 2: exact file contentHash duplicates
        all.filter { it.id !in toRemove && it.contentHash.isNotBlank() && it.channelCount > 0 }
            .groupBy { it.contentHash }
            .values
            .forEach { group ->
                if (group.size > 1) {
                    val sorted = group.sortedWith(rankComparator)
                    sorted.drop(1).forEach { toRemove[it.id] = it }
                }
            }

        // Pass 3: uploaded files without contentHash whose channels are >= 98% covered by a kept playlist
        val keptUploaded = mutableListOf<PlaylistEntry>()
        val candidates = all
            .filter { it.id !in toRemove && it.url.isBlank() && it.channelCount > 0 }
            .sortedWith(rankComparator)
        for (cand in candidates) {
            var isDup = false
            val eligibleParents = keptUploaded.filter { it.channelCount >= cand.channelCount }
            if (eligibleParents.isNotEmpty()) {
                val sample = samplePlaylistChannelIds(cand.id)
                if (sample.size >= 10) {
                    val threshold = (sample.size * 0.98).toInt().coerceAtLeast(1)
                    for (parent in eligibleParents) {
                        if (countSharedChannelIds(parent.id, sample) >= threshold) {
                            isDup = true
                            break
                        }
                    }
                }
            }
            if (isDup) {
                toRemove[cand.id] = cand
            } else {
                keptUploaded += cand
            }
        }

        val removedNames = mutableListOf<String>()
        for (dup in toRemove.values) {
            deletePlaylist(dup.id, skipOrphanScan = true)
            removedNames += dup.name.ifBlank { dup.id }
        }
        return DeduplicateResult(
            removedCount = removedNames.size,
            removedNames = removedNames,
            remainingCount = listPlaylists().size,
        )
    }

    fun reclassifyChannelCountries(): Int {
        var cursorRowId = 0L
        var totalUpdated = 0
        while (true) {
            val (nextRowId, updated) = reclassifyBatchAfterRowId(cursorRowId, limit = 5000)
            totalUpdated += updated
            if (nextRowId <= cursorRowId) break
            cursorRowId = nextRowId
        }
        invalidateCatalogCache()
        return totalUpdated
    }

    /**
     * Keyset-paginated country classification in small transactions so multi-million-row
     * imports categorize smoothly with bounded memory and without locking readers.
     */
    fun reclassifyBatchAfterRowId(afterRowId: Long, limit: Int = 5000): Pair<Long, Int> = synchronized(writeLock) {
        var maxSeenRowId = afterRowId
        val updates = ArrayList<Pair<String, String>>(256)
        readableDatabase.rawQuery(
            "SELECT rowid, id, name, category, country, url, tvg_id FROM channels WHERE rowid > ? ORDER BY rowid ASC LIMIT ?",
            arrayOf(afterRowId.toString(), limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val rid = c.getLong(0)
                if (rid > maxSeenRowId) maxSeenRowId = rid
                val id = c.getString(1) ?: continue
                val name = c.getString(2).orEmpty()
                val category = c.getString(3).orEmpty()
                val existingCountry = c.getString(4).orEmpty().trim()
                val url = c.getString(5).orEmpty()
                val tvgId = c.getString(6).orEmpty()
                val inferred = M3uParser.inferCountryForExisting(
                    tvgId = tvgId,
                    group = category,
                    name = name,
                    url = url,
                )
                val nextCountry = when {
                    inferred.isNotBlank() -> inferred
                    existingCountry.isNotBlank() &&
                        M3uParser.isTypedStreamPathUrl(url) &&
                        M3uParser.extractHostCcTldRaw(url) == existingCountry -> ""
                    else -> existingCountry
                }
                if (nextCountry != existingCountry) {
                    updates += id to nextCountry
                }
            }
        }
        if (updates.isNotEmpty()) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.compileStatement("UPDATE channels SET country = ? WHERE id = ?").use { stmt ->
                    updates.forEach { (id, country) ->
                        stmt.clearBindings()
                        stmt.bindString(1, country)
                        stmt.bindString(2, id)
                        stmt.executeUpdateDelete()
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        return@synchronized maxSeenRowId to updates.size
    }

    fun preparePlaylistImport(playlist: PlaylistEntry): Pair<Long, Int> {
        synchronized(writeLock) {
            savePlaylistRecord(
                playlist.copy(
                    status = "importing",
                    lastError = "Preparazione importazione…",
                    updatedAt = System.currentTimeMillis() / 1000,
                ),
            )
        }
        // Delete existing links in bounded 5k-row chunks so concurrent readers/writers never stall on writeLock.
        var deletedTotal = 0
        while (true) {
            val deleted = synchronized(writeLock) {
                val db = writableDatabase
                db.beginTransaction()
                try {
                    db.execSQL(
                        """
                        DELETE FROM playlist_channels
                        WHERE rowid IN (
                            SELECT rowid FROM playlist_channels
                            WHERE playlist_id = ?
                            LIMIT 5000
                        )
                        """.trimIndent(),
                        arrayOf(playlist.id),
                    )
                    val changes = db.compileStatement("SELECT changes()").use { it.simpleQueryForLong().toInt() }
                    db.setTransactionSuccessful()
                    changes
                } finally {
                    db.endTransaction()
                }
            }
            deletedTotal += deleted
            if (deleted < 5_000) break
            try {
                Thread.sleep(10L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return synchronized(writeLock) {
            val db = writableDatabase
            val maxRowId = db.rawQuery("SELECT COALESCE(MAX(rowid), 0) FROM channels", null).use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
            val nextNumber = db.rawQuery("SELECT COALESCE(MAX(number), 0) + 1 FROM channels", null).use {
                if (it.moveToFirst()) it.getInt(0).coerceAtLeast(1) else 1
            }
            maxRowId to nextNumber
        }
    }

    fun upsertChannelBatch(
        playlistId: String,
        batch: List<IptvChannel>,
        startPosition: Int,
        startNumber: Int,
    ): Pair<Int, Int> = synchronized(writeLock) {
        if (batch.isEmpty()) return@synchronized 0 to startNumber
        val db = writableDatabase
        var nextNumber = startNumber.coerceAtLeast(1)
        var linked = 0
        db.beginTransaction()
        try {
            db.compileStatement(
                """
                INSERT OR IGNORE INTO channels
                (id, name, url, logo, category, language, country, tvg_id, quality, number, added_at, playlist_id, content_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { insertCh ->
                db.compileStatement(
                    """
                    UPDATE channels SET
                        logo = CASE WHEN (logo IS NULL OR logo = '') AND ? IS NOT NULL THEN ? ELSE logo END,
                        category = CASE WHEN (category = '' OR LOWER(category) = 'general') AND ? != '' AND LOWER(?) != 'general' THEN ? ELSE category END,
                        language = CASE WHEN language = '' AND ? != '' THEN ? ELSE language END,
                        country = CASE WHEN country = '' AND ? != '' THEN ? ELSE country END,
                        tvg_id = CASE WHEN tvg_id = '' AND ? != '' THEN ? ELSE tvg_id END,
                        quality = CASE WHEN quality = '' AND ? != '' THEN ? ELSE quality END
                    WHERE id = ?
                      AND ((logo IS NULL OR logo = '') OR country = '' OR tvg_id = '' OR category = '' OR LOWER(category) = 'general' OR language = '' OR quality = '')
                    """.trimIndent(),
                ).use { updateExisting ->
                    db.compileStatement(
                        "INSERT OR IGNORE INTO playlist_channels(playlist_id, channel_id, position) VALUES (?, ?, ?)",
                    ).use { insertLink ->
                        batch.forEachIndexed { idx, channel ->
                            val cleanLogo = channel.logo?.takeIf { it.isNotBlank() }
                            insertCh.clearBindings()
                            insertCh.bindString(1, channel.id)
                            insertCh.bindString(2, channel.name)
                            insertCh.bindString(3, channel.url)
                            if (cleanLogo != null) insertCh.bindString(4, cleanLogo) else insertCh.bindNull(4)
                            insertCh.bindString(5, channel.category)
                            insertCh.bindString(6, channel.language)
                            insertCh.bindString(7, channel.country)
                            insertCh.bindString(8, channel.tvgId)
                            insertCh.bindString(9, channel.quality)
                            insertCh.bindLong(10, nextNumber.toLong())
                            insertCh.bindLong(11, channel.addedAt)
                            insertCh.bindString(12, playlistId)
                            insertCh.bindString(13, channel.contentType.ifBlank { "live" })
                            val rowId = insertCh.executeInsert()
                            if (rowId != -1L) {
                                nextNumber++
                            } else if (
                                cleanLogo != null ||
                                channel.country.isNotBlank() ||
                                channel.tvgId.isNotBlank() ||
                                (channel.category.isNotBlank() && !channel.category.equals(M3uParser.GENERAL_CATEGORY, true))
                            ) {
                                updateExisting.clearBindings()
                                if (cleanLogo != null) {
                                    updateExisting.bindString(1, cleanLogo)
                                    updateExisting.bindString(2, cleanLogo)
                                } else {
                                    updateExisting.bindNull(1)
                                    updateExisting.bindNull(2)
                                }
                                updateExisting.bindString(3, channel.category)
                                updateExisting.bindString(4, channel.category)
                                updateExisting.bindString(5, channel.category)
                                updateExisting.bindString(6, channel.language)
                                updateExisting.bindString(7, channel.language)
                                updateExisting.bindString(8, channel.country)
                                updateExisting.bindString(9, channel.country)
                                updateExisting.bindString(10, channel.tvgId)
                                updateExisting.bindString(11, channel.tvgId)
                                updateExisting.bindString(12, channel.quality)
                                updateExisting.bindString(13, channel.quality)
                                updateExisting.bindString(14, channel.id)
                                updateExisting.executeUpdateDelete()
                            }

                            insertLink.clearBindings()
                            insertLink.bindString(1, playlistId)
                            insertLink.bindString(2, channel.id)
                            insertLink.bindLong(3, (startPosition + idx).toLong())
                            insertLink.executeInsert()
                            linked++
                        }
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return@synchronized linked to nextNumber
    }

    fun updatePlaylistImportProgress(playlistId: String, importedCount: Int, statusMessage: String) = synchronized(writeLock) {
        val cv = ContentValues().apply {
            put("channel_count", importedCount)
            put("status", "importing")
            put("last_error", statusMessage)
            put("updated_at", System.currentTimeMillis() / 1000)
        }
        writableDatabase.update("playlists", cv, "id = ?", arrayOf(playlistId))
    }

    fun finalizePlaylistImport(
        playlistId: String,
        totalChannels: Int,
        status: String,
        lastError: String,
        cleanupOrphans: Boolean = false,
    ) = synchronized(writeLock) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (cleanupOrphans) {
                val deletedOrphans = db.delete(
                    "channels",
                    "playlist_id = ? AND NOT EXISTS (SELECT 1 FROM playlist_channels pc WHERE pc.channel_id = channels.id)",
                    arrayOf(playlistId),
                )
                if (deletedOrphans > 0) resetSearchIndexLocked(db)
                db.execSQL(
                    """
                    UPDATE channels
                    SET playlist_id = (
                        SELECT pc.playlist_id FROM playlist_channels pc WHERE pc.channel_id = channels.id LIMIT 1
                    )
                    WHERE playlist_id = ?
                      AND EXISTS (SELECT 1 FROM playlist_channels pc WHERE pc.channel_id = channels.id)
                      AND NOT EXISTS (
                          SELECT 1 FROM playlist_channels pc2
                          WHERE pc2.channel_id = channels.id AND pc2.playlist_id = ?
                      )
                    """.trimIndent(),
                    arrayOf(playlistId, playlistId),
                )
            }
            val cv = ContentValues().apply {
                put("channel_count", totalChannels)
                put("status", status)
                put("last_error", lastError)
                put("updated_at", System.currentTimeMillis() / 1000)
            }
            db.update("playlists", cv, "id = ?", arrayOf(playlistId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            invalidateCatalogCache()
        }
    }

    private data class ExistingChannelMeta(
        val number: Int,
        val logo: String?,
        val category: String,
        val language: String,
        val country: String,
        val tvgId: String,
        val quality: String,
        val playlistId: String,
        val contentType: String,
    )

    fun addChannelsForPlaylist(playlist: PlaylistEntry, channels: List<IptvChannel>) = synchronized(writeLock) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existingMeta = HashMap<String, ExistingChannelMeta>(16_384)
            db.rawQuery(
                "SELECT id, number, logo, category, language, country, tvg_id, quality, playlist_id, content_type FROM channels",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    existingMeta[c.getString(0)] = ExistingChannelMeta(
                        number = c.getInt(1),
                        logo = c.getString(2),
                        category = c.getString(3).orEmpty(),
                        language = c.getString(4).orEmpty(),
                        country = c.getString(5).orEmpty(),
                        tvgId = c.getString(6).orEmpty(),
                        quality = c.getString(7).orEmpty(),
                        playlistId = c.getString(8).orEmpty(),
                        contentType = c.getString(9).orEmpty(),
                    )
                }
            }

            db.delete("playlist_channels", "playlist_id = ?", arrayOf(playlist.id))

            db.compileStatement(
                """
                INSERT OR REPLACE INTO channels
                (id, name, url, logo, category, language, country, tvg_id, quality, number, added_at, playlist_id, content_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { upsertChannel ->
                db.compileStatement(
                    "INSERT OR REPLACE INTO playlist_channels(playlist_id, channel_id, position) VALUES (?, ?, ?)",
                ).use { insertLink ->
                    channels.forEachIndexed { idx, channel ->
                        val prev = existingMeta[channel.id]
                        val resolvedLogo = channel.logo?.takeIf { it.isNotBlank() } ?: prev?.logo
                        val resolvedCategory =
                            if (channel.category != M3uParser.GENERAL_CATEGORY && channel.category.isNotBlank()) {
                                channel.category
                            } else {
                                prev?.category?.takeIf { it.isNotBlank() } ?: channel.category
                            }
                        val resolvedLang = channel.language.ifBlank { prev?.language.orEmpty() }
                        val resolvedCountry = channel.country.ifBlank { prev?.country.orEmpty() }
                        val resolvedTvgId = channel.tvgId.ifBlank { prev?.tvgId.orEmpty() }
                        val resolvedQuality = channel.quality.ifBlank { prev?.quality.orEmpty() }
                        val resolvedNumber = prev?.number ?: 0
                        val resolvedType = prev?.contentType?.takeIf { it.isNotBlank() }
                            ?: channel.contentType.takeIf { it.isNotBlank() }
                            ?: M3uParser.inferContentType(channel.url, channel.name)

                        upsertChannel.clearBindings()
                        upsertChannel.bindString(1, channel.id)
                        upsertChannel.bindString(2, channel.name)
                        upsertChannel.bindString(3, channel.url)
                        if (resolvedLogo != null) upsertChannel.bindString(4, resolvedLogo) else upsertChannel.bindNull(4)
                        upsertChannel.bindString(5, resolvedCategory)
                        upsertChannel.bindString(6, resolvedLang)
                        upsertChannel.bindString(7, resolvedCountry)
                        upsertChannel.bindString(8, resolvedTvgId)
                        upsertChannel.bindString(9, resolvedQuality)
                        upsertChannel.bindLong(10, resolvedNumber.toLong())
                        upsertChannel.bindLong(11, channel.addedAt)
                        upsertChannel.bindString(12, playlist.id)
                        upsertChannel.bindString(13, resolvedType)
                        upsertChannel.executeInsert()

                        insertLink.clearBindings()
                        insertLink.bindString(1, playlist.id)
                        insertLink.bindString(2, channel.id)
                        insertLink.bindLong(3, idx.toLong())
                        insertLink.executeInsert()
                    }
                }
            }

            db.delete(
                "channels",
                "playlist_id = ? AND NOT EXISTS (SELECT 1 FROM playlist_channels pc WHERE pc.channel_id = channels.id)",
                arrayOf(playlist.id),
            )
            db.execSQL(
                """
                UPDATE channels
                SET playlist_id = (
                    SELECT pc.playlist_id FROM playlist_channels pc WHERE pc.channel_id = channels.id LIMIT 1
                )
                WHERE playlist_id = ?
                  AND EXISTS (SELECT 1 FROM playlist_channels pc WHERE pc.channel_id = channels.id)
                  AND NOT EXISTS (
                      SELECT 1 FROM playlist_channels pc2
                      WHERE pc2.channel_id = channels.id AND pc2.playlist_id = ?
                  )
                """.trimIndent(),
                arrayOf(playlist.id, playlist.id),
            )

            savePlaylistRecord(playlist.copy(channelCount = channels.size, updatedAt = System.currentTimeMillis() / 1000))
            reindexChannels(db)
            resetSearchIndexLocked(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            invalidateCatalogCache()
        }
    }

    private fun reindexChannels(db: SQLiteDatabase) {
        val cursor = db.rawQuery(
            """
            SELECT id FROM channels
            ORDER BY CASE WHEN number > 0 THEN 0 ELSE 1 END ASC, number ASC, added_at ASC, rowid ASC
            """.trimIndent(),
            null,
        )
        val ids = ArrayList<String>(cursor.count.coerceAtLeast(16))
        cursor.use {
            while (it.moveToNext()) ids.add(it.getString(0))
        }
        db.compileStatement("UPDATE channels SET number = ? WHERE id = ?").use { stmt ->
            ids.forEachIndexed { index, id ->
                stmt.clearBindings()
                stmt.bindLong(1, (index + 1).toLong())
                stmt.bindString(2, id)
                stmt.executeUpdateDelete()
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Diagnostics (14-day retention)                                      */
    /* ------------------------------------------------------------------ */

    fun insertDiagnosticLogs(events: List<DiagnosticLogInput>): Int {
        if (events.isEmpty()) return 0
        synchronized(writeLock) {
            val db = writableDatabase
            var inserted = 0
            db.beginTransaction()
            try {
                db.compileStatement(
                    """
                    INSERT INTO diagnostic_logs(
                        ts_ms, source, severity, code, device_id, device_name, app_version,
                        client_ip, channel_id, channel_name, playback_mode, relay_key,
                        stream_url, message, details_json
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { stmt ->
                    for (ev in events) {
                        val codeClean = ev.code.trim().take(64)
                        if (codeClean.isEmpty()) continue
                        stmt.clearBindings()
                        stmt.bindLong(1, ev.tsMs)
                        stmt.bindString(2, ev.source.trim().ifBlank { "tv" }.take(24))
                        stmt.bindString(3, ev.severity.trim().ifBlank { "warn" }.lowercase().take(16))
                        stmt.bindString(4, codeClean)
                        stmt.bindString(5, ev.deviceId.trim().take(96))
                        stmt.bindString(6, ev.deviceName.trim().take(96))
                        stmt.bindString(7, ev.appVersion.trim().take(32))
                        stmt.bindString(8, ev.clientIp.trim().take(64))
                        stmt.bindString(9, ev.channelId.trim().take(96))
                        stmt.bindString(10, ev.channelName.trim().take(160))
                        stmt.bindString(11, ev.playbackMode.trim().take(24))
                        stmt.bindString(12, ev.relayKey.trim().take(64))
                        stmt.bindString(13, ev.streamUrl.trim().take(512))
                        stmt.bindString(14, ev.message.trim().take(500))
                        stmt.bindString(15, ev.detailsJson.ifBlank { "{}" }.take(4096))
                        stmt.executeInsert()
                        inserted += 1
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return inserted
        }
    }

    fun pruneOldDiagnosticLogs(retentionDays: Int = DIAGNOSTIC_RETENTION_DAYS): Int {
        val safeDays = retentionDays.coerceAtLeast(1).toLong()
        val cutoffMs = System.currentTimeMillis() - safeDays * 86_400_000L
        synchronized(writeLock) {
            val db = writableDatabase
            var deleted = db.delete("diagnostic_logs", "ts_ms < ?", arrayOf(cutoffMs.toString()))
            val total = db.rawQuery("SELECT COUNT(*) FROM diagnostic_logs", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
            if (total > DIAGNOSTIC_MAX_ROWS) {
                val excess = total - DIAGNOSTIC_MAX_ROWS
                db.execSQL(
                    """
                    DELETE FROM diagnostic_logs
                    WHERE id IN (SELECT id FROM diagnostic_logs ORDER BY ts_ms ASC, id ASC LIMIT ?)
                    """.trimIndent(),
                    arrayOf(excess),
                )
                deleted += excess
            }
            return deleted
        }
    }

    fun deleteDiagnosticLogsForDevice(deviceId: String): Int {
        val clean = deviceId.trim()
        if (clean.isBlank()) return 0
        synchronized(writeLock) {
            return writableDatabase.delete("diagnostic_logs", "device_id = ?", arrayOf(clean))
        }
    }

    fun queryDiagnosticLogs(
        sinceMs: Long,
        untilMs: Long? = null,
        code: String? = null,
        channel: String? = null,
        device: String? = null,
        severity: String? = null,
        source: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<DiagnosticLogRow> {
        val db = readableDatabase
        val clauses = ArrayList<String>()
        val args = ArrayList<String>()
        clauses.add("ts_ms >= ?")
        args.add(sinceMs.toString())
        if (untilMs != null && untilMs > 0L) {
            clauses.add("ts_ms <= ?")
            args.add(untilMs.toString())
        }
        code?.trim()?.takeIf { it.isNotEmpty() }?.let { c ->
            val clean = c.uppercase()
            if (clean.endsWith("*")) {
                clauses.add("UPPER(code) LIKE ?")
                args.add("${clean.dropLast(1)}%")
            } else {
                clauses.add("(UPPER(code) = ? OR UPPER(code) LIKE ?)")
                args.add(clean)
                args.add("${clean}_%")
            }
        }
        channel?.trim()?.takeIf { it.isNotEmpty() }?.let { ch ->
            clauses.add("(channel_id = ? OR LOWER(channel_name) LIKE ?)")
            args.add(ch)
            args.add("%${ch.lowercase()}%")
        }
        device?.trim()?.takeIf { it.isNotEmpty() }?.let { dev ->
            clauses.add("(device_id = ? OR LOWER(device_name) LIKE ?)")
            args.add(dev)
            args.add("%${dev.lowercase()}%")
        }
        severity?.trim()?.takeIf { it.isNotEmpty() }?.let { sev ->
            clauses.add("LOWER(severity) = ?")
            args.add(sev.lowercase())
        }
        source?.trim()?.takeIf { it.isNotEmpty() }?.let { src ->
            clauses.add("LOWER(source) = ?")
            args.add(src.lowercase())
        }
        val safeLimit = limit.coerceIn(1, 1000)
        val safeOffset = offset.coerceAtLeast(0)
        args.add(safeLimit.toString())
        args.add(safeOffset.toString())
        val sql = """
            SELECT id, ts_ms, source, severity, code, device_id, device_name, app_version,
                   client_ip, channel_id, channel_name, playback_mode, relay_key, stream_url,
                   message, details_json
            FROM diagnostic_logs
            WHERE ${clauses.joinToString(" AND ")}
            ORDER BY ts_ms DESC, id DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return db.rawQuery(sql, args.toTypedArray()).use { c ->
            c.mapAll { cur ->
                DiagnosticLogRow(
                    id = cur.getLong(0),
                    tsMs = cur.getLong(1),
                    source = cur.getString(2),
                    severity = cur.getString(3),
                    code = cur.getString(4),
                    deviceId = cur.getString(5),
                    deviceName = cur.getString(6),
                    appVersion = cur.getString(7),
                    clientIp = cur.getString(8),
                    channelId = cur.getString(9),
                    channelName = cur.getString(10),
                    playbackMode = cur.getString(11),
                    relayKey = cur.getString(12),
                    streamUrl = cur.getString(13),
                    message = cur.getString(14),
                    detailsJson = cur.getString(15),
                )
            }
        }
    }

    fun diagnosticSummary(sinceMs: Long): DiagnosticSummary {
        val db = readableDatabase
        val sinceStr = sinceMs.toString()
        var total = 0
        var oldest: Long? = null
        var newest: Long? = null
        db.rawQuery(
            "SELECT COUNT(*), MIN(ts_ms), MAX(ts_ms) FROM diagnostic_logs WHERE ts_ms >= ?",
            arrayOf(sinceStr),
        ).use { c ->
            if (c.moveToFirst()) {
                total = c.getInt(0)
                if (!c.isNull(1)) oldest = c.getLong(1)
                if (!c.isNull(2)) newest = c.getLong(2)
            }
        }
        val bySeverity = LinkedHashMap<String, Int>()
        db.rawQuery(
            "SELECT severity, COUNT(*) FROM diagnostic_logs WHERE ts_ms >= ? GROUP BY severity",
            arrayOf(sinceStr),
        ).use { c ->
            while (c.moveToNext()) {
                bySeverity[c.getString(0)] = c.getInt(1)
            }
        }
        val topCodes = db.rawQuery(
            """
            SELECT code, COUNT(*) AS cnt, MAX(ts_ms) AS last_ts
            FROM diagnostic_logs
            WHERE ts_ms >= ?
            GROUP BY code
            ORDER BY cnt DESC, last_ts DESC
            LIMIT 20
            """.trimIndent(),
            arrayOf(sinceStr),
        ).use { c ->
            c.mapAll { cur ->
                DiagnosticCodeStat(
                    code = cur.getString(0),
                    count = cur.getInt(1),
                    lastTsMs = cur.getLong(2),
                )
            }
        }
        val rawChannels = db.rawQuery(
            """
            SELECT channel_id, MAX(channel_name) AS ch_name,
                   SUM(CASE WHEN severity IN ('error','fatal') THEN 1 ELSE 0 END) AS err_cnt,
                   SUM(CASE WHEN severity = 'warn' THEN 1 ELSE 0 END) AS warn_cnt,
                   COUNT(*) AS tot_cnt,
                   MAX(ts_ms) AS last_ts
            FROM diagnostic_logs
            WHERE ts_ms >= ? AND channel_id != ''
            GROUP BY channel_id
            ORDER BY err_cnt DESC, tot_cnt DESC, last_ts DESC
            LIMIT 20
            """.trimIndent(),
            arrayOf(sinceStr),
        ).use { c ->
            c.mapAll { cur ->
                DiagnosticChannelStat(
                    channelId = cur.getString(0),
                    channelName = cur.getString(1),
                    errorCount = cur.getInt(2),
                    warnCount = cur.getInt(3),
                    totalCount = cur.getInt(4),
                    topCode = "",
                    lastTsMs = cur.getLong(5),
                )
            }
        }
        val topChannels = rawChannels.map { item ->
            val bestCode = db.rawQuery(
                """
                SELECT code, COUNT(*) AS cnt
                FROM diagnostic_logs
                WHERE ts_ms >= ? AND channel_id = ?
                GROUP BY code
                ORDER BY cnt DESC
                LIMIT 1
                """.trimIndent(),
                arrayOf(sinceStr, item.channelId),
            ).use { c -> if (c.moveToFirst()) c.getString(0) else "" }
            item.copy(topCode = bestCode)
        }
        val byDevice = db.rawQuery(
            """
            SELECT device_id, MAX(device_name) AS dev_name, MAX(app_version) AS app_ver,
                   SUM(CASE WHEN severity IN ('error','fatal') THEN 1 ELSE 0 END) AS err_cnt,
                   SUM(CASE WHEN severity = 'warn' THEN 1 ELSE 0 END) AS warn_cnt,
                   COUNT(*) AS tot_cnt,
                   MAX(ts_ms) AS last_ts
            FROM diagnostic_logs
            WHERE ts_ms >= ?
            GROUP BY device_id
            ORDER BY tot_cnt DESC, last_ts DESC
            LIMIT 20
            """.trimIndent(),
            arrayOf(sinceStr),
        ).use { c ->
            c.mapAll { cur ->
                DiagnosticDeviceStat(
                    deviceId = cur.getString(0),
                    deviceName = cur.getString(1),
                    appVersion = cur.getString(2),
                    errorCount = cur.getInt(3),
                    warnCount = cur.getInt(4),
                    totalCount = cur.getInt(5),
                    lastTsMs = cur.getLong(6),
                )
            }
        }
        return DiagnosticSummary(
            retentionDays = DIAGNOSTIC_RETENTION_DAYS,
            totalCount = total,
            oldestTsMs = oldest,
            newestTsMs = newest,
            bySeverity = bySeverity,
            topCodes = topCodes,
            topChannels = topChannels,
            byDevice = byDevice,
        )
    }

    companion object {
        val VALID_ISO_SET = Locale.getISOCountries().toHashSet()
        private const val LIVE_CANON_VERSION = "v1"
        private val LIVE_LEADING_TAG_RE = Regex("""^(?:\[([^\]]{2,10})]|\(([^)]{2,10})\)|([A-Za-z0-9]{2,8}(?:-[A-Za-z0-9]{2,4})?))\s*[:|/]\s*""")
        private val LIVE_BRACKET_RE = Regex("""\([^)]*\)|\[[^\]]*]""")
        private val LIVE_SYMBOL_RE = Regex("""[✦★☆◈◆●•]+""")
        private val LIVE_QUALITY_TOKEN_RE = Regex("""\b(?:uhd|4k|fhd|hd|sd|hevc|h265|h264|vip|raw|backup|bk|50fps|60fps|aac|mp4|opt|opc|alt)\b""", RegexOption.IGNORE_CASE)
        private val LIVE_PUNCT_RE = Regex("""[|_•·/]+""")
        private val LIVE_SPACE_RE = Regex("""\s+""")
        private val SEARCH_TOKEN_REGEX = Regex("[\\p{L}\\p{N}]+")
        private const val DB_NAME = "casatv-server.db"
        private const val DB_VERSION = 2
        private const val CLIENT_TTL_SECONDS = 90
        const val DIAGNOSTIC_RETENTION_DAYS = 14
        private const val DIAGNOSTIC_MAX_ROWS = 50_000
        private const val ENABLED_CHANNEL_WHERE = """
            (EXISTS (
                SELECT 1 FROM playlist_channels pc
                JOIN playlists p ON pc.playlist_id = p.id
                WHERE pc.channel_id = c.id AND p.enabled = 1
            ) OR (
                NOT EXISTS (SELECT 1 FROM playlist_channels pc2 WHERE pc2.channel_id = c.id)
                AND NOT EXISTS (SELECT 1 FROM playlists p2 WHERE p2.id = c.playlist_id AND p2.enabled = 0)
            ))
        """

        @Volatile
        private var instance: ServerDatabase? = null

        fun get(context: Context): ServerDatabase =
            instance ?: synchronized(this) {
                instance ?: ServerDatabase(context).also { instance = it }
            }
    }
}

private fun <T> Cursor.mapAll(mapper: (Cursor) -> T): List<T> {
    val out = ArrayList<T>()
    while (moveToNext()) out += mapper(this)
    return out
}
