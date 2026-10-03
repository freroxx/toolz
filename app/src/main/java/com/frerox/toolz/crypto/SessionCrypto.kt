/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.crypto

import com.google.crypto.tink.subtle.X25519 as TinkX25519
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * PHASE 1 (docs/WHISPER_ROADMAP.md §1.1): pure-software session cryptography
 * primitives for the upcoming prekey/ratchet protocol (Phases 2–3).
 *
 * Deliberately separate from [com.frerox.toolz.data.whisper.WhisperCrypto]:
 * - `IdentityVault` (that class) = hardware-bound long-term identity. Unchanged.
 * - `SessionCrypto` (this object) = stateless primitives used by ephemeral session
 *   keys, which never live in AndroidKeyStore (ephemeral keys must be cheap,
 *   numerous, and disposable).
 *
 * X25519 implementation notes (Phase 1B, constant-time):
 *  - All Diffie-Hellman ops delegate to Tink's `subtle.X25519`
 *    (`com.google.crypto.tink:tink-android`), a constant-time curve25519-donna
 *    port (data-invariant cswap, long-limb field ops — no BigInteger branches on
 *    secret material). The old java.math.BigInteger Montgomery ladder is removed.
 *  - Correctness is pinned by the RFC 7748 §5.2 and §6.1 test vectors in
 *    `SessionCryptoVectorTest` (including the non-canonical MSB-set input, which
 *    Tink accepts by masking the high bit per RFC 7748 §5).
 *  - `sharedSecret` returns null for banned/low-order peer points: Tink throws
 *    InvalidKeyException for those (plus an explicit all-zero guard below).
 *    Callers MUST treat null as an invalid peer key, never as a valid secret.
 */
object SessionCrypto {

    const val PRIVATE_KEY_SIZE = 32
    const val PUBLIC_KEY_SIZE = 32
    const val SHARED_SECRET_SIZE = 32
    private const val AES_KEY_LEN_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12

    // ------------------------------------------------------------------ X25519

    /** Generates a cryptographically random X25519 private scalar (clamped on use by Tink). */
    fun generatePrivateKey(): ByteArray =
        TinkX25519.generatePrivateKey()

    /** RFC 7748 §6.1: public = scalarMult(clamped private, base point 9) via Tink. */
    fun publicFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == PRIVATE_KEY_SIZE) { "X25519 private key must be 32 bytes" }
        try {
            return TinkX25519.publicFromPrivate(privateKey)
        } catch (e: java.security.InvalidKeyException) {
            throw IllegalArgumentException("Invalid X25519 private key", e)
        }
    }

    /**
     * RFC 7748 §5: X25519(k, u). Returns null when the peer point has low order
     * (banned point per Tink's Curve25519 list, or all-zero shared secret) — the
     * RFC-mandated check; callers MUST treat null as an invalid peer key, never
     * as a valid all-zero secret.
     */
    fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray? {
        if (privateKey.size != PRIVATE_KEY_SIZE || peerPublicKey.size != PUBLIC_KEY_SIZE) return null
        return try {
            val s = TinkX25519.computeSharedSecret(privateKey, peerPublicKey)
            if (s.all { it == 0.toByte() }) null else s
        } catch (_: java.security.InvalidKeyException) {
            // Banned/low-order peer point (includes all-zero, 1, and the small-order
            // catalogue in Tink's Curve25519.BANNED_PUBLIC_KEYS). Fail closed.
            null
        } catch (_: IllegalStateException) {
            // Tink's fault-attack collinearity guard tripped — treat as invalid.
            null
        }
    }

    // ------------------------------------------------------------- KDF & AEAD

    /** RFC 5869 HKDF-SHA256. Salt may be empty (zero-filled per RFC). */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val prk = hmacSha256(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val n = (outLen + 31) / 32
        require(n <= 255) { "HKDF output too long" }
        val okm = ByteArray(outLen)
        var prev = ByteArray(0)
        var offset = 0
        for (i in 1..n) {
            val input = prev + info + byteArrayOf(i.toByte())
            prev = hmacSha256(prk, input)
            val take = minOf(32, outLen - offset)
            System.arraycopy(prev, 0, okm, offset, take)
            offset += take
        }
        return okm
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    /** AES-256-GCM encrypt; returns iv‖ciphertext‖tag packed (iv first, 12 bytes). */
    fun aesGcmSeal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
        val iv = ByteArray(IV_LEN).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    /** Inverse of [aesGcmSeal]; returns null on authentication failure. */
    fun aesGcmOpen(key: ByteArray, packed: ByteArray, aad: ByteArray): ByteArray? {
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
        if (packed.size <= IV_LEN) return null
        val iv = packed.copyOfRange(0, IV_LEN)
        val ct = packed.copyOfRange(IV_LEN, packed.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            if (aad.isNotEmpty()) cipher.updateAAD(aad)
            cipher.doFinal(ct)
        } catch (_: Exception) {
            null
        }
    }
}
