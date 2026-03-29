package com.frerox.toolz.ui.screens.utils

import android.content.Context
import android.os.BatteryManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryInfoScreen(
    viewModel: BatteryInfoViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current

    val animatedLevel by animateIntAsState(
        targetValue = state.level,
        animationSpec = tween(1500, easing = EaseOutExpo),
        label = "BatteryLevel"
    )

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose { viewModel.stopListening() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { @Suppress("DEPRECATION") Text("ENERGY ANALYTICS", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelSmall) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
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
                BatteryLevelGauge(
                    level = animatedLevel, 
                    isCharging = state.isCharging,
                    performanceMode = performanceMode
                )

                // Status Strip
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BatteryMetricShort("STATUS", if (state.isCharging) "CHARGING" else "DISCHARGING", Icons.Rounded.Power)
                        VerticalDivider(modifier = Modifier.height(32.dp).alpha(0.2f))
                        BatteryMetricShort("HEALTH", state.health.uppercase(), Icons.Rounded.HealthAndSafety)
                        VerticalDivider(modifier = Modifier.height(32.dp).alpha(0.2f))
                        BatteryMetricShort("TEMP", "${state.temperature}°C", Icons.Rounded.Thermostat)
                    }
                }

                // Grid of detailed metrics
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BatteryDetailCard(Modifier.weight(1f), "VOLTAGE", "${state.voltage}mV", Icons.Rounded.ElectricBolt)
                        BatteryDetailCard(Modifier.weight(1f), "CAPACITY", "${state.capacityAh.toInt()}mAh", Icons.Rounded.BatteryChargingFull)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BatteryDetailCard(Modifier.weight(1f), "TECHNOLOGY", state.technology, Icons.Rounded.Memory)
                        BatteryDetailCard(Modifier.weight(1f), "POWER SOURCE", state.powerSource, Icons.Rounded.Usb)
                    }
                }

                // Advanced Info Card
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Analytics, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            @Suppress("DEPRECATION")
                            Text("ENGINE DIAGNOSTICS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        DiagnosticRow("Battery Current", "${state.currentNowMa} mA")
                        DiagnosticRow("Charge Counter", "${state.chargeCounterUah} uAh")
                        DiagnosticRow("Optimization", "Hardware Level")
                    }
                }
                
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun BatteryLevelGauge(level: Int, isCharging: Boolean, performanceMode: Boolean) {
    val levelColor = when {
        level < 20 -> Color(0xFFEF5350)
        level < 50 -> Color(0xFFFFA726)
        else -> Color(0xFF66BB6A)
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        // Outer Glow
        if (!performanceMode) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.05f,
                targetValue = 0.15f,
                animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                label = "glow"
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(levelColor.copy(alpha = glowAlpha), Color.Transparent),
                        radius = size.width / 1.1f
                    )
                )
            }
        }

        // Circular Gauge
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(0.85f),
            strokeWidth = 16.dp,
            strokeCap = StrokeCap.Round,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        )
        
        CircularProgressIndicator(
            progress = { level / 100f },
            modifier = Modifier.fillMaxSize(0.85f),
            strokeWidth = 16.dp,
            strokeCap = StrokeCap.Round,
            color = levelColor,
            trackColor = Color.Transparent,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$level",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 86.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-4).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "%", 
                    style = MaterialTheme.typography.headlineMedium, 
                    fontWeight = FontWeight.Black,
                    color = levelColor,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
            
            if (isCharging) {
                val chargingTransition = rememberInfiniteTransition(label = "charging")
                val iconScale by chargingTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                    label = "scale"
                )
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = Color(0xFFFBC02D),
                    modifier = Modifier.size(32.dp).scale(if (performanceMode) 1f else iconScale)
                )
            }
        }
    }
}

@Composable
fun BatteryMetricShort(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        @Suppress("DEPRECATION")
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, fontSize = 9.sp)
    }
}

@Composable
fun BatteryDetailCard(modifier: Modifier, label: String, value: String, icon: ImageVector) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            @Suppress("DEPRECATION")
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
