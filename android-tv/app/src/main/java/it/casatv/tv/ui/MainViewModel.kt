package it.casatv.tv.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import it.casatv.tv.data.LocalM3uParser
import it.casatv.tv.data.UpdateInfo
import it.casatv.tv.update.GitHubUpdater
import it.casatv.tv.update.TvUpdateFrequency
import java.io.File
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.casatv.tv.data.ApiException
import it.casatv.tv.data.BrowseFilter
import it.casatv.tv.data.CasaApi
import it.casatv.tv.data.OfflineCache
import kotlinx.coroutines.Dispatchers
import it.casatv.tv.data.Channel
import it.casatv.tv.data.ChannelPage
import it.casatv.tv.data.GlobalSearchResult
import it.casatv.tv.data.HomeRow
import it.casatv.tv.data.Presence
import it.casatv.tv.data.Profile
import it.casatv.tv.data.ProfileLanguage
import it.casatv.tv.data.ServerConfig
import it.casatv.tv.data.StreamSource
import it.casatv.tv.data.SyncStateResponse
import it.casatv.tv.data.SystemAlert
import it.casatv.tv.data.SystemStatus
import it.casatv.tv.discovery.DiscoveredServer
import it.casatv.tv.discovery.MdnsDiscovery
import it.casatv.tv.service.CasaTvService
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseState(
    val filter: BrowseFilter = BrowseFilter(),
    val items: List<Channel> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val initialized: Boolean = false,
)

data class PlaybackSession(
    val channel: Channel,
    val queue: List<Channel> = listOf(channel),
    val index: Int = 0,
    val sources: List<StreamSource> = emptyList(),
    val sourceIndex: Int = -1,
    val playToken: Int = 0,
) {
    val activeStreamUrl: String
        get() = sources.getOrNull(sourceIndex)?.url?.takeIf { it.isNotBlank() } ?: channel.streamUrl
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val api = CasaApi()
    private val discovery = MdnsDiscovery(application)
    private val deviceId: String = prefs.getString(KEY_DEVICE_ID, null)
        ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    private val _system = MutableStateFlow<SystemStatus?>(null)
    val system: StateFlow<SystemStatus?> = _system.asStateFlow()

    private val _resourceAlert = MutableStateFlow<SystemAlert?>(null)
    val resourceAlert: StateFlow<SystemAlert?> = _resourceAlert.asStateFlow()
    private var lastAlertSignature: String? = null
    private var lastAlertShownAt: Long = 0L

    private val _systemBusy = MutableStateFlow(false)
    val systemBusy: StateFlow<Boolean> = _systemBusy.asStateFlow()

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER_URL, null))
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _config = MutableStateFlow<ServerConfig?>(null)
    val config: StateFlow<ServerConfig?> = _config.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    val offlineCache = OfflineCache(application)

    private val _isServerDisconnected = MutableStateFlow(false)
    val isServerDisconnected: StateFlow<Boolean> = _isServerDisconnected.asStateFlow()
    private var reconnectJob: Job? = null

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _updateDownloading = MutableStateFlow(false)
    val updateDownloading: StateFlow<Boolean> = _updateDownloading.asStateFlow()

    private val _updateProgress = MutableStateFlow(0f)
    val updateProgress: StateFlow<Float> = _updateProgress.asStateFlow()

    private val _updateError = MutableStateFlow<String?>(null)
    val updateError: StateFlow<String?> = _updateError.asStateFlow()

    private val githubUpdater = GitHubUpdater(application)
    private val _updateFrequency = MutableStateFlow(githubUpdater.getFrequency())
    val updateFrequency: StateFlow<TvUpdateFrequency> = _updateFrequency.asStateFlow()

    val discoveredServers: StateFlow<List<DiscoveredServer>> = discovery.servers
    val discovering: StateFlow<Boolean> = discovery.discovering
    val discoveryError: StateFlow<String?> = discovery.error

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _profileAvatars = MutableStateFlow(DEFAULT_AVATARS)
    val profileAvatars: StateFlow<List<String>> = _profileAvatars.asStateFlow()

    private val _profileLanguages = MutableStateFlow(DEFAULT_LANGUAGES)
    val profileLanguages: StateFlow<List<ProfileLanguage>> = _profileLanguages.asStateFlow()

    private val _activeProfile = MutableStateFlow<Profile?>(null)
    val activeProfile: StateFlow<Profile?> = _activeProfile.asStateFlow()

    private val _profileGate = MutableStateFlow(true)
    val profileGate: StateFlow<Boolean> = _profileGate.asStateFlow()

    private fun sortProfiles(list: List<Profile>): List<Profile> {
        return list.sortedWith(
            compareBy(
                { it.id == "guest" || it.isGuest || it.name.equals("Ospite", ignoreCase = true) || it.name.equals("Guest", ignoreCase = true) },
                { it.createdAt }
            )
        )
    }

    private val _profileBusy = MutableStateFlow(false)
    val profileBusy: StateFlow<Boolean> = _profileBusy.asStateFlow()
    private var profileJob: Job? = null
    private var profileSelectedByUser = false

    private val _tvCountry = MutableStateFlow<String?>(null)
    val tvCountry: StateFlow<String?> = _tvCountry.asStateFlow()

    private val _homeRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val homeRows: StateFlow<List<HomeRow>> = _homeRows.asStateFlow()

    private val _homeLoading = MutableStateFlow(false)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()

    private val _homeError = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = _homeError.asStateFlow()

    private val _zapQueue = MutableStateFlow<List<Channel>>(emptyList())
    val zapQueue: StateFlow<List<Channel>> = _zapQueue.asStateFlow()

    private val _tvLoading = MutableStateFlow(false)
    val tvLoading: StateFlow<Boolean> = _tvLoading.asStateFlow()

    private val _tvError = MutableStateFlow<String?>(null)
    val tvError: StateFlow<String?> = _tvError.asStateFlow()

    /** Selected country has no channels, so the queue shows the whole catalog. */
    private val _tvFallback = MutableStateFlow(false)
    val tvFallback: StateFlow<Boolean> = _tvFallback.asStateFlow()

    private val _tvInitialized = MutableStateFlow(false)
    val tvInitialized: StateFlow<Boolean> = _tvInitialized.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GlobalSearchResult>>(emptyList())
    val searchResults: StateFlow<List<GlobalSearchResult>> = _searchResults.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    private var searchJob: Job? = null

    fun searchGlobalChannels(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchLoading.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(280)
            _searchLoading.value = true
            try {
                val q = query.trim()
                val page = runCatching {
                    api.channels(
                        country = null,
                        query = q,
                        limit = 200,
                        sort = "number",
                    )
                }.getOrNull()

                val channelResults = if (page != null && page.items.isNotEmpty()) {
                    page.items.map { GlobalSearchResult.ChannelResult(it) }
                } else {
                    offlineCache.getChannels(query = q).map { GlobalSearchResult.ChannelResult(it) }
                }
                _searchResults.value = channelResults
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _searchLoading.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchLoading.value = false
    }

    private val _playback = MutableStateFlow<PlaybackSession?>(null)
    val playback: StateFlow<PlaybackSession?> = _playback.asStateFlow()

    private val _lastPlayedChannelId = MutableStateFlow<String?>(null)
    val lastPlayedChannelId: StateFlow<String?> = _lastPlayedChannelId.asStateFlow()

    private val _aspectRatioMode = MutableStateFlow(
        prefs.getInt(KEY_ASPECT_RATIO_MODE, androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
    )
    val aspectRatioMode: StateFlow<Int> = _aspectRatioMode.asStateFlow()

    fun setAspectRatioMode(mode: Int) {
        _aspectRatioMode.value = mode
        prefs.edit().putInt(KEY_ASPECT_RATIO_MODE, mode).apply()
    }

    private val _multiTvSync = MutableStateFlow(prefs.getBoolean(KEY_MULTI_TV_SYNC, true))
    val multiTvSync: StateFlow<Boolean> = _multiTvSync.asStateFlow()

    fun setMultiTvSync(enabled: Boolean) {
        _multiTvSync.value = enabled
        prefs.edit().putBoolean(KEY_MULTI_TV_SYNC, enabled).apply()
    }

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, "light") ?: "light")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        val normalized = if (mode.equals("dark", ignoreCase = true)) "dark" else "light"
        _themeMode.value = normalized
        prefs.edit().putString(KEY_THEME_MODE, normalized).apply()
    }

    suspend fun syncPlayback(
        channelId: String,
        isLive: Boolean,
        positionMs: Long,
        liveOffsetMs: Long,
        segmentSeq: Long = 0L,
        segmentOffsetMs: Long = 0L,
        segmentDurationMs: Long = 0L,
    ): SyncStateResponse = api.syncPlayback(
        channelId = channelId,
        isLive = isLive,
        positionMs = positionMs,
        liveOffsetMs = liveOffsetMs,
        segmentSeq = segmentSeq,
        segmentOffsetMs = segmentOffsetMs,
        segmentDurationMs = segmentDurationMs,
    )

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()

    private val _favorites = MutableStateFlow<List<Channel>>(emptyList())
    val favorites: StateFlow<List<Channel>> = _favorites.asStateFlow()

    private val _favoritesLoading = MutableStateFlow(false)
    val favoritesLoading: StateFlow<Boolean> = _favoritesLoading.asStateFlow()

    private val _favoritesError = MutableStateFlow<String?>(null)
    val favoritesError: StateFlow<String?> = _favoritesError.asStateFlow()


    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val favoriteBusy = mutableSetOf<String>()
    private var connectJob: Job? = null
    private var homeJob: Job? = null
    private var browseJob: Job? = null
    private var favoritesJob: Job? = null
    private var systemJob: Job? = null
    private var zapJob: Job? = null

    private val language: String
        get() = _activeProfile.value?.language ?: "it"

    init {
        val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
        api.deviceId = deviceId
        api.deviceName = deviceName
        api.devicePlatform = "Android TV ${android.os.Build.VERSION.RELEASE}"
        api.setPlayback(Presence.playbackState, Presence.playbackChannel)

        val saved = _serverUrl.value
        startDiscovery()
        if (saved != null) {
            CasaTvService.start(application)
            connect(saved, automatic = true)
        }

        // Autonomous server connection: automatically connect to discovered server without user action
        viewModelScope.launch {
            discoveredServers.collect { servers ->
                if (_config.value == null && !_connecting.value && servers.isNotEmpty()) {
                    val savedTarget = _serverUrl.value
                    val target = servers.firstOrNull { it.url == savedTarget } ?: servers.first()
                    android.util.Log.i("CasaTV", "Selezione autonoma server: ${target.name} (${target.url})")
                    connect(target.url, automatic = true)
                }
            }
        }
    }

    fun startDiscovery() {
        if (_config.value == null) discovery.start()
    }

    fun stopDiscovery() = discovery.stop()

    fun refreshDiscovery() {
        discovery.stop()
        discovery.start()
    }

    fun changeServer(url: String) {
        connect(url, automatic = false)
    }

    fun connect(input: String, automatic: Boolean = false) {
        if (_connecting.value) return
        val normalized = try {
            CasaApi.normalizeBaseUrl(input)
        } catch (error: Exception) {
            _connectionError.value = error.message
                ?: localize(language, "Indirizzo non valido", "Invalid address", "Dirección no válida")
            return
        }

        val previousUrl = _serverUrl.value
        val previousProfileId = _activeProfile.value?.id
        _connecting.value = true
        _connectionError.value = null
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            try {
                val health = api.health(normalized)
                if (!health.ok) throw IllegalStateException("Server is not responding")
                api.use(normalized)
                api.useProfile(null)
                val profileResponse = api.profiles()
                _profiles.value = sortProfiles(profileResponse.profiles)
                _profileAvatars.value = profileResponse.avatars.ifEmpty { DEFAULT_AVATARS }
                _profileLanguages.value =
                    profileResponse.languages.ifEmpty { DEFAULT_LANGUAGES }

                val savedId = prefs.getString(KEY_PROFILE_ID, null)
                val selected = profileResponse.profiles.firstOrNull { it.id == savedId }
                    ?: profileResponse.profiles.firstOrNull()
                if (selected != null) {
                    api.useProfile(selected.id)
                    // Re-read the profiles with the active profile header so the
                    // country names on the gate match the selected UI language.
                    runCatching { api.profiles() }.onSuccess { localized ->
                        _profiles.value = sortProfiles(localized.profiles)
                        _profileAvatars.value = localized.avatars.ifEmpty { DEFAULT_AVATARS }
                        _profileLanguages.value =
                            localized.languages.ifEmpty { DEFAULT_LANGUAGES }
                    }
                }
                val serverConfig = api.config(normalized)
                _config.value = serverConfig
                _serverUrl.value = normalized
                _isServerDisconnected.value = false
                prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
                discovery.stop()
                if (selected != null) {
                    _activeProfile.value = serverConfig.activeProfile ?: selected
                    val country = preferredTvCountry(selected)
                    _tvCountry.value = country
                    _browse.value = BrowseState(
                        filter = BrowseFilter(country = country.takeIf { it.isNotBlank() }),
                    )
                    loadHome(showLoading = true)
                    loadZapQueue(country)
                    loadFavorites()
                }
                // Update persistent offline cache with freshly received server metadata
                viewModelScope.launch(Dispatchers.IO) {
                    offlineCache.update(
                        config = serverConfig,
                        serverUrls = serverConfig.urls,
                        profiles = sortProfiles(profileResponse.profiles),
                        activeProfileId = selected?.id,
                        primaryServerUrl = normalized,
                    )
                }
                // Always enter user selection page on app startup
                if (!profileSelectedByUser) {
                    _profileGate.value = true
                }
                prefs.edit()
                    .putString(KEY_LANGUAGE, _activeProfile.value?.language ?: "it")
                    .apply()
                CasaTvService.start(getApplication())
                startSystemPolling()
                checkForUpdates()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (offlineCache.hasValidCache()) {
                    android.util.Log.w("CasaTV", "Server non raggiungibile, attivazione flussi memorizzati offline")
                    val cached = offlineCache.cachedData
                    val fallbackConfig = cached.config ?: ServerConfig(name = "CasaTV (Offline)", baseUrl = normalized)
                    _config.value = fallbackConfig
                    _profiles.value = sortProfiles(cached.profiles)
                    val savedId = prefs.getString(KEY_PROFILE_ID, null) ?: cached.activeProfileId
                    val selected = cached.profiles.firstOrNull { it.id == savedId }
                        ?: cached.profiles.firstOrNull()
                    _activeProfile.value = selected
                    if (!profileSelectedByUser) {
                        _profileGate.value = true
                    }
                    val country = selected?.let { preferredTvCountry(it) }.orEmpty()
                    _tvCountry.value = country
                    _serverUrl.value = normalized
                    _isServerDisconnected.value = true
                    _connectionError.value = null

                    if (cached.channels.isNotEmpty()) {
                        _zapQueue.value = cached.channels
                        _tvInitialized.value = true
                        _browse.value = BrowseState(
                            filter = BrowseFilter(country = country.takeIf { it.isNotBlank() }),
                            items = cached.channels,
                            total = cached.channels.size,
                            initialized = true,
                        )
                    }
                    if (cached.homeRows.isNotEmpty()) {
                        _homeRows.value = cached.homeRows
                    }
                    if (cached.favorites.isNotEmpty()) {
                        _favorites.value = cached.favorites
                    }

                    startReconnectionPoll()
                    discovery.start()
                } else {
                    if (previousUrl != null) {
                        api.use(previousUrl)
                        api.useProfile(previousProfileId)
                    }
                    _connectionError.value = humanError(error)
                    discovery.start()
                    if (!automatic && prefs.getString(KEY_SERVER_URL, null) == null) {
                        _serverUrl.value = null
                    }
                }
            } finally {
                _connecting.value = false
            }
        }
    }


    fun startReconnectionPoll() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            while (isActive && _isServerDisconnected.value) {
                delay(15_000)
                if (!_isServerDisconnected.value) break
                val primary = _serverUrl.value ?: offlineCache.cachedData.primaryServerUrl
                var reconnectedUrl: String? = null

                if (primary.isNotBlank()) {
                    try {
                        val h = api.health(primary)
                        if (h.ok) reconnectedUrl = primary
                    } catch (_: Exception) {}
                }

                if (reconnectedUrl == null) {
                    val alternates = offlineCache.cachedData.serverUrls
                        .map { it.url }
                        .filter { it.isNotBlank() && it != primary }
                    for (alt in alternates) {
                        try {
                            val h = api.health(alt)
                            if (h.ok) {
                                reconnectedUrl = alt
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (reconnectedUrl != null) {
                    android.util.Log.i("CasaTV", "Server ricollegato con successo: $reconnectedUrl")
                    api.use(reconnectedUrl)
                    _serverUrl.value = reconnectedUrl
                    prefs.edit().putString(KEY_SERVER_URL, reconnectedUrl).apply()
                    _isServerDisconnected.value = false
                    try {
                        val newConfig = api.config(reconnectedUrl)
                        _config.value = newConfig
                        offlineCache.update(config = newConfig, primaryServerUrl = reconnectedUrl)
                    } catch (_: Exception) {}
                    loadHome(showLoading = false)
                    loadZapQueue()
                    loadFavorites(showLoading = false)
                    showMessage(
                        localize(
                            language,
                            "Server ricollegato",
                            "Server reconnected",
                            "Servidor reconectado",
                        ),
                    )
                    break
                }
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        homeJob?.cancel()
        browseJob?.cancel()
        favoritesJob?.cancel()
        searchJob?.cancel()
        zapJob?.cancel()
        systemJob?.cancel()
        discovery.stop()
        CasaTvService.stop(getApplication())
        Presence.idle()
        api.setPlayback("idle")
        _system.value = null
        _resourceAlert.value = null
        lastAlertSignature = null
        api.useProfile(null)
        prefs.edit().remove(KEY_SERVER_URL).apply()
        _serverUrl.value = null
        _config.value = null
        _connectionError.value = null
        _connecting.value = false
        _profiles.value = emptyList()
        _activeProfile.value = null
        _profileGate.value = false
        _profileBusy.value = false
        _tvCountry.value = null
        _homeRows.value = emptyList()
        _homeError.value = null
        _zapQueue.value = emptyList()
        _tvLoading.value = false
        _tvError.value = null
        _tvFallback.value = false
        _tvInitialized.value = false
        _playback.value = null
        _browse.value = BrowseState()
        _favorites.value = emptyList()
        _favoritesError.value = null
        _homeLoading.value = false
    }

    fun selectProfile(profile: Profile) {
        if (_profileBusy.value) return
        profileSelectedByUser = true
        _profileBusy.value = true
        _activeProfile.value = profile
        _profileGate.value = false
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            try {
                if (profile.id == "guest" || prefs.getString(KEY_PROFILE_ID, null) == "guest") {
                    api.clearGuestProfile()
                }
                activateProfile(profile)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                showMessage(humanError(error))
            } finally {
                _profileBusy.value = false
            }
        }
    }

    fun cancelProfileSelection() {
        profileJob?.cancel()
        _profileBusy.value = false
        if (_activeProfile.value != null) {
            _profileGate.value = false
        }
    }

    fun switchProfile() {
        _playback.value = null
        _profileGate.value = true
    }

    fun closeProfileGate() {
        if (_activeProfile.value != null && profileSelectedByUser) _profileGate.value = false
    }

    fun createProfile(
        name: String,
        avatar: String,
        language: String,
        country: String,
        onComplete: (Boolean) -> Unit,
    ) {
        saveProfile(null, name, avatar, language, country, onComplete)
    }

    fun updateProfile(
        profile: Profile,
        name: String,
        avatar: String,
        language: String,
        country: String,
        onComplete: (Boolean) -> Unit,
    ) {
        saveProfile(profile, name, avatar, language, country, onComplete)
    }

    private fun saveProfile(
        existing: Profile?,
        name: String,
        avatar: String,
        language: String,
        country: String,
        onComplete: (Boolean) -> Unit,
    ) {
        if (_profileBusy.value) return
        viewModelScope.launch {
            _profileBusy.value = true
            try {
                val saved = if (existing == null) {
                    api.createProfile(name, avatar, language, country)
                } else {
                    api.updateProfile(existing.id, name, avatar, language, country)
                }
                val refreshed = api.profiles()
                _profiles.value = sortProfiles(refreshed.profiles)
                _profileAvatars.value = refreshed.avatars.ifEmpty { DEFAULT_AVATARS }
                _profileLanguages.value = refreshed.languages.ifEmpty { DEFAULT_LANGUAGES }
                val wasActive = existing == null || existing.id == _activeProfile.value?.id
                if (existing != null && existing.country != saved.country) {
                    prefs.edit().remove(tvCountryKey(saved.id)).apply()
                }
                if (wasActive) {
                    activateProfile(saved)
                }
                showMessage(
                    localize(
                        saved.language,
                        if (existing == null) "Profilo creato" else "Profilo aggiornato",
                        if (existing == null) "Profile created" else "Profile updated",
                        if (existing == null) "Perfil creado" else "Perfil actualizado",
                    ),
                )
                onComplete(true)
            } catch (error: Exception) {
                showMessage(humanError(error))
                onComplete(false)
            } finally {
                _profileBusy.value = false
            }
        }
    }

    fun deleteProfile(profile: Profile, onComplete: (Boolean) -> Unit) {
        if (_profileBusy.value) return
        viewModelScope.launch {
            _profileBusy.value = true
            try {
                api.deleteProfile(profile.id)
                prefs.edit().remove(tvCountryKey(profile.id)).apply()
                val refreshed = api.profiles()
                _profiles.value = sortProfiles(refreshed.profiles)
                if (_activeProfile.value?.id == profile.id) {
                    val next = refreshed.profiles.firstOrNull()
                    if (next != null) {
                        activateProfile(next)
                    } else {
                        _activeProfile.value = null
                        _profileGate.value = true
                    }
                }
                showMessage(localize(language, "Profilo eliminato", "Profile deleted", "Perfil eliminado"))
                onComplete(true)
            } catch (error: Exception) {
                showMessage(humanError(error))
                onComplete(false)
            } finally {
                _profileBusy.value = false
            }
        }
    }

    private suspend fun activateProfile(profile: Profile) {
        val previousProfileId = _activeProfile.value?.id
        api.useProfile(profile.id)
        val serverConfig = try {
            api.config()
        } catch (error: Exception) {
            api.useProfile(previousProfileId)
            throw error
        }
        homeJob?.cancel()
        browseJob?.cancel()
        favoritesJob?.cancel()
        searchJob?.cancel()
        zapJob?.cancel()
        _config.value = serverConfig
        _activeProfile.value = serverConfig.activeProfile ?: profile
        prefs.edit().putString(KEY_PROFILE_ID, profile.id).apply()
        runCatching { api.profiles() }.onSuccess { refreshed ->
            _profiles.value = refreshed.profiles
            _profileAvatars.value = refreshed.avatars.ifEmpty { DEFAULT_AVATARS }
            _profileLanguages.value = refreshed.languages.ifEmpty { DEFAULT_LANGUAGES }
            refreshed.profiles.firstOrNull { it.id == profile.id }?.let {
                _activeProfile.value = it
            }
        }
        val active = _activeProfile.value ?: profile
        prefs.edit()
            .putString(KEY_PROFILE_ID, active.id)
            .putString(KEY_LANGUAGE, active.language)
            .apply()
        Presence.profileName = active.name
        Presence.idle()
        api.setPlayback("idle")

        val country = preferredTvCountry(profile)
        _tvCountry.value = country
        _profileGate.value = false
        _playback.value = null
        _homeRows.value = emptyList()
        _homeError.value = null
        _zapQueue.value = emptyList()
        _tvLoading.value = false
        _tvError.value = null
        _tvFallback.value = false
        _tvInitialized.value = false
        _favorites.value = emptyList()
        _favoritesError.value = null
        _browse.value = BrowseState(
            filter = BrowseFilter(country = country.takeIf { it.isNotBlank() }),
        )
        loadHome(showLoading = true)
        loadZapQueue(country)
        loadFavorites()
    }

    private fun preferredTvCountry(profile: Profile): String {
        val saved = prefs.getString(tvCountryKey(profile.id), null)
        return normalizeCountryCode(saved)
            ?: normalizeCountryCode(profile.country)
            ?: "IT"
    }

    fun setTvCountry(country: String) {
        val profile = _activeProfile.value ?: return
        val code = normalizeCountryCode(country) ?: return
        _tvCountry.value = code
        prefs.edit().putString(tvCountryKey(profile.id), code).apply()
        loadZapQueue(code)
        val name = countryDisplayName(code)
        showMessage(
            localize(
                profile.language,
                "Modalità TV: canali di $name",
                "TV mode: channels from $name",
                "Modo TV: canales de $name",
            ),
        )
    }

    private fun countryDisplayName(code: String): String = countryName(code, language)

    fun loadHome(showLoading: Boolean = true) {
        if (_config.value == null || _activeProfile.value == null) return
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            if (showLoading) _homeLoading.value = true
            _homeError.value = null
            try {
                val response = api.home()
                val rows = response.rows.filter { it.channels.isNotEmpty() }
                _homeRows.value = rows
                _isServerDisconnected.value = false
                viewModelScope.launch(Dispatchers.IO) {
                    offlineCache.update(homeRows = rows)
                }
            } catch (error: Exception) {
                _isServerDisconnected.value = true
                startReconnectionPoll()
                val cached = offlineCache.cachedData.homeRows
                if (cached.isNotEmpty()) {
                    _homeRows.value = cached
                    _homeError.value = null
                } else {
                    _homeError.value = humanError(error)
                }
            } finally {
                _homeLoading.value = false
            }
        }
    }

    fun loadZapQueue(country: String = _tvCountry.value ?: _activeProfile.value?.country.orEmpty()) {
        if (_config.value == null || _activeProfile.value == null) return
        zapJob?.cancel()
        zapJob = viewModelScope.launch {
            _tvLoading.value = true
            _tvError.value = null
            try {
                val code = normalizeCountryCode(country)
                val allChannels = mutableListOf<Channel>()
                var offset = 0
                val pageSize = 1000
                var hasMore = true
                var fallback = false

                while (hasMore) {
                    val page = if (code != null) {
                        api.channels(country = code, limit = pageSize, offset = offset, sort = "number")
                    } else {
                        api.channels(limit = pageSize, offset = offset, sort = "number")
                    }
                    if (page.items.isEmpty()) break
                    allChannels.addAll(page.items)
                    offset += page.items.size
                    hasMore = page.hasMore && page.items.size >= pageSize
                }

                // A playlist without country metadata must not leave the TV
                // screen empty: show the whole catalog, clearly marked.
                if (code != null && allChannels.isEmpty()) {
                    val fallbackPage = api.channels(limit = pageSize, sort = "number")
                    allChannels.addAll(fallbackPage.items)
                    fallback = allChannels.isNotEmpty()
                }

                val items = allChannels.sortedWith(channelOrder)
                _zapQueue.value = items
                _tvFallback.value = fallback
                _tvInitialized.value = true
                _isServerDisconnected.value = false
                viewModelScope.launch(Dispatchers.IO) {
                    offlineCache.update(channels = items)
                }
                _playback.value?.let { session ->
                    val index = items.indexOfFirst { it.id == session.channel.id }
                    if (index >= 0) {
                        _playback.value = session.copy(queue = items, index = index)
                    } else if (items.isNotEmpty()) {
                        _playback.value = session.copy(
                            queue = listOf(session.channel) + items,
                            index = 0,
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _isServerDisconnected.value = true
                startReconnectionPoll()
                val cached = offlineCache.cachedData.channels
                if (cached.isNotEmpty()) {
                    val code = normalizeCountryCode(country)
                    val fallbackItems = offlineCache.getChannels(country = code).ifEmpty { cached }
                    _zapQueue.value = fallbackItems
                    _tvInitialized.value = true
                    _tvError.value = null
                } else {
                    _tvError.value = humanError(error)
                    _tvInitialized.value = true
                }
            } finally {
                _tvLoading.value = false
            }
        }
    }

    fun ensureTvLoaded() {
        if (_config.value == null || _activeProfile.value == null) return
        if (_tvLoading.value || _tvInitialized.value) return
        loadZapQueue()
    }

    fun reloadTv() {
        _tvInitialized.value = false
        loadZapQueue()
    }

    fun loadFavorites(showLoading: Boolean = true) {
        if (_config.value == null || _activeProfile.value == null) return
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            if (showLoading) _favoritesLoading.value = true
            _favoritesError.value = null
            try {
                val page = api.channels(favorite = true, limit = 500, sort = "number")
                _favorites.value = page.items
                viewModelScope.launch(Dispatchers.IO) {
                    offlineCache.update(favorites = page.items)
                }
            } catch (error: Exception) {
                _isServerDisconnected.value = true
                startReconnectionPoll()
                val cached = offlineCache.cachedData.favorites
                if (cached.isNotEmpty()) {
                    _favorites.value = cached
                    _favoritesError.value = null
                } else {
                    _favoritesError.value = humanError(error)
                }
            } finally {
                _favoritesLoading.value = false
            }
        }
    }


    fun tuneToChannel(channelId: String) {
        viewModelScope.launch {
            val found = runCatching { api.channel(channelId) }.getOrNull()
            if (found != null) {
                val q = _zapQueue.value.ifEmpty { listOf(found) }
                startPlayback(found, q)
            } else {
                showMessage(localize(language, "Canale non trovato", "Channel not found", "Canal no encontrado"))
            }
        }
    }

    fun ensureBrowseLoaded() {
        val state = _browse.value
        if (state.initialized || state.loading) return
        startBrowseLoad(reset = true, debounceMs = 0)
    }

    fun setBrowseCountry(country: String?) {
        updateBrowseFilter(_browse.value.filter.copy(country = country))
    }

    fun setBrowseCategory(category: String?) {
        updateBrowseFilter(_browse.value.filter.copy(category = category))
    }

    fun setBrowseLanguage(language: String?) {
        updateBrowseFilter(_browse.value.filter.copy(language = language))
    }

    fun setBrowseQuery(query: String) {
        updateBrowseFilter(_browse.value.filter.copy(query = query), debounceMs = 320)
    }

    private fun updateBrowseFilter(
        filter: BrowseFilter,
        debounceMs: Long = 0,
    ) {
        if (filter == _browse.value.filter) return
        _browse.value = _browse.value.copy(
            filter = filter,
            error = null,
            loading = true,
            initialized = false,
        )
        startBrowseLoad(reset = true, debounceMs = debounceMs)
    }

    private fun startBrowseLoad(reset: Boolean, debounceMs: Long = 0) {
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            if (_config.value == null) return@launch
            if (reset) {
                _browse.value = _browse.value.copy(loading = true, error = null)
            } else {
                _browse.value = _browse.value.copy(loadingMore = true, error = null)
            }
            try {
                val filter = _browse.value.filter
                val offset = if (reset) 0 else _browse.value.items.size
                var page = api.channels(
                    country = filter.country,
                    category = filter.category,
                    language = filter.language,
                    query = filter.query,
                    limit = BROWSE_PAGE_SIZE,
                    offset = offset,
                    sort = "number",
                )
                // Custom playlists (the Android server) rarely match the
                // profile country: fall back once to all countries instead of
                // showing an empty screen on first open.
                if (
                    reset &&
                    !_browse.value.initialized &&
                    filter.country != null &&
                    filter.language == null &&
                    filter.query.isBlank() &&
                    page.items.isEmpty()
                ) {
                    val fallback = api.channels(
                        country = null,
                        category = filter.category,
                        limit = BROWSE_PAGE_SIZE,
                        offset = 0,
                        sort = "number",
                    )
                    if (fallback.items.isNotEmpty()) {
                        page = fallback
                        _browse.value = _browse.value.copy(filter = filter.copy(country = null))
                    }
                }
                val current = _browse.value
                val merged = if (reset) {
                    page.items
                } else {
                    (current.items + page.items).distinctBy { it.id }
                }
                _browse.value = current.copy(
                    items = merged,
                    total = page.total,
                    hasMore = page.hasMore,
                    loading = false,
                    loadingMore = false,
                    initialized = true,
                    error = null,
                )
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _isServerDisconnected.value = true
                startReconnectionPoll()
                val filter = _browse.value.filter
                val cached = offlineCache.getChannels(
                    country = filter.country,
                    category = filter.category,
                    query = filter.query,
                )
                if (cached.isNotEmpty()) {
                    val current = _browse.value
                    _browse.value = current.copy(
                        items = cached,
                        total = cached.size,
                        hasMore = false,
                        loading = false,
                        loadingMore = false,
                        initialized = true,
                        error = null,
                    )
                } else {
                    _browse.value = _browse.value.copy(
                        loading = false,
                        loadingMore = false,
                        error = humanError(error),
                        initialized = true,
                    )
                }
            }
        }
    }

    fun loadMoreBrowse() {
        val state = _browse.value
        if (state.loading || state.loadingMore || !state.hasMore || _config.value == null) return
        startBrowseLoad(reset = false)
    }

    fun reloadBrowse() {
        if (_config.value == null) return
        _browse.value = _browse.value.copy(initialized = false)
        startBrowseLoad(reset = true)
    }

    fun startPlayback(channel: Channel, queue: List<Channel>) {
        _lastPlayedChannelId.value = channel.id
        if (channel.streamUrl.isBlank()) {
            showMessage(
                localize(
                    language,
                    "Questo canale non ha un flusso disponibile",
                    "This channel has no available stream",
                    "Este canal no tiene un flujo disponible",
                ),
            )
            return
        }
        // The caller's list is the visible one (genre, category, language,
        // country row): zapping must stay inside it. Only fall back to the TV
        // mode queue when the caller did not provide a usable list.
        val callerQueue = queue.filter { it.streamUrl.isNotBlank() }
        val source = when {
            callerQueue.isNotEmpty() && callerQueue.any { it.id == channel.id } -> callerQueue
            callerQueue.isEmpty() && queue.isNotEmpty() -> queue
            _zapQueue.value.any { it.id == channel.id } -> _zapQueue.value
            queue.isNotEmpty() -> listOf(channel) + queue.filterNot { it.id == channel.id }
            _zapQueue.value.isNotEmpty() ->
                listOf(channel) + _zapQueue.value.filterNot { it.id == channel.id }
            else -> listOf(channel)
        }
        val index = source.indexOfFirst { it.id == channel.id }
        val normalizedQueue = if (index >= 0) source else listOf(channel) + source
        val playIndex = if (index >= 0) index else 0
        _playback.value = PlaybackSession(
            channel = normalizedQueue[playIndex],
            queue = normalizedQueue,
            index = playIndex,
        )
        Presence.playing(normalizedQueue[playIndex].name)
        api.setPlayback("playing", normalizedQueue[playIndex].name)
        markPlayed(channel.id)
        prefetchSources(channel.id)
    }

    fun playRelative(offset: Int) {
        val session = _playback.value ?: return
        val playable = session.queue.filter { it.streamUrl.isNotBlank() }
        if (playable.isEmpty()) return
        val currentIdx = playable.indexOfFirst { it.id == session.channel.id }.coerceAtLeast(0)
        val size = playable.size
        val nextIndex = ((currentIdx + offset) % size + size) % size
        val nextChannel = playable[nextIndex]
        val fullIndex = session.queue.indexOfFirst { it.id == nextChannel.id }.coerceAtLeast(0)
        _playback.value = session.copy(
            channel = nextChannel,
            index = fullIndex,
            sources = emptyList(),
            sourceIndex = -1,
            playToken = session.playToken + 1,
        )
        Presence.playing(nextChannel.name)
        api.setPlayback("playing", nextChannel.name)
        markPlayed(nextChannel.id)
        prefetchSources(nextChannel.id)
    }

    suspend fun tuneToNumber(number: Int): Channel? {
        if (number <= 0) return null
        val channel = try {
            api.channelByNumber(number)
        } catch (_: Exception) {
            offlineCache.findChannelByNumber(number)
                ?: _zapQueue.value.firstOrNull { it.number == number }
                ?: return null
        }
        val current = _playback.value
        val regionQueue = _zapQueue.value
        val queue = when {
            current != null && current.queue.any { it.id == channel.id } -> current.queue
            regionQueue.any { it.id == channel.id } -> regionQueue
            // Keep zapping alive even when tuning a channel outside the
            // region queue: continue with the current region afterwards.
            else -> listOf(channel) + regionQueue.filterNot { it.id == channel.id }
        }.map { item ->
            if (item.id == channel.id) mergeChannel(item, channel) else item
        }
        val index = queue.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        _playback.value = PlaybackSession(
            channel = queue.getOrElse(index) { channel },
            queue = queue,
            index = index,
        )
        Presence.playing(channel.name)
        api.setPlayback("playing", channel.name)
        markPlayed(channel.id)
        prefetchSources(channel.id)
        return channel
    }

    private var prefetchJob: Job? = null
    private fun prefetchSources(channelId: String) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            delay(350)
            runCatching { api.channelSources(channelId) }.onSuccess { result ->
                val current = _playback.value
                if (current != null && current.channel.id == channelId && current.sources.isEmpty()) {
                    val sources = result.sources.filter { it.url.isNotBlank() }.take(8)
                    if (sources.isNotEmpty()) {
                        _playback.value = current.copy(sources = sources)
                    }
                }
            }
        }
    }

    suspend fun tryNextSource(): Boolean {
        val initial = _playback.value ?: return false
        val channelId = initial.channel.id
        val token = initial.playToken
        var session = initial
        if (session.sources.isEmpty()) {
            val loaded = runCatching { api.channelSources(channelId) }
                .getOrNull()
                ?.sources
                ?.filter { it.url.isNotBlank() }
                ?.take(8)
                .orEmpty()
            val latest = _playback.value ?: return false
            if (latest.channel.id != channelId || latest.playToken != token) return false
            if (loaded.isEmpty()) return false
            session = latest.copy(sources = loaded)
        }
        val currentCheck = _playback.value ?: return false
        if (currentCheck.channel.id != channelId || currentCheck.playToken != token) return false
        var next = if (session.sourceIndex < 0) 0 else session.sourceIndex + 1
        // Skip the initial source if it is identical to the streamUrl that just failed.
        if (
            session.sourceIndex < 0 &&
            next == 0 &&
            session.sources.getOrNull(0)?.url == session.channel.streamUrl
        ) {
            next = 1
        }
        if (next >= session.sources.size) {
            _playback.value = session
            return false
        }
        _playback.value = session.copy(
            sourceIndex = next,
            playToken = session.playToken + 1,
        )
        return true
    }

    /** Restart the current channel from the server's default source order. */
    fun restartPlaybackSource() {
        val session = _playback.value ?: return
        _playback.value = session.copy(
            sourceIndex = -1,
            playToken = session.playToken + 1,
        )
    }

    private val _playlists = MutableStateFlow<List<it.casatv.tv.data.PlaylistDto>>(emptyList())
    val playlists: StateFlow<List<it.casatv.tv.data.PlaylistDto>> = _playlists.asStateFlow()

    fun loadPlaylists() {
        viewModelScope.launch {
            _playlists.value = api.getPlaylists()
        }
    }

    fun addPlaylist(name: String, url: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = api.addPlaylist(name, url)
            if (success) {
                showMessage("Playlist aggiunta con successo")
                loadPlaylists()
                loadHome()
                reloadTv()
            } else {
                showMessage("Impossibile scaricare la playlist")
            }
            onComplete(success)
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            val success = api.deletePlaylist(id)
            if (success) {
                showMessage("Playlist rimossa")
                loadPlaylists()
                loadHome()
                reloadTv()
            }
        }
    }

    fun refreshPlaylist(id: String) {
        viewModelScope.launch {
            showMessage("Aggiornamento playlist...")
            val success = api.refreshPlaylist(id)
            if (success) {
                showMessage("Playlist aggiornata")
                loadPlaylists()
                loadHome()
                reloadTv()
            } else {
                showMessage("Errore nell'aggiornamento")
            }
        }
    }

    fun toggleFavorite(channel: Channel) {
        val id = channel.id
        synchronized(favoriteBusy) {
            if (!favoriteBusy.add(id)) return
        }
        viewModelScope.launch {
            try {
                val updated = try {
                    api.setFavorite(id, !channel.favorite)
                } catch (_: java.io.IOException) {
                    _isServerDisconnected.value = true
                    startReconnectionPoll()
                    channel.copy(favorite = !channel.favorite)
                }
                applyChannelUpdate(updated)
                viewModelScope.launch(Dispatchers.IO) {
                    val currentFavs = _favorites.value
                    offlineCache.update(favorites = currentFavs)
                }
                showMessage(
                    localize(
                        language,
                        if (updated.favorite) "Aggiunto ai preferiti" else "Rimosso dai preferiti",
                        if (updated.favorite) "Added to favorites" else "Removed from favorites",
                        if (updated.favorite) "Añadido a favoritos" else "Eliminado de favoritos",
                    ),
                )
            } catch (error: Exception) {
                showMessage(humanError(error))
            } finally {
                synchronized(favoriteBusy) { favoriteBusy.remove(id) }
            }
        }
    }

    private fun applyChannelUpdate(updated: Channel) {
        val merged = mergeChannel(
            _playback.value?.channel?.takeIf { it.id == updated.id }
                ?: _favorites.value.firstOrNull { it.id == updated.id }
                ?: _browse.value.items.firstOrNull { it.id == updated.id }
                ?: _zapQueue.value.firstOrNull { it.id == updated.id }
                ?: updated,
            updated,
        )

        _homeRows.value = _homeRows.value.mapNotNull { row ->
            val channels = if (row.id == "favorites") {
                if (merged.favorite) {
                    val without = row.channels.filterNot { it.id == merged.id }
                    (without + merged).sortedWith(channelOrder)
                } else {
                    row.channels.filterNot { it.id == merged.id }
                }
            } else {
                row.channels.map { if (it.id == merged.id) mergeChannel(it, updated) else it }
            }
            if (channels.isEmpty() && row.id == "favorites") {
                null
            } else {
                row.copy(channels = channels, total = maxOf(row.total, channels.size))
            }
        }

        _browse.value = _browse.value.copy(
            items = _browse.value.items.map {
                if (it.id == merged.id) mergeChannel(it, updated) else it
            },
        )

        _favorites.value = if (merged.favorite) {
            (_favorites.value.filterNot { it.id == merged.id } + merged).sortedWith(channelOrder)
        } else {
            _favorites.value.filterNot { it.id == merged.id }
        }

        _zapQueue.value = _zapQueue.value.map {
            if (it.id == merged.id) mergeChannel(it, updated) else it
        }

        _playback.value?.let { session ->
            _playback.value = session.copy(
                channel = if (session.channel.id == merged.id) {
                    mergeChannel(session.channel, updated)
                } else {
                    session.channel
                },
                queue = session.queue.map {
                    if (it.id == merged.id) mergeChannel(it, updated) else it
                },
            )
        }
    }

    private fun mergeChannel(old: Channel, new: Channel): Channel =
        new.copy(
            now = new.now ?: old.now,
            next = new.next ?: old.next,
            lastPlayedAt = new.lastPlayedAt ?: old.lastPlayedAt,
        )

    private var markJob: Job? = null
    private fun markPlayed(channelId: String) {
        markJob?.cancel()
        markJob = viewModelScope.launch {
            delay(2000)
            runCatching { api.markPlayed(channelId) }
        }
    }

    fun stopPlayback() {
        if (_playback.value == null) return
        _playback.value = null
        Presence.idle()
        api.setPlayback("idle")
        loadHome(showLoading = false)
    }

    fun startSystemPolling() {
        systemJob?.cancel()
        systemJob = viewModelScope.launch {
            while (isActive) {
                loadSystem()
                delay(20_000)
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest.isBlank() || current.isBlank()) return false
        val lParts = latest.split('.').mapNotNull { it.toIntOrNull() }
        val cParts = current.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(lParts.size, cParts.size)) {
            val l = lParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    fun setUpdateFrequency(freq: TvUpdateFrequency) {
        githubUpdater.setFrequency(freq)
        _updateFrequency.value = freq
    }

    fun checkGitHubUpdates(force: Boolean = false) {
        viewModelScope.launch {
            val ghUpdate = githubUpdater.checkForUpdate(force = force)
            if (ghUpdate != null) {
                _updateInfo.value = ghUpdate
            } else if (force) {
                _messages.emit("Nessun nuovo aggiornamento trovato su GitHub.")
            }
        }
    }

    fun importLocalM3uUrl(url: String) {
        viewModelScope.launch {
            val clean = url.trim()
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                _messages.emit("Inserisci un URL http:// o https:// valido")
                return@launch
            }
            try {
                val text = withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(clean).build()
                    OkHttpClient().newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        resp.body?.string() ?: error("Risposta vuota")
                    }
                }
                applyLocalM3uText(text)
            } catch (e: Exception) {
                _messages.emit("Errore caricamento M3U: ${e.message}")
            }
        }
    }

    fun importLocalM3uUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Impossibile leggere il file selezionato")
                }
                applyLocalM3uText(text)
            } catch (e: Exception) {
                _messages.emit("Errore lettura file M3U: ${e.message}")
            }
        }
    }

    private suspend fun applyLocalM3uText(text: String) {
        val channels = withContext(Dispatchers.Default) { LocalM3uParser.parse(text) }
        if (channels.isEmpty()) {
            _messages.emit("Nessun canale valido trovato nella lista M3U")
            return
        }
        offlineCache.update(channels = channels)
        _zapQueue.value = channels
        _tvInitialized.value = true
        _tvError.value = null
        _browse.value = _browse.value.copy(
            items = channels.take(100),
            total = channels.size,
            hasMore = false,
            error = null,
        )
        _messages.emit("Caricati ${channels.size} canali M3U in locale")
    }

    fun checkForUpdates(silent: Boolean = true) {
        viewModelScope.launch {
            val currentVersion = it.casatv.tv.BuildConfig.VERSION_NAME
            val lanInfo = try {
                val info = api.versionInfo()
                val isNewer = isNewerVersion(info.latest, currentVersion)
                if ((info.updateAvailable || isNewer) && !info.downloadUrl.isNullOrBlank()) info else null
            } catch (_: Exception) {
                null
            }
            val ghInfo = githubUpdater.checkForUpdate(force = !silent)

            val best = when {
                lanInfo != null && ghInfo != null ->
                    if (isNewerVersion(ghInfo.latest, lanInfo.latest)) ghInfo else lanInfo
                ghInfo != null -> ghInfo
                lanInfo != null -> lanInfo
                else -> null
            }

            if (best != null) {
                _updateInfo.value = best
            } else {
                _updateInfo.value = null
                if (!silent) {
                    _messages.emit("L'app è già aggiornata all'ultima versione disponibile ($currentVersion).")
                }
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    fun installUpdate() {
        val info = _updateInfo.value ?: return
        val downloadUrl = info.downloadUrl ?: return
        if (_updateDownloading.value) return
        _updateDownloading.value = true
        _updateProgress.value = 0f
        _updateError.value = null
        viewModelScope.launch {
            try {
                val updatesDir = File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(updatesDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()
                api.downloadFile(downloadUrl, apkFile) { progress ->
                    _updateProgress.value = progress
                }
                _updateDownloading.value = false
                launchApkInstaller(apkFile)
            } catch (e: Exception) {
                _updateDownloading.value = false
                _updateError.value = e.message ?: "Errore durante il download dell'aggiornamento"
            }
        }
    }

    private fun launchApkInstaller(apkFile: File) {
        try {
            val context = getApplication<Application>()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _updateError.value = "Impossibile avviare l'installazione: ${e.message}"
        }
    }

    fun refreshSystem() {
        if (_systemBusy.value) return
        viewModelScope.launch {
            _systemBusy.value = true
            try {
                loadSystem()
            } finally {
                _systemBusy.value = false
            }
        }
    }

    private suspend fun loadSystem() {
        val currentBase = _serverUrl.value ?: return
        var reachable = false
        try {
            val health = api.health(currentBase)
            if (health.ok) reachable = true
        } catch (_: Exception) {
            val alternates = offlineCache.cachedData.serverUrls
                .map { it.url }
                .filter { it.isNotBlank() && it != currentBase }
            for (alt in alternates) {
                try {
                    val h = api.health(alt)
                    if (h.ok) {
                        api.use(alt)
                        _serverUrl.value = alt
                        prefs.edit().putString(KEY_SERVER_URL, alt).apply()
                        reachable = true
                        break
                    }
                } catch (_: Exception) {}
            }
        }

        _isServerDisconnected.value = !reachable
        if (!reachable) {
            startReconnectionPoll()
            return
        }

        runCatching { api.heartbeat() }
        runCatching { api.system() }.onSuccess { status ->
            _system.value = status
            val worst = status.alerts
                .filter { it.level == "critical" || it.level == "warning" }
                .maxByOrNull { if (it.level == "critical") it.value + 1000.0 else it.value }
            if (worst != null) {
                val signature = "${worst.kind}:${worst.level}"
                val now = System.currentTimeMillis()
                if (signature != lastAlertSignature || now - lastAlertShownAt > 180_000L) {
                    lastAlertSignature = signature
                    lastAlertShownAt = now
                    _resourceAlert.value = worst
                }
            } else {
                lastAlertSignature = null
                _resourceAlert.value = null
            }
        }
    }

    fun dismissResourceAlert() {
        _resourceAlert.value = null
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            try {
                api.refreshCatalog()
                showMessage(
                    localize(
                        language,
                        "Aggiornamento del catalogo avviato",
                        "Catalog update started",
                        "Actualización del catálogo iniciada",
                    ),
                )
            } catch (error: Exception) {
                showMessage(humanError(error))
            }
        }
    }


    fun reloadServer() {
        val base = _serverUrl.value ?: return
        viewModelScope.launch {
            try {
                val serverConfig = api.config(base)
                _config.value = serverConfig
                showMessage(
                    localize(
                        language,
                        "Dati del server aggiornati",
                        "Server data refreshed",
                        "Datos del servidor actualizados",
                    ),
                )
                loadHome(showLoading = false)
                loadZapQueue()
                loadFavorites(showLoading = false)
            } catch (error: Exception) {
                showMessage(humanError(error))
            }
        }
    }

    fun showMessage(text: String) {
        _messages.tryEmit(text)
    }

    private fun humanError(error: Exception): String = when (error) {
        is ApiException -> error.message ?: localize(language, "Errore del server", "Server error", "Error del servidor")
        is IOException -> localize(
            language,
            "Server non raggiungibile. Controlla che CasaTV sia acceso e sulla stessa rete.",
            "Server unreachable. Make sure CasaTV is on and on the same network.",
            "Servidor no disponible. Comprueba que CasaTV esté encendido y en la misma red.",
        )
        is IllegalArgumentException -> error.message
            ?: localize(language, "Indirizzo del server non valido", "Invalid server address", "Dirección del servidor no válida")
        else -> error.message
            ?: localize(language, "Si è verificato un errore", "Something went wrong", "Se ha producido un error")
    }

    private val channelOrder = compareBy<Channel>(
        { it.number == null },
        { it.number ?: Int.MAX_VALUE },
        { it.name.lowercase() },
    )

    private fun tvCountryKey(profileId: String) = "tv_country_$profileId"

    override fun onCleared() {
        super.onCleared()
        discovery.stop()
        if (_activeProfile.value?.id == "guest" || prefs.getString(KEY_PROFILE_ID, null) == "guest") {
            viewModelScope.launch { runCatching { api.clearGuestProfile() } }
        }
    }

    companion object {
        private const val PREFS_NAME = "casatv"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_PROFILE_ID = "active_profile_id"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LANGUAGE = "active_language"
        private const val KEY_ASPECT_RATIO_MODE = "aspect_ratio_mode"
        private const val KEY_MULTI_TV_SYNC = "multi_tv_sync"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val BROWSE_PAGE_SIZE = 120

        private val DEFAULT_AVATARS = listOf(
            "🦊", "🐼", "🐯", "🐨", "🦁", "🐸", "🐵", "🐺", "🦄", "🐙",
            "🐳", "🦉", "🐻", "🐧", "🦖", "🐝", "🐬", "🦋", "🐰", "🐲",
        )
        private val DEFAULT_LANGUAGES = listOf(
            ProfileLanguage("it", "Italiano"),
            ProfileLanguage("en", "English"),
            ProfileLanguage("es", "Español"),
        )
    }
}
