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

import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ExpressiveStatePill
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.TextButton
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.PrecisionTimerText
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
    val userMessage by viewModel.userMessage.collectAsState()
    val rawAccent = if (state.isFinished || state.isRinging) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val accent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(durationMillis = 500),
        label = "timerAccent",
    )
    val view = LocalView.current
    // T-P2-05: rememberSaveable (survives rotation even without configChanges).
    var showConfetti by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pendingPreset by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingStaging by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // T-P2-05: confetti keyed on isRinging (not isFinished) — no confetti on silent finish.
    LaunchedEffect(state.isRinging) {
        if (state.isRinging) showConfetti = true
    }

    LaunchedEffect(userMessage) {
        if (userMessage != null) {
            snackbarHostState.showSnackbar(userMessage!!)
            viewModel.consumeMessage()
        }
    }

    // T-P1-04: gate keepScreenOn — screen off while idle 0:00 (no keep-on leak).
    // Backgrounded fallback is the service WakeLock (no view flag while backgrounded).
    DisposableEffect(state.keepScreenOn, state.isRunning, state.isRinging) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.keepScreenOn && (state.isRunning || state.isRinging)
        onDispose {
            view.keepScreenOn = previous
        }
    }

    // T-P2-05: Back while ringing can't strand alarm — block back, require explicit Stop.
    BackHandler(enabled = state.isRinging) {
        // Intentionally no-op: user must tap Dismiss/Stop in the ringing overlay.
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_TimerScreen_a1b2),
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_TimerScreen_c3d4))
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
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.st_TimerScreen_e5f6))
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            TimerContent(
                state = state,
                accent = accent,
                contentPadding = padding,
                onTimeSelected = { h, m, s ->
                    // T-P1-03: replacing a paused-after-run timer requires
                    // confirm (dialog); merely staged durations edit freely.
                    if (!state.isRunning && state.remainingTime > 0L && state.hasRun && !state.isRinging) {
                        pendingStaging = "$h:$m:$s"
                    } else {
                        viewModel.onTimeSelectedChange(h, m, s)
                    }
                },
                onPresetSelected = { h, mins, secs ->
                    if (!state.isRunning && state.remainingTime > 0L && state.hasRun && !state.isRinging) {
                        pendingPreset = "$h:$mins:$secs"
                    } else {
                        viewModel.setTimer(h, mins, secs)
                    }
                },
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

            // "Start a new timer?" confirm for staging/preset while paused (T-P1-03).
            // Cancel/dismiss just drops the proposal — the paused timer is
            // untouched (rollback), since nothing is staged until confirm.
            pendingPreset?.let { encoded ->
                val (ph, pm, ps) = decodeHms(encoded)
                AlertDialog(
                    onDismissRequest = { pendingPreset = null },
                    title = { Text("Start a new timer?") },
                    text = { Text("A timer is paused at ${formatTimerTime(state.remainingTime)}. Start a new ${formatHms(ph, pm, ps)} timer instead?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setTimer(ph, pm, ps, true)
                            pendingPreset = null
                        }) { Text("Start") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingPreset = null }) { Text(stringResource(R.string.st_TimerScreen_q3r4)) }
                    },
                )
            }
            pendingStaging?.let { encoded ->
                val (sh, sm, ss) = decodeHms(encoded)
                AlertDialog(
                    onDismissRequest = { pendingStaging = null },
                    title = { Text("Start a new timer?") },
                    text = { Text("A timer is paused at ${formatTimerTime(state.remainingTime)}. Start a new ${formatHms(sh, sm, ss)} timer instead?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.onTimeSelectedChange(sh, sm, ss, true)
                            pendingStaging = null
                        }) { Text("Start") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingStaging = null }) { Text(stringResource(R.string.st_TimerScreen_q3r4)) }
                    },
                )
            }
        }

        if (showSettings) {
            TimeSettingsBottomSheet(
                title = stringResource(R.string.st_TimerScreen_g7h8),
                onDismiss = { showSettings = false },
                accent = accent
            ) {
                SettingsSection(title = stringResource(R.string.st_TimerScreen_i9j0), icon = Icons.Rounded.Settings, accent = accent) {
                    PreferenceRow(
                        title = stringResource(R.string.st_TimerScreen_b2c3),
                        subtitle = stringResource(R.string.st_TimerScreen_d4e5),
                        checked = state.showMilliseconds,
                        onCheckedChange = viewModel::setShowMilliseconds,
                    )
                    PreferenceRow(
                        title = stringResource(R.string.st_TimerScreen_f6g7),
                        subtitle = stringResource(R.string.st_TimerScreen_h8i9),
                        checked = state.showStatusPill,
                        onCheckedChange = viewModel::setShowStatusPill,
                    )
                    PreferenceRow(
                        title = stringResource(R.string.st_TimerScreen_k1l2),
                        subtitle = stringResource(R.string.st_TimerScreen_m3n4),
                        checked = state.repeatLastDuration,
                        onCheckedChange = viewModel::setRepeatLastDuration,
                    )
                    PreferenceRow(
                        title = stringResource(R.string.st_TimerScreen_o5p6),
                        subtitle = stringResource(R.string.st_TimerScreen_q7r8),
                        checked = state.keepScreenOn,
                        onCheckedChange = viewModel::setKeepScreenOn,
                    )
                }
                SettingsSection(title = stringResource(R.string.st_TimerScreen_s9t0), icon = Icons.Rounded.Notifications, accent = accent) {
                    PreferenceRow(
                        title = stringResource(R.string.st_TimerScreen_u1v2),
                        subtitle = stringResource(R.string.st_TimerScreen_w3x4),
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
    onTimeSelected: (Int, Int, Int) -> Unit,
    onPresetSelected: (Int, Int, Int) -> Unit,
    onAddTime: (Long) -> Unit,
    onDismissAlarm: () -> Unit,
    timerHistory: List<Triple<Int, Int, Int>> = emptyList(),
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
                // Wheel + presets are disabled while counting down or ringing:
                // a running timer can't be modified. Tapping a preset while
                // paused routes through the "Start a new timer?" confirm.
                enabled = !state.isRunning && !state.isRinging,
                onPresetSelected = onPresetSelected,
                timerHistory = timerHistory,
                accent = accent,
            )
        }
        AnimatedVisibility(
            visible = state.remainingTime > 0L || state.initialTime > 0L,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            // T-P2-05: QuickAdd disabled at idle 0:00 (no implicit staging), cap feedback via snackbar.
            TimerQuickAddRow(
                onAddTime = onAddTime,
                enabled = state.remainingTime > 0L || state.initialTime > 0L || state.isRunning,
            )
        }
        // T-P2-05: dead TimerFinishedBanner removed (unreachable — ringing returns early via overlay).
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
    // T-P1-04: tick 250-500ms, snap progress (no 100ms tween waste).
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = androidx.compose.animation.core.LinearEasing),
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
                    val display = displayMillis(state)
                    val hasHours = display >= 3_600_000L
                    val mainStyle = if (hasHours) {
                        MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Black,
                            fontFeatureSettings = "tnum",
                            letterSpacing = 0.sp,
                        )
                    } else {
                        MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontFeatureSettings = "tnum",
                            letterSpacing = 0.sp,
                        )
                    }
                    PrecisionTimerText(
                        timeMillis = display,
                        showMillis = state.showMilliseconds,
                        isRunning = state.isRunning,
                        accent = accent,
                        style = mainStyle,
                        fractionStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    AnimatedVisibility(
                        visible = state.showStatusPill,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        ExpressiveStatePill(
                            text = if (state.isRunning) stringResource(R.string.st_TimerScreen_y5z6) else if (state.isRinging) stringResource(R.string.st_TimerScreen_a7b8) else stringResource(R.string.st_TimerScreen_c9d0),
                            icon = timerStatusIcon(state),
                            color = accent,
                        )
                    }
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
    onTimeSelected: (Int, Int, Int) -> Unit,
) {
    var showCustomDurationDialog by rememberSaveable { mutableStateOf(false) }
    // T-P2-02: wheel disabled while paused-after-run (replacing it needs the
    // "Start a new timer?" confirm) — never while merely staging a duration.
    val pickerEnabled = !state.isRunning && !state.isRinging && !(state.remainingTime > 0L && state.hasRun)

    if (showCustomDurationDialog) {
        CustomDurationDialog(
            currentHours = state.selectedHours,
            currentMinutes = state.selectedMinutes,
            currentSeconds = state.selectedSeconds,
            accent = accent,
            onDismiss = { showCustomDurationDialog = false },
            onConfirm = { h, mins, secs ->
                onTimeSelected(h, mins, secs)
                showCustomDurationDialog = false
            }
        )
    }

    ExpressiveCard(
        // T-P2-02: non-clickable card had misleading ripple — tap opens editor when idle.
        onClick = {
            if (pickerEnabled) showCustomDurationDialog = true
        },
        onLongClick = {
            if (pickerEnabled) {
                showCustomDurationDialog = true
            }
        },
        enabled = pickerEnabled,
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.st_TimerScreen_e1f2),
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

            // Infinite iPhone-style H/M/S wheel (M3 Expressive): hours 0..99,
            // minutes/seconds 0..59. Tap the card for direct entry (custom dialog).
            VerticalSmoothDurationPicker(
                hours = state.selectedHours,
                minutes = state.selectedMinutes,
                seconds = state.selectedSeconds,
                accent = accent,
                enabled = pickerEnabled,
                onChange = onTimeSelected,
            )
        }
    }
}

@Composable
private fun TimerPresets(
    enabled: Boolean,
    onPresetSelected: (Int, Int, Int) -> Unit,
    timerHistory: List<Triple<Int, Int, Int>>,
    accent: Color,
) {
    val presets = if (timerHistory.isNotEmpty()) {
        timerHistory.take(3)
    } else {
        listOf(Triple(0, 5, 0), Triple(0, 15, 0), Triple(0, 30, 0))
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.st_TimerScreen_i5j6),
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
            presets.forEach { (hours, minutes, seconds) ->
                PresetCard(
                    hours = hours,
                    minutes = minutes,
                    seconds = seconds,
                    tapEnabled = enabled,
                    modifier = Modifier.weight(1f),
                    onPresetSelected = onPresetSelected,
                    accent = accent,
                )
            }
        }
    }
}

@Composable
private fun PresetCard(
    hours: Int,
    minutes: Int,
    seconds: Int,
    tapEnabled: Boolean,
    modifier: Modifier,
    onPresetSelected: (Int, Int, Int) -> Unit,
    accent: Color,
) {
    val durationLabel = formatHms(hours, minutes, seconds)
    val unitLabel = when {
        hours > 0 -> ""
        minutes > 0 && seconds == 0 -> stringResource(R.string.st_TimerScreen_k7l8)
        else -> ""
    }

    ExpressiveCard(
        // Tap applies the preset. Disabled (dimmed) while the timer is
        // counting down or ringing — a running timer can't be modified.
        onClick = { if (tapEnabled) onPresetSelected(hours, minutes, seconds) },
        enabled = tapEnabled,
        modifier = modifier.height(84.dp),
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (tapEnabled) 0.6f else 0.3f),
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
                color = if (tapEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
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
private fun TimerQuickAddRow(onAddTime: (Long) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // FIX (user report): labels carried their own "+" ("+10s") AND the Add
        // icon — double "+". The icon is the "+", labels stay plain ("10s").
        QuickAddButton(label = "10s", millis = 10_000L, modifier = Modifier.weight(1f), onAddTime = onAddTime, enabled = enabled)
        QuickAddButton(label = "1m", millis = 60_000L, modifier = Modifier.weight(1f), onAddTime = onAddTime, enabled = enabled)
        QuickAddButton(label = "5m", millis = 300_000L, modifier = Modifier.weight(1f), onAddTime = onAddTime, enabled = enabled)
    }
}

@Composable
private fun QuickAddButton(label: String, millis: Long, modifier: Modifier, onAddTime: (Long) -> Unit, enabled: Boolean = true) {
    ExpressiveCard(
        onClick = { if (enabled) onAddTime(millis) },
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.7f else 0.35f),
        elevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, contentDescription = label, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
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
            icon = { Icon(Icons.Rounded.HourglassEmpty, contentDescription = stringResource(R.string.st_TimerScreen_u7v8)) },
            label = stringResource(R.string.st_TimerScreen_u7v8),
            value = formatTimerTime(state.initialTime),
            accent = accent,
        )
        TimerMetricCard(
            modifier = Modifier.weight(1f),
            icon = { Icon(Icons.Rounded.Alarm, contentDescription = stringResource(R.string.st_TimerScreen_w9x0)) },
            label = stringResource(R.string.st_TimerScreen_w9x0),
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
        enabled = false,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimerControlDock(
    state: TimerState,
    accent: Color,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onToggleAlarms: () -> Unit,
) {
    val onLabel = stringResource(R.string.st_TimerScreen_c5d6)
    val offLabel = stringResource(R.string.st_TimerScreen_e7f8)
    val resetLabel = stringResource(R.string.st_TimerScreen_g9h0)
    val pauseLabel = stringResource(R.string.st_TimerScreen_i1j2)
    val dismissLabel = stringResource(R.string.st_TimerScreen_k3l4)
    val startLabel = stringResource(R.string.st_TimerScreen_m5n6)
    val toggleCd = when {
        state.isRunning -> pauseLabel
        state.isRinging -> dismissLabel
        else -> startLabel
    }
    // T-P1-04: guard reset when idle (never enabled at 0:00).
    val canReset = state.remainingTime > 0L || state.initialTime > 0L || state.isRunning || state.isRinging || state.isStarted

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
                        contentDescription = if (state.alarmsEnabled) onLabel else offLabel,
                        tint = if (state.alarmsEnabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                label = if (state.alarmsEnabled) onLabel else offLabel,
            )
            clickableItem(
                onClick = onReset,
                enabled = canReset,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = resetLabel) },
                label = resetLabel,
            )
        },
    ) {
        ToolzExpressiveButton(
            onClick = onToggle,
            enabled = state.remainingTime > 0L || state.initialTime > 0L || state.selectedHours > 0 || state.selectedMinutes > 0 || state.selectedSeconds > 0,
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
                contentDescription = toggleCd,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    state.isRunning -> pauseLabel
                    state.isRinging -> dismissLabel
                    else -> startLabel
                },
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun timerSubtitle(state: TimerState): String = when {
    state.isRunning -> stringResource(R.string.st_TimerScreen_y5z6)
    state.isRinging -> stringResource(R.string.st_TimerScreen_a7b8)
    state.isPaused -> stringResource(R.string.st_TimerScreen_q9r0)
    else -> stringResource(R.string.st_TimerScreen_s1t2)
}

private fun timerStatusIcon(state: TimerState) = when {
    state.isRinging -> Icons.Rounded.NotificationsActive
    state.isRunning -> Icons.Rounded.Timer
    state.isPaused -> Icons.Rounded.Pause
    else -> Icons.Rounded.HourglassEmpty
}

/** Decode "h:m:s" (or legacy "m:s") into a Triple, all values coerced to wheel ranges. */
private fun decodeHms(encoded: String): Triple<Int, Int, Int> {
    val parts = encoded.split(":")
    return when (parts.size) {
        3 -> Triple(
            (parts[0].toIntOrNull() ?: 0).coerceIn(0, 99),
            (parts[1].toIntOrNull() ?: 0).coerceIn(0, 59),
            (parts[2].toIntOrNull() ?: 0).coerceIn(0, 59),
        )
        2 -> Triple(
            0,
            (parts[0].toIntOrNull() ?: 0).coerceIn(0, 59),
            (parts[1].toIntOrNull() ?: 0).coerceIn(0, 59),
        )
        else -> Triple(0, 0, 0)
    }
}

/** "H:MM:SS" when hours > 0, else "M:SS". */
private fun formatHms(hours: Int, minutes: Int, seconds: Int): String =
    if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }

private fun displayMillis(state: TimerState): Long = when {
    // T-P1-04: no fallback masking — dial shows raw remaining; staged preview lives in wheel picker.
    state.isRinging -> 0L
    else -> state.remainingTime.coerceAtLeast(0L)
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
    currentHours: Int = 0,
    currentMinutes: Int,
    currentSeconds: Int = 0,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit
) {
    // T-P2-04: key remember, validate hours 0..99 + mins/secs, error for 0, hoist focus effect.
    var hoursText by remember(currentHours) { mutableStateOf(if (currentHours > 0) currentHours.toString() else "") }
    var minsText by remember(currentMinutes) { mutableStateOf(if (currentMinutes > 0) currentMinutes.toString() else "") }
    var secsText by remember(currentSeconds) { mutableStateOf(if (currentSeconds > 0) currentSeconds.toString() else "") }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.st_TimerScreen_e1f2), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = {
                        if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.length <= 2)) {
                            hoursText = it
                            error = null
                        }
                    },
                    label = { Text("Hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        focusedLabelColor = accent,
                        cursorColor = accent
                    )
                )
                OutlinedTextField(
                    value = minsText,
                    onValueChange = {
                        if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.length <= 2)) {
                            minsText = it
                            error = null
                        }
                    },
                    label = { Text(stringResource(R.string.st_TimerScreen_u3v4)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        focusedLabelColor = accent,
                        cursorColor = accent
                    )
                )
                OutlinedTextField(
                    value = secsText,
                    onValueChange = {
                        if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.length <= 2)) {
                            secsText = it
                            error = null
                        }
                    },
                    label = { Text(stringResource(R.string.st_TimerScreen_w3x4)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        focusedLabelColor = accent,
                        cursorColor = accent
                    )
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hrs = hoursText.toIntOrNull() ?: 0
                    val mins = minsText.toIntOrNull() ?: -1
                    val secs = secsText.toIntOrNull() ?: 0
                    when {
                        hrs < 0 || hrs > 99 -> error = "Enter 0–99 hours"
                        mins < 0 || mins > 59 -> error = "Enter 0–59 minutes"
                        secs < 0 || secs > 59 -> error = "Enter 0–59 seconds"
                        hrs == 0 && mins == 0 && secs == 0 -> error = "Pick a duration greater than 0:00"
                        else -> onConfirm(hrs.coerceIn(0, 99), mins.coerceIn(0, 59), secs.coerceIn(0, 59))
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = accent)
            ) {
                Text(stringResource(R.string.st_TimerScreen_w5x6), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(stringResource(R.string.st_TimerScreen_y7z8))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Compat: legacy min/sec callers (hours = 0). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDurationDialog(
    currentMinutes: Int,
    currentSeconds: Int = 0,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    CustomDurationDialog(
        currentHours = 0,
        currentMinutes = currentMinutes,
        currentSeconds = currentSeconds,
        accent = accent,
        onDismiss = onDismiss,
        onConfirm = { _, m, s -> onConfirm(m, s) },
    )
}
