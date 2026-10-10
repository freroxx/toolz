/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

/**
 * Phase-2 GROUPS DOMAIN of [WhisperRepository], following the
 * WhisperRepositorySocial.kt extraction pattern (extension functions, no
 * call-site changes). v1.1.7 dev, feature-flag OFF — nothing here is
 * reachable from any 1:1 path while [WhisperGroupsConfig.ENABLED] is false.
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

/** Current group picture: URL + IV are server-visible metadata; the image key
 * travels per-member sealed (see `picture` events) so bytes stay opaque. */
data class WhisperGroupPicture(
    val url: String,
    val iv: String,
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
            WhisperGroupEventType.ADD -> {
                require(isMemberSigned(ev, members, verify, signerKeyOf)) { "add not signed by inviter" }
                require(members[ev.actor] == WhisperGroupRole.ADMIN || !inviteAdminsOnly) {
                    "add not admin-signed"
                }
                val target = targetOf(ev)
                require(!members.containsKey(target)) { "add of existing member" }
                require(members.size < WhisperGroupsConfig.MAX_MEMBERS) { "group full" }
                members[target] = WhisperGroupRole.MEMBER
                joinEpoch[target] = ev.epoch
                epoch = ev.epoch
            }
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
                val pic = parseGroupPicture(ev.payloadJson) ?: error("bad picture payload")
                picture = WhisperGroupPicture(url = pic.first, iv = pic.second, epoch = ev.epoch)
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

/** ADD/REMOVE/PROMOTE/INVITE/CANCEL carry the subject user id as the raw payload; RENAME carries the name. */
private fun targetOf(ev: WhisperGroupEvent): String = ev.payloadJson.trim().trim('"')

/**
 * Group-picture payload: `{"url","iv","frames":{memberId: sealedKeyFrame}}`.
 * URL/IV are server-visible metadata; each frame seals the image key to one
 * member through their 1:1 session (same primitive as message fan-out).
 */
fun buildGroupPicturePayload(url: String, iv: String, frames: Map<String, String>): String =
    groupJson.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        buildJsonObject {
            put("url", url)
            put("iv", iv)
            put("frames", groupJson.encodeToJsonElement(
                kotlinx.serialization.builtins.MapSerializer(
                    kotlinx.serialization.serializer<String>(),
                    kotlinx.serialization.serializer<String>(),
                ),
                frames,
            ))
        },
    )

fun parseGroupPicturePayload(raw: String): Triple<String, String, Map<String, String>>? = runCatching {
    val obj = groupJson.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject ?: return null
    val url = (obj["url"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return null
    val iv = (obj["iv"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return null
    if (url.isBlank()) return null
    val frames = (obj["frames"] as? kotlinx.serialization.json.JsonObject)
        ?.mapNotNull { (k, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.let { k to it } }
        .orEmpty().toMap()
    Triple(url, iv, frames)
}.getOrNull()

private fun parseGroupPicture(raw: String): Pair<String, String>? =
    parseGroupPicturePayload(raw)?.let { it.first to it.second }

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
    val senderId: String,
    val senderName: String,
    val body: String,
    val createdAt: String,
    val epoch: Long,
    val mine: Boolean,
)

// ── Transport ops (all flag-gated, 1:1 paths untouched) ─────────────────
// v1 notes: sends are online-only (outbox groupId legs land in the next
// slice); every open requires a fresh verified sync; event verification is
// TOFU against current bundle signers (see file header).

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
    val rows = db.from("whisper_group_events").select { filter { eq("group_id", groupId) } }
        .decodeList<GroupEventRow>()
    val events = rows.sortedBy { it.seq }.map { it.toModel() }
    if (events.isEmpty()) error("Group has no history.")
    // Pre-fetch every distinct actor key (bundle fetch is suspend; the
    // verifier lambda below is a pure map read — never blocks).
    val keys = mutableMapOf<String, String?>()
    events.map { it.actor }.distinct().forEach { uid -> keys[uid] = signerKeyOf(uid) }
    val membership = applyEvents(
        groupId = groupId,
        events = events,
        verify = { payload, sig, signer -> groupVerify(payload, sig, signer) },
        signerKeyOf = { uid -> keys[uid] },
    )
    persistGroupCache(group, membership, events)
    membership
}

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
    val sig = crypto.signProtocol(groupEventSignBytes(groupId, seq, epoch, type, actor, payload))
        ?: error("Could not sign the group event.")
    db.from("whisper_group_events").insert(
        GroupEventInsert(
            groupId = groupId,
            seq = seq,
            epoch = epoch,
            type = type.wire,
            actor = actor,
            payload = payload,
            adminSig = sig,
            clientVersionCode = groupClientVersion(),
        ),
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
    val me = requireMe()
    val clean = plaintext.trim()
    require(clean.isNotBlank()) { "Message is empty." }
    require(clean.length <= WhisperRepository.MAX_MESSAGE_CHARS) { "Message is too long." }
    val m = syncGroup(groupId).getOrThrow()
    require(m.isMember(me)) { "You are no longer a member of this group." }
    val targets = m.members.keys.sorted()
    val frames = mutableMapOf<String, String>()
    val failed = mutableListOf<String>()
    for (memberId in targets) {
        val frame = buildGroupFrameBody(groupId, m.epoch, System.currentTimeMillis(), clean)
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
    val row = GroupMessageInsert(
        groupId = groupId,
        senderId = me,
        clientId = java.util.UUID.randomUUID().toString(),
        content = encodeGroupRow(
            WhisperGroupRowContent(
                v = WhisperGroupsConfig.ENVELOPE_VERSION,
                g = groupId,
                e = m.epoch,
                s = System.currentTimeMillis(),
                senderId = me,
                frames = frames,
            ),
        ),
        contentIv = "v3-group",
        epoch = m.epoch,
        clientVersionCode = groupClientVersion(),
    )
    db.from("whisper_group_messages").insert(row)
}

/** Reads the chat: opens my frame of every row I belonged to at its epoch. */
suspend fun WhisperRepository.fetchGroupMessages(
    groupId: String,
    limit: Int = 200,
): Result<List<WhisperGroupChatMessage>> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val m = syncGroup(groupId).getOrThrow()
    require(m.isMember(me)) { "You are no longer a member of this group." }
    val rows = db.from("whisper_group_messages").select {
        filter { eq("group_id", groupId) }
        // Newest-first at the server so the LIMIT keeps the live head, never a
        // stale arbitrary slice; the client re-sorts ascending below.
        order("created_at", Order.DESCENDING)
        limit(limit.toLong())
    }.decodeList<GroupMessageRow>()
    val names = getFriends().getOrNull().orEmpty().associate { it.id to it.effectiveName }
    rows.sortedWith(compareBy({ it.createdAt ?: "" }, { it.id })).mapNotNull { row ->
        if (!m.isMemberAt(row.senderId, row.epoch) || !m.isMemberAt(me, row.epoch)) return@mapNotNull null
        val body = decodeGroupRow(row.content) ?: return@mapNotNull null
        if (body.g != groupId || body.e != row.epoch) return@mapNotNull null
        val frame = body.frames[me] ?: return@mapNotNull null
        val inner = openV3Frame(frame, row.senderId, row.senderId, me) ?: return@mapNotNull null
        val text = openGroupFrameBody(groupId, row.epoch, inner) ?: return@mapNotNull null
        WhisperGroupChatMessage(
            id = row.id,
            groupId = groupId,
            senderId = row.senderId,
            senderName = if (row.senderId == me) "You" else names[row.senderId] ?: "Member",
            body = text,
            createdAt = row.createdAt ?: "",
            epoch = row.epoch,
            mine = row.senderId == me,
        )
    }
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
        buildGroupPicturePayload(url, "", frames),
    ).getOrThrow()
    syncGroup(groupId).getOrThrow()
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
    val (url, _, frames) = parseGroupPicturePayload(latest.payload) ?: error("Bad picture data.")
    val frame = frames[me] ?: return@runCatching null
    val inner = openV3Frame(frame, latest.actor, latest.actor, me) ?: error("Could not open the picture key.")
    val keyB64 = openGroupFrameBody(groupId, latest.epoch, inner) ?: error("Could not open the picture key.")
    val key = runCatching { java.util.Base64.getDecoder().decode(keyB64) }.getOrNull()
        ?: error("Bad picture key.")
    val png = encryptedImageHost.download(url).getOrThrow()
    val sealed = WhisperImageCipherTransport.decode(png) ?: error("Bad picture data.")
    openGroupPictureBytes(sealed, key) ?: error("Could not decrypt the picture.")
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
