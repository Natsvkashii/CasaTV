package it.casatv.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.tv.data.Facet
import it.casatv.tv.data.Profile
import it.casatv.tv.data.ProfileLanguage
import it.casatv.tv.ui.LocalAppStrings
import it.casatv.tv.ui.MainViewModel
import it.casatv.tv.ui.buildCountryCatalog
import it.casatv.tv.ui.components.BackgroundGlow
import it.casatv.tv.ui.components.TvButton
import it.casatv.tv.ui.theme.CasaBackground
import it.casatv.tv.ui.theme.CasaBorder
import it.casatv.tv.ui.theme.CasaDim
import it.casatv.tv.ui.theme.CasaGreen
import it.casatv.tv.ui.theme.CasaMuted
import it.casatv.tv.ui.theme.CasaRed
import it.casatv.tv.ui.theme.CasaSurface
import it.casatv.tv.ui.theme.CasaSurfaceHigh
import it.casatv.tv.ui.theme.CasaText
import kotlinx.coroutines.delay

@Composable
fun ProfileGateScreen(vm: MainViewModel) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeProfile by vm.activeProfile.collectAsStateWithLifecycle()
    val avatars by vm.profileAvatars.collectAsStateWithLifecycle()
    val languages by vm.profileLanguages.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val busy by vm.profileBusy.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    var managing by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }

    val countries = remember(config?.facets?.countries, strings.language) {
        buildCountryCatalog(config?.facets?.countries.orEmpty(), strings.language)
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(creating, editing) {
        if (!creating && editing == null && profiles.isNotEmpty()) {
            delay(150)
            runCatching { focusRequester.requestFocus() }
        }
    }

    BackHandler(enabled = true) {
        when {
            busy -> vm.cancelProfileSelection()
            creating -> creating = false
            editing != null -> editing = null
            managing -> managing = false
            activeProfile != null -> vm.closeProfileGate()
        }
    }

    Box(Modifier.fillMaxSize().background(CasaBackground)) {
        BackgroundGlow()

        if (creating || editing != null) {
            ProfileEditor(
                profile = editing,
                avatars = avatars,
                languages = languages,
                countries = countries,
                canDelete = editing != null && profiles.size > 1,
                busy = busy,
                onClose = {
                    creating = false
                    editing = null
                },
                onSave = { name, avatar, language, country ->
                    val current = editing
                    if (current == null) {
                        vm.createProfile(name, avatar, language, country) { ok ->
                            if (ok) creating = false
                        }
                    } else {
                        vm.updateProfile(current, name, avatar, language, country) { ok ->
                            if (ok) editing = null
                        }
                    }
                },
                onDelete = {
                    val current = editing
                    if (current != null) {
                        vm.deleteProfile(current) { ok ->
                            if (ok) editing = null
                        }
                    }
                },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = strings.t("Chi sta guardando?", "Who's watching?", "¿Quién está viendo?"),
                    color = CasaText,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.t(
                        "Scegli un profilo per continuare",
                        "Choose a profile to continue",
                        "Elige un perfil para continuar",
                    ),
                    color = CasaMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(40.dp))

                val sortedProfiles = remember(profiles) {
                    profiles.sortedWith(
                        compareBy(
                            { it.id == "guest" || it.isGuest || it.name.equals("Ospite", ignoreCase = true) || it.name.equals("Guest", ignoreCase = true) },
                            { it.createdAt },
                        ),
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowItemsIndexed(sortedProfiles, key = { _, profile -> profile.id }) { index, profile ->
                        val isActive = activeProfile?.id == profile.id
                        AppleProfileTile(
                            profile = profile,
                            active = isActive,
                            managing = managing,
                            onClick = {
                                if (managing) {
                                    if (profile.id == "guest") {
                                        vm.showMessage(
                                            strings.t(
                                                "Il profilo Ospite è temporaneo e non può essere modificato.",
                                                "The Guest profile is temporary and cannot be edited.",
                                                "El perfil Invitado es temporal y no se puede editar.",
                                            ),
                                        )
                                    } else {
                                        editing = profile
                                    }
                                } else {
                                    vm.selectProfile(profile)
                                }
                            },
                            modifier = if (
                                (activeProfile == null && index == 0) || isActive
                            ) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        )
                    }

                    item(key = "add_profile") {
                        AppleNewProfileTile(
                            enabled = !busy,
                            onClick = { creating = true },
                        )
                    }
                }

                Spacer(Modifier.height(46.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (activeProfile != null && !managing) {
                        TvButton(
                            text = strings.t("Torna indietro", "Go back", "Volver"),
                            onClick = { vm.closeProfileGate() },
                            primary = false,
                        )
                    }
                    TvButton(
                        text = if (managing) {
                            strings.t("Fine", "Done", "Listo")
                        } else {
                            strings.t("Gestisci profili", "Manage profiles", "Gestionar perfiles")
                        },
                        onClick = { managing = !managing },
                        primary = managing,
                    )
                }
            }
        }

        if (busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(it.casatv.tv.ui.theme.CasaBackground.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CasaGreen, strokeWidth = 3.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = strings.t("Aggiorno il profilo…", "Updating profile…"),
                        color = CasaMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleProfileTile(
    profile: Profile,
    active: Boolean,
    managing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight

    Column(
        modifier = modifier
            .width(134.dp)
            .focusable(interactionSource = interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tileBg = if (isLight) {
            if (focused) it.casatv.tv.ui.theme.CasaSurfaceHigh else it.casatv.tv.ui.theme.CasaSurface
        } else {
            if (focused) Color(0xFF2C2C2E) else Color(0xFF1C1C1E)
        }
        val tileBorder = when {
            focused -> if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
            active -> CasaGreen
            else -> if (isLight) it.casatv.tv.ui.theme.CasaBorder else Color(0x33FFFFFF)
        }
        Box(
            modifier = Modifier
                .size(118.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(tileBg)
                .border(
                    width = if (focused) 3.dp else if (active) 2.dp else 1.dp,
                    color = tileBorder,
                    shape = RoundedCornerShape(32.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = profile.avatar, fontSize = 52.sp)
            if (active) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(CasaGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = profile.name,
            color = if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaText,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (managing) {
                strings.t("Modifica", "Edit", "Editar")
            } else {
                "${profile.flag} ${profile.displayLanguage}"
            },
            color = if (managing) CasaGreen else CasaDim,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppleNewProfileTile(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isLight = it.casatv.tv.ui.theme.CasaIsLight

    Column(
        modifier = modifier
            .width(134.dp)
            .focusable(enabled = enabled, interactionSource = interaction)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tileBg = if (isLight) {
            if (focused) it.casatv.tv.ui.theme.CasaSurfaceHigh else it.casatv.tv.ui.theme.CasaSurface
        } else {
            if (focused) Color(0xFF2C2C2E) else Color(0xFF141416)
        }
        val tileBorder = if (focused) {
            if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White
        } else {
            if (isLight) it.casatv.tv.ui.theme.CasaBorder else Color(0x33FFFFFF)
        }
        Box(
            modifier = Modifier
                .size(118.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(tileBg)
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = tileBorder,
                    shape = RoundedCornerShape(32.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaMuted,
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = strings.t("Aggiungi", "Add", "Añadir"),
            color = if (focused) (if (isLight) it.casatv.tv.ui.theme.CasaBlue else Color.White) else CasaMuted,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = strings.t("Nuovo profilo", "New profile", "Nuevo perfil"),
            color = CasaDim,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProfileEditor(
    profile: Profile?,
    avatars: List<String>,
    languages: List<ProfileLanguage>,
    countries: List<Facet>,
    canDelete: Boolean,
    busy: Boolean,
    onClose: () -> Unit,
    onSave: (name: String, avatar: String, language: String, country: String) -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var avatar by remember(profile?.id) {
        mutableStateOf(profile?.avatar ?: avatars.firstOrNull() ?: "🦊")
    }
    var language by remember(profile?.id) { mutableStateOf(profile?.language ?: "it") }
    var country by remember(profile?.id) {
        mutableStateOf(profile?.country ?: countries.firstOrNull()?.code ?: "IT")
    }
    var countryPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val avatarFocus = remember { FocusRequester() }

    val selectedCountry = countries.firstOrNull { it.code == country }
    val countryLabel = selectedCountry?.let { "${it.flag} ${it.name}" } ?: country

    LaunchedEffect(Unit) {
        delay(140)
        runCatching { avatarFocus.requestFocus() }
    }

    BackHandler(enabled = !busy) {
        if (countryPicker) countryPicker = false else onClose()
    }

    Box(Modifier.fillMaxSize().background(CasaBackground)) {
        BackgroundGlow()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 980.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 44.dp, vertical = 28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(CasaGreen),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = strings.t("PROFILI CASA TV", "CASA TV PROFILES"),
                        color = CasaGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    TvButton(
                        text = strings.t("Annulla", "Cancel"),
                        onClick = onClose,
                        primary = false,
                        enabled = !busy,
                    )
                    Spacer(Modifier.width(10.dp))
                    TvButton(
                        text = strings.t("Salva", "Save"),
                        onClick = { onSave(name.trim(), avatar, language, country) },
                        primary = true,
                        enabled = name.isNotBlank() && !busy,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (profile == null) {
                        strings.t("Nuovo profilo", "New profile")
                    } else {
                        strings.t("Modifica profilo", "Edit profile")
                    },
                    color = CasaText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.t(
                        "Il profilo decide lingua dell’app, canali consigliati e coda della modalità TV.",
                        "The profile sets the app language, recommended channels and TV mode queue.",
                    ),
                    color = CasaMuted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(22.dp))

                if (compact) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        EditorIdentity(
                            avatar = avatar,
                            name = name,
                            onNameChange = { name = it },
                            language = language,
                            languages = languages,
                            onLanguageChange = { language = it },
                            countryLabel = countryLabel,
                            onChangeCountry = { countryPicker = true },
                            deleteEnabled = false,
                            onDelete = { confirmDelete = true },
                        )
                        EditorAvatars(
                            avatars = avatars,
                            selected = avatar,
                            onSelect = { avatar = it },
                            columns = 10,
                            focusRequester = avatarFocus,
                        )
                        if (canDelete) {
                            DeleteProfileButton(
                                enabled = !busy,
                                onClick = { confirmDelete = true },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(34.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(380.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            EditorIdentity(
                                avatar = avatar,
                                name = name,
                                onNameChange = { name = it },
                                language = language,
                                languages = languages,
                                onLanguageChange = { language = it },
                                countryLabel = countryLabel,
                                onChangeCountry = { countryPicker = true },
                                deleteEnabled = canDelete && !busy,
                                onDelete = { confirmDelete = true },
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = strings.t("Scegli un avatar", "Choose an avatar"),
                                color = CasaDim,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            AvatarRows(
                                avatars = avatars,
                                selected = avatar,
                                onSelect = { avatar = it },
                                columns = 10,
                                focusRequester = avatarFocus,
                            )
                            Spacer(Modifier.height(26.dp))
                            Text(
                                text = strings.t("Anteprima", "Preview"),
                                color = CasaDim,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            ProfilePreview(
                                avatar = avatar,
                                name = name.ifBlank { strings.t("Nuovo profilo", "New profile") },
                                countryLabel = countryLabel,
                                languageName = languages.firstOrNull { it.code == language }?.name
                                    ?: language,
                            )
                        }
                    }
                }
            }
        }

        if (countryPicker) {
            CountryPickerOverlay(
                countries = countries,
                selected = country,
                title = strings.t("Paese del profilo", "Profile country"),
                subtitle = strings.t(
                    "I consigliati e la coda della modalità TV partiranno da questo paese.",
                    "Recommendations and the TV mode queue will start from this country.",
                ),
                onSelect = {
                    country = it.code
                    countryPicker = false
                },
                onClose = { countryPicker = false },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = CasaSurfaceHigh,
            titleContentColor = CasaText,
            textContentColor = CasaMuted,
            title = { Text(strings.t("Eliminare questo profilo?", "Delete this profile?")) },
            text = {
                Text(
                    strings.t(
                        "Preferiti, cronologia e impostazioni del profilo verranno rimossi dal server.",
                        "The profile's favorites, history and settings will be removed from the server.",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text(
                        strings.t("Elimina", "Delete"),
                        color = CasaRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(strings.t("Annulla", "Cancel"), color = CasaGreen)
                }
            },
        )
    }
}

@Composable
private fun EditorIdentity(
    avatar: String,
    name: String,
    onNameChange: (String) -> Unit,
    language: String,
    languages: List<ProfileLanguage>,
    onLanguageChange: (String) -> Unit,
    countryLabel: String,
    onChangeCountry: () -> Unit,
    deleteEnabled: Boolean,
    onDelete: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(CasaSurface)
                .border(1.dp, CasaBorder, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = avatar, fontSize = 44.sp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = strings.t("Nome del profilo", "Profile name"),
                color = CasaDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            )
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 28) onNameChange(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(strings.t("Es. Marco", "E.g. Mark"), color = CasaDim, fontSize = 13.sp)
                },
                textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                    color = CasaText,
                ),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CasaGreen,
                    unfocusedBorderColor = CasaBorder,
                    focusedContainerColor = CasaBackground.copy(alpha = 0.7f),
                    unfocusedContainerColor = CasaBackground.copy(alpha = 0.7f),
                    cursorColor = CasaGreen,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {}),
            )
        }
    }

    Spacer(Modifier.height(24.dp))
    Text(
        text = strings.t("Lingua dell’app", "App language"),
        color = CasaDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
    )
    Spacer(Modifier.height(9.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        languages.forEach { entry ->
            TvButton(
                text = entry.name,
                onClick = { onLanguageChange(entry.code) },
                primary = language == entry.code,
                modifier = Modifier.weight(1f),
            )
        }
    }

    Spacer(Modifier.height(24.dp))
    Text(
        text = strings.t("Paese preferito", "Preferred country"),
        color = CasaDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
    )
    Spacer(Modifier.height(9.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = countryLabel,
            color = CasaText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TvButton(
            text = strings.t("Cambia paese", "Change country"),
            onClick = onChangeCountry,
            primary = false,
        )
    }

    if (deleteEnabled) {
        Spacer(Modifier.height(30.dp))
        Box(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .background(CasaBorder.copy(alpha = 0.6f)),
        )
        Spacer(Modifier.height(18.dp))
        DeleteProfileButton(enabled = true, onClick = onDelete)
    }
}

@Composable
private fun DeleteProfileButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalAppStrings.current
    TvButton(
        text = strings.t("Elimina profilo", "Delete profile"),
        onClick = onClick,
        primary = false,
        enabled = enabled,
    )
}

@Composable
private fun EditorAvatars(
    avatars: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    columns: Int,
    focusRequester: FocusRequester,
) {
    AvatarRows(
        avatars = avatars,
        selected = selected,
        onSelect = onSelect,
        columns = columns,
        focusRequester = focusRequester,
    )
}

@Composable
private fun AvatarRows(
    avatars: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    columns: Int,
    focusRequester: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        avatars.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { index, emoji ->
                    AvatarCell(
                        emoji = emoji,
                        selected = emoji == selected,
                        onClick = { onSelect(emoji) },
                        modifier = if (rowIndex == 0 && index == 0) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarCell(
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(shape)
            .background(if (focused) CasaSurfaceHigh else CasaSurface.copy(alpha = 0.8f))
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = when {
                    focused -> CasaGreen
                    selected -> CasaGreen.copy(alpha = 0.7f)
                    else -> CasaBorder
                },
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 27.sp)
    }
}

@Composable
private fun ProfilePreview(
    avatar: String,
    name: String,
    countryLabel: String,
    languageName: String,
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .widthIn(max = 430.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CasaSurface.copy(alpha = 0.85f))
            .border(1.dp, CasaBorder, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = avatar, fontSize = 48.sp)
        Spacer(Modifier.width(18.dp))
        Column {
            Text(
                text = name,
                color = CasaText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$countryLabel · $languageName",
                color = CasaMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = strings.t(
                    "Consigliati, preferiti e cronologia personali",
                    "Personal recommendations, favorites and history",
                ),
                color = CasaDim,
                fontSize = 10.sp,
            )
        }
    }
}

/**
 * Full-screen country chooser. Navigation is done with the D-pad through a
 * lazy grid, which keeps the list cheap even with 200+ countries.
 */
@Composable
fun CountryPickerOverlay(
    countries: List<Facet>,
    selected: String,
    title: String,
    subtitle: String,
    onSelect: (Facet) -> Unit,
    onClose: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(countries, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            countries
        } else {
            countries.filter {
                it.name.lowercase().contains(needle) || it.code.lowercase().contains(needle)
            }
        }
    }
    val firstFocus = remember { FocusRequester() }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val selectedIndex = remember(filtered, selected) {
        filtered.indexOfFirst { it.code == selected }
    }
    val focusCode = filtered.getOrNull(selectedIndex)?.code ?: filtered.firstOrNull()?.code

    LaunchedEffect(Unit) {
        gridState.scrollToItem(selectedIndex.coerceAtLeast(0))
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onClose)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CasaBackground.copy(alpha = 0.98f)),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 46.dp, vertical = 30.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = CasaText,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = subtitle, color = CasaMuted, fontSize = 11.sp)
                }
                TvButton(
                    text = strings.t("Chiudi", "Close"),
                    onClick = onClose,
                    primary = false,
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        strings.t("Cerca un paese…", "Search for a country…"),
                        color = CasaDim,
                        fontSize = 13.sp,
                    )
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
            )
            Spacer(Modifier.height(16.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = strings.t("Nessun paese trovato", "No country found"),
                        color = CasaMuted,
                        fontSize = 13.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(190.dp),
                    modifier = Modifier.weight(1f),
                    state = gridState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = 20.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(filtered, key = { _, country -> country.code }) { index, country ->
                        CountryTile(
                            country = country,
                            selected = country.code == selected,
                            onClick = { onSelect(country) },
                            modifier = if (country.code == focusCode) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.t(
                    "Il paese si può cambiare anche durante la visione con il tasto INFO.",
                    "You can also change the country while watching with the INFO key.",
                ),
                color = CasaDim,
                fontSize = 10.sp,
            )
        }
    }
    }
}

@Composable
private fun CountryTile(
    country: Facet,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(if (focused) CasaSurfaceHigh else CasaSurface.copy(alpha = 0.8f))
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = when {
                    focused -> CasaGreen
                    selected -> CasaGreen.copy(alpha = 0.7f)
                    else -> CasaBorder
                },
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = country.flag, fontSize = 18.sp)
        Spacer(Modifier.width(9.dp))
        Text(
            text = country.name,
            color = CasaText,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = country.count.toString(),
            color = CasaDim,
            fontSize = 9.sp,
        )
    }
}
