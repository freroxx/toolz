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
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

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

    fun loopDurationMs(
        frameCount: Int,
        fps: Int,
        mode: LivingFrameLoopMode,
        speed: Float = LivingFrameSpeed.DEFAULT
    ): Long {
        val steps = playbackOrder(frameCount, mode).size
        val source = LivingFrameFps.snap(fps).coerceAtLeast(1)
        val multiplier = LivingFrameSpeed.snap(speed)
        return (steps * 1000.0 / (source * multiplier)).roundToLong()
    }

    /**
     * Frames the widget should actually display for [displayFps] and [speed].
     *
     * Speed is a resampling multiplier, not a stored-frame edit. At 2× a
     * 10-frame / 30 fps loop becomes 5 display frames; at 0.5× it becomes 20.
     * When [displayFps] is above the captured rate the same source frame is
     * repeated, which is honest: it cannot invent detail that was never
     * captured. The returned list contains source frame indices in order and
     * already includes the boomerang return path.
     */
    fun displaySequence(
        frameCount: Int,
        sourceFps: Int,
        displayFps: Int,
        speed: Float,
        mode: LivingFrameLoopMode
    ): List<Int> {
        val base = playbackOrder(frameCount, mode)
        if (base.size <= 1) return base
        val source = LivingFrameFps.snap(sourceFps).coerceAtLeast(1)
        val display = LivingFrameFps.snap(displayFps).coerceAtLeast(1)
        val multiplier = LivingFrameSpeed.snap(speed)
        val count = (base.size * display / (source * multiplier))
            .roundToInt()
            .coerceAtLeast(1)
        val last = base.lastIndex
        return List(count) { i ->
            val sourcePosition = floor(i * source * multiplier / display)
                .toInt()
                .coerceIn(0, last)
            base[sourcePosition]
        }
    }

    /** Real-time length of a display sequence; used by previews and summaries. */
    fun displayDurationMs(
        frameCount: Int,
        sourceFps: Int,
        displayFps: Int,
        speed: Float,
        mode: LivingFrameLoopMode
    ): Long {
        val frames = displaySequence(frameCount, sourceFps, displayFps, speed, mode).size
        return frames * 1000L / LivingFrameFps.snap(displayFps).coerceAtLeast(1)
    }

    /**
     * How a widget gets rendered so the RemoteViews fit the system's bitmap
     * memory ceiling. [bytes] counts every child bitmap in the RemoteViews,
     * including the duplicates a boomerang playback order creates.
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

    private val SCALES = floatArrayOf(
        1f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.33f, 0.25f, 0.2f, 0.15f, 0.1f
    )

    /** Binder/serialization cost per child; without it tiny budgets are fictional. */
    private const val FRAME_OVERHEAD_BYTES = 64L

    /**
     * Preference order: keep every frame at full size, then use a cheaper
     * pixel format, then lower resolution, and only then drop frames (fps is
     * rescaled so the loop keeps its real-time length). The returned plan is
     * guaranteed to fit [budgetBytes] whenever even a 2×2 frame can fit.
     */
    fun plan(
        frameCount: Int,
        widthPx: Int,
        heightPx: Int,
        opaque: Boolean,
        fps: Int,
        budgetBytes: Long,
        loopMode: LivingFrameLoopMode = LivingFrameLoopMode.LOOP
    ): RenderPlan {
        val n = frameCount.coerceAtLeast(1)
        val budget = budgetBytes.coerceAtLeast(0L)
        val width = widthPx.coerceAtLeast(2)
        val height = heightPx.coerceAtLeast(2)
        val steps = listOf(1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 30, 40)
            .filter { it <= n }
            .ifEmpty { listOf(1) }
        var fallback: RenderPlan? = null
        for (step in steps) {
            val kept = ceil(n / step.toFloat()).toInt().coerceAtLeast(1)
            for (scale in SCALES) {
                val w = (width * scale).roundToInt().coerceAtLeast(2)
                val h = (height * scale).roundToInt().coerceAtLeast(2)
                val formats = if (opaque) listOf(false, true) else listOf(false)
                for (is565 in formats) {
                    val bpp = if (is565) 2L else 4L
                    val children = playbackOrder(kept, loopMode).size
                    val bytes = children.toLong() *
                        (w.toLong() * h * bpp + FRAME_OVERHEAD_BYTES)
                    val p = RenderPlan(
                        scale, step, is565, kept, w, h, bytes,
                        LivingFrameFps.snap(fps / step.toFloat())
                    )
                    fallback = p
                    if (bytes <= budget) return p
                }
            }
        }
        if (fallback != null && fallback.bytes <= budget) return fallback
        // The smallest scale in the normal ladder can still be too large on
        // tiny screens/tiny budgets. Keep one frame and solve for the largest
        // 2..N pixel rectangle that fits, instead of returning a plan the
        // caller cannot push.
        return oneFramePlan(width, height, opaque, n, fps, budget)
    }

    private fun oneFramePlan(
        width: Int,
        height: Int,
        opaque: Boolean,
        step: Int,
        fps: Int,
        budget: Long
    ): RenderPlan {
        val bpp = if (opaque) 2L else 4L
        val available = (budget - FRAME_OVERHEAD_BYTES).coerceAtLeast(0L)
        if (available < bpp * 4L) {
            return RenderPlan(
                scale = 0.1f, step = step, rgb565 = opaque,
                frames = 1, outW = 2, outH = 2,
                bytes = (2L * 2L * bpp) + FRAME_OVERHEAD_BYTES,
                effectiveFps = LivingFrameFps.snap(fps.toFloat() / step)
            )
        }
        val scale = sqrt(
            available.toDouble() / (bpp.toDouble() * width.toDouble() * height.toDouble())
        )
        var w = (width * scale).toInt().coerceIn(2, width)
        var h = (height * scale).toInt().coerceIn(2, height)
        // Coercing an extreme aspect ratio up to 2 px can overshoot again.
        // Shrink the long side until the rectangle fits this budget.
        while (w > 2 && w.toLong() * h * bpp + FRAME_OVERHEAD_BYTES > budget) w--
        while (h > 2 && w.toLong() * h * bpp + FRAME_OVERHEAD_BYTES > budget) h--
        val bytes = w.toLong() * h * bpp + FRAME_OVERHEAD_BYTES
        return RenderPlan(
            scale = (w.toFloat() / width).coerceIn(0.001f, 1f),
            step = step, rgb565 = opaque,
            frames = 1, outW = w, outH = h, bytes = bytes,
            effectiveFps = LivingFrameFps.snap(fps.toFloat() / step)
        )
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
