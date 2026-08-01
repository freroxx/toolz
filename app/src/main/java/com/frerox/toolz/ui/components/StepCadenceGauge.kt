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

package com.frerox.toolz.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated arc gauge showing real-time step cadence (steps/min).
 *
 * Visual zones:
 * - Red (<80): Very slow / idle
 * - Yellow (80-110): Normal walking
 * - Green (110-150): Brisk walk / jog
 * - Blue (>150): Running
 *
 * The needle animates smoothly using spring physics.
 */
@Composable
fun StepCadenceGauge(
    cadence: () -> Int,
    modifier: Modifier = Modifier,
    showNeedle: Boolean = true
) {
    val currentCadence by rememberUpdatedState(cadence())

    // Spring-animated cadence display
    val animatedCadence by animateFloatAsState(
        targetValue = currentCadence.toFloat().coerceIn(0f, 200f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "CadenceSpring"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .padding(8.dp)
        ) {
            val centerX = size.width / 2
            val centerY = size.height * 0.85f
            val radius = minOf(centerX, centerY) * 0.85f

            val arcWidth = size.width * 0.06f
            val startAngle = 200f
            val sweepAngle = 140f

            // Background track
            drawArc(
                color = surfaceColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = arcWidth, cap = StrokeCap.Round)
            )

            // Zone arcs: each zone is a pair of (startRatio, endRatio, color)
            val zoneRed = Triple(0f, 0.4f, Color(0xFFE57373).copy(alpha = 0.3f))
            val zoneYellow = Triple(0.4f, 0.55f, Color(0xFFFFD54F).copy(alpha = 0.3f))
            val zoneGreen = Triple(0.55f, 0.75f, Color(0xFF81C784).copy(alpha = 0.3f))
            val zoneBlue = Triple(0.75f, 1.0f, Color(0xFF64B5F6).copy(alpha = 0.3f))

            listOf(zoneRed, zoneYellow, zoneGreen, zoneBlue).forEach { (s, e, color) ->
                val zoneStart = startAngle + sweepAngle * s
                val zoneSweep = sweepAngle * (e - s)
                drawArc(
                    color = color,
                    startAngle = zoneStart,
                    sweepAngle = zoneSweep,
                    useCenter = false,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = arcWidth, cap = StrokeCap.Butt)
                )
            }

            // Active arc fill to current cadence
            val cadenceRatio = (animatedCadence / 200f).coerceIn(0f, 1f)
            val activeSweep = sweepAngle * cadenceRatio
            drawArc(
                color = cadenceColor(animatedCadence.toInt()),
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = arcWidth, cap = StrokeCap.Round)
            )

            // Needle
            if (showNeedle) {
                val needleAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
                val needleLen = radius * 0.7f
                val needleX = centerX + (needleLen * cos(needleAngleRad)).toFloat()
                val needleY = centerY + (needleLen * sin(needleAngleRad)).toFloat()

                drawLine(
                    color = primaryColor,
                    start = Offset(centerX, centerY),
                    end = Offset(needleX, needleY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center dot
                drawCircle(
                    color = primaryColor,
                    radius = 8.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
            }
        }

        // Cadence number with slide animation
        AnimatedContent(
            targetState = currentCadence,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInVertically { -it } togetherWith slideOutVertically { it }
                } else {
                    slideInVertically { it } togetherWith slideOutVertically { -it }
                }
            },
            label = "CadenceNumber"
        ) { cad ->
            Text(
                text = "$cad",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = cadenceColor(cad)
            )
        }

        Text(
            text = "steps/min",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

private fun cadenceColor(cadence: Int): Color {
    return when {
        cadence < 80 -> Color(0xFFE57373)   // Red - idle/slow
        cadence < 110 -> Color(0xFFFFD54F) // Yellow - normal walk
        cadence < 150 -> Color(0xFF81C784) // Green - brisk walk/jog
        else -> Color(0xFF64B5F6)           // Blue - running
    }
}
