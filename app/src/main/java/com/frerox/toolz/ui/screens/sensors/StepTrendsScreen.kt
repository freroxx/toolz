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

package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.steps.StepEntry
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StepTrendsScreen(
    viewModel: StepCounterViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vibrationManager = LocalVibrationManager.current
    var selectedRange by remember { mutableStateOf("Week") }
    var selectedMetric by remember { mutableStateOf("Steps") }
    var chartMode by remember { mutableStateOf(0) } // 0=Bars, 1=Line, 2=Both

    val weekLabel = stringResource(R.string.st_StepTrendsScreen_w7e8)
    val monthLabel = stringResource(R.string.st_StepTrendsScreen_m9o0)
    val yearLabel = stringResource(R.string.st_StepTrendsScreen_y1e2)
    val stepsLabel = stringResource(R.string.st_StepTrendsScreen_s3t4)
    val distanceLabel = stringResource(R.string.st_StepTrendsScreen_d5i6)
    val caloriesLabel = stringResource(R.string.st_StepTrendsScreen_c7a8)

    val rawHistory = state.rawHistoryForRange
    val totalVal = when (selectedMetric) {
        "Steps" -> rawHistory.sumOf { it.steps }.toDouble()
        "Distance" -> rawHistory.sumOf { com.frerox.toolz.util.StepTrackerUtils.calculateDistanceKm(it.steps, state.stepLength) }
        "Calories" -> rawHistory.sumOf { com.frerox.toolz.util.StepTrackerUtils.calculateCalories(it.steps, state.caloriesPer1k) }.toDouble()
        "Active Time" -> rawHistory.sumOf { com.frerox.toolz.util.StepTrackerUtils.calculateMoveMinutes(it.steps) }.toDouble()
        else -> rawHistory.sumOf { it.steps }.toDouble()
    }
    val avgVal = if (rawHistory.isNotEmpty()) totalVal / rawHistory.size else 0.0

    val formattedTotal = when (selectedMetric) {
        "Distance" -> {
            val displayDist = if (state.distanceUnit == "km") totalVal else com.frerox.toolz.util.StepTrackerUtils.kmToMiles(totalVal)
            "%.1f %s".format(displayDist, state.distanceUnit)
        }
        "Steps" -> "%,.0f".format(totalVal)
        "Calories" -> "%,.0f kcal".format(totalVal)
        "Active Time" -> "%,.0f min".format(totalVal)
        else -> "%,.0f".format(totalVal)
    }

    val formattedAvg = when (selectedMetric) {
        "Distance" -> {
            val displayDist = if (state.distanceUnit == "km") avgVal else com.frerox.toolz.util.StepTrackerUtils.kmToMiles(avgVal)
            "%.2f %s/d".format(displayDist, state.distanceUnit)
        }
        "Steps" -> "%,.0f/d".format(avgVal)
        "Calories" -> "%,.0f kcal/d".format(avgVal)
        "Active Time" -> "%,.0f min/d".format(avgVal)
        else -> "%,.0f/d".format(avgVal)
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_StepTrendsScreen_a1c2),
                subtitle = stringResource(R.string.st_StepTrendsScreen_t3i4),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_StepTrendsScreen_b5a6))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { chartMode = (chartMode + 1) % 3 },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    ) {
                        val icon = when (chartMode) {
                            0 -> Icons.Rounded.BarChart
                            1 -> Icons.Rounded.Timeline
                            else -> Icons.AutoMirrored.Rounded.ShowChart
                        }
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header & Metrics Toggle
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StaggeredEntrance(index = 0) {
                            ToolzConnectedButtonGroup(
                                selectedIndex = when (selectedRange) { "Week" -> 0; "Month" -> 1; else -> 2 },
                                options = listOf(weekLabel, monthLabel, yearLabel),
                                onOptionSelected = { i ->
                                    val r = when (i) { 0 -> "Week"; 1 -> "Month"; else -> "Year" }
                                    selectedRange = r
                                    viewModel.setTrendRange(r)
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            )
                        }

                        StaggeredEntrance(index = 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f), CircleShape).padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("Steps" to stepsLabel, "Distance" to distanceLabel, "Calories" to caloriesLabel).forEach { pair ->
                                    val m = pair.first
                                    val label = pair.second
                                    val isSelected = selectedMetric == m
                                    Surface(
                                        onClick = { 
                                            vibrationManager?.vibrateClick()
                                            selectedMetric = m 
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Text(
                                                label.uppercase(),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Black,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Main Chart Card
                item {
                    AnimatedContent(
                        targetState = Triple(selectedRange, selectedMetric, state.fullHistory),
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(600)) + scaleIn(initialScale = 0.95f, animationSpec = tween(600)))
                                .togetherWith(fadeOut(animationSpec = tween(400)))
                        },
                        label = "ChartAnimation"
                    ) { (_, _, history) ->
                        StaggeredEntrance(index = 2) {
                            ChartCard(
                                history = history,
                                goal = state.goal,
                                caloriesPer1k = state.caloriesPer1k,
                                stepLengthCm = state.stepLength,
                                chartMode = chartMode,
                                metric = selectedMetric,
                                distanceUnit = state.distanceUnit
                            )
                        }
                    }
                }

                // Summary Stats Grid
                item {
                    AnimatedContent(
                        targetState = Triple(selectedRange, selectedMetric, state.rawHistoryForRange),
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(600)) + slideInVertically(animationSpec = tween(600)) { it / 4 })
                                .togetherWith(fadeOut(animationSpec = tween(400)))
                        },
                        label = "StatsAnimation"
                    ) { (_, _, _) ->
                        StaggeredEntrance(index = 3) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    SummaryStat(
                                        modifier = Modifier.weight(1.2f),
                                        label = stringResource(R.string.st_StepTrendsScreen_a9v0),
                                        value = formattedAvg,
                                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    SummaryStat(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(R.string.st_StepTrendsScreen_s1t2),
                                        value = "${state.streak}",
                                        unit = stringResource(R.string.st_StepTrendsScreen_d7a8),
                                        icon = Icons.Rounded.Bolt,
                                        color = Color(0xFFFF9800)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    SummaryStat(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(R.string.st_StepTrendsScreen_a3c4),
                                        value = "${state.activeDaysCount}",
                                        unit = stringResource(R.string.st_StepTrendsScreen_d7a8),
                                        icon = Icons.Rounded.CheckCircle,
                                        color = Color(0xFF4CAF50)
                                    )
                                    SummaryStat(
                                        modifier = Modifier.weight(1.2f),
                                        label = stringResource(R.string.st_StepTrendsScreen_t5o6),
                                        value = formattedTotal,
                                        icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                                        color = Color(0xFF2196F3)
                                    )
                                }
                            }
                        }
                    }
                }

                // Daily List
                if (state.fullHistory.isNotEmpty()) {
                    item {
                        AnimatedContent(
                            targetState = Triple(selectedRange, selectedMetric, state.fullHistory),
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(600)) + slideInVertically(animationSpec = tween(600)) { it / 3 })
                                    .togetherWith(fadeOut(animationSpec = tween(400)))
                            },
                            label = "HistoryAnimation"
                        ) { (_, _, history) ->
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    stringResource(R.string.st_StepTrendsScreen_h9b0),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                                
                                ExpressiveCard(
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = LargeExpressiveShape,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                                    elevation = 0.dp
                                ) {
                                    Column {
                                        val maxSteps = history.maxOfOrNull { it.steps } ?: 0
                                        history.forEachIndexed { index, entry ->
                                            DayListRow(
                                                entry = entry,
                                                goal = state.goal,
                                                caloriesPer1k = state.caloriesPer1k,
                                                stepLengthCm = state.stepLength,
                                                metric = selectedMetric,
                                                distanceUnit = state.distanceUnit,
                                                isBestDay = entry.steps > 0 && entry.steps == maxSteps
                                            )
                                            if (index < history.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = 24.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(48.dp)) }
            }
        }
    }
}

@Composable
private fun ChartCard(
    history: List<StepEntry>,
    goal: Int,
    caloriesPer1k: Int,
    stepLengthCm: Int,
    chartMode: Int,
    metric: String,
    distanceUnit: String
) {
    val avgSteps = if (history.isNotEmpty()) history.map { it.steps }.average().toFloat() else 0f
    var selectedEntry by remember(history) { mutableStateOf<StepEntry?>(null) }

    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        ) {
            // Chart
            InteractiveFitnessChart(
                history = history,
                goal = goal,
                caloriesPer1k = caloriesPer1k,
                stepLengthCm = stepLengthCm,
                chartMode = chartMode,
                metric = metric,
                distanceUnit = distanceUnit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                onDaySelected = { selectedEntry = it }
            )

            // Selected day details — compact
            AnimatedVisibility(
                visible = selectedEntry != null,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                selectedEntry?.let { entry ->
                    val pct = if (goal > 0) (entry.steps * 100 / goal) else 0
                    val goalMet = entry.steps >= goal
                    val dayLabel = try {
                        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(entry.date)?.let {
                            SimpleDateFormat("EEE, MMM d", Locale.ENGLISH).format(it)
                        } ?: entry.date
                    } catch (e: Exception) { entry.date }

                    val formattedValue = when (metric) {
                        "Steps" -> "%,d steps".format(entry.steps)
                        "Distance" -> {
                            val km = com.frerox.toolz.util.StepTrackerUtils.calculateDistanceKm(entry.steps, stepLengthCm)
                            val displayDist = if (distanceUnit == "km") km else com.frerox.toolz.util.StepTrackerUtils.kmToMiles(km)
                            "%.2f %s".format(displayDist, distanceUnit)
                        }
                        "Calories" -> "%d kcal".format(com.frerox.toolz.util.StepTrackerUtils.calculateCalories(entry.steps, caloriesPer1k))
                        "Active Time" -> "%d min".format(com.frerox.toolz.util.StepTrackerUtils.calculateMoveMinutes(entry.steps))
                        else -> "%,d steps".format(entry.steps)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                dayLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (goalMet) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Text(
                                        "$pct%",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = if (goalMet) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                        Text(
                            text = formattedValue,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (goalMet) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Average footer
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.TrendingUp, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.st_StepTrendsScreen_avg_steps, String.format(Locale.getDefault(), "%,.0f", avgSteps)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Text(stringResource(R.string.st_StepTrendsScreen_goal_steps, String.format(Locale.getDefault(), "%,d", goal)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun SummaryStat(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        shape = MediumExpressiveShape,
        containerColor = color.copy(alpha = 0.1f),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color, letterSpacing = 1.sp)
            }
            
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                )
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayListRow(
    entry: StepEntry,
    goal: Int,
    caloriesPer1k: Int,
    stepLengthCm: Int,
    metric: String,
    distanceUnit: String,
    isBestDay: Boolean = false
) {
    val metricGoal = when (metric) {
        "Steps" -> goal.toDouble()
        "Distance" -> com.frerox.toolz.util.StepTrackerUtils.calculateDistanceKm(goal, stepLengthCm)
        "Calories" -> com.frerox.toolz.util.StepTrackerUtils.calculateCalories(goal, caloriesPer1k).toDouble()
        "Active Time" -> com.frerox.toolz.util.StepTrackerUtils.calculateMoveMinutes(goal).toDouble()
        else -> goal.toDouble()
    }
    val value = when (metric) {
        "Steps" -> entry.steps.toDouble()
        "Distance" -> com.frerox.toolz.util.StepTrackerUtils.calculateDistanceKm(entry.steps, stepLengthCm)
        "Calories" -> com.frerox.toolz.util.StepTrackerUtils.calculateCalories(entry.steps, caloriesPer1k).toDouble()
        "Active Time" -> com.frerox.toolz.util.StepTrackerUtils.calculateMoveMinutes(entry.steps).toDouble()
        else -> entry.steps.toDouble()
    }

    val percent = if (metricGoal > 0) (value / metricGoal).coerceIn(0.0, 1.0).toFloat() else 0f
    val percentInt = (percent * 100).toInt()
    val goalMet = entry.steps >= goal

    val formattedVal = when (metric) {
        "Steps" -> "%,d steps".format(entry.steps)
        "Distance" -> {
            val km = com.frerox.toolz.util.StepTrackerUtils.calculateDistanceKm(entry.steps, stepLengthCm)
            val displayDist = if (distanceUnit == "km") km else com.frerox.toolz.util.StepTrackerUtils.kmToMiles(km)
            "%.2f %s".format(displayDist, distanceUnit)
        }
        "Calories" -> "%d kcal".format(com.frerox.toolz.util.StepTrackerUtils.calculateCalories(entry.steps, caloriesPer1k))
        "Active Time" -> "%d min".format(com.frerox.toolz.util.StepTrackerUtils.calculateMoveMinutes(entry.steps))
        else -> "%,d steps".format(entry.steps)
    }

    // Determine if it's an aggregated week/month label or a specific date.
    // Keep this strict to avoid mis-detecting daily ISO dates as "aggregated".
    val isAggregated = entry.date.startsWith("Week ")

    val dayLabel = try {
        if (isAggregated) entry.date else {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(entry.date)?.let {
                "${SimpleDateFormat("EEE", Locale.ENGLISH).format(it)} ${SimpleDateFormat("d", Locale.ENGLISH).format(it)}"
            } ?: entry.date
        }
    } catch (e: Exception) { entry.date }

    val monthLabel = try {
        if (isAggregated) "" else {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(entry.date)?.let {
                SimpleDateFormat("MMM", Locale.ENGLISH).format(it)
            } ?: ""
        }
    } catch (e: Exception) { "" }

    val weekdayShort = try {
        if (isAggregated) "" else {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(entry.date)?.let {
                SimpleDateFormat("EEE", Locale.ENGLISH).format(it)
            } ?: ""
        }
    } catch (e: Exception) { "" }

    val dayOfMonthShort = try {
        if (isAggregated) "" else {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(entry.date)?.let {
                SimpleDateFormat("d", Locale.ENGLISH).format(it)
            } ?: ""
        }
    } catch (e: Exception) { "" }

    val primaryColor = MaterialTheme.colorScheme.primary
    val animatedProgress by animateFloatAsState(
        targetValue = percent,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "DayProgress"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isBestDay) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(44.dp)
        ) {
            if (isAggregated) {
                Text(dayLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            } else if (weekdayShort.isNotEmpty() && dayOfMonthShort.isNotEmpty()) {
                Text(
                    weekdayShort,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    dayOfMonthShort,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            } else if (monthLabel.isNotEmpty()) {
                Text(monthLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                Text(dayLabel.substringAfterLast(" "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            } else {
                Text(dayLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        // Progress bar + value
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = formattedVal,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isBestDay) primaryColor else if (goalMet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    if (isBestDay) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = "Best Day", tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                    }
                }
                if (goalMet) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                }
            }
            ExpressiveLinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = if (goalMet) primaryColor else primaryColor.copy(alpha = 0.5f),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
        }

        // Percent badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(SmallExpressiveShape)
                .background(if (goalMet) primaryColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                "$percentInt%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = if (goalMet) primaryColor else MaterialTheme.colorScheme.outline
            )
        }
    }
}
