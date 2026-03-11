package com.frerox.toolz.ui.screens.light

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightMeterScreen(
    viewModel: LightMeterViewModel,
    onBack: () -> Unit
) {
    val luxValue by viewModel.luxValue.collectAsState()
    val hasSensor = viewModel.hasSensor
    
    var maxLux by remember { mutableStateOf(0f) }
    LaunchedEffect(luxValue) {
        maxLux = max(maxLux, luxValue)
    }

    val (statusLabel, statusColor, statusDesc) = when {
        luxValue < 10 -> Triple("Dark", Color(0xFF424242), "Night time or closed room")
        luxValue < 50 -> Triple("Dim", Color(0xFF757575), "Low light, suitable for sleeping")
        luxValue < 200 -> Triple("Normal", Color(0xFF4CAF50), "Indoor lighting, living room")
        luxValue < 500 -> Triple("Bright", Color(0xFFFFC107), "Office work or heavy reading")
        luxValue < 1000 -> Triple("Very Bright", Color(0xFFFF9800), "Detailed mechanical work")
        else -> Triple("Extremely Bright", Color(0xFFFF5722), "Direct sunlight or studio lights")
    }

    val animatedLux by animateFloatAsState(
        targetValue = luxValue,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "LuxValue"
    )
    val animatedColor by animateColorAsState(targetValue = statusColor, label = "StatusColor")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LIGHT METER", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { maxLux = 0f }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset Max")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (!hasSensor) {
                NoSensorState()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Main Gauge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(320.dp)
                    ) {
                        LuxGauge(value = animatedLux, color = animatedColor)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Lightbulb,
                                contentDescription = null,
                                tint = animatedColor,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "${luxValue.toInt()}",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 80.sp,
                                    letterSpacing = (-2).sp
                                )
                            )
                            Text(
                                text = "LUX",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                letterSpacing = 4.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Status Card
                    Surface(
                        color = animatedColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = statusLabel.uppercase(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = animatedColor,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = statusDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Peak & Study Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InfoCard(
                            modifier = Modifier.weight(1f),
                            label = "PEAK LUX",
                            value = "${maxLux.toInt()}",
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        val examReady = luxValue >= 500
                        InfoCard(
                            modifier = Modifier.weight(1f),
                            label = "EXAM READY",
                            value = if (examReady) "YES" else "NO",
                            color = if (examReady) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun InfoCard(modifier: Modifier, label: String, value: String, color: Color) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@Composable
fun LuxGauge(value: Float, color: Color) {
    val sweepAngle = 240f
    val startAngle = 150f
    // Use logarithmic scale for gauge to handle wide range 0-10000+ lux
    // But for a simple gauge, let's just cap at 2000 for visibility
    val progress = min(value / 2000f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = LinearOutSlowInEasing),
        label = "GaugeProgress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 24.dp.toPx()
        
        // Background track
        drawArc(
            color = Color.LightGray.copy(alpha = 0.15f),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Progress track
        drawArc(
            brush = Brush.sweepGradient(
                0f to color.copy(alpha = 0.5f),
                0.5f to color,
                1f to color
            ),
            startAngle = startAngle,
            sweepAngle = sweepAngle * animatedProgress,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun NoSensorState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("No Light Sensor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "This device does not have a hardware light sensor required for this tool.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
