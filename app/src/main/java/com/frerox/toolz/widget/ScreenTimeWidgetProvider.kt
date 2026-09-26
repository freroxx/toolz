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

package com.frerox.toolz.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.frerox.toolz.data.focus.UsageStatsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// ---------------------------------------------------------------------------
//  Screen Time — Glance is canonical (ScreenTimeGlanceWidget). The legacy
//  RemoteViews provider was dead (no manifest entry) and has been removed.
//  This file keeps the shared Hilt entry point + ring drawer used by Glance.
// ---------------------------------------------------------------------------

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun usageStatsRepository(): UsageStatsRepository
}

// ---------------------------------------------------------------------------
//  Ring/Arc drawing utility — pure Canvas, no Compose dependency
// ---------------------------------------------------------------------------

object ScreenTimeWidgetDrawer {
    fun drawRing(
        progress: Float,
        ringColor: Int,
        trackColor: Int,
        bgColor: Int,
        sizePx: Int,
    ): Bitmap {
        val bmp    = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = sizePx * 0.10f
        val inset  = stroke / 2f + sizePx * 0.04f

        // Background fill
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bgPaint)

        // Track (full circle)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = trackColor
            style       = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap   = Paint.Cap.ROUND
        }
        canvas.drawArc(
            RectF(inset, inset, sizePx - inset, sizePx - inset),
            -90f, 360f, false, trackPaint,
        )

        // Progress arc
        if (progress > 0.01f) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color       = ringColor
                style       = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap   = Paint.Cap.ROUND
            }
            canvas.drawArc(
                RectF(inset, inset, sizePx - inset, sizePx - inset),
                -90f, progress * 360f, false, progressPaint,
            )
        }
        return bmp
    }
}
