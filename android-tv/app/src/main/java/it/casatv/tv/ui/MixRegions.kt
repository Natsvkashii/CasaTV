package it.casatv.tv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RadialGradient
import android.graphics.Shader
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.NumberFormat
import java.util.EnumMap
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

private val STROKE_SELECTED_GLOW = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val STROKE_SELECTED_CORE = Stroke(width = 1.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val STROKE_FOCUSED_GLOW = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val STROKE_FOCUSED_CORE = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

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

private fun appendAndroidPolygonPath(path: AndroidPath, points: List<Pair<Float, Float>>, vp: MapViewport) {
    if (points.isEmpty()) return
    val p0 = vp.toScreen(points[0].first, points[0].second)
    path.moveTo(p0.x, p0.y)
    for (i in 1 until points.size) {
        val p = vp.toScreen(points[i].first, points[i].second)
        path.lineTo(p.x, p.y)
    }
    path.close()
}

private fun appendAndroidPolylinePath(path: AndroidPath, points: List<Pair<Float, Float>>, vp: MapViewport) {
    if (points.isEmpty()) return
    val p0 = vp.toScreen(points[0].first, points[0].second)
    path.moveTo(p0.x, p0.y)
    for (i in 1 until points.size) {
        val p = vp.toScreen(points[i].first, points[i].second)
        path.lineTo(p.x, p.y)
    }
}

private data class CachedTvWorldRender(
    val vp: MapViewport,
    val unselectedBitmap: ImageBitmap,
    val allWorldBitmap: ImageBitmap,
    val regionPaths: Map<MixRegion, Path>,
    val selectedBrush: Brush,
    val focusedBrush: Brush,
)

private fun renderTvBaseWorldBitmap(
    canvasW: Int,
    canvasH: Int,
    bitmapScale: Float,
    vp: MapViewport,
    globePath: AndroidPath,
    graticulePath: AndroidPath,
    equatorPath: AndroidPath,
    regionAndroidPaths: List<Pair<MixRegion, AndroidPath>>,
    isAllWorld: Boolean,
): ImageBitmap {
    val bw = (canvasW * bitmapScale).roundToInt().coerceAtLeast(1)
    val bh = (canvasH * bitmapScale).roundToInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    c.scale(bitmapScale, bitmapScale)

    val oceanPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        shader = RadialGradient(
            canvasW * 0.5f,
            canvasH * 0.5f,
            maxOf(canvasW, canvasH) * 0.55f,
            intArrayOf(0xFF0A223E.toInt(), 0xFF050E1B.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    c.drawPath(globePath, oceanPaint)

    c.save()
    c.clipPath(globePath)
    val dashEffect = DashPathEffect(floatArrayOf(5f, 7f), 0f)
    val gridPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeWidth = 1f
        color = 0x2B1E3A5F
        pathEffect = dashEffect
    }
    c.drawPath(graticulePath, gridPaint)
    val eqPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeWidth = 1.4f
        color = 0x4038BDF8
        pathEffect = dashEffect
    }
    c.drawPath(equatorPath, eqPaint)
    c.restore()

    val rimPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeWidth = 1.6f
        color = 0xFF1F426B.toInt()
    }
    c.drawPath(globePath, rimPaint)

    val landFillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        shader = LinearGradient(
            vp.left,
            vp.top,
            vp.left + vp.width,
            vp.top + vp.height,
            if (isAllWorld) 0xC20EA5E9.toInt() else 0xFF132740.toInt(),
            if (isAllWorld) 0xC20369A1.toInt() else 0xFF0D1B2E.toInt(),
            Shader.TileMode.CLAMP,
        )
    }
    val landStrokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        strokeWidth = if (isAllWorld) 0.85f else 0.8f
        color = if (isAllWorld) 0xAA082F49.toInt() else 0xFF284870.toInt()
    }

    regionAndroidPaths.forEach { (_, path) ->
        c.drawPath(path, landFillPaint)
        c.drawPath(path, landStrokePaint)
    }
    return bmp.asImageBitmap()
}

private fun buildCachedTvWorldRender(mapData: WorldMapData, canvasSize: IntSize): CachedTvWorldRender {
    val w = canvasSize.width.coerceAtLeast(1)
    val h = canvasSize.height.coerceAtLeast(1)
    val vp = computeMapViewport(w.toFloat(), h.toFloat())
    val bitmapScale = minOf(1f, 1400f / w.toFloat())

    val globeAndroid = AndroidPath().apply {
        appendAndroidPolygonPath(this, mapData.globeOutline, vp)
    }
    val equatorAndroid = AndroidPath().apply {
        appendAndroidPolylinePath(this, mapData.equatorLine, vp)
    }
    val graticuleAndroid = AndroidPath().apply {
        mapData.parallels.forEach { appendAndroidPolylinePath(this, it, vp) }
        mapData.meridians.forEach { appendAndroidPolylinePath(this, it, vp) }
    }

    val androidPairs = ArrayList<Pair<MixRegion, AndroidPath>>(mapData.regions.size)
    val composeMap = EnumMap<MixRegion, Path>(MixRegion::class.java)
    mapData.regions.forEach { geo ->
        val merged = AndroidPath()
        geo.polygons.forEach { poly ->
            appendAndroidPolygonPath(merged, poly.points, vp)
        }
        androidPairs.add(geo.region to merged)
        composeMap[geo.region] = merged.asComposePath()
    }

    val unselectedBmp = renderTvBaseWorldBitmap(
        canvasW = w,
        canvasH = h,
        bitmapScale = bitmapScale,
        vp = vp,
        globePath = globeAndroid,
        graticulePath = graticuleAndroid,
        equatorPath = equatorAndroid,
        regionAndroidPaths = androidPairs,
        isAllWorld = false,
    )
    val allWorldBmp = renderTvBaseWorldBitmap(
        canvasW = w,
        canvasH = h,
        bitmapScale = bitmapScale,
        vp = vp,
        globePath = globeAndroid,
        graticulePath = graticuleAndroid,
        equatorPath = equatorAndroid,
        regionAndroidPaths = androidPairs,
        isAllWorld = true,
    )

    return CachedTvWorldRender(
        vp = vp,
        unselectedBitmap = unselectedBmp,
        allWorldBitmap = allWorldBmp,
        regionPaths = composeMap,
        selectedBrush = Brush.linearGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFFD4D4D4)),
            start = Offset(vp.left, vp.top),
            end = Offset(vp.left + vp.width, vp.top + vp.height),
        ),
        focusedBrush = Brush.linearGradient(
            listOf(Color(0xFFE5E5E5), Color(0xFFB3B3B3)),
            start = Offset(vp.left, vp.top),
            end = Offset(vp.left + vp.width, vp.top + vp.height),
        ),
    )
}

private fun formatCount(n: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(n)

@Composable
fun TvMixRegionPickerHost(vm: MainViewModel) {
    val selectedMixRegions by vm.selectedMixRegions.collectAsStateWithLifecycle()
    val selectedMixLanguages by vm.selectedMixLanguages.collectAsStateWithLifecycle()
    val selectedMixGenres by vm.selectedMixGenres.collectAsStateWithLifecycle()
    val mixPickerStats by vm.mixPickerStats.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val zapQueue by vm.zapQueue.collectAsStateWithLifecycle()

    val totalAllChannels = remember(config?.stats?.channels, zapQueue.size) {
        config?.stats?.channels ?: zapQueue.size
    }
    MixRegionPickerDialog(
        selectedRegions = selectedMixRegions,
        regionCounts = mixPickerStats.regionCounts,
        totalChannels = totalAllChannels,
        onToggleRegion = { vm.toggleMixRegion(it) },
        onStartMix = { vm.startMixFromSelectedRegions() },
        onDismiss = { vm.dismissMixRegionPicker() },
        selectedLanguages = selectedMixLanguages,
        selectedGenres = selectedMixGenres,
        matchingChannelCount = mixPickerStats.matchingCount,
        onToggleLanguage = { vm.toggleMixLanguage(it) },
        onToggleGenre = { vm.toggleMixGenre(it) },
        onClearFilters = { vm.clearMixFilters() },
    )
}

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
    val mapData by produceState<WorldMapData?>(initialValue = cachedWorldMapData, context) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { loadWorldMapData(context) }
        }
    }
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
            val outerShape = RoundedCornerShape(8.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(outerShape)
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, Color(0xFF2B2B2B), outerShape)
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
                            color = Color.White,
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
                            color = Color(0xFFA3A3A3),
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
                            text = strings.t("Tutto il mondo", "All World", "Todo el mundo"),
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
                                strings.t("Filtri ($activeFilterCount)", "Filters ($activeFilterCount)", "Filtros ($activeFilterCount)")
                            } else {
                                strings.t("Filtri", "Filters", "Filtros")
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
                            text = strings.t("Avvia Mix", "Start Mix", "Iniciar Mix"),
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

                val cachedWorld by produceState<CachedTvWorldRender?>(initialValue = null, canvasSize, mapData) {
                    val data = mapData ?: return@produceState
                    if (canvasSize.width <= 0 || canvasSize.height <= 0) {
                        value = null
                        return@produceState
                    }
                    value = withContext(Dispatchers.Default) {
                        buildCachedTvWorldRender(data, canvasSize)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(mapShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF141414), Color(0xFF000000)),
                            ),
                        )
                        .border(
                            width = if (mapHasFocus) 2.dp else 1.dp,
                            color = if (mapHasFocus) Color.White else Color(0xFF2B2B2B),
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

                            val currentGeo = mapData?.byRegion?.get(focusedRegion)
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
                                val data = mapData ?: return@detectTapGestures
                                val vp = computeMapViewport(size.width.toFloat(), size.height.toFloat())
                                val (nx, ny) = vp.toNormalized(offset.x, offset.y)
                                val hit = hitTestWorldRegion(nx, ny, data)
                                if (hit != null) {
                                    focusedRegion = hit
                                    runCatching { mapFocus.requestFocus() }
                                    onToggleRegion(hit)
                                }
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val rendered = cachedWorld ?: return@Canvas
                        val data = mapData ?: return@Canvas
                        val vp = rendered.vp

                        // Single cached offscreen base bitmap (0.2ms, 0 SW path masks)
                        drawImage(
                            image = if (allWorldSelected) rendered.allWorldBitmap else rendered.unselectedBitmap,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        )

                        // Pass 1: Selected regions using single merged compound Path per region
                        if (!allWorldSelected) {
                            selectedRegions.forEach { reg ->
                                val regPath = rendered.regionPaths[reg] ?: return@forEach
                                drawPath(
                                    path = regPath,
                                    color = Color(0x3800D4FF),
                                    style = STROKE_SELECTED_GLOW,
                                )
                                drawPath(path = regPath, brush = rendered.selectedBrush)
                                drawPath(
                                    path = regPath,
                                    color = Color(0xFFE0F7FF),
                                    style = STROKE_SELECTED_CORE,
                                )
                            }
                        }

                        // Pass 2: Focused region outline (1 single merged Path)
                        if (mapHasFocus) {
                            val focusPath = rendered.regionPaths[focusedRegion]
                            if (focusPath != null) {
                                if (!allWorldSelected && focusedRegion !in selectedRegions) {
                                    drawPath(path = focusPath, brush = rendered.focusedBrush)
                                }
                                drawPath(
                                    path = focusPath,
                                    color = Color(0x45FFFFFF),
                                    style = STROKE_FOCUSED_GLOW,
                                )
                                drawPath(
                                    path = focusPath,
                                    color = Color.White,
                                    style = STROKE_FOCUSED_CORE,
                                )
                            }
                        }

                        // Pass 3: Region Anchor Pins (GPU circles)
                        data.regions.forEach { geo ->
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
                    val activeGeo = mapData?.byRegion?.get(focusedRegion)
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

                        val badgeShape = RoundedCornerShape(4.dp)
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(clampedX, clampedY) }
                                .onSizeChanged { calloutSize = it }
                                .clip(badgeShape)
                                .background(Color(0xE60A0A0A))
                                .border(
                                    width = 1.dp,
                                    color = if (regionSelected) Color.White else Color(0xFF52525B),
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
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xD90A0A0A))
                            .border(1.dp, Color(0xFF2B2B2B), RoundedCornerShape(4.dp))
                            .padding(horizontal = (9 * scale).dp, vertical = (3.5f * scale).dp),
                    ) {
                        Text(
                            text = strings.t(
                                "Frecce: esplora · OK: seleziona · SU: Barra in alto",
                                "Arrows: explore · OK: select · UP: Top bar",
                                "Flechas: explorar · OK: elegir · ARRIBA: Barra superior",
                            ),
                            color = Color(0xFFA3A3A3),
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
                        color = Color(0xFFD4D4D8),
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
                            color = Color.White,
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
                                                region in selectedRegions -> Color(0xFF222222)
                                                allWorldSelected -> Color(0xFF1A1A1A)
                                                isCursor -> Color(0xFF1F1F1F)
                                                else -> Color(0xFF111111)
                                            },
                                        )
                                        .border(
                                            width = if (isCursor) 2.dp else 1.dp,
                                            color = when {
                                                isCursor -> Color.White
                                                region in selectedRegions -> Color.White
                                                allWorldSelected -> Color(0xFF737373)
                                                else -> Color(0xFF2B2B2B)
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
                                                if (isSelected) Color.White else Color(0xFF52525B),
                                            ),
                                    )
                                    Spacer(Modifier.width((5 * scale).dp))
                                    Text(
                                        text = if (count > 0) "${region.label(strings.language)} · ${formatCount(count)}" else region.label(strings.language),
                                        color = when {
                                            isCursor || isSelected -> Color.White
                                            else -> Color(0xFFA3A3A3)
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

@OptIn(ExperimentalComposeUiApi::class)
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
    val clearFocus = remember { FocusRequester() }
    val applyFocus = remember { FocusRequester() }
    val langTabFocus = remember { FocusRequester() }
    val genreTabFocus = remember { FocusRequester() }
    val firstChipFocus = remember { FocusRequester() }
    val panelShape = RoundedCornerShape((22 * scale).dp)

    LaunchedEffect(activeTab) {
        delay(60)
        runCatching { firstChipFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD902060D)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(0.93f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, Color(0xFF2B2B2B), RoundedCornerShape(8.dp))
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
                            color = Color(0xFFA3A3A3),
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
                            modifier = Modifier
                                .focusRequester(clearFocus)
                                .focusProperties {
                                    up = FocusRequester.Cancel
                                    left = genreTabFocus
                                    right = applyFocus
                                    down = if (activeTab == 0) langTabFocus else genreTabFocus
                                },
                        )
                        MixTopActionButton(
                            text = strings.t("Applica", "Apply", "Aplicar"),
                            selected = true,
                            accent = true,
                            scale = scale,
                            onClick = onClose,
                            modifier = Modifier
                                .focusRequester(applyFocus)
                                .focusProperties {
                                    up = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                    left = clearFocus
                                    down = if (activeTab == 0) langTabFocus else genreTabFocus
                                },
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
                        modifier = Modifier
                            .focusRequester(langTabFocus)
                            .focusProperties {
                                up = applyFocus
                                left = FocusRequester.Cancel
                                right = genreTabFocus
                                down = firstChipFocus
                            },
                    )
                    MixTopActionButton(
                        text = genreLabel,
                        selected = activeTab == 1,
                        accent = activeTab == 1,
                        scale = scale,
                        onClick = { activeTab = 1 },
                        modifier = Modifier
                            .focusRequester(genreTabFocus)
                            .focusProperties {
                                up = applyFocus
                                left = langTabFocus
                                right = clearFocus
                                down = firstChipFocus
                            },
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
                            val chipModifier = when (idx) {
                                0 -> Modifier
                                    .focusRequester(firstChipFocus)
                                    .focusProperties { up = langTabFocus }
                                1 -> Modifier.focusProperties { up = langTabFocus }
                                2 -> Modifier.focusProperties { up = clearFocus }
                                3 -> Modifier.focusProperties { up = applyFocus }
                                else -> Modifier
                            }
                            TvFilterChip(
                                label = item.label(language),
                                selected = item.code in selectedLanguages,
                                scale = scale,
                                onClick = { onToggleLanguage(item.code) },
                                modifier = chipModifier,
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
                            val chipModifier = when (idx) {
                                0 -> Modifier
                                    .focusRequester(firstChipFocus)
                                    .focusProperties { up = genreTabFocus }
                                1 -> Modifier.focusProperties { up = genreTabFocus }
                                2 -> Modifier.focusProperties { up = clearFocus }
                                3 -> Modifier.focusProperties { up = applyFocus }
                                else -> Modifier
                            }
                            TvFilterChip(
                                label = item.label(language),
                                selected = item.id in selectedGenres,
                                scale = scale,
                                onClick = { onToggleGenre(item.id) },
                                modifier = chipModifier,
                            )
                        }
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
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    focused -> Color.White
                    selected -> Color(0xFF222222)
                    else -> Color(0xFF141414)
                },
            )
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = when {
                    focused || selected -> Color.White
                    else -> Color(0xFF2B2B2B)
                },
                shape = shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = (11 * scale).dp, vertical = (9 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size((15 * scale).dp)
                .clip(CircleShape)
                .background(if (focused) Color.Black else if (selected) Color.White else Color(0xFF222222))
                .border(1.dp, if (focused) Color.Black else Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (focused) Color.White else Color.Black,
                    modifier = Modifier.size((10 * scale).dp),
                )
            }
        }
        Spacer(Modifier.width((8 * scale).dp))
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
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
    val shape = RoundedCornerShape(6.dp)
    val bg = when {
        focused -> Color.White
        accent -> Color(0xFF222222)
        selected -> Color(0xFF1E1E1E)
        else -> Color(0xFF141414)
    }
    val fg = if (focused) Color.Black else Color.White
    val border = when {
        focused || accent || selected -> Color.White
        else -> Color(0xFF2B2B2B)
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
