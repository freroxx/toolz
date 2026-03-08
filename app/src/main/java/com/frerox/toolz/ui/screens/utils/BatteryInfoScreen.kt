package com.frerox.toolz.ui.screens.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            TopAppBar(
                title = { Text("Battery Health", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Battery Level Circle
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                val progress = state.level / 100f
                val animatedProgress by animateFloatAsState(targetValue = progress, label = "BatteryLevel")
                
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 16.dp,
                    strokeCap = StrokeCap.Round,
                    color = when {
                        state.level <= 15 -> Color.Red
                        state.level <= 30 -> Color.Yellow
                        state.isCharging -> Color.Green
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (state.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = if (state.isCharging) Color.Green else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${state.level}%",
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.status,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Detailed Stats Grid
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BatteryStatItem("Health", state.health, Icons.Rounded.Favorite, Modifier.weight(1f))
                        BatteryStatItem("Temp", "${state.temperature}°C", Icons.Rounded.Thermostat, Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BatteryStatItem("Voltage", "${state.voltage}mV", Icons.Rounded.ElectricBolt, Modifier.weight(1f))
                        BatteryStatItem("Source", state.powerSource, Icons.Rounded.Power, Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    BatteryStatItem("Technology", state.technology, Icons.Rounded.Memory, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun BatteryStatItem(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}
