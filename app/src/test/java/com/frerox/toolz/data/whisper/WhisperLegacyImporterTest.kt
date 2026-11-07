/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guards the table/column decisions of the one-time `toolz_db` -> WhisperDatabase
 * move. The destructive MediaStore/Room IO itself is exercised by instrumentation;
 * these tests protect the pure parts that determine WHAT is copied.
 */
class WhisperLegacyImporterTest {

    @Test
    fun tables_coverEveryMovedWhisperTableExactlyOnce() {
        assertEquals(
            listOf(
                "whisper_messages",
                "whisper_outbox",
                "whisper_local_tombstones",
                "whisper_groups_local",
                "whisper_group_members_local",
                "whisper_group_events_local",
                "whisper_story_local",
                "whisper_self_read",
            ),
            WhisperLegacyImporter.TABLES,
        )
        assertEquals(
            "duplicate table in importer list",
            WhisperLegacyImporter.TABLES.size,
            WhisperLegacyImporter.TABLES.toSet().size,
        )
        assertFalse(
            "importer must never touch non-whisper tables",
            WhisperLegacyImporter.TABLES.any { !it.startsWith("whisper_") },
        )
    }

    @Test
    fun sharedColumns_keepsTargetOrderAndDropsColumnsAbsentFromLegacy() {
        assertEquals(
            listOf("id", "senderId", "content"),
            WhisperLegacyImporter.sharedColumns(
                target = listOf("id", "senderId", "content", "newColumn"),
                legacy = listOf("content", "id", "senderId"),
            ),
        )
    }

    @Test
    fun sharedColumns_isEmptyWhenSchemasDoNotOverlap() {
        assertEquals(
            emptyList<String>(),
            WhisperLegacyImporter.sharedColumns(
                target = listOf("id", "body"),
                legacy = listOf("messageId", "payload"),
            ),
        )
    }
}
