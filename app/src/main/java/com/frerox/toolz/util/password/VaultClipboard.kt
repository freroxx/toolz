/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.util.password

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.frerox.toolz.worker.VaultClipboardClearWorker

object VaultClipboard {
    const val CLEAR_TIMEOUT_SECONDS = 30L

    /**
     * Copy a vault secret using the system clipboard with auto-clear.
     * Uses EXTRA_IS_SENSITIVE on API 33+ so keyboards/history exclude it,
     * and schedules [VaultClipboardClearWorker] to wipe it after timeout.
     */
    fun copySecret(context: Context, value: String, label: String = "Toolz secret") {
        if (value.isEmpty()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, value).apply {
            if (Build.VERSION.SDK_INT >= 33) {
                description.extras?.putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)
        runCatching { VaultClipboardClearWorker.schedule(context, value, CLEAR_TIMEOUT_SECONDS) }
    }
}
