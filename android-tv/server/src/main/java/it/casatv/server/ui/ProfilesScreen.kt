package it.casatv.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.casatv.server.core.COUNTRY_CODES
import it.casatv.server.core.LocalStrings
import it.casatv.server.core.PROFILE_AVATARS
import it.casatv.server.core.PROFILE_LANGUAGES
import it.casatv.server.core.ProfileRow
import it.casatv.server.core.countryFlag
import it.casatv.server.core.countryName
import it.casatv.server.core.languageLabel
import it.casatv.server.ui.theme.CasaBorder
import it.casatv.server.ui.theme.CasaDanger
import it.casatv.server.ui.theme.CasaDim
import it.casatv.server.ui.theme.CasaGreen
import it.casatv.server.ui.theme.CasaMuted
import it.casatv.server.ui.theme.CasaPanelHigh
import it.casatv.server.ui.theme.CasaText

@Composable
fun ProfilesScreen(vm: ServerViewModel) {
    val strings = LocalStrings.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProfileRow?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProfileRow?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                strings.t(
                    "I profili sono salvati sul server e valgono per tutte le TV.",
                    "Profiles are stored on the server and shared by every TV.",
                    "Los perfiles se guardan en el servidor y valen para todas las TV.",
                ),
                color = CasaMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            CasaButton(
                text = strings.t("+ Nuovo", "+ New", "+ Nuevo"),
                onClick = { creating = true },
                primary = true,
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 26.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServerProfileAvatarBadge(
                            avatar = profile.avatar,
                            name = profile.name,
                            size = 44.dp,
                            isGuest = profile.id == "guest",
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, color = CasaText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("${languageLabel(profile.language)} ·", color = CasaMuted, fontSize = 12.sp)
                                ServerCountryFlagIcon(countryCode = profile.country, width = 18.dp, height = 12.dp)
                                Text(countryName(profile.country, strings.language), color = CasaMuted, fontSize = 12.sp)
                            }
                            Text(
                                "${strings.t("ID", "ID", "ID")}: ${profile.id}",
                                color = CasaDim,
                                fontSize = 10.sp,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CasaButton(
                                strings.t("Modifica", "Edit", "Editar"),
                                { editing = profile },
                            )
                            CasaButton(
                                strings.t("Elimina", "Delete", "Eliminar"),
                                { deleting = profile },
                                danger = true,
                                enabled = profiles.size > 1,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        ProfileDialog(
            existing = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { name, avatar, language, country ->
                val current = editing
                if (current == null) {
                    vm.createProfile(name, avatar, language, country)
                } else {
                    vm.updateProfile(current.id, name, avatar, language, country)
                }
                creating = false
                editing = null
            },
        )
    }

    val pending = deleting
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = CasaPanelHigh,
            title = { Text(strings.t("Eliminare il profilo?", "Delete profile?", "¿Eliminar el perfil?"), color = CasaText) },
            text = {
                Text(
                    strings.t(
                        "Le TV collegate a questo profilo dovranno sceglierne un altro.",
                        "TVs using this profile will have to pick another one.",
                        "Las TV con este perfil tendrán que elegir otro.",
                    ),
                    color = CasaMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProfile(pending.id)
                    deleting = null
                }) { Text(strings.t("Elimina", "Delete", "Eliminar"), color = CasaDanger) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(strings.t("Annulla", "Cancel", "Cancelar"), color = CasaMuted)
                }
            },
        )
    }
}

@Composable
private fun ProfileDialog(
    existing: ProfileRow?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var avatar by remember { mutableStateOf(existing?.avatar ?: PROFILE_AVATARS.first()) }
    var language by remember { mutableStateOf(existing?.language ?: strings.language) }
    var country by remember { mutableStateOf(existing?.country ?: "IT") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CasaPanelHigh,
        title = {
            Text(
                if (existing == null) {
                    strings.t("Nuovo profilo", "New profile", "Nuevo perfil")
                } else {
                    strings.t("Modifica profilo", "Edit profile", "Editar perfil")
                },
                color = CasaText,
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxHeight(0.8f)
                    .verticalScroll(rememberScrollState()),
            ) {
                CasaTextField(name, { name = it.take(28) }, strings.t("Nome", "Name", "Nombre"))
                Spacer(Modifier.height(12.dp))
                Text(strings.t("Avatar", "Avatar", "Avatar"), color = CasaMuted, fontSize = 12.sp)
                LazyRow(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(PROFILE_AVATARS) { option ->
                        Box(
                            Modifier
                                .background(
                                    if (option == avatar) CasaGreen.copy(alpha = 0.22f) else CasaPanelHigh,
                                    RoundedCornerShape(10.dp),
                                )
                                .border(
                                    width = if (option == avatar) 1.5.dp else 1.dp,
                                    color = if (option == avatar) CasaGreen else CasaBorder,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { avatar = option }
                                .padding(6.dp),
                        ) {
                            ServerProfileAvatarBadge(avatar = option, name = name.ifBlank { "A" }, size = 30.dp)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(strings.t("Lingua", "Language", "Idioma"), color = CasaMuted, fontSize = 12.sp)
                Row(
                    Modifier
                        .padding(vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PROFILE_LANGUAGES.forEach { option ->
                        Chip(
                            text = option.name,
                            selected = language == option.code,
                            onClick = { language = option.code },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(strings.t("Paese", "Country", "País"), color = CasaMuted, fontSize = 12.sp)
                LazyRow(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(COUNTRY_CODES) { code ->
                        Row(
                            modifier = Modifier
                                .background(
                                    if (country == code) CasaGreen.copy(alpha = 0.16f) else CasaPanelHigh,
                                    RoundedCornerShape(10.dp),
                                )
                                .border(
                                    1.dp,
                                    if (country == code) CasaGreen else CasaBorder,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { country = code }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ServerCountryFlagIcon(countryCode = code, width = 18.dp, height = 12.dp)
                            Text(
                                text = code,
                                color = if (country == code) CasaGreen else CasaMuted,
                                fontSize = 13.sp,
                                fontWeight = if (country == code) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.ifBlank { "Profilo" },
                        avatar,
                        language,
                        country,
                    )
                },
            ) { Text(strings.t("Salva", "Save", "Guardar"), color = CasaGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.t("Annulla", "Cancel", "Cancelar"), color = CasaMuted)
            }
        },
    )
}
