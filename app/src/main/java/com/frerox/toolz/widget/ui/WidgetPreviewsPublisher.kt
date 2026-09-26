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

package com.frerox.toolz.widget.ui

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.frerox.toolz.widget.glance.MusicWidgetReceiver
import com.frerox.toolz.widget.glance.PomodoroWidgetReceiver
import com.frerox.toolz.widget.glance.ScreenTimeWidgetReceiver
import com.frerox.toolz.widget.glance.SearchBarWidgetReceiver

// ---------------------------------------------------------------------------
//  Generated previews publisher (API 35+). Rate-limited ~2/hr by the system.
//  Call once from MainActivity onCreate (IO dispatcher). Safe no-op below 35
//  and on any failure — previewLayout covers API 31-34.
// ---------------------------------------------------------------------------

object WidgetPreviewsPublisher {
    private const val TAG = "WidgetPreviews"

    suspend fun publishAll(context: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        try {
            val manager = GlanceAppWidgetManager(context)
            // Publish in priority order; each is independently guarded.
            // Receivers (not widgets): SearchBar receiver now serves Quick Actions.
            try {
                manager.setWidgetPreviews(MusicWidgetReceiver::class)
            } catch (e: Exception) {
                Log.w(TAG, "music preview failed", e)
            }
            try {
                manager.setWidgetPreviews(PomodoroWidgetReceiver::class)
            } catch (e: Exception) {
                Log.w(TAG, "pomodoro preview failed", e)
            }
            try {
                manager.setWidgetPreviews(SearchBarWidgetReceiver::class)
            } catch (e: Exception) {
                Log.w(TAG, "toolbar preview failed", e)
            }
            try {
                manager.setWidgetPreviews(ScreenTimeWidgetReceiver::class)
            } catch (e: Exception) {
                Log.w(TAG, "screentime preview failed", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "publishAll failed (non-fatal)", e)
        }
    }
}
