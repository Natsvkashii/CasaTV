package it.casatv.server.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.server.core.LocalStrings
import it.casatv.server.core.PlaylistEntry
import it.casatv.server.ui.theme.CasaDanger
import it.casatv.server.ui.theme.CasaDim
import it.casatv.server.ui.theme.CasaGreen
import it.casatv.server.ui.theme.CasaMuted
import it.casatv.server.ui.theme.CasaPanelHigh
import it.casatv.server.ui.theme.CasaText
import it.casatv.server.ui.theme.CasaWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChannelsScreen(vm: ServerViewModel) {
    val strings = LocalStrings.current
    val status by vm.playlistStatus.collectAsStateWithLifecycle()
    val playlists by vm.playlistsList.collectAsStateWithLifecycle()
    val busy by vm.playlistBusy.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(status.url) }

    LaunchedEffect(status.url) {
        if (status.url.isNotBlank()) url = status.url
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.importPlaylistFile(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            SectionTitle(
                strings.t("Playlist M3U", "M3U playlist", "Lista M3U"),
                trailing = if (status.count > 0) "${status.count}" else null,
            )
            Text(
                if (status.count > 0) {
                    strings.t(
                        "${status.count} canali disponibili sulle TV",
                        "${status.count} channels available on your TVs",
                        "${status.count} canales disponibles en las TV",
                    )
                } else {
                    strings.t(
                        "Nessuna playlist importata",
                        "No playlist imported yet",
                        "Aún no hay ninguna lista importada",
                    )
                },
                color = if (status.count > 0) CasaGreen else CasaMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (status.source.isNotBlank()) {
                Text(
                    status.source,
                    color = CasaDim,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (status.lastImport > 0) {
                Text(
                    strings.t("Ultimo aggiornamento", "Last update", "Última actualización") +
                        ": " + dateText(status.lastImport),
                    color = CasaDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            CasaTextField(
                value = url,
                onValueChange = { url = it.trim() },
                label = strings.t(
                    "Indirizzo della playlist (http/https)",
                    "Playlist address (http/https)",
                    "Dirección de la lista (http/https)",
                ),
            )
            Text(
                strings.t(
                    "Incolla il link della playlist M3U/M3U8 del tuo provider.",
                    "Paste your provider's M3U/M3U8 playlist link.",
                    "Pega el enlace de la lista M3U/M3U8 de tu proveedor.",
                ),
                color = CasaDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CasaButton(
                    text = strings.t("Importa", "Import", "Importar"),
                    onClick = { vm.importPlaylist(url) },
                    primary = true,
                    enabled = !busy,
                )
                CasaButton(
                    text = strings.t("Da file", "From file", "Desde archivo"),
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                )
                if (status.url.isNotBlank()) {
                    CasaButton(
                        text = strings.t("Aggiorna", "Refresh", "Actualizar"),
                        onClick = { vm.refreshPlaylist() },
                        enabled = !busy,
                    )
                }
                if (status.count > 0) {
                    CasaButton(
                        text = strings.t("Rimuovi", "Remove", "Quitar"),
                        onClick = { vm.clearPlaylist() },
                        danger = true,
                        enabled = !busy,
                    )
                }
            }
            if (busy) {
                Text(
                    strings.t(
                        "Importazione in corso…",
                        "Importing…",
                        "Importando…",
                    ),
                    color = CasaGreen,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Text(
                strings.t(
                    "La playlist viene letta dal telefono: le TV la vedono subito nella scheda Canali. Nessuna transcodifica, il flusso arriva diretto dalla sorgente.",
                    "The playlist is read on the phone: your TVs see it right away in the Channels tab. No transcoding, the stream goes straight to the TV.",
                    "La lista se lee en el teléfono: las TV la ven al instante en la pestaña Canales. Sin transcodificación, el flujo va directo a la TV.",
                ),
                color = CasaMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                strings.t(
                    "Formati: M3U, M3U8 e PLS con righe #EXTINF. I canali senza un flusso http/https valido vengono ignorati.",
                    "Formats: M3U, M3U8 and PLS with #EXTINF lines. Channels without a valid http/https stream are skipped.",
                    "Formatos: M3U, M3U8 y PLS con líneas #EXTINF. Los canales sin un flujo http/https válido se ignoran.",
                ),
                color = CasaDim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (playlists.isNotEmpty()) {
            Panel {
                SectionTitle(
                    strings.t("Playlist configurate", "Configured playlists", "Listas configuradas"),
                    trailing = "${playlists.size}",
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    playlists.forEach { p ->
                        val isError = p.status == "error" || p.channelCount == 0
                        val isWarn = !isError && p.status == "warn"
                        val statusColor = when {
                            isError -> CasaDanger
                            isWarn -> CasaWarning
                            else -> CasaGreen
                        }
                        val statusLabel = when {
                            isError -> strings.t("Non funziona", "Not working", "No funciona")
                            isWarn -> strings.t("Avviso", "Warning", "Aviso")
                            else -> strings.t("Attiva", "Active", "Activa")
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    CasaPanelHigh,
                                    RoundedCornerShape(10.dp),
                                )
                                .border(
                                    1.dp,
                                    if (isError) CasaDanger.copy(alpha = 0.65f) else CasaPanelHigh,
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
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
                                            p.name,
                                            color = CasaText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        "${p.channelCount} canali · $statusLabel · " +
                                            if (p.url.isNotBlank()) p.url else strings.t("File caricato", "Uploaded file", "Archivo cargado"),
                                        color = if (isError) CasaDanger else CasaDim,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                            if (p.lastError.isNotBlank() || isError) {
                                Text(
                                    text = p.lastError.ifBlank {
                                        strings.t("Playlist non valida o senza canali funzionanti", "Invalid playlist or no working channels", "Lista no válida")
                                    },
                                    color = CasaDanger,
                                    fontSize = 11.sp,
                                )
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CasaButton(
                                    text = strings.t("Verifica", "Check", "Verificar"),
                                    onClick = { vm.checkPlaylist(p.id) },
                                    enabled = !busy,
                                )
                                CasaButton(
                                    text = strings.t("Elimina", "Delete", "Eliminar"),
                                    onClick = { vm.deletePlaylist(p.id) },
                                    danger = true,
                                    enabled = !busy,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (status.categories.isNotEmpty()) {
            Panel {
                SectionTitle(
                    strings.t("Categorie", "Categories", "Categorías"),
                    trailing = "${status.categories.size}",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    status.categories.forEach { (name, count) ->
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .background(
                                    it.casatv.server.ui.theme.CasaPanelHigh,
                                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("$name · $count", color = CasaMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun dateText(seconds: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(seconds * 1000))
