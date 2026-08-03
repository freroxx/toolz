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

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.ui.theme.LocalVibrationManager
import java.util.concurrent.TimeUnit

/**
 * Animated step counter card with live stats: steps, duration, distance, calories.
 * Designed for the main step screen header — replaces the simple step number display.
 * 
 * Each stat animates incrementally as values change, using a count-up effect.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StepSessionCard(
    stepCount: Int,
    sessionStartMs: Long,
    stepLengthCm: Float,
    caloriesPer1k: Float,
    modifier: Modifier = Modifier
) {
    val vibrationManager = LocalVibrationManager.current
    
    // Animate step count with spring
    val animatedSteps by animateIntAsState(
        targetValue = stepCount,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "StepsSpring"
    )
    
    // Duration calculation
    val durationMs = if (sessionStartMs > 0) {
        System.currentTimeMillis() - sessionStartMs
    } else 0L
    
    val distanceKm = (stepCount * stepLengthCm / 100000f)
    val calories = (stepCount / 1000f) * caloriesPer1k
    
    val durationText = formatDuration(durationMs)
    
    ExpressiveCard(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main step count
            Text(
                text = "%,d".format(animatedSteps),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                ),
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "steps",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip(
                    icon = Icons.Rounded.Timer,
                    value = durationText,
                    label = "Duration"
                )
                StatChip(
                    icon = Icons.Rounded.Route,
                    value = "%.2f".format(distanceKm),
                    label = "km"
                )
                StatChip(
                    icon = Icons.Rounded.LocalFireDepartment,
                    value = "%.0f".format(calories),
                    label = "kcal"
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Session indicator
            if (sessionStartMs > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    PulsingDot(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Session active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    text = "Start walking to track",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "PulsingDot")

    // Simple alpha-only glow (no scaling) for a cleaner feel
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotRingAlpha"
    )

    val coreAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotCoreAlpha"
    )

    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(11.dp))
    ) {
        // Soft glow ring
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(18.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(color.copy(alpha = ringAlpha))
        )
        // Fixed core
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = coreAlpha))
        )
    }
}

private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
