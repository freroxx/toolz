/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V6-R7: in-band sender key ("ik") — Signal-style. The receiver's decryption trial
 * set gains the sender's current public key carried INSIDE the envelope, severing
 * delivery from profiles.public_key freshness/pollution.
 *
 * Security invariants under test:
 *  - ik round-trips verbatim (it is trial-only material; adoption is proof-gated)
 *  - legacy envelopes without ik parse identically to before (backward compat)
 *  - hostile/truncated payloads never throw
 */
class WhisperEnvelopeInBandTest {

    @Test
    fun `ik round-trips and entries are preserved`() {
        val entries = listOf(
            Triple("aaaa1111", "ivA==", "ctA=="),
            Triple("bbbb2222", "ivB==", "ctB=="),
        )
        val json = WhisperEnvelope.encode(entries, senderPublicKeyBase64 = "U0VOREVSX1BVQg==")!!
        assertTrue(WhisperEnvelope.isEnvelope(json))
        assertEquals("U0VOREVSX1BVQg==", WhisperEnvelope.inBandSenderKey(json))
        assertArrayEquals(entries.toTypedArray(), WhisperEnvelope.decode(json)!!.toTypedArray())
    }

    @Test
    fun `legacy envelopes without ik still decode with null inband key`() {
        val legacyJson = """{"v":2,"k":[{"kid":"kid12345","iv":"aXY=","ct":"Y3Q="}]}"""
        val entries = WhisperEnvelope.decode(legacyJson)!!
        assertEquals(1, entries.size)
        assertNull(WhisperEnvelope.inBandSenderKey(legacyJson))
    }

    @Test
    fun `encode without sender key omits ik field entirely`() {
        val json = WhisperEnvelope.encode(listOf(Triple("k", "i", "c")))!!
        assertFalse(json.contains("\"ik\""))
        assertNull(WhisperEnvelope.inBandSenderKey(json))
    }

    @Test
    fun `hostile payloads degrade to null never throw`() {
        assertNull(WhisperEnvelope.inBandSenderKey("{\"v\":2,\"k\":[],\"ik\":12345}"))
        assertNull(WhisperEnvelope.inBandSenderKey("{\"v\":2,\"k\":[{\"kid\":\"x\"}],\"ik\":\"ok\"}"))
        assertNull(WhisperEnvelope.inBandSenderKey(""))
    }
}
