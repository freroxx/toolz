/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.frerox.toolz.data.clipboard.ClipboardGate
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.worker.ClipboardBootRetryWorker
import com.frerox.toolz.worker.ClipboardCleanupWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restores clipboard monitoring after boot/update only when the user has
 * completed onboarding, left monitoring enabled, and a privileged read path
 * is actually available. Never starts a zombie foreground service.
 */
class ClipboardBootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        val appContext = context.applicationContext
        ClipboardCleanupWorker.schedule(appContext)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = EntryPointAccessors.fromApplication(
                    appContext,
                    BootEntryPoint::class.java,
                ).settingsRepository()
                val monitoring = repository.clipboardMonitoringEnabled.first()
                val seen = repository.clipboardOnboardingSeen.first()
                if (!monitoring || !seen) return@launch
                val capabilities = ClipboardGate.capabilities(appContext, seen)
                if (capabilities.backgroundCapable) {
                    ClipboardService.startIfNeeded(appContext)
                } else {
                    // Shizuku frequently starts after the boot broadcast.
                    // Retry in the background instead of giving up for the
                    // rest of the session.
                    ClipboardBootRetryWorker.enqueue(appContext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Boot restore failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ClipboardBootReceiver"
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT,
        )
    }
}
