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
    onValueChangeFinished: () -> Unit = {},
    valueRange: ClosedFloatingPointRange<Float>,
    isPlaying: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "wave_motion")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_state"
    )

    val currentAmplitude by animateFloatAsState(
        targetValue = if (isPlaying && !isDragged) 5f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "amplitude_state"
    )

    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .drawWithCache {
                    val path = Path()
                    onDrawBehind {
                        val horizontalPadding = 12.dp.toPx()
                        val canvasWidth = size.width - (horizontalPadding * 2)
                        val centerY = size.height / 2

                        val range = valueRange.endInclusive - valueRange.start
                        val progress = if (range > 0) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
                        val thumbX = horizontalPadding + (canvasWidth * progress)

                        val waveFreq = 32.dp.toPx()
                        val maxAmp = currentAmplitude.dp.toPx()

                        // NEW: Taper distance ensures the wave starts and ends at 0 height
                        val taperDistance = 20.dp.toPx()

                        // 1. Draw Inactive Track (Right side)
                        drawLine(
                            color = inactiveColor,
                            start = Offset(thumbX, centerY),
                            end = Offset(size.width - horizontalPadding, centerY),
                            strokeWidth = 4.dp.toPx(), // Slightly thinner for M3 look
                            cap = StrokeCap.Round
                        )

                        // 2. Draw Active Squiggly Path (Left side)
                        path.reset()
                        path.moveTo(horizontalPadding, centerY)

                        val activeWidth = thumbX - horizontalPadding
                        val step = 2f // Smaller step = smoother curve on high-res screens
                        var x = 0f

                        while (x < activeWidth) {
                            // CALCULATE TAPER:
                            // This scales the amplitude down to 0 at the start and end of the active line.
                            val startTaper = (x / taperDistance).coerceIn(0f, 1f)
                            val endTaper = ((activeWidth - x) / taperDistance).coerceIn(0f, 1f)
                            val taperedAmp = maxAmp * startTaper * endTaper

                            val y = centerY + taperedAmp * sin((2 * PI * x / waveFreq) - phase).toFloat()
                            path.lineTo(horizontalPadding + x, y)
                            x += step
                        }

                        // Ensure we connect perfectly to the thumb's center
                        path.lineTo(thumbX, centerY)

                        drawPath(
                            path = path,
                            color = activeColor,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // 3. Draw Thumb (Always present)
                        drawCircle(
                            color = activeColor,
                            radius = if (isDragged) 10.dp.toPx() else 8.dp.toPx(),
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