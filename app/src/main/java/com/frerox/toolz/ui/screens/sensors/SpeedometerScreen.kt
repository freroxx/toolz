package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeedometerScreen(
    viewModel: SpeedometerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.speedState.collectAsStateWithLifecycle()
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    // Bouncy spring for the primary velocity value
    val animatedSpeed by animateFloatAsState(
        targetValue = state.speedDisplay,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SpeedValue"
    )

    // Fluid progress for the wavy gauge
    val animatedProgress by animateFloatAsState(
        targetValue = state.speedProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SpeedProgress"
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
            ExpressiveTopAppBar(
                title = "SPEEDOMETER",
                subtitle = "Precision Velocity Tracking",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Expressive Fab Menu for secondary actions like Reset
                    ExpressiveFabMenu(
                        items = listOf(
                            Triple("Reset Stats", Icons.Rounded.History, { 
                                vibrationManager?.vibrateSuccess()
                                viewModel.resetStats() 
                            }),
                            Triple("Settings", Icons.Rounded.Settings, { /* Open Settings */ })
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            ToolzHorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.padding(bottom = 16.dp),
                content = {
                    FilledIconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.toggleUnit()
                        },
                        modifier = Modifier.size(48.dp),
                        shape = SmallExpressiveShape
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = "Toggle Units")
                    }
                },
                trailingContent = {
                    clickableItem(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.toggleUnit()
                        },
                        icon = { Icon(Icons.Rounded.Speed, null) },
                        label = state.unit.label
                    )
                    clickableItem(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            // Toggle tracking or other quick action
                        },
                        icon = { 
                            Icon(
                                if (state.isTracking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 
                                contentDescription = null 
                            ) 
                        },
                        label = if (state.isTracking) "PAUSE" else "START"
                    )
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
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
                    // Sweeping Dashboard Interface: Primary Reading on Squircle Container
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(340.dp)) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .graphicsLayer {
                                    // Subtle organic scale bounce based on speed
                                    val s = 1f + (state.speedProgress * 0.05f)
                                    scaleX = s
                                    scaleY = s
                                },
                            shape = SquircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                // Dynamic background glow
                                if (!performanceMode) {
                                    val glowAlpha by animateFloatAsState(
                                        targetValue = 0.1f + (state.speedProgress * 0.3f),
                                        animationSpec = tween(500),
                                        label = "GlowAlpha"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(primaryColor.copy(alpha = glowAlpha), Color.Transparent)
                                                )
                                            )
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = String.format(Locale.getDefault(), "%.${state.unit.precision}f", animatedSpeed),
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            fontSize = 110.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = (-6).sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = state.unit.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = primaryColor,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 4.sp
                                    )
                                }
                            }
                        }

                        // Official Wavy Progress Indicator integration
                        ToolzWavyCircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = primaryColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                        )
                    }

                    // GPS Signal Integrity Status
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                        shape = BouncyShape, // Using BouncyShape for secondary status
                        modifier = Modifier.padding(top = 8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val signalColor = when {
                                !state.isGpsEnabled -> MaterialTheme.colorScheme.error
                                state.accuracy == 0f -> MaterialTheme.colorScheme.outline
                                state.accuracy < 10 -> Color(0xFF4CAF50)
                                state.accuracy < 30 -> Color(0xFFFFC107)
                                else -> Color(0xFFF44336)
                            }
                            
                            // Pulse animation for GPS signal
                            val infiniteTransition = rememberInfiniteTransition(label = "GpsPulse")
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "Alpha"
                            )
                            
                            Box(modifier = Modifier.size(10.dp).graphicsLayer { alpha = pulseAlpha }.clip(CircleShape).background(signalColor))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (!state.isGpsEnabled) "GPS DISCONNECTED" else "GPS PRECISION: ±${state.accuracy.toInt()}m",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(48.dp))

                    // Peak Stats with Organic Bouncy Containers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StaggeredEntrance(index = 0) {
                            SpeedStatCard(
                                modifier = Modifier.weight(1f),
                                label = "PEAK SPEED",
                                value = String.format(Locale.getDefault(), "%.${state.unit.precision}f", state.maxSpeedDisplay),
                                unit = state.unit.label,
                                icon = Icons.Rounded.Speed,
                                color = primaryColor
                            )
                        }
                        StaggeredEntrance(index = 1) {
                            val distance = if (state.unit == SpeedUnit.KMH) state.totalDistanceMeters / 1000 else (state.totalDistanceMeters / 1000) * 0.621371
                            SpeedStatCard(
                                modifier = Modifier.weight(1f),
                                label = "TRIP DISTANCE",
                                value = String.format(Locale.getDefault(), "%.1f", distance),
                                unit = if (state.unit == SpeedUnit.KMH) "KM" else "MI",
                                icon = Icons.Rounded.Route,
                                color = tertiaryColor
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Secondary Dashboard Data
                    StaggeredEntrance(index = 2) {
                        ExpressiveCard(
                            onClick = { vibrationManager?.vibrateTick() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = SquircleShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                            elevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InfoItemInternal("ALTITUDE", "${state.altitude.toInt()}m", Icons.Rounded.Terrain)
                                VerticalDivider(modifier = Modifier.height(40.dp).width(1.5.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                InfoItemInternal("COORDINATES", "${String.format("%.4f", state.latitude)}, ${String.format("%.4f", state.longitude)}", Icons.Rounded.Map)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(120.dp)) // Extra space for Floating Toolbar
                }
            } else {
                PermissionViewInternal { 
                    vibrationManager?.vibrateClick()
                    locationPermissionState.launchPermissionRequest() 
                }
            }
        }
    }
}

@Composable
fun SpeedStatCard(modifier: Modifier, label: String, value: String, unit: String, icon: ImageVector, color: Color) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick = { vibrationManager?.vibrateTick() },
        modifier = modifier,
        shape = BouncyShape, // Stat cards use the bouncier corner radius
        containerColor = color.copy(alpha = 0.1f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Surface(
                modifier = Modifier.size(48.dp), 
                shape = SmallExpressiveShape, 
                color = color.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(28.dp), tint = color)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value, 
                    style = MaterialTheme.typography.displaySmall, 
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    unit, 
                    style = MaterialTheme.typography.labelMedium, 
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp), 
                    fontWeight = FontWeight.Black, 
                    color = color,
                    letterSpacing = 1.sp
                )
            }
            Text(
                label, 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Black, 
                color = color.copy(alpha = 0.7f), 
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun InfoItemInternal(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(36.dp), 
            shape = CircleShape, 
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PermissionViewInternal(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Surface(
                modifier = Modifier.size(160.dp), 
                shape = LargeExpressiveShape, 
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val infiniteTransition = rememberInfiniteTransition(label = "PermissionPulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.9f,
                        targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "Scale"
                    )
                    Icon(
                        Icons.Rounded.LocationSearching, 
                        null, 
                        modifier = Modifier.size(80.dp).graphicsLayer { scaleX = scale; scaleY = scale }, 
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
            Text("LOCATION ACCESS REQUIRED", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(
                "Toolz requires precision GPS to calculate velocity, distance, and altitude. Your privacy is guaranteed; data is processed locally.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 48.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyLarge
            )
            ToolzExpressiveButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = BouncyShape
            ) {
                Text("ACTIVATE SENSORS", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpeedometerPreview() {
    ToolzTheme {
        Box(Modifier.fillMaxSize().toolzBackground()) {
            // Mocking some of the UI for preview
        }
    }
}
