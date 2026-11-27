package it.casatv.tv.ui.components

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import it.casatv.tv.data.Channel
import it.casatv.tv.data.StreamSource
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.countryName
import it.casatv.tv.ui.localizedSubtitle
import it.casatv.tv.ui.theme.CasaAmber
import it.casatv.tv.ui.theme.CasaBorder
import it.casatv.tv.ui.theme.CasaGreen
import it.casatv.tv.ui.theme.CasaIsLight
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaRed
import it.casatv.tv.ui.theme.CasaSurface
import it.casatv.tv.ui.theme.CasaSurfaceHigh
import it.casatv.tv.ui.theme.CasaText
import kotlinx.coroutines.delay

@Composable
fun ChannelContextMenuDialog(
    channel: Channel,
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayWithSource: (List<StreamSource>, Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onStartMixCountry: ((String) -> Unit)? = null,
    onRemoveContinue: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val isLight = CasaIsLight
    var sources by remember(channel.id) { mutableStateOf<List<StreamSource>>(emptyList()) }
    var loadingSources by remember(channel.id) { mutableStateOf(!channel.isVodContent) }
    var showSourcesPicker by remember(channel.id) { mutableStateOf(false) }
    var showDetails by remember(channel.id) { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val openedAtMs = remember(channel.id) { SystemClock.uptimeMillis() }
    var freshConfirmDown by remember(channel.id) { mutableStateOf(false) }

    LaunchedEffect(channel.id) {
        if (!channel.isVodContent) {
            loadingSources = true
            sources = vm.fetchChannelSources(channel.id)
            loadingSources = false
        }
    }

    LaunchedEffect(showSourcesPicker, showDetails) {
        delay(90)
        runCatching { firstFocus.requestFocus() }
    }

    BackHandler {
        when {
            showSourcesPicker -> showSourcesPicker = false
            showDetails -> showDetails = false
            else -> onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isLight) Color.White else Color(0xFF0A0A0A))
                .border(1.dp, CasaBorder, RoundedCornerShape(8.dp))
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val code = native.keyCode
                    if (code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        code == AndroidKeyEvent.KEYCODE_ENTER ||
                        code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                    ) {
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (native.repeatCount > 0 || (native.eventTime - openedAtMs) < 220L) {
                                    true
                                } else {
                                    freshConfirmDown = true
                                    false
                                }
                            }
                            KeyEventType.KeyUp -> {
                                if (!freshConfirmDown) {
                                    true
                                } else {
                                    freshConfirmDown = false
                                    false
                                }
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 52.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CasaSurfaceHigh)
                        .border(1.dp, CasaBorder, RoundedCornerShape(4.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ChannelLogo(channel = channel, modifier = Modifier.size(40.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (channel.contentType == "series" && !channel.seriesTitle.isNullOrBlank()) channel.seriesTitle else channel.name,
                        color = CasaText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!channel.country.isNullOrBlank()) {
                            CountryFlagIcon(countryCode = channel.country, width = 18.dp, height = 12.dp)
                            val cName = countryName(channel.country, strings.language)
                            if (cName.isNotBlank()) {
                                Text(text = cName, color = CasaMuted, fontSize = 11.sp)
                            }
                        }
                        val srcCnt = maxOf(sources.size, channel.streamsCount)
                        if (!channel.isVodContent && srcCnt > 1) {
                            Text(
                                text = "· $srcCnt ${strings.t("sorgenti", "sources", "fuentes")}",
                                color = CasaText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(CasaSurfaceHigh)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = strings.t("Chiudi", "Close", "Cerrar"),
                        tint = CasaMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CasaBorder),
            )

            when {
                showSourcesPicker -> {
                    ContextMenuActionRow(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        title = strings.t("Indietro al menù", "Back to menu", "Volver al menú"),
                        subtitle = null,
                        onClick = { showSourcesPicker = false },
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    if (loadingSources) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(color = CasaText, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = strings.t("Carico sorgenti…", "Loading sources…", "Cargando fuentes…"),
                                color = CasaMuted,
                                fontSize = 12.sp,
                            )
                        }
                    } else if (sources.isEmpty()) {
                        Text(
                            text = strings.t("Nessuna sorgente alternativa trovata.", "No alternate sources found.", "Sin fuentes alternativas."),
                            color = CasaMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 310.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(sources, key = { idx, s -> "${s.id}_$idx" }) { idx, src ->
                                val host = src.host.ifBlank {
                                    runCatching { java.net.URI(src.url).host }.getOrNull().orEmpty()
                                }
                                val statusColor = when (src.status.lowercase()) {
                                    "verified" -> CasaGreen
                                    "degraded" -> CasaAmber
                                    "offline" -> CasaRed
                                    else -> CasaText
                                }
                                val proto = src.protocol.ifBlank {
                                    if (src.url.startsWith("https://", ignoreCase = true)) "HTTPS" else "HTTP"
                                }
                                val title = buildString {
                                    append(src.name.ifBlank { channel.name })
                                    if (src.recommended) append(" · ${strings.t("Consigliata", "Recommended", "Recomendada")}")
                                }
                                val sub = buildString {
                                    append(src.quality.ifBlank { "HD" })
                                    if (proto.isNotBlank()) append(" · $proto")
                                    if (host.isNotBlank()) append(" · $host")
                                    src.latencyMs?.let { append(" · ${it}ms") }
                                }
                                ContextMenuActionRow(
                                    icon = CasaIcons.Layers,
                                    title = title,
                                    subtitle = sub,
                                    accentColor = statusColor,
                                    onClick = { onPlayWithSource(sources, idx) },
                                )
                            }
                        }
                    }
                }

                showDetails -> {
                    ContextMenuActionRow(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        title = strings.t("Indietro al menù", "Back to menu", "Volver al menú"),
                        subtitle = null,
                        onClick = { showDetails = false },
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(CasaSurface)
                            .border(1.dp, CasaBorder, RoundedCornerShape(6.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val host = runCatching { java.net.URI(channel.streamUrl).host }.getOrNull().orEmpty()
                        Text(
                            text = "${strings.t("Categoria", "Category", "Categoría")}: ${localizedSubtitle(channel, strings.language)}",
                            color = CasaText,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "${strings.t("Sorgenti unite", "Merged sources", "Fuentes unidas")}: ${maxOf(sources.size, channel.streamsCount)}",
                            color = CasaText,
                            fontSize = 12.sp,
                        )
                        if (host.isNotBlank()) {
                            Text(
                                text = "Host: $host",
                                color = CasaMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Text(
                            text = "ID: ${channel.id}",
                            color = CasaMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                else -> {
                    ContextMenuActionRow(
                        icon = Icons.Filled.PlayArrow,
                        title = if (channel.isVodContent) {
                            strings.t("Apri / Riproduci", "Open / Play", "Abrir / Reproducir")
                        } else {
                            strings.t("Riproduci canale", "Play channel", "Reproducir canal")
                        },
                        subtitle = if (channel.isVodContent) null else strings.t("Selezione dinamica della sorgente migliore", "Auto-selects the best stream dynamically", "Selecciona el mejor flujo automáticamente"),
                        accentColor = CasaText,
                        onClick = onPlay,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    if (onRemoveContinue != null) {
                        ContextMenuActionRow(
                            icon = Icons.Filled.Delete,
                            title = strings.t("Rimuovi da Continua a guardare", "Remove from Continue Watching", "Quitar de Seguir viendo"),
                            subtitle = strings.t("Elimina solo dalla lista Continua a guardare", "Removes only from Continue Watching", "Quitar solo de Seguir viendo"),
                            accentColor = CasaRed,
                            onClick = onRemoveContinue,
                        )
                    }
                    ContextMenuActionRow(
                        icon = if (channel.favorite) Icons.Filled.Star else CasaIcons.StarOutline,
                        title = if (channel.favorite) {
                            strings.t("Rimuovi dai Preferiti", "Remove from Favorites", "Quitar de Favoritos")
                        } else {
                            strings.t("Aggiungi ai Preferiti", "Add to Favorites", "Añadir a Favoritos")
                        },
                        subtitle = if (channel.favorite) {
                            strings.t("Canale salvato nei tuoi Preferiti", "Currently in your Favorites", "Actualmente en tus Favoritos")
                        } else {
                            strings.t("Salva nei Preferiti del profilo", "Save to your profile Favorites", "Guardar en Favoritos del perfil")
                        },
                        accentColor = if (channel.favorite) CasaAmber else CasaText,
                        onClick = {
                            onToggleFavorite()
                            onDismiss()
                        },
                    )
                    if (!channel.isVodContent) {
                        val srcCnt = maxOf(sources.size, channel.streamsCount)
                        ContextMenuActionRow(
                            icon = CasaIcons.Layers,
                            title = strings.t("Scegli sorgente", "Choose stream source", "Elegir fuente"),
                            subtitle = when {
                                loadingSources -> strings.t("Ricerca flussi disponibili…", "Checking available streams…", "Buscando flujos…")
                                srcCnt > 1 -> strings.t("$srcCnt flussi disponibili per questo canale", "$srcCnt available streams for this channel", "$srcCnt flujos disponibles")
                                else -> strings.t("1 flusso disponibile", "1 available stream", "1 flujo disponible")
                            },
                            accentColor = CasaText,
                            onClick = { showSourcesPicker = true },
                        )
                        val cc = channel.country?.trim()?.uppercase().orEmpty()
                        if (onStartMixCountry != null && cc.length == 2) {
                            ContextMenuActionRow(
                                icon = CasaIcons.Radio,
                                title = strings.t("Avvia Mix da ${countryName(cc, strings.language)}", "Start Mix in ${countryName(cc, strings.language)}", "Iniciar Mix en ${countryName(cc, strings.language)}"),
                                subtitle = strings.t("Zapping rapido tra canali verificati", "Instant zap through verified streams", "Zapping rápido entre canales verificados"),
                                accentColor = CasaText,
                                onClick = {
                                    onDismiss()
                                    onStartMixCountry(cc)
                                },
                            )
                        }
                    }
                    ContextMenuActionRow(
                        icon = Icons.Filled.Info,
                        title = strings.t("Dettagli", "Details", "Detalles"),
                        subtitle = localizedSubtitle(channel, strings.language),
                        accentColor = CasaText,
                        onClick = { showDetails = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextMenuActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White,
) {
    val isLight = CasaIsLight
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(6.dp)
    val focusBg = if (isLight) Color.Black else Color.White
    val focusFg = if (isLight) Color.White else Color.Black
    val idleBg = if (isLight) CasaSurface else Color(0xFF161616)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) focusBg else idleBg)
            .border(if (focused) 2.dp else 1.dp, if (focused) focusBg else CasaBorder, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (focused) focusFg else accentColor,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (focused) focusFg else CasaText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = if (focused) focusFg.copy(alpha = 0.72f) else CasaMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
