/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fired by [com.frerox.toolz.data.skin.LauncherIconManager] after the launcher
 * tile switches. Long-lived services collect it and re-post their ongoing
 * notifications so the shade follows the picked icon immediately instead of
 * keeping the bitmap that was current when the notification first posted.
 */
object IconChangeBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyChanged() {
        _events.tryEmit(Unit)
    }
}
