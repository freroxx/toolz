/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frerox.toolz.data.AppDatabase
import com.frerox.toolz.di.DatabaseModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves MIGRATION_67_68 is purely additive: existing message rows survive and
 * the conversation hot query can now use the (senderId, receiverId, sortEpoch)
 * index instead of sorting every matching row.
 */
@RunWith(AndroidJUnit4::class)
class WhisperMessageIndexMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun removeTestDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun migration67To68PreservesMessagesAndAddsSortIndex() {
        helper.createDatabase(TEST_DB, 67).apply {
            execSQL(
                "INSERT INTO whisper_messages " +
                    "(id, senderId, receiverId, content, contentIv, replyToId, isRead, " +
                    "createdAt, replyToContent, replyToSenderName, isDeletedForEveryone, " +
                    "sortEpoch, protocolVersion, reactionsJson) " +
                    "VALUES ('msg-1', 'alice', 'bob', 'ciphertext', 'iv', NULL, 0, " +
                    "'2026-11-07T00:00:00Z', NULL, NULL, 0, 1762473600000, 2, '')",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            68,
            true,
            DatabaseModule.MIGRATION_67_68,
        ).use { database ->
            database.query(
                "SELECT senderId, receiverId, content, sortEpoch " +
                    "FROM whisper_messages WHERE id = 'msg-1'",
            ).use { cursor ->
                assertTrue("Existing message row was lost", cursor.moveToFirst())
                assertEquals("alice", cursor.getString(0))
                assertEquals("bob", cursor.getString(1))
                assertEquals("ciphertext", cursor.getString(2))
                assertEquals(1762473600000L, cursor.getLong(3))
            }

            database.query("PRAGMA index_list('whisper_messages')").use { cursor ->
                val names = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertTrue(
                    "Conversation sort index missing",
                    names.contains("index_whisper_messages_senderId_receiverId_sortEpoch"),
                )
            }

            // The point of the index: the hot branch must SEARCH it rather than
            // materialize and sort every participant row.
            database.query(
                "EXPLAIN QUERY PLAN SELECT id FROM whisper_messages " +
                    "WHERE senderId = 'alice' AND receiverId = 'bob' " +
                    "ORDER BY sortEpoch DESC LIMIT 500",
            ).use { cursor ->
                val detailCol = cursor.getColumnIndexOrThrow("detail")
                val plan = buildString {
                    while (cursor.moveToNext()) {
                        append(cursor.getString(detailCol)).append('\n')
                    }
                }
                assertTrue(
                    "Query plan does not use the new index:\n$plan",
                    plan.contains("index_whisper_messages_senderId_receiverId_sortEpoch"),
                )
            }
        }
    }

    private companion object {
        const val TEST_DB = "whisper-message-index-migration-test"
    }
}
