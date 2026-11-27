package it.casatv.server.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.server.core.LocalStrings
import it.casatv.server.core.NetworkUtils
import it.casatv.server.ui.theme.CasaBorder
import it.casatv.server.ui.theme.CasaDanger
import it.casatv.server.ui.theme.CasaDim
import it.casatv.server.ui.theme.CasaGreen
import it.casatv.server.ui.theme.CasaMuted
import it.casatv.server.ui.theme.CasaPanel
import it.casatv.server.ui.theme.CasaText

@Composable
fun DashboardScreen(vm: ServerViewModel) {
    val strings = LocalStrings.current
    val status by vm.status.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val clients by vm.clientCount.collectAsStateWithLifecycle()
    val playlistStatus by vm.playlistStatus.collectAsStateWithLifecycle()

    val addresses = NetworkUtils.addresses()

    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isBatteryOptimized = remember(powerManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else false
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 26.dp),
    ) {
        if (isBatteryOptimized) {
            item {
                Panel(modifier = Modifier.border(1.dp, CasaDanger.copy(alpha = 0.6f), RoundedCornerShape(16.dp))) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings.t("Ottimizzazione batteria attiva", "Battery optimization active", "Optimización de batería activa"),
                                color = CasaDanger,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text(
                                strings.t(
                                    "Android/Samsung potrebbero sospendere il server a schermo spento. Disattiva l'ottimizzazione per tenerlo sempre attivo.",
                                    "Android/Samsung may suspend the server when screen turns off. Disable optimization to keep it running.",
                                    "Android/Samsung pueden suspender el servidor con pantalla apagada. Desactiva la optimización.",
                                ),
                                color = CasaMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        CasaButton(
                            text = strings.t("Disattiva", "Disable", "Desactivar"),
                            danger = true,
                            onClick = {
                                runCatching {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                        )
                    }
                }
            }
        }
        item {
            Panel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle(
                            if (status.running) {
                                strings.t("Server attivo", "Server running", "Servidor activo")
                            } else {
                                strings.t("Server fermo", "Server stopped", "Servidor detenido")
                            },
                        )
                        Text(
                            if (status.running) status.baseUrl else "—",
                            color = if (status.running) CasaGreen else CasaMuted,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            strings.t(
                                "Apri CasaTV sulla TV e inserisci questo indirizzo",
                                "Open CasaTV on the TV and enter this address",
                                "Abre CasaTV en la TV e introduce esta dirección",
                            ),
                            color = CasaMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    if (status.running) {
                        CasaButton(
                            text = strings.t("Ferma", "Stop", "Detener"),
                            onClick = vm::stopServer,
                            danger = true,
                        )
                    } else {
                        CasaButton(
                            text = strings.t("Avvia", "Start", "Iniciar"),
                            onClick = vm::startServer,
                            primary = true,
                        )
                    }
                }
                if (status.error != null) {
                    Text(
                        status.error!!,
                        color = CasaDanger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (addresses.size > 1) {
                    Spacer(Modifier.height(14.dp))
                    addresses.drop(1).forEach { address ->
                        KV(address.label, "http://${address.address}:${status.port}")
                    }
                }
                Spacer(Modifier.height(14.dp))
                val stats = listOf(
                    playlistStatus.count.toString() to strings.t("Canali IPTV", "IPTV Channels", "Canales IPTV"),
                    "$clients" to strings.t("TV collegate", "Connected TVs", "TV conectadas"),
                    "${profiles.size}" to strings.t("Profili", "Profiles", "Perfiles"),
                )
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        stats.forEach { (value, label) ->
                            StatTile(value, label)
                        }
                    }
                }
            }
        }
    }
}
