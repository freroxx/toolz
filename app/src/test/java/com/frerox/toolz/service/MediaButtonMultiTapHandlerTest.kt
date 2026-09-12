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

package com.frerox.toolz.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaButtonMultiTapHandlerTest {

    @Test
    fun `single tap does not fire prematurely and fires after timeout`() = runTest {
        var singleCount = 0
        var doubleCount = 0
        var tripleCount = 0

        val handler = MediaButtonMultiTapHandler(
            coroutineScope = this,
            multiTapTimeoutMs = 380L,
            doubleTapSettleMs = 350L,
            tripleTapCooldownMs = 350L,
            onSingleTap = { singleCount++ },
            onDoubleTap = { doubleCount++ },
            onTripleTap = { tripleCount++ },
            timeProvider = { currentTime }
        )

        handler.onMediaButtonClick()

        advanceTimeBy(300L)
        assertEquals(0, singleCount)
        assertEquals(0, doubleCount)
        assertEquals(0, tripleCount)

        advanceTimeBy(81L)
        assertEquals(1, singleCount)
        assertEquals(0, doubleCount)
        assertEquals(0, tripleCount)
    }

    @Test
    fun `double tap at comfortable cadence cancels single tap and fires next`() = runTest {
        var singleCount = 0
        var doubleCount = 0
        var tripleCount = 0

        val handler = MediaButtonMultiTapHandler(
            coroutineScope = this,
            multiTapTimeoutMs = 380L,
            doubleTapSettleMs = 350L,
            tripleTapCooldownMs = 350L,
            onSingleTap = { singleCount++ },
            onDoubleTap = { doubleCount++ },
            onTripleTap = { tripleCount++ },
            timeProvider = { currentTime }
        )

        // First click
        handler.onMediaButtonClick()

        // Wait 280ms (normal comfortable speed, well within 380ms)
        advanceTimeBy(280L)
        // Second click
        handler.onMediaButtonClick()

        // Single tap should NOT have fired
        assertEquals(0, singleCount)
        assertEquals(0, doubleCount)

        // Wait 300ms (settle window is 350ms)
        advanceTimeBy(300L)
        assertEquals(0, doubleCount)

        // Advance remaining 51ms to pass 350ms settle
        advanceTimeBy(51L)
        assertEquals(0, singleCount)
        assertEquals(1, doubleCount)
        assertEquals(0, tripleCount)
    }

    @Test
    fun `triple tap fires immediately on third tap with zero delay`() = runTest {
        var singleCount = 0
        var doubleCount = 0
        var tripleCount = 0

        val handler = MediaButtonMultiTapHandler(
            coroutineScope = this,
            multiTapTimeoutMs = 380L,
            doubleTapSettleMs = 350L,
            tripleTapCooldownMs = 350L,
            onSingleTap = { singleCount++ },
            onDoubleTap = { doubleCount++ },
            onTripleTap = { tripleCount++ },
            timeProvider = { currentTime }
        )

        handler.onMediaButtonClick() // Tap 1
        advanceTimeBy(200L)
        handler.onMediaButtonClick() // Tap 2
        advanceTimeBy(200L)
        handler.onMediaButtonClick() // Tap 3

        // Immediate execution without needing advanceTimeBy
        assertEquals(0, singleCount)
        assertEquals(0, doubleCount)
        assertEquals(1, tripleCount)
    }

    @Test
    fun `triple tap ignores trailing fourth bounce click within cooldown`() = runTest {
        var singleCount = 0
        var doubleCount = 0
        var tripleCount = 0

        val handler = MediaButtonMultiTapHandler(
            coroutineScope = this,
            multiTapTimeoutMs = 380L,
            doubleTapSettleMs = 350L,
            tripleTapCooldownMs = 350L,
            onSingleTap = { singleCount++ },
            onDoubleTap = { doubleCount++ },
            onTripleTap = { tripleCount++ },
            timeProvider = { currentTime }
        )

        handler.onMediaButtonClick() // Tap 1
        advanceTimeBy(150L)
        handler.onMediaButtonClick() // Tap 2
        advanceTimeBy(150L)
        handler.onMediaButtonClick() // Tap 3 -> triggers immediately
        assertEquals(1, tripleCount)

        // Accidental bounce click 100ms later
        advanceTimeBy(100L)
        handler.onMediaButtonClick() // Tap 4 (bounce)

        // Advance 500ms
        advanceTimeBy(500L)
        // Ensure no extra single or double tap was triggered by the bounce
        assertEquals(0, singleCount)
        assertEquals(0, doubleCount)
        assertEquals(1, tripleCount)
    }

    @Test
    fun `spaced out taps fire separate single taps`() = runTest {
        var singleCount = 0
        var doubleCount = 0
        var tripleCount = 0

        val handler = MediaButtonMultiTapHandler(
            coroutineScope = this,
            multiTapTimeoutMs = 380L,
            doubleTapSettleMs = 350L,
            tripleTapCooldownMs = 350L,
            onSingleTap = { singleCount++ },
            onDoubleTap = { doubleCount++ },
            onTripleTap = { tripleCount++ },
            timeProvider = { currentTime }
        )

        handler.onMediaButtonClick()
        advanceTimeBy(400L)
        assertEquals(1, singleCount)

        handler.onMediaButtonClick()
        advanceTimeBy(400L)
        assertEquals(2, singleCount)
        assertEquals(0, doubleCount)
        assertEquals(0, tripleCount)
    }

    @Test
    fun `cancelPending prevents scheduled tap from firing`() = runTest {
        var singleCount = 0

        val handler = MediaButtonMultiTapHandler(
            coroutineScope = this,
            multiTapTimeoutMs = 380L,
            onSingleTap = { singleCount++ },
            onDoubleTap = {},
            onTripleTap = {},
            timeProvider = { currentTime }
        )

        handler.onMediaButtonClick()
        advanceTimeBy(200L)
        handler.cancelPending()
        advanceTimeBy(300L)

        assertEquals(0, singleCount)
    }
}
