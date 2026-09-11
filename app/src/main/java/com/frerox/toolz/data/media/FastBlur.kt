/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.data.media

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Fast, high-quality blur for background-removal backdrops.
 *
 * Single bilinear downscale-upscale (the old approach) leaves blotchy bands on
 * gradients. Stack blur is O(w·h) regardless of radius and looks properly
 * gaussian — run on a capped bitmap, it costs ~50 ms and ~4 MB transient.
 *
 * The pixel core ([stackBlurPixels]) is pure Kotlin so unit tests can pin it;
 * the Bitmap wrappers are thin I/O shells.
 */
object FastBlur {

    /**
     * Preview blur: downscale to [maxEdge], stack-blur, return at capped size.
     * Callers displaying via Compose must NOT upscale — ContentScale fits it.
     */
    fun blurredPreview(src: Bitmap, maxEdge: Int = 512, radius: Int = 18): Bitmap? {
        return try {
            if (src.isRecycled || radius < 1) return null
            val base = cappedCopy(src, maxEdge) ?: return null
            val w = base.width
            val h = base.height
            val pix = IntArray(w * h)
            base.getPixels(pix, 0, w, 0, 0, w, h)
            if (base !== src) base.recycle()
            stackBlurPixels(pix, w, h, radius)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                it.setPixels(pix, 0, w, 0, 0, w, h)
            }
        } catch (_: Throwable) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /**
     * Export backdrop: blur small, upscale smooth to full res. A 12 MP photo
     * never sees a full-res blur kernel — peak transient stays ~640px.
     */
    fun blurredBackdrop(src: Bitmap, workEdge: Int = 640, radius: Int = 25): Bitmap? {
        return try {
            if (src.isRecycled || radius < 1) return null
            val small = blurredPreview(src, workEdge, radius) ?: return null
            Bitmap.createScaledBitmap(small, src.width, src.height, true).also {
                if (small != it) small.recycle()
            }
        } catch (_: Throwable) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Uniform downscale copy preserving aspect; null on failure. */
    private fun cappedCopy(src: Bitmap, maxEdge: Int): Bitmap? {
        return try {
            val edge = max(src.width, src.height)
            if (edge <= maxEdge) {
                Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).also {
                    android.graphics.Canvas(it).drawBitmap(src, 0f, 0f, null)
                }
            } else {
                val s = maxEdge.toFloat() / edge
                Bitmap.createScaledBitmap(
                    src,
                    (src.width * s).toInt().coerceAtLeast(1),
                    (src.height * s).toInt().coerceAtLeast(1),
                    true,
                )
            }
        } catch (_: Throwable) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /**
     * Stack blur in place on ARGB [pix] (row-major w×h). O(w·h), radius-independent.
     * Alpha channel is preserved per-pixel.
     */
    fun stackBlurPixels(pix: IntArray, w: Int, h: Int, radius: Int) {
        if (radius < 1 || pix.size < w * h || w < 2 || h < 2) return
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        val vmin = IntArray(max(w, h))
        var divsum = div + 1 shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum) { it / divsum }

        var yw = 0
        var yi = 0
        val stack = Array(div) { IntArray(3) }
        val r1 = radius + 1

        // ── Horizontal pass ──
        var y = 0
        while (y < h) {
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var i = -radius
            while (i <= radius) {
                val p = pix[yi + min(wm, max(i, 0))]
                val sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                val rbs = r1 - kotlin.math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            var stackpointer = radius
            var x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                var stackstart = stackpointer - radius + div
                var sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (y == 0) vmin[x] = min(x + radius + 1, wm)
                val p = pix[yw + vmin[x]]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi++
                x++
            }
            yw += w
            y++
        }

        // ── Vertical pass ──
        var x = 0
        while (x < w) {
            var rinsum = 0
            var ginsum = 0
            var binsum = 0
            var routsum = 0
            var goutsum = 0
            var boutsum = 0
            var rsum = 0
            var gsum = 0
            var bsum = 0
            var yp = -radius * w
            var i = -radius
            while (i <= radius) {
                yi = max(0, yp) + x
                val sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                val rbs = r1 - kotlin.math.abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) yp += w
                i++
            }
            yi = x
            var stackpointer = radius
            y = 0
            while (y < h) {
                val alpha = pix[yi] and 0xff000000.toInt()
                pix[yi] = alpha or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                var stackstart = stackpointer - radius + div
                var sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (x == 0) vmin[y] = min(y + r1, hm) * w
                val p = x + vmin[y]
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi += w
                y++
            }
            x++
        }
    }
}
