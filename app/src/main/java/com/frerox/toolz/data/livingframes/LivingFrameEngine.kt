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

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pure math: playback order, render budgeting, shuffle. No Android dependencies. */
object LivingFrameEngine {

    /**
     * Child order for the widget's flipper. BOOMERANG replays the middle
     * frames backwards (0..n-1, n-2..1) so the loop seam has no duplicate.
     */
    fun playbackOrder(frameCount: Int, mode: LivingFrameLoopMode): List<Int> {
        if (frameCount <= 1) return listOf(0)
        val forward = (0 until frameCount).toList()
        return when (mode) {
            LivingFrameLoopMode.LOOP -> forward
            LivingFrameLoopMode.BOOMERANG ->
                if (frameCount == 2) forward else forward + (frameCount - 2 downTo 1).toList()
        }
    }

    fun loopDurationMs(frameCount: Int, fps: Int, mode: LivingFrameLoopMode): Long {
        val steps = playbackOrder(frameCount, mode).size
        return steps * 1000L / LivingFrameFps.snap(fps).coerceAtLeast(1)
    }

    /**
     * How a widget gets rendered so the RemoteViews fit the system's bitmap
     * memory ceiling. [bytes] counts unique bitmaps only.
     */
    data class RenderPlan(
        val scale: Float,
        val step: Int,
        val rgb565: Boolean,
        val frames: Int,
        val outW: Int,
        val outH: Int,
        val bytes: Long,
        val effectiveFps: Int
    ) {
        val reduced: Boolean get() = scale < 0.999f || step > 1 || rgb565
    }

    private val SCALES = floatArrayOf(1f, 0.85f, 0.7f, 0.6f)

    /**
     * Preference order: full frames at full size, then cheaper pixel format,
     * then lower resolution, and only then fewer frames (fps is rescaled so
     * the loop keeps its real-time length).
     */
    fun plan(
        frameCount: Int,
        widthPx: Int,
        heightPx: Int,
        opaque: Boolean,
        fps: Int,
        budgetBytes: Long
    ): RenderPlan {
        val n = frameCount.coerceAtLeast(1)
        var fallback: RenderPlan? = null
        for (step in 1..4) {
            val kept = ceil(n / step.toFloat()).toInt().coerceAtLeast(1)
            for (scale in SCALES) {
                val w = (widthPx * scale).roundToInt().coerceAtLeast(2)
                val h = (heightPx * scale).roundToInt().coerceAtLeast(2)
                val formats = if (opaque) listOf(false, true) else listOf(false)
                for (is565 in formats) {
                    val bpp = if (is565) 2L else 4L
                    val bytes = kept.toLong() * w * h * bpp
                    val p = RenderPlan(
                        scale, step, is565, kept, w, h, bytes,
                        LivingFrameFps.snap(fps / step.toFloat())
                    )
                    fallback = p
                    if (bytes <= budgetBytes) return p
                }
            }
        }
        return fallback!!
    }

    /** Deterministic shuffle cycle: same widget + cycle always gives the same order. */
    fun shuffleOrder(count: Int, seed: Long): List<Int> =
        (0 until count).shuffled(java.util.Random(seed))

    /**
     * Slideshow cursor → photo index. [position] only ever increases; each
     * cycle through [count] photos is its own shuffle, and a cycle never
     * starts on the photo the previous one ended on.
     */
    fun slideIndex(position: Int, count: Int, shuffle: Boolean, seed: Long): Int {
        if (count <= 1) return 0
        val pos = position.coerceAtLeast(0)
        if (!shuffle || count == 2) return pos % count
        val cycle = pos / count
        val inCycle = pos % count
        val order = shuffleOrder(count, seed + cycle).toMutableList()
        if (cycle > 0) {
            val prevLast = shuffleOrder(count, seed + cycle - 1).last()
            if (order[0] == prevLast) {
                val t = order[0]; order[0] = order[1]; order[1] = t
            }
        }
        return order[inCycle]
    }
}
