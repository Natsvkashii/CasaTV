package it.casatv.mobile.data

import kotlinx.serialization.Serializable

@Serializable
data class MobileChannel(
    val id: String,
    val number: Int? = null,
    val name: String,
    val streamUrl: String,
    val logo: String? = null,
    val country: String? = null,
    val countryName: String = "",
    val flag: String = "",
    val category: String = "Generale",
    val language: String? = null,
    val quality: String = "SD",
    val epgId: String? = null,
    val playlistId: String = "local",
    val favorite: Boolean = false,
    val isFromServer: Boolean = false,
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
) {
    val isVodContent: Boolean
        get() = isVod || contentType == "movie" || contentType == "series"

    val displayNumber: String
        get() = number?.toString() ?: "—"

    val subtitle: String
        get() = listOfNotNull(
            category.takeIf { it.isNotBlank() },
            quality.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
}

@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val source: String, // URL or file URI / label
    val isLocalFile: Boolean = false,
    val enabled: Boolean = true,
    val channelCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = "ok",
    val lastError: String = "",
)

@Serializable
data class CountryFacet(
    val code: String,
    val name: String,
    val flag: String,
    val count: Int,
)

@Serializable
data class ServerChannelDto(
    val id: String,
    val number: Int? = null,
    val name: String = "",
    val country: String? = null,
    val countryName: String = "",
    val flag: String = "",
    val categories: List<String> = emptyList(),
    val categoryNames: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val quality: String = "SD",
    val logo: String? = null,
    val favorite: Boolean = false,
    val streamUrl: String = "",
    val epgId: String? = null,
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
)

@Serializable
data class MobileSeriesSeasonDto(
    val season: Int = 1,
    val episodes: List<ServerChannelDto> = emptyList(),
)

@Serializable
data class MobileSeriesDetailDto(
    val showTitle: String = "",
    val logo: String? = null,
    val country: String? = null,
    val countryName: String = "",
    val flag: String = "",
    val category: String = "",
    val totalSeasons: Int = 0,
    val totalEpisodes: Int = 0,
    val resumeEpisodeId: String? = null,
    val seasons: List<MobileSeriesSeasonDto> = emptyList(),
)

@Serializable
data class MobileVodCategoryDto(
    val id: String = "",
    val name: String = "",
    val count: Int = 0,
)

@Serializable
data class MobileVodLanguageDto(
    val code: String = "",
    val name: String = "",
    val flag: String = "",
    val count: Int = 0,
)

@Serializable
data class MobileVodCategoriesResponse(
    val languages: List<MobileVodLanguageDto> = emptyList(),
    val genres: List<MobileVodCategoryDto> = emptyList(),
    val platforms: List<MobileVodCategoryDto> = emptyList(),
    val categories: List<MobileVodCategoryDto> = emptyList(),
)

@Serializable
data class ServerChannelPage(
    val items: List<ServerChannelDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val hasMore: Boolean = false,
)

@Serializable
data class MobileStreamSource(
    val channelId: String = "",
    val name: String = "",
    val url: String = "",
    val quality: String = "",
    val label: String = "",
    val host: String = "",
    val protocol: String = "",
    val latencyMs: Long? = null,
    val recommended: Boolean = false,
)

@Serializable
data class ServerHealth(
    val ok: Boolean = false,
    val name: String = "CasaTV",
    val version: String = "",
    val channels: Int = 0,
    val liveChannels: Int? = null,
    val streams: Int? = null,
    val movies: Int? = null,
    val series: Int? = null,
    val vodTotal: Int? = null,
)

@Serializable
data class MobileRecordingDto(
    val id: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val country: String = "",
    val logo: String? = null,
    val status: String = "",
    val startedAtMs: Long = 0L,
    val endedAtMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    val remainingMs: Long = 0L,
    val recordedMs: Long = 0L,
    val bytes: Long = 0L,
    val playUrl: String = "",
    val downloadUrl: String = "",
)

@Serializable
data class MobileRecordingsListResponse(
    val ok: Boolean = false,
    val recordings: List<MobileRecordingDto> = emptyList(),
)

@Serializable
data class MobileRecordingSingleResponse(
    val ok: Boolean = false,
    val recording: MobileRecordingDto? = null,
)

enum class ConnectionMode {
    LOCAL_ONLY,
    SEARCHING_WIFI,
    SERVER_CONNECTED,
}
