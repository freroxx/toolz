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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AltimeterScreen(
    viewModel: AltimeterViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    val animatedAltitude by animateFloatAsState(
        targetValue = state.altitudeDisplay.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "AltitudeValue"
    )

    DisposableEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            viewModel.startListening()
        }
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "ALTIMETER",
                subtitle = "Precision Elevation Tracking",
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
                            vibrationManager?.vibrateSuccess()
                            viewModel.resetStats()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = "Reset Peak")
                    }
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
                            if (state.referenceAltitudeMeters == null) viewModel.setReferenceAltitude() else viewModel.clearReferenceAltitude()
                        },
                        modifier = Modifier.size(48.dp),
                        shape = SmallExpressiveShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (state.referenceAltitudeMeters != null) 
                                MaterialTheme.colorScheme.tertiary 
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            if (state.referenceAltitudeMeters == null) Icons.Rounded.AddLocationAlt else Icons.Rounded.LocationDisabled, 
                            contentDescription = "Toggle Reference"
                        )
                    }
                },
                trailingContent = {
                    clickableItem(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.toggleUnit()
                        },
                        icon = { Icon(Icons.Rounded.Straighten, null) },
                        label = state.unit.label
                    )
                    clickableItem(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.togglePressureUnit()
                        },
                        icon = { Icon(Icons.Rounded.Compress, null) },
                        label = state.pressureUnit.label
                    )
                    clickableItem(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.resetStats()
                        },
                        icon = { Icon(Icons.Rounded.Refresh, null) },
                        label = "RESET"
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
                    // Altitude Card with Organic Terrain Visualization
                    ExpressiveCard(
                        onClick = { vibrationManager?.vibrateTick() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .graphicsLayer {
                                translationY = sin(animatedAltitude * 0.1f) * 2f
                            },
                        shape = SquircleShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        elevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            // Animated Terrain Visualization
                            if (!performanceMode) {
                                val infiniteTransition = rememberInfiniteTransition(label = "TerrainLines")
                                val offset by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 100f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(15000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "Offset"
                                )
                                
                                val primaryColor = MaterialTheme.colorScheme.primary
                                Canvas(modifier = Modifier.fillMaxSize().alpha(0.15f)) {
                                    val path = Path()
                                    val width = size.width
                                    val height = size.height
                                    val spacing = 32.dp.toPx()
                                    
                                    for (i in -2..(height / spacing).toInt() + 2) {
                                        val y = i * spacing + (offset % spacing)
                                        path.reset()
                                        path.moveTo(0f, y)
                                        for (x in 0..width.toInt() step 20) {
                                            val dy = sin((x + offset) * 0.015f) * 15f
                                            path.lineTo(x.toFloat(), y + dy)
                                        }
                                        drawPath(
                                            path = path,
                                            color = primaryColor,
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ExpressiveStatePill(
                                    text = state.source,
                                    icon = if (state.source == "Barometer") Icons.Rounded.Compress else Icons.Rounded.GpsFixed,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                Spacer(Modifier.height(32.dp))

                                Box(contentAlignment = Alignment.Center) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
                                    val pulseScale by infiniteTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 1.05f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(2000, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "PulseScale"
                                    )

                                    ToolzWavyCircularProgressIndicator(
                                        progress = { 
                                            // Visualize climb rate activity
                                            (abs(state.climbRateMps) / 2.0).toFloat().coerceIn(0.1f, 1f)
                                        },
                                        modifier = Modifier.size(260.dp).graphicsLayer {
                                            scaleX = pulseScale
                                            scaleY = pulseScale
                                        },
                                        color = if (state.climbRateMps >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
                                    )

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.1f", animatedAltitude),
                                            style = MaterialTheme.typography.displayLarge.copy(
                                                fontSize = 80.sp, 
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = (-4).sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${state.unit.label} ASL",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 2.sp
                                        )
                                    }
                                }
                                
                                state.relativeAltitudeDisplay?.let { relative ->
                                    Spacer(Modifier.height(16.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                        shape = BouncyShape
                                    ) {
                                        Text(
                                            text = "REL: ${if (relative >= 0) "+" else ""}${String.format("%.1f", relative)} ${state.unit.label}",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    // Secondary Data Hub - Staggered Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StaggeredEntrance(index = 0, modifier = Modifier.weight(1f)) {
                            AltimeterStatCard(
                                label = "CLIMB RATE",
                                value = String.format(Locale.getDefault(), "%.2f", state.climbRateDisplay),
                                unit = "${state.unit.label[0]}/s",
                                icon = if (state.climbRateMps >= 0) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                                color = if (state.climbRateMps >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        StaggeredEntrance(index = 1, modifier = Modifier.weight(1f)) {
                            AltimeterStatCard(
                                label = "PRESSURE",
                                value = String.format(Locale.getDefault(), "%.1f", state.pressureDisplay),
                                unit = state.pressureUnit.label,
                                icon = Icons.Rounded.Compress,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StaggeredEntrance(index = 2, modifier = Modifier.weight(1f)) {
                            AltimeterStatCard(
                                label = "MAX ALT",
                                value = String.format(Locale.getDefault(), "%.0f", state.maxAltitudeDisplay),
                                unit = state.unit.label,
                                icon = Icons.Rounded.Landscape,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        StaggeredEntrance(index = 3, modifier = Modifier.weight(1f)) {
                            AltimeterStatCard(
                                label = "MIN ALT",
                                value = String.format(Locale.getDefault(), "%.0f", state.minAltitudeDisplay),
                                unit = state.unit.label,
                                icon = Icons.Rounded.Terrain,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(Modifier.height(120.dp))
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
fun AltimeterStatCard(
    modifier: Modifier = Modifier, 
    label: String, 
    value: String, 
    unit: String, 
    icon: ImageVector, 
    color: Color
) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick = { vibrationManager?.vibrateTick() },
        modifier = modifier,
        shape = BouncyShape,
        containerColor = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = color)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    unit, 
                    style = MaterialTheme.typography.labelSmall, 
                    modifier = Modifier.padding(bottom = 3.dp, start = 4.dp), 
                    fontWeight = FontWeight.Black, 
                    color = color
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
            Text("ALTITUDE SENSORS LOCKED", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(
                "Toolz requires precision location access to calibrate vertical displacement relative to sea level. Your data is never shared.",
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
                Text("ACTIVATE ALTIMETER", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AltimeterPreview() {
    ToolzTheme {
        Box(Modifier.fillMaxSize().toolzBackground()) {
            AltimeterScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                onBack = {}
            )
        }
    }
}
