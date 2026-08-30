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
import com.frerox.toolz.data.purgeshot.PurgeShotHandler
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
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
    @Inject lateinit var settingsRepository: SettingsRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val urisList: List<String> = intent.getStringArrayListExtra("uris")
            ?: listOfNotNull(intent.getStringExtra("uri"))
        if (urisList.isEmpty()) return

        val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        // Cancel the alert notification
        try {
            intent.getStringExtra("uri")?.let { notifMgr?.cancel(Uri.parse(it).hashCode()) }
            notifMgr?.cancel(urisList.hashCode())
        } catch (_: Exception) {}

        when (intent.action) {
            "PURGE_KEEP" -> {
                Log.d(TAG, "Keep chosen for ${urisList.size} screenshot(s)")
            }
            "PURGE_ENQUEUE" -> {
                val namesList: List<String> = intent.getStringArrayListExtra("displayNames")
                    ?: listOfNotNull(intent.getStringExtra("displayName"))
                val pathsList: List<String> = intent.getStringArrayListExtra("paths")
                    ?: listOfNotNull(intent.getStringExtra("path"))
                val duration = intent.getLongExtra("duration", 15 * 60_000L)
                val label = intent.getStringExtra("label") ?: "15 min"

                val pending = goAsync()
                scope.launch {
                    try {
                        urisList.forEachIndexed { idx, uriStr ->
                            val uri = Uri.parse(uriStr)
                            val name = namesList.getOrElse(idx) { "Screenshot_${idx + 1}" }
                            val path = pathsList.getOrNull(idx)
                            repository.enqueue(uri, name, duration, label, path)
                        }
                        Log.i(TAG, "Enqueued ${urisList.size} screenshot(s) via notification: $label")

                        // Show the single "Screenshot scheduled" notification
                        PurgeShotHandler.showScheduledNotification(
                            context,
                            settingsRepository,
                            urisList.size,
                            label
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "enqueue failed", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}