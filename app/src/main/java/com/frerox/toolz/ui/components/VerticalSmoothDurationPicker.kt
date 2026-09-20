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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

private val WheelItemHeight: Dp = 56.dp

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
 * Infinite iPhone-style duration wheel (H / M / S), M3 Expressive styled.
 *
 * Stability rules (learned from the old 2-column wheel):
 * - ONE settle reporter for all three columns: simultaneous flings can't clobber
 *   each other (each column reads its siblings' pager state, never stale props).
 * - External -> pager sync only when idle (`!isScrollInProgress`) via instant
 *   [scrollToPage][androidx.compose.foundation.pager.PagerState.scrollToPage].
 * - No per-item animations: item alpha/scale derive directly from the pager's
 *   live offset, so rendering runs at scroll frequency with zero animatable churn.
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

    // Single settle reporter: one onChange per user gesture, siblings read live.
    LaunchedEffect(hoursPager, minutesPager, secondsPager) {
        snapshotFlow {
            Triple(
                floorMod(hoursPager.settledPage, TIMER_WHEEL_HOURS),
                floorMod(minutesPager.settledPage, TIMER_WHEEL_MINUTES),
                floorMod(secondsPager.settledPage, TIMER_WHEEL_SECONDS),
            )
        }.distinctUntilChanged().collect { (h, m, s) ->
            if (h != safeHours || m != safeMinutes || s != safeSeconds) {
                vibrationManager?.vibrateTick()
                onChange(h, m, s)
            }
        }
    }

    // External -> pager (dialog entry, preset tap, rotation): only when idle.
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfiniteWheelColumn(
            pagerState = hoursPager,
            cycle = TIMER_WHEEL_HOURS,
            label = "Hours",
            accent = accent,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        WheelSeparator(accent = accent)
        InfiniteWheelColumn(
            pagerState = minutesPager,
            cycle = TIMER_WHEEL_MINUTES,
            label = "Mins",
            accent = accent,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        WheelSeparator(accent = accent)
        InfiniteWheelColumn(
            pagerState = secondsPager,
            cycle = TIMER_WHEEL_SECONDS,
            label = "Secs",
            accent = accent,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
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
    Text(
        text = ":",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        color = accent,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfiniteWheelColumn(
    pagerState: PagerState,
    cycle: Int,
    label: String,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.height(WheelItemHeight * 3),
            contentAlignment = Alignment.Center,
        ) {
            // Track.
            Surface(
                modifier = Modifier.fillMaxHeight(),
                shape = SquircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.12f)),
            ) {}
            // Center focus pill (behind the pager so touches pass through).
            Surface(
                modifier = Modifier.height(WheelItemHeight * 0.95f),
                shape = SmallExpressiveShape,
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.22f)),
            ) {}
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxHeight(),
                userScrollEnabled = enabled,
                contentPadding = PaddingValues(vertical = WheelItemHeight),
                beyondViewportPageCount = 2,
            ) { index ->
                val value = floorMod(index, cycle)
                // Live offset — no animatables, smooth at scroll frequency.
                val pageOffset = ((pagerState.currentPage - index) +
                    pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 3f)
                val selected = pageOffset < 0.5f
                val text = String.format("%02d", value)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .graphicsLayer {
                            val scale = (1f - 0.13f * pageOffset).coerceIn(0.7f, 1f)
                            scaleX = scale
                            scaleY = scale
                            alpha = (1f - 0.32f * pageOffset).coerceIn(0.25f, 1f)
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
                        style = if (selected) {
                            MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
                        } else {
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        },
                        fontFamily = FontFamily.Monospace,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = accent.copy(alpha = 0.7f),
            letterSpacing = 2.sp,
        )
    }
}
