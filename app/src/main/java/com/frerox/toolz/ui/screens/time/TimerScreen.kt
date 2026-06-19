package com.frerox.toolz.ui.screens.time

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveFilterChip
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
import com.frerox.toolz.ui.screens.time.components.PomodoroSuccessConfetti
import com.frerox.toolz.ui.screens.time.components.PreferenceRow
import com.frerox.toolz.ui.screens.time.components.SettingsSection
import com.frerox.toolz.ui.screens.time.components.TimeSettingsBottomSheet
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val rawAccent = if (state.isFinished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val accent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(durationMillis = 500),
        label = "timerAccent",
    )
    val view = LocalView.current
    var showConfetti by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.isFinished) {
        if (state.isFinished) showConfetti = true
    }

    DisposableEffect(state.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Timer",
                subtitle = timerSubtitle(state),
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
            TimerControlDock(
                state = state,
                accent = accent,
                onToggle = {
                    if (state.isFinished) viewModel.stopRingtone()
                    viewModel.toggleStartStop()
                },
                onReset = viewModel::reset,
                onResetToInitial = viewModel::resetToInitial,
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = Color.Transparent,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            TimerContent(
                state = state,
                accent = accent,
                contentPadding = padding,
                onTimeSelected = viewModel::onTimeSelectedChange,
                onPresetSelected = { minutes -> viewModel.setTimer(minutes, 0) },
                onAddTime = viewModel::addTime,
                onDismissAlarm = {
                    viewModel.stopRingtone()
                    if (state.repeatLastDuration) viewModel.resetToInitial() else viewModel.reset()
                },
            )

            if (showConfetti) {
                PomodoroSuccessConfetti(onFinished = { showConfetti = false })
            }
        }

        if (showSettings) {
            TimeSettingsBottomSheet(
                title = "Timer Settings",
                onDismiss = { showSettings = false },
                accent = accent
            ) {
                SettingsSection(title = "Behavior", icon = Icons.Rounded.Settings, accent = accent) {
                    PreferenceRow(
                        title = "Repeat-ready",
                        subtitle = "Dismiss alarm back to the last duration",
                        checked = state.repeatLastDuration,
                        onCheckedChange = viewModel::setRepeatLastDuration,
                    )
                    PreferenceRow(
                        title = "Keep screen awake",
                        subtitle = "Useful for kitchen, workout, or desk timing",
                        checked = state.keepScreenOn,
                        onCheckedChange = viewModel::setKeepScreenOn,
                    )
                }
                SettingsSection(title = "Alerts", icon = Icons.Rounded.Notifications, accent = accent) {
                    PreferenceRow(
                        title = "Gradual Volume",
                        subtitle = "Increase alarm volume slowly",
                        checked = state.gradualVolume,
                        onCheckedChange = viewModel::setGradualVolume,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerContent(
    state: TimerState,
    accent: Color,
    contentPadding: PaddingValues,
    onTimeSelected: (Int, Int) -> Unit,
    onPresetSelected: (Int) -> Unit,
    onAddTime: (Long) -> Unit,
    onDismissAlarm: () -> Unit,
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
            TimerDial(state = state, accent = accent)
        }
        StaggeredEntrance(index = 1) {
            TimerWheelPicker(state = state, accent = accent, onTimeSelected = onTimeSelected)
        }
        StaggeredEntrance(index = 2) {
            TimerPresets(enabled = !state.isRunning, onPresetSelected = onPresetSelected)
        }
        AnimatedVisibility(
            visible = state.remainingTime > 0L || state.initialTime > 0L,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            TimerQuickAddRow(onAddTime = onAddTime)
        }
        AnimatedVisibility(
            visible = state.isFinished,
            enter = fadeIn() + scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
            exit = fadeOut() + scaleOut(),
        ) {
            TimerFinishedBanner(onDismissAlarm = onDismissAlarm)
        }
        StaggeredEntrance(index = 3) {
            TimerDetailRow(state = state, accent = accent)
        }
    }
}

@Composable
private fun TimerDial(state: TimerState, accent: Color) {
    val progress = if (state.initialTime > 0L) {
        1f - (state.remainingTime.toFloat() / state.initialTime.toFloat())
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "TimerProgress",
    )

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
                        targetState = formatTimerTime(displayMillis(state)),
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)).togetherWith(fadeOut()) },
                        label = "TimerTime",
                    ) { time: String ->
                        Text(
                            text = time,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.sp,
                            ),
                            color = if (state.isRunning || state.isFinished) accent else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ExpressiveStatePill(
                        text = if (state.isRunning) "Counting down" else if (state.isFinished) "Finished" else "Ready",
                        icon = timerStatusIcon(state),
                        color = accent,
                    )
                }
            }
            ToolzWavyCircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TimerWheelPicker(
    state: TimerState,
    accent: Color,
    onTimeSelected: (Int, Int) -> Unit,
) {
    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        elevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Set duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfiniteTimeWheel(
                    value = state.selectedMinutes,
                    valueRange = 0..999,
                    label = "Minutes",
                    accent = accent,
                    enabled = !state.isRunning,
                    onValueChange = { onTimeSelected(it, state.selectedSeconds) },
                )
                Text(
                    text = ":",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
                InfiniteTimeWheel(
                    value = state.selectedSeconds,
                    valueRange = 0..59,
                    label = "Seconds",
                    accent = accent,
                    enabled = !state.isRunning,
                    onValueChange = { onTimeSelected(state.selectedMinutes, it) },
                )
            }
        }
    }
}

@Composable
private fun InfiniteTimeWheel(
    value: Int,
    valueRange: IntRange,
    label: String,
    accent: Color,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    val itemCount = valueRange.last - valueRange.first + 1
    val initialPage = (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2) % itemCount + value
    val pagerState = rememberPagerState(initialPage = initialPage) { Int.MAX_VALUE }

    LaunchedEffect(enabled) {
        if (enabled) {
            snapshotFlow { pagerState.settledPage }
                .collect { page ->
                    val newValue = (page % itemCount) + valueRange.first
                    if (newValue != value) onValueChange(newValue)
                }
        }
    }

    LaunchedEffect(value, enabled) {
        if (!pagerState.isScrollInProgress) {
            val currentPageValue = (pagerState.currentPage % itemCount) + valueRange.first
            if (currentPageValue != value) {
                val targetPage = pagerState.currentPage + (value - currentPageValue)
                pagerState.scrollToPage(targetPage)
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .width(116.dp)
                    .height(172.dp),
                shape = MediumExpressiveShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (enabled) 0.9f else 0.45f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.16f)),
            ) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (enabled) 1f else 0.42f),
                    userScrollEnabled = enabled,
                    contentPadding = PaddingValues(vertical = 54.dp),
                ) { index ->
                    val itemValue = valueRange.first + (index % itemCount)
                    val isSelected = itemValue == (pagerState.currentPage % itemCount) + valueRange.first
                    val locale = LocalConfiguration.current.locales[0]
                    
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.2f else 0.8f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "wheelScale"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                alpha = if (isSelected) 1f else 0.3f
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(locale, "%02d", itemValue),
                            style = if (isSelected) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .width(104.dp)
                    .height(58.dp),
                shape = BouncyShape,
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
            ) {}
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = accent,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun TimerPresets(enabled: Boolean, onPresetSelected: (Int) -> Unit) {
    val presets = listOf(1, 3, 5, 10, 25, 45)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Quick presets", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.take(3).forEach { minutes ->
                PresetChip(minutes = minutes, enabled = enabled, modifier = Modifier.weight(1f), onPresetSelected = onPresetSelected)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.drop(3).forEach { minutes ->
                PresetChip(minutes = minutes, enabled = enabled, modifier = Modifier.weight(1f), onPresetSelected = onPresetSelected)
            }
        }
    }
}

@Composable
private fun PresetChip(minutes: Int, enabled: Boolean, modifier: Modifier, onPresetSelected: (Int) -> Unit) {
    ExpressiveFilterChip(
        selected = false,
        enabled = enabled,
        onClick = { onPresetSelected(minutes) },
        modifier = modifier,
        label = {
            Text(
                text = "${minutes}m",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black,
            )
        },
    )
}

@Composable
private fun TimerQuickAddRow(onAddTime: (Long) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAddCard(label = "+30s", millis = 30_000L, modifier = Modifier.weight(1f), onAddTime = onAddTime)
        QuickAddCard(label = "+1m", millis = 60_000L, modifier = Modifier.weight(1f), onAddTime = onAddTime)
        QuickAddCard(label = "+5m", millis = 300_000L, modifier = Modifier.weight(1f), onAddTime = onAddTime)
    }
}

@Composable
private fun QuickAddCard(label: String, millis: Long, modifier: Modifier, onAddTime: (Long) -> Unit) {
    ExpressiveCard(
        onClick = { onAddTime(millis) },
        modifier = modifier.height(62.dp),
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun TimerDetailRow(state: TimerState, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimerMetricCard(
            modifier = Modifier.weight(1f),
            icon = { Icon(Icons.Rounded.HourglassEmpty, contentDescription = null) },
            label = "Duration",
            value = formatTimerTime(state.initialTime),
            accent = accent,
        )
        TimerMetricCard(
            modifier = Modifier.weight(1f),
            icon = { Icon(Icons.Rounded.Alarm, contentDescription = null) },
            label = "Remaining",
            value = formatTimerTime(displayMillis(state)),
            accent = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun TimerMetricCard(
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
private fun TimerFinishedBanner(onDismissAlarm: () -> Unit) {
    ExpressiveCard(
        onClick = onDismissAlarm,
        modifier = Modifier.fillMaxWidth(),
        shape = BouncyShape,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        elevation = 0.dp,
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Timer complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text("Tap to dismiss the alarm.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimerControlDock(
    state: TimerState,
    accent: Color,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onResetToInitial: () -> Unit,
) {
    ToolzHorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        trailingContent = {
            clickableItem(
                onClick = onResetToInitial,
                icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                label = "Again",
                enabled = state.initialTime > 0L,
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
            enabled = state.remainingTime > 0L || selectedDurationMillis(state) > 0L,
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

private fun timerSubtitle(state: TimerState): String = when {
    state.isRunning -> "Counting down"
    state.isFinished -> "Time is up"
    state.isPaused -> "Paused"
    else -> "Precision countdown"
}

private fun timerStatusIcon(state: TimerState) = when {
    state.isFinished -> Icons.Rounded.NotificationsActive
    state.isRunning -> Icons.Rounded.Timer
    state.isPaused -> Icons.Rounded.Pause
    else -> Icons.Rounded.HourglassEmpty
}

private fun displayMillis(state: TimerState): Long = when {
    state.isFinished -> 0L
    state.remainingTime > 0L -> state.remainingTime
    state.initialTime > 0L && !state.isFinished -> state.initialTime
    else -> selectedDurationMillis(state)
}

private fun selectedDurationMillis(state: TimerState): Long {
    return (state.selectedMinutes * 60L + state.selectedSeconds) * 1000L
}

private fun formatTimerTime(timeMillis: Long): String {
    val totalSeconds = ((timeMillis + 999) / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
