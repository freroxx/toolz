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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SonicPulseGauge(
    speed: Float,
    progress: Float,
    unit: String,
    precision: Int,
    modifier: Modifier = Modifier
) {
    val performanceMode = LocalPerformanceMode.current
    
    // Adaptive color based on speed progress
    val targetColor = when {
        progress < 0.2f -> Color(0xFF00E5FF) // Cyan
        progress < 0.5f -> Color(0xFF00E676) // Green
        progress < 0.8f -> Color(0xFFFFAB40) // Orange
        else -> Color(0xFFFF5252) // Red
    }
    
    val adaptiveColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(1000),
        label = "AdaptiveColor"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        // Sonic Pulse Background
        if (!performanceMode) {
            val infiniteTransition = rememberInfiniteTransition(label = "SonicPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1f + (progress * 0.15f),
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "PulseScale"
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.05f,
                targetValue = 0.1f + (progress * 0.2f),
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "PulseAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .clip(SquircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(adaptiveColor, Color.Transparent)
                        )
                    )
            )
        }

        // Main Display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.getDefault(), "%.${precision}f", speed),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-6).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.titleMedium,
                color = adaptiveColor,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
        }

        // Circular Progress
        SpeedometerCircularProgress(
            progress = progress,
            color = adaptiveColor,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SpeedometerCircularProgress(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "Progress"
    )

    Canvas(modifier = modifier.padding(12.dp)) {
        val strokeWidth = 12.dp.toPx()
        val radius = (size.minDimension / 2) - (strokeWidth / 2)
        
        // Track
        drawCircle(
            color = color.copy(alpha = 0.1f),
            radius = radius,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Progress Arc
        rotate(-90f) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun SpeedSparkline(
    history: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    Canvas(modifier = modifier) {
        val maxVal = (history.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val width = size.width
        val height = size.height
        val stepX = width / (history.size - 1).coerceAtLeast(1)
        
        val path = Path()
        history.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - (value / maxVal * height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Gradient under the path
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.2f), Color.Transparent)
            )
        )
    }
}
