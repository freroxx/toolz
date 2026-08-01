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

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import com.frerox.toolz.ui.theme.ToolzTheme
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    valueRange: ClosedFloatingPointRange<Float>,
    isPlaying: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    SquigglySlider(
        value = { value },
        onValueChange = onValueChange,
        modifier = modifier,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        isPlaying = isPlaying,
        activeColor = activeColor,
        inactiveColor = inactiveColor,
    )
}

@Composable
fun SquigglySlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true, // Added for compatibility if needed, but not in original. Wait, the original didn't have enabled.
    onValueChangeFinished: () -> Unit = {},
    valueRange: ClosedFloatingPointRange<Float>,
    isPlaying: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val haptic = rememberToolzHapticFeedback()
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    LaunchedEffect(isDragged) {
        if (isDragged) haptic.tick()
    }

    // Compatibility check: In performance mode, we skip animations
    val performanceMode = LocalPerformanceMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "wave_motion")
    val phase by if (isPlaying && !performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase_state"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val currentAmplitude by animateFloatAsState(
        targetValue = if (isPlaying && !isDragged && !performanceMode) 6f else if (isDragged) 8f else 0f,
        animationSpec = if (isPlaying && !performanceMode) spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow) else snap(),
        label = "amplitude_state"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragged) 1.2f else 1f,
        animationSpec = if (isPlaying && !performanceMode) spring(Spring.DampingRatioMediumBouncy) else snap(),
        label = "thumb_scale"
    )

    Box(
        modifier = modifier.height(56.dp).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Track layer
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .drawWithCache {
                    val path = Path()
                    onDrawBehind {
                        val horizontalPadding = 16.dp.toPx()
                        val canvasWidth = size.width - (horizontalPadding * 2)
                        val centerY = size.height / 2

                        val range = valueRange.endInclusive - valueRange.start
                        val sliderValue = value()
                        val progress = if (range > 0) {
                            ((sliderValue - valueRange.start) / range).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val thumbX = horizontalPadding + (canvasWidth * progress)

                        // 1. Inactive Track (Clean simple line)
                        drawLine(
                            color = inactiveColor.copy(alpha = 0.24f),
                            start = Offset(thumbX, centerY),
                            end = Offset(size.width - horizontalPadding, centerY),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        if (!isPlaying && currentAmplitude == 0f) {
                            // Simple linear track for performance mode
                            drawLine(
                                color = activeColor,
                                start = Offset(horizontalPadding, centerY),
                                end = Offset(thumbX, centerY),
                                strokeWidth = 4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        } else {
                            // 2. Active Squiggly Path (Single clean expressive line)
                            val waveFreq = 32.dp.toPx()
                            val maxAmp = currentAmplitude.dp.toPx()
                            val taperDistance = 24.dp.toPx()

                            path.reset()
                            path.moveTo(horizontalPadding, centerY)

                            val activeWidth = thumbX - horizontalPadding
                            val step = 2.dp.toPx() // Density-aware step for smooth, high-performance drawing
                            var x = 0f

                            while (x < activeWidth) {
                                val startTaper = (x / taperDistance).coerceIn(0f, 1f)
                                val endTaper = ((activeWidth - x) / taperDistance).coerceIn(0f, 1f)
                                val taperedAmp = maxAmp * startTaper * endTaper

                                val y = centerY + taperedAmp * sin((2 * PI * x / waveFreq) - phase).toFloat()
                                path.lineTo(horizontalPadding + x, y)
                                x += step
                            }

                            path.lineTo(thumbX, centerY)

                            // Draw main line
                            drawPath(
                                path = path,
                                color = activeColor,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // 3. Clean Material 3 Expressive Thumb (No glow/shadow halos)
                        // Main Thumb
                        drawCircle(
                            color = activeColor,
                            radius = 10.dp.toPx() * thumbScale,
                            center = Offset(thumbX, centerY)
                        )
                        // Inner Dot
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx() * thumbScale,
                            center = Offset(thumbX, centerY)
                        )
                    }
                }
        )

        Slider(
            value = value(),
            onValueChange = currentOnValueChange,
            onValueChangeFinished = {
                haptic.click()
                currentOnValueChangeFinished()
            },
            valueRange = valueRange,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SquigglySliderPreview() {
    var value by remember { mutableFloatStateOf(0.4f) }
    ToolzTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            SquigglySlider(
                value = value,
                onValueChange = { value = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
