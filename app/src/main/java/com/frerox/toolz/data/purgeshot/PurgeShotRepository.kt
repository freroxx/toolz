/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.purgeshot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.frerox.toolz.worker.PurgeShotDeletionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurgeShotRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PurgeShotDao,
    private val externalBackup: PurgeShotExternalBackupHelper
) {
    companion object {
        private const val TAG = "PurgeShotRepo"
        private const val WORK_PREFIX = "purgeshot_delete_"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val pendingFlow: Flow<List<PurgeShotEntity>> = dao.observePending()
    val allFlow: Flow<List<PurgeShotEntity>> = dao.observeAll()
    val pendingCountFlow: Flow<Int> = dao.observePendingCount()

    /**
     * Called on app start to ensure durability. If Room empty but external backup has entries,
     * restore them. Also re-enqueues WorkManager + AlarmManager for all pending entries.
     */
    suspend fun ensureRestoredAndRescheduled() = withContext(Dispatchers.IO) {
        try {
            val pending = dao.getPendingSync()
            if (pending.isEmpty()) {
                val external = externalBackup.restoreFromExternal()
                if (external.isNotEmpty()) {
                    Log.i(TAG, "Restoring ${external.size} entries from external backup")
                    // Filter: keep only future or recently-expired (last 30 days)
                    val now = System.currentTimeMillis()
                    val cutoff = now - TimeUnit.DAYS.toMillis(30)
                    val toRestore = external.filter { it.scheduledDeleteAtMs > cutoff }
                    if (toRestore.isNotEmpty()) {
                        // Reset IDs to 0 for autoGenerate to avoid PK collisions
                        val clean = toRestore.map { it.copy(id = 0) }
                        dao.insertAll(clean)
                    }
                }
            }
            // Re-schedule all pending regardless (handles reboot / update)
            val allPending = dao.getPendingSync()
            for (e in allPending) {
                scheduleDeletionWork(e)
                scheduleExactAlarm(e)
            }
            // Also mirror to external to ensure consistency
            externalBackup.mirrorToExternal(allPending)
            Log.d(TAG, "Rescheduled ${allPending.size} pending purges")
        } catch (e: Exception) {
            Log.w(TAG, "ensureRestored failed", e)
        }
    }

    suspend fun enqueue(
        fileUri: Uri,
        displayName: String,
        durationMillis: Long,
        label: String,
        filePath: String? = null
    ): PurgeShotEntity {
        val now = System.currentTimeMillis()
        val entity = PurgeShotEntity(
            fileUriString = fileUri.toString(),
            displayName = displayName,
            filePath = filePath,
            createdAtMs = now,
            scheduledDeleteAtMs = now + durationMillis,
            durationMillis = durationMillis,
            durationLabel = label,
            status = PurgeShotEntity.STATUS_PENDING
        )
        val id = dao.insert(entity)
        val inserted = entity.copy(id = id)
        scheduleDeletionWork(inserted)
        scheduleExactAlarm(inserted)
        // Mirror
        scope.launch {
            externalBackup.mirrorToExternal(dao.getPendingSync())
        }
        Log.i(TAG, "Enqueued $displayName -> $label (${durationMillis}ms) id=$id")
        return inserted
    }

    suspend fun cancel(id: Long) {
        dao.cancelById(id)
        cancelWork(id)
        cancelAlarm(id)
        scope.launch { externalBackup.mirrorToExternal(dao.getPendingSync()) }
    }

    suspend fun deleteNow(id: Long): Boolean {
        val entity = dao.getById(id) ?: return false
        val ok = deleteFile(entity)
        if (ok) {
            dao.updateStatus(id, PurgeShotEntity.STATUS_DELETED)
            cancelWork(id)
            cancelAlarm(id)
        } else {
            dao.incrementAttempts(id, "manual delete failed")
        }
        scope.launch { externalBackup.mirrorToExternal(dao.getPendingSync()) }
        return ok
    }

    suspend fun clearPending() {
        val pending = dao.getPendingSync()
        for (e in pending) {
            cancelWork(e.id)
            cancelAlarm(e.id)
        }
        dao.clearPending()
        externalBackup.mirrorToExternal(emptyList())
    }

    /**
     * Attempts to delete the underlying media file via MediaStore. Returns true if deleted or already gone.
     */
    suspend fun deleteFile(entity: PurgeShotEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(entity.fileUriString)
            val resolver = context.contentResolver
            // Try direct URI delete first (most reliable for MediaStore URIs)
            var rows = 0
            try {
                rows = resolver.delete(uri, null, null)
                if (rows > 0) {
                    Log.i(TAG, "Deleted via URI: $uri rows=$rows")
                    return@withContext true
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Direct delete SecurityException, trying query fallback", e)
            } catch (e: Exception) {
                Log.w(TAG, "Direct delete failed", e)
            }
            // Fallback: query by DISPLAY_NAME or _ID
            // Try to extract ID from URI
            val idStr = uri.lastPathSegment
            if (idStr != null) {
                try {
                    val id = idStr.toLong()
                    val contentUri = when {
                        uri.toString().contains("images") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        uri.toString().contains("video") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val target = android.content.ContentUris.withAppendedId(contentUri, id)
                    rows = resolver.delete(target, null, null)
                    if (rows > 0) {
                        Log.i(TAG, "Deleted via ID fallback: $target")
                        return@withContext true
                    }
                } catch (_: Exception) {}
            }
            // Try by displayName path
            if (entity.filePath != null) {
                try {
                    val file = java.io.File(entity.filePath)
                    if (file.exists()) {
                        if (file.delete()) {
                            Log.i(TAG, "Deleted via File API: ${entity.filePath}")
                            // Also try to remove MediaStore entry
                            try {
                                resolver.delete(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    "${MediaStore.MediaColumns.DATA}=?",
                                    arrayOf(entity.filePath)
                                )
                            } catch (_: Exception) {}
                            return@withContext true
                        }
                    } else {
                        // File already gone — consider success (expired)
                        Log.i(TAG, "File already gone: ${entity.filePath}")
                        return@withContext true
                    }
                } catch (_: Exception) {}
            }
            // Final check: query if URI still resolvable
            try {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { c ->
                    if (!c.moveToFirst()) {
                        Log.i(TAG, "URI no longer exists, treating as deleted: $uri")
                        return@withContext true
                    }
                }
            } catch (_: Exception) {}

            Log.w(TAG, "All delete attempts failed for $uri")
            return@withContext false
        } catch (e: Exception) {
            Log.w(TAG, "deleteFile exception", e)
            return@withContext false
        }
    }

    private fun scheduleDeletionWork(entity: PurgeShotEntity) {
        try {
            val delay = entity.scheduledDeleteAtMs - System.currentTimeMillis()
            if (delay <= 0) {
                // Due already — run immediately
                enqueueImmediateWork(entity.id)
                return
            }
            val data = Data.Builder()
                .putLong("purge_id", entity.id)
                .build()
            // Use unique work per entry so it survives app update and can be re-enqueued idempotently
            val request = OneTimeWorkRequestBuilder<PurgeShotDeletionWorker>()
                .setInputData(data)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("purgeshot")
                .addTag("purgeshot_${entity.id}")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_PREFIX + entity.id,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "Scheduled WorkManager for id=${entity.id} delay=${delay}ms")
        } catch (e: Exception) {
            Log.w(TAG, "scheduleDeletionWork failed", e)
        }
    }

    private fun enqueueImmediateWork(id: Long) {
        try {
            val data = Data.Builder().putLong("purge_id", id).build()
            val request = OneTimeWorkRequestBuilder<PurgeShotDeletionWorker>()
                .setInputData(data)
                .addTag("purgeshot")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        } catch (e: Exception) {
            Log.w(TAG, "enqueueImmediateWork failed", e)
        }
    }

    private fun cancelWork(id: Long) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + id)
        } catch (_: Exception) {}
    }

    // AlarmManager as secondary guarantee (exact, survives Doze when permitted)
    private fun scheduleExactAlarm(entity: PurgeShotEntity) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, com.frerox.toolz.service.PurgeShotAlarmReceiver::class.java).apply {
                action = "com.frerox.toolz.PURGE_ALARM"
                putExtra("purge_id", entity.id)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                entity.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Only schedule if within ~1 year (AlarmManager limit)
            val triggerAt = entity.scheduledDeleteAtMs
            if (triggerAt - System.currentTimeMillis() > 0) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        if (am.canScheduleExactAlarms()) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                        } else {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                        }
                    } else {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    }
                    Log.d(TAG, "Scheduled Alarm for id=${entity.id}")
                } catch (e: SecurityException) {
                    Log.w(TAG, "Exact alarm denied, using inexact", e)
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "scheduleExactAlarm failed", e)
        }
    }

    private fun cancelAlarm(id: Long) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, com.frerox.toolz.service.PurgeShotAlarmReceiver::class.java).apply {
                action = "com.frerox.toolz.PURGE_ALARM"
            }
            val pi = PendingIntent.getBroadcast(
                context,
                id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
            pi.cancel()
        } catch (_: Exception) {}
    }

    suspend fun processDue() {
        val due = dao.getDue()
        for (e in due) {
            val ok = deleteFile(e)
            if (ok) {
                dao.updateStatus(e.id, PurgeShotEntity.STATUS_DELETED)
                cancelWork(e.id)
                cancelAlarm(e.id)
            } else {
                // If file already gone, mark deleted; else increment attempts and reschedule short retry
                val stillExists = checkExists(e)
                if (!stillExists) {
                    dao.updateStatus(e.id, PurgeShotEntity.STATUS_EXPIRED)
                    cancelWork(e.id); cancelAlarm(e.id)
                } else {
                    dao.incrementAttempts(e.id, "delete failed, will retry")
                    // retry in 5 minutes via WorkManager
                    val data = Data.Builder().putLong("purge_id", e.id).build()
                    val req = OneTimeWorkRequestBuilder<PurgeShotDeletionWorker>()
                        .setInputData(data)
                        .setInitialDelay(5, TimeUnit.MINUTES)
                        .build()
                    WorkManager.getInstance(context).enqueue(req)
                }
            }
        }
        if (due.isNotEmpty()) {
            externalBackup.mirrorToExternal(dao.getPendingSync())
        }
    }

    private fun checkExists(entity: PurgeShotEntity): Boolean {
        return try {
            val uri = Uri.parse(entity.fileUriString)
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { c ->
                c.moveToFirst()
            } ?: false
        } catch (_: Exception) {
            if (entity.filePath != null) java.io.File(entity.filePath).exists() else false
        }
    }
}
