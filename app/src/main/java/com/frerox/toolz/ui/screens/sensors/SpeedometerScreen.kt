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

package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
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

    // Mirror for HUD Mode
    val hudModifier = if (state.isHudMode) {
        Modifier.graphicsLayer {
            rotationY = 180f
            rotationX = 180f
        }
    } else Modifier

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "SPEEDOMETER",
                subtitle = if (state.isHudMode) "HUD MODE ACTIVE" else "Precision Speedometer",
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
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.toggleHudMode()
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            if (state.isHudMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "HUD Mode"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (!state.isHudMode) {
                ToolzHorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier.padding(bottom = 16.dp),
                    content = {
                        FilledIconButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                if (state.isTracking) viewModel.stopTracking() else viewModel.startTracking()
                            },
                            modifier = Modifier.size(48.dp),
                            shape = SmallExpressiveShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (state.isTracking) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(
                                if (state.isTracking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isTracking) "Pause" else "Start"
                            )
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
                                vibrationManager?.vibrateSuccess()
                                viewModel.resetStats()
                            },
                            icon = { Icon(Icons.Rounded.History, null) },
                            label = "RESET"
                        )
                    }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .then(hudModifier)
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
                    // Sonic Pulse Gauge
                    Box(modifier = Modifier.size(340.dp), contentAlignment = Alignment.Center) {
                        SonicPulseGauge(
                            speed = state.speedDisplay,
                            progress = state.speedProgress,
                            unit = state.unit.label,
                            precision = state.unit.precision,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Paused Overlay
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !state.isTracking,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                shape = SquircleShape,
                                modifier = Modifier.padding(48.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Rounded.PauseCircleFilled, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "PAUSED",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Speed Sparkline (Trend)
                    StaggeredEntrance(index = 0) {
                        ExpressiveCard(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            shape = SquircleShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
                            elevation = 0.dp
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                SpeedSparkline(
                                    history = state.speedHistory,
                                    color = primaryColor,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    text = "LIVE TREND",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.align(Alignment.TopStart),
                                    color = primaryColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // GPS Signal Status
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                        shape = BouncyShape,
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

                    Spacer(Modifier.height(32.dp))

                    // Peak Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StaggeredEntrance(index = 1) {
                            SpeedStatCard(
                                modifier = Modifier.weight(1f),
                                label = "PEAK VELOCITY",
                                value = String.format(Locale.getDefault(), "%.${state.unit.precision}f", state.maxSpeedDisplay),
                                unit = state.unit.label,
                                icon = Icons.Rounded.Speed,
                                color = primaryColor
                            )
                        }
                        StaggeredEntrance(index = 2) {
                            val distance = if (state.unit == SpeedUnit.KMH) state.totalDistanceMeters / 1000 else (state.totalDistanceMeters / 1000) * 0.621371
                            SpeedStatCard(
                                modifier = Modifier.weight(1f),
                                label = "TOTAL TRIP",
                                value = String.format(Locale.getDefault(), "%.1f", distance),
                                unit = if (state.unit == SpeedUnit.KMH) "KM" else "MI",
                                icon = Icons.Rounded.Route,
                                color = tertiaryColor
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Secondary Info
                    StaggeredEntrance(index = 3) {
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
                                InfoItemInternal("LOCATION", "${String.format("%.4f", state.latitude)}, ${String.format("%.4f", state.longitude)}", Icons.Rounded.Map)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(120.dp))
                }
            } else {
                SpeedometerPermissionView { 
                    vibrationManager?.vibrateClick()
                    locationPermissionState.launchPermissionRequest() 
                }
            }
        }
    }
}

@Composable
fun SpeedStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(16.dp), tint = color)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = color.copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun InfoItemInternal(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SpeedometerPermissionView(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "GPS REQUIRED",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Location access is needed to calculate velocity and track your trip progress accurately.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onGrant,
            shape = MediumExpressiveShape,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("ENABLE PERMISSIONS", fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpeedometerPreview() {
    // We can't easily preview with ViewModel, but we can render the screen with dummy state if we refactor.
    // For now, I'll just check if it compiles.
}
