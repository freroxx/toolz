/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.access

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

object CleanerAutomationState {
    fun isAccessibilityEnabled(context: Context, serviceIdSuffix: String = "CleanerAccessibilityService"): Boolean = try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.resolveInfo.serviceInfo.name.contains(serviceIdSuffix) }
    } catch (_: Exception) { false }
}
