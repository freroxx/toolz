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
import kotlinx.serialization.json.contentOrNull

/** Compile-time gate: false until the ship-day floor flip (see plan §6). */
object WhisperGroupsConfig {
    /** Master switch — every group entry point must check this first. */
    const val ENABLED = false

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
) {
    val admins: Set<String> get() = members.filterValues { it == WhisperGroupRole.ADMIN }.keys
    fun isMember(userId: String): Boolean = members.containsKey(userId)
    fun isAdmin(userId: String): Boolean = members[userId] == WhisperGroupRole.ADMIN
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
    val members = mutableMapOf<String, WhisperGroupRole>()
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
            name = runCatching {
                val obj = groupJson.parseToJsonElement(ev.payloadJson) as? kotlinx.serialization.json.JsonObject
                obj?.get("name")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull } ?: ""
            }.getOrDefault("").take(WhisperGroupsConfig.MAX_GROUP_NAME_CHARS)
            epoch = ev.epoch
            continue
        }
        require(members.isNotEmpty()) { "event before create" }
        when (type) {
            WhisperGroupEventType.ADD -> {
                require(isAdminSigned(ev, members, verify, signerKeyOf)) { "add not admin-signed" }
                val target = targetOf(ev)
                require(!members.containsKey(target)) { "add of existing member" }
                require(members.size < WhisperGroupsConfig.MAX_MEMBERS) { "group full" }
                members[target] = WhisperGroupRole.MEMBER
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
                epoch = ev.epoch
            }
            WhisperGroupEventType.LEAVE -> {
                require(members.containsKey(ev.actor)) { "leave by non-member" }
                require(members[ev.actor] != WhisperGroupRole.ADMIN || members.count { it.value == WhisperGroupRole.ADMIN } > 1) {
                    "last admin cannot leave"
                }
                members.remove(ev.actor)
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
    return WhisperGroupMembership(groupId, epoch, members.toMap(), name)
}

private fun isAdminSigned(
    ev: WhisperGroupEvent,
    members: Map<String, WhisperGroupRole>,
    verify: (ByteArray, String, String) -> Boolean,
    signerKeyOf: (String) -> String?,
): Boolean {
    if (members[ev.actor] != WhisperGroupRole.ADMIN) return false
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
