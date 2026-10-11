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
import android.graphics.ImageDecoder
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
    val droppedFrames: Int = 0
)

/**
 * Decodes GIF / video / photo sources into small Binder-safe WebP frames.
 * Caps keep home-widget updates under the ~1MB Binder limit and avoid
 * TransactionTooLarge crashes (see research notes in LivingFrameModels).
 */
@Singleton
class LivingFrameImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: LivingFramesRepository,
    private val codecExtractor: LivingFrameCodecExtractor
) {
    suspend fun importGif(uri: Uri, fps: Int, name: String = "GIF Loop"): LivingFrameImportResult =
        withContext(Dispatchers.IO) {
            val id = repo.newId()
            val targetFps = LivingFrameEngine.clampFps(fps)
            val frames = decodeGifFrames(uri, targetFps)
            val kept = frames.take(LivingFramesRepository.MAX_FRAMES)
            val dropped = (frames.size - kept.size).coerceAtLeast(0)
            storeFrames(id, kept, name, LivingFrameType.GIF, targetFps, uri.toString(), dropped)
        }

    suspend fun importVideo(
        uri: Uri,
        fps: Int,
        name: String = "Video Loop",
        maxDurationMs: Long = 10_000L
    ): LivingFrameImportResult = withContext(Dispatchers.IO) {
        val id = repo.newId()
        val targetFps = LivingFrameEngine.clampFps(fps)
        // Path 1 — full MediaCodec decode: exact uniform samples, no seek dupes.
        var frames: List<Bitmap> = try {
            codecExtractor.extract(
                uri, targetFps, maxDurationMs,
                LivingFramesRepository.STORE_SIDE_VIDEO,
                LivingFramesRepository.MAX_FRAMES
            )
        } catch (_: Exception) { emptyList() }
        // Path 2 — retriever fallback.
        if (frames.isEmpty()) {
            frames = extractVideoFrames(uri, targetFps, maxDurationMs)
        }
        // Uniqueness gate: refuse to build a dead loop from duplicate frames.
        frames = dedupeFrames(frames)
        require(frames.size >= 2) { "Couldn't extract motion from this video — try a different clip." }
        storeFrames(id, frames, name, LivingFrameType.VIDEO, targetFps, uri.toString(), 0)
    }

    /**
     * Drop consecutive near-duplicate frames (64-bit aHash, hamming ≤ 4).
     * Seek-based extraction often repeats keyframes; without this the widget
     * "animates" identical bitmaps and looks stuck on frame one.
     */
    fun dedupeFrames(frames: List<Bitmap>): List<Bitmap> {
        if (frames.size <= 1) return frames
        val out = ArrayList<Bitmap>(frames.size)
        var lastHash: Long? = null
        frames.forEach { bmp ->
            val h = try { frameHash(bmp) } catch (_: Exception) { null }
            if (h == null || lastHash == null || hamming(h, lastHash) > 4) {
                out.add(bmp)
                if (h != null) lastHash = h
            } else {
                try { bmp.recycle() } catch (_: Exception) {}
            }
        }
        return out.ifEmpty { frames }
    }

    private fun frameHash(bmp: Bitmap): Long {
        val tiny = Bitmap.createScaledBitmap(bmp, 8, 8, true)
        try {
            var sum = 0L
            val px = IntArray(64)
            tiny.getPixels(px, 0, 8, 0, 0, 8, 8)
            for (p in px) {
                sum += ((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 4 + (p and 0xFF)
            }
            val avg = sum / (64 * 8)
            var hash = 0L
            for (p in px) {
                hash = hash shl 1
                val g = (((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 4 + (p and 0xFF)) / 8
                if (g > avg) hash = hash or 1L
            }
            return hash
        } finally {
            try { tiny.recycle() } catch (_: Exception) {}
        }
    }

    private fun hamming(a: Long, b: Long): Int {
        var x = a xor b
        var n = 0
        while (x != 0L) {
            n += (x and 1L).toInt()
            x = x ushr 1
        }
        return n
    }

    suspend fun importPhoto(uri: Uri, name: String): LivingFrameImportResult =
        withContext(Dispatchers.IO) {
            val id = repo.newId()
            val bmp = loadDownscaled(uri, LivingFramesRepository.STORE_SIDE_STATIC)
                ?: throw IllegalArgumentException("Could not decode image")
            storeFrames(id, listOf(bmp), name, LivingFrameType.PHOTO, 1, uri.toString(), 0)
        }

    suspend fun importSlideshow(uris: List<Uri>, name: String): LivingFrameImportResult =
        withContext(Dispatchers.IO) {
            val id = repo.newId()
            val bmps = uris.take(20).mapNotNull { loadDownscaled(it, LivingFramesRepository.STORE_SIDE_STATIC) }
            require(bmps.isNotEmpty()) { "No decodable images" }
            storeFrames(id, bmps, name, LivingFrameType.SLIDESHOW, 1, "${bmps.size} photos", 0)
        }

    // -- decoders ----------------------------------------------------------

    private fun decodeGifFrames(uri: Uri, fps: Int): List<Bitmap> {
        val out = mutableListOf<Bitmap>()
        val maxSide = LivingFramesRepository.STORE_SIDE_ANIMATED
        try {
            // Path 1 — android.graphics.Movie: deterministic sampling across one
            // real loop duration (no wall-clock drift, no missing-bounds bug).
            try {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    @Suppress("DEPRECATION")
                    val movie = android.graphics.Movie.decodeStream(ins)
                    if (movie != null && movie.width() > 0 && movie.height() > 0) {
                        val w = movie.width()
                        val h = movie.height()
                        val scale = maxSide.toFloat() / maxOf(w, h).toFloat()
                        val tw = (w * minOf(1f, scale)).roundToInt().coerceAtLeast(1)
                        val th = (h * minOf(1f, scale)).roundToInt().coerceAtLeast(1)
                        val dur = movie.duration().takeIf { it > 0 } ?: 1000
                        val count = ((dur.toLong() * fps) / 1000L).toInt()
                            .coerceIn(1, LivingFramesRepository.MAX_FRAMES)
                        repeat(count) { i ->
                            try {
                                movie.setTime((i * dur / count) % dur.coerceAtLeast(1))
                                val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                                val c = android.graphics.Canvas(bmp)
                                c.scale(tw.toFloat() / w, th.toFloat() / h)
                                movie.draw(c, 0f, 0f)
                                out.add(bmp)
                            } catch (_: Exception) { }
                        }
                        if (out.isNotEmpty() && out.any { !isBlankFrame(it) }) {
                            return out.take(LivingFramesRepository.MAX_FRAMES)
                        }
                        out.forEach { try { it.recycle() } catch (_: Exception) {} }
                        out.clear()
                    }
                }
            } catch (_: Exception) { }
            // Path 2 — AnimatedImageDrawable with EXPLICIT bounds (unset bounds
            // draw nothing — the old "stuck on first frame" bug).
            if (Build.VERSION.SDK_INT >= 28) {
                val src = ImageDecoder.createSource(context.contentResolver, uri)
                val drawable = ImageDecoder.decodeDrawable(src)
                if (drawable is android.graphics.drawable.AnimatedImageDrawable) {
                    val w = drawable.intrinsicWidth.coerceAtLeast(1)
                    val h = drawable.intrinsicHeight.coerceAtLeast(1)
                    drawable.setBounds(0, 0, w, h)
                    val scale = maxSide.toFloat() / maxOf(w, h).toFloat()
                    val tw = (w * minOf(1f, scale)).roundToInt().coerceAtLeast(1)
                    val th = (h * minOf(1f, scale)).roundToInt().coerceAtLeast(1)
                    val totalMs = 3000L // sample 3s window; looping GIFs repeat anyway
                    val count = ((totalMs * fps) / 1000L).toInt()
                        .coerceIn(1, LivingFramesRepository.MAX_FRAMES)
                    drawable.start()
                    repeat(count) {
                        try {
                            val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                            val c = android.graphics.Canvas(bmp)
                            c.save()
                            c.scale(tw.toFloat() / w, th.toFloat() / h)
                            drawable.draw(c)
                            c.restore()
                            out.add(bmp)
                            Thread.sleep(1000L / fps.coerceAtLeast(1))
                        } catch (_: Exception) { }
                    }
                    try { drawable.stop() } catch (_: Exception) {}
                    if (out.isNotEmpty() && out.any { !isBlankFrame(it) }) {
                        return out.take(LivingFramesRepository.MAX_FRAMES)
                    }
                    out.forEach { try { it.recycle() } catch (_: Exception) {} }
                    out.clear()
                }
            }
            // Path 3 — honest static fallback: real first frame (never blank set).
            loadDownscaled(uri, maxSide)?.let {
                if (!isBlankFrame(it)) out.add(it)
                else try { it.recycle() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            try {
                loadDownscaled(uri, maxSide)?.let {
                    if (out.isEmpty() && !isBlankFrame(it)) out.add(it)
                }
            } catch (_: Exception) {}
        }
        return out
    }

    /** True when every sampled pixel is (near-)transparent — a failed decode. */
    private fun isBlankFrame(bmp: Bitmap): Boolean {
        return try {
            val w = bmp.width
            val h = bmp.height
            if (w <= 0 || h <= 0 || bmp.isRecycled) return true
            val step = maxOf(1, minOf(w, h) / 16)
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    if ((bmp.getPixel(x, y) ushr 24) > 8) return false
                    x += step
                }
                y += step
            }
            true
        } catch (_: Exception) { true }
    }

    private fun extractVideoFrames(uri: Uri, fps: Int, maxDurationMs: Long): List<Bitmap> {
        val out = mutableListOf<Bitmap>()
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 3000L
            val capped = minOf(durMs, maxDurationMs).coerceAtLeast(500L)
            val count = LivingFrameEngine.maxFramesFor(capped, fps)
                .coerceIn(1, LivingFramesRepository.MAX_FRAMES)
            repeat(count) { i ->
                try {
                    val tUs = (i * capped * 1000L / count).coerceAtLeast(0L)
                    val raw = retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: return@repeat
                    out.add(downscale(raw, LivingFramesRepository.STORE_SIDE_VIDEO))
                    if (raw !== out.last()) {
                        // downscale() copies; recycle original to save memory.
                        try { if (!raw.isRecycled) raw.recycle() } catch (_: Exception) {}
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) {
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
        if (out.isEmpty()) throw IllegalArgumentException("Could not extract video frames")
        return out
    }

    private fun loadDownscaled(uri: Uri, maxSide: Int): Bitmap? {
        return try {
            // Two-pass via file descriptor (bounds first, then sampled decode).
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, bounds)
                if (bounds.outWidth <= 0) return null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
                    ?: return null
                downscale(bmp, maxSide)
            }
        } catch (_: Exception) { null }
    }

    private fun downscale(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest.toFloat()
        val tw = (src.width * scale).roundToInt().coerceAtLeast(1)
        val th = (src.height * scale).roundToInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(src, tw, th, true)
        } catch (_: Exception) { src }
    }

    private suspend fun storeFrames(
        id: String,
        frames: List<Bitmap>,
        name: String,
        type: LivingFrameType,
        fps: Int,
        sourceLabel: String,
        dropped: Int
    ): LivingFrameImportResult = withContext(Dispatchers.IO) {
        require(frames.isNotEmpty()) { "Nothing to import" }
        val dir = repo.frameDir(id)
        var totalBytes = 0L
        frames.forEachIndexed { i, bmp ->
            try {
                val f = File(dir, "frame_%04d.webp".format(i))
                FileOutputStream(f).use { fos ->
                    @Suppress("DEPRECATION")
                    bmp.compress(Bitmap.CompressFormat.WEBP, 80, fos)
                }
                totalBytes += f.length()
            } catch (_: Exception) {}
        }
        // Cover = first frame.
        try {
            val cover = File(dir, "cover.webp")
            FileOutputStream(cover).use { fos ->
                @Suppress("DEPRECATION")
                frames.first().compress(Bitmap.CompressFormat.WEBP, 85, fos)
            }
        } catch (_: Exception) {}
        val first = frames.first()
        val avg = if (frames.isNotEmpty()) totalBytes / frames.size else 0L
        val frame = LivingFrame(
            id = id,
            name = name.ifBlank { defaultName(type) },
            type = type,
            frameCount = frames.size,
            width = first.width,
            height = first.height,
            fps = LivingFrameEngine.clampFps(fps),
            sourceLabel = sourceLabel.take(120),
            avgFrameBytes = avg
        )
        repo.upsert(frame)
        try { frames.forEach { if (!it.isRecycled) it.recycle() } } catch (_: Exception) {}
        LivingFrameImportResult(frame, dropped)
    }

    private fun defaultName(type: LivingFrameType): String = when (type) {
        LivingFrameType.GIF -> "GIF Loop"
        LivingFrameType.VIDEO -> "Video Loop"
        LivingFrameType.PHOTO -> "Photo Frame"
        LivingFrameType.SLIDESHOW -> "Slideshow"
    }
}
