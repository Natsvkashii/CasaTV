package it.casatv.tv.data

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDto(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val sourceType: String = "",
    val enabled: Boolean = true,
    val channelCount: Int = 0,
    val status: String = "ok",
    val lastError: String = "",
    val lastImport: Long = 0L,
)

@Serializable
data class PlaylistsResponse(
    val ok: Boolean = false,
    val playlists: List<PlaylistDto> = emptyList(),
)

@Serializable
data class PlaylistActionResponse(
    val ok: Boolean = false,
    val playlist: PlaylistDto? = null,
)

data class LocalM3uStatus(
    val label: String = "",
    val count: Int = 0,
    val error: String = "",
)

@Serializable
data class Health(
    val ok: Boolean = false,
    val name: String = "CasaTV",
    val version: String = "",
    val channels: Int = 0,
    val streams: Int = 0,
    val epg: Boolean = false,
)

@Serializable
data class ServerUrl(
    val name: String = "",
    val address: String = "",
    val url: String = "",
)

@Serializable
data class ServerStats(
    val channels: Int = 0,
    val allChannels: Int = 0,
    val streams: Int = 0,
    val onlineStreams: Int = 0,
    val favorites: Int = 0,
    val hidden: Int = 0,
    val epgPrograms: Int = 0,
    val countries: Int = 0,
)

@androidx.compose.runtime.Immutable
@Serializable
data class Facet(
    val code: String = "",
    val id: String = "",
    val name: String = "",
    val flag: String = "",
    val count: Int = 0,
)

@androidx.compose.runtime.Immutable
@Serializable
data class Facets(
    val countries: List<Facet> = emptyList(),
    val categories: List<Facet> = emptyList(),
    val languages: List<Facet> = emptyList(),
)

@Serializable
data class EpgSummary(
    val sources: Int = 0,
    val programs: Int = 0,
    val matchedChannels: Int = 0,
    val active: Boolean = false,
)

@Serializable
data class CatalogSummary(
    val source: String = "",
    val defaultSource: String = "",
    val lastIngest: Long? = null,
)

@Serializable
data class ServerConfig(
    val name: String = "CasaTV",
    val version: String = "",
    val serverTime: Long = 0,
    val baseUrl: String = "",
    val urls: List<ServerUrl> = emptyList(),
    val stats: ServerStats = ServerStats(),
    val facets: Facets = Facets(),
    val defaultCountry: String = "IT",
    val activeProfile: Profile? = null,
    val epg: EpgSummary = EpgSummary(),
    val catalog: CatalogSummary = CatalogSummary(),
)

@Serializable
data class Profile(
    val id: String = "",
    val name: String = "",
    val avatar: String = "🦊",
    val language: String = "it",
    val languageName: String = "",
    val country: String = "IT",
    val countryName: String = "",
    val flag: String = "",
    val isGuest: Boolean = false,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
) {
    val displayLanguage: String
        get() = languageName.ifBlank {
            when (language) {
                "en" -> "English"
                "es" -> "Español"
                else -> "Italiano"
            }
        }
}

@Serializable
data class ProfileLanguage(
    val code: String = "it",
    val name: String = "Italiano",
)

@Serializable
data class ProfilesResponse(
    val profiles: List<Profile> = emptyList(),
    val avatars: List<String> = emptyList(),
    val languages: List<ProfileLanguage> = emptyList(),
)

@androidx.compose.runtime.Immutable
@Serializable
data class StreamSource(
    val id: String = "",
    val quality: String = "SD",
    val feed: String? = null,
    val status: String = "unknown",
    val url: String = "",
)

@Serializable
data class ChannelSources(
    val channel: String = "",
    val sources: List<StreamSource> = emptyList(),
)

@androidx.compose.runtime.Immutable
@Serializable
data class NowProgram(
    val title: String = "",
    val start: Long = 0,
    val stop: Long = 0,
)

@androidx.compose.runtime.Immutable
@Serializable
data class NextProgram(
    val title: String = "",
    val start: Long = 0,
)

@androidx.compose.runtime.Immutable
@Serializable
data class Channel(
    val id: String,
    val number: Int? = null,
    val name: String = "",
    val country: String? = null,
    val countryName: String = "",
    val flag: String = "",
    val categories: List<String> = emptyList(),
    val categoryNames: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val languageNames: List<String> = emptyList(),
    val quality: String = "SD",
    val logo: String? = null,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val enabled: Boolean = true,
    val status: String = "unknown",
    val streamsCount: Int = 0,
    val onlineStreams: Int = 0,
    val isNsfw: Boolean = false,
    val streamUrl: String = "",
    val epgId: String? = null,
    val lastPlayedAt: Long? = null,
    val now: NowProgram? = null,
    val next: NextProgram? = null,
) {
    val subtitle: String
        get() {
            val category = categoryNames.firstOrNull()
                ?: categories.firstOrNull()?.replaceFirstChar { it.uppercase() }
                ?: "Canale"
            return listOfNotNull(category, quality.takeIf { it.isNotBlank() }).joinToString(" · ")
        }

    val displayNumber: String
        get() = number?.toString() ?: "—"
}

@androidx.compose.runtime.Immutable
@Serializable
data class HomeRow(
    val id: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val type: String = "channels",
    val total: Int = 0,
    val channels: List<Channel> = emptyList(),
)

@androidx.compose.runtime.Immutable
@Serializable
data class HomeResponse(
    val rows: List<HomeRow> = emptyList(),
)

@androidx.compose.runtime.Immutable
@Serializable
data class ChannelPage(
    val items: List<Channel> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val hasMore: Boolean = false,
)

@androidx.compose.runtime.Immutable
@Serializable
data class EpgProgram(
    val title: String = "",
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val start: Long = 0,
    val stop: Long = 0,
    val icon: String? = null,
)

@androidx.compose.runtime.Immutable
@Serializable
data class ChannelEpg(
    val channelId: String = "",
    val now: EpgProgram? = null,
    val next: EpgProgram? = null,
    val upcoming: List<EpgProgram> = emptyList(),
)

@androidx.compose.runtime.Immutable
@Serializable
data class EpgGuideChannel(
    val id: String,
    val name: String = "",
    val number: Int? = null,
    val logo: String? = null,
    val country: String? = null,
)

@androidx.compose.runtime.Immutable
@Serializable
data class EpgGuideProgram(
    val channelId: String = "",
    val start: Long = 0,
    val stop: Long = 0,
    val title: String = "",
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
)

@Serializable
data class EpgGuideResponse(
    val from: Long = 0,
    val to: Long = 0,
    val channels: List<EpgGuideChannel> = emptyList(),
    val programs: List<EpgGuideProgram> = emptyList(),
)

@Serializable
data class CpuUsage(
    val cores: Int = 1,
    val load1: Double = 0.0,
    val percent: Double = 0.0,
)

@Serializable
data class MemoryUsage(
    val total: Long = 0,
    val free: Long = 0,
    val used: Long = 0,
    val percent: Double = 0.0,
)

@Serializable
data class ProcessUsage(
    val rss: Long = 0,
    val heapUsed: Long = 0,
    val heapTotal: Long = 0,
)

@Serializable
data class ResourceUsage(
    val cpu: CpuUsage = CpuUsage(),
    val memory: MemoryUsage = MemoryUsage(),
    val disk: MemoryUsage = MemoryUsage(),
    val process: ProcessUsage = ProcessUsage(),
)

@Serializable
data class SystemAlert(
    val level: String = "warning",
    val kind: String = "",
    val title: String = "",
    val message: String = "",
    val value: Double = 0.0,
)

@Serializable
data class ConnectedDevice(
    val id: String = "",
    val name: String = "",
    val platform: String = "",
    val appVersion: String = "",
    val profileId: String = "",
    val profileName: String = "",
    val state: String = "idle",
    val channel: String = "",
    val ip: String = "",
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
)

@Serializable
data class LastUpdate(
    val catalog: Long? = null,
    val epg: Long? = null,
    val ingest: Long? = null,
)

@Serializable
data class UpdateInfo(
    val latest: String = "",
    val current: String? = null,
    val updateAvailable: Boolean = false,
    val downloadUrl: String? = null,
    val changelog: String? = null,
    val minVersion: String? = null,
)

@Serializable
data class SystemStatus(
    val ok: Boolean = false,
    val name: String = "CasaTV",
    val version: String = "",
    val serverTime: Long = 0,
    val startedAt: Long = 0,
    val uptimeSeconds: Long = 0,
    val health: String = "ok",
    val alerts: List<SystemAlert> = emptyList(),
    val resources: ResourceUsage = ResourceUsage(),
    val lastUpdate: LastUpdate = LastUpdate(),
    val stats: ServerStats = ServerStats(),
    val devices: List<ConnectedDevice> = emptyList(),
    val deviceCount: Int = 0,
    val update: UpdateInfo = UpdateInfo(),
)

@Serializable
data class ApiError(
    val error: String? = null,
)

sealed interface GlobalSearchResult {
    data class ChannelResult(
        val channel: Channel,
    ) : GlobalSearchResult
}

data class BrowseFilter(
    val country: String? = null,
    val category: String? = null,
    val language: String? = null,
    val query: String = "",
)

@Serializable
data class SyncPeer(
    val id: String = "",
    val name: String = "",
    val isLeader: Boolean = false,
)

@Serializable
data class SyncStateResponse(
    val ok: Boolean = false,
    val serverTimeMs: Long = 0L,
    val syncActive: Boolean = false,
    val isLeader: Boolean = false,
    val peerCount: Int = 1,
    val leaderId: String = "",
    val leaderName: String = "",
    val targetLiveOffsetMs: Long = 0L,
    val targetPositionMs: Long = 0L,
    val targetSegmentSeq: Long = 0L,
    val targetSegmentOffsetMs: Long = 0L,
    val targetSegmentDurationMs: Long = 0L,
    val peers: List<SyncPeer> = emptyList(),
)
