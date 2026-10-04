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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val updateService: UpdateService,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {
    suspend fun checkForUpdates(showNotification: Boolean = false): UpdateCheckResult {
        return try {
            // 1. Try GitHub Release API (dynamic)
            val response = try {
                updateService.getLatestRelease(
                    UpdateConstants.GITHUB_OWNER,
                    UpdateConstants.GITHUB_REPO
                )
            } catch (e: Exception) {
                null
            }

            if (response?.isSuccessful == true) {
                val release = response.body()
                if (release != null) {
                    val currentVersion = getCurrentVersionName()
                    val latestVersion = release.tagName.removePrefix("v")

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        val preferredAbi = settingsRepository.preferredAbi.first()
                        val bestAsset = UpdateHelper.getBestAsset(release.assets, preferredAbi)
                        
                        if (bestAsset != null) {
                            val result = UpdateCheckResult.NewUpdate(
                                version = latestVersion,
                                changelog = release.body ?: "Bug fixes and performance improvements.",
                                downloadUrl = bestAsset.downloadUrl,
                                isCritical = false
                            )
                            saveUpdateInfo(result)
                            if (showNotification) {
                                showUpdateNotification(latestVersion)
                            }
                            return result
                        }
                    } else {
                        return UpdateCheckResult.UpToDate
                    }
                }
            }

            // 2. Fallback to Manifest (statically controlled)
            val manifestResponse = try {
                updateService.getUpdateManifest(UpdateConstants.MANIFEST_URL)
            } catch (e: Exception) {
                null
            }
            
            if (manifestResponse?.isSuccessful == true) {
                val manifest = manifestResponse.body()
                if (manifest != null) {
                    // Fail-closed manifest authentication: when the manifest
                    // is signed, a missing/invalid Ed25519 signature means
                    // NO UPDATE — a mod or MITM can never forge a
                    // minimumVersionCode bump or swap a download URL.
                    if (manifest.signature != null) {
                        val rawBytes = try {
                            updateService.getUpdateManifestRaw(UpdateConstants.MANIFEST_URL)
                                .body()?.bytes()
                        } catch (e: Exception) {
                            null
                        }
                        val verified = rawBytes != null && verifyManifestSignature(
                            canonicalManifestBytes(rawBytes),
                            manifest.signature
                        )
                        if (!verified) {
                            Log.w(
                                TAG,
                                "Update manifest signature missing or invalid — " +
                                    "treating as no-update (fail-closed)"
                            )
                            return UpdateCheckResult.UpToDate
                        }
                        // Only a signature-verified manifest may seed the
                        // LOCAL Whisper gate's cached version floor.
                        cacheManifestFloor(manifest)
                    }
                    val currentVersion = getCurrentVersionName()
                    if (isNewerVersion(currentVersion, manifest.versionName)) {
                        val preferredAbi = settingsRepository.preferredAbi.first()
                        val bestRelease = manifest.releases?.let { UpdateHelper.getBestRelease(it, preferredAbi) }
                        
                        if (bestRelease != null) {
                            val result = UpdateCheckResult.NewUpdate(
                                version = manifest.versionName,
                                changelog = manifest.changelog ?: "New version available with improvements.",
                                downloadUrl = bestRelease.downloadUrl,
                                isCritical = manifest.isCritical ?: false
                            )
                            saveUpdateInfo(result)
                            if (showNotification) {
                                showUpdateNotification(manifest.versionName)
                            }
                            return result
                        }
                    } else {
                        return UpdateCheckResult.UpToDate
                    }
                }
            }
            
            UpdateCheckResult.Error("Could not fetch update information")
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Unknown error during update check")
        }
    }

    private var updateApkUrlInternal: String? = null

    private suspend fun saveUpdateInfo(update: UpdateCheckResult.NewUpdate) {
        updateApkUrlInternal = update.downloadUrl
        settingsRepository.setAvailableUpdate(
            update.version,
            update.changelog,
            update.downloadUrl
        )
    }

    private fun showUpdateNotification(version: String) {
        // Periodic UpdateCheckWorker runs with showNotification=true every time:
        // collapse re-notifies for the same version so the shade holds one row.
        val prefs = context.getSharedPreferences("toolz_update_notifs", Context.MODE_PRIVATE)
        if (prefs.getString("notified_version", null) == version) return
        prefs.edit().putString("notified_version", version).apply()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationHelper.createAllChannels(context)

        // 1. Content Intent: Open Update Screen
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_update_dialog", true)
            putExtra("navigate_to", "update_settings")
        }
        val contentPendingIntent = PendingIntent.getActivity(context, 8001, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // 2. Action: Download Now
        val downloadUrl = updateApkUrlInternal ?: ""
        val downloadIntent = Intent(context, com.frerox.toolz.worker.UpdateReceiver::class.java).apply {
            action = "com.frerox.toolz.DOWNLOAD_UPDATE"
            putExtra("url", downloadUrl)
            putExtra("version", version)
        }
        val downloadPendingIntent = PendingIntent.getBroadcast(context, 8002, downloadIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // 3. Action: View Changelog
        val changelogIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "update_settings")
        }
        val changelogPendingIntent = PendingIntent.getActivity(context, 8003, changelogIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationHelper.baseBuilder(context, NotificationHelper.CHANNEL_APP_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_toolz)
            .setContentTitle("New Version Available: $version")
            .setContentText("A new version of Toolz is ready for deployment. Tap to see what's new.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Toolz $version is available with bug fixes and new features. Download now to stay up to date."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .addAction(R.drawable.ic_stat_toolz, "Download", downloadPendingIntent)
            .addAction(R.drawable.ic_stat_toolz, "Details", changelogPendingIntent)
            .build()

        notificationManager.notify(NotificationHelper.ID_APP_UPDATE, notification)
    }

    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        if (current == latest) return false
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val curr = currentParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > curr) return true
            if (lat < curr) return false
        }
        return false
    }

    // ── Anti-mod update-lock ────────────────────────────────────────────
    // Whisper nav gate (WhisperBuildGate) calls [checkWhisperBlocked]: while the
    // installed build is below the manifest floor — or a critical release is
    // newer than the install — Whisper screens show a blocking "Update
    // required" card instead of chat UI. Fail-open on network error so offline
    // users are never bricked by an unreachable manifest.

    fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    /** Pure rule — unit-testable without Android. */
    fun isWhisperBlockedByManifest(installedCode: Long, manifest: UpdateManifest): Boolean {
        val floor = manifest.minimumVersionCode?.toLong() ?: 0L
        if (floor > 0 && installedCode < floor) return true
        // Reuse isCritical: a critical release newer than the install also
        // pauses Whisper until the user updates.
        if (manifest.isCritical == true && installedCode < manifest.versionCode.toLong()) return true
        return false
    }

    suspend fun checkWhisperBlocked(): WhisperBlockState {
        return try {
            val res = updateService.getUpdateManifest(UpdateConstants.MANIFEST_URL)
            if (!res.isSuccessful) return WhisperBlockState.NotBlocked
            val manifest = res.body() ?: return WhisperBlockState.NotBlocked
            if (isWhisperBlockedByManifest(getCurrentVersionCode(), manifest)) {
                WhisperBlockState.Blocked(
                    latestVersionName = manifest.versionName,
                    latestVersionCode = manifest.versionCode,
                    isCritical = manifest.isCritical == true,
                )
            } else {
                WhisperBlockState.NotBlocked
            }
        } catch (e: Exception) {
            WhisperBlockState.NotBlocked
        }
    }

    // ── Offline update-manifest signature gate ──────────────────
    // The manifest is the ONLY channel that can raise the Whisper
    // minimumVersionCode floor, so it must be authenticated. The
    // Ed25519 public half is embedded at build time; the private
    // half stays with the release signer. java.security Ed25519
    // (JDK/Android API 28+) — no new dependency.

    companion object {
        private const val TAG = "UpdateRepository"
        private const val MANIFEST_CACHE_PREFS = "toolz_update_manifest"
        private const val KEY_CACHED_MINIMUM_VERSION_CODE = "minimum_version_code"

        /**
         * Official update-manifest Ed25519 public key — raw 32-byte
         * key, base64.
         *
         * REPLACE_ME — generate a keypair and embed the public half:
         *   python3 -c "from cryptography.hazmat.primitives.asymmetric import ed25519; from cryptography.hazmat.primitives import serialization; import base64; k = ed25519.Ed25519PrivateKey.generate(); print(base64.b64encode(k.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)).decode())"
         * or with openssl:
         *   openssl genpkey -algorithm Ed25519 -out ed25519-priv.pem
         *   openssl pkey -in ed25519-priv.pem -pubout -outform DER | tail -c 32 | openssl base64 -A
         * Keep the private key offline; sign every released
         * update_manifest.json with it (see verifyManifestSignature).
         */
        const val ED25519_PUBLIC_KEY_B64 = "pLIOx0vcMq4/JWQhrsNl97x8cTIYHvtYwP7NGnLkYBM="

        /** X.509 SubjectPublicKeyInfo header wrapping a raw Ed25519 key. */
        private val ED25519_X509_PREFIX = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        )

        /**
         * Verify the Ed25519 signature of an update manifest against
         * the embedded [ED25519_PUBLIC_KEY_B64].
         *
         * @param manifestJsonBytesWithoutSignatureField canonical manifest
         *        bytes — the raw manifest JSON with the `signature` and
         *        `_comment` fields removed, minified (no whitespace),
         *        keys in original order. See [canonicalManifestBytes].
         * @param signatureB64 base64 Ed25519 signature (64 bytes).
         * @return true only when the signature verifies; false on ANY
         *         error (fail-closed).
         *
         * Release-signing tool (both produce the same 64-byte signature
         * over the canonical bytes):
         *   python:
         *     python3 -c "import json,base64;from cryptography.hazmat.primitives.asymmetric import ed25519; m=json.load(open('update_manifest.json')); m.pop('signature',None); m.pop('_comment',None); c=json.dumps(m,separators=(',',':')).encode(); k=ed25519.Ed25519PrivateKey.from_private_bytes(base64.b64decode(open('priv.key.b64').read())); print(base64.b64encode(k.sign(c)).decode())"
         *   openssl (canonical.json = manifest minus signature/_comment, minified):
         *     openssl pkeyutl -sign -inkey ed25519-priv.pem -rawin -in canonical.json | openssl base64 -A
         */
        fun verifyManifestSignature(
            manifestJsonBytesWithoutSignatureField: ByteArray,
            signatureB64: String,
        ): Boolean = verifyManifestSignatureWithKey(
            manifestJsonBytesWithoutSignatureField, signatureB64, ED25519_PUBLIC_KEY_B64
        )

        /**
         * Same as [verifyManifestSignature] with an explicit raw-32-byte
         * base64 public key — exposed for unit tests (internal).
         */
        internal fun verifyManifestSignatureWithKey(
            manifestJsonBytesWithoutSignatureField: ByteArray,
            signatureB64: String,
            publicKeyB64: String,
        ): Boolean {
            return try {
                val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                    X509EncodedKeySpec(
                        ED25519_X509_PREFIX + Base64.getDecoder().decode(publicKeyB64)
                    )
                )
                val signature = Signature.getInstance("Ed25519").apply {
                    initVerify(publicKey)
                    update(manifestJsonBytesWithoutSignatureField)
                }
                signature.verify(Base64.getDecoder().decode(signatureB64))
            } catch (e: Exception) {
                false // fail-closed: bad key, bad signature, bad base64
            }
        }

        /**
         * Canonical manifest bytes for signature verification: the raw
         * JSON with the `signature` and `_comment` fields removed,
         * re-serialized compactly (org.json preserves key order).
         * Signers MUST produce identical bytes — python equivalent:
         *   json.dumps(m, separators=(',',':'))
         */
        fun canonicalManifestBytes(manifestJsonBytes: ByteArray): ByteArray {
            val json = JSONObject(String(manifestJsonBytes, Charsets.UTF_8))
            json.remove("signature")
            json.remove("_comment")
            return json.toString().toByteArray(Charsets.UTF_8)
        }
    }

    /**
     * Remember the manifest's minimumVersionCode — only from
     * signature-verified manifests — so the LOCAL Whisper gate
     * (WhisperBuildGate.gateWhisperAccess) can enforce the floor
     * while offline.
     */
    private fun cacheManifestFloor(manifest: UpdateManifest) {
        manifest.minimumVersionCode?.let { floor ->
            context.getSharedPreferences(MANIFEST_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_CACHED_MINIMUM_VERSION_CODE, floor)
                .apply()
        }
    }

    /** Last signature-verified manifest floor; null when never cached. */
    fun cachedMinimumVersionCode(): Int? {
        val floor = context
            .getSharedPreferences(MANIFEST_CACHE_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CACHED_MINIMUM_VERSION_CODE, 0)
        return floor.takeIf { it > 0 }
    }
}

sealed interface WhisperBlockState {
    data object NotBlocked : WhisperBlockState
    data class Blocked(
        val latestVersionName: String,
        val latestVersionCode: Int,
        val isCritical: Boolean,
        /** Set when blocked by the LOCAL runtime gate (cert pin / debuggable / version floor). */
        val reason: String? = null,
        /** Official release page to offer the user; the blocked card opens this. */
        val officialDownloadUrl: String? = null,
    ) : WhisperBlockState
}

sealed class UpdateCheckResult {
    data class NewUpdate(
        val version: String,
        val changelog: String,
        val downloadUrl: String,
        val isCritical: Boolean
    ) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}
