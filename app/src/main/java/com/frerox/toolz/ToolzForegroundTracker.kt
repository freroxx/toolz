/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reliable foreground tracker.
 *
 * Replaces the old single-Activity `onWindowFocusChanged` flag which went stale
 * on cold start, QS tile popups and multi-window. Counts resumed activities.
 */
object ToolzForegroundTracker {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private var resumedCount = 0
    private var registered = false

    @Synchronized
    fun register(app: Application) {
        if (registered) return
        registered = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumedCount++
                _isForeground.value = true
                ToolzApplication.setFocused(true)
            }

            override fun onActivityPaused(activity: Activity) {
                resumedCount = (resumedCount - 1).coerceAtLeast(0)
                val fg = resumedCount > 0
                _isForeground.value = fg
                ToolzApplication.setFocused(fg)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
