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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted update session. Written when an update is found and updated as
 * the download progresses, so background work, notification taps and app
 * restarts all share one record instead of relying on a live ViewModel.
 */
data class StoredUpdate(
    val version: String,
    val versionCode: Int,
    val changelog: String,
    val downloadUrl: String,
    val sizeBytes: Long?,
    val sha256: String?,
    val isCritical: Boolean,
    val abi: String,
    val source: String,
    val apkPath: String?,
) {
    fun toPending(): PendingUpdate = PendingUpdate(
        version = version,
        versionCode = versionCode,
        changelog = changelog,
        downloadUrl = downloadUrl,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        isCritical = isCritical,
        abi = abi,
        source = source,
    )

    companion object {
        fun from(update: PendingUpdate, apkPath: String? = null): StoredUpdate = StoredUpdate(
            version = update.version,
            versionCode = update.versionCode,
            changelog = update.changelog,
            downloadUrl = update.downloadUrl,
            sizeBytes = update.sizeBytes,
            sha256 = update.sha256,
            isCritical = update.isCritical,
            abi = update.abi,
            source = update.source,
            apkPath = apkPath,
        )
    }
}

/**
 * Small SharedPreferences store for the update session. DataStore keeps the
 * user-facing settings; this holds transient-but-restart-safe state.
 *
 * Skip/notified versions stay in their legacy pref files so upgrading the app
 * does not resurrect a skipped release or re-post an old notification.
 */
@Singleton
class UpdateStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private companion object {
        const val PREFS = "toolz_update_state"

        const val KEY_VERSION = "pending_version"
        const val KEY_VERSION_CODE = "pending_version_code"
        const val KEY_CHANGELOG = "pending_changelog"
        const val KEY_URL = "pending_url"
        const val KEY_SIZE = "pending_size"
        const val KEY_SHA256 = "pending_sha256"
        const val KEY_CRITICAL = "pending_critical"
        const val KEY_ABI = "pending_abi"
        const val KEY_SOURCE = "pending_source"
        const val KEY_APK_PATH = "pending_apk_path"
        const val KEY_INSTALL_CONFIRMATION = "install_confirmation"

        const val SKIP_PREFS = "toolz_update_skip"
        const val KEY_SKIPPED_VERSION = "skipped_version"

        const val NOTIF_PREFS = "toolz_update_notifs"
        const val KEY_NOTIFIED_VERSION = "notified_version"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val skipPrefs = context.getSharedPreferences(SKIP_PREFS, Context.MODE_PRIVATE)
    private val notifPrefs = context.getSharedPreferences(NOTIF_PREFS, Context.MODE_PRIVATE)

    fun pending(): StoredUpdate? {
        val version = prefs.getString(KEY_VERSION, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val url = prefs.getString(KEY_URL, null).orEmpty()
        if (url.isBlank()) return null
        return StoredUpdate(
            version = version,
            versionCode = prefs.getInt(KEY_VERSION_CODE, 0),
            changelog = prefs.getString(KEY_CHANGELOG, null).orEmpty(),
            downloadUrl = url,
            sizeBytes = prefs.getLong(KEY_SIZE, 0L).takeIf { it > 0L },
            sha256 = prefs.getString(KEY_SHA256, null),
            isCritical = prefs.getBoolean(KEY_CRITICAL, false),
            abi = prefs.getString(KEY_ABI, null).orEmpty(),
            source = prefs.getString(KEY_SOURCE, null).orEmpty().ifBlank { "manifest" },
            apkPath = prefs.getString(KEY_APK_PATH, null),
        )
    }

    fun setPending(update: StoredUpdate?) {
        val editor = prefs.edit()
        if (update == null) {
            editor
                .remove(KEY_VERSION)
                .remove(KEY_VERSION_CODE)
                .remove(KEY_CHANGELOG)
                .remove(KEY_URL)
                .remove(KEY_SIZE)
                .remove(KEY_SHA256)
                .remove(KEY_CRITICAL)
                .remove(KEY_ABI)
                .remove(KEY_SOURCE)
                .remove(KEY_APK_PATH)
        } else {
            editor
                .putString(KEY_VERSION, update.version)
                .putInt(KEY_VERSION_CODE, update.versionCode)
                .putString(KEY_CHANGELOG, update.changelog)
                .putString(KEY_URL, update.downloadUrl)
                .putLong(KEY_SIZE, update.sizeBytes ?: 0L)
                .putString(KEY_SHA256, update.sha256)
                .putBoolean(KEY_CRITICAL, update.isCritical)
                .putString(KEY_ABI, update.abi)
                .putString(KEY_SOURCE, update.source)
                .putString(KEY_APK_PATH, update.apkPath)
        }
        editor.apply()
    }

    fun skippedVersion(): String? =
        skipPrefs.getString(KEY_SKIPPED_VERSION, null)?.takeIf { it.isNotBlank() }

    fun setSkippedVersion(version: String?) {
        skipPrefs.edit().apply {
            if (version.isNullOrBlank()) remove(KEY_SKIPPED_VERSION)
            else putString(KEY_SKIPPED_VERSION, version)
        }.apply()
    }

    fun notifiedVersion(): String? =
        notifPrefs.getString(KEY_NOTIFIED_VERSION, null)?.takeIf { it.isNotBlank() }

    /** Returns true exactly once per version. */
    fun markNotifiedIfNew(version: String): Boolean {
        if (notifiedVersion() == version) return false
        notifPrefs.edit().putString(KEY_NOTIFIED_VERSION, version).apply()
        return true
    }

    /**
     * Version reported by the PackageInstaller before the process restarted.
     * On the next launch it is compared with the running version to show the
     * "Updated" confirmation instead of an empty updater.
     */
    fun installConfirmation(): String? =
        prefs.getString(KEY_INSTALL_CONFIRMATION, null)?.takeIf { it.isNotBlank() }

    fun setInstallConfirmation(version: String?) {
        prefs.edit().apply {
            if (version.isNullOrBlank()) remove(KEY_INSTALL_CONFIRMATION)
            else putString(KEY_INSTALL_CONFIRMATION, version)
        }.commit()
    }

    fun clearSession() {
        setPending(null)
        setInstallConfirmation(null)
    }
}
