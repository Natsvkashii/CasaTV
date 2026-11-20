package it.casatv.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.components.EmptyBox
import it.casatv.tv.ui.components.ErrorBox
import it.casatv.tv.ui.components.GridChannelCard
import it.casatv.tv.ui.components.LoadingBox
import it.casatv.tv.ui.components.TvButton
import it.casatv.tv.ui.formatNumber
import it.casatv.tv.ui.theme.CasaDim
import it.casatv.tv.ui.theme.CasaGreen
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaText
import kotlinx.coroutines.delay

@Composable
fun FavoritesScreen(
    vm: MainViewModel,
    onBrowse: () -> Unit,
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val loading by vm.favoritesLoading.collectAsStateWithLifecycle()
    val error by vm.favoritesError.collectAsStateWithLifecycle()
    val lastPlayedId by vm.lastPlayedChannelId.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val targetIndex = remember(favorites, lastPlayedId) {
        favorites.indexOfFirst { it.id == lastPlayedId }.takeIf { it >= 0 } ?: 0
    }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = targetIndex)
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadFavorites(showLoading = favorites.isEmpty())
    }

    LaunchedEffect(favorites.isNotEmpty(), targetIndex) {
        if (!initialFocusDone && favorites.isNotEmpty()) {
            runCatching { gridState.scrollToItem(targetIndex) }
            delay(160)
            runCatching { firstFocus.requestFocus() }
            initialFocusDone = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.t("Preferiti", "Favorites", "Favoritos"),
                    color = CasaText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (favorites.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "·  ${favorites.size} ${if (favorites.size == 1) strings.t("canale", "channel", "canal") else strings.t("canali", "channels", "canales")}",
                        color = CasaMuted,
                        fontSize = 14.sp,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MENU",
                    color = CasaGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = strings.t(
                        "aggiunge o rimuove un canale",
                        "adds or removes a channel",
                        "añade o quita un canal",
                    ),
                    color = CasaDim,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(14.dp))
                TvButton(
                    text = strings.t("Sfoglia i canali", "Browse channels", "Explorar canales"),
                    onClick = onBrowse,
                    primary = false,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            loading && favorites.isEmpty() -> LoadingBox(
                text = strings.t("Carico i preferiti…", "Loading favorites…"),
                modifier = Modifier.weight(1f),
            )

            error != null && favorites.isEmpty() -> ErrorBox(
                message = error.orEmpty(),
                onRetry = { vm.loadFavorites() },
                modifier = Modifier.weight(1f),
            )

            favorites.isEmpty() -> EmptyBox(
                title = strings.t("Nessun preferito", "No favorites yet"),
                message = strings.t(
                    "Apri Canali o Home, evidenzia un canale e premi il tasto MENU del telecomando. " +
                        "Lo ritroverai qui, sincronizzato con il server.",
                    "Open Channels or Home, highlight a channel and press MENU on the remote. " +
                        "You will find it here, synced with the server.",
                ),
                modifier = Modifier.weight(1f),
                actionLabel = strings.t("Vai ai canali", "Go to channels"),
                onAction = onBrowse,
            )

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(178.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(
                    favorites,
                    key = { _, channel -> channel.id },
                    contentType = { _, _ -> "channel_card" },
                ) { index, channel ->
                    val onCardClick = remember(channel, favorites) { { vm.startPlayback(channel, favorites) } }
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
            }
        }
    }
}
