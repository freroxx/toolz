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

package com.frerox.toolz.widget.livingframes

import android.widget.RemoteViews
import com.frerox.toolz.R

/**
 * Corners are clipped by the launcher, not baked into the bitmap.
 *
 * A RemoteViews bitmap is scaled by the host to the widget view's measured
 * size. That size is not exposed to the provider, so a radius baked at
 * `outPx / estimatedWidgetPx` drifts as soon as the estimate is off. A
 * rounded background drawable is measured in the host's own pixels, so the
 * outline is always exactly [dp] no matter how the launcher sizes the widget
 * or how much the bitmap was downscaled.
 *
 * A transparent shape still supplies a round-rect outline. With
 * `clipToOutline="true"` on the frame ImageView, the square bitmap is clipped
 * to that outline. Keeping the bitmap square also lets opaque videos render
 * in RGB_565 (twice the pixels for the same host cache bytes).
 */
internal object LivingFramesCorners {

    /** Matches the metadata validation ceiling; the editor exposes 0..40. */
    const val MAX_DP = 48

    private val DRAWABLES = intArrayOf(
        R.drawable.living_frames_corner_0,
        R.drawable.living_frames_corner_1,
        R.drawable.living_frames_corner_2,
        R.drawable.living_frames_corner_3,
        R.drawable.living_frames_corner_4,
        R.drawable.living_frames_corner_5,
        R.drawable.living_frames_corner_6,
        R.drawable.living_frames_corner_7,
        R.drawable.living_frames_corner_8,
        R.drawable.living_frames_corner_9,
        R.drawable.living_frames_corner_10,
        R.drawable.living_frames_corner_11,
        R.drawable.living_frames_corner_12,
        R.drawable.living_frames_corner_13,
        R.drawable.living_frames_corner_14,
        R.drawable.living_frames_corner_15,
        R.drawable.living_frames_corner_16,
        R.drawable.living_frames_corner_17,
        R.drawable.living_frames_corner_18,
        R.drawable.living_frames_corner_19,
        R.drawable.living_frames_corner_20,
        R.drawable.living_frames_corner_21,
        R.drawable.living_frames_corner_22,
        R.drawable.living_frames_corner_23,
        R.drawable.living_frames_corner_24,
        R.drawable.living_frames_corner_25,
        R.drawable.living_frames_corner_26,
        R.drawable.living_frames_corner_27,
        R.drawable.living_frames_corner_28,
        R.drawable.living_frames_corner_29,
        R.drawable.living_frames_corner_30,
        R.drawable.living_frames_corner_31,
        R.drawable.living_frames_corner_32,
        R.drawable.living_frames_corner_33,
        R.drawable.living_frames_corner_34,
        R.drawable.living_frames_corner_35,
        R.drawable.living_frames_corner_36,
        R.drawable.living_frames_corner_37,
        R.drawable.living_frames_corner_38,
        R.drawable.living_frames_corner_39,
        R.drawable.living_frames_corner_40,
        R.drawable.living_frames_corner_41,
        R.drawable.living_frames_corner_42,
        R.drawable.living_frames_corner_43,
        R.drawable.living_frames_corner_44,
        R.drawable.living_frames_corner_45,
        R.drawable.living_frames_corner_46,
        R.drawable.living_frames_corner_47,
        R.drawable.living_frames_corner_48
    )

    fun index(dp: Int): Int = dp.coerceIn(0, MAX_DP)

    fun drawable(dp: Int): Int = DRAWABLES[index(dp)]

    /** Applies the outline to an image view that is clipping to its outline. */
    fun apply(views: RemoteViews, viewId: Int, dp: Int) {
        views.setInt(viewId, "setBackgroundResource", drawable(dp))
    }
}
