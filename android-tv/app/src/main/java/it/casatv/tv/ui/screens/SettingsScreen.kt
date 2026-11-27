package it.casatv.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
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
import it.casatv.tv.ui.countryFacet
import it.casatv.tv.ui.components.TvButton
import it.casatv.tv.ui.formatNumber
import it.casatv.tv.ui.relativeTime
import it.casatv.tv.update.TvUpdateFrequency
import it.casatv.tv.ui.theme.CasaAmber
import it.casatv.tv.ui.theme.CasaBackground
import it.casatv.tv.ui.theme.CasaBlue
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
    val strings = LocalAppStrings.current
    val localStatus by vm.localM3uStatus.collectAsStateWithLifecycle()
    var localUrl by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            vm.importLocalM3uUri(uri)
        }
    }

    SettingsCard(
        title = strings.t("Lista M3U locale (sul televisore)", "Local M3U Playlist (on TV)", "Lista M3U local (en la TV)"),
        subtitle = strings.t(
            "Carica un file (.m3u, .m3u8, .pls, .txt) o URL direttamente su questo dispositivo senza usare il server",
            "Load a file (.m3u, .m3u8, .pls, .txt) or URL directly on this TV without the server",
            "Carga un archivo (.m3u, .m3u8, .pls, .txt) o URL directamente en este televisor",
        ),
    ) {
        if (localStatus.label.isNotBlank() || localStatus.error.isNotBlank() || localStatus.count > 0) {
            val hasErr = localStatus.error.isNotBlank()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background((if (hasErr) CasaRed else CasaGreen).copy(alpha = 0.14f))
                    .border(1.dp, (if (hasErr) CasaRed else CasaGreen).copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = localStatus.label.ifBlank { strings.t("Lista locale", "Local playlist", "Lista local") },
                        color = CasaText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (hasErr) {
                            strings.t("Non funziona", "Not working", "No funciona") + ": ${localStatus.error}"
                        } else {
                            strings.t("Attiva · ${localStatus.count} canali", "Active · ${localStatus.count} channels", "Activa · ${localStatus.count} canales")
                        },
                        color = if (hasErr) CasaRed else CasaGreen,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                TvButton(
                    text = strings.t("Rimuovi", "Remove", "Quitar"),
                    onClick = { vm.clearLocalM3u() },
                    primary = false,
                    icon = Icons.Filled.Delete,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = strings.t(
                "Attenzione: il dispositivo potrebbe non essere in grado di gestire tutti i dati della lista M3U e potrebbe risultare lento su dispositivi di fascia bassa.",
                "Warning: low-RAM TVs may be slower when parsing huge local M3U files directly.",
                "Atención: el dispositivo puede ir más lento con listas M3U locales muy grandes.",
            ),
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
            label = { Text("URL lista M3U / M3U8 / PLS") },
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
                text = strings.t("Carica URL M3U", "Load M3U URL", "Cargar URL M3U"),
                onClick = { vm.importLocalM3uUrl(localUrl) },
                primary = true,
            )
            TvButton(
                text = strings.t("Scegli file locale (.M3U / .M3U8)", "Pick local file (.M3U / .M3U8)", "Elegir archivo (.M3U / .M3U8)"),
                onClick = {
                    try {
                        picker.launch(
                            arrayOf(
                                "audio/x-mpegurl",
                                "application/vnd.apple.mpegurl",
                                "application/x-mpegurl",
                                "text/plain",
                                "*/*",
                            ),
                        )
                    } catch (_: android.content.ActivityNotFoundException) {
                        vm.showMessage(
                            strings.t(
                                "Nessun selettore file disponibile su questa TV",
                                "No file picker available on this TV",
                                "No hay selector de archivos en esta TV",
                            ),
                        )
                    }
                },
                primary = false,
                icon = Icons.Filled.Add,
            )
        }
    }
}

private val LocalSettingsScale = compositionLocalOf { 1f }

private enum class SettingsSection(
    val icon: ImageVector,
    val titleIt: String,
    val titleEn: String,
    val titleEs: String,
    val subtitleIt: String,
    val subtitleEn: String,
    val subtitleEs: String,
) {
    ProfileAndAppearance(
        icon = Icons.Filled.Person,
        titleIt = "Profilo & Aspetto",
        titleEn = "Profile & Appearance",
        titleEs = "Perfil y Aspecto",
        subtitleIt = "Utente, lingua, tema e Multi-TV",
        subtitleEn = "User, language, theme & Multi-TV",
        subtitleEs = "Usuario, idioma, tema y Multi-TV",
    ),
    SourcesAndPlaylists(
        icon = Icons.Filled.PlayArrow,
        titleIt = "Sorgenti & Playlist",
        titleEn = "Sources & Playlists",
        titleEs = "Fuentes y Listas",
        subtitleIt = "Liste M3U, catalogo e file locali",
        subtitleEn = "M3U lists, catalog & local files",
        subtitleEs = "Listas M3U, catálogo y archivos",
    ),
    ServerAndSystem(
        icon = Icons.Filled.Settings,
        titleIt = "Server & Sistema",
        titleEn = "Server & System",
        titleEs = "Servidor y Sistema",
        subtitleIt = "Rete, risorse e aggiornamenti",
        subtitleEn = "Network, resources & updates",
        subtitleEs = "Red, recursos y actualizaciones",
    ),
    ControlsAndAbout(
        icon = Icons.Filled.Info,
        titleIt = "Guida & Info",
        titleEn = "Guide & Info",
        titleEs = "Guía e Info",
        subtitleIt = "Tasti telecomando e versione",
        subtitleEn = "Remote keys & app version",
        subtitleEs = "Teclas del mando y versión",
    ),
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
    val adminUnlocked by vm.adminUnlocked.collectAsStateWithLifecycle()
    val multiTvSync by vm.multiTvSync.collectAsStateWithLifecycle()
    val liveSafetyBuffer by vm.liveSafetyBufferEnabled.collectAsStateWithLifecycle()
    val mixIncludeAudioOnly by vm.mixIncludeAudioOnly.collectAsStateWithLifecycle()
    val showRemoteHintsBanner by vm.showRemoteHintsBanner.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val isServerDisconnected by vm.isServerDisconnected.collectAsStateWithLifecycle()
    val updateInfo by vm.updateInfo.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    var confirmDisconnect by remember { mutableStateOf(false) }
    var countryPicker by remember { mutableStateOf(false) }
    var serverPicker by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf(SettingsSection.ProfileAndAppearance) }
    val rightPaneFirstFocus = remember { FocusRequester() }
    val detailScroll = rememberScrollState()
    val sidebarNavScroll = rememberScrollState()
    val configuration = LocalConfiguration.current
    val s = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        minOf(
            configuration.screenWidthDp / 1280f,
            configuration.screenHeightDp / 720f,
        ).coerceIn(0.75f, 1.35f)
    }

    LaunchedEffect(activeSection) {
        detailScroll.scrollTo(0)
        if (activeSection == SettingsSection.SourcesAndPlaylists || activeSection == SettingsSection.ServerAndSystem) {
            vm.checkAdminAuthStatus()
        }
    }

    BackHandler {
        onBack()
    }

    val countries = remember(config?.facets?.countries, strings.language) {
        buildCountryCatalog(config?.facets?.countries.orEmpty(), strings.language)
    }
    val tvCountryFacet = countryFacet(tvCountry, countries)
        ?: countryFacet(profile?.country, countries)

    CompositionLocalProvider(LocalSettingsScale provides s) {
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = (26 * s).dp, vertical = (16 * s).dp),
            horizontalArrangement = Arrangement.spacedBy((18 * s).dp),
        ) {
            // Left Sidebar: clear cognitive grouping into 4 structured sections + pinned server footer
            val sidebarShape = RoundedCornerShape((20 * s).dp)
            Column(
                modifier = Modifier
                    .width((276 * s).dp.coerceAtLeast(224.dp))
                    .fillMaxHeight()
                    .clip(sidebarShape)
                    .background(CasaSurface)
                    .border(1.dp, CasaBorder, sidebarShape)
                    .padding((14 * s).dp),
            ) {
                TvButton(
                    text = strings.t("Torna alla TV", "Back to TV", "Volver a TV"),
                    onClick = onBack,
                    primary = false,
                    icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height((12 * s).dp))
                Text(
                    text = strings.t("IMPOSTAZIONI", "SETTINGS", "AJUSTES"),
                    color = CasaDim,
                    fontSize = (9.5f * s).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    modifier = Modifier.padding(horizontal = (6 * s).dp),
                )
                Spacer(Modifier.height((6 * s).dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(sidebarNavScroll),
                    verticalArrangement = Arrangement.spacedBy((6 * s).dp),
                ) {
                    val canJumpRight = activeSection != SettingsSection.ControlsAndAbout &&
                        (activeSection != SettingsSection.ProfileAndAppearance || profile != null)
                    SettingsSection.entries.forEach { section ->
                        SettingsNavItem(
                            section = section,
                            selected = activeSection == section,
                            language = strings.language,
                            onClick = { activeSection = section },
                            rightFocus = if (canJumpRight) rightPaneFirstFocus else null,
                        )
                    }
                }

                Spacer(Modifier.height((8 * s).dp))

                // Quick live server status summary anchored at bottom of sidebar (never clipped)
                val footerShape = RoundedCornerShape((12 * s).dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(footerShape)
                        .background(CasaBackground.copy(alpha = 0.65f))
                        .border(1.dp, CasaBorder, footerShape)
                        .padding(horizontal = (11 * s).dp, vertical = (8.5f * s).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size((8 * s).dp)
                            .clip(CircleShape)
                            .background(if (isServerDisconnected) CasaAmber else CasaGreen),
                    )
                    Spacer(Modifier.width((8 * s).dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = config?.name ?: "CasaTV Server",
                            color = CasaText,
                            fontSize = (11.5f * s).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (isServerDisconnected) {
                                strings.t("Modalità offline", "Offline mode", "Modo sin conexión")
                            } else {
                                serverUrl.orEmpty().removePrefix("http://").ifBlank { "Online" }
                            },
                            color = CasaMuted,
                            fontSize = (9.5f * s).sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Right Detail Pane: spacious, cleanly structured cards scaled to screen size
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(detailScroll),
                verticalArrangement = Arrangement.spacedBy((12 * s).dp),
            ) {
                when (activeSection) {
                    SettingsSection.ProfileAndAppearance -> {
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
                                firstActionFocus = rightPaneFirstFocus,
                            )
                        }
                        ThemeSettingsCard(
                            currentTheme = themeMode,
                            onSelectTheme = { vm.setThemeMode(it) },
                        )
                        SettingsCard(
                            title = strings.t("Sincronizzazione Multi-TV", "Multi-TV Synchronization", "Sincronización Multi-TV"),
                            subtitle = strings.t(
                                "Allinea automaticamente audio e video quando più TV in casa guardano lo stesso canale.",
                                "Automatically aligns audio and video when multiple TVs watch the same channel.",
                                "Alinea automáticamente audio y vídeo cuando varias TV ven el mismo canal.",
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
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = strings.t(
                                            "Riduce l'eco tra televisori vicini regolando il flusso in tempo reale.",
                                            "Reduces echo between nearby TVs by aligning playback in real-time.",
                                            "Reduce el eco entre televisores cercanos alineando la reproducción.",
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

                        SettingsCard(
                            title = strings.t("Buffer anti-interruzione", "Anti-freeze safety buffer", "Búfer anti-cortes"),
                            subtitle = strings.t(
                                "Crea gradualmente una riserva invisibile (~8s) e riconnette il canale in background se il flusso cade.",
                                "Gradually builds an invisible ~8s safety cushion and reconnects silently if the stream drops.",
                                "Crea gradualmente un colchón invisible (~8s) y reconecta el canal en segundo plano si se corta.",
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (liveSafetyBuffer) {
                                            strings.t("Protezione anti-blocco attiva", "Anti-freeze protection enabled", "Protección anti-cortes activa")
                                        } else {
                                            strings.t("Riserva disattivata (solo bassa latenza)", "Safety buffer disabled (low latency)", "Búfer desactivado")
                                        },
                                        color = if (liveSafetyBuffer) CasaGreen else CasaMuted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = strings.t(
                                            "Rallenta impercettibilmente all'avvio fino a ~8s per assorbire cali di rete e rigenerare il buffer dopo una riconnessione.",
                                            "Imperceptibly paces playback up to ~8s so temporary network drops recover seamlessly.",
                                            "Modula imperceptiblemente la reproducción hasta ~8s para absorber cortes temporales de red.",
                                        ),
                                        color = CasaDim,
                                        fontSize = 11.sp,
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                TvButton(
                                    text = if (liveSafetyBuffer) strings.t("Disattiva", "Disable", "Desactivar") else strings.t("Attiva", "Enable", "Activar"),
                                    onClick = { vm.setLiveSafetyBufferEnabled(!liveSafetyBuffer) },
                                    primary = !liveSafetyBuffer,
                                )
                            }
                        }

                        SettingsCard(
                            title = strings.t("Origine unica sul server (Live Relay)", "Single-origin Server Relay", "Origen único en servidor"),
                            subtitle = strings.t(
                                "Il server centrale riceve il canale una sola volta e lo ritrasmette alle TV per sincronizzazione precisa e pausa istantanea.",
                                "The central server receives live channels once and redistributes them to TVs for tighter sync and instant pause.",
                                "El servidor central recibe cada canal una sola vez y lo redistribuye a las TV.",
                            ),
                        ) {
                            val serverRelayEnabled by vm.serverRelayEnabled.collectAsStateWithLifecycle()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (serverRelayEnabled) {
                                            strings.t("Attivo (consigliato con server dedicato)", "Enabled (recommended)", "Activo (recomendado)")
                                        } else {
                                            strings.t("Disattivato (connessione diretta da ciascuna TV)", "Disabled (direct playback)", "Desactivado (conexión directa)")
                                        },
                                        color = if (serverRelayEnabled) CasaGreen else CasaMuted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = strings.t(
                                            "Fallback automatico in diretta se un flusso usa un formato o protocollo non supportato.",
                                            "Automatically falls back to direct stream if a channel requires it.",
                                            "Vuelve al directo automáticamente si el flujo no es compatible.",
                                        ),
                                        color = CasaDim,
                                        fontSize = 11.sp,
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                TvButton(
                                    text = if (serverRelayEnabled) strings.t("Disattiva", "Disable", "Desactivar") else strings.t("Attiva", "Enable", "Activar"),
                                    onClick = { vm.setServerRelayEnabled(!serverRelayEnabled) },
                                    primary = !serverRelayEnabled,
                                )
                            }
                        }

                        SettingsCard(
                            title = strings.t("Pausa Live & Registrazione sul server", "Live Pause & Server Recording", "Pausa en directo y grabación"),
                            subtitle = strings.t(
                                "Mantiene la TV accesa in pausa e registra sul server centrale fino al limite scelto (cancellata all'uscita, oppure richiesta alla riapertura se raggiunge il limite).",
                                "Keeps the TV awake while paused and records on the central server up to your hour limit.",
                                "Mantiene la TV encendida en pausa y graba en el servidor central hasta el límite configurado.",
                            ),
                        ) {
                            val maxHours by vm.timeshiftMaxHours.collectAsStateWithLifecycle()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(1, 2, 3, 4, 6, 8).forEach { h ->
                                    TvButton(
                                        text = if (h == 1) {
                                            strings.t("1 ora", "1 hour", "1 hora")
                                        } else {
                                            strings.t("$h ore", "$h hours", "$h horas")
                                        },
                                        onClick = { vm.setTimeshiftMaxHours(h) },
                                        primary = maxHours == h,
                                    )
                                }
                            }
                        }

                        SettingsCard(
                            title = strings.t("Canali solo audio in Mix", "Audio-only channels in Mix", "Canales solo audio en Mix"),
                            subtitle = strings.t(
                                "Esclude di default radio e flussi senza video (es. FM) dallo zapping casuale Mix.",
                                "Excludes radio and video-less streams (e.g. FM) from Mix shuffle by default.",
                                "Excluye por defecto radios y flujos sin vídeo (ej. FM) del Mix aleatorio.",
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (mixIncludeAudioOnly) {
                                            strings.t("Inclusi nello zapping Mix", "Included in Mix shuffle", "Incluidos en el Mix")
                                        } else {
                                            strings.t("Esclusi di default (solo canali con video)", "Excluded by default (video channels only)", "Excluidos por defecto (solo vídeo)")
                                        },
                                        color = if (mixIncludeAudioOnly) CasaGreen else CasaMuted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = strings.t(
                                            "Attiva se vuoi includere le stazioni radio e i canali audio durante lo zapping Mix.",
                                            "Enable to include radio stations and audio-only channels during Mix zapping.",
                                            "Actívalo si deseas incluir emisoras de radio y canales de solo audio en Mix.",
                                        ),
                                        color = CasaDim,
                                        fontSize = 11.sp,
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                TvButton(
                                    text = if (mixIncludeAudioOnly) strings.t("Disattiva", "Disable", "Desactivar") else strings.t("Attiva", "Enable", "Activar"),
                                    onClick = { vm.setMixIncludeAudioOnly(!mixIncludeAudioOnly) },
                                    primary = !mixIncludeAudioOnly,
                                )
                            }
                        }

                        SettingsCard(
                            title = strings.t("Banner comandi telecomando", "Remote controls hint banner", "Banner de controles del mando"),
                            subtitle = strings.t(
                                "Mostra in alto per 6 secondi le indicazioni dei tasti all'apertura del player TV.",
                                "Shows remote key hints at the top for 6 seconds when opening the TV player.",
                                "Muestra arriba durante 6 segundos las teclas del mando al abrir el reproductor de TV.",
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (showRemoteHintsBanner) {
                                            strings.t("Banner temporaneo attivo (6s)", "Temporary banner enabled (6s)", "Banner temporal activo (6s)")
                                        } else {
                                            strings.t("Banner nascosto", "Banner disabled", "Banner desactivado")
                                        },
                                        color = if (showRemoteHintsBanner) CasaGreen else CasaMuted,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = strings.t(
                                            "Se disattivato, resta visibile solo la barra inferiore essenziale con nome, paese e orario.",
                                            "When disabled, only the minimal bottom bar with channel name, country and clocks stays visible.",
                                            "Si está desactivado, solo se muestra la barra inferior con canal, país y relojes.",
                                        ),
                                        color = CasaDim,
                                        fontSize = 11.sp,
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                TvButton(
                                    text = if (showRemoteHintsBanner) strings.t("Disattiva", "Disable", "Desactivar") else strings.t("Attiva", "Enable", "Activar"),
                                    onClick = { vm.setShowRemoteHintsBanner(!showRemoteHintsBanner) },
                                    primary = !showRemoteHintsBanner,
                                )
                            }
                        }
                    }

                    SettingsSection.SourcesAndPlaylists -> {
                        if (adminUnlocked) {
                            PlaylistsSettingsCard(
                                vm = vm,
                                strings = strings,
                                firstActionFocus = rightPaneFirstFocus,
                            )
                            SettingsCard(
                                title = strings.t("Panoramica Catalogo", "Catalog Overview", "Resumen del Catálogo"),
                                subtitle = strings.t(
                                    "Canali e statistiche attualmente disponibili sul server CasaTV.",
                                    "Channels and statistics currently available on the CasaTV server.",
                                    "Canales y estadísticas actualmente disponibles en el servidor CasaTV.",
                                ),
                            ) {
                                val stats = config?.stats ?: ServerStats()
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatCell(
                                        strings.t("Canali TV", "Live TV", "Canales TV"),
                                        formatNumber(stats.channels, strings.language),
                                        Modifier.weight(1f),
                                        accent = true,
                                    )
                                    StatCell(
                                        strings.t("Flussi Live", "Live Streams", "Flujos TV"),
                                        formatNumber(stats.streams, strings.language),
                                        Modifier.weight(1f),
                                    )
                                    StatCell(
                                        strings.t("Film", "Movies", "Películas"),
                                        formatNumber(stats.movies, strings.language),
                                        Modifier.weight(1f),
                                    )
                                    StatCell(
                                        strings.t("Serie TV", "Series", "Series"),
                                        formatNumber(stats.series, strings.language),
                                        Modifier.weight(1f),
                                    )
                                    StatCell(
                                        strings.t("Paesi", "Countries", "Países"),
                                        formatNumber(stats.countries, strings.language),
                                        Modifier.weight(1f),
                                    )
                                    StatCell(
                                        strings.t("Preferiti", "Favorites", "Favoritos"),
                                        formatNumber(stats.favorites, strings.language),
                                        Modifier.weight(1f),
                                    )
                                }
                            }
                        } else {
                            AdminPinGateCard(
                                vm = vm,
                                strings = strings,
                                subtitle = strings.t(
                                    "Sblocca con la password amministratore per gestire le playlist M3U del server.",
                                    "Unlock with admin password to manage server M3U playlists.",
                                    "Desbloquea con la contraseña de administrador para gestionar las listas del servidor.",
                                ),
                                firstActionFocus = rightPaneFirstFocus,
                            )
                        }
                        LocalTvM3uCard(vm = vm)
                    }

                    SettingsSection.ServerAndSystem -> {
                        ConnectionCard(
                            config = config,
                            serverUrl = serverUrl.orEmpty(),
                            onChangeServer = {
                                vm.refreshDiscovery()
                                serverPicker = true
                            },
                            firstActionFocus = rightPaneFirstFocus,
                        )
                        if (adminUnlocked) {
                            AdminSecurityCard(vm = vm, strings = strings)
                            SettingsCard(
                                title = strings.t("Aggiornamenti & Azioni Server", "Updates & Server Actions", "Actualizaciones y Acciones"),
                                subtitle = strings.t(
                                    "Cerca nuove versioni su GitHub Releases (${BuildConfig.GITHUB_REPO}) o sincronizza il server.",
                                    "Check for new versions on GitHub Releases (${BuildConfig.GITHUB_REPO}) or sync the server.",
                                    "Busca nuevas versiones en GitHub Releases (${BuildConfig.GITHUB_REPO}) o sincroniza el servidor.",
                                ),
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
                                    )
                                    TvButton(
                                        text = strings.t("Aggiorna catalogo", "Update catalog", "Actualizar catálogo"),
                                        onClick = { vm.refreshCatalog() },
                                        primary = false,
                                        icon = Icons.Filled.Refresh,
                                    )
                                    TvButton(
                                        text = strings.t("Verifica aggiornamenti", "Check updates", "Buscar actualizaciones"),
                                        onClick = { vm.checkForUpdates(silent = false) },
                                        primary = true,
                                        icon = Icons.Filled.Refresh,
                                    )
                                    TvButton(
                                        text = strings.t("Scollega", "Disconnect", "Desconectar"),
                                        onClick = { confirmDisconnect = true },
                                        primary = false,
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                val updateFreq by vm.updateFrequency.collectAsStateWithLifecycle()
                                Text(
                                    text = strings.t(
                                        "Frequenza controllo automatico aggiornamenti",
                                        "Automatic update check frequency",
                                        "Frecuencia de búsqueda automática de actualizaciones",
                                    ),
                                    color = CasaMuted,
                                    fontSize = 12.sp,
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
                            SystemCard(
                                system = system,
                                busy = systemBusy,
                                onRefresh = { vm.refreshSystem() },
                            )
                        } else {
                            AdminPinGateCard(
                                vm = vm,
                                strings = strings,
                                subtitle = strings.t(
                                    "Inserisci la password amministratore per vedere utenti attivi, dispositivi e azioni server.",
                                    "Enter the admin password to view connected users, playback and server actions.",
                                    "Introduce la contraseña para ver usuarios conectados y ajustes del servidor.",
                                ),
                                firstActionFocus = null,
                            )
                        }
                    }

                    SettingsSection.ControlsAndAbout -> {
                        SettingsCard(
                            title = strings.t("Guida al Telecomando", "Remote Control Guide", "Guía del Mando"),
                            subtitle = strings.t(
                                "Tasti rapidi per navigare e guardare la TV senza sforzo.",
                                "Shortcuts for fast navigation and watching TV.",
                                "Atajos para navegar y ver la TV cómodamente.",
                            ),
                        ) {
                            RemoteRow("SINISTRA", strings.t("Apre il menù rapido canali laterale con anteprima live", "Opens the quick left-side channel drawer with live preview", "Abre el menú rápido lateral de canales con vista previa"))
                            RemoteRow("DESTRA", strings.t("Apre il pannello Sorgenti del canale (o Episodi/Simili su Film e Serie)", "Opens channel Sources drawer (or Episodes/Similar on Movies & Series)", "Abre panel de Fuentes del canal (o Episodios en Cine/Series)"))
                            RemoteRow("SU / GIÙ", strings.t("Cambia canale (o scorre il menù laterale)", "Changes channel (or browses the left drawer)", "Cambia de canal (o explora el menú lateral)"))
                            RemoteRow("OK (breve / lungo)", strings.t("Apre il canale / tieni premuto per il menù contestuale", "Opens channel / hold to open the context menu", "Abre canal / mantén pulsado para menú contextual"))
                            RemoteRow("OK (tieni 3s nel player)", strings.t("Avvia o ferma la registrazione del canale (salvata 7 giorni)", "Starts or stops channel recording (saved for 7 days)", "Inicia o detiene la grabación del canal (guardada 7 días)"))
                            RemoteRow("0 – 9", strings.t("Digita direttamente il numero del canale", "Type a channel number directly", "Introduce el número de canal directamente"))
                            RemoteRow("MENU", strings.t("Apre opzioni audio/sottotitoli o aggiunge ai Preferiti", "Opens audio/subtitle options or toggles Favorite", "Abre opciones de audio/subtítulos o Favoritos"))
                            RemoteRow("INFO", strings.t("Cambia paese della modalità TV (o regioni del Mix)", "Changes TV country (or Mix world regions)", "Cambia el país del modo TV (o regiones del Mix)"))
                            RemoteRow("BACK", strings.t("Chiude il menù aperto o esce dal player", "Closes the open menu or exits playback", "Cierra el menú abierto o sale del reproductor"))
                        }
                        SettingsCard(
                            title = strings.t("Informazioni su CasaTV", "About CasaTV", "Acerca de CasaTV"),
                            subtitle = null,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = strings.t("CasaTV per Android TV", "CasaTV for Android TV", "CasaTV para Android TV"),
                                        color = CasaText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = strings.t(
                                            "Versione app ${BuildConfig.VERSION_NAME} · Server ${config?.version ?: "—"}",
                                            "App version ${BuildConfig.VERSION_NAME} · Server ${config?.version ?: "—"}",
                                            "Versión de la app ${BuildConfig.VERSION_NAME} · Servidor ${config?.version ?: "—"}",
                                        ),
                                        color = CasaMuted,
                                        fontSize = 12.sp,
                                    )
                                    val update = updateInfo ?: system?.update?.takeIf { it.updateAvailable }
                                    if (update?.updateAvailable == true) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = strings.t(
                                                "Aggiornamento disponibile (GitHub): v${update.latest}",
                                                "Update available (GitHub): v${update.latest}",
                                                "Actualización disponible (GitHub): v${update.latest}",
                                            ),
                                            color = CasaAmber,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = strings.t(
                                            "Playlist locali · Streaming LAN",
                                            "Local playlists · LAN streaming",
                                            "Listas locales · Streaming LAN",
                                        ),
                                        color = CasaDim,
                                        fontSize = 11.sp,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "GitHub Releases · ${BuildConfig.GITHUB_REPO}",
                                        color = CasaDim,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
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
}

@Composable
private fun SettingsNavItem(
    section: SettingsSection,
    selected: Boolean,
    language: String,
    onClick: () -> Unit,
    rightFocus: FocusRequester? = null,
) {
    val s = LocalSettingsScale.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val shape = RoundedCornerShape((13 * s).dp)

    val bg = when {
        focused -> if (isLight) CasaBlue else Color.White
        selected -> if (isLight) Color(0x1F0070F3) else Color(0x26FFFFFF)
        else -> Color.Transparent
    }
    val titleCol = when {
        focused -> if (isLight) Color.White else Color.Black
        selected -> if (isLight) CasaBlue else Color.White
        else -> CasaText
    }
    val subCol = when {
        focused -> if (isLight) Color(0xCCFFFFFF) else Color(0x99000000)
        selected -> if (isLight) CasaBlue.copy(alpha = 0.8f) else CasaMuted
        else -> CasaDim
    }
    val borderCol = when {
        focused -> if (isLight) CasaBlue else Color.White
        selected -> if (isLight) Color(0x550070F3) else CasaGreen.copy(alpha = 0.65f)
        else -> Color.Transparent
    }

    val title = when (language.lowercase().take(2)) {
        "en" -> section.titleEn
        "es" -> section.titleEs
        else -> section.titleIt
    }
    val subtitle = when (language.lowercase().take(2)) {
        "en" -> section.subtitleEn
        "es" -> section.subtitleEs
        else -> section.subtitleIt
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, borderCol, shape)
            .then(
                if (rightFocus != null) {
                    Modifier.focusProperties { right = rightFocus }
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = (11 * s).dp, vertical = (9 * s).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size((32 * s).dp)
                .clip(RoundedCornerShape((9 * s).dp))
                .background(
                    when {
                        focused -> if (isLight) Color(0x28FFFFFF) else Color(0x16000000)
                        selected -> if (isLight) Color(0x220070F3) else CasaGreen.copy(alpha = 0.16f)
                        else -> if (isLight) Color(0x0E000000) else Color(0x14FFFFFF)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = if (selected && !focused) (if (isLight) CasaBlue else CasaGreen) else titleCol,
                modifier = Modifier.size((17 * s).dp),
            )
        }
        Spacer(Modifier.width((10 * s).dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleCol,
                fontSize = (12.5f * s).sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height((2 * s).dp))
            Text(
                text = subtitle,
                color = subCol,
                fontSize = (9.5f * s).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
    firstActionFocus: FocusRequester? = null,
) {
    val strings = LocalAppStrings.current
    val s = LocalSettingsScale.current
    SettingsCard(
        title = strings.t("Profilo e preferenze", "Profile and preferences", "Perfil y preferencias"),
        subtitle = strings.t(
            "Lingua, paese e preferiti sono salvati per questo profilo.",
            "Language, country and favorites are saved for this profile.",
            "El idioma, el país y los favoritos se guardan para este perfil.",
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val avatarShape = RoundedCornerShape((15 * s).dp)
            Box(
                modifier = Modifier
                    .size((54 * s).dp)
                    .clip(avatarShape)
                    .background(CasaBackground.copy(alpha = 0.8f))
                    .border(1.dp, CasaBorder, avatarShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = profile.avatar, fontSize = (28 * s).sp)
            }
            Spacer(Modifier.width((12 * s).dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    color = CasaText,
                    fontSize = (17 * s).sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height((3 * s).dp))
                Text(
                    text = "${profile.flag} ${profile.countryName} · ${profile.displayLanguage}",
                    color = CasaMuted,
                    fontSize = (10.5f * s).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvButton(
                text = strings.t("Cambia profilo", "Switch profile", "Cambiar perfil"),
                onClick = onSwitchProfile,
                primary = false,
                modifier = if (firstActionFocus != null) Modifier.focusRequester(firstActionFocus) else Modifier,
            )
        }

        Spacer(Modifier.height((14 * s).dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings.t("LINGUA DELL’APP", "APP LANGUAGE", "IDIOMA DE LA APP"),
                color = CasaDim,
                fontSize = (9 * s).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                modifier = Modifier.weight(1f),
            )
            languages.forEach { entry ->
                TvButton(
                    text = entry.name,
                    onClick = { if (entry.code != profile.language) onLanguage(entry.code) },
                    primary = entry.code == profile.language,
                    modifier = Modifier.padding(start = (8 * s).dp),
                )
            }
        }

        Spacer(Modifier.height((13 * s).dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = strings.t("PAESE PREFERITO", "PREFERRED COUNTRY", "PAÍS PREFERIDO"),
                    color = CasaDim,
                    fontSize = (9 * s).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )
                Spacer(Modifier.height((4 * s).dp))
                Text(
                    text = "${profile.flag} ${profile.countryName}",
                    color = CasaText,
                    fontSize = (12.5f * s).sp,
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
    val s = LocalSettingsScale.current
    val isLight = currentTheme == "light"
    SettingsCard(
        title = strings.t("Tema", "Theme", "Tema"),
        subtitle = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((14 * s).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvButton(
                text = strings.t("CHIARO", "LIGHT", "CLARO"),
                onClick = { onSelectTheme("light") },
                primary = isLight,
            )
            TvButton(
                text = strings.t("SCURO", "DARK", "OSCURO"),
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
    firstActionFocus: FocusRequester? = null,
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
                modifier = if (firstActionFocus != null) Modifier.focusRequester(firstActionFocus) else Modifier,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (critical) CasaRed else CasaAmber,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = strings.t(
                            "Risorse quasi esaurite: ${alert.message}. Chiudi app o flussi non necessari sul server.",
                            "Resources almost exhausted: ${alert.message}. Close unneeded apps or streams on the server.",
                            "Recursos casi agotados: ${alert.message}. Cierra aplicaciones o flujos innecesarios en el servidor.",
                        ),
                        color = if (critical) CasaRed else CasaAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = LocalSettingsScale.current
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val cardShape = RoundedCornerShape((18 * s).dp)
    val cardBg = if (isLight) it.casatv.tv.ui.theme.CasaSurface else Color(0x14FFFFFF)
    val cardBorder = if (isLight) it.casatv.tv.ui.theme.CasaBorder else Color(0x24FFFFFF)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBg)
            .border(
                width = 1.dp,
                color = cardBorder,
                shape = cardShape,
            )
            .padding(horizontal = (20 * s).dp, vertical = (16 * s).dp),
    ) {
        Text(
            text = title,
            color = CasaText,
            fontSize = (15.5f * s).sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height((3 * s).dp))
            Text(
                text = subtitle,
                color = CasaDim,
                fontSize = (10.5f * s).sp,
            )
        }
        Spacer(Modifier.height((12 * s).dp))
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
    val s = LocalSettingsScale.current
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val cellShape = RoundedCornerShape((13 * s).dp)
    Column(
        modifier = modifier
            .clip(cellShape)
            .background(if (isLight) CasaBackground.copy(alpha = 0.7f) else Color(0x14FFFFFF))
            .border(1.dp, CasaBorder, cellShape)
            .padding(horizontal = (13 * s).dp, vertical = (9 * s).dp),
    ) {
        Text(
            text = label,
            color = CasaDim,
            fontSize = (9 * s).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height((4 * s).dp))
        Text(
            text = value,
            color = if (accent) (if (isLight) CasaBlue else CasaGreen) else CasaText,
            fontSize = (16.5f * s).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun RemoteRow(key: String, action: String) {
    val s = LocalSettingsScale.current
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val keyShape = RoundedCornerShape((9 * s).dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = (3.5f * s).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width((88 * s).dp)
                .clip(keyShape)
                .background(if (isLight) CasaBackground else Color(0x1FFFFFFF))
                .border(1.dp, CasaBorder, keyShape)
                .padding(vertical = (4.5f * s).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = key,
                color = if (isLight) CasaBlue else Color(0xFFFF9F0A),
                fontSize = (10.5f * s).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.width((12 * s).dp))
        Text(
            text = action,
            color = CasaMuted,
            fontSize = (11.5f * s).sp,
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
private fun AdminPinGateCard(
    vm: MainViewModel,
    strings: it.casatv.tv.ui.AppStrings,
    subtitle: String,
    firstActionFocus: FocusRequester? = null,
) {
    val isDefault by vm.adminIsDefaultPin.collectAsStateWithLifecycle()
    val mustChange by vm.adminMustChangePin.collectAsStateWithLifecycle()
    val busy by vm.adminAuthBusy.collectAsStateWithLifecycle()
    val authErr by vm.adminAuthError.collectAsStateWithLifecycle()
    var pinInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    SettingsCard(
        title = if (mustChange) {
            strings.t("Imposta nuova password server", "Set New Server Password", "Nueva contraseña del servidor")
        } else {
            strings.t("Area Server Protetta", "Protected Server Area", "Área Protegida del Servidor")
        },
        subtitle = if (mustChange) {
            strings.t(
                "La password predefinita 0000 deve essere cambiata prima di accedere.",
                "The default 0000 password must be changed before accessing.",
                "La contraseña inicial 0000 debe cambiarse antes de continuar.",
            )
        } else {
            subtitle
        },
    ) {
        if (isDefault && !mustChange) {
            Text(
                text = strings.t(
                    "Password iniziale predefinita: 0000 (verrà chiesto di cambiarla).",
                    "Default initial password: 0000 (you will be asked to change it).",
                    "Contraseña inicial: 0000 (se pedirá cambiarla).",
                ),
                color = CasaAmber,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (!authErr.isNullOrBlank()) {
            Text(
                text = authErr.orEmpty(),
                color = CasaRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (mustChange) {
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it.take(32) },
                label = { Text(strings.t("Nuova password (min. 4 caratteri)", "New password (min 4 chars)", "Nueva contraseña")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CasaText,
                    unfocusedTextColor = CasaText,
                    focusedBorderColor = CasaBlue,
                    unfocusedBorderColor = CasaBorder,
                ),
                modifier = Modifier.fillMaxWidth().let { if (firstActionFocus != null) it.focusRequester(firstActionFocus) else it },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it.take(32) },
                label = { Text(strings.t("Conferma nuova password", "Confirm new password", "Confirmar contraseña")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CasaText,
                    unfocusedTextColor = CasaText,
                    focusedBorderColor = CasaBlue,
                    unfocusedBorderColor = CasaBorder,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            TvButton(
                text = strings.t("Salva e sblocca", "Save & unlock", "Guardar y desbloquear"),
                onClick = { if (!busy) vm.changeAdminPin(null, newPin, confirmPin) },
                primary = true,
            )
        } else {
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it.take(32) },
                label = { Text(strings.t("Password amministratore", "Admin password", "Contraseña de administrador")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CasaText,
                    unfocusedTextColor = CasaText,
                    focusedBorderColor = CasaBlue,
                    unfocusedBorderColor = CasaBorder,
                ),
                modifier = Modifier.fillMaxWidth().let { if (firstActionFocus != null) it.focusRequester(firstActionFocus) else it },
            )
            Spacer(Modifier.height(10.dp))
            TvButton(
                text = strings.t("Sblocca", "Unlock", "Desbloquear"),
                onClick = { if (!busy) vm.unlockAdmin(pinInput) },
                primary = true,
            )
        }
    }
}

@Composable
private fun AdminSecurityCard(
    vm: MainViewModel,
    strings: it.casatv.tv.ui.AppStrings,
) {
    var showChange by remember { mutableStateOf(false) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val authErr by vm.adminAuthError.collectAsStateWithLifecycle()

    SettingsCard(
        title = strings.t("Sicurezza Amministratore", "Admin Security", "Seguridad de Administrador"),
        subtitle = strings.t(
            "Gestisci la password per le impostazioni del server e gli utenti connessi.",
            "Manage password protection for server settings and active users.",
            "Gestiona la contraseña para los ajustes del servidor y usuarios conectados.",
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(
                text = strings.t("Cambia password", "Change password", "Cambiar contraseña"),
                onClick = { showChange = !showChange },
                primary = false,
            )
            TvButton(
                text = strings.t("Blocca area server", "Lock server area", "Bloquear área"),
                onClick = { vm.lockAdmin() },
                primary = false,
            )
        }
        if (showChange) {
            Spacer(Modifier.height(10.dp))
            if (!authErr.isNullOrBlank()) {
                Text(text = authErr.orEmpty(), color = CasaRed, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }
            OutlinedTextField(
                value = oldPin,
                onValueChange = { oldPin = it.take(32) },
                label = { Text(strings.t("Password attuale", "Current password", "Contraseña actual")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CasaText,
                    unfocusedTextColor = CasaText,
                    focusedBorderColor = CasaBlue,
                    unfocusedBorderColor = CasaBorder,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it.take(32) },
                label = { Text(strings.t("Nuova password", "New password", "Nueva contraseña")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CasaText,
                    unfocusedTextColor = CasaText,
                    focusedBorderColor = CasaBlue,
                    unfocusedBorderColor = CasaBorder,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it.take(32) },
                label = { Text(strings.t("Conferma nuova password", "Confirm new password", "Confirmar contraseña")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CasaText,
                    unfocusedTextColor = CasaText,
                    focusedBorderColor = CasaBlue,
                    unfocusedBorderColor = CasaBorder,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            TvButton(
                text = strings.t("Salva password", "Save password", "Guardar contraseña"),
                onClick = { vm.changeAdminPin(oldPin, newPin, confirmPin) },
                primary = true,
            )
        }
    }
}

@Composable
private fun PlaylistsSettingsCard(
    vm: MainViewModel,
    strings: it.casatv.tv.ui.AppStrings,
    firstActionFocus: FocusRequester? = null,
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showAllPlaylistsDialog by remember { mutableStateOf(false) }
    val uploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            vm.uploadServerPlaylistUri(uri)
        }
    }

    LaunchedEffect(Unit) {
        vm.loadPlaylists()
    }

    SettingsCard(
        title = strings.t("Playlist canali M3U", "M3U Channel Playlists", "Listas de canales M3U"),
        subtitle = strings.t(
            "Carica file M3U, M3U8 o PLS sul server oppure aggiungi un link remoto.",
            "Upload M3U, M3U8 or PLS files to the server or add a remote link.",
            "Sube archivos M3U, M3U8 o PLS al servidor o añade un enlace remoto.",
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
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(
                    text = strings.t("Carica file (.M3U / .M3U8)", "Upload file (.M3U / .M3U8)", "Subir archivo (.M3U / .M3U8)"),
                    onClick = {
                        try {
                            uploadPicker.launch(
                                arrayOf(
                                    "audio/x-mpegurl",
                                    "application/vnd.apple.mpegurl",
                                    "application/x-mpegurl",
                                    "text/plain",
                                    "*/*",
                                ),
                            )
                        } catch (_: android.content.ActivityNotFoundException) {
                            vm.showMessage(
                                strings.t(
                                    "Nessun selettore file disponibile su questa TV",
                                    "No file picker available on this TV",
                                    "No hay selector de archivos en esta TV",
                                ),
                            )
                        }
                    },
                    primary = true,
                    icon = Icons.Filled.Add,
                    modifier = if (firstActionFocus != null) Modifier.focusRequester(firstActionFocus) else Modifier,
                )
                TvButton(
                    text = strings.t("+ Aggiungi Link", "+ Add Link", "+ Añadir Enlace"),
                    onClick = { showAddDialog = true },
                    primary = false,
                    icon = Icons.Filled.Add,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (playlists.isEmpty()) {
            Text(
                text = strings.t(
                    "Nessuna playlist configurata sul server. Usa 'Carica file' o '+ Aggiungi Link'.",
                    "No playlists configured on the server. Use 'Upload file' or '+ Add Link'.",
                    "No hay listas configuradas en el servidor. Usa 'Subir archivo' o '+ Añadir Enlace'.",
                ),
                color = CasaMuted,
                fontSize = 12.sp,
            )
        } else {
            val visiblePlaylists = remember(playlists) { playlists.take(5) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visiblePlaylists.forEach { p ->
                    PlaylistItemRow(p = p, vm = vm, strings = strings)
                }
                if (playlists.size > 5) {
                    Spacer(Modifier.height(2.dp))
                    TvButton(
                        text = strings.t(
                            "Mostra e gestisci tutte (${playlists.size})",
                            "Show & manage all (${playlists.size})",
                            "Ver y gestionar todas (${playlists.size})",
                        ),
                        onClick = { showAllPlaylistsDialog = true },
                        primary = false,
                    )
                }
            }
        }
    }

    if (showAllPlaylistsDialog) {
        AllPlaylistsDialog(
            playlists = playlists,
            vm = vm,
            strings = strings,
            onDismiss = { showAllPlaylistsDialog = false },
        )
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
private fun PlaylistItemRow(
    p: it.casatv.tv.data.PlaylistDto,
    vm: MainViewModel,
    strings: it.casatv.tv.ui.AppStrings,
) {
    val isError = p.status == "error" || p.channelCount == 0
    val isWarn = !isError && p.status == "warn"
    val statusColor = when {
        isError -> CasaRed
        isWarn -> CasaAmber
        else -> CasaGreen
    }
    val statusLabel = when {
        isError -> strings.t("Non funziona", "Not working", "No funciona")
        isWarn -> strings.t("Avviso", "Warning", "Aviso")
        else -> strings.t("Attiva", "Active", "Activa")
    }
    val srcTag = if (p.url.isNotBlank()) "LINK" else "FILE"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isError) CasaRed.copy(alpha = 0.08f) else Color(0x14FFFFFF))
            .border(
                1.dp,
                if (isError) CasaRed.copy(alpha = 0.7f) else Color(0x1FFFFFFF),
                RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Text(
                    text = p.name,
                    color = CasaText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusLabel.uppercase(),
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(
                    text = srcTag,
                    color = CasaMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x1FFFFFFF))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${p.channelCount} canali" + if (p.url.isNotBlank()) " · ${p.url}" else " · File caricato",
                color = CasaMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (p.lastError.isNotBlank() || isError) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = p.lastError.ifBlank {
                        strings.t("Playlist non valida o senza canali funzionanti", "Invalid playlist or no working channels", "Lista no válida")
                    },
                    color = CasaRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TvButton(
            text = strings.t("Verifica", "Check", "Verificar"),
            onClick = { vm.checkPlaylist(p.id) },
            primary = false,
            icon = Icons.Filled.Warning.takeIf { isError },
        )
        if (p.url.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            TvButton(
                text = strings.t("Aggiorna", "Refresh", "Actualizar"),
                onClick = { vm.refreshPlaylist(p.id) },
                primary = false,
                icon = Icons.Filled.Refresh,
            )
        }
        Spacer(Modifier.width(6.dp))
        TvButton(
            text = strings.t("Elimina", "Delete", "Eliminar"),
            onClick = { vm.deletePlaylist(p.id) },
            primary = false,
            icon = Icons.Filled.Delete,
        )
    }
}

@Composable
private fun AllPlaylistsDialog(
    playlists: List<it.casatv.tv.data.PlaylistDto>,
    vm: MainViewModel,
    strings: it.casatv.tv.ui.AppStrings,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CasaSurface,
        titleContentColor = CasaText,
        textContentColor = CasaText,
        title = {
            Text(
                text = strings.t(
                    "Tutte le playlist e sorgenti (${playlists.size})",
                    "All playlists & sources (${playlists.size})",
                    "Todas las listas y fuentes (${playlists.size})",
                ),
                color = CasaText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(playlists, key = { it.id }) { p ->
                    PlaylistItemRow(p = p, vm = vm, strings = strings)
                }
            }
        },
        confirmButton = {
            TvButton(
                text = strings.t("Chiudi", "Close", "Cerrar"),
                onClick = onDismiss,
                primary = true,
            )
        },
    )
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
                    placeholder = { Text("https://...", color = CasaDim, fontSize = 12.sp) },
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
        containerColor = CasaSurfaceHigh,
        shape = RoundedCornerShape(16.dp),
    )
}
