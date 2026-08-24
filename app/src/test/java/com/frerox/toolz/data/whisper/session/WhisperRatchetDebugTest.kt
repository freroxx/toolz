/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper.session

import com.frerox.toolz.crypto.SessionCrypto
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

/** TEMPORARY diagnostics: finds the smallest failing seed and dumps full traces. */
class WhisperRatchetDebugTest {

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun short(b: ByteArray) = b64(b).take(8)

    @Test
    fun scanSeeds_forWrongPlaintextOnly() {
        var badSeeds = 0
        for (seedByte in 1..80) {
            val result = runChaos(seedByte)
            if (result.failed) {
                badSeeds++
                println("=== WRONG-PLAINTEXT SEED $seedByte ===")
                result.trace.takeLast(20).forEach { println(it) }
            }
        }
        org.junit.Assert.assertEquals("$badSeeds seeds produced wrong plaintexts", 0, badSeeds)
    }

    private data class Wire(
        val from: String, val header: WhisperRatchet.Header,
        val packed: ByteArray, val plaintext: ByteArray,
    )

    private class Outcome {
        var failed = false
        var opsUntilFailure = 0
        val trace = mutableListOf<String>()
        var aliceSnap = ""
        var bobSnap = ""
        var failingFrom = ""
        var failingDh = ""
        var failingPn = -1
        var failingN = -1
    }

    private fun runChaos(seedByte: Int): Outcome {
        val seedBytes = byteArrayOf(seedByte.toByte())
        val out = Outcome()
        val rnd = SecureRandom(seedBytes)
        val sk = SessionCrypto.hkdfSha256("chaos-seed".toByteArray(), ByteArray(32), "t".toByteArray(), 32)
        val spkPriv = SessionCrypto.generatePrivateKey()
        val spkPubB64 = Base64.getEncoder().encodeToString(SessionCrypto.publicFromPrivate(spkPriv))
        val alice = WhisperRatchet.initiator(sk, spkPubB64)
        val bob = WhisperRatchet.responder(sk, spkPriv, spkPubB64)

        val inFlight = mutableListOf<Wire>()
        var bobCanSend = false
        fun ad(id: Int) = id.toString().toByteArray()
        var id = 0

        var wrong = 0
        var lost = 0
        fun applyWire(wire: Wire): Boolean {
            return try {
                val plain = if (wire.from == "A") {
                    bob.decrypt(wire.header, wire.packed, ad(id)).also { bobCanSend = true }
                } else {
                    alice.decrypt(wire.header, wire.packed, ad(id))
                }
                if (!plain.contentEquals(wire.plaintext)) error("WRONG PLAINTEXT seed=$seedByte")
                true
            } catch (e: WhisperRatchetLostMessage) {
                lost++
                false
            }
        }

        try {
            var sentCount = 0
            repeat(120) {
                listOf("A" to alice, "B" to bob).forEach { (who, sender) ->
                    if (who == "B" && !bobCanSend) return@forEach
                    repeat(rnd.nextInt(3)) {
                        val payload = "m-$id-$who".toByteArray()
                        val sealed = sender.encrypt(payload, ad(id))
                        val wire = Wire(who, sealed.header, sealed.ciphertextPacked, payload).also { }
                        out.trace.add("SEND $who #${id} dh=${short(sealed.header.dhPub)} pn=${sealed.header.pn} n=${sealed.header.n}")
                        when {
                            rnd.nextInt(25) == 0 -> out.trace.add("  DROP")
                            rnd.nextInt(6) == 0 -> inFlight.add(wire)
                            else -> { if (applyWire(wire)) out.trace.add("  OK recv") }
                        }
                        id++; sentCount++
                    }
                }
                if (lost > 0 || wrong > 0) {
                    println("seed=$seedByte partial: sent=$sentCount lost=$lost wrong=$wrong")
                }
                lost = 0; wrong = 0
                if (rnd.nextInt(3) == 0 && inFlight.isNotEmpty()) {
                    repeat(minOf(inFlight.size, rnd.nextInt(14))) {
                        val w = inFlight.removeAt(rnd.nextInt(inFlight.size))
                        out.trace.add("FLUSH ${w.from}")
                        if (applyWire(w)) out.trace.add("  OK recv(late)")
                    }
                }
                out.opsUntilFailure++
            }
        } catch (e: Exception) {
            if ((e.message ?: "").contains("WRONG PLAINTEXT")) out.failed = true
            out.aliceSnap = alice.snapshot().toString().take(400)
            out.bobSnap = bob.snapshot().toString().take(400)
        }
        return out
    }
}
