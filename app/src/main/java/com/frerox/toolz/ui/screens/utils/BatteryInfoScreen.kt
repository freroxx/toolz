package com.frerox.toolz.ui.screens.utils

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BatteryInfoScreen(
    viewModel: BatteryInfoViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose { viewModel.stopListening() }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "POWER ANALYTICS",
                subtitle = "Hardware energy distribution",
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .bouncyClick(onClick = onBack)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.remoteError != null) {
                        IconButton(
                            onClick = { viewModel.loadRemoteSpecs(forceRefresh = true) },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .bouncyClick(onClick = { viewModel.loadRemoteSpecs(forceRefresh = true) })
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Main Level Gauge
                StaggeredEntrance(index = 0) {
                    ExpressiveBatteryGauge(
                        level = state.level,
                        isCharging = state.isCharging
                    )
                }

                // Primary Status Card - FIXED SPACING
                StaggeredEntrance(index = 1) {
                    ExpressiveCard(
                        onClick = {},
                        shape = RoundedCornerShape(28.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 20.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusItem(Modifier.weight(1f), "STATUS", state.status.uppercase(), if (state.isCharging) Icons.Rounded.Bolt else Icons.Rounded.Power)
                            VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            StatusItem(Modifier.weight(1f), "HEALTH", state.health.uppercase(), Icons.Rounded.HealthAndSafety)
                            VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            StatusItem(Modifier.weight(1f), "TEMP", "${state.temperature}°C", Icons.Rounded.Thermostat)
                        }
                    }
                }

                // Hardware Specifications Card - FIXED WITH LISTITEM
                if (state.remoteSpec != null) {
                    val batterySpecs = state.remoteSpec?.categories?.find { it.name.contains("Battery", ignoreCase = true) }
                    if (batterySpecs != null) {
                        StaggeredEntrance(index = 2) {
                            ExpressiveCard(
                                onClick = {},
                                shape = RoundedCornerShape(32.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth(),
                                elevation = 0.dp
                            ) {
                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                    ListItem(
                                        headlineContent = { Text("HARDWARE SPECS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp) },
                                        supportingContent = { Text(state.remoteSpec?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        leadingContent = {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.size(40.dp),
                                                tonalElevation = 0.dp,
                                                shadowElevation = 0.dp
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                                                }
                                            }
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    batterySpecs.items.take(4).forEach { detail ->
                                        ListItem(
                                            overlineContent = { Text(detail.name.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)) },
                                            headlineContent = { Text(detail.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold) },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Grid of detailed metrics
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StaggeredEntrance(index = 3, modifier = Modifier.weight(1f)) {
                            MetricCard("VOLTAGE", "${state.voltage}mV", Icons.Rounded.ElectricBolt)
                        }
                        StaggeredEntrance(index = 4, modifier = Modifier.weight(1f)) {
                            MetricCard("CAPACITY", "${state.capacityMah}mAh", Icons.Rounded.BatteryChargingFull)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StaggeredEntrance(index = 5, modifier = Modifier.weight(1f)) {
                            MetricCard("TECHNOLOGY", state.technology, Icons.Rounded.Memory)
                        }
                        StaggeredEntrance(index = 6, modifier = Modifier.weight(1f)) {
                            MetricCard("SOURCE", state.powerSource, Icons.Rounded.Usb)
                        }
                    }
                }

                // Engine Diagnostics
                StaggeredEntrance(index = 7) {
                    ExpressiveCard(
                        onClick = {},
                        shape = RoundedCornerShape(28.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 0.dp
                    ) {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            ListItem(
                                headlineContent = { Text("SYSTEM DIAGNOSTICS", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp) },
                                leadingContent = { Icon(Icons.Rounded.Analytics, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ListItem(
                                headlineContent = { Text("Real-time Current", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailingContent = { Text("${state.currentNowMa} mA", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            ListItem(
                                headlineContent = { Text("Charge Counter", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailingContent = { Text("${state.chargeCounterUah} uAh", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun ExpressiveBatteryGauge(level: Int, isCharging: Boolean) {
    val performanceMode = LocalPerformanceMode.current

    val batteryColor by animateColorAsState(
        targetValue = when {
            isCharging -> Color(0xFF00E5FF) // Vivid Cyan for charging
            level < 20 -> MaterialTheme.colorScheme.error
            level < 45 -> Color(0xFFFFA726) // Amber/Orange
            else -> Color(0xFF66BB6A) // Green
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "BatteryColor"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "BatteryProgress"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        if (!performanceMode) {
            val infiniteTransition = rememberInfiniteTransition(label = "aura")
            val auraAlpha by infiniteTransition.animateFloat(
                initialValue = 0.08f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                label = "auraAlpha"
            )
            val auraScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                label = "auraScale"
            )
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = if (isCharging) auraScale else 1f
                scaleY = if (isCharging) auraScale else 1f
            }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(batteryColor.copy(alpha = if (isCharging) auraAlpha else auraAlpha / 2), Color.Transparent),
                        radius = size.width / 1.5f
                    )
                )
            }
        }

        ToolzWavyCircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(0.88f),
            color = batteryColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$level",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 92.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-4).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = batteryColor,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }

            if (isCharging) {
                Spacer(Modifier.height(4.dp))
                ExpressiveStatePill(
                    text = if (level >= 100) "Full" else "Charging",
                    icon = if (level >= 100) Icons.Rounded.BatteryFull else Icons.Rounded.Bolt,
                    color = if (level >= 100) Color(0xFF66BB6A) else Color(0xFF00E5FF),
                    modifier = Modifier.bouncyClick(onClick = {})
                )
            }
        }
    }
}

@Composable
fun StatusItem(modifier: Modifier, label: String, value: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 18.sp),
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun MetricCard(label: String, value: String, icon: ImageVector) {
    ExpressiveCard(
        onClick = {},
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(icon, null, modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
