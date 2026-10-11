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
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Full-decode video frame extraction (MediaExtractor + MediaCodec, no
 * surface). Unlike MediaMetadataRetriever seeks — which snap per-codec and
 * silently duplicate/drop frames — this decodes the stream and captures
 * exact uniform samples. Throws when nothing usable comes out so the
 * importer fails loudly instead of building a dead loop.
 */
@Singleton
class LivingFrameCodecExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun extract(
        uri: Uri,
        fps: Int,
        maxDurationMs: Long,
        targetSide: Int,
        maxFrames: Int
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val targetFps = fps.coerceIn(LivingFrameFps.MIN, LivingFrameFps.MAX)
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    track = i
                    format = f
                    break
                }
            }
            require(track >= 0 && format != null) { "No video track" }
            extractor.selectTrack(track)
            val durationUs = try {
                format.getLong(MediaFormat.KEY_DURATION)
            } catch (_: Exception) { 3_000_000L }.coerceAtLeast(500_000L)
            val cappedUs = minOf(durationUs, maxDurationMs * 1000L)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            val count = ((cappedUs / 1_000_000.0 * targetFps).toInt())
                .coerceIn(1, maxFrames)
            val targets = LongArray(count) { i -> i * cappedUs / count }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val out = ArrayList<Bitmap>(count)
            val startMs = android.os.SystemClock.elapsedRealtime()
            var inputEos = false
            var targetIdx = 0
            val bufferInfo = MediaCodec.BufferInfo()
            var lastFrameMs = android.os.SystemClock.elapsedRealtime()

            while (targetIdx < count) {
                // Watchdog: never spend longer than ~45s decoding.
                if (android.os.SystemClock.elapsedRealtime() - startMs > 45_000L) break
                if (!inputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        try {
                            val inBuf = codec.getInputBuffer(inIdx)
                            if (inBuf == null) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, 0)
                            } else {
                                val n = extractor.readSampleData(inBuf, 0)
                                if (n < 0) {
                                    codec.queueInputBuffer(
                                        inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputEos = true
                                } else {
                                    codec.queueInputBuffer(
                                        inIdx, 0, n, extractor.sampleTime, extractor.sampleFlags
                                    )
                                    extractor.advance()
                                }
                            }
                        } catch (_: Exception) {
                            try { codec.queueInputBuffer(inIdx, 0, 0, 0L, 0) } catch (_: Exception) {}
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outIdx >= 0) {
                    lastFrameMs = android.os.SystemClock.elapsedRealtime()
                    try {
                        val pts = bufferInfo.presentationTimeUs
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        // Capture every target this frame covers (catch-up safe,
                        // decode once even if several targets land on it).
                        if (bufferInfo.size > 0) {
                            var covered = 0
                            while (targetIdx + covered < count && pts >= targets[targetIdx + covered]) covered++
                            if (covered > 0) {
                                yuvToBitmap(codec, outIdx, targetSide)?.let { bmp ->
                                    repeat(covered) { out.add(if (it == 0) bmp else bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false) ?: bmp) }
                                }
                                targetIdx += covered
                            }
                        } else if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            // EOS with no data — stop draining.
                        }
                        if (isEos) break
                    } finally {
                        try { codec.releaseOutputBuffer(outIdx, false) } catch (_: Exception) {}
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    lastFrameMs = android.os.SystemClock.elapsedRealtime()
                }
                if (inputEos && android.os.SystemClock.elapsedRealtime() - lastFrameMs > 5_000L) break
            }
            require(out.isNotEmpty()) { "Decoder produced no frames" }
            out
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    private fun yuvToBitmap(codec: MediaCodec, index: Int, targetSide: Int): Bitmap? {
        return try {
            val image = codec.getOutputImage(index) ?: return null
            try {
                val w = (image.width / 2 * 2).coerceAtLeast(2)
                val h = (image.height / 2 * 2).coerceAtLeast(2)
                val nv21 = yuv420ToNv21(image, w, h) ?: return null
                val yuv = YuvImage(nv21, ImageFormat.NV21, w, h, null)
                val stream = ByteArrayOutputStream(w * h)
                if (!yuv.compressToJpeg(Rect(0, 0, w, h), 88, stream)) return null
                val bytes = stream.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0) return null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > targetSide) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            } finally {
                try { image.close() } catch (_: Exception) {}
            }
        } catch (_: Exception) { null }
    }

    private fun yuv420ToNv21(image: android.media.Image, w: Int, h: Int): ByteArray? {
        return try {
            val planes = image.planes
            if (planes.size < 3) return null
            val nv21 = ByteArray(w * h + w * h / 2)
            // Y plane, row by row (respect stride).
            val yBuf = planes[0].buffer
            val yStride = planes[0].rowStride
            var pos = 0
            for (row in 0 until h) {
                yBuf.position(row * yStride)
                yBuf.get(nv21, pos, w)
                pos += w
            }
            // Interleaved VU from U/V planes (absolute gets — position-safe).
            val uBuf = planes[1].buffer
            val vBuf = planes[2].buffer
            val uRow = planes[1].rowStride
            val vRow = planes[2].rowStride
            val uPix = planes[1].pixelStride
            val vPix = planes[2].pixelStride
            for (row in 0 until h / 2) {
                for (col in 0 until w / 2) {
                    val o = pos + row * w + col * 2
                    nv21[o] = vBuf.get(row * vRow + col * vPix)
                    nv21[o + 1] = uBuf.get(row * uRow + col * uPix)
                }
            }
            nv21
        } catch (_: Exception) { null }
    }
}
