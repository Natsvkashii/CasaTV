package it.casatv.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import it.casatv.tv.BuildConfig
import it.casatv.tv.discovery.DiscoveredServer
import it.casatv.tv.data.Profile
import it.casatv.tv.data.ProfileLanguage
import it.casatv.tv.data.ServerConfig
import it.casatv.tv.data.ServerStats
import it.casatv.tv.data.SystemStatus
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.buildCountryCatalog
import it.casatv.tv.ui.components.ScreenHeader
import it.casatv.tv.ui.components.TvButton
import it.casatv.tv.ui.countryFacet
import it.casatv.tv.ui.formatNumber
import it.casatv.tv.ui.relativeTime
import it.casatv.tv.update.TvUpdateFrequency
import it.casatv.tv.ui.theme.CasaAmber
import it.casatv.tv.ui.theme.CasaBackground
import it.casatv.tv.ui.theme.CasaBorder
import it.casatv.tv.ui.theme.CasaDim
import it.casatv.tv.ui.theme.CasaGreen
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaRed
import it.casatv.tv.ui.theme.CasaSurface
import it.casatv.tv.ui.theme.CasaSurfaceHigh
import it.casatv.tv.ui.theme.CasaText

@Composable
private fun LocalTvM3uCard(vm: MainViewModel) {
    var localUrl by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            vm.importLocalM3uUri(uri)
        }
    }

    SettingsCard(
        title = "Lista M3U locale (sul televisore)",
        subtitle = "Carica una lista M3U direttamente su questo dispositivo senza usare il server",
    ) {
        Text(
            text = "Attenzione: il dispositivo potrebbe non essere in grado di gestire tutti i dati della lista M3U e potrebbe risultare lento su dispositivi di fascia bassa.",
            color = CasaAmber,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(CasaAmber.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                .padding(12.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = localUrl,
            onValueChange = { localUrl = it },
            label = { Text("URL lista M3U / M3U8") },
            placeholder = { Text("https://...") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CasaText,
                unfocusedTextColor = CasaText,
                focusedBorderColor = CasaGreen,
                unfocusedBorderColor = CasaBorder,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(
                text = "Carica URL M3U",
                onClick = { vm.importLocalM3uUrl(localUrl) },
                primary = true,
            )
            TvButton(
                text = "Scegli file .M3U locale",
                onClick = {
                    picker.launch(
                        arrayOf(
                            "audio/x-mpegurl",
                            "application/vnd.apple.mpegurl",
                            "application/x-mpegurl",
                            "text/plain",
                            "*/*",
                        ),
                    )
                },
                primary = false,
            )
        }
    }
}

private enum class SettingsSection {
    Profile,
    Connection,
    Playlists,
    System,
    Catalog,
    Maintenance,
    MultiTv,
    Remote,
}

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit = {},
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val languages by vm.profileLanguages.collectAsStateWithLifecycle()
    val tvCountry by vm.tvCountry.collectAsStateWithLifecycle()
    val system by vm.system.collectAsStateWithLifecycle()
    val systemBusy by vm.systemBusy.collectAsStateWithLifecycle()
    val multiTvSync by vm.multiTvSync.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    var confirmDisconnect by remember { mutableStateOf(false) }
    var countryPicker by remember { mutableStateOf(false) }
    var serverPicker by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf(SettingsSection.Profile) }
    val firstActionFocus = remember { FocusRequester() }

    BackHandler {
        onBack()
    }

    val countries = remember(config?.facets?.countries, strings.language) {
        buildCountryCatalog(config?.facets?.countries.orEmpty(), strings.language)
    }
    val tvCountryFacet = countryFacet(tvCountry, countries)
        ?: countryFacet(profile?.country, countries)

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvButton(
                    text = strings.t("← Canali", "← Channels", "← Canales"),
                    onClick = onBack,
                    primary = false,
                )
                TvButton(
                    text = strings.t("Profilo", "Profile", "Perfil"),
                    onClick = { activeSection = SettingsSection.Profile },
                    primary = activeSection == SettingsSection.Profile,
                )
                TvButton(
                    text = strings.t("Connessione", "Connection", "Conexión"),
                    onClick = { activeSection = SettingsSection.Connection },
                    primary = activeSection == SettingsSection.Connection,
                )
                TvButton(
                    text = strings.t("Playlist", "Playlists", "Listas"),
                    onClick = { activeSection = SettingsSection.Playlists },
                    primary = activeSection == SettingsSection.Playlists,
                )
                TvButton(
                    text = strings.t("Sistema", "System", "Sistema"),
                    onClick = { activeSection = SettingsSection.System },
                    primary = activeSection == SettingsSection.System,
                )
                TvButton(
                    text = strings.t("Catalogo & EPG", "Catalog & EPG", "Catálogo y EPG"),
                    onClick = { activeSection = SettingsSection.Catalog },
                    primary = activeSection == SettingsSection.Catalog,
                )
                TvButton(
                    text = strings.t("Manutenzione", "Maintenance", "Mantenimiento"),
                    onClick = { activeSection = SettingsSection.Maintenance },
                    primary = activeSection == SettingsSection.Maintenance,
                )
                TvButton(
                    text = strings.t("Multi-TV", "Multi-TV", "Multi-TV"),
                    onClick = { activeSection = SettingsSection.MultiTv },
                    primary = activeSection == SettingsSection.MultiTv,
                )
                TvButton(
                    text = strings.t("Telecomando", "Remote", "Mando"),
                    onClick = { activeSection = SettingsSection.Remote },
                    primary = activeSection == SettingsSection.Remote,
                )
            }
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (activeSection) {
                    SettingsSection.Profile -> {
                        val active = profile
                        if (active != null) {
                            ProfileCard(
                                profile = active,
                                languages = languages,
                                tvCountry = tvCountryFacet,
                                onSwitchProfile = { vm.switchProfile() },
                                onLanguage = { code ->
                                    vm.updateProfile(
                                        active,
                                        active.name,
                                        active.avatar,
                                        code,
                                        active.country,
                                    ) {}
                                },
                                onChangeCountry = { countryPicker = true },
                            )
                            Spacer(Modifier.height(14.dp))
                            ThemeSettingsCard(
                                currentTheme = themeMode,
                                onSelectTheme = { vm.setThemeMode(it) },
                            )
                        }
                    }
                    SettingsSection.Connection -> {
                        ConnectionCard(
                            config = config,
                            serverUrl = serverUrl.orEmpty(),
                            onChangeServer = {
                                vm.refreshDiscovery()
                                serverPicker = true
                            },
                        )
                    }
                    SettingsSection.Playlists -> {
                        LocalTvM3uCard(vm = vm)
                        PlaylistsSettingsCard(
                            vm = vm,
                            strings = strings,
                        )
                    }
                    SettingsSection.System -> {
                        SystemCard(
                            system = system,
                            busy = systemBusy,
                            onRefresh = { vm.refreshSystem() },
                        )
                        SettingsCard(
                            title = strings.t("Informazioni", "About", "Información"),
                            subtitle = null,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = strings.t("CasaTV per Android TV", "CasaTV for Android TV", "CasaTV para Android TV"),
                                        color = CasaText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = strings.t(
                                            "Versione app ${BuildConfig.VERSION_NAME} · Server ${config?.version ?: "—"}",
                                            "App version ${BuildConfig.VERSION_NAME} · Server ${config?.version ?: "—"}",
                                            "Versión de la app ${BuildConfig.VERSION_NAME} · Servidor ${config?.version ?: "—"}",
                                        ),
                                        color = CasaDim,
                                        fontSize = 11.sp,
                                    )
                                    val update = system?.update
                                    if (update?.updateAvailable == true) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = strings.t(
                                                "Aggiornamento disponibile: v${update.latest}",
                                                "Update available: v${update.latest}",
                                                "Actualización disponible: v${update.latest}",
                                            ),
                                            color = CasaAmber,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                Text(
                                    text = strings.t(
                                        "Catalogo iptv-org · Proxy HLS locale",
                                        "iptv-org catalog · Local HLS proxy",
                                        "Catálogo iptv-org · Proxy HLS local",
                                    ),
                                    color = CasaDim,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                    SettingsSection.Catalog -> {
                        SettingsCard(
                            title = strings.t("Catalogo", "Catalog", "Catálogo"),
                            subtitle = strings.t(
                                "Dati indicizzati dal server CasaTV",
                                "Data indexed by the CasaTV server",
                                "Datos indexados por el servidor CasaTV",
                            ),
                        ) {
                            val stats = config?.stats ?: ServerStats()
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatCell(
                                    strings.t("Canali", "Channels", "Canales"),
                                    formatNumber(stats.channels, strings.language),
                                    Modifier.weight(1f),
                                )
                                StatCell(
                                    strings.t("Flussi", "Streams", "Flujos"),
                                    formatNumber(stats.streams, strings.language),
                                    Modifier.weight(1f),
                                )
                                StatCell(
                                    "Online",
                                    formatNumber(stats.onlineStreams, strings.language),
                                    Modifier.weight(1f),
                                )
                                StatCell(
                                    strings.t("Paesi", "Countries", "Países"),
                                    formatNumber(stats.countries, strings.language),
                                    Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatCell(
                                    strings.t("Preferiti", "Favorites", "Favoritos"),
                                    formatNumber(stats.favorites, strings.language),
                                    Modifier.weight(1f),
                                )
                                StatCell(
                                    strings.t("Nascosti", "Hidden", "Ocultos"),
                                    formatNumber(stats.hidden, strings.language),
                                    Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    SettingsSection.Maintenance -> {
                        SettingsCard(
                            title = strings.t("Manutenzione", "Maintenance", "Mantenimiento"),
                            subtitle = strings.t(
                                "Le operazioni vengono eseguite dal server in background.",
                                "Operations are performed by the server in the background.",
                                "Las operaciones las realiza el servidor en segundo plano.",
                            ),
                            firstActionFocus = firstActionFocus,
                        ) {
                            val source = config?.catalog?.source.orEmpty()
                            if (source.isNotBlank()) {
                                Text(
                                    text = "${strings.t("Sorgente M3U", "M3U source", "Fuente M3U")}: $source",
                                    color = CasaDim,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TvButton(
                                    text = strings.t("Ricarica dati", "Reload data", "Recargar datos"),
                                    onClick = { vm.reloadServer() },
                                    primary = false,
                                    icon = Icons.Filled.Refresh,
                                    modifier = Modifier.focusRequester(firstActionFocus),
                                )
                                TvButton(
                                    text = strings.t("Aggiorna catalogo", "Update catalog", "Actualizar catálogo"),
                                    onClick = { vm.refreshCatalog() },
                                    primary = false,
                                    icon = Icons.Filled.Refresh,
                                )
                                TvButton(
                                    text = strings.t("Verifica aggiornamenti", "Check for updates", "Buscar actualizaciones"),
                                    onClick = { vm.checkForUpdates(silent = false) },
                                    primary = false,
                                    icon = Icons.Filled.Refresh,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TvButton(
                                    text = strings.t("Cambia server", "Change server", "Cambiar servidor"),
                                    onClick = { confirmDisconnect = true },
                                    primary = false,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            val updateFreq by vm.updateFrequency.collectAsStateWithLifecycle()
                            Text(
                                text = "Frequenza controllo aggiornamenti GitHub",
                                color = CasaMuted,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TvUpdateFrequency.entries.forEach { freq ->
                                    TvButton(
                                        text = freq.label,
                                        onClick = { vm.setUpdateFrequency(freq) },
                                        primary = updateFreq == freq,
                                    )
                                }
                            }
                        }
                    }
                    SettingsSection.MultiTv -> {
                        SettingsCard(
                            title = strings.t("Sincronizzazione Multi-TV", "Multi-TV Synchronization", "Sincronización Multi-TV"),
                            subtitle = strings.t(
                                "Sincronizza automaticamente audio e video quando più TV in casa guardano lo stesso canale.",
                                "Automatically synchronizes audio and video when multiple TVs watch the same channel.",
                                "Sincroniza automáticamente audio y vídeo cuando varias TV ven el mismo canal.",
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (multiTvSync) {
                                            strings.t("Sincronizzazione automatica attiva", "Automatic sync enabled", "Sincronización automática activa")
                                        } else {
                                            strings.t("Sincronizzazione disattivata", "Sync disabled", "Sincronización desactivada")
                                        },
                                        color = if (multiTvSync) CasaGreen else CasaMuted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = strings.t(
                                            "Elimina l'eco e il ritardo tra TV nello stesso ambiente allineando frame e flusso audio in tempo reale.",
                                            "Eliminates echo and delay between TVs in the same room by aligning frames and audio in real-time.",
                                            "Elimina el eco y retraso entre televisores alineando audio y vídeo en tiempo real.",
                                        ),
                                        color = CasaDim,
                                        fontSize = 11.sp,
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                TvButton(
                                    text = if (multiTvSync) strings.t("Disattiva", "Disable", "Desactivar") else strings.t("Attiva", "Enable", "Activar"),
                                    onClick = { vm.setMultiTvSync(!multiTvSync) },
                                    primary = !multiTvSync,
                                )
                            }
                        }
                    }
                    SettingsSection.Remote -> {
                        SettingsCard(
                            title = strings.t("Telecomando", "Remote", "Mando a distancia"),
                            subtitle = strings.t(
                                "Comandi disponibili durante la navigazione e la visione.",
                                "Keys available while browsing and watching.",
                                "Teclas disponibles durante la navegación y la reproducción.",
                            ),
                        ) {
                            RemoteRow("▲ ▼", strings.t("Cambia canale durante la visione", "Change channel while watching", "Cambiar canal durante la reproducción"))
                            RemoteRow("OK", strings.t("Mostra o nascondi le informazioni sul canale", "Show or hide channel information", "Mostrar u ocultar la información del canal"))
                            RemoteRow("0 – 9", strings.t("Digita il numero del canale e premi OK per andare", "Type the channel number and press OK to tune", "Escribe el número del canal y pulsa OK para sintonizar"))
                            RemoteRow("MENU", strings.t("Aggiunge o rimuove il canale dai preferiti", "Add or remove the channel from favorites", "Añadir o quitar el canal de favoritos"))
                            RemoteRow("INFO", strings.t("Cambia il paese della modalità TV", "Change the TV mode country", "Cambiar el país del modo TV"))
                            RemoteRow("BACK", strings.t("Torna indietro o chiude il canale in visione", "Go back or close the playing channel", "Volver atrás o cerrar el canal en reproducción"))
                        }
                    }
                }
            }
        }

        if (countryPicker) {
            CountryPickerOverlay(
                countries = countries,
                selected = tvCountry ?: profile?.country ?: "IT",
                title = strings.t("Paese preferito", "Preferred country", "País preferido"),
                subtitle = strings.t(
                    "Il profilo usa questo paese per consigliati, Home e coda della modalità TV.",
                    "The profile uses this country for recommendations, Home and the TV mode queue.",
                    "El perfil usa este país para recomendados, Inicio y la cola del modo TV.",
                ),
                onSelect = { country ->
                    countryPicker = false
                    profile?.let { active ->
                        vm.updateProfile(
                            active,
                            active.name,
                            active.avatar,
                            active.language,
                            country.code,
                        ) {}
                        vm.setTvCountry(country.code)
                    }
                },
                onClose = { countryPicker = false },
            )
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            containerColor = CasaSurfaceHigh,
            titleContentColor = CasaText,
            textContentColor = CasaMuted,
            title = { Text(strings.t("Scollegare il server?", "Disconnect the server?", "¿Desconectar el servidor?")) },
            text = {
                Text(
                    strings.t(
                        "L’app tornerà alla schermata di configurazione. " +
                            "Profili, preferiti e impostazioni restano salvati sul server.",
                        "The app will return to the setup screen. " +
                            "Profiles, favorites and settings stay saved on the server.",
                        "La aplicación volverá a la pantalla de configuración. " +
                            "Los perfiles, favoritos y ajustes permanecen guardados en el servidor.",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisconnect = false
                        vm.disconnect()
                    },
                ) {
                    Text(
                        strings.t("Scollega", "Disconnect", "Desconectar"),
                        color = CasaRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) {
                    Text(strings.t("Annulla", "Cancel", "Cancelar"), color = CasaGreen)
                }
            },
        )
    }

    if (serverPicker) {
        ServerPickerDialog(
            vm = vm,
            currentUrl = serverUrl.orEmpty(),
            onDismiss = { serverPicker = false },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    languages: List<ProfileLanguage>,
    tvCountry: it.casatv.tv.data.Facet?,
    onSwitchProfile: () -> Unit,
    onLanguage: (String) -> Unit,
    onChangeCountry: () -> Unit,
) {
    val strings = LocalAppStrings.current
    SettingsCard(
        title = strings.t("Profilo e preferenze", "Profile and preferences", "Perfil y preferencias"),
        subtitle = strings.t(
            "Lingua, paese e preferiti sono salvati per questo profilo.",
            "Language, country and favorites are saved for this profile.",
            "El idioma, el país y los favoritos se guardan para este perfil.",
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CasaBackground.copy(alpha = 0.8f))
                    .border(1.dp, CasaBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = profile.avatar, fontSize = 30.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    color = CasaText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${profile.flag} ${profile.countryName} · ${profile.displayLanguage}",
                    color = CasaMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvButton(
                text = strings.t("Cambia profilo", "Switch profile", "Cambiar perfil"),
                onClick = onSwitchProfile,
                primary = false,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings.t("LINGUA DELL’APP", "APP LANGUAGE", "IDIOMA DE LA APP"),
                color = CasaDim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                modifier = Modifier.weight(1f),
            )
            languages.forEach { entry ->
                TvButton(
                    text = entry.name,
                    onClick = { if (entry.code != profile.language) onLanguage(entry.code) },
                    primary = entry.code == profile.language,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = strings.t("PAESE PREFERITO", "PREFERRED COUNTRY", "PAÍS PREFERIDO"),
                    color = CasaDim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${profile.flag} ${profile.countryName}",
                    color = CasaText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            TvButton(
                text = strings.t("Cambia paese", "Change country", "Cambiar país"),
                onClick = onChangeCountry,
                primary = false,
            )
        }

        if (tvCountry != null) {
            Spacer(Modifier.height(13.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(CasaBackground.copy(alpha = 0.55f))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
            ) {
                Text(
                    text = strings.t(
                        "Modalità TV: ${tvCountry.flag} ${tvCountry.name} · cambia anche con INFO durante la visione",
                        "TV mode: ${tvCountry.flag} ${tvCountry.name} · also change it with INFO while watching",
                        "Modo TV: ${tvCountry.flag} ${tvCountry.name} · cámbialo también con INFO mientras ves la TV",
                    ),
                    color = CasaMuted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun ThemeSettingsCard(
    currentTheme: String,
    onSelectTheme: (String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val isLight = currentTheme == "light"
    SettingsCard(
        title = strings.t("Tema e visibilità", "Theme and visibility", "Tema y visibilidad"),
        subtitle = strings.t(
            "Scegli il tema dell'interfaccia. Il tema chiaro migliora notevolmente il contrasto e la leggibilità su TV.",
            "Choose interface theme. Light theme significantly improves contrast and readability on TV.",
            "Elige el tema de la interfaz. El tema claro mejora notablemente el contraste y la legibilidad en TV.",
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvButton(
                text = strings.t("☀️ Tema Chiaro (Alta Leggibilità)", "☀️ Light Theme (High Contrast)", "☀️ Tema Claro (Alta Legibilidad)"),
                onClick = { onSelectTheme("light") },
                primary = isLight,
            )
            TvButton(
                text = strings.t("🌙 Tema Scuro", "🌙 Dark Theme", "🌙 Tema Oscuro"),
                onClick = { onSelectTheme("dark") },
                primary = !isLight,
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    config: ServerConfig?,
    serverUrl: String,
    onChangeServer: () -> Unit,
) {
    val strings = LocalAppStrings.current
    SettingsCard(
        title = strings.t("Server collegato", "Connected server", "Servidor conectado"),
        subtitle = strings.t(
            "CasaTV è raggiungibile sulla rete locale.",
            "CasaTV is reachable on the local network.",
            "CasaTV está disponible en la red local.",
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(7.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(CasaGreen),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = config?.name ?: "CasaTV",
                        color = CasaText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = serverUrl,
                    color = CasaMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = strings.t("VERSIONE SERVER", "SERVER VERSION", "VERSIÓN DEL SERVIDOR"),
                    color = CasaDim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = config?.version ?: "—",
                    color = CasaText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        val urls = config?.urls.orEmpty()
        if (urls.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                urls.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.name.ifBlank { strings.t("rete", "network", "red") },
                            color = CasaDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(44.dp),
                        )
                        Text(
                            text = entry.url,
                            color = CasaMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(
                text = strings.t("Cambia server", "Change server", "Cambiar servidor"),
                onClick = onChangeServer,
                primary = true,
                icon = Icons.Filled.Refresh,
            )
        }
    }
}


@Composable
private fun SystemCard(
    system: SystemStatus?,
    busy: Boolean,
    onRefresh: () -> Unit,
) {
    val strings = LocalAppStrings.current
    SettingsCard(
        title = strings.t(
            "Stato del sistema",
            "System status",
            "Estado del sistema",
        ),
        subtitle = strings.t(
            "Connessione, ultimo aggiornamento, dispositivi e risorse del server.",
            "Connection, last update, devices and server resources.",
            "Conexión, última actualización, dispositivos y recursos del servidor.",
        ),
    ) {
        if (system == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.t(
                        "Raccolta dei dati in corso…",
                        "Collecting data…",
                        "Recopilando datos…",
                    ),
                    color = CasaMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                TvButton(
                    text = strings.t("Aggiorna stato", "Refresh status", "Actualizar estado"),
                    onClick = onRefresh,
                    primary = false,
                    icon = Icons.Filled.Refresh,
                )
            }
            return@SettingsCard
        }

        val alert = system.alerts.maxByOrNull { it.value }
        if (alert != null) {
            val critical = alert.level == "critical"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background((if (critical) CasaRed else CasaAmber).copy(alpha = 0.16f))
                    .border(
                        1.dp,
                        (if (critical) CasaRed else CasaAmber).copy(alpha = 0.65f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    text = "⚠ " + strings.t(
                        "Risorse quasi esaurite: ${alert.message}. Chiudi app o flussi non necessari sul server.",
                        "Resources almost exhausted: ${alert.message}. Close unneeded apps or streams on the server.",
                        "Recursos casi agotados: ${alert.message}. Cierra aplicaciones o flujos innecesarios en el servidor.",
                    ),
                    color = if (critical) CasaRed else CasaAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell(
                strings.t("CONNESSIONE", "CONNECTION", "CONEXIÓN"),
                when (system.health) {
                    "critical" -> strings.t("Critica", "Critical", "Crítica")
                    "warning" -> strings.t("Attenzione", "Warning", "Atención")
                    else -> strings.t("Online", "Online", "En línea")
                },
                Modifier.weight(1f),
                accent = system.health == "ok",
            )
            StatCell(
                strings.t("ULTIMO AGGIORNAMENTO", "LAST UPDATE", "ÚLTIMA ACTUALIZACIÓN"),
                relativeTime(system.lastUpdate.catalog, strings.language),
                Modifier.weight(1f),
            )
            StatCell(
                strings.t("DISPOSITIVI", "DEVICES", "DISPOSITIVOS"),
                formatNumber(system.deviceCount, strings.language),
                Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))
        ResourceBar(
            strings.t("CPU", "CPU", "CPU"),
            system.resources.cpu.percent,
            strings.language,
        )
        Spacer(Modifier.height(7.dp))
        ResourceBar(
            strings.t("RAM", "RAM", "RAM"),
            system.resources.memory.percent,
            strings.language,
        )
        Spacer(Modifier.height(7.dp))
        ResourceBar(
            strings.t("Disco", "Disk", "Disco"),
            system.resources.disk.percent,
            strings.language,
        )

        val devices = system.devices
        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(13.dp))
            Text(
                text = strings.t(
                    "Dispositivi connessi",
                    "Connected devices",
                    "Dispositivos conectados",
                ),
                color = CasaDim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
            )
            Spacer(Modifier.height(6.dp))
            devices.take(4).forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(6.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (device.state == "playing") CasaGreen else CasaDim),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = device.name.ifBlank { device.platform },
                        color = CasaText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (device.state == "playing" && device.channel.isNotBlank()) {
                        Text(
                            text = device.channel,
                            color = CasaGreen,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(180.dp),
                        )
                    } else {
                        Text(
                            text = strings.t("inattivo", "idle", "inactivo"),
                            color = CasaDim,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton(
                text = strings.t(
                    if (busy) "Aggiorno…" else "Aggiorna stato",
                    if (busy) "Refreshing…" else "Refresh status",
                    if (busy) "Actualizando…" else "Actualizar estado",
                ),
                onClick = onRefresh,
                primary = false,
                icon = Icons.Filled.Refresh,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = strings.t(
                    "Attivo da ${formatUptime(system.uptimeSeconds, strings.language)}",
                    "Up for ${formatUptime(system.uptimeSeconds, strings.language)}",
                    "Activo desde hace ${formatUptime(system.uptimeSeconds, strings.language)}",
                ),
                color = CasaDim,
                fontSize = 10.sp,
            )
        }
    }
}

private fun formatUptime(seconds: Long, language: String): String {
    val hours = seconds / 3600
    val days = hours / 24
    val code = language.lowercase().take(2)
    return when {
        days > 0 -> if (code == "it") "$days g" else "$days d"
        hours > 0 -> "$hours h"
        else -> "${seconds / 60} min"
    }
}

@Composable
private fun ResourceBar(label: String, percent: Double, language: String) {
    val value = percent.coerceIn(0.0, 100.0)
    val color = when {
        value >= 90 -> CasaRed
        value >= 75 -> CasaAmber
        else -> CasaGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = CasaDim,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.width(56.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(CasaBackground),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value / 100.0).toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${value.toInt()}%",
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(42.dp),
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String?,
    focusable: Boolean = false,
    firstActionFocus: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val cardBg = if (isLight) {
        if (focusable && focused) it.casatv.tv.ui.theme.CasaSurfaceHigh else it.casatv.tv.ui.theme.CasaSurface
    } else {
        if (focusable && focused) Color(0x33FFFFFF) else Color(0x14FFFFFF)
    }
    val cardBorder = if (isLight) {
        if (focusable && focused) it.casatv.tv.ui.theme.CasaBlue else it.casatv.tv.ui.theme.CasaBorder
    } else {
        if (focusable && focused) Color.White else Color(0x24FFFFFF)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(
                width = if (focusable && focused) 2.dp else 1.dp,
                color = cardBorder,
                shape = RoundedCornerShape(20.dp),
            )
            .then(
                if (firstActionFocus != null) {
                    Modifier.focusProperties { down = firstActionFocus }
                } else {
                    Modifier
                },
            )
            .then(
                if (focusable) {
                    Modifier.focusable(interactionSource = interaction)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Text(
            text = title,
            color = CasaText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = CasaDim,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(13.dp))
        content()
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = CasaDim,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = if (accent) Color(0xFF64D2FF) else CasaText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun RemoteRow(key: String, action: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(92.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x1FFFFFFF))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp))
                .padding(vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = key,
                color = Color(0xFFFF9F0A),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = action,
            color = CasaMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ServerPickerDialog(
    vm: MainViewModel,
    currentUrl: String,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val discovered by vm.discoveredServers.collectAsStateWithLifecycle()
    val discovering by vm.discovering.collectAsStateWithLifecycle()
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    var manualUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.t("Cambia server CasaTV", "Change CasaTV server", "Cambiar servidor CasaTV"),
                    color = CasaText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                )
                if (discovering) {
                    CircularProgressIndicator(
                        color = CasaGreen,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = strings.t(
                        "Seleziona un altro server presente sulla rete o inserisci l'indirizzo:",
                        "Select another server on the network or enter the address:",
                        "Selecciona otro servidor de la red o introduce la dirección:",
                    ),
                    color = CasaMuted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    text = strings.t("SERVER RILEVATI", "DISCOVERED SERVERS", "SERVIDORES DETECTADOS"),
                    color = CasaDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))

                if (discovered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CasaBackground.copy(alpha = 0.6f))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = if (discovering) {
                                strings.t("Ricerca server in corso…", "Searching for servers…", "Buscando servidores…")
                            } else {
                                strings.t("Nessun altro server rilevato.", "No other servers found.", "No se encontraron otros servidores.")
                            },
                            color = CasaMuted,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isLight = it.casatv.tv.ui.theme.CasaIsLight
                        discovered.forEach { server ->
                            val isCurrent = server.url.trimEnd('/') == currentUrl.trimEnd('/')
                            val serverBg = if (isLight) {
                                if (isCurrent) Color(0x1F0070F3) else it.casatv.tv.ui.theme.CasaSurface
                            } else {
                                if (isCurrent) Color(0x24FFFFFF) else Color(0x14FFFFFF)
                            }
                            val serverBorder = if (isLight) {
                                if (isCurrent) it.casatv.tv.ui.theme.CasaBlue else it.casatv.tv.ui.theme.CasaBorder
                            } else {
                                if (isCurrent) Color.White else Color(0x1FFFFFFF)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(serverBg)
                                    .border(1.dp, serverBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = server.name,
                                            color = CasaText,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                        )
                                        if (isCurrent) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = strings.t("ATTIVO", "ACTIVE", "ACTIVO"),
                                                color = Color(0xFF64D2FF),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    Text(
                                        text = server.url,
                                        color = CasaMuted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                if (!isCurrent) {
                                    TvButton(
                                        text = strings.t("Collega", "Connect", "Conectar"),
                                        onClick = {
                                            vm.changeServer(server.url)
                                            onDismiss()
                                        },
                                        primary = true,
                                        enabled = !connecting,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.t("INDIRIZZO MANUALE", "MANUAL ADDRESS", "DIRECCIÓN MANUAL"),
                        color = CasaDim,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TvButton(
                        text = strings.t("Cerca di nuovo", "Search again", "Buscar de nuevo"),
                        onClick = { vm.refreshDiscovery() },
                        primary = false,
                        icon = Icons.Filled.Refresh,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = manualUrl,
                        onValueChange = { manualUrl = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = {
                            Text("http://192.168.1.10:8787", color = CasaDim, fontSize = 12.sp)
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = CasaText),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (it.casatv.tv.ui.theme.CasaIsLight) it.casatv.tv.ui.theme.CasaBlue else Color.White,
                            unfocusedBorderColor = CasaBorder,
                            focusedContainerColor = CasaBackground,
                            unfocusedContainerColor = CasaBackground,
                        ),
                    )
                    TvButton(
                        text = strings.t("Collega", "Connect", "Conectar"),
                        onClick = {
                            if (manualUrl.isNotBlank()) {
                                vm.changeServer(manualUrl.trim())
                                onDismiss()
                            }
                        },
                        primary = true,
                        enabled = manualUrl.isNotBlank() && !connecting,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.t("Chiudi", "Close", "Cerrar"), color = CasaMuted)
            }
        },
    )
}

@Composable
private fun PlaylistsSettingsCard(
    vm: MainViewModel,
    strings: it.casatv.tv.ui.AppStrings,
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadPlaylists()
    }

    SettingsCard(
        title = strings.t("Playlist canali M3U", "M3U Channel Playlists", "Listas de canales M3U"),
        subtitle = strings.t(
            "Configura e gestisci le sorgenti canali memorizzate sul server.",
            "Configure and manage channel sources stored on the server.",
            "Configura y gestiona las fuentes de canales almacenadas en el servidor.",
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.t(
                    "${playlists.size} playlist configurate",
                    "${playlists.size} configured playlists",
                    "${playlists.size} listas configuradas",
                ),
                color = CasaText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            TvButton(
                text = strings.t("+ Aggiungi Playlist", "+ Add Playlist", "+ Añadir Lista"),
                onClick = { showAddDialog = true },
                primary = true,
                icon = Icons.Filled.Add,
            )
        }

        Spacer(Modifier.height(14.dp))

        if (playlists.isEmpty()) {
            Text(
                text = strings.t(
                    "Nessuna playlist configurata sul server. Premi '+ Aggiungi Playlist' per inserire un link M3U.",
                    "No playlists configured on the server. Press '+ Add Playlist' to enter an M3U link.",
                    "No hay listas configuradas en el servidor. Pulsa '+ Añadir Lista' para introducir un enlace M3U.",
                ),
                color = CasaMuted,
                fontSize = 12.sp,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                playlists.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = p.name,
                                color = CasaText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "${p.channelCount} canali" + if (p.url.isNotBlank()) " · ${p.url}" else "",
                                color = CasaMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TvButton(
                            text = strings.t("Aggiorna", "Refresh", "Actualizar"),
                            onClick = { vm.refreshPlaylist(p.id) },
                            primary = false,
                            icon = Icons.Filled.Refresh,
                        )
                        Spacer(Modifier.width(6.dp))
                        TvButton(
                            text = strings.t("Elimina", "Delete", "Eliminar"),
                            onClick = { vm.deletePlaylist(p.id) },
                            primary = false,
                            icon = Icons.Filled.Delete,
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlaylistDialog(
            strings = strings,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                vm.addPlaylist(name, url)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddPlaylistDialog(
    strings: it.casatv.tv.ui.AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.t("Aggiungi nuova Playlist", "Add New Playlist", "Añadir nueva Lista"),
                color = CasaText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = strings.t("Nome identificativo (es. Sport, Canali Nazionali):", "Playlist name (e.g. Sports, National):", "Nombre identificativo:"),
                    color = CasaMuted,
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Sport / Canali", color = CasaDim, fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = CasaText),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (it.casatv.tv.ui.theme.CasaIsLight) it.casatv.tv.ui.theme.CasaBlue else Color.White,
                        unfocusedBorderColor = CasaBorder,
                        focusedContainerColor = CasaBackground,
                        unfocusedContainerColor = CasaBackground,
                    ),
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.t("Indirizzo URL M3U / M3U8:", "M3U / M3U8 URL address:", "Dirección URL M3U / M3U8:"),
                    color = CasaMuted,
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://provider.com/playlist.m3u", color = CasaDim, fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = CasaText),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (it.casatv.tv.ui.theme.CasaIsLight) it.casatv.tv.ui.theme.CasaBlue else Color.White,
                        unfocusedBorderColor = CasaBorder,
                        focusedContainerColor = CasaBackground,
                        unfocusedContainerColor = CasaBackground,
                    ),
                )
            }
        },
        confirmButton = {
            TvButton(
                text = strings.t("Salva e Scarica", "Save & Download", "Guardar y Descargar"),
                onClick = {
                    if (url.isNotBlank()) {
                        onConfirm(name, url)
                    }
                },
                primary = true,
                enabled = url.isNotBlank(),
            )
        },
        dismissButton = {
            TvButton(
                text = strings.t("Annulla", "Cancel", "Cancelar"),
                onClick = onDismiss,
                primary = false,
            )
        },
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(16.dp),
    )
}
