package it.casatv.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.casatv.mobile.data.LocalPlaylist
import it.casatv.mobile.ui.LocalMobileStrings
import it.casatv.mobile.ui.MobileUiState

@Composable
fun PlaylistsScreen(
    state: MobileUiState,
    onImportUrl: (name: String, url: String) -> Unit,
    onImportFile: (Uri) -> Unit,
    onUploadFileToServer: (Uri) -> Unit,
    onCheckPlaylist: (LocalPlaylist) -> Unit,
    onTogglePlaylist: (LocalPlaylist, Boolean) -> Unit,
    onRefreshPlaylist: (LocalPlaylist) -> Unit,
    onDeletePlaylist: (LocalPlaylist) -> Unit,
    onDismissMessage: () -> Unit,
) {
    val strings = LocalMobileStrings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var showUrlDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImportFile(uri)
        }
    }

    val serverFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onUploadFileToServer(uri)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = strings.playlistsTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.playlistsSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { showUrlDialog = true },
                        enabled = !state.isImporting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.AddLink, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.fromUrl)
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                filePicker.launch(
                                    arrayOf(
                                        "audio/x-mpegurl",
                                        "application/vnd.apple.mpegurl",
                                        "application/x-mpegurl",
                                        "text/plain",
                                        "*/*",
                                    ),
                                )
                            } catch (_: android.content.ActivityNotFoundException) {
                                android.widget.Toast.makeText(context, "Nessun selettore file disponibile", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !state.isImporting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.localFileM3u)
                    }
                }
                if (!state.connectedServerUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            try {
                                serverFilePicker.launch(
                                    arrayOf(
                                        "audio/x-mpegurl",
                                        "application/vnd.apple.mpegurl",
                                        "application/x-mpegurl",
                                        "text/plain",
                                        "*/*",
                                    ),
                                )
                            } catch (_: android.content.ActivityNotFoundException) {
                                android.widget.Toast.makeText(context, "Nessun selettore file disponibile", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !state.isImporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (strings.lang) {
                                "it" -> "Carica file .M3U / .M3U8 / .PLS sul server CasaTV"
                                "es" -> "Subir archivo .M3U / .M3U8 / .PLS al servidor CasaTV"
                                else -> "Upload .M3U / .M3U8 / .PLS file to CasaTV server"
                            },
                        )
                    }
                }
            }
        }

        if (state.isImporting) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        state.errorMessage?.let { err ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismissMessage) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        state.statusMessage?.let { msg ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismissMessage) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        if (state.playlists.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = strings.noPlaylists,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = strings.noPlaylistsHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(state.playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onCheck = { onCheckPlaylist(playlist) },
                    onToggle = { enabled -> onTogglePlaylist(playlist, enabled) },
                    onRefresh = { onRefreshPlaylist(playlist) },
                    onDelete = { onDeletePlaylist(playlist) },
                )
            }
        }
    }

    if (showUrlDialog) {
        AddUrlPlaylistDialog(
            onDismiss = { showUrlDialog = false },
            onConfirm = { name, url ->
                showUrlDialog = false
                onImportUrl(name, url)
            },
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: LocalPlaylist,
    onCheck: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalMobileStrings.current
    val isError = playlist.status == "error" || playlist.channelCount == 0
    val statusColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val statusLabel = when {
        isError && strings.lang == "it" -> "Non funziona"
        isError && strings.lang == "es" -> "No funciona"
        isError -> "Not working"
        strings.lang == "it" -> "Attiva"
        strings.lang == "es" -> "Activa"
        else -> "Active"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (isError) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f)) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${strings.get("channels_count_label", playlist.channelCount)} · " +
                            if (playlist.isLocalFile) strings.localFileLabel else "URL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCheck) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = "Verifica",
                        tint = statusColor,
                    )
                }
                if (!playlist.isLocalFile) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aggiorna")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Elimina",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Switch(
                    checked = playlist.enabled,
                    onCheckedChange = onToggle,
                )
            }
            if (playlist.lastError.isNotBlank() || isError) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = playlist.lastError.ifBlank {
                        when (strings.lang) {
                            "it" -> "Playlist non valida o senza canali funzionanti"
                            "es" -> "Lista no válida o sin canales activos"
                            else -> "Invalid playlist or no working channels"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddUrlPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
) {
    val strings = LocalMobileStrings.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addUrlTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.nameOptional) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(strings.urlM3uLabel) },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, url) },
                enabled = url.trim().startsWith("http"),
            ) {
                Text(strings.loadAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
    )
}
