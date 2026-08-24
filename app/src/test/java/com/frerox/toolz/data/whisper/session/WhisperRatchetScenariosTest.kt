/*
 * Copyright (C) 2026 Toolz Contributors
 */
package com.frerox.toolz.data.whisper.session

import com.frerox.toolz.crypto.SessionCrypto
import org.junit.Test
import java.util.Base64

/** Deterministic minimal scenarios — prints state at every step. */
class WhisperRatchetScenariosTest {

    private fun b(b: ByteArray) = Base64.getEncoder().encodeToString(b).take(10)

    @Test
    fun scenario_outOfOrderThenReply() {
        val sk = SessionCrypto.hkdfSha256("s".toByteArray(), ByteArray(32), "t".toByteArray(), 32)
        val spkPriv = SessionCrypto.generatePrivateKey()
        val spkPubB64 = Base64.getEncoder().encodeToString(SessionCrypto.publicFromPrivate(spkPriv))

        val alice = WhisperRatchet.initiator(sk, spkPubB64)
        val bob = WhisperRatchet.responder(sk, spkPriv, spkPubB64)

        fun seal(r: WhisperRatchet, msg: String) = r.encrypt(msg.toByteArray()).let { Triple(it.header, it.ciphertextPacked, msg) }
        fun open(r: WhisperRatchet, h: WhisperRatchet.Header, ct: ByteArray): String =
            try { String(r.decrypt(h, ct)) } catch (e: Exception) { "LOST(${e.message?.take(40)})" }

        // A sends m0, m1, m2 on chain dhA1
        val messages = listOf("m0", "m1", "m2").map { seal(alice, it) }
        messages.forEachIndexed { i, (h, ct, msg) ->
            println("A->B m$i dh=${b(h.dhPub)} pn=${h.pn} n=${h.n} -> ${open(bob, h, ct)}")
        }
        // B replies b0 (triggers Bob's lazy sending ratchet)
        val (bh, bct, bmsg) = seal(bob, "b0")
        println("B->A b0 dh=${b(bh.dhPub)} pn=${bh.pn} n=${bh.n}")
        println("   A opens b0 -> ${open(alice, bh, bct)}")

        // A sends m3 on its NEW chain
        val (h3, ct3, m3) = seal(alice, "m3")
        println("A->B m3 dh=${b(h3.dhPub)} pn=${h3.pn} n=${h3.n}")
        println("   B opens m3 -> ${open(bob, h3, ct3)}")

        // Delayed delivery of nothing pending; now B replies again
        val (bh2, bct2, b1) = seal(bob, "b1")
        println("B->A b1 dh=${b(bh2.dhPub)} pn=${bh2.pn} n=${bh2.n}")
        println("   A opens b1 -> ${open(alice, bh2, bct2)}")

        // Crossed: A sent m4 BEFORE receiving b1? simulate: A sends m4 using current state
        val (h4, ct4, m4) = seal(alice, "m4")
        println("A->B m4 dh=${b(h4.dhPub)} pn=${h4.pn} n=${h4.n}")
        println("   B opens m4 -> ${open(bob, h4, ct4)}")
    }
}
