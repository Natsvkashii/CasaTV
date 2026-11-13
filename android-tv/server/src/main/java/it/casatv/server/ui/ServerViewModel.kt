package it.casatv.server.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.casatv.server.core.IptvChannel
import it.casatv.server.core.PlaylistEntry
import it.casatv.server.core.PlaylistError
import it.casatv.server.core.PlaylistException
import it.casatv.server.core.PlaylistManager
import it.casatv.server.core.PlaylistStatus
import it.casatv.server.core.Prefs
import it.casatv.server.core.ProfileRow
import it.casatv.server.core.PROFILE_AVATARS
import it.casatv.server.core.ServerDatabase
import it.casatv.server.core.ServerStatus
import it.casatv.server.core.ServerRuntime
import it.casatv.server.core.textOf
import it.casatv.server.service.ServerService
import it.casatv.server.update.AvailableUpdate
import it.casatv.server.update.GitHubUpdater
import it.casatv.server.update.UpdateFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val db = ServerDatabase.get(context)
    private val playlist = PlaylistManager(context)
    private val updater = GitHubUpdater(context)

    private val _language = MutableStateFlow(Prefs.language(context))
    val language: StateFlow<String?> = _language.asStateFlow()

    val status: StateFlow<ServerStatus> = ServerRuntime.status

    private val _profiles = MutableStateFlow<List<ProfileRow>>(emptyList())
    val profiles: StateFlow<List<ProfileRow>> = _profiles.asStateFlow()

    private val _clientCount = MutableStateFlow(0)
    val clientCount: StateFlow<Int> = _clientCount.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _serverName = MutableStateFlow(Prefs.serverName(context))
    val serverName: StateFlow<String> = _serverName.asStateFlow()

    private val _port = MutableStateFlow(Prefs.port(context))
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _autoStart = MutableStateFlow(Prefs.autoStart(context))
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private val _playlistStatus = MutableStateFlow(PlaylistStatus())
    val playlistStatus: StateFlow<PlaylistStatus> = _playlistStatus.asStateFlow()

    private val _playlistsList = MutableStateFlow<List<PlaylistEntry>>(emptyList())
    val playlistsList: StateFlow<List<PlaylistEntry>> = _playlistsList.asStateFlow()

    private val _playlistBusy = MutableStateFlow(false)
    val playlistBusy: StateFlow<Boolean> = _playlistBusy.asStateFlow()

    private val _allChannels = MutableStateFlow<List<IptvChannel>>(emptyList())
    val allChannels: StateFlow<List<IptvChannel>> = _allChannels.asStateFlow()

    private val _updateFrequency = MutableStateFlow(updater.getFrequency())
    val updateFrequency: StateFlow<UpdateFrequency> = _updateFrequency.asStateFlow()

    private val _availableUpdate = MutableStateFlow<AvailableUpdate?>(null)
    val availableUpdate: StateFlow<AvailableUpdate?> = _availableUpdate.asStateFlow()

    private val _updateProgress = MutableStateFlow<Float?>(null)
    val updateProgress: StateFlow<Float?> = _updateProgress.asStateFlow()

    init {
        refresh()
        checkGitHubUpdates(force = false)
    }

    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val profiles = db.listProfiles()
                val clients = db.listClients().size
                Snapshot(profiles, clients)
            }
            _profiles.value = snapshot.profiles
            _clientCount.value = snapshot.clients
            _playlistStatus.value = withContext(Dispatchers.IO) { playlist.status() }
            _playlistsList.value = withContext(Dispatchers.IO) { playlist.listPlaylists() }
            _allChannels.value = withContext(Dispatchers.IO) { db.listChannels() }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun setLanguage(code: String) {
        Prefs.setLanguage(context, code)
        _language.value = code.take(2).lowercase()
        refresh()
    }

    fun importPlaylist(url: String) {
        val clean = url.trim()
        if (clean.isBlank()) {
            _message.value = playlistStrings().t(
                "Inserisci l'indirizzo della playlist",
                "Enter the playlist address",
                "Introduce la dirección de la lista",
            )
            return
        }
        viewModelScope.launch {
            _playlistBusy.value = true
            try {
                val res = withContext(Dispatchers.IO) { playlist.importUrl(clean) }
                _message.value = playlistStrings().t(
                    "Playlist importata: ${res.count} canali",
                    "Playlist imported: ${res.count} channels",
                    "Lista importada: ${res.count} canales",
                )
            } catch (error: PlaylistException) {
                _message.value = when (error.reason) {
                    PlaylistError.InvalidUrl -> playlistStrings().t("Inserisci un link valido", "Enter a valid link", "Introduce un enlace válido")
                    PlaylistError.Download -> playlistStrings().t("Download fallito: verifica il link", "Download failed: check link", "Descarga fallida")
                    PlaylistError.Empty -> playlistStrings().t("Nessun canale trovato", "No channels found", "No se encontraron canales")
                }
            } catch (error: Exception) {
                _message.value = error.message ?: "Errore importazione"
            } finally {
                _playlistBusy.value = false
                refresh()
            }
        }
    }

    fun importPlaylistFile(uri: Uri) {
        viewModelScope.launch {
            _playlistBusy.value = true
            try {
                val res = withContext(Dispatchers.IO) { playlist.importUri(uri) }
                _message.value = playlistStrings().t(
                    "Playlist importata: ${res.count} canali",
                    "Playlist imported: ${res.count} channels",
                    "Lista importada: ${res.count} canales",
                )
            } catch (error: PlaylistException) {
                _message.value = when (error.reason) {
                    PlaylistError.InvalidUrl -> playlistStrings().t("File non valido", "Invalid file", "Archivo no válido")
                    PlaylistError.Download -> playlistStrings().t("Lettura fallita", "Read failed", "Error de lectura")
                    PlaylistError.Empty -> playlistStrings().t("Nessun canale trovato", "No channels found", "No se encontraron canales")
                }
            } catch (error: Exception) {
                _message.value = error.message ?: "Errore importazione"
            } finally {
                _playlistBusy.value = false
                refresh()
            }
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            _playlistBusy.value = true
            try {
                withContext(Dispatchers.IO) { playlist.deletePlaylist(id) }
                _message.value = playlistStrings().t("Playlist eliminata", "Playlist deleted", "Lista eliminada")
            } finally {
                _playlistBusy.value = false
                refresh()
            }
        }
    }

    fun refreshPlaylist() {
        viewModelScope.launch {
            _playlistBusy.value = true
            try {
                val res = withContext(Dispatchers.IO) { playlist.refresh() }
                val count = res?.count ?: 0
                _message.value = playlistStrings().t(
                    "Aggiornato: $count canali",
                    "Refreshed: $count channels",
                    "Actualizado: $count canales",
                )
            } catch (error: Exception) {
                _message.value = error.message ?: "Errore aggiornamento"
            } finally {
                _playlistBusy.value = false
                refresh()
            }
        }
    }

    fun clearPlaylist() {
        viewModelScope.launch {
            _playlistBusy.value = true
            try {
                withContext(Dispatchers.IO) { playlist.clear() }
                _message.value = playlistStrings().t("Playlist rimossa", "Playlist cleared", "Lista eliminada")
            } finally {
                _playlistBusy.value = false
                refresh()
            }
        }
    }

    private fun playlistStrings() = textOf(_language.value ?: Prefs.activeLanguage(context))

    fun createProfile(name: String, avatar: String, language: String, country: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.createProfile(name = clean, avatar = avatar, language = language, country = country)
            }
            refresh()
        }
    }

    fun updateProfile(id: String, name: String, avatar: String, language: String, country: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.updateProfile(id = id, name = clean, avatar = avatar, language = language, country = country)
            }
            refresh()
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.deleteProfile(id) }
            refresh()
        }
    }

    fun saveSettings(serverName: String, port: Int, autoStart: Boolean) {
        val cleanName = serverName.trim().ifBlank { "CasaTV" }
        val validPort = port.coerceIn(1024, 65535)
        Prefs.setServerName(context, cleanName)
        Prefs.setPort(context, validPort)
        Prefs.setAutoStart(context, autoStart)
        _serverName.value = cleanName
        _port.value = validPort
        _autoStart.value = autoStart
        _message.value = "Impostazioni salvate"
    }

    fun startServer() {
        ServerService.start(context)
    }

    fun stopServer() {
        ServerService.stop(context)
    }

    fun loadAllChannels() {
        viewModelScope.launch(Dispatchers.IO) {
            _allChannels.value = db.listChannels()
        }
    }

    fun setUpdateFrequency(freq: UpdateFrequency) {
        updater.setFrequency(freq)
        _updateFrequency.value = freq
    }

    fun checkGitHubUpdates(force: Boolean) {
        viewModelScope.launch {
            updater.checkForUpdate(force = force)
                .onSuccess { update ->
                    _availableUpdate.value = update
                    if (force && update == null) {
                        _message.value = "Server aggiornato all'ultima versione"
                    }
                }
                .onFailure { err ->
                    if (force) {
                        _message.value = "Errore controllo aggiornamenti: ${err.message}"
                    }
                }
        }
    }

    fun installGitHubUpdate() {
        val update = _availableUpdate.value ?: return
        if (_updateProgress.value != null) return
        viewModelScope.launch {
            _updateProgress.value = 0f
            updater.downloadAndInstall(update.downloadUrl) { p ->
                _updateProgress.value = p
            }.onFailure { err ->
                _message.value = "Errore download aggiornamento: ${err.message}"
            }
            _updateProgress.value = null
        }
    }

    fun dismissUpdate() {
        _availableUpdate.value = null
    }

    private data class Snapshot(
        val profiles: List<ProfileRow>,
        val clients: Int,
    )
}
