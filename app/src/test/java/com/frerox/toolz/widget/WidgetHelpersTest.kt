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

package com.frerox.toolz.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.frerox.toolz.widget.glance.liveProgressFraction
import com.frerox.toolz.widget.ui.WidgetSizes
import com.frerox.toolz.widget.ui.formatStopwatch
import com.frerox.toolz.widget.ui.formatWidgetClock
import com.frerox.toolz.widget.ui.musicTier
import com.frerox.toolz.widget.ui.pomoTier
import com.frerox.toolz.widget.ui.stopwatchIsWide
import com.frerox.toolz.widget.ui.toolbarIsExpanded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetHelpersTest {

    @Test
    fun stopwatch_zeroStaysZero() {
        assertEquals("00:00", formatStopwatch(0L))
        assertEquals("00:00", formatStopwatch(-500L))
    }

    @Test
    fun stopwatch_ceilsAndRollsOver() {
        assertEquals("00:01", formatStopwatch(1L))
        assertEquals("00:01", formatStopwatch(999L))
        assertEquals("01:00", formatStopwatch(59_999L))
        assertEquals("12:34", formatStopwatch(754_000L))
    }

    @Test
    fun stopwatch_hoursFormat() {
        assertEquals("1:00:00", formatStopwatch(3_599_999L))
        assertEquals("1:02:03", formatStopwatch(3_723_000L))
    }

    @Test
    fun countdown_zeroStaysZero() {
        assertEquals("00:00", formatWidgetClock(0L))
        assertEquals("00:01", formatWidgetClock(1L))
        assertEquals("25:00", formatWidgetClock(25 * 60 * 1000L))
    }

    @Test
    fun tiers_matchCanonicalSizes() {
        assertEquals(0, musicTier(WidgetSizes.MusicSmall))
        assertEquals(1, musicTier(WidgetSizes.MusicMedium))
        assertEquals(2, musicTier(WidgetSizes.MusicLarge))

        assertEquals(0, pomoTier(WidgetSizes.PomoSquare))
        assertEquals(2, pomoTier(WidgetSizes.PomoWide))
        assertEquals(1, pomoTier(WidgetSizes.PomoTall))

        assertFalse(stopwatchIsWide(WidgetSizes.StopwatchSquare))
        assertTrue(stopwatchIsWide(WidgetSizes.StopwatchWide))

        assertFalse(toolbarIsExpanded(WidgetSizes.ToolbarCompact))
        assertTrue(toolbarIsExpanded(WidgetSizes.ToolbarExpanded))
    }

    @Test
    fun tiers_degradeGracefullyOnIntermediates() {
        // OneUI/Nothing hand intermediate sizes during drag — never clip to a
        // larger tier, always fall back to the smaller one.
        assertEquals(0, musicTier(DpSize(200.dp, 120.dp)))
        assertEquals(0, pomoTier(DpSize(200.dp, 170.dp)))
        assertFalse(stopwatchIsWide(DpSize(200.dp, 200.dp)))
        assertFalse(toolbarIsExpanded(DpSize(279.dp, 72.dp)))
    }

    @Test
    fun liveProgress_extrapolatesWhilePlaying() {
        val f = liveProgressFraction(
            positionAtCaptureMs = 10_000L,
            durationMs = 100_000L,
            capturedAtElapsedMs = 1_000L,
            isPlaying = true,
            nowElapsedMs = 11_000L
        )
        assertEquals(0.2f, f, 0.0001f)
    }

    @Test
    fun liveProgress_frozenWhilePaused() {
        val f = liveProgressFraction(
            positionAtCaptureMs = 10_000L,
            durationMs = 100_000L,
            capturedAtElapsedMs = 1_000L,
            isPlaying = false,
            nowElapsedMs = 999_000L
        )
        assertEquals(0.1f, f, 0.0001f)
    }

    @Test
    fun liveProgress_clamped() {
        assertEquals(
            1f,
            liveProgressFraction(90_000L, 100_000L, 0L, true, 50_000L),
            0.0001f
        )
        assertEquals(0f, liveProgressFraction(0L, 0L, 0L, true, 50_000L), 0.0001f)
    }
}
