/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.purgeshot

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import com.frerox.toolz.service.PurgeShotAccessibilityService
import com.frerox.toolz.util.shizuku.ShizukuHelper
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PurgeShotPermissionHelper {
    private const val TAG = "PurgeShotPermHelper"

    fun hasMediaPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun hasAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    fun hasAccessibilityPermission(context: Context): Boolean {
        return PurgeShotAccessibilityService.isEnabled(context)
    }

    /**
     * Force-grants all required and optional permissions for PurgeShot via Shizuku shell.
     * Grants:
     *  - POST_NOTIFICATIONS, READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_EXTERNAL_STORAGE
     *  - SYSTEM_ALERT_WINDOW (overlay permission for instant popups outside Toolz)
     *  - MANAGE_EXTERNAL_STORAGE (silent deletion without consent prompts)
     *  - ACCESS_RESTRICTED_SETTINGS (Android 13+ accessibility sideload bypass)
     *  - Accessibility Service activation in Secure Settings
     *  - Battery optimization whitelist (prevents OS killing background detection)
     */
    suspend fun forceAllPermissionsViaShizuku(context: Context, executor: ShizukuShellExecutor): Boolean = withContext(Dispatchers.IO) {
        if (!ShizukuHelper.isAuthorized()) {
            Log.w(TAG, "Cannot force permissions: Shizuku not authorized")
            return@withContext false
        }
        val pkg = context.packageName
        try {
            if (!executor.ensureService()) {
                Log.w(TAG, "Cannot force permissions: Shizuku service unavailable")
                return@withContext false
            }

            val commands = mutableListOf(
                // 1. Runtime media & notification permissions
                "pm grant $pkg android.permission.POST_NOTIFICATIONS",
                "pm grant $pkg android.permission.READ_MEDIA_IMAGES",
                "pm grant $pkg android.permission.READ_MEDIA_VIDEO",
                "pm grant $pkg android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
                "pm grant $pkg android.permission.READ_EXTERNAL_STORAGE",
                "pm grant $pkg android.permission.WRITE_EXTERNAL_STORAGE",

                // 2. AppOps: Overlay (SYSTEM_ALERT_WINDOW) for instant popups over any app
                "appops set $pkg SYSTEM_ALERT_WINDOW allow",

                // 3. AppOps: MANAGE_EXTERNAL_STORAGE for silent auto-delete
                "appops set $pkg MANAGE_EXTERNAL_STORAGE allow",

                // 4. AppOps: Access restricted settings (Android 13+ accessibility restriction bypass)
                "appops set $pkg ACCESS_RESTRICTED_SETTINGS allow",

                // 5. Battery optimization whitelist (never killed by Doze/battery savers)
                "dumpsys deviceidle whitelist +$pkg"
            )

            for (cmd in commands) {
                val res = executor.executeForResult(cmd)
                Log.d(TAG, "Shizuku cmd: $cmd -> exit=${res.exitCode} out='${res.stdout}' err='${res.stderr}'")
            }

            // 6. Enable PurgeShot Accessibility Service via secure settings
            try {
                val serviceComponent = "$pkg/com.frerox.toolz.service.PurgeShotAccessibilityService"
                val current = executor.executeSingle("settings get secure enabled_accessibility_services").trim()
                if (!current.contains("PurgeShotAccessibilityService")) {
                    val updated = if (current.isBlank() || current == "null") serviceComponent else "$current:$serviceComponent"
                    executor.executeSingle("settings put secure enabled_accessibility_services $updated")
                }
                executor.executeSingle("settings put secure accessibility_enabled 1")
                Log.i(TAG, "Accessibility service enabled via Shizuku secure settings")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable accessibility via Shizuku", e)
            }

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "forceAllPermissionsViaShizuku failed", e)
            return@withContext false
        }
    }
}
