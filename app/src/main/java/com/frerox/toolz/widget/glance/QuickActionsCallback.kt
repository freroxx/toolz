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

package com.frerox.toolz.widget.glance

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.frerox.toolz.util.VibrationManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull

/**
 * Quick Actions toolbar callbacks.
 * Flashlight toggles torch directly (no app open) with haptic feedback.
 * Falls back to opening the flashlight tool when camera is unavailable
 * or permission is missing — never a dead button.
 */
class QuickActionsCallback : ActionCallback {

    companion object {
        val PARAM_ACTION = ActionParameters.Key<String>("qa_action")
        const val ACTION_FLASHLIGHT = "flashlight"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface QuickActionsEntryPoint {
        fun vibrationManager(): VibrationManager
        fun settingsRepository(): com.frerox.toolz.data.settings.SettingsRepository
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (parameters[PARAM_ACTION] != ACTION_FLASHLIGHT) return
        val entry = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                QuickActionsEntryPoint::class.java
            )
        } catch (_: Exception) {
            null
        }
        try {
            val enabled = entry?.settingsRepository()?.widgetHaptics?.firstOrNull() ?: true
            if (enabled) {
                try { entry?.vibrationManager()?.vibrateClick() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        val toggled = tryToggleTorch(context)
        if (!toggled) {
            // Fallback: open flashlight tool so the tap always does something.
            try {
                val intent = com.frerox.toolz.widget.ui.widgetNavIntent(context, "flashlight").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        } else {
            // Refresh toolbar so any state-driven tint updates.
            try { QuickActionsGlanceWidget().update(context, glanceId) } catch (_: Exception) {}
        }
    }

    private fun tryToggleTorch(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return false
            val cameraId = try {
                cm.cameraIdList.firstOrNull { id ->
                    try {
                        cm.getCameraCharacteristics(id).get(
                            android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
                        ) == true
                    } catch (_: Exception) {
                        false
                    }
                }
            } catch (_: Exception) {
                null
            } ?: return false
            // Track state in prefs — CameraManager has no isTorchOn query.
            val prefs = context.getSharedPreferences("quick_actions_torch", Context.MODE_PRIVATE)
            val currentlyOn = try { prefs.getBoolean("torch_on", false) } catch (_: Exception) { false }
            try {
                cm.setTorchMode(cameraId, !currentlyOn)
                try { prefs.edit().putBoolean("torch_on", !currentlyOn).apply() } catch (_: Exception) {}
                true
            } catch (_: SecurityException) {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
