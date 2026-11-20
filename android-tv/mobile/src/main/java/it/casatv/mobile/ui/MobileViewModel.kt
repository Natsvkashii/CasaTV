package it.casatv.mobile.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.casatv.mobile.core.LocalDatabase
import it.casatv.mobile.core.M3uParser
import it.casatv.mobile.core.PlaylistRepository
import it.casatv.mobile.data.ConnectionMode
import it.casatv.mobile.data.CountryFacet
import it.casatv.mobile.data.LocalPlaylist
import it.casatv.mobile.data.MobileChannel
import it.casatv.mobile.network.CasaServerClient
import it.casatv.mobile.network.MdnsDiscovery
import it.casatv.mobile.update.AvailableUpdate
import it.casatv.mobile.update.GitHubUpdater
import it.casatv.mobile.update.UpdateFrequency
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class MobileUiState(
    val language: String = "en",
    val darkTheme: Boolean = true,
    val autoConnectWifiServer: Boolean = true,
    val autoSaveServerM3u: Boolean = true,
    val connectionMode: ConnectionMode = ConnectionMode.LOCAL_ONLY,
    val connectedServerUrl: String? = null,
    val connectedServerName: String? = null,
    val playlists: List<LocalPlaylist> = emptyList(),
    val allChannels: List<MobileChannel> = emptyList(),
    val countries: List<CountryFacet> = emptyList(),
    val selectedCountry: String? = null,
    val searchQuery: String = "",
    val mixChannels: List<MobileChannel> = emptyList(),
    val showMixRegionPicker: Boolean = false,
    val selectedMixRegions: Set<MixRegion> = setOf(MixRegion.ALL_WORLD),
    val activeChannel: MobileChannel? = null,
    val isMixMode: Boolean = false,
    val mixIndex: Int = 0,
    val mixTotal: Int = 0,
    val backgroundPlayEnabled: Boolean = true,
    val pipOnLeaveEnabled: Boolean = true,
    val liveSafetyBufferEnabled: Boolean = true,
    val mixIncludeAudioOnly: Boolean = false,
    val isImporting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val updateFrequency: UpdateFrequency = UpdateFrequency.WEEKLY,
    val isCheckingUpdates: Boolean = false,
    val availableUpdate: AvailableUpdate? = null,
    val updateProgress: Float? = null,
) {
    val filteredChannels: List<MobileChannel>
        get() {
            val q = searchQuery.trim().lowercase()
            return allChannels.filter { ch ->
                val matchesCountry = selectedCountry == null || ch.country == selectedCountry
                val matchesQuery = q.isEmpty() ||
                    ch.name.lowercase().contains(q) ||
                    ch.category.lowercase().contains(q) ||
                    (ch.number?.toString()?.contains(q) == true)
                matchesCountry && matchesQuery
            }
        }

    val favoriteChannels: List<MobileChannel>
        get() = allChannels.filter { it.favorite }
}

class MobileViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("casatv_mobile_prefs", Context.MODE_PRIVATE)
    private val db = LocalDatabase(application)
    private val repo = PlaylistRepository(application, db)
    private val discovery = MdnsDiscovery(application)
    private val serverClient = CasaServerClient()
    private val updater = GitHubUpdater(application)

    private val _state = MutableStateFlow(
        MobileUiState(
            language = resolveInitialLanguage(),
            darkTheme = prefs.getBoolean(KEY_DARK_THEME, true),
            autoConnectWifiServer = prefs.getBoolean(KEY_AUTO_SERVER, true),
            autoSaveServerM3u = prefs.getBoolean(KEY_AUTO_SAVE_SERVER_M3U, true),
            selectedCountry = prefs.getString(KEY_SELECTED_COUNTRY, null),
            backgroundPlayEnabled = prefs.getBoolean(KEY_BACKGROUND_PLAY, true),
            pipOnLeaveEnabled = prefs.getBoolean(KEY_PIP_ON_LEAVE, true),
            liveSafetyBufferEnabled = prefs.getBoolean(KEY_LIVE_SAFETY_BUFFER, true),
            mixIncludeAudioOnly = prefs.getBoolean(KEY_MIX_INCLUDE_AUDIO_ONLY, false),
            updateFrequency = updater.getFrequency(),
        ),
    )
    val state: StateFlow<MobileUiState> = _state.asStateFlow()

    private var localChannelsCache: List<MobileChannel> = emptyList()
    private var serverChannelsCache: List<MobileChannel> = emptyList()
    private var discoveryJob: Job? = null

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .callTimeout(4000, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    private val mixHistory = mutableListOf<MobileChannel>()
    private var mixCursor: Int = -1
    private val deadMixUrls = Collections.synchronizedSet(linkedSetOf<String>())
    private val runtimeAudioOnlyUrls = Collections.synchronizedSet(linkedSetOf<String>())
    @Volatile
    private var verifiedNextMixChannel: MobileChannel? = null
    private var mixPreloadJob: Job? = null

    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (_state.value.autoConnectWifiServer) {
                discoverAndSyncServer()
            }
        }

        override fun onLost(network: Network) {
            if (!discovery.isOnWifi()) {
                disconnectServerSilently()
            }
        }
    }

    init {
        refreshLocalData()
        registerWifiMonitor()
        if (_state.value.autoConnectWifiServer) {
            discoverAndSyncServer()
        }
        checkGitHubUpdates(force = false)
    }

    private fun registerWifiMonitor() {
        runCatching {
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager?.registerNetworkCallback(req, wifiCallback)
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { connectivityManager?.unregisterNetworkCallback(wifiCallback) }
    }

    fun refreshLocalData() {
        viewModelScope.launch {
            val playlists = repo.getPlaylists()
            localChannelsCache = repo.getLocalChannels()
            _state.update { it.copy(playlists = playlists) }
            rebuildChannelList()
        }
    }

    fun discoverAndSyncServer() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            if (!discovery.isOnWifi()) {
                disconnectServerSilently()
                return@launch
            }
            _state.update { it.copy(connectionMode = ConnectionMode.SEARCHING_WIFI) }
            val lastUrl = prefs.getString(KEY_LAST_SERVER_URL, null)
            val found = discovery.discoverFirst(lastKnownUrl = lastUrl)
            if (found == null) {
                disconnectServerSilently()
                return@launch
            }

            val baseUrl = found.baseUrl
            prefs.edit().putString(KEY_LAST_SERVER_URL, baseUrl).apply()
            val favs = repo.getFavoriteUrls()
            val fetched = runCatching { serverClient.fetchChannels(baseUrl, favs) }.getOrNull()
            if (fetched == null) {
                disconnectServerSilently()
                return@launch
            }

            serverChannelsCache = fetched
            if (_state.value.autoSaveServerM3u) {
                val m3uText = serverClient.fetchServerM3u(baseUrl)
                if (!m3uText.isNullOrBlank()) {
                    val srvId = "srv_${found.host.replace('.', '_')}"
                    repo.importFromM3uText(
                        id = srvId,
                        name = "Server ${found.name}",
                        source = "$baseUrl/api/playlist/export.m3u",
                        m3uText = m3uText,
                    )
                    _state.update { it.copy(playlists = repo.getPlaylists()) }
                    localChannelsCache = repo.getLocalChannels()
                }
            }
            _state.update {
                it.copy(
                    connectionMode = ConnectionMode.SERVER_CONNECTED,
                    connectedServerUrl = baseUrl,
                    connectedServerName = found.name,
                )
            }
            rebuildChannelList()
        }
    }

    private fun disconnectServerSilently() {
        serverChannelsCache = emptyList()
        _state.update {
            it.copy(
                connectionMode = ConnectionMode.LOCAL_ONLY,
                connectedServerUrl = null,
                connectedServerName = null,
            )
        }
        rebuildChannelList()
    }

    private fun rebuildChannelList() {
        val merged = linkedMapOf<String, MobileChannel>()
        // Local M3U channels take priority, then any additional channels from optional Wi-Fi server
        for (ch in localChannelsCache) {
            merged[ch.streamUrl] = ch
        }
        for (ch in serverChannelsCache) {
            val existing = merged[ch.streamUrl]
            if (existing == null) {
                merged[ch.streamUrl] = ch
            } else if (ch.favorite && !existing.favorite) {
                merged[ch.streamUrl] = existing.copy(favorite = true)
            }
        }
        val list = merged.values.toList()
        val countsByCountry = list
            .filter { !it.country.isNullOrBlank() }
            .groupingBy { if (it.country.equals("UK", true)) "GB" else it.country!!.uppercase() }
            .eachCount()

        val lang = _state.value.language
        val facets = java.util.Locale.getISOCountries()
            .map { code ->
                val (name, flag) = M3uParser.countryMeta(code, lang)
                CountryFacet(
                    code = code,
                    name = name,
                    flag = flag,
                    count = countsByCountry[code] ?: 0,
                )
            }
            .sortedBy { it.name.lowercase() }

        _state.update { current ->
            val validCountry = current.selectedCountry?.takeIf { sel -> facets.any { it.code == sel } }
            current.copy(
                allChannels = list,
                countries = facets,
                selectedCountry = validCountry,
            )
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun selectCountry(code: String?) {
        prefs.edit().apply {
            if (code == null) remove(KEY_SELECTED_COUNTRY) else putString(KEY_SELECTED_COUNTRY, code)
        }.apply()
        _state.update { it.copy(selectedCountry = code) }
    }

    fun toggleFavorite(channel: MobileChannel) {
        viewModelScope.launch {
            val next = !channel.favorite
            repo.setFavorite(channel.streamUrl, next)
            localChannelsCache = localChannelsCache.map {
                if (it.streamUrl == channel.streamUrl) it.copy(favorite = next) else it
            }
            serverChannelsCache = serverChannelsCache.map {
                if (it.streamUrl == channel.streamUrl) it.copy(favorite = next) else it
            }
            synchronized(mixHistory) {
                for (i in mixHistory.indices) {
                    if (mixHistory[i].streamUrl == channel.streamUrl) {
                        mixHistory[i] = mixHistory[i].copy(favorite = next)
                    }
                }
            }
            verifiedNextMixChannel?.let { cand ->
                if (cand.streamUrl == channel.streamUrl) {
                    verifiedNextMixChannel = cand.copy(favorite = next)
                }
            }
            _state.update { current ->
                if (current.activeChannel?.streamUrl == channel.streamUrl) {
                    current.copy(activeChannel = current.activeChannel.copy(favorite = next))
                } else {
                    current
                }
            }
            rebuildChannelList()
        }
    }

    fun importM3uUrl(name: String, url: String) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, errorMessage = null, statusMessage = null) }
            repo.importFromUrl(name, url)
                .onSuccess { pl ->
                    val isBroken = pl.status == "error" || pl.channelCount == 0
                    _state.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = if (isBroken) null else "Lista '${pl.name}' caricata (${pl.channelCount} canali)",
                            errorMessage = if (isBroken) "${pl.name}: non funziona (${pl.lastError})" else null,
                        )
                    }
                    refreshLocalData()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = err.message ?: "Errore caricamento URL M3U",
                        )
                    }
                }
        }
    }

    fun importM3uFile(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, errorMessage = null, statusMessage = null) }
            repo.importFromUri(uri)
                .onSuccess { pl ->
                    val isBroken = pl.status == "error" || pl.channelCount == 0
                    _state.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = if (isBroken) null else "File '${pl.name}' importato (${pl.channelCount} canali)",
                            errorMessage = if (isBroken) "${pl.name}: non funziona (${pl.lastError})" else null,
                        )
                    }
                    refreshLocalData()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = err.message ?: "Errore lettura file M3U",
                        )
                    }
                }
        }
    }

    fun checkPlaylist(playlist: LocalPlaylist) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, errorMessage = null, statusMessage = null) }
            repo.checkPlaylist(playlist)
                .onSuccess { pl ->
                    val isBroken = pl.status == "error" || pl.channelCount == 0
                    _state.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = if (isBroken) null else "${pl.name}: attiva (${pl.channelCount} canali)",
                            errorMessage = if (isBroken) "${pl.name}: non funziona (${pl.lastError})" else null,
                        )
                    }
                    refreshLocalData()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = err.message ?: "Errore verifica",
                        )
                    }
                }
        }
    }

    fun uploadM3uFileToServer(uri: Uri) {
        val serverUrl = _state.value.connectedServerUrl
        if (serverUrl.isNullOrBlank()) {
            _state.update { it.copy(errorMessage = "Nessun server CasaTV connesso") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, errorMessage = null, statusMessage = null) }
            repo.uploadFileToServer(serverUrl, uri)
                .onSuccess { msg ->
                    _state.update { it.copy(isImporting = false, statusMessage = msg) }
                    discoverAndSyncServer()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = err.message ?: "Errore upload sul server",
                        )
                    }
                    discoverAndSyncServer()
                }
        }
    }

    fun togglePlaylistEnabled(playlist: LocalPlaylist, enabled: Boolean) {
        viewModelScope.launch {
            repo.togglePlaylist(playlist.id, enabled)
            refreshLocalData()
        }
    }

    fun refreshPlaylist(playlist: LocalPlaylist) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, errorMessage = null, statusMessage = null) }
            repo.refreshUrlPlaylist(playlist)
                .onSuccess { pl ->
                    val isBroken = pl.status == "error" || pl.channelCount == 0
                    _state.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = if (isBroken) null else "Aggiornata '${pl.name}' (${pl.channelCount} canali)",
                            errorMessage = if (isBroken) "${pl.name}: non funziona (${pl.lastError})" else null,
                        )
                    }
                    refreshLocalData()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = err.message ?: "Aggiornamento fallito",
                        )
                    }
                    refreshLocalData()
                }
        }
    }

    fun deletePlaylist(playlist: LocalPlaylist) {
        viewModelScope.launch {
            repo.deletePlaylist(playlist.id)
            refreshLocalData()
        }
    }

    private fun resolveInitialLanguage(): String {
        val saved = prefs.getString(KEY_LANGUAGE, null)
        if (!saved.isNullOrBlank() && saved in SUPPORTED_LANGUAGE_CODES) return saved
        val detected = detectDeviceLanguage()
        prefs.edit().putString(KEY_LANGUAGE, detected).apply()
        return detected
    }

    fun setLanguage(code: String) {
        val clean = if (code in SUPPORTED_LANGUAGE_CODES) code else "en"
        prefs.edit().putString(KEY_LANGUAGE, clean).apply()
        _state.update { it.copy(language = clean) }
        rebuildChannelList()
    }

    fun setDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, dark).apply()
        _state.update { it.copy(darkTheme = dark) }
    }

    fun setAutoConnectWifiServer(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SERVER, enabled).apply()
        _state.update { it.copy(autoConnectWifiServer = enabled) }
        if (enabled) {
            discoverAndSyncServer()
        } else {
            discoveryJob?.cancel()
            disconnectServerSilently()
        }
    }

    fun setAutoSaveServerM3u(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SAVE_SERVER_M3U, enabled).apply()
        _state.update { it.copy(autoSaveServerM3u = enabled) }
    }

    fun setLiveSafetyBufferEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_SAFETY_BUFFER, enabled).apply()
        _state.update { it.copy(liveSafetyBufferEnabled = enabled) }
    }

    fun setUpdateFrequency(freq: UpdateFrequency) {
        updater.setFrequency(freq)
        _state.update { it.copy(updateFrequency = freq) }
    }

    fun checkGitHubUpdates(force: Boolean) {
        viewModelScope.launch {
            if (force) {
                _state.update { it.copy(isCheckingUpdates = true, statusMessage = null, errorMessage = null) }
            }
            updater.checkForUpdate(force = force)
                .onSuccess { upd ->
                    _state.update {
                        it.copy(
                            isCheckingUpdates = false,
                            availableUpdate = upd,
                            statusMessage = if (force && upd == null) {
                                MobileStrings(it.language).alreadyUpToDate
                            } else {
                                it.statusMessage
                            },
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isCheckingUpdates = false,
                            errorMessage = if (force) "Errore controllo aggiornamenti: ${err.message}" else it.errorMessage,
                        )
                    }
                }
        }
    }

    fun installGitHubUpdate() {
        val upd = _state.value.availableUpdate ?: return
        if (_state.value.updateProgress != null) return
        viewModelScope.launch {
            _state.update { it.copy(updateProgress = 0f) }
            updater.downloadAndInstall(upd.downloadUrl) { p ->
                _state.update { it.copy(updateProgress = p) }
            }.onFailure { err ->
                _state.update { it.copy(errorMessage = "Errore download aggiornamento: ${err.message}") }
            }
            _state.update { it.copy(updateProgress = null) }
        }
    }

    fun dismissUpdate() {
        _state.update { it.copy(availableUpdate = null) }
    }

    fun openChannel(channel: MobileChannel) {
        resetMixSession(resetRegions = true)
        _state.update {
            it.copy(
                activeChannel = channel,
                isMixMode = false,
                mixIndex = 0,
                mixTotal = 0,
            )
        }
    }

    fun closePlayer() {
        resetMixSession(resetRegions = true)
        _state.update {
            it.copy(
                activeChannel = null,
                isMixMode = false,
                mixIndex = 0,
                mixTotal = 0,
            )
        }
    }

    fun clearMessages() {
        _state.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    fun setBackgroundPlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_PLAY, enabled).apply()
        _state.update { it.copy(backgroundPlayEnabled = enabled) }
    }

    fun setPipOnLeaveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PIP_ON_LEAVE, enabled).apply()
        _state.update { it.copy(pipOnLeaveEnabled = enabled) }
    }

    fun setMixIncludeAudioOnly(enabled: Boolean) {
        if (_state.value.mixIncludeAudioOnly == enabled) return
        prefs.edit().putBoolean(KEY_MIX_INCLUDE_AUDIO_ONLY, enabled).apply()
        cachedMixSourceRef = null
        cachedMixIncludeAudioOnly = null
        mixPreloadJob?.cancel()
        mixPreloadJob = null
        verifiedNextMixChannel = null
        _state.update { it.copy(mixIncludeAudioOnly = enabled) }
        if (_state.value.isMixMode) {
            preverifyNextMixChannel()
        }
    }

    private fun resetMixSession(resetRegions: Boolean = true) {
        mixPreloadJob?.cancel()
        mixPreloadJob = null
        verifiedNextMixChannel = null
        synchronized(mixHistory) {
            mixHistory.clear()
            mixCursor = -1
        }
        deadMixUrls.clear()
        if (resetRegions) {
            _state.update {
                it.copy(
                    selectedMixRegions = setOf(MixRegion.ALL_WORLD),
                    mixIndex = 0,
                    mixTotal = 0,
                )
            }
        }
    }

    fun openMixRegionPicker() {
        if (!_state.value.isMixMode) {
            resetMixSession(resetRegions = true)
        }
        _state.update { it.copy(showMixRegionPicker = true) }
    }

    fun dismissMixRegionPicker() {
        _state.update { it.copy(showMixRegionPicker = false) }
        if (!_state.value.isMixMode) {
            resetMixSession(resetRegions = true)
        }
    }

    fun toggleMixRegion(region: MixRegion) {
        val current = _state.value.selectedMixRegions
        val next = if (region == MixRegion.ALL_WORLD) {
            setOf(MixRegion.ALL_WORLD)
        } else {
            val withoutWorld = current - MixRegion.ALL_WORLD
            val toggled = if (region in withoutWorld) withoutWorld - region else withoutWorld + region
            toggled.ifEmpty { setOf(MixRegion.ALL_WORLD) }
        }
        verifiedNextMixChannel?.let { cand ->
            if (!matchesMixRegions(cand.country, next)) {
                mixPreloadJob?.cancel()
                mixPreloadJob = null
                verifiedNextMixChannel = null
            }
        }
        _state.update { it.copy(selectedMixRegions = next) }
    }

    fun startMixFromSelectedRegions() {
        val regions = _state.value.selectedMixRegions
        mixPreloadJob?.cancel()
        mixPreloadJob = null
        verifiedNextMixChannel?.let { cand ->
            if (!matchesMixRegions(cand.country, regions)) {
                verifiedNextMixChannel = null
            }
        }
        synchronized(mixHistory) {
            mixHistory.clear()
            mixCursor = -1
        }
        deadMixUrls.clear()
        _state.update { it.copy(showMixRegionPicker = false) }
        playRandomMixChannel()
    }

    fun openOrRefreshMix() {
        openMixRegionPicker()
    }

    @Volatile
    private var cachedMixSourceRef: List<MobileChannel>? = null
    @Volatile
    private var cachedMixIncludeAudioOnly: Boolean? = null
    @Volatile
    private var cachedMixHttpAll: List<MobileChannel> = emptyList()
    @Volatile
    private var cachedMixBuckets: Map<MixRegion, List<MobileChannel>> = emptyMap()

    private fun isMixChannelExcludedAsAudioOnly(ch: MobileChannel): Boolean =
        !_state.value.mixIncludeAudioOnly && (ch.streamUrl in runtimeAudioOnlyUrls || isLikelyAudioOnlyChannel(ch))

    private fun ensureMixBucketsIndexed(channels: List<MobileChannel>) {
        val includeAudio = _state.value.mixIncludeAudioOnly
        if (channels === cachedMixSourceRef && includeAudio == cachedMixIncludeAudioOnly) return
        val httpAll = ArrayList<MobileChannel>(channels.size)
        val buckets = java.util.EnumMap<MixRegion, MutableList<MobileChannel>>(MixRegion::class.java)
        channels.forEach { ch ->
            if (!isHttpUrl(ch.streamUrl)) return@forEach
            if (!includeAudio && isLikelyAudioOnlyChannel(ch)) return@forEach
            httpAll.add(ch)
            resolveMixRegion(ch.country)?.let { region ->
                buckets.getOrPut(region) { ArrayList() }.add(ch)
            }
        }
        cachedMixHttpAll = httpAll
        cachedMixBuckets = buckets
        cachedMixSourceRef = channels
        cachedMixIncludeAudioOnly = includeAudio
    }

    private fun filteredMixPool(channels: List<MobileChannel>): List<MobileChannel> {
        ensureMixBucketsIndexed(channels)
        val regions = _state.value.selectedMixRegions
        if (regions.isEmpty() || MixRegion.ALL_WORLD in regions) return cachedMixHttpAll
        val regional = if (regions.size == 1) {
            cachedMixBuckets[regions.first()].orEmpty()
        } else {
            ArrayList<MobileChannel>().apply {
                regions.forEach { region -> cachedMixBuckets[region]?.let(::addAll) }
            }
        }
        return regional.ifEmpty { cachedMixHttpAll }
    }

    fun playRandomMixChannel() {
        val pool = filteredMixPool(_state.value.allChannels)
        if (pool.isEmpty()) return
        val nextChannel = consumeVerifiedNextMixChannel() ?: pickRandomUnvisitedMixChannel(pool)
            ?: return
        val pair = synchronized(mixHistory) {
            mixHistory.add(nextChannel)
            mixCursor = mixHistory.lastIndex
            mixCursor to mixHistory.size
        }
        _state.update {
            it.copy(
                activeChannel = nextChannel,
                isMixMode = true,
                mixIndex = pair.first,
                mixTotal = pair.second,
                showMixRegionPicker = false,
            )
        }
        preverifyNextMixChannel()
    }

    fun previousChannel() {
        val currentState = _state.value
        if (!currentState.isMixMode) {
            val list = currentState.filteredChannels
            val active = currentState.activeChannel ?: return
            if (list.isEmpty()) return
            val idx = list.indexOfFirst { it.streamUrl == active.streamUrl }.takeIf { it >= 0 } ?: 0
            val prevIdx = ((idx - 1) % list.size + list.size) % list.size
            _state.update { it.copy(activeChannel = list[prevIdx]) }
            return
        }

        val triple = synchronized(mixHistory) {
            if (mixCursor <= 0 || mixHistory.isEmpty()) {
                null
            } else {
                mixCursor -= 1
                Triple(mixHistory[mixCursor], mixCursor, mixHistory.size)
            }
        } ?: return

        _state.update {
            it.copy(
                activeChannel = triple.first,
                isMixMode = true,
                mixIndex = triple.second,
                mixTotal = triple.third,
            )
        }
        if (verifiedNextMixChannel == null) {
            preverifyNextMixChannel()
        }
    }

    fun nextChannel() {
        val currentState = _state.value
        if (!currentState.isMixMode) {
            val list = currentState.filteredChannels
            val active = currentState.activeChannel ?: return
            if (list.isEmpty()) return
            val idx = list.indexOfFirst { it.streamUrl == active.streamUrl }.takeIf { it >= 0 } ?: 0
            val nextIdx = (idx + 1) % list.size
            _state.update { it.copy(activeChannel = list[nextIdx]) }
            return
        }

        val existingTriple = synchronized(mixHistory) {
            if (mixCursor >= 0 && mixCursor < mixHistory.lastIndex) {
                mixCursor += 1
                Triple(mixHistory[mixCursor], mixCursor, mixHistory.size)
            } else {
                null
            }
        }
        if (existingTriple != null) {
            _state.update {
                it.copy(
                    activeChannel = existingTriple.first,
                    isMixMode = true,
                    mixIndex = existingTriple.second,
                    mixTotal = existingTriple.third,
                )
            }
            if (verifiedNextMixChannel == null) {
                preverifyNextMixChannel()
            }
            return
        }

        val pool = filteredMixPool(currentState.allChannels)
        val nextChannel = consumeVerifiedNextMixChannel() ?: pickRandomUnvisitedMixChannel(pool)
            ?: return
        val pair = synchronized(mixHistory) {
            mixHistory.add(nextChannel)
            mixCursor = mixHistory.lastIndex
            mixCursor to mixHistory.size
        }
        _state.update {
            it.copy(
                activeChannel = nextChannel,
                isMixMode = true,
                mixIndex = pair.first,
                mixTotal = pair.second,
            )
        }
        preverifyNextMixChannel()
    }

    private fun isHttpUrl(raw: String): Boolean {
        val clean = raw.trim().lowercase()
        return clean.startsWith("http://") || clean.startsWith("https://")
    }

    private fun consumeVerifiedNextMixChannel(): MobileChannel? {
        mixPreloadJob?.cancel()
        mixPreloadJob = null
        val candidate = verifiedNextMixChannel
        verifiedNextMixChannel = null
        return candidate?.takeIf {
            matchesMixRegions(it.country, _state.value.selectedMixRegions) && !isMixChannelExcludedAsAudioOnly(it)
        }
    }

    private fun pickRandomUnvisitedMixChannel(pool: List<MobileChannel>): MobileChannel? {
        if (pool.isEmpty()) return null
        val currentUrl = _state.value.activeChannel?.streamUrl
        val visitedUrls = synchronized(mixHistory) { mixHistory.mapTo(hashSetOf()) { it.streamUrl } }
        val eligiblePool = if (_state.value.mixIncludeAudioOnly) {
            pool
        } else {
            pool.filter { !isMixChannelExcludedAsAudioOnly(it) }.ifEmpty { pool }
        }
        val fresh = eligiblePool.filter { ch ->
            ch.streamUrl !in visitedUrls && ch.streamUrl !in deadMixUrls && ch.streamUrl != currentUrl
        }
        if (fresh.isNotEmpty()) return fresh.random()
        val unvisited = eligiblePool.filter { it.streamUrl !in visitedUrls && it.streamUrl != currentUrl }
        if (unvisited.isNotEmpty()) return unvisited.random()
        val others = eligiblePool.filter { it.streamUrl != currentUrl }
        return (if (others.isNotEmpty()) others else eligiblePool).randomOrNull()
    }

    fun skipAudioOnlyMixChannel(streamUrl: String): Boolean {
        runtimeAudioOnlyUrls.add(streamUrl)
        verifiedNextMixChannel?.let { cand ->
            if (cand.streamUrl in runtimeAudioOnlyUrls) {
                mixPreloadJob?.cancel()
                mixPreloadJob = null
                verifiedNextMixChannel = null
            }
        }
        val currentState = _state.value
        if (!currentState.isMixMode || currentState.mixIncludeAudioOnly || currentState.activeChannel?.streamUrl != streamUrl) {
            return false
        }
        val pool = filteredMixPool(currentState.allChannels)
            .filter { !isMixChannelExcludedAsAudioOnly(it) && it.streamUrl != streamUrl }
        val nextChannel = consumeVerifiedNextMixChannel()?.takeIf { it.streamUrl != streamUrl }
            ?: pickRandomUnvisitedMixChannel(pool)?.takeIf { it.streamUrl != streamUrl }
            ?: return false
        val pair = synchronized(mixHistory) {
            if (mixHistory.lastOrNull()?.streamUrl == streamUrl) {
                mixHistory.removeAt(mixHistory.lastIndex)
            }
            mixHistory.add(nextChannel)
            mixCursor = mixHistory.lastIndex
            mixCursor to mixHistory.size
        }
        _state.update {
            it.copy(
                activeChannel = nextChannel,
                isMixMode = true,
                mixIndex = pair.first,
                mixTotal = pair.second,
            )
        }
        preverifyNextMixChannel()
        return true
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

    private fun preverifyNextMixChannel() {
        if (verifiedNextMixChannel != null) return
        mixPreloadJob?.cancel()
        mixPreloadJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && verifiedNextMixChannel == null) {
                val pool = filteredMixPool(_state.value.allChannels)
                if (pool.isEmpty()) break
                val currentUrl = _state.value.activeChannel?.streamUrl
                val visitedUrls = synchronized(mixHistory) { mixHistory.mapTo(hashSetOf()) { it.streamUrl } }
                val eligiblePool = if (_state.value.mixIncludeAudioOnly) {
                    pool
                } else {
                    pool.filter { !isMixChannelExcludedAsAudioOnly(it) }.ifEmpty { pool }
                }
                var candidates = eligiblePool.filter { ch ->
                    ch.streamUrl !in visitedUrls && ch.streamUrl !in deadMixUrls && ch.streamUrl != currentUrl
                }
                if (candidates.isEmpty()) {
                    deadMixUrls.clear()
                    candidates = eligiblePool.filter { it.streamUrl !in visitedUrls && it.streamUrl != currentUrl }
                }
                if (candidates.isEmpty()) {
                    candidates = eligiblePool.filter { it.streamUrl != currentUrl }.ifEmpty { eligiblePool }
                }
                val batch = pickRandomCandidates(candidates, 3)
                if (batch.isEmpty()) break

                val winner = coroutineScope {
                    val found = CompletableDeferred<MobileChannel>()
                    val workers = batch.map { candidate ->
                        launch {
                            val ok = isStreamAvailable(candidate.streamUrl)
                            if (!isActive) return@launch
                            if (ok) {
                                found.complete(candidate)
                            } else {
                                deadMixUrls.add(candidate.streamUrl)
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

    private data class ProbeSample(
        val finalUrl: HttpUrl,
        val contentType: String,
        val bytes: ByteArray,
    )

    private fun fetchProbeSample(url: String, maxBytes: Int, job: Job?): ProbeSample? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", EXO_USER_AGENT)
            .get()
            .build()
        val call = probeClient.newCall(req)
        val handle = job?.invokeOnCompletion { call.cancel() }
        return try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) return null
                val stream = resp.body?.byteStream() ?: return null
                val buffer = ByteArray(maxBytes)
                var total = 0
                while (total < maxBytes) {
                    val read = stream.read(buffer, total, maxBytes - total)
                    if (read <= 0) break
                    total += read
                }
                if (total <= 0) return null
                ProbeSample(
                    finalUrl = resp.request.url,
                    contentType = resp.header("Content-Type").orEmpty(),
                    bytes = buffer.copyOf(total),
                )
            }
        } finally {
            handle?.dispose()
        }
    }

    private suspend fun isStreamAvailable(streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        val clean = streamUrl.trim()
        if (!isHttpUrl(clean)) return@withContext false
        val job = kotlin.coroutines.coroutineContext[Job]
        try {
            val sample = fetchProbeSample(clean, 4096, job) ?: return@withContext false
            val text = sample.bytes.toString(Charsets.UTF_8)
            val isDash = clean.contains(".mpd", ignoreCase = true) ||
                sample.finalUrl.toString().contains(".mpd", ignoreCase = true) ||
                sample.contentType.contains("dash+xml", ignoreCase = true) ||
                text.contains("<MPD", ignoreCase = true)

            if (isDash) {
                return@withContext text.contains("<MPD", ignoreCase = true)
            }

            val isHls = clean.contains(".m3u8", ignoreCase = true) ||
                sample.finalUrl.toString().contains(".m3u8", ignoreCase = true) ||
                sample.contentType.contains("mpegurl", ignoreCase = true) ||
                text.trimStart().startsWith("#EXTM3U")

            if (!isHls) {
                return@withContext !sample.contentType.contains("text/html", ignoreCase = true) &&
                    sample.bytes.size >= 188
            }

            if (!text.contains("#EXTM3U")) return@withContext false
            val firstEntry = text.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: return@withContext true

            val childUrl = sample.finalUrl.resolve(firstEntry) ?: return@withContext false
            val childSample = fetchProbeSample(childUrl.toString(), 1024, job) ?: return@withContext false
            val childIsM3u8 = childUrl.toString().contains(".m3u8", ignoreCase = true) ||
                childSample.contentType.contains("mpegurl", ignoreCase = true)
            if (childIsM3u8) {
                childSample.bytes.toString(Charsets.UTF_8).contains("#EXTM3U")
            } else {
                !childSample.contentType.contains("text/html", ignoreCase = true)
            }
        } catch (err: CancellationException) {
            throw err
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_AUTO_SERVER = "auto_connect_wifi_server"
        private const val KEY_AUTO_SAVE_SERVER_M3U = "auto_save_server_m3u"
        private const val KEY_SELECTED_COUNTRY = "selected_country"
        private const val KEY_LAST_SERVER_URL = "last_server_url"
        private const val KEY_BACKGROUND_PLAY = "background_play_enabled"
        private const val KEY_PIP_ON_LEAVE = "pip_on_leave_enabled"
        private const val KEY_LIVE_SAFETY_BUFFER = "live_safety_buffer_enabled"
        private const val KEY_MIX_INCLUDE_AUDIO_ONLY = "mix_include_audio_only"
        private const val EXO_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
