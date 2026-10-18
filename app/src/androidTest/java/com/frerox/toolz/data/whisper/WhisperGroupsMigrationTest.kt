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
 * Proves the reinstall-proof signer-history schema change (64->65) is purely
 * additive: a group cache populated at schema 62 migrates through 63, 64 and
 * 65 with the cached event rows intact and `signerCert` arriving as NULL
 * (the legacy fallback path of the published-set verifier) — never a lost
 * row, never a destructive reset.
 */
@RunWith(AndroidJUnit4::class)
class WhisperGroupsMigrationTest {
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
    fun migration62to65PreservesGroupRowsAndAddsNullableSignerCert() {
        helper.createDatabase(TEST_DB, 62).apply {
            execSQL(
                "INSERT INTO whisper_groups_local (id, name, epoch, createdBy, updatedAtMs) " +
                    "VALUES ('group-1', 'Creator crew', 3, 'creator-1', 1000)",
            )
            execSQL(
                "INSERT INTO whisper_group_events_local (id, groupId, seq, epoch, type, actor, payload, adminSig, createdAtMs) " +
                    "VALUES ('ev-1', 'group-1', 1, 0, 'create', 'creator-1', '{\"name\":\"Creator crew\"}', 'SIG', 1000)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            65,
            true,
            DatabaseModule.MIGRATION_62_63,
            DatabaseModule.MIGRATION_63_64,
            DatabaseModule.MIGRATION_64_65,
        ).use { database ->
            database.query("SELECT name, epoch FROM whisper_groups_local WHERE id = 'group-1'").use { cursor ->
                // cache rows are the deliverable: no destructive reset mid-chain
                assertTrue(cursor.moveToFirst())
                assertEquals("Creator crew", cursor.getString(0))
                assertEquals(3, cursor.getInt(1))
            }
            database.query("SELECT seq, adminSig, signerCert FROM whisper_group_events_local WHERE id = 'ev-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getLong(0))
                assertEquals("SIG", cursor.getString(1))
                // pre-history rows migrate as NULL → legacy current-cert fold-back
                assertTrue("signerCert must arrive NULL after migration", cursor.isNull(2))
            }
        }
    }

    private companion object {
        const val TEST_DB = "whisper-groups-migration-test"
    }
}
