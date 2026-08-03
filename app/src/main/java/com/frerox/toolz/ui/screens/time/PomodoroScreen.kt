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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ExpressiveSlider
import com.frerox.toolz.ui.components.ExpressiveStatePill
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzHorizontalFloatingToolbar
import com.frerox.toolz.ui.components.ToolzWavyCircularProgressIndicator
import com.frerox.toolz.ui.components.ToolzWavyLinearProgressIndicator
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.screens.time.components.PomodoroQuoteMarquee
import com.frerox.toolz.ui.screens.time.components.PomodoroSettingsBottomSheet
import com.frerox.toolz.ui.screens.time.components.PomodoroSuccessConfetti
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PomodoroScreen(
    viewModel: PomodoroViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val rawActiveColor = state.mode.activeColor()
    val activeColor by animateColorAsState(
        targetValue = rawActiveColor,
        animationSpec = tween(durationMillis = 600),
        label = "activeColor"
    )
    val view = LocalView.current
    var showSettings by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    var lastCompleted by remember { mutableIntStateOf(state.sessionsCompleted) }
    LaunchedEffect(state.sessionsCompleted) {
        if (state.sessionsCompleted > lastCompleted && state.sessionsCompleted == state.sessionsGoal) {
            showConfetti = true
        }
        lastCompleted = state.sessionsCompleted
    }

    DisposableEffect(state.keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_PomodoroScreen_a1b2),
                subtitle = state.mode.supportingLabel,
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_PomodoroScreen_4f2d))
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
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.st_PomodoroScreen_c3d4))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        floatingActionButton = {
            PomodoroControlDock(
                state = state,
                activeColor = activeColor,
                onToggle = {
                    if (state.isFinished) viewModel.stopRingtone()
                    viewModel.toggleStartStop()
                },
                onReset = viewModel::reset,
                onSkip = viewModel::skip,
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = Color.Transparent,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            PomodoroContent(
                state = state,
                activeColor = activeColor,
                contentPadding = padding,
                onModeSelected = viewModel::selectMode,
                onSilence = viewModel::stopRingtone,
            )
            
            if (showConfetti) {
                PomodoroSuccessConfetti(onFinished = { showConfetti = false })
            }
        }

        if (showSettings) {
            PomodoroSettingsBottomSheet(
                state = state,
                activeColor = activeColor,
                onDismiss = { showSettings = false },
                onWorkMinutesChanged = viewModel::setWorkMinutes,
                onShortBreakMinutesChanged = viewModel::setShortBreakMinutes,
                onLongBreakMinutesChanged = viewModel::setLongBreakMinutes,
                onGoalChanged = viewModel::setSessionsGoal,
                onAutoStartChanged = viewModel::setAutoStartNext,
                onKeepScreenOnChanged = viewModel::setKeepScreenOn,
                onShowQuotesChanged = viewModel::setShowQuotes,
                onQuotesChanged = viewModel::setQuotes,
                onAiFormat = viewModel::formatQuotesWithAi,
                onResetQuotes = viewModel::resetQuotes,
                onResetGoal = viewModel::resetGoal,
                onGradualVolumeChanged = viewModel::setGradualVolume
            )
        }
    }
}

@Composable
private fun PomodoroContent(
    state: PomodoroState,
    activeColor: Color,
    contentPadding: PaddingValues,
    onModeSelected: (PomodoroMode) -> Unit,
    onSilence: () -> Unit,
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
            PomodoroTimerDial(state = state, activeColor = activeColor)
        }
        StaggeredEntrance(index = 1) {
            PomodoroModeSelector(
                selectedMode = state.mode,
                enabled = !state.isRunning,
                onModeSelected = onModeSelected,
            )
        }
        if (state.showQuotes) {
            StaggeredEntrance(index = 2) {
                PomodoroQuoteMarquee(quotesText = state.quotes, activeColor = activeColor)
            }
        }
        AnimatedVisibility(
            visible = state.isFinished,
            enter = fadeIn() + scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
            exit = fadeOut() + scaleOut(),
        ) {
            CompletionBanner(mode = state.mode, onSilence = onSilence)
        }
        StaggeredEntrance(index = 3) {
            PomodoroStatsRow(state = state, activeColor = activeColor)
        }
    }
}

@Composable
private fun PomodoroTimerDial(state: PomodoroState, activeColor: Color) {
    val rawProgress = 1f - (state.remainingTime.toFloat() / state.totalTime.toFloat())
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "PomodoroProgress",
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val dialSize = maxWidth.coerceAtMost(330.dp)
        Box(
            modifier = Modifier.size(dialSize),
            contentAlignment = Alignment.Center,
        ) {
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
                    val locale = LocalConfiguration.current.locales[0]
                    AnimatedContent(
                        targetState = formatPomodoroTime(state.remainingTime, locale),
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)).togetherWith(fadeOut()) },
                        label = "PomodoroTime",
                    ) { time ->
                        Text(
                            text = time,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.sp,
                            ),
                            color = if (state.isRunning) activeColor else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ExpressiveStatePill(
                        text = if (state.isRunning) state.mode.label else stringResource(R.string.st_PomodoroScreen_d5e6),
                        icon = if (state.mode == PomodoroMode.WORK) Icons.Rounded.CenterFocusStrong else Icons.Rounded.Coffee,
                        color = activeColor,
                    )
                }
            }
            ToolzWavyCircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = activeColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun PomodoroModeSelector(
    selectedMode: PomodoroMode,
    enabled: Boolean,
    onModeSelected: (PomodoroMode) -> Unit,
) {
    ToolzConnectedButtonGroup(
        selectedIndex = PomodoroMode.entries.indexOf(selectedMode),
        options = PomodoroMode.entries.map { it.label },
        enabled = enabled,
        onOptionSelected = { onModeSelected(PomodoroMode.entries[it]) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PomodoroStatsRow(state: PomodoroState, activeColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
            label = stringResource(R.string.st_PomodoroScreen_f7g8),
            value = "${state.sessionsCompleted}/${state.sessionsGoal}",
            accent = activeColor,
            progress = (state.sessionsCompleted.toFloat() / state.sessionsGoal.toFloat()).coerceIn(0f, 1f)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
            label = stringResource(R.string.st_PomodoroScreen_h9i0),
            value = nextPhaseLabel(state),
            accent = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    accent: Color,
    progress: Float? = null,
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = SmallExpressiveShape, color = accent.copy(alpha = 0.14f), contentColor = accent) {
                    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        icon()
                    }
                }
                if (progress != null) {
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = accent
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) {
                ToolzWavyLinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun CompletionBanner(mode: PomodoroMode, onSilence: () -> Unit) {
    ExpressiveCard(
        onClick = onSilence,
        modifier = Modifier.fillMaxWidth(),
        shape = BouncyShape,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        elevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.st_PomodoroScreen_j1k2), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(
                    if (mode == PomodoroMode.WORK) stringResource(R.string.st_PomodoroScreen_l3m4) else stringResource(R.string.st_PomodoroScreen_n5o6),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PomodoroControlDock(
    state: PomodoroState,
    activeColor: Color,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
) {
    val resetLabel = stringResource(R.string.st_PomodoroScreen_p7q8)
    val skipLabel = stringResource(R.string.st_PomodoroScreen_r9s0)
    val pauseLabel = stringResource(R.string.st_PomodoroScreen_u1v2)
    val startLabel = stringResource(R.string.st_PomodoroScreen_w3x4)

    ToolzHorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        trailingContent = {
            clickableItem(
                onClick = onReset,
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                label = resetLabel,
            )
            clickableItem(
                onClick = onSkip,
                icon = { Icon(Icons.Rounded.SkipNext, contentDescription = null) },
                label = skipLabel,
            )
        },
    ) {
        ToolzExpressiveButton(
            onClick = onToggle,
            shape = BouncyShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else activeColor,
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
            Text(if (state.isRunning) pauseLabel else startLabel, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PomodoroMode.activeColor(): Color = when (this) {
    PomodoroMode.WORK -> MaterialTheme.colorScheme.primary
    PomodoroMode.SHORT_BREAK -> MaterialTheme.colorScheme.tertiary
    PomodoroMode.LONG_BREAK -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun phaseMessage(state: PomodoroState): String = when {
    state.isRunning && state.mode == PomodoroMode.WORK -> stringResource(R.string.st_PomodoroScreen_y5z6)
    state.isRunning -> stringResource(R.string.st_PomodoroScreen_a7b8)
    state.isFinished -> stringResource(R.string.st_PomodoroScreen_c9d0)
    else -> stringResource(R.string.st_PomodoroScreen_e1f2)
}

@Composable
private fun nextPhaseLabel(state: PomodoroState): String = when {
    state.mode != PomodoroMode.WORK -> stringResource(R.string.st_PomodoroScreen_g3h4)
    (state.sessionsCompleted + 1) % 4 == 0 -> stringResource(R.string.st_PomodoroScreen_i5j6)
    else -> stringResource(R.string.st_PomodoroScreen_k7l8)
}

private fun formatPomodoroTime(timeMillis: Long, locale: Locale): String {
    val totalSeconds = ((timeMillis + 999) / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(locale, "%02d:%02d", minutes, seconds)
}
