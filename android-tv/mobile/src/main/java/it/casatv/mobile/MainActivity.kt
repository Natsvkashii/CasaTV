package it.casatv.mobile

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import it.casatv.mobile.ui.LocalMobileStrings
import it.casatv.mobile.ui.MobileStrings
import it.casatv.mobile.ui.MobileViewModel
import it.casatv.mobile.ui.screens.MainMobileScreen
import it.casatv.mobile.ui.screens.PlayerScreen
import it.casatv.mobile.ui.theme.CasaTvMobileTheme

class MainActivity : ComponentActivity() {
    private var hasActiveChannel = false
    private var pipOnLeaveEnabled = true
    private var isInPipModeState by mutableStateOf(false)

    private fun enterPipMode(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        return runCatching { enterPictureInPictureMode(params) }.getOrDefault(false)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (hasActiveChannel && pipOnLeaveEnabled) {
            enterPipMode()
        }
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MobileViewModel = viewModel()
            val state by vm.state.collectAsState()
            val strings = remember(state.language) { MobileStrings(state.language) }

            LaunchedEffect(state.activeChannel, state.pipOnLeaveEnabled) {
                hasActiveChannel = state.activeChannel != null
                pipOnLeaveEnabled = state.pipOnLeaveEnabled
            }

            CompositionLocalProvider(LocalMobileStrings provides strings) {
                CasaTvMobileTheme(darkTheme = state.darkTheme) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainMobileScreen(
                            state = state,
                            onSearchChange = vm::setSearchQuery,
                            onSelectCountry = vm::selectCountry,
                            onOpenChannel = vm::openChannel,
                            onToggleFavorite = vm::toggleFavorite,
                            onOpenMixPicker = vm::openMixRegionPicker,
                            onToggleMixRegion = vm::toggleMixRegion,
                            onToggleMixLanguage = vm::toggleMixLanguage,
                            onToggleMixGenre = vm::toggleMixGenre,
                            onClearMixFilters = vm::clearMixFilters,
                            onStartMixFromRegions = vm::startMixFromSelectedRegions,
                            onDismissMixPicker = vm::dismissMixRegionPicker,
                            onImportUrl = vm::importM3uUrl,
                            onImportFile = vm::importM3uFile,
                            onUploadFileToServer = vm::uploadM3uFileToServer,
                            onCheckPlaylist = vm::checkPlaylist,
                            onTogglePlaylist = vm::togglePlaylistEnabled,
                            onRefreshPlaylist = vm::refreshPlaylist,
                            onDeletePlaylist = vm::deletePlaylist,
                            onDismissMessage = vm::clearMessages,
                            onSetLanguage = vm::setLanguage,
                            onSetDarkTheme = vm::setDarkTheme,
                            onSetAutoServer = vm::setAutoConnectWifiServer,
                            onSetAutoSaveServerM3u = vm::setAutoSaveServerM3u,
                            onSetBackgroundPlay = vm::setBackgroundPlayEnabled,
                            onSetPipOnLeave = vm::setPipOnLeaveEnabled,
                            onSetLiveSafetyBuffer = vm::setLiveSafetyBufferEnabled,
                            onSetMixIncludeAudioOnly = vm::setMixIncludeAudioOnly,
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
                                isMixMode = state.isMixMode,
                                mixIndex = state.mixIndex,
                                mixTotal = state.mixTotal,
                                isInPipMode = isInPipModeState,
                                backgroundPlayEnabled = state.backgroundPlayEnabled,
                                liveSafetyBufferEnabled = state.liveSafetyBufferEnabled,
                                mixIncludeAudioOnly = state.mixIncludeAudioOnly,
                                onToggleBackgroundPlay = { vm.setBackgroundPlayEnabled(!state.backgroundPlayEnabled) },
                                onEnterPip = { enterPipMode() },
                                onPreviousChannel = vm::previousChannel,
                                onNextChannel = vm::nextChannel,
                                onAudioOnlyDetectedInMix = vm::skipAudioOnlyMixChannel,
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
