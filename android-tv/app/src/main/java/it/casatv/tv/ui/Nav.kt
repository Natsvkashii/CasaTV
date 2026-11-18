package it.casatv.tv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(
    val labelIt: String,
    val labelEn: String,
    val labelEs: String,
    val icon: ImageVector,
) {
    Home("Home", "Home", "Inicio", Icons.Filled.Home),
    Tv("Diretta TV", "Live TV", "TV en vivo", Icons.Filled.PlayArrow),
    Settings("Impostazioni", "Settings", "Ajustes", Icons.Filled.Settings);

    fun label(language: String): String {
        val code = language.lowercase().take(2)
        return when (code) {
            "en" -> labelEn
            "es" -> labelEs
            else -> labelIt
        }
    }
}

enum class TvSubTab(
    val labelIt: String,
    val labelEn: String,
    val labelEs: String,
) {
    Channels("Canali", "Channels", "Canales"),
    Mix("Mix", "Mix", "Mix"),
    Favorites("Preferiti", "Favorites", "Favoritos");

    fun label(language: String): String {
        val code = language.lowercase().take(2)
        return when (code) {
            "en" -> labelEn
            "es" -> labelEs
            else -> labelIt
        }
    }
}
