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

/** FILL = centre-crop to the widget, FIT = letterbox inside it. */
@Serializable
enum class LivingFrameFit { FIT, FILL }

/** Tap behaviour. NEXT_FRAME means "next photo" and only applies to slideshows. */
@Serializable
enum class LivingFrameTapAction { OPEN_PREVIEW, NEXT_FRAME, NOTHING }

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
    val cornerRadiusDp: Int = 24,
    val tapAction: LivingFrameTapAction = LivingFrameTapAction.OPEN_PREVIEW,
    val slideshowIntervalMin: Int = 60,
    val shuffle: Boolean = false,
    val showCaption: Boolean = false,
    val avgFrameBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceLabel: String = ""
) {
    val isAnimated: Boolean
        get() = (type == LivingFrameType.GIF || type == LivingFrameType.VIDEO) && frameCount > 1

    val isSlideshow: Boolean get() = type == LivingFrameType.SLIDESHOW
}

/**
 * Playback rates the home-screen flipper can run at. Each step has its own
 * ViewFlipper in the widget layout (flipInterval is an XML attribute and is
 * not remotely settable), so this list and living_frames_widget.xml must
 * stay in sync — see [intervalMs].
 */
object LivingFrameFps {
    val STEPS = listOf(1, 2, 4, 5, 6, 8, 10, 12, 15, 20, 24, 30)
    const val DEFAULT = 12
    const val MIN = 1
    const val MAX = 30

    fun snap(fps: Float): Int = STEPS.minByOrNull { abs(it - fps) } ?: DEFAULT
    fun snap(fps: Int): Int = snap(fps.toFloat())
    fun intervalMs(fps: Int): Int = (1000f / snap(fps)).roundToInt()
    fun stepIndex(fps: Int): Int = STEPS.indexOf(snap(fps)).coerceAtLeast(0)

    fun label(fps: Int): String = when (snap(fps)) {
        1 -> "Slideshow pace"
        2, 4 -> "Gentle"
        5, 6 -> "Relaxed"
        8, 10 -> "Natural"
        12, 15 -> "Smooth"
        20, 24 -> "Cinematic"
        else -> "Fast"
    }
}
