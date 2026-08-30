/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.frerox.toolz.R
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PurgeShotActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PurgeShotAction"
    }

    @Inject lateinit var repository: PurgeShotRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val uriStr = intent.getStringExtra("uri") ?: return
        val uri = Uri.parse(uriStr)
        val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        when (intent.action) {
            "PURGE_KEEP" -> {
                // Dismiss only — no repository write, safe to run synchronously.
                try {
                    notifMgr?.cancel(uri.hashCode())
                } catch (_: Exception) {
                }
            }
            "PURGE_ENQUEUE" -> {
                val displayName = intent.getStringExtra("displayName") ?: "Screenshot"
                val path = intent.getStringExtra("path")
                val duration = intent.getLongExtra("duration", 15 * 60_000L)
                val label = intent.getStringExtra("label") ?: "15 min"

                // goAsync() is required here: repository.enqueue() is a suspend Room/DataStore
                // write, and without extending the receiver's priority window the system can
                // reclaim this process before the write lands — the notification action would
                // appear to succeed (dismissed, confirmation shown) while nothing was actually
                // queued for deletion.
                val pending = goAsync()
                scope.launch {
                    try {
                        repository.enqueue(uri, displayName, duration, label, path)
                        Log.i(TAG, "Enqueued via notification: $label")
                    } catch (e: Exception) {
                        Log.w(TAG, "enqueue failed", e)
                    } finally {
                        pending.finish()
                    }
                }

                try {
                    notifMgr?.cancel(uri.hashCode())
                    val notif = NotificationCompat.Builder(context, PurgeShotService.ALERTS_CHANNEL_ID)
                        .setContentTitle("PurgeShot queued")
                        .setContentText("$displayName • $label")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setAutoCancel(true)
                        .build()
                    notifMgr?.notify(uri.hashCode() + 1000, notif)
                } catch (_: Exception) {
                }
            }
        }
    }
}