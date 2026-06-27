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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.TextButton
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.VerticalSmoothDurationPicker
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.FinishedOverlay
import com.frerox.toolz.ui.screens.time.components.PomodoroSuccessConfetti
import com.frerox.toolz.ui.screens.time.components.PreferenceRow
import com.frerox.toolz.ui.screens.time.components.SettingsSection
import com.frerox.toolz.ui.screens.time.components.TimeSettingsBottomSheet
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.Locale

@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val timerHistory by viewModel.timerHistory.collectAsState()
    val rawAccent = if (state.isFinished || state.isRinging) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val accent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(durationMillis = 500),
        label = "timerAccent",
    )
    val view = LocalView.current
    var showConfetti by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var presetToEdit by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.isFinished) {
        if (state.isFinished) showConfetti = true
    }

    DisposableEffect(state.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.keepScreenOn
        onDispose { 
            view.keepScreenOn = previous
        }
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
                    if (state.isRinging) {
                        viewModel.stopRingtone()
                    } else {
                        viewModel.toggleStartStop()
                    }
                },
                onReset = viewModel::reset,
                onToggleAlarms = viewModel::toggleAlarms,
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
                onPresetSelected = { mins, secs -> viewModel.setTimer(mins, secs) },
                onPresetLongClick = { index -> presetToEdit = index },
                onAddTime = viewModel::addTime,
                onDismissAlarm = {
                    viewModel.stopRingtone()
                    if (state.repeatLastDuration) viewModel.resetToInitial() else viewModel.reset()
                },
                timerHistory = timerHistory,
            )

            if (showConfetti) {
                PomodoroSuccessConfetti(onFinished = { showConfetti = false })
            }
            
            presetToEdit?.let { index ->
                LockPresetDialog(
                    initialMinutes = timerHistory.getOrNull(index)?.first ?: 0,
                    initialSeconds = timerHistory.getOrNull(index)?.second ?: 0,
                    onDismiss = { presetToEdit = null },
                    onConfirm = { m, s ->
                        viewModel.lockPreset(index, m, s)
                        presetToEdit = null
                    },
                    accent = accent
                )
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
    onPresetSelected: (Int, Int) -> Unit,
    onPresetLongClick: (Int) -> Unit,
    onAddTime: (Long) -> Unit,
    onDismissAlarm: () -> Unit,
    timerHistory: List<Pair<Int, Int>> = emptyList(),
) {
    val performanceMode = LocalPerformanceMode.current

    // Finished overlay (ringing)
    if (state.isRinging) {
        FinishedOverlay(
            totalDurationMillis = state.initialTime,
            onDismiss = onDismissAlarm,
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = contentPadding.calculateTopPadding()),
        )
        return
    }

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
            TimerPresets(
                enabled = !state.isRunning,
                onPresetSelected = onPresetSelected,
                onPresetLongClick = onPresetLongClick,
                timerHistory = timerHistory,
                accent = accent,
            )
        }
        AnimatedVisibility(
            visible = state.remainingTime > 0L || state.initialTime > 0L,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            TimerQuickAddRow(onAddTime = onAddTime)
        }
        AnimatedVisibility(
            visible = state.isRinging,
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
        animationSpec = tween(100, easing = androidx.compose.animation.core.LinearEasing),
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
                        text = if (state.isRunning) "Counting down" else if (state.isRinging) "Ringing" else "Ready",
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
    var showCustomDurationDialog by remember { mutableStateOf(false) }

    if (showCustomDurationDialog) {
        CustomDurationDialog(
            currentMinutes = state.selectedMinutes,
            accent = accent,
            onDismiss = { showCustomDurationDialog = false },
            onConfirm = { mins ->
                onTimeSelected(mins, state.selectedSeconds)
                showCustomDurationDialog = false
            }
        )
    }

    ExpressiveCard(
        onClick = {},
        onLongClick = {
            if (!state.isRunning && !state.isRinging) {
                showCustomDurationDialog = true
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Set Duration",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Icon(
                    Icons.Rounded.HourglassEmpty,
                    null,
                    tint = accent.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            if (state.selectedMinutes > 59) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${state.selectedMinutes}m : ${String.format(Locale.getDefault(), "%02d", state.selectedSeconds)}s",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        color = accent
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { onTimeSelected(0, state.selectedSeconds) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Reset to wheels", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                VerticalSmoothDurationPicker(
                    minutes = state.selectedMinutes,
                    seconds = state.selectedSeconds,
                    accent = accent,
                    enabled = !state.isRunning && !state.isRinging,
                    onChange = onTimeSelected,
                )
            }
        }
    }
}

@Composable
private fun TimerPresets(
    enabled: Boolean,
    onPresetSelected: (Int, Int) -> Unit,
    onPresetLongClick: (Int) -> Unit,
    timerHistory: List<Pair<Int, Int>>,
    accent: Color,
) {
    val presets = if (timerHistory.isNotEmpty()) {
        timerHistory.take(3)
    } else {
        listOf(Pair(5, 0), Pair(15, 0), Pair(30, 0))
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "History Presets",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
            Icon(
                Icons.Rounded.RestartAlt,
                null,
                tint = accent.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            presets.forEachIndexed { index, (minutes, seconds) ->
                PresetCard(
                    minutes = minutes,
                    seconds = seconds,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onPresetSelected = onPresetSelected,
                    onLongClick = { onPresetLongClick(index) },
                    accent = accent,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(
    minutes: Int,
    seconds: Int,
    enabled: Boolean,
    modifier: Modifier,
    onPresetSelected: (Int, Int) -> Unit,
    onLongClick: () -> Unit,
    accent: Color,
) {
    val vibrationManager = com.frerox.toolz.ui.theme.LocalVibrationManager.current
    val durationLabel = if (seconds > 0) {
        if (minutes > 0) "$minutes:${String.format("%02d", seconds)}" else "${seconds}s"
    } else {
        "$minutes"
    }
    val unitLabel = if (minutes > 0 && seconds == 0) "MIN" else ""

    ExpressiveCard(
        onClick = { onPresetSelected(minutes, seconds) },
        onLongClick = {
            vibrationManager?.vibrateLongClick()
            onLongClick()
        },
        enabled = enabled,
        modifier = modifier.height(84.dp),
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.6f else 0.3f),
        elevation = 0.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            if (unitLabel.isNotEmpty()) {
                Text(
                    text = unitLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = accent.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun LockPresetDialog(
    initialMinutes: Int,
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
    accent: Color
) {
    var mins by remember { mutableStateOf(initialMinutes) }
    var secs by remember { mutableStateOf(initialSeconds) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ExpressiveCard(
            onClick = {},
            shape = LargeExpressiveShape,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Lock Preset",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "This slot will be locked to this duration",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.Lock, null, tint = accent, modifier = Modifier.size(24.dp))
                }

                VerticalSmoothDurationPicker(
                    minutes = mins,
                    seconds = secs,
                    accent = accent,
                    enabled = true,
                    onChange = { m, s -> mins = m; secs = s },
                    modifier = Modifier.height(180.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = MediumExpressiveShape
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Bold)
                    }
                    ToolzExpressiveButton(
                        onClick = { onConfirm(mins, secs) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = MediumExpressiveShape,
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("LOCK", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}


@Composable
private fun TimerQuickAddRow(onAddTime: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        QuickAddButton(label = "+10s", millis = 10_000L, modifier = Modifier.weight(1f), onAddTime = onAddTime)
        QuickAddButton(label = "+1m", millis = 60_000L, modifier = Modifier.weight(1f), onAddTime = onAddTime)
        QuickAddButton(label = "+5m", millis = 300_000L, modifier = Modifier.weight(1f), onAddTime = onAddTime)
    }
}

@Composable
private fun QuickAddButton(label: String, millis: Long, modifier: Modifier, onAddTime: (Long) -> Unit) {
    ExpressiveCard(
        onClick = { onAddTime(millis) },
        modifier = modifier.height(56.dp),
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        elevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
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
    onToggleAlarms: () -> Unit,
) {
    ToolzHorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        trailingContent = {
            clickableItem(
                onClick = onToggleAlarms,
                icon = {
                    Icon(
                        if (state.alarmsEnabled) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = if (state.alarmsEnabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                label = if (state.alarmsEnabled) "On" else "Off",
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
            enabled = state.remainingTime > 0L || state.initialTime > 0L || state.selectedMinutes > 0 || state.selectedSeconds > 0,
            shape = BouncyShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isRunning || state.isRinging) MaterialTheme.colorScheme.errorContainer else accent,
                contentColor = if (state.isRunning || state.isRinging) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Icon(
                if (state.isRunning) Icons.Rounded.Pause
                else if (state.isRinging) Icons.Rounded.NotificationsActive
                else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    state.isRunning -> "Pause"
                    state.isRinging -> "Dismiss"
                    else -> "Start"
                },
                fontWeight = FontWeight.Black
            )
        }
    }
}

private fun timerSubtitle(state: TimerState): String = when {
    state.isRunning -> "Counting down"
    state.isRinging -> "Time is up"
    state.isPaused -> "Paused"
    else -> "Precision countdown"
}

private fun timerStatusIcon(state: TimerState) = when {
    state.isRinging -> Icons.Rounded.NotificationsActive
    state.isRunning -> Icons.Rounded.Timer
    state.isPaused -> Icons.Rounded.Pause
    else -> Icons.Rounded.HourglassEmpty
}

private fun displayMillis(state: TimerState): Long = when {
    state.isRinging -> 0L
    state.remainingTime > 0L -> state.remainingTime
    state.initialTime > 0L && !state.isRinging -> state.initialTime
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDurationDialog(
    currentMinutes: Int,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(if (currentMinutes > 0) currentMinutes.toString() else "") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Set Duration", fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { 
                    if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.length <= 4)) {
                        text = it
                    }
                },
                label = { Text("Minutes") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    focusedLabelColor = accent,
                    cursorColor = accent
                )
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    val mins = text.toIntOrNull() ?: 0
                    onConfirm(mins)
                },
                colors = ButtonDefaults.textButtonColors(contentColor = accent)
            ) {
                Text("Confirm", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
