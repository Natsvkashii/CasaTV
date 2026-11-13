package it.casatv.server.core

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/**
 * Single SQLite database of the Android server. Profiles live here (not on the
 * TV), so the same profile can be used by every TV that connects.
 */
class ServerDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    private val lock = Any()

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        runCatching { db.enableWriteAheadLogging() }
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
                added_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_number ON channels(number)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_category ON channels(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_channels_country ON channels(country)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createChannelsTable(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        runCatching {
            db.execSQL("ALTER TABLE profile_library ADD COLUMN watch_count INTEGER NOT NULL DEFAULT 0")
        }
        runCatching {
            db.execSQL("ALTER TABLE library ADD COLUMN saga TEXT NOT NULL DEFAULT ''")
        }
        runCatching {
            db.execSQL("ALTER TABLE library ADD COLUMN collection TEXT NOT NULL DEFAULT ''")
        }
        runCatching {
            db.execSQL("ALTER TABLE replay_segments ADD COLUMN is_discontinuity INTEGER NOT NULL DEFAULT 0")
        }
        runCatching {
            db.execSQL("ALTER TABLE replay_segments ADD COLUMN is_optimized INTEGER NOT NULL DEFAULT 0")
        }
        runCatching {
            db.execSQL("ALTER TABLE replay_segments ADD COLUMN track TEXT NOT NULL DEFAULT 'mux'")
        }
        runCatching {
            db.execSQL("ALTER TABLE channels ADD COLUMN playlist_id TEXT NOT NULL DEFAULT 'default'")
        }
        createPlaylistsTable(db)
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
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    /* ------------------------------------------------------------------ */
    /* Profiles                                                            */
    /* ------------------------------------------------------------------ */

    fun listProfiles(): List<ProfileRow> = synchronized(lock) {
        readableDatabase.rawQuery(
            "SELECT * FROM profiles ORDER BY CASE WHEN id = 'guest' THEN 1 ELSE 0 END ASC, created_at ASC, name COLLATE NOCASE ASC",
            null,
        ).use { cursor -> cursor.mapAll(::readProfile) }
    }

    fun getProfile(id: String?): ProfileRow? = synchronized(lock) {
        if (id.isNullOrBlank()) return null
        readableDatabase.rawQuery(
            "SELECT * FROM profiles WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) readProfile(cursor) else null }
    }

    fun defaultProfile(language: String = "it"): ProfileRow = synchronized(lock) {
        listProfiles().firstOrNull() ?: createProfile(
            name = if (language.take(2) == "en") "Main" else if (language.take(2) == "es") "Principal" else "Principale",
            avatar = PROFILE_AVATARS.first(),
            language = language.take(2),
            country = "IT",
        )
    }

    fun createProfile(name: String, avatar: String, language: String, country: String): ProfileRow =
        synchronized(lock) {
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
            getProfile(id)!!
        }

    fun updateProfile(
        id: String,
        name: String,
        avatar: String,
        language: String,
        country: String,
    ): ProfileRow? = synchronized(lock) {
        val existing = getProfile(id) ?: return null
        val values = ContentValues().apply {
            put("name", name.trim().ifBlank { existing.name }.take(28))
            put("avatar", avatar.ifBlank { existing.avatar })
            put("language", normalizeLanguage(language))
            put("country", normalizeCountry(country))
            put("updated_at", System.currentTimeMillis() / 1000)
        }
        writableDatabase.update("profiles", values, "id = ?", arrayOf(id))
        getProfile(id)
    }

    fun deleteProfile(id: String): Boolean = synchronized(lock) {
        if (id == "guest") return false
        val count = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM profiles WHERE id <> 'guest'",
            null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        if (count <= 1) return false
        val removed = writableDatabase.delete("profiles", "id = ?", arrayOf(id)) > 0
        writableDatabase.delete("profile_library", "profile_id = ?", arrayOf(id))
        removed
    }

    fun ensureGuestProfile() = synchronized(lock) {
        val exists = readableDatabase.rawQuery(
            "SELECT id FROM profiles WHERE id = 'guest' LIMIT 1",
            null,
        ).use { it.moveToFirst() }
        if (!exists) {
            val now = System.currentTimeMillis() / 1000
            writableDatabase.execSQL(
                """
                INSERT INTO profiles(id, name, avatar, language, country, created_at, updated_at)
                VALUES ('guest', 'Ospite', '👤', 'it', 'IT', ?, ?)
                """.trimIndent(),
                arrayOf(now, now),
            )
        }
    }

    fun clearGuestProfile() = synchronized(lock) {
        writableDatabase.delete("profile_library", "profile_id = ?", arrayOf("guest"))
    }

    fun setFavorite(profileId: String, itemId: String, favorite: Boolean) {
        synchronized(lock) {
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

    fun favoriteIds(profileId: String): Set<String> = synchronized(lock) {
        readableDatabase.rawQuery(
            "SELECT item_id FROM profile_library WHERE profile_id = ? AND favorite = 1",
            arrayOf(profileId),
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    fun markPlayed(profileId: String, itemId: String) {
        synchronized(lock) {
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

    fun mostWatchedIds(profileId: String, limit: Int = 16): List<String> = synchronized(lock) {
        readableDatabase.rawQuery(
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

    fun recentIds(profileId: String, limit: Int = 20): List<String> = synchronized(lock) {
        readableDatabase.rawQuery(
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

    fun listChannels(): List<IptvChannel> = synchronized(lock) {
        readableDatabase.rawQuery(
            """
            SELECT c.* FROM channels c
            LEFT JOIN playlists p ON c.playlist_id = p.id
            WHERE p.enabled IS NULL OR p.enabled = 1
            ORDER BY c.number ASC
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.mapAll(::readChannel) }
    }

    fun channelCount(): Int = synchronized(lock) {
        readableDatabase.rawQuery(
            """
            SELECT COUNT(*) FROM channels c
            LEFT JOIN playlists p ON c.playlist_id = p.id
            WHERE p.enabled IS NULL OR p.enabled = 1
            """.trimIndent(),
            null,
        ).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun getChannel(id: String?): IptvChannel? = synchronized(lock) {
        if (id.isNullOrBlank()) return null
        readableDatabase.rawQuery(
            "SELECT * FROM channels WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) readChannel(cursor) else null }
    }

    fun getChannelByNumber(number: Int): IptvChannel? = synchronized(lock) {
        if (number <= 0) return null
        readableDatabase.rawQuery(
            "SELECT * FROM channels WHERE number = ? LIMIT 1",
            arrayOf(number.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) readChannel(cursor) else null }
    }

    fun listAlternativeChannels(channel: IptvChannel, limit: Int = 8): List<IptvChannel> = synchronized(lock) {
        val rows = if (channel.tvgId.isNotBlank()) {
            readableDatabase.rawQuery(
                """
                SELECT * FROM channels
                WHERE tvg_id = ? OR LOWER(name) = LOWER(?)
                ORDER BY CASE WHEN id = ? THEN 0 ELSE 1 END, number ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(channel.tvgId, channel.name, channel.id, limit.toString()),
            ).use { cursor -> cursor.mapAll(::readChannel) }
        } else {
            readableDatabase.rawQuery(
                """
                SELECT * FROM channels
                WHERE LOWER(name) = LOWER(?)
                ORDER BY CASE WHEN id = ? THEN 0 ELSE 1 END, number ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(channel.name, channel.id, limit.toString()),
            ).use { cursor -> cursor.mapAll(::readChannel) }
        }
        rows.ifEmpty { listOf(channel) }
    }

    fun channelCountryCount(): Int = synchronized(lock) {
        readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT country) FROM channels WHERE country != ''",
            null,
        ).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun channelCountsByCountry(): List<Pair<String, Int>> = synchronized(lock) {
        readableDatabase.rawQuery(
            "SELECT country, COUNT(*) FROM channels WHERE country != '' GROUP BY country ORDER BY COUNT(*) DESC",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1))
            }
        }
    }

    fun channelCountsByLanguage(): List<Pair<String, Int>> = synchronized(lock) {
        readableDatabase.rawQuery(
            "SELECT language, COUNT(*) FROM channels WHERE language != '' GROUP BY language ORDER BY COUNT(*) DESC LIMIT 80",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1))
            }
        }
    }

    /** Replaces the whole playlist: channels removed upstream disappear here too. */
    fun replaceChannels(channels: List<IptvChannel>) {
        synchronized(lock) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("channels", null, null)
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
            }
        }
    }

    fun clearChannels() {
        synchronized(lock) {
            writableDatabase.delete("channels", null, null)
        }
    }

    fun channelCountsByCategory(): List<Pair<String, Int>> = synchronized(lock) {
        readableDatabase.rawQuery(
            "SELECT category, COUNT(*) FROM channels GROUP BY category ORDER BY COUNT(*) DESC",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1))
            }
        }
    }

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
        synchronized(lock) {
            val now = System.currentTimeMillis() / 1000
            val existing = readableDatabase.rawQuery(
                "SELECT first_seen FROM clients WHERE id = ?",
                arrayOf(id),
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
            val values = ContentValues().apply {
                put("id", id)
                put("name", name)
                put("platform", platform)
                put("app_version", appVersion)
                put("profile_id", profileId)
                put("state", state)
                put("channel", channel)
                put("ip", ip)
                put("first_seen", existing ?: now)
                put("last_seen", now)
            }
            writableDatabase.insertWithOnConflict(
                "clients",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            // Entries older than the TTL are not "connected" any more.
            writableDatabase.delete("clients", "last_seen < ?", arrayOf((now - CLIENT_TTL_SECONDS).toString()))
        }
    }

    fun listClients(): List<ClientRow> = synchronized(lock) {
        val now = System.currentTimeMillis() / 1000
        readableDatabase.rawQuery(
            "SELECT * FROM clients WHERE last_seen >= ? ORDER BY last_seen DESC",
            arrayOf((now - CLIENT_TTL_SECONDS).toString()),
        ).use { cursor -> cursor.mapAll(::readClient) }
    }

    fun removeClient(id: String) {
        synchronized(lock) {
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
        return IptvChannel(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
            logo = cursor.getString(cursor.getColumnIndexOrThrow("logo")),
            category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
            language = cursor.getString(cursor.getColumnIndexOrThrow("language")),
            country = cursor.getString(cursor.getColumnIndexOrThrow("country")),
            tvgId = cursor.getString(cursor.getColumnIndexOrThrow("tvg_id")),
            quality = cursor.getString(cursor.getColumnIndexOrThrow("quality")),
            number = cursor.getInt(cursor.getColumnIndexOrThrow("number")),
            addedAt = cursor.getLong(cursor.getColumnIndexOrThrow("added_at")),
            playlistId = playlistId,
        )
    }

    fun listPlaylists(): List<PlaylistEntry> = synchronized(lock) {
        readableDatabase.rawQuery("SELECT * FROM playlists ORDER BY added_at ASC", null).use { cursor ->
            cursor.mapAll(::readPlaylist)
        }
    }

    private fun readPlaylist(cursor: Cursor): PlaylistEntry = PlaylistEntry(
        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
        url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
        enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1,
        channelCount = cursor.getInt(cursor.getColumnIndexOrThrow("channel_count")),
        addedAt = cursor.getLong(cursor.getColumnIndexOrThrow("added_at")),
        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
    )

    fun getPlaylist(id: String): PlaylistEntry? = synchronized(lock) {
        readableDatabase.rawQuery("SELECT * FROM playlists WHERE id = ? LIMIT 1", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) readPlaylist(cursor) else null
        }
    }

    fun savePlaylistRecord(playlist: PlaylistEntry) = synchronized(lock) {
        val cv = ContentValues().apply {
            put("id", playlist.id)
            put("name", playlist.name)
            put("url", playlist.url)
            put("enabled", if (playlist.enabled) 1 else 0)
            put("channel_count", playlist.channelCount)
            put("added_at", playlist.addedAt)
            put("updated_at", playlist.updatedAt)
        }
        writableDatabase.insertWithOnConflict("playlists", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun togglePlaylist(id: String, enabled: Boolean) = synchronized(lock) {
        val cv = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
            put("updated_at", System.currentTimeMillis() / 1000)
        }
        writableDatabase.update("playlists", cv, "id = ?", arrayOf(id))
    }

    fun deletePlaylist(id: String) = synchronized(lock) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("channels", "playlist_id = ?", arrayOf(id))
            db.delete("playlists", "id = ?", arrayOf(id))
            reindexChannels(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addChannelsForPlaylist(playlist: PlaylistEntry, channels: List<IptvChannel>) = synchronized(lock) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("channels", "playlist_id = ?", arrayOf(playlist.id))
            channels.forEach { channel ->
                val cv = ContentValues().apply {
                    put("id", channel.id)
                    put("name", channel.name)
                    put("url", channel.url)
                    put("logo", channel.logo)
                    put("category", channel.category)
                    put("language", channel.language)
                    put("country", channel.country)
                    put("tvg_id", channel.tvgId)
                    put("quality", channel.quality)
                    put("number", 0)
                    put("added_at", channel.addedAt)
                    put("playlist_id", playlist.id)
                }
                db.insertWithOnConflict("channels", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            savePlaylistRecord(playlist.copy(channelCount = channels.size, updatedAt = System.currentTimeMillis() / 1000))
            reindexChannels(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun reindexChannels(db: SQLiteDatabase) {
        val cursor = db.rawQuery("SELECT id FROM channels ORDER BY number ASC, added_at ASC", null)
        val ids = ArrayList<String>()
        while (cursor.moveToNext()) ids.add(cursor.getString(0))
        cursor.close()
        ids.forEachIndexed { index, id ->
            val cv = ContentValues().apply { put("number", index + 1) }
            db.update("channels", cv, "id = ?", arrayOf(id))
        }
    }

    companion object {
        private const val DB_NAME = "casatv-server.db"
        private const val DB_VERSION = 2
        private const val CLIENT_TTL_SECONDS = 90

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
