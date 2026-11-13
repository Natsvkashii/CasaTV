package it.casatv.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
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

            CasaTvMobileTheme(darkTheme = state.darkTheme) {
                val active = state.activeChannel
                if (active != null) {
                    PlayerScreen(
                        channel = active,
                        onToggleFavorite = { vm.toggleFavorite(active) },
                        onBack = { vm.closePlayer() },
                    )
                } else {
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
                        onSetDarkTheme = vm::setDarkTheme,
                        onSetAutoServer = vm::setAutoConnectWifiServer,
                        onSetAutoSaveServerM3u = vm::setAutoSaveServerM3u,
                        onSearchServerNow = vm::discoverAndSyncServer,
                        onSetUpdateFrequency = vm::setUpdateFrequency,
                        onCheckUpdatesNow = { vm.checkGitHubUpdates(force = true) },
                        onInstallUpdate = vm::installGitHubUpdate,
                        onDismissUpdate = vm::dismissUpdate,
                    )
                }
            }
        }
    }
}
