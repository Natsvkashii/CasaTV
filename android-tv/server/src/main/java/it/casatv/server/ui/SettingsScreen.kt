package it.casatv.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.server.BuildConfig
import it.casatv.server.core.LocalStrings
import it.casatv.server.core.PROFILE_LANGUAGES
import it.casatv.server.update.UpdateFrequency
import it.casatv.server.ui.theme.CasaBg
import it.casatv.server.ui.theme.CasaBorder
import it.casatv.server.ui.theme.CasaDim
import it.casatv.server.ui.theme.CasaGreen
import it.casatv.server.ui.theme.CasaMuted
import it.casatv.server.ui.theme.CasaPanelHigh
import it.casatv.server.ui.theme.CasaText

@Composable
fun SettingsScreen(vm: ServerViewModel) {
    val strings = LocalStrings.current
    val language by vm.language.collectAsStateWithLifecycle()
    val name by vm.serverName.collectAsStateWithLifecycle()
    val port by vm.port.collectAsStateWithLifecycle()
    val autoStart by vm.autoStart.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val updateFreq by vm.updateFrequency.collectAsStateWithLifecycle()
    val availableUpdate by vm.availableUpdate.collectAsStateWithLifecycle()
    val updateProgress by vm.updateProgress.collectAsStateWithLifecycle()

    var nameDraft by remember(name) { mutableStateOf(name) }
    var portDraft by remember(port) { mutableStateOf(port.toString()) }
    var autoDraft by remember(autoStart) { mutableStateOf(autoStart) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            SectionTitle(strings.t("Lingua dell'app e del server", "App and server language", "Idioma de la app y del servidor"))
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PROFILE_LANGUAGES.forEach { option ->
                    Chip(
                        text = option.name,
                        selected = language == option.code,
                        onClick = { vm.setLanguage(option.code) },
                    )
                }
            }
            Text(
                strings.t(
                    "Cambia anche la lingua di film, trame e risposte del server.",
                    "It also changes the language of movies, plots and server responses.",
                    "También cambia el idioma de películas, tramas y respuestas del servidor.",
                ),
                color = CasaDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Panel {
            SectionTitle(strings.t("Server", "Server", "Servidor"))
            Spacer(Modifier.height(10.dp))
            CasaTextField(
                nameDraft,
                { nameDraft = it.take(32) },
                strings.t("Nome del server", "Server name", "Nombre del servidor"),
            )
            Spacer(Modifier.height(10.dp))
            CasaTextField(
                portDraft,
                { portDraft = it.filter { ch -> ch.isDigit() }.take(5) },
                strings.t("Porta HTTP", "HTTP port", "Puerto HTTP"),
                number = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.t("Avvia all'apertura", "Start on launch", "Iniciar al abrir"),
                        color = CasaText,
                        fontSize = 14.sp,
                    )
                    Text(
                        strings.t(
                            "Il server parte quando apri l'app",
                            "The server starts when you open the app",
                            "El servidor se inicia al abrir la app",
                        ),
                        color = CasaMuted,
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = autoDraft,
                    onCheckedChange = { autoDraft = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CasaBg,
                        checkedTrackColor = CasaGreen,
                        uncheckedThumbColor = CasaMuted,
                        uncheckedTrackColor = CasaPanelHigh,
                    ),
                )
            }
            Row(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CasaButton(
                    strings.t("Salva", "Save", "Guardar"),
                    onClick = {
                        vm.saveSettings(nameDraft, portDraft.toIntOrNull() ?: port, autoDraft)
                    },
                    primary = true,
                )
                CasaButton(
                    strings.t("Riavvia server", "Restart server", "Reiniciar servidor"),
                    onClick = {
                        vm.stopServer()
                        vm.startServer()
                    },
                )
            }
            if (status.running) {
                Text(
                    strings.t(
                        "Per applicare porta o nome, riavvia il server.",
                        "Restart the server to apply a new port or name.",
                        "Reinicia el servidor para aplicar un puerto o nombre nuevo.",
                    ),
                    color = CasaDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Panel {
            SectionTitle(strings.t("Informazioni", "About", "Información"))
            Spacer(Modifier.height(8.dp))
            KV(strings.t("Versione app", "App version", "Versión de la app"), BuildConfig.VERSION_NAME)
            KV(strings.t("Porta", "Port", "Puerto"), port.toString())
            KV(
                strings.t("Indirizzo", "Address", "Dirección"),
                if (status.running) status.baseUrl else "—",
            )
            Text(
                strings.t(
                    "CasaTV Server trasmette film e serie TV dal telefono alle TV della stessa rete Wi-Fi. I profili restano sul server e sono condivisi da tutti i dispositivi.",
                    "CasaTV Server streams movies and TV series from your phone to TVs on the same Wi-Fi network. Profiles live on the server and are shared by every device.",
                    "CasaTV Server transmite películas y series desde el teléfono a las TV de la misma red Wi-Fi. Los perfiles están en el servidor y se comparten entre todos los dispositivos.",
                ),
                color = CasaMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .background(CasaPanelHigh, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            )
        }

        Panel {
            SectionTitle(strings.t("Aggiornamenti GitHub", "GitHub Updates", "Actualizaciones GitHub"))
            Spacer(Modifier.height(8.dp))
            Text(
                strings.t("Frequenza controllo automatico", "Auto-check frequency", "Frecuencia de comprobación"),
                color = CasaMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                UpdateFrequency.entries.forEach { freq ->
                    Chip(
                        text = freq.labelIt,
                        selected = updateFreq == freq,
                        onClick = { vm.setUpdateFrequency(freq) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            CasaButton(
                strings.t("Controlla aggiornamenti ora", "Check updates now", "Comprobar ahora"),
                onClick = { vm.checkGitHubUpdates(force = true) },
                primary = false,
            )
            availableUpdate?.let { upd ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Nuova versione disponibile: v${upd.version}",
                    color = CasaGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                CasaButton(
                    if (updateProgress != null) {
                        "Download ${(updateProgress!! * 100).toInt()}%..."
                    } else {
                        "Scarica e installa v${upd.version}"
                    },
                    onClick = { if (updateProgress == null) vm.installGitHubUpdate() },
                    primary = true,
                )
            }
        }

        Panel {
            SectionTitle(strings.t("Endpoint utili", "Useful endpoints", "Endpoints útiles"))
            Spacer(Modifier.height(8.dp))
            listOf(
                "/healthz",
                "/api/profiles",
                "/api/channels",
                "/api/playlist",
                "/api/library",
                "/api/system",
            ).forEach { endpoint ->
                Text(
                    endpoint,
                    color = CasaGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
