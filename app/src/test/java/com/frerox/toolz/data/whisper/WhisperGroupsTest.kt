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
            // u2 is not a member at all: rejected before the admin/open-invite check.
            assertTrue((e.message ?: "").contains("signed by inviter"))
        }
    }

    @Test
    fun `open invites let members add, strangers still blocked`() {
        val openCreate = create().copy(payloadJson = """{"name":"Crew","invite":"all"}""")
        val memberAdd = listOf(openCreate, ev(2, 1, WhisperGroupEventType.ADD, admin, "\"u2\""))
        val m = applyEvents(gid, memberAdd, verify, keys)
        assertTrue(!m.inviteAdminsOnly)
        assertTrue(m.canInvite("u2"))
        // u2 (member, stub-verified) adds u3.
        val keys2: (String) -> String? = { "KEY" }
        val m2 = applyEvents(gid, memberAdd + ev(3, 1, WhisperGroupEventType.ADD, "u2", "\"u3\""), verify, keys2)
        assertTrue(m2.isMember("u3"))
        // A stranger (never a member) cannot add even with a valid signature.
        try {
            applyEvents(gid, memberAdd + ev(3, 1, WhisperGroupEventType.ADD, "stranger", "\"u4\""), verify, keys2)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("signed by inviter"))
        }
    }

    @Test
    fun `member add under closed invites fails closed`() {
        val keys2: (String) -> String? = { "KEY" }
        val log = listOf(create(), ev(2, 1, WhisperGroupEventType.ADD, admin, "\"u2\""))
        try {
            applyEvents(gid, log + ev(3, 1, WhisperGroupEventType.ADD, "u2", "\"u3\""), verify, keys2)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("not admin-signed"))
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
    fun `epoch ranges gate history across remove and re-add`() {
        val keys2: (String) -> String? = { "KEY" }
        val log = listOf(
            create(),
            ev(2, 1, WhisperGroupEventType.ADD, admin, "\"u2\""),
            ev(3, 2, WhisperGroupEventType.REMOVE, admin, "\"u2\""),
            ev(4, 3, WhisperGroupEventType.ADD, admin, "\"u2\""),
        )
        val m = applyEvents(gid, log, verify, keys2)
        assertTrue(m.isMember("u2"))
        // Epoch 1 (first tenure) and 3+ (second tenure) open; epoch 2 (removed) shut.
        assertTrue(m.isMemberAt("u2", 1))
        assertTrue(!m.isMemberAt("u2", 2))
        assertTrue(m.isMemberAt("u2", 3))
        assertTrue(m.isMemberAt("u2", 1_000_000))
        // The admin never left: every epoch open.
        assertTrue(m.isMemberAt(admin, 0))
        assertTrue(m.isMemberAt(admin, 2))
        // A stranger was never a member at any epoch.
        assertTrue(!m.isMemberAt("stranger", 1))
    }

    @Test
    fun `invite join decline cancel lifecycle`() {
        val keys2: (String) -> String? = { "KEY" }
        val invited = listOf(
            create(),
            ev(2, 0, WhisperGroupEventType.INVITE, admin, "\"u2\""),
        )
        val m0 = applyEvents(gid, invited, verify, keys2)
        assertTrue(m0.pendingInvites.contains("u2"))
        assertTrue(!m0.isMember("u2"))
        // Join without... join with invite: member, pending cleared, epoch bumps.
        val joined = invited + ev(3, 1, WhisperGroupEventType.JOIN, "u2", "\"\"")
        val m1 = applyEvents(gid, joined, verify, keys2)
        assertTrue(m1.isMember("u2"))
        assertTrue(m1.pendingInvites.isEmpty())
        assertEquals(1L, m1.epoch)
        // Fresh invite then decline: pending cleared, never a member.
        val declined = joined + listOf(
            ev(4, 1, WhisperGroupEventType.INVITE, admin, "\"u3\""),
            ev(5, 1, WhisperGroupEventType.DECLINE, "u3", "\"\""),
        )
        val m2 = applyEvents(gid, declined, verify, keys2)
        assertTrue(!m2.isMember("u3"))
        assertTrue(m2.pendingInvites.isEmpty())
        // Fresh invite then admin cancel: same end state.
        val canceled = declined + listOf(
            ev(6, 1, WhisperGroupEventType.INVITE, admin, "\"u4\""),
            ev(7, 1, WhisperGroupEventType.CANCEL, admin, "\"u4\""),
        )
        val m3 = applyEvents(gid, canceled, verify, keys2)
        assertTrue(!m3.isMember("u4"))
        assertTrue(m3.pendingInvites.isEmpty())
    }

    @Test
    fun `join without invite fails closed`() {
        val keys2: (String) -> String? = { "KEY" }
        try {
            applyEvents(gid, listOf(create(), ev(2, 1, WhisperGroupEventType.JOIN, "u2", "\"\"")), verify, keys2)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("without invite"))
        }
    }

    @Test
    fun `cancel by non-admin fails closed`() {
        val keys2: (String) -> String? = { "KEY" }
        val log = listOf(
            create(),
            ev(2, 0, WhisperGroupEventType.INVITE, admin, "\"u2\""),
            ev(3, 1, WhisperGroupEventType.JOIN, "u2", "\"\""),
            ev(4, 1, WhisperGroupEventType.INVITE, admin, "\"u3\""),
        )
        try {
            applyEvents(gid, log + ev(5, 1, WhisperGroupEventType.CANCEL, "u2", "\"u3\""), verify, keys2)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("admin-signed"))
        }
    }

    @Test
    fun `picture latest wins and requires admin`() {
        val keys2: (String) -> String? = { "KEY" }
        fun pic(url: String) = buildGroupPicturePayload(url, "iv1", mapOf(admin to "F"))
        val log = listOf(
            create(),
            ev(2, 0, WhisperGroupEventType.PICTURE, admin, pic("https://a")),
            ev(3, 0, WhisperGroupEventType.PICTURE, admin, pic("https://b")),
        )
        val m = applyEvents(gid, log, verify, keys2)
        assertEquals("https://b", m.picture?.url)
        // Member-signed picture rejected.
        val log2 = listOf(
            create(),
            ev(2, 0, WhisperGroupEventType.INVITE, admin, "\"u2\""),
            ev(3, 1, WhisperGroupEventType.JOIN, "u2", "\"\""),
        )
        try {
            applyEvents(gid, log2 + ev(4, 1, WhisperGroupEventType.PICTURE, "u2", pic("https://evil")), verify, keys2)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("admin-signed"))
        }
        // Garbage payload rejected (error() throws IllegalStateException, not
        // IllegalArgumentException like require() — hence the wider catch).
        try {
            applyEvents(gid, listOf(create(), ev(2, 0, WhisperGroupEventType.PICTURE, admin, "nope")), verify, keys2)
            fail("must throw")
        } catch (e: IllegalStateException) {
            assertTrue((e.message ?: "").contains("picture payload"))
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
    fun `flag on in dev builds`() {
        assertTrue(groupsEnabled())
    }

    @Test
    fun `recipient matrix`() {
        // Owner with toggle on: everything except own actions.
        assertTrue(shouldNotifyGroupEvent(GroupNotifScope.JOIN, "u2", "owner", amOwner = true, toggleOn = true))
        assertTrue(shouldNotifyGroupEvent(GroupNotifScope.LEAVE, "u2", "owner", amOwner = true, toggleOn = true))
        assertTrue(shouldNotifyGroupEvent(GroupNotifScope.DECLINE, "u2", "owner", amOwner = true, toggleOn = true))
        assertTrue(!shouldNotifyGroupEvent(GroupNotifScope.JOIN, "owner", "owner", amOwner = true, toggleOn = true))
        // Toggle off silences everything.
        assertTrue(!shouldNotifyGroupEvent(GroupNotifScope.JOIN, "u2", "owner", amOwner = true, toggleOn = false))
        assertTrue(!shouldNotifyGroupEvent(GroupNotifScope.DECLINE, "u2", "owner", amOwner = true, toggleOn = false))
        // Non-owner (opted-in member/admin): join/leave yes, decline no.
        assertTrue(shouldNotifyGroupEvent(GroupNotifScope.JOIN, "u3", "u2", amOwner = false, toggleOn = true))
        assertTrue(shouldNotifyGroupEvent(GroupNotifScope.LEAVE, "u3", "u2", amOwner = false, toggleOn = true))
        assertTrue(!shouldNotifyGroupEvent(GroupNotifScope.DECLINE, "u3", "u2", amOwner = false, toggleOn = true))
    }

    @Test
    fun `templates render without placeholders`() {
        val rnd = kotlin.random.Random(7)
        repeat(40) {
            val j = WhisperGroupNotifTemplates.joinText("Aïcha-ß", "Crew €", rnd)
            val l = WhisperGroupNotifTemplates.leaveText("Aïcha-ß", "Crew €", rnd)
            val d = WhisperGroupNotifTemplates.declineText("Aïcha-ß", "Crew €", rnd)
            assertTrue(!j.contains("%1\$s") && !l.contains("%1\$s") && !d.contains("%1\$s"))
            // Every template names the user; the group slot is optional by design
            // (the notification title already carries the group name).
            assertTrue(j.contains("Aïcha-ß") && l.contains("Aïcha-ß") && d.contains("Aïcha-ß"))
        }
        assertTrue(WhisperGroupNotifTemplates.joinPoolSize() >= 8)
        assertTrue(WhisperGroupNotifTemplates.leavePoolSize() >= 8)
        assertTrue(WhisperGroupNotifTemplates.declinePoolSize() >= 5)
    }
}
