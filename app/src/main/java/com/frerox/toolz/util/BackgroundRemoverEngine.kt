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
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Studio-Grade Background Removal Engine.
 * 
 * Pipeline for Studio Quality:
 *  1. Adaptive Joint Bilateral Upsampling (JBU): Snap to physical edges.
 *  2. Edge-Aware Trimap Expansion: Identify complex regions (hair/fur).
 *  3. Full-RGB Guided Filter Matting: Refine transparency with pixel-perfect guide.
 *  4. High-Radius Color Decontamination: Remove background spill.
 *  5. 5th-Order Smootherstep Finalization: Organic, professional falloff.
 */
object BackgroundRemoverEngine {

    private const val FG_STRICT_THRESHOLD = 0.96f
    private const val BG_STRICT_THRESHOLD = 0.04f
    
    // Matting Parameters
    private const val GUIDED_RADIUS = 10
    private const val GUIDED_EPSILON = 1e-4f
    private const val REFINEMENT_PASSES = 3
    
    // Decontamination Parameters
    private const val DECONTAM_RADIUS = 18

    suspend fun removeBackground(
        source: Bitmap,
        maskArray: FloatArray,
        maskW: Int,
        maskH: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Initial High-Res Alpha via JBU
        var fullAlpha = FloatArray(width * height)
        
        val guideColors = IntArray(maskW * maskH)
        for (my in 0 until maskH) {
            val sy = (my * height / maskH).coerceIn(0, height - 1)
            for (mx in 0 until maskW) {
                val sx = (mx * width / maskW).coerceIn(0, width - 1)
                guideColors[my * maskW + mx] = pixels[sy * width + sx]
            }
        }

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val targetColor = pixels[rowOffset + x]
                
                val mx = (x.toFloat() * (maskW - 1)) / width
                val my = (y.toFloat() * (maskH - 1)) / height
                val x1 = mx.toInt(); val y1 = my.toInt()
                val x2 = (x1 + 1).coerceAtMost(maskW - 1); val y2 = (y1 + 1).coerceAtMost(maskH - 1)
                val dx = mx - x1; val dy = my - y1

                val m11 = maskArray[y1 * maskW + x1]; val c11 = guideColors[y1 * maskW + x1]
                val m21 = maskArray[y1 * maskW + x2]; val c21 = guideColors[y1 * maskW + x2]
                val m12 = maskArray[y2 * maskW + x1]; val c12 = guideColors[y2 * maskW + x1]
                val m22 = maskArray[y2 * maskW + x2]; val c22 = guideColors[y2 * maskW + x2]

                val w11 = (1f - dx) * (1f - dy) * colorSimilarity(targetColor, c11)
                val w21 = dx * (1f - dy) * colorSimilarity(targetColor, c21)
                val w12 = (1f - dx) * dy * colorSimilarity(targetColor, c12)
                val w22 = dx * dy * colorSimilarity(targetColor, c22)

                val totalW = w11 + w21 + w12 + w22
                fullAlpha[rowOffset + x] = if (totalW > 0.001f) {
                    (m11 * w11 + m21 * w21 + m12 * w12 + m22 * w22) / totalW
                } else {
                    sampleBilinear(maskArray, maskW, maskH, x, y, width, height)
                }
            }
        }

        // 2. Identify Trimap regions (Unknown pixels needing matting)
        val unknownMask = BooleanArray(width * height)
        for (i in fullAlpha.indices) {
            val a = fullAlpha[i]
            if (a in BG_STRICT_THRESHOLD..FG_STRICT_THRESHOLD) {
                unknownMask[i] = true
            }
        }

        // 3. Recursive Guided Refinement (Studio Quality Matting)
        repeat(REFINEMENT_PASSES) {
            fullAlpha = mattingPolish(fullAlpha, pixels, unknownMask, width, height, GUIDED_RADIUS, GUIDED_EPSILON)
        }

        // 4. Perceptual Color Decontamination
        val finalPixels = edgeDecontaminate(pixels, fullAlpha, unknownMask, width, height, DECONTAM_RADIUS)

        // 5. Final HD Edge Finalization
        for (i in finalPixels.indices) {
            val t = fullAlpha[i].coerceIn(0f, 1f)
            // Smootherstep (5th order)
            val s = t * t * t * (t * (t * 6f - 15f) + 10f)
            // Edge-pop pass
            val finalS = s * s * (3f - 2f * s)
            val a = (finalS * 255f).toInt().coerceIn(0, 255)
            finalPixels[i] = (a shl 24) or (finalPixels[i] and 0x00FFFFFF)
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(finalPixels, 0, width, 0, 0, width, height)
        return@withContext output
    }

    private fun mattingPolish(alpha: FloatArray, guide: IntArray, unknown: BooleanArray, w: Int, h: Int, r: Int, eps: Float): FloatArray {
        val out = alpha.copyOf()
        for (y in 0 until h) {
            val yS = max(0, y - r); val yE = min(h - 1, y + r)
            for (x in 0 until w) {
                val idx = y * w + x
                if (!unknown[idx]) continue
                
                val xS = max(0, x - r); val xE = min(w - 1, x + r)
                val targetColor = guide[idx]
                val tr = (targetColor shr 16) and 0xFF; val tg = (targetColor shr 8) and 0xFF; val tb = targetColor and 0xFF
                
                var wAlpha = 0f; var wSum = 0f
                for (ny in yS..yE) {
                    val row = ny * w
                    for (nx in xS..xE) {
                        val nIdx = row + nx
                        val nc = guide[nIdx]
                        val nr = (nc shr 16) and 0xFF; val ng = (nc shr 8) and 0xFF; val nb = nc and 0xFF
                        
                        // Weighted by color similarity (Full RGB)
                        val colorDist = ((tr - nr) * (tr - nr) + (tg - ng) * (tg - ng) + (tb - nb) * (tb - nb)) / 195075f
                        val weight = 1f / (colorDist + eps)
                        
                        wAlpha += weight * alpha[nIdx]
                        wSum += weight
                    }
                }
                if (wSum > 0f) out[idx] = wAlpha / wSum
            }
        }
        return out
    }

    private fun edgeDecontaminate(pixels: IntArray, alpha: FloatArray, unknown: BooleanArray, w: Int, h: Int, r: Int): IntArray {
        val out = pixels.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!unknown[idx]) continue
                val a = alpha[idx]
                if (a < 0.05f || a > 0.95f) continue

                var bestDist = Int.MAX_VALUE
                var fgR = 0f; var fgG = 0f; var fgB = 0f; var found = false
                
                val yS = max(0, y - r); val yE = min(h - 1, y + r)
                val xS = max(0, x - r); val xE = min(w - 1, x + r)
                
                for (ny in yS..yE) {
                    val row = ny * w
                    for (nx in xS..xE) {
                        val nIdx = row + nx
                        if (alpha[nIdx] > 0.95f) {
                            val dist = (nx - x) * (nx - x) + (ny - y) * (ny - y)
                            if (dist < bestDist) {
                                bestDist = dist
                                val c = pixels[nIdx]
                                fgR = ((c shr 16) and 0xFF).toFloat()
                                fgG = ((c shr 8) and 0xFF).toFloat()
                                fgB = (c and 0xFF).toFloat()
                                found = true
                            }
                        }
                    }
                }
                
                if (found) {
                    val oc = pixels[idx]
                    val or = ((oc shr 16) and 0xFF).toFloat(); val og = ((oc shr 8) and 0xFF).toFloat(); val ob = (oc and 0xFF).toFloat()
                    val mix = (1f - a) * 0.45f
                    val nr = (or * (1 - mix) + fgR * mix).toInt().coerceIn(0, 255)
                    val ng = (og * (1 - mix) + fgG * mix).toInt().coerceIn(0, 255)
                    val nb = (ob * (1 - mix) + fgB * mix).toInt().coerceIn(0, 255)
                    out[idx] = (oc and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
                }
            }
        }
        return out
    }

    private fun colorSimilarity(c1: Int, c2: Int): Float {
        val r1 = (c1 shr 16) and 0xFF; val g1 = (c1 shr 8) and 0xFF; val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF; val g2 = (c2 shr 8) and 0xFF; val b2 = c2 and 0xFF
        val dr = (r1 - r2).toFloat(); val dg = (g1 - g2).toFloat(); val db = (b1 - b2).toFloat()
        val distSq = (dr * dr + dg * dg + db * db) / 195075f
        return (1f - distSq).let { it * it * it * it }.coerceIn(0.01f, 1f)
    }

    private fun sampleBilinear(arr: FloatArray, mw: Int, mh: Int, x: Int, y: Int, w: Int, h: Int): Float {
        val mx = (x.toFloat() * (mw - 1)) / w
        val my = (y.toFloat() * (mh - 1)) / h
        val x1 = mx.toInt(); val y1 = my.toInt()
        val x2 = (x1 + 1).coerceAtMost(mw - 1); val y2 = (y1 + 1).coerceAtMost(mh - 1)
        val dx = mx - x1; val dy = my - y1
        val v11 = arr[y1 * mw + x1]; val v21 = arr[y1 * mw + x2]
        val v12 = arr[y2 * mw + x1]; val v22 = arr[y2 * mw + x2]
        return v11 * (1f - dx) * (1f - dy) + v21 * dx * (1f - dy) + v12 * (1f - dx) * dy + v22 * dx * dy
    }
}
