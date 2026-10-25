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

package com.frerox.toolz.ui.screens.livingframes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameCorners
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameEstimate
import com.frerox.toolz.data.livingframes.LivingFrameFit
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import com.frerox.toolz.data.livingframes.LivingFrameLoopTransition
import com.frerox.toolz.data.livingframes.LivingFrameResolution
import com.frerox.toolz.data.livingframes.LivingFrameSpeed
import com.frerox.toolz.data.livingframes.LivingFrameTapAction
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.data.livingframes.LivingFramesStorage
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.widget.livingframes.LivingFramesSlideshow
import kotlin.math.roundToInt

// ── Shared building blocks ───────────────────────────────────────────────

@Composable
internal fun PanelCard(content: @Composable () -> Unit) {
    ExpressiveCard(
        onClick = {},
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        enabled = false
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            content()
        }
    }
}

@Composable
internal fun SettingHeader(title: String, value: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun SettingBlock(title: String, value: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingHeader(title, value)
        content()
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun StyledSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
internal fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

// ── Customize ────────────────────────────────────────────────────────────

/**
 * The controls most people need are visible; anything that changes how the
 * stored file is processed, or that only matters to power users, is folded
 * into "More options" so the first screen stays readable.
 */
@Composable
internal fun CustomizePanel(
    frame: LivingFrame,
    nextIn: String?,
    vm: LivingFramesViewModel
) {
    val vib = LocalVibrationManager.current
    var advanced by rememberSaveable(frame.id) { mutableStateOf(false) }

    PanelCard {
        if (frame.isSlideshow) {
            SettingBlock("Change photo every") {
                ChoiceChips(
                    options = LivingFramesSlideshow.INTERVALS_MIN,
                    selected = frame.slideshowIntervalMin,
                    label = { LivingFramesSlideshow.intervalLabel(it) },
                    onSelect = { vib?.vibrateTick(); vm.setSlideshowInterval(frame.id, it) }
                )
            }
            if (nextIn != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Next photo in $nextIn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vib?.vibrateClick(); vm.skipSlide(frame.id) }) {
                        Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Skip")
                    }
                }
            }
            SwitchRow(
                title = "Shuffle",
                subtitle = "Mix up the order",
                checked = frame.shuffle,
                onChange = { vib?.vibrateTick(); vm.setShuffle(frame.id, it) }
            )
        } else if (frame.isAnimated) {
            SettingBlock("Speed", LivingFrameSpeed.label(frame.speed)) {
                StyledSlider(
                    value = LivingFrameSpeed.index(frame.speed).toFloat(),
                    range = 0f..LivingFrameSpeed.STEPS.lastIndex.toFloat(),
                    steps = (LivingFrameSpeed.STEPS.lastIndex - 1).coerceAtLeast(0),
                    onChange = {
                        val idx = it.roundToInt().coerceIn(0, LivingFrameSpeed.STEPS.lastIndex)
                        if (idx != LivingFrameSpeed.index(frame.speed)) {
                            vib?.vibrateTick()
                            vm.setSpeed(frame.id, LivingFrameSpeed.STEPS[idx])
                        }
                    }
                )
            }
        }

        SettingBlock("Corners", "${frame.cornerRadiusDp} dp") {
            StyledSlider(
                value = frame.cornerRadiusDp.toFloat(),
                range = LivingFrameCorners.MIN.toFloat()..LivingFrameCorners.MAX.toFloat(),
                steps = (LivingFrameCorners.MAX - LivingFrameCorners.MIN - 1).coerceAtLeast(0),
                onChange = { vm.setCorner(frame.id, it.roundToInt()) }
            )
        }

        SettingBlock("Framing") {
            ChoiceChips(
                options = listOf(LivingFrameFit.FILL, LivingFrameFit.FIT),
                selected = frame.fit,
                label = { if (it == LivingFrameFit.FILL) "Fill" else "Fit" },
                onSelect = { vib?.vibrateTick(); vm.setFit(frame.id, it) }
            )
            Text(
                if (frame.fit == LivingFrameFit.FILL) "Fills the widget and crops the edges."
                else "Shows the whole picture, with space around it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SwitchRow(
            title = "Show name",
            subtitle = "Adds a small label to the widget",
            checked = frame.showCaption,
            onChange = { vib?.vibrateTick(); vm.setShowCaption(frame.id, it) }
        )

        TextButton(
            onClick = { vib?.vibrateClick(); advanced = !advanced },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (advanced) "Fewer options" else "More options", modifier = Modifier.weight(1f))
            Icon(
                if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null,
                Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = advanced,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            AdvancedControls(frame, vm)
        }
    }
}

@Composable
private fun AdvancedControls(frame: LivingFrame, vm: LivingFramesViewModel) {
    val vib = LocalVibrationManager.current
    val displayFps = LivingFrameFps.snap(frame.fps)
    val sourceFps = frame.sourceFps.coerceAtLeast(1)
    val playFps = LivingFrameEngine.playbackFps(frame)
    val stored = LivingFrameEstimate.storedSize(frame.width, frame.height, frame.quality)
    val renderW = (stored.first * frame.resolutionPercent / 100f).roundToInt()
    val renderH = (stored.second * frame.resolutionPercent / 100f).roundToInt()

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (frame.isAnimated) {
            SettingBlock("Smoothness", "$displayFps fps") {
                StyledSlider(
                    value = LivingFrameFps.stepIndex(frame.fps).toFloat(),
                    range = 0f..LivingFrameFps.STEPS.lastIndex.toFloat(),
                    steps = (LivingFrameFps.STEPS.lastIndex - 1).coerceAtLeast(0),
                    onChange = {
                        val idx = it.roundToInt().coerceIn(0, LivingFrameFps.STEPS.lastIndex)
                        if (idx != LivingFrameFps.stepIndex(frame.fps)) {
                            vib?.vibrateTick()
                            vm.setFps(frame.id, LivingFrameFps.STEPS[idx])
                        }
                    }
                )
                if (displayFps > playFps) {
                    Text(
                        "This clip was captured at $sourceFps fps, so it plays at $playFps fps. " +
                            "Extra frames would only repeat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingBlock("Detail", "${frame.resolutionPercent}% · ${renderW}×${renderH} px") {
            StyledSlider(
                value = LivingFrameResolution.STEPS
                    .indexOf(LivingFrameResolution.snap(frame.resolutionPercent))
                    .coerceAtLeast(0)
                    .toFloat(),
                range = 0f..LivingFrameResolution.STEPS.lastIndex.toFloat(),
                steps = (LivingFrameResolution.STEPS.lastIndex - 1).coerceAtLeast(0),
                onChange = {
                    val idx = it.roundToInt().coerceIn(0, LivingFrameResolution.STEPS.lastIndex)
                    val percent = LivingFrameResolution.STEPS[idx]
                    if (percent != LivingFrameResolution.snap(frame.resolutionPercent)) {
                        vib?.vibrateTick()
                        vm.setResolution(frame.id, percent)
                    }
                }
            )
            Text(
                "Lower settings save battery and memory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (frame.isAnimated) {
            SettingBlock("Direction") {
                ChoiceChips(
                    options = LivingFrameLoopMode.entries,
                    selected = frame.loopMode,
                    label = { if (it == LivingFrameLoopMode.BOOMERANG) "Back and forth" else "Loop" },
                    onSelect = { vib?.vibrateTick(); vm.setLoopMode(frame.id, it) }
                )
            }
            if (frame.loopMode != LivingFrameLoopMode.BOOMERANG) {
                SettingBlock("Loop transition") {
                    ChoiceChips(
                        options = LivingFrameLoopTransition.entries,
                        selected = frame.loopTransition,
                        label = {
                            when (it) {
                                LivingFrameLoopTransition.NONE -> "None"
                                LivingFrameLoopTransition.FADE -> "Fade"
                                LivingFrameLoopTransition.BLUR -> "Soft blur"
                            }
                        },
                        onSelect = { vib?.vibrateTick(); vm.setLoopTransition(frame.id, it) }
                    )
                }
            }
        }

        TapActionSetting(frame, vm)
    }
}

@Composable
private fun TapActionSetting(frame: LivingFrame, vm: LivingFramesViewModel) {
    val vib = LocalVibrationManager.current
    val tapActions = buildList {
        add("Open Living Widgets" to LivingFrameTapAction.OPEN_PREVIEW)
        add("Open a tool" to LivingFrameTapAction.OPEN_TOOL)
        if (frame.isSlideshow) add("Next photo" to LivingFrameTapAction.NEXT_FRAME)
        if (frame.isAnimated) add("Pause or play" to LivingFrameTapAction.PAUSE_PLAY)
        add("Do nothing" to LivingFrameTapAction.NOTHING)
    }
    val effectiveTap = when {
        frame.tapAction == LivingFrameTapAction.NEXT_FRAME && !frame.isSlideshow ->
            LivingFrameTapAction.OPEN_PREVIEW
        frame.tapAction == LivingFrameTapAction.PAUSE_PLAY && !frame.isAnimated ->
            LivingFrameTapAction.OPEN_PREVIEW
        else -> frame.tapAction
    }
    SettingBlock("When tapped") {
        ChoiceChips(
            options = tapActions,
            selected = tapActions.firstOrNull { it.second == effectiveTap },
            label = { it.first },
            onSelect = { vib?.vibrateTick(); vm.setTapAction(frame.id, it.second) }
        )
        if (frame.tapAction == LivingFrameTapAction.OPEN_TOOL) {
            ToolChips(
                selectedRoute = frame.tapTargetRoute,
                onSelect = { vib?.vibrateTick(); vm.setTapTarget(frame.id, it) }
            )
        }
    }
}

@Composable
private fun ToolChips(selectedRoute: String?, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LivingFrameTools.ALL, key = { it.route }) { tool ->
            FilterChip(
                selected = tool.route == selectedRoute,
                onClick = { onSelect(tool.route) },
                label = {
                    Text(
                        stringResource(tool.titleRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(tool.icon, null, Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

// ── Placed widgets ───────────────────────────────────────────────────────

/** Only rendered when at least one copy is on the home screen. */
@Composable
internal fun PlacedWidgetsPanel(
    placed: List<PlacedWidget>,
    onRefresh: (Int) -> Unit
) {
    PanelCard {
        SettingHeader("On your home screen", "${placed.size}")
        placed.forEach { PlacedRow(it, onRefresh) }
    }
}

@Composable
private fun PlacedRow(w: PlacedWidget, onRefresh: (Int) -> Unit) {
    val info = w.info
    val failed = info?.error != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SquircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (failed) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (failed) Icons.Rounded.Warning else Icons.Rounded.Widgets, null,
                Modifier.size(19.dp),
                tint = if (failed) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(w.cells, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                renderSummary(w),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (info?.reduced == true && !failed) {
                Text(
                    "Optimized to fit your launcher",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = { onRefresh(w.widgetId) }) {
            Icon(Icons.Rounded.Refresh, "Refresh widget")
        }
    }
}

private fun renderSummary(w: PlacedWidget): String {
    val i = w.info ?: return "Waiting for your launcher…"
    // Launcher exceptions are technical and often truncated. Keep them in the
    // widget diagnostics; show an actionable sentence here instead.
    if (i.error != null) return "Couldn't draw here. Tap refresh, or add it again."
    val kind = if (i.frames == 1) "Still" else "${i.fps} fps"
    val next = w.nextSlideIn?.let { " · next in $it" } ?: ""
    return "$kind · ${i.widthPx}×${i.heightPx} px$next"
}

// ── Storage ──────────────────────────────────────────────────────────────

@Composable
internal fun StoragePanel(storage: LivingFramesStorage, onCleanup: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    PanelCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Storage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${LivingFramesRepository.formatBytes(storage.usedBytes)} used · " +
                        "${storage.setCount} set${if (storage.setCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide" else "Manage")
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    Modifier.size(18.dp)
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(
                    progress = { storage.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${LivingFramesRepository.formatBytes(storage.freeBytes)} free on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (storage.orphanBytes > 0L) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${storage.orphanDirs.size} unused folder" +
                                "${if (storage.orphanDirs.size == 1) "" else "s"} " +
                                "(${LivingFramesRepository.formatBytes(storage.orphanBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onCleanup) { Text("Clean up") }
                    }
                }
            }
        }
    }
}
