/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

/**
 * V5 — Self-healing envelope encryption.
 * =====================================
 * ROOT CAUSE this eliminates: the old format encrypted every message to exactly ONE
 * recipient key. Any drift between "the key contacts think you have" and "the private
 * key your device actually holds" (reinstall, interrupted rotation, OEM keystore
 * weirdness) made messages permanently unreadable: the "[Encrypted message]" bug.
 *
 * THE FIX — multi-recipient envelopes:
 * A sender encrypts each message to EVERY recipient key it knows about (the TOFU-pinned
 * one AND any fresher server-published one), tagged with a short key id. Whichever copy
 * matches a key the recipient actually controls opens the message. Key drift stops
 * being user-visible: worst case, one extra ciphertext travels for a few minutes until
 * profiles resync.
 *
 * Wire format (stored in `messages.content`, `content_iv` = NULL):
 *   {"v":2,"k":[{"kid":"0123abcd","iv":"…","ct":"…"}, …]}
 * kid = first 8 hex chars of SHA-256(public_key_b64). Legacy v1 rows (raw b64 ct +
 * separate iv column) keep decrypting through the existing path; detection is by
 * prefix, so both formats coexist forever.
 */
object WhisperEnvelope {

    const val VERSION = 2
    const val PREFIX_V2 = "{\"v\":$VERSION"
    private const val KID_LEN = 8

    @Serializable
    private data class Entry(val kid: String, val iv: String, val ct: String)

    @Serializable
    private data class Envelope(val v: Int, val k: List<Entry>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Stable short identifier of a public key — safe to store/ship in envelopes. */
    fun keyId(publicKeyBase64: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(publicKeyBase64.toByteArray(Charsets.UTF_8))
            .take(KID_LEN / 2)
            .joinToString("") { "%02x".format(it) }

    fun isEnvelope(content: String): Boolean = content.startsWith(PREFIX_V2)

    /**
     * Builds an envelope from per-key encryptions. Entries with duplicate kids are
     * collapsed (first wins). Returns null when no entry could be produced.
     */
    fun encode(entries: List<Triple<String /*kid*/, String /*ivB64*/, String /*ctB64*/>>): String? {
        val deduped = entries.distinctBy { it.first }
        if (deduped.isEmpty()) return null
        return json.encodeToString(
            Envelope.serializer(),
            Envelope(v = VERSION, k = deduped.map { Entry(it.first, it.second, it.third) }),
        )
    }

    /** All entries, in order. Null when the content is not a valid v2 envelope. */
    fun decode(content: String): List<Triple<String, String, String>>? {
        if (!isEnvelope(content)) return null
        return runCatching {
            val env = json.decodeFromString(Envelope.serializer(), content)
            env.k.map { Triple(it.kid, it.iv, it.ct) }
        }.getOrNull()
    }

    /**
     * PHASE 2 §2.4: X3DH handshake headers ride on the FIRST session message as a
     * sibling key ("x3dh") next to the entry list. Encoded via [encodeWithExtra],
     * surfaced here for the responder.
     */
    fun decodeExtra(content: String): kotlinx.serialization.json.JsonObject? {
        if (!isEnvelope(content)) return null
        return runCatching {
            json.parseToJsonElement(content).jsonObject["x3dh"]?.jsonObject
        }.getOrNull()
    }

    fun encodeWithExtra(
        entries: List<Triple<String, String, String>>,
        extra: Pair<String, kotlinx.serialization.json.JsonObject>? = null,
    ): String? {
        val base = encode(entries) ?: return null
        if (extra == null) return base
        return runCatching {
            val merged = buildJsonObject {
                json.parseToJsonElement(base).jsonObject.forEach { (k, v) -> put(k, v) }
                put(extra.first, extra.second)
            }
            merged.toString()
        }.getOrNull()
    }

    /** The kid this device's current public key maps to, or null without a key. */
    fun ownKid(crypto: WhisperCrypto): String? =
        crypto.getPublicKeyBase64()?.let { keyId(it) }
}
