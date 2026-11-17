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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MobileUiState(
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
    val activeChannel: MobileChannel? = null,
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
            darkTheme = prefs.getBoolean(KEY_DARK_THEME, true),
            autoConnectWifiServer = prefs.getBoolean(KEY_AUTO_SERVER, true),
            autoSaveServerM3u = prefs.getBoolean(KEY_AUTO_SAVE_SERVER_M3U, true),
            selectedCountry = prefs.getString(KEY_SELECTED_COUNTRY, null),
            updateFrequency = updater.getFrequency(),
        ),
    )
    val state: StateFlow<MobileUiState> = _state.asStateFlow()

    private var localChannelsCache: List<MobileChannel> = emptyList()
    private var serverChannelsCache: List<MobileChannel> = emptyList()
    private var discoveryJob: Job? = null

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

        val facets = java.util.Locale.getISOCountries()
            .map { code ->
                val (name, flag) = M3uParser.countryMeta(code)
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
            rebuildChannelList()
        }
    }

    fun importM3uUrl(name: String, url: String) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, errorMessage = null, statusMessage = null) }
            repo.importFromUrl(name, url)
                .onSuccess { pl ->
                    _state.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = "Lista '${pl.name}' caricata (${pl.channelCount} canali)",
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
                    _state.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = "File '${pl.name}' importato (${pl.channelCount} canali)",
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
                    _state.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = "Aggiornata '${pl.name}' (${pl.channelCount} canali)",
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
                }
        }
    }

    fun deletePlaylist(playlist: LocalPlaylist) {
        viewModelScope.launch {
            repo.deletePlaylist(playlist.id)
            refreshLocalData()
        }
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
                                "L'app è già aggiornata all'ultima versione"
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
        _state.update { it.copy(activeChannel = channel) }
    }

    fun closePlayer() {
        _state.update { it.copy(activeChannel = null) }
    }

    fun clearMessages() {
        _state.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    companion object {
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_AUTO_SERVER = "auto_connect_wifi_server"
        private const val KEY_AUTO_SAVE_SERVER_M3U = "auto_save_server_m3u"
        private const val KEY_SELECTED_COUNTRY = "selected_country"
        private const val KEY_LAST_SERVER_URL = "last_server_url"
    }
}
