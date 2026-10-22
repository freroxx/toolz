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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.frerox.toolz.data.calendar.CalDavSyncClient
import com.frerox.toolz.data.calendar.CalendarSyncSettingsRepository
import com.frerox.toolz.data.calendar.EventRepository
import com.frerox.toolz.data.calendar.PhoneCalendarImporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker for automatic calendar synchronisation.
 *
 * Syncs:
 * 1. Phone calendars (CalendarContract) — selected IDs from settings
 * 2. CalDAV accounts — fetches and merges remote VEVENT objects
 *
 * Scheduled via WorkManager (FOSS — no GCM/FCM required).
 * Requires network connectivity; uses [NetworkType.CONNECTED] constraint.
 */
@HiltWorker
class CalendarAutoSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: EventRepository,
    private val phoneImporter: PhoneCalendarImporter,
    private val calDavClient: CalDavSyncClient,
    private val syncSettings: CalendarSyncSettingsRepository,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CalAutoSync"
        const val WORK_TAG = "calendar_auto_sync"

        /**
         * Schedule (or reschedule) the periodic sync.
         * Calling with [intervalHours] = 0 cancels the worker.
         */
        fun schedule(workManager: WorkManager, intervalHours: Int) {
            if (intervalHours <= 0) {
                cancel(workManager)
                return
            }
            val request = PeriodicWorkRequestBuilder<CalendarAutoSyncWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(WORK_TAG)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            Log.d(TAG, "Scheduled auto-sync every $intervalHours hours")
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_TAG)
            Log.d(TAG, "Auto-sync cancelled")
        }
    }

    override suspend fun doWork(): Result {
        return runCatching {
            var imported = 0

            // ── 1. Phone calendar sync ─────────────────────────────────────
            val phoneCalIds = syncSettings.getSelectedPhoneCalendarIdsSnapshot()
            if (phoneCalIds.isNotEmpty()) {
                val lastSync = 0L // always fetch all for simplicity; delta sync is a future improvement
                val phoneEvents = phoneImporter.importFromCalendars(phoneCalIds, lastSync)
                if (phoneEvents.isNotEmpty()) {
                    val res = repository.insertEventsDeduplicated(phoneEvents)
                    imported += res.insertedCount + res.updatedCount
                    Log.d(TAG, "Phone sync: ${res.insertedCount} inserted, ${res.updatedCount} updated, ${res.skippedCount} skipped")
                }
            }

            // ── 2. CalDAV sync ────────────────────────────────────────────
            val accounts = syncSettings.getCalDavAccountsSnapshot()
            for (account in accounts) {
                calDavClient.fetchEvents(account).fold(
                    onSuccess = { events ->
                        if (events.isNotEmpty()) {
                            val res = repository.insertEventsDeduplicated(events)
                            imported += res.insertedCount + res.updatedCount
                            Log.d(TAG, "CalDAV sync [${account.url}]: ${res.insertedCount} inserted, ${res.updatedCount} updated, ${res.skippedCount} skipped")
                        }
                    },
                    onFailure = { e ->
                        Log.w(TAG, "CalDAV sync failed for ${account.url}: ${e.message}")
                    }
                )
            }

            syncSettings.updateLastSyncMs()
            Log.d(TAG, "Auto-sync complete. Total imported/merged: $imported")
            Result.success()
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Auto-sync failed", e)
            Result.retry()
        }
    }
}
