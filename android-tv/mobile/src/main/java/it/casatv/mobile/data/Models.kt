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
) {
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
data class ServerHealth(
    val ok: Boolean = false,
    val name: String = "CasaTV",
    val version: String = "",
    val channels: Int = 0,
)

enum class ConnectionMode {
    LOCAL_ONLY,
    SEARCHING_WIFI,
    SERVER_CONNECTED,
}
