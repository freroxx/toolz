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

import android.Manifest
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.ToolzHorizontalFloatingToolbar
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import android.provider.Settings as AndroidSettings

// Gauge geometry: a 270° arc opening at the bottom, with the scale ends sitting on the lower corners.
private const val GAUGE_START_ANGLE = 135f
private const val GAUGE_SWEEP_ANGLE = 270f
private const val GAUGE_MAJOR_TICKS = 6
private const val GAUGE_MINOR_PER_MAJOR = 5

/** Keeps the trend chart from stretching tiny speeds to full height. */
private const val TREND_MIN_PEAK_MPS = 3f

private const val TabularDigits = "tnum"
private val HudRed = Color(0xFFFF5449)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeedometerScreen(
    viewModel: SpeedometerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val granted = permission.status.isGranted
    val vibration = LocalVibrationManager.current
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // GPS is on only while the screen is visible and permitted; the ViewModel cleans up on stop.
    LifecycleStartEffect(granted) {
        if (granted) viewModel.startUpdates()
        onStopOrDispose { viewModel.stopUpdates() }
    }

    val keepScreenOn = state.settings.keepScreenOn || state.isHudMode
    DisposableEffect(keepScreenOn, view) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(state.isOverLimit) {
        if (state.isOverLimit) vibration?.vibrateClick()
    }

    val tripResetMessage = stringResource(R.string.speedometer_trip_reset)
    val undoLabel = stringResource(R.string.speedometer_undo)
    val startLabel = stringResource(R.string.speedometer_trip_start)
    val pauseLabel = stringResource(R.string.speedometer_trip_pause)
    val resumeLabel = stringResource(R.string.speedometer_trip_resume)
    val resetLabel = stringResource(R.string.speedometer_reset)
    val settingsLabel = stringResource(R.string.speedometer_settings)

    Scaffold(
        topBar = {
            if (!state.isHudMode) {
                ExpressiveTopAppBar(
                    title = stringResource(R.string.speedometer_title),
                    subtitle = stringResource(R.string.speedometer_subtitle),
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                vibration?.vibrateClick()
                                onBack()
                            },
                            modifier = Modifier
                                .padding(8.dp)
                                .clip(SmallExpressiveShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.speedometer_back),
                            )
                        }
                    },
                    actions = {
                        if (granted) {
                            IconButton(
                                onClick = {
                                    vibration?.vibrateClick()
                                    viewModel.toggleHudMode()
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Flip,
                                    contentDescription = stringResource(R.string.speedometer_hud_enter),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (granted && !state.isHudMode) {
                ToolzHorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier.padding(bottom = 16.dp),
                    content = {
                        val recording = state.tripState == TripState.RECORDING
                        FilledIconButton(
                            onClick = {
                                vibration?.vibrateClick()
                                viewModel.toggleTrip()
                            },
                            modifier = Modifier.size(48.dp),
                            shape = SmallExpressiveShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (recording) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                contentColor = if (recording) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                            ),
                        ) {
                            Icon(
                                imageVector = if (recording) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = when (state.tripState) {
                                    TripState.IDLE -> startLabel
                                    TripState.RECORDING -> pauseLabel
                                    TripState.PAUSED -> resumeLabel
                                },
                            )
                        }
                    },
                    trailingContent = {
                        clickableItem(
                            onClick = {
                                val snapshot = viewModel.resetTrip()
                                if (snapshot != null) {
                                    vibration?.vibrateSuccess()
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = tripResetMessage,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreTrip(snapshot)
                                        }
                                    }
                                } else {
                                    vibration?.vibrateTick()
                                }
                            },
                            icon = { Icon(Icons.Rounded.RestartAlt, null) },
                            label = resetLabel,
                        )
                        clickableItem(
                            onClick = {
                                vibration?.vibrateClick()
                                showSettings = true
                            },
                            icon = { Icon(Icons.Rounded.Tune, null) },
                            label = settingsLabel,
                        )
                    },
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = Color.Transparent,
    ) { padding ->
        Crossfade(targetState = state.isHudMode, label = "hudCrossfade") { hud ->
            when {
                hud -> HudScreen(
                    state = state,
                    onExit = {
                        vibration?.vibrateClick()
                        viewModel.toggleHudMode()
                    },
                )

                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .toolzBackground()
                        .padding(top = padding.calculateTopPadding()),
                ) {
                    if (granted) {
                        SpeedometerContent(
                            state = state,
                            onUnitClick = {
                                vibration?.vibrateTick()
                                viewModel.cycleUnit()
                            },
                            onOpenLocationSettings = {
                                runCatching {
                                    context.startActivity(Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS))
                                }
                            },
                        )
                    } else {
                        PermissionView(
                            onGrant = {
                                vibration?.vibrateClick()
                                permission.launchPermissionRequest()
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            onDismiss = { showSettings = false },
            onUnit = viewModel::setUnit,
            onLimitEnabled = viewModel::setSpeedLimitEnabled,
            onLimit = viewModel::setSpeedLimit,
            onKeepScreenOn = viewModel::setKeepScreenOn,
            onHudFlip = viewModel::setHudFlip,
        )
    }
}

// region Main content

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedometerContent(
    state: SpeedUiState,
    onUnitClick: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = state.unit

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SpeedGauge(
            speed = state.speedDisplay,
            gaugeMax = state.gaugeMax,
            limit = if (state.settings.speedLimitEnabled) state.settings.speedLimit.toFloat() else null,
            overLimit = state.isOverLimit,
            unitLabel = unit.label,
            onUnitClick = onUnitClick,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
        )

        AnimatedVisibility(visible = state.signal == GpsSignal.DISABLED) {
            LocationOffBanner(onOpenSettings = onOpenLocationSettings)
        }

        StatusRow(state = state)

        TrendCard(history = state.speedHistory)

        StatGrid(state = state)

        // Clearance for the floating toolbar.
        Spacer(Modifier.height(96.dp))
    }
}

// endregion

// region Gauge

@Composable
private fun SpeedGauge(
    speed: Float,
    gaugeMax: Float,
    limit: Float?,
    overLimit: Boolean,
    unitLabel: String,
    onUnitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "gaugeSpeed",
    )
    val animatedMax by animateFloatAsState(
        targetValue = gaugeMax,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "gaugeMax",
    )
    val accent by animateColorAsState(
        targetValue = if (overLimit) colors.error else colors.primary,
        label = "gaugeAccent",
    )
    val numberColor by animateColorAsState(
        targetValue = if (overLimit) colors.error else colors.onSurface,
        label = "gaugeNumber",
    )

    val trackColor = colors.surfaceContainerHighest
    val majorTickColor = colors.onSurfaceVariant.copy(alpha = 0.6f)
    val minorTickColor = colors.outlineVariant
    val limitColor = colors.tertiary
    val textMeasurer = rememberTextMeasurer()
    val scaleLabelStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant)
    val speedStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = 88.sp,
        lineHeight = 96.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TabularDigits,
    )

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 26.dp.toPx()
            val arcTopLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val radius = arcSize.width / 2f
            val arcStyle = Stroke(width = stroke, cap = StrokeCap.Round)
            val progress = if (animatedMax > 0f) (animatedSpeed / animatedMax).coerceIn(0f, 1f) else 0f

            drawArc(
                color = trackColor,
                startAngle = GAUGE_START_ANGLE,
                sweepAngle = GAUGE_SWEEP_ANGLE,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = arcStyle,
            )
            val sweep = GAUGE_SWEEP_ANGLE * progress
            if (sweep > 0.5f) {
                drawArc(
                    color = accent,
                    startAngle = GAUGE_START_ANGLE,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = arcStyle,
                )
            }

            // Scale ticks, just inside the arc.
            val tickCount = GAUGE_MAJOR_TICKS * GAUGE_MINOR_PER_MAJOR
            val tickOuter = radius - stroke / 2f - 10.dp.toPx()
            for (i in 0..tickCount) {
                val major = i % GAUGE_MINOR_PER_MAJOR == 0
                val length = if (major) 12.dp.toPx() else 6.dp.toPx()
                val angle = Math.toRadians((GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * i / tickCount).toDouble())
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                drawLine(
                    color = if (major) majorTickColor else minorTickColor,
                    start = center + Offset(dx * tickOuter, dy * tickOuter),
                    end = center + Offset(dx * (tickOuter - length), dy * (tickOuter - length)),
                    strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Speed limit marker across the track.
            if (limit != null && animatedMax > 0f) {
                val fraction = (limit / animatedMax).coerceIn(0f, 1f)
                val angle = Math.toRadians((GAUGE_START_ANGLE + GAUGE_SWEEP_ANGLE * fraction).toDouble())
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                val inner = radius - stroke / 2f - 3.dp.toPx()
                val outer = radius + stroke / 2f + 3.dp.toPx()
                drawLine(
                    color = limitColor,
                    start = center + Offset(dx * inner, dy * inner),
                    end = center + Offset(dx * outer, dy * outer),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Scale end labels, under the arc's two ends.
            val zeroLabel = textMeasurer.measure("0", scaleLabelStyle)
            val maxLabel = textMeasurer.measure(animatedMax.roundToInt().toString(), scaleLabelStyle)
            val endAngle = Math.toRadians(GAUGE_START_ANGLE.toDouble())
            val endX = (cos(endAngle) * radius).toFloat()
            val endY = (sin(endAngle) * radius).toFloat() + stroke / 2f + 6.dp.toPx()
            drawText(
                textLayoutResult = zeroLabel,
                topLeft = Offset(center.x + endX - zeroLabel.size.width / 2f, center.y + endY),
            )
            drawText(
                textLayoutResult = maxLabel,
                topLeft = Offset(center.x - endX - maxLabel.size.width / 2f, center.y + endY),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animatedSpeed.roundToInt().toString(),
                style = speedStyle,
                color = numberColor,
                maxLines = 1,
                softWrap = false,
            )
            AssistChip(
                onClick = onUnitClick,
                label = { Text(unitLabel) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.SwapHoriz,
                        contentDescription = stringResource(R.string.speedometer_change_unit),
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                shape = CircleShape,
            )
        }
    }
}

// endregion

// region Status

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusRow(state: SpeedUiState) {
    val colors = MaterialTheme.colorScheme

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Signal
        val (signalIcon, signalContainer, signalContent) = when (state.signal) {
            GpsSignal.DISABLED -> Triple(Icons.Rounded.GpsOff, colors.errorContainer, colors.onErrorContainer)
            GpsSignal.SEARCHING -> Triple(Icons.Rounded.GpsNotFixed, colors.surfaceContainerHigh, colors.onSurfaceVariant)
            GpsSignal.POOR -> Triple(Icons.Rounded.GpsNotFixed, colors.errorContainer, colors.onErrorContainer)
            GpsSignal.FAIR -> Triple(Icons.Rounded.GpsFixed, colors.tertiaryContainer, colors.onTertiaryContainer)
            GpsSignal.GOOD -> Triple(Icons.Rounded.GpsFixed, colors.primaryContainer, colors.onPrimaryContainer)
        }
        val signalText = when (state.signal) {
            GpsSignal.DISABLED -> stringResource(R.string.speedometer_gps_off)
            GpsSignal.SEARCHING -> stringResource(R.string.speedometer_gps_searching)
            else -> stringResource(R.string.speedometer_gps_accuracy, state.accuracyMeters?.roundToInt() ?: 0)
        }
        StatusPill(signalText, signalIcon, signalContainer, signalContent)

        if (state.signal != GpsSignal.DISABLED && state.satellitesVisible > 0) {
            StatusPill(
                text = stringResource(R.string.speedometer_satellites, state.satellitesUsed, state.satellitesVisible),
                icon = Icons.Rounded.SatelliteAlt,
                container = colors.surfaceContainerHigh,
                content = colors.onSurfaceVariant,
            )
        }

        if (state.settings.speedLimitEnabled) {
            StatusPill(
                text = stringResource(R.string.speedometer_limit_pill, state.settings.speedLimit),
                icon = Icons.Rounded.Speed,
                container = if (state.isOverLimit) colors.errorContainer else colors.tertiaryContainer,
                content = if (state.isOverLimit) colors.onErrorContainer else colors.onTertiaryContainer,
            )
        }

        if (state.tripState != TripState.IDLE) {
            val recording = state.tripState == TripState.RECORDING
            StatusPill(
                text = stringResource(
                    if (recording) R.string.speedometer_trip_recording else R.string.speedometer_trip_paused,
                ),
                icon = if (recording) Icons.Rounded.Route else Icons.Rounded.Pause,
                container = if (recording) colors.secondaryContainer else colors.surfaceContainerHigh,
                content = if (recording) colors.onSecondaryContainer else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
) {
    val animatedContainer by animateColorAsState(container, label = "pillContainer")
    val animatedContent by animateColorAsState(content, label = "pillContent")

    Surface(color = animatedContainer, contentColor = animatedContent, shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun LocationOffBanner(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.speedometer_gps_disabled_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.speedometer_open_location_settings))
            }
        }
    }
}

// endregion

// region Trend

@Composable
private fun TrendCard(history: List<Float>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.speedometer_trend_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpeedTrend(
                history = history,
                lineColor = MaterialTheme.colorScheme.primary,
                baselineColor = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            )
        }
    }
}

@Composable
private fun SpeedTrend(
    history: List<Float>,
    lineColor: Color,
    baselineColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val strokeWidth = 2.5.dp.toPx()
        drawLine(
            color = baselineColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        if (history.size < 2) return@Canvas

        val peak = max(history.max(), TREND_MIN_PEAK_MPS)
        val step = size.width / (SPEED_HISTORY_SIZE - 1)
        val usableHeight = size.height - strokeWidth * 2f
        // Newest sample is pinned to the right edge; the chart fills in from there.
        val points = history.mapIndexed { index, value ->
            Offset(
                x = size.width - (history.lastIndex - index) * step,
                y = strokeWidth + usableHeight * (1f - value / peak),
            )
        }

        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val previous = points[i - 1]
                val current = points[i]
                val midX = (previous.x + current.x) / 2f
                cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(fill, color = lineColor.copy(alpha = 0.12f))
        drawPath(
            line,
            color = lineColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

// endregion

// region Stats

@Composable
private fun StatGrid(state: SpeedUiState) {
    val unit = state.unit
    val locale = Locale.getDefault()
    val headingRotation = rememberHeadingRotation(state.bearingDegrees)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatRow {
            StatTile(
                label = stringResource(R.string.speedometer_stat_distance),
                value = String.format(locale, "%.2f", unit.toDistance(state.distanceMeters)),
                unit = unit.distanceLabel,
                icon = Icons.Rounded.Route,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.speedometer_stat_duration),
                value = formatDuration(state.elapsedMs),
                unit = null,
                icon = Icons.Rounded.Timer,
                supporting = stringResource(R.string.speedometer_moving_time, formatDuration(state.movingTimeMs)),
                modifier = Modifier.weight(1f),
            )
        }
        StatRow {
            StatTile(
                label = stringResource(R.string.speedometer_stat_top_speed),
                value = String.format(locale, "%.1f", state.maxSpeedDisplay),
                unit = unit.label,
                icon = Icons.Rounded.Speed,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.speedometer_stat_average),
                value = String.format(locale, "%.1f", state.averageSpeedDisplay),
                unit = unit.label,
                icon = Icons.Rounded.Speed,
                modifier = Modifier.weight(1f),
            )
        }
        StatRow {
            StatTile(
                label = stringResource(R.string.speedometer_stat_altitude),
                value = state.altitudeMeters?.let { unit.toAltitude(it).roundToInt().toString() } ?: "–",
                unit = unit.altitudeLabel,
                icon = Icons.Rounded.Terrain,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.speedometer_stat_heading),
                value = state.bearingDegrees?.let { cardinal(it) } ?: "–",
                unit = null,
                icon = Icons.Rounded.Navigation,
                iconRotation = headingRotation,
                supporting = state.bearingDegrees?.let { String.format(locale, "%03d°", it.roundToInt() % 360) },
                modifier = Modifier.weight(1f),
            )
        }
        val latitude = state.latitude
        val longitude = state.longitude
        if (latitude != null && longitude != null) {
            StatTile(
                label = stringResource(R.string.speedometer_stat_position),
                value = String.format(locale, "%.5f, %.5f", latitude, longitude),
                unit = null,
                icon = Icons.Rounded.Map,
                valueStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    unit: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    iconRotation: Float = 0f,
    valueStyle: TextStyle = MaterialTheme.typography.headlineSmall,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = SquircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = iconRotation },
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = value,
                    style = valueStyle.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularDigits),
                    maxLines = 1,
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Animates the heading arrow along the shortest path, so 359° → 1° doesn't spin the long way. */
@Composable
private fun rememberHeadingRotation(bearing: Float?): Float {
    var accumulated by remember { mutableFloatStateOf(bearing ?: 0f) }
    if (bearing != null) {
        LaunchedEffect(bearing) {
            val delta = ((bearing - accumulated) % 360f + 540f) % 360f - 180f
            accumulated += delta
        }
    }
    val rotation by animateFloatAsState(
        targetValue = accumulated,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "headingRotation",
    )
    return rotation
}

private val CardinalDirections = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

private fun cardinal(bearing: Float): String =
    CardinalDirections[(((bearing % 360f + 360f) % 360f + 22.5f) / 45f).toInt() % CardinalDirections.size]

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val locale = Locale.getDefault()
    return if (hours > 0L) {
        String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(locale, "%d:%02d", minutes, seconds)
    }
}

// endregion

// region HUD

@Composable
private fun HudScreen(
    state: SpeedUiState,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedColor by animateColorAsState(
        targetValue = if (state.isOverLimit) HudRed else Color.White,
        label = "hudSpeedColor",
    )
    val flip = state.settings.hudFlip

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // The close button stays un-mirrored so it's always usable.
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.speedometer_hud_exit),
                tint = Color.White,
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            val speedSize = with(LocalDensity.current) { (maxWidth * 0.46f).toSp() }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = if (flip == HudFlip.HORIZONTAL) -1f else 1f
                        scaleY = if (flip == HudFlip.VERTICAL) -1f else 1f
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.speedDisplay.roundToInt().toString(),
                    style = TextStyle(
                        fontSize = speedSize,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = TabularDigits,
                    ),
                    color = speedColor,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = state.unit.label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                if (state.settings.speedLimitEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.speedometer_limit_pill, state.settings.speedLimit),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (state.isOverLimit) HudRed else Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// endregion

// region Permission

@Composable
private fun PermissionView(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Icon(
                Icons.Rounded.LocationOff,
                contentDescription = null,
                modifier = Modifier
                    .padding(28.dp)
                    .size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.speedometer_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.speedometer_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onGrant,
            shape = MediumExpressiveShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(stringResource(R.string.speedometer_permission_grant))
        }
    }
}

// endregion

// region Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    settings: SpeedSettings,
    onDismiss: () -> Unit,
    onUnit: (SpeedUnit) -> Unit,
    onLimitEnabled: (Boolean) -> Unit,
    onLimit: (Int) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onHudFlip: (HudFlip) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.speedometer_settings),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            SettingsLabel(stringResource(R.string.speedometer_settings_units))
            SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                SpeedUnit.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == settings.unit,
                        onClick = { onUnit(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, SpeedUnit.entries.size),
                        icon = {},
                        label = { Text(option.label) },
                    )
                }
            }

            SettingsSwitch(
                title = stringResource(R.string.speedometer_settings_limit),
                description = stringResource(R.string.speedometer_settings_limit_desc),
                checked = settings.speedLimitEnabled,
                onCheckedChange = onLimitEnabled,
            )
            AnimatedVisibility(visible = settings.speedLimitEnabled) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val unit = settings.unit
                    Slider(
                        value = settings.speedLimit.toFloat(),
                        onValueChange = { onLimit(it.roundToInt()) },
                        valueRange = unit.limitMin.toFloat()..unit.limitMax.toFloat(),
                        steps = (unit.limitMax - unit.limitMin) / 5 - 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${settings.speedLimit} ${unit.label}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularDigits),
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 88.dp),
                    )
                }
            }

            SettingsSwitch(
                title = stringResource(R.string.speedometer_settings_keep_on),
                description = null,
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOn,
            )

            SettingsLabel(stringResource(R.string.speedometer_settings_hud_mirror))
            SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                HudFlip.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == settings.hudFlip,
                        onClick = { onHudFlip(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, HudFlip.entries.size),
                        icon = {},
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        HudFlip.VERTICAL -> R.string.speedometer_hud_flip_vertical
                                        HudFlip.HORIZONTAL -> R.string.speedometer_hud_flip_horizontal
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val supporting: (@Composable () -> Unit)? = description?.let { text -> { Text(text) } }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting,
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
    )
}

// endregion

@Preview(showBackground = true)
@Composable
private fun SpeedometerContentPreview() {
    MaterialTheme {
        SpeedometerContent(
            state = SpeedUiState(
                settings = SpeedSettings(speedLimitEnabled = true, speedLimit = 90),
                speedMps = 27f,
                maxSpeedMps = 31f,
                distanceMeters = 12_400.0,
                movingTimeMs = 780_000L,
                elapsedMs = 905_000L,
                altitudeMeters = 84.0,
                bearingDegrees = 48f,
                latitude = 30.42781,
                longitude = -9.59816,
                accuracyMeters = 4f,
                satellitesUsed = 14,
                satellitesVisible = 22,
                signal = GpsSignal.GOOD,
                tripState = TripState.RECORDING,
                speedHistory = List(60) { 18f + (it % 12) },
                gaugeMax = 120f,
            ),
            onUnitClick = {},
            onOpenLocationSettings = {},
        )
    }
}