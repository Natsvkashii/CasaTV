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
    val movies: Int = 0,
    val series: Int = 0,
    val vodTotal: Int = 0,
    val totalItems: Int = 0,
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
    val timeshiftMaxHours: Int = 2,
    val serverRelayEnabled: Boolean = true,
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
    val avatar: String = "blue",
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
    val channelId: String = "",
    val name: String = "",
    val quality: String = "SD",
    val host: String = "",
    val protocol: String = "",
    val feed: String? = null,
    val status: String = "unknown",
    val latencyMs: Long? = null,
    val recommended: Boolean = false,
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
    val contentType: String = "live",
    val isVod: Boolean = false,
    val vodStreamUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val upNext: Boolean = false,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val epgId: String? = null,
    val lastPlayedAt: Long? = null,
    val now: NowProgram? = null,
    val next: NextProgram? = null,
) {
    val isVodContent: Boolean
        get() = isVod || contentType == "movie" || contentType == "series"

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
    val syncGroupKey: String = "",
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
    val leaderUpdatedAgoMs: Long = 0L,
    val peers: List<SyncPeer> = emptyList(),
)

@Serializable
data class TimeshiftSessionDto(
    val id: String = "",
    val deviceId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val status: String = "",
    val startedAtMs: Long = 0L,
    val recordedMs: Long = 0L,
    val maxDurationMs: Long = 0L,
    val maxHours: Int = 2,
    val bytes: Long = 0L,
    val segmentCount: Int = 0,
    val hasAudioTrack: Boolean = false,
    val error: String = "",
    val playlistUrl: String = "",
)

@Serializable
data class TimeshiftSessionResponse(
    val ok: Boolean = false,
    val session: TimeshiftSessionDto? = null,
)

@Serializable
data class TimeshiftPendingResponse(
    val ok: Boolean = false,
    val hasPending: Boolean = false,
    val session: TimeshiftSessionDto? = null,
)

@Serializable
data class VodLanguageFacet(
    val code: String = "",
    val name: String = "",
    val flag: String = "",
    val count: Int = 0,
)

@Serializable
data class VodCategoriesResponse(
    val languages: List<VodLanguageFacet> = emptyList(),
    val genres: List<Facet> = emptyList(),
    val platforms: List<Facet> = emptyList(),
    val categories: List<Facet> = emptyList(),
)

@Serializable
data class MixReadyResponse(
    val ok: Boolean = false,
    val poolSize: Int = 0,
    val prewarmedId: String? = null,
    val items: List<Channel> = emptyList(),
)

@Serializable
data class VodRelatedResponse(
    val mode: String = "similar",
    val items: List<Channel> = emptyList(),
)

@Serializable
data class SeriesSeasonDto(
    val season: Int = 1,
    val episodes: List<Channel> = emptyList(),
)

@Serializable
data class SeriesDetailResponse(
    val showTitle: String = "",
    val logo: String? = null,
    val country: String? = null,
    val countryName: String = "",
    val flag: String = "",
    val category: String = "",
    val totalSeasons: Int = 0,
    val totalEpisodes: Int = 0,
    val resumeEpisodeId: String? = null,
    val seasons: List<SeriesSeasonDto> = emptyList(),
)

@Serializable
data class LiveRelayTuneResponse(
    val ok: Boolean = false,
    val mode: String = "direct",
    val relayKey: String = "",
    val playlistUrl: String = "",
    val url: String = "",
    val viewers: Int = 0,
    val hasAudioTrack: Boolean = false,
    val reason: String = "",
)

@Serializable
data class RecordingDto(
    val id: String = "",
    val deviceId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val country: String = "",
    val logo: String? = null,
    val status: String = "",
    val error: String = "",
    val startedAtMs: Long = 0L,
    val endedAtMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    val remainingMs: Long = 0L,
    val recordedMs: Long = 0L,
    val bytes: Long = 0L,
    val segmentCount: Int = 0,
    val playUrl: String = "",
    val downloadUrl: String = "",
)

@Serializable
data class RecordingsListResponse(
    val ok: Boolean = false,
    val recordings: List<RecordingDto> = emptyList(),
)

@Serializable
data class RecordingSingleResponse(
    val ok: Boolean = false,
    val recording: RecordingDto? = null,
)
