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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frerox.toolz.ui.screens.livingframes.LivingFramesImportCancellation
import com.frerox.toolz.ui.screens.livingframes.LivingFramesImportNotifications

/**
 * Notification "Cancel" action. The import is a ViewModel coroutine, so this
 * only raises a flag; the per-frame progress callback observes it and cancels
 * the job, which runs the importer's cleanup path.
 */
class LivingFramesImportCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL) return
        LivingFramesImportCancellation.request()
        LivingFramesImportNotifications.cancel(context)
    }

    companion object {
        const val ACTION_CANCEL = "com.frerox.toolz.LIVING_FRAMES_IMPORT_CANCEL"
    }
}
