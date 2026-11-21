package it.casatv.tv.ui

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import org.json.JSONArray
import org.json.JSONObject

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

private data class WorldPolyBbox(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val points: List<Pair<Float, Float>>,
)

private data class WorldRegionGeo(
    val region: MixRegion,
    val anchor: Pair<Float, Float>,
    val polygons: List<WorldPolyBbox>,
    val left: MixRegion? = null,
    val right: MixRegion? = null,
    val up: MixRegion? = null,
    val down: MixRegion? = null,
)

private data class WorldMapData(
    val globeOutline: List<Pair<Float, Float>>,
    val equatorLine: List<Pair<Float, Float>>,
    val parallels: List<List<Pair<Float, Float>>>,
    val meridians: List<List<Pair<Float, Float>>>,
    val regions: List<WorldRegionGeo>,
    val byRegion: Map<MixRegion, WorldRegionGeo>,
)

private data class RegionNavSpec(
    val left: MixRegion? = null,
    val right: MixRegion? = null,
    val up: MixRegion? = null,
    val down: MixRegion? = null,
)

private val REGION_NAV_SPECS: Map<MixRegion, RegionNavSpec> = mapOf(
    MixRegion.NORTH_AMERICA to RegionNavSpec(right = MixRegion.EUROPE, down = MixRegion.CENTRAL_AMERICA),
    MixRegion.CENTRAL_AMERICA to RegionNavSpec(left = MixRegion.NORTH_AMERICA, right = MixRegion.NORTH_AFRICA, up = MixRegion.NORTH_AMERICA, down = MixRegion.SOUTH_AMERICA),
    MixRegion.SOUTH_AMERICA to RegionNavSpec(right = MixRegion.SOUTH_AFRICA, up = MixRegion.CENTRAL_AMERICA),
    MixRegion.EUROPE to RegionNavSpec(left = MixRegion.NORTH_AMERICA, right = MixRegion.EURASIA, down = MixRegion.NORTH_AFRICA),
    MixRegion.NORTH_AFRICA to RegionNavSpec(left = MixRegion.CENTRAL_AMERICA, right = MixRegion.MIDDLE_EAST, up = MixRegion.EUROPE, down = MixRegion.SOUTH_AFRICA),
    MixRegion.SOUTH_AFRICA to RegionNavSpec(left = MixRegion.SOUTH_AMERICA, right = MixRegion.AUSTRALIA, up = MixRegion.NORTH_AFRICA),
    MixRegion.MIDDLE_EAST to RegionNavSpec(left = MixRegion.NORTH_AFRICA, right = MixRegion.ASIA, up = MixRegion.EURASIA, down = MixRegion.SOUTH_AFRICA),
    MixRegion.EURASIA to RegionNavSpec(left = MixRegion.EUROPE, down = MixRegion.ASIA),
    MixRegion.ASIA to RegionNavSpec(left = MixRegion.MIDDLE_EAST, right = MixRegion.OCEANIA, up = MixRegion.EURASIA, down = MixRegion.AUSTRALIA),
    MixRegion.AUSTRALIA to RegionNavSpec(left = MixRegion.SOUTH_AFRICA, right = MixRegion.OCEANIA, up = MixRegion.ASIA),
    MixRegion.OCEANIA to RegionNavSpec(left = MixRegion.AUSTRALIA, up = MixRegion.ASIA),
)

@Volatile
private var cachedWorldMapData: WorldMapData? = null

private fun parsePointsArray(arr: JSONArray?): List<Pair<Float, Float>> {
    if (arr == null) return emptyList()
    val list = ArrayList<Pair<Float, Float>>(arr.length())
    for (i in 0 until arr.length()) {
        val pair = arr.optJSONArray(i) ?: continue
        list.add(pair.optDouble(0).toFloat() to pair.optDouble(1).toFloat())
    }
    return list
}

private fun parseLinesArray(arr: JSONArray?): List<List<Pair<Float, Float>>> {
    if (arr == null) return emptyList()
    val list = ArrayList<List<Pair<Float, Float>>>(arr.length())
    for (i in 0 until arr.length()) {
        val pts = parsePointsArray(arr.optJSONArray(i))
        if (pts.isNotEmpty()) list.add(pts)
    }
    return list
}

private fun loadWorldMapData(context: Context): WorldMapData {
    cachedWorldMapData?.let { return it }
    val jsonText = context.assets.open("world_map_regions.json").bufferedReader().use { it.readText() }
    val root = JSONObject(jsonText)
    val globeOutline = parsePointsArray(root.optJSONArray("globe"))
    val equatorLine = parsePointsArray(root.optJSONArray("equator"))
    val parallels = parseLinesArray(root.optJSONArray("parallels"))
    val meridians = parseLinesArray(root.optJSONArray("meridians"))
    val regionsObj = root.optJSONObject("regions") ?: JSONObject()

    val geos = ArrayList<WorldRegionGeo>(REGION_NAV_SPECS.size)
    val byReg = EnumMap<MixRegion, WorldRegionGeo>(MixRegion::class.java)
    REGION_NAV_SPECS.forEach { (region, nav) ->
        val entry = regionsObj.optJSONObject(region.name) ?: return@forEach
        val anchorArr = entry.optJSONArray("anchor")
        val anchor = if (anchorArr != null && anchorArr.length() >= 2) {
            anchorArr.optDouble(0).toFloat() to anchorArr.optDouble(1).toFloat()
        } else 0.5f to 0.5f
        val polysJson = entry.optJSONArray("polygons")
        val polys = ArrayList<WorldPolyBbox>(polysJson?.length() ?: 0)
        if (polysJson != null) {
            for (i in 0 until polysJson.length()) {
                val pts = parsePointsArray(polysJson.optJSONArray(i))
                if (pts.size < 3) continue
                var minX = 1f
                var minY = 1f
                var maxX = 0f
                var maxY = 0f
                pts.forEach { (x, y) ->
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
                polys.add(WorldPolyBbox(minX, minY, maxX, maxY, pts))
            }
        }
        val geo = WorldRegionGeo(
            region = region,
            anchor = anchor,
            polygons = polys,
            left = nav.left,
            right = nav.right,
            up = nav.up,
            down = nav.down,
        )
        geos.add(geo)
        byReg[region] = geo
    }
    return WorldMapData(
        globeOutline = globeOutline,
        equatorLine = equatorLine,
        parallels = parallels,
        meridians = meridians,
        regions = geos,
        byRegion = byReg,
    ).also { cachedWorldMapData = it }
}

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
    val padX = totalW * 0.015f
    val padY = totalH * 0.02f
    val availW = (totalW - padX * 2f).coerceAtLeast(1f)
    val availH = (totalH - padY * 2f).coerceAtLeast(1f)
    val targetRatio = 1.9417f
    return if (availW / availH > targetRatio) {
        val w = availH * targetRatio
        MapViewport(left = (totalW - w) / 2f, top = padY, width = w, height = availH)
    } else {
        val h = availW / targetRatio
        MapViewport(left = padX, top = (totalH - h) / 2f, width = availW, height = h)
    }
}

private fun isPointInPolygon(nx: Float, ny: Float, poly: WorldPolyBbox): Boolean {
    if (nx < poly.minX || nx > poly.maxX || ny < poly.minY || ny > poly.maxY) return false
    var inside = false
    val pts = poly.points
    var j = pts.lastIndex
    for (i in pts.indices) {
        val (xi, yi) = pts[i]
        val (xj, yj) = pts[j]
        val intersects = ((yi > ny) != (yj > ny)) &&
            (nx < (xj - xi) * (ny - yi) / ((yj - yi).takeIf { kotlin.math.abs(it) > 1e-6f } ?: 1e-6f) + xi)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}

private fun hitTestWorldRegion(nx: Float, ny: Float, mapData: WorldMapData): MixRegion? {
    if (nx !in -0.02f..1.02f || ny !in -0.02f..1.02f) return null
    mapData.regions.forEach { geo ->
        if (geo.polygons.any { isPointInPolygon(nx, ny, it) }) {
            return geo.region
        }
    }
    var bestRegion: MixRegion? = null
    var bestDist = 0.07f
    mapData.regions.forEach { geo ->
        val dAnchor = hypot((nx - geo.anchor.first) * 1.94f, ny - geo.anchor.second)
        val dMinPoly = geo.polygons.minOfOrNull { poly ->
            poly.points.minOf { (px, py) -> hypot((nx - px) * 1.94f, ny - py) }
        } ?: 9f
        val d = minOf(dAnchor, dMinPoly)
        if (d < bestDist) {
            bestDist = d
            bestRegion = geo.region
        }
    }
    return bestRegion
}

private fun buildDirectPolygonPath(points: List<Pair<Float, Float>>, vp: MapViewport): Path =
    Path().apply {
        if (points.isEmpty()) return@apply
        val first = vp.toScreen(points[0].first, points[0].second)
        moveTo(first.x, first.y)
        for (i in 1 until points.size) {
            val p = vp.toScreen(points[i].first, points[i].second)
            lineTo(p.x, p.y)
        }
        close()
    }

private fun buildPolylinePath(points: List<Pair<Float, Float>>, vp: MapViewport): Path =
    Path().apply {
        if (points.isEmpty()) return@apply
        val first = vp.toScreen(points[0].first, points[0].second)
        moveTo(first.x, first.y)
        for (i in 1 until points.size) {
            val p = vp.toScreen(points[i].first, points[i].second)
            lineTo(p.x, p.y)
        }
    }

private data class BuiltTvWorldPaths(
    val vp: MapViewport,
    val globePath: Path,
    val equatorPath: Path,
    val parallelPaths: List<Path>,
    val meridianPaths: List<Path>,
    val regionPaths: Map<MixRegion, List<Path>>,
)

private fun buildWorldPaths(mapData: WorldMapData, vp: MapViewport): BuiltTvWorldPaths {
    val regMap = EnumMap<MixRegion, List<Path>>(MixRegion::class.java)
    mapData.regions.forEach { geo ->
        regMap[geo.region] = geo.polygons.map { buildDirectPolygonPath(it.points, vp) }
    }
    return BuiltTvWorldPaths(
        vp = vp,
        globePath = buildDirectPolygonPath(mapData.globeOutline, vp),
        equatorPath = buildPolylinePath(mapData.equatorLine, vp),
        parallelPaths = mapData.parallels.map { buildPolylinePath(it, vp) },
        meridianPaths = mapData.meridians.map { buildPolylinePath(it, vp) },
        regionPaths = regMap,
    )
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
    selectedLanguages: Set<String> = emptySet(),
    selectedGenres: Set<String> = emptySet(),
    matchingChannelCount: Int? = null,
    onToggleLanguage: (String) -> Unit = {},
    onToggleGenre: (String) -> Unit = {},
    onClearFilters: () -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val mapData = remember(context) { loadWorldMapData(context) }
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
    var filtersOpen by remember { mutableStateOf(false) }
    var wasFiltersOpen by remember { mutableStateOf(false) }

    val mapFocus = remember { FocusRequester() }
    val allWorldFocus = remember { FocusRequester() }
    val filtersFocus = remember { FocusRequester() }
    val startMixFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val activeFilterCount = selectedLanguages.size + selectedGenres.size

    LaunchedEffect(Unit) {
        delay(90)
        runCatching { mapFocus.requestFocus() }
    }

    LaunchedEffect(filtersOpen) {
        if (!filtersOpen && wasFiltersOpen) {
            delay(60)
            runCatching { filtersFocus.requestFocus() }
        }
        wasFiltersOpen = filtersOpen
    }

    Dialog(
        onDismissRequest = {
            if (filtersOpen) filtersOpen = false else onDismiss()
        },
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
                        val regionPart = if (allWorldSelected) {
                            MixRegion.ALL_WORLD.label(strings.language)
                        } else {
                            selectedRegions.joinToString(" · ") { it.label(strings.language) }
                        }
                        val shownCount = matchingChannelCount ?: totalChannels
                        val filterPart = if (activeFilterCount > 0) {
                            " · $activeFilterCount ${strings.t("filtri", "filters", "filtros")}"
                        } else ""
                        val activeSummary = "$regionPart$filterPart · ${formatCount(shownCount)} " +
                            strings.t("canali", "channels", "canales")
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
                            text = if (activeFilterCount > 0) {
                                strings.t("⚙ Filtri ($activeFilterCount)", "⚙ Filters ($activeFilterCount)", "⚙ Filtros ($activeFilterCount)")
                            } else {
                                strings.t("⚙ Filtri", "⚙ Filters", "⚙ Filtros")
                            },
                            selected = activeFilterCount > 0,
                            accent = false,
                            scale = scale,
                            onClick = { filtersOpen = true },
                            modifier = Modifier
                                .focusRequester(filtersFocus)
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

                // Interactive High-Def Natural Earth I World Map Canvas Container
                var canvasSize by remember { mutableStateOf(IntSize.Zero) }
                var calloutSize by remember { mutableStateOf(IntSize.Zero) }
                val mapShape = RoundedCornerShape((16 * scale).dp)

                val builtPaths = remember(canvasSize, mapData) {
                    if (canvasSize.width <= 0 || canvasSize.height <= 0) {
                        null
                    } else {
                        buildWorldPaths(
                            mapData = mapData,
                            vp = computeMapViewport(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
                        )
                    }
                }

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
                            if (filtersOpen) return@onPreviewKeyEvent false
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

                            val currentGeo = mapData.byRegion[focusedRegion]
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
                                        val topTarget = when (focusedRegion) {
                                            MixRegion.NORTH_AMERICA -> allWorldFocus
                                            MixRegion.EUROPE -> filtersFocus
                                            else -> startMixFocus
                                        }
                                        runCatching { topTarget.requestFocus() }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        .pointerInput(mapData) {
                            detectTapGestures { offset ->
                                val vp = computeMapViewport(size.width.toFloat(), size.height.toFloat())
                                val (nx, ny) = vp.toNormalized(offset.x, offset.y)
                                val hit = hitTestWorldRegion(nx, ny, mapData)
                                if (hit != null) {
                                    focusedRegion = hit
                                    runCatching { mapFocus.requestFocus() }
                                    onToggleRegion(hit)
                                }
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val paths = builtPaths ?: return@Canvas
                        val vp = paths.vp

                        // Natural Earth I curved ocean background & rim
                        drawPath(
                            path = paths.globePath,
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF0A223E), Color(0xFF050E1B)),
                            ),
                        )
                        clipPath(paths.globePath) {
                            val gridDash = PathEffect.dashPathEffect(floatArrayOf(5f, 7f), 0f)
                            paths.parallelPaths.forEach { p ->
                                drawPath(path = p, color = Color(0x2B1E3A5F), style = Stroke(width = 1f, pathEffect = gridDash))
                            }
                            paths.meridianPaths.forEach { m ->
                                drawPath(path = m, color = Color(0x2B1E3A5F), style = Stroke(width = 1f, pathEffect = gridDash))
                            }
                            drawPath(
                                path = paths.equatorPath,
                                color = Color(0x4038BDF8),
                                style = Stroke(width = 1.4f, pathEffect = gridDash),
                            )
                        }
                        drawPath(
                            path = paths.globePath,
                            color = Color(0xFF1F426B),
                            style = Stroke(width = 1.6f),
                        )

                        // Pass 1: Crisp Natural Earth country outlines grouped by MixRegion
                        mapData.regions.forEach { geo ->
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
                                    listOf(Color(0xC20EA5E9), Color(0xC20369A1)),
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

                            val borderColor = when {
                                isFocused -> Color(0xFFFFFFFF)
                                isDirectlySelected -> Color(0xFFE0F7FF)
                                allWorldSelected -> Color(0xAA082F49)
                                else -> Color(0xFF284870)
                            }
                            val borderWidth = when {
                                isFocused -> 1.5f
                                isDirectlySelected -> 1.05f
                                isActive -> 0.85f
                                else -> 0.8f
                            }

                            val polyPaths = paths.regionPaths[geo.region].orEmpty()
                            polyPaths.forEach { path ->
                                if (isDirectlySelected || isFocused) {
                                    drawPath(
                                        path = path,
                                        color = if (isFocused) Color(0x45FFFFFF) else Color(0x3800D4FF),
                                        style = Stroke(width = if (isFocused) 4.5f else 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                    )
                                }
                                drawPath(path = path, brush = fillBrush)
                                drawPath(
                                    path = path,
                                    color = borderColor,
                                    style = Stroke(width = borderWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }

                        // Pass 2: Region Anchor Pins
                        mapData.regions.forEach { geo ->
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
                    val activeGeo = mapData.byRegion[focusedRegion]
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

            if (filtersOpen) {
                BackHandler { filtersOpen = false }
                TvMixFiltersOverlay(
                    scale = scale,
                    language = strings.language,
                    selectedLanguages = selectedLanguages,
                    selectedGenres = selectedGenres,
                    matchingChannelCount = matchingChannelCount,
                    onToggleLanguage = onToggleLanguage,
                    onToggleGenre = onToggleGenre,
                    onClearFilters = onClearFilters,
                    onClose = { filtersOpen = false },
                )
            }
        }
    }
}

@Composable
private fun TvMixFiltersOverlay(
    scale: Float,
    language: String,
    selectedLanguages: Set<String>,
    selectedGenres: Set<String>,
    matchingChannelCount: Int?,
    onToggleLanguage: (String) -> Unit,
    onToggleGenre: (String) -> Unit,
    onClearFilters: () -> Unit,
    onClose: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var activeTab by remember { mutableStateOf(0) } // 0 = Languages, 1 = Genres
    val sortedLanguages = remember(language) { sortedMixLanguages(language) }
    val sortedGenres = remember(language) { sortedMixGenres(language) }
    val firstChipFocus = remember { FocusRequester() }
    val panelShape = RoundedCornerShape((22 * scale).dp)

    LaunchedEffect(activeTab) {
        delay(60)
        runCatching { firstChipFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD902060D)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(0.93f)
                .clip(panelShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF0C1728), Color(0xFF060B14))))
                .border(1.5.dp, Color(0xFF28507E), panelShape)
                .padding((20 * scale).dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = strings.t("FILTRI MIX · LINGUE E GENERI", "MIX FILTERS · LANGUAGES & GENRES", "FILTROS MIX · IDIOMAS Y GÉNEROS"),
                        color = Color.White,
                        fontSize = (17 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val totalActive = selectedLanguages.size + selectedGenres.size
                    val countLabel = if (matchingChannelCount != null) {
                        " · ${formatCount(matchingChannelCount)} ${strings.t("canali", "channels", "canales")}"
                    } else ""
                    Text(
                        text = if (totalActive == 0) {
                            strings.t("Nessun filtro attivo (tutti i canali)", "No active filters (all channels)", "Sin filtros activos") + countLabel
                        } else {
                            strings.t("$totalActive filtri selezionati", "$totalActive filters selected", "$totalActive filtros activos") + countLabel
                        },
                        color = Color(0xFF7DD3FC),
                        fontSize = (11.5f * scale).sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                    MixTopActionButton(
                        text = strings.t("Azzera", "Clear", "Borrar"),
                        selected = false,
                        accent = false,
                        scale = scale,
                        onClick = onClearFilters,
                    )
                    MixTopActionButton(
                        text = strings.t("✓ Applica", "✓ Apply", "✓ Aplicar"),
                        selected = true,
                        accent = true,
                        scale = scale,
                        onClick = onClose,
                    )
                }
            }

            Spacer(Modifier.height((12 * scale).dp))

            Row(horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
                val langLabel = strings.t("Lingue", "Languages", "Idiomas") +
                    " (${sortedLanguages.size})" +
                    if (selectedLanguages.isNotEmpty()) " · ${selectedLanguages.size}" else ""
                val genreLabel = strings.t("Generi", "Genres", "Géneros") +
                    " (${sortedGenres.size})" +
                    if (selectedGenres.isNotEmpty()) " · ${selectedGenres.size}" else ""
                MixTopActionButton(
                    text = langLabel,
                    selected = activeTab == 0,
                    accent = activeTab == 0,
                    scale = scale,
                    onClick = { activeTab = 0 },
                )
                MixTopActionButton(
                    text = genreLabel,
                    selected = activeTab == 1,
                    accent = activeTab == 1,
                    scale = scale,
                    onClick = { activeTab = 1 },
                )
            }

            Spacer(Modifier.height((12 * scale).dp))

            if (activeTab == 0) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues((4 * scale).dp),
                    horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
                    verticalArrangement = Arrangement.spacedBy((8 * scale).dp),
                ) {
                    items(sortedLanguages.indices.toList(), key = { sortedLanguages[it].code }) { idx ->
                        val item = sortedLanguages[idx]
                        TvFilterChip(
                            label = item.label(language),
                            selected = item.code in selectedLanguages,
                            scale = scale,
                            onClick = { onToggleLanguage(item.code) },
                            modifier = if (idx == 0) Modifier.focusRequester(firstChipFocus) else Modifier,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues((4 * scale).dp),
                    horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
                    verticalArrangement = Arrangement.spacedBy((8 * scale).dp),
                ) {
                    items(sortedGenres.indices.toList(), key = { sortedGenres[it].id }) { idx ->
                        val item = sortedGenres[idx]
                        TvFilterChip(
                            label = item.label(language),
                            selected = item.id in selectedGenres,
                            scale = scale,
                            onClick = { onToggleGenre(item.id) },
                            modifier = if (idx == 0) Modifier.focusRequester(firstChipFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFilterChip(
    label: String,
    selected: Boolean,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape((10 * scale).dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    focused && selected -> Color(0xFF00D4FF)
                    focused -> Color(0xFF1D4470)
                    selected -> Color(0xFF0B355C)
                    else -> Color(0xFF0C1728)
                },
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color.White
                    selected -> Color(0xFF00D4FF)
                    else -> Color(0xFF1E3A5F)
                },
                shape = shape,
            )
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = (11 * scale).dp, vertical = (9 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size((15 * scale).dp)
                .clip(CircleShape)
                .background(if (selected) Color(0xFF00D4FF) else Color(0xFF162740))
                .border(1.dp, if (selected) Color.White else Color(0xFF2D4C73), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF04101A),
                    modifier = Modifier.size((10 * scale).dp),
                )
            }
        }
        Spacer(Modifier.width((8 * scale).dp))
        Text(
            text = label,
            color = when {
                focused && selected -> Color(0xFF04101A)
                focused -> Color.White
                selected -> Color(0xFFBAE6FD)
                else -> Color(0xFFE2E8F0)
            },
            fontSize = (11.5f * scale).sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            .focusable(interactionSource = interaction)
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
