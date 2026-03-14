package com.frerox.toolz.ui.screens.utils

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.fadingEdge
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryInfoScreen(
    viewModel: BatteryInfoViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("BATTERY PRO", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .fadingEdge(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.02f to Color.Black, 0.98f to Color.Black, 1f to Color.Transparent),
                    length = 24.dp
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Advanced Battery Indicator
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                val progress = state.level / 100f
                val animatedProgress by animateFloatAsState(
                    targetValue = progress, 
                    animationSpec = tween(1000, easing = FastOutSlowInEasing),
                    label = "BatteryLevel"
                )
                
                val statusColor = when {
                    state.level <= 15 -> Color(0xFFEF5350)
                    state.level <= 30 -> Color(0xFFFFA726)
                    state.isCharging -> Color(0xFF66BB6A)
                    else -> MaterialTheme.colorScheme.primary
                }

                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(240.dp),
                    strokeWidth = 20.dp,
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                )

                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(240.dp),
                    strokeWidth = 20.dp,
                    strokeCap = StrokeCap.Round,
                    color = statusColor,
                    trackColor = Color.Transparent
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val icon = when {
                        state.isCharging -> Icons.Rounded.BatteryChargingFull
                        state.level <= 20 -> Icons.Rounded.Battery0Bar
                        state.level <= 40 -> Icons.Rounded.Battery3Bar
                        state.level <= 60 -> Icons.Rounded.Battery4Bar
                        state.level <= 80 -> Icons.Rounded.Battery5Bar
                        else -> Icons.Rounded.BatteryFull
                    }
                    
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = statusColor
                    )
                    Text(
                        text = "${state.level}%",
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black, fontSize = 60.sp, letterSpacing = (-2).sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (state.isCharging) "CHARGING" else "DISCHARGING",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = statusColor,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Real-time Current Usage
            if (state.currentNowMa != 0) {
                MetricCard(
                    title = if (state.currentNowMa < 0) "DISCHARGE RATE" else "CHARGE RATE",
                    value = "${abs(state.currentNowMa)}",
                    unit = "mA",
                    icon = if (state.currentNowMa < 0) Icons.AutoMirrored.Rounded.TrendingDown else Icons.AutoMirrored.Rounded.TrendingUp,
                    color = if (state.currentNowMa < 0) Color(0xFFEF5350) else Color(0xFF66BB6A)
                )
            }

            // Technical Grid
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "TECHNICAL SPECIFICATIONS", 
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )
                }
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            BatteryDetailItem("HEALTH", state.health, Icons.Rounded.Favorite, Modifier.weight(1f))
                            BatteryDetailItem("TEMP", "${state.temperature}°C", Icons.Rounded.Thermostat, Modifier.weight(1f))
                        }
                        HorizontalDivider(modifier = Modifier.alpha(0.1f))
                        Row(Modifier.fillMaxWidth()) {
                            BatteryDetailItem("VOLTAGE", "${state.voltage} mV", Icons.Rounded.ElectricBolt, Modifier.weight(1f))
                            BatteryDetailItem("SOURCE", state.powerSource, Icons.Rounded.Power, Modifier.weight(1f))
                        }
                        HorizontalDivider(modifier = Modifier.alpha(0.1f))
                        Row(Modifier.fillMaxWidth()) {
                            BatteryDetailItem("CAPACITY", if (state.capacityAh > 0) "${state.capacityAh.toInt()} mAh" else "N/A", Icons.Rounded.SdStorage, Modifier.weight(1f))
                            BatteryDetailItem("TECH", state.technology, Icons.Rounded.Memory, Modifier.weight(1f))
                        }
                    }
                }
            }
            
            // Usage Tips
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Maintain charge between 20% - 80% to maximize long-term battery health.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, unit: String, icon: ImageVector, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color, letterSpacing = 0.5.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = color)
                    Text(unit, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp), fontWeight = FontWeight.Bold, color = color)
                }
            }
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = color.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(32.dp), tint = color)
                }
            }
        }
    }
}

@Composable
fun BatteryDetailItem(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline, letterSpacing = 0.5.sp)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
        }
    }
}
