/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import androidx.compose.runtime.Immutable
import com.frerox.toolz.BuildConfig
import com.frerox.toolz.R
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

// ────────────────────────────────────────────────────────────────────────────
// Daily Ups (v1.1.7) — client side of the story plane.
//
// ARCHITECTURE (mirrors server section 20261018_whisper_stories_actions.sql):
//  - A story is ONE preprocessed photo (EXIF/GPS stripped, 1920 long edge,
//    JPEG q82 / lossless PNG for alpha) sealed with a fresh AES-256-GCM key
//    and uploaded through the SAME whisper-image-upload edge chat pictures
//    use — with ImgBB expiration=86400 as defense-in-depth behind the
//    server's own 24h lifecycle.
//  - Distribution is NEVER a Double Ratchet frame: the descriptor
//    (`whisper:story:{...}`) is sealed ONCE per friend in a V2 static
//    envelope ([WhisperRepository.sealStaticEnvelope] — pinned-key chain,
//    no session state) and stored in the whisper_story_recipients row.
//    Friends added later see nothing prior: the audience snaps at publish
//    time; a block re-check happens server-side on every read.
//  - The poster keeps a SELF-sealed descriptor (state='own' row) so their
//    copy re-opens offline and still carries the delete capability for a
//    best-effort whisper-image-delete.
//  - Views/reactions are RPC-mediated; the poster fetches counts on demand.
//    The story id always comes from the ROW (server truth) — never from the
//    descriptor, which only carries media + policy facts.
//  - Cleanup: SQL watchdog rows + the whisper-story-sweep edge function;
//    clients only PING the sweep opportunistically (fire-and-forget).
//
// LOCAL CACHE INVARIANT (WhisperStoryLocalEntity): rows hold only opaque
// sealed envelopes + lifecycle metadata. This file is the only component
// that opens them, and it never persists a plaintext descriptor or key.
// ────────────────────────────────────────────────────────────────────────────

/** Compile-time switchboard for the Daily Ups surface.
 *
 * Daily Ups are intentionally hidden from the UI in this release: the feature
 * is not ready to ship, so the hub, composer, stories bar, routes and deep
 * links are all closed while the data layer, SQL, edge functions and every
 * screen file stay in place for a later release. */
object WhisperDailyUpsConfig {
    /** Master kill switch for the whole feature surface. */
    const val ENABLED = false

    /** Server re-writes expiry anyway; clients mirror 24h for labels. */
    const val TTL_MS = 24 * 60 * 60 * 1000L

    /** Server enforced (whisper_story_publish_quota, one UTC-day bucket). */
    const val DAILY_LIMIT = 5

    /** Server enforced (inline guard in whisper_story_publish). */
    const val MAX_RECIPIENTS = 96

    /** Server rejects story writes below this (client_version_code >= 18). */
    const val MIN_VERSION_CODE = 18

    /** ImgBB defense-in-depth: the hosted copy self-expires behind the purge. */
    const val UPLOAD_TTL_SECONDS = 24 * 60 * 60L

    /** Server bound: sealed_descriptor holds 1..32768 chars. */
    const val DESCRIPTOR_MAX_CHARS = 32_768

    /** One fetch page for friends' envelopes; oldest-expiring first. */
    const val SYNC_PAGE = 200

    /** The exact emoji palette the server validates (whisper_story_react). */
    val REACTION_EMOJIS: List<String> = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

    const val SYNC_TIMEOUT_MS = 30_000L
    const val PUBLISH_TIMEOUT_MS = 150_000L
    const val IMAGE_TIMEOUT_MS = 45_000L
}

/** Daily Ups compile gate; mirrors [requireGroupsEnabled]. */
internal fun requireDailyUpsEnabled() {
    check(WhisperDailyUpsConfig.ENABLED) { "Daily Ups are disabled in this release" }
}

/**
 * The sealed payload every recipient (and the poster) receives. Deliberately
 * tiny and stable: single-letter keys and a distinguishing prefix against
 * paste-in or cross-message confusion. [imageKeyB64] is the AES-256 story
 * image key (32 bytes → 44 base64 chars); the GCM nonce rides at the head of
 * the sealed transport blob, exactly like chat pictures.
 *
 * [attachmentId] is the ImgBB delete capability the upload edge returned —
 * present ONLY in the poster's self-sealed copy; viewers never hold it.
 */
@Serializable
data class WhisperStoryDescriptor(
    @SerialName("v") val version: Int = 1,
    @SerialName("u") val hostUrl: String,
    @SerialName("k") val imageKeyB64: String,
    @SerialName("x") val expiresAtMs: Long,
    @SerialName("r") val receiptsEnabled: Boolean = false,
    @SerialName("a") val attachmentId: String? = null,
) {
    fun encode(): String = PREFIX + json.encodeToString(serializer(), this)

    companion object {
        const val PREFIX = "whisper:story:"
        const val VERSION = 1

        private val json = Json { encodeDefaults = false }

        /** Strict decode: prefix required, shape validated, nothing permissive. */
        fun decode(raw: String): WhisperStoryDescriptor? {
            if (!raw.startsWith(PREFIX)) return null
            val body = runCatching {
                json.decodeFromString(serializer(), raw.removePrefix(PREFIX))
            }.getOrNull() ?: return null
            if (body.version != VERSION) return null
            // 32 bytes base64 → exactly 44 chars; anything else is tampering.
            if (body.imageKeyB64.length != 44) return null
            if (body.hostUrl.isBlank() || body.expiresAtMs <= 0L) return null
            return body
        }
    }
}

/** Decrypted bytes of a Daily Up photo, ready for the viewer UI. */
class WhisperStoryImage internal constructor(
    val bytes: ByteArray,
)

/**
 * Receipts snapshot of ONE of my own stories, fetched on demand. RLS lets
 * the poster read every view/reaction row; viewers only ever see their own.
 */
@Immutable
data class WhisperStoryReceiptSnapshot(
    val storyId: String,
    /** Viewer ids with a receipt row (receipts on) — names resolve client-side. */
    val viewerIds: List<String>,
    /** One toggleable emoji per viewer: viewerId → emoji. */
    val reactions: Map<String, String>,
)

// ────────────────────────────────────────────────────────────────────────────
// Server row DTOs (decode-only; ignoreUnknownKeys throughout)
// ────────────────────────────────────────────────────────────────────────────

@Serializable
private data class StoryRecipientJoinedRow(
    @SerialName("story_id") val storyId: String,
    @SerialName("sealed_descriptor") val sealedDescriptor: String,
    @SerialName("story") val story: StoryServerRow? = null,
)

@Serializable
private data class StoryServerRow(
    val id: String,
    @SerialName("poster_id") val posterId: String,
    val state: String,
    @SerialName("receipts_enabled") val receiptsEnabled: Boolean = false,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
private data class StoryViewRow(
    @SerialName("story_id") val storyId: String,
    @SerialName("viewer_id") val viewerId: String,
)

@Serializable
private data class StoryReactionRow(
    @SerialName("story_id") val storyId: String,
    @SerialName("user_id") val userId: String,
    val emoji: String,
)

@Serializable
private data class StoryQuotaRow(
    @SerialName("successful_count") val successfulCount: Int,
)

// ────────────────────────────────────────────────────────────────────────────
// Observations (local cache; server truth arrives via [syncDailyUps])
// ────────────────────────────────────────────────────────────────────────────

/** Friends' live stories (oldest-expiring first — the viewer's reading order). */
fun WhisperRepository.observeFriendDailyUps(nowMs: Long = System.currentTimeMillis()): Flow<List<WhisperStoryLocalEntity>> =
    storyLocalDao.observeActive(nowMs)

/** My own live stories (the My Ups strip). */
fun WhisperRepository.observeMyDailyUps(nowMs: Long = System.currentTimeMillis()): Flow<List<WhisperStoryLocalEntity>> =
    storyLocalDao.observeOwn(nowMs)

// ────────────────────────────────────────────────────────────────────────────
// Publish
// ────────────────────────────────────────────────────────────────────────────

/**
 * Publishes one story to every CURRENT friend minus [excludedFriendIds].
 *
 * Fail-closed ordering: preprocess → seal → upload → seal audience legs →
 * ONE publish RPC (atomic server side: story + recipients + quota) → local
 * own-row. If any seal-leg fails, NOTHING is sent: a story seen by half the
 * audience is worse than no story. Throws [IllegalStateException]s mapped by
 * [mapDailyUpError]; the caller toasts and keeps the draft.
 */
suspend fun WhisperRepository.publishDailyUp(
    photoBytes: ByteArray,
    mimeType: String,
    receiptsEnabled: Boolean,
    excludedFriendIds: Set<String>,
): Result<WhisperStoryDescriptor> = runCatching {
    requireDailyUpsEnabled()
    val me = requireStoryMe()
    storyIo(WhisperDailyUpsConfig.PUBLISH_TIMEOUT_MS) {
        val prep = preprocessDailyUpPhoto(appContext, photoBytes, mimeType)
        val photo = when (prep) {
            is DailyUpPhotoPreparation.Ready -> prep.photo
            is DailyUpPhotoPreparation.Rejected ->
                throw IllegalStateException(rejectionMessage(prep.reason))
        }

        val (sealed, key) = newGroupPictureSeal(photo.bytes)
        val png = WhisperImageCipherTransport.encode(sealed)
        val (hostUrl, attachmentId) = encryptedImageHost
            .upload(png, "story-$me-${System.currentTimeMillis()}", WhisperDailyUpsConfig.UPLOAD_TTL_SECONDS)
            .getOrThrow()
        require(hostUrl.isNotBlank() && !attachmentId.isNullOrBlank()) { "Picture upload failed." }

        // Audience = CURRENT accepted friends minus exclusions (server
        // re-validates against friendships + the block ledger atomically).
        val friends = getFriends().getOrThrow()
        val audience = friends.map { it.id }
            .filter { it != me && it !in excludedFriendIds }
            .distinct()
        check(audience.size <= WhisperDailyUpsConfig.MAX_RECIPIENTS) {
            "audience too large (${audience.size})"
        }

        // One descriptor per friend; story id comes later from the RPC (rows
        // carry it), so the leg payload stays key/host-facts only.
        val baseDescriptor = WhisperStoryDescriptor(
            hostUrl = hostUrl,
            imageKeyB64 = Base64.getEncoder().encodeToString(key),
            expiresAtMs = System.currentTimeMillis() + WhisperDailyUpsConfig.TTL_MS,
            receiptsEnabled = receiptsEnabled,
        )
        val legs = audience.map { friendId ->
            val sealedDescriptor = sealStaticEnvelope(baseDescriptor.encode(), friendId)
                ?: throw IllegalStateException("could not start your secure session")
            friendId to sealedDescriptor
        }

        val published = db.rpc(
            "whisper_story_publish",
            buildJsonObject {
                put("p_client_id", UUID.randomUUID().toString())
                put("p_host_url", hostUrl)
                put("p_host_object_id", attachmentId)
                put("p_receipts_enabled", receiptsEnabled)
                put("p_recipients", buildJsonArray {
                    legs.forEach { (rid, sealed) ->
                        add(buildJsonObject {
                            put("r", rid)
                            put("d", sealed)
                        })
                    }
                })
                put("p_client_version_code", BuildConfig.VERSION_CODE)
            },
        ).decodeAs<StoryServerRow>()

        val expiresAtMs = runCatching { Instant.parse(published.expiresAt).toEpochMilli() }
            .getOrElse { System.currentTimeMillis() + WhisperDailyUpsConfig.TTL_MS }

        // Self leg: same facts + delete capability, sealed to myself, so My
        // Ups re-opens locally and can still whisper-image-delete early.
        storyLocalDao.upsert(
            WhisperStoryLocalEntity(
                storyId = published.id,
                ownerId = me,
                sealedDescriptor = sealDescriptorToSelf(
                    baseDescriptor.copy(attachmentId = attachmentId),
                ) ?: throw IllegalStateException("could not start your secure session"),
                expiresAtMs = expiresAtMs,
                receiptsEnabled = receiptsEnabled,
                state = STORY_STATE_OWN,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        kickDailyUpSweep()
        baseDescriptor
    }
}

/** Stable failure vocabulary for preprocessing (mapped by [mapDailyUpError]). */
private fun rejectionMessage(reason: DailyUpPhotoRejection): String = when (reason) {
    DailyUpPhotoRejection.UNSUPPORTED -> "unsupported photo format"
    DailyUpPhotoRejection.INPUT_TOO_LARGE -> "photo too large"
    DailyUpPhotoRejection.DECODE_FAILED -> "photo decoding failed"
    DailyUpPhotoRejection.OUTPUT_TOO_LARGE -> "photo too large"
    DailyUpPhotoRejection.METADATA_REMAINS -> "photo metadata could not be stripped"
}

// ────────────────────────────────────────────────────────────────────────────
// Sync (server → local cache reconcile; pull-only on hub/screen loads)
// ────────────────────────────────────────────────────────────────────────────

/**
 * Reconciles the local story cache with the server. Bounded by page limits;
 * prune decisions run client-side against what RLS returns — a friend's
 * deleted story (or a new block, either direction) simply stops having rows
 * here after the next sync.
 *
 * The opportunistic sweep ping piggybacks HERE (not only on publish) so a
 * hub open converges any leftover hosted copies without a worker.
 */
suspend fun WhisperRepository.syncDailyUps(): Result<Unit> = runCatching {
    requireDailyUpsEnabled()
    val me = requireStoryMe()
    storyIo(WhisperDailyUpsConfig.SYNC_TIMEOUT_MS) {
        val nowMs = System.currentTimeMillis()
        val isoNow = Instant.ofEpochMilli(nowMs).toString()

        // 1. My own published rows (poster-side RLS; metadata only — no keys).
        val ownRows = db.from("whisper_stories").select {
            filter {
                eq("poster_id", me)
                eq("state", "published")
                gt("expires_at", isoNow)
            }
            order("expires_at", Order.ASCENDING)
            limit(WhisperDailyUpsConfig.SYNC_PAGE.toLong())
        }.decodeList<StoryServerRow>()

        // 2. My addressed envelopes joined with their story row. Inner join:
        //    RLS-invisible stories (unpublished/expired/blocked/vanished)
        //    drop out — nothing unreadable is ever cached.
        val myRows = db.from("whisper_story_recipients").select(
            Columns.raw("story_id,sealed_descriptor,story:whisper_stories!inner(poster_id,state,receipts_enabled,expires_at)"),
        ) {
            filter { eq("recipient_id", me) }
            order("created_at", Order.ASCENDING)
            limit(WhisperDailyUpsConfig.SYNC_PAGE.toLong())
        }.decodeList<StoryRecipientJoinedRow>()

        // ── own rows: refresh server-authoritative expiry; NEVER clobber the
        // self-sealed descriptor (it is publish-time-only state).
        for (row in ownRows) {
            val serverExpiry = runCatching { Instant.parse(row.expiresAt).toEpochMilli() }
                .getOrElse { continue }
            val local = storyLocalDao.byId(row.id) ?: continue // reinstalled → keyless; skip
            if (local.expiresAtMs != serverExpiry) {
                storyLocalDao.upsert(
                    local.copy(expiresAtMs = serverExpiry, updatedAtMs = nowMs),
                )
            }
        }

        // ── received rows: upsert every still-readable envelope.
        for (row in myRows) {
            val story = row.story ?: continue
            val serverExpiry = runCatching { Instant.parse(story.expiresAt).toEpochMilli() }
                .getOrElse { continue }
            storyLocalDao.upsert(
                WhisperStoryLocalEntity(
                    storyId = row.storyId,
                    ownerId = story.posterId,
                    sealedDescriptor = row.sealedDescriptor,
                    expiresAtMs = serverExpiry,
                    receiptsEnabled = story.receiptsEnabled,
                    state = STORY_STATE_ACTIVE,
                    updatedAtMs = nowMs,
                ),
            )
        }

        // ── prune local rows the server no longer lets us read.
        val freshIds = myRows.map { it.storyId }.toSet()
        for (stale in storyLocalDao.listActive(nowMs)) {
            if (stale.storyId !in freshIds) storyLocalDao.delete(stale.storyId)
        }
        val ownIds = ownRows.map { it.id }.toSet()
        for (stale in storyLocalDao.listOwn(nowMs)) {
            if (stale.storyId !in ownIds) storyLocalDao.delete(stale.storyId)
        }
        storyLocalDao.deleteDue(nowMs, 50)

        kickDailyUpSweep()
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Open (viewer / my-up download + decrypt)
// ────────────────────────────────────────────────────────────────────────────

private suspend fun WhisperRepository.storyDescriptor(
    entity: WhisperStoryLocalEntity,
): WhisperStoryDescriptor? {
    if (entity.sealedDescriptor.isBlank()) return null
    val plain = openStaticEnvelope(entity.sealedDescriptor, entity.ownerId) ?: return null
    return WhisperStoryDescriptor.decode(plain)
}

/**
 * Downloads and decrypts a story image. Mapped failures: locked (descriptor
 * unopenable — e.g. the poster rotated keys after I cached it), corrupt
 * (host returned garbage), offline (host failure). Expiry is judged locally
 * on the cached row before this is called; the server judges it for RPCs.
 */
suspend fun WhisperRepository.downloadDailyUpImage(storyId: String): Result<WhisperStoryImage> = runCatching {
    requireDailyUpsEnabled()
    storyIo(WhisperDailyUpsConfig.IMAGE_TIMEOUT_MS) {
        val row = storyLocalDao.byId(storyId) ?: throw IllegalStateException("no such story")
        val desc = storyDescriptor(row) ?: throw IllegalStateException("locked story")
        val hosted = encryptedImageHost.download(desc.hostUrl).getOrThrow()
        val sealed = WhisperImageCipherTransport.decode(hosted)
            ?: throw IllegalStateException("corrupt story image")
        val plain = openGroupPictureBytes(sealed, Base64.getDecoder().decode(desc.imageKeyB64))
            ?: throw IllegalStateException("corrupt story image")
        WhisperStoryImage(plain)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// View receipts + reactions
// ────────────────────────────────────────────────────────────────────────────

/**
 * Marks a story viewed. Idempotent on the server; a view row is written ONLY
 * when the poster enabled receipts (the RPC decides — the client never
 * makes that choice).
 */
suspend fun WhisperRepository.markDailyUpViewed(storyId: String): Result<Unit> = runCatching {
    requireDailyUpsEnabled()
    requireStoryMe()
    storyIo(WhisperDailyUpsConfig.SYNC_TIMEOUT_MS) {
        db.rpc(
            "whisper_story_view",
            buildJsonObject {
                put("p_story_id", storyId)
                put("p_client_version_code", BuildConfig.VERSION_CODE)
            },
        )
    }
}

/** Toggles a single emoji reaction (server enforces the one-per-viewer PK). */
suspend fun WhisperRepository.toggleDailyUpReaction(
    storyId: String,
    emoji: String,
    on: Boolean,
): Result<Unit> = runCatching {
    requireDailyUpsEnabled()
    requireStoryMe()
    require(emoji in WhisperDailyUpsConfig.REACTION_EMOJIS) { "unknown reaction emoji" }
    storyIo(WhisperDailyUpsConfig.SYNC_TIMEOUT_MS) {
        db.rpc(
            "whisper_story_react",
            buildJsonObject {
                put("p_story_id", storyId)
                put("p_emoji", emoji)
                put("p_on", on)
                put("p_client_version_code", BuildConfig.VERSION_CODE)
            },
        )
    }
}

/**
 * MY current reaction per story (RLS: authors read their own row). The
 * viewer uses this to render the "already reacted" state without exposing
 * anyone else's rows.
 */
suspend fun WhisperRepository.fetchMyDailyUpReactions(
    storyIds: List<String>,
): Result<Map<String, String>> = runCatching {
    requireDailyUpsEnabled()
    val me = requireStoryMe()
    if (storyIds.isEmpty()) return@runCatching emptyMap()
    storyIo(WhisperDailyUpsConfig.SYNC_TIMEOUT_MS) {
        db.from("whisper_story_reactions").select {
            filter { isIn("story_id", storyIds); eq("user_id", me) }
        }.decodeList<StoryReactionRow>().associate { it.storyId to it.emoji }
    }
}

/**
 * Fetches who viewed / reacted to MY stories. Passing someone else's story
 * id simply returns nothing (RLS hides the rows — never an error).
 */
suspend fun WhisperRepository.fetchDailyUpReceipts(
    storyIds: List<String>,
): Result<List<WhisperStoryReceiptSnapshot>> = runCatching {
    requireDailyUpsEnabled()
    val me = requireStoryMe()
    if (storyIds.isEmpty()) return@runCatching emptyList()
    storyIo(WhisperDailyUpsConfig.SYNC_TIMEOUT_MS) {
        val views = db.from("whisper_story_views").select {
            filter { isIn("story_id", storyIds) }
        }.decodeList<StoryViewRow>()
        val reactions = db.from("whisper_story_reactions").select {
            filter { isIn("story_id", storyIds) }
        }.decodeList<StoryReactionRow>()
        storyIds.map { id ->
            WhisperStoryReceiptSnapshot(
                storyId = id,
                viewerIds = views.filter { it.storyId == id }.map { it.viewerId },
                reactions = reactions.filter { it.storyId == id }.associate { it.userId to it.emoji },
            )
        }
    }
}

/** Stories I posted so far TODAY (server quota: 5 per UTC day). */
suspend fun WhisperRepository.dailyUpsPostedToday(): Int {
    if (!WhisperDailyUpsConfig.ENABLED) return 0
    val me = myId
    if (me.isBlank()) return 0
    // Quota is advisory client-side; the server is the real ceiling.
    return runCatching {
        storyIo(WhisperDailyUpsConfig.SYNC_TIMEOUT_MS) {
            val today = LocalDate.now(ZoneOffset.UTC).toString()
            db.from("whisper_story_publish_quota").select {
                filter { eq("poster_id", me); eq("day", today) }
            }.decodeList<StoryQuotaRow>().sumOf { it.successfulCount }
        }
    }.getOrDefault(0).coerceAtMost(WhisperDailyUpsConfig.DAILY_LIMIT)
}

// ────────────────────────────────────────────────────────────────────────────
// Delete (poster side; server owns the lifecycle)
// ────────────────────────────────────────────────────────────────────────────

/**
 * Deletes MY story end-to-end:
 *  1. `whisper_story_delete` → state='delete_pending' (RLS hides the story
 *     immediately; the day's quota stays spent — it is a per-day budget);
 *  2. drops the local own row (the UI stops listing it);
 *  3. best-effort host delete through the chat-picture whisper-image-delete
 *     edge (ownership = poster). A failure here only means the sweep does it.
 */
suspend fun WhisperRepository.deleteDailyUp(storyId: String): Result<Unit> = runCatching {
    requireDailyUpsEnabled()
    val me = requireStoryMe()
    // Read the self-sealed row BEFORE the server lifecycle moves on: the
    // delete below (and any later sync) removes it — after that the delete
    // capability is unreachable and only the sweep could still act.
    val ownCopy = storyIo(WhisperDailyUpsConfig.SYNC_TIMEOUT_MS) {
        storyLocalDao.byId(storyId)?.takeIf { it.ownerId == me && it.state == STORY_STATE_OWN }
    }
    storyIo(WhisperDailyUpsConfig.SYNC_TIMEOUT_MS) {
        db.rpc(
            "whisper_story_delete",
            buildJsonObject { put("p_story_id", storyId) },
        )
    }
    // Local row dies regardless: the server row is now delete_pending and any
    // later sync would prune it anyway.
    storyLocalDao.delete(storyId)
    runCatching {
        val local = ownCopy ?: return@runCatching
        storyIo(WhisperDailyUpsConfig.IMAGE_TIMEOUT_MS) {
            val desc = storyDescriptor(local) ?: return@storyIo
            encryptedImageHost.delete(desc.hostUrl, desc.attachmentId)
        }
    }
    kickDailyUpSweep()
}

// ────────────────────────────────────────────────────────────────────────────
// Internals
// ────────────────────────────────────────────────────────────────────────────

internal const val STORY_STATE_ACTIVE = "active"
internal const val STORY_STATE_OWN = "own"

/** Authenticated id or a mapped throw; mirrors the groups file's requireMe(). */
private fun WhisperRepository.requireStoryMe(): String {
    val me = myId
    require(me.isNotBlank()) { "Sign in to share Daily Ups." }
    return me
}

private suspend fun <T> storyIo(timeoutMs: Long, block: suspend () -> T): T =
    withContext(Dispatchers.IO) {
        withTimeout(timeoutMs) { block() }
    }

/**
 * Seals a descriptor to MYSELF: ECDH against my own static key, one envelope
 * entry, in-band key set ([WhisperRepository.sealStaticEnvelope] cannot do
 * this — there is no self pin). A poster who cannot re-open their story is
 * useless, so a failure here fails the whole publish.
 */
private fun WhisperRepository.sealDescriptorToSelf(descriptor: WhisperStoryDescriptor): String? {
    val pub = crypto.getPublicKeyBase64() ?: return null
    val (ct, iv) = crypto.encryptMessage(
        plainText = descriptor.encode(),
        peerPublicKeyBase64 = pub,
        senderId = myId,
        receiverId = myId,
    ) ?: return null
    return WhisperEnvelope.encode(listOf(Triple(WhisperEnvelope.keyId(pub), iv, ct)), pub)
}

/**
 * Fire-and-forget sweep ping (attest-gated edge). The SQL watchdog and
 * ImgBB expiration both degrade gracefully, so a failure here is invisible:
 * logged at info, never thrown, never awaited by callers.
 */
private fun WhisperRepository.kickDailyUpSweep() {
    appScope.launch {
        runCatching {
            edgeFunctions.execute(
                EdgeFunctionClient.Request(
                    function = "whisper-story-sweep",
                    jsonBody = "{}",
                    authMode = EdgeFunctionClient.AuthMode.USER,
                ),
            )
        }.onFailure { e ->
            android.util.Log.i("WhisperStories", "sweep ping skipped: ${e.message}")
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Error mapping (consumed by the story UI; mirrors mapGroupError)
// ────────────────────────────────────────────────────────────────────────────

fun mapDailyUpError(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val msg = raw.lowercase()
    return when {
        "quota exceeded" in msg ->
            R.string.st_Whisper_DailyUp_ErrQuota
        // Server: audience fell out of current friendships / block ledger.
        "story audience must be current friends" in msg || "not a member" in msg ->
            R.string.st_Whisper_DailyUp_ErrNotFriend
        "audience too large" in msg ->
            R.string.st_Whisper_DailyUp_ErrAudience
        "stale client version" in msg ->
            R.string.st_Whisper_DailyUp_ErrVersion
        "unknown reaction emoji" in msg || "story reactions are for the audience" in msg ->
            R.string.st_Whisper_DailyUp_ErrReaction
        "unsupported photo format" in msg ->
            R.string.st_Whisper_DailyUp_ErrUnsupported
        "photo too large" in msg ->
            R.string.st_Whisper_DailyUp_ErrTooBig
        "photo decoding failed" in msg ->
            R.string.st_Whisper_DailyUp_ErrDecode
        "photo metadata could not be stripped" in msg ->
            R.string.st_Whisper_DailyUp_ErrMetadata
        "locked story" in msg ->
            R.string.st_Whisper_DailyUp_ErrLocked
        "corrupt story image" in msg ->
            R.string.st_Whisper_DailyUp_ErrCorrupt
        "no such story" in msg ->
            R.string.st_Whisper_DailyUp_ErrExpired
        "picture upload failed" in msg ->
            R.string.st_Whisper_DailyUp_ErrUpload
        "could not start your secure session" in msg ->
            R.string.st_Whisper_DailyUp_ErrUnreachable
        "sign in to share daily ups" in msg || "authenticated caller required" in msg ->
            R.string.st_Whisper_DailyUp_ErrAuth
        "timed out" in msg || "timeout" in msg ->
            R.string.st_Whisper_DailyUp_ErrSync
        else -> null
    }
}

/** Resolves a Daily Up error to display text (mapped string or the raw message). */
fun dailyUpErrorText(context: android.content.Context, e: Throwable?): String {
    val raw = e?.message
    val res = mapDailyUpError(raw)
    return if (res != null) context.getString(res) else raw
        ?: context.getString(R.string.st_Whisper_DailyUp_ErrSync)
}
