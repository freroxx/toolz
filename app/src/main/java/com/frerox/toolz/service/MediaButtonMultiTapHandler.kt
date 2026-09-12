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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles multi-tap media button events (such as headset hook or play/pause button presses)
 * with timings tuned to match popular streaming apps like Spotify and YouTube Music:
 * - Single tap: Play/Pause after [multiTapTimeoutMs] (default 380ms).
 * - Double tap: Next Track after [doubleTapSettleMs] (default 350ms) to allow for a comfortable triple-tap cadence.
 * - Triple tap: Previous Track executed immediately (0ms delay) on the 3rd press with [tripleTapCooldownMs]
 *   debounce to ignore accidental trailing 4th clicks/bounces.
 */
class MediaButtonMultiTapHandler(
    private val coroutineScope: CoroutineScope,
    private val multiTapTimeoutMs: Long = 380L,
    private val doubleTapSettleMs: Long = 350L,
    private val tripleTapCooldownMs: Long = 350L,
    private val onSingleTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onTripleTap: () -> Unit,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var clickCount = 0
    private var lastClickTime: Long? = null
    private var lastImmediateActionTime: Long? = null
    private var pendingJob: Job? = null

    fun onMediaButtonClick() {
        val now = timeProvider()

        // Ignore rapid bounce clicks immediately following an instant triple-tap execution
        val lastImmediate = lastImmediateActionTime
        if (lastImmediate != null && (now - lastImmediate) < tripleTapCooldownMs) {
            return
        }

        val lastClick = lastClickTime
        if (lastClick == null || (now - lastClick) > multiTapTimeoutMs) {
            clickCount = 1
        } else {
            clickCount++
        }
        lastClickTime = now

        pendingJob?.cancel()

        if (clickCount >= 3) {
            // Triple tap: trigger immediately without waiting for settle
            clickCount = 0
            lastClickTime = null
            lastImmediateActionTime = now
            onTripleTap()
            return
        }

        pendingJob = coroutineScope.launch {
            val waitDelay = if (clickCount == 1) multiTapTimeoutMs else doubleTapSettleMs
            delay(waitDelay)
            val countToExecute = clickCount
            clickCount = 0
            lastClickTime = null
            when (countToExecute) {
                1 -> onSingleTap()
                2 -> onDoubleTap()
            }
        }
    }

    fun cancelPending() {
        pendingJob?.cancel()
        pendingJob = null
        clickCount = 0
        lastClickTime = null
    }
}
