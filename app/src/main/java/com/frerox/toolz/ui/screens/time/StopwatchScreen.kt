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

import android.content.res.Configuration
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
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
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzHorizontalFloatingToolbar
import com.frerox.toolz.ui.components.ToolzWavyCircularProgressIndicator
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
    // Saveable so rotation never loses UI flags (S-P1-03).
    var lapFlashAt by rememberSaveable { mutableStateOf(0L) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val restoredMessage = stringResource(R.string.st_StopwatchScreen_y1z2)
    val cappedMessage = stringResource(R.string.st_StopwatchScreen_a3b4)

    LaunchedEffect(state.lastLapAt) {
        if (state.lastLapAt > 0L) lapFlashAt = state.lastLapAt
    }
    // Reboot-restore snackbar, shown once (S-P0-01).
    LaunchedEffect(state.restoredAfterReboot) {
        if (state.restoredAfterReboot) {
            snackbarHostState.showSnackbar(restoredMessage)
            viewModel.consumeRestoreFlag()
        }
    }
    // Lap-cap toast, shown once (S-P1-02: 200 laps blocked, documented).
    LaunchedEffect(state.lapCapped) {
        if (state.lapCapped) {
            snackbarHostState.showSnackbar(cappedMessage)
            viewModel.consumeLapCapped()
        }
    }

    // Capture the previous keepScreenOn ONCE — the old keyed effect re-captured on
    // every toggle and leaked the value onto the next screen (S-P1-03).
    val initialKeepScreenOn = remember { view.keepScreenOn }
    DisposableEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = initialKeepScreenOn }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_StopwatchScreen_a1b2),
                subtitle = if (state.isRunning) stringResource(R.string.st_StopwatchScreen_c3d4) else stringResource(R.string.st_StopwatchScreen_e5f6),
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_StopwatchScreen_g7h8))
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
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.st_StopwatchScreen_i9j0))
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                title = stringResource(R.string.st_StopwatchScreen_k1l2),
                onDismiss = { showSettings = false },
                accent = accent
            ) {
                SettingsSection(title = stringResource(R.string.st_StopwatchScreen_m3n4), icon = Icons.Rounded.DisplaySettings, accent = accent) {
                    PreferenceRow(
                        title = stringResource(R.string.st_StopwatchScreen_o5p6),
                        subtitle = stringResource(R.string.st_StopwatchScreen_q7r8),
                        checked = state.showMilliseconds,
                        onCheckedChange = viewModel::setShowMilliseconds,
                    )
                }
                SettingsSection(title = stringResource(R.string.st_StopwatchScreen_s9t0), icon = Icons.Rounded.Settings, accent = accent) {
                    PreferenceRow(
                        title = stringResource(R.string.st_StopwatchScreen_u1v2),
                        subtitle = stringResource(R.string.st_StopwatchScreen_w3x4),
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
    // Single scroll container (S-P1-02): no outer verticalScroll fighting the laps
    // LazyColumn. Dial + stats are fixed; the laps list takes remaining space.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(top = contentPadding.calculateTopPadding())
            .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 28.dp))
            .padding(horizontal = 20.dp)
            .padding(bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StaggeredEntrance(index = 0) {
            StopwatchDial(
                elapsedTime = state.elapsedTime,
                isRunning = state.isRunning,
                showMilliseconds = state.showMilliseconds,
                lastLapAt = state.lastLapAt,
                lapFlashAt = lapFlashAt,
                accent = accent,
            )
        }
        StaggeredEntrance(index = 1) {
            StopwatchStatsRow(
                laps = state.laps,
                showMilliseconds = state.showMilliseconds,
                accent = accent,
            )
        }
        StopwatchLapsPanel(
            laps = state.laps,
            showMilliseconds = state.showMilliseconds,
            accent = accent,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun StopwatchDial(
    elapsedTime: Long,
    isRunning: Boolean,
    showMilliseconds: Boolean,
    lastLapAt: Long,
    lapFlashAt: Long,
    accent: Color,
) {
    val lapPulse by animateFloatAsState(
        targetValue = if (lapFlashAt == lastLapAt && lapFlashAt > 0L) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "lapPulse",
    )
    val sweepProgress = ((elapsedTime % 60_000L).toFloat() / 60_000f).coerceIn(0f, 1f)
    val locale = safeStopwatchLocale()
    val timeString = formatStopwatchTime(elapsedTime, showMilliseconds, locale)
    // Long strings (>24h / ms digits) drop to a smaller style so 100h+ fits (S-P2-01).
    val timeStyle = if (timeString.length > 11) {
        MaterialTheme.typography.headlineMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
        )
    } else {
        MaterialTheme.typography.displayMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
        )
    }

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
                    // FIX (user report): 2 fractional digits, not 3 — centiseconds.
                    // AnimatedContent ticking every 30ms looks broken (constant
                    // fade/scale churn), so when ms are shown render plain Text
                    // with no transition; animate only whole-second changes.
                    if (showMilliseconds) {
                        Text(
                            text = timeString,
                            style = timeStyle,
                            color = if (isRunning) accent else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        AnimatedContent(
                            targetState = timeString,
                            // TalkBack liveRegion=Off (S-P2-04): liveRegion is intentionally
                            // left UNSET (the property default). The ticker must never
                            // spam announcements; the time stays focusable/readable on demand.
                            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.97f)).togetherWith(fadeOut()) },
                            label = "stopwatchTime",
                        ) { time ->
                            Text(
                                text = time,
                                style = timeStyle,
                                color = if (isRunning) accent else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ExpressiveStatePill(
                        text = if (isRunning) stringResource(R.string.st_StopwatchScreen_y5z6) else if (elapsedTime > 0L) stringResource(R.string.st_StopwatchScreen_a7b8) else stringResource(R.string.st_StopwatchScreen_c9d0),
                        icon = if (isRunning) Icons.Rounded.Timer else Icons.Rounded.Timeline,
                        color = accent,
                    )
                }
            }
            ToolzWavyCircularProgressIndicator(
                progress = { sweepProgress },
                modifier = Modifier
                    .fillMaxSize()
                    .progressSemantics(sweepProgress),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f + lapPulse * 0.15f),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun StopwatchStatsRow(
    laps: List<Long>,
    showMilliseconds: Boolean,
    accent: Color,
) {
    // Hoisted once per laps change (S-P1-02) — never recomputed per tick/item.
    val lapEntries = remember(laps) { laps.toLapEntries() }
    val bestDuration = remember(lapEntries) { lapEntries.minOfOrNull { it.duration } }
    val locale = safeStopwatchLocale()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StopwatchMetricCard(
            modifier = Modifier.weight(1f),
            icon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
            label = stringResource(R.string.st_StopwatchScreen_e1f2),
            value = laps.size.toString(),
            accent = accent,
        )
        StopwatchMetricCard(
            modifier = Modifier.weight(1f),
            icon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
            label = stringResource(R.string.st_StopwatchScreen_g3h4),
            // S-P2-01: honor the ms setting here too (was hardcoded false).
            value = bestDuration?.let { formatStopwatchTime(it, showMilliseconds, locale) } ?: "--:--",
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
                // 48dp touch-target guidance (S-P2-04).
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StopwatchLapsPanel(
    laps: List<Long>,
    showMilliseconds: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val lapEntries = remember(laps) { laps.toLapEntries() }
    val bestDuration = remember(lapEntries) { lapEntries.minOfOrNull { it.duration } }
    val worstDuration = remember(lapEntries) { lapEntries.maxOfOrNull { it.duration } }
    val locale = safeStopwatchLocale()
    ExpressiveCard(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        elevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.st_StopwatchScreen_i5j6), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(lapEntries.size.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = accent)
            }
            if (lapEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(124.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(42.dp), tint = accent.copy(alpha = 0.45f))
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.st_StopwatchScreen_k7l8), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Virtualized for 200 laps without jank (S-P1-02); fills remaining space.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        lapEntries,
                        // Stable across inserts/resets (S-P1-02): totals are unique
                        // monotonic timestamps, unlike positional numbers.
                        key = { _, item -> "${item.total}_${item.number}" },
                    ) { index, lap ->
                        // Entrance animation only for small lists — never re-animate
                        // 200 rows on every insert (S-P1-02).
                        if (lapEntries.size <= 20) {
                            StaggeredEntrance(index = index % 5) {
                                StopwatchLapCard(
                                    lap = lap,
                                    isBest = lap.duration == bestDuration,
                                    isSlowest = lap.duration == worstDuration,
                                    accent = accent,
                                    showMilliseconds = showMilliseconds,
                                    locale = locale,
                                )
                            }
                        } else {
                            StopwatchLapCard(
                                lap = lap,
                                isBest = lap.duration == bestDuration,
                                isSlowest = lap.duration == worstDuration,
                                accent = accent,
                                showMilliseconds = showMilliseconds,
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
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Text(lap.number.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.st_StopwatchScreen_m9n0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatStopwatchTime(lap.duration, showMilliseconds, locale),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (isBest) stringResource(R.string.st_StopwatchScreen_g3h4) else if (isSlowest) stringResource(R.string.st_StopwatchScreen_o1p2) else stringResource(R.string.st_StopwatchScreen_q3r4), style = MaterialTheme.typography.labelSmall, color = markerColor, fontWeight = FontWeight.Black)
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
    val lapLabel = stringResource(R.string.st_StopwatchScreen_m9n0)
    val resetLabel = stringResource(R.string.st_StopwatchScreen_s5t6)
    val pauseLabel = stringResource(R.string.st_StopwatchScreen_w9x0)
    val startLabel = stringResource(R.string.st_StopwatchScreen_u7v8)

    ToolzHorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        trailingContent = {
            clickableItem(
                onClick = onLap,
                // Explicit description for TalkBack (S-P2-04); gated on running.
                icon = { Icon(Icons.Rounded.Flag, contentDescription = lapLabel) },
                label = lapLabel,
                enabled = state.isRunning,
            )
            clickableItem(
                onClick = onReset,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = resetLabel) },
                label = resetLabel,
            )
        },
    ) {
        // Queued in the ViewModel until bound, so never a silent drop (S-P0-01).
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
                contentDescription = if (state.isRunning) pauseLabel else startLabel,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (state.isRunning) pauseLabel else startLabel, fontWeight = FontWeight.Black)
        }
    }
}

private data class LapEntry(
    val number: Int,
    val total: Long,
    val duration: Long,
)

private fun List<Long>.toLapEntries(): List<LapEntry> {
    return mapIndexed { index, total ->
        val previousTotal = getOrNull(index + 1) ?: 0L
        LapEntry(
            number = size - index,
            total = total,
            duration = (total - previousTotal).coerceAtLeast(0L),
        )
    }
}

/** locales[0] can throw on odd OEM configs — fall back to default (S-P2-01). */
@Composable
private fun safeStopwatchLocale(configuration: Configuration = LocalConfiguration.current): Locale {
    return remember(configuration) {
        try {
            configuration.locales.get(0) ?: Locale.getDefault()
        } catch (_: Exception) {
            Locale.getDefault()
        }
    }
}

/**
 * Stopwatch formatter: >=24h renders as "1d 02:03:04[.67]" so 100h+ fits
 * narrow screens; the fractional part is CENTISECONDS (2 digits). Three
 * digits + a 30ms AnimatedContent churned the text animation, so ms mode
 * renders plain Text (see StopwatchDial) and shows 2 digits.
 */
private fun formatStopwatchTime(timeMillis: Long, showMilliseconds: Boolean, locale: Locale): String {
    val safe = timeMillis.coerceAtLeast(0L)
    val totalSeconds = safe / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val centis = (safe % 1000) / 10
    if (hours >= 24) {
        val days = hours / 24
        val h = hours % 24
        return if (showMilliseconds) {
            String.format(locale, "%dd %02d:%02d:%02d.%02d", days, h, minutes, seconds, centis)
        } else {
            String.format(locale, "%dd %02d:%02d:%02d", days, h, minutes, seconds)
        }
    }
    return when {
        showMilliseconds && hours > 0 -> String.format(locale, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
        showMilliseconds -> String.format(locale, "%02d:%02d.%02d", minutes, seconds, centis)
        hours > 0 -> String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format(locale, "%02d:%02d", minutes, seconds)
    }
}