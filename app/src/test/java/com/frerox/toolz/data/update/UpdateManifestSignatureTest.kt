/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.update

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the offline update-manifest signature gate
 * ([UpdateRepository.verifyManifestSignature]) — the fail-closed
 * check that stops mods/MITM from forging `minimumVersionCode`
 * bumps or swapping release URLs. Pure JVM, no Android needed.
 */
class UpdateManifestSignatureTest {

    private val canonical =
        """{"versionCode":17,"versionName":"1.1.6","minimumVersionCode":17}"""
            .toByteArray(Charsets.UTF_8)

    /** Returns (raw-32-byte public key as base64, private key). */
    private fun generateKeyPair(): Pair<String, java.security.PrivateKey> {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        // Ed25519 public keys encode as 44-byte X.509 SubjectPublicKeyInfo;
        // the app embeds only the raw 32-byte key (last 32 bytes).
        val rawPublic = keyPair.public.encoded.copyOfRange(12, 44)
        return Base64.getEncoder().encodeToString(rawPublic) to keyPair.private
    }

    private fun sign(privateKey: java.security.PrivateKey, data: ByteArray): String =
        Signature.getInstance("Ed25519").run {
            initSign(privateKey)
            update(data)
            Base64.getEncoder().encodeToString(sign())
        }

    @Test
    fun validSignatureVerifies() {
        val (publicKeyB64, privateKey) = generateKeyPair()
        val signature = sign(privateKey, canonical)
        assertTrue(
            UpdateRepository.verifyManifestSignatureWithKey(canonical, signature, publicKeyB64)
        )
    }

    @Test
    fun tamperedManifestIsRejected() {
        val (publicKeyB64, privateKey) = generateKeyPair()
        val signature = sign(privateKey, canonical)
        val tampered = canonical.copyOf().also { bytes ->
            bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0xFF).toByte()
        }
        assertFalse(
            UpdateRepository.verifyManifestSignatureWithKey(tampered, signature, publicKeyB64)
        )
    }

    @Test
    fun signatureFromDifferentKeyIsRejected() {
        val (_, privateKey) = generateKeyPair()
        val (otherPublicKeyB64, _) = generateKeyPair()
        val signature = sign(privateKey, canonical)
        assertFalse(
            UpdateRepository.verifyManifestSignatureWithKey(canonical, signature, otherPublicKeyB64)
        )
    }

    @Test
    fun malformedSignatureFailsClosed() {
        val (publicKeyB64, _) = generateKeyPair()
        assertFalse(
            UpdateRepository.verifyManifestSignatureWithKey(canonical, "not-base64!!!", publicKeyB64)
        )
        assertFalse(
            UpdateRepository.verifyManifestSignatureWithKey(canonical, "", publicKeyB64)
        )
    }

    @Test
    fun unprovisionedPlaceholderKeyFailsClosed() {
        // Until a real keypair replaces REPLACE_ME_ED25519_PUBKEY,
        // nothing may verify — the gate must fail closed, not open.
        val (_, privateKey) = generateKeyPair()
        val signature = sign(privateKey, canonical)
        assertFalse(UpdateRepository.verifyManifestSignature(canonical, signature))
    }
}
