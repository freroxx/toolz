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
 *   "WG-EVENT|groupId|seq|epoch|type|actor|payloadJson".
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
    val members = mutableMapOf<String, WhisperGroupRole>()
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
            WhisperGroupEventType.CREATE -> error("unreachable")
        }
    }
    require(members.isNotEmpty()) { "empty group" }
    val ranges = (joinEpoch.keys + closedRanges.keys).associateWith { user ->
        val closed = closedRanges[user].orEmpty()
        val open = joinEpoch[user]?.let { listOf(it..Long.MAX_VALUE) }.orEmpty()
        closed + open
    }
    return WhisperGroupMembership(groupId, epoch, members.toMap(), name, inviteAdminsOnly, ranges)
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

/** ADD/REMOVE/PROMOTE carry the subject user id as the raw payload; RENAME carries the name. */
private fun targetOf(ev: WhisperGroupEvent): String = ev.payloadJson.trim().trim('"')

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
    if (!membership.isMember(me)) error("You are no longer a member of this group.")
    persistGroupCache(group, membership, events)
    membership
}

suspend fun WhisperRepository.syncGroups(): Result<List<WhisperGroupMembership>> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    val myRows = db.from("whisper_group_members").select { filter { eq("user_id", me) } }
        .decodeList<GroupMemberRow>()
    myRows.map { syncGroup(it.groupId).getOrThrow() }
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

/** Creates a group: row + admin/member rows + signed CREATE event (event-first). */
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
    db.from("whisper_group_members").insert(GroupMemberRow(groupId = groupId, userId = me, role = "admin"))
    others.forEach { uid ->
        db.from("whisper_group_members").insert(GroupMemberRow(groupId = groupId, userId = uid, role = "member", invitedBy = me))
    }
    val payload = groupJson.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        kotlinx.serialization.json.buildJsonObject {
            put("name", cleanName)
            put("invite", if (inviteAdminsOnly) "admins" else "all")
        },
    )
    publishGroupEvent(
        groupId = groupId,
        seq = 1,
        epoch = 0,
        type = WhisperGroupEventType.CREATE,
        actor = me,
        payload = payload,
    ).getOrThrow()
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

suspend fun WhisperRepository.addGroupMember(groupId: String, userId: String): Result<Unit> = runCatching {
    requireGroupsEnabled()
    val me = requireMe()
    require(userId.isNotBlank() && userId != me) { "Choose another user to add." }
    val m = syncGroup(groupId).getOrThrow()
    require(m.canInvite(me)) { "Only admins can invite to this group." }
    require(!m.isMember(userId)) { "Already a member." }
    require(m.members.size < WhisperGroupsConfig.MAX_MEMBERS) { "Group is full." }
    val status = getFriendshipStatus(userId).getOrNull()?.first
    require(status == FriendStatus.ACCEPTED) { "Only friends can be added to a group." }
    val (seq, epoch) = nextGroupSeqEpoch(groupId)
    // Event-first (events are authoritative); the member row follows.
    publishGroupEvent(groupId, seq, epoch + 1, WhisperGroupEventType.ADD, me, "\"$userId\"").getOrThrow()
    db.from("whisper_group_members").insert(
        GroupMemberRow(groupId = groupId, userId = userId, role = "member", invitedBy = me),
    )
    syncGroup(groupId).getOrThrow()
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
        limit(limit.toLong())
    }.decodeList<GroupMessageRow>()
    val names = getFriends().getOrNull().orEmpty().associate { it.id to it.effectiveName }
    rows.sortedBy { it.createdAt ?: "" }.mapNotNull { row ->
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
