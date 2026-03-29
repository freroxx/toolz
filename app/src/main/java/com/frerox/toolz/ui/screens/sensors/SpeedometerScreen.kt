package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
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
    val performanceMode = LocalPerformanceMode.current

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

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SPEEDOMETER", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.resetStats() },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.History, "Reset Stats")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(top = padding.calculateTopPadding())
        ) {
            if (locationPermissionState.status.isGranted) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Main Speed Gauge
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
                        val infiniteTransition = rememberInfiniteTransition(label = "glow")
                        val glowAlpha by if (performanceMode) remember { mutableFloatStateOf(0.08f) } else infiniteTransition.animateFloat(
                            initialValue = 0.05f,
                            targetValue = 0.12f,
                            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                            label = "glow"
                        )

                        if (!performanceMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            listOf(primaryColor.copy(alpha = glowAlpha), Color.Transparent)
                                        ),
                                        CircleShape
                                    )
                            )
                        }

                        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            val strokeWidth = 20.dp.toPx()
                            drawArc(
                                color = Color.Gray.copy(alpha = 0.1f),
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            
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
                                    fontSize = 90.sp, 
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = (-4).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            @Suppress("DEPRECATION")
                            Text(
                                text = "KILOMETERS / HOUR",
                                style = MaterialTheme.typography.labelSmall,
                                color = primaryColor,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val signalColor = when {
                                state.accuracy == 0f -> MaterialTheme.colorScheme.outline
                                state.accuracy < 10 -> Color(0xFF4CAF50)
                                state.accuracy < 30 -> Color(0xFFFFC107)
                                else -> Color(0xFFF44336)
                            }
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(signalColor))
                            Spacer(Modifier.width(8.dp))
                            @Suppress("DEPRECATION")
                            Text(
                                text = if (!state.isGpsEnabled) "GPS ENGINE OFFLINE" else "GPS SIGNAL STRENGTH: ${if (state.accuracy < 20) "HIGH" else "LOW"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(40.dp))

                    // Stats Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SpeedStatCard(
                            modifier = Modifier.weight(1f),
                            label = "PEAK SPEED",
                            value = "${state.maxSpeedKmh.toInt()}",
                            unit = "KM/H",
                            icon = Icons.Rounded.Speed,
                            color = primaryColor
                        )
                        SpeedStatCard(
                            modifier = Modifier.weight(1f),
                            label = "TOTAL TRIP",
                            value = String.format(Locale.getDefault(), "%.1f", state.totalDistanceMeters / 1000),
                            unit = "KM",
                            icon = Icons.Rounded.Navigation,
                            color = tertiaryColor
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Bottom Info
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InfoItemInternal("ALTITUDE", "${state.altitude.toInt()}m", Icons.Rounded.Terrain)
                            VerticalDivider(modifier = Modifier.height(32.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            InfoItemInternal("ACCURACY", "±${state.accuracy.toInt()}m", Icons.Rounded.LocationOn)
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                }
            } else {
                PermissionViewInternal { locationPermissionState.launchPermissionRequest() }
            }
        }
    }
}

@Composable
fun SpeedStatCard(modifier: Modifier, label: String, value: String, unit: String, icon: ImageVector, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                @Suppress("DEPRECATION")
                Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp), fontWeight = FontWeight.Bold, color = color)
            }
            @Suppress("DEPRECATION")
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color.copy(alpha = 0.7f), letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun InfoItemInternal(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            @Suppress("DEPRECATION")
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PermissionViewInternal(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Surface(
                modifier = Modifier.size(140.dp), 
                shape = RoundedCornerShape(40.dp), 
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(64.dp).alpha(0.5f), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("LOCATION ENGINE LOCKED", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(
                "Speedometer requires GPS data to calculate real-time movement and distance. All processing happens locally.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 40.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text("GRANT PERMISSION", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}
