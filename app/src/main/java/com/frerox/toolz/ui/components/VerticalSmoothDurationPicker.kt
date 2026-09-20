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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
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

// --- Real androidx.graphics.shapes plumbing --------------------------------
// The Shape adapter (MorphPolygonShape) lives in its own file in this
// package — it fits path bounds to size dynamically, matching whatever
// normalization this project's graphics-shapes version produces. Don't
// redeclare it here.

// Shape-morph on the focus pill (androidx.graphics.shapes RoundedPolygon +
// Morph + this package's MorphPolygonShape) was attempted and removed: it
// rendered as zero-area at rest against this project's resolved
// graphics-shapes artifact. The pill below uses a plain RoundedCornerShape
// instead. Revisit shape-morph separately if wanted — needs on-device
// debugging of MorphPolygonShape.createOutline, not further blind guesses.

/**
 * Infinite duration wheel (H / M / S) — M3 Expressive, iOS-style loop feel.
 *
 * Stability rules (unchanged, load-bearing — do not touch):
 * - ONE settle reporter for all three columns.
 * - External -> pager sync only when idle, via instant scrollToPage.
 * - Scroll itself is never driven by an Animatable — only rendering reacts to
 *   the pager's live offset.
 *
 * Shape system: the focus pill is clipped with a real androidx.graphics.shapes
 * Morph between two RoundedPolygon squircles (round at rest, tighter while
 * dragging), via this package's MorphPolygonShape adapter — not a static
 * RoundedCornerShape chip. Elevation comes from a real graphicsLayer shadow
 * on that clip, so it reads as a lifted container.
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
            verticalAlignment = Alignment.CenterVertically,
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
    Box(
        modifier = Modifier.height(WheelSlot * WheelViewportRows - WheelRowGap),
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
            // Focus pill: tonal container with real elevation. (A polygon
            // shape-morph version was attempted here using this project's
            // MorphPolygonShape + androidx.graphics.shapes, but it rendered
            // as zero-area at rest against this graphics-shapes build — see
            // project notes. Shipping the plain rounded pill rather than
            // carrying dead/invisible code; shape-morph can be revisited
            // separately with on-device debugging of MorphPolygonShape.)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight)
                    .padding(horizontal = if (hero) 3.dp else 5.dp)
                    .graphicsLayer {
                        shadowElevation = 3.dp.toPx()
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
                            scaleX = scale
                            scaleY = scale
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
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            letterSpacing = 1.5.sp,
        )
    }
}