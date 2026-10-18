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

/** Proves the additive story-cache migration preserves existing Whisper local state. */
@RunWith(AndroidJUnit4::class)
class WhisperStoryLocalMigrationTest {
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
    fun migration62To63PreservesGroupRowsAndCreatesBoundedStoryTable() {
        helper.createDatabase(TEST_DB, 62).apply {
            execSQL(
                "INSERT INTO whisper_groups_local (id, name, epoch, createdBy, updatedAtMs) " +
                    "VALUES ('group-1', 'Existing group', 7, 'owner-1', 1000)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            63,
            true,
            DatabaseModule.MIGRATION_62_63,
        ).use { database ->
            database.query("SELECT name, epoch FROM whisper_groups_local WHERE id = 'group-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Existing group", cursor.getString(0))
                assertEquals(7, cursor.getInt(1))
            }
            database.query("PRAGMA index_list('whisper_story_local')").use { cursor ->
                val names = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertTrue("Expiry sweep index missing", names.contains("index_whisper_story_local_expiresAtMs"))
                assertTrue("Owner expiry index missing", names.contains("index_whisper_story_local_ownerId_expiresAtMs"))
            }
        }
    }

    private companion object {
        const val TEST_DB = "whisper-story-local-migration-test"
    }
}
