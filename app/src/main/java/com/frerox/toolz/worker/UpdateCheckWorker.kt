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
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.update.UpdateManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val updateManager: UpdateManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val update = updateManager.check(showNotification = true)
            settingsRepository.setLastUpdateCheck(System.currentTimeMillis())
            if (update != null) {
                val autoUpdate = try { settingsRepository.autoUpdateEnabled.first() } catch (_: Exception) { false }
                if (autoUpdate) {
                    // Foreground download with auto-install chaining; progress
                    // surfaces via UpdateNotifications.showProgress.
                    updateManager.download(update, autoInstall = true)
                }
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("UpdateCheck", "Update check failed", e)
            Result.retry()
        }
    }
}
