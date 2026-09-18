/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

    // ── Vector land polygons ────────────────────────────────────────────────
    // W-P1-02: parse once to NORMALIZED coords (0..1), then scale per size.
    // Was: split+toFloatOrNull on every size change.
    var normalizedPolys by remember { mutableStateOf<List<List<Pair<Float, Float>>>?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val text = try {
                    context.assets.open("world_map.txt").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    "-180,-90,180,-90,180,-65,-180,-65,-180,-90|" +
                    "-80,-55,-40,-10,-50,10,-80,10,-80,-55|" +
                    "-170,70,-60,70,-60,10,-100,15,-170,70|" +
                    "-20,35,50,35,50,-35,10,-35,-20,35|" +
                    "-10,70,180,70,180,10,-10,10,-10,70|" +
                    "110,-10,155,-10,155,-45,110,-45,110,-10"
                }
                val polys = ArrayList<List<Pair<Float, Float>>>()
                text.split("|").forEach { poly ->
                    val cs = poly.split(",")
                    if (cs.size >= 2) {
                        val pts = ArrayList<Pair<Float, Float>>()
                        var i = 0
                        while (i < cs.size - 1) {
                            val lon = cs[i].toFloatOrNull()
                            val lat = cs[i + 1].toFloatOrNull()
                            if (lon != null && lat != null) {
                                // Normalized: x=(lon+180)/360, y=(90-lat)/180
                                pts.add(((lon + 180f) / 360f) to ((90f - lat) / 180f))
                            }
                            i += 2
                        }
                        if (pts.size >= 2) polys.add(pts)
                    }
                }
                normalizedPolys = polys
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    // Scale normalized polys to current map size — remembered, not rebuilt per frame.
    // NOTE: derived vectorPath/terminator/dotNorm are declared after canvasSize/now
    // below (they depend on them). See "Derived remembered paths" section.

    // ── Satellite bitmap — equirectangular NASA Blue Marble ───────────────────
    // W-P1-02: subsampled decode (inSampleSize) to view size, recycle on replace/
    // dispose, skip auto-download on metered (cache only).
    var satelliteBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        if (satelliteBitmap == null) {
            withContext(Dispatchers.IO) {
                val month = java.time.YearMonth.now().toString()
                val cacheFile = java.io.File(context.cacheDir, "satellite_$month.jpg")

                if (cacheFile.exists()) {
                    try {
                        satelliteBitmap = decodeSampled(cacheFile.absolutePath, reqWidth = 1024)
                        if (satelliteBitmap != null) return@withContext
                    } catch (e: Exception) { /* fall through to download */ }
                }

                context.cacheDir.listFiles()?.forEach {
                    if (it.name.startsWith("satellite_") && it.name != cacheFile.name) {
                        runCatching { it.delete() }
                    }
                }

                // Metered guard: never auto-fetch on metered networks (W-P1-02).
                if (isMetered(context)) return@withContext

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
                            satelliteBitmap = decodeSampled(cacheFile.absolutePath, reqWidth = 1024)
                            break
                        }
                    } catch (e: Exception) { /* try next URL */ }
                }
            }
        }
    }

    // W-P1-02: recycle bitmap when replaced or disposed (was never recycled).
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            satelliteBitmap?.recycle()
            satelliteBitmap = null
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
    // W-P1-02: pulse tied to composition visibility — LaunchedEffect cancels on
    // dispose automatically; loop guards isActive so off-screen dispose stops it.
    LaunchedEffect(userLatLon) {
        if (userLatLon != null) {
            try {
                while (true) {
                    pulsAnim.snapTo(0f)
                    pulsAnim.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
                    delay(2800)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        }
    }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(60_000L) } }

    // ── Derived remembered paths (W-P1-02: no per-frame alloc) ───────────────
    val vectorPath: Path? = remember(normalizedPolys, canvasSize) {
        val polys = normalizedPolys ?: return@remember null
        val cW = canvasSize.width; val cH = canvasSize.height
        if (cW <= 0f || cH <= 0f) return@remember null
        val (w, h) = getMapDimensions(cW, cH)
        val p = Path()
        for (poly in polys) {
            var first = true
            for ((nx, ny) in poly) {
                val x = nx * w; val y = ny * h
                if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
            }
            p.close()
        }
        p
    }
    val nowMinute = remember(now) { now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES) }
    val terminator: TerminatorPaths? = remember(nowMinute, canvasSize) {
        val cW = canvasSize.width; val cH = canvasSize.height
        if (cW <= 0f || cH <= 0f) return@remember null
        val (w, h) = getMapDimensions(cW, cH)
        computeTerminator(nowMinute, w, h, steps = 240)
    }
    val dotNorm: List<Pair<Float, Float>> = remember(locations) {
        locations.map { loc ->
            ((loc.longitude.toFloat() + 180f) / 360f) to ((90f - loc.latitude.toFloat()) / 180f)
        }
    }

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

                            val newZoom = (gs.zoom * zoomChange).coerceIn(1f, 15f)
                            val f       = newZoom / gs.zoom

                            // Zoom around centroid + apply pan delta
                            val rawPanX = gs.panX * f + (centroid.x - cx) * (1f - f) + panChange.x
                            val rawPanY = gs.panY * f + (centroid.y - cy) * (1f - f) + panChange.y

                            // W-P1-02: simplified clamp (was fragile maxPX/clampPX math).
                            // Symmetric bounds: scaled map larger than canvas -> allow edge-to-edge
                            // pan; smaller -> lock centered (pan 0).
                            val scaledW = mW * newZoom
                            val scaledH = mH * newZoom
                            val maxPanX = if (scaledW > cW) (scaledW - cW) / 2f else 0f
                            val maxPanY = if (scaledH > cH) (scaledH - cH) / 2f else 0f

                            gs.zoom = newZoom
                            gs.panX = rawPanX.coerceIn(-maxPanX, maxPanX)
                            gs.panY = rawPanY.coerceIn(-maxPanY, maxPanY)

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
                                style = Stroke(0.8.dp.toPx() / zoom.coerceAtLeast(0.1f)))
                        }
                    }

                    // W-P1-02: precomputed terminator paths (remembered, no per-frame alloc).
                    terminator?.let { t ->
                        drawPath(t.nightPath, Color(0xFF010408).copy(alpha = colors.nightOverlayAlpha))
                        // Single glow stroke (was 4 full-width strokes per draw).
                        drawPath(t.termPath, Color(0xFFFFB347).copy(alpha = 0.22f),
                            style = Stroke(3.dp.toPx()))
                    }

                    drawLocationDots(
                        locations   = locations,
                        dotNorm     = dotNorm,
                        selected    = selectedLocation,
                        highlighted = highlightedZones,
                        selPulse    = selAnim.value,
                        hiPulse     = hiAnim.value,
                        colors      = colors,
                        w = w, h = h, zoom = zoom,
                        canvasW = canvasW, canvasH = canvasH,
                        panX = panX, panY = panY,
                    )

                    userLatLon?.let { (lat, lon) ->
                        drawUserPin(lat.toFloat(), lon.toFloat(), pulsAnim.value, w, h, zoom)
                    }
                }
            }
        }
    }

    // ── Build vector path on background thread ────────────────────────────────
    // NOTE: vector is parsed once to normalized coords above (LaunchedEffect(Unit));
    // vectorPath is derived via remember(normalizedPolys, canvasSize). No per-size re-parse.
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
// W-P1-02: precomputed off-draw (remembered on minute+size). No per-frame alloc:
// no FloatArray/Path/dp.toPx in draw. 240 steps, single glow stroke.

private data class TerminatorPaths(val nightPath: Path, val termPath: Path)

private fun computeTerminator(now: ZonedDateTime, w: Float, h: Float, steps: Int = 240): TerminatorPaths {
    val utc = now.withZoneSameInstant(ZoneOffset.UTC)
    val dayOfYear = utc.dayOfYear.toDouble()
    val hourDec = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
    val declDeg = -23.45 * cos(Math.toRadians(360.0 / 365.0 * (dayOfYear + 10.0)))
    val declRad = Math.toRadians(declDeg)
    val subLon = 180.0 - hourDec * 15.0

    // Stack-allocated lists (computed once per minute, not per frame).
    val tLats = ArrayList<Float>(steps + 1)
    val tLons = ArrayList<Float>(steps + 1)
    for (i in 0..steps) {
        val lon = -180.0 + i * (360.0 / steps)
        tLons.add(lon.toFloat())
        val hAngle = Math.toRadians(lon - subLon)
        val td = tan(declRad)
        tLats.add(
            if (abs(td) < 0.001) 0f
            else Math.toDegrees(atan(-cos(hAngle) / td)).toFloat().coerceIn(-89f, 89f)
        )
    }

    val nightPath = Path()
    if (declDeg < 0) {
        nightPath.moveTo(0f, 0f); nightPath.lineTo(w, 0f)
        for (i in steps downTo 0) {
            nightPath.lineTo((tLons[i] + 180f) / 360f * w, (90f - tLats[i]) / 180f * h)
        }
    } else {
        nightPath.moveTo(0f, h); nightPath.lineTo(w, h)
        for (i in steps downTo 0) {
            nightPath.lineTo((tLons[i] + 180f) / 360f * w, (90f - tLats[i]) / 180f * h)
        }
    }
    nightPath.close()

    val termPath = Path()
    for (i in 0..steps) {
        val x = (tLons[i] + 180f) / 360f * w
        val y = (90f - tLats[i]) / 180f * h
        if (i == 0) termPath.moveTo(x, y) else termPath.lineTo(x, y)
    }
    return TerminatorPaths(nightPath, termPath)
}

// ─── Location dots ────────────────────────────────────────────────────────────
// FIX (zoom/fullscreen drift): dots are drawn INSIDE the world-space
// withTransform (translate pan + scale zoom + centering offset), so they MUST
// be positioned in MAP-SPACE (nx*w, ny*h) and let the transform place them.
// Computing screen coords manually here double-applies zoom/pan (dots flew off
// the map when zooming or in fullscreen). Screen-space math lives ONLY in the
// tap handler (hit-testing), never in draw.
private fun DrawScope.drawLocationDots(
    locations: List<WorldClockLocation>,
    dotNorm: List<Pair<Float, Float>>,
    selected: WorldClockLocation?,
    highlighted: Set<String>,
    selPulse: Float,
    hiPulse: Float,
    colors: WorldMapColors,
    w: Float, h: Float, zoom: Float,
    canvasW: Float, canvasH: Float,
    panX: Float, panY: Float,
) {
    val sa = (1f / sqrt(zoom)).coerceIn(0.22f, 1f)
    // Hoist density conversions — one call each per draw (was ~1600/frame).
    val rSel = 5.0.dp.toPx() * sa
    val rSelInner = 2.0.dp.toPx() * sa
    val rHi = 4.dp.toPx() * sa
    val rHiInner = 1.5.dp.toPx() * sa
    val rHiGlowBase = 5.dp.toPx() * sa
    val rHiGlowExtra = 8.dp.toPx() * sa
    val rDot = 2.1.dp.toPx() * sa
    val rDotHi = 0.85.dp.toPx() * sa
    val shadowR = 2.5.dp.toPx() * sa
    val shadowOff = Offset(0.4f * sa, 0.4f * sa)
    val hiOff = Offset(0.3f * sa, 0.3f * sa)
    val cx = canvasW / 2f; val cy = canvasH / 2f
    val mapOffX = (canvasW - w) / 2f
    val mapOffY = (canvasH - h) / 2f
    val lowZoom = zoom < 1.5f

    for (i in locations.indices) {
        val loc = locations[i]
        val (nx, ny) = dotNorm[i]
        // Map-space position — the enclosing withTransform applies
        // centering + zoom/pivot + pan. Do NOT pre-apply them here.
        val pos = Offset(nx * w, ny * h)
        // Cull in screen-space (cheap visibility check only, never for drawing).
        val mapX = nx * w + mapOffX
        val mapY = ny * h + mapOffY
        val screenX = zoom * (mapX - cx) + cx + panX
        val screenY = zoom * (mapY - cy) + cy + panY
        if (screenX < -24f || screenX > canvasW + 24f || screenY < -24f || screenY > canvasH + 24f) continue
        val isSel = selected?.zoneId == loc.zoneId && selected.city == loc.city
        val isHi = highlighted.contains(loc.zoneId)

        when {
            isSel -> {
                drawCircle(colors.dotSelected, rSel, pos)
                drawCircle(Color.White, rSelInner, pos)
            }
            isHi -> {
                val r = rHiGlowBase + hiPulse * rHiGlowExtra
                drawCircle(colors.dotHighlight.copy(alpha = 0.18f * (1f - hiPulse)), r, pos)
                drawCircle(colors.dotHighlight, rHi, pos)
                drawCircle(Color.White, rHiInner, pos)
            }
            lowZoom -> {
                // Clustered fast path: single circle, alpha-faded at low zoom.
                drawCircle(colors.dotDefault.copy(alpha = 0.75f), rDot, pos)
            }
            else -> {
                drawCircle(Color.Black.copy(alpha = 0.12f),
                    shadowR, pos + shadowOff)
                drawCircle(colors.dotDefault, rDot, pos)
                drawCircle(Color.White.copy(alpha = 0.45f),
                    rDotHi, pos - hiOff)
            }
        }
    }
}

// ─── User GPS pin ─────────────────────────────────────────────────────────────
private fun DrawScope.drawUserPin(lat: Float, lon: Float, pulse: Float, w: Float, h: Float, zoom: Float) {
    val pos   = latLonToOffset(lat, lon, w, h)
    val sa    = (1f / sqrt(zoom)).coerceIn(0.22f, 1f)
    val green = Color(0xFF00E676)
    // Hoisted conversions (was 5 dp.toPx per frame — now computed once each).
    val rPulse = 28.dp.toPx() * sa
    val rHalo = 10.dp.toPx() * sa
    val rDot = 5.5.dp.toPx() * sa
    val rInner = 2.3.dp.toPx() * sa
    val stroke = 0.9.dp.toPx() * sa
    if (pulse > 0.01f)
        drawCircle(green.copy(alpha = (1f - pulse) * 0.35f), rPulse * pulse, pos)
    drawCircle(green.copy(alpha = 0.28f), rHalo, pos)
    drawCircle(green, rDot, pos)
    drawCircle(Color.White, rInner, pos)
    drawCircle(Color(0xFF00875A).copy(alpha = 0.5f), rDot, pos,
        style = Stroke(stroke))
}

// ─── Satellite helpers (W-P1-02: Coil-style subsample + metered guard) ───────
// Coil is used for remote images elsewhere; here we keep the manual disk cache
// (monthly NASA URL) but decode subsampled to the view (no 16-25MB full-res
// ARGB hold) and never auto-fetch on metered networks.

private fun isMetered(context: android.content.Context): Boolean {
    return runCatching {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        cm.isActiveNetworkMetered
    }.getOrDefault(false)
}

private fun decodeSampled(path: String, reqWidth: Int = 1024): Bitmap? {
    return runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val outW = bounds.outWidth
        if (outW > reqWidth && outW > 0) {
            var half = outW / 2
            while (half / sample >= reqWidth) sample *= 2
        }
        val opts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        android.graphics.BitmapFactory.decodeFile(path, opts)
    }.getOrNull()
}
