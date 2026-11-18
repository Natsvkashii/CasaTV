package it.casatv.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import it.casatv.tv.data.UpdateInfo
import it.casatv.tv.ui.components.TvButton
import it.casatv.tv.ui.theme.CasaBorder
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.tv.data.SystemAlert
import it.casatv.tv.ui.components.BackgroundGlow
import it.casatv.tv.ui.screens.PlayerScreen
import it.casatv.tv.ui.screens.ProfileGateScreen
import it.casatv.tv.ui.screens.SettingsScreen
import it.casatv.tv.ui.screens.TvModeScreen
import it.casatv.tv.ui.theme.CasaAmber
import it.casatv.tv.ui.theme.CasaBackground
import it.casatv.tv.ui.theme.CasaGreen
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaRed
import it.casatv.tv.ui.theme.CasaSurfaceHigh
import it.casatv.tv.ui.theme.CasaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CasaTvApp(vm: MainViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
    val isServerDisconnected by vm.isServerDisconnected.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val activeProfile by vm.activeProfile.collectAsStateWithLifecycle()
    val profileGate by vm.profileGate.collectAsStateWithLifecycle()
    val resourceAlert by vm.resourceAlert.collectAsStateWithLifecycle()
    val updateInfo by vm.updateInfo.collectAsStateWithLifecycle()
    val updateDownloading by vm.updateDownloading.collectAsStateWithLifecycle()
    val updateProgress by vm.updateProgress.collectAsStateWithLifecycle()
    val updateError by vm.updateError.collectAsStateWithLifecycle()
    var inSettings by rememberSaveable { mutableStateOf(false) }
    var tvSubTab by rememberSaveable { mutableStateOf(TvSubTab.Channels) }
    var message by remember { mutableStateOf<String?>(null) }

    val deviceLanguage = remember {
        val code = java.util.Locale.getDefault().language.lowercase()
        if (code == "it" || code == "es") code else "en"
    }
    val language = activeProfile?.language ?: deviceLanguage
    val strings = remember(language) { AppStrings(language) }

    LaunchedEffect(Unit) {
        vm.messages.collectLatest { text ->
            message = text
            delay(2800)
            message = null
        }
    }

    LaunchedEffect(resourceAlert) {
        if (resourceAlert != null) {
            delay(8500)
            vm.dismissResourceAlert()
        }
    }

    BackHandler(enabled = playback == null && inSettings && !profileGate) {
        inSettings = false
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        Box(
            Modifier
                .fillMaxSize()
                .background(CasaBackground),
        ) {
            BackgroundGlow()
            if (profileGate) {
                ProfileGateScreen(vm)
            } else if (playback != null) {
                PlayerScreen(vm)
            } else if (inSettings) {
                SettingsScreen(
                    vm = vm,
                    onBack = { inSettings = false },
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp, bottom = 10.dp),
                ) {
                    TvModeScreen(
                        vm = vm,
                        initialSubTab = tvSubTab,
                        onOpenSettings = { inSettings = true },
                        onOpenUpdate = { vm.checkForUpdates(silent = false) },
                    )
                }
            }

            ResourceAlertPopup(
                alert = resourceAlert,
                strings = strings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 28.dp),
            )

            MessageToast(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp),
            )

            updateInfo?.let { info ->
                UpdateDialog(
                    info = info,
                    downloading = updateDownloading,
                    progress = updateProgress,
                    error = updateError,
                    onInstall = { vm.installUpdate() },
                    onDismiss = { vm.dismissUpdate() },
                )
            }
        }
    }
}

@Composable
private fun ResourceAlertPopup(
    alert: SystemAlert?,
    strings: AppStrings,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = alert != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        val critical = alert?.level == "critical"
        val accent = if (critical) CasaRed else CasaAmber
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CasaSurfaceHigh)
                .border(1.5.dp, accent.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 13.dp),
        ) {
            Text(
                text = "⚠ " + strings.t(
                    if (critical) "Risorse del server in esaurimento" else "Risorse del server quasi al limite",
                    if (critical) "Server resources critically low" else "Server resources almost full",
                    if (critical) "Recursos del servidor agotándose" else "Recursos del servidor casi al límite",
                ),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.t(
                    "${alert?.message.orEmpty()} · Chiudi app o flussi non necessari sul server.",
                    "${alert?.message.orEmpty()} · Close unneeded apps or streams on the server.",
                    "${alert?.message.orEmpty()} · Cierra aplicaciones o flujos innecesarios en el servidor.",
                ),
                color = CasaMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun MessageToast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CasaSurfaceHigh)
                .border(1.dp, CasaGreen.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = message.orEmpty(),
                color = CasaText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    downloading: Boolean,
    progress: Float,
    error: String?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            Text(
                text = strings.t(
                    "Aggiornamento CasaTV disponibile",
                    "CasaTV update available",
                    "Actualización de CasaTV disponible",
                ),
                color = CasaText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.t(
                        "È disponibile la nuova versione ${info.latest} dell'app per la TV sul server locale.",
                        "New version ${info.latest} of the TV app is ready on the local server.",
                        "La nueva versión ${info.latest} de la app para TV está lista en el servidor local.",
                    ),
                    color = CasaMuted,
                    fontSize = 13.sp,
                )
                if (downloading) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.t(
                            "Download installer dal server in corso… ${(progress * 100).toInt()}%",
                            "Downloading installer from local server… ${(progress * 100).toInt()}%",
                            "Descargando instalador desde el servidor local… ${(progress * 100).toInt()}%",
                        ),
                        color = CasaGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = CasaGreen,
                        trackColor = CasaBorder,
                    )
                } else if (error != null) {
                    Text(text = error, color = CasaRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            if (!downloading) {
                TvButton(
                    text = strings.t("Aggiorna ora", "Update now", "Actualizar ahora"),
                    onClick = onInstall,
                    primary = true,
                )
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = strings.t("Più tardi", "Later", "Más tarde"),
                        color = CasaMuted,
                    )
                }
            }
        },
    )
}
