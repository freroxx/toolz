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
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager-backed clipboard clearing that survives process death.
 * Replaces VM-scoped temporary delays so sensitive tokens cannot leak on process restart.
 */
@HiltWorker
class WhisperClipboardClearWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_TOKEN = "key_token"
        const val KEY_RESTORE_TO = "key_restore_to"
        const val UNIQUE_WORK_NAME = "whisper_clipboard_clear_work"
    }

    override suspend fun doWork(): ListenableWorker.Result {
        val token = inputData.getString(KEY_TOKEN) ?: return ListenableWorker.Result.success()
        val restoreTo = inputData.getString(KEY_RESTORE_TO)

        withContext(Dispatchers.Main) {
            val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@withContext
            val currentClip = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            if (currentClip == token) {
                clipboard.setPrimaryClip(ClipData.newPlainText(null, restoreTo ?: ""))
            }
        }
        return ListenableWorker.Result.success()
    }
}
