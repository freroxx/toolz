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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.frerox.toolz.widget.glance.MusicWidgetReceiver
import com.frerox.toolz.widget.glance.PomodoroWidgetReceiver
import com.frerox.toolz.widget.glance.SearchBarWidgetReceiver
import com.frerox.toolz.widget.glance.TimerWidgetReceiver
import kotlin.reflect.KClass

// ---------------------------------------------------------------------------
//  Generated previews publisher (API 35+). Rate-limited by the system.
//  Each widget overrides providePreview() with static demo data +
//  previewSizeMode=Responsive so the picker renders every tier without
//  clipping. Safe no-op below 35 and on any failure —
//  previewLayout covers API 31-34.
// ---------------------------------------------------------------------------

object WidgetPreviewsPublisher {
    private const val TAG = "WidgetPreviews"
    private const val PREFS = "widget_previews"
    private const val KEY_LAST_PUBLISH_MS = "last_publish_ms"
    private const val KEY_LAST_VERSION = "last_version_code"
    private const val MIN_INTERVAL_MS = 24 * 60 * 60 * 1000L

    suspend fun publishAll(context: Context, force: Boolean = false) {
        if (Build.VERSION.SDK_INT < 35) return
        val appContext = context.applicationContext ?: context
        if (!shouldPublish(appContext, force)) return
        try {
            val manager = GlanceAppWidgetManager(appContext)
            var rateLimited = false
            rateLimited = publishOne(appContext, manager, MusicWidgetReceiver::class, "music") || rateLimited
            rateLimited = publishOne(appContext, manager, PomodoroWidgetReceiver::class, "pomodoro") || rateLimited
            rateLimited = publishOne(appContext, manager, SearchBarWidgetReceiver::class, "toolbar") || rateLimited
            rateLimited = publishOne(appContext, manager, TimerWidgetReceiver::class, "stopwatch") || rateLimited
            // Don't stamp the clock when rate-limited — retry on next launch
            // instead of waiting out the full 24h throttle.
            if (!rateLimited) {
                stampPublished(appContext)
            } else {
                Log.w(TAG, "publish rate-limited, will retry on next launch")
            }
        } catch (e: Exception) {
            Log.w(TAG, "publishAll failed (non-fatal)", e)
        }
    }

    private fun shouldPublish(context: Context, force: Boolean): Boolean {
        if (force) return true
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val version = currentVersionCode(context)
            val lastVersion = prefs.getLong(KEY_LAST_VERSION, -1L)
            // Fresh install or app update -> always (re)publish so the picker
            // never shows a stale/blank tile after upgrade.
            if (version != -1L && version != lastVersion) return true
            // Skip when the system already holds generated previews for all
            // four providers (e.g. restored by the launcher).
            if (Build.VERSION.SDK_INT >= 35 && allPreviewsPresent(context)) {
                stampPublished(context)
                return false
            }
            val last = prefs.getLong(KEY_LAST_PUBLISH_MS, 0L)
            System.currentTimeMillis() - last >= MIN_INTERVAL_MS
        } catch (_: Exception) {
            true
        }
    }

    private fun stampPublished(context: Context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_PUBLISH_MS, System.currentTimeMillis())
                .putLong(KEY_LAST_VERSION, currentVersionCode(context))
                .apply()
        } catch (_: Exception) {}
    }

    private fun currentVersionCode(context: Context): Long {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else @Suppress("DEPRECATION") info.versionCode.toLong()
        } catch (_: Exception) {
            -1L
        }
    }

    @RequiresApi(35)
    private fun allPreviewsPresent(context: Context): Boolean {
        return try {
            val appWidgetManager =
                context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
            val installed = appWidgetManager.installedProviders
            listOf(
                MusicWidgetReceiver::class.java,
                PomodoroWidgetReceiver::class.java,
                SearchBarWidgetReceiver::class.java,
                TimerWidgetReceiver::class.java
            ).all { receiver ->
                val component = ComponentName(context, receiver)
                installed.any { info ->
                    info.provider == component &&
                        (info.generatedPreviewCategories and
                            android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    @RequiresApi(35)
    private suspend fun publishOne(
        context: Context,
        manager: GlanceAppWidgetManager,
        receiver: KClass<out GlanceAppWidgetReceiver>,
        name: String
    ): Boolean {
        return try {
            // Skip receivers the system already has a home-screen preview for,
            // unless this is a fresh version (handled by shouldPublish).
            val result = manager.setWidgetPreviews(receiver)
            if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED) {
                Log.w(TAG, "$name preview rate-limited")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "$name preview failed", e)
            false
        }
    }
}
