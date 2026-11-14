/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the one-time `toolz_db` -> [WhisperDatabase] move copies every legacy
 * row, leaves unrelated tables alone, drops only the moved tables, and is
 * idempotent across a retry.
 *
 * Uses the framework (unencrypted) SQLite driver on purpose: the importer only
 * needs [SupportSQLiteDatabase] semantics, and SQLCipher's at-rest behavior is
 * covered by the production builder.
 */
@RunWith(AndroidJUnit4::class)
class WhisperSplitMigrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var legacyHelper: SupportSQLiteOpenHelper
    private lateinit var legacy: SupportSQLiteDatabase
    private lateinit var target: WhisperDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(LEGACY_DB)
        legacyHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(LEGACY_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        legacy = legacyHelper.writableDatabase
        target = Room.inMemoryDatabaseBuilder(context, WhisperDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        runCatching { target.close() }
        runCatching { legacyHelper.close() }
        context.deleteDatabase(LEGACY_DB)
    }

    @Test
    fun migrateIfNeeded_copiesRowsDropsWhisperTablesAndKeepsUnrelatedTables() {
        createLegacyTables(includeSortEpoch = true)
        legacy.execSQL(
            "INSERT INTO whisper_messages (id, senderId, receiverId, content, isRead, createdAt, " +
                "isDeletedForEveryone, sortEpoch) " +
                "VALUES ('m1', 'alice', 'bob', 'ciphertext', 0, '2026-11-07T00:00:00Z', 0, 123)",
        )
        legacy.execSQL(
            "INSERT INTO whisper_outbox (clientId, senderId, receiverId, encryptedContent, contentIv, " +
                "createdAt, enqueuedAtMs, attempts) " +
                "VALUES ('c1', 'alice', 'bob', 'sealed', 'iv', '2026-11-07T00:00:00Z', 5, 1)",
        )
        // Unrelated data must survive the sweep.
        legacy.execSQL("CREATE TABLE notes (id TEXT NOT NULL PRIMARY KEY)")
        legacy.execSQL("INSERT INTO notes (id) VALUES ('n1')")

        val copied = WhisperLegacyImporter.migrateIfNeeded(
            legacy = legacy,
            target = target.openHelper.writableDatabase,
        )

        assertEquals(2, copied)
        target.openHelper.writableDatabase.query(
            "SELECT senderId, content, sortEpoch FROM whisper_messages WHERE id = 'm1'",
        ).use { cursor ->
            assertTrue("copied message missing", cursor.moveToFirst())
            assertEquals("alice", cursor.getString(0))
            assertEquals("ciphertext", cursor.getString(1))
            assertEquals(123L, cursor.getLong(2))
        }
        target.openHelper.writableDatabase.query(
            "SELECT encryptedContent FROM whisper_outbox WHERE clientId = 'c1'",
        ).use { cursor ->
            assertTrue("copied outbox row missing", cursor.moveToFirst())
            assertEquals("sealed", cursor.getString(0))
        }

        // Moved tables are gone from the legacy database...
        legacy.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'whisper_%'")
            .use { cursor -> assertFalse("legacy whisper tables survived", cursor.moveToFirst()) }
        // ...but unrelated tables are untouched.
        legacy.query("SELECT 1 FROM notes WHERE id = 'n1'").use { cursor ->
            assertTrue("unrelated table was damaged", cursor.moveToFirst())
        }
    }

    @Test
    fun migrateIfNeeded_isIdempotent() {
        createLegacyTables(includeSortEpoch = true)
        legacy.execSQL(
            "INSERT INTO whisper_messages (id, senderId, receiverId, content, isRead, createdAt, " +
                "isDeletedForEveryone, sortEpoch) " +
                "VALUES ('m1', 'alice', 'bob', 'ciphertext', 0, '2026-11-07T00:00:00Z', 0, 1)",
        )

        val targetDb = target.openHelper.writableDatabase
        assertEquals(1, WhisperLegacyImporter.migrateIfNeeded(legacy, targetDb))
        assertEquals(0, WhisperLegacyImporter.migrateIfNeeded(legacy, targetDb))

        targetDb.query("SELECT COUNT(*) FROM whisper_messages").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun migrateIfNeeded_copiesAcrossSchemaDriftUsingSharedColumns() {
        // An older legacy table without sortEpoch/reactionsJson: the importer
        // must copy the columns that exist and let the target defaults fill in
        // the rest instead of failing the whole migration.
        createLegacyTables(includeSortEpoch = false)
        legacy.execSQL(
            "INSERT INTO whisper_messages (id, senderId, receiverId, content, isRead, createdAt, " +
                "isDeletedForEveryone) " +
                "VALUES ('m1', 'alice', 'bob', 'ciphertext', 1, '2026-11-07T00:00:00Z', 0)",
        )

        assertEquals(
            1,
            WhisperLegacyImporter.migrateIfNeeded(legacy, target.openHelper.writableDatabase),
        )
        target.openHelper.writableDatabase.query(
            "SELECT content, sortEpoch FROM whisper_messages WHERE id = 'm1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ciphertext", cursor.getString(0))
            assertEquals(0L, cursor.getLong(1))
        }
    }

    @Test
    fun migrateIfNeeded_defersVacuumAndKeepsTheMoveCompleteBeforeItRuns() {
        createLegacyTables(includeSortEpoch = true)
        legacy.execSQL(
            "INSERT INTO whisper_messages (id, senderId, receiverId, content, isRead, createdAt, " +
                "isDeletedForEveryone, sortEpoch) " +
                "VALUES ('m1', 'alice', 'bob', 'ciphertext', 0, '2026-11-07T00:00:00Z', 0, 1)",
        )

        var scheduled: (() -> Unit)? = null
        val targetDb = target.openHelper.writableDatabase
        val copied = WhisperLegacyImporter.migrateIfNeeded(
            legacy = legacy,
            target = targetDb,
            scheduleMaintenance = { scheduled = it },
        )

        // The correctness half (copy + delete + drop) is already committed when
        // the provider returns; only the space-reclamation VACUUM is deferred.
        assertEquals(1, copied)
        assertNotNull("maintenance was not scheduled", scheduled)
        targetDb.query("SELECT COUNT(*) FROM whisper_messages").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        legacy.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'whisper_%'")
            .use { cursor -> assertFalse("legacy whisper tables survived", cursor.moveToFirst()) }

        scheduled!!.invoke()
    }

    private fun createLegacyTables(includeSortEpoch: Boolean) {
        val sortColumns = if (includeSortEpoch) {
            ", sortEpoch INTEGER NOT NULL DEFAULT 0, protocolVersion INTEGER NOT NULL DEFAULT 0, " +
                "reactionsJson TEXT NOT NULL DEFAULT ''"
        } else {
            ""
        }
        legacy.execSQL(
            "CREATE TABLE whisper_messages (id TEXT NOT NULL PRIMARY KEY, senderId TEXT NOT NULL, " +
                "receiverId TEXT NOT NULL, content TEXT NOT NULL, contentIv TEXT, replyToId TEXT, " +
                "isRead INTEGER NOT NULL, createdAt TEXT NOT NULL, replyToContent TEXT, " +
                "replyToSenderName TEXT, isDeletedForEveryone INTEGER NOT NULL" +
                sortColumns + ")",
        )
        legacy.execSQL(
            "CREATE TABLE whisper_outbox (clientId TEXT NOT NULL PRIMARY KEY, senderId TEXT NOT NULL, " +
                "receiverId TEXT NOT NULL, encryptedContent TEXT NOT NULL, contentIv TEXT NOT NULL, " +
                "replyToId TEXT, createdAt TEXT NOT NULL, enqueuedAtMs INTEGER NOT NULL, " +
                "attempts INTEGER NOT NULL, groupId TEXT, groupMsgKind TEXT)",
        )
    }

    private companion object {
        const val LEGACY_DB = "whisper-split-legacy-test"
    }
}
