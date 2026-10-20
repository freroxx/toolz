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
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import com.frerox.toolz.worker.UpdateReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of asking [UpdateInstaller.install] to launch an install UI. */
sealed interface InstallLaunch {
    data object SessionStarted : InstallLaunch
    data object ViewIntentStarted : InstallLaunch
    data object NeedsPermission : InstallLaunch
}

/** Outcome of validating a downloaded APK against the installed app. */
sealed interface ApkValidation {
    data object Valid : ApkValidation
    data class Invalid(val reason: ApkIdentity.Reason) : ApkValidation
}

/** Terminal outcome parsed from the PackageInstaller status broadcast. */
sealed interface InstallStatus {
    data object Success : InstallStatus
    data object Aborted : InstallStatus

    /** The installer needs its confirmation UI launched by the receiver. */
    data class PendingUserAction(val intent: Intent?) : InstallStatus
    data class Blocked(val msg: String) : InstallStatus
    data class Failed(val msg: String) : InstallStatus
}

/**
 * Installs a downloaded APK, preferring a PackageInstaller session with an
 * ACTION_VIEW FileProvider fallback. Sideloads always require user
 * confirmation — USER_ACTION_NOT_REQUIRED is deliberately never set.
 *
 * Nothing is installed until [validate] confirms the archive is the same
 * package, a newer version and signed with the running app's key.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val ACTION_INSTALL_STATUS = "com.frerox.toolz.INSTALL_STATUS"
        private const val TAG = "UpdateInstaller"
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(activityContext: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${activityContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activityContext.startActivity(intent)
    }

    /**
     * Checks package name, version code and signing certificate against the
     * running app. Safe to call from a background thread; usually called
     * inside [Dispatchers.IO].
     */
    fun validate(apkFile: File): ApkValidation {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            return ApkValidation.Invalid(ApkIdentity.Reason.NotAnApk)
        }
        return try {
            val packageManager = context.packageManager
            val installed = packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            @Suppress("DEPRECATION")
            val archive = packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ) ?: return ApkValidation.Invalid(ApkIdentity.Reason.NotAnApk)

            val reason = ApkIdentity.evaluate(
                installedPackage = context.packageName,
                installedVersionCode = installed.longVersionCode,
                installedSigners = signerDigests(installed),
                apkPackage = archive.packageName,
                apkVersionCode = archive.longVersionCode,
                apkSigners = signerDigests(archive),
            )
            if (reason == null) ApkValidation.Valid else ApkValidation.Invalid(reason)
        } catch (e: Exception) {
            Log.w(TAG, "APK validation failed: ${e.message}")
            ApkValidation.Invalid(ApkIdentity.Reason.NotAnApk)
        }
    }

    private fun signerDigests(info: PackageInfo?): Set<String> {
        val signingInfo = try {
            info?.signingInfo
        } catch (_: Exception) {
            null
        } ?: return emptySet()
        val certificates = runCatching { signingInfo.apkContentsSigners.toList() }
            .getOrElse { emptyList() }
            .ifEmpty {
                runCatching { signingInfo.signingCertificateHistory.toList() }
                    .getOrDefault(emptyList())
            }
        return certificates.mapNotNull { certificate ->
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                digest.digest(certificate.toByteArray())
                    .joinToString("") { "%02x".format(Locale.US, it) }
            } catch (_: Exception) {
                null
            }
        }.toSet()
    }

    /**
     * Launch the install flow. The session path copies the APK on an IO
     * thread; the final outcome arrives via [ACTION_INSTALL_STATUS].
     */
    suspend fun install(apkFile: File): InstallLaunch = withContext(Dispatchers.IO) {
        if (!canInstall()) return@withContext InstallLaunch.NeedsPermission
        if (installWithSession(apkFile)) return@withContext InstallLaunch.SessionStarted
        try {
            installWithView(apkFile)
            InstallLaunch.ViewIntentStarted
        } catch (e: Exception) {
            Log.w(TAG, "view install failed: ${e.message}")
            InstallLaunch.NeedsPermission
        }
    }

    /** Commit a full-install session. Returns true once commit is enqueued. */
    private fun installWithSession(apkFile: File): Boolean {
        var sessionId = -1
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(context.packageName)
                setSize(apkFile.length())
            }
            sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            try {
                session.openWrite("apk", 0, -1).use { output ->
                    apkFile.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                        (output as? FileOutputStream)?.fd?.sync()
                    }
                }
                val statusIntent = Intent(context, UpdateReceiver::class.java).apply {
                    action = ACTION_INSTALL_STATUS
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_MUTABLE
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
                try {
                    context.packageManager.packageInstaller.abandonSession(sessionId)
                } catch (_: Exception) {
                }
            }
            return false
        }
    }

    /** Fallback: ACTION_VIEW through FileProvider. */
    private fun installWithView(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Parse a PackageInstaller [ACTION_INSTALL_STATUS] broadcast. */
    fun statusFromIntent(intent: Intent): InstallStatus {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            ?: intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)
            ?: "The system installer stopped the update."
        return when (status) {
            PackageInstaller.STATUS_SUCCESS -> InstallStatus.Success
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallStatus.Aborted
            PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallStatus.PendingUserAction(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            )
            PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallStatus.Blocked(message)
            else -> InstallStatus.Failed(message)
        }
    }
}
