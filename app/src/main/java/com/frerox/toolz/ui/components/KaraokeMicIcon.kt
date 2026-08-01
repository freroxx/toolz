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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.screens.media.rememberDynamicColors
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// KaraokeMicIcon — M3 Expressive pill-shaped mic button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun KaraokeMicIcon(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 26.dp,
    thumbnailUri: String? = null,
    isLoading: Boolean = false,
    micRms: Float = 0f
) {
    val dynamicColors = rememberDynamicColors(thumbnailUri)

    // Animated container color — idle = surfaceContainerHigh, active = vibrant primary tint
    val containerColor by animateColorAsState(
        targetValue = if (isActive)
            dynamicColors.primary.copy(alpha = 0.18f)
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "micContainer"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isActive)
            dynamicColors.primary.copy(alpha = 0.7f)
        else
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        animationSpec = tween(350),
        label = "micBorder"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isActive) dynamicColors.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(350),
        label = "micIcon"
    )

    // Gentle press scale
    val pressedScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "pressScale"
    )

    // Infinite glow pulse when active
    val inf = rememberInfiniteTransition(label = "micInf")
    val glowPulse by if (isActive) {
        inf.animateFloat(
            0.5f, 1f,
            infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glowPulse"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // RMS-driven mic icon scale
    val rmsBoost = if (isActive && micRms > -40f) {
        ((micRms + 40f) / 40f).coerceIn(0f, 1f) * 0.18f
    } else 0f
    val micIconScale by animateFloatAsState(
        targetValue = 1f + rmsBoost,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "rmsScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring — only when active
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(size + 16.dp)
                    .graphicsLayer {
                        alpha = glowPulse * 0.55f
                        scaleX = 1f + glowPulse * 0.06f
                        scaleY = 1f + glowPulse * 0.06f
                    }
                    .background(
                        Brush.radialGradient(
                            listOf(
                                dynamicColors.primary.copy(alpha = 0.45f),
                                dynamicColors.primary.copy(alpha = 0.0f)
                            )
                        ),
                        RoundedCornerShape(50)
                    )
            )
        }

        Surface(
            onClick = onClick,
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = containerColor,
            border = androidx.compose.foundation.BorderStroke(
                if (isActive) 2.dp else 1.dp,
                borderColor
            ),
            shadowElevation = if (isActive) 10.dp else 2.dp,
            tonalElevation = if (isActive) 6.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize + 6.dp),
                        color = dynamicColors.primary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    // Waveform bars alongside icon when active
                    if (isActive) {
                        RmsWaveformBars(
                            micRms = micRms,
                            color = dynamicColors.primary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = if (isActive) "Stop Recording" else "Start Recording",
                        tint = iconColor,
                        modifier = Modifier
                            .size(iconSize)
                            .graphicsLayer {
                                scaleX = micIconScale
                                scaleY = micIconScale
                            }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RmsWaveformBars — 5 animated bars driven by mic RMS + infinite wave
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RmsWaveformBars(
    micRms: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "waveInf")

    // Base wave phase
    val phase by inf.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "wavePhase"
    )

    // Normalize RMS: -60dB..0dB → 0..1
    val rmsNorm = ((micRms + 60f) / 60f).coerceIn(0f, 1f)

    val bars = 5
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(bars) { i ->
                val sinVal = sin(phase + i * 0.8f).toFloat()
                val barHeightFrac = (0.18f + rmsNorm * 0.45f + sinVal * 0.15f * rmsNorm).coerceIn(0.08f, 0.9f)
                val animatedHeight by animateFloatAsState(
                    targetValue = barHeightFrac,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                    label = "bar$i"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(animatedHeight)
                        .padding(horizontal = 1.5.dp)
                        .background(
                            color.copy(alpha = 0.35f + rmsNorm * 0.25f),
                            RoundedCornerShape(50)
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SmallKaraokeMicIcon — compact pill used in track rows
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SmallKaraokeMicIcon(
    isVisible: Boolean,
    isActive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailUri: String? = null,
    isLoading: Boolean = false,
    micRms: Float = 0f
) {
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "smallMicScale"
    )

    if (isVisible || scale > 0.01f) {
        KaraokeMicIcon(
            isActive = isActive,
            onClick = onClick,
            modifier = modifier.scale(scale),
            size = 38.dp,
            iconSize = 19.dp,
            thumbnailUri = thumbnailUri,
            isLoading = isLoading,
            micRms = micRms
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LiveMicIcon — pulsing status badge inside active karaoke
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LiveMicIcon(
    isRecording: Boolean,
    thumbnailUri: String? = null,
    modifier: Modifier = Modifier
) {
    val dynamicColors = rememberDynamicColors(thumbnailUri)
    val inf = rememberInfiniteTransition(label = "liveMicInf")

    val pulse by inf.animateFloat(
        1f, 1.28f,
        infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "livePulse"
    )

    val color by animateColorAsState(
        targetValue = if (isRecording) Color(0xFFFF1744) else dynamicColors.primary,
        animationSpec = tween(400),
        label = "liveMicColor"
    )

    Box(
        modifier = modifier
            .size(32.dp)
            .scale(if (isRecording) pulse else 1f),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = 0.15f), CircleShape)
            )
        }
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = if (isRecording) "Recording" else "Mic",
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
