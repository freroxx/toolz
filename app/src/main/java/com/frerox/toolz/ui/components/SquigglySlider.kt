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

import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.ToolzTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * A seek slider with a restrained, continuous wave on the played portion.
 *
 * The inactive and active tracks share one path — the active colour is revealed
 * with a clip — so the wave never pinches, kinks or jumps at the thumb. Gestures
 * are handled directly instead of stacking an invisible Material Slider on top:
 * that older approach used two different thumb geometries, which made the drawn
 * thumb and the real touch target drift apart.
 */
@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    valueRange: ClosedFloatingPointRange<Float>,
    isPlaying: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentDescription: String? = null,
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
        contentDescription = contentDescription,
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
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentDescription: String? = null,
) {
    val density = LocalDensity.current
    val performanceMode = LocalPerformanceMode.current
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val haptic = rememberToolzHapticFeedback()
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val range = valueRange.endInclusive - valueRange.start
    val progress = if (range > 0f) {
        ((currentValue() - valueRange.start) / range).coerceIn(0f, 1f)
    } else {
        0f
    }

    // A single slow phase keeps the wave alive without looking busy.
    val infiniteTransition = rememberInfiniteTransition(label = "squiggly_phase")
    val phase by if (isPlaying && !performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "squiggly_phase_state",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // Small, calm amplitude. The old 6-8dp wave read as a vibration rather
    // than a progress indicator.
    val amplitudeDp by animateFloatAsState(
        targetValue = when {
            performanceMode || !isPlaying -> 0f
            isDragged -> 2.6f
            else -> 1.8f
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "squiggly_amplitude",
    )
    val thumbRadiusDp by animateFloatAsState(
        targetValue = if (isDragged) 8f else 6.5f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "squiggly_thumb",
    )

    val horizontalPadding = 12.dp
    val trackHeight = 4.dp
    val thumbCenterColor = MaterialTheme.colorScheme.surface

    fun valueForX(x: Float, width: Float): Float {
        val paddingPx = with(density) { horizontalPadding.toPx() }
        val usable = (width - paddingPx * 2f).coerceAtLeast(1f)
        val fraction = ((x - paddingPx) / usable).coerceIn(0f, 1f)
        return valueRange.start + fraction * range
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 2.dp)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(progress, valueRange)
                if (enabled) {
                    setProgress { target ->
                        val clamped = target.coerceIn(valueRange.start, valueRange.endInclusive)
                        currentOnValueChange(clamped)
                        currentOnValueChangeFinished()
                        true
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(enabled, valueRange, density) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        haptic.tick()
                        val dragStart = DragInteraction.Start()
                        interactionSource.tryEmit(dragStart)
                        currentOnValueChange(valueForX(down.position.x, size.width.toFloat()))

                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) break
                            currentOnValueChange(valueForX(change.position.x, size.width.toFloat()))
                            change.consume()
                            pressed = change.pressed
                        }

                        interactionSource.tryEmit(DragInteraction.Stop(dragStart))
                        currentOnValueChangeFinished()
                    }
                },
        ) {
            val paddingPx = horizontalPadding.toPx()
            val startX = paddingPx
            val endX = size.width - paddingPx
            if (endX <= startX) return@Canvas

            val centerY = size.height / 2f
            val usable = endX - startX
            val thumbX = startX + usable * progress
            val amplitude = amplitudeDp.dp.toPx()
            val strokeWidth = trackHeight.toPx()

            val trackPath = Path().apply {
                moveTo(startX, centerY)
                if (amplitude <= 0.05f) {
                    lineTo(endX, centerY)
                } else {
                    val step = 2.dp.toPx()
                    val frequency = 42.dp.toPx()
                    val edgeFade = 14.dp.toPx()
                    var x = 0f
                    while (x < usable) {
                        val fadeIn = (x / edgeFade).coerceIn(0f, 1f)
                        val fadeOut = ((usable - x) / edgeFade).coerceIn(0f, 1f)
                        val envelope = fadeIn * fadeOut
                        val y = centerY + amplitude * envelope *
                            sin((2 * PI * x / frequency) - phase).toFloat()
                        lineTo(startX + x, y)
                        x += step
                    }
                    lineTo(endX, centerY)
                }
            }

            drawPath(
                path = trackPath,
                color = inactiveColor.copy(alpha = 0.5f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            clipRect(left = startX, top = 0f, right = thumbX, bottom = size.height) {
                drawPath(
                    path = trackPath,
                    color = activeColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            if (isDragged) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.14f),
                    radius = 16.dp.toPx(),
                    center = Offset(thumbX, centerY),
                )
            }
            drawCircle(
                color = activeColor,
                radius = thumbRadiusDp.dp.toPx(),
                center = Offset(thumbX, centerY),
            )
            drawCircle(
                color = thumbCenterColor,
                radius = 3.dp.toPx(),
                center = Offset(thumbX, centerY),
            )
        }
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
        ) {
            SquigglySlider(
                value = value,
                onValueChange = { value = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
