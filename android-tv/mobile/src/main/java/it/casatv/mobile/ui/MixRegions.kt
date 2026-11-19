package it.casatv.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.casatv.mobile.data.MobileChannel
import java.text.NumberFormat
import java.util.EnumMap
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class MixRegion(
    val titleIt: String,
    val titleEn: String,
    val titleEs: String,
    val subtitleIt: String,
    val subtitleEn: String,
    val subtitleEs: String,
    val countryCodes: Set<String>,
) {
    ALL_WORLD(
        titleIt = "TUTTO IL MONDO",
        titleEn = "ALL THE WORLD",
        titleEs = "TODO EL MUNDO",
        subtitleIt = "Zapping globale senza confini",
        subtitleEn = "Global shuffle everywhere",
        subtitleEs = "Zapping global sin fronteras",
        countryCodes = emptySet(),
    ),
    EUROPE(
        titleIt = "EUROPA",
        titleEn = "EUROPE",
        titleEs = "EUROPA",
        subtitleIt = "Mediterranea, Occidentale e Nordica",
        subtitleEn = "Western, Nordic & South Europe",
        subtitleEs = "Occidental, Nórdica y Sur",
        countryCodes = setOf(
            "AL", "AD", "AT", "BE", "BA", "BG", "BY", "CH", "CY", "CZ", "DE", "DK",
            "EE", "ES", "FI", "FR", "GB", "UK", "GR", "HR", "HU", "IE", "IS", "IT",
            "LI", "LT", "LU", "LV", "MC", "MD", "ME", "MK", "MT", "NL", "NO", "PL",
            "PT", "RO", "RS", "SE", "SI", "SK", "SM", "UA", "VA", "XK", "FO", "GI",
        ),
    ),
    NORTH_AMERICA(
        titleIt = "NORD AMERICA",
        titleEn = "NORTH AMERICA",
        titleEs = "NORTEAMÉRICA",
        subtitleIt = "Stati Uniti, Canada e Atlantico",
        subtitleEn = "USA, Canada & North Atlantic",
        subtitleEs = "EE. UU., Canadá y Atlántico",
        countryCodes = setOf("US", "CA", "BM", "GL", "PM"),
    ),
    CENTRAL_AMERICA(
        titleIt = "CENTRO AMERICA",
        titleEn = "CENTRAL AMERICA",
        titleEs = "CENTROAMÉRICA",
        subtitleIt = "Messico, Istmo e Caraibi",
        subtitleEn = "Mexico, Isthmus & Caribbean",
        subtitleEs = "México, Istmo y Caribe",
        countryCodes = setOf(
            "MX", "BZ", "CR", "CU", "DO", "GT", "HN", "HT", "JM", "NI", "PA", "PR",
            "SV", "BS", "BB", "TT", "AG", "AW", "CW", "DM", "GD", "GP", "KN", "LC",
            "MQ", "SX", "VC", "VG", "VI", "KY", "TC", "BQ", "AI", "MS", "BL", "MF",
        ),
    ),
    SOUTH_AMERICA(
        titleIt = "SUD AMERICA",
        titleEn = "SOUTH AMERICA",
        titleEs = "SUDAMÉRICA",
        subtitleIt = "Regione Andina, Brasile e Sud",
        subtitleEn = "Andes, Brazil & Southern Cone",
        subtitleEs = "Andes, Brasil y Cono Sur",
        countryCodes = setOf(
            "AR", "BO", "BR", "CL", "CO", "EC", "FK", "GF", "GY", "PE", "PY", "SR",
            "UY", "VE",
        ),
    ),
    NORTH_AFRICA(
        titleIt = "NORD AFRICA",
        titleEn = "NORTH AFRICA",
        titleEs = "NORTE DE ÁFRICA",
        subtitleIt = "Maghreb, Egitto e Sud Med",
        subtitleEn = "Maghreb, Nile & South Med",
        subtitleEs = "Magreb, Egipto y Mediterráneo",
        countryCodes = setOf("DZ", "EG", "LY", "MA", "SD", "TN", "EH"),
    ),
    SOUTH_AFRICA(
        titleIt = "SUD AFRICA",
        titleEn = "SUB-SAHARAN AFRICA",
        titleEs = "ÁFRICA SUBSAHARIANA",
        subtitleIt = "Africa Australe e Subsahariana",
        subtitleEn = "Southern, East & West Africa",
        subtitleEs = "África Austral y Subsahariana",
        countryCodes = setOf(
            "AO", "BF", "BI", "BJ", "BW", "CD", "CF", "CG", "CI", "CM", "CV", "DJ",
            "ER", "ET", "GA", "GH", "GM", "GN", "GQ", "GW", "KE", "KM", "LR", "LS",
            "MG", "ML", "MR", "MU", "MW", "MZ", "NA", "NE", "NG", "RE", "RW", "SC",
            "SL", "SN", "SO", "SS", "ST", "SZ", "TD", "TG", "TZ", "UG", "YT", "ZA",
            "ZM", "ZW",
        ),
    ),
    MIDDLE_EAST(
        titleIt = "MEDIO ORIENTE",
        titleEn = "MIDDLE EAST",
        titleEs = "ORIENTE MEDIO",
        subtitleIt = "Levante, Arabia e Golfo",
        subtitleEn = "Levant, Arabia & Persian Gulf",
        subtitleEs = "Levante, Arabia y Golfo",
        countryCodes = setOf(
            "AE", "BH", "IQ", "IR", "IL", "JO", "KW", "LB", "OM", "PS", "QA", "SA",
            "SY", "YE",
        ),
    ),
    EURASIA(
        titleIt = "EURASIA & CAUCASO",
        titleEn = "EURASIA & CAUCASUS",
        titleEs = "EURASIA Y CÁUCASO",
        subtitleIt = "Turchia, Caucaso e Asia Centrale",
        subtitleEn = "Turkey, Caucasus & Central Asia",
        subtitleEs = "Turquía, Cáucaso y Asia Central",
        countryCodes = setOf("AM", "AZ", "GE", "KZ", "KG", "RU", "TJ", "TM", "TR", "UZ"),
    ),
    ASIA(
        titleIt = "ASIA",
        titleEn = "ASIA",
        titleEs = "ASIA",
        subtitleIt = "Estremo Oriente, India e Sud-Est",
        subtitleEn = "East Asia, India & Southeast Asia",
        subtitleEs = "Asia Oriental, India y Sudeste",
        countryCodes = setOf(
            "AF", "BD", "BN", "BT", "CN", "HK", "ID", "IN", "JP", "KH", "KP", "KR",
            "LA", "LK", "MM", "MN", "MO", "MV", "MY", "NP", "PH", "PK", "SG", "TH",
            "TL", "TW", "VN",
        ),
    ),
    AUSTRALIA(
        titleIt = "AUSTRALIA",
        titleEn = "AUSTRALIA",
        titleEs = "AUSTRALIA",
        subtitleIt = "Australia e Nuova Zelanda",
        subtitleEn = "Australia & New Zealand",
        subtitleEs = "Australia y Nueva Zelanda",
        countryCodes = setOf("AU", "NZ"),
    ),
    OCEANIA(
        titleIt = "OCEANIA",
        titleEn = "OCEANIA",
        titleEs = "OCEANÍA",
        subtitleIt = "Pacifico, Melanesia e Polinesia",
        subtitleEn = "Pacific Islands & Polynesia",
        subtitleEs = "Islas del Pacífico y Polinesia",
        countryCodes = setOf(
            "AS", "CK", "FJ", "FM", "GU", "KI", "MH", "MP", "NC", "NF", "NR", "NU",
            "PF", "PG", "PN", "PW", "SB", "TK", "TO", "TV", "VU", "WF", "WS",
        ),
    );

    fun label(language: String): String = when (language.lowercase().take(2)) {
        "en" -> titleEn
        "es" -> titleEs
        else -> titleIt
    }

    fun subtitle(language: String): String = when (language.lowercase().take(2)) {
        "en" -> subtitleEn
        "es" -> subtitleEs
        else -> subtitleIt
    }
}

private val MOBILE_COUNTRY_TO_REGION: Map<String, MixRegion> = buildMap {
    MixRegion.entries.forEach { region ->
        if (region != MixRegion.ALL_WORLD) {
            region.countryCodes.forEach { code -> put(code, region) }
        }
    }
}

private fun normalizeIsoCountry(raw: String?): String? {
    val clean = raw.orEmpty().trim().uppercase(Locale.ROOT)
    val mapped = if (clean == "UK") "GB" else clean
    return if (mapped.length == 2 && mapped.all { it in 'A'..'Z' }) mapped else null
}

fun resolveMixRegion(rawCode: String?): MixRegion? {
    val norm = normalizeIsoCountry(rawCode) ?: return null
    return MOBILE_COUNTRY_TO_REGION[norm]
}

fun matchesMixRegions(countryCode: String?, selected: Set<MixRegion>): Boolean {
    if (selected.isEmpty() || MixRegion.ALL_WORLD in selected) return true
    val region = resolveMixRegion(countryCode) ?: return false
    return region in selected
}

fun computeMixRegionCounts(channels: List<MobileChannel>): Map<MixRegion, Int> {
    val map = EnumMap<MixRegion, Int>(MixRegion::class.java)
    channels.forEach { ch ->
        val region = resolveMixRegion(ch.country) ?: return@forEach
        map[region] = (map[region] ?: 0) + 1
    }
    return map
}

private data class MobileWorldRegionGeo(
    val region: MixRegion,
    val anchor: Pair<Float, Float>,
    val polygons: List<List<Pair<Float, Float>>>,
)

private val MOBILE_WORLD_REGION_GEOS: List<MobileWorldRegionGeo> = listOf(
    MobileWorldRegionGeo(
        region = MixRegion.NORTH_AMERICA,
        anchor = 0.21f to 0.265f,
        polygons = listOf(
            listOf(
                0.030f to 0.142f, 0.068f to 0.105f, 0.118f to 0.098f, 0.155f to 0.118f,
                0.195f to 0.102f, 0.225f to 0.132f, 0.250f to 0.120f, 0.265f to 0.165f,
                0.242f to 0.195f, 0.275f to 0.175f, 0.305f to 0.155f, 0.335f to 0.195f,
                0.350f to 0.228f, 0.320f to 0.258f, 0.302f to 0.285f, 0.286f to 0.322f,
                0.276f to 0.365f, 0.280f to 0.405f, 0.264f to 0.398f, 0.252f to 0.362f,
                0.224f to 0.358f, 0.202f to 0.382f, 0.186f to 0.376f, 0.160f to 0.366f,
                0.142f to 0.336f, 0.125f to 0.285f, 0.118f to 0.232f, 0.095f to 0.195f,
                0.062f to 0.202f, 0.034f to 0.182f,
            ),
            listOf(
                0.315f to 0.068f, 0.355f to 0.042f, 0.405f to 0.036f, 0.436f to 0.058f,
                0.425f to 0.102f, 0.396f to 0.148f, 0.372f to 0.198f, 0.350f to 0.190f,
                0.338f to 0.142f, 0.312f to 0.105f,
            ),
            listOf(
                0.208f to 0.055f, 0.262f to 0.042f, 0.298f to 0.056f, 0.296f to 0.096f,
                0.262f to 0.105f, 0.228f to 0.088f, 0.198f to 0.082f,
            ),
        ),
    ),
    MobileWorldRegionGeo(
        region = MixRegion.CENTRAL_AMERICA,
        anchor = 0.22f to 0.46f,
        polygons = listOf(
            listOf(
                0.160f to 0.374f, 0.190f to 0.388f, 0.205f to 0.395f, 0.215f to 0.432f,
                0.242f to 0.432f, 0.248f to 0.452f, 0.236f to 0.470f, 0.252f to 0.495f,
                0.268f to 0.518f, 0.264f to 0.538f, 0.244f to 0.528f, 0.226f to 0.504f,
                0.204f to 0.482f, 0.182f to 0.456f, 0.166f to 0.422f, 0.152f to 0.395f,
            ),
            listOf(
                0.244f to 0.426f, 0.272f to 0.424f, 0.302f to 0.438f,
                0.305f to 0.452f, 0.282f to 0.454f, 0.252f to 0.442f,
            ),
            listOf(
                0.306f to 0.454f, 0.332f to 0.456f, 0.348f to 0.466f,
                0.344f to 0.482f, 0.320f to 0.482f, 0.304f to 0.470f,
            ),
        ),
    ),
    MobileWorldRegionGeo(
        region = MixRegion.SOUTH_AMERICA,
        anchor = 0.325f to 0.675f,
        polygons = listOf(
            listOf(
                0.266f to 0.538f, 0.286f to 0.516f, 0.314f to 0.514f, 0.342f to 0.534f,
                0.376f to 0.562f, 0.408f to 0.606f, 0.414f to 0.648f, 0.396f to 0.692f,
                0.378f to 0.738f, 0.354f to 0.782f, 0.336f to 0.828f, 0.328f to 0.876f,
                0.314f to 0.916f, 0.292f to 0.912f, 0.284f to 0.862f, 0.282f to 0.802f,
                0.276f to 0.735f, 0.266f to 0.675f, 0.250f to 0.625f, 0.252f to 0.576f,
            ),
        ),
    ),
    MobileWorldRegionGeo(
        region = MixRegion.EUROPE,
        anchor = 0.515f to 0.275f,
        polygons = listOf(
            listOf(
                0.436f to 0.334f, 0.442f to 0.308f, 0.464f to 0.296f, 0.470f to 0.274f,
                0.494f to 0.258f, 0.506f to 0.234f, 0.532f to 0.236f, 0.558f to 0.220f,
                0.582f to 0.214f, 0.596f to 0.252f, 0.592f to 0.296f, 0.574f to 0.328f,
                0.556f to 0.348f, 0.548f to 0.378f, 0.532f to 0.374f, 0.526f to 0.344f,
                0.518f to 0.372f, 0.502f to 0.378f, 0.494f to 0.348f, 0.484f to 0.326f,
                0.468f to 0.342f, 0.456f to 0.362f, 0.436f to 0.358f,
            ),
            listOf(
                0.492f to 0.208f, 0.496f to 0.168f, 0.516f to 0.128f, 0.548f to 0.102f,
                0.580f to 0.114f, 0.584f to 0.158f, 0.576f to 0.202f, 0.552f to 0.214f,
                0.534f to 0.194f, 0.518f to 0.222f, 0.498f to 0.226f,
            ),
            listOf(
                0.450f to 0.242f, 0.458f to 0.205f, 0.468f to 0.178f, 0.478f to 0.196f,
                0.476f to 0.234f, 0.484f to 0.258f, 0.466f to 0.270f, 0.448f to 0.266f,
            ),
            listOf(
                0.428f to 0.228f, 0.438f to 0.214f, 0.446f to 0.226f,
                0.444f to 0.254f, 0.432f to 0.260f, 0.426f to 0.246f,
            ),
            listOf(
                0.414f to 0.136f, 0.432f to 0.122f, 0.452f to 0.126f,
                0.454f to 0.146f, 0.434f to 0.152f, 0.416f to 0.146f,
            ),
        ),
    ),
    MobileWorldRegionGeo(
        region = MixRegion.NORTH_AFRICA,
        anchor = 0.502f to 0.448f,
        polygons = listOf(
            listOf(
                0.438f to 0.390f, 0.468f to 0.376f, 0.498f to 0.380f, 0.518f to 0.402f,
                0.548f to 0.408f, 0.572f to 0.414f, 0.582f to 0.456f, 0.586f to 0.508f,
                0.545f to 0.516f, 0.500f to 0.518f, 0.455f to 0.512f, 0.426f to 0.496f,
                0.420f to 0.452f, 0.428f to 0.414f,
            ),
        ),
    ),
    MobileWorldRegionGeo(
        region = MixRegion.SOUTH_AFRICA,
        anchor = 0.538f to 0.648f,
        polygons = listOf(
            listOf(
                0.416f to 0.520f, 0.456f to 0.516f, 0.508f to 0.520f, 0.562f to 0.518f,
                0.592f to 0.524f, 0.618f to 0.548f, 0.640f to 0.542f, 0.626f to 0.595f,
                0.602f to 0.652f, 0.588f to 0.714f, 0.572f to 0.768f, 0.544f to 0.812f,
                0.520f to 0.806f, 0.504f to 0.752f, 0.494f to 0.690f, 0.486f to 0.628f,
                0.454f to 0.598f, 0.418f to 0.574f, 0.410f to 0.544f,
            ),
            listOf(
                0.604f to 0.742f, 0.616f to 0.696f, 0.630f to 0.674f,
                0.636f to 0.696f, 0.628f to 0.742f, 0.618f to 0.774f, 0.604f to 0.768f,
            ),
        ),
    ),
    MobileWorldRegionGeo(
        region = MixRegion.MIDDLE_EAST,
        anchor = 0.622f to 0.440f,
        polygons = listOf(
            listOf(
                0.576f to 0.366f, 0.608f to 0.360f, 0.642f to 0.366f, 0.668f to 0.392f,
                0.670f to 0.438f, 0.656f to 0.462f, 0.664f to 0.494f, 0.634f to 0.522f,
                0.606f to 0.532f, 0.592f to 0.492f, 0.580f to 0.448f, 0.572f to 0.405f,
            ),
        ),
    ),
    MobileWorldRegionGeo(
        region = MixRegion.EURASIA,
        anchor = 0.728f to 0.205f,
        polygons = listOf(
            listOf(
                0.556f to 0.330f, 0.574f to 0.310f, 0.602f to 0.312f, 0.618f to 0.334f,
                0.608f to 0.356f, 0.582f to 0.358f, 0.558f to 0.352f,
            ),
            listOf(
                0.584f to 0.192f, 0.596f to 0.140f, 0.640f to 0.116f, 0.700f to 0.104f,
                0.775f to 0.088f, 0.848f to 0.105f, 0.916f to 0.130f, 0.952f to 0.162f,
                0.920f to 0.198f, 0.896f to 0.246f, 0.872f to 0.228f, 0.842f to 0.256f,
                0.790f to 0.275f, 0.735f to 0.296f, 0.688f to 0.344f, 0.648f to 0.356f,
                0.616f to 0.332f, 0.600f to 0.278f, 0.590f to 0.236f,
            ),
        ),
    ),
    MobileWorldRegionGeo(
        region = MixRegion.ASIA,
        anchor = 0.768f to 0.420f,
        polygons = listOf(
            listOf(
                0.666f to 0.365f, 0.702f to 0.330f, 0.752f to 0.295f, 0.808f to 0.268f,
                0.848f to 0.278f, 0.862f to 0.318f, 0.848f to 0.362f, 0.852f to 0.402f,
                0.836f to 0.448f, 0.802f to 0.486f, 0.804f to 0.542f, 0.782f to 0.562f,
                0.764f to 0.520f, 0.748f to 0.482f, 0.722f to 0.506f, 0.712f to 0.548f,
                0.694f to 0.512f, 0.680f to 0.456f, 0.666f to 0.418f,
            ),
            listOf(
                0.878f to 0.285f, 0.900f to 0.282f, 0.908f to 0.312f, 0.898f to 0.355f,
                0.880f to 0.395f, 0.866f to 0.390f, 0.876f to 0.348f, 0.872f to 0.314f,
            ),
            listOf(
                0.742f to 0.570f, 0.766f to 0.578f, 0.794f to 0.608f, 0.818f to 0.625f,
                0.806f to 0.640f, 0.770f to 0.625f, 0.738f to 0.592f,
            ),
            listOf(
                0.816f to 0.566f, 0.842f to 0.556f, 0.862f to 0.576f,
                0.858f to 0.614f, 0.834f to 0.618f, 0.814f to 0.596f,
            ),
            listOf(
                0.864f to 0.474f, 0.882f to 0.470f, 0.892f to 0.502f,
                0.888f to 0.538f, 0.870f to 0.538f, 0.862f to 0.504f,
            ),
        ),
    ),
    MobileWorldRegionGeo(
        region = MixRegion.AUSTRALIA,
        anchor = 0.838f to 0.735f,
        polygons = listOf(
            listOf(
                0.766f to 0.715f, 0.792f to 0.678f, 0.822f to 0.654f, 0.846f to 0.668f,
                0.866f to 0.642f, 0.890f to 0.676f, 0.904f to 0.728f, 0.890f to 0.786f,
                0.862f to 0.822f, 0.832f to 0.796f, 0.798f to 0.794f, 0.768f to 0.802f,
                0.760f to 0.758f,
            ),
            listOf(
                0.928f to 0.802f, 0.946f to 0.774f, 0.962f to 0.788f, 0.954f to 0.836f,
                0.934f to 0.876f, 0.916f to 0.868f, 0.924f to 0.834f,
            ),
        ),
    ),
    MobileWorldRegionGeo(
        region = MixRegion.OCEANIA,
        anchor = 0.925f to 0.612f,
        polygons = listOf(
            listOf(
                0.864f to 0.592f, 0.892f to 0.596f, 0.918f to 0.616f,
                0.916f to 0.642f, 0.892f to 0.640f, 0.864f to 0.620f,
            ),
            listOf(
                0.926f to 0.562f, 0.948f to 0.556f, 0.964f to 0.576f,
                0.958f to 0.606f, 0.934f to 0.608f, 0.922f to 0.584f,
            ),
            listOf(
                0.950f to 0.632f, 0.972f to 0.628f, 0.988f to 0.650f,
                0.982f to 0.680f, 0.958f to 0.684f, 0.946f to 0.658f,
            ),
        ),
    ),
)

private val MOBILE_GEO_BY_REGION = MOBILE_WORLD_REGION_GEOS.associateBy { it.region }

private data class MobileMapViewport(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun toScreen(nx: Float, ny: Float): Offset = Offset(left + nx * width, top + ny * height)
    fun toNormalized(x: Float, y: Float): Pair<Float, Float> =
        ((x - left) / width) to ((y - top) / height)
}

private fun computeMobileMapViewport(totalW: Float, totalH: Float): MobileMapViewport {
    if (totalW <= 1f || totalH <= 1f) return MobileMapViewport(0f, 0f, 1f, 1f)
    val padX = totalW * 0.02f
    val padY = totalH * 0.03f
    val availW = (totalW - padX * 2f).coerceAtLeast(1f)
    val availH = (totalH - padY * 2f).coerceAtLeast(1f)
    val targetRatio = 1.94f
    return if (availW / availH > targetRatio) {
        val w = availH * targetRatio
        MobileMapViewport(left = (totalW - w) / 2f, top = padY, width = w, height = availH)
    } else {
        val h = availW / targetRatio
        MobileMapViewport(left = padX, top = (totalH - h) / 2f, width = availW, height = h)
    }
}

private fun isMobilePointInPolygon(nx: Float, ny: Float, poly: List<Pair<Float, Float>>): Boolean {
    var inside = false
    var j = poly.lastIndex
    for (i in poly.indices) {
        val (xi, yi) = poly[i]
        val (xj, yj) = poly[j]
        val intersects = ((yi > ny) != (yj > ny)) &&
            (nx < (xj - xi) * (ny - yi) / ((yj - yi).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1e-6f) + xi)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}

private fun hitTestMobileWorldRegion(nx: Float, ny: Float): MixRegion? {
    if (nx !in -0.02f..1.02f || ny !in -0.02f..1.02f) return null
    MOBILE_WORLD_REGION_GEOS.forEach { geo ->
        if (geo.polygons.any { isMobilePointInPolygon(nx, ny, it) }) {
            return geo.region
        }
    }
    var bestRegion: MixRegion? = null
    var bestDist = 0.09f
    MOBILE_WORLD_REGION_GEOS.forEach { geo ->
        val dAnchor = hypot((nx - geo.anchor.first) * 1.94f, ny - geo.anchor.second)
        val dMinPoly = geo.polygons.minOf { poly ->
            poly.minOf { (px, py) -> hypot((nx - px) * 1.94f, ny - py) }
        }
        val d = minOf(dAnchor, dMinPoly)
        if (d < bestDist) {
            bestDist = d
            bestRegion = geo.region
        }
    }
    return bestRegion
}

private fun buildMobileSmoothViewportPath(points: List<Pair<Float, Float>>, vp: MobileMapViewport): Path {
    return Path().apply {
        if (points.size < 3) {
            if (points.isEmpty()) return@apply
            val first = vp.toScreen(points[0].first, points[0].second)
            moveTo(first.x, first.y)
            for (i in 1 until points.size) {
                val p = vp.toScreen(points[i].first, points[i].second)
                lineTo(p.x, p.y)
            }
            close()
            return@apply
        }
        val screenPts = points.map { vp.toScreen(it.first, it.second) }
        val last = screenPts.last()
        val first = screenPts.first()
        moveTo((last.x + first.x) * 0.5f, (last.y + first.y) * 0.5f)
        for (i in screenPts.indices) {
            val curr = screenPts[i]
            val next = screenPts[(i + 1) % screenPts.size]
            val midX = (curr.x + next.x) * 0.5f
            val midY = (curr.y + next.y) * 0.5f
            quadraticTo(curr.x, curr.y, midX, midY)
        }
        close()
    }
}

private fun formatMobileCount(n: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(n)

@Composable
fun MixRegionPickerDialog(
    language: String,
    selectedRegions: Set<MixRegion>,
    regionCounts: Map<MixRegion, Int>,
    totalChannels: Int,
    onToggleRegion: (MixRegion) -> Unit,
    onStartMix: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun tr(itStr: String, enStr: String, esStr: String): String = when (language.lowercase().take(2)) {
        "en" -> enStr
        "es" -> esStr
        else -> itStr
    }

    val allWorldSelected = MixRegion.ALL_WORLD in selectedRegions
    var inspectedRegion by remember {
        mutableStateOf(
            selectedRegions.firstOrNull { it != MixRegion.ALL_WORLD } ?: MixRegion.EUROPE,
        )
    }
    val cfg = LocalConfiguration.current
    val isLandscape = cfg.screenWidthDp > cfg.screenHeightDp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0A1220), Color(0xFF050A13))))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = tr("MIX · MAPPAMONDO", "MIX · WORLD MAP", "MIX · MAPAMUNDI"),
                        color = Color(0xFFF8FAFC),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val activeNames = if (allWorldSelected) {
                        "${MixRegion.ALL_WORLD.label(language)} · ${formatMobileCount(totalChannels)} " +
                            tr("canali", "channels", "canales")
                    } else {
                        selectedRegions.joinToString(" · ") { it.label(language) }
                    }
                    Text(
                        text = tr(
                            "Tocca le regioni sul mappamondo · $activeNames",
                            "Tap regions on the map · $activeNames",
                            "Toca las regiones · $activeNames",
                        ),
                        color = Color(0xFF7DD3FC),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = tr("Chiudi", "Close", "Cerrar"),
                        tint = Color(0xFFF8FAFC),
                    )
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MobileWorldMapSurface(
                        language = language,
                        selectedRegions = selectedRegions,
                        allWorldSelected = allWorldSelected,
                        inspectedRegion = inspectedRegion,
                        onSelectRegion = { region ->
                            inspectedRegion = region
                            onToggleRegion(region)
                        },
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        MobileRegionGridChips(
                            language = language,
                            selectedRegions = selectedRegions,
                            allWorldSelected = allWorldSelected,
                            regionCounts = regionCounts,
                            inspectedRegion = inspectedRegion,
                            onSelectRegion = { region ->
                                inspectedRegion = region
                                onToggleRegion(region)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.height(8.dp))
                        MobileMixBottomButtons(
                            language = language,
                            allWorldSelected = allWorldSelected,
                            onAllWorld = { onToggleRegion(MixRegion.ALL_WORLD) },
                            onStartMix = onStartMix,
                        )
                    }
                }
            } else {
                MobileWorldMapSurface(
                    language = language,
                    selectedRegions = selectedRegions,
                    allWorldSelected = allWorldSelected,
                    inspectedRegion = inspectedRegion,
                    onSelectRegion = { region ->
                        inspectedRegion = region
                        onToggleRegion(region)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .padding(horizontal = 14.dp),
                )
                Spacer(Modifier.height(10.dp))
                MobileRegionGridChips(
                    language = language,
                    selectedRegions = selectedRegions,
                    allWorldSelected = allWorldSelected,
                    regionCounts = regionCounts,
                    inspectedRegion = inspectedRegion,
                    onSelectRegion = { region ->
                        inspectedRegion = region
                        onToggleRegion(region)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                MobileMixBottomButtons(
                    language = language,
                    allWorldSelected = allWorldSelected,
                    onAllWorld = { onToggleRegion(MixRegion.ALL_WORLD) },
                    onStartMix = onStartMix,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF08101C))
                        .border(1.dp, Color(0xFF1C2C42), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun MobileWorldMapSurface(
    language: String,
    selectedRegions: Set<MixRegion>,
    allWorldSelected: Boolean,
    inspectedRegion: MixRegion,
    onSelectRegion: (MixRegion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var calloutSize by remember { mutableStateOf(IntSize.Zero) }
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.radialGradient(listOf(Color(0xFF0A203A), Color(0xFF050A13))))
            .border(1.dp, Color(0xFF1D3B5E), shape)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val vp = computeMobileMapViewport(size.width.toFloat(), size.height.toFloat())
                    val (nx, ny) = vp.toNormalized(offset.x, offset.y)
                    val hit = hitTestMobileWorldRegion(nx, ny)
                    if (hit != null) onSelectRegion(hit)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val vp = computeMobileMapViewport(size.width, size.height)
            clipRect(left = vp.left, top = vp.top, right = vp.left + vp.width, bottom = vp.top + vp.height) {
                val gridDash = PathEffect.dashPathEffect(floatArrayOf(5f, 7f), 0f)
                listOf(0.25f, 0.50f, 0.75f).forEach { fy ->
                    val y = vp.top + vp.height * fy
                    drawLine(
                        color = if (fy == 0.50f) Color(0x3338BDF8) else Color(0x221E3A5F),
                        start = Offset(vp.left, y),
                        end = Offset(vp.left + vp.width, y),
                        strokeWidth = 1f,
                        pathEffect = gridDash,
                    )
                }
                listOf(0.25f, 0.50f, 0.75f).forEach { fx ->
                    val x = vp.left + vp.width * fx
                    drawLine(
                        color = Color(0x221E3A5F),
                        start = Offset(x, vp.top),
                        end = Offset(x, vp.top + vp.height),
                        strokeWidth = 1f,
                        pathEffect = gridDash,
                    )
                }
            }

            MOBILE_WORLD_REGION_GEOS.forEach { geo ->
                val isDirect = geo.region in selectedRegions
                val isActive = allWorldSelected || isDirect
                val isInspected = geo.region == inspectedRegion

                val brush = when {
                    isDirect -> Brush.linearGradient(
                        listOf(Color(0xFF00D4FF), Color(0xFF0284C7)),
                        start = Offset(vp.left, vp.top),
                        end = Offset(vp.left + vp.width, vp.top + vp.height),
                    )
                    allWorldSelected -> Brush.linearGradient(
                        listOf(Color(0xBF0EA5E9), Color(0xBF0369A1)),
                        start = Offset(vp.left, vp.top),
                        end = Offset(vp.left + vp.width, vp.top + vp.height),
                    )
                    else -> Brush.linearGradient(
                        listOf(Color(0xFF132740), Color(0xFF0D1B2E)),
                        start = Offset(vp.left, vp.top),
                        end = Offset(vp.left + vp.width, vp.top + vp.height),
                    )
                }

                geo.polygons.forEach { pts ->
                    val path = buildMobileSmoothViewportPath(pts, vp)
                    if (isActive || isInspected) {
                        drawPath(
                            path = path,
                            color = if (isInspected) Color(0x66FFFFFF) else Color(0x4500D4FF),
                            style = Stroke(width = if (isInspected) 8f else 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                    drawPath(path = path, brush = brush)
                    drawPath(
                        path = path,
                        color = when {
                            isInspected -> Color.White
                            isActive -> Color(0xFFE0F7FF)
                            else -> Color(0xFF284870)
                        },
                        style = Stroke(
                            width = if (isInspected) 2.2f else if (isActive) 1.5f else 1f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }

            MOBILE_WORLD_REGION_GEOS.forEach { geo ->
                val isActive = allWorldSelected || geo.region in selectedRegions
                val isInspected = geo.region == inspectedRegion
                val c = vp.toScreen(geo.anchor.first, geo.anchor.second)
                if (isInspected) {
                    drawCircle(color = Color(0x4500D4FF), radius = 11f, center = c)
                    drawCircle(color = Color.White, radius = 4f, center = c)
                } else {
                    drawCircle(
                        color = if (isActive) Color(0xFFE0F7FF) else Color(0x8038BDF8),
                        radius = 2.2f,
                        center = c,
                    )
                }
            }
        }

        // Compact name-only floating callout pill
        val activeGeo = MOBILE_GEO_BY_REGION[inspectedRegion]
        if (activeGeo != null && canvasSize.width > 0 && canvasSize.height > 0) {
            val vp = computeMobileMapViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat())
            val anchorPx = vp.toScreen(activeGeo.anchor.first, activeGeo.anchor.second)
            val active = allWorldSelected || inspectedRegion in selectedRegions

            val preferBelow = activeGeo.anchor.second < 0.30f
            val cw = calloutSize.width.takeIf { it > 0 } ?: 110
            val ch = calloutSize.height.takeIf { it > 0 } ?: 24
            val marginPx = 8
            val rawX = (anchorPx.x - cw / 2f).roundToInt()
            val rawY = if (preferBelow) {
                (anchorPx.y + 10).roundToInt()
            } else {
                (anchorPx.y - ch - 10).roundToInt()
            }
            val clampedX = rawX.coerceIn(marginPx, (canvasSize.width - cw - marginPx).coerceAtLeast(marginPx))
            val clampedY = rawY.coerceIn(marginPx, (canvasSize.height - ch - marginPx).coerceAtLeast(marginPx))

            val pillShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .offset { IntOffset(clampedX, clampedY) }
                    .onSizeChanged { calloutSize = it }
                    .clip(pillShape)
                    .background(Color(0xE606101E))
                    .border(1.dp, if (active) Color(0xFF00D4FF) else Color(0xFF7DD3FC), pillShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = inspectedRegion.label(language),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MobileRegionGridChips(
    language: String,
    selectedRegions: Set<MixRegion>,
    allWorldSelected: Boolean,
    regionCounts: Map<MixRegion, Int>,
    inspectedRegion: MixRegion,
    onSelectRegion: (MixRegion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val regions = remember { MixRegion.entries.filter { it != MixRegion.ALL_WORLD } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(regions, key = { it.name }) { region ->
            val isDirect = region in selectedRegions
            val isSelected = allWorldSelected || isDirect
            val isInspected = region == inspectedRegion
            val count = regionCounts[region] ?: 0
            val chipShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(chipShape)
                    .background(
                        when {
                            isDirect -> Color(0xFF0B2742)
                            allWorldSelected -> Color(0xFF081E35)
                            else -> Color(0xFF070E1A)
                        },
                    )
                    .border(
                        width = if (isDirect) 1.5.dp else 1.dp,
                        color = when {
                            isDirect -> Color(0xFF00D4FF)
                            isInspected -> Color(0xFF7DD3FC)
                            allWorldSelected -> Color(0x8038BDF8)
                            else -> Color(0xFF1C2C42)
                        },
                        shape = chipShape,
                    )
                    .clickable { onSelectRegion(region) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFF00D4FF) else Color(0xFF132238))
                        .border(1.dp, if (isSelected) Color.White else Color(0xFF284266), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF04101A),
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = region.label(language),
                        color = if (isSelected) Color(0xFFBAE6FD) else Color(0xFFF8FAFC),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (count > 0) "${formatMobileCount(count)} ch · ${region.subtitle(language)}" else region.subtitle(language),
                        color = Color(0xFF94A3B8),
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileMixBottomButtons(
    language: String,
    allWorldSelected: Boolean,
    onAllWorld: () -> Unit,
    onStartMix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun tr(itStr: String, enStr: String, esStr: String): String = when (language.lowercase().take(2)) {
        "en" -> enStr
        "es" -> esStr
        else -> itStr
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onAllWorld,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (allWorldSelected) Color(0xFF0E2B49) else Color.Transparent,
                contentColor = if (allWorldSelected) Color(0xFF7DD3FC) else Color(0xFFF8FAFC),
            ),
        ) {
            Text(tr("Tutto il mondo", "All World", "Todo"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = onStartMix,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0284C7),
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(tr("Avvia Mix", "Start Mix", "Iniciar Mix"), fontWeight = FontWeight.Bold)
        }
    }
}
