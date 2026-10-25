/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.util.Log
import okhttp3.CertificatePinner

/**
 * Light certificate pinning for Whisper backends.
 *
 * DEFAULT IS LOG-ONLY (no enforcement): [PINS] ships empty so a server-side
 * cert rotation can never brick the app. Enforcement is a conscious operator
 * step — fill [PINS] with live `sha256/...` pins, flip [ENFORCE] to true,
 * release, and keep a 2-pin overlap across rotations.
 *
 * Hosts covered once enforced: Supabase project host, update-manifest host
 * (toolz-app / github pages host serving update_manifest.json), ImgBB.
 *
 * Obtain a pin for a host WITHOUT trusting the current chain blindly:
 *   echo | openssl s_client -connect HOST:443 -servername HOST 2>/dev/null \
 *     | openssl x509 -pubkey -noout \
 *     | openssl pkey -pubin -outform der \
 *     | openssl dgst -sha256 -binary | openssl enc -base64
 * Cross-check the output from two networks before shipping it.
 */
object WhisperPinConfig {

    private const val TAG = "WhisperPin"

    // ─────────────────────────────────────────────────────────────────────
    // Anti-mod build identity — consumed by PlayIntegrityAttestor (client
    // side) and mirrored server-side as Supabase secrets (OFFICIAL_PACKAGE,
    // OFFICIAL_CERT_SHA256, MIN_VERSION_CODE) enforced by
    // supabase/functions/_shared/attest.ts.
    // ─────────────────────────────────────────────────────────────────────

    /** Must equal app/build.gradle.kts → defaultConfig.applicationId. */
    const val OFFICIAL_PACKAGE = "com.frerox.toolz"

    /**
     * Release signing-certificate SHA-256, upper-case hex, no colons.
     *
     * Public value (derived from the signed APK, not a secret):
     *   apksigner verify --print-certs app-release.apk | grep -i 'SHA-256'
     *
     * The release keystore itself stays off-source; only this fingerprint
     * ships. Rotation lineage (debug -> release, v3.1, minSdk 33) keeps
     * updates installable: API 33+ reports the release cert, API 31-32
     * still sees the debug ancestor and must reinstall once to migrate.
     * The server-side OFFICIAL_CERT_SHA256 secret makes the final call.
     */
    const val OFFICIAL_CERT_SHA256 = "58A16BCD6E9CEF75706A957B19A15BED19BD47F0B7FDB602364D9EBFC5C369F9"

    /**
     * Minimum versionCode accepted by the anti-mod gate. This is the static
     * floor of the LOCAL Whisper runtime gate (gateWhisperAccess); the gate
     * applies `max(cached manifest floor, this value)`. Keep in sync with the
     * edge secret MIN_VERSION_CODE.
     *
     * `update_manifest.json` may lag (`minimumVersionCode: 17`) because the
     * file is Ed25519-signed; do not edit it without re-signing. The local
     * max() still rejects older builds as soon as this constant is raised.
     *
     * 18 = v1.1.7. v1.1.6 (17) is intentionally locked out of Whisper: it
     * cannot decrypt the current protocol and has no app-scoped notification
     * monitor, so letting it in produces silent message loss.
     */
    const val MIN_VERSION_CODE = 18L

    /** Flip to true ONLY together with a fully populated [PINS]. */
    const val ENFORCE = false

    /**
     * hostname -> list of `sha256/BASE64==` pins. Empty = log-only mode.
     * Example (REPLACE with real pins, keep ≥2 per host across rotations):
     *   "xyzcompany.supabase.co" to listOf("sha256/AAA...", "sha256/BBB..."),
     */
    val PINS: Map<String, List<String>> = emptyMap()

    /** Null in log-only mode (no enforcement). Non-null only when enforced. */
    fun certificatePinnerOrNull(): CertificatePinner? {
        if (!ENFORCE || PINS.isEmpty()) return null
        return CertificatePinner.Builder().apply {
            PINS.forEach { (host, pins) -> pins.forEach { add(host, it) } }
        }.build()
    }

    /** Log-only handshake note — safe to call on every edge request. */
    fun logHandshake(host: String) {
        if (com.frerox.toolz.BuildConfig.DEBUG) {
            val mode = if (certificatePinnerOrNull() == null) "LOG-ONLY" else "ENFORCED"
            Log.d(TAG, "edge TLS to $host (pinning $mode)")
        }
    }
}
