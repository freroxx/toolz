/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.crypto

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
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
 * X25519 implementation notes:
 *  - Montgomery ladder over Curve25519 per RFC 7748, implemented with
 *    java.math.BigInteger. Correctness is pinned by the RFC 7748 §5.2 and
 *    §6.1 test vectors in `SessionCryptoVectorTest`.
 *  - PERFORMANCE (known limitation, documented in the roadmap): BigInteger is not
 *    constant-time and slower than table-based implementations. Handshake-time use
 *    (Phase 2) is unaffected; if Phase 3 profiling shows per-message cost matters,
 *    swap the internals for Tink's `subtle.X25519` behind this exact interface —
 *    callers will not change.
 */
object SessionCrypto {

    const val PRIVATE_KEY_SIZE = 32
    const val PUBLIC_KEY_SIZE = 32
    const val SHARED_SECRET_SIZE = 32
    private const val AES_KEY_LEN_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val IV_LEN = 12

    private val P = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19))
    private val A24 = BigInteger.valueOf(121665)
    private val BASE_POINT: ByteArray = ByteArray(32).also { it[0] = 9 }

    // ------------------------------------------------------------------ X25519

    /** Generates a cryptographically random X25519 private scalar (clamped on use). */
    fun generatePrivateKey(): ByteArray =
        ByteArray(PRIVATE_KEY_SIZE).also { SecureRandom().nextBytes(it) }

    /** RFC 7748 §6.1: public = scalarMult(clamped private, base point 9). */
    fun publicFromPrivate(privateKey: ByteArray): ByteArray =
        scalarMult(privateKey, BASE_POINT)

    /**
     * RFC 7748 §5: X25519(k, u). Returns null when the peer point has low order
     * (all-zero shared secret) — the RFC-mandated check; callers MUST treat null
     * as an invalid peer key, never as a valid all-zero secret.
     */
    fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray? {
        val k = scalarMult(privateKey, peerPublicKey)
        if (k.all { it == 0.toByte() }) return null
        return k
    }

    private fun scalarMult(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        // RFC 7748 §5: "implementations of X25519 MUST mask the most significant bit
        // in the final byte" of u — non-canonical inputs (MSB set) are legal and must
        // be accepted (caught live by RFC vector 2).
        val u = publicKey.copyOf()
        u[31] = (u[31].toInt() and 0x7f).toByte()
        var x1 = decodeLittleEndian(u).mod(P)
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = false
        // RFC 7748 §5 clamping applied to the scalar before the Montgomery ladder.
        val clampedBytes = privateKey.copyOf()
        clampedBytes[0] = (clampedBytes[0].toInt() and 248).toByte()
        clampedBytes[31] = ((clampedBytes[31].toInt() and 127) or 64).toByte()
        val k = decodeLittleEndian(clampedBytes)

        for (t in 254 downTo 0) {
            val kt = k.testBit(t)
            if (kt != swap) {
                var tmp = x2; x2 = x3; x3 = tmp
                tmp = z2; z2 = z3; z3 = tmp
                swap = kt
            }
            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)
            val t0 = da.add(cb).mod(P)
            val t1 = da.subtract(cb).mod(P)
            x3 = t0.multiply(t0).mod(P)
            z3 = x1.multiply(t1.multiply(t1)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e)).mod(P)).mod(P)
        }
        if (swap) {
            var tmp = x2; x2 = x3; x3 = tmp
            tmp = z2; z2 = z3; z3 = tmp
        }
        val result = encodeLittleEndian(x2.multiply(z2.modPow(P.subtract(BigInteger.TWO), P)).mod(P))
        // scrub
        x1 = BigInteger.ZERO; x2 = BigInteger.ZERO; x3 = BigInteger.ZERO
        z2 = BigInteger.ZERO; z3 = BigInteger.ZERO
        return result
    }

    private fun decodeLittleEndian(bytes: ByteArray): BigInteger =
        BigInteger(1, bytes.reversedArray())

    private fun encodeLittleEndian(v: BigInteger): ByteArray {
        val out = ByteArray(32)
        val raw = v.toByteArray()
        // BigInteger is big-endian two's complement; take low 32 bytes little-endian.
        var i = raw.size - 1
        var o = 0
        while (i >= 0 && o < 32) {
            out[o++] = raw[i--]
        }
        return out
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
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
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
