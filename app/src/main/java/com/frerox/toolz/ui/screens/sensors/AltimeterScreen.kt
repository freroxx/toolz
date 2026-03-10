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
import androidx.compose.material.icons.rounded.FilterHdr
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerticalAlignTop
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
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AltimeterScreen(
    viewModel: AltimeterViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    val animatedAltitude by animateFloatAsState(
        targetValue = state.altitudeMeters.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "Altitude"
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
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                CenterAlignedTopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text("ALTITUDE TRACKER", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Settings */ }) {
                            Icon(Icons.Rounded.Settings, null)
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
                    // Altitude Display
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        shape = RoundedCornerShape(48.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Decorative Terrain Lines
                            Canvas(modifier = Modifier.fillMaxSize().alpha(0.05f)) {
                                for (i in 0..15) {
                                    val y = size.height * (i / 15f)
                                    drawLine(
                                        color = Color.Gray,
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "CURRENT ALTITUDE",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                                
                                Spacer(Modifier.height(24.dp))
                                
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f", animatedAltitude),
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = 86.sp, 
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = (-4).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "METERS ABOVE SEA LEVEL",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    // Stats Cards
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AltimeterStatCard(
                            modifier = Modifier.weight(1f),
                            label = "PEAK",
                            value = String.format(Locale.getDefault(), "%.0f m", state.maxAltitude),
                            icon = Icons.Rounded.VerticalAlignTop,
                            color = MaterialTheme.colorScheme.primary
                        )
                        AltimeterStatCard(
                            modifier = Modifier.weight(1f),
                            label = "BASE",
                            value = String.format(Locale.getDefault(), "%.0f m", if (state.minAltitude == Double.MAX_VALUE) 0.0 else state.minAltitude),
                            icon = Icons.Rounded.History,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    // Info Section
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(40.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(28.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InfoItem("Data Source", state.source, Icons.Rounded.LocationOn)
                                Box(modifier = Modifier.height(40.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                                InfoItem("Atm. Pressure", String.format(Locale.getDefault(), "%.1f hPa", state.pressureHpa), Icons.Rounded.FilterHdr)
                            }
                            
                            if (state.accuracy > 0 && state.source == "GPS") {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp).alpha(0.15f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Signal Accuracy: ±${state.accuracy.toInt()} meters",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(48.dp))
                }
            } else {
                PermissionView { locationPermissionState.launchPermissionRequest() }
            }
        }
    }
}

@Composable
fun AltimeterStatCard(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = color)
            Spacer(Modifier.height(16.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun InfoItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun PermissionView(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Surface(modifier = Modifier.size(120.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(64.dp).alpha(0.2f), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("LOCATION REQUIRED", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(
                "Precise location data is required to determine altitude in environments without a barometric sensor.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text("GRANT PERMISSION", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}
