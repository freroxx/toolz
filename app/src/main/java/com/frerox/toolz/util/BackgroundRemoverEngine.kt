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
 * Studio-Grade High-Precision Background Removal Engine.
 *
 * Designed for complex subjects (curly hair, fine strands, earphones, glass).
 *
 * Pipeline:
 *  1. Smooth Continuous Bilinear Upsampling: Converts 256x256 raw mask to full resolution
 *     without any blocky grid artifacts.
 *  2. High-Resolution Guided Filter Matting: Transfers physical hair strand textures
 *     directly from the high-res RGB image to the alpha channel.
 *  3. Local Color-Distance Trimap Refinement: Evaluates local foreground (hair/skin)
 *     vs background color statistics to separate fine curls and background gaps.
 *  4. Foreground Color Decontamination: Replaces edge RGB with pure local foreground color,
 *     eliminating background color spill entirely.
 *  5. Anti-Aliased Alpha Contrast Ramp: Clamps noise (<0.06) to 0 and solid (>0.90) to 1.
 */
object BackgroundRemoverEngine {

    private const val GUIDED_RADIUS = 9
    private const val GUIDED_EPSILON = 1e-4f
    private const val DECONTAM_RADIUS = 10

    suspend fun removeBackground(
        source: Bitmap,
        maskArray: FloatArray,
        maskW: Int,
        maskH: Int,
    ): Bitmap = withContext(Dispatchers.Default) {

        val width = source.width
        val height = source.height

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Smooth Bilinear Upsampling (Eliminates blocky grid artifacts)
        var alpha = bilinearUpsample(maskArray, maskW, maskH, width, height)

        // 2. High-Resolution RGB Guided Filter Matting (Snaps alpha to physical hair edges)
        alpha = guidedFilterMatting(alpha, pixels, width, height, GUIDED_RADIUS, GUIDED_EPSILON)

        // 3. Local Color-Distance Hair & Complex Detail Refinement
        alpha = refineHairDetailWithColorStats(alpha, pixels, width, height, radius = 7)

        // 4. Foreground Color Decontamination (Zero background color bleeding)
        val finalPixels = decontaminateEdges(pixels, alpha, width, height, DECONTAM_RADIUS)

        // 5. Anti-Aliased Alpha Contrast Finalization
        for (i in finalPixels.indices) {
            val rawA = alpha[i]
            val a = when {
                rawA < 0.06f -> 0f
                rawA > 0.90f -> 1f
                else -> {
                    val t = (rawA - 0.06f) / (0.90f - 0.06f)
                    t * t * (3f - 2f * t)
                }
            }
            val alphaInt = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
            finalPixels[i] = (alphaInt shl 24) or (finalPixels[i] and 0x00FFFFFF)
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(finalPixels, 0, width, 0, 0, width, height)
        output
    }

    // -----------------------------------------------------------------------
    // Step 1: Smooth Bilinear Upsampling
    // -----------------------------------------------------------------------

    private fun bilinearUpsample(
        mask: FloatArray, maskW: Int, maskH: Int, w: Int, h: Int
    ): FloatArray {
        val out = FloatArray(w * h)
        val scaleX = (maskW - 1).toFloat() / w
        val scaleY = (maskH - 1).toFloat() / h

        for (y in 0 until h) {
            val my = y * scaleY
            val y1 = my.toInt()
            val y2 = (y1 + 1).coerceAtMost(maskH - 1)
            val dy = my - y1
            val row1 = y1 * maskW
            val row2 = y2 * maskW
            val outRow = y * w

            for (x in 0 until w) {
                val mx = x * scaleX
                val x1 = mx.toInt()
                val x2 = (x1 + 1).coerceAtMost(maskW - 1)
                val dx = mx - x1

                val v11 = mask[row1 + x1]
                val v21 = mask[row1 + x2]
                val v12 = mask[row2 + x1]
                val v22 = mask[row2 + x2]

                out[outRow + x] = (v11 * (1f - dx) * (1f - dy) +
                                   v21 * dx * (1f - dy) +
                                   v12 * (1f - dx) * dy +
                                   v22 * dx * dy).coerceIn(0f, 1f)
            }
        }
        return out
    }

    // -----------------------------------------------------------------------
    // Step 2: High-Resolution RGB Guided Filter Matting
    // -----------------------------------------------------------------------

    private suspend fun guidedFilterMatting(
        p: FloatArray, guide: IntArray, w: Int, h: Int, r: Int, eps: Float
    ): FloatArray = withContext(Dispatchers.Default) {
        val out = p.copyOf()
        val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val stripHeight = (h + cpuCount - 1) / cpuCount

        // Compute local linear transform coefficients a, b in parallel
        (0 until cpuCount).map { strip ->
            async {
                val yStart = strip * stripHeight
                val yEnd = min(yStart + stripHeight, h)

                for (y in yStart until yEnd) {
                    val yS = max(0, y - r); val yE = min(h - 1, y + r)
                    val rowOffset = y * w

                    for (x in 0 until w) {
                        val idx = rowOffset + x
                        val pVal = p[idx]
                        if (pVal <= 0.02f || pVal >= 0.98f) continue

                        val xS = max(0, x - r); val xE = min(w - 1, x + r)

                        // Calculate mean and covariance in local window
                        var meanI_r = 0.0; var meanI_g = 0.0; var meanI_b = 0.0
                        var meanP = 0.0
                        var varI = 0.0
                        var covIP = 0.0
                        var count = 0

                        val centerC = guide[idx]
                        val cR = (centerC shr 16) and 0xFF
                        val cG = (centerC shr 8) and 0xFF
                        val cB = centerC and 0xFF
                        val cLum = 0.299f * cR + 0.587f * cG + 0.114f * cB

                        for (ny in yS..yE) {
                            val nRow = ny * w
                            for (nx in xS..xE) {
                                val nIdx = nRow + nx
                                val gc = guide[nIdx]
                                val rVal = (gc shr 16) and 0xFF
                                val gVal = (gc shr 8) and 0xFF
                                val bVal = gc and 0xFF
                                val lum = 0.299f * rVal + 0.587f * gVal + 0.114f * bVal

                                val gP = p[nIdx]

                                meanP += gP
                                meanI_r += lum
                                count++
                            }
                        }

                        if (count > 0) {
                            meanP /= count
                            meanI_r /= count

                            for (ny in yS..yE) {
                                val nRow = ny * w
                                for (nx in xS..xE) {
                                    val nIdx = nRow + nx
                                    val gc = guide[nIdx]
                                    val rVal = (gc shr 16) and 0xFF
                                    val gVal = (gc shr 8) and 0xFF
                                    val bVal = gc and 0xFF
                                    val lum = 0.299f * rVal + 0.587f * gVal + 0.114f * bVal

                                    val diffI = lum - meanI_r
                                    val diffP = p[nIdx] - meanP
                                    varI += diffI * diffI
                                    covIP += diffI * diffP
                                }
                            }

                            varI /= count
                            covIP /= count

                            val a = covIP / (varI + eps * 255.0 * 255.0)
                            val b = meanP - a * meanI_r
                            out[idx] = (a * cLum + b).toFloat().coerceIn(0f, 1f)
                        }
                    }
                }
            }
        }.awaitAll()
        out
    }

    // -----------------------------------------------------------------------
    // Step 3: Local Color Statistics Hair & Detail Refinement
    // -----------------------------------------------------------------------

    private fun refineHairDetailWithColorStats(
        alpha: FloatArray, guide: IntArray, w: Int, h: Int, radius: Int
    ): FloatArray {
        val out = alpha.copyOf()
        for (y in 0 until h) {
            val yS = max(0, y - radius); val yE = min(h - 1, y + radius)
            val rowOffset = y * w

            for (x in 0 until w) {
                val idx = rowOffset + x
                val a = alpha[idx]
                if (a <= 0.08f || a >= 0.92f) continue

                val xS = max(0, x - radius); val xE = min(w - 1, x + radius)

                var fgR = 0.0; var fgG = 0.0; var fgB = 0.0; var fgW = 0.0
                var bgR = 0.0; var bgG = 0.0; var bgB = 0.0; var bgW = 0.0

                for (ny in yS..yE) {
                    val nRow = ny * w
                    for (nx in xS..xE) {
                        val nIdx = nRow + nx
                        val nA = alpha[nIdx]
                        val c = guide[nIdx]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF

                        if (nA > 0.85f) {
                            fgR += r; fgG += g; fgB += b; fgW += 1.0
                        } else if (nA < 0.15f) {
                            bgR += r; bgG += g; bgB += b; bgW += 1.0
                        }
                    }
                }

                if (fgW > 0.0 && bgW > 0.0) {
                    val meanFgR = fgR / fgW; val meanFgG = fgG / fgW; val meanFgB = fgB / fgW
                    val meanBgR = bgR / bgW; val meanBgG = bgG / bgW; val meanBgB = bgB / bgW

                    val currC = guide[idx]
                    val currR = ((currC shr 16) and 0xFF).toDouble()
                    val currG = ((currC shr 8) and 0xFF).toDouble()
                    val currB = (currC and 0xFF).toDouble()

                    val distFgSq = (currR - meanFgR) * (currR - meanFgR) +
                                   (currG - meanFgG) * (currG - meanFgG) +
                                   (currB - meanFgB) * (currB - meanFgB)

                    val distBgSq = (currR - meanBgR) * (currR - meanBgR) +
                                   (currG - meanBgG) * (currG - meanBgG) +
                                   (currB - meanBgB) * (currB - meanBgB)

                    val colorProb = (distBgSq / (distFgSq + distBgSq + 1e-5)).toFloat().coerceIn(0f, 1f)

                    // Blend Guided Alpha with Color Statistic Probability
                    out[idx] = (0.55f * a + 0.45f * colorProb).coerceIn(0f, 1f)
                }
            }
        }
        return out
    }

    // -----------------------------------------------------------------------
    // Step 4: Foreground Color Decontamination
    // -----------------------------------------------------------------------

    private fun decontaminateEdges(
        pixels: IntArray, alpha: FloatArray, w: Int, h: Int, r: Int
    ): IntArray {
        val out = pixels.copyOf()
        for (y in 0 until h) {
            val yS = max(0, y - r); val yE = min(h - 1, y + r)
            val rowOffset = y * w

            for (x in 0 until w) {
                val idx = rowOffset + x
                val a = alpha[idx]
                if (a <= 0.05f || a >= 0.95f) continue

                val xS = max(0, x - r); val xE = min(w - 1, x + r)

                var fgR = 0.0; var fgG = 0.0; var fgB = 0.0
                var fgW = 0.0

                for (ny in yS..yE) {
                    val nRow = ny * w
                    for (nx in xS..xE) {
                        val nIdx = nRow + nx
                        if (alpha[nIdx] > 0.85f) {
                            val distSq = ((nx - x) * (nx - x) + (ny - y) * (ny - y)).toDouble()
                            val weight = 1.0 / (distSq + 1.0)
                            val c = pixels[nIdx]
                            fgR += weight * ((c shr 16) and 0xFF)
                            fgG += weight * ((c shr 8) and 0xFF)
                            fgB += weight * (c and 0xFF)
                            fgW += weight
                        }
                    }
                }

                if (fgW > 0.0) {
                    val pureFgR = (fgR / fgW).toInt().coerceIn(0, 255)
                    val pureFgG = (fgG / fgW).toInt().coerceIn(0, 255)
                    val pureFgB = (fgB / fgW).toInt().coerceIn(0, 255)
                    out[idx] = (out[idx] and 0xFF000000.toInt()) or (pureFgR shl 16) or (pureFgG shl 8) or pureFgB
                }
            }
        }
        return out
    }
}
