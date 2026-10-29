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

package com.frerox.toolz.data.clipboard

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.frerox.toolz.util.shizuku.ShizukuHelper

/**
 * Single source of truth for clipboard capture readiness.
 *
 * Android 10+ blocks background clipboard reads. Toolz uses Shizuku's shell
 * user service (`cmd clipboard get`) or, on ROMs where that command is not
 * available, the `android:read_clipboard` app-op granted through Shizuku.
 * Accessibility services do NOT grant clipboard access and are not offered as
 * a capture path. Without either capability the tool still works while Toolz
 * is in the foreground, plus manual paste, share target, ACTION_PROCESS_TEXT
 * and the QS popup.
 */
object ClipboardGate {

    enum class Status {
        /** User paused monitoring in Toolz settings. */
        PAUSED,
        /** Background capture through Shizuku's user service. */
        ACTIVE_SHIZUKU,
        /** Background capture through the Shizuku-granted read_clipboard app-op. */
        ACTIVE_APP_OP,
        /** No background capability; foreground/manual/share capture still works. */
        FOREGROUND_ONLY,
        /** First run: onboarding has not been completed yet. */
        SETUP_REQUIRED,
    }

    enum class CaptureMode { BACKGROUND_SHIZUKU, BACKGROUND_APP_OP, FOREGROUND }

    data class Capabilities(
        val shizukuInstalled: Boolean = false,
        val shizukuRunning: Boolean = false,
        val shizukuAuthorized: Boolean = false,
        val appOpAllowed: Boolean = false,
        val onboardingSeen: Boolean = false,
    ) {
        val backgroundCapable: Boolean get() = shizukuAuthorized || appOpAllowed
        val preferredMode: CaptureMode
            get() = when {
                shizukuAuthorized -> CaptureMode.BACKGROUND_SHIZUKU
                appOpAllowed -> CaptureMode.BACKGROUND_APP_OP
                else -> CaptureMode.FOREGROUND
            }
    }

    fun capabilities(context: Context, onboardingSeen: Boolean): Capabilities {
        val installed = ShizukuHelper.isInstalled(context)
        val running = ShizukuHelper.isRunning()
        val authorized = running && ShizukuHelper.isAuthorized()
        return Capabilities(
            shizukuInstalled = installed,
            shizukuRunning = running,
            shizukuAuthorized = authorized,
            appOpAllowed = isClipboardAppOpAllowed(context),
            onboardingSeen = onboardingSeen,
        )
    }

    fun evaluate(
        context: Context,
        monitoringEnabled: Boolean,
        onboardingSeen: Boolean = true,
    ): Status {
        if (!monitoringEnabled) return Status.PAUSED
        val caps = capabilities(context, onboardingSeen)
        if (!caps.onboardingSeen) return Status.SETUP_REQUIRED
        return when {
            caps.shizukuAuthorized -> Status.ACTIVE_SHIZUKU
            caps.appOpAllowed -> Status.ACTIVE_APP_OP
            else -> Status.FOREGROUND_ONLY
        }
    }

    /** True when Toolz can read the clipboard without being the focused app. */
    fun canCaptureInBackground(context: Context): Boolean {
        if (ShizukuHelper.isAuthorized()) return true
        return isClipboardAppOpAllowed(context)
    }

    /**
     * Reads the local app-op state. `unsafeCheckOpNoThrow` is available from
     * API 29 (minSdk is 31), so no reflection is needed.
     */
    fun isClipboardAppOpAllowed(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        if (appOps == null) {
            false
        } else {
            val mode = appOps.unsafeCheckOpNoThrow(
                OP_READ_CLIPBOARD,
                Process.myUid(),
                context.packageName,
            )
            mode == AppOpsManager.MODE_ALLOWED
        }
    } catch (_: Exception) {
        false
    }

    fun shizukuInstallIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://shizuku.rikka.app/download/"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** App-op name accepted by `appops set`. */
    const val OP_READ_CLIPBOARD = "android:read_clipboard"

    /** Shell op name accepted by `appops set` (no namespace prefix). */
    const val OP_READ_CLIPBOARD_SHELL = "READ_CLIPBOARD"
}
