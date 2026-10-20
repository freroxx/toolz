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

package com.frerox.toolz.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.update.UpdateManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Daily background check. The work itself only needs any connection;
 * automatic downloading is deferred until the network is unmetered
 * (critical updates are exempt).
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val updateManager: UpdateManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val update = updateManager.check(showNotification = true)
        if (update != null) {
            val autoUpdate = runCatching {
                settingsRepository.autoUpdateEnabled.first()
            }.getOrDefault(false)
            if (autoUpdate && updateManager.canAutoDownloadNow(update)) {
                // Progress and the install prompt surface through the manager.
                updateManager.download(update, autoInstall = true)
            }
        }
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "update check failed: ${e.message}", e)
        Result.retry()
    }

    private companion object {
        const val TAG = "UpdateCheckWorker"
    }
}
