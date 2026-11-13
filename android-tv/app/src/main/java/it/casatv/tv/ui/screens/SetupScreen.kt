package it.casatv.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.tv.discovery.DiscoveredServer
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.components.BackgroundGlow
import it.casatv.tv.ui.components.CasaLogo
import it.casatv.tv.ui.components.InfoPill
import it.casatv.tv.ui.components.TvButton
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
fun SetupScreen(vm: MainViewModel) {
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val savedUrl by vm.serverUrl.collectAsStateWithLifecycle()
    var manualUrl by rememberSaveable { mutableStateOf(savedUrl.orEmpty()) }

    LaunchedEffect(Unit) {
        vm.startDiscovery()
    }
    DisposableEffect(Unit) {
        onDispose { vm.stopDiscovery() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        BackgroundGlow()
        val compact = maxWidth < 1100.dp

        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 44.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CasaLogo()
                Spacer(Modifier.height(18.dp))
                SetupCard(
                    vm = vm,
                    manualUrl = manualUrl,
                    onManualUrlChange = { manualUrl = it },
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 52.dp, vertical = 38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(44.dp),
            ) {
                SetupIntro(Modifier.weight(1f))
                SetupCard(
                    vm = vm,
                    manualUrl = manualUrl,
                    onManualUrlChange = { manualUrl = it },
                    modifier = Modifier.width(492.dp),
                )
            }
        }

        if (connecting) {
            val strings = LocalAppStrings.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB805090C)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = CasaGreen,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(38.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = strings.t(
                            "Connessione a CasaTV…",
                            "Connecting to CasaTV…",
                            "Conectando con CasaTV…",
                        ),
                        color = CasaText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.t(
                            "Verifico il server e carico il catalogo",
                            "Checking the server and loading the catalog",
                            "Comprobando el servidor y cargando el catálogo",
                        ),
                        color = CasaMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupIntro(modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        CasaLogo()
        Spacer(Modifier.height(36.dp))
        Text(
            text = strings.t(
                "La TV di casa,\nsenza fronzoli.",
                "Home TV,\nwithout the frills.",
                "La TV de casa,\nsin complicaciones.",
            ),
            style = MaterialTheme.typography.displayMedium,
            color = CasaText,
            lineHeight = 47.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = strings.t(
                "Il catalogo IPTV della tua rete, ordinato come una TV. " +
                    "Scegli un canale, premi OK e usa il telecomando.",
                "The IPTV catalog from your network, ordered like a TV. " +
                    "Pick a channel, press OK and use the remote.",
                "El catálogo IPTV de tu red, ordenado como una TV. " +
                    "Elige un canal, pulsa OK y usa el mando.",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = CasaMuted,
            modifier = Modifier.widthIn(max = 430.dp),
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoPill(strings.t("Canali IPTV", "IPTV channels", "Canales IPTV"))
            InfoPill(strings.t("Guida TV", "TV guide", "Guía TV"))
            InfoPill(strings.t("Preferiti", "Favorites", "Favoritos"))
        }
        Spacer(Modifier.height(40.dp))
        RemoteHint()
    }
}

@Composable
private fun SetupCard(
    vm: MainViewModel,
    manualUrl: String,
    onManualUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val discovered by vm.discoveredServers.collectAsStateWithLifecycle()
    val discovering by vm.discovering.collectAsStateWithLifecycle()
    val discoveryError by vm.discoveryError.collectAsStateWithLifecycle()
    val connecting by vm.connecting.collectAsStateWithLifecycle()
    val connectionError by vm.connectionError.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CasaSurface.copy(alpha = 0.94f))
            .border(1.dp, CasaBorder, RoundedCornerShape(20.dp))
            .padding(26.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(CasaGreen),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = strings.t("CONFIGURAZIONE", "SETUP", "CONFIGURACIÓN"),
                color = CasaGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = strings.t(
                "Connetti il server",
                "Connect the server",
                "Conecta el servidor",
            ),
            style = MaterialTheme.typography.headlineMedium,
            color = CasaText,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = strings.t(
                "CasaTV cerca autonomamente il server sulla rete Wi-Fi e si collega senza intervento.",
                "CasaTV automatically finds the server on the Wi-Fi network and connects without user action.",
                "CasaTV busca automáticamente el servidor en la red Wi-Fi y se conecta sin intervención.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = CasaMuted,
        )

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.t("SERVER SULLA RETE", "SERVERS ON THE NETWORK", "SERVIDORES EN LA RED"),
                color = CasaDim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
            )
            if (discovering) {
                CircularProgressIndicator(
                    color = CasaGreen,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (discovered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CasaBackground.copy(alpha = 0.55f))
                    .border(1.dp, CasaBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
            ) {
                Text(
                    text = if (discovering) {
                        strings.t(
                            "Cerco CasaTV sulla rete…",
                            "Looking for CasaTV on the network…",
                            "Buscando CasaTV en la red…",
                        )
                    } else {
                        strings.t(
                            "Nessun server trovato. Inserisci l’indirizzo mostrato dal server.",
                            "No server found. Enter the address shown by the server.",
                            "No se ha encontrado ningún servidor. Introduce la dirección que muestra el servidor.",
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (discovering) CasaMuted else CasaDim,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                discovered.forEach { server ->
                    DiscoveredServerRow(
                        server = server,
                        enabled = !connecting,
                        onClick = { vm.connect(server.url) },
                    )
                }
            }
        }

        if (discoveryError != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = discoveryError.orEmpty(),
                color = CasaDim,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(17.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.t(
                    "OPPURE INSERISCI L’INDIRIZZO",
                    "OR ENTER THE ADDRESS",
                    "O INTRODUCE LA DIRECCIÓN",
                ),
                color = CasaDim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
            )
            TvButton(
                text = strings.t("Cerca di nuovo", "Search again", "Buscar de nuevo"),
                onClick = { vm.startDiscovery() },
                primary = false,
                icon = Icons.Filled.Refresh,
                enabled = !connecting,
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            OutlinedTextField(
                value = manualUrl,
                onValueChange = onManualUrlChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text("http://192.168.1.10:8787", color = CasaDim, fontSize = 13.sp)
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = CasaText),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CasaGreen,
                    unfocusedBorderColor = CasaBorder,
                    focusedContainerColor = CasaBackground.copy(alpha = 0.7f),
                    unfocusedContainerColor = CasaBackground.copy(alpha = 0.7f),
                    cursorColor = CasaGreen,
                    focusedLabelColor = CasaGreen,
                    unfocusedLabelColor = CasaMuted,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (manualUrl.isNotBlank() && !connecting) {
                            vm.connect(manualUrl)
                        }
                    },
                ),
            )
            TvButton(
                text = strings.t("Connetti", "Connect", "Conectar"),
                onClick = { vm.connect(manualUrl) },
                primary = true,
                enabled = manualUrl.isNotBlank() && !connecting,
            )
        }

        if (connectionError != null) {
            Spacer(Modifier.height(13.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(CasaRed.copy(alpha = 0.1f))
                    .border(1.dp, CasaRed.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    text = connectionError.orEmpty(),
                    color = CasaRed,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = strings.t(
                "Suggerimento: all’avvio il server mostra l’indirizzo di rete da usare.",
                "Tip: when it starts, the server prints the network address to use.",
                "Consejo: al arrancar, el servidor muestra la dirección de red que debes usar.",
            ),
            color = CasaDim,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun RemoteHint() {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CasaSurface.copy(alpha = 0.55f))
            .border(1.dp, CasaBorder.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            tint = CasaGreen.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = strings.t(
                "Usa ▲ ▼ ◀ ▶ per muoverti e OK per selezionare",
                "Use ▲ ▼ ◀ ▶ to move and OK to select",
                "Usa ▲ ▼ ◀ ▶ para moverte y OK para seleccionar",
            ),
            color = CasaMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DiscoveredServerRow(
    server: DiscoveredServer,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) CasaSurfaceHigh else CasaBackground.copy(alpha = 0.55f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) CasaGreen else CasaBorder.copy(alpha = 0.7f),
                shape = shape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(CasaGreen.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "TV",
                color = CasaGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = server.name,
                color = CasaText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = server.url.removePrefix("http://"),
                color = CasaDim,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = strings.t("CONNETTI", "CONNECT", "CONECTAR"),
            color = CasaGreen,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}
