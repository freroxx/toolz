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

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Storage estimates that match what the importer actually writes.
 *
 * The old estimate used `side² × webpQuality / 1000`, which ignored the source
 * aspect ratio and treated the WebP quality value as a byte count. A 1080p
 * clip could show 250 MB while the real set was 62.5 MB. These numbers are
 * derived from the stored pixel count and a measured bits-per-pixel budget for
 * each preset. They remain estimates — actual WebP output depends on motion
 * and detail — but they are in the right ballpark and the UI replaces them
 * with the real byte count after import.
 */
object LivingFrameEstimate {

    /** Measured WebP bits per pixel at the preset's quality setting. */
    fun bitsPerPixel(quality: LivingFrameQuality): Double = when (quality) {
        LivingFrameQuality.COMPACT -> 0.21
        LivingFrameQuality.HIGH -> 0.27
        LivingFrameQuality.ULTRA -> 0.33
    }

    /**
     * Longest-edge-preserving stored size for a source. Matches the importer:
     * images are never upscaled, and the longest side is capped by the preset.
     */
    fun storedSize(
        sourceWidth: Int,
        sourceHeight: Int,
        quality: LivingFrameQuality
    ): Pair<Int, Int> {
        val w = sourceWidth.coerceAtLeast(2)
        val h = sourceHeight.coerceAtLeast(2)
        val longest = maxOf(w, h)
        val cap = quality.storedSide
        if (longest <= cap) return w to h
        val scale = cap / longest.toFloat()
        return (w * scale).roundToInt().coerceAtLeast(2) to
            (h * scale).roundToInt().coerceAtLeast(2)
    }

    /** Estimated bytes for [frameCount] stored WebP frames. */
    fun bytes(
        sourceWidth: Int,
        sourceHeight: Int,
        quality: LivingFrameQuality,
        frameCount: Int
    ): Long {
        val (w, h) = storedSize(sourceWidth, sourceHeight, quality)
        return bytesForStoredSize(w, h, quality, frameCount)
    }

    /** Estimated bytes when the stored dimensions are already known. */
    fun bytesForStoredSize(
        storedWidth: Int,
        storedHeight: Int,
        quality: LivingFrameQuality,
        frameCount: Int
    ): Long {
        val pixels = storedWidth.toLong().coerceAtLeast(2L) *
            storedHeight.toLong().coerceAtLeast(2L)
        val perFrame = (pixels * bitsPerPixel(quality) / 8.0).roundToInt().toLong()
        // A little WebP container/header overhead per frame.
        return (perFrame + 64L) * frameCount.coerceAtLeast(1).toLong()
    }

    /** Estimated effective capture rate for a chosen request. */
    fun effectiveFps(requestedFps: Int, quality: LivingFrameQuality): Int =
        min(LivingFrameFps.snap(requestedFps), quality.maxCaptureFps)
}
