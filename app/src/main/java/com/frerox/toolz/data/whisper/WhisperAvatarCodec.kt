/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import com.frerox.toolz.crypto.SessionCrypto
import java.security.MessageDigest

/**
 * V6-R7 AVATARS: deterministic encryption for profile avatars hosted on ImgBB.
 *
 * WHY NOT PAIRWISE ECDH (like chat images)? Avatars are viewed by an OPEN set —
 * friends, discover browsers, strangers. Per-recipient envelopes would be unbounded.
 *
 * INSTEAD the key is derived deterministically from the OWNER'S PUBLIC KEY:
 *
 *     key = HKDF-SHA256(ikm = SHA-256(ownerPubB64 ‖ ":whisper-avatar-v1"), 32B)
 *
 * The owner can seal using only their own public key, and every viewer already
 * receives that exact public key inside the same [com.frerox.toolz.data.whisper.WhisperProfile]
 * row they are rendering — so anyone legitimate can derive the key and open the
 * image, while ImgBB hosts nothing but an opaque PNG.
 *
 * THREAT MODEL (honest): this is obfuscation-grade for third-party hosts, not
 * anonymity from whoever legitimately holds the profile row. It preserves the
 * roadmap promise "third-party image storage sees ciphertext only" without any
 * key-distribution infrastructure. AEAD still fails closed on wrong keys.
 */
object WhisperAvatarCodec {

    private const val SALT = ":whisper-avatar-v1"
    private const val INFO = "whisper-avatar-key"

    /** Deterministic per-owner avatar key. Stable across processes and devices. */
    fun deriveKey(ownerPublicKeyBase64: String): ByteArray {
        val ikm = MessageDigest.getInstance("SHA-256")
            .digest((ownerPublicKeyBase64 + SALT).toByteArray(Charsets.UTF_8))
        return SessionCrypto.hkdfSha256(ikm, ByteArray(32), INFO.toByteArray(), 32)
    }

    /** AES-GCM seal with the owner pub bound as AAD (tamper-evident swap). */
    fun seal(jpegBytes: ByteArray, ownerPublicKeyBase64: String): ByteArray =
        SessionCrypto.aesGcmSeal(
            deriveKey(ownerPublicKeyBase64),
            jpegBytes,
            ownerPublicKeyBase64.toByteArray(Charsets.UTF_8),
        )

    /** Inverse of [seal]; null when the key/AAD does not match (fails closed). */
    fun open(wrappedBytes: ByteArray, ownerPublicKeyBase64: String): ByteArray? =
        SessionCrypto.aesGcmOpen(
            deriveKey(ownerPublicKeyBase64),
            wrappedBytes,
            ownerPublicKeyBase64.toByteArray(Charsets.UTF_8),
        )
}
