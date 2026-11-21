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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.tv.data.Facet
import it.casatv.tv.data.GlobalSearchResult
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.MixRegionPickerDialog
import it.casatv.tv.ui.TvSubTab
import it.casatv.tv.ui.buildCountryCatalog
import it.casatv.tv.ui.computeFilteredMixRegionCounts
import it.casatv.tv.ui.computeMixRegionCounts
import it.casatv.tv.ui.countMatchingMixChannels
import it.casatv.tv.ui.components.EmptyBox
import it.casatv.tv.ui.components.ErrorBox
import it.casatv.tv.ui.components.GridChannelCard
import it.casatv.tv.ui.components.LoadingBox
import it.casatv.tv.ui.components.TvButton
import it.casatv.tv.ui.components.TvUniversalSearchBar
import it.casatv.tv.ui.components.UniversalSearchResultCard
import it.casatv.tv.ui.components.channelCountLabel
import it.casatv.tv.ui.components.rememberTvUiScale
import it.casatv.tv.ui.countryFacet
import it.casatv.tv.ui.countryFlag
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
    initialSubTab: TvSubTab = TvSubTab.Channels,
    onOpenSettings: () -> Unit = {},
    onOpenUpdate: (() -> Unit)? = null,
) {
    val queue by vm.zapQueue.collectAsStateWithLifecycle()
    val loading by vm.tvLoading.collectAsStateWithLifecycle()
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
    val lastPlayedId by vm.lastPlayedChannelId.collectAsStateWithLifecycle()
    val showMixRegionPicker by vm.showMixRegionPicker.collectAsStateWithLifecycle()
    val selectedMixRegions by vm.selectedMixRegions.collectAsStateWithLifecycle()
    val selectedMixLanguages by vm.selectedMixLanguages.collectAsStateWithLifecycle()
    val selectedMixGenres by vm.selectedMixGenres.collectAsStateWithLifecycle()
    val mixChannels by vm.mixChannels.collectAsStateWithLifecycle()
    val mixIncludeAudioOnly by vm.mixIncludeAudioOnly.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val scale = rememberTvUiScale()
    val targetIndex = remember(queue, lastPlayedId) {
        queue.indexOfFirst { it.id == lastPlayedId }.takeIf { it >= 0 } ?: 0
    }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    val firstFocus = remember { FocusRequester() }
    var pickerOpen by remember { mutableStateOf(false) }
    var subTab by rememberSaveable { mutableStateOf(initialSubTab) }

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

    LaunchedEffect(queue.isNotEmpty(), subTab, searchQuery.isEmpty(), targetIndex, pickerOpen) {
        if (!pickerOpen && queue.isNotEmpty() && subTab == TvSubTab.Channels && searchQuery.isBlank()) {
            runCatching { gridState.scrollToItem(targetIndex) }
            delay(160)
            runCatching { firstFocus.requestFocus() }
        }
    }

    BackHandler(enabled = searchQuery.isNotBlank() || subTab != TvSubTab.Channels) {
        if (searchQuery.isNotBlank()) {
            vm.clearSearch()
        } else {
            subTab = TvSubTab.Channels
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
                    subTab = it
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

            // 3. Paese selezionato
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
                    .clip(RoundedCornerShape(50))
                    .background(CasaSurface.copy(alpha = 0.85f))
                    .border(1.dp, CasaBorder, RoundedCornerShape(50))
                    .padding(horizontal = 13.dp),
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
                Text(
                    text = if (isServerDisconnected) {
                        strings.t("Offline", "Offline", "Offline")
                    } else if (searchQuery.isNotBlank()) {
                        if (searchLoading) strings.t("Cerco…", "Searching…", "Buscando…")
                        else "${searchResults.size} " + strings.t("risultati", "results", "resultados")
                    } else if (loading) {
                        strings.t("Carico…", "Loading…", "Cargando…")
                    } else {
                        when (subTab) {
                            TvSubTab.Channels -> channelCountLabel(queue.size)
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
                avatar = profile?.avatar ?: "👤",
                onClick = { vm.switchProfile() },
            )

            // 7. Icona Impostazioni in alto a destra
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

                else -> LazyVerticalGrid(
                    state = rememberLazyGridState(),
                    columns = GridCells.Adaptive(scale.gridMinCardWidth),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(
                        searchResults,
                        key = { _, result ->
                            when (result) {
                                is GlobalSearchResult.ChannelResult -> "ch_${result.channel.id}"
                            }
                        },
                    ) { _, result ->
                        UniversalSearchResultCard(
                            result = result,
                            onClick = {
                                when (result) {
                                    is GlobalSearchResult.ChannelResult -> {
                                        val channels = searchResults.filterIsInstance<GlobalSearchResult.ChannelResult>().map { it.channel }
                                        vm.startPlayback(result.channel, channels.ifEmpty { listOf(result.channel) })
                                    }
                                }
                            },
                            onFavorite = {
                                if (result is GlobalSearchResult.ChannelResult) {
                                    vm.toggleFavorite(result.channel)
                                }
                            },
                        )
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
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            itemsIndexed(
                                queue,
                                key = { _, channel -> channel.id },
                                contentType = { _, _ -> "channel_card" },
                            ) { index, channel ->
                                val onCardClick = remember(channel, queue) { { vm.startPlayback(channel, queue) } }
                                val onCardFavorite = remember(channel) { { vm.toggleFavorite(channel) } }
                                GridChannelCard(
                                    channel = channel,
                                    onClick = onCardClick,
                                    onFavorite = onCardFavorite,
                                    modifier = if (index == targetIndex) {
                                        Modifier.focusRequester(firstFocus)
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                            if (!loading && queue.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = strings.t(
                                            "Diretta TV · ${country?.name ?: countryCode} · ${queue.size} canali",
                                            "Live TV · ${country?.name ?: countryCode} · ${queue.size} channels",
                                            "TV en vivo · ${country?.name ?: countryCode} · ${queue.size} canales",
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


                TvSubTab.Favorites -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        FavoritesScreen(
                            vm = vm,
                            onBrowse = { subTab = TvSubTab.Channels },
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
        val mixPool = mixChannels.ifEmpty { queue }
        val hasExtraFilters = selectedMixLanguages.isNotEmpty() || selectedMixGenres.isNotEmpty()
        val regionCounts = remember(config?.facets?.countries, mixPool, hasExtraFilters, selectedMixLanguages, selectedMixGenres, mixIncludeAudioOnly) {
            if (hasExtraFilters && mixPool.isNotEmpty()) {
                computeFilteredMixRegionCounts(
                    channels = mixPool,
                    includeAudioOnly = mixIncludeAudioOnly,
                    selectedLanguages = selectedMixLanguages,
                    selectedGenres = selectedMixGenres,
                )
            } else {
                computeMixRegionCounts(config?.facets?.countries.orEmpty())
            }
        }
        val matchingCount = remember(mixPool, hasExtraFilters, selectedMixRegions, selectedMixLanguages, selectedMixGenres, mixIncludeAudioOnly) {
            if (!hasExtraFilters || mixPool.isEmpty()) {
                null
            } else {
                countMatchingMixChannels(
                    channels = mixPool,
                    includeAudioOnly = mixIncludeAudioOnly,
                    selectedRegions = selectedMixRegions,
                    selectedLanguages = selectedMixLanguages,
                    selectedGenres = selectedMixGenres,
                )
            }
        }
        val totalAllChannels = remember(config?.stats?.channels, queue.size) {
            config?.stats?.channels ?: queue.size
        }
        MixRegionPickerDialog(
            selectedRegions = selectedMixRegions,
            regionCounts = regionCounts,
            totalChannels = totalAllChannels,
            onToggleRegion = { vm.toggleMixRegion(it) },
            onStartMix = { vm.startMixFromSelectedRegions() },
            onDismiss = { vm.dismissMixRegionPicker() },
            selectedLanguages = selectedMixLanguages,
            selectedGenres = selectedMixGenres,
            matchingChannelCount = matchingCount,
            onToggleLanguage = { vm.toggleMixLanguage(it) },
            onToggleGenre = { vm.toggleMixGenre(it) },
            onClearFilters = { vm.clearMixFilters() },
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
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(CasaSurface.copy(alpha = 0.85f))
            .border(1.dp, CasaBorder, RoundedCornerShape(50))
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
                focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
                isSelected -> if (isLight) Color(0x1F0070F3) else Color(0x33FFFFFF)
                else -> Color.Transparent
            }
            val textCol = when {
                focused -> if (isLight) Color.White else Color.Black
                isSelected -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
                else -> CasaMuted
            }
            val borderCol = when {
                focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .border(if (focused) 1.5.dp else 0.dp, borderCol, RoundedCornerShape(50))
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
    val shape = RoundedCornerShape(50)
    val bg = when {
        focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        else -> if (isLight) Color(0x140070F3) else Color(0x1800D4FF)
    }
    val borderCol = when {
        focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        else -> if (isLight) Color(0x440070F3) else Color(0x5500D4FF)
    }
    val textCol = when {
        focused -> if (isLight) Color.White else Color.Black
        else -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color(0xFF7DD3FC)
    }
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
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    when {
                        focused -> if (isLight) Color.White else Color(0xFF0070F3)
                        else -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color(0xFF00D4FF)
                    },
                ),
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
    val shape = RoundedCornerShape(9999.dp)
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else if (isLight) CasaSurface else Color(0x1FFFFFFF))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaBorder,
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
        Text(text = country?.flag ?: "🌍", fontSize = 16.sp)
        Spacer(Modifier.width(7.dp))
        Text(
            text = country?.name ?: countryCode,
            color = if (focused) (if (isLight) Color.White else Color.Black) else CasaText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "▾",
            color = if (focused) (if (isLight) Color(0xCCFFFFFF) else Color.Black) else CasaMuted,
            fontSize = 11.sp,
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
                if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White)
                else if (isLight) CasaSurface else Color(0x1AFFFFFF),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaBorder,
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
                if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White)
                else if (isLight) CasaSurface else Color(0x1AFFFFFF),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaBorder,
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = avatar, fontSize = 17.sp)
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
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White)
                else CasaGreen.copy(alpha = 0.2f),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaGreen,
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
        Text(
            text = "⬆ v$version",
            color = if (focused) (if (isLight) Color.White else Color.Black) else CasaGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
