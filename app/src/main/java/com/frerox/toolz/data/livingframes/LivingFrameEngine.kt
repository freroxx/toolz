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

import kotlin.math.abs
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
     * Flipper rate for a set. The user's fps is a ceiling: running the
     * flipper faster than `sourceFps × speed` only repeats frames, which
     * costs binder transactions and host redraws for zero extra motion. So
     * the flipper runs at the rate the content needs, or at the user's
     * ceiling when that is lower (decimating frames to save battery).
     *
     * The layout picked for the widget MUST use this same value, because
     * [displaySequence] is resampled for exactly this flipper interval.
     */
    fun playbackFps(sourceFps: Int, requestedFps: Int, speed: Float): Int {
        val source = LivingFrameFps.snap(sourceFps).coerceAtLeast(1)
        val needed = LivingFrameFps.snap(source * LivingFrameSpeed.snap(speed))
        return minOf(needed, LivingFrameFps.snap(requestedFps))
            .coerceAtLeast(LivingFrameFps.MIN)
    }

    fun playbackFps(frame: LivingFrame): Int =
        playbackFps(frame.sourceFps, frame.fps, frame.speed)

    /**
     * Frames the widget should actually display for [displayFps] and [speed].
     *
     * Speed is a resampling multiplier, not a stored-frame edit. At 2× a
     * 10-frame / 30 fps loop becomes 5 display frames; at 0.5× it becomes 20.
     * Sampling follows the flipper's real interval ([LivingFrameFps.actualRate])
     * and picks the source frame at the centre of each display slot, so
     * duplicates and drops are spread evenly instead of bunching up.
     * The returned list contains source frame indices in order and already
     * includes the boomerang return path.
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
        val multiplier = LivingFrameSpeed.snap(speed).toDouble()
        // Source frames consumed per flipper tick.
        val step = source * multiplier / LivingFrameFps.actualRate(display)
        val count = (base.size / step).roundToInt().coerceAtLeast(1)
        val last = base.lastIndex
        return List(count) { i ->
            val sourcePosition = floor((i + 0.5) * step + 1e-9)
                .toInt()
                .coerceIn(0, last)
            base[sourcePosition]
        }
    }

    /**
     * Source bitmap size needed to draw [sourceWidth]×[sourceHeight] into an
     * [outW]×[outH] widget without upscaling.
     *
     * A widget is not a square. Asking the decoder for `max(outW, outH)` is
     * enough for FIT, but wrong for FILL on a portrait widget showing a
     * landscape clip: the crop keeps only the middle of the source, so the
     * source's *longest* side must be larger than the widget's short side.
     * For a 466×473 widget and a 16:9 clip that is 841 px, not 473 px.
     */
    fun requiredSourceSide(
        outW: Int,
        outH: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        fit: LivingFrameFit
    ): Int {
        val w = outW.coerceAtLeast(2)
        val h = outH.coerceAtLeast(2)
        val sw = sourceWidth.coerceAtLeast(1)
        val sh = sourceHeight.coerceAtLeast(1)
        if (fit == LivingFrameFit.FIT) return maxOf(w, h)
        val sourceAspect = sw / sh.toFloat()
        val destAspect = w / h.toFloat()
        val required = if (sourceAspect > destAspect) {
            h * sourceAspect
        } else {
            w / sourceAspect
        }
        return ceil(required).toInt().coerceAtLeast(1)
    }

    /** Real-time length of a display sequence; used by previews and summaries. */
    fun displayDurationMs(
        frameCount: Int,
        sourceFps: Int,
        displayFps: Int,
        speed: Float,
        mode: LivingFrameLoopMode,
        transition: LivingFrameLoopTransition = LivingFrameLoopTransition.NONE
    ): Long {
        val frames = displaySteps(
            frameCount, sourceFps, displayFps, speed, mode, transition
        ).size
        return frames.toLong() * LivingFrameFps.intervalMs(displayFps)
    }

    /**
     * One flipper child. Normal frames are `from == to`; seam frames blend the
     * last source frame into the first. [mix] is 0 for normal frames and
     * (0, 1) for a blend.
     */
    data class DisplayStep(
        val from: Int,
        val to: Int,
        val mix: Float
    ) {
        val blended: Boolean get() = from != to && mix > 0f
    }

    /** Target length of the loop-seam crossfade. */
    const val LOOP_SEAM_MS = 180

    /**
     * Number of extra blend children needed to fade the seam over roughly
     * [LOOP_SEAM_MS]. The wrap back to the first frame completes the last
     * step, so this is one less than the number of flipper intervals in the
     * fade. At least one blend child is always produced, so a slow loop still
     * softens instead of snapping.
     */
    fun seamStepCount(displayFps: Int): Int {
        val interval = LivingFrameFps.intervalMs(displayFps).coerceAtLeast(1)
        return ((LOOP_SEAM_MS.toFloat() / interval).roundToInt() - 1).coerceAtLeast(1)
    }

    /**
     * Playback children, including the loop-seam crossfade. BOOMERANG already
     * has matching endpoints, and a single-frame or identical-endpoint loop
     * has nothing to fade, so those ignore [transition].
     */
    fun displaySteps(
        frameCount: Int,
        sourceFps: Int,
        displayFps: Int,
        speed: Float,
        mode: LivingFrameLoopMode,
        transition: LivingFrameLoopTransition
    ): List<DisplayStep> {
        val base = displaySequence(frameCount, sourceFps, displayFps, speed, mode)
        val normal = base.map { DisplayStep(it, it, 0f) }
        if (transition == LivingFrameLoopTransition.NONE) return normal
        if (mode == LivingFrameLoopMode.BOOMERANG || base.size <= 1) return normal
        val first = base.first()
        val last = base.last()
        if (first == last) return normal
        val count = seamStepCount(displayFps)
        val seam = List(count) { i ->
            DisplayStep(last, first, (i + 1f) / (count + 1f))
        }
        return normal + seam
    }

    fun displaySteps(
        frame: LivingFrame,
        displayFps: Int = playbackFps(frame)
    ): List<DisplayStep> = displaySteps(
        frame.frameCount,
        frame.sourceFps,
        displayFps,
        frame.speed,
        frame.loopMode,
        frame.loopTransition
    )

    /** Rotate a loop so [first] becomes child 0; used when resuming from a pause. */
    fun rotateSteps(steps: List<DisplayStep>, first: Int): List<DisplayStep> {
        if (steps.size <= 1) return steps
        val i = first.coerceIn(0, steps.lastIndex)
        if (i == 0) return steps
        return steps.subList(i, steps.size) + steps.subList(0, i)
    }

    /**
     * Find the display step closest to a saved pause point. Fps, speed or a
     * resize can change the step list while a widget is paused; the paused
     * look should survive that instead of jumping to frame 0.
     */
    fun nearestStepIndex(steps: List<DisplayStep>, from: Int, to: Int, mix: Float): Int {
        if (steps.isEmpty()) return 0
        var best = 0
        var bestScore = Float.MAX_VALUE
        steps.forEachIndexed { index, step ->
            val score = when {
                step.from == from && step.to == to -> abs(step.mix - mix)
                step.from == from -> 1f + abs(step.mix - mix)
                else -> 2f + abs(step.mix - mix)
            }
            if (score < bestScore) {
                bestScore = score
                best = index
            }
        }
        return best
    }

    /** Baked corner radius in output pixels, before the ImageView scales it back. */
    data class CornerRadii(val rx: Float, val ry: Float)

    /**
     * Corner radius for a bitmap that will be drawn into a [widthPx]×
     * [heightPx] view. [densityX]/[densityY] already include `outPx /
     * widgetPx`, so when the view scales the bitmap back to widget size the
     * radius lands on the configured dp at every resolution and aspect ratio.
     */
    fun cornerRadii(
        cornerDp: Int,
        densityX: Float,
        densityY: Float,
        widthPx: Int,
        heightPx: Int
    ): CornerRadii {
        if (cornerDp <= 0) return CornerRadii(0f, 0f)
        val maxRx = widthPx.coerceAtLeast(1) / 2f
        val maxRy = heightPx.coerceAtLeast(1) / 2f
        return CornerRadii(
            rx = (cornerDp * densityX.coerceAtLeast(0f)).coerceIn(0f, maxRx),
            ry = (cornerDp * densityY.coerceAtLeast(0f)).coerceIn(0f, maxRy)
        )
    }

    /** Density for a bitmap that scales [outPx] → [widgetPx] in the view. */
    fun densityScale(density: Float, outPx: Int, widgetPx: Int): Float =
        density * outPx.coerceAtLeast(1) / widgetPx.coerceAtLeast(1)

    /**
     * How a widget gets rendered so the RemoteViews fit the system's bitmap
     * memory ceiling. [bytes] counts every child bitmap in the RemoteViews,
     * including seam blends and the duplicates a boomerang order creates.
     * [frames] is the number of flipper children (not the number of stored
     * source frames).
     */
    data class RenderPlan(
        val scale: Float,
        val step: Int,
        val rgb565: Boolean,
        val frames: Int,
        val outW: Int,
        val outH: Int,
        val bytes: Long,
        val effectiveFps: Int,
        val transition: Boolean = false
    ) {
        val reduced: Boolean get() = scale < 0.999f || step > 1 || rgb565
    }

    private val SCALES = floatArrayOf(
        1f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.33f, 0.25f, 0.2f, 0.15f, 0.1f
    )

    /** Binder/serialization cost per child; without it tiny budgets are fictional. */
    private const val FRAME_OVERHEAD_BYTES = 1024L

    /** Below this fps a static push is no better than a slideshow. */
    const val MIN_STATIC_FPS = 8

    /** Below this scale the widget looks soft enough to prefer streaming. */
    const val MIN_STATIC_SCALE = 0.4f

    /**
     * A 900-child RemoteViews would be enormous regardless of the bitmap
     * budget. 240 children is 20 s at 12 fps and still comfortable to inflate.
     */
    const val MAX_STATIC_CHILDREN = 240

    /**
     * Fps-first static planner: for each supported rate (highest first) it
     * finds the largest resolution that fits, then returns the plan with the
     * best resolution, breaking ties by fps. This deliberately prefers 10 fps
     * at 70% over 30 fps at 20%: sharpness is what the user notices first.
     *
     * Returns null when no plan meets [minFps] and [minScale]; callers stream
     * in that case. The transition is included in the byte count, so enabling
     * Fade/BLUR can push a clip over the line.
     */
    fun planStatic(
        frameCount: Int,
        sourceFps: Int,
        speed: Float,
        requestedFps: Int,
        widthPx: Int,
        heightPx: Int,
        opaque: Boolean,
        budgetBytes: Long,
        loopMode: LivingFrameLoopMode,
        transition: LivingFrameLoopTransition = LivingFrameLoopTransition.NONE,
        minFps: Int = MIN_STATIC_FPS,
        minScale: Float = MIN_STATIC_SCALE
    ): RenderPlan? {
        val n = frameCount.coerceAtLeast(1)
        if (n <= 1) return null
        val budget = budgetBytes.coerceAtLeast(0L)
        val width = widthPx.coerceAtLeast(2)
        val height = heightPx.coerceAtLeast(2)
        // Never play faster than the user asked: minFps is the floor for
        // lowering a faster request, not a reason to raise a slow one.
        val top = LivingFrameFps.snap(requestedFps).coerceAtLeast(1)
        val floor = minOf(minFps, top).coerceAtLeast(1)
        var best: RenderPlan? = null
        for (rate in LivingFrameFps.STEPS.filter { it in floor..top }.sortedDescending()) {
            val steps = displaySteps(n, sourceFps, rate, speed, loopMode, transition)
            if (steps.size > MAX_STATIC_CHILDREN) continue
            val plan = bestFit(
                children = steps.size,
                width = width,
                height = height,
                opaque = opaque,
                budget = budget,
                fps = rate,
                minScale = minScale,
                transition = transition != LivingFrameLoopTransition.NONE
            ) ?: continue
            val current = best
            if (current == null ||
                plan.scale > current.scale + 1e-4f ||
                (abs(plan.scale - current.scale) <= 1e-4f &&
                        plan.effectiveFps > current.effectiveFps)
            ) {
                best = plan
            }
        }
        return best
    }

    /** One still image, scaled only as far as the budget requires. */
    fun planStill(
        widthPx: Int,
        heightPx: Int,
        opaque: Boolean,
        budgetBytes: Long
    ): RenderPlan {
        val width = widthPx.coerceAtLeast(2)
        val height = heightPx.coerceAtLeast(2)
        val budget = budgetBytes.coerceAtLeast(0L)
        return bestFit(
            children = 1,
            width = width,
            height = height,
            opaque = opaque,
            budget = budget,
            fps = 1,
            minScale = 0.1f,
            transition = false
        ) ?: oneFramePlan(width, height, opaque, 1, budget)
    }

    private fun bestFit(
        children: Int,
        width: Int,
        height: Int,
        opaque: Boolean,
        budget: Long,
        fps: Int,
        minScale: Float,
        transition: Boolean
    ): RenderPlan? {
        for (scale in SCALES) {
            if (scale < minScale) break
            val w = (width * scale).roundToInt().coerceAtLeast(2)
            val h = (height * scale).roundToInt().coerceAtLeast(2)
            val formats = if (opaque) listOf(false, true) else listOf(false)
            for (is565 in formats) {
                val bpp = if (is565) 2L else 4L
                val bytes = children.toLong() *
                        (w.toLong() * h * bpp + FRAME_OVERHEAD_BYTES)
                if (bytes <= budget) {
                    return RenderPlan(
                        scale = scale,
                        step = 1,
                        rgb565 = is565,
                        frames = children,
                        outW = w,
                        outH = h,
                        bytes = bytes,
                        effectiveFps = fps,
                        transition = transition
                    )
                }
            }
        }
        return null
    }

    private fun oneFramePlan(
        width: Int,
        height: Int,
        opaque: Boolean,
        fps: Int,
        budget: Long
    ): RenderPlan {
        val bpp = if (opaque) 2L else 4L
        val available = (budget - FRAME_OVERHEAD_BYTES).coerceAtLeast(0L)
        if (available < bpp * 4L) {
            return RenderPlan(
                scale = 0.1f, step = 1, rgb565 = opaque,
                frames = 1, outW = 2, outH = 2,
                bytes = (2L * 2L * bpp) + FRAME_OVERHEAD_BYTES,
                effectiveFps = fps
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
            step = 1, rgb565 = opaque,
            frames = 1, outW = w, outH = h, bytes = bytes,
            effectiveFps = fps
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