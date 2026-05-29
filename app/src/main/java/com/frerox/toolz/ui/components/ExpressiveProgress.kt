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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
fun ExpressiveScanningIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    frameColor: Color = MaterialTheme.colorScheme.surfaceDim,
) {
    val performanceMode = LocalPerformanceMode.current
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, frameColor.copy(alpha = 0.7f), RoundedCornerShape(28.dp)),
    ) {
        val density = LocalDensity.current
        val maxHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val lineHeightPx = with(density) { 12.dp.toPx() }
        val travel = remember(maxHeightPx, lineHeightPx) {
            (maxHeightPx - lineHeightPx).coerceAtLeast(0f)
        }
        val infiniteTransition = rememberInfiniteTransition(label = "scanningIndicator")
        val scanProgress by if (performanceMode) {
            remember { mutableFloatStateOf(0.5f) }
        } else {
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "scanProgress",
            )
        }
        val targetOffset = IntOffset(0, (scanProgress * travel).roundToInt())
        val animatedOffset by animateIntOffsetAsState(
            targetValue = targetOffset,
            animationSpec = if (performanceMode) tween(120) else spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            label = "scanLineOffset",
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (!performanceMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .graphicsLayer {
                            translationY = animatedOffset.y.toFloat()
                        }
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    color.copy(alpha = 0.15f),
                                    color.copy(alpha = 0.8f),
                                    color.copy(alpha = 0.15f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val corner = 24.dp.toPx()
                val lineLength = size.minDimension * 0.18f
                val activeColor = color.copy(alpha = if (performanceMode) 0.6f else 0.8f)

                drawRoundRect(
                    color = frameColor.copy(alpha = 0.12f),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = 1.dp.toPx()),
                )

                drawLine(activeColor, Offset(0f, corner), Offset(0f, corner + lineLength), stroke)
                drawLine(activeColor, Offset(0f, corner), Offset(corner + lineLength, 0f), stroke)
                drawLine(activeColor, Offset(size.width, corner), Offset(size.width, corner + lineLength), stroke)
                drawLine(activeColor, Offset(size.width, corner), Offset(size.width - corner - lineLength, 0f), stroke)
                drawLine(activeColor, Offset(0f, size.height - corner), Offset(0f, size.height - corner - lineLength), stroke)
                drawLine(activeColor, Offset(0f, size.height - corner), Offset(corner + lineLength, size.height), stroke)
                drawLine(activeColor, Offset(size.width, size.height - corner), Offset(size.width, size.height - corner - lineLength), stroke)
                drawLine(activeColor, Offset(size.width, size.height - corner), Offset(size.width - corner - lineLength, size.height), stroke)
            }

            Box(modifier = Modifier.align(Alignment.Center)) {
                ExpressivePulseIndicator(
                    modifier = Modifier.size(54.dp),
                    color = color,
                )
            }
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
            
            ExpressiveScanningIndicator(modifier = Modifier.size(120.dp))
        }
    }
}
