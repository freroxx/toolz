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
 *
 * [densityX]/[densityY] must already include `outPx / widgetPx`
 * ([LivingFrameEngine.densityScale]): the launcher scales the bitmap back to
 * widget size, and the baked corner radius has to be scaled by the same
 * amount. Passing the raw display density made corners grow with the render
 * resolution.
 */
object LivingFrameRenderer {

    fun render(
        src: Bitmap,
        frame: LivingFrame,
        outW: Int,
        outH: Int,
        densityX: Float,
        densityY: Float,
        rgb565: Boolean
    ): Bitmap {
        val w = outW.coerceAtLeast(2)
        val h = outH.coerceAtLeast(2)
        val out = newBitmap(w, h, rgb565, frame)
        val canvas = Canvas(out)
        drawSource(
            canvas, src, frame, w, h, densityX, densityY,
            alpha = frame.opacity.coerceIn(0f, 1f), rgb565 = rgb565
        )
        return out
    }

    /**
     * Crossfade between two source frames for the loop seam. [mix] is the
     * weight of [to] (0 = [from], 1 = [to]). With [soft] the result is
     * rendered through a low-resolution pass, which gives the blur transition
     * its soft-focus dissolve without an expensive stack blur.
     */
    fun renderBlend(
        from: Bitmap,
        to: Bitmap,
        frame: LivingFrame,
        outW: Int,
        outH: Int,
        densityX: Float,
        densityY: Float,
        mix: Float,
        rgb565: Boolean,
        soft: Boolean
    ): Bitmap {
        val w = outW.coerceAtLeast(2)
        val h = outH.coerceAtLeast(2)
        val amount = mix.coerceIn(0f, 1f)
        if (amount <= 0.001f) {
            return render(from, frame, w, h, densityX, densityY, rgb565)
        }
        if (amount >= 0.999f) {
            return render(to, frame, w, h, densityX, densityY, rgb565)
        }
        val out = newBitmap(w, h, rgb565, frame)
        val canvas = Canvas(out)
        val opacity = frame.opacity.coerceIn(0f, 1f)
        // Drawing `to` over `from` leaves the colour at (1-mix)·from + mix·to
        // while the union of the two identical corner masks keeps alpha right.
        drawSource(canvas, from, frame, w, h, densityX, densityY, opacity, rgb565)
        drawSource(canvas, to, frame, w, h, densityX, densityY, opacity * amount, rgb565)
        if (!soft) return out
        return try {
            soften(out, frame, rgb565)
        } finally {
            // The softened copy is what the flipper keeps; the intermediate
            // full-size blend would otherwise linger until the next GC.
            runCatching { out.recycle() }
        }
    }

    private fun newBitmap(w: Int, h: Int, rgb565: Boolean, frame: LivingFrame): Bitmap {
        val config = if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        val out = Bitmap.createBitmap(w, h, config)
        // A fully covered opaque output lets the host skip alpha blending.
        if (!rgb565 && isOpaque(frame)) out.setHasAlpha(false)
        return out
    }

    private fun drawSource(
        canvas: Canvas,
        src: Bitmap,
        frame: LivingFrame,
        w: Int,
        h: Int,
        densityX: Float,
        densityY: Float,
        alpha: Float,
        rgb565: Boolean
    ) {
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
            this.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        }
        val radii = LivingFrameEngine.cornerRadii(
            cornerDp = frame.cornerRadiusDp,
            densityX = densityX,
            densityY = densityY,
            widthPx = w,
            heightPx = h
        )

        if (radii.rx < 0.5f && radii.ry < 0.5f) {
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
            canvas.drawRoundRect(dst, radii.rx, radii.ry, paint)
        }
    }

    /** Low-resolution round-trip used by the BLUR loop transition. */
    private fun soften(src: Bitmap, frame: LivingFrame, rgb565: Boolean): Bitmap {
        val w = (src.width / 4).coerceAtLeast(2)
        val h = (src.height / 4).coerceAtLeast(2)
        val config = if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        val small = Bitmap.createBitmap(w, h, config)
        Canvas(small).drawBitmap(
            src,
            Rect(0, 0, src.width, src.height),
            Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        val out = Bitmap.createBitmap(src.width, src.height, config)
        if (!rgb565 && isOpaque(frame)) out.setHasAlpha(false)
        Canvas(out).drawBitmap(
            small,
            Rect(0, 0, small.width, small.height),
            Rect(0, 0, src.width, src.height),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        small.recycle()
        return out
    }

    /**
     * The opaque picture alone: cropped/scaled to [outW]×[outH] with no
     * corners and full opacity. This is what the proxy cache stores; corners
     * and opacity are applied afterwards by [render].
     */
    fun renderBase(src: Bitmap, frame: LivingFrame, outW: Int, outH: Int, rgb565: Boolean): Bitmap =
        render(
            src,
            frame.copy(cornerRadiusDp = 0, opacity = 1f),
            outW,
            outH,
            1f,
            1f,
            rgb565
        )

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
