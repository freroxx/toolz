/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.util.secure

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Namespace binding for Toolz secure containers.
 *
 * A container written by Toolz records no plaintext identifier, but its KDF
 * input is mixed with this value. Another app that happens to implement the
 * same envelope format therefore cannot open a Toolz file even when the user
 * supplies the right code/password.
 *
 * SECURITY NOTE: this is deliberately NOT a secret. It is compiled into the
 * APK and can be recovered by anyone who reverse-engineers the app. It is a
 * namespace/ownership binding (the "tied to the app" property), not a
 * substitute for the user secret or for the device Keystore.
 */
object AppBinding {

    /**
     * Fixed pepper. Kept stable across builds so a container written by an
     * older version still opens after an update. Changing it is a format break.
     */
    private const val PEPPER = "toolz.secure.container.v2/whisper-aubup"

    fun of(context: Context): String {
        val packageName = context.packageName
        val certificate = signingCertificateSha256(context)
        return buildString {
            append(packageName)
            append('|')
            append(certificate ?: "no-cert")
            append('|')
            append(PEPPER)
        }
    }

    /**
     * Best-effort SHA-256 of the app's first signing certificate. A missing or
     * unreadable certificate degrades to a package-only binding instead of
     * blocking backups.
     */
    private fun signingCertificateSha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ).signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES,
            ).signatures
        }
        val bytes = signatures?.firstOrNull()?.toByteArray() ?: return null
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
