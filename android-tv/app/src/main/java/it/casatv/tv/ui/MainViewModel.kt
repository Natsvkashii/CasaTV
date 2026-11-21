package it.casatv.tv.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import it.casatv.tv.BuildConfig
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
import it.casatv.tv.data.CasaApi
import it.casatv.tv.data.OfflineCache
import kotlinx.coroutines.Dispatchers
import it.casatv.tv.data.Channel
import it.casatv.tv.data.GlobalSearchResult
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackSession(
    val channel: Channel,
    val queue: List<Channel> = listOf(channel),
    val index: Int = 0,
    val sources: List<StreamSource> = emptyList(),
    val sourceIndex: Int = -1,
    val playToken: Int = 0,
    val isMixMode: Boolean = false,
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

    val offlineCache = OfflineCache(application)

    private val _config = MutableStateFlow<ServerConfig?>(
        offlineCache.cachedData.config ?: ServerConfig(
            name = "CasaTV",
            baseUrl = _serverUrl.value.orEmpty(),
        ),
    )
    val config: StateFlow<ServerConfig?> = _config.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _isServerDisconnected = MutableStateFlow(false)
    val isServerDisconnected: StateFlow<Boolean> = _isServerDisconnected.asStateFlow()
    private var serverConnectedLive = false
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

    private val _profiles = MutableStateFlow(
        sortProfiles(offlineCache.cachedData.profiles).ifEmpty {
            listOf(
                Profile(
                    id = "guest",
                    name = "Ospite",
                    avatar = "👤",
                    language = "it",
                    country = "IT",
                    isGuest = true,
                ),
            )
        },
    )
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _profileAvatars = MutableStateFlow(DEFAULT_AVATARS)
    val profileAvatars: StateFlow<List<String>> = _profileAvatars.asStateFlow()

    private val _profileLanguages = MutableStateFlow(DEFAULT_LANGUAGES)
    val profileLanguages: StateFlow<List<ProfileLanguage>> = _profileLanguages.asStateFlow()

    private val _activeProfile = MutableStateFlow<Profile?>(
        offlineCache.cachedData.let { cached ->
            val savedId = prefs.getString(KEY_PROFILE_ID, null) ?: cached.activeProfileId
            cached.profiles.firstOrNull { it.id == savedId } ?: cached.profiles.firstOrNull()
        },
    )
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

    private val _zapQueue = MutableStateFlow(offlineCache.cachedData.channels)
    val zapQueue: StateFlow<List<Channel>> = _zapQueue.asStateFlow()

    private val _tvLoading = MutableStateFlow(false)
    val tvLoading: StateFlow<Boolean> = _tvLoading.asStateFlow()

    private val _tvError = MutableStateFlow<String?>(null)
    val tvError: StateFlow<String?> = _tvError.asStateFlow()

    /** Selected country has no channels, so the queue shows the whole catalog. */
    private val _tvFallback = MutableStateFlow(false)
    val tvFallback: StateFlow<Boolean> = _tvFallback.asStateFlow()

    private val _tvInitialized = MutableStateFlow(offlineCache.cachedData.channels.isNotEmpty())
    val tvInitialized: StateFlow<Boolean> = _tvInitialized.asStateFlow()

    private val _mixChannels = MutableStateFlow<List<Channel>>(emptyList())
    val mixChannels: StateFlow<List<Channel>> = _mixChannels.asStateFlow()

    private val _mixLoading = MutableStateFlow(false)
    val mixLoading: StateFlow<Boolean> = _mixLoading.asStateFlow()

    private val _showMixRegionPicker = MutableStateFlow(false)
    val showMixRegionPicker: StateFlow<Boolean> = _showMixRegionPicker.asStateFlow()

    private val _selectedMixRegions = MutableStateFlow(setOf(MixRegion.ALL_WORLD))
    val selectedMixRegions: StateFlow<Set<MixRegion>> = _selectedMixRegions.asStateFlow()

    private val _selectedMixLanguages = MutableStateFlow<Set<String>>(emptySet())
    val selectedMixLanguages: StateFlow<Set<String>> = _selectedMixLanguages.asStateFlow()

    private val _selectedMixGenres = MutableStateFlow<Set<String>>(emptySet())
    val selectedMixGenres: StateFlow<Set<String>> = _selectedMixGenres.asStateFlow()

    private val mixHistory = mutableListOf<Channel>()
    private var mixIndex: Int = -1
    private val deadMixChannelIds = java.util.Collections.synchronizedSet(linkedSetOf<String>())
    private val runtimeAudioOnlyMixIds = java.util.Collections.synchronizedSet(linkedSetOf<String>())
    @Volatile
    private var verifiedNextMixChannel: Channel? = null
    private var mixPreloadJob: Job? = null
    private var mixPoolJob: Job? = null

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

    private val _liveSafetyBufferEnabled = MutableStateFlow(prefs.getBoolean(KEY_LIVE_SAFETY_BUFFER, true))
    val liveSafetyBufferEnabled: StateFlow<Boolean> = _liveSafetyBufferEnabled.asStateFlow()

    fun setLiveSafetyBufferEnabled(enabled: Boolean) {
        _liveSafetyBufferEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LIVE_SAFETY_BUFFER, enabled).apply()
    }

    private val _mixIncludeAudioOnly = MutableStateFlow(prefs.getBoolean(KEY_MIX_INCLUDE_AUDIO_ONLY, false))
    val mixIncludeAudioOnly: StateFlow<Boolean> = _mixIncludeAudioOnly.asStateFlow()

    fun setMixIncludeAudioOnly(enabled: Boolean) {
        if (_mixIncludeAudioOnly.value == enabled) return
        _mixIncludeAudioOnly.value = enabled
        prefs.edit().putBoolean(KEY_MIX_INCLUDE_AUDIO_ONLY, enabled).apply()
        cachedMixSourceRef = null
        cachedMixIncludeAudioOnly = null
        mixPreloadJob?.cancel()
        mixPreloadJob = null
        verifiedNextMixChannel = null
        if (_playback.value?.isMixMode == true) {
            preverifyNextMixChannel()
        }
    }

    private val _showRemoteHintsBanner = MutableStateFlow(prefs.getBoolean(KEY_SHOW_REMOTE_HINTS_BANNER, true))
    val showRemoteHintsBanner: StateFlow<Boolean> = _showRemoteHintsBanner.asStateFlow()

    fun setShowRemoteHintsBanner(enabled: Boolean) {
        _showRemoteHintsBanner.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_REMOTE_HINTS_BANNER, enabled).apply()
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

    private val _favorites = MutableStateFlow(offlineCache.cachedData.favorites)
    val favorites: StateFlow<List<Channel>> = _favorites.asStateFlow()

    private val _favoritesLoading = MutableStateFlow(false)
    val favoritesLoading: StateFlow<Boolean> = _favoritesLoading.asStateFlow()

    private val _favoritesError = MutableStateFlow<String?>(null)
    val favoritesError: StateFlow<String?> = _favoritesError.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val localM3uHttpClient by lazy { OkHttpClient() }
    private val favoriteBusy = mutableSetOf<String>()
    private var connectJob: Job? = null
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
        checkForUpdates(silent = true)

        // Autonomous server connection: automatically connect to discovered server without user action
        viewModelScope.launch {
            discoveredServers.collect { servers ->
                if (!serverConnectedLive && !_connecting.value && servers.isNotEmpty()) {
                    val savedTarget = _serverUrl.value
                    val target = servers.firstOrNull { it.url == savedTarget } ?: servers.first()
                    android.util.Log.i("CasaTV", "Selezione autonoma server: ${target.name} (${target.url})")
                    connect(target.url, automatic = true)
                }
            }
        }
    }

    fun startDiscovery() {
        if (!serverConnectedLive) discovery.start()
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
                serverConnectedLive = true
                prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
                discovery.stop()
                if (selected != null) {
                    _activeProfile.value = serverConfig.activeProfile ?: selected
                    val country = preferredTvCountry(selected)
                    _tvCountry.value = country
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
                    _isServerDisconnected.value = true
                    _connectionError.value = null
                    startReconnectionPoll()
                    discovery.start()
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
        _zapQueue.value = emptyList()
        _tvLoading.value = false
        _tvError.value = null
        _tvFallback.value = false
        _tvInitialized.value = false
        _playback.value = null
        resetMixState(clearPool = true)
        _favorites.value = emptyList()
        _favoritesError.value = null
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
        resetMixState(clearPool = true)
        _zapQueue.value = emptyList()
        _tvLoading.value = false
        _tvError.value = null
        _tvFallback.value = false
        _tvInitialized.value = false
        _favorites.value = emptyList()
        _favoritesError.value = null
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
                ensureMixPoolLoaded()
                _playback.value?.takeIf { !it.isMixMode }?.let { session ->
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
                    ensureMixPoolLoaded()
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

    private var mixPoolFullLoaded: Boolean = false

    private fun resetMixState(clearPool: Boolean = false, resetRegions: Boolean = true) {
        mixPreloadJob?.cancel()
        mixPreloadJob = null
        verifiedNextMixChannel = null
        _showMixRegionPicker.value = false
        synchronized(mixHistory) {
            mixHistory.clear()
            mixIndex = -1
        }
        deadMixChannelIds.clear()
        if (resetRegions) {
            _selectedMixRegions.value = setOf(MixRegion.ALL_WORLD)
            _selectedMixLanguages.value = emptySet()
            _selectedMixGenres.value = emptySet()
        }
        if (clearPool) {
            mixPoolJob?.cancel()
            mixPoolJob = null
            mixPoolFullLoaded = false
            _mixChannels.value = emptyList()
            _mixLoading.value = false
        }
    }

    @Volatile
    private var cachedMixSourceRef: List<Channel>? = null
    @Volatile
    private var cachedMixIncludeAudioOnly: Boolean? = null
    @Volatile
    private var cachedMixHttpAll: List<Channel> = emptyList()
    @Volatile
    private var cachedMixBuckets: Map<MixRegion, List<Channel>> = emptyMap()

    private fun isHttpUrl(raw: String): Boolean {
        val clean = raw.trim().lowercase()
        return clean.startsWith("http://") || clean.startsWith("https://")
    }

    private fun isMixChannelExcludedAsAudioOnly(ch: Channel): Boolean =
        !_mixIncludeAudioOnly.value && (ch.id in runtimeAudioOnlyMixIds || isLikelyAudioOnlyChannel(ch))

    private fun hasActiveMixFilters(): Boolean =
        _selectedMixLanguages.value.isNotEmpty() || _selectedMixGenres.value.isNotEmpty()

    private fun matchesActiveMixFilters(ch: Channel): Boolean =
        matchesMixLanguages(ch, _selectedMixLanguages.value) &&
            matchesMixGenres(ch, _selectedMixGenres.value)

    private fun ensureMixBucketsIndexed() {
        val rawSource = _mixChannels.value
            .ifEmpty { _zapQueue.value }
            .ifEmpty { offlineCache.cachedData.channels }
        val includeAudio = _mixIncludeAudioOnly.value
        if (rawSource === cachedMixSourceRef && includeAudio == cachedMixIncludeAudioOnly) return
        val httpAll = ArrayList<Channel>(rawSource.size)
        val buckets = java.util.EnumMap<MixRegion, MutableList<Channel>>(MixRegion::class.java)
        rawSource.forEach { ch ->
            if (!isHttpUrl(ch.streamUrl)) return@forEach
            if (!includeAudio && isLikelyAudioOnlyChannel(ch)) return@forEach
            httpAll.add(ch)
            resolveMixRegion(ch.country)?.let { region ->
                buckets.getOrPut(region) { ArrayList() }.add(ch)
            }
        }
        cachedMixHttpAll = httpAll
        cachedMixBuckets = buckets
        cachedMixSourceRef = rawSource
        cachedMixIncludeAudioOnly = includeAudio
    }

    private fun regionalPoolFor(regions: Set<MixRegion>): List<Channel> {
        ensureMixBucketsIndexed()
        if (regions.isEmpty() || MixRegion.ALL_WORLD in regions) return cachedMixHttpAll
        if (regions.size == 1) return cachedMixBuckets[regions.first()].orEmpty()
        val combined = ArrayList<Channel>()
        regions.forEach { region ->
            cachedMixBuckets[region]?.let { combined.addAll(it) }
        }
        return combined
    }

    private fun currentMixPool(): List<Channel> {
        val regional = regionalPoolFor(_selectedMixRegions.value)
        if (!hasActiveMixFilters()) return regional.ifEmpty { cachedMixHttpAll }
        return regional.filter { matchesActiveMixFilters(it) }
    }

    private fun consumeVerifiedNextMixChannel(): Channel? {
        mixPreloadJob?.cancel()
        mixPreloadJob = null
        val verified = verifiedNextMixChannel
        verifiedNextMixChannel = null
        val regions = _selectedMixRegions.value
        return verified?.takeIf {
            matchesMixRegions(it.country, regions) &&
                !isMixChannelExcludedAsAudioOnly(it) &&
                matchesActiveMixFilters(it)
        }
    }

    private fun pickRandomUnvisitedMixChannel(pool: List<Channel>): Channel? {
        if (pool.isEmpty()) return null
        val currentId = _playback.value?.channel?.id
        val visitedIds = synchronized(mixHistory) { mixHistory.mapTo(hashSetOf()) { it.id } }
        val eligiblePool = if (_mixIncludeAudioOnly.value) {
            pool
        } else {
            pool.filter { !isMixChannelExcludedAsAudioOnly(it) }.ifEmpty { pool }
        }
        val fresh = eligiblePool.filter { it.id !in visitedIds && it.id !in deadMixChannelIds && it.id != currentId }
        if (fresh.isNotEmpty()) return fresh.random()
        val unvisited = eligiblePool.filter { it.id !in visitedIds && it.id != currentId }
        if (unvisited.isNotEmpty()) return unvisited.random()
        val others = eligiblePool.filter { it.id != currentId }
        return (if (others.isNotEmpty()) others else eligiblePool).randomOrNull()
    }

    private suspend fun fetchAllRemoteMixChannels(): List<Channel> {
        val all = mutableListOf<Channel>()
        var offset = 0
        val pageSize = 4000
        while (offset < 20000) {
            val page = runCatching {
                api.channels(country = null, limit = pageSize, offset = offset, sort = "number")
            }.getOrNull() ?: break
            if (page.items.isEmpty()) break
            all.addAll(page.items)
            offset += page.items.size
            if (!page.hasMore || page.items.size < pageSize) break
        }
        return all
    }

    private fun ensureMixPoolLoaded() {
        val localSeed = (_zapQueue.value + offlineCache.cachedData.channels)
            .filter { isHttpUrl(it.streamUrl) }
            .distinctBy { it.streamUrl }
        if (_mixChannels.value.isEmpty() && localSeed.isNotEmpty()) {
            _mixChannels.value = localSeed.shuffled()
            if (verifiedNextMixChannel == null) {
                preverifyNextMixChannel()
            }
        }
        if (mixPoolFullLoaded || mixPoolJob?.isActive == true) return
        mixPoolJob = viewModelScope.launch {
            val remote = fetchAllRemoteMixChannels()
            val combined = (remote + _zapQueue.value + offlineCache.cachedData.channels)
                .filter { isHttpUrl(it.streamUrl) }
                .distinctBy { it.streamUrl }
                .shuffled()
            if (combined.isNotEmpty()) {
                _mixChannels.value = combined
                mixPoolFullLoaded = remote.isNotEmpty()
                if (verifiedNextMixChannel == null) {
                    preverifyNextMixChannel()
                }
            }
        }
    }

    private fun preverifyNextMixChannel() {
        if (verifiedNextMixChannel != null) return
        mixPreloadJob?.cancel()
        mixPreloadJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && verifiedNextMixChannel == null) {
                val pool = currentMixPool()
                if (pool.isEmpty()) break
                val currentId = _playback.value?.channel?.id
                val visitedIds = synchronized(mixHistory) { mixHistory.mapTo(hashSetOf()) { it.id } }
                val eligiblePool = if (_mixIncludeAudioOnly.value) {
                    pool
                } else {
                    pool.filter { !isMixChannelExcludedAsAudioOnly(it) }.ifEmpty { pool }
                }
                var candidates = eligiblePool.filter { ch ->
                    ch.id !in visitedIds && ch.id !in deadMixChannelIds && ch.id != currentId
                }
                if (candidates.isEmpty()) {
                    deadMixChannelIds.clear()
                    candidates = eligiblePool.filter { it.id !in visitedIds && it.id != currentId }
                }
                if (candidates.isEmpty()) {
                    candidates = eligiblePool.filter { it.id != currentId }.ifEmpty { eligiblePool }
                }
                val batch = pickRandomCandidates(candidates, 3)
                if (batch.isEmpty()) break

                val winner = coroutineScope {
                    val found = CompletableDeferred<Channel>()
                    val workers = batch.map { candidate ->
                        launch {
                            val available = api.isStreamAvailable(candidate.streamUrl)
                            if (!isActive) return@launch
                            if (available) {
                                found.complete(candidate)
                            } else {
                                deadMixChannelIds.add(candidate.id)
                            }
                        }
                    }
                    val awaiter = launch {
                        workers.forEach { it.join() }
                        if (!found.isCompleted) {
                            found.cancel()
                        }
                    }
                    try {
                        found.await()
                    } catch (_: CancellationException) {
                        if (!isActive) throw CancellationException()
                        null
                    } finally {
                        workers.forEach { it.cancel() }
                        awaiter.cancel()
                    }
                }

                if (winner != null) {
                    verifiedNextMixChannel = winner
                    break
                }
            }
        }
    }

    private fun setMixPlaybackChannel(
        target: Channel,
        queueSnapshot: List<Channel>,
        index: Int,
        sources: List<StreamSource> = emptyList(),
        sourceIndex: Int = -1,
        nextToken: Int? = null,
    ) {
        // Mix mode intentionally skips watch history (_lastPlayedChannelId and markPlayed).
        markJob?.cancel()
        markJob = null
        val prev = _playback.value
        _playback.value = PlaybackSession(
            channel = target,
            queue = queueSnapshot,
            index = index,
            sources = sources,
            sourceIndex = sourceIndex,
            playToken = nextToken ?: ((prev?.playToken ?: -1) + 1),
            isMixMode = true,
        )
        Presence.playing(target.name)
        api.setPlayback("playing", target.name)
        prefetchSources(target.id)
    }

    private fun <T> pickRandomCandidates(list: List<T>, count: Int): List<T> {
        if (list.size <= count) return list
        val picked = ArrayList<T>(count)
        val usedIndices = HashSet<Int>(count * 2)
        while (picked.size < count) {
            val idx = kotlin.random.Random.nextInt(list.size)
            if (usedIndices.add(idx)) {
                picked.add(list[idx])
            }
        }
        return picked
    }

    fun openMixSection() {
        if (_playback.value?.isMixMode != true) {
            resetMixState(clearPool = false, resetRegions = true)
        }
        ensureMixPoolLoaded()
        _showMixRegionPicker.value = true
    }

    fun dismissMixRegionPicker() {
        _showMixRegionPicker.value = false
        if (_playback.value?.isMixMode != true) {
            resetMixState(clearPool = false, resetRegions = true)
        }
    }

    fun toggleMixRegion(region: MixRegion) {
        val current = _selectedMixRegions.value
        val next: Set<MixRegion> = if (region == MixRegion.ALL_WORLD) {
            setOf(MixRegion.ALL_WORLD)
        } else {
            val withoutWorld = current - MixRegion.ALL_WORLD
            val toggled = if (region in withoutWorld) withoutWorld - region else withoutWorld + region
            toggled.ifEmpty { setOf(MixRegion.ALL_WORLD) }
        }
        _selectedMixRegions.value = next
        verifiedNextMixChannel?.let { cand ->
            if (!matchesMixRegions(cand.country, next)) {
                mixPreloadJob?.cancel()
                mixPreloadJob = null
                verifiedNextMixChannel = null
            }
        }
        if (currentMixPool().isNotEmpty() && verifiedNextMixChannel == null) {
            preverifyNextMixChannel()
        }
    }

    fun toggleMixLanguage(code: String) {
        val current = _selectedMixLanguages.value
        _selectedMixLanguages.value = if (code in current) current - code else current + code
        invalidateVerifiedMixAfterFilterChange()
    }

    fun toggleMixGenre(genreId: String) {
        val current = _selectedMixGenres.value
        _selectedMixGenres.value = if (genreId in current) current - genreId else current + genreId
        invalidateVerifiedMixAfterFilterChange()
    }

    fun clearMixFilters() {
        if (!hasActiveMixFilters()) return
        _selectedMixLanguages.value = emptySet()
        _selectedMixGenres.value = emptySet()
        invalidateVerifiedMixAfterFilterChange()
    }

    private fun invalidateVerifiedMixAfterFilterChange() {
        mixPreloadJob?.cancel()
        mixPreloadJob = null
        verifiedNextMixChannel = null
        if (_playback.value?.isMixMode == true && currentMixPool().isNotEmpty()) {
            preverifyNextMixChannel()
        }
    }

    fun startMixFromSelectedRegions() {
        _showMixRegionPicker.value = false
        val regions = _selectedMixRegions.value
        mixPreloadJob?.cancel()
        mixPreloadJob = null
        verifiedNextMixChannel?.let { cand ->
            if (!matchesMixRegions(cand.country, regions)) {
                verifiedNextMixChannel = null
            }
        }
        synchronized(mixHistory) {
            mixHistory.clear()
            mixIndex = -1
        }
        deadMixChannelIds.clear()
        playRandomMixChannel()
    }

    fun refreshMixChannels() {
        ensureMixPoolLoaded()
    }

    private fun startMixWithPoolChannel(nextChannel: Channel) {
        val snapshot = synchronized(mixHistory) {
            mixHistory.add(nextChannel)
            mixIndex = mixHistory.lastIndex
            mixHistory.toList() to mixIndex
        }
        setMixPlaybackChannel(
            target = nextChannel,
            queueSnapshot = snapshot.first,
            index = snapshot.second,
        )
        preverifyNextMixChannel()
    }

    fun playRandomMixChannel() {
        val regions = _selectedMixRegions.value
        val regionalMatches = regionalPoolFor(regions)
        val existingHttp = cachedMixHttpAll

        if (regionalMatches.isNotEmpty() || (mixPoolFullLoaded && existingHttp.isNotEmpty())) {
            ensureMixPoolLoaded()
            val basePool = regionalMatches.ifEmpty { existingHttp }
            val filteredPool = if (hasActiveMixFilters()) {
                basePool.filter { matchesActiveMixFilters(it) }
            } else {
                basePool
            }
            if (filteredPool.isEmpty()) {
                if (mixPoolFullLoaded) {
                    showMessage(
                        localize(
                            language,
                            "Nessun canale corrisponde ai filtri selezionati",
                            "No channels match the selected filters",
                            "Ningún canal coincide con los filtros seleccionados",
                        ),
                    )
                    return
                }
            } else {
                val nextChannel = consumeVerifiedNextMixChannel() ?: pickRandomUnvisitedMixChannel(filteredPool)
                    ?: return
                startMixWithPoolChannel(nextChannel)
                return
            }
        }

        viewModelScope.launch {
            _mixLoading.value = true
            try {
                val remote = fetchAllRemoteMixChannels()
                val combined = (remote + _zapQueue.value + offlineCache.cachedData.channels)
                    .filter { isHttpUrl(it.streamUrl) }
                    .distinctBy { it.streamUrl }
                    .shuffled()
                if (combined.isEmpty()) {
                    showMessage(
                        localize(
                            language,
                            "Nessun canale disponibile",
                            "No channels available",
                            "No hay canales disponibles",
                        ),
                    )
                    return@launch
                }
                _mixChannels.value = combined
                mixPoolFullLoaded = remote.isNotEmpty()
                val basePool = regionalPoolFor(regions)
                    .ifEmpty { cachedMixHttpAll }
                    .ifEmpty { combined }
                val pool = if (hasActiveMixFilters()) {
                    basePool.filter { matchesActiveMixFilters(it) }
                } else {
                    basePool
                }
                if (pool.isEmpty()) {
                    showMessage(
                        localize(
                            language,
                            "Nessun canale corrisponde ai filtri selezionati",
                            "No channels match the selected filters",
                            "Ningún canal coincide con los filtros seleccionados",
                        ),
                    )
                    return@launch
                }
                val nextChannel = consumeVerifiedNextMixChannel()
                    ?: pickRandomUnvisitedMixChannel(pool)
                    ?: pool.first()
                startMixWithPoolChannel(nextChannel)
            } finally {
                _mixLoading.value = false
            }
        }
    }

    fun skipAudioOnlyMixChannel(channelId: String): Boolean {
        runtimeAudioOnlyMixIds.add(channelId)
        verifiedNextMixChannel?.let { cand ->
            if (cand.id in runtimeAudioOnlyMixIds) {
                mixPreloadJob?.cancel()
                mixPreloadJob = null
                verifiedNextMixChannel = null
            }
        }
        val session = _playback.value ?: return false
        if (!session.isMixMode || session.channel.id != channelId || _mixIncludeAudioOnly.value) {
            return false
        }
        val pool = currentMixPool().filter { !isMixChannelExcludedAsAudioOnly(it) && it.id != channelId }
        val nextChannel = consumeVerifiedNextMixChannel()?.takeIf { it.id != channelId }
            ?: pickRandomUnvisitedMixChannel(pool)?.takeIf { it.id != channelId }
            ?: return false
        synchronized(mixHistory) {
            if (mixHistory.lastOrNull()?.id == channelId) {
                mixHistory.removeAt(mixHistory.lastIndex)
                mixIndex = (mixHistory.lastIndex).coerceAtLeast(-1)
            }
        }
        startMixWithPoolChannel(nextChannel)
        return true
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
        if (session.isMixMode) {
            playMixRelative(session, offset)
            return
        }
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

    private fun playMixRelative(session: PlaybackSession, offset: Int) {
        if (offset == 0) return
        if (offset < 0) {
            val pair = synchronized(mixHistory) {
                if (mixIndex <= 0 || mixHistory.isEmpty()) return
                mixIndex = (mixIndex + offset).coerceAtLeast(0)
                mixHistory[mixIndex] to mixHistory.toList()
            }
            setMixPlaybackChannel(
                target = pair.first,
                queueSnapshot = pair.second,
                index = mixIndex,
                nextToken = session.playToken + 1,
            )
            if (verifiedNextMixChannel == null) {
                preverifyNextMixChannel()
            }
            return
        }

        val existingPair: Pair<Channel, List<Channel>>? = synchronized(mixHistory) {
            if (mixIndex >= 0 && mixIndex < mixHistory.lastIndex) {
                mixIndex = (mixIndex + offset).coerceAtMost(mixHistory.lastIndex)
                mixHistory[mixIndex] to mixHistory.toList()
            } else {
                null
            }
        }
        if (existingPair != null) {
            setMixPlaybackChannel(
                target = existingPair.first,
                queueSnapshot = existingPair.second,
                index = mixIndex,
                nextToken = session.playToken + 1,
            )
            if (verifiedNextMixChannel == null) {
                preverifyNextMixChannel()
            }
            return
        }

        val pool = currentMixPool()
        val nextChannel = consumeVerifiedNextMixChannel() ?: pickRandomUnvisitedMixChannel(pool)
            ?: return
        val snapshot = synchronized(mixHistory) {
            mixHistory.add(nextChannel)
            mixIndex = mixHistory.lastIndex
            mixHistory.toList() to mixIndex
        }
        setMixPlaybackChannel(
            target = nextChannel,
            queueSnapshot = snapshot.first,
            index = snapshot.second,
            nextToken = session.playToken + 1,
        )
        preverifyNextMixChannel()
    }

    fun playChannelAtQueueIndex(targetIndex: Int) {
        val session = _playback.value ?: return
        val target = session.queue.getOrNull(targetIndex) ?: return
        if (target.id == session.channel.id && targetIndex == session.index) return
        if (session.isMixMode) {
            synchronized(mixHistory) {
                mixIndex = targetIndex.coerceIn(0, (mixHistory.lastIndex).coerceAtLeast(0))
            }
            setMixPlaybackChannel(
                target = target,
                queueSnapshot = session.queue,
                index = targetIndex,
                nextToken = session.playToken + 1,
            )
            if (verifiedNextMixChannel == null) {
                preverifyNextMixChannel()
            }
            return
        }

        _lastPlayedChannelId.value = target.id
        _playback.value = session.copy(
            channel = target,
            index = targetIndex,
            sources = emptyList(),
            sourceIndex = -1,
            playToken = session.playToken + 1,
        )
        Presence.playing(target.name)
        api.setPlayback("playing", target.name)
        markPlayed(target.id)
        prefetchSources(target.id)
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
            if (loaded.isEmpty()) {
                if (latest.isMixMode) deadMixChannelIds.add(channelId)
                return false
            }
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
            if (session.isMixMode) deadMixChannelIds.add(channelId)
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

    private val _localM3uStatus = MutableStateFlow(it.casatv.tv.data.LocalM3uStatus())
    val localM3uStatus: StateFlow<it.casatv.tv.data.LocalM3uStatus> = _localM3uStatus.asStateFlow()

    fun loadPlaylists() {
        viewModelScope.launch {
            _playlists.value = api.getPlaylists()
        }
    }

    fun addPlaylist(name: String, url: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val dto = api.addPlaylist(name, url)
            if (dto != null) {
                if (dto.status == "error" || dto.channelCount == 0) {
                    showMessage("${dto.name}: non funziona (${dto.lastError.ifBlank { "nessun canale" }})")
                } else {
                    showMessage("Playlist aggiunta con successo (${dto.channelCount} canali)")
                }
                loadPlaylists()
                reloadTv()
                onComplete(dto.status != "error" && dto.channelCount > 0)
            } else {
                showMessage("Impossibile contattare il server")
                onComplete(false)
            }
        }
    }

    fun uploadServerPlaylistUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val (fileName, bytes) = withContext(Dispatchers.IO) {
                    var displayName = ""
                    runCatching {
                        app.contentResolver.query(
                            uri,
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null,
                        )?.use { c ->
                            if (c.moveToFirst()) {
                                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) displayName = c.getString(idx).orEmpty().trim()
                            }
                        }
                    }
                    if (displayName.isBlank()) {
                        displayName = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "playlist.m3u" }
                    }
                    val data = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Impossibile leggere il file selezionato")
                    displayName to data
                }
                val dto = api.uploadPlaylistFile(fileName, fileName, bytes)
                if (dto != null) {
                    if (dto.status == "error" || dto.channelCount == 0) {
                        showMessage("${dto.name}: non funziona (${dto.lastError.ifBlank { "nessun canale" }})")
                    } else {
                        showMessage("File '${dto.name}' caricato (${dto.channelCount} canali)")
                    }
                    loadPlaylists()
                    reloadTv()
                } else {
                    showMessage("Impossibile caricare il file sul server")
                }
            } catch (e: Exception) {
                showMessage("Errore lettura file: ${e.message}")
            }
        }
    }

    fun checkPlaylist(id: String) {
        viewModelScope.launch {
            showMessage("Verifica playlist...")
            val dto = api.checkPlaylist(id)
            if (dto != null) {
                if (dto.status == "error" || dto.channelCount == 0) {
                    showMessage("${dto.name}: non funziona (${dto.lastError.ifBlank { "nessun canale" }})")
                } else {
                    showMessage("${dto.name}: attiva (${dto.channelCount} canali)")
                }
                loadPlaylists()
            } else {
                showMessage("Errore verifica playlist")
            }
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            val success = api.deletePlaylist(id)
            if (success) {
                showMessage("Playlist rimossa")
                loadPlaylists()
                reloadTv()
            }
        }
    }

    fun refreshPlaylist(id: String) {
        viewModelScope.launch {
            showMessage("Aggiornamento playlist...")
            val dto = api.refreshPlaylist(id)
            if (dto != null) {
                if (dto.status == "error" || dto.channelCount == 0) {
                    showMessage("${dto.name}: non funziona (${dto.lastError.ifBlank { "errore aggiornamento" }})")
                } else {
                    showMessage("Playlist aggiornata (${dto.channelCount} canali)")
                }
                loadPlaylists()
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
        val historyMatch = synchronized(mixHistory) {
            mixHistory.firstOrNull { it.id == updated.id }
        }
        val merged = mergeChannel(
            _playback.value?.channel?.takeIf { it.id == updated.id }
                ?: _favorites.value.firstOrNull { it.id == updated.id }
                ?: _zapQueue.value.firstOrNull { it.id == updated.id }
                ?: historyMatch
                ?: updated,
            updated,
        )

        _favorites.value = if (merged.favorite) {
            (_favorites.value.filterNot { it.id == merged.id } + merged).sortedWith(channelOrder)
        } else {
            _favorites.value.filterNot { it.id == merged.id }
        }

        _zapQueue.value = _zapQueue.value.map {
            if (it.id == merged.id) mergeChannel(it, updated) else it
        }

        _mixChannels.value = _mixChannels.value.map {
            if (it.id == merged.id) mergeChannel(it, updated) else it
        }

        synchronized(mixHistory) {
            for (i in mixHistory.indices) {
                if (mixHistory[i].id == merged.id) {
                    mixHistory[i] = mergeChannel(mixHistory[i], updated)
                }
            }
        }

        verifiedNextMixChannel?.let { next ->
            if (next.id == merged.id) {
                verifiedNextMixChannel = mergeChannel(next, updated)
            }
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
        val current = _playback.value ?: return
        markJob?.cancel()
        markJob = null
        _playback.value = null
        Presence.idle()
        api.setPlayback("idle")
        if (current.isMixMode) {
            resetMixState(clearPool = false, resetRegions = true)
        }
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
                _localM3uStatus.value = it.casatv.tv.data.LocalM3uStatus(label = clean.ifBlank { "URL M3U" }, count = 0, error = "URL non valido (richiesto http:// o https://)")
                _messages.emit("Inserisci un URL http:// o https:// valido")
                return@launch
            }
            try {
                val text = withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(clean).build()
                    localM3uHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        resp.body?.string() ?: error("Risposta vuota")
                    }
                }
                applyLocalM3uText(text, clean)
            } catch (e: Exception) {
                val msg = e.message ?: "Errore caricamento URL"
                _localM3uStatus.value = it.casatv.tv.data.LocalM3uStatus(label = clean, count = 0, error = msg)
                _messages.emit("Errore caricamento M3U: $msg")
            }
        }
    }

    fun importLocalM3uUri(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val displayName = withContext(Dispatchers.IO) {
                var n = ""
                runCatching {
                    app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) n = c.getString(idx).orEmpty().trim()
                        }
                    }
                }
                n.ifBlank { uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "File locale" } }
            }
            try {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Impossibile leggere il file selezionato")
                }
                applyLocalM3uText(text, displayName)
            } catch (e: Exception) {
                val msg = e.message ?: "Errore lettura file"
                _localM3uStatus.value = it.casatv.tv.data.LocalM3uStatus(label = displayName, count = 0, error = msg)
                _messages.emit("Errore lettura file M3U: $msg")
            }
        }
    }

    private suspend fun applyLocalM3uText(text: String, label: String) {
        val channels = withContext(Dispatchers.Default) { LocalM3uParser.parse(text) }
        if (channels.isEmpty()) {
            _localM3uStatus.value = it.casatv.tv.data.LocalM3uStatus(
                label = label,
                count = 0,
                error = "Nessun canale IPTV valido trovato (#EXTINF o PLS)",
            )
            _messages.emit("Nessun canale valido trovato nella lista M3U")
            return
        }
        offlineCache.update(channels = channels)
        _zapQueue.value = channels
        _tvInitialized.value = true
        _tvError.value = null
        _localM3uStatus.value = it.casatv.tv.data.LocalM3uStatus(label = label, count = channels.size, error = "")
        _messages.emit("Caricati ${channels.size} canali M3U in locale")
    }

    fun clearLocalM3u() {
        _localM3uStatus.value = it.casatv.tv.data.LocalM3uStatus()
        reloadTv()
    }

    fun checkForUpdates(silent: Boolean = true) {
        viewModelScope.launch {
            val currentVersion = it.casatv.tv.BuildConfig.VERSION_NAME
            val ghInfo = githubUpdater.checkForUpdate(force = !silent)
            val lanInfo = if (!_serverUrl.value.isNullOrBlank()) {
                try {
                    val info = api.versionInfo()
                    val isNewer = isNewerVersion(info.latest, currentVersion)
                    if ((info.updateAvailable || isNewer) && !info.downloadUrl.isNullOrBlank()) info else null
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            val best = when {
                ghInfo != null && lanInfo != null ->
                    if (isNewerVersion(lanInfo.latest, ghInfo.latest)) lanInfo else ghInfo
                ghInfo != null -> ghInfo
                lanInfo != null -> lanInfo
                else -> null
            }

            val existing = _updateInfo.value
            val chosen = when {
                best != null && existing != null && silent && isNewerVersion(existing.latest, best.latest) -> existing
                best != null -> best
                !silent -> null
                else -> existing
            }
            _updateInfo.value = chosen
            if (chosen == null && !silent) {
                _messages.emit("L'app è già aggiornata all'ultima versione disponibile ($currentVersion).")
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
                if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
                    githubUpdater.downloadApk(downloadUrl, apkFile) { progress ->
                        _updateProgress.value = progress
                    }
                } else {
                    api.downloadFile(downloadUrl, apkFile) { progress ->
                        _updateProgress.value = progress
                    }
                }
                var inspected = inspectApk(apkFile)
                val isLanUrl = !downloadUrl.contains("github.com", ignoreCase = true)
                if ((inspected == null || inspected.first <= BuildConfig.VERSION_CODE) && isLanUrl) {
                    val ghFallback = githubUpdater.checkForUpdate(force = true)
                    val ghUrl = ghFallback?.downloadUrl
                    if (!ghUrl.isNullOrBlank()) {
                        if (apkFile.exists()) apkFile.delete()
                        _updateProgress.value = 0f
                        githubUpdater.downloadApk(ghUrl, apkFile) { progress ->
                            _updateProgress.value = progress
                        }
                        inspected = inspectApk(apkFile)
                    }
                }
                if (inspected == null || inspected.first <= BuildConfig.VERSION_CODE) {
                    if (apkFile.exists()) apkFile.delete()
                    _updateDownloading.value = false
                    val foundLabel = inspected?.second ?: "?"
                    _updateError.value = "APK scaricato obsoleto o non valido ($foundLabel <= ${BuildConfig.VERSION_NAME})"
                    return@launch
                }
                _updateDownloading.value = false
                launchApkInstaller(apkFile)
            } catch (e: Exception) {
                _updateDownloading.value = false
                _updateError.value = e.message ?: "Errore durante il download dell'aggiornamento"
            }
        }
    }

    private fun inspectApk(apkFile: File): Pair<Long, String>? {
        val context = getApplication<Application>()
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
        }.getOrNull() ?: return null
        if (info.packageName != context.packageName) return null
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        val versionName = info.versionName.orEmpty().ifBlank { versionCode.toString() }
        return versionCode to versionName
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
        private const val KEY_LIVE_SAFETY_BUFFER = "live_safety_buffer_enabled"
        private const val KEY_MIX_INCLUDE_AUDIO_ONLY = "mix_include_audio_only"
        private const val KEY_SHOW_REMOTE_HINTS_BANNER = "show_remote_hints_banner"
        private const val KEY_THEME_MODE = "theme_mode"

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
