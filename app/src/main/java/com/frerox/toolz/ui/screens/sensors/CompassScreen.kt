package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlin.math.cos
import kotlin.math.sin
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CompassScreen(
    viewModel: CompassViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    val animatedAzimuth by animateFloatAsState(
        targetValue = state.azimuth,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "Azimuth"
    )

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("COMPASS", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Orientation Display
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${state.azimuth.toInt()}°",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 80.sp, 
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = getDirectionLabel(state.azimuth).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.weight(1f))

            // Modern Compass Dial
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .drawBehind {
                        // Background glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFE91E63).copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.width / 1.5f
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Fixed indicators
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.width / 2
                    // North indicator (Top)
                    drawPath(
                        path = Path().apply {
                            moveTo(center.x, center.y - radius - 10.dp.toPx())
                            lineTo(center.x - 10.dp.toPx(), center.y - radius + 10.dp.toPx())
                            lineTo(center.x + 10.dp.toPx(), center.y - radius + 10.dp.toPx())
                            close()
                        },
                        color = Color(0xFFE91E63)
                    )
                }

                // Rotating Part
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(-animatedAzimuth)
                ) {
                    ModernCompassDial(state = state)
                }
                
                // Center Decoration
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Place, 
                            null, 
                            modifier = Modifier.size(24.dp), 
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Bottom Status Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (state.showQibla) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Place, null, tint = Color(0xFF4CAF50))
                                Spacer(Modifier.width(12.dp))
                                Text("Qibla Direction", fontWeight = FontWeight.Bold)
                            }
                            val qAngle = state.qiblaAngle
                            if (qAngle != null) {
                                Text("${qAngle.toInt()}°", fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                            } else {
                                Text("Locating...", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp).alpha(0.3f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val accuracyColor = when(state.accuracy) {
                                3 -> Color(0xFF4CAF50)
                                2 -> Color(0xFFFFC107)
                                else -> Color(0xFFF44336)
                            }
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accuracyColor))
                            Spacer(Modifier.width(12.dp))
                            Text("Sensor Accuracy", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            when(state.accuracy) {
                                3 -> "High"
                                2 -> "Medium"
                                else -> "Low / Unreliable"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            if (state.showQibla && !locationPermissionState.status.isGranted) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { locationPermissionState.launchPermissionRequest() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.LocationOn, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Grant Location for Qibla")
                }
            }
        }
    }
}

@Composable
fun ModernCompassDial(state: CompassState) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = center
        val radius = size.width / 2
        val innerRadius = radius - 40.dp.toPx()
        
        // Circular lines
        drawCircle(
            color = onSurface.copy(alpha = 0.1f),
            radius = radius,
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = onSurface.copy(alpha = 0.05f),
            radius = innerRadius,
            style = Stroke(width = 1.dp.toPx())
        )

        // Ticks and Labels
        for (i in 0 until 360 step 2) {
            val angleRad = Math.toRadians(i.toDouble() - 90).toFloat()
            val isMain = i % 30 == 0
            val isCardinal = i % 90 == 0
            
            val tickLength = when {
                isCardinal -> 24.dp.toPx()
                isMain -> 16.dp.toPx()
                else -> 8.dp.toPx()
            }
            
            val color = when {
                isCardinal -> primaryColor
                isMain -> onSurface
                else -> onSurface.copy(alpha = 0.2f)
            }

            val start = Offset(
                center.x + (radius - tickLength) * cos(angleRad),
                center.y + (radius - tickLength) * sin(angleRad)
            )
            val end = Offset(
                center.x + radius * cos(angleRad),
                center.y + radius * sin(angleRad)
            )
            
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = if (isMain) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            if (isCardinal) {
                val label = when(i) {
                    0 -> "N"
                    90 -> "E"
                    180 -> "S"
                    270 -> "W"
                    else -> ""
                }
                // We'd use drawText here, but for simplicity in this Canvas, 
                // cardinal points are handled by fixed overlays or icons usually.
                // Instead, let's draw a small circle for cardinal points
                drawCircle(
                    color = color,
                    radius = 4.dp.toPx(),
                    center = Offset(
                        center.x + (radius - 40.dp.toPx()) * cos(angleRad),
                        center.y + (radius - 40.dp.toPx()) * sin(angleRad)
                    )
                )
            }
        }

        // Qibla Marker
        val qiblaAngle = state.qiblaAngle
        if (state.showQibla && qiblaAngle != null) {
            val qAngle = Math.toRadians(qiblaAngle.toDouble() - 90).toFloat()
            val qRadius = radius - 20.dp.toPx()
            
            // Draw Qibla Arrow
            rotate(degrees = qiblaAngle, pivot = center) {
                val arrowPath = Path().apply {
                    moveTo(center.x, center.y - radius + 30.dp.toPx())
                    lineTo(center.x - 10.dp.toPx(), center.y - radius + 50.dp.toPx())
                    lineTo(center.x + 10.dp.toPx(), center.y - radius + 50.dp.toPx())
                    close()
                }
                drawPath(arrowPath, Color(0xFF4CAF50))
            }
        }
    }
}

private fun getDirectionLabel(azimuth: Float): String {
    return when (azimuth) {
        in 337.5..360.0, in 0.0..22.5 -> "North"
        in 22.5..67.5 -> "North East"
        in 67.5..112.5 -> "East"
        in 112.5..157.5 -> "South East"
        in 157.5..202.5 -> "South"
        in 202.5..247.5 -> "South West"
        in 247.5..292.5 -> "West"
        in 292.5..337.5 -> "North West"
        else -> "North"
    }
}
