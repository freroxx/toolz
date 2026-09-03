/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.access

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.frerox.toolz.util.shizuku.ShizukuHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class GateId { ALL_FILES, MEDIA, USAGE_ACCESS, AUTOMATION }
enum class GateState { GRANTED, DENIED, PARTIAL }

data class GateStatus(
    val id: GateId,
    val state: GateState,
    val title: String,
    val reason: String,
    val fixAction: String
)

@Singleton
class AccessGate @Inject constructor(@ApplicationContext private val context: Context) {

    fun allFilesGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    fun mediaGranted(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        val img = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val vid = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        val aud = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        return img && vid && aud
    }

    fun mediaPartial(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        if (mediaGranted()) return false
        val any = listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        return any
    }

    fun usageAccessGranted(): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    fun automationGranted(): Boolean = try {
        ShizukuHelper.isAuthorized() || CleanerAutomationState.isAccessibilityEnabled(context)
    } catch (_: Exception) { false }

    fun statuses(): List<GateStatus> = listOf(
        GateStatus(
            GateId.ALL_FILES,
            if (allFilesGranted()) GateState.GRANTED else GateState.DENIED,
            "All-files access",
            if (allFilesGranted()) "Android/data, obb and leftovers are visible"
            else "Without it CorpseFinder, Empty Folders and System Junk can't see Android/data",
            "Grant"
        ),
        GateStatus(
            GateId.MEDIA,
            when { mediaGranted() -> GateState.GRANTED; mediaPartial() -> GateState.PARTIAL; Build.VERSION.SDK_INT < 33 -> GateState.GRANTED; else -> GateState.DENIED },
            "Photos, videos & audio",
            when { mediaGranted() -> "Media clutter and large media visible"; mediaPartial() -> "Partial: some media kinds still hidden"; Build.VERSION.SDK_INT < 33 -> "Covered by storage permission"; else -> "Media Clutter and gallery cleanup stay empty" },
            "Grant"
        ),
        GateStatus(
            GateId.USAGE_ACCESS,
            if (usageAccessGranted()) GateState.GRANTED else GateState.DENIED,
            "Usage access",
            if (usageAccessGranted()) "Real per-app cache sizes" else "App Cache shows blocked until granted — no silent zero",
            "Open settings"
        ),
        GateStatus(
            GateId.AUTOMATION,
            if (automationGranted()) GateState.GRANTED else GateState.DENIED,
            "Auto-clear (Shizuku or Accessibility)",
            if (automationGranted()) "One-tap cache clearing available" else "Optional: clears internal caches without opening each app",
            "Enable"
        )
    )

    fun intentFor(id: GateId): Intent = when (id) {
        GateId.ALL_FILES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        GateId.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        GateId.AUTOMATION -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        GateId.MEDIA -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }

    companion object {
        /** Runtime Allow/Deny system dialog exists for these (33+). Everything else is settings-only. */
        fun mediaRuntimePermissions(): List<String> =
            if (Build.VERSION.SDK_INT >= 33) listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            ) else emptyList()

        fun notificationRuntimePermission(): String? =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
    }

    /** Mandatory gates: scan stays locked until these are GRANTED. Usage/automation degrade gracefully. */
    fun mandatoryUnmet(): List<GateId> {
        val out = mutableListOf<GateId>()
        if (!allFilesGranted()) out.add(GateId.ALL_FILES)
        if (Build.VERSION.SDK_INT >= 33 && !mediaGranted()) out.add(GateId.MEDIA)
        return out
    }

    fun canScan(): Boolean = mandatoryUnmet().isEmpty()
}
