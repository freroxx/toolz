/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.worker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Clears a vault secret from the system clipboard after a timeout.
 * Only clears when the clipboard still holds the exact copied value,
 * so user copies made afterwards are never destroyed.
 */
@HiltWorker
class VaultClipboardClearWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val expected = inputData.getString(KEY_VALUE) ?: return Result.success()
        if (expected.isEmpty()) return Result.success()
        withContext(Dispatchers.Main) {
            val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@withContext
            val current = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            if (current == expected) {
                clipboard.setPrimaryClip(ClipData.newPlainText(null, ""))
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_VALUE = "vault_clip_value"
        private const val UNIQUE_PREFIX = "vault_clip_clear_"

        fun schedule(context: Context, copiedValue: String, timeoutSeconds: Long = 30) {
            if (copiedValue.isEmpty()) return
            val request = OneTimeWorkRequestBuilder<VaultClipboardClearWorker>()
                .setInitialDelay(timeoutSeconds.coerceIn(10, 300), TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_VALUE to copiedValue))
                .addTag(UNIQUE_PREFIX + copiedValue.hashCode())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
