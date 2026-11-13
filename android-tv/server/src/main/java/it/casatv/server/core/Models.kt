package it.casatv.server.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileRow(
    val id: String,
    val name: String,
    val avatar: String,
    val language: String,
    val country: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A channel imported from an M3U playlist. Streamed directly to the TV. */
data class IptvChannel(
    val id: String,
    val name: String,
    val url: String,
    val logo: String? = null,
    val category: String = M3uParser.GENERAL_CATEGORY,
    val language: String = "",
    val country: String = "",
    val tvgId: String = "",
    val quality: String = "",
    val number: Int = 0,
    val addedAt: Long = 0,
    val playlistId: String = "default",
)

data class PlaylistEntry(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val channelCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis() / 1000,
    val updatedAt: Long = System.currentTimeMillis() / 1000,
)

data class PlaylistStatus(
    val count: Int = 0,
    val source: String = "",
    val url: String = "",
    val lastImport: Long = 0,
    val categories: List<Pair<String, Int>> = emptyList(),
)

data class PlaylistImportResult(
    val count: Int,
    val source: String,
    val skipped: Int = 0,
)

data class ClientRow(
    val id: String,
    val name: String,
    val platform: String,
    val appVersion: String,
    val profileId: String,
    val state: String,
    val channel: String,
    val ip: String,
    val firstSeen: Long,
    val lastSeen: Long,
)

data class ServerStatus(
    val running: Boolean = false,
    val port: Int = Prefs.DEFAULT_PORT,
    val ip: String? = null,
    val startedAt: Long = 0,
    val error: String? = null,
) {
    val baseUrl: String
        get() = NetworkUtils.baseUrl(ip, port)
}

/** Process-wide bridge between the foreground service and the UI. */
object ServerRuntime {
    private val _status = MutableStateFlow(ServerStatus())
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    fun update(transform: (ServerStatus) -> ServerStatus) {
        _status.value = transform(_status.value)
    }

    fun setRunning(port: Int, ip: String?) {
        _status.value = ServerStatus(running = true, port = port, ip = ip, startedAt = System.currentTimeMillis())
    }

    fun setStopped(error: String? = null) {
        _status.value = ServerStatus(running = false, error = error)
    }
}
