/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pins [FastBlur.stackBlurPixels] — pure pixel math, no Android framework.
 * A broken blur ships as visible banding on every Blur export, so the core
 * invariants (uniform invariance, symmetry, boundedness, alpha preservation)
 * are locked here.
 */
class FastBlurTest {

    private fun solid(w: Int, h: Int, argb: Int): IntArray =
        IntArray(w * h) { argb }

    @Test
    fun uniformImageIsUnchanged() {
        val pix = solid(16, 16, 0xFF804020.toInt())
        FastBlur.stackBlurPixels(pix, 16, 16, 8)
        for (p in pix) assertEquals(0xFF804020.toInt(), p)
    }

    @Test
    fun radiusZeroIsNoOp() {
        val pix = IntArray(64) { 0xFF000000.toInt() or (it * 0x010101) }
        val before = pix.copyOf()
        FastBlur.stackBlurPixels(pix, 8, 8, 0)
        assertTrue(pix.contentEquals(before))
    }

    @Test
    fun singleWhitePixelSpreadsSymmetrically() {
        val w = 21
        val h = 21
        val pix = IntArray(w * h) { 0xFF000000.toInt() }
        pix[10 * w + 10] = 0xFFFFFFFF.toInt()
        FastBlur.stackBlurPixels(pix, w, h, 5)
        fun lum(p: Int) = (p shr 16) and 0xFF
        // Symmetric pairs around the center must match.
        assertEquals(lum(pix[10 * w + 5]), lum(pix[10 * w + 15]))
        assertEquals(lum(pix[5 * w + 10]), lum(pix[15 * w + 10]))
        assertEquals(lum(pix[7 * w + 7]), lum(pix[13 * w + 13]))
        // Center stays brightest, edges stay darkest.
        assertTrue(lum(pix[10 * w + 10]) >= lum(pix[10 * w + 9]))
        assertEquals(0, lum(pix[0]))
    }

    @Test
    fun outputStaysInRangeAndPreservesAlpha() {
        val w = 24
        val h = 18
        // Opaque red gradient + semi-transparent patch.
        val pix = IntArray(w * h) { i ->
            val x = i % w
            0x80000000.toInt() or (x * 10 shl 16)
        }
        FastBlur.stackBlurPixels(pix, w, h, 6)
        for (p in pix) {
            val a = (p ushr 24) and 0xFF
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            assertEquals("alpha preserved", 0x80, a)
            assertTrue("r in range: $r", r in 0..230)
            assertEquals("g stays 0", 0, g)
            assertEquals("b stays 0", 0, b)
        }
    }

    @Test
    fun largerRadiusBlursMore() {
        fun centerAfter(radius: Int): Int {
            val w = 31
            val pix = IntArray(w * w) { 0xFF000000.toInt() }
            pix[15 * w + 15] = 0xFFFFFFFF.toInt()
            FastBlur.stackBlurPixels(pix, w, w, radius)
            return (pix[15 * w + 15] shr 16) and 0xFF
        }
        val small = centerAfter(2)
        val big = centerAfter(10)
        // Wider kernel spreads the single white pixel thinner at the center.
        assertTrue("r=2 center=$small vs r=10 center=$big", big < small)
    }

    @Test
    fun degenerateInputsDoNotThrow() {
        FastBlur.stackBlurPixels(IntArray(0), 0, 0, 5)
        FastBlur.stackBlurPixels(IntArray(4), 1, 1, 5)
        FastBlur.stackBlurPixels(IntArray(4) { 0xFFFFFFFF.toInt() }, 2, 2, -3)
    }

    @Test
    fun blurConservesEnergyRoughly() {
        // Sum of channels shouldn't drift far (stack blur is near-energy-preserving).
        val w = 20
        val h = 20
        val pix = IntArray(w * h) { i -> 0xFF000000.toInt() or ((i * 37) % 256 shl 16) or ((i * 91) % 256) }
        fun sum(a: IntArray): Long {
            var s = 0L
            for (p in a) s += ((p shr 16) and 0xFF) + (p and 0xFF)
            return s
        }
        val before = sum(pix).toDouble()
        FastBlur.stackBlurPixels(pix, w, h, 4)
        val after = sum(pix).toDouble()
        assertTrue("energy drift: $before -> $after", abs(after - before) / before < 0.05)
    }
}
