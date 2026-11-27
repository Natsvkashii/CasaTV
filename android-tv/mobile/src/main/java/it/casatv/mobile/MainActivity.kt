package it.casatv.mobile

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import it.casatv.mobile.service.MobilePlaybackService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    private val requestNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("casatv_mobile_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("asked_notifications_perm", false)) {
                prefs.edit().putBoolean("asked_notifications_perm", true).apply()
                requestNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            val vm: MobileViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val strings = remember(state.language) { MobileStrings(state.language) }
            var showStartupIntro by rememberSaveable { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                MobilePlaybackService.stopRequests.collect {
                    vm.closePlayer()
                }
            }

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
                            onOpenChannel = vm::openVodOrSeries,
                            onOpenEpisodeDirectly = vm::openChannel,
                            onFetchChannelSources = vm::fetchChannelSourcesForMenu,
                            onOpenChannelWithSource = { ch, srcs, idx ->
                                vm.openChannel(ch, preloadedSources = srcs, initialSourceIndex = idx)
                            },
                            onDismissSeriesDetail = vm::closeSeriesDetail,
                            onToggleFavorite = vm::toggleFavorite,
                            onSetVodMode = vm::setVodMode,
                            onSetVodType = vm::setVodType,
                            onSetVodCategory = vm::setVodCategory,
                            onSetVodGenre = vm::setVodGenre,
                            onSetVodPlatform = vm::setVodPlatform,
                            onSetVodLanguage = vm::setVodLanguage,
                            onRemoveVodContinue = vm::removeVodContinue,
                            onLoadMoreVod = { vm.loadVodCatalog(reset = false) },
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
                            onLoadRecordings = vm::loadRecordings,
                            onPlayRecording = vm::playRecording,
                            onDownloadRecording = vm::downloadRecording,
                            onStopRecording = vm::stopLiveRecording,
                            onDeleteRecording = vm::deleteRecording,
                        )
                        val active = state.activeChannel
                        if (active != null) {
                            PlayerScreen(
                                channel = active,
                                sources = state.playerSources,
                                activeSourceIndex = state.activeSourceIndex,
                                onSelectSource = vm::selectPlayerSource,
                                isMixMode = state.isMixMode,
                                mixIndex = state.mixIndex,
                                mixTotal = state.mixTotal,
                                isInPipMode = isInPipModeState,
                                backgroundPlayEnabled = state.backgroundPlayEnabled,
                                liveSafetyBufferEnabled = state.liveSafetyBufferEnabled,
                                mixIncludeAudioOnly = state.mixIncludeAudioOnly,
                                nextEpisode = state.nextEpisodeChannel,
                                onPlayNextEpisode = { state.nextEpisodeChannel?.let(vm::openChannel) },
                                onToggleBackgroundPlay = { vm.setBackgroundPlayEnabled(!state.backgroundPlayEnabled) },
                                onEnterPip = { enterPipMode() },
                                onPreviousChannel = vm::previousChannel,
                                onNextChannel = vm::nextChannel,
                                onAudioOnlyDetectedInMix = vm::skipAudioOnlyMixChannel,
                                onToggleFavorite = { vm.toggleFavorite(active) },
                                onSaveVodProgress = { pos, dur -> vm.saveVodProgress(active.id, pos, dur) },
                                onStartRecording = if (state.connectionMode == it.casatv.mobile.data.ConnectionMode.SERVER_CONNECTED) {
                                    { vm.startLiveRecording(active) }
                                } else null,
                                onStopRecording = if (state.connectionMode == it.casatv.mobile.data.ConnectionMode.SERVER_CONNECTED) {
                                    { id -> vm.stopLiveRecording(id) }
                                } else null,
                                onReportSourceEvent = { event, url ->
                                    vm.reportChannelSource(active.id, url, event)
                                },
                                onBack = { vm.closePlayer() },
                            )
                        }
                        if (showStartupIntro && active == null) {
                            it.casatv.mobile.ui.screens.MobileStartupSplash(
                                preloadReady = state.startupPreloadReady,
                                onFinished = { showStartupIntro = false },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
