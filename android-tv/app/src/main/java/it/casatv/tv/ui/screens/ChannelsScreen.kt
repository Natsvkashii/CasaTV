package it.casatv.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.tv.data.BrowseFilter
import it.casatv.tv.data.Channel
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.buildLanguageCatalog
import it.casatv.tv.ui.components.EmptyBox
import it.casatv.tv.ui.components.ErrorBox
import it.casatv.tv.ui.components.FilterPill
import it.casatv.tv.ui.components.GridChannelCard
import it.casatv.tv.ui.components.LoadingBox
import it.casatv.tv.ui.components.ScreenHeader
import it.casatv.tv.ui.components.TvButton
import it.casatv.tv.ui.components.channelCountLabel
import it.casatv.tv.ui.formatNumber
import it.casatv.tv.ui.theme.CasaBackground
import it.casatv.tv.ui.theme.CasaBlue
import it.casatv.tv.ui.theme.CasaBorder
import it.casatv.tv.ui.theme.CasaDim
import it.casatv.tv.ui.theme.CasaGreen
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaSurface
import it.casatv.tv.ui.theme.CasaSurfaceHigh
import it.casatv.tv.ui.theme.CasaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ChannelsScreen(vm: MainViewModel) {
    val browse by vm.browse.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val keyboard = LocalSoftwareKeyboardController.current
    val strings = LocalAppStrings.current
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.ensureBrowseLoaded()
    }

    LaunchedEffect(browse.items.isNotEmpty()) {
        if (!initialFocusDone && browse.items.isNotEmpty()) {
            delay(160)
            runCatching { firstFocus.requestFocus() }
            initialFocusDone = true
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                val state = vm.browse.value
                if (last >= state.items.size - 12) vm.loadMoreBrowse()
            }
    }

    var countryPickerOpen by remember { mutableStateOf(false) }
    var categoryPickerOpen by remember { mutableStateOf(false) }
    var languagePickerOpen by remember { mutableStateOf(false) }

    val countries = config?.facets?.countries ?: emptyList()
    val categories = config?.facets?.categories ?: emptyList()
    val languages = remember(config?.facets?.languages, strings.language) {
        buildLanguageCatalog(config?.facets?.languages.orEmpty(), strings.language)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Single unified filter bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SearchBar(
                    query = browse.filter.query,
                    onQueryChange = { vm.setBrowseQuery(it) },
                    onSearch = { keyboard?.hide() },
                    modifier = Modifier.weight(1f),
                )

                FilterIconButton(
                    iconText = browse.filter.country?.let { code ->
                        countries.firstOrNull { it.code == code }?.flag ?: code
                    } ?: "🌐",
                    label = browse.filter.country?.let { code ->
                        countries.firstOrNull { it.code == code }?.name ?: code
                    } ?: strings.t("Paesi", "Countries", "Países"),
                    isActive = browse.filter.country != null,
                    onClick = { countryPickerOpen = true },
                )

                FilterIconButton(
                    iconText = "📁",
                    label = browse.filter.category?.let { catId ->
                        categories.firstOrNull { it.id == catId }?.name ?: catId
                    } ?: strings.t("Categorie", "Categories", "Categorías"),
                    isActive = browse.filter.category != null,
                    onClick = { categoryPickerOpen = true },
                )

                FilterIconButton(
                    iconText = "🗣️",
                    label = browse.filter.language?.let { langCode ->
                        languages.firstOrNull { it.code == langCode }?.name ?: langCode
                    } ?: strings.t("Lingue", "Languages", "Idiomas"),
                    isActive = browse.filter.language != null,
                    onClick = { languagePickerOpen = true },
                )

                TvIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = strings.t("Ricarica", "Reload", "Recargar"),
                    onClick = { vm.reloadBrowse() },
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CasaSurface.copy(alpha = 0.8f))
                        .border(1.dp, CasaBorder, RoundedCornerShape(50))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (browse.loading) CasaMuted else CasaGreen),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (browse.loading) "…" else formatNumber(browse.total, strings.language),
                        color = CasaMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

        when {
            browse.loading && browse.items.isEmpty() -> LoadingBox(
                text = strings.t("Cerco i canali…", "Loading channels…", "Buscando canales…"),
                modifier = Modifier.weight(1f),
            )

            browse.error != null && browse.items.isEmpty() -> ErrorBox(
                message = browse.error.orEmpty(),
                onRetry = { vm.ensureBrowseLoaded() },
                modifier = Modifier.weight(1f),
            )

            browse.items.isEmpty() && browse.initialized -> EmptyBox(
                title = strings.t("Nessun canale trovato", "No channels found", "Ningún canal encontrado"),
                message = strings.t(
                    "Prova a cambiare paese o categoria, oppure cerca un altro nome.",
                    "Try a different country or category, or search for another name.",
                    "Prueba a cambiar el país o la categoría, o busca otro nombre.",
                ),
                modifier = Modifier.weight(1f),
                actionLabel = strings.t("Azzera filtri", "Clear filters", "Borrar filtros"),
                onAction = {
                    vm.setBrowseCountry(null)
                    vm.setBrowseCategory(null)
                    vm.setBrowseLanguage(null)
                    vm.setBrowseQuery("")
                },
            )

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(178.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(browse.items, key = { _, channel -> channel.id }) { index, channel ->
                    GridChannelCard(
                        channel = channel,
                        onClick = {
                            keyboard?.hide()
                            vm.startPlayback(channel, browse.items)
                        },
                        onFavorite = { vm.toggleFavorite(channel) },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        },
                    )
                }
                if (browse.loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = CasaGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                if (!browse.hasMore && browse.items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = if (browse.items.size == 1) {
                                strings.t(
                                    "Fine del catalogo · 1 canale visualizzato",
                                    "End of catalog · 1 channel shown",
                                    "Fin del catálogo · 1 canal mostrado",
                                )
                            } else {
                                strings.t(
                                    "Fine del catalogo · ${formatNumber(browse.items.size, strings.language)} canali visualizzati",
                                    "End of catalog · ${formatNumber(browse.items.size, strings.language)} channels shown",
                                    "Fin del catálogo · ${formatNumber(browse.items.size, strings.language)} canales mostrados",
                                )
                            },
                            color = CasaDim,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }

    if (countryPickerOpen) {
            FilterOptionsModal(
                title = strings.t("Filtra per paese", "Filter by country", "Filtrar por país"),
                options = countries,
                selectedId = browse.filter.country,
                allLabel = strings.t("Tutti i paesi", "All countries", "Todos los países"),
                optionId = { it.code },
                optionLabel = { "${it.flag} ${it.name}" },
                onSelect = {
                    vm.setBrowseCountry(it)
                    countryPickerOpen = false
                },
                onClose = { countryPickerOpen = false },
            )
        }

        if (categoryPickerOpen) {
            FilterOptionsModal(
                title = strings.t("Filtra per categoria", "Filter by category", "Filtrar por categoría"),
                options = categories,
                selectedId = browse.filter.category,
                allLabel = strings.t("Tutte le categorie", "All categories", "Todas las categorías"),
                optionId = { it.id },
                optionLabel = { it.name },
                onSelect = {
                    vm.setBrowseCategory(it)
                    categoryPickerOpen = false
                },
                onClose = { categoryPickerOpen = false },
            )
        }

        if (languagePickerOpen) {
            FilterOptionsModal(
                title = strings.t("Filtra per lingua", "Filter by language", "Filtrar por idioma"),
                options = languages,
                selectedId = browse.filter.language,
                allLabel = strings.t("Tutte le lingue", "All languages", "Todos los idiomas"),
                optionId = { it.code.ifBlank { it.name } },
                optionLabel = { it.name },
                onSelect = {
                    vm.setBrowseLanguage(it)
                    languagePickerOpen = false
                },
                onClose = { languagePickerOpen = false },
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        placeholder = {
            Text(
                strings.t("Cerca un canale per nome…", "Search for a channel by name…", "Buscar un canal por nombre…"),
                color = CasaDim,
                fontSize = 13.sp,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = CasaDim,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(CasaSurface)
                        .border(1.dp, CasaBorder, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures { onQueryChange("") }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = strings.t("Cancella ricerca", "Clear search", "Borrar búsqueda"),
                        tint = CasaMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        },
        textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
            color = CasaText,
        ),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CasaGreen,
            unfocusedBorderColor = CasaBorder,
            focusedContainerColor = CasaBackground.copy(alpha = 0.75f),
            unfocusedContainerColor = CasaBackground.copy(alpha = 0.75f),
            cursorColor = CasaGreen,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

@Composable
private fun FilterIconButton(
    iconText: String,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val shape = RoundedCornerShape(12.dp)

    val bg = when {
        focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        isActive -> if (isLight) Color(0x1F0070F3) else it.casatv.tv.ui.theme.CasaBlue.copy(alpha = 0.25f)
        else -> if (isLight) it.casatv.tv.ui.theme.CasaSurface else Color(0x1AFFFFFF)
    }
    val borderCol = when {
        focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        isActive -> it.casatv.tv.ui.theme.CasaBlue
        else -> CasaBorder
    }
    val textCol = when {
        focused -> if (isLight) Color.White else Color.Black
        isActive -> it.casatv.tv.ui.theme.CasaBlue
        else -> CasaText
    }

    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(
                if (focused) 2.dp else 1.dp,
                borderCol,
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = iconText, fontSize = 15.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = textCol,
            fontSize = 12.sp,
            fontWeight = if (isActive || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "▾",
            color = if (focused) (if (isLight) Color.White.copy(alpha = 0.85f) else Color.Black) else CasaDim,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun TvIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val shape = RoundedCornerShape(12.dp)

    val bg = if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else if (isLight) it.casatv.tv.ui.theme.CasaSurface else Color(0x1AFFFFFF)
    val borderCol = if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaBorder
    val iconTint = if (focused) (if (isLight) Color.White else Color.Black) else CasaText

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, borderCol, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun <T> FilterOptionsModal(
    title: String,
    options: List<T>,
    selectedId: String?,
    allLabel: String,
    optionId: (T) -> String,
    optionLabel: (T) -> String,
    onSelect: (String?) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxHeight(0.75f)
                .clip(RoundedCornerShape(20.dp))
                .background(CasaSurfaceHigh)
                .border(1.dp, CasaBorder, RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = CasaText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                TvButton(
                    text = "✕",
                    onClick = onClose,
                    primary = false,
                )
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "all-option") {
                    ModalOptionItem(
                        text = allLabel,
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(options, key = { optionId(it) }) { opt ->
                    ModalOptionItem(
                        text = optionLabel(opt),
                        selected = selectedId == optionId(opt),
                        onClick = { onSelect(optionId(opt)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModalOptionItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        selected -> if (isLight) Color(0x1F0070F3) else it.casatv.tv.ui.theme.CasaBlue.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val borderCol = when {
        focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        selected -> it.casatv.tv.ui.theme.CasaBlue
        else -> Color.Transparent
    }
    val textCol = when {
        focused -> if (isLight) Color.White else Color.Black
        selected -> it.casatv.tv.ui.theme.CasaBlue
        else -> CasaText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, borderCol, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            color = textCol,
            fontSize = 14.sp,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Text(
                text = "✓",
                color = if (focused) (if (isLight) Color.White else Color.Black) else it.casatv.tv.ui.theme.CasaBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
