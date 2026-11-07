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

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Device-local ciphertext store for Whisper.
 *
 * WHY A SEPARATE DATABASE (2026-11): Android Auto Backup rules are file-level, so
 * the only way to keep Whisper message rows out of cloud backup while still
 * backing up the rest of `toolz_db` is to move them to their own database file.
 * The file (`toolz_whisper_db`) is explicitly excluded in `backup_rules.xml` and
 * `data_extraction_rules.xml`; `toolz_db` stays included for PurgeShot/notes/etc.
 *
 * The split is transparent to every caller: the six DAO Hilt providers now point
 * at this database instead of [com.frerox.toolz.data.AppDatabase]. Existing
 * installs are migrated once by [WhisperLegacyImporter], which copies every row
 * out of the legacy tables in `toolz_db` and then drops them. No message,
 * outbox entry, tombstone, group cache row, story row or self-read copy is lost.
 *
 * The database is encrypted with the same SQLCipher master key as `toolz_db`.
 * Rows are ciphertext-only (except the Keystore-sealed self-read copies), and
 * the database is never backed up, so restoring on another device yields an
 * empty store — which is the intended privacy property.
 */
@Database(
    entities = [
        WhisperMessageEntity::class,
        WhisperOutboxEntity::class,
        WhisperLocalTombstoneEntity::class,
        WhisperGroupEntity::class,
        WhisperGroupMemberEntity::class,
        WhisperGroupEventEntity::class,
        WhisperStoryLocalEntity::class,
        WhisperSelfReadEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WhisperDatabase : RoomDatabase() {
    abstract fun whisperMessageDao(): WhisperMessageDao
    abstract fun whisperOutboxDao(): WhisperOutboxDao
    abstract fun whisperLocalTombstoneDao(): WhisperLocalTombstoneDao
    abstract fun whisperGroupDao(): WhisperGroupDao
    abstract fun whisperStoryLocalDao(): WhisperStoryLocalDao
    abstract fun whisperSelfReadDao(): WhisperSelfReadDao

    companion object {
        /**
         * Never add this file (or its `-wal`/`-shm` siblings) to a backup rule.
         * The whole point of the split is that Whisper ciphertext and metadata
         * stay on this device.
         */
        const val DB_NAME = "toolz_whisper_db"
    }
}
