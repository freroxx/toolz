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
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class LivingFrameImportResult(
    val frame: LivingFrame,
    /** Something worth telling the user (single-frame GIF, skipped photos…). */
    val note: String? = null
)

data class VideoProbe(val durationMs: Long, val width: Int, val height: Int)

/** A quota/free-space failure must abort the import, not trigger a fallback decoder. */
private class LivingFrameStorageException(message: String) : IOException(message)

/**
 * Turns GIFs, video windows, photos and photo sets into stored WebP frames.
 *
 * Memory rule: frames are encoded and written one at a time. The previous
 * implementation held every source bitmap until the whole set was stored,
 * which could need >100 MB for a 40-frame 720p clip and crash on low-end
 * devices. The import is staged in <id>.tmp and atomically published only
 * after the last frame is durable.
 *
 * Timing rule: the playback fps is always derived from (frames captured ÷
 * real duration covered), never assumed.
 */
@Singleton
class LivingFrameImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: LivingFramesRepository,
    private val codec: LivingFrameCodecExtractor
) {
    private val importLock = Mutex()

    // -- public API --------------------------------------------------------

    suspend fun probeVideo(uri: Uri): VideoProbe? = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return@withContext null
            if (dur <= 0) return@withContext null
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            VideoProbe(dur, w, h)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    suspend fun videoThumbnail(uri: Uri, atMs: Long, maxSide: Int = 480): Bitmap? =
        withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                r.getFrameAtTime(
                    atMs.coerceAtLeast(0L) * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )?.let { downscale(it, maxSide) }
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Out of memory reading a video thumbnail", e)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } finally {
                runCatching { r.release() }
            }
        }

    suspend fun importGif(
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): LivingFrameImportResult = withContext(Dispatchers.IO) {
        importLock.withLock {
            val movie = decodeMovieToWriter(uri, onProgress)
            if (movie != null) return@withLock finishAnimated(movie, onProgress, uri)

            val animated = decodeAnimatedImageToWriter(uri, onProgress)
            if (animated != null) return@withLock finishAnimated(animated, onProgress, uri)

            val still = loadDownscaled(uri, LivingFramesRepository.STORE_SIDE_GIF)
                ?: throw IllegalArgumentException("Couldn't read that GIF.")
            val writer = newWriter(
                maxFrames = LivingFramesRepository.MAX_FRAMES,
                quality = 84
            )
            try {
                writer.add(still)
                onProgress(1f)
                writer.finish(
                    name = "Photo Frame",
                    type = LivingFrameType.PHOTO,
                    fps = 1,
                    sourceLabel = uri.toString(),
                    note = "That GIF has a single frame, so it was added as a photo."
                )
            } catch (t: Throwable) {
                writer.abort()
                throw t
            }
        }
    }

    suspend fun importVideo(
        uri: Uri,
        startMs: Long,
        lengthMs: Long,
        onProgress: (Float) -> Unit = {}
    ): LivingFrameImportResult {
        // Probe outside the lock: it is cheap metadata-only work and lets us
        // clamp a too-long start/length to the real clip.
        val probe = probeVideo(uri)
        return withContext(Dispatchers.IO) {
            importLock.withLock {
                val duration = probe?.durationMs?.takeIf { it > 0L }
                val minLen = minOf(500L, duration ?: 500L).coerceAtLeast(150L)
                val len = lengthMs.coerceIn(minLen, 6_000L)
                    .coerceAtMost(duration ?: 6_000L)
                    .coerceAtLeast(150L)
                val start = if (duration != null) {
                    startMs.coerceIn(0L, (duration - len).coerceAtLeast(0L))
                } else {
                    startMs.coerceAtLeast(0L)
                }
                val targetFps = LivingFrameFps.snap(
                    minOf(15f, LivingFramesRepository.MAX_FRAMES * 1000f / len)
                )
                val count = (len / 1000f * targetFps).roundToInt()
                    .coerceIn(2, LivingFramesRepository.MAX_FRAMES)

                val fromCodec = videoAttempt(uri, start, len, count, useCodec = true, onProgress)
                if (fromCodec != null) return@withLock fromCodec
                val fallback = videoAttempt(uri, start, len, count, useCodec = false, onProgress)
                if (fallback != null) return@withLock fallback

                throw IllegalArgumentException(
                    "No motion in that part of the video. Try another start point."
                )
            }
        }
    }

    suspend fun importPhoto(
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): LivingFrameImportResult = withContext(Dispatchers.IO) {
        importLock.withLock {
            val bmp = loadDownscaled(uri, LivingFramesRepository.STORE_SIDE_PHOTO)
                ?: throw IllegalArgumentException("Couldn't read that image.")
            val writer = newWriter(maxFrames = 1, quality = 88)
            try {
                writer.add(bmp)
                onProgress(1f)
                writer.finish(
                    name = "Photo Frame",
                    type = LivingFrameType.PHOTO,
                    fps = 1,
                    sourceLabel = uri.toString()
                )
            } catch (t: Throwable) {
                writer.abort()
                throw t
            }
        }
    }

    suspend fun importSlideshow(
        uris: List<Uri>,
        onProgress: (Float) -> Unit = {}
    ): LivingFrameImportResult = withContext(Dispatchers.IO) {
        importLock.withLock {
            val picked = uris.take(LivingFramesRepository.MAX_SLIDES)
            require(picked.isNotEmpty()) { "None of those photos could be read." }
            val writer = newWriter(
                maxFrames = LivingFramesRepository.MAX_SLIDES,
                quality = 88
            )
            var skipped = 0
            try {
                picked.forEachIndexed { i, uri ->
                    currentCoroutineContext().ensureActive()
                    val bmp = loadDownscaled(uri, LivingFramesRepository.STORE_SIDE_PHOTO)
                    if (bmp == null) {
                        skipped++
                    } else {
                        writer.add(bmp)
                    }
                    onProgress((i + 1) / picked.size.toFloat())
                }
                require(writer.count > 0) { "None of those photos could be read." }
                onProgress(1f)
                val note = when {
                    uris.size > LivingFramesRepository.MAX_SLIDES ->
                        "Slideshows hold up to ${LivingFramesRepository.MAX_SLIDES} photos; the rest were left out."
                    skipped > 0 ->
                        "$skipped photo${if (skipped == 1) "" else "s"} couldn't be read and were skipped."
                    else -> null
                }
                writer.finish(
                    name = "Slideshow",
                    type = LivingFrameType.SLIDESHOW,
                    fps = 1,
                    sourceLabel = "${writer.count} photos",
                    note = note
                )
            } catch (t: Throwable) {
                writer.abort()
                throw t
            }
        }
    }

    // -- GIF / animated image decoding ------------------------------------

    private suspend fun decodeMovieToWriter(
        uri: Uri,
        onProgress: (Float) -> Unit
    ): Writer? {
        val writer = newWriter(
            maxFrames = LivingFramesRepository.MAX_FRAMES,
            quality = 84,
            trackMotion = true
        )
        try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                @Suppress("DEPRECATION")
                val movie = Movie.decodeStream(ins)
                if (movie == null) {
                    writer.abort()
                    return null
                }
                if (movie.width() <= 0 || movie.height() <= 0 || movie.duration() <= 0) {
                    writer.abort()
                    return null
                }
                val w = movie.width()
                val h = movie.height()
                val dur = movie.duration()
                writer.setDuration(dur.toLong())
                val side = LivingFramesRepository.STORE_SIDE_GIF
                val scale = minOf(1f, side / maxOf(w, h).toFloat())
                val tw = (w * scale).roundToInt().coerceAtLeast(1)
                val th = (h * scale).roundToInt().coerceAtLeast(1)
                val count = (dur / 1000f * 15f).roundToInt()
                    .coerceIn(2, LivingFramesRepository.MAX_FRAMES)
                for (i in 0 until count) {
                    currentCoroutineContext().ensureActive()
                    val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                    try {
                        movie.setTime((i.toLong() * dur / count).toInt())
                        val canvas = Canvas(bmp)
                        canvas.scale(tw / w.toFloat(), th / h.toFloat())
                        movie.draw(canvas, 0f, 0f)
                        if (isBlank(bmp)) {
                            bmp.recycle()
                        } else {
                            writer.add(bmp)
                        }
                    } catch (t: Throwable) {
                        if (!bmp.isRecycled) bmp.recycle()
                        throw t
                    }
                    onProgress((i + 1) / count.toFloat() * 0.6f)
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException || t is LivingFrameStorageException || t is OutOfMemoryError) {
                writer.abort()
                throw t
            }
            Log.w(TAG, "Movie decoder failed; trying ImageDecoder", t)
            writer.abort()
            return null
        }
        if (writer.count == 0) {
            writer.abort()
            return null
        }
        return writer
    }

    private suspend fun decodeAnimatedImageToWriter(
        uri: Uri,
        onProgress: (Float) -> Unit
    ): Writer? {
        if (Build.VERSION.SDK_INT < 28) return null
        val writer = newWriter(
            maxFrames = LivingFramesRepository.MAX_FRAMES,
            quality = 84,
            trackMotion = true
        )
        var drawable: AnimatedImageDrawable? = null
        try {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            val decoded = ImageDecoder.decodeDrawable(src)
            if (decoded !is AnimatedImageDrawable) {
                writer.abort()
                return null
            }
            drawable = decoded
            val w = decoded.intrinsicWidth.coerceAtLeast(1)
            val h = decoded.intrinsicHeight.coerceAtLeast(1)
            decoded.setBounds(0, 0, w, h)
            val side = LivingFramesRepository.STORE_SIDE_GIF
            val scale = minOf(1f, side / maxOf(w, h).toFloat())
            val tw = (w * scale).roundToInt().coerceAtLeast(1)
            val th = (h * scale).roundToInt().coerceAtLeast(1)
            val fps = 12
            val count = 36
            writer.setDuration(count * 1000L / fps)
            decoded.start()
            for (i in 0 until count) {
                currentCoroutineContext().ensureActive()
                val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bmp)
                    canvas.scale(tw / w.toFloat(), th / h.toFloat())
                    decoded.draw(canvas)
                    if (isBlank(bmp)) {
                        bmp.recycle()
                    } else {
                        writer.add(bmp)
                    }
                } catch (t: Throwable) {
                    if (!bmp.isRecycled) bmp.recycle()
                    throw t
                }
                onProgress((i + 1) / count.toFloat() * 0.6f)
                delay(1000L / fps)
            }
            if (writer.count == 0) {
                writer.abort()
                return null
            }
            return writer
        } catch (t: Throwable) {
            if (t is CancellationException || t is LivingFrameStorageException || t is OutOfMemoryError) {
                writer.abort()
                throw t
            }
            Log.w(TAG, "ImageDecoder animation failed", t)
            writer.abort()
            return null
        } finally {
            runCatching { drawable?.stop() }
        }
    }

    private suspend fun finishAnimated(
        writer: Writer,
        onProgress: (Float) -> Unit,
        uri: Uri
    ): LivingFrameImportResult {
        return try {
            onProgress(1f)
            val moving = writer.count > 1 && writer.hasMotion
            if (!moving) {
                writer.finish(
                    name = "Photo Frame",
                    type = LivingFrameType.PHOTO,
                    fps = 1,
                    sourceLabel = uri.toString(),
                    note = if (writer.count <= 1) {
                        "That GIF has a single frame, so it was added as a photo."
                    } else {
                        "That image doesn't move, so it was added as a photo."
                    }
                )
            } else {
                val fps = LivingFrameFps.snap(
                    writer.count * 1000f / writer.durationMs.coerceAtLeast(1L)
                )
                writer.finish(
                    name = "GIF Loop",
                    type = LivingFrameType.GIF,
                    fps = fps,
                    sourceLabel = uri.toString()
                )
            }
        } catch (t: Throwable) {
            writer.abort()
            throw t
        }
    }

    // -- video fallback ---------------------------------------------------

    private suspend fun videoAttempt(
        uri: Uri,
        start: Long,
        len: Long,
        count: Int,
        useCodec: Boolean,
        onProgress: (Float) -> Unit
    ): LivingFrameImportResult? {
        val writer = newWriter(
            maxFrames = LivingFramesRepository.MAX_FRAMES,
            quality = 82,
            trackMotion = true
        )
        try {
            if (useCodec) {
                codec.extractTo(
                    uri = uri,
                    startMs = start,
                    lengthMs = len,
                    count = count,
                    targetSide = LivingFramesRepository.STORE_SIDE_VIDEO,
                    // The extractor may reuse one decoded frame for several
                    // playback slots; it owns the bitmap until all are encoded.
                    onFrame = { writer.add(it, takeOwnership = false) },
                    onProgress = { onProgress(it * 0.6f) }
                )
            } else {
                retrieverToWriter(uri, start, len, count, writer) {
                    onProgress(it * 0.6f)
                }
            }
            if (writer.count < 2 || !writer.hasMotion) {
                writer.abort()
                return null
            }
            val fps = LivingFrameFps.snap(writer.count * 1000f / len)
            onProgress(1f)
            return writer.finish(
                name = "Video Loop",
                type = LivingFrameType.VIDEO,
                fps = fps,
                sourceLabel = uri.toString()
            )
        } catch (t: Throwable) {
            writer.abort()
            if (t is CancellationException || t is LivingFrameStorageException || t is OutOfMemoryError) {
                throw t
            }
            Log.w(TAG, "Video ${if (useCodec) "decoder" else "retriever"} failed", t)
            return null
        }
    }

    private suspend fun retrieverToWriter(
        uri: Uri,
        startMs: Long,
        lengthMs: Long,
        count: Int,
        writer: Writer,
        onProgress: (Float) -> Unit
    ) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            for (i in 0 until count) {
                currentCoroutineContext().ensureActive()
                val bmp = try {
                    val tUs = (startMs + i * lengthMs / count) * 1000L
                    r.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST)
                } catch (e: OutOfMemoryError) {
                    Log.w(TAG, "Out of memory in video fallback", e)
                    throw e
                }
                if (bmp != null) {
                    writer.add(downscale(bmp, LivingFramesRepository.STORE_SIDE_VIDEO))
                }
                onProgress((i + 1) / count.toFloat())
            }
        } finally {
            runCatching { r.release() }
        }
    }

    // -- writer ------------------------------------------------------------

    private fun newWriter(
        maxFrames: Int,
        quality: Int,
        trackMotion: Boolean = false
    ): Writer = Writer(maxFrames, quality, trackMotion)

    private inner class Writer(
        private val maxFrames: Int,
        private val quality: Int,
        private val trackMotion: Boolean
    ) {
        private val id = repo.newId()
        // Staging only: the live directory is created atomically on finish.
        private val dir: File = repo.stagingDir(id).apply { mkdirs() }
        private var published = false
        var count: Int = 0
            private set
        var width: Int = 0
            private set
        var height: Int = 0
            private set
        var durationMs: Long = 0L
            private set
        fun setDuration(duration: Long) {
            durationMs = duration.coerceAtLeast(0L)
        }
        private var totalBytes = 0L
        private var firstHash: Long? = null
        var hasMotion: Boolean = false
            private set

        suspend fun add(bitmap: Bitmap, takeOwnership: Boolean = true) {
            try {
                currentCoroutineContext().ensureActive()
                if (count >= maxFrames) {
                    throw LivingFrameStorageException("That set has too many frames.")
                }
                val estimate = (bitmap.byteCount.toLong() / 2).coerceIn(64L * 1024, 8L * 1024 * 1024)
                if (count == 0 || count % 4 == 0) {
                    val check = repo.checkStorage(estimate)
                    if (!check.ok) {
                        throw LivingFrameStorageException(check.message ?: "Not enough storage.")
                    }
                }
                if (totalBytes + estimate > LivingFramesRepository.MAX_SET_BYTES) {
                    throw LivingFrameStorageException("That set is too large to store.")
                }
                val file = File(dir, String.format(Locale.US, "frame_%04d.webp", count))
                FileOutputStream(file).use { out ->
                    if (!bitmap.compress(webp(), quality, out)) {
                        throw IOException("Couldn't encode a frame.")
                    }
                    out.fd.sync()
                }
                if (file.length() <= 0L) throw IOException("Couldn't encode a frame.")
                totalBytes += file.length()
                if (totalBytes > LivingFramesRepository.MAX_SET_BYTES) {
                    throw LivingFrameStorageException("That set is too large to store.")
                }
                if (count == 0) {
                    width = bitmap.width
                    height = bitmap.height
                    writeCover(bitmap)
                }
                val h = hash(bitmap)
                if (count == 0) firstHash = h
                if (trackMotion && h != null && firstHash != null &&
                    java.lang.Long.bitCount(h xor firstHash!!) > 2
                ) {
                    hasMotion = true
                }
                count++
            } finally {
                if (takeOwnership) {
                    runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
                }
            }
        }

        private fun writeCover(bitmap: Bitmap) {
            val cover = downscaleCopy(bitmap, LivingFramesRepository.COVER_SIDE)
            try {
                FileOutputStream(File(dir, "cover.webp")).use { out ->
                    if (!cover.compress(webp(), 80, out)) {
                        throw IOException("Couldn't encode a cover image.")
                    }
                    out.fd.sync()
                }
            } finally {
                if (cover !== bitmap && !cover.isRecycled) cover.recycle()
            }
        }

        suspend fun finish(
            name: String,
            type: LivingFrameType,
            fps: Int,
            sourceLabel: String,
            note: String? = null
        ): LivingFrameImportResult {
            if (count <= 0) throw IOException("Nothing to import")
            val check = repo.checkStorage(0L)
            if (!check.ok) {
                throw LivingFrameStorageException(check.message ?: "Not enough storage.")
            }
            // A still GIF can arrive as several identical frames from Movie.
            // Keep only frame 0 so PHOTO metadata and storage stay truthful.
            if (type == LivingFrameType.PHOTO && count > 1) {
                for (i in 1 until count) {
                    runCatching { File(dir, String.format(Locale.US, "frame_%04d.webp", i)).delete() }
                }
                count = 1
                totalBytes = File(dir, String.format(Locale.US, "frame_%04d.webp", 0)).length()
            }
            val frame = LivingFrame(
                id = id,
                name = name,
                type = type,
                frameCount = count,
                width = width.coerceAtLeast(1),
                height = height.coerceAtLeast(1),
                fps = LivingFrameFps.snap(fps),
                avgFrameBytes = totalBytes / count.coerceAtLeast(1),
                sourceLabel = sourceLabel.take(160)
            )
            // Once the files are in place, finish the metadata swap even if the
            // caller is being cancelled. Otherwise a cancellation at exactly
            // this point could leave new files with old/no metadata, or delete
            // a set that was already published.
            withContext(NonCancellable + Dispatchers.IO) {
                val previous = repo.publishImport(id)
                published = true
                try {
                    repo.upsert(frame)
                    // Metadata is now durable; the old copy is disposable. A
                    // failure here only leaves an orphan for the cleanup pass.
                    runCatching { previous?.deleteRecursively() }
                } catch (t: Throwable) {
                    val target = repo.frameDir(id)
                    runCatching { target.deleteRecursively() }
                    if (previous != null && !target.exists() && !previous.renameTo(target)) {
                        Log.e(TAG, "Could not restore previous files for $id", t)
                    }
                    published = false
                    throw t
                }
            }
            return LivingFrameImportResult(frame, note)
        }

        fun abort() {
            // Non-suspending on purpose: this runs from cancellation handlers,
            // where a suspending withContext would throw before deleting.
            if (!published) runCatching { dir.deleteRecursively() }
        }
    }

    // -- image loading (EXIF-aware) ---------------------------------------

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
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Out of memory loading a photo", e)
        null
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        null
    }

    private fun legacyDecode(uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0) return null
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
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
        } catch (_: Exception) {
            0f
        }
        if (degrees == 0f) return bmp
        val rotated = Bitmap.createBitmap(
            bmp, 0, 0, bmp.width, bmp.height,
            Matrix().apply { postRotate(degrees) }, true
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
                src,
                (src.width * s).roundToInt().coerceAtLeast(1),
                (src.height * s).roundToInt().coerceAtLeast(1),
                true
            )
            if (scaled !== src) src.recycle()
            scaled
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory scaling a frame", e)
            src
        }
    }

    private fun downscaleCopy(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val s = maxSide / longest.toFloat()
        return Bitmap.createScaledBitmap(
            src,
            (src.width * s).roundToInt().coerceAtLeast(1),
            (src.height * s).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
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
                    if ((bmp.getPixel(x, y) ushr 24) > 8) {
                        seen = true
                        break
                    }
                    x += step
                }
                y += step
            }
            !seen
        }
    } catch (_: Exception) {
        true
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
        for (v in lum) {
            h = h shl 1
            if (v > avg) h = h or 1L
        }
        h
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val TAG = "LivingFrameImporter"
    }
}
