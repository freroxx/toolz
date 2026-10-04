/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LOCAL-ONLY anti-mod attestation headers for every `whisper-*` edge call.
 *
 * FOSS-safe: NO network, NO Google API, NO Play Integrity dependency, NO
 * reflection. The signing certificate is read from the platform
 * PackageManager ([PackageManager.GET_SIGNING_CERTIFICATES] on API 28+,
 * [PackageManager.GET_SIGNATURES] as the legacy fallback) and hashed with
 * SHA-256. The result is compared against
 * [WhisperPinConfig.OFFICIAL_CERT_SHA256] with case/colon/whitespace
 * normalization, so `AA:BB`, `aabb` and `AABB` all compare equal.
 *
 * Headers sent (via [EdgeFunctionClient]) on EVERY whisper-* request:
 * - `X-App-Package`      == applicationId (`com.frerox.toolz`)
 * - `X-App-Cert-Sha256`  == SHA-256 of the signing cert (upper-case hex, no colons)
 * - `X-App-VersionCode`  == current versionCode
 *
 * The server is the sole judge. Headers are always sent, even when the local
 * comparison fails — on DEBUG builds a self-signed (debug-keystore) cert is
 * allowed and the headers still go out; on release builds a mismatch is
 * logged and the server decides (strict baseline: package + cert + version,
 * enforced in `supabase/functions/_shared/attest.ts`).
 *
 * The computed result is cached for 1h: the signing cert cannot change
 * while the process lives, so the hot path is a volatile read.
 */
@Singleton
class PlayIntegrityAttestor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val HEADER_PACKAGE = "X-App-Package"
        const val HEADER_CERT = "X-App-Cert-Sha256"
        const val HEADER_VERSION = "X-App-VersionCode"

        /** HTTP status the edge gate uses for unofficial builds. */
        const val UNOFFICIAL_BUILD_HTTP_CODE = 428

        private const val TAG = "WhisperAttest"
        private const val RESULT_CACHE_TTL_MS = 3_600_000L
        private val WHITESPACE = Regex("\\s+")
    }

    /** Memoized attestation outcome (the three headers + local verdict). */
    private data class AttestResult(
        val headers: Map<String, String>,
        val locallyOfficial: Boolean,
    )

    @Volatile private var cachedResult: AttestResult? = null
    @Volatile private var cachedResultAt: Long = 0L

    /**
     * Attestation headers for a whisper-* edge call. Never throws: the
     * affected header is sent empty on lookup failure and the server
     * decides. Always contains exactly the three identity headers.
     */
    suspend fun attestHeaders(): Map<String, String> =
        withContext(Dispatchers.IO) { freshResult().headers }

    /**
     * Local verdict: does this build's signing cert match
     * [WhisperPinConfig.OFFICIAL_CERT_SHA256] (normalized)? Debug builds
     * pass (self-signed allowed). Informational only — headers are sent
     * regardless; the server makes the final call.
     */
    suspend fun isLocallyOfficial(): Boolean =
        withContext(Dispatchers.IO) { freshResult().locallyOfficial }

    /** Cache lookup with 1h TTL; recomputes on miss or expiry. */
    private fun freshResult(): AttestResult {
        val now = System.currentTimeMillis()
        cachedResult?.let { if (now - cachedResultAt < RESULT_CACHE_TTL_MS) return it }
        val result = computeResult()
        cachedResult = result
        cachedResultAt = now
        return result
    }

    private fun computeResult(): AttestResult {
        val cert = signingCertSha256()
        val locallyOfficial = certMatchesOfficial(cert)
        if (com.frerox.toolz.BuildConfig.DEBUG) {
            // Debug builds ship the debug-keystore (self-signed) cert:
            // allowed locally, headers still sent — server decides.
            Log.d(
                TAG,
                "debug build: certSha256=${cert.take(16)}… locallyOfficial=$locallyOfficial (headers still sent)",
            )
        } else if (!locallyOfficial) {
            Log.w(
                TAG,
                "signing cert does not match OFFICIAL_CERT_SHA256 — headers sent, server decides",
            )
        }
        return AttestResult(
            headers = mapOf(
                HEADER_PACKAGE to context.packageName,
                HEADER_CERT to cert,
                HEADER_VERSION to versionCode().toString(),
            ),
            locallyOfficial = locallyOfficial || com.frerox.toolz.BuildConfig.DEBUG,
        )
    }

    /**
     * Normalized comparison (case-, colon- and whitespace-insensitive) of the
     * on-device signing cert against [WhisperPinConfig.OFFICIAL_CERT_SHA256].
     * A placeholder or blank expected value matches nothing — fail closed
     * locally; headers still go out so the server decides.
     */
    private fun certMatchesOfficial(certSha256: String): Boolean {
        val expected = normalizeCert(WhisperPinConfig.OFFICIAL_CERT_SHA256)
        val actual = normalizeCert(certSha256)
        return expected.isNotBlank() && actual.isNotBlank() && expected == actual
    }

    private fun normalizeCert(raw: String): String =
        raw.replace(":", "").replace(WHITESPACE, "").uppercase()

    fun versionCode(): Long {
        return try {
            val pm = context.packageManager
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
            else @Suppress("DEPRECATION") pi.versionCode.toLong()
        } catch (e: Exception) {
            if (com.frerox.toolz.BuildConfig.DEBUG) Log.d(TAG, "versionCode lookup failed", e)
            0L
        }
    }

    /** SHA-256 of the CURRENT signing cert, upper-case hex without colons.
     * Rotation-safe: apkContentsSigners (this platform's active signer) comes
     * first; signingCertificateHistory order is oldest-first on some
     * releases, so history-first would pin a rotated-out ancestor. */
    fun signingCertSha256(): String {
        return try {
            val pm = context.packageManager
            val sigs: Array<out android.content.pm.Signature>? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val pi = pm.getPackageInfo(
                        context.packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES,
                    )
                    val signing = pi.signingInfo
                    if (signing != null) {
                        (signing.apkContentsSigners.toList() + signing.signingCertificateHistory.toList())
                            .distinctBy { it.toByteArray().contentHashCode() }
                            .toTypedArray()
                    } else null
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(
                        context.packageName,
                        PackageManager.GET_SIGNATURES,
                    ).signatures
                }
            val bytes = sigs?.firstOrNull()?.toByteArray() ?: return ""
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            if (com.frerox.toolz.BuildConfig.DEBUG) Log.d(TAG, "cert fingerprint failed", e)
            ""
        }
    }
}
