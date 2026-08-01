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

package com.frerox.toolz.ui.screens.light

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightMeterScreen(
    viewModel: LightMeterViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val hasSensor = viewModel.hasSensor
    val performanceMode = LocalPerformanceMode.current
    
    val currentLux = state.luxValue
    val displayLux = if (state.unit == LightUnit.FOOT_CANDLE) currentLux * 0.092903f else currentLux
    val unitLabel = if (state.unit == LightUnit.LUX) "LUX" else "FC"

    val (statusLabel, statusColor, statusDesc) = when {
        currentLux < 5 -> Triple("Pitch Black", MaterialTheme.colorScheme.outline, "Absolute void of light")
        currentLux < 20 -> Triple("Very Dim", MaterialTheme.colorScheme.secondary, "Shadowy environment")
        currentLux < 100 -> Triple("Mood Lighting", MaterialTheme.colorScheme.primary, "Relaxed atmosphere")
        currentLux < 250 -> Triple("Residential", MaterialTheme.colorScheme.tertiary, "Comfortable interior")
        currentLux < 500 -> Triple("Office Standard", MaterialTheme.colorScheme.primary, "Optimal workspace")
        currentLux < 1000 -> Triple("Detailed Task", MaterialTheme.colorScheme.secondary, "Precision work environment")
        currentLux < 5000 -> Triple("Overcast Day", MaterialTheme.colorScheme.primary, "Bright natural ambient")
        else -> Triple("Direct Sunlight", MaterialTheme.colorScheme.primary, "Extreme light intensity")
    }

    val animatedLux by animateFloatAsState(
        targetValue = currentLux,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "LuxValue"
    )
    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(500),
        label = "StatusColor"
    )

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "LIGHT METER",
                subtitle = "Ambient illumination analytics",
                navigationIcon = {
                    ToolzExpressiveIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ToolzExpressiveIconButton(
                        onClick = { viewModel.toggleUnit() },
                        modifier = Modifier.padding(8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(if (state.unit == LightUnit.LUX) Icons.Rounded.Lightbulb else Icons.Rounded.FlashlightOn, "Toggle Unit")
                    }
                    ToolzExpressiveIconButton(
                        onClick = { viewModel.resetStats() },
                        modifier = Modifier.padding(8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset Stats")
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
                        .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp))
                        .padding(horizontal = 24.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Main Gauge
                    StaggeredEntrance(index = 0) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(280.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "glow")
                            val glowAlpha by if (performanceMode) remember { mutableFloatStateOf(0.08f) } else infiniteTransition.animateFloat(
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

                            ToolzWavyCircularProgressIndicator(
                                progress = { min(currentLux / 2000f, 1f) },
                                modifier = Modifier.size(260.dp),
                                color = animatedColor,
                                trackColor = animatedColor.copy(alpha = 0.1f)
                            )
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Rounded.LightMode,
                                    contentDescription = null,
                                    tint = animatedColor,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (displayLux < 10) "%.1f".format(displayLux) else displayLux.toInt().toString(),
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 72.sp,
                                        letterSpacing = (-2).sp
                                    )
                                )
                                Text(
                                    text = unitLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Status Chip
                    StaggeredEntrance(index = 1) {
                        Surface(
                            color = animatedColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, animatedColor.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(Modifier.size(8.dp).background(animatedColor, CircleShape))
                                Text(
                                    text = statusLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = animatedColor,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // History Sparkline
                    StaggeredEntrance(index = 2) {
                        ExpressiveCard(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                SparklineChart(
                                    data = state.history,
                                    color = animatedColor,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    "LIVE HISTORY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.align(Alignment.TopStart)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Peak & Study Info
                    StaggeredEntrance(index = 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            InfoCard(
                                modifier = Modifier.weight(1f),
                                label = "SESSION PEAK",
                                value = if (state.maxLux < 10) "%.1f".format(state.maxLux.toUnit(state.unit)) else state.maxLux.toUnit(state.unit).toInt().toString(),
                                unit = unitLabel,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            val examReady = currentLux >= 500
                            InfoCard(
                                modifier = Modifier.weight(1f),
                                label = "TASK READY",
                                value = if (examReady) "OPTIMAL" else "LOW",
                                unit = "LEVEL",
                                color = if (examReady) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Avg & Min
                    StaggeredEntrance(index = 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            InfoCard(
                                modifier = Modifier.weight(1f),
                                label = "AVERAGE",
                                value = if (state.avgLux < 10) "%.1f".format(state.avgLux.toUnit(state.unit)) else state.avgLux.toUnit(state.unit).toInt().toString(),
                                unit = unitLabel,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            InfoCard(
                                modifier = Modifier.weight(1f),
                                label = "MINIMUM",
                                value = if (state.minLux == Float.MAX_VALUE) "-" else if (state.minLux < 10) "%.1f".format(state.minLux.toUnit(state.unit)) else state.minLux.toUnit(state.unit).toInt().toString(),
                                unit = unitLabel,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

private fun Float.toUnit(unit: LightUnit): Float {
    return if (unit == LightUnit.FOOT_CANDLE) this * 0.092903f else this
}

@Composable
fun InfoCard(modifier: Modifier, label: String, value: String, unit: String, color: Color) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
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
fun SparklineChart(data: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        
        val maxVal = max(data.maxOrNull() ?: 0f, 1f)
        val minVal = data.minOrNull() ?: 0f
        val range = max(maxVal - minVal, 1f)
        
        val width = size.width
        val height = size.height
        val stepX = width / (data.size - 1)
        
        val path = Path()
        data.forEachIndexed { i, value ->
            val x = i * stepX
            val y = height - ((value - minVal) / range * height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        
        // Fill area
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.2f), Color.Transparent)
            )
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
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LightMeterScreenPreview() {
    com.frerox.toolz.ui.theme.ToolzTheme {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // We can't easily mock the ViewModel here without Hilt or manual factory, 
            // but we can use a dummy state if we refactor the screen to take state instead of ViewModel.
            // For now, let's just preview the NoSensorState or a mock-like setup if possible.
            NoSensorState()
        }
    }
}
