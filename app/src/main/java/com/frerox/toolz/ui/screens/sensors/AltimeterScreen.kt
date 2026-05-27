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
                    ExpressiveFabMenu(
                        items = listOf(
                            Triple("Reset Peak", Icons.Rounded.History, { 
                                vibrationManager?.vibrateSuccess()
                                viewModel.resetStats() 
                            }),
                            Triple("Pressure Unit", Icons.Rounded.Compress, {
                                vibrationManager?.vibrateClick()
                                viewModel.togglePressureUnit()
                            })
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
                        Icon(Icons.Rounded.Height, contentDescription = "Toggle Units")
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
                        icon = { Icon(Icons.Rounded.FilterHdr, null) },
                        label = state.pressureUnit.label
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
                            .height(340.dp)
                            .graphicsLayer {
                                // Subtle vertical bounce
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
                                        animation = tween(10000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "Offset"
                                )
                                
                                val primaryColor = MaterialTheme.colorScheme.primary
                                Canvas(modifier = Modifier.fillMaxSize().alpha(0.12f)) {
                                    val path = Path()
                                    val width = size.width
                                    val height = size.height
                                    val spacing = 40.dp.toPx()
                                    
                                    for (i in -2..(height / spacing).toInt() + 2) {
                                        val y = i * spacing + (offset % spacing)
                                        path.reset()
                                        path.moveTo(0f, y)
                                        for (x in 0..width.toInt() step 20) {
                                            val dy = sin((x + offset) * 0.02f) * 10f
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
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    shape = BouncyShape
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (state.source == "Barometer") Icons.Rounded.Compress else Icons.Rounded.GpsFixed,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = state.source.uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                                
                                Spacer(Modifier.height(32.dp))
                                
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f", animatedAltitude),
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = 96.sp, 
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
                    }

                    Spacer(Modifier.height(32.dp))

                    // Wavy Elevation Consistency Indicator
                    ToolzWavyLinearProgressIndicator(
                        progress = { 
                            if (state.maxAltitudeMeters != state.minAltitudeMeters) {
                                ((state.altitudeMeters - state.minAltitudeMeters) / (state.maxAltitudeMeters - state.minAltitudeMeters)).toFloat().coerceIn(0f, 1f)
                            } else 0.5f
                        },
                        modifier = Modifier.fillMaxWidth().height(16.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                    )

                    Spacer(Modifier.height(40.dp))

                    // Peak Stats with Bouncy Containers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StaggeredEntrance(index = 0) {
                            AltimeterStatCard(
                                modifier = Modifier.weight(1f),
                                label = "MAX ALTITUDE",
                                value = String.format(Locale.getDefault(), "%.0f", state.maxAltitudeDisplay),
                                unit = state.unit.label,
                                icon = Icons.Rounded.Landscape,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        StaggeredEntrance(index = 1) {
                            AltimeterStatCard(
                                modifier = Modifier.weight(1f),
                                label = "MIN ALTITUDE",
                                value = String.format(Locale.getDefault(), "%.0f", state.minAltitudeDisplay),
                                unit = state.unit.label,
                                icon = Icons.Rounded.Terrain,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Secondary Data Hub
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
                                InfoItemInternal("PRESSURE", "${String.format("%.1f", state.pressureDisplay)} ${state.pressureUnit.label}", Icons.Rounded.FilterHdr)
                                VerticalDivider(modifier = Modifier.height(40.dp).width(1.5.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                InfoItemInternal("PRECISION", "±${state.accuracy.toInt()}m", Icons.Rounded.GpsFixed)
                            }
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
fun AltimeterStatCard(modifier: Modifier, label: String, value: String, unit: String, icon: ImageVector, color: Color) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick = { vibrationManager?.vibrateTick() },
        modifier = modifier,
        shape = BouncyShape,
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
            // Preview logic
        }
    }
}
