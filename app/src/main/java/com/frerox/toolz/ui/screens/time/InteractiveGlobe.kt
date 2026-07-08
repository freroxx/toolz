package com.frerox.toolz.ui.screens.time

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun InteractiveGlobe(
    selectedLocation: WorldClockLocation?,
    locations: List<WorldClockLocation>,
    highlightedZones: Set<String>,
    modifier: Modifier = Modifier,
    onLocationSelected: (WorldClockLocation) -> Unit,
) {
    val scope = rememberCoroutineScope()
    
    // Rotation state: X is around the Y-axis (longitude), Y is around the X-axis (latitude)
    val rotationX = remember { Animatable(0f) }
    val rotationY = remember { Animatable(0f) }

    var isDragging by remember { mutableStateOf(false) }

    // Auto-rotate to selected location
    LaunchedEffect(selectedLocation) {
        if (selectedLocation != null && !isDragging) {
            // Longitude to RotationX: -180..180 -> -PI..PI (but we need to invert or adjust for view)
            // Latitude to RotationY: -90..90 -> -PI/2..PI/2
            val targetX = Math.toRadians(-selectedLocation.longitude).toFloat()
            val targetY = Math.toRadians(selectedLocation.latitude).toFloat()
            
            launch {
                rotationX.animateTo(
                    targetValue = targetX,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                )
            }
            launch {
                rotationY.animateTo(
                    targetValue = targetY,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                )
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val radius = minOf(maxWidth, maxHeight).value * 0.45f
        val center = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaX = dragAmount.x / radius
                            val deltaY = -dragAmount.y / radius
                            
                            scope.launch {
                                rotationX.snapTo(rotationX.value + deltaX)
                                rotationY.snapTo((rotationY.value + deltaY).coerceIn(-PI.toFloat() / 2, PI.toFloat() / 2))
                            }
                        }
                    )
                }
        ) {
            drawGlobe(
                center = center,
                radius = radius,
                rotX = rotationX.value,
                rotY = rotationY.value,
                locations = locations,
                selected = selectedLocation,
                highlightedZones = highlightedZones
            )
        }
    }
}

private fun DrawScope.drawGlobe(
    center: Offset,
    radius: Float,
    rotX: Float,
    rotY: Float,
    locations: List<WorldClockLocation>,
    selected: WorldClockLocation?,
    highlightedZones: Set<String>
) {
    // Ocean background
    val ocean = Brush.radialGradient(
        0.0f to Color(0xFF1A237E),
        0.7f to Color(0xFF0D47A1),
        1.0f to Color(0xFF01579B),
        center = center,
        radius = radius
    )
    drawCircle(brush = ocean, radius = radius, center = center)

    // Graticules (lat/lon lines)
    val graticuleColor = Color.White.copy(alpha = 0.15f)
    for (lat in -90..90 step 30) {
        drawLatLine(center, radius, Math.toRadians(lat.toDouble()).toFloat(), rotX, rotY, graticuleColor)
    }
    for (lon in 0..360 step 30) {
        drawLonLine(center, radius, Math.toRadians(lon.toDouble()).toFloat(), rotX, rotY, graticuleColor)
    }

    // Simplified continents (placeholder for actual geometry if available, 
    // but here we'll use dots for major cities to give it a "tech" look)
    locations.forEach { loc ->
        drawLocation(center, radius, loc, rotX, rotY, selected, highlightedZones)
    }

    // Atmospheric glow
    drawCircle(
        brush = Brush.radialGradient(
            0.85f to Color.Transparent,
            1.0f to Color(0xFF64B5F6).copy(alpha = 0.3f),
            center = center,
            radius = radius * 1.05f
        ),
        radius = radius * 1.05f,
        center = center
    )
    
    // Rim highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.2f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.dp.toPx())
    )
}

private fun DrawScope.drawLatLine(center: Offset, radius: Float, lat: Float, rotX: Float, rotY: Float, color: Color) {
    val path = Path()
    var first = true
    for (lon in 0..360 step 10) {
        val p = project(radius, Math.toRadians(lon.toDouble()).toFloat(), lat, rotX, rotY)
        if (p.z > 0) {
            val screenPos = center + Offset(p.x, p.y)
            if (first) path.moveTo(screenPos.x, screenPos.y) else path.lineTo(screenPos.x, screenPos.y)
            first = false
        } else {
            first = true
        }
    }
    drawPath(path, color, style = Stroke(width = 0.5.dp.toPx()))
}

private fun DrawScope.drawLonLine(center: Offset, radius: Float, lon: Float, rotX: Float, rotY: Float, color: Color) {
    val path = Path()
    var first = true
    for (lat in -90..90 step 5) {
        val p = project(radius, lon, Math.toRadians(lat.toDouble()).toFloat(), rotX, rotY)
        if (p.z > 0) {
            val screenPos = center + Offset(p.x, p.y)
            if (first) path.moveTo(screenPos.x, screenPos.y) else path.lineTo(screenPos.x, screenPos.y)
            first = false
        } else {
            first = true
        }
    }
    drawPath(path, color, style = Stroke(width = 0.5.dp.toPx()))
}

private fun DrawScope.drawLocation(
    center: Offset,
    radius: Float,
    loc: WorldClockLocation,
    rotX: Float,
    rotY: Float,
    selected: WorldClockLocation?,
    highlightedZones: Set<String>
) {
    val p = project(
        radius,
        Math.toRadians(loc.longitude).toFloat(),
        Math.toRadians(loc.latitude).toFloat(),
        rotX,
        rotY
    )

    if (p.z > 0) {
        val screenPos = center + Offset(p.x, p.y)
        val isSelected = selected?.zoneId == loc.zoneId && selected.city == loc.city
        val isHighlighted = highlightedZones.contains(loc.zoneId)
        
        val color = when {
            isSelected -> Color(0xFFFFD166)
            isHighlighted -> Color(0xFF7DE2D1)
            else -> Color.White.copy(alpha = 0.6f)
        }
        
        val pointSize = when {
            isSelected -> 6.dp.toPx()
            isHighlighted -> 4.dp.toPx()
            else -> 2.dp.toPx()
        }

        if (isSelected) {
            drawCircle(color.copy(alpha = 0.3f), pointSize * 2.5f, screenPos)
        }
        drawCircle(color, pointSize, screenPos)
    }
}

private data class Point3D(val x: Float, val y: Float, val z: Float)

private fun project(radius: Float, lon: Float, lat: Float, rotX: Float, rotY: Float): Point3D {
    // Standard spherical coordinates to Cartesian
    // lon is longitude, lat is latitude
    // x = r * cos(lat) * sin(lon)
    // y = r * sin(lat)
    // z = r * cos(lat) * cos(lon)
    
    // We apply rotations
    // Rotation around Y (longitude shift): newLon = lon + rotX
    // Rotation around X (latitude shift): 
    
    val rotatedLon = lon + rotX
    
    var x = radius * cos(lat) * sin(rotatedLon)
    var y = radius * sin(lat)
    var z = radius * cos(lat) * cos(rotatedLon)
    
    // Rotate around X axis for rotY
    val ry = y * cos(rotY) - z * sin(rotY)
    val rz = y * sin(rotY) + z * cos(rotY)
    
    return Point3D(x, ry, rz)
}
