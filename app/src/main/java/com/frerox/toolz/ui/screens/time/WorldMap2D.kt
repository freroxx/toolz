package com.frerox.toolz.ui.screens.time

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

// ─── Map mode ─────────────────────────────────────────────────────────────────
enum class MapMode { NORMAL, SATELLITE }

// ─── Theme-aware color schema ─────────────────────────────────────────────────
data class WorldMapColors(
    val ocean: Color,
    val land: Color,
    val landBorder: Color,
    val gridLine: Color,
    val equator: Color,
    val dotDefault: Color,
    val dotHighlight: Color,
    val dotSelected: Color,
    val nightOverlayAlpha: Float = 0.34f,
) {
    companion object {
        fun fromTheme(primary: Color, secondary: Color, isDark: Boolean): WorldMapColors =
            if (isDark) WorldMapColors(
                ocean             = Color(0xFF0A1520).blend(primary, 0.07f),
                land              = Color(0xFF182820).blend(secondary, 0.10f),
                landBorder        = secondary.copy(alpha = 0.22f),
                gridLine          = primary.copy(alpha = 0.12f),
                equator           = primary.copy(alpha = 0.35f),
                dotDefault        = Color(0xFF90A4AE),
                dotHighlight      = secondary,
                dotSelected       = primary,
                nightOverlayAlpha = 0.50f,
            ) else WorldMapColors(
                ocean             = Color(0xFFD4E8F8).blend(primary, 0.08f),
                land              = Color(0xFFB4CCBA).blend(secondary, 0.12f),
                landBorder        = secondary.copy(alpha = 0.28f),
                gridLine          = primary.copy(alpha = 0.14f),
                equator           = primary.copy(alpha = 0.38f),
                dotDefault        = Color(0xFF546E7A),
                dotHighlight      = secondary,
                dotSelected       = primary,
                nightOverlayAlpha = 0.34f,
            )

        val Satellite = WorldMapColors(
            ocean             = Color(0xFF060D18),
            land              = Color(0xFF1A2B1C),
            landBorder        = Color(0xFF2E5235).copy(alpha = 0.55f),
            gridLine          = Color(0xFFFFFFFF).copy(alpha = 0.08f),
            equator           = Color(0xFFFFFFFF).copy(alpha = 0.18f),
            dotDefault        = Color(0xFFE0EEF8),
            dotHighlight      = Color(0xFF00E5FF),
            dotSelected       = Color(0xFFFFD54F),
            nightOverlayAlpha = 0.48f,
        )
    }
}

private fun Color.blend(other: Color, t: Float) = Color(
    red   = (red   * (1 - t) + other.red   * t).coerceIn(0f, 1f),
    green = (green * (1 - t) + other.green * t).coerceIn(0f, 1f),
    blue  = (blue  * (1 - t) + other.blue  * t).coerceIn(0f, 1f),
    alpha = 1f,
)

// ─── Gesture state — plain Kotlin class, NO Compose snapshot semantics ────────
//
// WHY NOT mutableStateOf / mutableFloatStateOf?
// ==============================================
// pointerInput coroutines run in the Compose coroutine scope with an associated
// Snapshot. Writes to mutableStateOf inside that coroutine go into that snapshot
// and may NOT be readable within the same coroutine before the snapshot is
// committed (which only happens at a Compose frame boundary). This creates a
// race: the tap handler reads zoom/pan BEFORE the gesture's snapshot commits,
// so it sees the old, pre-gesture values.
//
// A plain Kotlin class has no snapshot semantics — writes are immediately
// visible to every read in the same thread (the main thread, always). This
// guarantees the tap handler sees the exact same transform the Canvas used.
private class MapGestureState {
    var zoom: Float = 1f
    var panX: Float = 0f
    var panY: Float = 0f
    // Canvas size, set each draw frame — always current
    var canvasW: Float = 0f
    var canvasH: Float = 0f
}

// ─── Helper: compute map w/h at 2:1 ratio fitting into canvas ─────────────────
// Always Fit Center: ensures 100% of the map is visible initially.
private fun getMapDimensions(canvasW: Float, canvasH: Float): Pair<Float, Float> {
    if (canvasW == 0f || canvasH == 0f) return 0f to 0f
    val aspectRatio = 2f
    return if (canvasW / canvasH > aspectRatio) {
        // Very wide screen: height is the bottleneck
        (canvasH * aspectRatio) to canvasH
    } else {
        // Tall screen: width is the bottleneck
        canvasW to (canvasW / aspectRatio)
    }
}

// ─── Main composable ──────────────────────────────────────────────────────────
@Composable
fun WorldMap2D(
    locations: List<WorldClockLocation>,
    selectedLocation: WorldClockLocation?,
    highlightedZones: Set<String>,
    userLatLon: Pair<Double, Double>?,
    mapMode: MapMode,
    mapColors: WorldMapColors,
    modifier: Modifier = Modifier,
    onLocationSelected: (WorldClockLocation) -> Unit,
) {
    val context = LocalContext.current

    // ── Vector map path ───────────────────────────────────────────────────────
    var vectorPath by remember { mutableStateOf<Path?>(null) }
    var pathBuiltForSize by remember { mutableStateOf(Size.Zero) }

    // ── Satellite bitmap — equirectangular NASA Blue Marble ───────────────────
    var satelliteBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        if (satelliteBitmap == null) {
            withContext(Dispatchers.IO) {
                val month = java.time.YearMonth.now().toString()
                val cacheFile = java.io.File(context.cacheDir, "satellite_$month.jpg")

                if (cacheFile.exists()) {
                    try {
                        satelliteBitmap = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                        return@withContext
                    } catch (e: Exception) {}
                }

                context.cacheDir.listFiles()?.forEach {
                    if (it.name.startsWith("satellite_") && it.name != cacheFile.name) {
                        it.delete()
                    }
                }

                val urls = listOf(
                    "https://eoimages.gsfc.nasa.gov/images/imagerecords/57000/57752/land_shallow_topo_2048.jpg",
                    "https://eoimages.gsfc.nasa.gov/images/imagerecords/74000/74518/world.200407.3x5400x2700.jpg"
                )
                for (url in urls) {
                    try {
                        val u = java.net.URL(url)
                        val connection = u.openConnection() as java.net.HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 10000
                        connection.connect()
                        if (connection.responseCode == 200) {
                            val input = connection.inputStream
                            cacheFile.outputStream().use { out ->
                                input.copyTo(out)
                            }
                            satelliteBitmap = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                            break
                        }
                    } catch (e: Exception) {}
                }
            }
        }
    }

    // ── Gesture state — pure Kotlin, no snapshot isolation ────────────────────
    val gs = remember { MapGestureState() }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    // canvasVersion is the ONLY Compose state variable for gesture updates.
    // Incrementing it is what triggers the Canvas to recompose and redraw.
    // gs.zoom/panX/panY are NOT Compose state — they're plain Kotlin vars.
    var canvasVersion by remember { mutableIntStateOf(0) }

    // ── Purely visual animations — don't affect hit-testing ───────────────────
    val selAnim = remember { Animatable(0f) }
    LaunchedEffect(selectedLocation) {
        if (selectedLocation != null) {
            selAnim.snapTo(0f)
            launch {
                selAnim.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
            }
            // Animate pan/zoom to center the selection
            if (canvasSize != androidx.compose.ui.geometry.Size.Zero) {
                launch {
                    val canvasW = canvasSize.width
                    val canvasH = canvasSize.height
                    val (w, h) = getMapDimensions(canvasW, canvasH)
                    val cx = canvasW / 2f
                    val cy = canvasH / 2f
                    // mapOffset centers the map in the canvas
                    val mapOffX = (canvasW - w) / 2f
                    val mapOffY = (canvasH - h) / 2f
                    // Position of the target dot in canvas space (before zoom/pan)
                    val mapX = latLonToOffset(selectedLocation.latitude.toFloat(), selectedLocation.longitude.toFloat(), w, h).x + mapOffX
                    val mapY = latLonToOffset(selectedLocation.latitude.toFloat(), selectedLocation.longitude.toFloat(), w, h).y + mapOffY

                    val targetZoom = maxOf(gs.zoom, 2f)
                    // panX such that: zoom*(mapX - cx) + cx + panX = cx  => panX = -zoom*(mapX-cx)
                    val targetPanX = -targetZoom * (mapX - cx)
                    val targetPanY = -targetZoom * (mapY - cy)

                    androidx.compose.animation.core.Animatable(0f).animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                    ) {
                        val progress = this.value
                        gs.zoom = androidx.compose.ui.util.lerp(gs.zoom, targetZoom, progress)
                        gs.panX = androidx.compose.ui.util.lerp(gs.panX, targetPanX, progress)
                        gs.panY = androidx.compose.ui.util.lerp(gs.panY, targetPanY, progress)
                        canvasVersion++
                    }
                }
            }
        }
    }
    val hiAnim = remember { Animatable(0f) }
    LaunchedEffect(highlightedZones) {
        if (highlightedZones.isNotEmpty()) {
            hiAnim.snapTo(0f)
            hiAnim.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
        }
    }
    val pulsAnim = remember { Animatable(0f) }
    LaunchedEffect(userLatLon) {
        if (userLatLon != null) while (true) {
            pulsAnim.snapTo(0f)
            pulsAnim.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
            delay(2800)
        }
    }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(60_000L) } }

    // ── Canvas ────────────────────────────────────────────────────────────────
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                // TAP_SLOP: how many px of drift before we treat it as a drag, not a tap
                val TAP_SLOP = 16.dp.toPx()

                awaitEachGesture {
                    // ── 1. Capture the first pointer down ─────────────────────
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    firstDown.consume()

                    val downPos = firstDown.position
                    var isTap   = true

                    // ── 2. Process all subsequent events until all fingers up ─
                    do {
                        val event  = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed }
                        if (active.isEmpty()) break

                        val centroid   = event.calculateCentroid(useCurrent = false)
                        val panChange  = event.calculatePan()
                        val zoomChange = event.calculateZoom()
                        val isMulti    = active.size >= 2

                        val drift = active.firstOrNull()?.position
                            ?.let { p -> val d = p - downPos; sqrt(d.x * d.x + d.y * d.y) }
                            ?: 0f

                        if (isMulti || drift > TAP_SLOP) isTap = false

                        if (!isTap) {
                            val cW = gs.canvasW; val cH = gs.canvasH
                            val (mW, mH) = getMapDimensions(cW, cH)
                            val cx = cW / 2f; val cy = cH / 2f
                            val mapOffX = (cW - mW) / 2f
                            val mapOffY = (cH - mH) / 2f

                            val newZoom = (gs.zoom * zoomChange).coerceIn(1f, 15f)
                            val f       = newZoom / gs.zoom

                            // Zoom around centroid + apply pan delta
                            val rawPanX = gs.panX * f + (centroid.x - cx) * (1f - f) + panChange.x
                            val rawPanY = gs.panY * f + (centroid.y - cy) * (1f - f) + panChange.y

                            // Clamp: the visible map edge must not go past canvas edge
                            // Map left edge in screen space: zoom*(0 + mapOffX - cx) + cx + panX
                            // That should be >= 0:  panX >= -zoom*(mapOffX - cx) - cx ... simplified:
                            // Max pan X = zoom*(mW/2 + mapOffX - cx) - cx + cx = zoom*(mW/2 + mapOffX - cx)
                            val maxPX = newZoom * (mW / 2f + mapOffX - cx) + cx - cx // simplifies to:
                            val clampPX = newZoom * (mW / 2f) - cW / 2f + (cW - newZoom * cW) / 2f
                            // Simpler bounds: after transform, left of map = zoom*(mapOffX - cx) + cx + panX
                            // Must be <= 0 for left, right of map must be >= cW
                            val leftEdge  = { p: Float -> newZoom * (mapOffX - cx) + cx + p }
                            val rightEdge = { p: Float -> newZoom * (mapOffX + mW - cx) + cx + p }
                            val topEdge   = { p: Float -> newZoom * (mapOffY - cy) + cy + p }
                            val botEdge   = { p: Float -> newZoom * (mapOffY + mH - cy) + cy + p }
                            val minPX = if (mW * newZoom > cW) -(rightEdge(0f) - cW) else -(leftEdge(0f))
                            val maxPanX = if (mW * newZoom > cW) -leftEdge(0f) else -(rightEdge(0f) - cW)
                            val minPY = if (mH * newZoom > cH) -(botEdge(0f) - cH) else -(topEdge(0f))
                            val maxPanY = if (mH * newZoom > cH) -topEdge(0f) else -(botEdge(0f) - cH)

                            gs.zoom = newZoom
                            gs.panX = rawPanX.coerceIn(minOf(minPX, maxPanX), maxOf(minPX, maxPanX))
                            gs.panY = rawPanY.coerceIn(minOf(minPY, maxPanY), maxOf(minPY, maxPanY))

                            canvasVersion++
                        }

                        event.changes.forEach { it.consume() }

                    } while (event.changes.any { it.pressed })

                    // ── 3. Tap handler ────────────────────────────────────────
                    // The draw pipeline applies:
                    //   withTransform { translate(panX, panY); scale(zoom, pivot=canvasCenter) }
                    //   then translate((canvasW-w)/2, (canvasH-h)/2)
                    // So screen coords: screenX = zoom*(mapX + mapOffX - cx) + cx + panX
                    // where mapX = lonFraction * w
                    if (isTap) {
                        val cW = gs.canvasW; val cH = gs.canvasH
                        val (w, h) = getMapDimensions(cW, cH)
                        if (w > 0f && h > 0f) {
                            val cx = cW / 2f; val cy = cH / 2f
                            val mapOffX = (cW - w) / 2f
                            val mapOffY = (cH - h) / 2f

                            val maxScreenDistSq = 48.dp.toPx() * 48.dp.toPx()

                            val nearest = locations.minByOrNull { loc ->
                                val mapX = (loc.longitude.toFloat() + 180f) / 360f * w + mapOffX
                                val mapY = (90f - loc.latitude.toFloat()) / 180f * h + mapOffY
                                val screenX = gs.zoom * (mapX - cx) + cx + gs.panX
                                val screenY = gs.zoom * (mapY - cy) + cy + gs.panY
                                val dx = screenX - downPos.x
                                val dy = screenY - downPos.y
                                dx * dx + dy * dy
                            }
                            if (nearest != null) {
                                val mapX = (nearest.longitude.toFloat() + 180f) / 360f * w + mapOffX
                                val mapY = (90f - nearest.latitude.toFloat()) / 180f * h + mapOffY
                                val screenX = gs.zoom * (mapX - cx) + cx + gs.panX
                                val screenY = gs.zoom * (mapY - cy) + cy + gs.panY
                                val dx = screenX - downPos.x
                                val dy = screenY - downPos.y
                                if (dx * dx + dy * dy <= maxScreenDistSq) {
                                    onLocationSelected(nearest)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // Read canvasVersion to subscribe — Canvas recomposes when it increments.
        @Suppress("UNUSED_EXPRESSION") canvasVersion

        // Update the canvas size in gs immediately (same draw phase, main thread).
        gs.canvasW = size.width
        gs.canvasH = size.height

        val canvasW = size.width
        val canvasH = size.height
        val (w, h)  = getMapDimensions(canvasW, canvasH)
        val zoom    = gs.zoom
        val panX    = gs.panX
        val panY    = gs.panY
        val isSat   = mapMode == MapMode.SATELLITE
        val colors  = if (isSat) WorldMapColors.Satellite else mapColors

        // ── Build vector path lazily (first draw, or if canvas size changed) ─
        if (pathBuiltForSize != size && !isSat) {
            pathBuiltForSize = size
        }

        // ── Ocean background ──────────────────────────────────────────────────
        drawRect(color = colors.ocean, size = size)

        // ── World-space transform ─────────────────────────────────────────────
        // The map content (w×h) is centered in the canvas (canvasW×canvasH).
        // In portrait-fullscreen, w > canvasW so the map overflows — user pans.
        withTransform({
            translate(panX, panY)
            scale(zoom, zoom, pivot = Offset(canvasW / 2f, canvasH / 2f))
        }) {
            clipRect(0f, 0f, canvasW, canvasH) {
                withTransform({ translate((canvasW - w) / 2f, (canvasH - h) / 2f) }) {

                    if (isSat) {
                        val bmp = satelliteBitmap
                        if (bmp != null) {
                            drawImage(
                                image     = bmp.asImageBitmap(),
                                dstOffset = IntOffset.Zero,
                                dstSize   = IntSize(w.roundToInt(), h.roundToInt()),
                            )
                        } else {
                            drawRect(Color(0xFF0D1B2A), size = Size(w, h))
                        }
                        drawGraticules(colors, w, h, zoom)
                    } else {
                        drawGraticules(colors, w, h, zoom)
                        vectorPath?.let { path ->
                            drawPath(path, colors.land)
                            drawPath(path, colors.landBorder,
                                style = Stroke(0.6.dp.toPx() / zoom.coerceAtLeast(0.1f)))
                        }
                    }

                    drawDayNightOverlay(now, w, h, colors)

                    drawLocationDots(
                        locations   = locations,
                        selected    = selectedLocation,
                        highlighted = highlightedZones,
                        selPulse    = selAnim.value,
                        hiPulse     = hiAnim.value,
                        colors      = colors,
                        w = w, h = h, zoom = zoom,
                    )

                    userLatLon?.let { (lat, lon) ->
                        drawUserPin(lat.toFloat(), lon.toFloat(), pulsAnim.value, w, h, zoom)
                    }
                }
            }
        }
    }

    // ── Build vector path on background thread ────────────────────────────────
    LaunchedEffect(gs.canvasW, gs.canvasH) {
        val cW = gs.canvasW; val cH = gs.canvasH
        if (cW <= 0f || cH <= 0f) return@LaunchedEffect
        val (w, h) = getMapDimensions(cW, cH)
        withContext(Dispatchers.IO) {
            try {
                val text = context.assets.open("world_map.txt").bufferedReader().use { it.readText() }
                val p = Path()
                text.split("|").forEach { poly ->
                    val cs = poly.split(",")
                    if (cs.size >= 2) {
                        var first = true; var i = 0
                        while (i < cs.size - 1) {
                            val lon = cs[i].toFloatOrNull()
                            val lat = cs[i + 1].toFloatOrNull()
                            if (lon != null && lat != null) {
                                val x = (lon + 180f) / 360f * w
                                val y = (90f - lat) / 180f * h
                                if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
                            }
                            i += 2
                        }
                        p.close()
                    }
                }
                vectorPath = p
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}

// ─── Projection ───────────────────────────────────────────────────────────────

private fun latLonToOffset(lat: Float, lon: Float, w: Float, h: Float) = Offset(
    x = (lon + 180f) / 360f * w,
    y = (90f - lat) / 180f * h,
)

// ─── Graticule grid ───────────────────────────────────────────────────────────
private fun DrawScope.drawGraticules(c: WorldMapColors, w: Float, h: Float, zoom: Float) {
    val sw = (0.5.dp.toPx() / zoom).coerceAtLeast(0.3f)
    for (lat in -60..60 step 30) {
        val y = (90f - lat.toFloat()) / 180f * h
        drawLine(if (lat == 0) c.equator else c.gridLine, Offset(0f, y), Offset(w, y), sw)
    }
    for (lon in -150..180 step 30) {
        val x = (lon.toFloat() + 180f) / 360f * w
        drawLine(if (lon == 0) c.equator else c.gridLine, Offset(x, 0f), Offset(x, h), sw)
    }
    val faint = c.gridLine.copy(alpha = c.gridLine.alpha * 0.45f)
    for (lat in listOf(23.4f, -23.4f, 66.6f, -66.6f)) {
        drawLine(faint, Offset(0f, (90f - lat) / 180f * h), Offset(w, (90f - lat) / 180f * h), sw * 0.6f)
    }
}

// ─── Day / Night overlay ──────────────────────────────────────────────────────
private fun DrawScope.drawDayNightOverlay(now: ZonedDateTime, w: Float, h: Float, c: WorldMapColors) {
    val utc      = now.withZoneSameInstant(ZoneOffset.UTC)
    val dayOfYear= utc.dayOfYear.toDouble()
    val hourDec  = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
    val declDeg  = -23.45 * cos(Math.toRadians(360.0 / 365.0 * (dayOfYear + 10.0)))
    val declRad  = Math.toRadians(declDeg)
    val subLon   = 180.0 - hourDec * 15.0

    val steps = 720
    val tLats = FloatArray(steps + 1)
    val tLons = FloatArray(steps + 1)
    for (i in 0..steps) {
        val lon  = -180.0 + i * (360.0 / steps)
        tLons[i] = lon.toFloat()
        val H    = Math.toRadians(lon - subLon)
        val td   = tan(declRad)
        tLats[i] = if (abs(td) < 0.001) 0f
                   else Math.toDegrees(atan(-cos(H) / td)).toFloat().coerceIn(-89f, 89f)
    }

    val nightPath = Path()
    if (declDeg < 0) {
        nightPath.moveTo(0f, 0f); nightPath.lineTo(w, 0f)
        for (i in steps downTo 0) { val p = latLonToOffset(tLats[i], tLons[i], w, h); nightPath.lineTo(p.x, p.y) }
    } else {
        nightPath.moveTo(0f, h); nightPath.lineTo(w, h)
        for (i in steps downTo 0) { val p = latLonToOffset(tLats[i], tLons[i], w, h); nightPath.lineTo(p.x, p.y) }
    }
    nightPath.close()
    drawPath(nightPath, Color(0xFF010408).copy(alpha = c.nightOverlayAlpha))

    val termPath = Path(); var first = true
    for (i in 0..steps) {
        val p = latLonToOffset(tLats[i], tLons[i], w, h)
        if (first) { termPath.moveTo(p.x, p.y); first = false } else termPath.lineTo(p.x, p.y)
    }
    drawPath(termPath, Color(0xFFFF8C00).copy(alpha = 0.07f), style = Stroke(18.dp.toPx()))
    drawPath(termPath, Color(0xFFFFB347).copy(alpha = 0.12f), style = Stroke(9.dp.toPx()))
    drawPath(termPath, Color(0xFFFFE066).copy(alpha = 0.28f), style = Stroke(3.dp.toPx()))
    drawPath(termPath, Color(0xFFFFFFCC).copy(alpha = 0.40f), style = Stroke(1.2.dp.toPx()))
}

// ─── Location dots ────────────────────────────────────────────────────────────
private fun DrawScope.drawLocationDots(
    locations: List<WorldClockLocation>,
    selected: WorldClockLocation?,
    highlighted: Set<String>,
    selPulse: Float,
    hiPulse: Float,
    colors: WorldMapColors,
    w: Float, h: Float, zoom: Float,
) {
    val sa = (1f / sqrt(zoom)).coerceIn(0.22f, 1f)

    locations.forEach { loc ->
        val pos   = latLonToOffset(loc.latitude.toFloat(), loc.longitude.toFloat(), w, h)
        val isSel = selected?.zoneId == loc.zoneId && selected.city == loc.city
        val isHi  = highlighted.contains(loc.zoneId)

        when {
            isSel -> {
                drawCircle(colors.dotSelected, 5.0.dp.toPx() * sa, pos)
                drawCircle(Color.White, 2.0.dp.toPx() * sa, pos)
            }
            isHi -> {
                val r = (5.dp.toPx() + hiPulse * 8.dp.toPx()) * sa
                drawCircle(colors.dotHighlight.copy(alpha = 0.18f * (1f - hiPulse)), r, pos)
                drawCircle(colors.dotHighlight, 4.dp.toPx() * sa, pos)
                drawCircle(Color.White, 1.5.dp.toPx() * sa, pos)
            }
            else -> {
                drawCircle(Color.Black.copy(alpha = 0.12f),
                    2.5.dp.toPx() * sa, pos + Offset(0.4f * sa, 0.4f * sa))
                drawCircle(colors.dotDefault, 2.1.dp.toPx() * sa, pos)
                drawCircle(Color.White.copy(alpha = 0.45f),
                    0.85.dp.toPx() * sa, pos - Offset(0.3f * sa, 0.3f * sa))
            }
        }
    }
}

// ─── User GPS pin ─────────────────────────────────────────────────────────────
private fun DrawScope.drawUserPin(lat: Float, lon: Float, pulse: Float, w: Float, h: Float, zoom: Float) {
    val pos   = latLonToOffset(lat, lon, w, h)
    val sa    = (1f / sqrt(zoom)).coerceIn(0.22f, 1f)
    val green = Color(0xFF00E676)
    if (pulse > 0.01f)
        drawCircle(green.copy(alpha = (1f - pulse) * 0.35f), 28.dp.toPx() * pulse * sa, pos)
    drawCircle(green.copy(alpha = 0.28f), 10.dp.toPx() * sa, pos)
    drawCircle(green, 5.5.dp.toPx() * sa, pos)
    drawCircle(Color.White, 2.3.dp.toPx() * sa, pos)
    drawCircle(Color(0xFF00875A).copy(alpha = 0.5f), 5.5.dp.toPx() * sa, pos,
        style = Stroke(0.9.dp.toPx() * sa))
}
