package it.casatv.mobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RadialGradient
import android.graphics.Shader
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import it.casatv.mobile.data.MobileChannel
import java.text.NumberFormat
import java.util.EnumMap
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

private data class MobileWorldPolyBbox(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val points: List<Pair<Float, Float>>,
)

private data class MobileWorldRegionGeo(
    val region: MixRegion,
    val anchor: Pair<Float, Float>,
    val polygons: List<MobileWorldPolyBbox>,
)

private data class MobileWorldMapData(
    val globeOutline: List<Pair<Float, Float>>,
    val equatorLine: List<Pair<Float, Float>>,
    val parallels: List<List<Pair<Float, Float>>>,
    val meridians: List<List<Pair<Float, Float>>>,
    val regions: List<MobileWorldRegionGeo>,
    val byRegion: Map<MixRegion, MobileWorldRegionGeo>,
)

@Volatile
private var cachedMobileWorldMapData: MobileWorldMapData? = null

private fun parseMobilePointsArray(arr: JSONArray?): List<Pair<Float, Float>> {
    if (arr == null) return emptyList()
    val list = ArrayList<Pair<Float, Float>>(arr.length())
    for (i in 0 until arr.length()) {
        val pair = arr.optJSONArray(i) ?: continue
        list.add(pair.optDouble(0).toFloat() to pair.optDouble(1).toFloat())
    }
    return list
}

private fun parseMobileLinesArray(arr: JSONArray?): List<List<Pair<Float, Float>>> {
    if (arr == null) return emptyList()
    val list = ArrayList<List<Pair<Float, Float>>>(arr.length())
    for (i in 0 until arr.length()) {
        val pts = parseMobilePointsArray(arr.optJSONArray(i))
        if (pts.isNotEmpty()) list.add(pts)
    }
    return list
}

private fun loadMobileWorldMapData(context: Context): MobileWorldMapData {
    cachedMobileWorldMapData?.let { return it }
    val jsonText = context.assets.open("world_map_regions.json").bufferedReader().use { it.readText() }
    val root = JSONObject(jsonText)
    val globeOutline = parseMobilePointsArray(root.optJSONArray("globe"))
    val equatorLine = parseMobilePointsArray(root.optJSONArray("equator"))
    val parallels = parseMobileLinesArray(root.optJSONArray("parallels"))
    val meridians = parseMobileLinesArray(root.optJSONArray("meridians"))
    val regionsObj = root.optJSONObject("regions") ?: JSONObject()

    val orderedRegions = listOf(
        MixRegion.NORTH_AMERICA,
        MixRegion.CENTRAL_AMERICA,
        MixRegion.SOUTH_AMERICA,
        MixRegion.EUROPE,
        MixRegion.NORTH_AFRICA,
        MixRegion.SOUTH_AFRICA,
        MixRegion.MIDDLE_EAST,
        MixRegion.EURASIA,
        MixRegion.ASIA,
        MixRegion.AUSTRALIA,
        MixRegion.OCEANIA,
    )

    val geos = ArrayList<MobileWorldRegionGeo>(orderedRegions.size)
    val byReg = EnumMap<MixRegion, MobileWorldRegionGeo>(MixRegion::class.java)
    orderedRegions.forEach { region ->
        val entry = regionsObj.optJSONObject(region.name) ?: return@forEach
        val anchorArr = entry.optJSONArray("anchor")
        val anchor = if (anchorArr != null && anchorArr.length() >= 2) {
            anchorArr.optDouble(0).toFloat() to anchorArr.optDouble(1).toFloat()
        } else 0.5f to 0.5f
        val polysJson = entry.optJSONArray("polygons")
        val polys = ArrayList<MobileWorldPolyBbox>(polysJson?.length() ?: 0)
        if (polysJson != null) {
            for (i in 0 until polysJson.length()) {
                val pts = parseMobilePointsArray(polysJson.optJSONArray(i))
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
                polys.add(MobileWorldPolyBbox(minX, minY, maxX, maxY, pts))
            }
        }
        val geo = MobileWorldRegionGeo(region = region, anchor = anchor, polygons = polys)
        geos.add(geo)
        byReg[region] = geo
    }
    return MobileWorldMapData(
        globeOutline = globeOutline,
        equatorLine = equatorLine,
        parallels = parallels,
        meridians = meridians,
        regions = geos,
        byRegion = byReg,
    ).also { cachedMobileWorldMapData = it }
}

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
    val padX = totalW * 0.015f
    val padY = totalH * 0.02f
    val availW = (totalW - padX * 2f).coerceAtLeast(1f)
    val availH = (totalH - padY * 2f).coerceAtLeast(1f)
    val targetRatio = 1.9417f
    return if (availW / availH > targetRatio) {
        val w = availH * targetRatio
        MobileMapViewport(left = (totalW - w) / 2f, top = padY, width = w, height = availH)
    } else {
        val h = availW / targetRatio
        MobileMapViewport(left = padX, top = (totalH - h) / 2f, width = availW, height = h)
    }
}

private fun isMobilePointInPolygon(nx: Float, ny: Float, poly: MobileWorldPolyBbox): Boolean {
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

private fun hitTestMobileWorldRegion(nx: Float, ny: Float, mapData: MobileWorldMapData): MixRegion? {
    if (nx !in -0.02f..1.02f || ny !in -0.02f..1.02f) return null
    mapData.regions.forEach { geo ->
        if (geo.polygons.any { isMobilePointInPolygon(nx, ny, it) }) {
            return geo.region
        }
    }
    var bestRegion: MixRegion? = null
    var bestDist = 0.085f
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

private fun appendMobileAndroidPolygonPath(path: AndroidPath, points: List<Pair<Float, Float>>, vp: MobileMapViewport) {
    if (points.isEmpty()) return
    val p0 = vp.toScreen(points[0].first, points[0].second)
    path.moveTo(p0.x, p0.y)
    for (i in 1 until points.size) {
        val p = vp.toScreen(points[i].first, points[i].second)
        path.lineTo(p.x, p.y)
    }
    path.close()
}

private fun appendMobileAndroidPolylinePath(path: AndroidPath, points: List<Pair<Float, Float>>, vp: MobileMapViewport) {
    if (points.isEmpty()) return
    val p0 = vp.toScreen(points[0].first, points[0].second)
    path.moveTo(p0.x, p0.y)
    for (i in 1 until points.size) {
        val p = vp.toScreen(points[i].first, points[i].second)
        path.lineTo(p.x, p.y)
    }
}

private data class CachedMobileWorldRender(
    val vp: MobileMapViewport,
    val unselectedBitmap: ImageBitmap,
    val allWorldBitmap: ImageBitmap,
    val regionPaths: Map<MixRegion, Path>,
    val selectedBrush: Brush,
)

private fun renderMobileBaseWorldBitmap(
    canvasW: Int,
    canvasH: Int,
    bitmapScale: Float,
    vp: MobileMapViewport,
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
        strokeWidth = 1.3f
        color = 0x4038BDF8
        pathEffect = dashEffect
    }
    c.drawPath(equatorPath, eqPaint)
    c.restore()

    val rimPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeWidth = 1.5f
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
            if (isAllWorld) 0xD9FFFFFF.toInt() else 0xFF1A1A1A.toInt(),
            if (isAllWorld) 0xD9B3B3B3.toInt() else 0xFF111111.toInt(),
            Shader.TileMode.CLAMP,
        )
    }
    val landStrokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        strokeWidth = if (isAllWorld) 0.85f else 0.65f
        color = if (isAllWorld) 0xAA000000.toInt() else 0xFF3A3A3A.toInt()
    }

    regionAndroidPaths.forEach { (_, path) ->
        c.drawPath(path, landFillPaint)
        c.drawPath(path, landStrokePaint)
    }
    return bmp.asImageBitmap()
}

private fun buildCachedMobileWorldRender(mapData: MobileWorldMapData, canvasSize: IntSize): CachedMobileWorldRender {
    val w = canvasSize.width.coerceAtLeast(1)
    val h = canvasSize.height.coerceAtLeast(1)
    val vp = computeMobileMapViewport(w.toFloat(), h.toFloat())
    val bitmapScale = minOf(1f, 1400f / w.toFloat())

    val globeAndroid = AndroidPath().apply {
        appendMobileAndroidPolygonPath(this, mapData.globeOutline, vp)
    }
    val equatorAndroid = AndroidPath().apply {
        appendMobileAndroidPolylinePath(this, mapData.equatorLine, vp)
    }
    val graticuleAndroid = AndroidPath().apply {
        mapData.parallels.forEach { appendMobileAndroidPolylinePath(this, it, vp) }
        mapData.meridians.forEach { appendMobileAndroidPolylinePath(this, it, vp) }
    }

    val androidPairs = ArrayList<Pair<MixRegion, AndroidPath>>(mapData.regions.size)
    val composeMap = EnumMap<MixRegion, Path>(MixRegion::class.java)
    mapData.regions.forEach { geo ->
        val merged = AndroidPath()
        geo.polygons.forEach { poly ->
            appendMobileAndroidPolygonPath(merged, poly.points, vp)
        }
        androidPairs.add(geo.region to merged)
        composeMap[geo.region] = merged.asComposePath()
    }

    val unselectedBmp = renderMobileBaseWorldBitmap(
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
    val allWorldBmp = renderMobileBaseWorldBitmap(
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

    return CachedMobileWorldRender(
        vp = vp,
        unselectedBitmap = unselectedBmp,
        allWorldBitmap = allWorldBmp,
        regionPaths = composeMap,
        selectedBrush = Brush.linearGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFFD4D4D4)),
            start = Offset(vp.left, vp.top),
            end = Offset(vp.left + vp.width, vp.top + vp.height),
        ),
    )
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
    selectedLanguages: Set<String> = emptySet(),
    selectedGenres: Set<String> = emptySet(),
    matchingChannelCount: Int? = null,
    onToggleLanguage: (String) -> Unit = {},
    onToggleGenre: (String) -> Unit = {},
    onClearFilters: () -> Unit = {},
) {
    fun tr(itStr: String, enStr: String, esStr: String): String = when (language.lowercase().take(2)) {
        "en" -> enStr
        "es" -> esStr
        else -> itStr
    }

    val allWorldSelected = MixRegion.ALL_WORLD in selectedRegions
    val activeFilterCount = selectedLanguages.size + selectedGenres.size
    var filtersOpen by remember { mutableStateOf(false) }
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
                .background(Color(0xFF000000))
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
                    val shownCount = matchingChannelCount ?: totalChannels
                    val filterLabel = if (activeFilterCount > 0) {
                        " · $activeFilterCount ${tr("filtri", "filters", "filtros")}"
                    } else ""
                    val activeNames = if (allWorldSelected) {
                        "${MixRegion.ALL_WORLD.label(language)} · ${formatMobileCount(shownCount)} " +
                            tr("canali", "channels", "canales") + filterLabel
                    } else {
                        val regionList = selectedRegions.joinToString(" · ") { it.label(language) }
                        val countPart = if (matchingChannelCount != null) {
                            " · ${formatMobileCount(shownCount)} ${tr("canali", "channels", "canales")}"
                        } else ""
                        regionList + countPart + filterLabel
                    }
                    Text(
                        text = tr(
                            "Tocca le regioni · $activeNames",
                            "Tap regions · $activeNames",
                            "Toca las regiones · $activeNames",
                        ),
                        color = Color(0xFFA3A3A3),
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
                            activeFilterCount = activeFilterCount,
                            onAllWorld = { onToggleRegion(MixRegion.ALL_WORLD) },
                            onOpenFilters = { filtersOpen = true },
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
                    activeFilterCount = activeFilterCount,
                    onAllWorld = { onToggleRegion(MixRegion.ALL_WORLD) },
                    onOpenFilters = { filtersOpen = true },
                    onStartMix = onStartMix,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF08101C))
                        .border(1.dp, Color(0xFF1C2C42), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }

        if (filtersOpen) {
            MobileMixFiltersDialog(
                language = language,
                selectedLanguages = selectedLanguages,
                selectedGenres = selectedGenres,
                matchingChannelCount = matchingChannelCount ?: totalChannels,
                onToggleLanguage = onToggleLanguage,
                onToggleGenre = onToggleGenre,
                onClearFilters = onClearFilters,
                onDismiss = { filtersOpen = false },
            )
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
    val context = LocalContext.current
    val mapData by produceState<MobileWorldMapData?>(initialValue = cachedMobileWorldMapData, context) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { loadMobileWorldMapData(context) }
        }
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var calloutSize by remember { mutableStateOf(IntSize.Zero) }
    val shape = RoundedCornerShape(16.dp)

    val cachedWorld by produceState<CachedMobileWorldRender?>(initialValue = null, canvasSize, mapData) {
        val data = mapData ?: return@produceState
        if (canvasSize.width <= 0 || canvasSize.height <= 0) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            buildCachedMobileWorldRender(data, canvasSize)
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF0A0A0A))
            .border(1.dp, Color(0xFF2B2B2B), shape)
            .onSizeChanged { canvasSize = it }
            .pointerInput(mapData) {
                detectTapGestures { offset ->
                    val data = mapData ?: return@detectTapGestures
                    val vp = computeMobileMapViewport(size.width.toFloat(), size.height.toFloat())
                    val (nx, ny) = vp.toNormalized(offset.x, offset.y)
                    val hit = hitTestMobileWorldRegion(nx, ny, data)
                    if (hit != null) onSelectRegion(hit)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val rendered = cachedWorld ?: return@Canvas
            val data = mapData ?: return@Canvas
            val vp = rendered.vp

            drawImage(
                image = if (allWorldSelected) rendered.allWorldBitmap else rendered.unselectedBitmap,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )

            if (!allWorldSelected) {
                selectedRegions.forEach { reg ->
                    val regPath = rendered.regionPaths[reg] ?: return@forEach
                    drawPath(path = regPath, brush = rendered.selectedBrush)
                    drawPath(
                        path = regPath,
                        color = Color(0xFFE0F7FF),
                        style = Stroke(width = 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }

            val inspectedPath = rendered.regionPaths[inspectedRegion]
            if (inspectedPath != null) {
                drawPath(
                    path = inspectedPath,
                    color = Color(0x44FFFFFF),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    path = inspectedPath,
                    color = Color.White,
                    style = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            data.regions.forEach { geo ->
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
        val activeGeo = mapData?.byRegion?.get(inspectedRegion)
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
                    .background(Color(0xF0000000))
                    .border(1.dp, if (active) Color(0xFFFFFFFF) else Color(0xFF525252), pillShape)
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
                            isDirect -> Color(0xFF1E1E1E)
                            allWorldSelected -> Color(0xFF141414)
                            else -> Color(0xFF0A0A0A)
                        },
                    )
                    .border(
                        width = if (isDirect) 2.dp else 1.dp,
                        color = when {
                            isDirect -> Color(0xFFFFFFFF)
                            isInspected -> Color(0xFFA3A3A3)
                            allWorldSelected -> Color(0x80FFFFFF)
                            else -> Color(0xFF2B2B2B)
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
                        .background(if (isSelected) Color(0xFFFFFFFF) else Color(0xFF171717))
                        .border(1.dp, if (isSelected) Color.White else Color(0xFF404040), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF000000),
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = region.label(language),
                        color = if (isSelected) Color(0xFFFFFFFF) else Color(0xFFE5E5E5),
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
    activeFilterCount: Int,
    onAllWorld: () -> Unit,
    onOpenFilters: () -> Unit,
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onAllWorld,
            modifier = Modifier.weight(1.08f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (allWorldSelected) Color(0xFF1F1F1F) else Color.Transparent,
                contentColor = Color(0xFFFFFFFF),
            ),
        ) {
            Text(
                text = tr("Tutto il mondo", "All World", "Todo"),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onOpenFilters,
            modifier = Modifier.weight(0.88f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (activeFilterCount > 0) Color(0xFF1F1F1F) else Color.Transparent,
                contentColor = Color(0xFFFFFFFF),
            ),
        ) {
            Text(
                text = if (activeFilterCount > 0) {
                    tr("Filtri ($activeFilterCount)", "Filters ($activeFilterCount)", "Filtros ($activeFilterCount)")
                } else {
                    tr("Filtri", "Filters", "Filtros")
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onStartMix,
            modifier = Modifier.weight(1.04f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = tr("Avvia Mix", "Start Mix", "Iniciar Mix"),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MobileMixFiltersDialog(
    language: String,
    selectedLanguages: Set<String>,
    selectedGenres: Set<String>,
    matchingChannelCount: Int,
    onToggleLanguage: (String) -> Unit,
    onToggleGenre: (String) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun tr(itStr: String, enStr: String, esStr: String): String = when (language.lowercase().take(2)) {
        "en" -> enStr
        "es" -> esStr
        else -> itStr
    }

    var activeTab by remember { mutableStateOf(0) } // 0 = Languages, 1 = Genres
    val sortedLanguages = remember(language) { sortedMobileMixLanguages(language) }
    val sortedGenres = remember(language) { sortedMobileMixGenres(language) }
    val totalActive = selectedLanguages.size + selectedGenres.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = tr("FILTRI MIX · LINGUE E GENERI", "MIX FILTERS · LANGUAGES & GENRES", "FILTROS MIX · IDIOMAS Y GÉNEROS"),
                        color = Color(0xFFF8FAFC),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (totalActive == 0) {
                            tr("Nessun filtro attivo", "No active filters", "Sin filtros activos") +
                                " · ${formatMobileCount(matchingChannelCount)} ${tr("canali", "channels", "canales")}"
                        } else {
                            tr("$totalActive filtri attivi", "$totalActive active filters", "$totalActive filtros activos") +
                                " · ${formatMobileCount(matchingChannelCount)} ${tr("canali", "channels", "canales")}"
                        },
                        color = Color(0xFFA3A3A3),
                        fontSize = 12.sp,
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val langLabel = tr("Lingue", "Languages", "Idiomas") +
                    " (${sortedLanguages.size})" +
                    if (selectedLanguages.isNotEmpty()) " · ${selectedLanguages.size}" else ""
                val genreLabel = tr("Generi", "Genres", "Géneros") +
                    " (${sortedGenres.size})" +
                    if (selectedGenres.isNotEmpty()) " · ${selectedGenres.size}" else ""
                MobileFilterTabButton(
                    text = langLabel,
                    active = activeTab == 0,
                    onClick = { activeTab = 0 },
                    modifier = Modifier.weight(1f),
                )
                MobileFilterTabButton(
                    text = genreLabel,
                    active = activeTab == 1,
                    onClick = { activeTab = 1 },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(10.dp))

            if (activeTab == 0) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sortedLanguages, key = { it.code }) { item ->
                        MobileFilterChip(
                            label = item.label(language),
                            selected = item.code in selectedLanguages,
                            onClick = { onToggleLanguage(item.code) },
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sortedGenres, key = { it.id }) { item ->
                        MobileFilterChip(
                            label = item.label(language),
                            selected = item.id in selectedGenres,
                            onClick = { onToggleGenre(item.id) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, Color(0xFF2B2B2B), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onClearFilters,
                    enabled = totalActive > 0,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF8FAFC)),
                ) {
                    Text(tr("Azzera", "Clear", "Borrar"))
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Applica", "Apply", "Aplicar"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MobileFilterTabButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (active) Color.White else Color(0xFF141414))
            .border(1.dp, if (active) Color.White else Color(0xFF2B2B2B), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) Color.Black else Color(0xFFE5E5E5),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MobileFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val chipShape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(chipShape)
            .background(if (selected) Color(0xFF1E1E1E) else Color(0xFF0A0A0A))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White else Color(0xFF2B2B2B),
                shape = chipShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color(0xFF171717))
                .border(1.dp, if (selected) Color.White else Color(0xFF404040), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFE5E5E5),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
