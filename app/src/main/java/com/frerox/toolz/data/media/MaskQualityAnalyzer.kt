/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.data.media

import kotlin.math.ln

/**
 * Structured quality profile for a raw segmentation mask.
 *
 * All ratios are in [0, 1]. Computed once per inference result and threaded
 * through the pipeline so downstream stages can adapt without re-scanning the mask.
 *
 * @param edgeRatio      Fraction of pixels in the [0.03, 0.97] "ambiguous" band.
 *                       High (>0.05) → complex edges; low + likelyEmpty → model failure.
 * @param fgCoverage     Fraction of pixels >0.85 — the "hard foreground" blob.
 *                       If this + edgeRatio > 0.98 the model probably labelled everything
 *                       as foreground (wrong model for this subject).
 * @param maskEntropy    Shannon entropy of a 32-bin histogram, normalised to [0, 1].
 *                       A clean binary mask scores <0.35; noisy/uncertain output >0.65.
 * @param likelyEmpty    True when the foreground blob is <1 % of the image — the model
 *                       returned a near-blank result (wrong preprocessing, no clear subject).
 * @param likelyFull     True when >97 % of pixels are hard foreground — model probably
 *                       returned the entire image as subject (usually a normalisation bug).
 */
data class MaskConfidence(
    val edgeRatio: Float,
    val fgCoverage: Float,
    val maskEntropy: Float,
    val likelyEmpty: Boolean,
    val likelyFull: Boolean,
) {
    companion object {
        /** Sentinel used when no confidence analysis was performed. */
        val UNKNOWN = MaskConfidence(
            edgeRatio = 0.05f,
            fgCoverage = 0.5f,
            maskEntropy = 0.5f,
            likelyEmpty = false,
            likelyFull = false,
        )
    }
}

/**
 * Analyses a raw float mask [0..1] and returns a [MaskConfidence] profile.
 *
 * Pure Kotlin, no Android deps — fully unit-testable.
 *
 * Complexity: O(n) single pass for counts + O(32) pass for entropy = effectively O(n).
 * For a 1024×1024 mask (~1 M pixels) this takes <5 ms on a modern CPU.
 */
object MaskQualityAnalyzer {

    private const val BINS = 32
    private const val EDGE_LO = 0.03f
    private const val EDGE_HI = 0.97f
    private const val FG_THRESH = 0.85f
    private const val EMPTY_THRESHOLD = 0.01f   // <1 % fg → blank mask
    private const val FULL_THRESHOLD = 0.97f    // >97 % fg → whole-image mask

    fun analyze(mask: FloatArray): MaskConfidence {
        if (mask.isEmpty()) return MaskConfidence.UNKNOWN

        val n = mask.size
        val histogram = IntArray(BINS)
        var edgeCount = 0
        var fgCount = 0

        for (v in mask) {
            val clamped = v.coerceIn(0f, 1f)
            // Histogram bin
            val bin = (clamped * (BINS - 1)).toInt().coerceIn(0, BINS - 1)
            histogram[bin]++
            // Edge band
            if (clamped in EDGE_LO..EDGE_HI) edgeCount++
            // Hard foreground
            if (clamped > FG_THRESH) fgCount++
        }

        val edgeRatio   = edgeCount.toFloat() / n
        val fgCoverage  = fgCount.toFloat() / n
        val maskEntropy = shannonEntropy(histogram, n)

        return MaskConfidence(
            edgeRatio  = edgeRatio,
            fgCoverage = fgCoverage,
            maskEntropy = maskEntropy,
            likelyEmpty = fgCoverage < EMPTY_THRESHOLD,
            likelyFull  = fgCoverage > FULL_THRESHOLD,
        )
    }

    /**
     * Shannon entropy of the bin counts, normalised to [0, 1] by dividing by log2(BINS).
     * A perfectly binary mask (half 0, half 1) scores ~1/log2(32)=0.2 (two populated bins).
     * A uniform noise mask scores 1.0 (all bins equal).
     */
    private fun shannonEntropy(histogram: IntArray, total: Int): Float {
        if (total == 0) return 0f
        var entropy = 0.0
        val logTotal = ln(total.toDouble())
        for (count in histogram) {
            if (count == 0) continue
            val p = count.toDouble() / total
            entropy -= p * ln(p)
        }
        // Normalise by ln(BINS) so the result is in [0, 1]
        val maxEntropy = ln(BINS.toDouble())
        return if (maxEntropy > 0.0) (entropy / maxEntropy).toFloat().coerceIn(0f, 1f) else 0f
    }
}
