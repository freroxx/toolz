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
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Resolves the newest installable update from two independent sources:
 *
 *  1. the GitHub release feed (dynamic, but unsigned), and
 *  2. the statically hosted update manifest (Ed25519-signed).
 *
 * Both requests run in parallel. The manifest wins ties because it carries
 * versionCode, size, digest and criticality. A signed manifest with an
 * invalid signature is ignored for release selection, and only a verified
 * manifest may raise the Whisper version floor.
 *
 * The verified/last-successful manifest is cached so an offline check can
 * still offer a known update, and the Whisper gate keeps working offline.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val updateService: UpdateService,
    private val settingsRepository: SettingsRepository,
    private val moshi: Moshi,
    @ApplicationContext private val context: Context,
) {
    private val manifestAdapter by lazy { moshi.adapter(UpdateManifest::class.java) }

    private val manifestPrefs
        get() = context.getSharedPreferences(MANIFEST_CACHE_PREFS, Context.MODE_PRIVATE)

    private data class Candidate(
        val version: String,
        val versionCode: Int,
        val changelog: String,
        val downloadUrl: String,
        val sizeBytes: Long?,
        val sha256: String?,
        val isCritical: Boolean,
        val abi: String,
        val source: String,
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
    }

    private data class SourceResult(
        val candidate: Candidate? = null,
        /** True when the source answered authoritatively (even "no update"). */
        val answered: Boolean = false,
        /** True when a newer release exists but has no matching APK. */
        val sawNewerWithoutAsset: Boolean = false,
        val error: UpdateError? = null,
    )

    private data class ParsedManifest(
        val manifest: UpdateManifest,
        val rawJson: String,
        val verified: Boolean,
    )

    /**
     * Check both sources. Never throws: failures are classified as
     * [UpdateCheckResult.Error].
     */
    suspend fun checkForUpdates(): UpdateCheckResult {
        val current = getCurrentVersionName()
        val preferredAbi = preferredAbi()
        return try {
            val (gitHub, manifest) = coroutineScope {
                val gitHubDeferred = async { fetchGitHub(current, preferredAbi) }
                val manifestDeferred = async { fetchManifest(current, preferredAbi) }
                gitHubDeferred.await() to manifestDeferred.await()
            }

            val manifestCandidate = manifest.candidate
            val gitHubCandidate = gitHub.candidate
            val best = when {
                manifestCandidate != null && gitHubCandidate != null ->
                    if (UpdateVersion.compare(manifestCandidate.version, gitHubCandidate.version) >= 0) {
                        manifestCandidate
                    } else {
                        gitHubCandidate
                    }
                else -> manifestCandidate ?: gitHubCandidate
            }

            if (best != null) {
                // A manifest-sourced candidate is preferred at equal version.
                // If it lost to a newer unsigned GitHub build, still prefer a
                // cached verified manifest when that one is newer.
                val cached = cachedCandidate(preferredAbi)
                val chosen = if (
                    best.source == "github" &&
                    cached != null &&
                    UpdateVersion.compare(cached.version, best.version) > 0
                ) {
                    cached
                } else {
                    best
                }
                return UpdateCheckResult.NewUpdate(chosen.toPending())
            }

            if (gitHub.answered || manifest.answered) {
                // A newer release exists but no APK matched this device.
                if (gitHub.sawNewerWithoutAsset || manifest.sawNewerWithoutAsset) {
                    return UpdateCheckResult.Error(UpdateError.NoCompatibleBuild)
                }
                return UpdateCheckResult.UpToDate
            }

            // Both online sources failed: offer the last verified manifest
            // instead of an error when it still contains a newer release.
            cachedCandidate(preferredAbi)?.let {
                return UpdateCheckResult.NewUpdate(it.toPending())
            }

            val errors = listOfNotNull(gitHub.error, manifest.error)
            UpdateCheckResult.Error(pickError(errors))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "check failed: ${e.message}")
            UpdateCheckResult.Error(UpdateError.fromThrowable(e))
        }
    }

    private suspend fun fetchGitHub(current: String, preferredAbi: String): SourceResult {
        val response = try {
            updateService.getLatestRelease(
                UpdateConstants.GITHUB_OWNER,
                UpdateConstants.GITHUB_REPO,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SourceResult(error = UpdateError.fromThrowable(e))
        }
        if (!response.isSuccessful) {
            return SourceResult(error = UpdateError.fromHttp(response.code()))
        }
        val release = response.body()
            ?: return SourceResult(error = UpdateError.Http(response.code()))
        val latest = UpdateVersion.normalize(release.tagName)
        if (!UpdateVersion.isNewer(current, latest)) {
            return SourceResult(answered = true)
        }
        val asset = UpdateHelper.getBestAsset(release.assets, preferredAbi)
            ?: return SourceResult(answered = true, sawNewerWithoutAsset = true)
        val abi = UpdateHelper.ABI_FILTERS.firstOrNull {
            asset.name.contains(it, ignoreCase = true)
        } ?: "universal"
        return SourceResult(
            candidate = Candidate(
                version = latest,
                versionCode = 0,
                changelog = release.body?.trim().orEmpty()
                    .ifBlank { context.getString(R.string.st_Update_DefaultChangelog) },
                downloadUrl = asset.downloadUrl,
                sizeBytes = null,
                sha256 = null,
                isCritical = false,
                abi = abi,
                source = "github",
            ),
            answered = true,
        )
    }

    private suspend fun fetchManifest(current: String, preferredAbi: String): SourceResult {
        val rawBytes = try {
            val response = updateService.getUpdateManifestRaw(UpdateConstants.MANIFEST_URL)
            if (!response.isSuccessful) {
                return SourceResult(error = UpdateError.fromHttp(response.code()))
            }
            response.body()?.bytes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SourceResult(error = UpdateError.fromThrowable(e))
        }
        if (rawBytes == null) {
            return SourceResult(error = UpdateError.Http(200))
        }

        val parsed = parseManifest(rawBytes)
            ?: return SourceResult(
                error = UpdateError.Verification(
                    overrideMessageRes = R.string.st_Update_ErrorNotVerified,
                )
            )

        // Cache the raw bytes so offline checks and the Whisper floor keep
        // working. Only a verified manifest may touch the version floor.
        cacheManifest(parsed)

        if (!UpdateVersion.isNewer(current, parsed.manifest.versionName)) {
            return SourceResult(answered = true)
        }
        val release = parsed.manifest.releases
            ?.let { UpdateHelper.getBestRelease(it, preferredAbi) }
            ?: return SourceResult(answered = true, sawNewerWithoutAsset = true)

        return SourceResult(
            candidate = Candidate(
                version = UpdateVersion.normalize(parsed.manifest.versionName),
                versionCode = parsed.manifest.versionCode,
                changelog = parsed.manifest.changelog?.trim().orEmpty()
                    .ifBlank { context.getString(R.string.st_Update_DefaultChangelog) },
                downloadUrl = release.downloadUrl,
                sizeBytes = release.effectiveSizeBytes?.takeIf { it > 0L },
                sha256 = release.sha256?.takeIf { it.isNotBlank() },
                isCritical = parsed.manifest.isCritical == true,
                abi = release.abi,
                source = "manifest",
            ),
            answered = true,
        )
    }

    /**
     * Parses and, when signed, verifies a manifest. Returns null for
     * malformed JSON or an invalid signature (fail-closed).
     */
    private fun parseManifest(rawBytes: ByteArray): ParsedManifest? = try {
        val json = String(rawBytes, Charsets.UTF_8)
        val manifest = manifestAdapter.fromJson(json) ?: return null
        val signature = manifest.signature
        val verified = signature != null && verifyManifestSignature(
            canonicalManifestBytes(rawBytes),
            signature,
        )
        if (signature != null && !verified) {
            Log.w(TAG, "manifest signature invalid — ignoring this manifest")
            null
        } else {
            ParsedManifest(manifest, json, verified)
        }
    } catch (e: Exception) {
        Log.w(TAG, "manifest parse failed: ${e.message}")
        null
    }

    private fun cacheManifest(parsed: ParsedManifest) {
        val editor = manifestPrefs.edit()
            .putString(KEY_CACHED_MANIFEST, parsed.rawJson)
            .putBoolean(KEY_CACHED_MANIFEST_VERIFIED, parsed.verified)
        if (parsed.verified) {
            editor.putInt(
                KEY_CACHED_MINIMUM_VERSION_CODE,
                parsed.manifest.minimumVersionCode ?: 0,
            )
        }
        editor.apply()
    }

    private fun cachedCandidate(preferredAbi: String): Candidate? {
        val rawJson = manifestPrefs.getString(KEY_CACHED_MANIFEST, null) ?: return null
        val manifest = try {
            manifestAdapter.fromJson(rawJson)
        } catch (e: Exception) {
            null
        } ?: return null
        if (!UpdateVersion.isNewer(getCurrentVersionName(), manifest.versionName)) return null
        val release = manifest.releases
            ?.let { UpdateHelper.getBestRelease(it, preferredAbi) }
            ?: return null
        return Candidate(
            version = UpdateVersion.normalize(manifest.versionName),
            versionCode = manifest.versionCode,
            changelog = manifest.changelog?.trim().orEmpty()
                .ifBlank { context.getString(R.string.st_Update_DefaultChangelog) },
            downloadUrl = release.downloadUrl,
            sizeBytes = release.effectiveSizeBytes?.takeIf { it > 0L },
            sha256 = release.sha256?.takeIf { it.isNotBlank() },
            isCritical = manifest.isCritical == true,
            abi = release.abi,
            source = "manifest",
        )
    }

    private fun pickError(errors: List<UpdateError>): UpdateError =
        errors.firstOrNull { it is UpdateError.Offline }
            ?: errors.firstOrNull { it is UpdateError.Timeout }
            ?: errors.firstOrNull { it is UpdateError.RateLimited }
            ?: errors.firstOrNull { it is UpdateError.Verification }
            ?: errors.firstOrNull()
            ?: UpdateError.Unknown()

    private suspend fun preferredAbi(): String = try {
        settingsRepository.preferredAbi.first()
    } catch (e: Exception) {
        "AUTO"
    }

    fun getCurrentVersionName(): String = try {
        packageInfo().versionName ?: "0.0.0"
    } catch (e: Exception) {
        "0.0.0"
    }

    fun getCurrentVersionCode(): Long = try {
        packageInfo().longVersionCode
    } catch (e: Exception) {
        0L
    }

    private fun packageInfo() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

    /** Kept for compatibility; delegates to the shared comparator. */
    fun isNewerVersion(current: String, latest: String): Boolean =
        UpdateVersion.isNewer(current, latest)

    // ── Anti-mod update-lock ────────────────────────────────────────────
    // Whisper nav gate (WhisperBuildGate) calls [checkWhisperBlocked]: while
    // the installed build is below the manifest floor — or a critical release
    // is newer than the install — Whisper screens show a blocking "Update
    // required" card instead of chat UI. Fail-open when no verified manifest
    // is reachable, so offline users are never bricked.

    /** Pure rule — unit-testable without Android. */
    fun isWhisperBlockedByManifest(installedCode: Long, manifest: UpdateManifest): Boolean {
        val floor = manifest.minimumVersionCode?.toLong() ?: 0L
        if (floor > 0L && installedCode < floor) return true
        if (manifest.isCritical == true && installedCode < manifest.versionCode.toLong()) return true
        return false
    }

    suspend fun checkWhisperBlocked(): WhisperBlockState {
        val installedCode = getCurrentVersionCode()
        try {
            val response = updateService.getUpdateManifestRaw(UpdateConstants.MANIFEST_URL)
            if (response.isSuccessful) {
                val bytes = response.body()?.bytes()
                val parsed = bytes?.let { parseManifest(it) }
                if (parsed != null && parsed.verified) {
                    cacheManifest(parsed)
                    if (isWhisperBlockedByManifest(installedCode, parsed.manifest)) {
                        return WhisperBlockState.Blocked(
                            latestVersionName = parsed.manifest.versionName,
                            latestVersionCode = parsed.manifest.versionCode,
                            isCritical = parsed.manifest.isCritical == true,
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "whisper gate check failed: ${e.message}")
        }

        // Offline fallback: only a cached signature-verified manifest counts.
        val cached = cachedVerifiedManifest()
        if (cached != null && isWhisperBlockedByManifest(installedCode, cached)) {
            return WhisperBlockState.Blocked(
                latestVersionName = cached.versionName,
                latestVersionCode = cached.versionCode,
                isCritical = cached.isCritical == true,
            )
        }
        return WhisperBlockState.NotBlocked
    }

    private fun cachedVerifiedManifest(): UpdateManifest? {
        if (!manifestPrefs.getBoolean(KEY_CACHED_MANIFEST_VERIFIED, false)) return null
        val rawJson = manifestPrefs.getString(KEY_CACHED_MANIFEST, null) ?: return null
        return try {
            manifestAdapter.fromJson(rawJson)
        } catch (e: Exception) {
            null
        }
    }

    /** Last signature-verified manifest floor; null when never cached. */
    fun cachedMinimumVersionCode(): Int? {
        val floor = manifestPrefs.getInt(KEY_CACHED_MINIMUM_VERSION_CODE, 0)
        return floor.takeIf { it > 0 }
    }

    companion object {
        private const val TAG = "UpdateRepository"
        private const val MANIFEST_CACHE_PREFS = "toolz_update_manifest"
        private const val KEY_CACHED_MANIFEST = "cached_manifest_json"
        private const val KEY_CACHED_MANIFEST_VERIFIED = "cached_manifest_verified"
        private const val KEY_CACHED_MINIMUM_VERSION_CODE = "minimum_version_code"

        /**
         * Official update-manifest Ed25519 public key — raw 32-byte key, base64.
         *
         * Keep the private half offline; sign every released
         * update_manifest.json with it (see [verifyManifestSignature]).
         */
        const val ED25519_PUBLIC_KEY_B64 = "pLIOx0vcMq4/JWQhrsNl97x8cTIYHvtYwP7NGnLkYBM="

        /** X.509 SubjectPublicKeyInfo header wrapping a raw Ed25519 key. */
        private val ED25519_X509_PREFIX = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        )

        /**
         * Verify the Ed25519 signature of an update manifest against the
         * embedded [ED25519_PUBLIC_KEY_B64]. Fails closed on any error.
         */
        fun verifyManifestSignature(
            manifestJsonBytesWithoutSignatureField: ByteArray,
            signatureB64: String,
        ): Boolean = verifyManifestSignatureWithKey(
            manifestJsonBytesWithoutSignatureField, signatureB64, ED25519_PUBLIC_KEY_B64
        )

        /** Same as [verifyManifestSignature] with an explicit key (tests). */
        internal fun verifyManifestSignatureWithKey(
            manifestJsonBytesWithoutSignatureField: ByteArray,
            signatureB64: String,
            publicKeyB64: String,
        ): Boolean = try {
            val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                X509EncodedKeySpec(
                    ED25519_X509_PREFIX + Base64.getDecoder().decode(publicKeyB64)
                )
            )
            Signature.getInstance("Ed25519").run {
                initVerify(publicKey)
                update(manifestJsonBytesWithoutSignatureField)
                verify(Base64.getDecoder().decode(signatureB64))
            }
        } catch (e: Exception) {
            false // fail-closed: bad key, bad signature, bad base64
        }

        /**
         * Canonical manifest bytes for signature verification: raw JSON with
         * `signature` and `_comment` removed, re-serialized compactly
         * (org.json preserves key order).
         */
        fun canonicalManifestBytes(manifestJsonBytes: ByteArray): ByteArray {
            val json = JSONObject(String(manifestJsonBytes, Charsets.UTF_8))
            json.remove("signature")
            json.remove("_comment")
            return json.toString().toByteArray(Charsets.UTF_8)
        }
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

sealed interface UpdateCheckResult {
    data class NewUpdate(val update: PendingUpdate) : UpdateCheckResult {
        val version: String get() = update.version
        val changelog: String get() = update.changelog
        val downloadUrl: String get() = update.downloadUrl
        val isCritical: Boolean get() = update.isCritical
    }

    data object UpToDate : UpdateCheckResult

    data class Error(val error: UpdateError) : UpdateCheckResult {
        val messageRes: Int get() = error.messageRes
    }
}
