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
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.ToolzTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Premium loading wheel with expressive line animations.
 */
@Composable
fun ExpressiveLoadingWheel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDesc: String = "Loading",
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_wheel")
    
    val startValue = if (LocalInspectionMode.current) 0F else 1F
    val numLines = 12
    val floatAnimValues = (0 until numLines).map { remember { Animatable(startValue) } }
    
    LaunchedEffect(floatAnimValues) {
        (0 until numLines).map { index ->
            launch {
                floatAnimValues[index].animateTo(
                    targetValue = 0F,
                    animationSpec = tween(
                        durationMillis = 150,
                        easing = FastOutSlowInEasing,
                        delayMillis = 40 * index,
                    ),
                )
            }
        }
    }

    val rotationAnim by infiniteTransition.animateFloat(
        initialValue = 0F,
        targetValue = 360F,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
        ),
        label = "wheel_rotation",
    )

    val progressLineColor = MaterialTheme.colorScheme.inversePrimary

    val colorAnimValues = (0 until numLines).map { index ->
        infiniteTransition.animateColor(
            initialValue = color,
            targetValue = color,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 5000
                    progressLineColor at 5000 / numLines / 2 using LinearEasing
                    color at 5000 / numLines using LinearEasing
                },
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(5000 / numLines / 2 * index),
            ),
            label = "wheel_color",
        )
    }

    Canvas(
        modifier = modifier
            .size(56.dp)
            .padding(8.dp)
            .semantics { contentDescription = contentDesc }
            .graphicsLayer { rotationZ = rotationAnim }
    ) {
        repeat(numLines) { index ->
            rotate(degrees = index * 30f) {
                drawLine(
                    color = colorAnimValues[index].value,
                    alpha = if (floatAnimValues[index].value < 1f) 1f else 0f,
                    strokeWidth = 5F,
                    cap = StrokeCap.Round,
                    start = Offset(size.width / 2, size.height / 3.5f),
                    end = Offset(size.width / 2, floatAnimValues[index].value * size.height / 3.5f),
                )
            }
        }
    }
}

/**
 * Indeterminate loading indicator with particle-like motion.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val performanceMode = LocalPerformanceMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "loading_expressive")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Canvas(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                rotationZ = rotation
                if (!performanceMode) {
                    scaleX = scale
                    scaleY = scale
                }
            }
    ) {
        val sweepAngle = 280f
        val strokeWidth = 4.dp.toPx()
        
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        if (!performanceMode) {
            drawCircle(
                color = color.copy(alpha = 0.3f),
                radius = size.width / 2.5f,
                center = center
            )
        }
    }
}


@Composable
fun ExpressivePulseIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val performanceMode = LocalPerformanceMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulseIndicator")
    val outerScale by if (performanceMode) {
        remember { mutableFloatStateOf(1f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseOuterScale",
        )
    }
    val outerAlpha by if (performanceMode) {
        remember { mutableFloatStateOf(0.2f) }
    } else {
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseOuterAlpha",
        )
    }

    Canvas(modifier = modifier) {
        val minSide = size.minDimension
        val outerRadius = (minSide * 0.45f) * outerScale
        val innerRadius = minSide * 0.2f

        drawCircle(
            color = color.copy(alpha = outerAlpha),
            radius = outerRadius,
            center = center,
        )
        if (!performanceMode) {
            drawCircle(
                color = color.copy(alpha = 0.1f),
                radius = minSide * 0.35f,
                center = center,
            )
        }
        drawCircle(
            color = color,
            radius = innerRadius,
            center = center,
        )
    }
}

@Composable
fun ExpressiveTypingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "typing")
        
        repeat(3) { i ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 150)
                ),
                label = "dot$i"
            )
            
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(color, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

/**
 * Official Material 3 Expressive Progress Indicators.
 */

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LoadingIndicator(
        modifier = modifier,
        color = color
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeCap: StrokeCap = StrokeCap.Butt,
) = ToolzWavyLinearProgressIndicator(modifier = modifier, color = color, trackColor = trackColor, strokeCap = strokeCap)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeCap: StrokeCap = StrokeCap.Butt,
) = ToolzWavyLinearProgressIndicator(progress, modifier, color, trackColor, strokeCap)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveWavyLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeCap: StrokeCap = StrokeCap.Butt,
) = ToolzWavyLinearProgressIndicator(modifier = modifier, color = color, trackColor = trackColor, strokeCap = strokeCap)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveWavyLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeCap: StrokeCap = StrokeCap.Butt,
) = ToolzWavyLinearProgressIndicator(progress, modifier, color, trackColor, strokeCap)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeCap: StrokeCap = StrokeCap.Butt,
) = ToolzWavyCircularProgressIndicator(modifier = modifier, color = color, trackColor = trackColor, strokeCap = strokeCap)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeCap: StrokeCap = StrokeCap.Butt,
) = ToolzWavyCircularProgressIndicator(progress, modifier, color, trackColor, strokeCap)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzWavyLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeCap: StrokeCap = StrokeCap.Butt,
) {
    val performanceMode = LocalPerformanceMode.current
    if (performanceMode) {
        LinearProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        return
    }

    LinearWavyProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzWavyLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeCap: StrokeCap = StrokeCap.Butt,
) {
    val performanceMode = LocalPerformanceMode.current
    if (performanceMode) {
        LinearProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        return
    }

    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzWavyCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeCap: StrokeCap = StrokeCap.Butt,
) {
    val performanceMode = LocalPerformanceMode.current
    if (performanceMode) {
        CircularProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        return
    }

    CircularWavyProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzWavyCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    strokeCap: StrokeCap = StrokeCap.Butt,
) {
    val performanceMode = LocalPerformanceMode.current
    if (performanceMode) {
        CircularProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        return
    }

    CircularWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

/**
 * Expressive refresh indicator based on Material 3.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val scaleFraction = {
        if (isRefreshing) 1f
        else LinearOutSlowInEasing
            .transform(state.distanceFraction).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scaleFraction()
            scaleY = scaleFraction()
        }
    ) {
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = isRefreshing,
            containerColor = containerColor,
        )
    }
}

/**
 * Contained loading indicator for modern boxed loading states.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    if (progress != null) {
        ContainedLoadingIndicator(
            progress = progress,
            modifier = modifier,
            indicatorColor = color,
            containerColor = containerColor,
        )
    } else {
        ContainedLoadingIndicator(
            modifier = modifier,
            indicatorColor = color,
            containerColor = containerColor,
        )
    }
}

/**
 * M3 Expressive Pill for tool status display.
 */
@Composable
fun ExpressiveStatePill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpressiveProgressPreview() {
    ToolzTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ExpressiveLoadingWheel()
                ExpressiveLoadingIndicator()
                ExpressiveContainedLoadingIndicator()
            }
            
            ToolzWavyLinearProgressIndicator(progress = { 0.7f }, modifier = Modifier.fillMaxWidth())
            
            ToolzWavyCircularProgressIndicator(progress = { 0.6f })
        }
    }
}
