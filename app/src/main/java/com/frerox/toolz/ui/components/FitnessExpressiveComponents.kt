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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.frerox.toolz.data.steps.StepEntry
import com.frerox.toolz.util.StepTrackerUtils
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// Fluid Wavy Progress Indicator
// Canvas-drawn circular progress ring with animated sine-wave undulation.
// Wave amplitude scales with strokeWidth for consistent visual weight at any size.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FluidWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Float = 40f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    // Secondary phase for a richer multi-wave look
    val phase2 by infiniteTransition.animateFloat(
        initialValue = PI.toFloat(),
        targetValue = 3 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase2"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (min(size.width, size.height) / 2) - strokeWidth
        val currentProgress = progress()
        // Wave amplitude = reduced for a cleaner, more "flagship" feel
        val amplitude = strokeWidth * 0.15f

        // Background track
        drawCircle(
            color = color.copy(alpha = 0.08f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        if (currentProgress <= 0f) return@Canvas

        // Build wavy arc path
        val path = Path()
        val sweepAngle = currentProgress * 360f
        val steps = (sweepAngle * 2).toInt().coerceAtLeast(1) // Higher resolution for smoother curves

        for (i in 0..steps) {
            val angle = i / 2f
            val angleRad = Math.toRadians(angle.toDouble()).toFloat()
            // Simpler, more rhythmic wave pattern
            val wave = sin(angleRad * 8 + phase) * amplitude +
                       cos(angleRad * 5 + phase2) * (amplitude * 0.3f)
            val r = radius + wave
            val x = center.x + r * cos(angleRad)
            val y = center.y + r * sin(angleRad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Gradient brush from transparent -> primary for a premium look
        val gradientBrush = Brush.sweepGradient(
            0f to color.copy(alpha = 0.1f),
            currentProgress to color,
            center = center
        )

        // Rotate canvas -90 degrees so 0 degrees becomes 12 o'clock
        rotate(-90f, center) {
            drawPath(
                path = path,
                brush = gradientBrush,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Glowing tip dot at the end of the arc
            if (currentProgress > 0.01f) {
                val tipAngle = Math.toRadians(sweepAngle.toDouble()).toFloat()
                val tipX = center.x + radius * cos(tipAngle)
                val tipY = center.y + radius * sin(tipAngle)
                drawCircle(color = color, radius = strokeWidth / 2f, center = Offset(tipX, tipY))
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = strokeWidth / 5f,
                    center = Offset(tipX, tipY)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Interactive Fitness Chart
// Canvas bar chart with:
//  • Staggered entrance animation (bars appear sequentially by index)
//  • Goal-line drawn at the correct proportional height
//  • Tap to select a bar — shows a floating tooltip popup
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InteractiveFitnessChart(
    history: List<StepEntry>,
    goal: Int,
    modifier: Modifier = Modifier,
    caloriesPer1k: Int = 40,
    stepLengthCm: Int = 75,
    chartMode: Int = 0, // 0 = Columns, 1 = Line, 2 = Both
    metric: String = "Steps",
    distanceUnit: String = "km",
    onDaySelected: (StepEntry?) -> Unit = {}
) {
    if (history.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No data for this range",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Black,
        fontSize = 9.sp
    )
    val textMeasurer = rememberTextMeasurer()

    // Staggered entrance — each bar animates in with a delay based on its index
    val barAnimations = history.mapIndexed { index, _ ->
        val animatable = remember { Animatable(0f) }
        LaunchedEffect(history) {
            kotlinx.coroutines.delay(index * 30L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        animatable
    }

    var selectedIndex by remember { mutableStateOf(-1) }
    var tooltipEntry by remember { mutableStateOf<StepEntry?>(null) }
    var tooltipBarCenterX by remember { mutableStateOf(0f) }
    var tooltipBarTopY by remember { mutableStateOf(0f) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(history) {
                    detectTapGestures { offset ->
                        if (history.isEmpty()) return@detectTapGestures
                        val rawSpacing = if (history.size > 31) 0f else if (history.size > 7) 2.dp.toPx() else 6.dp.toPx()
                        val barWidth = ((size.width - (history.size - 1) * rawSpacing) / history.size).coerceAtLeast(2f)
                        val actualSpacing = if (history.size > 1) ((size.width - barWidth * history.size) / (history.size - 1)) else 0f
                        
                        val tappedIndex = (offset.x / (barWidth + actualSpacing)).toInt()
                            .coerceIn(0, history.lastIndex)

                        if (selectedIndex == tappedIndex) {
                            // Second tap on same bar — deselect
                            selectedIndex = -1
                            tooltipEntry = null
                            onDaySelected(null)
                        } else {
                            selectedIndex = tappedIndex
                            val entry = history[tappedIndex]
                            tooltipEntry = entry
                            val metricGoal = when (metric) {
                                "Steps" -> goal.toDouble()
                                "Distance" -> StepTrackerUtils.calculateDistanceKm(goal, stepLengthCm)
                                "Calories" -> StepTrackerUtils.calculateCalories(goal, caloriesPer1k).toDouble()
                                "Active Time" -> StepTrackerUtils.calculateMoveMinutes(goal).toDouble()
                                else -> goal.toDouble()
                            }
                            val maxVal = max(metricGoal, history.maxOf { e ->
                                when (metric) {
                                    "Steps" -> e.steps.toDouble()
                                    "Distance" -> StepTrackerUtils.calculateDistanceKm(e.steps, stepLengthCm)
                                    "Calories" -> StepTrackerUtils.calculateCalories(e.steps, caloriesPer1k).toDouble()
                                    "Active Time" -> StepTrackerUtils.calculateMoveMinutes(e.steps).toDouble()
                                    else -> e.steps.toDouble()
                                }
                            }).toFloat().coerceAtLeast(1f)
                            val entryVal = when (metric) {
                                "Steps" -> entry.steps.toDouble()
                                "Distance" -> StepTrackerUtils.calculateDistanceKm(entry.steps, stepLengthCm)
                                "Calories" -> StepTrackerUtils.calculateCalories(entry.steps, caloriesPer1k).toDouble()
                                "Active Time" -> StepTrackerUtils.calculateMoveMinutes(entry.steps).toDouble()
                                else -> entry.steps.toDouble()
                            }
                            val progress = (entryVal.toFloat() / maxVal).coerceIn(0.05f, 1f)
                            val barH = (size.height - 24.dp.toPx()) * progress
                            tooltipBarCenterX = tappedIndex * (barWidth + actualSpacing) + barWidth / 2f
                            tooltipBarTopY = (size.height - 24.dp.toPx()) - barH
                            onDaySelected(entry)
                        }
                    }
                }
        ) {
            val bottomPadding = 24.dp.toPx()
            val chartHeight = size.height - bottomPadding
            val rawSpacing = if (history.size > 31) 0f else if (history.size > 7) 2.dp.toPx() else 6.dp.toPx()
            val barWidth = ((size.width - (history.size - 1) * rawSpacing) / history.size).coerceAtLeast(2f)
            val actualSpacing = if (history.size > 1) ((size.width - barWidth * history.size) / (history.size - 1)) else 0f
            
            val metricGoal = when (metric) {
                "Steps" -> goal.toDouble()
                "Distance" -> StepTrackerUtils.calculateDistanceKm(goal, stepLengthCm)
                "Calories" -> StepTrackerUtils.calculateCalories(goal, caloriesPer1k).toDouble()
                "Active Time" -> StepTrackerUtils.calculateMoveMinutes(goal).toDouble()
                else -> goal.toDouble()
            }
            val maxVal = max(metricGoal, history.maxOf { e ->
                when (metric) {
                    "Steps" -> e.steps.toDouble()
                    "Distance" -> StepTrackerUtils.calculateDistanceKm(e.steps, stepLengthCm)
                    "Calories" -> StepTrackerUtils.calculateCalories(e.steps, caloriesPer1k).toDouble()
                    "Active Time" -> StepTrackerUtils.calculateMoveMinutes(e.steps).toDouble()
                    else -> e.steps.toDouble()
                }
            }).toFloat().coerceAtLeast(1f)

            // Goal line
            val goalY = chartHeight - (metricGoal.toFloat() / maxVal) * chartHeight
            val pathGoal = Path().apply {
                moveTo(0f, goalY)
                val dashLen = 12.dp.toPx()
                var x = 0f
                var on = true
                while (x < size.width) {
                    val next = (x + dashLen).coerceAtMost(size.width)
                    if (on) lineTo(next, goalY) else moveTo(next, goalY)
                    x = next
                    on = !on
                }
            }
            drawPath(
                path = pathGoal,
                color = primaryColor.copy(alpha = 0.25f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Evolution line graph (area chart under the bars)
            if (chartMode == 1 || chartMode == 2) {
                val points = mutableListOf<Offset>()
                history.forEachIndexed { index, entry ->
                    val entranceProgress = barAnimations.getOrNull(index)?.value ?: 1f
                    val entryVal = when (metric) {
                        "Steps" -> entry.steps.toDouble()
                        "Distance" -> StepTrackerUtils.calculateDistanceKm(entry.steps, stepLengthCm)
                        "Calories" -> StepTrackerUtils.calculateCalories(entry.steps, caloriesPer1k).toDouble()
                        "Active Time" -> StepTrackerUtils.calculateMoveMinutes(entry.steps).toDouble()
                        else -> entry.steps.toDouble()
                    }
                    val dataProgress = (entryVal.toFloat() / maxVal).coerceIn(0.05f, 1f)
                    val barHeight = chartHeight * dataProgress * entranceProgress
                    val x = index * (barWidth + actualSpacing) + barWidth / 2f
                    val y = chartHeight - barHeight
                    points.add(Offset(x, y))
                }
                
                if (points.size > 1) {
                    val linePath = Path()
                    linePath.moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val controlX = (p1.x + p2.x) / 2
                        linePath.cubicTo(controlX, p1.y, controlX, p2.y, p2.x, p2.y)
                    }
                    
                    // Fill area below the line
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(points.last().x, chartHeight)
                        lineTo(points.first().x, chartHeight)
                        close()
                    }
                    
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
                            startY = points.minOf { it.y },
                            endY = chartHeight
                        )
                    )
                    
                    // Draw line stroke
                    drawPath(
                        path = linePath,
                        color = primaryColor.copy(alpha = 0.6f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Bars
            history.forEachIndexed { index, entry ->
                val entranceProgress = barAnimations.getOrNull(index)?.value ?: 1f
                val entryVal = when (metric) {
                    "Steps" -> entry.steps.toDouble()
                    "Distance" -> StepTrackerUtils.calculateDistanceKm(entry.steps, stepLengthCm)
                    "Calories" -> StepTrackerUtils.calculateCalories(entry.steps, caloriesPer1k).toDouble()
                    "Active Time" -> StepTrackerUtils.calculateMoveMinutes(entry.steps).toDouble()
                    else -> entry.steps.toDouble()
                }
                val dataProgress = (entryVal.toFloat() / maxVal).coerceIn(0.05f, 1f)
                val barHeight = chartHeight * dataProgress * entranceProgress
                val x = index * (barWidth + actualSpacing)
                val y = chartHeight - barHeight

                val isSelected = selectedIndex == index
                val goalMet = entry.steps >= goal

                val barColor = when {
                    isSelected -> primaryColor
                    goalMet    -> primaryColor.copy(alpha = 0.85f)
                    else       -> primaryColor.copy(alpha = 0.28f)
                }

                if (chartMode == 0 || chartMode == 2) {
                    // Bar body
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f)
                    )
                }

                // Day label below bar (Mon/Tue/…)
                val dayLabel = try {
                    val d = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(entry.date)
                    SimpleDateFormat("EEE", Locale.ENGLISH).format(d!!)
                } catch (e: Exception) {
                    when {
                        entry.date.startsWith("Week ") -> entry.date.replace("Week ", "W")
                        entry.date.length > 3 -> entry.date.take(3)
                        else -> entry.date
                    }
                }

                val textLayout = textMeasurer.measure(dayLabel, labelStyle)
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        x = x + (barWidth - textLayout.size.width) / 2f,
                        y = chartHeight + 6.dp.toPx()
                    ),
                    color = if (isSelected) primaryColor else Color.Gray.copy(alpha = 0.7f)
                )
            }
        }

        // Floating tooltip popup
        AnimatedVisibility(
            visible = tooltipEntry != null,
            enter = fadeIn(tween(150)) + scaleIn(
                tween(200, easing = FastOutSlowInEasing),
                transformOrigin = TransformOrigin(0.5f, 1f)
            ),
            exit = fadeOut(tween(100)) + scaleOut(
                tween(150),
                transformOrigin = TransformOrigin(0.5f, 1f)
            )
        ) {
            tooltipEntry?.let { entry ->
                val density = LocalDensity.current
                val tipX = with(density) { tooltipBarCenterX.toDp() }
                val tipY = with(density) { tooltipBarTopY.toDp() }

                Box(
                    modifier = Modifier
                        .offset(
                             x = (tipX - 60.dp).coerceAtLeast(0.dp),
                             y = (tipY - 88.dp).coerceAtLeast(0.dp)
                        )
                        .width(120.dp)
                ) {
                    ChartTooltip(
                        entry = entry,
                        goal = goal,
                        caloriesPer1k = caloriesPer1k,
                        stepLengthCm = stepLengthCm,
                        metric = metric,
                        distanceUnit = distanceUnit
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chart Tooltip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChartTooltip(
    entry: StepEntry,
    goal: Int,
    caloriesPer1k: Int,
    stepLengthCm: Int,
    metric: String,
    distanceUnit: String
) {
    val goalMet = entry.steps >= goal
    val percent = if (goal > 0) (entry.steps * 100 / goal) else 0
    val dateLabel = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(entry.date)
        SimpleDateFormat("MMM d", Locale.getDefault()).format(d!!)
    } catch (e: Exception) { entry.date }

    val formattedValue = when (metric) {
        "Steps" -> "%,d steps".format(entry.steps)
        "Distance" -> {
            val km = StepTrackerUtils.calculateDistanceKm(entry.steps, stepLengthCm)
            val displayDist = if (distanceUnit == "km") km else StepTrackerUtils.kmToMiles(km)
            "%.2f %s".format(displayDist, distanceUnit)
        }
        "Calories" -> "%d kcal".format(StepTrackerUtils.calculateCalories(entry.steps, caloriesPer1k))
        "Active Time" -> "%d min".format(StepTrackerUtils.calculateMoveMinutes(entry.steps))
        else -> "%,d steps".format(entry.steps)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (goalMet) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(10.dp)
                    )
                }
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                )
            }
            Text(
                text = formattedValue,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
            Text(
                text = "$percent% of goal",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
            )
        }
    }
}
