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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameFit
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import com.frerox.toolz.data.livingframes.LivingFrameTapAction
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.data.livingframes.LivingFramesStorage
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveButton
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            content()
        }
    }
}

@Composable
internal fun SettingHeader(title: String, value: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun SettingBlock(title: String, value: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingHeader(title, value)
        content()
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StyledSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary
        )
    )
}

// ── Motion (GIF / video) ─────────────────────────────────────────────────────

@Composable
internal fun MotionPanel(frame: LivingFrame, vm: LivingFramesViewModel) {
    val vib = LocalVibrationManager.current
    val last = LivingFrameFps.STEPS.lastIndex
    val loopMs = LivingFrameEngine.loopDurationMs(frame.frameCount, frame.fps, frame.loopMode)
    PanelCard {
        SettingBlock("Speed", "${LivingFrameFps.snap(frame.fps)} fps · ${LivingFrameFps.label(frame.fps)}") {
            StyledSlider(
                value = LivingFrameFps.stepIndex(frame.fps).toFloat(),
                range = 0f..last.toFloat(),
                steps = last - 1,
                onChange = {
                    val idx = it.roundToInt().coerceIn(0, last)
                    if (idx != LivingFrameFps.stepIndex(frame.fps)) {
                        vib?.vibrateTick()
                        vm.setFps(frame.id, LivingFrameFps.STEPS[idx])
                    }
                }
            )
            Text(
                "One loop lasts %.1f s across %d frames.".format(loopMs / 1000f, frame.frameCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingBlock("Direction") {
            ToolzConnectedButtonGroup(
                selectedIndex = frame.loopMode.ordinal,
                options = listOf("Loop", "Boomerang"),
                onOptionSelected = { vib?.vibrateTick(); vm.setLoopMode(frame.id, LivingFrameLoopMode.entries[it]) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (frame.loopMode == LivingFrameLoopMode.BOOMERANG) "Plays forward, then backward, with no hitch at the seam."
                else "Restarts from the first frame every time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Timing (slideshow) ───────────────────────────────────────────────────────────

@Composable
internal fun TimingPanel(frame: LivingFrame, nextIn: String?, vm: LivingFramesViewModel) {
    val vib = LocalVibrationManager.current
    val intervals = LivingFramesSlideshow.INTERVALS_MIN
    PanelCard {
        SettingBlock("Change photo every") {
            ToolzConnectedButtonGroup(
                selectedIndex = intervals.indexOf(frame.slideshowIntervalMin).takeIf { it >= 0 } ?: 2,
                options = intervals.map { LivingFramesSlideshow.intervalLabel(it) },
                onOptionSelected = { vib?.vibrateTick(); vm.setSlideshowInterval(frame.id, intervals[it]) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        SwitchRow(
            title = "Shuffle",
            subtitle = "Every photo once per round, never the same one twice in a row.",
            checked = frame.shuffle,
            onChange = { vib?.vibrateTick(); vm.setShuffle(frame.id, it) }
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Next photo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (nextIn != null) "In $nextIn" else "Starts once it's on your home screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ToolzOutlinedExpressiveButton(onClick = { vib?.vibrateClick(); vm.skipSlide(frame.id) }) {
                Icon(Icons.Rounded.SkipNext, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Skip")
            }
        }
    }
}

// ── Look ──────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LookPanel(frame: LivingFrame, vm: LivingFramesViewModel) {
    val vib = LocalVibrationManager.current
    val taps = if (frame.isSlideshow)
        listOf("Open app" to LivingFrameTapAction.OPEN_PREVIEW, "Next photo" to LivingFrameTapAction.NEXT_FRAME, "Nothing" to LivingFrameTapAction.NOTHING)
    else
        listOf("Open app" to LivingFrameTapAction.OPEN_PREVIEW, "Nothing" to LivingFrameTapAction.NOTHING)
    PanelCard {
        SettingBlock("Framing") {
            ToolzConnectedButtonGroup(
                selectedIndex = if (frame.fit == LivingFrameFit.FILL) 0 else 1,
                options = listOf("Fill", "Fit"),
                onOptionSelected = { vib?.vibrateTick(); vm.setFit(frame.id, if (it == 0) LivingFrameFit.FILL else LivingFrameFit.FIT) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (frame.fit == LivingFrameFit.FILL) "Crops from the centre so the widget is always full."
                else "Shows the whole picture; empty space stays transparent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingBlock("Corners", "${frame.cornerRadiusDp} dp") {
            StyledSlider(
                value = frame.cornerRadiusDp.toFloat(), range = 0f..40f, steps = 39,
                onChange = { vm.setCorner(frame.id, it.roundToInt()) }
            )
        }
        SettingBlock("Opacity", "${(frame.opacity * 100).roundToInt()}%") {
            StyledSlider(
                value = frame.opacity, range = 0.4f..1f, steps = 0,
                onChange = { vm.setOpacity(frame.id, it) }
            )
        }
        SwitchRow(
            title = "Show name",
            subtitle = "A small label at the bottom of the widget.",
            checked = frame.showCaption,
            onChange = { vib?.vibrateTick(); vm.setShowCaption(frame.id, it) }
        )
        SettingBlock("When tapped") {
            ToolzConnectedButtonGroup(
                selectedIndex = taps.indexOfFirst { it.second == frame.tapAction }.coerceAtLeast(0),
                options = taps.map { it.first },
                onOptionSelected = { vib?.vibrateTick(); vm.setTapAction(frame.id, taps[it].second) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Widget (placement) ────────────────────────────────────────────────────────────────────

@Composable
internal fun WidgetPanel(
    frame: LivingFrame,
    placed: List<PlacedWidget>,
    onPin: () -> Unit,
    onRefresh: (Int) -> Unit,
    onDelete: () -> Unit
) {
    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingHeader(
                "On your home screen",
                if (placed.isEmpty()) null else "${placed.size}"
            )
            if (placed.isEmpty()) {
                Text(
                    "Not placed yet. Add it and it renders to the exact size you give it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                placed.forEach { PlacedRow(it, onRefresh) }
            }
            ToolzExpressiveButton(onClick = onPin, modifier = Modifier.fillMaxWidth()) {
                Text(if (placed.isEmpty()) "Add to home screen" else "Add another", fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Each copy can show a different set; the launcher's configuration screen asks which one. Some launchers pause widget animations in battery saver, and a few don't animate ViewFlipper widgets at all.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ToolzOutlinedExpressiveButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Delete “${frame.name}”", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun StoragePanel(
    storage: LivingFramesStorage?,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onCleanup: () -> Unit
) {
    PanelCard {
        SettingHeader(
            "Storage",
            storage?.let {
                "${LivingFramesRepository.formatBytes(it.usedBytes)} / " +
                    LivingFramesRepository.formatBytes(it.quotaBytes)
            }
        )
        if (loadFailed) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(SquircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    "The library file couldn't be read. Imported files were kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            ToolzOutlinedExpressiveButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try loading again")
            }
        }
        val s = storage
        if (s == null) {
            Text(
                "Checking storage…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LinearProgressIndicator(
                progress = { s.fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${s.setCount} set${if (s.setCount == 1) "" else "s"} · " +
                    "${LivingFramesRepository.formatBytes(s.freeBytes)} free on this device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (s.orphanBytes > 0L) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${s.orphanDirs.size} unused folder${if (s.orphanDirs.size == 1) "" else "s"} " +
                            "(${LivingFramesRepository.formatBytes(s.orphanBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    ToolzOutlinedExpressiveButton(onClick = onCleanup) { Text("Clean up") }
                }
            }
        }
        Text(
            "Frames are downscaled for the home screen (40-frame animation / 30-photo slideshow limit). " +
                "Imports keep at least ${LivingFramesRepository.formatBytes(LivingFramesRepository.MIN_FREE_BYTES)} free.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (failed) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (failed) Icons.Rounded.Warning else Icons.Rounded.Widgets, null,
                Modifier.size(20.dp),
                tint = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${w.cells} widget", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                renderSummary(w),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            val note = reductionNote(info)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { onRefresh(w.widgetId) }) { Icon(Icons.Rounded.Refresh, "Re-render widget") }
    }
}

private fun renderSummary(w: PlacedWidget): String {
    val i = w.info ?: return "Waiting for the launcher…"
    i.error?.let { return it }
    val mb = i.bytes / 1_048_576f
    val frames = if (i.frames == 1) "1 image" else "${i.frames} frames"
    val next = w.nextSlideIn?.let { " · next in $it" } ?: ""
    return "$frames · %.1f MB · ${i.widthPx}×${i.heightPx} px$next".format(mb)
}

private fun reductionNote(i: com.frerox.toolz.widget.livingframes.LivingFramesWidgetHelper.WidgetInfo?): String? {
    if (i == null || i.error != null || !i.reduced) return null
    val parts = buildList {
        if (i.scalePct in 1..99) add("${i.scalePct}% size")
        if (i.rgb565) add("16-bit colour")
        if (i.step > 1) add("every ${i.step}. frame")
    }
    return "Trimmed to fit the launcher's memory limit: ${parts.joinToString(", ")}."
}
