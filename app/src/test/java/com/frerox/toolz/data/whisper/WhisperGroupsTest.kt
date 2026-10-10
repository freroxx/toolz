/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import com.frerox.toolz.ui.screens.whisper.WhisperGroupChatViewModel
import com.frerox.toolz.ui.screens.whisper.resolveMentionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    /** Accepts every actor's key (JOIN/DECLINE self-signatures in tests). */
    private val keys2all: (String) -> String? = { "KEY" }

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

    /** An INVITE (signed by the inviter) plus a self-signed JOIN builds membership. */
    private fun inviteJoin(seq: Long, epoch: Long, uid: String, by: String = admin): List<WhisperGroupEvent> =
        listOf(
            ev(seq, epoch, WhisperGroupEventType.INVITE, by, "\"$uid\""),
            ev(seq + 1, epoch + 1, WhisperGroupEventType.JOIN, uid, "\"\""),
        )

    @Test
    fun `create plus invite-join builds membership`() {
        val m = applyEvents(gid, listOf(create()) + inviteJoin(2, 0, "u2"), verify, keys2all)
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
    fun `non-member invite fails closed`() {
        val log = listOf(create(), ev(2, 0, WhisperGroupEventType.INVITE, "u2", "\"u3\""))
        try {
            applyEvents(gid, log, verify, keys)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            // u2 is not a member at all: rejected before the admin/open-invite check.
            assertTrue((e.message ?: "").contains("signed by inviter"))
        }
    }

    @Test
    fun `open invites let members invite, strangers still blocked`() {
        val openCreate = create().copy(payloadJson = """{"name":"Crew","invite":"all"}""")
        val memberLog = listOf(openCreate) + inviteJoin(2, 0, "u2")
        val m = applyEvents(gid, memberLog, verify, keys2all)
        assertTrue(!m.inviteAdminsOnly)
        assertTrue(m.canInvite("u2"))
        // u2 (member, stub-verified) invites u3, who joins.
        val m2 = applyEvents(gid, memberLog + inviteJoin(4, 1, "u3", by = "u2"), verify, keys2all)
        assertTrue(m2.isMember("u3"))
        // A stranger (never a member) cannot invite even with a valid signature.
        try {
            applyEvents(gid, memberLog + ev(4, 1, WhisperGroupEventType.INVITE, "stranger", "\"u4\""), verify, keys2all)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("signed by inviter"))
        }
    }

    @Test
    fun `member invite under closed invites fails closed`() {
        val log = listOf(create()) + inviteJoin(2, 0, "u2")
        try {
            applyEvents(gid, log + ev(4, 1, WhisperGroupEventType.INVITE, "u2", "\"u3\""), verify, keys2all)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("not admin-signed"))
        }
    }

    @Test
    fun `legacy add events are rejected`() {
        val log = listOf(create(), ev(2, 1, WhisperGroupEventType.ADD, admin, "\"u2\""))
        try {
            applyEvents(gid, log, verify, keys)
            fail("must throw")
        } catch (e: IllegalStateException) {
            assertTrue((e.message ?: "").contains("legacy add"))
        }
        // The tolerant path truncates at the legacy row instead of bricking.
        val m = applyEventsTolerant(gid, log, verify, keys)
        assertTrue(m.degraded)
        assertEquals(2L, m.badSeq)
        assertTrue(m.isAdmin(admin))
        assertTrue(!m.isMember("u2"))
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
        var seq = 2L
        var epoch = 0L
        for (i in 2..12) {
            log += inviteJoin(seq, epoch, "u$i")
            seq += 2
            epoch += 1
        }
        val m = applyEvents(gid, log, verify, keys2all)
        assertEquals(12, m.members.size)
        try {
            applyEvents(gid, log + ev(seq, epoch, WhisperGroupEventType.INVITE, admin, "\"u13\""), verify, keys2all)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("full"))
        }
    }

    @Test
    fun `stale epoch and unordered logs rejected`() {
        // INVITE never moves the epoch, so the stale write needs a JOIN first (epoch 0 -> 1).
        val stale = listOf(create()) + inviteJoin(2, 0, "u2") +
            ev(4, 0, WhisperGroupEventType.INVITE, admin, "\"u3\"")
        try {
            applyEvents(gid, stale, verify, keys2all)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("stale epoch"))
        }
        val unordered = listOf(create(), ev(3, 1, WhisperGroupEventType.INVITE, admin, "\"u3\""), ev(2, 1, WhisperGroupEventType.INVITE, admin, "\"u2\""))
        try {
            applyEvents(gid, unordered, verify, keys)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("seq-ordered"))
        }
    }

    @Test
    fun `epoch ranges gate history across remove and re-add`() {
        val log = listOf(create()) +
            inviteJoin(2, 0, "u2") +
            ev(4, 2, WhisperGroupEventType.REMOVE, admin, "\"u2\"") +
            inviteJoin(5, 2, "u2")
        val m = applyEvents(gid, log, verify, keys2all)
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
        fun pic(url: String) = buildGroupPicturePayload(url, mapOf(admin to "F"))
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

    @Test
    fun `poisoned log truncates to verified prefix`() {
        val keys2: (String) -> String? = { "KEY" }
        val log = listOf(
            create(),
            ev(2, 0, WhisperGroupEventType.INVITE, admin, "\"u2\""),
            ev(3, 1, WhisperGroupEventType.JOIN, "u2", "\"\""),
            ev(4, 1, WhisperGroupEventType.RENAME, admin, "\"Evil\"", sig = "bad"),
            ev(5, 1, WhisperGroupEventType.RENAME, admin, "\"Good\""),
        )
        // Strict path still fails closed on the poison.
        try {
            applyEvents(gid, log, verify, keys2)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("admin-signed"))
        }
        // Tolerant path keeps the verified prefix and flags it.
        val m = applyEventsTolerant(gid, log, verify, keys2)
        assertTrue(m.degraded)
        assertEquals(4L, m.badSeq)
        assertTrue(m.isMember("u2"))
        assertEquals("Crew", m.name)
        assertTrue(!m.isMember("intruder"))
    }

    @Test
    fun `bad create still fails everything`() {
        val keys2: (String) -> String? = { "KEY" }
        try {
            applyEventsTolerant(
                gid,
                listOf(ev(1, 0, WhisperGroupEventType.CREATE, admin, "\"x\"", sig = "bad")),
                verify, keys2,
            )
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("signature"))
        }
    }

    @Test
    fun `error mapping covers fail-closed cases`() {
        assertTrue(mapGroupError("group full (12 max)") != null)
        assertTrue(mapGroupError("group send quota exceeded") != null)
        assertTrue(mapGroupError("stale epoch") != null)
        assertTrue(mapGroupError("no invite") != null)
        assertTrue(mapGroupError("You are no longer a member of this group.") != null)
        assertTrue(mapGroupError("Could not reach 2 member(s) — message not sent.") != null)
        assertTrue(mapGroupError("group_send_queued") != null)
        assertTrue(mapGroupError("some random network failure") == null)
        assertTrue(mapGroupError(null) == null)
        assertTrue(mapGroupError("") == null)
    }

    @Test
    fun `group message iv round-trips epoch`() {
        assertEquals(7L, groupMessageIvEpoch(groupMessageIv(7)))
        assertEquals(0L, groupMessageIvEpoch(groupMessageIv(0)))
        assertNull(groupMessageIvEpoch("v3-group"))
        assertNull(groupMessageIvEpoch("garbage"))
    }

    @Test
    fun `content envelope round-trips all fields`() {
        val c = WhisperGroupContent(
            t = "hello",
            img = GroupImageRef(url = "https://x/y", key = "a2V5", att = "a1", mime = "image/png"),
            reply = GroupReplyRef(id = "c1", sender = "u2", text = "quoted"),
            poll = GroupPoll(id = "p1", q = "Lunch?", opts = listOf("Yes", "No")),
            vote = GroupVote(poll = "p1", opt = 0),
            mentions = listOf("u2", "u3"),
        )
        val back = parseGroupContentBody(buildGroupContentBody(c))
        assertEquals(c, back)
        assertTrue(buildGroupContentBody(c).startsWith(GROUP_CONTENT_PREFIX))
    }

    @Test
    fun `legacy bare text stays text`() {
        assertEquals("plain hello", parseGroupContentBody("plain hello").t)
        assertNull(parseGroupContentBody("plain hello").img)
        // Corrupt structured bodies degrade to raw text, never crash.
        assertEquals("wg1:{oops", parseGroupContentBody("wg1:{oops").t)
    }

    @Test
    fun `tombstone keys are namespaced`() {
        assertEquals("gmsg:abc", groupTombstoneKey("abc"))
        assertTrue(!groupTombstoneKey("abc").contains("1:1"))
    }

    @Test
    fun `demote lifecycle`() {
        val log = listOf(create()) + inviteJoin(2, 0, "u2") + listOf(
            ev(4, 1, WhisperGroupEventType.PROMOTE, admin, "\"u2\""),
        )
        val promoted = applyEvents(gid, log, verify, keys2all)
        assertTrue(promoted.isAdmin("u2"))
        val demoted = applyEvents(gid, log + ev(5, 2, WhisperGroupEventType.DEMOTE, admin, "\"u2\""), verify, keys2all)
        assertTrue(!demoted.isAdmin("u2"))
        assertTrue(demoted.isMember("u2"))
        assertEquals(2L, demoted.epoch)
        // Demoting the last admin fails closed (solo-admin log).
        try {
            applyEvents(gid, listOf(create(), ev(2, 0, WhisperGroupEventType.DEMOTE, admin, "\"user-admin\"")), verify, keys2all)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("last admin"))
        }
        // Demoting a non-admin fails closed.
        try {
            applyEvents(
                gid,
                listOf(create()) + inviteJoin(2, 0, "u2") +
                    ev(4, 1, WhisperGroupEventType.DEMOTE, admin, "\"u2\""),
                verify, keys2all,
            )
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("non-admin"))
        }
        // A plain member's demote fails closed (u2 was never promoted here).
        val memberLog = listOf(create()) + inviteJoin(2, 0, "u2")
        try {
            applyEvents(gid, memberLog + ev(4, 1, WhisperGroupEventType.DEMOTE, "u2", "\"user-admin\""), verify, keys2all)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("admin-signed"))
        }
    }

    @Test
    fun `settings toggle and description`() {
        val base = listOf(create()) + inviteJoin(2, 0, "u2")
        val m0 = applyEvents(gid, base, verify, keys2all)
        assertTrue(!m0.adminOnlySend)
        assertEquals("", m0.description)
        // Admin-only mode on (no epoch bump).
        val m1 = applyEvents(
            gid, base + ev(4, 1, WhisperGroupEventType.SETTINGS, admin, buildGroupSettingsPayload(true, null)),
            verify, keys2all,
        )
        assertTrue(m1.adminOnlySend)
        assertEquals(1L, m1.epoch)
        // Description set + truncated.
        val long = "x".repeat(200)
        val m2 = applyEvents(
            gid, base + ev(4, 1, WhisperGroupEventType.SETTINGS, admin, buildGroupSettingsPayload(null, long)),
            verify, keys2all,
        )
        assertEquals(140, m2.description.length)
        assertTrue(!m2.adminOnlySend)
        // Absent keys leave values untouched.
        val m3 = applyEvents(
            gid, base + ev(4, 1, WhisperGroupEventType.SETTINGS, admin, buildGroupSettingsPayload(null, "Hi")),
            verify, keys2all,
        )
        assertEquals("Hi", m3.description)
        // Member-signed settings rejected; garbage payload rejected.
        try {
            applyEvents(gid, base + ev(4, 1, WhisperGroupEventType.SETTINGS, "u2", buildGroupSettingsPayload(true, null)), verify, keys2all)
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("admin-signed"))
        }
        try {
            applyEvents(gid, base + ev(4, 1, WhisperGroupEventType.SETTINGS, admin, "nope"), verify, keys2all)
            fail("must throw")
        } catch (e: IllegalStateException) {
            assertTrue((e.message ?: "").contains("settings payload"))
        }
    }

    @Test
    fun `poll tally keeps latest vote per sender`() {
        fun msg(id: String, sender: String, poll: String, opt: Int) = WhisperGroupChatMessage(
            id = id, groupId = gid, clientId = "c-$id", senderId = sender,
            senderName = sender, body = "", createdAt = "", epoch = 1, mine = false,
            vote = GroupVote(poll, opt),
        )
        val tally = WhisperGroupChatViewModel.tallyPolls(
            listOf(
                msg("1", "u2", "p1", 0),
                msg("2", "u3", "p1", 1),
                msg("3", "u2", "p1", 1),
            ),
            me = "u2",
        )
        val p1 = tally["p1"]!!
        assertEquals(2, p1.counts[1])
        assertNull(p1.counts[0])
        assertEquals(1, p1.myVote)
    }

    @Test
    fun `mention resolver prefers longest names`() {
        val names = mapOf("id1" to "Ann", "id2" to "Ann Lee", "id3" to "Bo")
        assertEquals(listOf("id2"), resolveMentionIds("hey @Ann Lee, sup", names))
        assertEquals(listOf("id1"), resolveMentionIds("hey @Ann, sup", names))
        assertEquals(emptyList<String>(), resolveMentionIds("no mentions here", names))
        assertEquals(emptyList<String>(), resolveMentionIds("hey @Zed", names))
    }

    @Test
    fun `unknown future event type truncates instead of bricking`() {
        val log = listOf(create()) + inviteJoin(2, 0, "u2") + listOf(
            WhisperGroupEvent(
                id = "e-x", groupId = gid, seq = 4, epoch = 1,
                type = WhisperGroupEventType.UNKNOWN, actor = admin,
                payloadJson = "{\"future\":1}", adminSig = "ok",
            ),
        )
        try {
            applyEvents(gid, log, verify, keys2all)
            fail("must throw")
        } catch (e: IllegalStateException) {
            assertTrue((e.message ?: "").contains("unknown event type"))
        }
        val m = applyEventsTolerant(gid, log, verify, keys2all)
        assertTrue(m.degraded)
        assertEquals(4L, m.badSeq)
        assertTrue(m.isMember("u2"))
    }

    @Test
    fun `last-admin errors map to friendly string`() {
        assertNotNull(mapGroupError("last admin cannot leave"))
        assertNotNull(mapGroupError("cannot demote the last admin"))
        assertNotNull(mapGroupError("cannot remove the last admin"))
        assertNotNull(mapGroupError("Could not start your secure session — nope"))
    }

    @Test
    fun `settings edit flag round-trips and gates picture`() {
        val payload = buildGroupSettingsPayload(true, "Hi", true)
        val parsed = parseGroupSettings(payload)!!
        assertEquals(true, parsed.first)
        assertEquals("Hi", parsed.second)
        assertEquals(true, parsed.third)
        // Absent edit key leaves prior value untouched.
        val keep = parseGroupSettings(buildGroupSettingsPayload(null, "Yo"))!!
        assertNull(keep.first)
        assertNull(keep.third)
        // Member-signed picture accepted when the flag is open...
        val openLog = listOf(create()) + inviteJoin(2, 0, "u2") + listOf(
            ev(4, 1, WhisperGroupEventType.SETTINGS, admin, buildGroupSettingsPayload(null, null, true)),
        )
        val open = applyEvents(gid, openLog, verify, keys2all)
        assertTrue(open.membersCanEdit)
        val picPayload = buildGroupPicturePayload("https://x/y", mapOf(admin to "F", "u2" to "G"))
        val withPic = applyEvents(
            gid, openLog + ev(5, 1, WhisperGroupEventType.PICTURE, "u2", picPayload),
            verify, keys2all,
        )
        assertEquals("https://x/y", withPic.picture?.url)
        // ...and rejected when closed.
        try {
            applyEvents(
                gid, listOf(create()) + inviteJoin(2, 0, "u2") +
                    ev(4, 1, WhisperGroupEventType.PICTURE, "u2", picPayload),
                verify, keys2all,
            )
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("admin-signed"))
        }
    }

    @Test
    fun `poll close and image expiry ride the envelope`() {
        val close = WhisperGroupContent(t = "poll closed", pollClose = "p1")
        assertEquals("p1", parseGroupContentBody(buildGroupContentBody(close)).pollClose)
        val img = GroupImageRef(url = "https://x/y", key = "a2V5", exp = 9_999_999_999L)
        val back = parseGroupContentBody(buildGroupContentBody(WhisperGroupContent(img = img)))
        assertEquals(9_999_999_999L, back.img?.exp)
        // Legacy rows without the new fields still parse (nulls, not crashes).
        assertNull(parseGroupContentBody("wg1:{\"t\":\"hi\"}").pollClose)
    }

    @Test
    fun `empty history maps to friendly string`() {
        assertNotNull(mapGroupError("Group has no history."))
    }
}
