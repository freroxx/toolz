/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase-2 groups: pure membership + envelope layer (no Android, no network).
 *
 * Security invariants under test:
 *  - membership derives ONLY from verified events (bad sig / non-admin op fails closed)
 *  - last admin can neither be removed nor leave; group caps at 12
 *  - stale epochs and unordered logs are rejected, never partially applied
 *  - inner frames bind groupId + epoch inside the (1:1-sealed) plaintext —
 *    cross-group / cross-epoch frames open to null, never to a body
 */
class WhisperGroupsTest {

    private val gid = "group-1"
    private val admin = "user-admin"
    private val adminKey = "ADMIN_X509"

    /** Stub verifier: only the literal "ok" signature passes. */
    private val verify: (ByteArray, String, String) -> Boolean =
        { _, sig, _ -> sig == "ok" }
    private val keys: (String) -> String? = { if (it == admin) adminKey else null }

    private fun ev(
        seq: Long,
        epoch: Long,
        type: WhisperGroupEventType,
        actor: String,
        payload: String,
        sig: String = "ok",
    ) = WhisperGroupEvent(
        id = "ev-$seq", groupId = gid, seq = seq, epoch = epoch,
        type = type, actor = actor, payloadJson = payload, adminSig = sig,
    )

    private fun create() = ev(1, 0, WhisperGroupEventType.CREATE, admin, """{"name":"Crew"}""")

    @Test
    fun `create plus add builds membership`() {
        val m = applyEvents(gid, listOf(create(), ev(2, 1, WhisperGroupEventType.ADD, admin, "\"u2\"")), verify, keys)
        assertTrue(m.isAdmin(admin))
        assertTrue(m.isMember("u2"))
        assertEquals(1L, m.epoch)
        assertEquals("Crew", m.name)
    }

    @Test
    fun `unsigned create fails closed`() {
        try {
            applyEvents(gid, listOf(create().copy(adminSig = "forged")), verify, keys)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("signature"))
        }
    }

    @Test
    fun `non-admin add fails closed`() {
        val log = listOf(create(), ev(2, 1, WhisperGroupEventType.ADD, "u2", "\"u3\""))
        try {
            applyEvents(gid, log, verify, keys)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("admin"))
        }
    }

    @Test
    fun `last admin cannot be removed or leave`() {
        val remove = listOf(create(), ev(2, 1, WhisperGroupEventType.REMOVE, admin, "\"$admin\""))
        try {
            applyEvents(gid, remove, verify, keys)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("last admin"))
        }
        val leave = listOf(create(), ev(2, 1, WhisperGroupEventType.LEAVE, admin, "\"\""))
        try {
            applyEvents(gid, leave, verify, keys)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("last admin"))
        }
    }

    @Test
    fun `group caps at twelve members`() {
        val log = mutableListOf(create())
        for (i in 2..12) log += ev(i.toLong(), 1, WhisperGroupEventType.ADD, admin, "\"u$i\"")
        val m = applyEvents(gid, log, verify, keys)
        assertEquals(12, m.members.size)
        try {
            applyEvents(gid, log + ev(13, 1, WhisperGroupEventType.ADD, admin, "\"u13\""), verify, keys)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("full"))
        }
    }

    @Test
    fun `stale epoch and unordered logs rejected`() {
        val stale = listOf(create(), ev(2, 1, WhisperGroupEventType.ADD, admin, "\"u2\""), ev(3, 0, WhisperGroupEventType.ADD, admin, "\"u3\""))
        try {
            applyEvents(gid, stale, verify, keys)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("stale epoch"))
        }
        val unordered = listOf(create(), ev(3, 1, WhisperGroupEventType.ADD, admin, "\"u3\""), ev(2, 1, WhisperGroupEventType.ADD, admin, "\"u2\""))
        try {
            applyEvents(gid, unordered, verify, keys)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("seq-ordered"))
        }
    }

    @Test
    fun `event from another group rejected`() {
        val other = create().copy(groupId = "group-2")
        try {
            applyEvents(gid, listOf(other), verify, keys)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("another group"))
        }
    }

    @Test
    fun `frame envelope round-trips and binds group plus epoch`() {
        val frame = buildGroupFrameBody(gid, 3, 7, "hello")
        assertEquals("hello", openGroupFrameBody(gid, 3, frame))
        assertNull(openGroupFrameBody("group-2", 3, frame))
        assertNull(openGroupFrameBody(gid, 4, frame))
        assertNull(openGroupFrameBody(gid, 3, "not-json"))
        // Version tamper via re-encode (string replace is blind to omitted defaults).
        val tampered = groupJson.encodeToString(
            WhisperGroupFrameBody.serializer(),
            WhisperGroupFrameBody(v = 99, g = gid, e = 3, s = 7, body = "hello"),
        )
        assertNull(openGroupFrameBody(gid, 3, tampered))
    }

    @Test
    fun `row content round-trips`() {
        val row = WhisperGroupRowContent(v = WhisperGroupsConfig.ENVELOPE_VERSION, g = gid, e = 3, s = 7, senderId = admin, frames = mapOf("u2" to "F2"))
        val decoded = decodeGroupRow(encodeGroupRow(row))
        assertEquals(row, decoded)
        assertNull(decodeGroupRow("garbage"))
    }

    @Test
    fun `sign bytes are deterministic and field-sensitive`() {
        val a = groupEventSignBytes(gid, 2, 1, WhisperGroupEventType.ADD, admin, "\"u2\"")
        val b = groupEventSignBytes(gid, 2, 1, WhisperGroupEventType.ADD, admin, "\"u2\"")
        assertEquals(String(a), String(b))
        val c = groupEventSignBytes(gid, 3, 1, WhisperGroupEventType.ADD, admin, "\"u2\"")
        assertTrue(!a.contentEquals(c))
    }

    @Test
    fun `flag defaults off`() {
        assertTrue(!groupsEnabled())
    }
}
