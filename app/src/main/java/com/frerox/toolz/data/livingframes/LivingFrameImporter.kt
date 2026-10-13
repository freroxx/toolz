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
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class LivingFrameImportResult(
    val frame: LivingFrame,
    /** Something worth telling the user (single-frame GIF, skipped photos…). */
    val note: String? = null
)

data class VideoProbe(val durationMs: Long, val width: Int, val height: Int)

/**
 * Turns GIFs, video windows, photos and photo sets into stored WebP frames.
 *
 * Timing rule: the playback fps is always derived from (frames captured ÷
 * real duration covered), never assumed. The previous importer sampled a
 * whole clip into a capped frame count but kept the requested fps, so long
 * clips played back sped up.
 */
@Singleton
class LivingFrameImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: LivingFramesRepository,
    private val codec: LivingFrameCodecExtractor
) {
    // -- public API --------------------------------------------------------

    suspend fun probeVideo(uri: Uri): VideoProbe? = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return@withContext null
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            VideoProbe(dur, w, h)
        } catch (_: Exception) { null } finally {
            runCatching { r.release() }
        }
    }

    suspend fun videoThumbnail(uri: Uri, atMs: Long, maxSide: Int = 480): Bitmap? =
        withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                r.getFrameAtTime(atMs.coerceAtLeast(0L) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { downscale(it, maxSide) }
            } catch (_: Exception) { null } finally {
                runCatching { r.release() }
            }
        }

    suspend fun importGif(uri: Uri, onProgress: (Float) -> Unit = {}): LivingFrameImportResult =
        withContext(Dispatchers.IO) {
            val decoded = decodeAnimated(uri, onProgress)
                ?: throw IllegalArgumentException("Couldn't read that GIF.")
            if (decoded.frames.size == 1) {
                val res = store(
                    decoded.frames, "Photo Frame", LivingFrameType.PHOTO, 1, uri.toString(),
                    onProgress, 0.6f
                )
                return@withContext res.copy(note = "That GIF has a single frame, so it was added as a photo.")
            }
            store(decoded.frames, "GIF Loop", LivingFrameType.GIF, decoded.fps, uri.toString(), onProgress, 0.6f)
        }

    suspend fun importVideo(
        uri: Uri,
        startMs: Long,
        lengthMs: Long,
        onProgress: (Float) -> Unit = {}
    ): LivingFrameImportResult = withContext(Dispatchers.IO) {
        val len = lengthMs.coerceIn(500L, 6_000L)
        val targetFps = LivingFrameFps.snap(minOf(15f, LivingFramesRepository.MAX_FRAMES * 1000f / len))
        val count = (len / 1000f * targetFps).roundToInt().coerceIn(2, LivingFramesRepository.MAX_FRAMES)

        var frames: List<Bitmap> = try {
            codec.extract(
                uri, startMs, len, count, LivingFramesRepository.STORE_SIDE_VIDEO
            ) { onProgress(it * 0.6f) }
        } catch (_: Exception) { emptyList() }
        if (frames.isEmpty()) {
            frames = retrieverFrames(uri, startMs, len, count) { onProgress(it * 0.6f) }
        }
        if (frames.size < 2 || !hasMotion(frames)) {
            frames.forEach { runCatching { it.recycle() } }
            throw IllegalArgumentException("No motion in that part of the video. Try another start point.")
        }
        val fps = LivingFrameFps.snap(frames.size * 1000f / len)
        store(frames, "Video Loop", LivingFrameType.VIDEO, fps, uri.toString(), onProgress, 0.6f)
    }

    suspend fun importPhoto(uri: Uri, onProgress: (Float) -> Unit = {}): LivingFrameImportResult =
        withContext(Dispatchers.IO) {
            val bmp = loadDownscaled(uri, LivingFramesRepository.STORE_SIDE_PHOTO)
                ?: throw IllegalArgumentException("Couldn't read that image.")
            store(listOf(bmp), "Photo Frame", LivingFrameType.PHOTO, 1, uri.toString(), onProgress, 0f)
        }

    suspend fun importSlideshow(uris: List<Uri>, onProgress: (Float) -> Unit = {}): LivingFrameImportResult =
        withContext(Dispatchers.IO) {
            val picked = uris.take(LivingFramesRepository.MAX_SLIDES)
            val bmps = ArrayList<Bitmap>()
            picked.forEachIndexed { i, u ->
                loadDownscaled(u, LivingFramesRepository.STORE_SIDE_PHOTO)?.let { bmps.add(it) }
                onProgress((i + 1) / picked.size.toFloat() * 0.6f)
            }
            require(bmps.isNotEmpty()) { "None of those photos could be read." }
            val skipped = uris.size - bmps.size
            val note = when {
                uris.size > LivingFramesRepository.MAX_SLIDES ->
                    "Slideshows hold up to ${LivingFramesRepository.MAX_SLIDES} photos; the rest were left out."
                skipped > 0 -> "$skipped photo${if (skipped == 1) "" else "s"} couldn't be read and were skipped."
                else -> null
            }
            store(bmps, "Slideshow", LivingFrameType.SLIDESHOW, 1, "${bmps.size} photos", onProgress, 0.6f)
                .copy(note = note)
        }

    // -- GIF / animated image decoding ---------------------------------------

    private class Decoded(val frames: List<Bitmap>, val fps: Int)

    private fun decodeAnimated(uri: Uri, onProgress: (Float) -> Unit): Decoded? {
        val side = LivingFramesRepository.STORE_SIDE_GIF
        // Path 1: Movie. Deterministic seeking across one real loop.
        try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                @Suppress("DEPRECATION")
                val movie = Movie.decodeStream(ins)
                if (movie != null && movie.width() > 0 && movie.height() > 0 && movie.duration() > 0) {
                    val w = movie.width()
                    val h = movie.height()
                    val dur = movie.duration()
                    val scale = minOf(1f, side / maxOf(w, h).toFloat())
                    val tw = (w * scale).roundToInt().coerceAtLeast(1)
                    val th = (h * scale).roundToInt().coerceAtLeast(1)
                    val count = (dur / 1000f * 15f).roundToInt().coerceIn(2, LivingFramesRepository.MAX_FRAMES)
                    val out = ArrayList<Bitmap>(count)
                    for (i in 0 until count) {
                        try {
                            movie.setTime((i.toLong() * dur / count).toInt())
                            val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                            val c = Canvas(bmp)
                            c.scale(tw / w.toFloat(), th / h.toFloat())
                            movie.draw(c, 0f, 0f)
                            out.add(bmp)
                        } catch (_: Exception) {}
                        onProgress((i + 1) / count.toFloat() * 0.6f)
                    }
                    if (out.size >= 2 && out.any { !isBlank(it) }) {
                        return Decoded(out, LivingFrameFps.snap(out.size * 1000f / dur))
                    }
                    out.forEach { runCatching { it.recycle() } }
                }
            }
        } catch (_: Exception) {}

        // Path 2: animated WebP / HEIF through ImageDecoder, sampled in real time.
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val src = ImageDecoder.createSource(context.contentResolver, uri)
                val drawable = ImageDecoder.decodeDrawable(src)
                if (drawable is AnimatedImageDrawable) {
                    val w = drawable.intrinsicWidth.coerceAtLeast(1)
                    val h = drawable.intrinsicHeight.coerceAtLeast(1)
                    drawable.setBounds(0, 0, w, h)
                    val scale = minOf(1f, side / maxOf(w, h).toFloat())
                    val tw = (w * scale).roundToInt().coerceAtLeast(1)
                    val th = (h * scale).roundToInt().coerceAtLeast(1)
                    val fps = 12
                    val count = 36
                    val out = ArrayList<Bitmap>(count)
                    drawable.start()
                    for (i in 0 until count) {
                        val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                        val c = Canvas(bmp)
                        c.scale(tw / w.toFloat(), th / h.toFloat())
                        drawable.draw(c)
                        out.add(bmp)
                        onProgress((i + 1) / count.toFloat() * 0.6f)
                        Thread.sleep(1000L / fps)
                    }
                    runCatching { drawable.stop() }
                    if (out.any { !isBlank(it) }) return Decoded(out, fps)
                    out.forEach { runCatching { it.recycle() } }
                }
            } catch (_: Exception) {}
        }

        // Path 3: honest static fallback.
        val still = loadDownscaled(uri, side) ?: return null
        return Decoded(listOf(still), 1)
    }

    private fun isBlank(bmp: Bitmap): Boolean = try {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0 || bmp.isRecycled) {
            true
        } else {
            val step = maxOf(1, minOf(w, h) / 16)
            var seen = false
            var y = 0
            while (y < h && !seen) {
                var x = 0
                while (x < w) {
                    if ((bmp.getPixel(x, y) ushr 24) > 8) { seen = true; break }
                    x += step
                }
                y += step
            }
            !seen
        }
    } catch (_: Exception) { true }

    // -- video fallback --------------------------------------------------------

    private fun retrieverFrames(
        uri: Uri, startMs: Long, lengthMs: Long, count: Int, onProgress: (Float) -> Unit
    ): List<Bitmap> {
        val out = ArrayList<Bitmap>()
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            for (i in 0 until count) {
                try {
                    val tUs = (startMs + i * lengthMs / count) * 1000L
                    r.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let {
                        out.add(downscale(it, LivingFramesRepository.STORE_SIDE_VIDEO))
                    }
                } catch (_: Exception) {}
                onProgress((i + 1) / count.toFloat())
            }
        } catch (_: Exception) {
        } finally {
            runCatching { r.release() }
        }
        return out
    }

    // -- motion check (average hash) -----------------------------------------------

    /** True when at least one frame differs visibly from the first. Never drops frames. */
    private fun hasMotion(frames: List<Bitmap>): Boolean {
        val base = hash(frames.first()) ?: return true
        return frames.drop(1).any { f ->
            val h = hash(f) ?: return@any false
            java.lang.Long.bitCount(h xor base) > 2
        }
    }

    private fun hash(bmp: Bitmap): Long? = try {
        val tiny = Bitmap.createScaledBitmap(bmp, 8, 8, true)
        val px = IntArray(64)
        tiny.getPixels(px, 0, 8, 0, 0, 8, 8)
        if (tiny !== bmp) tiny.recycle()
        val lum = IntArray(64) { i ->
            val p = px[i]
            (((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 4 + (p and 0xFF)) / 8
        }
        val avg = lum.sum() / 64
        var h = 0L
        for (v in lum) { h = h shl 1; if (v > avg) h = h or 1L }
        h
    } catch (_: Exception) { null }

    // -- image loading (EXIF-aware) ---------------------------------------------------

    private fun loadDownscaled(uri: Uri, maxSide: Int): Bitmap? = try {
        val raw: Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                var sample = 1
                while (longest / (sample * 2) >= maxSide) sample *= 2
                if (sample > 1) decoder.setTargetSampleSize(sample)
            }
        } else {
            legacyDecode(uri, maxSide)
        }
        raw?.let { downscale(it, maxSide) }
    } catch (_: Exception) { null }

    private fun legacyDecode(uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        if (Build.VERSION.SDK_INT < 24) return bmp
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                when (ExifInterface(ins).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) { 0f }
        if (degrees == 0f) return bmp
        val rotated = Bitmap.createBitmap(
            bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true
        )
        if (rotated !== bmp) bmp.recycle()
        return rotated
    }

    private fun downscale(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val s = maxSide / longest.toFloat()
        return try {
            val scaled = Bitmap.createScaledBitmap(
                src, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1), true
            )
            if (scaled !== src) src.recycle()
            scaled
        } catch (_: Exception) { src }
    }

    // -- storage -------------------------------------------------------------------------

    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
        else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP

    private suspend fun store(
        frames: List<Bitmap>,
        name: String,
        type: LivingFrameType,
        fps: Int,
        sourceLabel: String,
        onProgress: (Float) -> Unit,
        progressFloor: Float
    ): LivingFrameImportResult {
        require(frames.isNotEmpty()) { "Nothing to import" }
        val id = repo.newId()
        val dir = repo.ensureFrameDir(id)
        try {
            var total = 0L
            val quality = if (type == LivingFrameType.PHOTO || type == LivingFrameType.SLIDESHOW) 88 else 82
            frames.forEachIndexed { i, bmp ->
                val f = File(dir, "frame_%04d.webp".format(i))
                FileOutputStream(f).use { bmp.compress(webp(), quality, it) }
                total += f.length()
                onProgress(progressFloor + (1f - progressFloor) * (i + 1) / frames.size)
            }
            val first = frames.first()
            val cover = downscaleCopy(first, LivingFramesRepository.COVER_SIDE)
            FileOutputStream(File(dir, "cover.webp")).use { cover.compress(webp(), 80, it) }
            if (cover !== first) cover.recycle()

            val frame = LivingFrame(
                id = id,
                name = name,
                type = type,
                frameCount = frames.size,
                width = first.width,
                height = first.height,
                fps = LivingFrameFps.snap(fps),
                avgFrameBytes = total / frames.size,
                sourceLabel = sourceLabel.take(120)
            )
            repo.upsert(frame)
            return LivingFrameImportResult(frame)
        } catch (e: Exception) {
            runCatching { dir.deleteRecursively() }
            throw e
        } finally {
            frames.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
        }
    }

    private fun downscaleCopy(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val s = maxSide / longest.toFloat()
        return Bitmap.createScaledBitmap(
            src, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1), true
        )
    }
}
