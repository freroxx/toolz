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

/** Source kind for a Living Frame set. */
@Serializable
enum class LivingFrameType { GIF, VIDEO, PHOTO, SLIDESHOW }

/** Loop behaviour on the home widget. */
@Serializable
enum class LivingFrameLoopMode { LOOP, BOOMERANG, ONCE }

/** How the frame fills the widget ImageView. */
@Serializable
enum class LivingFrameFit { FIT, FILL }

/** Tap behaviour on the home widget. */
@Serializable
enum class LivingFrameTapAction { OPEN_PREVIEW, NEXT_FRAME, NOTHING }

/**
 * Persisted frame set. Frames live as files under
 * files/living_frames/<id>/frame_XXXX.webp — never in this JSON.
 */
@Serializable
data class LivingFrame(
    val id: String,
    val name: String,
    val type: LivingFrameType,
    val frameCount: Int,
    val width: Int,
    val height: Int,
    val fps: Int = 15,
    val loopMode: LivingFrameLoopMode = LivingFrameLoopMode.LOOP,
    val fit: LivingFrameFit = LivingFrameFit.FILL,
    val opacity: Float = 1f,
    val cornerRadiusDp: Int = 28,
    val tapAction: LivingFrameTapAction = LivingFrameTapAction.OPEN_PREVIEW,
    val slideshowIntervalMin: Int = 60,
    val shuffle: Boolean = false,
    val showCaption: Boolean = false,
    val avgFrameBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceLabel: String = ""
)

/** FPS presets offered in the UI. 30 is experimental/extreme — see LagMeter. */
object LivingFrameFps {
    const val MIN = 1
    const val MAX = 30
    const val DEFAULT = 15
    val PRESETS = listOf(1, 5, 10, 15, 24, 30)
    const val SMOOTH_THRESHOLD = 10
    const val EXPERIMENTAL_THRESHOLD = 16

    fun label(fps: Int): String = when (fps) {
        1 -> "1 fps · slideshow"
        5 -> "5 fps · eco"
        10 -> "10 fps · smooth"
        15 -> "15 fps · balanced"
        24 -> "24 fps · cinematic"
        30 -> "30 fps · extreme"
        else -> "$fps fps"
    }

    fun intervalMs(fps: Int): Long = (1000L / fps.coerceIn(MIN, MAX).coerceAtLeast(1))
}

/** Lag / battery risk meter shown next to the FPS picker. */
enum class LivingFrameLagLevel { FINE, MAY_LAG, LIKELY_LAG }

data class LivingFrameLagEstimate(
    val level: LivingFrameLagLevel,
    val kilobytesPerSecond: Long,
    val headline: String,
    val detail: String
)

object LivingFrameLagMeter {
    /**
     * Heuristic from Android widget research:
     * - RemoteViews updates go over Binder (~1MB limit, shared with everything else).
     * - updatePeriodMillis floors at 30 min; animation needs a foreground service +
     *   partiallyUpdateAppWidget, which launchers/OEMs (Samsung, Xiaomi) throttle.
     * - Each frame push costs avgFrameBytes over Binder; sustained > ~900KB/s
     *   risks TransactionTooLarge / dropped frames / battery drain.
     */
    fun estimate(fps: Int, avgFrameBytes: Long, widgetCount: Int = 1): LivingFrameLagEstimate {
        val kbps = (fps.toLong() * avgFrameBytes * widgetCount.coerceAtLeast(1)) / 1024L
        return when {
            fps <= 10 && kbps < 400 -> LivingFrameLagEstimate(
                LivingFrameLagLevel.FINE,
                kbps,
                "Should run fine",
                "Conservative rate — most launchers keep up, low battery use."
            )
            fps <= 15 && kbps < 900 -> LivingFrameLagEstimate(
                LivingFrameLagLevel.FINE,
                kbps,
                "Should run fine",
                "Balanced rate. Pauses automatically when the screen is off."
            )
            fps <= 15 -> LivingFrameLagEstimate(
                LivingFrameLagLevel.MAY_LAG,
                kbps,
                "May lag on some launchers",
                "Large frames at this rate can stutter on Samsung / Xiaomi. Try smaller crop."
            )
            else -> LivingFrameLagEstimate(
                LivingFrameLagLevel.LIKELY_LAG,
                kbps,
                "Experimental — likely to lag",
                "$fps fps pushes ${kbps}KB/s over Binder. Expect dropped frames + battery drain. In-app preview stays smooth."
            )
        }
    }
}
