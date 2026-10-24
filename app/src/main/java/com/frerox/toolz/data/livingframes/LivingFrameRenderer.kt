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

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.roundToInt

/**
 * One renderer for every widget bitmap. It draws straight to the widget's
 * exact pixel size, so rounded corners, fit/fill and opacity land exactly
 * where the user sees them — no ImageView cropping afterwards.
 */
object LivingFrameRenderer {

    fun render(
        src: Bitmap,
        frame: LivingFrame,
        outW: Int,
        outH: Int,
        density: Float,
        rgb565: Boolean
    ): Bitmap {
        val w = outW.coerceAtLeast(2)
        val h = outH.coerceAtLeast(2)
        val config = if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        val out = Bitmap.createBitmap(w, h, config)
        // A fully covered opaque output lets the host skip alpha blending.
        if (!rgb565 && isOpaque(frame)) out.setHasAlpha(false)
        val canvas = Canvas(out)

        val dst = if (frame.fit == LivingFrameFit.FIT) {
            val s = minOf(w / src.width.toFloat(), h / src.height.toFloat())
            val dw = src.width * s
            val dh = src.height * s
            RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
        } else {
            RectF(0f, 0f, w.toFloat(), h.toFloat())
        }

        // Map the source onto dst: centre-crop for FILL, exact for FIT.
        val srcRect = if (frame.fit == LivingFrameFit.FIT) {
            RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        } else {
            val sa = src.width / src.height.toFloat()
            val da = w / h.toFloat()
            if (sa > da) {
                val cw = src.height * da
                val l = (src.width - cw) / 2f
                RectF(l, 0f, l + cw, src.height.toFloat())
            } else {
                val ch = src.width / da
                val t = (src.height - ch) / 2f
                RectF(0f, t, src.width.toFloat(), t + ch)
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = rgb565
            alpha = (frame.opacity.coerceIn(0f, 1f) * 255f).toInt()
        }
        val radius = (frame.cornerRadiusDp * density)
            .coerceIn(0f, minOf(dst.width(), dst.height()) / 2f)

        if (radius < 0.5f) {
            // Square corners: a plain blit is much cheaper than a shader
            // round-rect and is the common case for video loops.
            val crop = Rect(
                srcRect.left.roundToInt().coerceIn(0, src.width - 1),
                srcRect.top.roundToInt().coerceIn(0, src.height - 1),
                srcRect.right.roundToInt().coerceIn(1, src.width),
                srcRect.bottom.roundToInt().coerceIn(1, src.height)
            )
            canvas.drawBitmap(src, crop, dst, paint)
        } else {
            val matrix = Matrix().apply { setRectToRect(srcRect, dst, Matrix.ScaleToFit.FILL) }
            paint.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                .also { it.setLocalMatrix(matrix) }
            canvas.drawRoundRect(dst, radius, radius, paint)
        }
        return out
    }

    /**
     * Only decoded video frames are guaranteed to have no alpha channel.
     * Photos, slideshows and GIFs may carry transparent pixels, so they always
     * get an ARGB output even when their corners/letterbox are fully filled.
     */
    fun isOpaque(frame: LivingFrame): Boolean =
        frame.type == LivingFrameType.VIDEO &&
                frame.cornerRadiusDp == 0 &&
                frame.opacity >= 0.999f &&
                frame.fit == LivingFrameFit.FILL
}