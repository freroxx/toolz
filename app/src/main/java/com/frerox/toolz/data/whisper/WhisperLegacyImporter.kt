/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.whisper

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * One-time, idempotent move of Whisper tables out of `toolz_db` into
 * [WhisperDatabase] (`toolz_whisper_db`).
 *
 * WHY: Auto Backup copies whole database files, so Whisper message metadata
 * could not be excluded while it lived in the shared `toolz_db`. [AppDatabase]
 * no longer declares these entities (schema v69); this importer copies every
 * row into the dedicated database, then removes the legacy tables from
 * `toolz_db` so backup can no longer carry them.
 *
 * SAFETY ORDER:
 *  1. copy every legacy row into the target DB (single transaction, committed);
 *  2. only after the copy is durable, delete the legacy rows (single transaction);
 *  3. drop the now-unmanaged legacy tables.
 * A crash before (2) leaves the legacy rows in place, so the next launch simply
 * re-copies (INSERT OR REPLACE) and retries. No data can be lost between steps.
 *
 * The delete runs with `PRAGMA secure_delete = ON` so SQLite overwrites the
 * freed cell/page bytes instead of leaving recoverable row data in the file;
 * a best-effort `wal_checkpoint(TRUNCATE)` + `VACUUM` then shrinks the file
 * before the next backup run.
 */
internal object WhisperLegacyImporter {

    /**
     * Every table that moved. Kept as an explicit list (not reflection) so a
     * future entity addition must consciously update the migration.
     */
    internal val TABLES = listOf(
        "whisper_messages",
        "whisper_outbox",
        "whisper_local_tombstones",
        "whisper_groups_local",
        "whisper_group_members_local",
        "whisper_group_events_local",
        "whisper_story_local",
        "whisper_self_read",
    )

    /**
     * Columns present in both schemas, in target order. The legacy table may
     * predate a column; intersecting by name keeps the copy valid across
     * schema drift instead of assuming identical column positions.
     */
    internal fun sharedColumns(target: List<String>, legacy: List<String>): List<String> =
        target.filter { it in legacy }

    /**
     * Copies and removes legacy Whisper data. Returns the number of rows copied.
     *
     * Idempotent: if the legacy tables are already gone or empty this is a cheap
     * no-op (plus one `DROP TABLE IF EXISTS` pass on the first run after an
     * upgrade). Callers should still guard it so a transient failure can never
     * prevent the app from starting; the next launch retries.
     */
    fun migrateIfNeeded(
        legacy: SupportSQLiteDatabase,
        target: SupportSQLiteDatabase,
    ): Int {
        val present = TABLES.filter { tableExists(legacy, it) }
        if (present.isEmpty()) return 0

        // Only touch tables the target also knows about. If the target is
        // missing one (should be impossible at WhisperDatabase v1), leaving the
        // legacy table untouched is safer than deleting data we did not copy.
        val movable = present.filter { tableExists(target, it) }
        val nonEmpty = movable.filter { tableHasRows(legacy, it) }

        var copied = 0
        if (nonEmpty.isNotEmpty()) {
            target.beginTransaction()
            try {
                for (table in nonEmpty) {
                    val columns = sharedColumns(
                        target = columnsOf(target, table),
                        legacy = columnsOf(legacy, table),
                    )
                    if (columns.isEmpty()) continue
                    copied += copyTable(legacy, target, table, columns)
                }
                target.setTransactionSuccessful()
            } finally {
                target.endTransaction()
            }

            // The whisper copy is committed. Now it is safe to remove the source
            // rows; secure_delete zeroes the freed bytes so the backup cannot
            // recover them even before VACUUM runs.
            legacy.beginTransaction()
            try {
                legacy.execSQL("PRAGMA secure_delete = ON")
                for (table in nonEmpty) legacy.execSQL("DELETE FROM `$table`")
                legacy.execSQL("PRAGMA secure_delete = OFF")
                legacy.setTransactionSuccessful()
            } finally {
                legacy.endTransaction()
            }
        }

        // AppDatabase v69 no longer manages these tables, so drop them outright
        // rather than leaving empty-but-present tables in every backup.
        legacy.beginTransaction()
        try {
            for (table in movable) legacy.execSQL("DROP TABLE IF EXISTS `$table`")
            legacy.setTransactionSuccessful()
        } finally {
            legacy.endTransaction()
        }

        // Best-effort: some Room connection pools reject VACUUM while another
        // connection is active. secure_delete already scrubbed the rows, so a
        // failure here costs space, never confidentiality.
        runCatching { legacy.query("PRAGMA wal_checkpoint(TRUNCATE)").close() }
        runCatching { legacy.execSQL("VACUUM") }
        return copied
    }

    private fun copyTable(
        legacy: SupportSQLiteDatabase,
        target: SupportSQLiteDatabase,
        table: String,
        columns: List<String>,
    ): Int {
        val projection = columns.joinToString(", ") { "`$it`" }
        val values = ContentValues(columns.size)
        var count = 0
        legacy.query("SELECT $projection FROM `$table`").use { cursor ->
            while (cursor.moveToNext()) {
                values.clear()
                for (index in columns.indices) {
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> values.putNull(columns[index])
                        Cursor.FIELD_TYPE_INTEGER -> values.put(columns[index], cursor.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> values.put(columns[index], cursor.getDouble(index))
                        Cursor.FIELD_TYPE_BLOB -> values.put(columns[index], cursor.getBlob(index))
                        else -> values.put(columns[index], cursor.getString(index))
                    }
                }
                target.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                count++
            }
        }
        return count
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(table),
        ).use { it.moveToFirst() }

    private fun tableHasRows(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT 1 FROM `$table` LIMIT 1").use { it.moveToFirst() }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): List<String> {
        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
        }
        return columns
    }
}
