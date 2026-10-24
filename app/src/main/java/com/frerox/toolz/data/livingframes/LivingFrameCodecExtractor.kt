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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full-decode frame extraction (MediaExtractor + MediaCodec, no surface).
 * Decodes the stream and hands uniform samples to [extractTo] one at a time,
 * so a 6-second clip never holds 40 full bitmaps in RAM.
 *
 * Honours the container's rotation tag, the decoder's crop rect and a start
 * offset, and reports progress.
 */
@Singleton
class LivingFrameCodecExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Captures [count] samples in the window. [onFrame] must not recycle the
     * bitmap; the extractor recycles it after all callbacks for that frame.
     * Returns the number of frames emitted.
     */
    suspend fun extractTo(
        uri: Uri,
        startMs: Long,
        lengthMs: Long,
        count: Int,
        targetSide: Int,
        onFrame: suspend (Bitmap) -> Unit,
        onProgress: (Float) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var emitted = 0
        try {
            extractor = MediaExtractor().also { it.setDataSource(context, uri, null) }
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                    track = i
                    format = f
                    break
                }
            }
            require(track >= 0 && format != null) { "No video track" }
            extractor.selectTrack(track)

            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                format.getInteger(MediaFormat.KEY_ROTATION)
            } else {
                0
            }
            val startUs = startMs.coerceAtLeast(0L) * 1000L
            val lengthUs = lengthMs.coerceAtLeast(100L) * 1000L
            if (startUs > 0L) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            // Some containers start presentation time at a non-zero offset.
            // Anchor targets to the first real sample so they are all not
            // satisfied by one early frame.
            val firstPts = extractor.sampleTime.takeIf { it >= 0L } ?: 0L
            val timeBase = if (firstPts > startUs) firstPts else startUs
            val targets = LongArray(count) { i -> timeBase + i * lengthUs / count }

            val mime = format.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // MediaCodec usually hands back limited-range YUV for SDR video,
            // but YuvImage writes those samples straight into a full-range
            // JPEG. The result is visibly washed out (std dev ~12% low on a
            // 1080p test clip). Expand once per stream before encoding. When
            // the container does not declare a range, inspect the first
            // decoded luma plane instead of guessing.
            val range = RangeState(
                if (format.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
                    format.getInteger(MediaFormat.KEY_COLOR_RANGE) != MediaFormat.COLOR_RANGE_FULL
                } else {
                    null
                }
            )
            val info = MediaCodec.BufferInfo()
            val startedAt = SystemClock.elapsedRealtime()
            var lastOutputAt = startedAt
            var inputEos = false
            var next = 0

            // Pick the frame NEAREST each target, not the first one at or
            // after it. Targets are integer microseconds and frame pts are
            // rounded by the container, so a target 1 us past a frame used to
            // grab the following frame: a whole source frame of jitter that
            // showed up as judder in the loop. Half a frame period is the
            // tolerance; the period is read from the stream and refined from
            // the real output spacing.
            var frameDeltaUs = runCatching {
                val key = MediaFormat.KEY_FRAME_RATE
                if (!format.containsKey(key)) {
                    0L
                } else {
                    val rate = runCatching { format.getInteger(key).toFloat() }
                        .getOrElse { format.getFloat(key) }
                    if (rate > 0f) (1_000_000f / rate).toLong() else 0L
                }
            }.getOrDefault(0L)
            var prevPts = Long.MIN_VALUE

            while (next < count) {
                currentCoroutineContext().ensureActive()
                val now = SystemClock.elapsedRealtime()
                if (now - startedAt > 180_000L) break // never decode forever
                // Keep the decoder fed: queue every free input buffer instead
                // of one per loop, so it never idles waiting on us.
                var fed = 0
                while (!inputEos && fed < 8) {
                    val inIdx = codec.dequeueInputBuffer(if (fed == 0) 5_000L else 0L)
                    if (inIdx < 0) break
                    val buf = codec.getInputBuffer(inIdx)
                    val n = if (buf != null) extractor.readSampleData(buf, 0) else -1
                    if (n < 0) {
                        codec.queueInputBuffer(
                            inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEos = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                    fed++
                }
                val outIdx = codec.dequeueOutputBuffer(info, 5_000L)
                if (outIdx >= 0) {
                    lastOutputAt = SystemClock.elapsedRealtime()
                    try {
                        val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        if (info.size > 0) {
                            if (prevPts != Long.MIN_VALUE) {
                                val delta = info.presentationTimeUs - prevPts
                                if (delta >= 2_000L && (frameDeltaUs == 0L || delta < frameDeltaUs)) {
                                    frameDeltaUs = delta
                                }
                            }
                            prevPts = info.presentationTimeUs
                            val toleranceUs = (frameDeltaUs / 2).coerceAtLeast(1_000L)
                            // One decoded picture can satisfy several targets
                            // (slow clips or a low requested fps).
                            var covered = 0
                            while (next + covered < count &&
                                info.presentationTimeUs + toleranceUs >= targets[next + covered]
                            ) {
                                covered++
                            }
                            if (covered > 0) {
                                val bmp = yuvToBitmap(codec, outIdx, targetSide, rotation, range)
                                if (bmp != null) {
                                    try {
                                        // One decoded picture can fill several
                                        // slots. Reuse it rather than allocating
                                        // hundreds of full-resolution copies.
                                        repeat(covered) {
                                            onFrame(bmp)
                                            emitted++
                                        }
                                        next += covered
                                        onProgress(next / count.toFloat())
                                    } finally {
                                        if (!bmp.isRecycled) bmp.recycle()
                                    }
                                }
                            }
                        }
                        if (eos) break
                    } finally {
                        runCatching { codec.releaseOutputBuffer(outIdx, false) }
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    lastOutputAt = SystemClock.elapsedRealtime()
                    runCatching {
                        val outFormat = codec.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
                            range.expand = outFormat.getInteger(MediaFormat.KEY_COLOR_RANGE) !=
                                    MediaFormat.COLOR_RANGE_FULL
                        }
                    }
                }
                if (inputEos && SystemClock.elapsedRealtime() - lastOutputAt > 5_000L) break
            }
            require(emitted > 0) { "Decoder produced no frames" }
            emitted
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    private fun yuvToBitmap(
        codec: MediaCodec,
        index: Int,
        targetSide: Int,
        rotation: Int,
        range: RangeState
    ): Bitmap? {
        return try {
            val image = codec.getOutputImage(index) ?: return null
            try {
                val crop = image.cropRect
                val w = (crop.width() / 2 * 2).coerceAtLeast(2)
                val h = (crop.height() / 2 * 2).coerceAtLeast(2)
                val expand = range.expand
                    ?: detectLimitedRange(image).also { range.expand = it }
                val nv21 = yuv420ToNv21(
                    image, crop.left, crop.top, w, h, expand
                ) ?: return null
                val stream = ByteArrayOutputStream(w * h)
                val ok = YuvImage(nv21, ImageFormat.NV21, w, h, null)
                    .compressToJpeg(Rect(0, 0, w, h), 95, stream)
                if (!ok) return null
                val bytes = stream.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0) return null
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (longest / (sample * 2) >= targetSide) sample *= 2
                var bmp = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                ) ?: return null
                val side = maxOf(bmp.width, bmp.height)
                if (side > targetSide) {
                    val s = targetSide / side.toFloat()
                    val scaled = Bitmap.createScaledBitmap(
                        bmp,
                        (bmp.width * s).toInt().coerceAtLeast(1),
                        (bmp.height * s).toInt().coerceAtLeast(1),
                        true
                    )
                    if (scaled !== bmp) bmp.recycle()
                    bmp = scaled
                }
                if (rotation % 360 != 0) {
                    val rotated = Bitmap.createBitmap(
                        bmp, 0, 0, bmp.width, bmp.height,
                        Matrix().apply { postRotate(rotation.toFloat()) }, true
                    )
                    if (rotated !== bmp) bmp.recycle()
                    bmp = rotated
                }
                bmp
            } finally {
                runCatching { image.close() }
            }
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory decoding a video frame", e)
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * A container may omit the range flag. A full-range luma plane almost
     * always has samples below 16 or above 235; a limited-range plane stays
     * inside that window. Sample the plane instead of scanning every pixel.
     */
    private fun detectLimitedRange(image: Image): Boolean {
        val yBuf = image.planes[0].buffer
        val start = yBuf.position()
        val end = yBuf.limit()
        if (end <= start) return true
        val step = ((end - start) / 4096).coerceAtLeast(1)
        var min = 255
        var max = 0
        var i = start
        while (i < end) {
            val v = yBuf.get(i).toInt() and 0xFF
            if (v < min) min = v
            if (v > max) max = v
            i += step
        }
        return min >= 12 && max <= 239
    }

    private fun yuv420ToNv21(
        image: Image,
        xOff: Int,
        yOff: Int,
        w: Int,
        h: Int,
        expandLimitedRange: Boolean
    ): ByteArray? {
        return try {
            val planes = image.planes
            if (planes.size < 3) return null
            val nv21 = ByteArray(w * h + w * h / 2)
            val yBuf = planes[0].buffer
            val yStride = planes[0].rowStride
            val yPix = planes[0].pixelStride
            var pos = 0
            for (row in 0 until h) {
                val base = (row + yOff) * yStride + xOff * yPix
                if (yPix == 1 && !expandLimitedRange) {
                    yBuf.position(base)
                    yBuf.get(nv21, pos, w)
                } else {
                    for (col in 0 until w) {
                        val y = yBuf.get(base + col * yPix).toInt() and 0xFF
                        nv21[pos + col] = if (expandLimitedRange) {
                            LIMITED_Y[y]
                        } else {
                            y.toByte()
                        }
                    }
                }
                pos += w
            }
            val uBuf = planes[1].buffer
            val vBuf = planes[2].buffer
            val uRow = planes[1].rowStride
            val vRow = planes[2].rowStride
            val uPix = planes[1].pixelStride
            val vPix = planes[2].pixelStride
            val cx = xOff / 2
            val cy = yOff / 2
            for (row in 0 until h / 2) {
                for (col in 0 until w / 2) {
                    val o = pos + row * w + col * 2
                    val v = vBuf.get((row + cy) * vRow + (col + cx) * vPix)
                        .toInt() and 0xFF
                    val u = uBuf.get((row + cy) * uRow + (col + cx) * uPix)
                        .toInt() and 0xFF
                    if (expandLimitedRange) {
                        nv21[o] = LIMITED_C[v]
                        nv21[o + 1] = LIMITED_C[u]
                    } else {
                        nv21[o] = v.toByte()
                        nv21[o + 1] = u.toByte()
                    }
                }
            }
            nv21
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory converting a video frame", e)
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Whether the decoder's current stream still needs limited→full expansion. */
    private class RangeState(var expand: Boolean?)

    companion object {
        private const val TAG = "LivingFrameCodec"

        /** Limited-range luma (16..235) to full-range (0..255). */
        private val LIMITED_Y = ByteArray(256) { i ->
            (((i - 16) * 255 + 109) / 219).coerceIn(0, 255).toByte()
        }

        /** Limited-range chroma (16..240, centred at 128) to full range. */
        private val LIMITED_C = ByteArray(256) { i ->
            (((i - 128) * 255 + 112) / 224 + 128).coerceIn(0, 255).toByte()
        }
    }
}