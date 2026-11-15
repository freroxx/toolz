package com.frerox.toolz.ui.components

import android.content.res.Configuration
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.liquidglass.LocalLiquidGlassEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
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
    enabled: Boolean = true,
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
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current

    LaunchedEffect(isDragged) {
        if (isDragged) haptic.tick()
    }

    // Performance optimization: skip animations in performance mode or when paused
    val performanceMode = LocalPerformanceMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "wave_motion")

    // The phase advances only when playing and not in performance mode.
    // When paused, animating is paused completely to save 120fps GPU cycles.
    val phase by if (!performanceMode && isPlaying) {
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

    // Fast, snappy amplitude/thumb so tap/drag feels immediate, not sluggish.
    val currentAmplitude by animateFloatAsState(
        targetValue = when {
            isDragged -> 8f
            isPlaying && !performanceMode -> 6f
            else -> 0f
        },
        animationSpec = tween(
            durationMillis = if (isDragged) 140 else if (isPlaying) 350 else 250,
            easing = FastOutSlowInEasing
        ),
        label = "amplitude_state"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragged) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
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
                            color = if (liquidGlassEnabled) inactiveColor.copy(alpha = 0.35f) else inactiveColor.copy(alpha = 0.24f),
                            start = Offset(thumbX, centerY),
                            end = Offset(size.width - horizontalPadding, centerY),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        if (!isPlaying && currentAmplitude == 0f) {
                            // Simple linear track for performance mode / paused
                            if (liquidGlassEnabled && !performanceMode) {
                                // Subtle refractive aura along line
                                drawLine(
                                    color = activeColor.copy(alpha = 0.25f),
                                    start = Offset(horizontalPadding, centerY),
                                    end = Offset(thumbX, centerY),
                                    strokeWidth = 8.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                            drawLine(
                                color = activeColor,
                                start = Offset(horizontalPadding, centerY),
                                end = Offset(thumbX, centerY),
                                strokeWidth = 4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        } else {
                            // 2. Active Squiggly Path
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

                            // Liquid glass refractive aura behind squiggly wave
                            if (liquidGlassEnabled && !performanceMode) {
                                drawPath(
                                    path = path,
                                    color = activeColor.copy(alpha = 0.28f),
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            // Main crisp line
                            drawPath(
                                path = path,
                                color = activeColor,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // 3. Thumb Drawing
                        if (liquidGlassEnabled && !performanceMode) {
                            // Authentic Liquid Glass Jewel Orb
                            val orbRadius = 12.dp.toPx() * thumbScale

                            // Ambient soft drop shadow
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.16f),
                                radius = orbRadius + 3.dp.toPx(),
                                center = Offset(thumbX, centerY + 2.dp.toPx())
                            )

                            // Refractive glass body with radial dispersion
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.65f),
                                        activeColor.copy(alpha = 0.45f),
                                        activeColor.copy(alpha = 0.90f)
                                    ),
                                    center = Offset(thumbX - 3.dp.toPx(), centerY - 3.dp.toPx()),
                                    radius = orbRadius
                                ),
                                radius = orbRadius,
                                center = Offset(thumbX, centerY)
                            )

                            // Specular glass edge highlight (lens refraction ring)
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.85f),
                                        activeColor.copy(alpha = 0.35f),
                                        Color(0xFFC4B5FD).copy(alpha = 0.70f),
                                        Color.White.copy(alpha = 0.85f)
                                    ),
                                    center = Offset(thumbX, centerY)
                                ),
                                radius = orbRadius,
                                center = Offset(thumbX, centerY),
                                style = Stroke(width = 1.5.dp.toPx())
                            )

                            // Inner gleaming highlight bead
                            drawCircle(
                                color = Color.White,
                                radius = 3.5.dp.toPx() * thumbScale,
                                center = Offset(thumbX - 2.5.dp.toPx() * thumbScale, centerY - 2.5.dp.toPx() * thumbScale)
                            )
                        } else {
                            // Clean Material 3 Expressive Thumb
                            drawCircle(
                                color = activeColor,
                                radius = 10.dp.toPx() * thumbScale,
                                center = Offset(thumbX, centerY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4.dp.toPx() * thumbScale,
                                center = Offset(thumbX, centerY)
                            )
                        }
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
