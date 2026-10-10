/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

/**
 * Phase-2 GROUPS DOMAIN of [WhisperRepository], following the
 * WhisperRepositorySocial.kt extraction pattern (extension functions, no
 * call-site changes). v1.1.7 dev, feature-flag ON — every entry point still
 * requires [WhisperGroupsConfig.ENABLED] first, and the server enforces
 * client_version_code >= 18 on every group write.
 *
 * Transport design (pairwise fan-out, ≤12 members):
 * - The sender seals ONE inner envelope per member through the UNMODIFIED
 *   1:1 session path (same seal/open, same 1:1 AAD `sender|receiver`). The
 *   inner envelope carries {g, e, s, body} so groupId + epoch ride INSIDE
 *   the AEAD ciphertext — stronger than AAD (which is exactly why there is
 *   no separate group AAD domain: the binding is encrypted, not adjacent).
 * - The server row (`whisper_group_messages.content`) holds a map of
 *   memberId → v3-frame-json. Each member opens only their own frame with
 *   their own 1:1 session, then verifies the inner envelope matches the
 *   row (groupId + epoch equality) — fail closed on any mismatch.
 * - Membership is built from verified `whisper_group_events` ONLY
 *   ([applyEvents]), never from the server members table. Admin ops are
 *   signed with the admin's protocol signing key
 *   (WhisperCrypto.signProtocol / verifyProtocol); the signature covers
 *   "WG-EVENT|groupId|seq|epoch|type|actor|payloadJson". Adds go through
 *   persistent invites: INVITE (signed, no state change) → JOIN (self-signed,
 *   requires a prior INVITE event for the actor) → member; DECLINE/CANCEL
 *   only clear pending. A `picture` admin event carries the group picture
 *   (URL server-visible, image key sealed per member).
 * - Removed members stop receiving frames (they are simply not in the
 *   fan-out set); post-add members see nothing prior (no history frames
 *   are ever re-sealed to them); a stale-epoch row is rejected both by
 *   the server trigger AND here before any decrypt is attempted.
 */

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.Order

/** Compile-time gate: true in v1.1.7 dev builds (1.1.7 is unshipped — only dev
 * devices carry it). Server-side, every group write still requires
 * client_version_code >= 18, and the ship-day floor flip blocks <1.1.7
 * clients from all of Whisper — so the flag can stay true for good. */
object WhisperGroupsConfig {
    /** Master switch — every group entry point must check this first. */
    const val ENABLED = true

    /** Max members per group, mirrored by SQL (member-cap trigger) and UI. */
    const val MAX_MEMBERS = 12

    /** Minimum client versionCode allowed to write group tables (SQL CHECK). */
    const val GROUP_MIN_VERSION_CODE = 18

    /** Max group display-name length (client + UI enforce, server stores). */
    const val MAX_GROUP_NAME_CHARS = 64

    /** Envelope version written into every inner frame and row map. */
    const val ENVELOPE_VERSION = 1

    /** Fan-out quota: 50 msgs/day x MAX_MEMBERS (mirrors SQL v_limit 600). */
    const val DAILY_ENVELOPE_BUDGET = 600
}

/** Requires groups; throws (fail-closed) when the flag is off. */
fun requireGroupsEnabled() {
    check(WhisperGroupsConfig.ENABLED) { "Whisper groups are not enabled in this build" }
}

/** Non-throwing gate for UI/call sites. */
fun groupsEnabled(): Boolean = WhisperGroupsConfig.ENABLED

// ── Wire models (client mirrors of the SQL group tables) ────────────────

enum class WhisperGroupEventType(val wire: String) {
    CREATE("create"),
    ADD("add"),
    REMOVE("remove"),
    LEAVE("leave"),
    RENAME("rename"),
    PROMOTE("promote"),
    INVITE("invite"),
    JOIN("join"),
    DECLINE("decline"),
    CANCEL("cancel"),
    PICTURE("picture"),
    ;

    companion object {
        fun of(wire: String): WhisperGroupEventType? = entries.firstOrNull { it.wire == wire }
    }
}

enum class WhisperGroupRole(val wire: String) {
    ADMIN("admin"),
    MEMBER("member"),
    ;

    companion object {
        fun of(wire: String): WhisperGroupRole? = entries.firstOrNull { it.wire == wire }
    }
}

data class WhisperGroupMember(
    val groupId: String,
    val userId: String,
    val role: WhisperGroupRole,
    val invitedBy: String? = null,
)

data class WhisperGroupEvent(
    val id: String,
    val groupId: String,
    val seq: Long,
    val epoch: Long,
    val type: WhisperGroupEventType,
    val actor: String,
    val payloadJson: String,
    val adminSig: String,
)

/** Current group picture: the URL is server-visible metadata; the image key
 * travels per-member sealed (see `picture` events) so bytes stay opaque. (The
 * AES-GCM nonce rides prepended inside the sealed bytes — there is no separate
 * IV field on the wire anymore.) */
data class WhisperGroupPicture(
    val url: String,
    val epoch: Long,
)

/** Membership rebuilt purely from a verified event log. */
data class WhisperGroupMembership(
    val groupId: String,
    val epoch: Long,
    val members: Map<String, WhisperGroupRole>,
    val name: String,
    /** True when only admins may add members (set at creation, signed in CREATE). */
    val inviteAdminsOnly: Boolean = true,
    /**
     * Per-member visible epochs: segments of [joinEpoch, leaveEpoch) derived
     * from the same events (open-ended = Long.MAX_VALUE). A re-added member
     * keeps a gap for the epochs they missed — history they never belonged
     * to stays shut even though they can fetch the rows again.
     */
    val ranges: Map<String, List<LongRange>> = emptyMap(),
    /** Outstanding invites (invited, not yet joined/declined/canceled). */
    val pendingInvites: Set<String> = emptySet(),
    /** Latest verified picture event, null until the first one. */
    val picture: WhisperGroupPicture? = null,
    /**
     * True when the log contains an event that failed verification: the
     * membership above covers the verified PREFIX only (every event before
     * [badSeq]). The UI must banner this — history after the break is
     * unverifiable, never silently trusted.
     */
    val degraded: Boolean = false,
    /** Seq of the first unverifiable event, null when the log is fully clean. */
    val badSeq: Long? = null,
) {
    val admins: Set<String> get() = members.filterValues { it == WhisperGroupRole.ADMIN }.keys
    fun isMember(userId: String): Boolean = members.containsKey(userId)
    fun isAdmin(userId: String): Boolean = members[userId] == WhisperGroupRole.ADMIN
    /** Whether [userId] may invite right now (admins always; members iff open invites). */
    fun canInvite(userId: String): Boolean = isAdmin(userId) || (!inviteAdminsOnly && isMember(userId))
    /** Whether [userId] belonged to the group at [epoch] (read-side history gate). */
    fun isMemberAt(userId: String, epoch: Long): Boolean =
        ranges[userId]?.any { epoch in it } ?: false
}

/** Inner frame plaintext: sealed per member through the 1:1 session. */
@Serializable
data class WhisperGroupFrameBody(
    /** Wire version — REQUIRED (no default): kotlinx drops default-valued fields
     * on encode, which would make the version gate vacuous. Every frame carries it. */
    val v: Int,
    val g: String,
    val e: Long,
    val s: Long,
    val body: String,
)

/** Server-row content: one v3 frame per member. */
@Serializable
data class WhisperGroupRowContent(
    /** Same required-version rule as [WhisperGroupFrameBody]. */
    val v: Int,
    val g: String,
    val e: Long,
    val s: Long,
    @SerialName("from") val senderId: String,
    val frames: Map<String, String>,
)

/** Shared codec: test-visible (same module) so tests tamper via re-encode, never string surgery. */
internal val groupJson = Json { ignoreUnknownKeys = true }

// ── Pure validators (unit-testable, no Android) ─────────────────────────

/** Canonical bytes an admin signs for an event (verifyProtocol input). */
fun groupEventSignBytes(
    groupId: String,
    seq: Long,
    epoch: Long,
    type: WhisperGroupEventType,
    actor: String,
    payloadJson: String,
): ByteArray =
    "WG-EVENT|$groupId|$seq|$epoch|${type.wire}|$actor|$payloadJson".toByteArray(Charsets.UTF_8)

/** Rebuilds membership from seq-ordered events; throws fail-closed on any violation. */
fun applyEvents(
    groupId: String,
    events: List<WhisperGroupEvent>,
    verify: (payload: ByteArray, sigB64: String, signerX509B64: String) -> Boolean,
    signerKeyOf: (userId: String) -> String?,
): WhisperGroupMembership {
    require(events.zipWithNext { a, b -> a.seq < b.seq }.all { it }) { "events not strictly seq-ordered" }
    var epoch = 0L
    var name = ""
    var inviteAdminsOnly = true
    var picture: WhisperGroupPicture? = null
    val members = mutableMapOf<String, WhisperGroupRole>()
    val invited = mutableSetOf<String>()
    val joinEpoch = mutableMapOf<String, Long>()
    val closedRanges = mutableMapOf<String, MutableList<LongRange>>()
    fun closeSegment(userId: String, atEpoch: Long) {
        val start = joinEpoch.remove(userId) ?: return
        closedRanges.getOrPut(userId) { mutableListOf() }.add(start until atEpoch)
    }
    for (ev in events) {
        require(ev.groupId == groupId) { "event from another group" }
        require(ev.epoch >= epoch) { "stale epoch ${ev.epoch} < $epoch" }
        val type = ev.type
        if (type == WhisperGroupEventType.CREATE) {
            require(members.isEmpty()) { "duplicate create" }
            val signer = signerKeyOf(ev.actor)
            require(signer != null && verify(groupEventSignBytes(ev.groupId, ev.seq, ev.epoch, type, ev.actor, ev.payloadJson), ev.adminSig, signer)) {
                "bad create signature"
            }
            members[ev.actor] = WhisperGroupRole.ADMIN
            joinEpoch[ev.actor] = ev.epoch
            val (parsedName, parsedInvite) = runCatching {
                val obj = groupJson.parseToJsonElement(ev.payloadJson) as? kotlinx.serialization.json.JsonObject
                val nm = obj?.get("name")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull } ?: ""
                val inv = obj?.get("invite")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull } ?: "admins"
                nm to (inv != "all")
            }.getOrDefault("" to true)
            name = parsedName.take(WhisperGroupsConfig.MAX_GROUP_NAME_CHARS)
            inviteAdminsOnly = parsedInvite
            epoch = ev.epoch
            continue
        }
        require(members.isNotEmpty()) { "event before create" }
        when (type) {
            // Retired in the invites slice: adds now go INVITE (signed, no
            // state change) → JOIN (self-signed). A legacy `add` row fails
            // closed here — and truncates the log in the tolerant path —
            // instead of silently resurrecting a privileged direct-add.
            WhisperGroupEventType.ADD -> error("legacy add events are no longer accepted (use invite+join)")
            WhisperGroupEventType.REMOVE -> {
                require(isAdminSigned(ev, members, verify, signerKeyOf)) { "remove not admin-signed" }
                val target = targetOf(ev)
                require(members.containsKey(target)) { "remove of non-member" }
                require(members[target] != WhisperGroupRole.ADMIN || members.count { it.value == WhisperGroupRole.ADMIN } > 1) {
                    "cannot remove last admin"
                }
                members.remove(target)
                closeSegment(target, ev.epoch)
                epoch = ev.epoch
            }
            WhisperGroupEventType.LEAVE -> {
                require(members.containsKey(ev.actor)) { "leave by non-member" }
                require(members[ev.actor] != WhisperGroupRole.ADMIN || members.count { it.value == WhisperGroupRole.ADMIN } > 1) {
                    "last admin cannot leave"
                }
                members.remove(ev.actor)
                closeSegment(ev.actor, ev.epoch)
                epoch = ev.epoch
            }
            WhisperGroupEventType.RENAME -> {
                require(isAdminSigned(ev, members, verify, signerKeyOf)) { "rename not admin-signed" }
                name = targetOf(ev).take(WhisperGroupsConfig.MAX_GROUP_NAME_CHARS)
                epoch = ev.epoch
            }
            WhisperGroupEventType.PROMOTE -> {
                require(isAdminSigned(ev, members, verify, signerKeyOf)) { "promote not admin-signed" }
                val target = targetOf(ev)
                require(members[target] == WhisperGroupRole.MEMBER) { "promote of non-member" }
                members[target] = WhisperGroupRole.ADMIN
                epoch = ev.epoch
            }
            WhisperGroupEventType.INVITE -> {
                require(isMemberSigned(ev, members, verify, signerKeyOf)) { "invite not signed by inviter" }
                require(members[ev.actor] == WhisperGroupRole.ADMIN || !inviteAdminsOnly) {
                    "invite not admin-signed"
                }
                val target = targetOf(ev)
                require(!members.containsKey(target)) { "invite of existing member" }
                require(!invited.contains(target)) { "already invited" }
                require(members.size + invited.size < WhisperGroupsConfig.MAX_MEMBERS) { "group full" }
                invited.add(target)
            }
            WhisperGroupEventType.JOIN -> {
                require(!members.containsKey(ev.actor)) { "join by existing member" }
                require(invited.contains(ev.actor)) { "join without invite" }
                val signer = signerKeyOf(ev.actor)
                require(signer != null && verify(
                    groupEventSignBytes(ev.groupId, ev.seq, ev.epoch, ev.type, ev.actor, ev.payloadJson),
                    ev.adminSig, signer,
                )) { "join not self-signed" }
                require(members.size < WhisperGroupsConfig.MAX_MEMBERS) { "group full" }
                members[ev.actor] = WhisperGroupRole.MEMBER
                joinEpoch[ev.actor] = ev.epoch
                invited.remove(ev.actor)
                epoch = ev.epoch
            }
            WhisperGroupEventType.DECLINE -> {
                require(invited.contains(ev.actor)) { "decline without invite" }
                val signer = signerKeyOf(ev.actor)
                require(signer != null && verify(
                    groupEventSignBytes(ev.groupId, ev.seq, ev.epoch, ev.type, ev.actor, ev.payloadJson),
                    ev.adminSig, signer,
                )) { "decline not self-signed" }
                invited.remove(ev.actor)
            }
            WhisperGroupEventType.CANCEL -> {
                require(isAdminSigned(ev, members, verify, signerKeyOf)) { "cancel not admin-signed" }
                val target = targetOf(ev)
                require(invited.contains(target)) { "cancel without invite" }
                invited.remove(target)
            }
            WhisperGroupEventType.PICTURE -> {
                require(isAdminSigned(ev, members, verify, signerKeyOf)) { "picture not admin-signed" }
                val picUrl = parseGroupPicture(ev.payloadJson) ?: error("bad picture payload")
                picture = WhisperGroupPicture(url = picUrl, epoch = ev.epoch)
            }
            WhisperGroupEventType.CREATE -> error("unreachable")
        }
    }
    require(members.isNotEmpty()) { "empty group" }
    val ranges = (joinEpoch.keys + closedRanges.keys).associateWith { user ->
        val closed = closedRanges[user].orEmpty()
        val open = joinEpoch[user]?.let { listOf(it..Long.MAX_VALUE) }.orEmpty()
        closed + open
    }
    return WhisperGroupMembership(groupId, epoch, members.toMap(), name, inviteAdminsOnly, ranges, invited.toSet(), picture)
}

/**
 * Poison-tolerant wrapper around [applyEvents]: a single malformed or
 * hostile event (any member can publish under their own actor key — RLS only
 * checks coarse membership) must brick NEITHER the group NOR every other
 * group. On the first unverifiable event the verified prefix still yields a
 * usable membership flagged [WhisperGroupMembership.degraded]; history at and
 * after [WhisperGroupMembership.badSeq] is dropped, never trusted. A bad
 * CREATE (prefix of length 1) still fails the whole log — nothing verifiable
 * exists without it.
 */
fun applyEventsTolerant(
    groupId: String,
    events: List<WhisperGroupEvent>,
    verify: (payload: ByteArray, sigB64: String, signerX509B64: String) -> Boolean,
    signerKeyOf: (userId: String) -> String?,
): WhisperGroupMembership {
    if (events.isEmpty()) error("Group has no history.")
    try {
        return applyEvents(groupId, events, verify, signerKeyOf)
    } catch (first: RuntimeException) {
        // Linear scan for the first failing prefix. O(k²) signature checks
        // worst case; admin logs are tens of events, never thousands.
        for (k in 1..events.size) {
            val failed = runCatching {
                applyEvents(groupId, events.subList(0, k), verify, signerKeyOf)
            }.exceptionOrNull()
            if (failed != null) {
                if (k == 1) throw first
                val good = applyEvents(groupId, events.subList(0, k - 1), verify, signerKeyOf)
                android.util.Log.w("WhisperGroups", "log truncated at seq ${events[k - 1].seq}: ${failed.message}")
                return good.copy(degraded = true, badSeq = events[k - 1].seq)
            }
        }
        throw first
    }
}

private fun isAdminSigned(
    ev: WhisperGroupEvent,
    members: Map<String, WhisperGroupRole>,
    verify: (ByteArray, String, String) -> Boolean,
    signerKeyOf: (String) -> String?,
): Boolean {
    if (members[ev.actor] != WhisperGroupRole.ADMIN) return false
    return memberSignatureValid(ev, verify, signerKeyOf)
}

/** Any current member with a valid signature (used for ADD under open invites). */
private fun isMemberSigned(
    ev: WhisperGroupEvent,
    members: Map<String, WhisperGroupRole>,
    verify: (ByteArray, String, String) -> Boolean,
    signerKeyOf: (String) -> String?,
): Boolean {
    if (!members.containsKey(ev.actor)) return false
    return memberSignatureValid(ev, verify, signerKeyOf)
}

private fun memberSignatureValid(
    ev: WhisperGroupEvent,
    verify: (ByteArray, String, String) -> Boolean,
    signerKeyOf: (String) -> String?,
): Boolean {
    val signer = signerKeyOf(ev.actor) ?: return false
    return verify(groupEventSignBytes(ev.groupId, ev.seq, ev.epoch, ev.type, ev.actor, ev.payloadJson), ev.adminSig, signer)
}

/** REMOVE/PROMOTE/INVITE/CANCEL carry the subject user id as the raw payload; RENAME carries the name. */
private fun targetOf(ev: WhisperGroupEvent): String = ev.payloadJson.trim().trim('"')

/**
 * Group-picture payload: `{"url","frames":{memberId: sealedKeyFrame}}`.
 * The URL is server-visible metadata; each frame seals the image key to one
 * member through their 1:1 session (same primitive as message fan-out).
 */
fun buildGroupPicturePayload(url: String, frames: Map<String, String>): String =
    groupJson.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        buildJsonObject {
            put("url", url)
            put("frames", groupJson.encodeToJsonElement(
                kotlinx.serialization.builtins.MapSerializer(
                    kotlinx.serialization.serializer<String>(),
                    kotlinx.serialization.serializer<String>(),
                ),
                frames,
            ))
        },
    )

fun parseGroupPicturePayload(raw: String): Pair<String, Map<String, String>>? = runCatching {
    val obj = groupJson.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject ?: return null
    val url = (obj["url"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return null
    if (url.isBlank()) return null
    // A legacy "iv" key (always "") is tolerated and ignored when present.
    val frames = (obj["frames"] as? kotlinx.serialization.json.JsonObject)
        ?.mapNotNull { (k, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.let { k to it } }
        .orEmpty().toMap()
    url to frames
}.getOrNull()

private fun parseGroupPicture(raw: String): String? =
    parseGroupPicturePayload(raw)?.first

// ── Envelope helpers ────────────────────────────────────────────────────

/** Builds one member's frame plaintext (sealed next via the 1:1 session). */
fun buildGroupFrameBody(groupId: String, epoch: Long, seq: Long, plaintext: String): String =
    groupJson.encodeToString(
        WhisperGroupFrameBody.serializer(),
        WhisperGroupFrameBody(v = WhisperGroupsConfig.ENVELOPE_VERSION, g = groupId, e = epoch, s = seq, body = plaintext),
    )

/**
 * Opens + verifies a received frame against its row. Returns the plaintext
 * body, or null (fail-closed, no mutation) on ANY mismatch: wrong group,
 * wrong epoch, bad envelope version.
 */
fun openGroupFrameBody(groupId: String, epoch: Long, framePlaintext: String): String? = runCatching {
    val body = groupJson.decodeFromString(WhisperGroupFrameBody.serializer(), framePlaintext)
    if (body.v != WhisperGroupsConfig.ENVELOPE_VERSION) return null
    if (body.g != groupId || body.e != epoch) return null
    body.body
}.getOrNull()

fun encodeGroupRow(row: WhisperGroupRowContent): String =
    groupJson.encodeToString(WhisperGroupRowContent.serializer(), row)

fun decodeGroupRow(raw: String): WhisperGroupRowContent? = runCatching {
    groupJson.decodeFromString(WhisperGroupRowContent.serializer(), raw)
}.getOrNull()

// ── Structured frame bodies (text/image/reply/poll/vote/mentions) ───────
// A frame body is either legacy bare text (pre-parity sends) or
// `wg1:<json>` (WhisperGroupContent). The prefix makes the two
// unambiguous: old clients render the caption fallback carried in `t`,
// new clients render the full card. Everything rides INSIDE the sealed
// frame — the server never sees structure, only frame sizes.

/** Prefix marking a structured group frame body. */
const val GROUP_CONTENT_PREFIX = "wg1:"

/** Image attachment reference: bytes live on the blob host, key sealed per member. */
@Serializable
data class GroupImageRef(
    val url: String,
    /** Base64 AES-256-GCM key for THIS image (sealed per member via the frame). */
    val key: String,
    val att: String? = null,
    val mime: String = "image/jpeg",
)

/** Reply quote snapshot (names/text frozen at send time — no extra fetch). */
@Serializable
data class GroupReplyRef(
    /** client_id of the quoted row (best-effort scroll target). */
    val id: String,
    val sender: String,
    val text: String,
)

/** A poll card: question + up to 6 options (enforced at send time). */
@Serializable
data class GroupPoll(
    /** Stable poll id (client UUID) votes reference. */
    val id: String,
    val q: String,
    val opts: List<String>,
)

/** One vote: latest vote per sender per poll wins the tally. */
@Serializable
data class GroupVote(
    val poll: String,
    val opt: Int,
)

@Serializable
data class WhisperGroupContent(
    /** Caption / plain text (also the legacy fallback for old clients). */
    val t: String = "",
    val img: GroupImageRef? = null,
    val reply: GroupReplyRef? = null,
    val poll: GroupPoll? = null,
    val vote: GroupVote? = null,
    val mentions: List<String> = emptyList(),
)

fun buildGroupContentBody(c: WhisperGroupContent): String =
    GROUP_CONTENT_PREFIX + groupJson.encodeToString(WhisperGroupContent.serializer(), c)

/** Parses a frame body; legacy bare text becomes `t`, corrupt JSON degrades to `t = raw`. */
fun parseGroupContentBody(raw: String): WhisperGroupContent {
    if (!raw.startsWith(GROUP_CONTENT_PREFIX)) return WhisperGroupContent(t = raw)
    return runCatching {
        groupJson.decodeFromString(WhisperGroupContent.serializer(), raw.removePrefix(GROUP_CONTENT_PREFIX))
    }.getOrDefault(WhisperGroupContent(t = raw))
}

// ── Server row DTOs (snake_case wire, mirror the SQL tables) ────────────

@Serializable
internal data class GroupRow(
    val id: String,
    val name: String,
    val epoch: Long,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
internal data class GroupRowInsert(
    val id: String,
    val name: String,
    val epoch: Long,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
internal data class GroupMemberRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("invited_by") val invitedBy: String? = null,
)

@Serializable
internal data class GroupInviteRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("invited_by") val invitedBy: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
internal data class GroupInviteInsert(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("invited_by") val invitedBy: String,
    @SerialName("client_version_code") val clientVersionCode: Int,
)

/** One outstanding invite for the invite-card UI. */
data class GroupInviteInfo(
    val groupId: String,
    val groupName: String,
    val invitedBy: String,
    val invitedByName: String,
    val createdAt: String,
)

@Serializable
internal data class GroupEventRow(
    val id: String,
    @SerialName("group_id") val groupId: String,
    val seq: Long,
    val epoch: Long,
    val type: String,
    val actor: String,
    val payload: String,
    @SerialName("admin_sig") val adminSig: String,
)

@Serializable
internal data class GroupEventInsert(
    @SerialName("group_id") val groupId: String,
    val seq: Long,
    val epoch: Long,
    val type: String,
    val actor: String,
    val payload: String,
    @SerialName("admin_sig") val adminSig: String,
    @SerialName("client_version_code") val clientVersionCode: Int,
)

@Serializable
internal data class GroupMessageRow(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("client_id") val clientId: String,
    val content: String,
    @SerialName("content_iv") val contentIv: String,
    val epoch: Long,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
internal data class GroupMessageInsert(
    @SerialName("group_id") val groupId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("client_id") val clientId: String,
    val content: String,
    @SerialName("content_iv") val contentIv: String,
    val epoch: Long,
    @SerialName("client_version_code") val clientVersionCode: Int,
)

/** One decrypted group chat line for the UI. */
data class WhisperGroupChatMessage(
    val id: String,
    val groupId: String,
    /** Server row id; client_id rides separately for receipts/tombstones. */
    val clientId: String = "",
    val senderId: String,
    val senderName: String,
    val body: String,
    val createdAt: String,
    val epoch: Long,
    val mine: Boolean,
    val image: GroupImageRef? = null,
    val reply: GroupReplyRef? = null,
    val poll: GroupPoll? = null,
    val vote: GroupVote? = null,
    val mentions: List<String> = emptyList(),
    /** Seen-by user ids for my messages (receipts lane; empty until loaded). */
    val seenBy: List<String> = emptyList(),
)

// ── Transport ops (all flag-gated, 1:1 paths untouched) ─────────────────
// Offline sends queue sealed bundles in the Room outbox (groupId legs of
// WhisperOutgoingQueue, drained by flushGroupOutbox on hub/chat loads);
// every open re-verifies the event log incrementally off the local cache;
// event verification is TOFU against current bundle signers (see file header).

private fun groupClientVersion(): Int = com.frerox.toolz.BuildConfig.VERSION_CODE

private fun WhisperRepository.requireMe(): String {
    val me = myId
    require(me.isNotBlank()) { "Not signed in." }
    return me
}

/** Signer-key lookup (one bundle fetch per actor; pre-fetched before apply). */
private suspend fun WhisperRepository.signerKeyOf(userId: String): String? {
    if (userId == myId) return crypto.protocolSigningPublicKeyBase64()
    return sessionFactory.fetchAndVerifyBundle(userId).getOrNull()?.signerX509B64
}

private fun WhisperRepository.groupVerify(
    payload: ByteArray,
    sigB64: String,
    signerX509B64: String,
): Boolean = crypto.verifyProtocol(payload, sigB64, signerX509B64)

private fun GroupEventRow.toModel() = WhisperGroupEvent(
    id = id,
    groupId = groupId,
    seq = seq,
    epoch = epoch,
    type = WhisperGroupEventType.of(type) ?: error("unknown event type $type"),
    actor = actor,
    payloadJson = payload,
    adminSig = adminSig,
)

/** Fetches + verifies the full event log, persists the cache, returns membership. */
suspend fun WhisperRepository.syncGroup(groupId: String): Result<WhisperGroupMembership> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val membership = fetchVerifiedMembership(groupId).getOrThrow()
    if (!membership.isMember(me)) error("You are no longer a member of this group.")
    membership
}

/** Verified log without the membership requirement (join path for invitees). */
suspend fun WhisperRepository.fetchVerifiedMembership(groupId: String): Result<WhisperGroupMembership> = runCatching {
    requireGroupsEnabled()
    requireMe()
    val group = db.from("whisper_groups").select { filter { eq("id", groupId) } }
        .decodeSingleOrNull<GroupRow>() ?: error("Group not found.")
    // Incremental sync: the local cache holds the verified prefix, so only
    // rows past the cached watermark travel the network on a warm device.
    val cached = groupDao.events(groupId)
    val watermark = cached.maxOfOrNull { it.seq } ?: 0L
    suspend fun fetchFresh(afterSeq: Long): List<WhisperGroupEvent> {
        val rows = if (afterSeq > 0) {
            db.from("whisper_group_events").select {
                filter { eq("group_id", groupId); gt("seq", afterSeq) }
            }.decodeList<GroupEventRow>()
        } else {
            db.from("whisper_group_events").select { filter { eq("group_id", groupId) } }
                .decodeList<GroupEventRow>()
        }
        return rows.sortedBy { it.seq }.map { it.toModel() }
    }
    var events = (cached.map { it.toModel() } + fetchFresh(watermark))
        .distinctBy { it.id }.sortedBy { it.seq }
    if (events.isEmpty()) error("Group has no history.")
    suspend fun verifyAll(log: List<WhisperGroupEvent>): WhisperGroupMembership {
        // Pre-fetch every distinct actor key (bundle fetch is suspend; the
        // verifier lambda below is a pure map read — never blocks).
        val keys = mutableMapOf<String, String?>()
        log.map { it.actor }.distinct().forEach { uid -> keys[uid] = signerKeyOf(uid) }
        return applyEventsTolerant(
            groupId = groupId,
            events = log,
            verify = { payload, sig, signer -> groupVerify(payload, sig, signer) },
            signerKeyOf = { uid -> keys[uid] },
        )
    }
    var membership = verifyAll(events)
    if (membership.degraded && (membership.badSeq ?: Long.MAX_VALUE) <= watermark) {
        // The poison sits AT/BELOW the watermark: the local cache itself is
        // suspect (diverged or poisoned). Drop it and refetch the full log —
        // the tolerant path then truncates at the true first-bad event.
        groupDao.clearEvents(groupId)
        events = fetchFresh(0)
        if (events.isEmpty()) error("Group has no history.")
        membership = verifyAll(events)
    }
    val verifiedEvents = if (membership.degraded) events.filter { it.seq < (membership.badSeq ?: Long.MAX_VALUE) } else events
    persistGroupCache(group, membership, verifiedEvents)
    membership
}

private fun WhisperGroupEventEntity.toModel() = WhisperGroupEvent(
    id = id,
    groupId = groupId,
    seq = seq,
    epoch = epoch,
    type = WhisperGroupEventType.of(type) ?: error("unknown event type $type"),
    actor = actor,
    payloadJson = payload,
    adminSig = adminSig,
)

suspend fun WhisperRepository.syncGroups(): Result<List<WhisperGroupMembership>> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val myRows = db.from("whisper_group_members").select { filter { eq("user_id", me) } }
        .decodeList<GroupMemberRow>()
    // Per-group isolation: one poisoned or unreachable group must never hide
    // the healthy ones (a total failure still surfaces when nothing synced).
    val ok = mutableListOf<WhisperGroupMembership>()
    var failures = 0
    myRows.forEach { row ->
        syncGroup(row.groupId)
            .onSuccess { ok.add(it) }
            .onFailure {
                failures++
                android.util.Log.w("WhisperGroups", "syncGroups: skipping ${row.groupId}: ${it.message}")
            }
    }
    if (ok.isEmpty() && failures > 0) error("Couldn't sync your groups. Check your connection and retry.")
    ok
}

private suspend fun WhisperRepository.persistGroupCache(
    group: GroupRow,
    membership: WhisperGroupMembership,
    events: List<WhisperGroupEvent>,
) {
    val now = System.currentTimeMillis()
    groupDao.upsertGroup(
        WhisperGroupEntity(
            id = group.id,
            name = membership.name.ifBlank { group.name },
            epoch = membership.epoch,
            createdBy = group.createdBy,
            updatedAtMs = now,
        ),
    )
    groupDao.clearMembers(group.id)
    groupDao.upsertMembers(
        membership.members.map { (uid, role) ->
            WhisperGroupMemberEntity(
                groupId = group.id,
                memberId = uid,
                role = role.wire,
                invitedBy = null,
            )
        },
    )
    groupDao.insertEvents(
        events.map {
            WhisperGroupEventEntity(
                id = it.id,
                groupId = it.groupId,
                seq = it.seq,
                epoch = it.epoch,
                type = it.type.wire,
                actor = it.actor,
                payload = it.payloadJson,
                adminSig = it.adminSig,
                createdAtMs = now,
            )
        },
    )
}

/** Cached group list for the Chats tab (offline-safe; sync fills it). */
suspend fun WhisperRepository.cachedGroups(): List<WhisperGroupEntity> = groupDao.allGroups()

suspend fun WhisperRepository.cachedGroupMembers(groupId: String): List<WhisperGroupMemberEntity> =
    groupDao.members(groupId)

/** Creates a group: row + own admin row + persistent INVITEs (join starts membership). */
suspend fun WhisperRepository.createGroup(
    name: String,
    memberIds: List<String>,
    inviteAdminsOnly: Boolean,
): Result<String> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val cleanName = name.trim().take(WhisperGroupsConfig.MAX_GROUP_NAME_CHARS)
    require(cleanName.isNotBlank()) { "Group name is required." }
    val others = memberIds.filter { it.isNotBlank() && it != me }.distinct()
    require(others.isNotEmpty()) { "Add at least one friend." }
    require(1 + others.size <= WhisperGroupsConfig.MAX_MEMBERS) {
        "Groups hold ${WhisperGroupsConfig.MAX_MEMBERS} members max."
    }
    // Friends-only adds: every invitee must be an accepted friend.
    val friendIds = getFriends().getOrThrow().map { it.id }.toSet()
    val strangers = others.filter { it !in friendIds }
    require(strangers.isEmpty()) { "Only friends can be added to a group." }
    val groupId = java.util.UUID.randomUUID().toString()
    db.from("whisper_groups").insert(GroupRowInsert(id = groupId, name = cleanName, epoch = 0, createdBy = me))
    try {
        db.from("whisper_group_members").insert(GroupMemberRow(groupId = groupId, userId = me, role = "admin"))
    } catch (e: Throwable) {
        // Bootstrap failed: remove the bare group row so no orphan survives.
        runCatching { db.from("whisper_groups").delete { filter { eq("id", groupId) } } }
        throw e
    }
    val payload = groupJson.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        kotlinx.serialization.json.buildJsonObject {
            put("name", cleanName)
            put("invite", if (inviteAdminsOnly) "admins" else "all")
        },
    )
    try {
        publishGroupEvent(
            groupId = groupId,
            seq = 1,
            epoch = 0,
            type = WhisperGroupEventType.CREATE,
            actor = me,
            payload = payload,
        ).getOrThrow()
    } catch (e: Throwable) {
        // No history: tear the whole stub down (row + admin row).
        runCatching { db.from("whisper_group_members").delete { filter { eq("group_id", groupId); eq("user_id", me) } } }
        runCatching { db.from("whisper_groups").delete { filter { eq("id", groupId) } } }
        throw e
    }
    var seq = 2L
    val attempted = mutableListOf<String>()
    try {
        others.forEach { uid ->
            attempted.add(uid)
            db.from("whisper_group_invites").insert(
                GroupInviteInsert(groupId = groupId, userId = uid, invitedBy = me, clientVersionCode = groupClientVersion()),
            )
            publishGroupEvent(groupId, seq, 0, WhisperGroupEventType.INVITE, me, "\"$uid\"").getOrThrow()
            seq += 1
        }
    } catch (e: Throwable) {
        // Partial fan-out: withdraw every invite row this loop touched
        // (completed AND in-flight — rows land before their events) so nobody
        // holds a phantom invite card the log never recorded.
        attempted.forEach { uid ->
            runCatching { db.from("whisper_group_invites").delete { filter { eq("group_id", groupId); eq("user_id", uid) } } }
        }
        throw e
    }
    syncGroup(groupId).getOrThrow()
    groupId
}

private suspend fun WhisperRepository.publishGroupEvent(
    groupId: String,
    seq: Long,
    epoch: Long,
    type: WhisperGroupEventType,
    actor: String,
    payload: String,
): Result<Unit> = runCatching {
    // Two admins acting at once race on nextGroupSeqEpoch: the loser hits the
    // unique(group_id, seq) constraint (23505). Re-read the head, re-sign
    // (the seq is inside the signed bytes) and retry — 3 attempts, then surface.
    var attemptSeq = seq
    var lastError: Throwable? = null
    repeat(3) { attempt ->
        val sig = crypto.signProtocol(groupEventSignBytes(groupId, attemptSeq, epoch, type, actor, payload))
            ?: error("Could not sign the group event.")
        val result = runCatching {
            db.from("whisper_group_events").insert(
                GroupEventInsert(
                    groupId = groupId,
                    seq = attemptSeq,
                    epoch = epoch,
                    type = type.wire,
                    actor = actor,
                    payload = payload,
                    adminSig = sig,
                    clientVersionCode = groupClientVersion(),
                ),
            )
        }
        if (result.isSuccess) return@runCatching
        val err = result.exceptionOrNull()
        if (!isGroupSeqCollision(err)) throw err ?: error("Could not publish the group event.")
        lastError = err
        if (attempt < 2) {
            kotlinx.coroutines.delay(150L * (attempt + 1))
            attemptSeq = nextGroupSeqEpoch(groupId).first
        }
    }
    throw lastError ?: error("Could not publish the group event.")
}

/** True when the failure is the unique(group_id, seq) race (Postgres 23505). */
private fun isGroupSeqCollision(e: Throwable?): Boolean {
    var cur = e
    while (cur != null) {
        val msg = cur.message.orEmpty()
        if (msg.contains("23505") || msg.contains("duplicate key", ignoreCase = true)) return true
        cur = cur.cause
    }
    return false
}

/**
 * Maps fail-closed transport errors to string resources. Raw server/client
 * English ("stale epoch", "42501") must never reach the chat UI untranslated.
 * Returns null when no mapping applies — callers fall back to the raw message.
 */
@androidx.annotation.StringRes
fun mapGroupError(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val msg = raw.lowercase()
    return when {
        "group full" in msg || "12 max" in msg ->
            com.frerox.toolz.R.string.st_Whisper_Groups_ErrFull
        "quota exceeded" in msg ->
            com.frerox.toolz.R.string.st_Whisper_Groups_ErrQuota
        "stale epoch" in msg ->
            com.frerox.toolz.R.string.st_Whisper_Groups_ErrStale
        "no invite" in msg ->
            com.frerox.toolz.R.string.st_Whisper_Groups_ErrNoInvite
        "no longer a member" in msg || "not a member" in msg || "not_member" in msg ->
            com.frerox.toolz.R.string.st_Whisper_Groups_ErrNotMember
        "could not reach" in msg ->
            com.frerox.toolz.R.string.st_Whisper_Groups_ErrUnreachable
        "couldn't sync your groups" in msg ->
            com.frerox.toolz.R.string.st_Whisper_Groups_ErrSync
        "group_send_queued" in msg ->
            com.frerox.toolz.R.string.st_Whisper_Groups_ErrQueued
        else -> null
    }
}

/** Resolves a group error to display text (mapped string or the raw message). */
fun groupErrorText(context: android.content.Context, e: Throwable?): String {
    val raw = e?.message
    val res = mapGroupError(raw)
    return if (res != null) context.getString(res) else raw ?: context.getString(
        com.frerox.toolz.R.string.st_Whisper_Groups_ErrSync,
    )
}

private suspend fun WhisperRepository.nextGroupSeqEpoch(groupId: String): Pair<Long, Long> {
    val rows = db.from("whisper_group_events").select { filter { eq("group_id", groupId) } }
        .decodeList<GroupEventRow>()
    val maxSeq = rows.maxOfOrNull { it.seq } ?: 0L
    val group = db.from("whisper_groups").select { filter { eq("id", groupId) } }
        .decodeSingleOrNull<GroupRow>()
    return (maxSeq + 1) to (group?.epoch ?: 0L)
}

suspend fun WhisperRepository.inviteGroupMember(groupId: String, userId: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    require(userId.isNotBlank() && userId != me) { "Choose another user to add." }
    val m = syncGroup(groupId).getOrThrow()
    require(m.canInvite(me)) { "Only admins can invite to this group." }
    require(!m.isMember(userId)) { "Already a member." }
    require(userId !in m.pendingInvites) { "Already invited." }
    require(m.members.size + m.pendingInvites.size < WhisperGroupsConfig.MAX_MEMBERS) { "Group is full." }
    val status = getFriendshipStatus(userId).getOrNull()?.first
    require(status == FriendStatus.ACCEPTED) { "Only friends can be added to a group." }
    val (seq, epoch) = nextGroupSeqEpoch(groupId)
    db.from("whisper_group_invites").insert(
        GroupInviteInsert(groupId = groupId, userId = userId, invitedBy = me, clientVersionCode = groupClientVersion()),
    )
    publishGroupEvent(groupId, seq, epoch, WhisperGroupEventType.INVITE, me, "\"$userId\"").getOrThrow()
    syncGroup(groupId).getOrThrow()
}

/** Accepts my invite: JOIN event first, then the member row (event-first like
 * every other op — a failed event leaves zero state behind, so a retry never
 * wedges on a member row the log doesn't know). */
suspend fun WhisperRepository.joinGroup(groupId: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = fetchVerifiedMembership(groupId).getOrThrow()
    if (m.isMember(me)) {
        // Heal path: the log already carries my JOIN (a previous attempt
        // published the event but the member row never landed). Restore the
        // row idempotently — a PK conflict just means it is already there.
        runCatching {
            db.from("whisper_group_members").insert(
                GroupMemberRow(groupId = groupId, userId = me, role = "member"),
            )
        }
        syncGroup(groupId).getOrThrow()
        return@runCatching
    }
    require(me in m.pendingInvites) { "No invite for this group." }
    require(m.members.size < WhisperGroupsConfig.MAX_MEMBERS) { "Group is full." }
    val (seq, epoch) = nextGroupSeqEpoch(groupId)
    // Event first: the invite row is still live, so the require-invite
    // trigger on the member insert below is satisfied either way.
    publishGroupEvent(groupId, seq, epoch + 1, WhisperGroupEventType.JOIN, me, "\"\"").getOrThrow()
    db.from("whisper_group_members").insert(
        GroupMemberRow(groupId = groupId, userId = me, role = "member"),
    )
    syncGroup(groupId).getOrThrow()
}

/** Refuses my invite: DECLINE event for the owner's eyes + invite row delete. */
suspend fun WhisperRepository.declineGroupInvite(groupId: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val invite = db.from("whisper_group_invites").select {
        filter { eq("group_id", groupId); eq("user_id", me) }
    }.decodeSingleOrNull<GroupInviteRow>() ?: error("No invite for this group.")
    check(invite.userId == me) { "No invite for this group." }
    val (seq, epoch) = nextGroupSeqEpoch(groupId)
    publishGroupEvent(groupId, seq, epoch, WhisperGroupEventType.DECLINE, me, "\"\"").getOrThrow()
    db.from("whisper_group_invites").delete { filter { eq("group_id", groupId); eq("user_id", me) } }
}

/** Owner/admin cancels someone's invite: row delete + CANCEL event for the log. */
suspend fun WhisperRepository.cancelGroupInvite(groupId: String, userId: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = syncGroup(groupId).getOrThrow()
    require(m.isAdmin(me)) { "Only admins can cancel invites." }
    require(userId in m.pendingInvites) { "No invite to cancel." }
    db.from("whisper_group_invites").delete { filter { eq("group_id", groupId); eq("user_id", userId) } }
    val (seq, epoch) = nextGroupSeqEpoch(groupId)
    publishGroupEvent(groupId, seq, epoch, WhisperGroupEventType.CANCEL, me, "\"$userId\"").getOrThrow()
    syncGroup(groupId).getOrThrow()
}

/** My outstanding invites with group names (invite-card source, persistent). */
suspend fun WhisperRepository.myGroupInvites(): Result<List<GroupInviteInfo>> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val rows = db.from("whisper_group_invites").select { filter { eq("user_id", me) } }
        .decodeList<GroupInviteRow>()
    val names = getFriends().getOrNull().orEmpty().associate { it.id to it.effectiveName }
    rows.mapNotNull { row ->
        val group = db.from("whisper_groups").select { filter { eq("id", row.groupId) } }
            .decodeSingleOrNull<GroupRow>() ?: return@mapNotNull null
        GroupInviteInfo(
            groupId = row.groupId,
            groupName = group.name.ifBlank { "Group" },
            invitedBy = row.invitedBy,
            invitedByName = names[row.invitedBy] ?: "Someone",
            createdAt = row.createdAt ?: "",
        )
    }
}

suspend fun WhisperRepository.removeGroupMember(groupId: String, userId: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = syncGroup(groupId).getOrThrow()
    require(m.isAdmin(me)) { "Only admins can remove members." }
    require(m.isMember(userId)) { "Not a member." }
    require(!(m.members[userId] == WhisperGroupRole.ADMIN && m.admins.size <= 1)) {
        "Cannot remove the last admin."
    }
    val (seq, epoch) = nextGroupSeqEpoch(groupId)
    publishGroupEvent(groupId, seq, epoch + 1, WhisperGroupEventType.REMOVE, me, "\"$userId\"").getOrThrow()
    db.from("whisper_group_members").delete { filter { eq("group_id", groupId); eq("user_id", userId) } }
    syncGroup(groupId).getOrThrow()
}

suspend fun WhisperRepository.promoteGroupMember(groupId: String, userId: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = syncGroup(groupId).getOrThrow()
    require(m.isAdmin(me)) { "Only admins can promote members." }
    require(m.members[userId] == WhisperGroupRole.MEMBER) { "Not a promotable member." }
    val (seq, epoch) = nextGroupSeqEpoch(groupId)
    publishGroupEvent(groupId, seq, epoch + 1, WhisperGroupEventType.PROMOTE, me, "\"$userId\"").getOrThrow()
    db.from("whisper_group_members").update({ set("role", "admin") }) {
        filter { eq("group_id", groupId); eq("user_id", userId) }
    }
    syncGroup(groupId).getOrThrow()
}

suspend fun WhisperRepository.renameGroup(groupId: String, name: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = syncGroup(groupId).getOrThrow()
    require(m.isAdmin(me)) { "Only admins can rename the group." }
    val clean = name.trim().take(WhisperGroupsConfig.MAX_GROUP_NAME_CHARS)
    require(clean.isNotBlank()) { "Group name is required." }
    val (seq, epoch) = nextGroupSeqEpoch(groupId)
    publishGroupEvent(groupId, seq, epoch + 1, WhisperGroupEventType.RENAME, me, "\"$clean\"").getOrThrow()
    db.from("whisper_groups").update({ set("name", clean) }) { filter { eq("id", groupId) } }
    syncGroup(groupId).getOrThrow()
}

suspend fun WhisperRepository.leaveGroup(groupId: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = syncGroup(groupId).getOrThrow()
    require(m.isMember(me)) { "You are not a member." }
    require(!(m.isAdmin(me) && m.admins.size <= 1)) { "The last admin cannot leave — promote someone first." }
    val (seq, epoch) = nextGroupSeqEpoch(groupId)
    publishGroupEvent(groupId, seq, epoch + 1, WhisperGroupEventType.LEAVE, me, "\"\"").getOrThrow()
    db.from("whisper_group_members").delete { filter { eq("group_id", groupId); eq("user_id", me) } }
    groupDao.deleteGroup(groupId)
    groupDao.clearMembers(groupId)
    groupDao.clearEvents(groupId)
}

/**
 * Deletes the whole group. Only the LAST remaining member may do this (the
 * server policy enforces zero other members) — the escape hatch for a creator
 * whose invites all declined: the last admin cannot leave, so without disband
 * the empty group would be stranded forever. Everything cascades server-side.
 */
suspend fun WhisperRepository.disbandGroup(groupId: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = syncGroup(groupId).getOrThrow()
    require(m.isMember(me)) { "You are not a member." }
    require(m.members.size <= 1) { "Only the last remaining member can disband the group." }
    db.from("whisper_groups").delete { filter { eq("id", groupId) } }
    groupDao.deleteGroup(groupId)
    groupDao.clearMembers(groupId)
    groupDao.clearEvents(groupId)
}

/** Sends one message to every current member (fail-closed: all legs or nothing). */
suspend fun WhisperRepository.sendGroupMessage(groupId: String, plaintext: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val clean = plaintext.trim()
    require(clean.isNotBlank()) { "Message is empty." }
    require(clean.length <= WhisperRepository.MAX_MESSAGE_CHARS) { "Message is too long." }
    sendGroupContent(groupId, WhisperGroupContent(t = clean)).getOrThrow()
}

/**
 * Sends structured content (text/image/reply/poll/vote/mentions) to every
 * current member. The frame carries `wg1:<json>`; legacy bare-text readers
 * still see `t`.
 */
suspend fun WhisperRepository.sendGroupContent(groupId: String, content: WhisperGroupContent): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = syncGroup(groupId).getOrThrow()
    require(m.isMember(me)) { "You are no longer a member of this group." }
    sealAndPublishContent(groupId, m, me, buildGroupContentBody(content)).getOrThrow()
}

/**
 * Sends an image: compressed + AES-sealed + uploaded ONCE, then only the
 * short key reference is fanned out per member (same economics as the group
 * picture — never N copies of the bytes).
 */
suspend fun WhisperRepository.sendGroupImage(
    groupId: String,
    imageBytes: ByteArray,
    mimeType: String,
    caption: String = "",
): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    require(mimeType in setOf("image/jpeg", "image/png", "image/webp")) {
        "Whisper supports JPEG, PNG, and WebP images."
    }
    val m = syncGroup(groupId).getOrThrow()
    require(m.isMember(me)) { "You are no longer a member of this group." }
    // Chat photos keep aspect ratio (the picture path square-crops for avatars).
    val (compressed, outMime) = compressGroupChatImage(imageBytes, mimeType)
    require(compressed.isNotEmpty()) { "Could not read that image." }
    val (sealed, key) = newGroupPictureSeal(compressed)
    val png = WhisperImageCipherTransport.encode(sealed)
    val (url, att) = encryptedImageHost.upload(png, "gmsg-$groupId-${System.currentTimeMillis()}", null).getOrThrow()
    require(url.isNotBlank()) { "Picture upload failed." }
    val ref = GroupImageRef(
        url = url,
        key = java.util.Base64.getEncoder().encodeToString(key),
        att = att,
        mime = outMime,
    )
    sendGroupContent(groupId, WhisperGroupContent(t = caption.trim(), img = ref)).getOrThrow()
}

/** Seals one frame per member and publishes the row (shared by text/image/poll paths). */
private suspend fun WhisperRepository.sealAndPublishContent(
    groupId: String,
    m: WhisperGroupMembership,
    me: String,
    frameBody: String,
): Result<Unit> = runCatching {
    val targets = m.members.keys.sorted()
    val frames = mutableMapOf<String, String>()
    val failed = mutableListOf<String>()
    for (memberId in targets) {
        val frame = buildGroupFrameBody(groupId, m.epoch, System.currentTimeMillis(), frameBody)
        val sealed = sealWithRatchet(me, memberId, frame)
        if (sealed == null) {
            failed.add(memberId)
        } else {
            frames[memberId] = sealed.first
        }
    }
    require(failed.isEmpty()) {
        "Could not reach ${failed.size} member(s) — message not sent. Retry when they're reachable."
    }
    val clientId = java.util.UUID.randomUUID().toString()
    val content = encodeGroupRow(
        WhisperGroupRowContent(
            v = WhisperGroupsConfig.ENVELOPE_VERSION,
            g = groupId,
            e = m.epoch,
            s = System.currentTimeMillis(),
            senderId = me,
            frames = frames,
        ),
    )
    val insertResult = runCatching {
        db.from("whisper_group_messages").insert(
            GroupMessageInsert(
                groupId = groupId,
                senderId = me,
                clientId = clientId,
                content = content,
                contentIv = groupMessageIv(m.epoch),
                epoch = m.epoch,
                clientVersionCode = groupClientVersion(),
            ),
        )
    }
    if (insertResult.isSuccess) return@runCatching
    val insertErr = insertResult.exceptionOrNull()
    // Permanent rejections (quota, stale epoch, full, non-member) will fail
    // the drain too — surface them, don't queue. Anything else (offline,
    // timeouts) queues the sealed bundle: frames are already sealed per
    // member, so the drain only replays the row insert (dup client_id = done).
    val mapped = mapGroupError(insertErr?.message)
    if (mapped != null) throw insertErr ?: error("Message not sent.")
    outgoingQueue.enqueue(
        WhisperQueuedMessage(
            clientId = clientId,
            senderId = me,
            receiverId = "",
            encryptedContent = content,
            contentIv = groupMessageIv(m.epoch),
            replyToId = null,
            createdAt = java.time.Instant.now().toString(),
            attempts = 0,
            groupId = groupId,
        ),
    )
    deliveryScheduler.scheduleNow()
    error("group_send_queued")
}

/** Reads the chat: opens my frame of every row I belonged to at its epoch. */
suspend fun WhisperRepository.fetchGroupMessages(
    groupId: String,
    limit: Int = 50,
    /** Pagination cursor: only rows strictly older than this created_at. */
    before: String? = null,
): Result<List<WhisperGroupChatMessage>> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = syncGroup(groupId).getOrThrow()
    require(m.isMember(me)) { "You are no longer a member of this group." }
    val rows = db.from("whisper_group_messages").select {
        filter {
            eq("group_id", groupId)
            if (before != null) lte("created_at", before)
        }
        // Newest-first at the server so the LIMIT keeps the live head, never a
        // stale arbitrary slice; the client re-sorts ascending below.
        order("created_at", Order.DESCENDING)
        limit(limit.toLong())
    }.decodeList<GroupMessageRow>()
    val names = getFriends().getOrNull().orEmpty().associate { it.id to it.effectiveName }
    rows.sortedWith(compareBy({ it.createdAt ?: "" }, { it.id }))
        .distinctBy { it.id }
        .mapNotNull { row ->
            if (deletedStore.isMessageDeleted(groupTombstoneKey(row.clientId))) return@mapNotNull null
            if (!m.isMemberAt(row.senderId, row.epoch) || !m.isMemberAt(me, row.epoch)) return@mapNotNull null
            val body = decodeGroupRow(row.content) ?: return@mapNotNull null
            if (body.g != groupId || body.e != row.epoch) return@mapNotNull null
            val frame = body.frames[me] ?: return@mapNotNull null
            val inner = openV3Frame(frame, row.senderId, row.senderId, me) ?: return@mapNotNull null
            val opened = openGroupFrameBody(groupId, row.epoch, inner) ?: return@mapNotNull null
            val content = parseGroupContentBody(opened)
            WhisperGroupChatMessage(
                id = row.id,
                groupId = groupId,
                clientId = row.clientId,
                senderId = row.senderId,
                senderName = if (row.senderId == me) "You" else names[row.senderId] ?: "Member",
                body = content.t,
                createdAt = row.createdAt ?: "",
                epoch = row.epoch,
                mine = row.senderId == me,
                image = content.img,
                reply = content.reply,
                poll = content.poll,
                vote = content.vote,
                mentions = content.mentions,
            )
        }
}

/** Local tombstone key for a group message row (delete-for-me). */
fun groupTombstoneKey(clientId: String): String = "gmsg:$clientId"

/** Hides one group message on this device only (delete-for-me). */
suspend fun WhisperRepository.deleteGroupMessageForMe(clientId: String) {
    requireGroupsEnabled()
    if (clientId.isBlank()) return
    deletedStore.markMessageDeletedSuspend(groupTombstoneKey(clientId))
}

/** Sender-side wipe: deletes the row for everyone (RLS is sender-only). */
suspend fun WhisperRepository.deleteGroupMessageForEveryone(groupId: String, rowId: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val rows = db.from("whisper_group_messages").select { filter { eq("id", rowId) } }
        .decodeList<GroupMessageRow>()
    val row = rows.firstOrNull() ?: error("Message not found.")
    require(row.senderId == me) { "Only the sender can delete for everyone." }
    require(row.groupId == groupId) { "Message is not in this group." }
    db.from("whisper_group_messages").delete { filter { eq("id", rowId) } }
}

// ── Seen receipts ───────────────────────────────────────────────────────

@Serializable
internal data class GroupReceiptRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
internal data class GroupReceiptInsert(
    @SerialName("group_id") val groupId: String,
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
internal data class GroupTypingRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Marks client_ids as seen by me (fire-and-forget from the chat screen). */
suspend fun WhisperRepository.markGroupSeen(groupId: String, clientIds: List<String>): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    if (groupId.isBlank() || clientIds.isEmpty()) return@runCatching
    // Upserts are idempotent (PK covers group+message+user); failures are
    // best-effort — receipts are presence metadata, never load-bearing.
    clientIds.distinct().take(100).forEach { cid ->
        runCatching {
            db.from("whisper_group_receipts").upsert(
                GroupReceiptInsert(groupId = groupId, messageId = cid, userId = me),
            )
        }
    }
}

/** Seen-by map for a group (message client_id → user ids), newest-first capped. */
suspend fun WhisperRepository.fetchGroupReceipts(groupId: String): Result<Map<String, List<String>>> = runCatching {
    requireGroupsEnabled()
    requireMe()
    if (groupId.isBlank()) return@runCatching emptyMap()
    val rows = db.from("whisper_group_receipts").select {
        filter { eq("group_id", groupId) }
        order("at", Order.DESCENDING)
        limit(2000)
    }.decodeList<GroupReceiptRow>()
    rows.groupBy({ it.messageId }, { it.userId })
}

// ── Typing signals ──────────────────────────────────────────────────────

/** Freshness window: typing rows older than this read as gone. */
const val GROUP_TYPING_FRESH_MS = 8_000L

/** Refreshes my typing signal (call throttled from the UI, ~3s). */
suspend fun WhisperRepository.sendGroupTyping(groupId: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    if (groupId.isBlank()) return@runCatching
    runCatching {
        // Plain upsert: the (group_id, user_id) PK is the conflict target
        // (same idiom as the 1:1 typing lane).
        db.from("whisper_group_typing").upsert(
            mapOf("group_id" to groupId, "user_id" to me),
        )
    }
}

/** Clears my typing signal (on send, on leave, on dispose). */
suspend fun WhisperRepository.clearGroupTyping(groupId: String) {
    runCatching {
        requireGroupsEnabled()
        val me = myId
        if (groupId.isBlank() || me.isBlank()) return
        db.from("whisper_group_typing").delete {
            filter { eq("group_id", groupId); eq("user_id", me) }
        }
    }
}

/** Live typing user ids for one group (stale rows pruned client-side). */
fun WhisperRepository.observeGroupTyping(groupId: String): kotlinx.coroutines.flow.Flow<List<String>> =
    kotlinx.coroutines.flow.callbackFlow {
        requireGroupsEnabled()
        if (groupId.isBlank()) { close(); return@callbackFlow }
        val me = myId
        val channel = groupChannel("grouppg_typing_$groupId")
        suspend fun current(): List<String> {
            val cutoff = System.currentTimeMillis() - GROUP_TYPING_FRESH_MS
            return runCatching {
                db.from("whisper_group_typing").select { filter { eq("group_id", groupId) } }
                    .decodeList<GroupTypingRow>()
            }.getOrDefault(emptyList())
                .filter { it.userId != me && it.userId.isNotBlank() }
                .filter { row ->
                    val ts = row.updatedAt?.let {
                        runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
                    } ?: 0L
                    ts >= cutoff
                }
                .map { it.userId }
                .distinct()
        }
        // Emit the current set immediately so late subscribers see typists.
        launch { trySend(current()) }
        val changes = channel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
            table = "whisper_group_typing"
            filter("group_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, groupId)
        }
        val job = launch {
            changes.collect {
                // Debounce: re-read the table (one cheap select per change burst).
                kotlinx.coroutines.delay(300)
                trySend(current())
            }
        }
        awaitClose {
            job.cancel()
            launch { runCatchingCE { realtime.removeChannel(channel) } }
        }
    }

// ── Lazy group-image open (disk-cached, per-message key) ────────────────

/**
 * Downloads + decrypts one group image attachment for me (disk-cached by
 * url+key fingerprint so scrolling never re-decrypts). Null when the frame
 * isn't mine or the bytes are bad.
 */
suspend fun WhisperRepository.openGroupImage(ref: GroupImageRef, cacheId: String): Result<ByteArray?> = runCatching {
    requireGroupsEnabled()
    requireMe()
    val fp = runCatching {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(ref.url.toByteArray())
        md.update(ref.key.toByteArray())
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault(ref.url)
    imageDiskCache.get(cacheId, fp)?.let { return@runCatching it }
    val key = runCatching { java.util.Base64.getDecoder().decode(ref.key) }.getOrNull()
        ?: error("Bad image key.")
    val png = encryptedImageHost.download(ref.url).getOrThrow()
    val sealed = WhisperImageCipherTransport.decode(png) ?: error("Bad image data.")
    val plain = openGroupPictureBytes(sealed, key) ?: error("Could not decrypt the image.")
    runCatching { imageDiskCache.put(cacheId, fp, plain) }
    plain
}

// ── Group picture (encrypted, same stack as chat images) ────────────────
// Pipeline: EXIF-strip + 1920 + JPEG82 (shared preprocess) → fresh AES-256-GCM
// key → PNG-wrap (image-only host) → EncryptedBlobHost upload → key sealed per
// member into a signed PICTURE event. URL is server-visible metadata (like the
// member list); bytes + key stay opaque. Picture changes do NOT bump the epoch.

/** Admin sets/replaces the picture. Fail-closed: all key frames or nothing. */
suspend fun WhisperRepository.setGroupPicture(
    groupId: String,
    imageBytes: ByteArray,
    mimeType: String,
): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = syncGroup(groupId).getOrThrow()
    require(m.isAdmin(me)) { "Only admins can change the picture." }
    val (compressed, _) = compressImageForGroupUpload(imageBytes, mimeType)
    require(compressed.isNotEmpty()) { "Could not read that image." }
    val published = runCatching {
        publishPictureFrames(groupId, me, m, compressed).getOrThrow()
    }
    if (published.isSuccess) {
        syncGroup(groupId).getOrThrow()
        return@runCatching
    }
    // Offline/mid-flight failure: queue the ORIGINAL bytes — the drain
    // re-runs compress/seal/upload/publish from scratch.
    val b64 = java.util.Base64.getEncoder().encodeToString(imageBytes)
    outgoingQueue.enqueue(
        WhisperQueuedMessage(
            clientId = "gpic:$groupId:${System.currentTimeMillis()}",
            senderId = me,
            receiverId = "",
            encryptedContent = "$mimeType\n$b64",
            contentIv = GROUP_PICTURE_OUTBOX_IV,
            replyToId = null,
            createdAt = java.time.Instant.now().toString(),
            attempts = 0,
            groupId = groupId,
        ),
    )
    deliveryScheduler.scheduleNow()
    android.util.Log.w("WhisperGroups", "setGroupPicture queued for $groupId: ${published.exceptionOrNull()?.message}")
    error("group_send_queued")
}

/** Seal-per-member + upload + signed PICTURE event (shared by set + drain). */
private suspend fun WhisperRepository.publishPictureFrames(
    groupId: String,
    me: String,
    m: WhisperGroupMembership,
    compressed: ByteArray,
): Result<Unit> = runCatching {
    val (sealed, key) = newGroupPictureSeal(compressed)
    val png = WhisperImageCipherTransport.encode(sealed)
    val (url, _) = encryptedImageHost.upload(png, "group-$groupId", null).getOrThrow()
    require(url.isNotBlank()) { "Picture upload failed." }
    val keyB64 = java.util.Base64.getEncoder().encodeToString(key)
    val frames = mutableMapOf<String, String>()
    val failed = mutableListOf<String>()
    for (memberId in m.members.keys.sorted()) {
        val frame = buildGroupFrameBody(groupId, m.epoch, System.currentTimeMillis(), keyB64)
        val out = sealWithRatchet(me, memberId, frame)
        if (out == null) failed.add(memberId) else frames[memberId] = out.first
    }
    require(failed.isEmpty()) {
        "Could not reach ${failed.size} member(s) — picture not changed. Retry when they're reachable."
    }
    val (seq, epoch) = nextGroupSeqEpoch(groupId)
    publishGroupEvent(
        groupId, seq, epoch, WhisperGroupEventType.PICTURE, me,
        buildGroupPicturePayload(url, frames),
    ).getOrThrow()
}

/** Opens the current picture for me (null = none set / not for me). */
suspend fun WhisperRepository.openGroupPicture(groupId: String): Result<ByteArray?> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = syncGroup(groupId).getOrThrow()
    require(m.isMember(me)) { "You are no longer a member of this group." }
    val rows = db.from("whisper_group_events").select {
        filter { eq("group_id", groupId); eq("type", "picture") }
    }.decodeList<GroupEventRow>()
    val latest = rows.maxByOrNull { it.seq } ?: return@runCatching null
    val (url, frames) = parseGroupPicturePayload(latest.payload) ?: error("Bad picture data.")
    val frame = frames[me] ?: return@runCatching null
    val inner = openV3Frame(frame, latest.actor, latest.actor, me) ?: error("Could not open the picture key.")
    val keyB64 = openGroupFrameBody(groupId, latest.epoch, inner) ?: error("Could not open the picture key.")
    val key = runCatching { java.util.Base64.getDecoder().decode(keyB64) }.getOrNull()
        ?: error("Bad picture key.")
    val png = encryptedImageHost.download(url).getOrThrow()
    val sealed = WhisperImageCipherTransport.decode(png) ?: error("Bad picture data.")
    openGroupPictureBytes(sealed, key) ?: error("Could not decrypt the picture.")
}

// ── Group outbox drain (offline sends + pictures) ───────────────────────
// The 1:1 lane (flushOutgoingMessages) skips groupId != null rows; this lane
// replays them. Message rows are ciphertext-only replays (frames sealed at
// enqueue time; dup client_id = already delivered). Picture rows re-run the
// full pipeline from the persisted original bytes. Drained on hub + chat
// loads (app-open driven); the worker lane stays 1:1-only by design.

private val groupFlushMutex = kotlinx.coroutines.sync.Mutex()

/** content_iv for group message rows; carries the row epoch for the drain. */
internal fun groupMessageIv(epoch: Long): String = "v3-group:$epoch"

/** Test-visible: parses the epoch back out of [groupMessageIv]. */
internal fun groupMessageIvEpoch(iv: String): Long? =
    iv.removePrefix("v3-group:").toLongOrNull()?.takeIf { iv.startsWith("v3-group:") }

/** Marker content_iv for queued group-picture retries (content = mime + \n + base64). */
internal const val GROUP_PICTURE_OUTBOX_IV = "gpic"

/**
 * Replays my queued group rows; safe to call repeatedly (dup-safe by
 * client_id, attempts-capped like the 1:1 lane). Returns delivered count.
 */
suspend fun WhisperRepository.flushGroupOutbox(): Int {
    requireGroupsEnabled()
    if (myId.isBlank()) return 0
    return groupFlushMutex.withLock {
        var delivered = 0
        outgoingQueue.entries().filter { it.senderId == myId && it.groupId != null }.forEach { queued ->
            if (queued.attempts >= 8) {
                android.util.Log.w("WhisperGroups", "Dropping undeliverable queued group row after ${queued.attempts} attempts (clientId=${queued.clientId})")
                outgoingQueue.remove(queued.clientId)
                outgoingQueue.noteDropped(queued.clientId)
                return@forEach
            }
            val result = runCatching {
                if (queued.contentIv == GROUP_PICTURE_OUTBOX_IV) {
                    drainGroupPicture(queued.groupId!!, queued.encryptedContent).getOrThrow()
                } else {
                    val epoch = groupMessageIvEpoch(queued.contentIv)
                        ?: error("Bad queued group row.")
                    db.from("whisper_group_messages").insert(
                        GroupMessageInsert(
                            groupId = queued.groupId!!,
                            senderId = queued.senderId,
                            clientId = queued.clientId,
                            content = queued.encryptedContent,
                            contentIv = queued.contentIv,
                            epoch = epoch,
                            clientVersionCode = groupClientVersion(),
                        ),
                    )
                }
            }
            if (result.isSuccess || isGroupDupKey(result.exceptionOrNull())) {
                outgoingQueue.remove(queued.clientId)
                delivered++
            } else {
                outgoingQueue.replace(queued.copy(attempts = queued.attempts + 1))
            }
        }
        delivered
    }
}

/** Re-runs a queued picture set from persisted original bytes. */
private suspend fun WhisperRepository.drainGroupPicture(groupId: String, packed: String): Result<Unit> = runCatching {
    val split = packed.indexOf('\n')
    require(split > 0) { "Bad queued group picture." }
    val mime = packed.substring(0, split)
    val bytes = runCatching {
        java.util.Base64.getDecoder().decode(packed.substring(split + 1))
    }.getOrNull() ?: error("Bad queued group picture.")
    setGroupPicture(groupId, bytes, mime).getOrThrow()
}

/** True on unique(client_id) replays (a prior attempt delivered, response lost). */
private fun isGroupDupKey(e: Throwable?): Boolean {
    var cur = e
    while (cur != null) {
        val msg = cur.message.orEmpty()
        if (msg.contains("23505") || msg.contains("duplicate key", ignoreCase = true)) return true
        cur = cur.cause
    }
    return false
}

// ── Realtime (Postgres Changes, same teardown discipline as 1:1) ───────

private suspend fun WhisperRepository.groupChannel(name: String): io.github.jan.supabase.realtime.RealtimeChannel {
    channelMutex.withLock {
        broadcastChannelCache[name]?.let { runCatchingCE { realtime.removeChannel(it) } }
        broadcastChannelCache.remove(name)
    }
    return supabase.channel(name)
}

/** Fires on every invite-row insert/delete visible to me (my invites only, via RLS). */
fun WhisperRepository.observeMyGroupInvites(): kotlinx.coroutines.flow.Flow<Unit> =
    kotlinx.coroutines.flow.callbackFlow {
        requireGroupsEnabled()
        val me = myId
        if (me.isBlank()) { close(); return@callbackFlow }
        val channel = groupChannel("grouppg_invites_$me")
        val changes = channel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
            table = "whisper_group_invites"
            filter("user_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, me)
        }
        val job = launch {
            changes.collect {
                trySend(Unit)
            }
        }
        awaitClose {
            job.cancel()
            launch { runCatchingCE { realtime.removeChannel(channel) } }
        }
    }

/** Fires with every new event row of one group (join/leave/decline/picture…). */
internal fun WhisperRepository.observeGroupLog(groupId: String): kotlinx.coroutines.flow.Flow<GroupEventRow> =
    kotlinx.coroutines.flow.callbackFlow {
        requireGroupsEnabled()
        if (groupId.isBlank()) { close(); return@callbackFlow }
        val channel = groupChannel("grouppg_log_$groupId")
        val changes = channel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
            table = "whisper_group_events"
            filter("group_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, groupId)
        }
        val job = launch {
            changes.collect { action ->
                val row = when (action) {
                    is io.github.jan.supabase.realtime.PostgresAction.Insert ->
                        runCatching { action.decodeRecord<GroupEventRow>() }.getOrNull()
                    else -> null
                }
                if (row != null) trySend(row)
            }
        }
        awaitClose {
            job.cancel()
            launch { runCatchingCE { realtime.removeChannel(channel) } }
        }
    }

/** Public ping for a new group message row (no ciphertext leaves the row). */
data class WhisperGroupMessagePing(
    val groupId: String,
    val senderId: String,
    val clientId: String,
)

/** Fires with every new message row of one group (foreground message pings). */
fun WhisperRepository.observeGroupMessagesLive(groupId: String): kotlinx.coroutines.flow.Flow<WhisperGroupMessagePing> =
    kotlinx.coroutines.flow.callbackFlow {
        requireGroupsEnabled()
        if (groupId.isBlank()) { close(); return@callbackFlow }
        val channel = groupChannel("grouppg_msg_$groupId")
        val changes = channel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
            table = "whisper_group_messages"
            filter("group_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, groupId)
        }
        val job = launch {
            changes.collect { action ->
                val row = when (action) {
                    is io.github.jan.supabase.realtime.PostgresAction.Insert ->
                        runCatching { action.decodeRecord<GroupMessageRow>() }.getOrNull()
                    else -> null
                }
                if (row != null) trySend(WhisperGroupMessagePing(groupId, row.senderId, row.clientId))
            }
        }
        awaitClose {
            job.cancel()
            launch { runCatchingCE { realtime.removeChannel(channel) } }
        }
    }

/**
 * Fires with the row id of every DELETED message row of one group
 * (sender-side wipe). Chat screens reload on this; the section invite/msg
 * ping lane ignores deletes (no notification for a withdrawal).
 */
fun WhisperRepository.observeGroupMessageDeletes(groupId: String): kotlinx.coroutines.flow.Flow<String> =
    kotlinx.coroutines.flow.callbackFlow {
        requireGroupsEnabled()
        if (groupId.isBlank()) { close(); return@callbackFlow }
        val channel = groupChannel("grouppg_msgdel_$groupId")
        val changes = channel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
            table = "whisper_group_messages"
            filter("group_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, groupId)
        }
        val job = launch {
            changes.collect { action ->
                // Deletes carry the old row (replica identity full); the id is
                // all the chat needs to drop the line on its next reload.
                val id = when (action) {
                    is io.github.jan.supabase.realtime.PostgresAction.Delete ->
                        runCatching { action.decodeOldRecord<GroupMessageRow>() }.getOrNull()?.id
                    else -> null
                }
                if (id != null) trySend(id)
            }
        }
        awaitClose {
            job.cancel()
            launch { runCatchingCE { realtime.removeChannel(channel) } }
        }
    }
