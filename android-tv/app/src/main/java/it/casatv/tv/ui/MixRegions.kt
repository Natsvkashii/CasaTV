package it.casatv.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import java.text.NumberFormat
import java.util.EnumMap
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Sociological & geographic macro-regions for Mix mode.
 * Organized so users can explore either a single cultural/linguistic sphere
 * or combine multiple continents at once.
 */
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
        subtitleEn = "Global shuffle across every region",
        subtitleEs = "Zapping global sin fronteras",
        countryCodes = emptySet(),
    ),
    EUROPE(
        titleIt = "EUROPA",
        titleEn = "EUROPE",
        titleEs = "EUROPA",
        subtitleIt = "Mediterranea, Occidentale e Nordica",
        subtitleEn = "Western, Nordic & Mediterranean Europe",
        subtitleEs = "Europa Occidental, Nórdica y Mediterránea",
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
        subtitleIt = "Stati Uniti, Canada e Nord Atlantico",
        subtitleEn = "United States, Canada & North Atlantic",
        subtitleEs = "Estados Unidos, Canadá y Atlántico Norte",
        countryCodes = setOf("US", "CA", "BM", "GL", "PM"),
    ),
    CENTRAL_AMERICA(
        titleIt = "CENTRO AMERICA",
        titleEn = "CENTRAL AMERICA",
        titleEs = "CENTROAMÉRICA",
        subtitleIt = "Messico, Istmo Centrale e Caraibi",
        subtitleEn = "Mexico, Central Isthmus & Caribbean",
        subtitleEs = "México, Istmo Central y Caribe",
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
        subtitleIt = "Regione Andina, Brasile e Cono Sud",
        subtitleEn = "Andean Region, Brazil & Southern Cone",
        subtitleEs = "Región Andina, Brasil y Cono Sur",
        countryCodes = setOf(
            "AR", "BO", "BR", "CL", "CO", "EC", "FK", "GF", "GY", "PE", "PY", "SR",
            "UY", "VE",
        ),
    ),
    NORTH_AFRICA(
        titleIt = "NORD AFRICA",
        titleEn = "NORTH AFRICA",
        titleEs = "NORTE DE ÁFRICA",
        subtitleIt = "Maghreb, Egitto e Sud Mediterraneo",
        subtitleEn = "Maghreb, Nile Valley & South Med",
        subtitleEs = "Magreb, Egipto y Sur del Mediterráneo",
        countryCodes = setOf("DZ", "EG", "LY", "MA", "SD", "TN", "EH"),
    ),
    SOUTH_AFRICA(
        titleIt = "SUD AFRICA",
        titleEn = "SOUTHERN & SUB-SAHARAN",
        titleEs = "ÁFRICA AUSTRAL Y SUBSAHARIANA",
        subtitleIt = "Africa Australe, Orientale e Subsahariana",
        subtitleEn = "Southern, East & Sub-Saharan Africa",
        subtitleEs = "África Austral, Oriental y Subsahariana",
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
        subtitleIt = "Levante, Penisola Arabica e Golfo",
        subtitleEn = "Levant, Arabian Peninsula & Gulf",
        subtitleEs = "Levante, Península Arábiga y Golfo",
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
        subtitleEs = "Asia Oriental, India y Sudeste Asiático",
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
        subtitleEn = "Pacific Islands, Melanesia & Polynesia",
        subtitleEs = "Islas del Pacífico, Melanesia y Polinesia",
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

private val COUNTRY_TO_REGION: Map<String, MixRegion> = buildMap {
    MixRegion.entries.forEach { region ->
        if (region != MixRegion.ALL_WORLD) {
            region.countryCodes.forEach { code -> put(code, region) }
        }
    }
}

fun resolveMixRegion(rawCode: String?): MixRegion? {
    val raw = rawCode.orEmpty().trim().uppercase(Locale.ROOT)
    val mapped = if (raw == "UK") "GB" else raw
    val norm = normalizeCountryCode(mapped) ?: return null
    return COUNTRY_TO_REGION[norm] ?: COUNTRY_TO_REGION[mapped]
}

fun matchesMixRegions(countryCode: String?, selected: Set<MixRegion>): Boolean {
    if (selected.isEmpty() || MixRegion.ALL_WORLD in selected) return true
    val region = resolveMixRegion(countryCode) ?: return false
    return region in selected
}

fun computeMixRegionCounts(countries: List<it.casatv.tv.data.Facet>): Map<MixRegion, Int> {
    val result = EnumMap<MixRegion, Int>(MixRegion::class.java)
    countries.forEach { facet ->
        val region = resolveMixRegion(facet.code) ?: return@forEach
        result[region] = (result[region] ?: 0) + facet.count
    }
    return result
}

private data class WorldRegionGeo(
    val region: MixRegion,
    val anchor: Pair<Float, Float>,
    val polygons: List<List<Pair<Float, Float>>>,
    val left: MixRegion? = null,
    val right: MixRegion? = null,
    val up: MixRegion? = null,
    val down: MixRegion? = null,
)

private val WORLD_REGION_GEOS: List<WorldRegionGeo> = listOf(
    WorldRegionGeo(
        region = MixRegion.NORTH_AMERICA,
        anchor = 0.21f to 0.265f,
        right = MixRegion.EUROPE,
        down = MixRegion.CENTRAL_AMERICA,
        polygons = listOf(
            // Alaska, Canada, contiguous US, Florida
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
            // Greenland
            listOf(
                0.315f to 0.068f, 0.355f to 0.042f, 0.405f to 0.036f, 0.436f to 0.058f,
                0.425f to 0.102f, 0.396f to 0.148f, 0.372f to 0.198f, 0.350f to 0.190f,
                0.338f to 0.142f, 0.312f to 0.105f,
            ),
            // Arctic Archipelago
            listOf(
                0.208f to 0.055f, 0.262f to 0.042f, 0.298f to 0.056f, 0.296f to 0.096f,
                0.262f to 0.105f, 0.228f to 0.088f, 0.198f to 0.082f,
            ),
        ),
    ),
    WorldRegionGeo(
        region = MixRegion.CENTRAL_AMERICA,
        anchor = 0.22f to 0.46f,
        left = MixRegion.NORTH_AMERICA,
        right = MixRegion.NORTH_AFRICA,
        up = MixRegion.NORTH_AMERICA,
        down = MixRegion.SOUTH_AMERICA,
        polygons = listOf(
            // Mexico, Yucatan & Central Isthmus
            listOf(
                0.160f to 0.374f, 0.190f to 0.388f, 0.205f to 0.395f, 0.215f to 0.432f,
                0.242f to 0.432f, 0.248f to 0.452f, 0.236f to 0.470f, 0.252f to 0.495f,
                0.268f to 0.518f, 0.264f to 0.538f, 0.244f to 0.528f, 0.226f to 0.504f,
                0.204f to 0.482f, 0.182f to 0.456f, 0.166f to 0.422f, 0.152f to 0.395f,
            ),
            // Cuba
            listOf(
                0.244f to 0.426f, 0.272f to 0.424f, 0.302f to 0.438f,
                0.305f to 0.452f, 0.282f to 0.454f, 0.252f to 0.442f,
            ),
            // Hispaniola & Caribbean
            listOf(
                0.306f to 0.454f, 0.332f to 0.456f, 0.348f to 0.466f,
                0.344f to 0.482f, 0.320f to 0.482f, 0.304f to 0.470f,
            ),
        ),
    ),
    WorldRegionGeo(
        region = MixRegion.SOUTH_AMERICA,
        anchor = 0.325f to 0.675f,
        right = MixRegion.SOUTH_AFRICA,
        up = MixRegion.CENTRAL_AMERICA,
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
    WorldRegionGeo(
        region = MixRegion.EUROPE,
        anchor = 0.515f to 0.275f,
        left = MixRegion.NORTH_AMERICA,
        right = MixRegion.EURASIA,
        down = MixRegion.NORTH_AFRICA,
        polygons = listOf(
            // Continental Europe, Iberia, Italy, Balkans
            listOf(
                0.436f to 0.334f, 0.442f to 0.308f, 0.464f to 0.296f, 0.470f to 0.274f,
                0.494f to 0.258f, 0.506f to 0.234f, 0.532f to 0.236f, 0.558f to 0.220f,
                0.582f to 0.214f, 0.596f to 0.252f, 0.592f to 0.296f, 0.574f to 0.328f,
                0.556f to 0.348f, 0.548f to 0.378f, 0.532f to 0.374f, 0.526f to 0.344f,
                0.518f to 0.372f, 0.502f to 0.378f, 0.494f to 0.348f, 0.484f to 0.326f,
                0.468f to 0.342f, 0.456f to 0.362f, 0.436f to 0.358f,
            ),
            // Scandinavia
            listOf(
                0.492f to 0.208f, 0.496f to 0.168f, 0.516f to 0.128f, 0.548f to 0.102f,
                0.580f to 0.114f, 0.584f to 0.158f, 0.576f to 0.202f, 0.552f to 0.214f,
                0.534f to 0.194f, 0.518f to 0.222f, 0.498f to 0.226f,
            ),
            // Great Britain
            listOf(
                0.450f to 0.242f, 0.458f to 0.205f, 0.468f to 0.178f, 0.478f to 0.196f,
                0.476f to 0.234f, 0.484f to 0.258f, 0.466f to 0.270f, 0.448f to 0.266f,
            ),
            // Ireland
            listOf(
                0.428f to 0.228f, 0.438f to 0.214f, 0.446f to 0.226f,
                0.444f to 0.254f, 0.432f to 0.260f, 0.426f to 0.246f,
            ),
            // Iceland
            listOf(
                0.414f to 0.136f, 0.432f to 0.122f, 0.452f to 0.126f,
                0.454f to 0.146f, 0.434f to 0.152f, 0.416f to 0.146f,
            ),
        ),
    ),
    WorldRegionGeo(
        region = MixRegion.NORTH_AFRICA,
        anchor = 0.502f to 0.448f,
        left = MixRegion.CENTRAL_AMERICA,
        right = MixRegion.MIDDLE_EAST,
        up = MixRegion.EUROPE,
        down = MixRegion.SOUTH_AFRICA,
        polygons = listOf(
            listOf(
                0.438f to 0.390f, 0.468f to 0.376f, 0.498f to 0.380f, 0.518f to 0.402f,
                0.548f to 0.408f, 0.572f to 0.414f, 0.582f to 0.456f, 0.586f to 0.508f,
                0.545f to 0.516f, 0.500f to 0.518f, 0.455f to 0.512f, 0.426f to 0.496f,
                0.420f to 0.452f, 0.428f to 0.414f,
            ),
        ),
    ),
    WorldRegionGeo(
        region = MixRegion.SOUTH_AFRICA,
        anchor = 0.538f to 0.648f,
        left = MixRegion.SOUTH_AMERICA,
        right = MixRegion.AUSTRALIA,
        up = MixRegion.NORTH_AFRICA,
        polygons = listOf(
            // Sub-Saharan, Horn & Southern Africa
            listOf(
                0.416f to 0.520f, 0.456f to 0.516f, 0.508f to 0.520f, 0.562f to 0.518f,
                0.592f to 0.524f, 0.618f to 0.548f, 0.640f to 0.542f, 0.626f to 0.595f,
                0.602f to 0.652f, 0.588f to 0.714f, 0.572f to 0.768f, 0.544f to 0.812f,
                0.520f to 0.806f, 0.504f to 0.752f, 0.494f to 0.690f, 0.486f to 0.628f,
                0.454f to 0.598f, 0.418f to 0.574f, 0.410f to 0.544f,
            ),
            // Madagascar
            listOf(
                0.604f to 0.742f, 0.616f to 0.696f, 0.630f to 0.674f,
                0.636f to 0.696f, 0.628f to 0.742f, 0.618f to 0.774f, 0.604f to 0.768f,
            ),
        ),
    ),
    WorldRegionGeo(
        region = MixRegion.MIDDLE_EAST,
        anchor = 0.622f to 0.440f,
        left = MixRegion.NORTH_AFRICA,
        right = MixRegion.ASIA,
        up = MixRegion.EURASIA,
        down = MixRegion.SOUTH_AFRICA,
        polygons = listOf(
            listOf(
                0.576f to 0.366f, 0.608f to 0.360f, 0.642f to 0.366f, 0.668f to 0.392f,
                0.670f to 0.438f, 0.656f to 0.462f, 0.664f to 0.494f, 0.634f to 0.522f,
                0.606f to 0.532f, 0.592f to 0.492f, 0.580f to 0.448f, 0.572f to 0.405f,
            ),
        ),
    ),
    WorldRegionGeo(
        region = MixRegion.EURASIA,
        anchor = 0.728f to 0.205f,
        left = MixRegion.EUROPE,
        down = MixRegion.ASIA,
        polygons = listOf(
            // Turkey & Caucasus
            listOf(
                0.556f to 0.330f, 0.574f to 0.310f, 0.602f to 0.312f, 0.618f to 0.334f,
                0.608f to 0.356f, 0.582f to 0.358f, 0.558f to 0.352f,
            ),
            // Russia, Kazakhstan, Central Asia & Siberia
            listOf(
                0.584f to 0.192f, 0.596f to 0.140f, 0.640f to 0.116f, 0.700f to 0.104f,
                0.775f to 0.088f, 0.848f to 0.105f, 0.916f to 0.130f, 0.952f to 0.162f,
                0.920f to 0.198f, 0.896f to 0.246f, 0.872f to 0.228f, 0.842f to 0.256f,
                0.790f to 0.275f, 0.735f to 0.296f, 0.688f to 0.344f, 0.648f to 0.356f,
                0.616f to 0.332f, 0.600f to 0.278f, 0.590f to 0.236f,
            ),
        ),
    ),
    WorldRegionGeo(
        region = MixRegion.ASIA,
        anchor = 0.768f to 0.420f,
        left = MixRegion.MIDDLE_EAST,
        right = MixRegion.OCEANIA,
        up = MixRegion.EURASIA,
        down = MixRegion.AUSTRALIA,
        polygons = listOf(
            // India, China, Mongolia, Korea, Indochina
            listOf(
                0.666f to 0.365f, 0.702f to 0.330f, 0.752f to 0.295f, 0.808f to 0.268f,
                0.848f to 0.278f, 0.862f to 0.318f, 0.848f to 0.362f, 0.852f to 0.402f,
                0.836f to 0.448f, 0.802f to 0.486f, 0.804f to 0.542f, 0.782f to 0.562f,
                0.764f to 0.520f, 0.748f to 0.482f, 0.722f to 0.506f, 0.712f to 0.548f,
                0.694f to 0.512f, 0.680f to 0.456f, 0.666f to 0.418f,
            ),
            // Japan
            listOf(
                0.878f to 0.285f, 0.900f to 0.282f, 0.908f to 0.312f, 0.898f to 0.355f,
                0.880f to 0.395f, 0.866f to 0.390f, 0.876f to 0.348f, 0.872f to 0.314f,
            ),
            // Sumatra & Java
            listOf(
                0.742f to 0.570f, 0.766f to 0.578f, 0.794f to 0.608f, 0.818f to 0.625f,
                0.806f to 0.640f, 0.770f to 0.625f, 0.738f to 0.592f,
            ),
            // Borneo & Sulawesi
            listOf(
                0.816f to 0.566f, 0.842f to 0.556f, 0.862f to 0.576f,
                0.858f to 0.614f, 0.834f to 0.618f, 0.814f to 0.596f,
            ),
            // Philippines
            listOf(
                0.864f to 0.474f, 0.882f to 0.470f, 0.892f to 0.502f,
                0.888f to 0.538f, 0.870f to 0.538f, 0.862f to 0.504f,
            ),
        ),
    ),
    WorldRegionGeo(
        region = MixRegion.AUSTRALIA,
        anchor = 0.838f to 0.735f,
        left = MixRegion.SOUTH_AFRICA,
        right = MixRegion.OCEANIA,
        up = MixRegion.ASIA,
        polygons = listOf(
            // Australia mainland
            listOf(
                0.766f to 0.715f, 0.792f to 0.678f, 0.822f to 0.654f, 0.846f to 0.668f,
                0.866f to 0.642f, 0.890f to 0.676f, 0.904f to 0.728f, 0.890f to 0.786f,
                0.862f to 0.822f, 0.832f to 0.796f, 0.798f to 0.794f, 0.768f to 0.802f,
                0.760f to 0.758f,
            ),
            // New Zealand
            listOf(
                0.928f to 0.802f, 0.946f to 0.774f, 0.962f to 0.788f, 0.954f to 0.836f,
                0.934f to 0.876f, 0.916f to 0.868f, 0.924f to 0.834f,
            ),
        ),
    ),
    WorldRegionGeo(
        region = MixRegion.OCEANIA,
        anchor = 0.925f to 0.612f,
        left = MixRegion.AUSTRALIA,
        up = MixRegion.ASIA,
        polygons = listOf(
            // Papua New Guinea
            listOf(
                0.864f to 0.592f, 0.892f to 0.596f, 0.918f to 0.616f,
                0.916f to 0.642f, 0.892f to 0.640f, 0.864f to 0.620f,
            ),
            // Melanesia / Micronesia cluster
            listOf(
                0.926f to 0.562f, 0.948f to 0.556f, 0.964f to 0.576f,
                0.958f to 0.606f, 0.934f to 0.608f, 0.922f to 0.584f,
            ),
            // Polynesia cluster
            listOf(
                0.950f to 0.632f, 0.972f to 0.628f, 0.988f to 0.650f,
                0.982f to 0.680f, 0.958f to 0.684f, 0.946f to 0.658f,
            ),
        ),
    ),
)

private val GEO_BY_REGION: Map<MixRegion, WorldRegionGeo> = WORLD_REGION_GEOS.associateBy { it.region }

private data class MapViewport(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun toScreen(nx: Float, ny: Float): Offset = Offset(left + nx * width, top + ny * height)
    fun toNormalized(x: Float, y: Float): Pair<Float, Float> =
        ((x - left) / width) to ((y - top) / height)
}

private fun computeMapViewport(totalW: Float, totalH: Float): MapViewport {
    if (totalW <= 1f || totalH <= 1f) return MapViewport(0f, 0f, 1f, 1f)
    val padX = totalW * 0.02f
    val padY = totalH * 0.03f
    val availW = (totalW - padX * 2f).coerceAtLeast(1f)
    val availH = (totalH - padY * 2f).coerceAtLeast(1f)
    val targetRatio = 1.94f
    return if (availW / availH > targetRatio) {
        val w = availH * targetRatio
        MapViewport(left = (totalW - w) / 2f, top = padY, width = w, height = availH)
    } else {
        val h = availW / targetRatio
        MapViewport(left = padX, top = (totalH - h) / 2f, width = availW, height = h)
    }
}

private fun isPointInPolygon(nx: Float, ny: Float, poly: List<Pair<Float, Float>>): Boolean {
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

private fun hitTestWorldRegion(nx: Float, ny: Float): MixRegion? {
    if (nx !in -0.02f..1.02f || ny !in -0.02f..1.02f) return null
    WORLD_REGION_GEOS.forEach { geo ->
        if (geo.polygons.any { isPointInPolygon(nx, ny, it) }) {
            return geo.region
        }
    }
    var bestRegion: MixRegion? = null
    var bestDist = 0.075f
    WORLD_REGION_GEOS.forEach { geo ->
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

private fun buildSmoothViewportPath(points: List<Pair<Float, Float>>, vp: MapViewport): Path {
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

private fun formatCount(n: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(n)

@Composable
fun MixRegionPickerDialog(
    selectedRegions: Set<MixRegion>,
    regionCounts: Map<MixRegion, Int>,
    totalChannels: Int,
    onToggleRegion: (MixRegion) -> Unit,
    onStartMix: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val cfg = LocalConfiguration.current
    val scale = remember(cfg.screenWidthDp, cfg.screenHeightDp) {
        minOf(cfg.screenWidthDp / 1280f, cfg.screenHeightDp / 720f).coerceIn(0.78f, 1.30f)
    }

    val allWorldSelected = MixRegion.ALL_WORLD in selectedRegions
    var focusedRegion by remember {
        mutableStateOf(
            selectedRegions.firstOrNull { it != MixRegion.ALL_WORLD } ?: MixRegion.EUROPE,
        )
    }
    var mapHasFocus by remember { mutableStateOf(true) }

    val mapFocus = remember { FocusRequester() }
    val allWorldFocus = remember { FocusRequester() }
    val startMixFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(90)
        runCatching { mapFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEB040811))
                .padding(horizontal = (24 * scale).dp, vertical = (14 * scale).dp),
        ) {
            val outerShape = RoundedCornerShape((22 * scale).dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(outerShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A1220), Color(0xFF060B14)),
                        ),
                    )
                    .border(1.5.dp, Color(0xFF1E3A5F), outerShape)
                    .padding(horizontal = (20 * scale).dp, vertical = (12 * scale).dp),
            ) {
                // Header with Title, Active Summary & Top Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = strings.t(
                                "MIX · MAPPAMONDO",
                                "MIX · WORLD MAP",
                                "MIX · MAPAMUNDI",
                            ),
                            color = Color(0xFFF8FAFC),
                            fontSize = (18 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp,
                        )
                        Spacer(Modifier.height((2 * scale).dp))
                        val activeSummary = if (allWorldSelected) {
                            "${MixRegion.ALL_WORLD.label(strings.language)} · ${formatCount(totalChannels)} " +
                                strings.t("canali", "channels", "canales")
                        } else {
                            selectedRegions.joinToString(" · ") { it.label(strings.language) }
                        }
                        Text(
                            text = activeSummary,
                            color = Color(0xFF7DD3FC),
                            fontSize = (11.5f * scale).sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width((12 * scale).dp))

                    val downToMapModifier = Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                        ) {
                            runCatching { mapFocus.requestFocus() }
                            true
                        } else {
                            false
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                        MixTopActionButton(
                            text = strings.t("🌍 Tutto il mondo", "🌍 All World", "🌍 Todo el mundo"),
                            selected = allWorldSelected,
                            accent = false,
                            scale = scale,
                            onClick = { onToggleRegion(MixRegion.ALL_WORLD) },
                            modifier = Modifier
                                .focusRequester(allWorldFocus)
                                .then(downToMapModifier),
                        )
                        MixTopActionButton(
                            text = strings.t("▶ Avvia Mix", "▶ Start Mix", "▶ Iniciar Mix"),
                            selected = true,
                            accent = true,
                            scale = scale,
                            onClick = onStartMix,
                            modifier = Modifier
                                .focusRequester(startMixFocus)
                                .then(downToMapModifier),
                        )
                        MixTopActionButton(
                            text = strings.t("Chiudi", "Close", "Cerrar"),
                            selected = false,
                            accent = false,
                            scale = scale,
                            onClick = onDismiss,
                            modifier = Modifier
                                .focusRequester(closeFocus)
                                .then(downToMapModifier),
                        )
                    }
                }

                Spacer(Modifier.height((10 * scale).dp))

                // Interactive World Map Canvas Container
                var canvasSize by remember { mutableStateOf(IntSize.Zero) }
                var calloutSize by remember { mutableStateOf(IntSize.Zero) }
                val mapShape = RoundedCornerShape((16 * scale).dp)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(mapShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF0A203A), Color(0xFF050A13)),
                            ),
                        )
                        .border(
                            width = if (mapHasFocus) 2.dp else 1.dp,
                            color = if (mapHasFocus) Color(0xFF38BDF8) else Color(0xFF1A314F),
                            shape = mapShape,
                        )
                        .onSizeChanged { canvasSize = it }
                        .focusRequester(mapFocus)
                        .onFocusChanged { mapHasFocus = it.isFocused }
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            val code = event.nativeKeyEvent.keyCode
                            val isDirectional = code == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
                                code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT ||
                                code == AndroidKeyEvent.KEYCODE_DPAD_UP ||
                                code == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                            val isSelect = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                                code == AndroidKeyEvent.KEYCODE_ENTER ||
                                code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                            val isPlay = code == AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                                code == AndroidKeyEvent.KEYCODE_MEDIA_PLAY

                            if (event.type == KeyEventType.KeyUp) {
                                return@onPreviewKeyEvent isDirectional || isSelect || isPlay
                            }
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                            val currentGeo = GEO_BY_REGION[focusedRegion]
                            when (code) {
                                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                                AndroidKeyEvent.KEYCODE_ENTER,
                                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    onToggleRegion(focusedRegion)
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> {
                                    onStartMix()
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                                    currentGeo?.left?.let { focusedRegion = it }
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    currentGeo?.right?.let { focusedRegion = it }
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                    currentGeo?.down?.let { focusedRegion = it }
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                    val nextUp = currentGeo?.up
                                    if (nextUp != null) {
                                        focusedRegion = nextUp
                                    } else {
                                        val topTarget = if (focusedRegion == MixRegion.NORTH_AMERICA) {
                                            allWorldFocus
                                        } else {
                                            startMixFocus
                                        }
                                        runCatching { topTarget.requestFocus() }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val vp = computeMapViewport(size.width.toFloat(), size.height.toFloat())
                                val (nx, ny) = vp.toNormalized(offset.x, offset.y)
                                val hit = hitTestWorldRegion(nx, ny)
                                if (hit != null) {
                                    focusedRegion = hit
                                    runCatching { mapFocus.requestFocus() }
                                    onToggleRegion(hit)
                                }
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val vp = computeMapViewport(size.width, size.height)

                        // Subtle cartographic graticule
                        clipRect(left = vp.left, top = vp.top, right = vp.left + vp.width, bottom = vp.top + vp.height) {
                            val gridDash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
                            listOf(0.25f, 0.50f, 0.75f).forEach { fy ->
                                val y = vp.top + vp.height * fy
                                drawLine(
                                    color = if (fy == 0.50f) Color(0x3338BDF8) else Color(0x221E3A5F),
                                    start = Offset(vp.left, y),
                                    end = Offset(vp.left + vp.width, y),
                                    strokeWidth = if (fy == 0.50f) 1.4f else 1f,
                                    pathEffect = gridDash,
                                )
                            }
                            listOf(0.20f, 0.40f, 0.50f, 0.60f, 0.80f).forEach { fx ->
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

                        // Pass 1: Smooth curved continent silhouettes
                        WORLD_REGION_GEOS.forEach { geo ->
                            val isDirectlySelected = geo.region in selectedRegions
                            val isActive = allWorldSelected || isDirectlySelected
                            val isFocused = mapHasFocus && focusedRegion == geo.region

                            val fillBrush = when {
                                isDirectlySelected -> Brush.linearGradient(
                                    listOf(Color(0xFF00D4FF), Color(0xFF0284C7)),
                                    start = Offset(vp.left, vp.top),
                                    end = Offset(vp.left + vp.width, vp.top + vp.height),
                                )
                                allWorldSelected -> Brush.linearGradient(
                                    listOf(Color(0xBF0EA5E9), Color(0xBF0369A1)),
                                    start = Offset(vp.left, vp.top),
                                    end = Offset(vp.left + vp.width, vp.top + vp.height),
                                )
                                isFocused -> Brush.linearGradient(
                                    listOf(Color(0xFF1F4B7A), Color(0xFF133356)),
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
                                val path = buildSmoothViewportPath(pts, vp)
                                if (isActive || isFocused) {
                                    drawPath(
                                        path = path,
                                        color = if (isFocused) Color(0x66FFFFFF) else Color(0x5000D4FF),
                                        style = Stroke(width = if (isFocused) 9f else 6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                    )
                                }
                                drawPath(path = path, brush = fillBrush)
                                drawPath(
                                    path = path,
                                    color = when {
                                        isFocused -> Color.White
                                        isActive -> Color(0xFFE0F7FF)
                                        else -> Color(0xFF284870)
                                    },
                                    style = Stroke(
                                        width = if (isFocused) 2.4f else if (isActive) 1.6f else 1.1f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                    ),
                                )
                            }
                        }

                        // Pass 2: Region Anchor Pins
                        WORLD_REGION_GEOS.forEach { geo ->
                            val isActive = allWorldSelected || geo.region in selectedRegions
                            val isFocused = mapHasFocus && focusedRegion == geo.region
                            val center = vp.toScreen(geo.anchor.first, geo.anchor.second)
                            if (isFocused) {
                                drawCircle(color = Color(0x4500D4FF), radius = 12f, center = center)
                                drawCircle(color = Color.White, radius = 4.5f, center = center)
                            } else if (isActive) {
                                drawCircle(color = Color(0xFFE0F7FF), radius = 3.2f, center = center)
                            } else {
                                drawCircle(color = Color(0x8038BDF8), radius = 2.2f, center = center)
                            }
                        }
                    }

                    // Compact name-only floating pill near focused region on map
                    val activeGeo = GEO_BY_REGION[focusedRegion]
                    if (activeGeo != null && canvasSize.width > 0 && canvasSize.height > 0) {
                        val vp = computeMapViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                        val anchorPx = vp.toScreen(activeGeo.anchor.first, activeGeo.anchor.second)
                        val regionSelected = allWorldSelected || focusedRegion in selectedRegions

                        val preferBelow = activeGeo.anchor.second < 0.28f
                        val cw = calloutSize.width.takeIf { it > 0 } ?: 130
                        val ch = calloutSize.height.takeIf { it > 0 } ?: 28
                        val marginPx = 12
                        val rawX = (anchorPx.x - cw / 2f).roundToInt()
                        val rawY = if (preferBelow) {
                            (anchorPx.y + 14).roundToInt()
                        } else {
                            (anchorPx.y - ch - 14).roundToInt()
                        }
                        val clampedX = rawX.coerceIn(marginPx, (canvasSize.width - cw - marginPx).coerceAtLeast(marginPx))
                        val clampedY = rawY.coerceIn(marginPx, (canvasSize.height - ch - marginPx).coerceAtLeast(marginPx))

                        val badgeShape = RoundedCornerShape(50)
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(clampedX, clampedY) }
                                .onSizeChanged { calloutSize = it }
                                .clip(badgeShape)
                                .background(Color(0xE606101E))
                                .border(
                                    width = 1.dp,
                                    color = if (regionSelected) Color(0xFF00D4FF) else Color(0xFF7DD3FC),
                                    shape = badgeShape,
                                )
                                .padding(horizontal = (9 * scale).dp, vertical = (3.5f * scale).dp),
                        ) {
                            Text(
                                text = focusedRegion.label(strings.language),
                                color = Color.White,
                                fontSize = (10.5f * scale).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }

                    // Bottom-left remote hint
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding((8 * scale).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xB8040914))
                            .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(8.dp))
                            .padding(horizontal = (9 * scale).dp, vertical = (3.5f * scale).dp),
                    ) {
                        Text(
                            text = strings.t(
                                "Frecce: esplora · OK: seleziona · ▲ Barra in alto",
                                "Arrows: explore · OK: select · ▲ Top bar",
                                "Flechas: explorar · OK: elegir · ▲ Barra superior",
                            ),
                            color = Color(0xFF94A3B8),
                            fontSize = (9 * scale).sp,
                        )
                    }
                }

                Spacer(Modifier.height((8 * scale).dp))

                // Detailed focus info bar + bottom region list (shows channel counts & descriptions)
                val focusedCount = regionCounts[focusedRegion] ?: 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = (2 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${focusedRegion.label(strings.language)} · ${focusedRegion.subtitle(strings.language)}",
                        color = Color(0xFFCBD5E1),
                        fontSize = (10.5f * scale).sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (focusedCount > 0) {
                        Spacer(Modifier.width((10 * scale).dp))
                        Text(
                            text = "${formatCount(focusedCount)} " + strings.t("canali", "channels", "canales"),
                            color = Color(0xFF7DD3FC),
                            fontSize = (10.5f * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(Modifier.height((6 * scale).dp))

                // Bottom Interactive Legend List of All 11 Regions with counts
                val stripRow1 = listOf(
                    MixRegion.NORTH_AMERICA,
                    MixRegion.CENTRAL_AMERICA,
                    MixRegion.SOUTH_AMERICA,
                    MixRegion.EUROPE,
                    MixRegion.EURASIA,
                    MixRegion.ASIA,
                )
                val stripRow2 = listOf(
                    MixRegion.NORTH_AFRICA,
                    MixRegion.SOUTH_AFRICA,
                    MixRegion.MIDDLE_EAST,
                    MixRegion.AUSTRALIA,
                    MixRegion.OCEANIA,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy((6 * scale).dp),
                ) {
                    listOf(stripRow1, stripRow2).forEach { rowRegions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy((6 * scale).dp),
                        ) {
                            rowRegions.forEach { region ->
                                val isSelected = allWorldSelected || region in selectedRegions
                                val isCursor = mapHasFocus && focusedRegion == region
                                val count = regionCounts[region] ?: 0
                                val chipShape = RoundedCornerShape((8 * scale).dp)
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(chipShape)
                                        .background(
                                            when {
                                                region in selectedRegions -> Color(0xFF0C3459)
                                                allWorldSelected -> Color(0xFF092542)
                                                isCursor -> Color(0xFF11253E)
                                                else -> Color(0xFF091221)
                                            },
                                        )
                                        .border(
                                            width = if (isCursor) 1.5.dp else 1.dp,
                                            color = when {
                                                isCursor -> Color.White
                                                region in selectedRegions -> Color(0xFF00D4FF)
                                                allWorldSelected -> Color(0x8038BDF8)
                                                else -> Color(0xFF1C304A)
                                            },
                                            shape = chipShape,
                                        )
                                        .clickable {
                                            focusedRegion = region
                                            runCatching { mapFocus.requestFocus() }
                                            onToggleRegion(region)
                                        }
                                        .padding(horizontal = (8 * scale).dp, vertical = (5 * scale).dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size((6 * scale).dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color(0xFF00D4FF) else Color(0xFF334155),
                                            ),
                                    )
                                    Spacer(Modifier.width((5 * scale).dp))
                                    Text(
                                        text = if (count > 0) "${region.label(strings.language)} · ${formatCount(count)}" else region.label(strings.language),
                                        color = when {
                                            isCursor -> Color.White
                                            isSelected -> Color(0xFFBAE6FD)
                                            else -> Color(0xFF94A3B8)
                                        },
                                        fontSize = (9.5f * scale).sp,
                                        fontWeight = if (isSelected || isCursor) FontWeight.Bold else FontWeight.Medium,
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
    }
}

@Composable
private fun MixTopActionButton(
    text: String,
    selected: Boolean,
    accent: Boolean,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape((10 * scale).dp)
    val bg = when {
        focused -> Color(0xFF00D4FF)
        accent -> Color(0xFF0284C7)
        selected -> Color(0xFF133A63)
        else -> Color(0xFF0F1B2E)
    }
    val fg = if (focused) Color(0xFF04101A) else Color(0xFFF8FAFC)
    val border = when {
        focused -> Color.White
        accent || selected -> Color(0xFF38BDF8)
        else -> Color(0xFF284266)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, border, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = (14 * scale).dp, vertical = (9 * scale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = (12 * scale).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
