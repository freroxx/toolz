/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.data.clipboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.frerox.toolz.util.shizuku.ShizukuHelper

/**
 * Single source of truth for clipboard capture readiness.
 *
 * Android 10+ blocks background clipboard reads. Toolz therefore REQUIRES
 * Shizuku OR the Toolz accessibility service — there is no silent
 * always-on monitoring without one of them.
 */
object ClipboardGate {

    enum class Status {
        /** User paused monitoring in Toolz settings. */
        PAUSED,
        /** Monitoring on + Shizuku authorized. Best path. */
        ACTIVE_SHIZUKU,
        /** Monitoring on + accessibility enabled. Fallback path. */
        ACTIVE_ACCESSIBILITY,
        /** Monitoring on but neither method available. */
        SETUP_REQUIRED,
    }

    fun evaluate(
        context: Context,
        monitoringEnabled: Boolean,
    ): Status {
        if (!monitoringEnabled) return Status.PAUSED
        if (ShizukuHelper.isAuthorized()) return Status.ACTIVE_SHIZUKU
        if (isToolzAccessibilityEnabled(context)) return Status.ACTIVE_ACCESSIBILITY
        return Status.SETUP_REQUIRED
    }

    fun isToolzAccessibilityEnabled(context: Context): Boolean = try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am != null) {
            val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
                .any {
                    it.resolveInfo.serviceInfo.packageName == context.packageName &&
                        it.resolveInfo.serviceInfo.name.contains("FocusFlowAccessibilityService")
                }
            if (enabled) return true
        }
        // Fallback to secure-settings string (works even if AccessibilityManager lags).
        val expected = "${context.packageName}/com.frerox.toolz.service.FocusFlowAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        enabledServices?.contains(expected) == true
    } catch (_: Exception) {
        false
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
