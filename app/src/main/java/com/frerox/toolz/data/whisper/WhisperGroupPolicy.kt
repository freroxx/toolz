/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Actions a group owner may delegate to admins or members.
 *
 * Ownership transfer is deliberately absent: a group has exactly one owner and
 * only that owner may transfer ownership.  The policy travels in a signed group
 * event, so this wire vocabulary must stay stable and unknown values deny.
 */
enum class WhisperGroupCapability(val wire: String) {
    SEND_MESSAGES("send"),
    INVITE_MEMBERS("invite"),
    APPROVE_JOIN_REQUESTS("approveJoin"),
    REMOVE_MEMBERS("remove"),
    PROMOTE_DEMOTE("manageRoles"),
    EDIT_INFO("editInfo"),
    EDIT_PICTURE("editPicture"),
    CREATE_POLLS("createPoll"),
    CLOSE_POLLS("closePoll"),
    ;

    companion object {
        fun of(wire: String): WhisperGroupCapability? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * Version-one group authorization policy. [ownerId] is scalar rather than a
 * role set so a payload cannot represent two owners.  Membership is supplied
 * separately from the verified event log; [validateFor] binds the policy to it
 * before authorization decisions are made.
 */
data class GroupPolicyV1(
    val ownerId: String,
    val adminCapabilities: Set<WhisperGroupCapability> = DEFAULT_ADMIN_CAPABILITIES,
    val memberCapabilities: Set<WhisperGroupCapability> = DEFAULT_MEMBER_CAPABILITIES,
) {
    init {
        require(ownerId.isNotBlank()) { "group policy owner is required" }
        require(memberCapabilities.all { it in MEMBER_DELEGABLE_CAPABILITIES }) {
            "member policy grants a privileged capability"
        }
    }

    /** Fails closed unless the sole owner is a current verified admin. */
    fun validateFor(members: Map<String, WhisperGroupRole>) {
        require(members[ownerId] == WhisperGroupRole.ADMIN) {
            "group policy owner must be a current admin"
        }
    }

    /** Returns false for non-members even when their id matches [ownerId]. */
    fun allows(
        userId: String,
        members: Map<String, WhisperGroupRole>,
        capability: WhisperGroupCapability,
    ): Boolean {
        val role = members[userId] ?: return false
        if (userId == ownerId) return role == WhisperGroupRole.ADMIN
        return when (role) {
            WhisperGroupRole.ADMIN -> capability in adminCapabilities
            WhisperGroupRole.MEMBER -> capability in memberCapabilities
        }
    }

    companion object {
        val DEFAULT_ADMIN_CAPABILITIES: Set<WhisperGroupCapability> = WhisperGroupCapability.entries.toSet()
        val DEFAULT_MEMBER_CAPABILITIES: Set<WhisperGroupCapability> = setOf(
            WhisperGroupCapability.SEND_MESSAGES,
        )
        val MEMBER_DELEGABLE_CAPABILITIES: Set<WhisperGroupCapability> = setOf(
            WhisperGroupCapability.SEND_MESSAGES,
            WhisperGroupCapability.INVITE_MEMBERS,
            WhisperGroupCapability.EDIT_INFO,
            WhisperGroupCapability.EDIT_PICTURE,
            WhisperGroupCapability.CREATE_POLLS,
        )
    }
}

private const val GROUP_POLICY_VERSION = 1
private val GROUP_POLICY_FIELDS = setOf("v", "owner", "admin", "member")

/**
 * Returns the one accepted policy payload spelling.  Its bytes are safe to
 * include in [groupEventSignBytes]; sorted capability wires prevent a Set's
 * iteration order from changing a signature.
 */
fun buildGroupPolicyPayload(policy: GroupPolicyV1): String {
    val admin = policy.adminCapabilities.canonicalCapabilityArray()
    val member = policy.memberCapabilities.canonicalCapabilityArray()
    return "{\"v\":$GROUP_POLICY_VERSION,\"owner\":${policy.ownerId.jsonString()},\"admin\":$admin,\"member\":$member}"
}

/**
 * Parses only version-one, canonical policy payloads.  Rejecting unknown
 * fields and non-canonical field/array ordering prevents a valid policy from
 * acquiring a second signed spelling or silently granting a future privilege.
 */
fun parseGroupPolicyPayload(raw: String): GroupPolicyV1? = runCatching {
    val obj = groupJson.parseToJsonElement(raw) as? JsonObject ?: return null
    if (obj.keys != GROUP_POLICY_FIELDS) return null
    val version = (obj["v"] as? JsonPrimitive)?.intOrNull ?: return null
    if (version != GROUP_POLICY_VERSION) return null
    val owner = (obj["owner"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    val admin = (obj["admin"] as? JsonArray)?.toCapabilities() ?: return null
    val member = (obj["member"] as? JsonArray)?.toCapabilities() ?: return null
    val policy = GroupPolicyV1(owner, admin, member)
    if (raw != buildGroupPolicyPayload(policy)) return null
    policy
}.getOrNull()

private fun JsonArray.toCapabilities(): Set<WhisperGroupCapability>? {
    val parsed = map { (it as? JsonPrimitive)?.contentOrNull?.let(WhisperGroupCapability::of) ?: return null }
    if (parsed.size != parsed.toSet().size) return null
    if (parsed.map(WhisperGroupCapability::wire) != parsed.map(WhisperGroupCapability::wire).sorted()) return null
    return parsed.toSet()
}

private fun Set<WhisperGroupCapability>.canonicalCapabilityArray(): String =
    map(WhisperGroupCapability::wire)
        .sorted()
        .joinToString(separator = ",", prefix = "[", postfix = "]") { it.jsonString() }

private fun String.jsonString(): String = JsonPrimitive(this).toString()
