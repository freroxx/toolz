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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import java.util.UUID

class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trackId = intent.getStringExtra("work_id")
        if (intent.getBooleanExtra("retry", false) && trackId != null) {
            val sourceUrl = intent.getStringExtra(MusicDownloadWorker.KEY_SOURCE_URL) ?: return
            val title = intent.getStringExtra(MusicDownloadWorker.KEY_TRACK_TITLE) ?: "Unknown"
            val artist = intent.getStringExtra(MusicDownloadWorker.KEY_TRACK_ARTIST) ?: "Unknown"
            val thumb = intent.getStringExtra(MusicDownloadWorker.KEY_THUMBNAIL_URL)
            val duration = intent.getLongExtra(MusicDownloadWorker.KEY_DURATION, 0L)
            val format = intent.getStringExtra(MusicDownloadWorker.KEY_FORMAT) ?: "M4A"
            val quality = intent.getStringExtra(MusicDownloadWorker.KEY_QUALITY) ?: "HIGH"
            val wm = WorkManager.getInstance(context)
            wm.cancelAllWorkByTag("download_$trackId")
            val req = androidx.work.OneTimeWorkRequestBuilder<MusicDownloadWorker>()
                .setInputData(androidx.work.workDataOf(
                    MusicDownloadWorker.KEY_TRACK_ID to trackId,
                    MusicDownloadWorker.KEY_TRACK_TITLE to title,
                    MusicDownloadWorker.KEY_TRACK_ARTIST to artist,
                    MusicDownloadWorker.KEY_SOURCE_URL to sourceUrl,
                    MusicDownloadWorker.KEY_THUMBNAIL_URL to thumb,
                    MusicDownloadWorker.KEY_DURATION to duration,
                    MusicDownloadWorker.KEY_FORMAT to format,
                    MusicDownloadWorker.KEY_QUALITY to quality
                ))
                .addTag("download_$trackId")
                .addTag(MusicDownloadWorker.TAG_MUSIC_DOWNLOAD)
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            wm.enqueueUniqueWork("music_download_$trackId", androidx.work.ExistingWorkPolicy.REPLACE, req)
            return
        }
        if (trackId != null) {
            WorkManager.getInstance(context).cancelAllWorkByTag("download_$trackId")
        }
    }
}
