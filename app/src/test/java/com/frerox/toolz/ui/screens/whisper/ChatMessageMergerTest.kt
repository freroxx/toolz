/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.whisper

import com.frerox.toolz.data.whisper.WhisperImageAttachment
import com.frerox.toolz.data.whisper.WhisperMessage
import com.frerox.toolz.data.whisper.WhisperReactionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P4a/P8: characterization tests for the chat list merge + ordering rules that
 * previously lived inline in WhisperChatViewModel's Room collector — the exact
 * code behind the historical "reactions vanish" and "pending messages vanish"
 * field bugs.
 */
class ChatMessageMergerTest {

    private fun msg(
        id: String,
        content: String = "text",
        isDeleted: Boolean = false,
        reactions: List<WhisperReactionSummary> = emptyList(),
        replyToContent: String? = null,
        isPending: Boolean = false,
        createdAt: String = "2026-08-01T00:00:00Z",
    ): WhisperMessage = WhisperMessage(
        id = id,
        content = if (isDeleted) "This message has been deleted" else content,
        reactions = reactions,
        replyToContent = replyToContent,
        isPending = isPending,
        createdAt = createdAt,
    )

    // NOTE: isDeletedForEveryone is a computed getter over content — builders mark
    // deletion purely through the shared tombstone text.
    companion object {
        const val TOMBSTONE = "This message has been deleted"
    }
    @Test
    fun `tombstone in incoming wins over stale plaintext`() {
        val existing = listOf(msg("m1", content = "old plain"))
        val incoming = listOf(msg("m1", content = "This message has been deleted", isDeleted = true))
        val out = ChatMessageMerger.mergeRoomEmission(existing, incoming, isReactionToggleInFlight = { false })
        assertTrue(out.single().isDeletedForEveryone)
    }

    @Test
    fun `existing tombstone never reverts to stale plaintext from Room`() {
        val existing = listOf(msg("m1", content = "This message has been deleted", isDeleted = true))
        val incoming = listOf(msg("m1", content = "stale re-emission", isDeleted = false))
        val out = ChatMessageMerger.mergeRoomEmission(existing, incoming, isReactionToggleInFlight = { false })
        assertTrue(out.single().isDeletedForEveryone)
    }

    @Test
    fun `room re-emission without reactions keeps visible reactions`() {
        val reactions = listOf(WhisperReactionSummary(emoji = "🔥", count = 2, userIds = listOf("a", "b"), reactedByMe = true))
        val existing = listOf(msg("m1", reactions = reactions))
        val incoming = listOf(msg("m1")) // bare Room row: no reaction data
        val out = ChatMessageMerger.mergeRoomEmission(existing, incoming, isReactionToggleInFlight = { false })
        assertEquals(reactions, out.single().reactions)
    }

    @Test
    fun `in-flight toggle defers even a fresh server snapshot`() {
        val optimistic = listOf(WhisperReactionSummary(emoji = "❤️", count = 1, userIds = listOf("me"), reactedByMe = true))
        val existing = listOf(msg("m1", reactions = optimistic))
        val incoming = listOf(msg("m1", reactions = listOf(WhisperReactionSummary(emoji = "🔥", count = 1, userIds = listOf("a")))))
        val out = ChatMessageMerger.mergeRoomEmission(existing, incoming, isReactionToggleInFlight = { it == "m1" })
        assertEquals(optimistic, out.single().reactions)
    }

    @Test
    fun `fresh payload with data and nothing in flight adopts server truth`() {
        val server = listOf(WhisperReactionSummary(emoji = "👍", count = 3, userIds = listOf("x", "y", "z")))
        val existing = listOf(msg("m1"))
        val incoming = listOf(msg("m1", reactions = server))
        val out = ChatMessageMerger.mergeRoomEmission(existing, incoming, isReactionToggleInFlight = { false })
        assertEquals(server, out.single().reactions)
    }

    @Test
    fun `reply snippets normalize image labels to the attachment prefix`() {
        val existing = listOf(msg("m1", replyToContent = "Image"))
        val incoming = listOf(msg("m1"))
        val out = ChatMessageMerger.mergeRoomEmission(existing, incoming, isReactionToggleInFlight = { false })
        assertEquals(WhisperImageAttachment.MESSAGE_PREFIX, out.single().replyToContent)
    }

    @Test
    fun `pending state takes the fresh value (no sticky pending)`() {
        val existing = listOf(msg("p1", isPending = true))
        val incoming = listOf(msg("p1", isPending = false))
        val out = ChatMessageMerger.mergeRoomEmission(existing, incoming, isReactionToggleInFlight = { false })
        assertFalse(out.single().isPending)
    }

    @Test
    fun `tombstones absent from the room emission are preserved`() {
        val tombstone = msg("gone", isDeleted = true)
        val kept = msg("keep")
        val incoming = listOf(kept)
        val out = ChatMessageMerger.mergeRoomEmission(listOf(tombstone, kept), incoming, isReactionToggleInFlight = { false })
        assertEquals(listOf(kept.id, tombstone.id), out.map { it.id })
    }

    @Test
    fun `ordering is chronological with pendings pinned last`() {
        val t1 = msg("a", createdAt = "2026-08-01T01:00:00Z")
        val t2 = msg("b", createdAt = "2026-08-01T02:00:00Z")
        val pendingNewer = msg("p", createdAt = "2026-08-01T03:00:00Z", isPending = true)
        val sorted = ChatMessageMerger.sorted(listOf(pendingNewer, t2, t1))
        assertEquals(listOf("a", "b", "p"), sorted.map { it.id })
    }

    @Test
    fun `unparseable timestamps sort oldest and never crash`() {
        val bad = msg("bad", createdAt = "not-a-date")
        val good = msg("good", createdAt = "2026-08-01T01:00:00Z")
        val sorted = ChatMessageMerger.sorted(listOf(bad, good))
        assertEquals(listOf("bad", "good"), sorted.map { it.id })
    }
}
