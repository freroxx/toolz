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

package com.frerox.toolz

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.NetworkType
import com.frerox.toolz.worker.WhisperLocalCleanupWorker
import com.frerox.toolz.worker.WhisperDeliveryWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import java.util.concurrent.TimeUnit
import com.frerox.toolz.util.network.AdBlockManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class ToolzApplication : Application(), Configuration.Provider {

    companion object {
        private val _isFocused = MutableStateFlow(false)
        val isFocused: StateFlow<Boolean> = _isFocused

        fun setFocused(focused: Boolean) {
            _isFocused.value = focused
        }
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var adBlockManager: AdBlockManager

    @Inject
    lateinit var musicRepository: com.frerox.toolz.data.music.MusicRepository

    @Inject
    lateinit var settingsRepository: com.frerox.toolz.data.settings.SettingsRepository

    @Inject
    lateinit var purgeShotRepository: com.frerox.toolz.data.purgeshot.PurgeShotRepository

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // The new standard for SQLCipher 4.6.1+ is a direct native load
        System.loadLibrary("sqlcipher")
        // P0-09: ensure live observer unregisters on low memory to avoid leak
        registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
                    runCatching { musicRepository.stopLiveObserver() }
                }
            }
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
            override fun onLowMemory() { runCatching { musicRepository.stopLiveObserver() } }
        })
        runCatching {
            val youtubeDl = Class.forName("com.yausername.youtubedl_android.YoutubeDL")
                .getMethod("getInstance")
                .invoke(null)
            youtubeDl.javaClass.getMethod("init", android.content.Context::class.java)
                .invoke(youtubeDl, this)
        }.onFailure {
            android.util.Log.w("ToolzApplication", "yt-dlp initialization failed; extractor fallback remains available", it)
        }
        scheduleWhisperLocalCleanup()
        scheduleWhisperDelivery()
        schedulePurgeShotReschedule()
        // PurgeShot: ensure it works outside Toolz — start observer service + JobScheduler trigger
        appScope.launch {
            try {
                purgeShotRepository.ensureRestoredAndRescheduled()
                val enabled = settingsRepository.purgeShotEnabled.first()
                if (enabled) {
                    val svc = android.content.Intent(this@ToolzApplication, com.frerox.toolz.service.PurgeShotService::class.java).apply {
                        action = com.frerox.toolz.service.PurgeShotService.ACTION_START
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
                    com.frerox.toolz.service.PurgeShotObserverJobService.schedule(this@ToolzApplication)
                } else {
                    com.frerox.toolz.service.PurgeShotObserverJobService.cancel(this@ToolzApplication)
                }
            } catch (e: Exception) {
                android.util.Log.w("ToolzApplication", "PurgeShot init failed", e)
            }
        }
        // Keep PurgeShot observer alive: react to enabled toggle (also handled in MainActivity, but app-level is authoritative outside)
        appScope.launch {
            settingsRepository.purgeShotEnabled.collect { enabled ->
                if (enabled) {
                    val svc = android.content.Intent(this@ToolzApplication, com.frerox.toolz.service.PurgeShotService::class.java).apply {
                        action = com.frerox.toolz.service.PurgeShotService.ACTION_START
                    }
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
                    } catch (_: Exception) {}
                    com.frerox.toolz.service.PurgeShotObserverJobService.schedule(this@ToolzApplication)
                } else {
                    try {
                        val svc = android.content.Intent(this@ToolzApplication, com.frerox.toolz.service.PurgeShotService::class.java).apply {
                            action = com.frerox.toolz.service.PurgeShotService.ACTION_STOP
                        }
                        startService(svc)
                    } catch (_: Exception) {}
                    com.frerox.toolz.service.PurgeShotObserverJobService.cancel(this@ToolzApplication)
                }
            }
        }
    }

    private fun scheduleWhisperLocalCleanup() {
        val request = PeriodicWorkRequestBuilder<WhisperLocalCleanupWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WhisperLocalCleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleWhisperDelivery() {
        val request = PeriodicWorkRequestBuilder<WhisperDeliveryWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WhisperEncryptedDelivery",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun schedulePurgeShotReschedule() {
        val request = PeriodicWorkRequestBuilder<com.frerox.toolz.worker.PurgeShotRescheduleWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PurgeShotReschedule",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        // Poll for missed screenshots every 15 min — backup for OEMs where JobScheduler / observer dies outside Toolz
        // UPDATE ensures the periodic work survives app updates and policy changes; KEEP would silently retain stale interval.
        val detect = PeriodicWorkRequestBuilder<com.frerox.toolz.worker.PurgeShotDetectWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PurgeShotDetect",
            ExistingPeriodicWorkPolicy.UPDATE,
            detect,
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
