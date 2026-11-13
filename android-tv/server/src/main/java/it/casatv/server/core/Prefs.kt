package it.casatv.server.core

import android.content.Context
import android.content.SharedPreferences

/** Persisted server settings (language is null until the first-run gate). */
object Prefs {
    const val NAME = "casatv_server"
    const val KEY_LANGUAGE = "language"
    const val KEY_SERVER_NAME = "server_name"
    const val KEY_PORT = "port"
    const val KEY_AUTO_START = "auto_start"
    const val KEY_PLAYLIST_URL = "playlist_url"
    const val KEY_PLAYLIST_SOURCE = "playlist_source"
    const val KEY_PLAYLIST_LAST_IMPORT = "playlist_last_import"
    const val KEY_ADMIN_PIN = "admin_pin"

    const val DEFAULT_PORT = 8787
    const val DEFAULT_ADMIN_PIN = "0000"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun language(context: Context): String? =
        get(context).getString(KEY_LANGUAGE, null)?.takeIf { it.length >= 2 }

    fun activeLanguage(context: Context): String =
        language(context) ?: "it"

    fun setLanguage(context: Context, code: String) {
        get(context).edit().putString(KEY_LANGUAGE, code.take(2).lowercase()).apply()
    }

    fun serverName(context: Context): String =
        get(context).getString(KEY_SERVER_NAME, null)?.takeIf { it.isNotBlank() } ?: "CasaTV"

    fun setServerName(context: Context, name: String) {
        get(context).edit().putString(KEY_SERVER_NAME, name.take(60)).apply()
    }

    fun adminPin(context: Context): String =
        get(context).getString(KEY_ADMIN_PIN, DEFAULT_ADMIN_PIN)?.takeIf { it.isNotBlank() } ?: DEFAULT_ADMIN_PIN

    fun setAdminPin(context: Context, pin: String) {
        get(context).edit().putString(KEY_ADMIN_PIN, pin.take(4)).apply()
    }

    fun port(context: Context): Int {
        val value = get(context).getInt(KEY_PORT, DEFAULT_PORT)
        return if (value in 1024..65000) value else DEFAULT_PORT
    }

    fun setPort(context: Context, port: Int) {
        get(context).edit().putInt(KEY_PORT, port).apply()
    }

    fun autoStart(context: Context): Boolean = get(context).getBoolean(KEY_AUTO_START, true)

    fun setAutoStart(context: Context, autoStart: Boolean) {
        get(context).edit().putBoolean(KEY_AUTO_START, autoStart).apply()
    }

    fun playlistUrl(context: Context): String =
        get(context).getString(KEY_PLAYLIST_URL, "").orEmpty()

    fun playlistSource(context: Context): String =
        get(context).getString(KEY_PLAYLIST_SOURCE, "").orEmpty()

    fun playlistLastImport(context: Context): Long =
        get(context).getLong(KEY_PLAYLIST_LAST_IMPORT, 0L)

    fun savePlaylist(context: Context, url: String, source: String, importedAt: Long) {
        get(context).edit()
            .putString(KEY_PLAYLIST_URL, url)
            .putString(KEY_PLAYLIST_SOURCE, source)
            .putLong(KEY_PLAYLIST_LAST_IMPORT, importedAt)
            .apply()
    }

    fun clearPlaylist(context: Context) {
        get(context).edit()
            .remove(KEY_PLAYLIST_URL)
            .remove(KEY_PLAYLIST_SOURCE)
            .remove(KEY_PLAYLIST_LAST_IMPORT)
            .apply()
    }
}
