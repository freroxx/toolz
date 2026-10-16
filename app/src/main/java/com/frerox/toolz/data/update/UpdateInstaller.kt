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

package com.frerox.toolz.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.frerox.toolz.worker.UpdateReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Result of asking [UpdateInstaller.install] to launch an install UI. */
sealed interface InstallLaunch {
    data object SessionStarted : InstallLaunch
    data object ViewIntentStarted : InstallLaunch
    data object NeedsPermission : InstallLaunch
}

/** Terminal outcome parsed from the PackageInstaller status broadcast. */
sealed interface InstallStatus {
    data object Success : InstallStatus
    data object Aborted : InstallStatus
    data class Blocked(val msg: String) : InstallStatus
    data class Failed(val msg: String) : InstallStatus
}

/**
 * Installs a downloaded APK, preferring a PackageInstaller session
 * (system install UI, no FileProvider involved) with an ACTION_VIEW
 * FileProvider fallback. Sideloads always require user confirmation —
 * USER_ACTION_NOT_REQUIRED is deliberately never set.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ACTION_INSTALL_STATUS = "com.frerox.toolz.INSTALL_STATUS"
        private const val TAG = "UpdateInstaller"
    }

    fun canInstall(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallSettings(activityContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activityContext.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityContext.startActivity(intent)
        }
    }

    /**
     * Commit a full-install PackageInstaller session for [apkFile].
     * Returns true once the commit is enqueued; the final outcome
     * arrives via [ACTION_INSTALL_STATUS] (see [statusFromIntent]).
     */
    fun installWithSession(apkFile: File): Boolean {
        var sessionId = -1
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(context.packageName)
                setSize(apkFile.length())
            }
            sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            try {
                session.openWrite("apk", 0, -1).use { out ->
                    apkFile.inputStream().use { input ->
                        val buf = ByteArray(8192)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                        }
                        out.flush()
                        // Session streams are ParcelFileDescriptor-backed
                        // (a FileOutputStream subclass); sync when possible.
                        (out as? FileOutputStream)?.fd?.sync()
                    }
                }
                val statusIntent = Intent(context, UpdateReceiver::class.java).apply {
                    action = ACTION_INSTALL_STATUS
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        PendingIntent.FLAG_IMMUTABLE
                    }
                val statusReceiver = PendingIntent.getBroadcast(
                    context, sessionId, statusIntent, flags
                )
                session.commit(statusReceiver.intentSender)
                session.close()
                return true
            } catch (e: Exception) {
                try { session.close() } catch (_: Exception) {}
                throw e
            }
        } catch (e: Exception) {
            Log.w(TAG, "session install failed: ${e.message}")
            if (sessionId != -1) {
                try { context.packageManager.packageInstaller.abandonSession(sessionId) } catch (_: Exception) {}
            }
            return false
        }
    }

    /** Legacy fallback: ACTION_VIEW through FileProvider (same as InstallHelper). */
    fun installWithView(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Launch the install flow: session first, ACTION_VIEW fallback.
     * Returns [InstallLaunch.NeedsPermission] when the unknown-sources
     * gate blocks us (caller shows rationale) or when neither path
     * could even be launched.
     */
    fun install(apkFile: File): InstallLaunch {
        if (!canInstall()) return InstallLaunch.NeedsPermission
        if (installWithSession(apkFile)) return InstallLaunch.SessionStarted
        return try {
            installWithView(apkFile)
            InstallLaunch.ViewIntentStarted
        } catch (e: Exception) {
            Log.w(TAG, "view install failed: ${e.message}")
            InstallLaunch.NeedsPermission
        }
    }

    /** Parse a PackageInstaller [ACTION_INSTALL_STATUS] broadcast. */
    fun statusFromIntent(intent: Intent): InstallStatus {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            ?: intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)
            ?: "Unknown install error"
        return when (status) {
            PackageInstaller.STATUS_SUCCESS -> InstallStatus.Success
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallStatus.Aborted
            PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallStatus.Blocked(msg)
            else -> InstallStatus.Failed(msg)
        }
    }
}
