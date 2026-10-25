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

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/** Source kind for a Living Widget set. */
@Serializable
enum class LivingFrameType { GIF, VIDEO, PHOTO, SLIDESHOW }

/**
 * Loop behaviour. ONCE was removed: a launcher-hosted flipper cannot stop on
 * its last frame. Old JSON containing "ONCE" decodes to the default (LOOP)
 * because the repository's Json uses coerceInputValues.
 */
@Serializable
enum class LivingFrameLoopMode { LOOP, BOOMERANG }

/**
 * Softens the jump from the last frame back to the first. FADE is a straight
 * alpha crossfade; BLUR is a cheap soft-focus dissolve. NONE is the default
 * for existing sets, so their playback does not change after an update.
 * BOOMERANG and single-frame sets ignore it.
 */
@Serializable
enum class LivingFrameLoopTransition { NONE, FADE, BLUR }

/** FILL = centre-crop to the widget, FIT = letterbox inside it. */
@Serializable
enum class LivingFrameFit { FIT, FILL }

/**
 * Tap behaviour. NEXT_FRAME means "next photo" and only applies to slideshows.
 * OPEN_TOOL opens any Toolz tool via [LivingFrame.tapTargetRoute].
 * PAUSE_PLAY pauses an animated widget on the exact frame that was tapped;
 * tapping the paused widget resumes from that same frame. It only works when
 * the set fits the single-update (static) path.
 */
@Serializable
enum class LivingFrameTapAction { OPEN_PREVIEW, OPEN_TOOL, NEXT_FRAME, NOTHING, PAUSE_PLAY }

/**
 * Import/export quality. This is chosen when a set is imported: it decides how
 * large each stored WebP frame is and how aggressively it is compressed.
 * Raising it later cannot invent pixels that were never decoded, so the widget
 * tool shows the stored resolution instead of pretending the setting is live.
 *
 * [maxCaptureFps] is the most frames per second the importer will extract for
 * this preset. A 30-second clip at 60 fps is 1,800 frames, so the importer
 * still clamps to [LivingFramesRepository.MAX_FRAMES].
 *
 * [storedSide] and [photoSide] are the longest stored edge. That keeps a
 * landscape 1080p clip at 1920×1080 on High instead of shrinking it to
 * 1080×608, which is what made wide widgets look soft.
 */
@Serializable
enum class LivingFrameQuality(
    val label: String,
    val storedSide: Int,
    val photoSide: Int,
    val webpQuality: Int,
    val maxCaptureFps: Int
) {
    COMPACT("Standard", 1280, 1280, 86, 15),
    HIGH("High", 1920, 1920, 92, 30),
    ULTRA("Maximum", 2560, 2560, 96, 60);

    companion object {
        val DEFAULT = HIGH
    }
}

/**
 * Playback speed multipliers. Speed is applied by resampling the display
 * sequence, never by changing the stored frames, so it is instant and
 * reversible. 0.25× is slow motion; 4× is a fast loop.
 */
object LivingFrameSpeed {
    val STEPS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)
    const val DEFAULT = 1f
    const val MIN = 0.25f
    const val MAX = 4f

    fun snap(speed: Float): Float =
        STEPS.minByOrNull { abs(it - speed) } ?: DEFAULT

    fun index(speed: Float): Int = STEPS.indexOf(snap(speed)).coerceAtLeast(0)

    fun label(speed: Float): String {
        val v = snap(speed)
        return if (abs(v - v.toInt()) < 0.001f) "×${v.toInt()}.0" else "×$v"
    }
}

/**
 * Widget render resolution. 100% uses the stored frame's native size; lower
 * values decode and render smaller, which is the honest way to trade detail
 * for battery and memory. Values above 100% would only upscale a stored copy.
 */
object LivingFrameResolution {
    const val MIN = 40
    const val MAX = 100
    const val STEP = 5
    val STEPS = (MIN..MAX step STEP).toList()
    const val DEFAULT = 100

    fun snap(percent: Int): Int =
        ((percent / STEP.toFloat()).roundToInt() * STEP).coerceIn(MIN, MAX)
}

/**
 * Widget corner radius in dp. 0 is a sharp rectangle; 48 is a pill on the
 * smallest 1×1 cell. The slider and the stored metadata share these bounds so
 * a value can never be saved that the editor cannot represent.
 */
object LivingFrameCorners {
    const val MIN = 0
    const val MAX = 48
    const val DEFAULT = 24

    fun snap(dp: Int): Int = dp.coerceIn(MIN, MAX)
}

/**
 * Persisted set. Pixels live as files under files/living_frames/<id>/ —
 * never in this JSON.
 */
@Serializable
data class LivingFrame(
    val id: String,
    val name: String,
    val type: LivingFrameType,
    val frameCount: Int,
    val width: Int,
    val height: Int,
    val fps: Int = LivingFrameFps.DEFAULT,
    val loopMode: LivingFrameLoopMode = LivingFrameLoopMode.LOOP,
    val fit: LivingFrameFit = LivingFrameFit.FILL,
    val opacity: Float = 1f,
    val cornerRadiusDp: Int = LivingFrameCorners.DEFAULT,
    val tapAction: LivingFrameTapAction = LivingFrameTapAction.OPEN_PREVIEW,
    /** Route opened when [tapAction] is OPEN_TOOL. */
    val tapTargetRoute: String = "",
    val slideshowIntervalMin: Int = 60,
    val shuffle: Boolean = false,
    val showCaption: Boolean = false,
    val avgFrameBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceLabel: String = "",
    /** Frames actually extracted per second. 0 on old metadata means "same as fps". */
    val capturedFps: Int = 0,
    val quality: LivingFrameQuality = LivingFrameQuality.DEFAULT,
    /** Playback multiplier. Applied by resampling, not by re-encoding frames. */
    val speed: Float = LivingFrameSpeed.DEFAULT,
    /** Render resolution as a percentage of the stored frame size. */
    val resolutionPercent: Int = LivingFrameResolution.DEFAULT,
    /** Softens the loop seam. Ignored by boomerang and single-frame sets. */
    val loopTransition: LivingFrameLoopTransition = LivingFrameLoopTransition.NONE
) {
    val isAnimated: Boolean
        get() = (type == LivingFrameType.GIF || type == LivingFrameType.VIDEO) && frameCount > 1

    val isSlideshow: Boolean get() = type == LivingFrameType.SLIDESHOW

    /** Bytes the stored frames occupy on disk (approximate for old metadata). */
    val storedBytes: Long get() = (avgFrameBytes.coerceAtLeast(0L) * frameCount)
        .coerceAtLeast(0L)

    /**
     * Rate the frames were captured at. Old metadata has no capturedFps, and
     * the importer used to cap video capture at 15 fps, so cap the fallback
     * there instead of claiming an old set was captured at a rate the user
     * later raised the playback slider to.
     */
    val sourceFps: Int get() =
        if (capturedFps > 0) capturedFps else minOf(LivingFrameFps.snap(fps), 15)
}

/**
 * Playback rates the home-screen flipper can run at. Each step has its own
 * widget layout (flipInterval is an XML attribute and is not remotely
 * settable), so this list and the living_frames_widget_f* layouts must stay
 * in sync — see [intervalMs].
 *
 * 40/48/60 are for high-refresh launchers. They still need the same number of
 * stored frames to look smooth; setting 60 fps on a 15 fps capture simply
 * plays the loop faster.
 */
object LivingFrameFps {
    val STEPS = listOf(1, 2, 4, 5, 6, 8, 10, 12, 15, 20, 24, 30, 40, 48, 60)
    const val DEFAULT = 12
    const val MIN = 1
    const val MAX = 60

    fun snap(fps: Float): Int = STEPS.minByOrNull { abs(it - fps) } ?: DEFAULT
    fun snap(fps: Int): Int = snap(fps.toFloat())

    /**
     * Largest supported rate that does not exceed [fps]. Use this when a hard
     * cap (storage, frame count, preset) must be honoured: rounding 45 fps up
     * to 48 would store more frames than the cap allows and make the loop
     * shorter than the selected window.
     */
    fun atMost(fps: Float): Int = STEPS.lastOrNull { it <= fps } ?: MIN
    fun atMost(fps: Int): Int = atMost(fps.toFloat())
    fun intervalMs(fps: Int): Int = (1000f / snap(fps)).roundToInt()

    /**
     * Rate the launcher's flipper really runs at. flipInterval is a whole
     * number of milliseconds, so 48 fps is 21 ms (47.6 fps) and 60 fps is
     * 17 ms (58.8 fps). Resampling must use this, not the nominal step, or
     * every loop drifts against the speed the user picked.
     */
    fun actualRate(fps: Int): Double = 1000.0 / intervalMs(fps)
    fun stepIndex(fps: Int): Int = STEPS.indexOf(snap(fps)).coerceAtLeast(0)

    fun label(fps: Int): String = when (snap(fps)) {
        1 -> "Slideshow pace"
        2, 4 -> "Gentle"
        5, 6 -> "Relaxed"
        8, 10 -> "Natural"
        12, 15 -> "Smooth"
        20, 24 -> "Cinematic"
        30, 40 -> "High frame rate"
        else -> "Ultra smooth"
    }
}

/**
 * Defensive normalisation at the storage boundary. Metadata may have been
 * written by an older release or edited by a backup tool, so never trust
 * values that can cause huge bitmaps, invalid paths or endless loops.
 */
object LivingFrameValidation {
    private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

    fun isValidId(id: String): Boolean = ID_PATTERN.matches(id) && id != "." && id != ".."

    fun normalize(frame: LivingFrame): LivingFrame? {
        if (!isValidId(frame.id)) return null
        val name = frame.name.trim().ifBlank { "Untitled" }.take(48)
        val maxCount = when (frame.type) {
            LivingFrameType.SLIDESHOW -> LivingFramesRepository.MAX_SLIDES
            LivingFrameType.PHOTO -> 1
            else -> LivingFramesRepository.MAX_FRAMES
        }
        val count = frame.frameCount.coerceIn(1, maxCount)
        val width = frame.width.coerceIn(1, 8_192)
        val height = frame.height.coerceIn(1, 8_192)
        return frame.copy(
            name = name,
            frameCount = count,
            width = width,
            height = height,
            fps = LivingFrameFps.snap(frame.fps),
            opacity = frame.opacity.coerceIn(0.4f, 1f),
            cornerRadiusDp = LivingFrameCorners.snap(frame.cornerRadiusDp),
            slideshowIntervalMin = frame.slideshowIntervalMin.coerceIn(1, 1_440),
            avgFrameBytes = frame.avgFrameBytes.coerceAtLeast(0L),
            sourceLabel = frame.sourceLabel.take(160),
            capturedFps = frame.capturedFps.coerceIn(0, LivingFrameFps.MAX),
            speed = LivingFrameSpeed.snap(frame.speed),
            resolutionPercent = LivingFrameResolution.snap(frame.resolutionPercent),
            tapTargetRoute = frame.tapTargetRoute.trim().take(160)
        )
    }
}