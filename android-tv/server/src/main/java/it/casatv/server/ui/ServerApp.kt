package it.casatv.server.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.server.BuildConfig
import it.casatv.server.core.LocalStrings
import it.casatv.server.core.PROFILE_LANGUAGES
import it.casatv.server.core.Strings
import it.casatv.server.ui.theme.CasaBg
import it.casatv.server.ui.theme.CasaBorder
import it.casatv.server.ui.theme.CasaDim
import it.casatv.server.ui.theme.CasaGreen
import it.casatv.server.ui.theme.CasaMuted
import it.casatv.server.ui.theme.CasaPanel
import it.casatv.server.ui.theme.CasaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ServerTab(
    val it: String,
    val en: String,
    val es: String,
    val icon: ImageVector,
) {
    Dashboard("Pannello", "Dashboard", "Panel", Icons.Default.Home),
    Channels("Canali", "Channels", "Canales", Icons.AutoMirrored.Filled.List),
    Profiles("Profili", "Profiles", "Perfiles", Icons.Default.Person),
    Settings("Impostazioni", "Settings", "Ajustes", Icons.Default.Settings);

    fun label(language: String): String = when (language.take(2).lowercase()) {
        "en" -> en
        "es" -> es
        else -> it
    }
}

@Composable
fun ServerApp(vm: ServerViewModel) {
    val language by vm.language.collectAsStateWithLifecycle()
    val current = language
    if (current == null) {
        CompositionLocalProvider(LocalStrings provides Strings("it")) {
            LanguageGate(onSelect = vm::setLanguage)
        }
        return
    }
    CompositionLocalProvider(LocalStrings provides Strings(current)) {
        ServerShell(vm, current)
    }
}

@Composable
private fun LanguageGate(onSelect: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CasaBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .background(CasaPanel, RoundedCornerShape(22.dp))
                .padding(horizontal = 26.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("CasaTV", color = CasaGreen, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                "Server",
                color = CasaText,
                fontSize = 15.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(26.dp))
            Text(
                "Scegli la lingua del server",
                color = CasaText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Choose your server language · Elige el idioma del servidor",
                color = CasaMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
            )
            PROFILE_LANGUAGES.forEach { option ->
                CasaButton(
                    text = option.name,
                    onClick = { onSelect(option.code) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    primary = option.code == "it",
                )
            }
            Text(
                "Puoi cambiarla in seguito da Impostazioni.",
                color = CasaDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

@Composable
private fun ServerShell(vm: ServerViewModel, language: String) {
    val strings = LocalStrings.current
    val status by vm.status.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ServerTab.Dashboard) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val offlineLabel = strings.t("Server fermo", "Server stopped", "Servidor detenido")

    LaunchedEffect(message) {
        if (message != null) {
            delay(3200)
            vm.clearMessage()
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = CasaPanel,
                drawerContentColor = CasaText,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                ) {
                    Text("CasaTV", color = CasaGreen, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "SERVER",
                        color = CasaMuted,
                        fontSize = 11.sp,
                        letterSpacing = 3.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (status.running) CasaGreen else CasaDim)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (status.running) status.baseUrl else offlineLabel,
                            color = if (status.running) CasaGreen else CasaMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                HorizontalDivider(color = CasaBorder)
                Spacer(Modifier.height(10.dp))
                ServerTab.entries.forEach { entry ->
                    NavigationDrawerItem(
                        label = { Text(entry.label(language), fontSize = 15.sp) },
                        icon = {
                            Icon(
                                entry.icon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        selected = tab == entry,
                        onClick = {
                            tab = entry
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = CasaGreen.copy(alpha = 0.14f),
                            selectedIconColor = CasaGreen,
                            selectedTextColor = CasaGreen,
                            unselectedContainerColor = Color.Transparent,
                            unselectedIconColor = CasaMuted,
                            unselectedTextColor = CasaMuted,
                        ),
                    )
                }
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = CasaBorder)
                Text(
                    "CasaTV Server · v${BuildConfig.VERSION_NAME}",
                    color = CasaDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CasaBg)
                .padding(horizontal = 18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = strings.t(
                            "Apri il menu",
                            "Open menu",
                            "Abrir el menú",
                        ),
                        tint = CasaText,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "CasaTV Server",
                        color = CasaText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (status.running) CasaGreen else CasaDim)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            tab.label(language) + " · " +
                                (if (status.running) status.baseUrl else offlineLabel),
                            color = if (status.running) CasaGreen else CasaMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (message != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(CasaGreen.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(message!!, color = CasaGreen, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
            }

            Box(Modifier.weight(1f)) {
                when (tab) {
                    ServerTab.Dashboard -> DashboardScreen(vm)
                    ServerTab.Channels -> ChannelsScreen(vm)
                    ServerTab.Profiles -> ProfilesScreen(vm)
                    ServerTab.Settings -> SettingsScreen(vm)
                }
            }
        }
    }
}
