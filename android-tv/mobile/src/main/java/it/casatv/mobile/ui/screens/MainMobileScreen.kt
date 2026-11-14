package it.casatv.mobile.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import it.casatv.mobile.data.ConnectionMode
import it.casatv.mobile.data.CountryFacet
import it.casatv.mobile.data.LocalPlaylist
import it.casatv.mobile.data.MobileChannel
import it.casatv.mobile.ui.MobileUiState
import it.casatv.mobile.update.UpdateFrequency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMobileScreen(
    state: MobileUiState,
    onSearchChange: (String) -> Unit,
    onSelectCountry: (String?) -> Unit,
    onOpenChannel: (MobileChannel) -> Unit,
    onToggleFavorite: (MobileChannel) -> Unit,
    onImportUrl: (name: String, url: String) -> Unit,
    onImportFile: (Uri) -> Unit,
    onTogglePlaylist: (LocalPlaylist, Boolean) -> Unit,
    onRefreshPlaylist: (LocalPlaylist) -> Unit,
    onDeletePlaylist: (LocalPlaylist) -> Unit,
    onDismissMessage: () -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAutoServer: (Boolean) -> Unit,
    onSetAutoSaveServerM3u: (Boolean) -> Unit,
    onSearchServerNow: () -> Unit,
    onSetUpdateFrequency: (UpdateFrequency) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCountryPicker by remember { mutableStateOf(false) }

    val selectedCountryFacet = remember(state.selectedCountry, state.countries) {
        state.countries.firstOrNull { it.code == state.selectedCountry }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CasaTV",
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(10.dp))
                        ConnectionBadge(state.connectionMode)
                    }
                },
                actions = {
                    Surface(
                        onClick = { showCountryPicker = true },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = selectedCountryFacet?.flag ?: "🌐",
                                fontSize = 18.sp,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = selectedCountryFacet?.code ?: "Tutti",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                    label = { Text("Canali") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("Preferiti") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                    label = { Text("Liste M3U") },
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Impostazioni") },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                0 -> ChannelsTab(
                    state = state,
                    onSearchChange = onSearchChange,
                    onSelectCountry = onSelectCountry,
                    onOpenChannel = onOpenChannel,
                    onToggleFavorite = onToggleFavorite,
                    onGoToPlaylists = { selectedTab = 2 },
                )
                1 -> FavoritesTab(
                    favorites = state.favoriteChannels,
                    onOpenChannel = onOpenChannel,
                    onToggleFavorite = onToggleFavorite,
                )
                2 -> PlaylistsScreen(
                    state = state,
                    onImportUrl = onImportUrl,
                    onImportFile = onImportFile,
                    onTogglePlaylist = onTogglePlaylist,
                    onRefreshPlaylist = onRefreshPlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                    onDismissMessage = onDismissMessage,
                )
                3 -> SettingsScreen(
                    state = state,
                    onSetDarkTheme = onSetDarkTheme,
                    onSetAutoServer = onSetAutoServer,
                    onSetAutoSaveServerM3u = onSetAutoSaveServerM3u,
                    onSearchServerNow = onSearchServerNow,
                    onSetUpdateFrequency = onSetUpdateFrequency,
                    onCheckUpdatesNow = onCheckUpdatesNow,
                    onInstallUpdate = onInstallUpdate,
                )
            }
        }
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            countries = state.countries,
            totalChannels = state.allChannels.size,
            selectedCountry = state.selectedCountry,
            onSelectCountry = { code ->
                onSelectCountry(code)
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false },
        )
    }

    state.availableUpdate?.let { upd ->
        AlertDialog(
            onDismissRequest = onDismissUpdate,
            title = { Text("Aggiornamento disponibile (v${upd.version})") },
            text = {
                Column {
                    Text(upd.notes)
                    if (state.updateProgress != null) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.updateProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onInstallUpdate,
                    enabled = state.updateProgress == null,
                ) {
                    Text(if (state.updateProgress != null) "Download..." else "Aggiorna")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUpdate) {
                    Text("Più tardi")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerDialog(
    countries: List<CountryFacet>,
    totalChannels: Int,
    selectedCountry: String?,
    onSelectCountry: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredCountries = remember(query, countries) {
        val q = query.trim().lowercase()
        val sorted = countries.sortedBy { it.name.lowercase() }
        if (q.isEmpty()) sorted
        else sorted.filter {
            it.name.lowercase().contains(q) || it.code.lowercase().contains(q)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Seleziona Paese", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Chiudi")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cerca paese...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Pulisci")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (query.isBlank()) {
                        item {
                            CountryOptionCard(
                                flag = "🌐",
                                name = "Tutti i paesi",
                                count = totalChannels,
                                selected = selectedCountry == null,
                                onClick = { onSelectCountry(null) },
                            )
                        }
                    }
                    items(filteredCountries, key = { it.code }) { facet ->
                        CountryOptionCard(
                            flag = facet.flag,
                            name = facet.name,
                            count = facet.count,
                            selected = selectedCountry == facet.code,
                            onClick = { onSelectCountry(facet.code) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryOptionCard(
    flag: String,
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = flag, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectionBadge(mode: ConnectionMode) {
    val (label, bg, fg) = when (mode) {
        ConnectionMode.SERVER_CONNECTED -> Triple(
            "Server Wi-Fi",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ConnectionMode.SEARCHING_WIFI -> Triple(
            "Ricerca Wi-Fi...",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConnectionMode.LOCAL_ONLY -> Triple(
            "Autonomo",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ChannelsTab(
    state: MobileUiState,
    onSearchChange: (String) -> Unit,
    onSelectCountry: (String?) -> Unit,
    onOpenChannel: (MobileChannel) -> Unit,
    onToggleFavorite: (MobileChannel) -> Unit,
    onGoToPlaylists: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Cerca canale o categoria...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Pulisci")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val activeCountries = remember(state.countries) {
            state.countries.filter { it.count > 0 }
        }
        if (activeCountries.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCountry == null,
                        onClick = { onSelectCountry(null) },
                        label = { Text("Tutti (${state.allChannels.size})") },
                    )
                }
                items(activeCountries, key = { it.code }) { facet ->
                    FilterChip(
                        selected = state.selectedCountry == facet.code,
                        onClick = {
                            onSelectCountry(if (state.selectedCountry == facet.code) null else facet.code)
                        },
                        label = { Text("${facet.flag} ${facet.name} (${facet.count})") },
                    )
                }
            }
        }

        val channels = state.filteredChannels
        if (channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (state.allChannels.isEmpty()) {
                            "Nessun canale disponibile"
                        } else {
                            "Nessun canale corrisponde alla ricerca"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.allChannels.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Carica una lista M3U da URL o file locale, oppure collegati a un Wi-Fi con un server CasaTV.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onGoToPlaylists) {
                            Text("Carica lista M3U")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(channels, key = { it.id }) { channel ->
                    ChannelRowCard(
                        channel = channel,
                        onClick = { onOpenChannel(channel) },
                        onToggleFavorite = { onToggleFavorite(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    favorites: List<MobileChannel>,
    onOpenChannel: (MobileChannel) -> Unit,
    onToggleFavorite: (MobileChannel) -> Unit,
) {
    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Nessun canale nei preferiti",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tocca il cuore su un canale per aggiungerlo qui.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(favorites, key = { it.id }) { channel ->
                ChannelRowCard(
                    channel = channel,
                    onClick = { onOpenChannel(channel) },
                    onToggleFavorite = { onToggleFavorite(channel) },
                )
            }
        }
    }
}

@Composable
private fun ChannelRowCard(
    channel: MobileChannel,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                    )
                } else {
                    Text(
                        text = channel.displayNumber,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val meta = buildString {
                    if (channel.flag.isNotBlank()) append("${channel.flag} ")
                    append(channel.subtitle)
                    if (channel.isFromServer) append(" · Server")
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (channel.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Preferito",
                    tint = if (channel.favorite) Color(0xFFFF5C5C) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
