package it.casatv.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import it.casatv.tv.ui.theme.CasaAmber
import it.casatv.tv.ui.theme.CasaBlue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.tv.data.Channel
import it.casatv.tv.data.Facet
import it.casatv.tv.data.GlobalSearchResult
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.TvMixRegionPickerHost
import it.casatv.tv.ui.TvSubTab
import it.casatv.tv.ui.buildCountryCatalog
import it.casatv.tv.ui.components.CasaIcons
import it.casatv.tv.ui.components.ChannelContextMenuDialog
import it.casatv.tv.ui.components.CountryFlagIcon
import it.casatv.tv.ui.components.EmptyBox
import it.casatv.tv.ui.components.ErrorBox
import it.casatv.tv.ui.components.GridChannelCard
import it.casatv.tv.ui.components.LoadingBox
import it.casatv.tv.ui.components.ProfileAvatarBadge
import it.casatv.tv.ui.components.TvButton
import it.casatv.tv.ui.components.TvUniversalSearchBar
import it.casatv.tv.ui.components.UniversalSearchResultCard
import it.casatv.tv.ui.components.channelCountLabel
import it.casatv.tv.ui.components.rememberTvUiScale
import it.casatv.tv.ui.countryFacet
import it.casatv.tv.ui.theme.CasaBorder
import it.casatv.tv.ui.theme.CasaDim
import it.casatv.tv.ui.theme.CasaGreen
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaSurface
import it.casatv.tv.ui.theme.CasaSurfaceHigh
import it.casatv.tv.ui.theme.CasaText
import kotlinx.coroutines.delay

@Composable
fun TvModeScreen(
    vm: MainViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenRecordings: () -> Unit = {},
    onOpenUpdate: (() -> Unit)? = null,
) {
    val queue by vm.zapQueue.collectAsStateWithLifecycle()
    val loading by vm.tvLoading.collectAsStateWithLifecycle()
    val tvTotal by vm.tvTotal.collectAsStateWithLifecycle()
    val tvHasMore by vm.tvHasMore.collectAsStateWithLifecycle()
    val tvLoadingMore by vm.tvLoadingMore.collectAsStateWithLifecycle()
    val error by vm.tvError.collectAsStateWithLifecycle()
    val fallback by vm.tvFallback.collectAsStateWithLifecycle()
    val tvCountry by vm.tvCountry.collectAsStateWithLifecycle()
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val searchLoading by vm.searchLoading.collectAsStateWithLifecycle()
    val isServerDisconnected by vm.isServerDisconnected.collectAsStateWithLifecycle()
    val updateInfo by vm.updateInfo.collectAsStateWithLifecycle()
    val vodTot by vm.vodTotal.collectAsStateWithLifecycle()
    val lastPlayedId by vm.lastPlayedChannelId.collectAsStateWithLifecycle()
    val lastVodFocusId by vm.lastVodFocusId.collectAsStateWithLifecycle()
    val showMixRegionPicker by vm.showMixRegionPicker.collectAsStateWithLifecycle()
    val subTab by vm.selectedTvSubTab.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val scale = rememberTvUiScale()
    val targetIndex = remember(queue, lastPlayedId) {
        queue.indexOfFirst { it.id == lastPlayedId }
    }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = vm.channelsScrollIndex,
        initialFirstVisibleItemScrollOffset = vm.channelsScrollOffset,
    )
    val firstFocus = remember { FocusRequester() }
    var pickerOpen by remember { mutableStateOf(false) }
    var contextMenuChannel by remember { mutableStateOf<Channel?>(null) }
    var contextMenuQueue by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var contextMenuIsContinue by remember { mutableStateOf(false) }

    val countryCode = tvCountry
        ?: profile?.country
        ?: config?.defaultCountry
        ?: "IT"
    val catalog = remember(config?.facets?.countries, strings.language) {
        buildCountryCatalog(config?.facets?.countries.orEmpty(), strings.language)
    }
    val country = countryFacet(countryCode, catalog)

    LaunchedEffect(Unit) {
        vm.ensureTvLoaded()
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (idx, off) ->
                vm.channelsScrollIndex = idx
                vm.channelsScrollOffset = off
            }
    }

    LaunchedEffect(gridState, queue.size, tvHasMore, tvLoadingMore, subTab, searchQuery) {
        if (subTab == TvSubTab.Channels && searchQuery.isBlank() && tvHasMore && !tvLoadingMore && queue.isNotEmpty()) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { lastVisible ->
                    if (lastVisible >= queue.size - 30 && tvHasMore && !tvLoadingMore) {
                        vm.loadMoreZapQueue()
                    }
                }
        }
    }

    LaunchedEffect(queue.isNotEmpty(), subTab, searchQuery.isEmpty(), targetIndex, pickerOpen) {
        if (!pickerOpen && queue.isNotEmpty() && subTab == TvSubTab.Channels && searchQuery.isBlank()) {
            val focusIdx = if (targetIndex >= 0) targetIndex else if (vm.channelsScrollIndex in queue.indices) vm.channelsScrollIndex else 0
            delay(40)
            val isVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == focusIdx }
            if (!isVisible && targetIndex >= 0) {
                runCatching { gridState.scrollToItem(focusIdx) }
            }
            repeat(10) {
                delay(60)
                if (runCatching { firstFocus.requestFocus(); true }.getOrDefault(false)) {
                    return@LaunchedEffect
                }
            }
        }
    }

    BackHandler(enabled = searchQuery.isNotBlank() || subTab != TvSubTab.Channels) {
        if (searchQuery.isNotBlank()) {
            vm.clearSearch()
        } else {
            vm.setSelectedTvSubTab(TvSubTab.Channels)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = scale.paddingHorizontal),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Barra Canali e Preferiti + pulsante separato Mix a destra di Preferiti
            TvSubTabSelector(
                selected = subTab,
                onSelect = {
                    if (searchQuery.isNotBlank()) vm.clearSearch()
                    vm.setSelectedTvSubTab(it)
                },
            )

            TvMixButton(
                onClick = {
                    if (searchQuery.isNotBlank()) vm.clearSearch()
                    vm.openMixSection()
                },
            )

            // 2. Barra di ricerca universale globale per canali
            TvUniversalSearchBar(
                query = searchQuery,
                onQueryChange = { vm.searchGlobalChannels(it) },
                onSearch = {},
                onClear = { vm.clearSearch() },
                loading = searchLoading,
                placeholderText = strings.t(
                    "Cerca canali…",
                    "Search channels…",
                    "Buscar canales…",
                ),
                modifier = Modifier.weight(1f),
            )

            // 3. Paese selezionato (solo per Canali Live; Film & Serie sono globali con filtro Lingua)
            if (searchQuery.isBlank() && subTab == TvSubTab.Channels) {
                TvModeCountryButton(
                    country = country,
                    countryCode = countryCode,
                    onClick = { pickerOpen = true },
                )
            }

            // 4. Badge canali / offline
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CasaSurface)
                    .border(1.dp, CasaBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isServerDisconnected) CasaAmber
                            else if (loading || searchLoading) CasaMuted
                            else CasaGreen,
                        ),
                )
                Spacer(Modifier.width(7.dp))
                val activeCountryTotal = when {
                    tvTotal > 0 -> tvTotal
                    !fallback && (country?.count ?: 0) > 0 -> country?.count ?: 0
                    else -> queue.size
                }
                Text(
                    text = if (isServerDisconnected) {
                        strings.t("Offline", "Offline", "Offline")
                    } else if (searchQuery.isNotBlank()) {
                        if (searchLoading) strings.t("Cerco…", "Searching…", "Buscando…")
                        else "${searchResults.size} " + strings.t("risultati", "results", "resultados")
                    } else if (loading) {
                        if (subTab == TvSubTab.Channels && !fallback && (country?.count ?: 0) > 0) {
                            channelCountLabel(country!!.count)
                        } else {
                            strings.t("Carico…", "Loading…", "Cargando…")
                        }
                    } else {
                        when (subTab) {
                            TvSubTab.Channels -> channelCountLabel(activeCountryTotal)
                            TvSubTab.Vod -> "${java.text.NumberFormat.getIntegerInstance().format(vodTot)} VOD"
                            TvSubTab.Favorites -> strings.t("Preferiti", "Favorites", "Favoritos")
                        }
                    },
                    color = CasaMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // 5. Update badge (se disponibile)
            if (updateInfo?.updateAvailable == true) {
                TopBarUpdateBadge(
                    version = updateInfo?.latest.orEmpty(),
                    onClick = { onOpenUpdate?.invoke() },
                )
            }

            // 6. Profilo utente
            TopBarProfileButton(
                avatar = profile?.avatar ?: "blue",
                profileName = profile?.name.orEmpty(),
                onClick = { vm.switchProfile() },
            )

            // 7. Registrazioni salvate (7 giorni)
            TopBarIconButton(
                icon = Icons.Filled.Menu,
                contentDescription = strings.t("Registrazioni", "Recordings", "Grabaciones"),
                onClick = onOpenRecordings,
            )

            // 8. Icona Impostazioni in alto a destra
            TopBarIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = strings.t("Impostazioni", "Settings", "Ajustes"),
                onClick = onOpenSettings,
            )
        }
        Spacer(Modifier.height(8.dp))

        // When search is active, show worldwide matching channels
        if (searchQuery.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = strings.t(
                        "Ricerca globale per \"$searchQuery\"",
                        "Worldwide search for \"$searchQuery\"",
                        "Búsqueda global para \"$searchQuery\"",
                    ),
                    color = CasaText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = strings.t("Catalogo globale", "Worldwide catalog", "Catálogo mundial"),
                    color = CasaDim,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(4.dp))

            when {
                searchLoading && searchResults.isEmpty() -> LoadingBox(
                    text = strings.t("Cerco tra i canali…", "Searching channels…", "Buscando canales…"),
                    modifier = Modifier.weight(1f),
                )

                !searchLoading && searchResults.isEmpty() -> EmptyBox(
                    title = strings.t("Nessun risultato trovato", "No results found", "Ningún resultado encontrado"),
                    message = strings.t(
                        "Nessun canale corrisponde a \"$searchQuery\".",
                        "No channel matches \"$searchQuery\".",
                        "Ningún canal coincide con \"$searchQuery\".",
                    ),
                    actionLabel = strings.t("Cancella ricerca", "Clear search", "Borrar búsqueda"),
                    onAction = { vm.clearSearch() },
                    modifier = Modifier.weight(1f),
                )

                else -> {
                    val liveSearchResults = remember(searchResults) {
                        searchResults.filterIsInstance<GlobalSearchResult.ChannelResult>().filter { !it.channel.isVodContent }
                    }
                    val movieSearchResults = remember(searchResults) {
                        searchResults.filterIsInstance<GlobalSearchResult.ChannelResult>().filter {
                            it.channel.contentType == "movie" || (it.channel.isVodContent && it.channel.contentType != "series")
                        }
                    }
                    val seriesSearchResults = remember(searchResults) {
                        searchResults.filterIsInstance<GlobalSearchResult.ChannelResult>().filter { it.channel.contentType == "series" }
                    }
                    val allSearchChannels = remember(searchResults) {
                        searchResults.filterIsInstance<GlobalSearchResult.ChannelResult>().map { it.channel }
                    }
                    val searchGridState = rememberLazyGridState(
                        initialFirstVisibleItemIndex = vm.searchScrollIndex,
                        initialFirstVisibleItemScrollOffset = vm.searchScrollOffset,
                    )
                    val searchRestoreFocus = remember { FocusRequester() }
                    val searchFocusTargetId = lastVodFocusId ?: lastPlayedId
                    LaunchedEffect(searchGridState) {
                        snapshotFlow { searchGridState.firstVisibleItemIndex to searchGridState.firstVisibleItemScrollOffset }
                            .collect { (idx, off) ->
                                vm.searchScrollIndex = idx
                                vm.searchScrollOffset = off
                            }
                    }
                    LaunchedEffect(searchResults.isNotEmpty(), searchFocusTargetId) {
                        if (searchResults.isNotEmpty() && !searchFocusTargetId.isNullOrBlank()) {
                            val livePos = liveSearchResults.indexOfFirst { it.channel.id == searchFocusTargetId }
                            val moviePos = movieSearchResults.indexOfFirst { it.channel.id == searchFocusTargetId }
                            val seriesPos = seriesSearchResults.indexOfFirst { it.channel.id == searchFocusTargetId }
                            val liveSectionItems = if (liveSearchResults.isNotEmpty()) 1 + liveSearchResults.size else 0
                            val movieSectionItems = if (movieSearchResults.isNotEmpty()) 1 + movieSearchResults.size else 0
                            val targetGridIdx = when {
                                livePos >= 0 -> 1 + livePos
                                moviePos >= 0 -> liveSectionItems + 1 + moviePos
                                seriesPos >= 0 -> liveSectionItems + movieSectionItems + 1 + seriesPos
                                else -> -1
                            }
                            if (targetGridIdx >= 0) {
                                delay(40)
                                val visible = searchGridState.layoutInfo.visibleItemsInfo.any { it.index == targetGridIdx }
                                if (!visible) runCatching { searchGridState.scrollToItem(targetGridIdx) }
                                repeat(10) {
                                    delay(60)
                                    if (runCatching { searchRestoreFocus.requestFocus(); true }.getOrDefault(false)) {
                                        return@LaunchedEffect
                                    }
                                }
                            }
                        }
                    }
                    LazyVerticalGrid(
                        state = searchGridState,
                        columns = GridCells.Adaptive(scale.gridMinCardWidth),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (liveSearchResults.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = strings.t("CANALI TV (${liveSearchResults.size})", "TV CHANNELS (${liveSearchResults.size})", "CANALES TV (${liveSearchResults.size})"),
                                    color = CasaMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                )
                            }
                            itemsIndexed(
                                liveSearchResults,
                                key = { _, result -> "sch_live_${result.channel.id}" },
                            ) { _, result ->
                                UniversalSearchResultCard(
                                    result = result,
                                    onClick = { vm.openVodItem(result.channel, allSearchChannels.ifEmpty { listOf(result.channel) }) },
                                    onFavorite = { vm.toggleFavorite(result.channel) },
                                    modifier = if (result.channel.id == searchFocusTargetId) {
                                        Modifier.focusRequester(searchRestoreFocus)
                                    } else {
                                        Modifier
                                    },
                                    onLongClick = {
                                        contextMenuIsContinue = false
                                        contextMenuQueue = allSearchChannels.ifEmpty { listOf(result.channel) }
                                        contextMenuChannel = result.channel
                                    },
                                )
                            }
                        }
                        if (movieSearchResults.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = strings.t("FILM (${movieSearchResults.size})", "MOVIES (${movieSearchResults.size})", "PELÍCULAS (${movieSearchResults.size})"),
                                    color = CasaMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    modifier = Modifier.padding(top = if (liveSearchResults.isNotEmpty()) 6.dp else 0.dp),
                                )
                            }
                            itemsIndexed(
                                movieSearchResults,
                                key = { _, result -> "sch_movie_${result.channel.id}" },
                            ) { _, result ->
                                UniversalSearchResultCard(
                                    result = result,
                                    onClick = { vm.openVodItem(result.channel, allSearchChannels.ifEmpty { listOf(result.channel) }) },
                                    onFavorite = { vm.toggleFavorite(result.channel) },
                                    modifier = if (result.channel.id == searchFocusTargetId) {
                                        Modifier.focusRequester(searchRestoreFocus)
                                    } else {
                                        Modifier
                                    },
                                    onLongClick = {
                                        contextMenuIsContinue = false
                                        contextMenuQueue = allSearchChannels.ifEmpty { listOf(result.channel) }
                                        contextMenuChannel = result.channel
                                    },
                                )
                            }
                        }
                        if (seriesSearchResults.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = strings.t("SERIE TV (${seriesSearchResults.size})", "TV SERIES (${seriesSearchResults.size})", "SERIES TV (${seriesSearchResults.size})"),
                                    color = CasaMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    modifier = Modifier.padding(top = if (liveSearchResults.isNotEmpty() || movieSearchResults.isNotEmpty()) 6.dp else 0.dp),
                                )
                            }
                            itemsIndexed(
                                seriesSearchResults,
                                key = { _, result -> "sch_series_${result.channel.id}" },
                            ) { _, result ->
                                UniversalSearchResultCard(
                                    result = result,
                                    onClick = { vm.openVodItem(result.channel, allSearchChannels.ifEmpty { listOf(result.channel) }) },
                                    onFavorite = { vm.toggleFavorite(result.channel) },
                                    modifier = if (result.channel.id == searchFocusTargetId) {
                                        Modifier.focusRequester(searchRestoreFocus)
                                    } else {
                                        Modifier
                                    },
                                    onLongClick = {
                                        contextMenuIsContinue = false
                                        contextMenuQueue = allSearchChannels.ifEmpty { listOf(result.channel) }
                                        contextMenuChannel = result.channel
                                    },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Normal subTab view
            when (subTab) {
                TvSubTab.Channels -> {
                    if (fallback) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CasaSurface.copy(alpha = 0.85f))
                                .border(1.dp, CasaBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = strings.t(
                                    "Nessun canale per ${country?.name ?: countryCode}: mostro l’intero catalogo. Scegli un altro paese per la modalità TV.",
                                    "No channels for ${country?.name ?: countryCode}: showing the whole catalog. Choose another TV mode country.",
                                    "No hay canales para ${country?.name ?: countryCode}: muestro todo el catálogo. Elige otro país para el modo TV.",
                                ),
                                color = CasaMuted,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    when {
                        loading && queue.isEmpty() -> LoadingBox(
                            text = strings.t(
                                "Carico i canali del paese…",
                                "Loading the country channels…",
                                "Cargando los canales del país…",
                            ),
                            modifier = Modifier.weight(1f),
                        )

                        error != null && queue.isEmpty() -> ErrorBox(
                            message = error.orEmpty(),
                            onRetry = { vm.reloadTv() },
                            modifier = Modifier.weight(1f),
                        )

                        queue.isEmpty() -> EmptyBox(
                            title = strings.t("Nessun canale", "No channels", "Sin canales"),
                            message = strings.t(
                                "Non ci sono canali per ${country?.name ?: countryCode}. Scegli un altro paese.",
                                "There are no channels for ${country?.name ?: countryCode}. Choose another country.",
                                "No hay canales para ${country?.name ?: countryCode}. Elige otro país.",
                            ),
                            modifier = Modifier.weight(1f),
                            actionLabel = strings.t("Cambia paese", "Change country", "Cambiar país"),
                            onAction = { pickerOpen = true },
                        )

                        else -> LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(scale.gridMinCardWidth),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(
                                queue,
                                key = { _, channel -> channel.id },
                                contentType = { _, _ -> "channel_card" },
                            ) { index, channel ->
                                val onCardClick = remember(channel, queue) { { vm.startPlayback(channel, queue) } }
                                val onCardFavorite = remember(channel) { { vm.toggleFavorite(channel) } }
                                val chanFocusTarget = if (targetIndex >= 0) targetIndex else if (vm.channelsScrollIndex in queue.indices) vm.channelsScrollIndex else 0
                                GridChannelCard(
                                    channel = channel,
                                    onClick = onCardClick,
                                    onFavorite = onCardFavorite,
                                    onLongClick = {
                                        contextMenuIsContinue = false
                                        contextMenuQueue = queue
                                        contextMenuChannel = channel
                                    },
                                    modifier = if (index == chanFocusTarget) {
                                        Modifier.focusRequester(firstFocus)
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                            if (!loading && queue.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    val nf = remember { java.text.NumberFormat.getIntegerInstance() }
                                    val loadedFmt = nf.format(queue.size)
                                    val totalFmt = nf.format(tvTotal)
                                    val countText = if (tvTotal > queue.size) "$loadedFmt / $totalFmt" else loadedFmt
                                    val suffix = when {
                                        tvLoadingMore -> strings.t(" · Caricamento altri canali…", " · Loading more channels…", " · Cargando más canales…")
                                        !tvHasMore && tvTotal > queue.size -> strings.t(" · Usa la ricerca per tutti i canali", " · Use search for all channels", " · Usa la búsqueda para todos los canales")
                                        else -> ""
                                    }
                                    Text(
                                        text = strings.t(
                                            "Diretta TV · ${country?.name ?: countryCode} · $countText canali$suffix",
                                            "Live TV · ${country?.name ?: countryCode} · $countText channels$suffix",
                                            "TV en vivo · ${country?.name ?: countryCode} · $countText canales$suffix",
                                        ),
                                        color = CasaMuted,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }


                TvSubTab.Vod -> {
                    val vodItems by vm.vodItems.collectAsStateWithLifecycle()
                    val vodContinue by vm.vodContinue.collectAsStateWithLifecycle()
                    val vodGenres by vm.vodGenres.collectAsStateWithLifecycle()
                    val vodLanguages by vm.vodLanguages.collectAsStateWithLifecycle()
                    val vodType by vm.vodFilterType.collectAsStateWithLifecycle()
                    val vodGenre by vm.vodSelectedGenre.collectAsStateWithLifecycle()
                    val vodLang by vm.vodSelectedLanguage.collectAsStateWithLifecycle()
                    val vodLoading by vm.vodLoading.collectAsStateWithLifecycle()
                    val vodHasMore by vm.vodHasMore.collectAsStateWithLifecycle()
                    val vodError by vm.vodError.collectAsStateWithLifecycle()
                    val vodGridState = rememberLazyGridState(
                        initialFirstVisibleItemIndex = vm.vodScrollIndex,
                        initialFirstVisibleItemScrollOffset = vm.vodScrollOffset,
                    )
                    val vodRestoreFocus = remember { FocusRequester() }
                    val vodFocusTargetId = lastVodFocusId ?: lastPlayedId
                    var languageMenuOpen by remember { mutableStateOf(false) }

                    LaunchedEffect(vodType) { vm.ensureVodLoaded() }
                    LaunchedEffect(vodGridState) {
                        snapshotFlow { vodGridState.firstVisibleItemIndex to vodGridState.firstVisibleItemScrollOffset }
                            .collect { (idx, off) ->
                                vm.vodScrollIndex = idx
                                vm.vodScrollOffset = off
                            }
                    }
                    LaunchedEffect(vodGridState, vodItems.size, vodHasMore, vodLoading) {
                        if (vodHasMore && !vodLoading && vodItems.isNotEmpty()) {
                            snapshotFlow { vodGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                                .collect { lastIdx ->
                                    if (lastIdx >= vodItems.size - 24 && vodHasMore && !vodLoading) {
                                        vm.loadVodCatalog(reset = false)
                                    }
                                }
                        }
                    }
                    LaunchedEffect(subTab, searchQuery.isEmpty(), vodItems.isNotEmpty(), vodFocusTargetId, languageMenuOpen) {
                        if (!languageMenuOpen && subTab == TvSubTab.Vod && searchQuery.isBlank() && !vodFocusTargetId.isNullOrBlank()) {
                            val inContinue = vodContinue.any { it.id == vodFocusTargetId }
                            val itemIdx = vodItems.indexOfFirst { it.id == vodFocusTargetId }
                            val targetGridIdx = when {
                                inContinue -> 0
                                itemIdx >= 0 -> (if (vodContinue.isNotEmpty()) 1 else 0) + itemIdx
                                else -> -1
                            }
                            if (targetGridIdx >= 0) {
                                delay(40)
                                val visible = vodGridState.layoutInfo.visibleItemsInfo.any { it.index == targetGridIdx }
                                if (!visible) runCatching { vodGridState.scrollToItem(targetGridIdx) }
                                repeat(10) {
                                    delay(60)
                                    if (runCatching { vodRestoreFocus.requestFocus(); true }.getOrDefault(false)) {
                                        return@LaunchedEffect
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            val typeOptions = listOf(
                                "vod" to strings.t("Tutti", "All", "Todos"),
                                "movie" to strings.t("Film", "Movies", "Películas"),
                                "series" to strings.t("Serie TV", "Series", "Series"),
                            )
                            for ((key, label) in typeOptions) {
                                item(key = "vt_$key") {
                                    TvButton(
                                        text = label,
                                        onClick = { vm.setVodFilterType(key) },
                                        primary = vodType == key,
                                    )
                                }
                            }
                            if (vodLanguages.isNotEmpty()) {
                                val activeLangObj = vodLanguages.firstOrNull { it.code == vodLang }
                                item(key = "vlang_btn") {
                                    TvButton(
                                        text = activeLangObj?.name
                                            ?: strings.t("Tutte le lingue", "All languages", "Todos los idiomas"),
                                        onClick = { languageMenuOpen = true },
                                        primary = vodLang != null,
                                    )
                                }
                            }
                            if (vodGenres.isNotEmpty()) {
                                item(key = "vgen_all") {
                                    TvButton(
                                        text = strings.t("Tutti i generi", "All genres", "Todos"),
                                        onClick = { vm.setVodGenre(null) },
                                        primary = vodGenre == null,
                                    )
                                }
                                vodGenres.take(24).forEach { gen ->
                                    item(key = "vgen_${gen.id}") {
                                        TvButton(
                                            text = gen.name,
                                            onClick = { vm.setVodGenre(gen.id) },
                                            primary = vodGenre == gen.id,
                                        )
                                    }
                                }
                            }
                        }

                        when {
                            vodLoading && vodItems.isEmpty() -> LoadingBox(
                                text = strings.t("Carico Film & Serie…", "Loading Movies & Series…"),
                                modifier = Modifier.weight(1f),
                            )
                            vodError != null && vodItems.isEmpty() -> ErrorBox(
                                message = vodError.orEmpty(),
                                onRetry = { vm.loadVodCatalog(reset = true) },
                                modifier = Modifier.weight(1f),
                            )
                            vodItems.isEmpty() -> EmptyBox(
                                title = strings.t("Nessun film o serie", "No movies or series"),
                                message = strings.t(
                                    "Prova a cambiare lingua o genere.",
                                    "Try changing language or genre.",
                                ),
                                actionLabel = strings.t("Torna ai canali", "Back to channels"),
                                onAction = { vm.setSelectedTvSubTab(TvSubTab.Channels) },
                                modifier = Modifier.weight(1f),
                            )
                            else -> LazyVerticalGrid(
                                state = vodGridState,
                                columns = GridCells.Adaptive(scale.gridMinCardWidth),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (vodContinue.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.padding(bottom = 8.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = null,
                                                    tint = CasaText,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Text(
                                                    text = strings.t("Continua a guardare", "Continue Watching", "Seguir viendo"),
                                                    color = CasaText,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                            ) {
                                                items(vodContinue, key = { "cont_${it.id}" }) { contCh ->
                                                    val upNextBadge = if (contCh.upNext) {
                                                        if (contCh.seasonNumber != null && contCh.episodeNumber != null) {
                                                            "${strings.t("PROSSIMO", "NEXT", "SIG.")} S${contCh.seasonNumber.toString().padStart(2, '0')}E${contCh.episodeNumber.toString().padStart(2, '0')}"
                                                        } else {
                                                            strings.t("PROSSIMO", "NEXT", "SIGUIENTE")
                                                        }
                                                    } else null
                                                    Box(modifier = Modifier.width(scale.gridMinCardWidth)) {
                                                        GridChannelCard(
                                                            channel = contCh,
                                                            onClick = { vm.startPlayback(contCh, vodContinue) },
                                                            onFavorite = { vm.toggleFavorite(contCh) },
                                                            onLongClick = {
                                                                contextMenuIsContinue = true
                                                                contextMenuQueue = vodContinue
                                                                contextMenuChannel = contCh
                                                            },
                                                            showCountryFlag = true,
                                                            displayTitle = if (contCh.contentType == "series" && !contCh.seriesTitle.isNullOrBlank()) contCh.seriesTitle else contCh.name,
                                                            badgeText = upNextBadge,
                                                            modifier = if (contCh.id == vodFocusTargetId) {
                                                                Modifier.focusRequester(vodRestoreFocus)
                                                            } else {
                                                                Modifier
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(10.dp))
                                        }
                                    }
                                }
                                itemsIndexed(
                                    vodItems,
                                    key = { _, item -> "vod_${item.id}" },
                                ) { _, item ->
                                    GridChannelCard(
                                        channel = item,
                                        onClick = { vm.openVodItem(item, vodItems) },
                                        onFavorite = { vm.toggleFavorite(item) },
                                        onLongClick = {
                                            contextMenuIsContinue = false
                                            contextMenuQueue = vodItems
                                            contextMenuChannel = item
                                        },
                                        showCountryFlag = true,
                                        displayTitle = if (item.contentType == "series" && !item.seriesTitle.isNullOrBlank()) item.seriesTitle else item.name,
                                        modifier = if (item.id == vodFocusTargetId) {
                                            Modifier.focusRequester(vodRestoreFocus)
                                        } else {
                                            Modifier
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (languageMenuOpen) {
                        val isLight = it.casatv.tv.ui.theme.CasaIsLight
                        androidx.compose.ui.window.Dialog(onDismissRequest = { languageMenuOpen = false }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isLight) Color.White else Color(0xFF0A0A0A))
                                    .border(1.dp, CasaBorder, RoundedCornerShape(8.dp))
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = strings.t("Filtra per lingua", "Filter by language", "Filtrar por idioma"),
                                    color = CasaText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.height(340.dp),
                                ) {
                                    item(span = { GridItemSpan(2) }) {
                                        TvButton(
                                            text = strings.t("Tutte le lingue", "All languages", "Todos los idiomas"),
                                            onClick = {
                                                vm.setVodLanguage(null)
                                                languageMenuOpen = false
                                            },
                                            primary = vodLang == null,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    itemsIndexed(vodLanguages, key = { _, l -> "lang_${l.code}" }) { _, langItem ->
                                        val langInteraction = remember { MutableInteractionSource() }
                                        val langFocused by langInteraction.collectIsFocusedAsState()
                                        val langSelected = vodLang == langItem.code
                                        val focusBg = if (isLight) Color.Black else Color.White
                                        val focusFg = if (isLight) Color.White else Color.Black
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    when {
                                                        langFocused -> focusBg
                                                        langSelected -> CasaSurfaceHigh
                                                        else -> CasaSurface
                                                    },
                                                )
                                                .border(
                                                    width = if (langFocused || langSelected) 2.dp else 1.dp,
                                                    color = when {
                                                        langFocused -> focusBg
                                                        langSelected -> CasaText
                                                        else -> CasaBorder
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                )
                                                .clickable(interactionSource = langInteraction, indication = null) {
                                                    vm.setVodLanguage(langItem.code)
                                                    languageMenuOpen = false
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CountryFlagIcon(
                                                countryCode = langItem.flag,
                                                width = 20.dp,
                                                height = 14.dp,
                                                cornerRadius = 2.dp,
                                                fallbackTint = if (langFocused) focusFg else CasaMuted,
                                            )
                                            Text(
                                                text = "${langItem.name} (${langItem.count})",
                                                color = if (langFocused) focusFg else CasaText,
                                                fontSize = 12.sp,
                                                fontWeight = if (langFocused || langSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TvSubTab.Favorites -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        FavoritesScreen(
                            vm = vm,
                            onBrowse = { vm.setSelectedTvSubTab(TvSubTab.Channels) },
                        )
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        CountryPickerOverlay(
            countries = catalog,
            selected = countryCode,
            title = strings.t("Paese della modalità TV", "TV mode country", "País del modo TV"),
            subtitle = strings.t(
                "I canali della modalità TV vengono ordinati per il paese scelto.",
                "TV mode channels are ordered by the selected country.",
                "Los canales del modo TV se ordenan por el país elegido.",
            ),
            onSelect = { selected ->
                pickerOpen = false
                vm.setTvCountry(selected.code)
            },
            onClose = { pickerOpen = false },
        )
    }

    if (showMixRegionPicker) {
        TvMixRegionPickerHost(vm)
    }

    contextMenuChannel?.let { targetCh ->
        ChannelContextMenuDialog(
            channel = targetCh,
            vm = vm,
            onDismiss = { contextMenuChannel = null },
            onPlay = {
                contextMenuChannel = null
                vm.openVodItem(targetCh, contextMenuQueue.ifEmpty { listOf(targetCh) })
            },
            onPlayWithSource = { srcs, idx ->
                contextMenuChannel = null
                vm.startPlayback(
                    targetCh,
                    contextMenuQueue.ifEmpty { listOf(targetCh) },
                    preloadedSources = srcs,
                    initialSourceIndex = idx,
                )
            },
            onToggleFavorite = {
                vm.toggleFavorite(targetCh)
                contextMenuChannel = null
            },
            onStartMixCountry = { cc ->
                contextMenuChannel = null
                vm.startMixForCountry(cc)
            },
            onRemoveContinue = if (contextMenuIsContinue) {
                {
                    vm.removeVodFromContinue(targetCh.id)
                    contextMenuChannel = null
                }
            } else null,
        )
    }
}

@Composable
fun TvSubTabSelector(
    selected: TvSubTab,
    onSelect: (TvSubTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(CasaSurface)
            .border(1.dp, CasaBorder, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isLight = it.casatv.tv.ui.theme.CasaIsLight
        TvSubTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val bg = when {
                focused -> if (isLight) Color.Black else Color.White
                isSelected -> CasaSurfaceHigh
                else -> Color.Transparent
            }
            val textCol = when {
                focused -> if (isLight) Color.White else Color.Black
                isSelected -> CasaText
                else -> CasaMuted
            }
            val borderCol = when {
                focused -> if (isLight) Color.Black else Color.White
                isSelected -> CasaBorder
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg)
                    .border(if (focused) 1.5.dp else if (isSelected) 1.dp else 0.dp, borderCol, RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(tab) },
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label(strings.language),
                    color = textCol,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun TvMixButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val shape = RoundedCornerShape(6.dp)
    val bg = if (focused) (if (isLight) Color.Black else Color.White) else CasaSurface
    val borderCol = if (focused) (if (isLight) Color.Black else Color.White) else CasaBorder
    val textCol = if (focused) (if (isLight) Color.White else Color.Black) else CasaText
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, borderCol, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (focused) textCol else CasaText),
        )
        Text(
            text = "Mix",
            color = textCol,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TvModeCountryButton(
    country: Facet?,
    countryCode: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (focused) (if (isLight) Color.Black else Color.White) else CasaSurface)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) (if (isLight) Color.Black else Color.White) else CasaBorder,
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountryFlagIcon(
            countryCode = country?.code ?: countryCode,
            width = 20.dp,
            height = 14.dp,
            cornerRadius = 2.dp,
            fallbackTint = if (focused) (if (isLight) Color.White else Color.Black) else CasaMuted,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = country?.name ?: countryCode,
            color = if (focused) (if (isLight) Color.White else Color.Black) else CasaText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = CasaIcons.KeyboardArrowDown,
            contentDescription = null,
            tint = if (focused) (if (isLight) Color.White else Color.Black) else CasaMuted,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(shape)
            .background(
                if (focused) (if (isLight) Color.Black else Color.White)
                else CasaSurface,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) (if (isLight) Color.Black else Color.White) else CasaBorder,
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) (if (isLight) Color.White else Color.Black) else CasaText,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun TopBarProfileButton(
    avatar: String,
    profileName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(shape)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) (if (isLight) Color.Black else Color.White) else CasaBorder,
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ProfileAvatarBadge(
            avatar = avatar,
            name = profileName,
            size = 34.dp,
        )
    }
}

@Composable
private fun TopBarUpdateBadge(
    version: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (focused) (if (isLight) Color.Black else Color.White)
                else CasaSurface,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) (if (isLight) Color.Black else Color.White) else CasaText,
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CasaIcons.ArrowUpward,
            contentDescription = null,
            tint = if (focused) (if (isLight) Color.White else Color.Black) else CasaText,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "v$version",
            color = if (focused) (if (isLight) Color.White else Color.Black) else CasaText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
