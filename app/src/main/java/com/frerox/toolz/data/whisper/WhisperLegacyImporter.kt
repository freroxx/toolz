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

import android.database.Cursor
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
     *
     * [scheduleMaintenance] receives the `VACUUM` that reclaims the freed
     * pages. The provider runs during Hilt injection, i.e. on the main thread,
     * and a VACUUM rewrites the whole legacy database — seconds on a large
     * one. Copy/delete/drop and the WAL checkpoint stay synchronous because
     * they gate correctness (backup exclusion, no history loss, no old page
     * images left in `-wal`); only the space reclamation is deferred. When
     * null (tests) the VACUUM runs inline.
     */
    fun migrateIfNeeded(
        legacy: SupportSQLiteDatabase,
        target: SupportSQLiteDatabase,
        scheduleMaintenance: ((() -> Unit) -> Unit)? = null,
    ): Int {
        val present = TABLES.filter { tableExists(legacy, it) }
        if (present.isEmpty()) return 0

        // Only touch tables the target also knows about. If the target is
        // missing one (should be impossible at WhisperDatabase v1), leaving the
        // legacy table untouched is safer than deleting data we did not copy.
        val movable = present.filter { tableExists(target, it) }

        // A row-bearing table is drainable only if the two schemas actually share
        // a column. A non-empty table with zero shared columns is left completely
        // untouched rather than being deleted/dropped without a successful copy.
        val emptyTables = movable.filterNot { tableHasRows(legacy, it) }
        val copyable = movable
            .filter { tableHasRows(legacy, it) }
            .mapNotNull { table ->
                val columns = sharedColumns(
                    target = columnsOf(target, table),
                    legacy = columnsOf(legacy, table),
                )
                if (columns.isEmpty()) null else table to columns
            }
        val drainable = emptyTables + copyable.map { it.first }

        var copied = 0
        if (copyable.isNotEmpty()) {
            target.beginTransaction()
            try {
                for ((table, columns) in copyable) {
                    copied += copyTable(legacy, target, table, columns)
                }
                target.setTransactionSuccessful()
            } finally {
                target.endTransaction()
            }
        }

        if (drainable.isNotEmpty()) {
            // The whisper copy is committed (or the table was already empty).
            // Now it is safe to remove the source rows; secure_delete zeroes the
            // freed bytes so the backup cannot recover them even before VACUUM.
            legacy.beginTransaction()
            try {
                legacy.execSQL("PRAGMA secure_delete = ON")
                for (table in drainable) legacy.execSQL("DELETE FROM `$table`")
                legacy.execSQL("PRAGMA secure_delete = OFF")
                legacy.setTransactionSuccessful()
            } finally {
                legacy.endTransaction()
            }

            // AppDatabase v69 no longer manages these tables, so drop them
            // outright rather than leaving empty-but-present tables in backup.
            legacy.beginTransaction()
            try {
                for (table in drainable) legacy.execSQL("DROP TABLE IF EXISTS `$table`")
                legacy.setTransactionSuccessful()
            } finally {
                legacy.endTransaction()
            }
        }

        // Fold the WAL now: the drop deleted pages whose OLD images can still sit
        // in `toolz_db-wal`, and that file is inside the (currently disabled)
        // backup boundary. TRUNCATE removes them once the checkpoint succeeds.
        runCatching { legacy.query("PRAGMA wal_checkpoint(TRUNCATE)").close() }

        // Best-effort: some Room connection pools reject VACUUM while another
        // connection is active. secure_delete already scrubbed the rows, so a
        // failure here costs space, never confidentiality.
        val maintenance: () -> Unit = {
            runCatching { legacy.execSQL("VACUUM") }
            Unit
        }
        if (scheduleMaintenance != null) scheduleMaintenance(maintenance) else maintenance()
        return copied
    }

    private fun copyTable(
        legacy: SupportSQLiteDatabase,
        target: SupportSQLiteDatabase,
        table: String,
        columns: List<String>,
    ): Int {
        val projection = columns.joinToString(", ") { "`$it`" }
        val insertSql = "INSERT OR REPLACE INTO `$table` (" +
            columns.joinToString(", ") { "`$it`" } +
            ") VALUES (" + columns.joinToString(", ") { "?" } + ")"
        val statement = target.compileStatement(insertSql)
        var count = 0
        try {
            legacy.query("SELECT $projection FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) {
                    statement.clearBindings()
                    for (index in columns.indices) {
                        val bind = index + 1
                        when (cursor.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> statement.bindNull(bind)
                            Cursor.FIELD_TYPE_INTEGER -> statement.bindLong(bind, cursor.getLong(index))
                            Cursor.FIELD_TYPE_FLOAT -> statement.bindDouble(bind, cursor.getDouble(index))
                            Cursor.FIELD_TYPE_BLOB -> statement.bindBlob(bind, cursor.getBlob(index))
                            else -> statement.bindString(bind, cursor.getString(index))
                        }
                    }
                    statement.executeInsert()
                    count++
                }
            }
        } finally {
            statement.close()
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
