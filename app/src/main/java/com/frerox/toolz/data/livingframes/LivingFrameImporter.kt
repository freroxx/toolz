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
import com.frerox.toolz.R
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
 * Memory rule: frames are encoded and written one at a time. Holding every
 * source bitmap until the whole set was stored would need hundreds of
 * megabytes for a 30 s high-resolution clip and crash on low-end devices.
 * The import is staged in <id>.tmp and atomically published only after the
 * last frame is durable.
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
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            // Container dimensions are the coded frame; a 90°/270° matrix
            // swaps the displayed axes. Without this the clip sheet reports
            // a portrait clip as landscape and sizes the stored copy wrong.
            val swap = rotation % 180 != 0
            VideoProbe(
                dur,
                if (swap) h else w,
                if (swap) w else h
            )
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
                val rotation = r.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull() ?: 0
                val rawW = r.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull() ?: 0
                val rawH = r.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull() ?: 0
                val portrait = displayPortrait(rawW, rawH, rotation)
                r.getFrameAtTime(
                    atMs.coerceAtLeast(0L) * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )?.let { downscale(oriented(it, rotation, portrait), maxSide) }
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
        quality: LivingFrameQuality = LivingFrameQuality.DEFAULT,
        onProgress: (Float) -> Unit = {}
    ): LivingFrameImportResult = withContext(Dispatchers.IO) {
        importLock.withLock {
            val movie = decodeMovieToWriter(uri, quality, onProgress)
            if (movie != null) return@withLock finishAnimated(movie, onProgress, uri)

            val animated = decodeAnimatedImageToWriter(uri, quality, onProgress)
            if (animated != null) return@withLock finishAnimated(animated, onProgress, uri)

            val still = loadDownscaled(uri, quality.storedSide)
                ?: throw IllegalArgumentException(context.getString(R.string.lw_import_gif_failed))
            val writer = newWriter(
                maxFrames = LivingFramesRepository.MAX_FRAMES,
                quality = quality
            )
            try {
                writer.add(still)
                onProgress(1f)
                writer.finish(
                    name = context.getString(R.string.lw_import_name_photo),
                    type = LivingFrameType.PHOTO,
                    fps = 1,
                    sourceLabel = uri.toString(),
                    note = context.getString(R.string.lw_import_note_gif_single)
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
        quality: LivingFrameQuality = LivingFrameQuality.DEFAULT,
        targetFps: Int = 0,
        onProgress: (Float) -> Unit = {}
    ): LivingFrameImportResult {
        // Probe outside the lock: it is cheap metadata-only work and lets us
        // clamp a too-long start/length to the real clip.
        val probe = probeVideo(uri)
        return withContext(Dispatchers.IO) {
            importLock.withLock {
                val duration = probe?.durationMs?.takeIf { it > 0L }
                val minLen = minOf(500L, duration ?: 500L).coerceAtLeast(150L)
                val len = lengthMs.coerceIn(minLen, LivingFramesRepository.MAX_CLIP_MS)
                    .coerceAtMost(duration ?: LivingFramesRepository.MAX_CLIP_MS)
                    .coerceAtLeast(150L)
                val start = if (duration != null) {
                    startMs.coerceIn(0L, (duration - len).coerceAtLeast(0L))
                } else {
                    startMs.coerceAtLeast(0L)
                }
                // Respect the user's rate, but never capture more frames than
                // the library can store, and never more than the preset allows.
                val wanted = if (targetFps > 0) targetFps.toFloat() else quality.maxCaptureFps.toFloat()
                val fps = LivingFrameFps.atMost(
                    minOf(
                        wanted,
                        quality.maxCaptureFps.toFloat(),
                        LivingFramesRepository.MAX_FRAMES * 1000f / len
                    )
                )
                val count = (len / 1000f * fps).roundToInt()
                    .coerceIn(2, LivingFramesRepository.MAX_FRAMES)

                val fromCodec = videoAttempt(
                    uri, start, len, count, quality,
                    useCodec = true, onProgress
                )
                if (fromCodec != null) return@withLock fromCodec
                val fallback = videoAttempt(
                    uri, start, len, count, quality,
                    useCodec = false, onProgress
                )
                if (fallback != null) return@withLock fallback

                throw IllegalArgumentException(context.getString(R.string.lw_import_no_motion))
            }
        }
    }

    suspend fun importPhoto(
        uri: Uri,
        quality: LivingFrameQuality = LivingFrameQuality.DEFAULT,
        onProgress: (Float) -> Unit = {}
    ): LivingFrameImportResult = withContext(Dispatchers.IO) {
        importLock.withLock {
            val bmp = loadDownscaled(uri, quality.photoSide)
                ?: throw IllegalArgumentException(context.getString(R.string.lw_import_image_failed))
            val writer = newWriter(maxFrames = 1, quality = quality)
            try {
                writer.add(bmp)
                onProgress(1f)
                writer.finish(
                    name = context.getString(R.string.lw_import_name_photo),
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
        quality: LivingFrameQuality = LivingFrameQuality.DEFAULT,
        onProgress: (Float) -> Unit = {}
    ): LivingFrameImportResult = withContext(Dispatchers.IO) {
        importLock.withLock {
            val picked = uris.take(LivingFramesRepository.MAX_SLIDES)
            require(picked.isNotEmpty()) { context.getString(R.string.lw_import_photos_failed) }
            val writer = newWriter(
                maxFrames = LivingFramesRepository.MAX_SLIDES,
                quality = quality
            )
            var skipped = 0
            try {
                picked.forEachIndexed { i, uri ->
                    currentCoroutineContext().ensureActive()
                    val bmp = loadDownscaled(uri, quality.photoSide)
                    if (bmp == null) {
                        skipped++
                    } else {
                        writer.add(bmp)
                    }
                    onProgress((i + 1) / picked.size.toFloat())
                }
                require(writer.count > 0) { context.getString(R.string.lw_import_photos_failed) }
                onProgress(1f)
                val note = when {
                    uris.size > LivingFramesRepository.MAX_SLIDES ->
                        context.getString(
                            R.string.lw_import_note_slides_limit,
                            LivingFramesRepository.MAX_SLIDES
                        )
                    skipped > 0 -> context.resources.getQuantityString(
                        R.plurals.lw_skipped_photos,
                        skipped,
                        skipped
                    )
                    else -> null
                }
                writer.finish(
                    name = context.getString(R.string.lw_import_name_slideshow),
                    type = LivingFrameType.SLIDESHOW,
                    fps = 1,
                    sourceLabel = context.resources.getQuantityString(
                        R.plurals.lw_photos_count,
                        writer.count,
                        writer.count
                    ),
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
        quality: LivingFrameQuality,
        onProgress: (Float) -> Unit
    ): Writer? {
        val writer = newWriter(
            maxFrames = LivingFramesRepository.MAX_FRAMES,
            quality = quality,
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
                val side = quality.storedSide
                val scale = minOf(1f, side / maxOf(w, h).toFloat())
                val tw = (w * scale).roundToInt().coerceAtLeast(1)
                val th = (h * scale).roundToInt().coerceAtLeast(1)
                // GIF movies have unreliable per-frame timing, so 30 fps is
                // the practical ceiling; MAX_FRAMES still clamps long clips.
                // Pick down (not up) so a 40 s GIF stores 800 frames at 20 fps
                // and keeps its real duration instead of 960 frames clamped to
                // 900 and played back slightly fast.
                val fps = LivingFrameFps.atMost(
                    minOf(
                        30f,
                        quality.maxCaptureFps.toFloat(),
                        LivingFramesRepository.MAX_FRAMES * 1000f / dur
                    )
                )
                val count = (dur / 1000f * fps).roundToInt()
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
        quality: LivingFrameQuality,
        onProgress: (Float) -> Unit
    ): Writer? {
        if (Build.VERSION.SDK_INT < 28) return null
        val writer = newWriter(
            maxFrames = LivingFramesRepository.MAX_FRAMES,
            quality = quality,
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
            val side = quality.storedSide
            val scale = minOf(1f, side / maxOf(w, h).toFloat())
            val tw = (w * scale).roundToInt().coerceAtLeast(1)
            val th = (h * scale).roundToInt().coerceAtLeast(1)
            // AnimatedImageDrawable does not expose the clip duration. Sample
            // three seconds at up to 15 fps instead of the old fixed 12 fps /
            // 36 frames; slower GIFs would only duplicate frames anyway.
            val fps = minOf(15, quality.maxCaptureFps)
            val count = (fps * 3).coerceIn(2, LivingFramesRepository.MAX_FRAMES)
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
                    name = context.getString(R.string.lw_import_name_photo),
                    type = LivingFrameType.PHOTO,
                    fps = 1,
                    sourceLabel = uri.toString(),
                    note = context.getString(
                        if (writer.count <= 1) R.string.lw_import_note_gif_single
                        else R.string.lw_import_note_image_still
                    )
                )
            } else {
                val fps = LivingFrameFps.snap(
                    writer.count * 1000f / writer.durationMs.coerceAtLeast(1L)
                )
                writer.finish(
                    name = context.getString(R.string.lw_import_name_gif),
                    type = LivingFrameType.GIF,
                    fps = fps,
                    capturedFps = fps,
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
        quality: LivingFrameQuality,
        useCodec: Boolean,
        onProgress: (Float) -> Unit
    ): LivingFrameImportResult? {
        val writer = newWriter(
            maxFrames = LivingFramesRepository.MAX_FRAMES,
            quality = quality,
            trackMotion = true
        )
        try {
            if (useCodec) {
                codec.extractTo(
                    uri = uri,
                    startMs = start,
                    lengthMs = len,
                    count = count,
                    targetSide = quality.storedSide,
                    // The extractor may reuse one decoded frame for several
                    // playback slots; it owns the bitmap until all are encoded.
                    onFrame = { writer.add(it, takeOwnership = false) },
                    onProgress = { onProgress(it * 0.6f) }
                )
            } else {
                retrieverToWriter(uri, start, len, count, quality, writer) {
                    onProgress(it * 0.6f)
                }
            }
            if (writer.count < 2 || !writer.hasMotion) {
                writer.abort()
                return null
            }
            onProgress(1f)
            // Base the stored rate on what actually landed on disk. A codec
            // timeout or a missing retriever frame must not claim a higher
            // capture rate than the files contain, or playback would run fast.
            val actualFps = LivingFrameFps.snap(writer.count * 1000f / len.coerceAtLeast(1L))
            return writer.finish(
                name = context.getString(R.string.lw_import_name_video),
                type = LivingFrameType.VIDEO,
                fps = actualFps,
                capturedFps = actualFps,
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
        quality: LivingFrameQuality,
        writer: Writer,
        onProgress: (Float) -> Unit
    ) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val rotation = r.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
            val rawW = r.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            val rawH = r.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            val portrait = displayPortrait(rawW, rawH, rotation)
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
                    writer.add(
                        downscale(
                            oriented(bmp, rotation, portrait),
                            quality.storedSide
                        )
                    )
                }
                onProgress((i + 1) / count.toFloat())
            }
        } finally {
            runCatching { r.release() }
        }
    }

    /**
     * Whether the *displayed* video is portrait. Coded dimensions are swapped
     * for 90°/270° matrix rotations.
     */
    private fun displayPortrait(width: Int, height: Int, rotation: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        return if (rotation % 180 != 0) width > height else height > width
    }

    /**
     * Applies the container rotation to a retriever frame. Some platform
     * versions already return display-oriented frames; for 90°/270° the
     * bitmap's aspect tells us whether another rotation would double it.
     * 180° cannot be detected from the aspect, so it is always applied.
     */
    private fun oriented(bitmap: Bitmap, rotation: Int, displayPortrait: Boolean): Bitmap {
        val normalized = ((rotation % 360) + 360) % 360
        if (normalized == 0) return bitmap
        if (normalized % 180 != 0) {
            val bitmapPortrait = bitmap.height > bitmap.width
            if (bitmapPortrait == displayPortrait) return bitmap
        }
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(normalized.toFloat()) }, true
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    // -- writer ------------------------------------------------------------

    private fun newWriter(
        maxFrames: Int,
        quality: LivingFrameQuality,
        trackMotion: Boolean = false
    ): Writer = Writer(maxFrames, quality, trackMotion)

    private inner class Writer(
        private val maxFrames: Int,
        private val quality: LivingFrameQuality,
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
        private var previousHash: Long? = null
        private var startUsedBytes = -1L
        var hasMotion: Boolean = false
            private set

        suspend fun add(bitmap: Bitmap, takeOwnership: Boolean = true) {
            try {
                currentCoroutineContext().ensureActive()
                if (count >= maxFrames) {
                    throw LivingFrameStorageException(context.getString(R.string.lw_import_too_many_frames))
                }
                if (startUsedBytes < 0L) startUsedBytes = repo.usageBytes()
                val estimate = (bitmap.byteCount.toLong() / 2)
                    .coerceIn(64L * 1024, 8L * 1024 * 1024)
                // One snapshot plus the bytes we wrote. The old code called
                // checkStorage() every four frames, which walked every stored
                // set (and built orphan lists) dozens of times per import.
                val projected = startUsedBytes + totalBytes + estimate
                if (projected > LivingFramesRepository.MAX_STORE_BYTES) {
                    throw LivingFrameStorageException(
                        context.getString(R.string.lw_import_storage_full)
                    )
                }
                if (count == 0 || count % 8 == 0) {
                    // statfs is cheap; this catches other apps filling the
                    // device without rescanning our own directory tree.
                    val free = context.filesDir.usableSpace.coerceAtLeast(0L)
                    if (free - estimate < LivingFramesRepository.MIN_FREE_BYTES) {
                        throw LivingFrameStorageException(
                            context.getString(
                                R.string.lw_import_need_space,
                                LivingFramesRepository.formatBytes(
                                    LivingFramesRepository.MIN_FREE_BYTES
                                )
                            )
                        )
                    }
                }
                if (totalBytes + estimate > LivingFramesRepository.MAX_SET_BYTES) {
                    throw LivingFrameStorageException(context.getString(R.string.lw_import_set_too_large))
                }
                val file = File(dir, String.format(Locale.US, "frame_%04d.webp", count))
                FileOutputStream(file).use { out ->
                    if (!bitmap.compress(webp(), quality.webpQuality, out)) {
                        throw IOException(context.getString(R.string.lw_import_frame_encode_failed))
                    }
                    // No per-frame fsync: these files are staging data until
                    // finish() publishes the directory. A crash leaves an
                    // orphan for cleanup, never a half-published set.
                }
                if (file.length() <= 0L) throw IOException(context.getString(R.string.lw_import_frame_encode_failed))
                totalBytes += file.length()
                if (totalBytes > LivingFramesRepository.MAX_SET_BYTES) {
                    throw LivingFrameStorageException(context.getString(R.string.lw_import_set_too_large))
                }
                if (count == 0) {
                    width = bitmap.width
                    height = bitmap.height
                    writeCover(bitmap)
                    startUsedBytes += File(dir, "cover.webp").length()
                }
                // Compare consecutive frames, not every frame with frame 0.
                // A slow pan that returns near its start still moves, and a
                // one-bit dHash change is enough to prove it is not a still.
                val h = hash(bitmap)
                if (trackMotion && h != null && previousHash != null &&
                    java.lang.Long.bitCount(h xor previousHash!!) > 1
                ) {
                    hasMotion = true
                }
                previousHash = h
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
                        throw IOException(context.getString(R.string.lw_import_cover_failed))
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
            capturedFps: Int = 0,
            note: String? = null
        ): LivingFrameImportResult {
            if (count <= 0) throw IOException(context.getString(R.string.lw_import_nothing))
            // A still GIF can arrive as several identical frames from Movie.
            // Keep only frame 0 so PHOTO metadata and storage stay truthful.
            if (type == LivingFrameType.PHOTO && count > 1) {
                for (i in 1 until count) {
                    runCatching { File(dir, String.format(Locale.US, "frame_%04d.webp", i)).delete() }
                }
                count = 1
                totalBytes = File(dir, String.format(Locale.US, "frame_%04d.webp", 0)).length()
            }
            // The writer has tracked every byte it wrote; do not walk the
            // whole library again here. Free space is a single statfs call.
            if (startUsedBytes < 0L) startUsedBytes = repo.usageBytes()
            if (startUsedBytes + totalBytes > LivingFramesRepository.MAX_STORE_BYTES) {
                throw LivingFrameStorageException(
                    context.getString(R.string.lw_import_storage_full)
                )
            }
            if (context.filesDir.usableSpace < LivingFramesRepository.MIN_FREE_BYTES) {
                throw LivingFrameStorageException(
                    context.getString(
                        R.string.lw_import_need_space,
                        LivingFramesRepository.formatBytes(LivingFramesRepository.MIN_FREE_BYTES)
                    )
                )
            }
            val animated = (type == LivingFrameType.GIF || type == LivingFrameType.VIDEO) &&
                    count > 1
            val frame = LivingFrame(
                id = id,
                name = name,
                type = type,
                frameCount = count,
                width = width.coerceAtLeast(1),
                height = height.coerceAtLeast(1),
                fps = LivingFrameFps.snap(fps),
                avgFrameBytes = totalBytes / count.coerceAtLeast(1),
                sourceLabel = sourceLabel.take(160),
                capturedFps = capturedFps.coerceIn(0, LivingFrameFps.MAX),
                quality = quality,
                // Animations are useful only if a tap can stop them on the
                // exact frame the user saw. Stills keep the neutral default.
                tapAction = if (animated) {
                    LivingFrameTapAction.PAUSE_PLAY
                } else {
                    LivingFrameTapAction.OPEN_PREVIEW
                },
                // Hard cuts are the neutral default. A seam transition is an
                // explicit style choice: fading every loop to frame zero is
                // exactly what makes a continuous video look like it restarts.
                loopTransition = LivingFrameLoopTransition.NONE
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
