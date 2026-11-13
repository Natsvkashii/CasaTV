package it.casatv.mobile.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import it.casatv.mobile.data.LocalPlaylist
import it.casatv.mobile.data.MobileChannel

class LocalDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE playlists (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                source TEXT NOT NULL,
                is_local_file INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                channel_count INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE channels (
                id TEXT PRIMARY KEY,
                playlist_id TEXT NOT NULL,
                number INTEGER,
                name TEXT NOT NULL,
                stream_url TEXT NOT NULL,
                logo TEXT,
                country TEXT,
                country_name TEXT NOT NULL DEFAULT '',
                flag TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT 'Generale',
                language TEXT,
                quality TEXT NOT NULL DEFAULT 'SD',
                epg_id TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_channels_playlist ON channels(playlist_id)")
        db.execSQL("CREATE INDEX idx_channels_name ON channels(name)")

        db.execSQL(
            """
            CREATE TABLE favorites (
                stream_url TEXT PRIMARY KEY,
                added_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Initial version 1
    }

    fun getPlaylists(): List<LocalPlaylist> {
        val result = mutableListOf<LocalPlaylist>()
        readableDatabase.rawQuery(
            "SELECT id, name, source, is_local_file, enabled, channel_count, updated_at FROM playlists ORDER BY updated_at DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    LocalPlaylist(
                        id = c.getString(0),
                        name = c.getString(1),
                        source = c.getString(2),
                        isLocalFile = c.getInt(3) == 1,
                        enabled = c.getInt(4) == 1,
                        channelCount = c.getInt(5),
                        updatedAt = c.getLong(6),
                    ),
                )
            }
        }
        return result
    }

    fun upsertPlaylistAndChannels(playlist: LocalPlaylist, channels: List<MobileChannel>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val pv = ContentValues().apply {
                put("id", playlist.id)
                put("name", playlist.name)
                put("source", playlist.source)
                put("is_local_file", if (playlist.isLocalFile) 1 else 0)
                put("enabled", if (playlist.enabled) 1 else 0)
                put("channel_count", channels.size)
                put("updated_at", playlist.updatedAt)
            }
            db.insertWithOnConflict("playlists", null, pv, SQLiteDatabase.CONFLICT_REPLACE)

            db.delete("channels", "playlist_id = ?", arrayOf(playlist.id))

            val stmt = db.compileStatement(
                """
                INSERT OR REPLACE INTO channels
                (id, playlist_id, number, name, stream_url, logo, country, country_name, flag, category, language, quality, epg_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            )
            for (ch in channels) {
                stmt.clearBindings()
                stmt.bindString(1, ch.id)
                stmt.bindString(2, playlist.id)
                if (ch.number != null) stmt.bindLong(3, ch.number.toLong()) else stmt.bindNull(3)
                stmt.bindString(4, ch.name)
                stmt.bindString(5, ch.streamUrl)
                if (ch.logo != null) stmt.bindString(6, ch.logo) else stmt.bindNull(6)
                if (ch.country != null) stmt.bindString(7, ch.country) else stmt.bindNull(7)
                stmt.bindString(8, ch.countryName)
                stmt.bindString(9, ch.flag)
                stmt.bindString(10, ch.category)
                if (ch.language != null) stmt.bindString(11, ch.language) else stmt.bindNull(11)
                stmt.bindString(12, ch.quality)
                if (ch.epgId != null) stmt.bindString(13, ch.epgId) else stmt.bindNull(13)
                stmt.executeInsert()
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setPlaylistEnabled(playlistId: String, enabled: Boolean) {
        val cv = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        writableDatabase.update("playlists", cv, "id = ?", arrayOf(playlistId))
    }

    fun deletePlaylist(playlistId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("channels", "playlist_id = ?", arrayOf(playlistId))
            db.delete("playlists", "id = ?", arrayOf(playlistId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getFavoriteUrls(): Set<String> {
        val urls = HashSet<String>()
        readableDatabase.rawQuery("SELECT stream_url FROM favorites", null).use { c ->
            while (c.moveToNext()) {
                urls.add(c.getString(0))
            }
        }
        return urls
    }

    fun setFavorite(streamUrl: String, favorite: Boolean) {
        if (favorite) {
            val cv = ContentValues().apply {
                put("stream_url", streamUrl)
                put("added_at", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict("favorites", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            writableDatabase.delete("favorites", "stream_url = ?", arrayOf(streamUrl))
        }
    }

    fun getEnabledChannels(): List<MobileChannel> {
        val favs = getFavoriteUrls()
        val result = mutableListOf<MobileChannel>()
        readableDatabase.rawQuery(
            """
            SELECT c.id, c.playlist_id, c.number, c.name, c.stream_url, c.logo,
                   c.country, c.country_name, c.flag, c.category, c.language, c.quality, c.epg_id
            FROM channels c
            INNER JOIN playlists p ON p.id = c.playlist_id
            WHERE p.enabled = 1
            ORDER BY COALESCE(c.number, 999999) ASC, c.name ASC
            """.trimIndent(),
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val streamUrl = c.getString(4)
                result.add(
                    MobileChannel(
                        id = c.getString(0),
                        playlistId = c.getString(1),
                        number = if (c.isNull(2)) null else c.getInt(2),
                        name = c.getString(3),
                        streamUrl = streamUrl,
                        logo = c.getString(5),
                        country = c.getString(6),
                        countryName = c.getString(7),
                        flag = c.getString(8),
                        category = c.getString(9),
                        language = c.getString(10),
                        quality = c.getString(11),
                        epgId = c.getString(12),
                        favorite = favs.contains(streamUrl),
                        isFromServer = false,
                    ),
                )
            }
        }
        return result
    }

    companion object {
        private const val DB_NAME = "casatv_mobile.db"
        private const val DB_VERSION = 1
    }
}
