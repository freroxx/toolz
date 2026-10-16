/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * In-app UI overrides for the dev console. Everything here is reversible
 * and app-local:
 * - RTL: flips the current Activity's decor view (no locale change).
 * - Font scale: updates the Activity resources configuration + recreates.
 * - System developer options / notification settings: deep-link intents,
 *   because true layout-bounds / animation-scale toggles require
 *   WRITE_SECURE_SETTINGS and cannot be flipped by a normal app.
 */

package com.frerox.toolz.data.developer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.core.view.WindowCompat

object DevUiOverrider {

    fun setRtl(activity: Activity, rtl: Boolean) {
        try {
            val decor = activity.window?.decorView ?: return
            decor.layoutDirection =
                if (rtl) android.view.View.LAYOUT_DIRECTION_RTL
                else android.view.View.LAYOUT_DIRECTION_LTR
        } catch (_: Exception) { }
    }

    fun isRtl(activity: Activity): Boolean {
        return try {
            activity.window?.decorView?.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        } catch (_: Exception) { false }
    }

    fun setFontScale(activity: Activity, scale: Float) {
        try {
            @Suppress("DEPRECATION")
            val res = activity.resources
            @Suppress("DEPRECATION")
            val config = res.configuration
            config.fontScale = scale
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            activity.recreate()
        } catch (_: Exception) { }
    }

    fun currentFontScale(context: Context): Float {
        return try { context.resources.configuration.fontScale } catch (_: Exception) { 1f }
    }

    fun openSystemDeveloperOptions(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    fun openAppNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    fun densityDpi(context: Context): Int {
        return try {
            context.resources.displayMetrics.densityDpi
        } catch (_: Exception) { DisplayMetrics.DENSITY_MEDIUM }
    }
}
