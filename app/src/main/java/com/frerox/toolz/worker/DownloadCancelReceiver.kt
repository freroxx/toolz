package com.frerox.toolz.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import java.util.UUID

class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trackId = intent.getStringExtra("work_id")
        if (trackId != null) {
            WorkManager.getInstance(context).cancelAllWorkByTag("download_$trackId")
        }
    }
}
