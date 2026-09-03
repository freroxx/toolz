/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * V3: weekly scan reminder + trash expiry. Never auto-deletes — notification only.
 */

package com.frerox.toolz.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.cleaner.trash.CleanerTrashDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CleanerScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val trashDao: CleanerTrashDao
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = try {
        try { trashDao.deleteExpired() } catch (_: Exception) {}
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            val ch = android.app.NotificationChannel("cleaner_reminder", "Cleaner reminders", android.app.NotificationManager.IMPORTANCE_LOW)
            nm?.createNotificationChannel(ch)
            val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            val pi = android.app.PendingIntent.getActivity(applicationContext, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            val n = androidx.core.app.NotificationCompat.Builder(applicationContext, "cleaner_reminder")
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .setContentTitle("Time for a cleanup?")
                .setContentText("Run File Cleaner to reclaim space. Nothing is deleted automatically.")
                .setContentIntent(pi).setAutoCancel(true).build()
            nm?.notify(4101, n)
        } catch (_: Exception) {}
        Result.success()
    } catch (_: Exception) { Result.success() }
}
