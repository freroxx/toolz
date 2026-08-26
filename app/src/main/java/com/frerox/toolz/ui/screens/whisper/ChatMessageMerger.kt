/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import com.frerox.toolz.data.whisper.WhisperImageAttachment
import com.frerox.toolz.data.whisper.WhisperMessage
import java.time.Instant

/**
 * P4a/P8: PURE message-list merge + ordering rules for [WhisperChatViewModel],
 * extracted verbatim so the historically buggy precedence decisions are pinned by
 * unit tests ([ChatMessageMergerTest]) instead of living inline in a collector.
 *
 * Rules carried over unchanged:
 *  - TOMBSTONE PRECEDENCE: a delete-for-everyone marker always wins, in BOTH
 *    directions — a tombstone must never revert to stale plaintext, and stale
 *    plaintext must never resurrect over a fresh tombstone.
 *  - REACTIONS PRECEDENCE (V6-R6 fix): Room entities carry NO reactions, so every
 *    Room re-emission used to wipe visible reactions. In-memory state wins unless
 *    the fresh payload explicitly carries data AND none of my toggles are in flight.
 *  - REPLY ENRICHMENT: image targets normalize to the attachment prefix so the UI
 *    matches model-robustly; quoted sender names resolve at render time (ids only).
 *  - PENDING STATE: the fresh value wins (the old OR-merge stuck rows pulsing).
 */
object ChatMessageMerger {

    /**
     * Merges a Room re-emission into the current list WITHOUT pendings — callers
     * append their unresolved optimistic sends afterwards and run [sorted].
     *
     * @param isReactionToggleInFlight true when one of MY toggles for that message
     *   id is still awaiting server confirmation (defers clobbering).
     */
    fun mergeRoomEmission(
        existing: List<WhisperMessage>,
        newMessages: List<WhisperMessage>,
        isReactionToggleInFlight: (String) -> Boolean,
    ): List<WhisperMessage> {
        val existingById = existing.associateBy { it.id }
        val newIds = newMessages.mapTo(mutableSetOf()) { it.id }
        val merged = newMessages.map { newMsg ->
            val prior = existingById[newMsg.id]
            if (prior != null) {
                newMsg.copy(
                    content = when {
                        prior.isDeletedForEveryone -> prior.content
                        newMsg.isDeletedForEveryone -> newMsg.content
                        else -> prior.content
                    },
                    reactions = if (
                        newMsg.reactions.isNotEmpty() &&
                        !isReactionToggleInFlight(newMsg.id)
                    ) {
                        newMsg.reactions
                    } else {
                        prior.reactions
                    },
                    replyToContent = (newMsg.replyToContent ?: prior.replyToContent)?.normalizeReplySnippet(),
                    replyToSenderName = newMsg.replyToSenderName ?: prior.replyToSenderName,
                    isPending = newMsg.isPending,
                )
            } else newMsg
        } + existing.filter { it.isDeletedForEveryone && it.id !in newIds }
        return merged
    }

    /**
     * P2-7/V2 M-4 shared ordering: chronological by parsed createdAt, pending rows
     * pinned last. Keys parsed ONCE per message (decorate-sort-undecorate); safe
     * parse falls back to [Instant.MIN] so legacy timestamps sort oldest.
     */
    fun sorted(messages: List<WhisperMessage>): List<WhisperMessage> =
        messages
            .map { msg -> Triple(msg, msg.createdAt.parseIsoInstant(), if (msg.isPending) 1 else 0) }
            .sortedWith(compareBy({ it.second }, { it.third }))
            .map { it.first }

    /** Reply snippets for image targets normalize to the attachment prefix. */
    fun String?.normalizeReplySnippet(): String? = when (this) {
        "Image", "📷 Image" -> WhisperImageAttachment.MESSAGE_PREFIX
        else -> this
    }

    private fun String.parseIsoInstant(): Instant =
        runCatching { Instant.parse(this) }.getOrDefault(Instant.MIN)
}
