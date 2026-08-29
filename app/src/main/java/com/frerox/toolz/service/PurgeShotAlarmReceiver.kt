/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.frerox.toolz.worker.PurgeShotDeletionWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PurgeShotAlarmReceiver : BroadcastReceiver() {
    companion object { private const val TAG = "PurgeShotAlarm" }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.frerox.toolz.PURGE_ALARM") return
        val id = intent.getLongExtra("purge_id", -1L)
        Log.i(TAG, "Alarm fired for id=$id")
        try {
            val data = Data.Builder().putLong("purge_id", id).build()
            val req = OneTimeWorkRequestBuilder<PurgeShotDeletionWorker>()
                .setInputData(data)
                .addTag("purgeshot")
                .build()
            WorkManager.getInstance(context).enqueue(req)
        } catch (e: Exception) {
            Log.w(TAG, "enqueue failed", e)
        }
    }
}
