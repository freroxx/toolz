package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CompassScreen(
    viewModel: CompassViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Using a more responsive spring for the dial's rotation
    val animatedAzimuth by animateFloatAsState(
        targetValue = state.azimuth,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "Azimuth"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("COMPASS", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = surfaceColor
    ) { padding ->
        val config = LocalConfiguration.current
        val screenWidth = config.screenWidthDp.dp
        val dialSize = screenWidth * 0.85f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Display - Clean Material 3 Expressive
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 48.dp)
                ) {
                    Text(
                        text = "${state.azimuth.toInt()}°",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 86.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-2).sp
                        ),
                        color = onSurface
                    )
                    
                    Surface(
                        color = primaryColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = getDirectionLabel(state.azimuth).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryColor,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                // Simplified Compass Dial
                Box(
                    modifier = Modifier
                        .size(dialSize)
                        .drawBehind {
                            // Subtle glow behind the dial
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = 0.05f), Color.Transparent),
                                    radius = size.width / 1.5f
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Static Indicator (Fixed at North)
                    Canvas(modifier = Modifier.size(dialSize + 40.dp)) {
                        // Top center fixed triangle
                        val path = Path().apply {
                            moveTo(size.width / 2, 0f)
                            lineTo(size.width / 2 - 12.dp.toPx(), 24.dp.toPx())
                            lineTo(size.width / 2 + 12.dp.toPx(), 24.dp.toPx())
                            close()
                        }
                        drawPath(path, primaryColor)
                    }

                    // Rotating Dial
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(-animatedAzimuth)
                    ) {
                        MinimalCompassDial(
                            onSurface = onSurface,
                            primaryColor = primaryColor
                        )
                    }

                    // Center Bubble Level / Stability Indicator
                    Surface(
                        modifier = Modifier
                            .size(dialSize * 0.25f)
                            .shadow(24.dp, CircleShape, spotColor = primaryColor.copy(alpha = 0.5f)),
                        shape = CircleShape,
                        color = surfaceColor,
                        tonalElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, onSurface.copy(alpha = 0.1f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Navigation,
                                null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .alpha(if (state.accuracy >= 2) 1f else 0.3f),
                                tint = primaryColor
                            )
                        }
                    }
                }

                Spacer(Modifier.height(64.dp))

                // Accuracy & Stability Info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccuracyIndicator(accuracy = state.accuracy, onSurface = onSurface)
                    
                    if (state.showQibla) {
                        QiblaSmallIndicator(
                            qiblaAngle = state.qiblaAngle,
                            isLocationGranted = locationPermissionState.status.isGranted,
                            onRequestPermission = { locationPermissionState.launchPermissionRequest() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccuracyIndicator(accuracy: Int, onSurface: Color) {
    val (label, color) = when (accuracy) {
        3 -> "HIGH ACCURACY" to Color(0xFF4CAF50)
        2 -> "MEDIUM ACCURACY" to Color(0xFFFFC107)
        else -> "CALIBRATION NEEDED" to Color(0xFFF44336)
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = onSurface.copy(alpha = 0.6f),
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun QiblaSmallIndicator(
    qiblaAngle: Float?,
    isLocationGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    val qiblaColor = Color(0xFF4CAF50)
    
    if (!isLocationGranted) {
        TextButton(onClick = onRequestPermission) {
            Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(18.dp), tint = qiblaColor)
            Spacer(Modifier.width(8.dp))
            Text("ENABLE QIBLA", color = qiblaColor, fontWeight = FontWeight.Bold)
        }
    } else if (qiblaAngle != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Place, null, modifier = Modifier.size(20.dp), tint = qiblaColor)
            Spacer(Modifier.width(8.dp))
            Text(
                "QIBLA: ${qiblaAngle.toInt()}°", 
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = qiblaColor
            )
        }
    }
}

@Composable
fun MinimalCompassDial(
    onSurface: Color,
    primaryColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = center
        val radius = size.width / 2
        
        // Background ring
        drawCircle(
            color = onSurface.copy(alpha = 0.04f),
            radius = radius,
            style = Stroke(width = 1.dp.toPx())
        )

        // Main Ticks
        for (i in 0 until 360 step 5) {
            val angleRad = Math.toRadians(i.toDouble() - 90).toFloat()
            val isMain = i % 30 == 0
            val isCardinal = i % 90 == 0
            
            val tickLength = if (isCardinal) 28.dp.toPx() else if (isMain) 18.dp.toPx() else 8.dp.toPx()
            val strokeWidth = if (isMain) 3.dp.toPx() else 1.5.dp.toPx()
            val alphaValue = if (isMain) 0.8f else 0.2f
            
            val start = Offset(
                center.x + (radius - tickLength) * cos(angleRad),
                center.y + (radius - tickLength) * sin(angleRad)
            )
            val end = Offset(
                center.x + radius * cos(angleRad),
                center.y + radius * sin(angleRad)
            )
            
            drawLine(
                color = if (isCardinal && i == 0) primaryColor else onSurface.copy(alpha = alphaValue),
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
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
