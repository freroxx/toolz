package com.kyant.backdrop.catalog.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.utils.DampedDragAnimation
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val isLightTheme = !isSystemInDarkTheme()
    val performanceMode = LocalPerformanceMode.current
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()

        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.45f,
                onDragStarted = {},
                onDragStopped = {
                    currentOnValueChangeFinished?.invoke()
                },
                onDrag = { _, _ -> }
            )
        }

        var dragFraction by remember { mutableFloatStateOf(-1f) }

        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }
                .collectLatest { v ->
                    if (dragFraction < 0f && dampedDragAnimation.targetValue != v) {
                        dampedDragAnimation.updateValue(v)
                    }
                }
        }

        val activeProgress = (if (dragFraction >= 0f) dragFraction else dampedDragAnimation.progress).coerceIn(0f, 1f)

        // Entire slider area touch & drag handler: 44dp tall, smooth 120fps tracking
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(trackWidth, range, isLtr) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val fraction = (down.position.x / trackWidth).coerceIn(0f, 1f)
                        dragFraction = fraction
                        dampedDragAnimation.press()

                        val targetVal = if (isLtr) {
                            valueRange.start + fraction * range
                        } else {
                            valueRange.endInclusive - fraction * range
                        }.coerceIn(valueRange)
                        currentOnValueChange(targetVal)

                        val pointerId = down.id
                        var lastVal = targetVal
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.fastFirstOrNull { it.id == pointerId }
                            if (change == null || change.changedToUpIgnoreConsumed()) {
                                break
                            }

                            val dragAmount = change.positionChange()
                            if (dragAmount != Offset.Zero) {
                                change.consume()
                                val currentFraction = (change.position.x / trackWidth).coerceIn(0f, 1f)
                                dragFraction = currentFraction
                                val dragVal = if (isLtr) {
                                    valueRange.start + currentFraction * range
                                } else {
                                    valueRange.endInclusive - currentFraction * range
                                }.coerceIn(valueRange)
                                lastVal = dragVal
                                currentOnValueChange(dragVal)
                            }
                        }

                        dampedDragAnimation.snapToValue(lastVal)
                        dragFraction = -1f
                        dampedDragAnimation.release()
                        currentOnValueChangeFinished?.invoke()
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track layer
            Box(
                Modifier
                    .fillMaxWidth()
                    .layerBackdrop(trackBackdrop)
            ) {
                Box(
                    Modifier
                        .clip(Capsule())
                        .background(trackColor)
                        .height(6f.dp)
                        .fillMaxWidth()
                )

                Box(
                    Modifier
                        .clip(Capsule())
                        .background(accentColor)
                        .height(6f.dp)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val width = (constraints.maxWidth * activeProgress).fastRoundToInt()
                            layout(width, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                )
            }

            // Authentic 3D Liquid Glass Thumb
            Box(
                Modifier
                    .graphicsLayer {
                        translationX =
                            (-size.width / 2f + trackWidth * activeProgress) * if (isLtr) 1f else -1f
                    }
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                                val progress = dampedDragAnimation.pressProgress
                                val scaleX = lerp(2f / 3f, 1f, progress)
                                val scaleY = lerp(0f, 1f, progress)
                                scale(scaleX, scaleY) {
                                    drawBackdrop()
                                }
                            }
                        ),
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            val blurPx = if (performanceMode) 4f.dp.toPx() else 8f.dp.toPx()
                            blur(blurPx * (1f - progress))
                            lens(
                                refractionHeight = (if (performanceMode) 6f else 10f).dp.toPx() * progress,
                                refractionAmount = (if (performanceMode) 8f else 14f).dp.toPx() * progress,
                                depthEffect = !performanceMode,
                                chromaticAberration = !performanceMode
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = progress
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = if (performanceMode) 2f.dp else 4f.dp,
                                color = Color.Black.copy(alpha = if (performanceMode) 0.08f else 0.12f)
                            )
                        },
                        innerShadow = {
                            if (!performanceMode) {
                                val progress = dampedDragAnimation.pressProgress
                                InnerShadow(
                                    radius = 4f.dp * progress,
                                    alpha = progress
                                )
                            } else null
                        },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(Color.White.copy(alpha = 1f - progress))
                        }
                    )
                    .size(40f.dp, 24f.dp)
            )
        }
    }
}
