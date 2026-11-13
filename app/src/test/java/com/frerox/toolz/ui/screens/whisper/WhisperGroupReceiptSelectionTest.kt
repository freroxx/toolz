/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import com.frerox.toolz.data.whisper.WhisperGroupChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seen receipts are capped per pass (one upsert batch, not an unbounded
 * fan-out). The timeline is oldest-first, so the cap must select the NEWEST
 * rows — taking the head marked ancient history and left the messages actually
 * on screen without a receipt.
 */
class WhisperGroupReceiptSelectionTest {

    private fun msg(
        id: String,
        mine: Boolean = false,
        clientId: String = id,
    ) = WhisperGroupChatMessage(
        id = id,
        groupId = "g",
        clientId = clientId,
        senderId = if (mine) "me" else "friend",
        senderName = "Friend",
        body = "body",
        createdAt = "2026-11-13T00:00:00Z",
        epoch = 0,
        mine = mine,
    )

    @Test
    fun `cap keeps the newest rows, newest first`() {
        val rows = listOf(msg("m1"), msg("m2"), msg("m3"))
        assertEquals(listOf("m3", "m2"), newestSeenReceiptIds(rows, limit = 2))
    }

    @Test
    fun `own rows are never receipted`() {
        val rows = listOf(msg("m1"), msg("mine", mine = true), msg("m2"))
        assertEquals(listOf("m2", "m1"), newestSeenReceiptIds(rows))
    }

    @Test
    fun `blank and duplicate client ids are dropped`() {
        val rows = listOf(
            msg("m1", clientId = "c1"),
            msg("m2", clientId = ""),
            msg("m3", clientId = "c1"),
            msg("m4", clientId = "c2"),
        )
        assertEquals(listOf("c2", "c1"), newestSeenReceiptIds(rows))
    }

    @Test
    fun `non-positive limit yields nothing`() {
        val rows = listOf(msg("m1"))
        assertTrue(newestSeenReceiptIds(rows, limit = 0).isEmpty())
        assertTrue(newestSeenReceiptIds(rows, limit = -5).isEmpty())
    }
}
