package it.casatv.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import it.casatv.mobile.ui.LocalMobileStrings
import it.casatv.mobile.ui.MobileStrings
import it.casatv.mobile.ui.MobileViewModel
import it.casatv.mobile.ui.screens.MainMobileScreen
import it.casatv.mobile.ui.screens.PlayerScreen
import it.casatv.mobile.ui.theme.CasaTvMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MobileViewModel = viewModel()
            val state by vm.state.collectAsState()
            val strings = remember(state.language) { MobileStrings(state.language) }

            CompositionLocalProvider(LocalMobileStrings provides strings) {
                CasaTvMobileTheme(darkTheme = state.darkTheme) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainMobileScreen(
                            state = state,
                            onSearchChange = vm::setSearchQuery,
                            onSelectCountry = vm::selectCountry,
                            onOpenChannel = vm::openChannel,
                            onToggleFavorite = vm::toggleFavorite,
                            onImportUrl = vm::importM3uUrl,
                            onImportFile = vm::importM3uFile,
                            onTogglePlaylist = vm::togglePlaylistEnabled,
                            onRefreshPlaylist = vm::refreshPlaylist,
                            onDeletePlaylist = vm::deletePlaylist,
                            onDismissMessage = vm::clearMessages,
                            onSetLanguage = vm::setLanguage,
                            onSetDarkTheme = vm::setDarkTheme,
                            onSetAutoServer = vm::setAutoConnectWifiServer,
                            onSetAutoSaveServerM3u = vm::setAutoSaveServerM3u,
                            onSearchServerNow = vm::discoverAndSyncServer,
                            onSetUpdateFrequency = vm::setUpdateFrequency,
                            onCheckUpdatesNow = { vm.checkGitHubUpdates(force = true) },
                            onInstallUpdate = vm::installGitHubUpdate,
                            onDismissUpdate = vm::dismissUpdate,
                        )
                        val active = state.activeChannel
                        if (active != null) {
                            PlayerScreen(
                                channel = active,
                                onToggleFavorite = { vm.toggleFavorite(active) },
                                onBack = { vm.closePlayer() },
                            )
                        }
                    }
                }
            }
        }
    }
}
