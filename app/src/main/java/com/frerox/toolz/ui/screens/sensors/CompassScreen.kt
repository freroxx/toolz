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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

            // Compass Dial
            Box(
                modifier = Modifier.size(320.dp),
                contentAlignment = Alignment.Center
            ) {
                CompassDial(rotation = -animatedAzimuth, state = state)
                
                // Fixed Reference Marker
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(4.dp, 24.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )
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
fun CompassDial(rotation: Float, state: CompassState) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        rotate(rotation) {
            val center = center
            val radius = size.width / 2
            
            // Outer Ring
            drawCircle(
                color = onSurface.copy(alpha = 0.05f),
                radius = radius,
                center = center
            )

            // Tick Marks
            for (i in 0 until 360 step 5) {
                val isMajor = i % 30 == 0
                val length = if (isMajor) 15.dp.toPx() else 8.dp.toPx()
                val thickness = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                val color = if (isMajor) onSurface else onSurface.copy(alpha = 0.3f)
                
                val angleRad = Math.toRadians(i.toDouble() - 90).toFloat()
                val start = Offset(
                    center.x + (radius - length) * cos(angleRad),
                    center.y + (radius - length) * sin(angleRad)
                )
                val end = Offset(
                    center.x + radius * cos(angleRad),
                    center.y + radius * sin(angleRad)
                )
                drawLine(color, start, end, thickness)
            }

            // Cardinal Points (Simple drawing)
            val nAngle = Math.toRadians(-90.0).toFloat()
            val nPath = Path().apply {
                val x = center.x + (radius - 40.dp.toPx()) * cos(nAngle)
                val y = center.y + (radius - 40.dp.toPx()) * sin(nAngle)
                moveTo(x, y - 10.dp.toPx())
                lineTo(x + 8.dp.toPx(), y + 10.dp.toPx())
                lineTo(x - 8.dp.toPx(), y + 10.dp.toPx())
                close()
            }
            drawPath(nPath, Color.Red)
            
            // Qibla Marker
            val qiblaAngle = state.qiblaAngle
            if (state.showQibla && qiblaAngle != null) {
                val qAngle = Math.toRadians(qiblaAngle.toDouble() - 90).toFloat()
                val qRadius = radius - 60.dp.toPx()
                drawCircle(
                    color = Color(0xFF4CAF50),
                    radius = 8.dp.toPx(),
                    center = Offset(
                        center.x + qRadius * cos(qAngle),
                        center.y + qRadius * sin(qAngle)
                    )
                )
                // Line to Qibla
                drawLine(
                    color = Color(0xFF4CAF50).copy(alpha = 0.3f),
                    start = center,
                    end = Offset(
                        center.x + (radius - 20.dp.toPx()) * cos(qAngle),
                        center.y + (radius - 20.dp.toPx()) * sin(qAngle)
                    ),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        
        // Inner Glow / Circle
        drawCircle(
            brush = Brush.radialGradient(
                listOf(primaryColor.copy(alpha = 0.1f), Color.Transparent),
                center = center,
                radius = 100.dp.toPx()
            ),
            radius = 100.dp.toPx(),
            center = center
        )
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
