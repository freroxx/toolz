/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The verification QR is generated from [WhisperCrypto.computeFingerprint]'s canonical
 * 32-hex-char (8 x 4) form. These cases pin the encoder/decoder contract: a code produced
 * by this app must always parse back to the exact dashed fingerprint the scanner compares
 * against, otherwise every scan fails with the "invalid code" toast.
 */
class WhisperVerifyPayloadTest {

    private val canonical = "A1B2-C3D4-E5F6-0718-293A-4B5C-6D7E-8F90"

    @Test
    fun payload_roundTripsCanonicalFingerprint() {
        val payload = whisperVerifyPayload("alice", canonical)
        assertEquals("whisper-verify:alice:A1B2C3D4E5F60718293A4B5C6D7E8F90", payload)
        assertEquals("alice" to canonical, parseWhisperVerifyPayload(payload))
    }

    @Test
    fun parser_acceptsLowercaseAndDashedFingerprint() {
        val parsed = parseWhisperVerifyPayload(
            "whisper-verify:bob:a1b2-c3d4-e5f6-0718-293a-4b5c-6d7e-8f90",
        )
        assertEquals("bob" to canonical, parsed)
    }

    @Test
    fun parser_normalisesLegacy64HexPayload() {
        val legacy = canonical.replace("-", "") + "00112233445566778899AABBCCDDEEFF"
        assertEquals("carol" to canonical, parseWhisperVerifyPayload("whisper-verify:carol:$legacy"))
    }

    @Test
    fun parser_rejectsWrongPrefix() {
        assertNull(parseWhisperVerifyPayload("hello:alice:A1B2C3D4E5F60718293A4B5C6D7E8F90"))
    }

    @Test
    fun parser_rejectsBlankUsername() {
        assertNull(parseWhisperVerifyPayload("whisper-verify::A1B2C3D4E5F60718293A4B5C6D7E8F90"))
    }

    @Test
    fun parser_rejectsWrongLength() {
        assertNull(parseWhisperVerifyPayload("whisper-verify:alice:A1B2C3D4"))
    }

    @Test
    fun parser_rejectsNonHexCharacters() {
        assertNull(parseWhisperVerifyPayload("whisper-verify:alice:A1B2C3D4E5F60718293A4B5C6D7E8F9Z"))
    }

    @Test
    fun parser_rejectsExtraSeparator() {
        assertNull(parseWhisperVerifyPayload("whisper-verify:alice:A1B2:C3D4E5F60718293A4B5C6D7E8F90"))
    }
}
