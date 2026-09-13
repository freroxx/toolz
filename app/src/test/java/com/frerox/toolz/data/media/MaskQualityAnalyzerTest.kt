/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MaskQualityAnalyzer] and [MaskConfidence].
 *
 * Verifies entropy, edge ratio, fg coverage, likelyEmpty, and likelyFull
 * on synthetic masks (all-zero, all-one, binary, noisy, real-world edge).
 */
class MaskQualityAnalyzerTest {

    @Test
    fun emptyArrayReturnsUnknownSentinel() {
        val conf = MaskQualityAnalyzer.analyze(floatArrayOf())
        assertEquals(MaskConfidence.UNKNOWN, conf)
    }

    @Test
    fun allZerosIsLikelyEmptyWithZeroEntropy() {
        val mask = FloatArray(100) { 0f }
        val conf = MaskQualityAnalyzer.analyze(mask)

        assertEquals(0f, conf.edgeRatio, 1e-6f)
        assertEquals(0f, conf.fgCoverage, 1e-6f)
        assertEquals(0f, conf.maskEntropy, 1e-6f)
        assertTrue(conf.likelyEmpty)
        assertFalse(conf.likelyFull)
    }

    @Test
    fun allOnesIsLikelyFullWithZeroEntropy() {
        val mask = FloatArray(100) { 1f }
        val conf = MaskQualityAnalyzer.analyze(mask)

        assertEquals(0f, conf.edgeRatio, 1e-6f)
        assertEquals(1f, conf.fgCoverage, 1e-6f)
        assertEquals(0f, conf.maskEntropy, 1e-6f)
        assertFalse(conf.likelyEmpty)
        assertTrue(conf.likelyFull)
    }

    @Test
    fun cleanBinaryMaskHasLowEntropy() {
        // 50% 0.0, 50% 1.0 — 2 out of 32 bins populated
        val mask = FloatArray(1000) { i -> if (i < 500) 0f else 1f }
        val conf = MaskQualityAnalyzer.analyze(mask)

        assertEquals(0f, conf.edgeRatio, 1e-6f)
        assertEquals(0.5f, conf.fgCoverage, 1e-6f)
        // Theoretical entropy: -2 * (0.5 * ln(0.5)) / ln(32) = ln(2)/ln(32) = 1/5 = 0.20
        assertEquals(0.20f, conf.maskEntropy, 0.01f)
        assertFalse(conf.likelyEmpty)
        assertFalse(conf.likelyFull)
    }

    @Test
    fun complexEdgeMaskReportsEdgeRatio() {
        // 40% bg, 40% fg, 20% ambiguous edge [0.03, 0.97]
        val mask = FloatArray(100) { i ->
            when {
                i < 40 -> 0f
                i < 60 -> 0.5f
                else -> 1f
            }
        }
        val conf = MaskQualityAnalyzer.analyze(mask)

        assertEquals(0.20f, conf.edgeRatio, 1e-6f)
        assertEquals(0.40f, conf.fgCoverage, 1e-6f)
        assertFalse(conf.likelyEmpty)
        assertFalse(conf.likelyFull)
    }

    @Test
    fun uniformNoiseHasHighEntropy() {
        // Evenly spread across all 32 bins
        val mask = FloatArray(3200) { i -> (i % 32) / 31f }
        val conf = MaskQualityAnalyzer.analyze(mask)

        // Entropy should be close to 1.0 (maximum)
        assertTrue("Expected high entropy for uniform distribution, got ${conf.maskEntropy}", conf.maskEntropy > 0.95f)
    }

    @Test
    fun nearBlankMaskTriggersLikelyEmpty() {
        // Only 5 pixels out of 1000 (>0.85) = 0.5% fg -> < 1% threshold
        val mask = FloatArray(1000) { i -> if (i < 5) 1.0f else 0.0f }
        val conf = MaskQualityAnalyzer.analyze(mask)

        assertTrue(conf.likelyEmpty)
        assertFalse(conf.likelyFull)
    }
}
