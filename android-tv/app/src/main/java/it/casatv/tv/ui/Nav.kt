package it.casatv.tv.ui

enum class TvSubTab(
    val labelIt: String,
    val labelEn: String,
    val labelEs: String,
) {
    Channels("Canali", "Channels", "Canales"),
    Vod("Film & Serie", "Movies & Series", "Cine y Series"),
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
