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
import it.casatv.mobile.data.MobileStreamSource
import it.casatv.mobile.data.MobileVodCategoryDto
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
import kotlinx.coroutines.delay
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
    val selectedMixLanguages: Set<String> = emptySet(),
    val selectedMixGenres: Set<String> = emptySet(),
    val mixPickerStats: MobileMixPickerStats = MobileMixPickerStats(),
    val activeChannel: MobileChannel? = null,
    val playerSources: List<MobileStreamSource> = emptyList(),
    val activeSourceIndex: Int = 0,
    val serverLiveChannels: Int? = null,
    val serverMovies: Int? = null,
    val serverSeries: Int? = null,
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
    val filteredChannels: List<MobileChannel> = emptyList(),
    val favoriteChannels: List<MobileChannel> = emptyList(),
    val vodMode: Boolean = false,
    val vodType: String = "vod",
    val vodCategory: String? = null,
    val vodGenre: String? = null,
    val vodPlatform: String? = null,
    val vodLanguage: String? = null,
    val vodItems: List<MobileChannel> = emptyList(),
    val vodContinue: List<MobileChannel> = emptyList(),
    val vodGenres: List<MobileVodCategoryDto> = emptyList(),
    val vodPlatforms: List<MobileVodCategoryDto> = emptyList(),
    val vodLanguages: List<it.casatv.mobile.data.MobileVodLanguageDto> = emptyList(),
    val vodCategories: List<MobileVodCategoryDto> = emptyList(),
    val vodTotal: Int = 0,
    val vodLoading: Boolean = false,
    val activeSeriesDetail: it.casatv.mobile.data.MobileSeriesDetailDto? = null,
    val activeSeriesSeasons: Map<Int, List<MobileChannel>> = emptyMap(),
    val nextEpisodeChannel: MobileChannel? = null,
    val recordings: List<it.casatv.mobile.data.MobileRecordingDto> = emptyList(),
    val recordingsLoading: Boolean = false,
    val startupPreloadReady: Boolean = false,
)

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
    private var filterJob: Job? = null
    private var vodJob: Job? = null
    private val vodLoadSeq = java.util.concurrent.atomic.AtomicLong(0L)
    private var wifiDebounceJob: Job? = null
    @Volatile
    private var lastServerM3uSyncAtMs: Long = 0L

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
            wifiDebounceJob?.cancel()
            wifiDebounceJob = viewModelScope.launch {
                delay(4_500L)
                if (_state.value.autoConnectWifiServer) {
                    discoverAndSyncServer()
                }
            }
        }

        override fun onLost(network: Network) {
            wifiDebounceJob?.cancel()
            wifiDebounceJob = viewModelScope.launch {
                delay(4_500L)
                if (!discovery.isOnWifi()) {
                    discoverAndSyncServer()
                }
            }
        }
    }

    init {
        refreshLocalData()
        registerWifiMonitor()
        if (_state.value.autoConnectWifiServer) {
            discoverAndSyncServer()
        } else {
            _state.update { it.copy(startupPreloadReady = true) }
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
        runCatching { db.close() }
    }

    fun refreshLocalData() {
        viewModelScope.launch {
            val playlists = repo.getPlaylists()
            localChannelsCache = repo.getLocalChannels()
            _state.update { it.copy(playlists = playlists) }
            rebuildChannelList()
        }
    }

    fun setVodMode(enabled: Boolean) {
        if (_state.value.vodMode == enabled) return
        _state.update { it.copy(vodMode = enabled) }
        if (enabled && _state.value.vodItems.isEmpty()) {
            loadVodCatalog(reset = true)
        }
    }

    fun setVodType(type: String) {
        if (_state.value.vodType == type) return
        _state.update { it.copy(vodType = type, vodCategory = null, vodGenre = null, vodPlatform = null) }
        loadVodCatalog(reset = true)
    }

    fun setVodCategory(cat: String?) {
        val clean = cat?.takeIf { it.isNotBlank() }
        if (_state.value.vodCategory == clean) return
        _state.update { it.copy(vodCategory = clean) }
        loadVodCatalog(reset = true)
    }

    fun setVodGenre(genre: String?) {
        val clean = genre?.takeIf { it.isNotBlank() }
        _state.update { it.copy(vodGenre = clean, vodPlatform = null, vodCategory = null) }
        loadVodCatalog(reset = true)
    }

    fun setVodPlatform(platform: String?) {
        val clean = platform?.takeIf { it.isNotBlank() }
        _state.update {
            it.copy(
                vodPlatform = clean,
                vodGenre = if (clean != null) null else it.vodGenre,
                vodCategory = null,
            )
        }
        loadVodCatalog(reset = true)
    }

    fun setVodLanguage(langCode: String?) {
        val clean = langCode?.takeIf { it.isNotBlank() }
        if (_state.value.vodLanguage == clean) return
        _state.update {
            it.copy(
                vodLanguage = clean,
                vodGenre = null,
                vodPlatform = null,
                vodCategory = null,
            )
        }
        loadVodCatalog(reset = true)
    }

    fun removeVodContinue(channelId: String) {
        val serverUrl = _state.value.connectedServerUrl
        _state.update { s ->
            s.copy(
                vodContinue = s.vodContinue.filterNot { it.id == channelId },
                vodItems = s.vodItems.map { if (it.id == channelId) it.copy(positionMs = 0L) else it },
            )
        }
        if (!serverUrl.isNullOrBlank()) {
            viewModelScope.launch {
                serverClient.removeVodContinue(serverUrl, channelId)
            }
        }
    }

    fun loadVodCatalog(reset: Boolean = true) {
        val snap = _state.value
        val serverUrl = snap.connectedServerUrl
        if (serverUrl.isNullOrBlank()) {
            // Local offline filter fallback
            val localVod = localChannelsCache.filter { ch ->
                val u = ch.streamUrl.lowercase()
                u.contains("/movie/") || u.contains("/series/") || u.endsWith(".mkv") || u.endsWith(".mp4")
            }
            _state.update { it.copy(vodItems = localVod, vodTotal = localVod.size, vodLoading = false) }
            return
        }
        if (!reset && snap.vodLoading) return
        val mySeq = if (reset) vodLoadSeq.incrementAndGet() else vodLoadSeq.get()
        vodJob?.cancel()
        if (reset) {
            _state.update { it.copy(vodItems = emptyList(), vodLoading = true) }
        } else {
            _state.update { it.copy(vodLoading = true) }
        }
        vodJob = viewModelScope.launch {
            try {
                val favs = repo.getFavoriteUrls()
                val cur = _state.value
                val offset = if (reset) 0 else cur.vodItems.size
                if (reset) {
                    launch {
                        val cont = serverClient.fetchVodContinue(serverUrl, favs)
                        if (mySeq == vodLoadSeq.get()) _state.update { it.copy(vodContinue = cont) }
                    }
                    launch {
                        val tax = serverClient.fetchVodTaxonomy(serverUrl, null, cur.vodType, cur.vodLanguage)
                        if (mySeq == vodLoadSeq.get()) {
                            val genres = tax.genres.ifEmpty { tax.categories }
                            val platforms = tax.platforms
                            val langs = if (tax.languages.isNotEmpty() || cur.vodLanguage == null) tax.languages else cur.vodLanguages
                            _state.update { it.copy(vodGenres = genres, vodPlatforms = platforms, vodLanguages = langs, vodCategories = genres) }
                        }
                    }
                }
                val (items, total) = serverClient.fetchVodPage(
                    baseUrl = serverUrl,
                    country = null,
                    category = cur.vodCategory,
                    genre = cur.vodGenre,
                    platform = cur.vodPlatform,
                    lang = cur.vodLanguage,
                    type = cur.vodType,
                    limit = 120,
                    offset = offset,
                    favoriteUrls = favs,
                )
                if (mySeq != vodLoadSeq.get()) return@launch
                val merged = if (reset) items else (cur.vodItems + items).distinctBy { it.id }
                _state.update { it.copy(vodItems = merged, vodTotal = total.coerceAtLeast(merged.size)) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                if (mySeq == vodLoadSeq.get()) {
                    _state.update { it.copy(vodLoading = false) }
                }
            }
        }
    }

    fun loadRecordings() {
        val srvUrl = _state.value.connectedServerUrl ?: return
        viewModelScope.launch {
            _state.update { it.copy(recordingsLoading = true) }
            val list = serverClient.fetchRecordings(srvUrl)
            _state.update { it.copy(recordings = list, recordingsLoading = false) }
        }
    }

    suspend fun startLiveRecording(ch: MobileChannel): it.casatv.mobile.data.MobileRecordingDto? {
        val srvUrl = _state.value.connectedServerUrl ?: return null
        val dto = serverClient.startRecording(
            baseUrl = srvUrl,
            channelId = ch.id,
            streamUrl = ch.streamUrl,
            channelName = ch.name,
            country = ch.country,
            logo = ch.logo,
        )
        if (dto != null) {
            loadRecordings()
            _state.update { it.copy(statusMessage = "Registrazione avviata · Salvata per 7 giorni") }
        }
        return dto
    }

    suspend fun stopLiveRecording(id: String): it.casatv.mobile.data.MobileRecordingDto? {
        val srvUrl = _state.value.connectedServerUrl ?: return null
        val dto = serverClient.stopRecording(srvUrl, id)
        loadRecordings()
        _state.update { it.copy(statusMessage = "Registrazione fermata e salvata per 7 giorni") }
        return dto
    }

    fun deleteRecording(id: String) {
        val srvUrl = _state.value.connectedServerUrl ?: return
        _state.update { s -> s.copy(recordings = s.recordings.filterNot { it.id == id }) }
        viewModelScope.launch {
            serverClient.deleteRecording(srvUrl, id)
        }
    }

    fun playRecording(rec: it.casatv.mobile.data.MobileRecordingDto) {
        val srvUrl = _state.value.connectedServerUrl?.trimEnd('/') ?: return
        val url = if (rec.playUrl.startsWith("http")) rec.playUrl else "$srvUrl${rec.playUrl.ifBlank { "/api/recordings/${rec.id}/master.m3u8" }}"
        val ch = MobileChannel(
            id = "rec_${rec.id}",
            name = rec.channelName.ifBlank { "Registrazione" },
            number = 0,
            country = rec.country,
            countryName = rec.country,
            logo = rec.logo?.let { if (it.startsWith("http")) it else "$srvUrl$it" },
            category = "Registrazioni",
            streamUrl = url,
            favorite = false,
            isFromServer = true,
            contentType = "movie",
            isVod = true,
            vodStreamUrl = url,
            durationMs = rec.recordedMs,
        )
        openChannel(ch)
    }

    fun downloadRecording(rec: it.casatv.mobile.data.MobileRecordingDto) {
        val srvUrl = _state.value.connectedServerUrl?.trimEnd('/') ?: return
        val url = if (rec.downloadUrl.startsWith("http")) rec.downloadUrl else "$srvUrl${rec.downloadUrl.ifBlank { "/api/recordings/${rec.id}/download" }}"
        runCatching {
            val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val safeName = rec.channelName.replace(Regex("""[^A-Za-z0-9._-]+"""), "_").trim('_').ifBlank { "CasaTV" }.take(36)
            val req = android.app.DownloadManager.Request(Uri.parse(url))
                .setTitle("CasaTV · ${rec.channelName}")
                .setDescription("Registrazione salvata (7 giorni)")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "CasaTV/CasaTV_${safeName}_${rec.id}.ts")
            dm.enqueue(req)
            _state.update { it.copy(statusMessage = "Download avviato in Downloads/CasaTV") }
        }.onFailure { err ->
            _state.update { it.copy(errorMessage = err.message ?: "Download fallito") }
        }
    }

    fun saveVodProgress(channelId: String, positionMs: Long, durationMs: Long) {
        val serverUrl = _state.value.connectedServerUrl ?: return
        viewModelScope.launch {
            serverClient.saveVodProgress(serverUrl, channelId, positionMs, durationMs)
        }
    }

    fun discoverAndSyncServer() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _state.update { it.copy(connectionMode = ConnectionMode.SEARCHING_WIFI) }
            val lastUrl = prefs.getString(KEY_LAST_SERVER_URL, null)
            val found = discovery.discoverFirst(lastKnownUrl = lastUrl)
            if (found == null) {
                disconnectServerSilently()
                _state.update { it.copy(startupPreloadReady = true) }
                return@launch
            }

            val baseUrl = found.baseUrl
            prefs.edit().putString(KEY_LAST_SERVER_URL, baseUrl).apply()
            val favs = repo.getFavoriteUrls()
            val fetched = runCatching { serverClient.fetchChannels(baseUrl, favs) }.getOrNull()
            if (fetched == null) {
                disconnectServerSilently()
                _state.update { it.copy(startupPreloadReady = true) }
                return@launch
            }
            val health = runCatching { serverClient.checkHealth(baseUrl) }.getOrNull()

            serverChannelsCache = fetched
            val nowSyncMs = System.currentTimeMillis()
            if (_state.value.autoSaveServerM3u && (nowSyncMs - lastServerM3uSyncAtMs >= 120_000L)) {
                val m3uText = serverClient.fetchServerM3u(baseUrl)
                if (!m3uText.isNullOrBlank()) {
                    val srvId = "srv_casatv_server"
                    repo.importFromM3uText(
                        id = srvId,
                        name = "Server ${found.name}",
                        source = "$baseUrl/api/playlist/export.m3u",
                        m3uText = m3uText,
                    )
                    lastServerM3uSyncAtMs = System.currentTimeMillis()
                    _state.update { it.copy(playlists = repo.getPlaylists()) }
                    localChannelsCache = repo.getLocalChannels()
                }
            }
            _state.update {
                it.copy(
                    connectionMode = ConnectionMode.SERVER_CONNECTED,
                    connectedServerUrl = baseUrl,
                    connectedServerName = found.name,
                    serverLiveChannels = health?.liveChannels,
                    serverMovies = health?.movies,
                    serverSeries = health?.series,
                    startupPreloadReady = true,
                )
            }
            rebuildChannelList()
            if (_state.value.vodMode) {
                loadVodCatalog(reset = true)
            }
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

    private fun normalizeMobileCountry(raw: String?): String? {
        val clean = raw?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        return if (clean == "UK") "GB" else clean
    }

    private fun computeFilteredAndFavorites(
        channels: List<MobileChannel>,
        selectedCountry: String?,
        query: String,
    ): Pair<List<MobileChannel>, List<MobileChannel>> {
        val targetCountry = normalizeMobileCountry(selectedCountry)
        val q = query.trim().lowercase()
        val filtered = if (targetCountry == null && q.isEmpty()) {
            channels
        } else {
            channels.filter { ch ->
                val matchesCountry = targetCountry == null || normalizeMobileCountry(ch.country) == targetCountry
                val matchesQuery = q.isEmpty() ||
                    ch.name.lowercase().contains(q) ||
                    ch.category.lowercase().contains(q) ||
                    (ch.number?.toString()?.contains(q) == true)
                matchesCountry && matchesQuery
            }
        }
        val favorites = channels.filter { it.favorite }
        return filtered to favorites
    }

    private fun scheduleFilterRecompute(debounceMs: Long = 0L) {
        filterJob?.cancel()
        val snap = _state.value
        val channels = snap.allChannels
        val selectedCountry = snap.selectedCountry
        val query = snap.searchQuery
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            if (debounceMs > 0L) delay(debounceMs)
            val (filtered, favorites) = computeFilteredAndFavorites(channels, selectedCountry, query)
            _state.update { it.copy(filteredChannels = filtered, favoriteChannels = favorites) }
        }
    }

    private fun rebuildChannelList() {
        filterJob?.cancel()
        val localSnap = localChannelsCache
        val serverSnap = serverChannelsCache
        val lang = _state.value.language
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val merged = linkedMapOf<String, MobileChannel>()
            val serverOnline = serverSnap.isNotEmpty()
            // User local M3U channels take priority; live server channels override stale srv_ M3U cache
            for (ch in localSnap) {
                if (serverOnline && ch.playlistId.startsWith("srv_")) continue
                merged[ch.streamUrl] = ch
            }
            for (ch in serverSnap) {
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
                .groupingBy { normalizeMobileCountry(it.country)!! }
                .eachCount()

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

            indexedMixSourceRef = null
            indexedMixEntries = emptyList()
            _state.update { current ->
                val activeValidCountry = current.selectedCountry?.takeIf { sel -> facets.any { it.code == sel } }
                val activeFilters = computeFilteredAndFavorites(list, activeValidCountry, current.searchQuery)
                current.copy(
                    allChannels = list,
                    countries = facets,
                    selectedCountry = activeValidCountry,
                    filteredChannels = activeFilters.first,
                    favoriteChannels = activeFilters.second,
                )
            }
            if (_state.value.showMixRegionPicker) {
                recomputeMixPickerStats()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        scheduleFilterRecompute(debounceMs = 150L)
    }

    fun selectCountry(code: String?) {
        prefs.edit().apply {
            if (code == null) remove(KEY_SELECTED_COUNTRY) else putString(KEY_SELECTED_COUNTRY, code)
        }.apply()
        _state.update { it.copy(selectedCountry = code) }
        scheduleFilterRecompute(debounceMs = 0L)
    }

    fun toggleFavorite(channel: MobileChannel) {
        viewModelScope.launch {
            val next = !channel.favorite
            repo.setFavorite(channel.streamUrl, next)
            val srvUrl = _state.value.connectedServerUrl
            if (!srvUrl.isNullOrBlank() && channel.isFromServer) {
                launch { runCatching { serverClient.setFavorite(srvUrl, channel.id, next) } }
            }
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

    fun openChannel(
        channel: MobileChannel,
        preloadedSources: List<MobileStreamSource> = emptyList(),
        initialSourceIndex: Int = -1,
    ) {
        resetMixSession(resetRegions = true)
        val chosenIdx = if (initialSourceIndex in preloadedSources.indices) initialSourceIndex else 0
        val effectiveChannel = if (initialSourceIndex in preloadedSources.indices) {
            val src = preloadedSources[initialSourceIndex]
            channel.copy(
                streamUrl = src.url,
                quality = src.quality.ifBlank { channel.quality },
            )
        } else {
            channel
        }
        _state.update {
            it.copy(
                activeChannel = effectiveChannel,
                playerSources = preloadedSources,
                activeSourceIndex = chosenIdx,
                activeSeriesDetail = null,
                isMixMode = false,
                mixIndex = 0,
                mixTotal = 0,
                nextEpisodeChannel = null,
            )
        }
        val srvUrl = _state.value.connectedServerUrl
        if (channel.contentType == "series" && !srvUrl.isNullOrBlank()) {
            viewModelScope.launch {
                val favs = repo.getFavoriteUrls()
                val nextEp = serverClient.fetchVodRelatedNext(srvUrl, channel.id, favs)
                if (_state.value.activeChannel?.id == channel.id) {
                    _state.update { it.copy(nextEpisodeChannel = nextEp) }
                }
            }
        } else if (preloadedSources.isEmpty() && channel.isFromServer && !channel.isVodContent && !srvUrl.isNullOrBlank()) {
            viewModelScope.launch {
                val srcs = serverClient.fetchChannelSources(srvUrl, channel.id)
                if (_state.value.activeChannel?.id == channel.id && srcs.isNotEmpty()) {
                    val idx = srcs.indexOfFirst { it.url == effectiveChannel.streamUrl }.takeIf { it >= 0 } ?: 0
                    _state.update { it.copy(playerSources = srcs, activeSourceIndex = idx) }
                }
            }
        }
    }

    suspend fun fetchChannelSourcesForMenu(channel: MobileChannel): List<MobileStreamSource> {
        val srvUrl = _state.value.connectedServerUrl ?: return emptyList()
        if (!channel.isFromServer || channel.isVodContent) return emptyList()
        return serverClient.fetchChannelSources(srvUrl, channel.id)
    }

    fun selectPlayerSource(index: Int) {
        val snap = _state.value
        val cur = snap.activeChannel ?: return
        val src = snap.playerSources.getOrNull(index) ?: return
        _state.update {
            it.copy(
                activeChannel = cur.copy(
                    streamUrl = src.url,
                    quality = src.quality.ifBlank { cur.quality },
                ),
                activeSourceIndex = index,
            )
        }
        reportChannelSource(cur.id, src.url, "manual_select")
    }

    fun reportChannelSource(channelId: String, url: String, event: String, latencyMs: Long = -1L) {
        val srvUrl = _state.value.connectedServerUrl ?: return
        viewModelScope.launch {
            serverClient.reportChannelSource(srvUrl, channelId, url, event, latencyMs)
        }
    }

    fun openVodOrSeries(channel: MobileChannel) {
        val srvUrl = _state.value.connectedServerUrl
        if (channel.contentType == "series" && !srvUrl.isNullOrBlank()) {
            viewModelScope.launch {
                val favs = repo.getFavoriteUrls()
                val res = serverClient.fetchVodSeriesDetail(srvUrl, channel.id, favs)
                if (res != null && res.second.values.any { it.isNotEmpty() }) {
                    _state.update {
                        it.copy(
                            activeSeriesDetail = res.first,
                            activeSeriesSeasons = res.second,
                        )
                    }
                } else {
                    openChannel(channel)
                }
            }
        } else {
            openChannel(channel)
        }
    }

    fun closeSeriesDetail() {
        _state.update { it.copy(activeSeriesDetail = null, activeSeriesSeasons = emptyMap()) }
    }

    fun closePlayer() {
        val wasVod = _state.value.activeChannel?.isVodContent == true
        resetMixSession(resetRegions = true)
        _state.update {
            it.copy(
                activeChannel = null,
                playerSources = emptyList(),
                activeSourceIndex = 0,
                isMixMode = false,
                mixIndex = 0,
                mixTotal = 0,
                nextEpisodeChannel = null,
            )
        }
        val srvUrl = _state.value.connectedServerUrl
        if (wasVod && !srvUrl.isNullOrBlank()) {
            viewModelScope.launch {
                delay(150L)
                val favs = repo.getFavoriteUrls()
                val cont = serverClient.fetchVodContinue(srvUrl, favs)
                _state.update { it.copy(vodContinue = cont) }
            }
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
                    selectedMixLanguages = emptySet(),
                    selectedMixGenres = emptySet(),
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
        recomputeMixPickerStats()
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
            if (!matchesMixRegions(cand.country, next) || !matchesActiveMixFilters(cand)) {
                mixPreloadJob?.cancel()
                mixPreloadJob = null
                verifiedNextMixChannel = null
            }
        }
        _state.update { it.copy(selectedMixRegions = next) }
        recomputeMixPickerStats()
    }

    fun toggleMixLanguage(code: String) {
        val current = _state.value.selectedMixLanguages
        val next = if (code in current) current - code else current + code
        _state.update { it.copy(selectedMixLanguages = next) }
        invalidateVerifiedMixAfterFilterChange()
    }

    fun toggleMixGenre(genreId: String) {
        val current = _state.value.selectedMixGenres
        val next = if (genreId in current) current - genreId else current + genreId
        _state.update { it.copy(selectedMixGenres = next) }
        invalidateVerifiedMixAfterFilterChange()
    }

    fun clearMixFilters() {
        if (_state.value.selectedMixLanguages.isEmpty() && _state.value.selectedMixGenres.isEmpty()) return
        _state.update {
            it.copy(
                selectedMixLanguages = emptySet(),
                selectedMixGenres = emptySet(),
            )
        }
        invalidateVerifiedMixAfterFilterChange()
    }

    private fun invalidateVerifiedMixAfterFilterChange() {
        recomputeMixPickerStats()
        verifiedNextMixChannel?.let { cand ->
            if (!matchesActiveMixFilters(cand)) {
                mixPreloadJob?.cancel()
                mixPreloadJob = null
                verifiedNextMixChannel = null
            }
        }
    }

    private fun recomputeMixPickerStats() {
        mixStatsJob?.cancel()
        val snap = _state.value
        val channels = snap.allChannels
        val includeAudio = snap.mixIncludeAudioOnly
        val regions = snap.selectedMixRegions
        val langMask = mobileLanguageCodesToMask(snap.selectedMixLanguages)
        val genreMask = mobileGenreIdsToMask(snap.selectedMixGenres)
        mixStatsJob = viewModelScope.launch(Dispatchers.Default) {
            var idx = indexedMixEntries
            if (channels !== indexedMixSourceRef) {
                idx = buildMobileMixIndex(channels)
                indexedMixEntries = idx
                indexedMixSourceRef = channels
            }
            val stats = computeMobileIndexedMixPickerStats(
                index = idx,
                includeAudioOnly = includeAudio,
                selectedRegions = regions,
                selectedLangMask = langMask,
                selectedGenreMask = genreMask,
            )
            _state.update { it.copy(mixPickerStats = stats) }
        }
    }

    private fun matchesActiveMixFilters(channel: MobileChannel, state: MobileUiState = _state.value): Boolean {
        if (!matchesMixRegions(channel.country, state.selectedMixRegions)) return false
        if (!matchesMobileMixLanguages(channel, state.selectedMixLanguages)) return false
        if (!matchesMobileMixGenres(channel, state.selectedMixGenres)) return false
        return true
    }

    fun startMixFromSelectedRegions() {
        mixPreloadJob?.cancel()
        mixPreloadJob = null
        verifiedNextMixChannel?.let { cand ->
            if (!matchesActiveMixFilters(cand)) {
                verifiedNextMixChannel = null
            }
        }
        synchronized(mixHistory) {
            mixHistory.clear()
            mixCursor = -1
        }
        deadMixUrls.clear()
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
    @Volatile
    private var indexedMixSourceRef: List<MobileChannel>? = null
    @Volatile
    private var indexedMixEntries: List<MobileIndexedMixChannel> = emptyList()
    private var mixStatsJob: Job? = null

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
        val currentState = _state.value
        val regions = currentState.selectedMixRegions
        val langMask = mobileLanguageCodesToMask(currentState.selectedMixLanguages)
        val genreMask = mobileGenreIdsToMask(currentState.selectedMixGenres)
        val hasFilters = langMask != 0L || genreMask != 0L
        val idx = indexedMixEntries
        if (channels === indexedMixSourceRef && idx.isNotEmpty()) {
            val allWorld = regions.isEmpty() || MixRegion.ALL_WORLD in regions
            val includeAudio = currentState.mixIncludeAudioOnly
            val out = ArrayList<MobileChannel>(idx.size / 4 + 16)
            for (i in idx.indices) {
                val e = idx[i]
                if (!includeAudio && e.isAudioOnly) continue
                if (!isHttpUrl(e.channel.streamUrl)) continue
                if (!allWorld && (e.region == null || e.region !in regions)) continue
                if (langMask != 0L && (e.langMask and langMask) == 0L) continue
                if (genreMask != 0L && (e.genreMask and genreMask) == 0L) continue
                out.add(e.channel)
            }
            if (out.isNotEmpty() || hasFilters || allWorld) return out
        }

        ensureMixBucketsIndexed(channels)
        val base = if (regions.isEmpty() || MixRegion.ALL_WORLD in regions) {
            cachedMixHttpAll
        } else {
            val regional = if (regions.size == 1) {
                cachedMixBuckets[regions.first()].orEmpty()
            } else {
                ArrayList<MobileChannel>().apply {
                    regions.forEach { region -> cachedMixBuckets[region]?.let(::addAll) }
                }
            }
            if (hasFilters) regional else regional.ifEmpty { cachedMixHttpAll }
        }
        if (!hasFilters) return base
        return base.filter { ch ->
            matchesMobileMixLanguages(ch, currentState.selectedMixLanguages) &&
                matchesMobileMixGenres(ch, currentState.selectedMixGenres)
        }
    }

    fun playRandomMixChannel() {
        val currentState = _state.value
        val verified = consumeVerifiedNextMixChannel()
        if (verified != null) {
            val pair = synchronized(mixHistory) {
                mixHistory.add(verified)
                mixCursor = mixHistory.lastIndex
                mixCursor to mixHistory.size
            }
            _state.update {
                it.copy(
                    activeChannel = verified,
                    isMixMode = true,
                    mixIndex = pair.first,
                    mixTotal = pair.second,
                    showMixRegionPicker = false,
                )
            }
            preverifyNextMixChannel()
            return
        }

        val serverUrl = currentState.connectedServerUrl
        if (!serverUrl.isNullOrBlank()) {
            viewModelScope.launch {
                val visitedIds = synchronized(mixHistory) { mixHistory.map { it.id } }
                val regs = currentState.selectedMixRegions.map { it.name }.filter { it != "ALL_WORLD" }.toSet()
                val readyList = withContext(Dispatchers.IO) {
                    serverClient.fetchMixReady(
                        baseUrl = serverUrl,
                        favoriteUrls = repo.getFavoriteUrls(),
                        regions = regs,
                        languages = currentState.selectedMixLanguages,
                        genres = currentState.selectedMixGenres,
                        excludeIds = visitedIds,
                        includeAudioOnly = currentState.mixIncludeAudioOnly,
                        limit = 6,
                    )
                }.filter { ch ->
                    ch.streamUrl !in deadMixUrls &&
                        (currentState.mixIncludeAudioOnly || ch.streamUrl !in runtimeAudioOnlyUrls)
                }
                val chosen = readyList.firstOrNull() ?: run {
                    val pool = filteredMixPool(_state.value.allChannels)
                    pickRandomUnvisitedMixChannel(pool)
                }
                if (chosen == null) {
                    if (currentState.selectedMixLanguages.isNotEmpty() || currentState.selectedMixGenres.isNotEmpty()) {
                        val msg = when {
                            currentState.language.startsWith("en") -> "No channels match the selected filters"
                            currentState.language.startsWith("es") -> "Ningún canal coincide con los filtros seleccionados"
                            else -> "Nessun canale corrisponde ai filtri selezionati"
                        }
                        _state.update { it.copy(statusMessage = msg) }
                    }
                    return@launch
                }
                readyList.getOrNull(1)?.let { verifiedNextMixChannel = it }
                val pair = synchronized(mixHistory) {
                    mixHistory.add(chosen)
                    mixCursor = mixHistory.lastIndex
                    mixCursor to mixHistory.size
                }
                _state.update {
                    it.copy(
                        activeChannel = chosen,
                        isMixMode = true,
                        mixIndex = pair.first,
                        mixTotal = pair.second,
                        showMixRegionPicker = false,
                    )
                }
                preverifyNextMixChannel()
            }
            return
        }

        val pool = filteredMixPool(currentState.allChannels)
        if (pool.isEmpty()) {
            if (currentState.selectedMixLanguages.isNotEmpty() || currentState.selectedMixGenres.isNotEmpty()) {
                val msg = when {
                    currentState.language.startsWith("en") -> "No channels match the selected filters"
                    currentState.language.startsWith("es") -> "Ningún canal coincide con los filtros seleccionados"
                    else -> "Nessun canale corrisponde ai filtri selezionati"
                }
                _state.update { it.copy(statusMessage = msg) }
            }
            return
        }
        val nextChannel = pickRandomUnvisitedMixChannel(pool) ?: return
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
            matchesActiveMixFilters(it) && !isMixChannelExcludedAsAudioOnly(it)
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
            val serverUrl = _state.value.connectedServerUrl
            if (!serverUrl.isNullOrBlank()) {
                val cur = _state.value
                val visitedIds = synchronized(mixHistory) { mixHistory.map { it.id } }
                val regs = cur.selectedMixRegions.map { it.name }.filter { it != "ALL_WORLD" }.toSet()
                val ready = serverClient.fetchMixReady(
                    baseUrl = serverUrl,
                    favoriteUrls = repo.getFavoriteUrls(),
                    regions = regs,
                    languages = cur.selectedMixLanguages,
                    genres = cur.selectedMixGenres,
                    excludeIds = visitedIds + listOfNotNull(cur.activeChannel?.id),
                    includeAudioOnly = cur.mixIncludeAudioOnly,
                    limit = 4,
                ).firstOrNull { ch ->
                    ch.streamUrl !in deadMixUrls &&
                        ch.streamUrl != cur.activeChannel?.streamUrl &&
                        (cur.mixIncludeAudioOnly || ch.streamUrl !in runtimeAudioOnlyUrls)
                }
                if (ready != null) {
                    verifiedNextMixChannel = ready
                    return@launch
                }
            }
            var rounds = 0
            while (isActive && verifiedNextMixChannel == null && rounds < 8) {
                rounds++
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
                delay(350L)
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
                val finalUrl = resp.request.url
                val ct = resp.header("Content-Type").orEmpty()
                if (total >= maxBytes) call.cancel()
                ProbeSample(
                    finalUrl = finalUrl,
                    contentType = ct,
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
