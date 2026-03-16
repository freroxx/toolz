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
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    // Compatibility check: In performance mode (isPlaying = false), we skip animations
    val infiniteTransition = rememberInfiniteTransition(label = "wave_motion")
    val phase by if (isPlaying) {
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
        targetValue = if (isPlaying && !isDragged) 6f else if (isDragged) 8f else 0f,
        animationSpec = if (isPlaying) spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow) else snap(),
        label = "amplitude_state"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragged) 1.2f else 1f,
        animationSpec = if (isPlaying) spring(Spring.DampingRatioMediumBouncy) else snap(),
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
                    val shadowPath = Path()
                    onDrawBehind {
                        val horizontalPadding = 16.dp.toPx()
                        val canvasWidth = size.width - (horizontalPadding * 2)
                        val centerY = size.height / 2

                        val range = valueRange.endInclusive - valueRange.start
                        val progress = if (range > 0) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
                        val thumbX = horizontalPadding + (canvasWidth * progress)

                        // 1. Inactive Track (Subtle glass line)
                        drawLine(
                            color = inactiveColor.copy(alpha = 0.3f),
                            start = Offset(thumbX, centerY),
                            end = Offset(size.width - horizontalPadding, centerY),
                            strokeWidth = 6.dp.toPx(),
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
                            // 2. Active Squiggly Path (Glow & Multi-layer)
                            val waveFreq = 36.dp.toPx()
                            val maxAmp = currentAmplitude.dp.toPx()
                            val taperDistance = 24.dp.toPx()

                            path.reset()
                            shadowPath.reset()
                            path.moveTo(horizontalPadding, centerY)
                            shadowPath.moveTo(horizontalPadding, centerY + 2.dp.toPx())

                            val activeWidth = thumbX - horizontalPadding
                            val step = if (isPlaying) 1.5f else 4f // Coarser step in low performance
                            var x = 0f

                            while (x < activeWidth) {
                                val startTaper = (x / taperDistance).coerceIn(0f, 1f)
                                val endTaper = ((activeWidth - x) / taperDistance).coerceIn(0f, 1f)
                                val taperedAmp = maxAmp * startTaper * endTaper

                                val y = centerY + taperedAmp * sin((2 * PI * x / waveFreq) - phase).toFloat()
                                path.lineTo(horizontalPadding + x, y)
                                shadowPath.lineTo(horizontalPadding + x, y + 2.dp.toPx())
                                x += step
                            }

                            path.lineTo(thumbX, centerY)
                            shadowPath.lineTo(thumbX, centerY + 2.dp.toPx())

                            // Draw shadow layer first
                            drawPath(
                                path = shadowPath,
                                color = activeColor.copy(alpha = 0.1f),
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Draw main glow layer
                            drawPath(
                                path = path,
                                color = activeColor.copy(alpha = 0.2f),
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Draw main line
                            drawPath(
                                path = path,
                                color = activeColor,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // 3. Premium Thumb
                        // Shadow
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.2f),
                            radius = (12.dp.toPx() * thumbScale) + 4.dp.toPx(),
                            center = Offset(thumbX, centerY + 2.dp.toPx())
                        )
                        // Outer Glow (Skip in performance mode if needed, but keeping for visual consistency)
                        if (isPlaying) {
                            drawCircle(
                                color = activeColor.copy(alpha = 0.3f),
                                radius = 16.dp.toPx() * thumbScale,
                                center = Offset(thumbX, centerY)
                            )
                        }
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
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
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
