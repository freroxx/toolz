package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import com.frerox.toolz.ui.components.fadingEdge
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text("COMPASS", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .fadingEdge(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.05f to Color.Black,
                    0.95f to Color.Black,
                    1f to Color.Transparent
                ),
                length = 24.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = "${state.azimuth.toInt()}°",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 86.sp, 
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-4).sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = getDirectionLabel(state.azimuth).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            letterSpacing = 2.sp
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))

                // Premium Compass Dial
                Box(
                    modifier = Modifier
                        .size(340.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.08f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = size.width / 1.2f
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Outer decorative ring
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = onSurface.copy(alpha = 0.05f),
                            radius = size.width / 2,
                            style = Stroke(width = 2.dp.toPx())
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
                    
                    // Fixed Center needle / Indicator
                    Canvas(modifier = Modifier.size(40.dp)) {
                        val path = Path().apply {
                            moveTo(size.width / 2, 0f)
                            lineTo(size.width, size.height)
                            lineTo(size.width / 2, size.height * 0.8f)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path, Color(0xFFE91E63))
                    }
                    
                    // Glass effect cover
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        tonalElevation = 12.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(Modifier.height(48.dp))

                // Stats Cards
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SensorInfoCard(
                        modifier = Modifier.weight(1f),
                        label = "ACCURACY",
                        value = when(state.accuracy) {
                            3 -> "HIGH"
                            2 -> "MEDIUM"
                            else -> "LOW"
                        },
                        icon = Icons.Rounded.PrecisionManufacturing,
                        color = when(state.accuracy) {
                            3 -> Color(0xFF4CAF50)
                            2 -> Color(0xFFFFC107)
                            else -> Color(0xFFF44336)
                        }
                    )
                    SensorInfoCard(
                        modifier = Modifier.weight(1f),
                        label = "TILT",
                        value = String.format(Locale.getDefault(), "%.1f°", 0f),
                        icon = Icons.Rounded.ScreenRotation,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (state.showQibla) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Place, null, tint = Color(0xFF4CAF50))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("QIBLA DIRECTION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                                val qAngle = state.qiblaAngle
                                Text(
                                    if (qAngle != null) "${qAngle.toInt()}° North-East" else "LOCATING...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!locationPermissionState.status.isGranted) {
                                Button(
                                    onClick = { locationPermissionState.launchPermissionRequest() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) {
                                    Text("GRANT", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun SensorInfoCard(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
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
        
        // Ticks and Labels
        for (i in 0 until 360 step 2) {
            val angleRad = Math.toRadians(i.toDouble() - 90).toFloat()
            val isMain = i % 30 == 0
            val isCardinal = i % 90 == 0
            
            val tickLength = when {
                isCardinal -> 28.dp.toPx()
                isMain -> 18.dp.toPx()
                else -> 10.dp.toPx()
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
                strokeWidth = if (isMain) 3.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            if (isCardinal) {
                drawCircle(
                    color = color,
                    radius = 4.dp.toPx(),
                    center = Offset(
                        center.x + (radius - 50.dp.toPx()) * cos(angleRad),
                        center.y + (radius - 50.dp.toPx()) * sin(angleRad)
                    )
                )
            }
        }

        // Qibla Marker
        val qiblaAngle = state.qiblaAngle
        if (state.showQibla && qiblaAngle != null) {
            rotate(degrees = qiblaAngle, pivot = center) {
                val arrowPath = Path().apply {
                    moveTo(center.x, center.y - radius + 30.dp.toPx())
                    lineTo(center.x - 12.dp.toPx(), center.y - radius + 55.dp.toPx())
                    lineTo(center.x + 12.dp.toPx(), center.y - radius + 55.dp.toPx())
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
