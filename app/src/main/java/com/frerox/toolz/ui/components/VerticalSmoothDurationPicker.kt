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

package com.frerox.toolz.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.theme.LocalVibrationManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.absoluteValue

/** Wheel ranges: hours 0..99, minutes 0..59, seconds 0..59. */
const val TIMER_WHEEL_HOURS = 100
const val TIMER_WHEEL_MINUTES = 60
const val TIMER_WHEEL_SECONDS = 60

private val WheelRowHeight: Dp = 44.dp
private val WheelRowGap: Dp = 4.dp
private val WheelSlot: Dp = WheelRowHeight + WheelRowGap
private const val WheelViewportRows = 3
private val CardShape = RoundedCornerShape(28.dp)

/** True modulo (pager pages are always >= 0, but external values may not be). */
private fun floorMod(page: Int, cycle: Int): Int = ((page % cycle) + cycle) % cycle

private const val LOOP_MID = Int.MAX_VALUE / 2

private fun initialPage(cycle: Int, value: Int): Int =
    LOOP_MID - floorMod(LOOP_MID, cycle) + value.coerceIn(0, cycle - 1)

/** Nearest looped page showing [value], so external syncs never jump across the loop. */
private fun nearestPage(current: Int, cycle: Int, value: Int): Int {
    val v = value.coerceIn(0, cycle - 1)
    val base = current - floorMod(current, cycle)
    val candidates = intArrayOf(base + v - cycle, base + v, base + v + cycle)
    return candidates.minBy { (it - current).absoluteValue }
}

/**
 * Infinite duration wheel (H / M / S) — M3 Expressive, iOS-style loop feel.
 *
 * Stability rules (unchanged, load-bearing — do not touch):
 * - ONE settle reporter for all three columns.
 * - External -> pager sync only when idle, via instant scrollToPage.
 * - Scroll itself is never driven by an Animatable — only rendering reacts to
 *   the pager's live offset.
 *
 * Shape system: the focus pill is a plain RoundedCornerShape tonal container
 * (secondaryContainer) with real elevation via graphicsLayer.shadowElevation.
 * It reacts to drag state — insets slightly and lifts while a column is
 * actively spinning, relaxes back at rest — and the settled digit gets a
 * quick spring "pop" on arrival so landing on a new number reads as an
 * arrival rather than just a stop. (A polygon shape-morph version using
 * androidx.graphics.shapes RoundedPolygon/Morph was tried and dropped: it
 * rendered zero-area against this project's resolved graphics-shapes
 * artifact. Worth revisiting separately with on-device debugging.)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerticalSmoothDurationPicker(
    hours: Int,
    minutes: Int,
    seconds: Int,
    accent: Color,
    enabled: Boolean,
    onChange: (hours: Int, mins: Int, secs: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vibrationManager = LocalVibrationManager.current

    val safeHours = hours.coerceIn(0, TIMER_WHEEL_HOURS - 1)
    val safeMinutes = minutes.coerceIn(0, TIMER_WHEEL_MINUTES - 1)
    val safeSeconds = seconds.coerceIn(0, TIMER_WHEEL_SECONDS - 1)

    val hoursPager = rememberPagerState(initialPage = initialPage(TIMER_WHEEL_HOURS, safeHours)) { Int.MAX_VALUE }
    val minutesPager = rememberPagerState(initialPage = initialPage(TIMER_WHEEL_MINUTES, safeMinutes)) { Int.MAX_VALUE }
    val secondsPager = rememberPagerState(initialPage = initialPage(TIMER_WHEEL_SECONDS, safeSeconds)) { Int.MAX_VALUE }

    // The settle reporter below is keyed on the pager objects (stable), so a
    // plain read of safeHours/safeMinutes/safeSeconds inside would freeze the
    // first-composition values forever: any wheel position matching the launch
    // values (e.g. mins back to 0) would compare equal and swallow onChange.
    // rememberUpdatedState keeps the guard comparing against current state.
    val latestStaged by rememberUpdatedState(Triple(safeHours, safeMinutes, safeSeconds))

    LaunchedEffect(hoursPager, minutesPager, secondsPager) {
        snapshotFlow {
            Triple(
                floorMod(hoursPager.settledPage, TIMER_WHEEL_HOURS),
                floorMod(minutesPager.settledPage, TIMER_WHEEL_MINUTES),
                floorMod(secondsPager.settledPage, TIMER_WHEEL_SECONDS),
            )
        }.distinctUntilChanged().collect { (h, m, s) ->
            val (lh, lm, ls) = latestStaged
            if (h != lh || m != lm || s != ls) {
                vibrationManager?.vibrateTick()
                onChange(h, m, s)
            }
        }
    }

    LaunchedEffect(safeHours) {
        if (!hoursPager.isScrollInProgress &&
            floorMod(hoursPager.currentPage, TIMER_WHEEL_HOURS) != safeHours
        ) {
            hoursPager.scrollToPage(nearestPage(hoursPager.currentPage, TIMER_WHEEL_HOURS, safeHours))
        }
    }
    LaunchedEffect(safeMinutes) {
        if (!minutesPager.isScrollInProgress &&
            floorMod(minutesPager.currentPage, TIMER_WHEEL_MINUTES) != safeMinutes
        ) {
            minutesPager.scrollToPage(nearestPage(minutesPager.currentPage, TIMER_WHEEL_MINUTES, safeMinutes))
        }
    }
    LaunchedEffect(safeSeconds) {
        if (!secondsPager.isScrollInProgress &&
            floorMod(secondsPager.currentPage, TIMER_WHEEL_SECONDS) != safeSeconds
        ) {
            secondsPager.scrollToPage(nearestPage(secondsPager.currentPage, TIMER_WHEEL_SECONDS, safeSeconds))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 10.dp, horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top,
        ) {
            InfiniteWheelColumn(
                pagerState = hoursPager,
                cycle = TIMER_WHEEL_HOURS,
                label = "Hours",
                accent = accent,
                enabled = enabled,
                hero = false,
                modifier = Modifier.weight(0.85f),
            )
            WheelSeparator(accent = accent)
            InfiniteWheelColumn(
                pagerState = minutesPager,
                cycle = TIMER_WHEEL_MINUTES,
                label = "Mins",
                accent = accent,
                enabled = enabled,
                hero = true,
                modifier = Modifier.weight(1f),
            )
            WheelSeparator(accent = accent)
            InfiniteWheelColumn(
                pagerState = secondsPager,
                cycle = TIMER_WHEEL_SECONDS,
                label = "Secs",
                accent = accent,
                enabled = enabled,
                hero = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Compat: legacy min/sec callers (hours = 0). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerticalSmoothDurationPicker(
    minutes: Int,
    seconds: Int,
    accent: Color,
    enabled: Boolean,
    onChange: (mins: Int, secs: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    VerticalSmoothDurationPicker(
        hours = 0,
        minutes = minutes,
        seconds = seconds,
        accent = accent,
        enabled = enabled,
        onChange = { _, m, s -> onChange(m, s) },
        modifier = modifier,
    )
}

@Composable
private fun WheelSeparator(accent: Color) {
    // Aligned to the focus-pill center: one full wheel slot of top offset
    // (the pager's vertical contentPadding), then centered in one row height.
    // The parent Row is Top-aligned so the column labels below never shift this.
    Box(
        modifier = Modifier
            .padding(top = WheelSlot)
            .height(WheelRowHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ":",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = accent.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfiniteWheelColumn(
    pagerState: PagerState,
    cycle: Int,
    label: String,
    accent: Color,
    enabled: Boolean,
    hero: Boolean,
    modifier: Modifier = Modifier,
) {
    // Pill reacts to interaction with plain, safe float animations — no
    // custom shape math. Slightly inset + lifted while actively dragging,
    // reads as "picked up"; relaxes back with a bouncy settle.
    val dragging = pagerState.isScrollInProgress
    val pillInset by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillInset",
    )
    val pillElevation by animateFloatAsState(
        targetValue = if (dragging) 6f else 2f,
        animationSpec = tween(180),
        label = "pillElevation",
    )

    // Brief scale "pop" on the settled value itself, so landing on a new
    // number reads as an arrival, not just a stop.
    val settled = floorMod(pagerState.settledPage, cycle)
    val popScale = remember { Animatable(1f) }
    LaunchedEffect(settled) {
        popScale.snapTo(0.86f)
        popScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelSlot * WheelViewportRows - WheelRowGap),
            contentAlignment = Alignment.Center,
        ) {
            // Focus pill: tonal container, real elevation, subtly reactive
            // to drag state (insets + lifts while spinning, relaxes at rest).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight)
                    .padding(horizontal = (if (hero) 3.dp else 5.dp) + (pillInset * 3).dp)
                    .graphicsLayer {
                        shadowElevation = pillElevation.dp.toPx()
                        scaleY = 1f - pillInset * 0.04f
                        shape = RoundedCornerShape(16.dp)
                        clip = true
                    }
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )

            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxHeight(),
                userScrollEnabled = enabled,
                contentPadding = PaddingValues(vertical = WheelSlot),
                beyondViewportPageCount = 2,
            ) { index ->
                val value = floorMod(index, cycle)
                val distance = ((pagerState.currentPage - index) +
                        pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 2f)
                val selected = distance < 0.5f

                val norm = distance.coerceAtMost(1.4f) / 1.4f
                val scale = 1f - 0.36f * norm
                val textAlpha = (1f - 0.82f * norm).coerceIn(0.2f, 1f)

                val text = String.format(java.util.Locale.US, "%02d", value)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelRowHeight)
                        .graphicsLayer {
                            scaleX = scale * (if (selected) popScale.value else 1f)
                            scaleY = scale * (if (selected) popScale.value else 1f)
                        }
                        .semantics {
                            contentDescription = if (selected) {
                                "$label $text selected"
                            } else {
                                "$label $text"
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        style = (if (hero) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium)
                            .copy(fontWeight = if (selected) FontWeight.Black else FontWeight.Medium),
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
                        },
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val labelAlpha by animateFloatAsState(
            targetValue = if (dragging) 0.9f else 0.6f,
            animationSpec = tween(180),
            label = "labelAlpha",
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = labelAlpha),
            letterSpacing = 1.5.sp,
        )
    }
}

// TODO: wrap in your project's theme composable (e.g. ToolzTheme { ... })
// once you tell me its actual name/import — left as MaterialTheme directly
// for now so this compiles without guessing at a symbol that may not exist.
// If your app defines its own accent color source (e.g. a design-system
// token) swap the hardcoded Color.kt value below for that instead.
@Preview(name = "Duration picker — light", showBackground = true)
@Composable
private fun VerticalSmoothDurationPickerPreviewLight() {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DurationPickerPreviewContent()
        }
    }
}

@Preview(
    name = "Duration picker — dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VerticalSmoothDurationPickerPreviewDark() {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DurationPickerPreviewContent()
        }
    }
}

@Composable
private fun DurationPickerPreviewContent() {
    var hours by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf(9) }
    var seconds by remember { mutableStateOf(6) }

    Box(modifier = Modifier.padding(16.dp)) {
        VerticalSmoothDurationPicker(
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            accent = MaterialTheme.colorScheme.primary,
            enabled = true,
            onChange = { h, m, s ->
                hours = h
                minutes = m
                seconds = s
            },
        )
    }
}