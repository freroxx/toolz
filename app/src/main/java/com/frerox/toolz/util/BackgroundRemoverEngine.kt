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

package com.frerox.toolz.util

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-Precision Sub-Pixel Alpha Matting & Edge Refinement Engine.
 *
 * Combines neural segmentation confidence with edge-guided color matting:
 *  1. Sub-pixel Bilinear Upsampling — High-fidelity mask resolution match.
 *  2. Edge-Guided Lum Refinement   — Guided filter snaps soft mask edges to physical object boundaries.
 *  3. Edge Color Decontamination   — Strips background color spill from boundary pixels.
 *  4. Smooth Alpha Ramp            — Eliminates noise floor without harsh stair-stepping or AI slop artifacts.
 */
object BackgroundRemoverEngine {

    // --- Guided Filter parameters ------------------------------------------
    private const val GF_RADIUS = 8     // Spatial radius
    private const val GF_EPS    = 1e-4f  // Edge regularisation

    private const val GF_RADIUS2 = 3
    private const val GF_EPS2    = 1e-5f

    // --- Colour decontamination radius -------------------------------------
    private const val DECONTAM_RADIUS = 6

    suspend fun removeBackground(
        source: Bitmap,
        maskArray: FloatArray,
        maskW: Int,
        maskH: Int,
    ): Bitmap = withContext(Dispatchers.Default) {

        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // Calculate resolution scaling factor for adaptive refinement
        val resScale = max(w, h).toFloat() / 1024f
        val adaptiveGfRadius = (GF_RADIUS * resScale).toInt().coerceIn(3, 20)
        val adaptiveGfRadius2 = (GF_RADIUS2 * resScale).toInt().coerceIn(2, 10)
        val adaptiveDecontamRadius = (DECONTAM_RADIUS * resScale).toInt().coerceIn(3, 12)

        // 1. Bilinear upsample to native image resolution
        var alpha = bilinearUpsample(maskArray, maskW, maskH, w, h)

        // 2. First guided filter pass — structure-guided edge snapping
        alpha = guidedFilterPass(alpha, pixels, w, h, adaptiveGfRadius, GF_EPS)

        // 3. Second guided filter pass — fine detail & hair strand snapping
        alpha = guidedFilterPass(alpha, pixels, w, h, adaptiveGfRadius2, GF_EPS2)

        // 4. Sub-pixel edge refinement based on luminance gradient
        alpha = refineEdgeGradients(alpha, pixels, w, h)

        // 5. Foreground colour decontamination (eliminates background color bleed)
        val finalPixels = decontaminateEdges(pixels, alpha, w, h, adaptiveDecontamRadius)

        // 6. Natural alpha ramp: clear background noise (< 0.05) and preserve smooth subject transparency
        val outputPixels = IntArray(w * h)
        for (i in finalPixels.indices) {
            val rawA = alpha[i]
            val a = when {
                rawA <= 0.04f -> 0f   // Pure background -> 100% transparent
                rawA >= 0.96f -> 1f   // Pure subject -> 100% solid
                else -> {
                    // Smooth cubic interpolation between 0.04 and 0.96
                    val t = (rawA - 0.04f) / (0.96f - 0.04f)
                    t * t * (3f - 2f * t)
                }
            }
            val alphaInt = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
            outputPixels[i] = (alphaInt shl 24) or (finalPixels[i] and 0x00FFFFFF)
        }

        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(outputPixels, 0, w, 0, 0, w, h)
        }
    }

    private fun bilinearUpsample(
        mask: FloatArray, maskW: Int, maskH: Int, w: Int, h: Int,
    ): FloatArray {
        val out = FloatArray(w * h)
        val scaleX = (maskW - 1).toFloat() / (w - 1).coerceAtLeast(1)
        val scaleY = (maskH - 1).toFloat() / (h - 1).coerceAtLeast(1)
        for (y in 0 until h) {
            val my = y * scaleY
            val y1 = my.toInt(); val y2 = (y1 + 1).coerceAtMost(maskH - 1)
            val dy = my - y1; val idy = 1f - dy
            val row1 = y1 * maskW; val row2 = y2 * maskW
            val outRow = y * w
            for (x in 0 until w) {
                val mx = x * scaleX
                val x1 = mx.toInt(); val x2 = (x1 + 1).coerceAtMost(maskW - 1)
                val dx = mx - x1; val idx2 = 1f - dx
                out[outRow + x] = (
                    mask[row1 + x1] * idx2 * idy +
                    mask[row1 + x2] * dx  * idy +
                    mask[row2 + x1] * idx2 * dy  +
                    mask[row2 + x2] * dx  * dy
                ).coerceIn(0f, 1f)
            }
        }
        return out
    }

    private suspend fun guidedFilterPass(
        p: FloatArray, guide: IntArray, w: Int, h: Int, r: Int, eps: Float,
    ): FloatArray = withContext(Dispatchers.Default) {

        val n = w * h
        val lum = FloatArray(n) { i ->
            val c = guide[i]
            (0.299f * ((c shr 16) and 0xFF) +
             0.587f * ((c shr  8) and 0xFF) +
             0.114f * ( c          and 0xFF)) / 255f
        }

        val out = p.copyOf()
        val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val stripH   = (h + cpuCount - 1) / cpuCount

        (0 until cpuCount).map { strip ->
            async {
                val yStart = strip * stripH
                val yEnd   = min(yStart + stripH, h)
                for (y in yStart until yEnd) {
                    for (x in 0 until w) {
                        val idx = y * w + x
                        val pVal = p[idx]
                        if (pVal < 0.03f || pVal > 0.97f) continue

                        val ys = max(0, y - r); val ye = min(h - 1, y + r)
                        val xs = max(0, x - r); val xe = min(w - 1, x + r)
                        var cnt = 0; var sumI = 0.0; var sumP = 0.0
                        var sumI2 = 0.0; var sumIP = 0.0

                        for (ny in ys..ye) {
                            val nRow = ny * w
                            for (nx in xs..xe) {
                                val nIdx = nRow + nx
                                val I = lum[nIdx].toDouble()
                                val P = p[nIdx].toDouble()
                                sumI  += I; sumP  += P
                                sumI2 += I * I; sumIP += I * P
                                cnt++
                            }
                        }

                        val meanI = sumI / cnt; val meanP = sumP / cnt
                        val varI  = sumI2 / cnt - meanI * meanI
                        val covIP = sumIP / cnt - meanI * meanP

                        val a = covIP / (varI + eps)
                        val b = meanP - a * meanI
                        out[idx] = (a * lum[idx] + b).toFloat().coerceIn(0f, 1f)
                    }
                }
            }
        }.awaitAll()
        out
    }

    private suspend fun refineEdgeGradients(
        alpha: FloatArray, guide: IntArray, w: Int, h: Int,
    ): FloatArray = withContext(Dispatchers.Default) {
        val out = alpha.copyOf()
        val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val stripH = (h + cpuCount - 1) / cpuCount

        (0 until cpuCount).map { strip ->
            async {
                val yStart = max(1, strip * stripH)
                val yEnd   = min(h - 1, yStart + stripH)

                for (y in yStart until yEnd) {
                    for (x in 1 until (w - 1)) {
                        val idx = y * w + x
                        val aVal = alpha[idx]
                        if (aVal <= 0.05f || aVal >= 0.95f) continue

                        fun getLum(px: Int, py: Int): Float {
                            val c = guide[py * w + px]
                            return (0.299f * ((c shr 16) and 0xFF) +
                                    0.587f * ((c shr  8) and 0xFF) +
                                    0.114f * ( c          and 0xFF)) / 255f
                        }

                        val l00 = getLum(x - 1, y - 1); val l01 = getLum(x, y - 1); val l02 = getLum(x + 1, y - 1)
                        val l10 = getLum(x - 1, y);                                 val l12 = getLum(x + 1, y)
                        val l20 = getLum(x - 1, y + 1); val l21 = getLum(x, y + 1); val l22 = getLum(x + 1, y + 1)

                        val gx = (l02 + 2f * l12 + l22) - (l00 + 2f * l10 + l20)
                        val gy = (l20 + 2f * l21 + l22) - (l00 + 2f * l01 + l02)
                        val gradMag = sqrt(gx * gx + gy * gy)

                        if (gradMag > 0.12f) {
                            val factor = min(gradMag * 1.5f, 0.8f)
                            val refined = if (aVal > 0.50f) {
                                aVal + (1.0f - aVal) * factor * 0.3f
                            } else {
                                aVal - aVal * factor * 0.3f
                            }
                            out[idx] = refined.coerceIn(0f, 1f)
                        }
                    }
                }
            }
        }.awaitAll()
        out
    }

    private fun decontaminateEdges(
        pixels: IntArray, alpha: FloatArray, w: Int, h: Int, r: Int,
    ): IntArray {
        val out = pixels.copyOf()
        for (y in 0 until h) {
            val ys = max(0, y - r); val ye = min(h - 1, y + r)
            val rowOffset = y * w
            for (x in 0 until w) {
                val idx = rowOffset + x
                val a = alpha[idx]
                if (a <= 0.08f || a >= 0.92f) continue

                val xs = max(0, x - r); val xe = min(w - 1, x + r)
                var fgR = 0.0; var fgG = 0.0; var fgB = 0.0; var fgW = 0.0

                for (ny in ys..ye) {
                    val nRow = ny * w
                    for (nx in xs..xe) {
                        val nIdx = nRow + nx
                        if (alpha[nIdx] > 0.85f) {
                            val d = (nx - x) * (nx - x) + (ny - y) * (ny - y)
                            val wt = 1.0 / (d + 1.0)
                            val c = pixels[nIdx]
                            fgR += wt * ((c shr 16) and 0xFF)
                            fgG += wt * ((c shr  8) and 0xFF)
                            fgB += wt * ( c          and 0xFF)
                            fgW += wt
                        }
                    }
                }

                if (fgW > 0.0) {
                    val pr = (fgR / fgW).toInt().coerceIn(0, 255)
                    val pg = (fgG / fgW).toInt().coerceIn(0, 255)
                    val pb = (fgB / fgW).toInt().coerceIn(0, 255)
                    out[idx] = (out[idx] and 0xFF000000.toInt()) or (pr shl 16) or (pg shl 8) or pb
                }
            }
        }
        return out
    }
}
