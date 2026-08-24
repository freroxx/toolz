/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper.session

import com.frerox.toolz.data.whisper.WhisperSessionFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * V6 (planwhisper.md §3.2/§3.4): codec for the v3 "ratchet" wire frame carried in
 * `messages.content` (with `messages.content_iv` set to [IV_MARK]).
 *
 * Shape (one JSON object, never an envelope — a ratchet ciphertext opens under
 * exactly one session, so multi-key fan-out does not apply):
 *
 *   {"v":3,"sid":"s0123456789ab","dh":"<b64 ratchet pub>","pn":0,"n":0,
 *    "ct":"<b64 iv‖ct‖tag>","x3dh":{"ik":"…","ek":"…","spk":"kid","opk":"kid"?}}
 *
 * `x3dh` rides ONLY on the first message of a peer-initiated session; every later
 * frame omits it. Detection is prefix-based like the v2 envelope so legacy rows and
 * v3 rows coexist forever.
 */
object WhisperV3Codec {

    const val VERSION = 3
    const val PREFIX = "{\"v\":$VERSION"
    /** Sentinel stored in the `content_iv` column for v3 ratchet rows. */
    const val IV_MARK = "v3"

    private val json = Json { ignoreUnknownKeys = true }
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    fun isV3(content: String): Boolean = content.startsWith(PREFIX)

    @Serializable
    data class Frame(
        @SerialName("sid") val sessionId: String,
        @SerialName("dh") val dhPubB64: String,
        @SerialName("pn") val pn: Int,
        @SerialName("n") val n: Int,
        @SerialName("ct") val ctB64: String,
        @SerialName("x3dh") val x3dh: X3dhWire? = null,
    ) {
        fun dhPub(): ByteArray = unb64.decode(dhPubB64)
        fun ciphertextPacked(): ByteArray = unb64.decode(ctB64)
    }

    /** Wire copy of the X3DH header (same fields as WhisperSessionFactory.X3dhHeader). */
    @Serializable
    data class X3dhWire(
        @SerialName("ik") val ikPubB64: String,
        @SerialName("ek") val ekPubB64: String,
        @SerialName("spk") val spkKid: String,
        @SerialName("opk") val opkKid: String? = null,
    )

    fun encode(
        sessionId: String,
        header: WhisperRatchet.Header,
        ciphertextPacked: ByteArray,
        x3dh: WhisperSessionFactory.X3dhHeader?,
    ): String {
        val obj = buildJsonObject {
            put("v", VERSION)
            put("sid", sessionId)
            put("dh", b64.encodeToString(header.dhPub))
            put("pn", header.pn)
            put("n", header.n)
            put("ct", b64.encodeToString(ciphertextPacked))
            if (x3dh != null) put("x3dh", x3dhJson(x3dh))
        }
        return obj.toString()
    }

    /** Null when [content] is not a parseable v3 frame (never throws on hostile input). */
    fun parse(content: String): Frame? {
        if (!isV3(content)) return null
        return runCatching {
            json.decodeFromString(Frame.serializer(), content)
        }.getOrNull()
    }

    fun x3dhJson(h: WhisperSessionFactory.X3dhHeader): JsonObject = buildJsonObject {
        put("ik", h.ikPubB64)
        put("ek", h.ekPubB64)
        put("spk", h.spkKid)
        if (h.opkKid != null) put("opk", h.opkKid)
    }

    fun x3dhFromJson(obj: JsonObject): WhisperSessionFactory.X3dhHeader? = runCatching {
        WhisperSessionFactory.X3dhHeader(
            ikPubB64 = obj["ik"]!!.jsonPrimitive.content,
            ekPubB64 = obj["ek"]!!.jsonPrimitive.content,
            spkKid = obj["spk"]!!.jsonPrimitive.content,
            opkKid = obj["opk"]?.jsonPrimitive?.content,
        )
    }.getOrNull()

    fun encodeX3dhWire(h: WhisperSessionFactory.X3dhHeader): X3dhWire =
        X3dhWire(h.ikPubB64, h.ekPubB64, h.spkKid, h.opkKid)

    fun toFactoryHeader(w: X3dhWire): WhisperSessionFactory.X3dhHeader =
        WhisperSessionFactory.X3dhHeader(w.ikPubB64, w.ekPubB64, w.spkKid, w.opkKid)
}
