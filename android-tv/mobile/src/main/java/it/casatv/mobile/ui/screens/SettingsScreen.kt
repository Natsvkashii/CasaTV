package it.casatv.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.casatv.mobile.BuildConfig
import it.casatv.mobile.data.ConnectionMode
import it.casatv.mobile.ui.LocalMobileStrings
import it.casatv.mobile.ui.MobileUiState
import it.casatv.mobile.ui.SUPPORTED_LANGUAGES
import it.casatv.mobile.update.UpdateFrequency

@Composable
fun SettingsScreen(
    state: MobileUiState,
    onSetLanguage: (String) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAutoServer: (Boolean) -> Unit,
    onSetAutoSaveServerM3u: (Boolean) -> Unit,
    onSetBackgroundPlay: (Boolean) -> Unit,
    onSetPipOnLeave: (Boolean) -> Unit,
    onSearchServerNow: () -> Unit,
    onSetUpdateFrequency: (UpdateFrequency) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val strings = LocalMobileStrings.current
    val lang = state.language.lowercase().take(2)
    fun tr(it: String, en: String, es: String): String = when (lang) {
        "en" -> en
        "es" -> es
        else -> it
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            // Compact summary hero card at top for immediate situational awareness
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "CasaTV Mobile · v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = strings.get("about_stats", state.allChannels.size, state.playlists.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state.connectionMode == ConnectionMode.SERVER_CONNECTED) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    ),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when (state.connectionMode) {
                                    ConnectionMode.SERVER_CONNECTED -> tr("Wi-Fi", "Wi-Fi", "Wi-Fi")
                                    ConnectionMode.SEARCHING_WIFI -> tr("Cerco…", "Searching…", "Buscando…")
                                    ConnectionMode.LOCAL_ONLY -> tr("Locale", "Local", "Local")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // Section 1: Aspetto & Lingua
        item {
            SettingsGroup(
                eyebrow = tr("ASPETTO & LINGUA", "APPEARANCE & LANGUAGE", "ASPECTO E IDIOMA"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.darkThemeTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (state.darkTheme) strings.darkThemeOn else strings.darkThemeOff,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.darkTheme,
                        onCheckedChange = onSetDarkTheme,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = strings.languageTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SUPPORTED_LANGUAGES, key = { it.code }) { option ->
                        FilterChip(
                            selected = state.language == option.code,
                            onClick = { onSetLanguage(option.code) },
                            label = { Text(option.nativeName) },
                        )
                    }
                }
            }
        }

        // Section 2: Riproduzione, PiP & Chromecast
        item {
            SettingsGroup(
                eyebrow = tr(
                    "RIPRODUZIONE, PIP & CHROMECAST",
                    "PLAYBACK, PIP & CHROMECAST",
                    "REPRODUCCIÓN, PIP Y CHROMECAST",
                ),
            ) {
                SettingsToggleRow(
                    icon = Icons.Default.PictureInPictureAlt,
                    title = tr(
                        "Picture-in-Picture automatico",
                        "Automatic Picture-in-Picture",
                        "Picture-in-Picture automático",
                    ),
                    subtitle = tr(
                        "Riduci il video in una finestra mobile quando esci dall'app.",
                        "Shrink video into a floating window when leaving the app.",
                        "Reduce el vídeo a una ventana flotante al salir de la app.",
                    ),
                    checked = state.pipOnLeaveEnabled,
                    onCheckedChange = onSetPipOnLeave,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                SettingsToggleRow(
                    icon = Icons.Default.Headphones,
                    title = tr(
                        "Riproduzione in background",
                        "Background playback",
                        "Reproducción en segundo plano",
                    ),
                    subtitle = tr(
                        "Continua ad ascoltare la diretta a schermo spento o in altre app.",
                        "Keep live audio playing when screen is off or in other apps.",
                        "Sigue escuchando el directo con la pantalla apagada o en otras apps.",
                    ),
                    checked = state.backgroundPlayEnabled,
                    onCheckedChange = onSetBackgroundPlay,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Chromecast",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = tr(
                                "Tocca l'icona Cast nel player per trasmettere su TV o dispositivi Google Cast.",
                                "Tap the Cast icon inside the player to stream to TVs or Chromecast.",
                                "Toca el icono Cast en el reproductor para enviar a tu TV o Chromecast.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Section 3: Server CasaTV Wi-Fi
        item {
            SettingsGroup(
                eyebrow = tr("SERVER CASATV WI-FI", "CASATV WI-FI SERVER", "SERVIDOR CASATV WI-FI"),
            ) {
                SettingsToggleRow(
                    icon = Icons.Default.Wifi,
                    title = strings.wifiServerTitle,
                    subtitle = strings.wifiServerSubtitle,
                    checked = state.autoConnectWifiServer,
                    onCheckedChange = onSetAutoServer,
                )

                val statusText = when (state.connectionMode) {
                    ConnectionMode.SERVER_CONNECTED ->
                        strings.get("server_connected_to", state.connectedServerUrl ?: "")
                    ConnectionMode.SEARCHING_WIFI ->
                        strings.serverSearching
                    ConnectionMode.LOCAL_ONLY ->
                        strings.serverAutonomous
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                if (state.autoConnectWifiServer) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingsToggleRow(
                        icon = Icons.Default.Tv,
                        title = strings.saveServerM3uTitle,
                        subtitle = strings.saveServerM3uSubtitle,
                        checked = state.autoSaveServerM3u,
                        onCheckedChange = onSetAutoSaveServerM3u,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onSearchServerNow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.searchServerNow)
                    }
                }
            }
        }

        // Section 4: Aggiornamenti
        item {
            SettingsGroup(
                eyebrow = tr("AGGIORNAMENTI APP", "APP UPDATES", "ACTUALIZACIONES"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = strings.githubUpdatesTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = strings.updateFrequencyLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(UpdateFrequency.entries) { freq ->
                        val label = when (freq) {
                            UpdateFrequency.DAILY -> strings.freqDaily
                            UpdateFrequency.WEEKLY -> strings.freqWeekly
                            UpdateFrequency.MONTHLY -> strings.freqMonthly
                        }
                        FilterChip(
                            selected = state.updateFrequency == freq,
                            onClick = { onSetUpdateFrequency(freq) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCheckUpdatesNow,
                    enabled = !state.isCheckingUpdates,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isCheckingUpdates) strings.checkingUpdates else strings.checkUpdatesNow)
                }
                if (state.isCheckingUpdates) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.errorMessage?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.statusMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                state.availableUpdate?.let { upd ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = strings.get("new_version_available", upd.version),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (state.updateProgress != null) {
                        LinearProgressIndicator(
                            progress = { state.updateProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Button(
                            onClick = onInstallUpdate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.get("download_and_install", upd.version))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    eyebrow: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = eyebrow,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
