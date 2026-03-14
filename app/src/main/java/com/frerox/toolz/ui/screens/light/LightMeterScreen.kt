package com.frerox.toolz.ui.screens.light

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.fadingEdge
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
        luxValue < 10 -> Triple("Pitch Dark", Color(0xFF424242), "Minimal visibility environment")
        luxValue < 50 -> Triple("Dimly Lit", Color(0xFF757575), "Suitable for resting")
        luxValue < 200 -> Triple("Cozy Indoor", Color(0xFF4CAF50), "Comfortable home lighting")
        luxValue < 500 -> Triple("Productive", Color(0xFFFFC107), "Optimal for office work")
        luxValue < 1000 -> Triple("Industrial", Color(0xFFFF9800), "Detailed precision tasks")
        else -> Triple("High Intensity", Color(0xFFFF5722), "Direct solar or studio light")
    }

    val animatedLux by animateFloatAsState(
        targetValue = luxValue,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "LuxValue"
    )
    val animatedColor by animateColorAsState(targetValue = statusColor, label = "StatusColor")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LIGHT METER", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { maxLux = 0f },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset Max")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                        .fadingEdge(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.05f to Color.Black,
                                0.95f to Color.Black,
                                1f to Color.Transparent
                            ),
                            length = 24.dp
                        )
                        .padding(horizontal = 24.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Main Gauge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(300.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "glow")
                        val glowAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.05f,
                            targetValue = 0.12f,
                            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                            label = "glowAlpha"
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(animatedColor.copy(alpha = glowAlpha), Color.Transparent),
                                    radius = size.width / 1.1f
                                )
                            )
                        }

                        LuxGauge(value = animatedLux, color = animatedColor)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Lightbulb,
                                contentDescription = null,
                                tint = animatedColor,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "${luxValue.toInt()}",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 72.sp,
                                    letterSpacing = (-2).sp
                                )
                            )
                            Text(
                                text = "LUMENS / M²",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // Status Card
                    Surface(
                        color = animatedColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.5.dp, animatedColor.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = animatedColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = statusLabel.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = animatedColor,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = statusDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Peak & Study Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InfoCard(
                            modifier = Modifier.weight(1f),
                            label = "SESSION PEAK",
                            value = "${maxLux.toInt()}",
                            unit = "LUX",
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        val examReady = luxValue >= 500
                        InfoCard(
                            modifier = Modifier.weight(1f),
                            label = "TASK READY",
                            value = if (examReady) "OPTIMAL" else "LOW",
                            unit = "LIGHT",
                            color = if (examReady) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun InfoCard(modifier: Modifier, label: String, value: String, unit: String, color: Color) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    text = " $unit",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = color.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
fun LuxGauge(value: Float, color: Color) {
    val sweepAngle = 240f
    val startAngle = 150f
    val progress = min(value / 2000f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = LinearOutSlowInEasing),
        label = "GaugeProgress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 20.dp.toPx()
        
        // Background track
        drawArc(
            color = Color.LightGray.copy(alpha = 0.1f),
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
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("SENSOR NOT DETECTED", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(
            "This tool requires a hardware light sensor which appears to be missing on this device.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
