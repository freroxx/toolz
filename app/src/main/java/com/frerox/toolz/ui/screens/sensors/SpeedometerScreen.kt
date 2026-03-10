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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.fadingEdge
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SpeedometerScreen(
    viewModel: SpeedometerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.speedState.collectAsState()
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    val animatedSpeed by animateFloatAsState(
        targetValue = state.speedKmh,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "Speed"
    )

    DisposableEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            viewModel.startTracking()
        }
        onDispose {
            viewModel.stopTracking()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            ) {
                CenterAlignedTopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text("SPEEDOMETER", fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.resetStats() }) {
                            Icon(Icons.Rounded.History, "Reset Stats")
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
            if (locationPermissionState.status.isGranted) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Main Speed Gauge
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(340.dp)) {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val tertiaryColor = MaterialTheme.colorScheme.tertiary
                        
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 24.dp.toPx()
                            // Background Track
                            drawArc(
                                color = Color.Gray.copy(alpha = 0.1f),
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            
                            // Progress
                            val sweep = (animatedSpeed / 160f * 270f).coerceIn(0f, 270f)
                            drawArc(
                                brush = Brush.sweepGradient(
                                    0.0f to primaryColor,
                                    0.5f to tertiaryColor,
                                    1.0f to Color.Red
                                ),
                                startAngle = 135f,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(Locale.getDefault(), "%.0f", animatedSpeed),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 110.sp, 
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = (-4).sp
                                )
                            )
                            Text(
                                text = "KM/H",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp
                            )
                        }
                    }

                    // Signal and Precision
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val signalColor = when {
                            state.accuracy == 0f -> MaterialTheme.colorScheme.outline
                            state.accuracy < 10 -> Color(0xFF4CAF50)
                            state.accuracy < 30 -> Color(0xFFFFC107)
                            else -> Color(0xFFF44336)
                        }
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(signalColor))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (!state.isGpsEnabled) "GPS DISABLED" else "GPS SIGNAL: ${if (state.accuracy < 20) "STRONG" else "WEAK"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(Modifier.height(48.dp))

                    // Stats Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SpeedStatCard(
                            modifier = Modifier.weight(1f),
                            label = "MAX SPEED",
                            value = "${state.maxSpeedKmh.toInt()}",
                            unit = "km/h",
                            icon = Icons.Rounded.Speed,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SpeedStatCard(
                            modifier = Modifier.weight(1f),
                            label = "DISTANCE",
                            value = String.format(Locale.getDefault(), "%.1f", state.totalDistanceMeters / 1000),
                            unit = "km",
                            icon = Icons.Rounded.Navigation,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Bottom Info
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InfoItem("Altitude", "${state.altitude.toInt()}m", Icons.Rounded.Terrain)
                            VerticalDivider(modifier = Modifier.height(32.dp))
                            InfoItem("Accuracy", "±${state.accuracy.toInt()}m", Icons.Rounded.LocationOn)
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                }
            } else {
                PermissionView { locationPermissionState.launchPermissionRequest() }
            }
        }
    }
}

@Composable
fun SpeedStatCard(modifier: Modifier, label: String, value: String, unit: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp), fontWeight = FontWeight.Bold)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color.copy(alpha = 0.7f))
        }
    }
}
