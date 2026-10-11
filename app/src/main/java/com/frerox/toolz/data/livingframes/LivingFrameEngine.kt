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

package com.frerox.toolz.data.livingframes

/** Pure playback math — unit-tested, no Android dependencies. */
object LivingFrameEngine {

    /** Frame interval for a target fps, clamped to [LivingFrameFps.MIN..MAX]. */
    fun intervalMs(fps: Int): Long = LivingFrameFps.intervalMs(fps)

    /**
     * Next frame index given current position, frame count and loop mode.
     * @param direction +1 forward, -1 backward (used by BOOMERANG).
     * @return Pair(nextIndex, nextDirection)
     */
    fun nextIndex(
        current: Int,
        direction: Int,
        frameCount: Int,
        loopMode: LivingFrameLoopMode
    ): Pair<Int, Int> {
        if (frameCount <= 1) return 0 to 1
        return when (loopMode) {
            LivingFrameLoopMode.LOOP -> ((current + 1) % frameCount) to 1
            LivingFrameLoopMode.ONCE -> (minOf(current + 1, frameCount - 1)) to 1
            LivingFrameLoopMode.BOOMERANG -> {
                var dir = if (direction == 0) 1 else direction
                var next = current + dir
                if (next >= frameCount) {
                    dir = -1
                    next = (frameCount - 2).coerceAtLeast(0)
                } else if (next < 0) {
                    dir = 1
                    next = minOf(1, frameCount - 1)
                }
                next to dir
            }
        }
    }

    /** Sequence of indices for [steps] ticks starting at [start] — handy for previews/tests. */
    fun sequence(
        start: Int,
        steps: Int,
        frameCount: Int,
        loopMode: LivingFrameLoopMode
    ): List<Int> {
        if (frameCount <= 0 || steps <= 0) return emptyList()
        val out = ArrayList<Int>(steps)
        var idx = start.coerceIn(0, frameCount - 1)
        var dir = 1
        repeat(steps) {
            out.add(idx)
            val (n, d) = nextIndex(idx, dir, frameCount, loopMode)
            idx = n
            dir = d
        }
        return out
    }

    /** Whether playback is finished and should hold (ONCE mode at last frame). */
    fun isFinished(current: Int, frameCount: Int, loopMode: LivingFrameLoopMode): Boolean =
        loopMode == LivingFrameLoopMode.ONCE && current >= frameCount - 1

    /** Clamp fps into the supported widget range. */
    fun clampFps(fps: Int): Int = fps.coerceIn(LivingFrameFps.MIN, LivingFrameFps.MAX)

    /** Max importable frames for a clip of [durationMs] at [fps]. */
    fun maxFramesFor(durationMs: Long, fps: Int): Int =
        ((durationMs.coerceAtLeast(0L) * clampFps(fps)) / 1000L).toInt().coerceAtLeast(1)

    /** Downscale factor so the longest side fits [maxSide]. */
    fun sampleSize(srcW: Int, srcH: Int, maxSide: Int): Int {
        if (srcW <= 0 || srcH <= 0) return 1
        var sample = 1
        while (maxOf(srcW, srcH) / sample > maxSide) sample *= 2
        return sample
    }
}
