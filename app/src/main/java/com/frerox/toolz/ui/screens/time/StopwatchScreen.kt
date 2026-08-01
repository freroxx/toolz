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

package com.frerox.toolz.ui.screens.time

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveStatePill
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzHorizontalFloatingToolbar
import com.frerox.toolz.ui.components.ToolzWavyCircularProgressIndicator
import com.frerox.toolz.ui.components.ToolzWavyLinearProgressIndicator
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.screens.time.components.PreferenceRow
import com.frerox.toolz.ui.screens.time.components.SettingsSection
import com.frerox.toolz.ui.screens.time.components.TimeSettingsBottomSheet
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StopwatchScreen(
    viewModel: StopwatchViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val rawAccent = if (state.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val accent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(durationMillis = 500),
        label = "stopwatchAccent",
    )
    val view = LocalView.current
    var lapFlashAt by remember { mutableLongStateOf(0L) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.lastLapAt) {
        if (state.lastLapAt > 0L) lapFlashAt = state.lastLapAt
    }

    DisposableEffect(state.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Stopwatch",
                subtitle = if (state.isRunning) "Recording time" else "Precision lap timing",
                titleHorizontalAlignment = Alignment.Start,
                navigationIcon = {
                    ToolzExpressiveIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = SmallExpressiveShape,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ToolzExpressiveIconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = SmallExpressiveShape,
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        floatingActionButton = {
            StopwatchControlDock(
                state = state,
                accent = accent,
                onToggle = viewModel::toggleStartStop,
                onLap = viewModel::lap,
                onReset = viewModel::reset,
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = Color.Transparent,
    ) { padding ->
        StopwatchContent(
            state = state,
            accent = accent,
            lapFlashAt = lapFlashAt,
            contentPadding = padding,
        )

        if (showSettings) {
            TimeSettingsBottomSheet(
                title = "Stopwatch Settings",
                onDismiss = { showSettings = false },
                accent = accent
            ) {
                SettingsSection(title = "Display", icon = Icons.Rounded.DisplaySettings, accent = accent) {
                    PreferenceRow(
                        title = "Show milliseconds",
                        subtitle = "Use compact timing when disabled",
                        checked = state.showMilliseconds,
                        onCheckedChange = viewModel::setShowMilliseconds,
                    )
                }
                SettingsSection(title = "Behavior", icon = Icons.Rounded.Settings, accent = accent) {
                    PreferenceRow(
                        title = "Keep screen awake",
                        subtitle = "Useful for long sessions and lap review",
                        checked = state.keepScreenOn,
                        onCheckedChange = viewModel::setKeepScreenOn,
                    )
                }
            }
        }
    }
}

@Composable
private fun StopwatchContent(
    state: StopwatchState,
    accent: Color,
    lapFlashAt: Long,
    contentPadding: PaddingValues,
) {
    val performanceMode = LocalPerformanceMode.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(top = contentPadding.calculateTopPadding())
            .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 28.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StaggeredEntrance(index = 0) {
            StopwatchDial(state = state, accent = accent, lapFlashAt = lapFlashAt)
        }
        StaggeredEntrance(index = 1) {
            StopwatchStatsRow(state = state, accent = accent)
        }
        StaggeredEntrance(index = 2) {
            StopwatchLapsPanel(state = state, accent = accent)
        }
    }
}

@Composable
private fun StopwatchDial(state: StopwatchState, accent: Color, lapFlashAt: Long) {
    val lapPulse by animateFloatAsState(
        targetValue = if (lapFlashAt == state.lastLapAt && lapFlashAt > 0L) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "lapPulse",
    )
    val sweepProgress = ((state.elapsedTime % 60_000L).toFloat() / 60_000f).coerceIn(0f, 1f)
    val locale = LocalConfiguration.current.locales[0]

    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val dialSize = maxWidth.coerceAtMost(330.dp)
        Box(modifier = Modifier.size(dialSize), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AnimatedContent(
                        targetState = formatStopwatchTime(state.elapsedTime, state.showMilliseconds, locale),
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.97f)).togetherWith(fadeOut()) },
                        label = "stopwatchTime",
                    ) { time ->
                        Text(
                            text = time,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                            ),
                            color = if (state.isRunning) accent else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ExpressiveStatePill(
                        text = if (state.isRunning) "Recording" else if (state.elapsedTime > 0L) "Paused" else "Ready",
                        icon = if (state.isRunning) Icons.Rounded.Timer else Icons.Rounded.Timeline,
                        color = accent,
                    )
                }
            }
            ToolzWavyCircularProgressIndicator(
                progress = { sweepProgress },
                modifier = Modifier.fillMaxSize(),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f + lapPulse * 0.15f),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun StopwatchStatsRow(state: StopwatchState, accent: Color) {
    val lapEntries = remember(state.laps) { state.lapEntries() }
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StopwatchMetricCard(
            modifier = Modifier.weight(1f),
            icon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
            label = "Laps",
            value = state.laps.size.toString(),
            accent = accent,
        )
        StopwatchMetricCard(
            modifier = Modifier.weight(1f),
            icon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
            label = "Best",
            value = lapEntries.minByOrNull { it.duration }?.duration?.let { formatStopwatchTime(it, false, locale) } ?: "--:--",
            accent = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun StopwatchMetricCard(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    accent: Color,
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
        elevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = SmallExpressiveShape, color = accent.copy(alpha = 0.14f), contentColor = accent) {
                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StopwatchLapsPanel(state: StopwatchState, accent: Color) {
    val lapEntries = remember(state.laps) { state.lapEntries() }
    val locale = LocalConfiguration.current.locales[0]
    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        elevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lap timeline", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(lapEntries.size.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = accent)
            }
            if (lapEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(124.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(42.dp), tint = accent.copy(alpha = 0.45f))
                        Spacer(Modifier.height(10.dp))
                        Text("No laps yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((lapEntries.size.coerceAtMost(5) * 86).dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(lapEntries, key = { _, item -> item.number }) { index, lap ->
                        StaggeredEntrance(index = index % 5) {
                            StopwatchLapCard(
                                lap = lap,
                                isBest = lap.duration == lapEntries.minOf { it.duration },
                                isSlowest = lap.duration == lapEntries.maxOf { it.duration },
                                accent = accent,
                                showMilliseconds = state.showMilliseconds,
                                locale = locale,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopwatchLapCard(
    lap: LapEntry,
    isBest: Boolean,
    isSlowest: Boolean,
    accent: Color,
    showMilliseconds: Boolean,
    locale: Locale,
) {
    val markerColor = when {
        isBest -> MaterialTheme.colorScheme.primary
        isSlowest -> MaterialTheme.colorScheme.tertiary
        else -> accent
    }
    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, markerColor.copy(alpha = 0.16f)),
        elevation = 0.dp,
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = SmallExpressiveShape, color = markerColor.copy(alpha = 0.14f), contentColor = markerColor) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text(lap.number.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Lap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatStopwatchTime(lap.duration, showMilliseconds, locale),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (isBest) "Best" else if (isSlowest) "Slow" else "Total", style = MaterialTheme.typography.labelSmall, color = markerColor, fontWeight = FontWeight.Black)
                Text(
                    formatStopwatchTime(lap.total, showMilliseconds, locale),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StopwatchControlDock(
    state: StopwatchState,
    accent: Color,
    onToggle: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
) {
    ToolzHorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        trailingContent = {
            clickableItem(
                onClick = onLap,
                icon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
                label = "Lap",
                enabled = state.elapsedTime > 0L,
            )
            clickableItem(
                onClick = onReset,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                label = "Reset",
            )
        },
    ) {
        ToolzExpressiveButton(
            onClick = onToggle,
            shape = BouncyShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else accent,
                contentColor = if (state.isRunning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Icon(
                if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (state.isRunning) "Pause" else "Start", fontWeight = FontWeight.Black)
        }
    }
}

private data class LapEntry(
    val number: Int,
    val total: Long,
    val duration: Long,
)

private fun StopwatchState.lapEntries(): List<LapEntry> {
    return laps.mapIndexed { index, total ->
        val previousTotal = laps.getOrNull(index + 1) ?: 0L
        LapEntry(
            number = laps.size - index,
            total = total,
            duration = (total - previousTotal).coerceAtLeast(0L),
        )
    }
}

private fun formatStopwatchTime(timeMillis: Long, showMilliseconds: Boolean, locale: Locale): String {
    val totalSeconds = timeMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val millis = (timeMillis % 1000) / 10
    return when {
        showMilliseconds && hours > 0 -> String.format(locale, "%d:%02d:%02d.%02d", hours, minutes, seconds, millis)
        showMilliseconds -> String.format(locale, "%02d:%02d.%02d", minutes, seconds, millis)
        hours > 0 -> String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format(locale, "%02d:%02d", minutes, seconds)
    }
}
