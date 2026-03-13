package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlin.math.abs
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
    val view = LocalView.current

    val animatedAzimuth by animateFloatAsState(
        targetValue = state.azimuth,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "Azimuth"
    )

    // Haptic feedback for North and Qibla
    LaunchedEffect(state.azimuth.toInt()) {
        val currentAzimuth = state.azimuth
        val qiblaAngle = state.qiblaAngle
        if (state.azimuth.toInt() == 0 || (qiblaAngle != null && abs(currentAzimuth - qiblaAngle) < 1f)) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

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
        val dialSize = (config.screenWidthDp.dp * 0.85f).coerceAtMost(400.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Material 3 Expressive Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text(
                    text = "${state.azimuth.toInt()}°",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = onSurface
                )
                
                Surface(
                    color = primaryColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = getDirectionLabel(state.azimuth).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryColor,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        letterSpacing = 1.sp
                    )
                }
            }

            // Expressive Dial Design
            Box(
                modifier = Modifier
                    .size(dialSize)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background Glow
                val infiniteTransition = rememberInfiniteTransition(label = "glow")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.05f,
                    targetValue = 0.15f,
                    animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                    label = "glowAlpha"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(primaryColor.copy(alpha = glowAlpha), Color.Transparent),
                                radius = 600f
                            )
                        )
                )

                // The Rotating Dial
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.width / 2
                    val center = center

                    // Draw outer ring
                    drawCircle(
                        color = onSurface.copy(alpha = 0.05f),
                        radius = radius,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw Ticks and Directions
                    rotate(-animatedAzimuth) {
                        for (i in 0 until 360 step 2) {
                            val angleRad = Math.toRadians(i.toDouble() - 90).toFloat()
                            val isMain = i % 30 == 0
                            val isCardinal = i % 90 == 0
                            
                            val tickLength = if (isCardinal) 24.dp.toPx() else if (isMain) 16.dp.toPx() else 8.dp.toPx()
                            val strokeWidth = if (isMain) 2.5.dp.toPx() else 1.dp.toPx()
                            val alphaValue = if (isMain) 0.6f else 0.2f
                            
                            val start = Offset(
                                center.x + (radius - tickLength) * cos(angleRad),
                                center.y + (radius - tickLength) * sin(angleRad)
                            )
                            val end = Offset(
                                center.x + radius * cos(angleRad),
                                center.y + radius * sin(angleRad)
                            )
                            
                            drawLine(
                                color = if (isCardinal && i == 0) Color.Red else onSurface.copy(alpha = alphaValue),
                                start = start,
                                end = end,
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    
                    // Qibla Pointer (Green)
                    val qiblaAngle = state.qiblaAngle
                    if (state.showQibla && qiblaAngle != null) {
                        rotate(-animatedAzimuth + qiblaAngle) {
                            val path = Path().apply {
                                moveTo(center.x, center.y - radius + 10.dp.toPx())
                                lineTo(center.x - 12.dp.toPx(), center.y - radius + 35.dp.toPx())
                                lineTo(center.x + 12.dp.toPx(), center.y - radius + 35.dp.toPx())
                                close()
                            }
                            drawPath(path, Color(0xFF4CAF50))
                            
                            drawCircle(
                                color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                                radius = 20.dp.toPx(),
                                center = Offset(center.x, center.y - radius + 60.dp.toPx())
                            )
                        }
                    }
                }

                // Fixed Pointer (Current Heading)
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .offset(y = -(dialSize / 2) + 10.dp)
                        .alpha(0.8f),
                    tint = primaryColor
                )

                // Center Stability Indicator / Bubble Level
                val bubbleX by animateFloatAsState(targetValue = (state.roll / 45f) * (dialSize.value / 4), label = "levelX")
                val bubbleY by animateFloatAsState(targetValue = (state.pitch / 45f) * (dialSize.value / 4), label = "levelY")
                
                Surface(
                    modifier = Modifier
                        .size(64.dp)
                        .offset(bubbleX.dp, bubbleY.dp)
                        .shadow(12.dp, CircleShape),
                    shape = CircleShape,
                    color = surfaceColor,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = if (state.isLevel) Color(0xFF4CAF50) else primaryColor.copy(alpha = 0.3f)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.showQibla) Icons.Rounded.Mosque else Icons.Rounded.Navigation,
                            null,
                            modifier = Modifier.size(28.dp),
                            tint = if (state.isLevel) Color(0xFF4CAF50) else primaryColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            // Info Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Accuracy Card
                CompassInfoCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Speed,
                    label = "ACCURACY",
                    value = when (state.accuracy) {
                        3 -> "HIGH"
                        2 -> "MEDIUM"
                        else -> "LOW"
                    },
                    color = when (state.accuracy) {
                        3 -> Color(0xFF4CAF50)
                        2 -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }
                )

                // Qibla Card
                if (state.showQibla) {
                    CompassInfoCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.LocationOn,
                        label = "QIBLA",
                        value = state.qiblaAngle?.let { "${it.toInt()}°" } ?: "...",
                        color = Color(0xFF4CAF50),
                        onClick = {
                            if (!locationPermissionState.status.isGranted) {
                                locationPermissionState.launchPermissionRequest()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CompassInfoCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
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
