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
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.frerox.toolz.service.PurgeShotService
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
import com.frerox.toolz.data.settings.SettingsRepository
import javax.inject.Singleton

@Singleton
class PurgeShotRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PurgeShotDao,
    private val externalBackup: PurgeShotExternalBackupHelper,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "PurgeShotRepo"
        internal const val WORK_PREFIX = "purgeshot_delete_"
        private const val MAX_DELETE_ATTEMPTS = 5
        private const val TERMINAL_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
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
                    var toRestore = external.filter { it.scheduledDeleteAtMs > cutoff }
                    if (toRestore.isNotEmpty()) {
                        // Dedup restore by uri and filePath to avoid duping after clear-data restore
                        toRestore = toRestore.distinctBy { it.filePath ?: it.fileUriString }
                        // Reset IDs to 0 for autoGenerate to avoid PK collisions
                        val clean = toRestore.map { it.copy(id = 0) }
                        dao.insertAll(clean)
                    }
                }
            }
            // Deduplicate any existing pending duplicates (e.g. legacy dupes before fix)
            deduplicatePending()
            // Screenshots the user deleted from their gallery are still in our
            // queue until the timer fires. Reconcile now so the queue reflects
            // reality and no doomed work/alarms stay armed.
            reconcilePending()
            // Keep the database from growing forever with old terminal rows.
            dao.purgeTerminalOlderThan(System.currentTimeMillis() - TERMINAL_RETENTION_MS)
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

    private suspend fun deduplicatePending() {
        val pending = dao.getPendingSync()
        if (pending.size <= 1) return
        val grouped = pending.groupBy { it.filePath ?: it.fileUriString }
        var removed = 0
        for ((_, group) in grouped) {
            if (group.size > 1) {
                // Keep earliest (lowest id / earliest scheduled)
                val sorted = group.sortedBy { it.id }
                val keep = sorted.first()
                val dupes = sorted.drop(1)
                for (dupe in dupes) {
                    try {
                        dao.deleteById(dupe.id)
                        cancelWork(dupe.id)
                        cancelAlarm(dupe.id)
                        removed++
                    } catch (_: Exception) {}
                }
                Log.w(TAG, "deduplicatePending removed ${dupes.size} dupes for key=${keep.filePath ?: keep.fileUriString} keepId=${keep.id}")
            }
        }
        if (removed > 0) {
            scope.launch { externalBackup.mirrorToExternal(dao.getPendingSync()) }
            Log.i(TAG, "deduplicatePending total removed=$removed")
        }
    }

    /**
     * Marks queued screenshots that no longer exist as [PurgeShotEntity.STATUS_EXPIRED].
     *
     * Users often delete a screenshot from their gallery before the timer fires.
     * Without this sweep, the app keeps showing a countdown for a file that is
     * already gone and keeps its WorkManager job + exact alarm armed.
     *
     * @return number of queue entries that were found missing.
     */
    suspend fun reconcilePending(): Int = withContext(Dispatchers.IO) {
        val pending = dao.getPendingSync()
        if (pending.isEmpty()) return@withContext 0
        var removed = 0
        for (entity in pending) {
            if (!fileExistsNow(entity.fileUriString, entity.filePath)) {
                dao.updateStatus(entity.id, PurgeShotEntity.STATUS_EXPIRED)
                cancelWork(entity.id)
                cancelAlarm(entity.id)
                removed++
                Log.i(TAG, "Reconciled missing screenshot id=${entity.id} ${entity.displayName}")
            }
        }
        if (removed > 0) {
            runCatching { externalBackup.mirrorToExternal(dao.getPendingSync()) }
        }
        removed
    }

    /** Public existence probe shared by workers and reconciliation. */
    suspend fun isFilePresent(entity: PurgeShotEntity): Boolean = withContext(Dispatchers.IO) {
        fileExistsNow(entity.fileUriString, entity.filePath)
    }

    /** Moves an entry to a terminal state and disarms its work/alarm. */
    suspend fun markTerminal(id: Long, status: String, error: String? = null) = withContext(Dispatchers.IO) {
        if (error != null) dao.incrementAttempts(id, error)
        dao.updateStatus(id, status)
        cancelWork(id)
        cancelAlarm(id)
        runCatching { externalBackup.mirrorToExternal(dao.getPendingSync()) }
    }

    /**
     * A file is present if either its MediaStore row or its on-disk path still
     * exists. A permission failure is treated as "present" so a transient
     * SecurityException can never make the queue forget a file that is still
     * there.
     */
    private fun fileExistsNow(uriString: String, filePath: String?): Boolean {
        if (!filePath.isNullOrBlank()) {
            if (runCatching { java.io.File(filePath).exists() }.getOrDefault(false)) return true
        }
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        if (uri.scheme == "file") {
            return runCatching { uri.path?.let { java.io.File(it).exists() } == true }.getOrDefault(false)
        }
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null,
            )?.use { it.moveToFirst() } ?: false
        } catch (_: SecurityException) {
            true
        } catch (_: Exception) {
            true
        }
    }

    suspend fun enqueue(
        fileUri: Uri,
        displayName: String,
        durationMillis: Long,
        label: String,
        filePath: String? = null
    ): PurgeShotEntity {
        // Dedup: skip if same uri or same filePath already pending
        val uriStr = fileUri.toString()
        val existingByUri = dao.findPendingByUri(uriStr)
        if (existingByUri != null) {
            Log.w(TAG, "skip duplicate enqueue uri=$uriStr already pending id=${existingByUri.id}")
            PurgeShotHandler.markUriHandled(uriStr)
            if (filePath != null) PurgeShotHandler.markUriHandled(filePath)
            return existingByUri
        }
        if (filePath != null) {
            val existingByPath = dao.findPendingByPath(filePath)
            if (existingByPath != null) {
                Log.w(TAG, "skip duplicate enqueue path=$filePath already pending id=${existingByPath.id} uri=$uriStr")
                PurgeShotHandler.markUriHandled(uriStr)
                PurgeShotHandler.markUriHandled(filePath)
                return existingByPath
            }
        }
        val now = System.currentTimeMillis()
        val entity = PurgeShotEntity(
            fileUriString = uriStr,
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

    fun enqueueMultipleAsync(
        items: List<Triple<Uri, String, String?>>,
        durationMillis: Long,
        label: String,
        onComplete: ((List<PurgeShotEntity>) -> Unit)? = null
    ) {
        scope.launch {
            val res = enqueueMultiple(items, durationMillis, label)
            if (res.isNotEmpty()) {
                PurgeShotHandler.showScheduledNotification(context, settingsRepository, res.size, label)
            }
            onComplete?.invoke(res)
        }
    }

    suspend fun enqueueMultiple(
        items: List<Triple<Uri, String, String?>>,
        durationMillis: Long,
        label: String
    ): List<PurgeShotEntity> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Load the current pending set once instead of running two Room queries
        // per item. A burst of screenshots is common (long-press volume, scroll
        // capture) and the old per-item queries made the popup feel sluggish.
        val pendingNow = dao.getPendingSync()
        val pendingUris = pendingNow.mapTo(HashSet()) { it.fileUriString }
        val pendingPaths = pendingNow.mapNotNullTo(HashSet()) { it.filePath }
        // Deduplicate incoming batch by uri and by filePath to prevent same screenshot queued multiple times
        val distinctItems = items.distinctBy { (uri, _, path) -> (path ?: uri.toString()) }
        val filtered = mutableListOf<Triple<Uri, String, String?>>()
        val seenPaths = mutableSetOf<String>()
        val seenUris = mutableSetOf<String>()
        for ((uri, name, path) in distinctItems) {
            val uriStr = uri.toString()
            // In-batch dedup by uri/path already via distinctBy, but also protect against uri vs path alias
            if (uriStr in seenUris) continue
            if (path != null && path in seenPaths) continue
            // DB dedup — skip if already pending by uri or path
            if (uriStr in pendingUris || (path != null && path in pendingPaths)) {
                Log.w(TAG, "skip duplicate batch uri=$uriStr path=$path")
                PurgeShotHandler.markUriHandled(uriStr)
                if (path != null) PurgeShotHandler.markUriHandled(path)
                continue
            }
            seenUris.add(uriStr)
            if (path != null) seenPaths.add(path)
            filtered.add(Triple(uri, name, path))
        }
        if (filtered.isEmpty()) {
            Log.i(TAG, "Enqueue batch filtered to 0 — all duplicates")
            return@withContext emptyList()
        }
        val entities = filtered.map { (uri, name, path) ->
            PurgeShotEntity(
                fileUriString = uri.toString(),
                displayName = name,
                filePath = path,
                createdAtMs = now,
                scheduledDeleteAtMs = now + durationMillis,
                durationMillis = durationMillis,
                durationLabel = label,
                status = PurgeShotEntity.STATUS_PENDING
            )
        }
        val ids = dao.insertAll(entities)
        val created = entities.mapIndexed { idx, e ->
            PurgeShotHandler.markUriHandled(e.fileUriString)
            if (e.filePath != null) PurgeShotHandler.markUriHandled(e.filePath)
            val id = ids.getOrElse(idx) { 0L }
            e.copy(id = id).also {
                scheduleDeletionWork(it)
                scheduleExactAlarm(it)
            }
        }
        scope.launch {
            try {
                externalBackup.mirrorToExternal(dao.getPendingSync())
            } catch (_: Exception) {}
        }
        Log.i(TAG, "Enqueued batch of ${created.size} screenshots for $label (filtered ${items.size} -> ${filtered.size})")
        created
    }

    fun deleteMultipleFilesAsync(
        items: List<Pair<Uri, String?>>,
        onComplete: ((Int) -> Unit)? = null
    ) {
        scope.launch {
            val deleted = deleteMultipleFiles(items)
            onComplete?.invoke(deleted)
        }
    }

    suspend fun getEntryByUri(uriString: String): PurgeShotEntity? = withContext(Dispatchers.IO) {
        dao.getByUri(uriString)
    }

    suspend fun getEntryByPath(path: String): PurgeShotEntity? = withContext(Dispatchers.IO) {
        dao.findAnyByPath(path)
    }

    suspend fun hasEntry(uriString: String): Boolean = withContext(Dispatchers.IO) {
        dao.getByUri(uriString) != null
    }

    suspend fun hasEntryForPath(path: String): Boolean = withContext(Dispatchers.IO) {
        dao.findAnyByPath(path) != null
    }

    suspend fun deleteMultipleFiles(
        items: List<Pair<Uri, String?>>
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        val now = System.currentTimeMillis()
        val resolvedKeys = mutableListOf<String>()
        for ((uri, path) in items) {
            val uriStr = uri.toString()
            PurgeShotHandler.markUriHandled(uriStr)
            val entity = PurgeShotEntity(
                fileUriString = uriStr,
                displayName = uri.lastPathSegment ?: "Screenshot",
                filePath = path,
                scheduledDeleteAtMs = now,
                durationMillis = 0L,
                durationLabel = "Now"
            )
            val ok = deleteFile(entity)
            if (ok) count++
            // Record the outcome so the queue/history reflects the action. The
            // popup used to delete files without leaving any trace, which made
            // "Deleted" stats and the history wrong.
            val status = if (ok) PurgeShotEntity.STATUS_DELETED else PurgeShotEntity.STATUS_FAILED
            val existing = dao.findPendingByUri(uriStr)
                ?: path?.let { dao.findPendingByPath(it) }
            if (existing != null) {
                dao.updateStatus(existing.id, status)
                cancelWork(existing.id)
                cancelAlarm(existing.id)
            } else {
                runCatching {
                    dao.insert(
                        entity.copy(
                            status = status,
                            lastError = if (ok) null else "manual delete failed",
                        ),
                    )
                }
            }
            resolvedKeys.add(uriStr)
            if (path != null) resolvedKeys.add(path)
        }
        runCatching { settingsRepository.addPurgeShotResolvedUris(resolvedKeys) }
        runCatching { externalBackup.mirrorToExternal(dao.getPendingSync()) }
        Log.i(TAG, "Deleted $count of ${items.size} files in batch")
        count
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
     *
     * Production hardening:
     *  - Handles RecoverableSecurityException (Android 10+ scoped storage) by surfacing a consent notification
     *  - Uses Shizuku privileged delete when available
     *  - Tries MANAGE_EXTERNAL_STORAGE path + direct File API
     *  - Falls back to trash request if permanent delete is denied and user opted for trash
     */
    suspend fun deleteFile(entity: PurgeShotEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            var uri = Uri.parse(entity.fileUriString)
            val resolver = context.contentResolver
            val resolvedPath = entity.filePath ?: (if (uri.scheme == "file") uri.path else queryPathFromUri(uri))

            // Fast path: the user (or another app) already deleted it. Avoid
            // spinning up Shizuku / MediaStore delete attempts for a missing file.
            if (!fileExistsNow(entity.fileUriString, resolvedPath)) {
                Log.i(TAG, "File already gone: ${entity.fileUriString}")
                return@withContext true
            }

            // If uri is file://, resolve to content:// URI via MediaStore if indexed
            if (uri.scheme == "file" && resolvedPath != null) {
                try {
                    resolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.Media._ID),
                        "${MediaStore.MediaColumns.DATA}=?",
                        arrayOf(resolvedPath),
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val id = c.getLong(0)
                            uri = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        }
                    }
                } catch (_: Exception) {}
            }

            // 0) Shizuku privileged path (instant, no consent needed) — rm via shell executor
            if (com.frerox.toolz.util.shizuku.ShizukuHelper.isAuthorized()) {
                try {
                    val path = resolvedPath
                    if (path != null) {
                        val executor = com.frerox.toolz.util.shizuku.ShizukuShellExecutor(context)
                        if (executor.ensureService()) {
                            val esc = path.replace("\"", "\\\"").replace("$", "\\$")
                            val result = executor.executeForResult("rm -f \"$esc\" && echo OK")
                            if (result.isSuccess && result.stdout.contains("OK")) {
                                Log.i(TAG, "Deleted via Shizuku: $path")
                                // Also clear MediaStore entry
                                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                                return@withContext true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Shizuku delete failed", e)
                }
            }

            // 1) Direct URI delete
            var rows = 0
            try {
                rows = resolver.delete(uri, null, null)
                if (rows > 0) {
                    Log.i(TAG, "Deleted via URI: $uri rows=$rows")
                    return@withContext true
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Direct delete SecurityException, trying fallbacks", e)
            } catch (e: Exception) {
                Log.w(TAG, "Direct delete failed", e)
            }

            // 2) ID fallback (handles content://media/external/images/media/123)
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
                    try {
                        rows = resolver.delete(target, null, null)
                        if (rows > 0) {
                            Log.i(TAG, "Deleted via ID fallback: $target")
                            return@withContext true
                        }
                    } catch (se: SecurityException) {
                        Log.w(TAG, "ID fallback SecurityException", se)
                    }
                } catch (_: Exception) {}
            }

            // 3) MANAGE_EXTERNAL_STORAGE direct File delete (if granted, bypasses MediaStore)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (android.os.Environment.isExternalStorageManager()) {
                    entity.filePath?.let { path ->
                        try {
                            val file = java.io.File(path)
                            if (!file.exists()) {
                                Log.i(TAG, "File already gone (all-files): $path")
                                return@withContext true
                            }
                            if (file.delete()) {
                                Log.i(TAG, "Deleted via all-files File API: $path")
                                try {
                                    resolver.delete(
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        "${MediaStore.MediaColumns.DATA}=?",
                                        arrayOf(path)
                                    )
                                } catch (_: Exception) {}
                                return@withContext true
                            }
                        } catch (_: Exception) {}
                    }
                }
            } else if (entity.filePath != null) {
                try {
                    val file = java.io.File(entity.filePath)
                    if (!file.exists()) {
                        Log.i(TAG, "File already gone: ${entity.filePath}")
                        return@withContext true
                    }
                    if (file.delete()) {
                        Log.i(TAG, "Deleted via File API: ${entity.filePath}")
                        try {
                            resolver.delete(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                "${MediaStore.MediaColumns.DATA}=?",
                                arrayOf(entity.filePath)
                            )
                        } catch (_: Exception) {}
                        return@withContext true
                    }
                } catch (_: Exception) {}
            }

            // 4) Final existence check: if URI not queryable, treat as already deleted
            try {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { c ->
                    if (!c.moveToFirst()) {
                        Log.i(TAG, "URI no longer exists, treating as deleted: $uri")
                        return@withContext true
                    }
                }
            } catch (_: Exception) {}

            // 5) As last resort on Android 11+, try trash (gives user 30d grace, better than failure)
            // We only auto-trash if permanent delete consistently fails and user hasn't disabled trash fallback
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val trashUri = uri // already MediaStore uri
                    val values = android.content.ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }
                    val updated = resolver.update(trashUri, values, null, null)
                    if (updated > 0) {
                        Log.i(TAG, "Trashed instead of deleted: $uri")
                        return@withContext true
                    }
                } catch (_: Exception) {}
            }

            Log.w(TAG, "All delete attempts failed for $uri")
            return@withContext false
        } catch (e: Exception) {
            Log.w(TAG, "deleteFile exception", e)
            return@withContext false
        }
    }

    private fun queryPathFromUri(uri: Uri): String? = try {
        if (uri.scheme == "file") {
            uri.path
        } else {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }
    } catch (_: Exception) { null }

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
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
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
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("purgeshot")
                .addTag("purgeshot_$id")
                .build()
            // Unique work: an alarm-triggered immediate run and a scheduled run
            // must never race each other into duplicate deletion jobs.
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_PREFIX + id,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        } catch (e: Exception) {
            Log.w(TAG, "enqueueImmediateWork failed", e)
        }
    }

    /**
     * Schedules a retry for a failed deletion with exponential backoff while
     * keeping one unique job per queue entry.
     */
    private fun scheduleRetryWork(id: Long, attempt: Int) {
        try {
            val backoffMs = (5L * 60_000L * (1L shl attempt.coerceIn(0, 4)))
                .coerceAtMost(60L * 60_000L)
            val data = Data.Builder().putLong("purge_id", id).build()
            val request = OneTimeWorkRequestBuilder<PurgeShotDeletionWorker>()
                .setInputData(data)
                .setInitialDelay(backoffMs, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("purgeshot")
                .addTag("purgeshot_$id")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_PREFIX + id,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            Log.d(TAG, "Retry scheduled for id=$id in ${backoffMs}ms (attempt $attempt)")
        } catch (e: Exception) {
            Log.w(TAG, "scheduleRetryWork failed", e)
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

    suspend fun processDue() = withContext(Dispatchers.IO) {
        val due = dao.getDue()
        for (e in due) {
            val ok = deleteFile(e)
            if (ok) {
                dao.updateStatus(e.id, PurgeShotEntity.STATUS_DELETED)
                cancelWork(e.id)
                cancelAlarm(e.id)
            } else {
                // If file already gone, mark expired; else increment attempts
                // and schedule one unique retry with exponential backoff.
                val stillExists = fileExistsNow(e.fileUriString, e.filePath)
                if (!stillExists) {
                    dao.updateStatus(e.id, PurgeShotEntity.STATUS_EXPIRED)
                    cancelWork(e.id)
                    cancelAlarm(e.id)
                } else {
                    val attempt = e.attempts + 1
                    if (attempt >= MAX_DELETE_ATTEMPTS) {
                        dao.updateStatus(e.id, PurgeShotEntity.STATUS_FAILED)
                        cancelWork(e.id)
                        cancelAlarm(e.id)
                        Log.w(TAG, "Giving up on id=${e.id} after $attempt attempts")
                    } else {
                        dao.incrementAttempts(e.id, "delete failed, will retry")
                        scheduleRetryWork(e.id, attempt)
                    }
                }
            }
        }
        if (due.isNotEmpty()) {
            runCatching { externalBackup.mirrorToExternal(dao.getPendingSync()) }
        }
    }
}
