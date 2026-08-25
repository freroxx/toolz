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

package com.frerox.toolz.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.frerox.toolz.data.AppDatabase
import com.frerox.toolz.data.notepad.NoteDao
import com.frerox.toolz.data.music.MusicDao
import com.frerox.toolz.data.steps.StepDao
import com.frerox.toolz.data.math.MathHistoryDao
import com.frerox.toolz.data.pdf.PdfAnnotationDao
import com.frerox.toolz.data.pdf.PdfMetadataDao
import com.frerox.toolz.data.notifications.NotificationDao
import com.frerox.toolz.data.focus.AppLimitDao
import com.frerox.toolz.data.focus.CaffeinateDao
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.todo.TaskDao
import com.frerox.toolz.data.ai.AiDao
import com.frerox.toolz.data.calendar.EventDao
import com.frerox.toolz.data.crypto.CryptoDao
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.search.SearchDao
import com.frerox.toolz.data.device.cache.DeviceSpecsDao
import com.frerox.toolz.data.whisper.WhisperMessageDao
import com.frerox.toolz.util.security.KeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * V2-FIX (reviewwhisper.md) H-10: whisper chronological ordering moved from ISO
     * string comparison to a numeric [sortEpoch] column. Adds the column with the same
     * DEFAULT (0) the entity declares via @ColumnInfo, then backfills every existing
     * row by parsing its createdAt (Instant.parse, OffsetDateTime.parse fallback,
     * epoch 0 on failure) through a single prepared statement loop.
     */
    // V3-FIX: 45->46 and 46->47 were shipped without migration objects (the old
    // destructive-upgrade era). Devices still on DB 45 (last public release) crashed
    // with "migration from 45 to 49 not found". Reconstructed purely from git
    // history: each bump ONLY added new cache tables, so CREATE TABLE IF NOT EXISTS
    // statements copied verbatim from the exported 49 schema are exact and safe —
    // these are retention-purged caches, but recreating them non-destructively is
    // free, so no user data is lost.
    private val MIGRATION_45_46 = object : Migration(45, 46) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `network_speed_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestampMs` INTEGER NOT NULL, `downloadMbps` REAL NOT NULL, `uploadMbps` REAL NOT NULL, " +
                    "`idleLatencyMs` INTEGER, `loadedLatencyMs` INTEGER, `bloatGrade` TEXT, `ssid` TEXT NOT NULL)"
            )
        }
    }

    private val MIGRATION_46_47 = object : Migration(46, 47) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `device_inventory` (`mac` TEXT NOT NULL, `ip` TEXT NOT NULL, " +
                    "`hostname` TEXT NOT NULL, `vendor` TEXT NOT NULL, `typeLabel` TEXT NOT NULL, " +
                    "`firstSeenMs` INTEGER NOT NULL, `lastSeenMs` INTEGER NOT NULL, `isGateway` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`mac`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scan_snapshots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestampMs` INTEGER NOT NULL, `ssid` TEXT NOT NULL, `bssid` TEXT NOT NULL, `json` TEXT NOT NULL)"
            )
        }
    }

    // PHASE 1 (roadmap §1.2): per-row wire protocol version for version negotiation.
    // V6-R7 (#cache): reactions JSON column — reactions render instantly on re-entry.
    private val MIGRATION_50_51 = object : Migration(50, 51) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE whisper_messages ADD COLUMN reactionsJson TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_49_50 = object : Migration(49, 50) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE whisper_messages ADD COLUMN protocol_version INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_47_48 = object : Migration(47, 48) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE whisper_messages ADD COLUMN sortEpoch INTEGER NOT NULL DEFAULT 0")

            data class Row(val id: String, val createdAtIso: String?)
            val rows = mutableListOf<Row>()
            db.query("SELECT id, createdAt FROM whisper_messages").use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("id")
                val createdIdx = cursor.getColumnIndexOrThrow("createdAt")
                while (cursor.moveToNext()) {
                    rows.add(Row(cursor.getString(idIdx), if (cursor.isNull(createdIdx)) null else cursor.getString(createdIdx)))
                }
            }

            db.compileStatement("UPDATE whisper_messages SET sortEpoch = ? WHERE id = ?").use { stmt ->
                for (row in rows) {
                    stmt.clearBindings()
                    stmt.bindLong(1, com.frerox.toolz.data.whisper.WhisperMessageEntity.parseSortEpoch(row.createdAtIso))
                    stmt.bindString(2, row.id)
                    stmt.executeUpdateDelete()
                }
            }
        }
    }

    // V3-FIX: 48 -> 49 adds passwords.isToken (nullable Boolean) so the Whisper
    // account type (64-char anon token vs password) is persisted metadata instead of
    // a vault-name substring heuristic. Matches the @ColumnInfo(defaultValue = "NULL")
    // declared on PasswordEntity.isToken; existing rows stay NULL (= unknown/legacy).
    private val MIGRATION_48_49 = object : Migration(48, 49) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE passwords ADD COLUMN isToken INTEGER DEFAULT NULL")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        System.loadLibrary("sqlcipher")

        val dbName = "toolz_db"
        val passphrase = KeyManager.getOrCreateMasterKey(context)
        val factory = SupportOpenHelperFactory(passphrase)
        
        // P0-1 FIX (reviewwhisper.md): fallbackToDestructiveMigration(true) would WIPES
        // E2EE history on any schema drift (version bump). For a messenger this is
        // catastrophic — pending outbox, tombstones, queued images become unrecoverable.
        // Replace with explicit migrations only + destructive only on downgrade for dev.
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
        .openHelperFactory(factory)
        // V2-FIX (reviewwhisper.md) H-10: explicit migrations only — every version bump
        // must ship one (see AppDatabase comment).
        .addMigrations(MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51)
        .fallbackToDestructiveMigrationOnDowngrade()
        // NOTE: Add explicit Migration objects here when schema changes. Schemas are now
        // EXPORTED to app/schemas (H-10 fix) so diffs are reviewable — never re-introduce
        // fallbackToDestructiveMigration().

        val db = builder.build()

        // Verify database is openable. SQLiteNotADatabaseException occurs when
        // the file exists but isn't a SQLCipher database or the key is wrong.
        // Fixed: do not reuse the same builder after delete (leaks first helper);
        // create a fresh builder.
        try {
            db.openHelper.writableDatabase
        } catch (e: Exception) {
            val isCorruptOrWrongKey = e is SQLiteNotADatabaseException ||
                (e.message?.contains("file is not a database") == true)
            // Legacy-hash recovery: a DB created by a build whose schema differed while
            // the VERSION NUMBER stayed the same makes Room throw
            // "Room cannot verify the data integrity" on every launch (fatal crash at
            // first Hilt injection). Such a schema predates our exported history, so no
            // honest Migration can be written from it — the only correct move is the
            // same one used for corrupt/wrong-key files above: delete and rebuild.
            // Going forward this cannot recur silently: schemas are exported
            // (app/schemas) and every version bump MUST ship an explicit Migration.
            val isLegacyHashMismatch = e is IllegalStateException &&
                e.message?.contains("Room cannot verify the data integrity") == true

            if (isCorruptOrWrongKey || isLegacyHashMismatch) {
                android.util.Log.e(
                    "DatabaseModule",
                    "Unopenable database (${e.javaClass.simpleName}: ${e.message}). " +
                        "Deleting '$dbName' and rebuilding from scratch.",
                    e
                )
                context.deleteDatabase(dbName)
                // Fresh builder avoids leaking the first helper's connection.
                return Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
            }
            throw e
        }

        return db
    }

    @Provides
    fun provideNoteDao(database: AppDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    fun provideMusicDao(database: AppDatabase): MusicDao {
        return database.musicDao()
    }

    @Provides
    fun provideStepDao(database: AppDatabase): StepDao {
        return database.stepDao()
    }

    @Provides
    fun provideMathHistoryDao(database: AppDatabase): MathHistoryDao {
        return database.mathHistoryDao()
    }

    @Provides
    fun providePdfAnnotationDao(database: AppDatabase): PdfAnnotationDao {
        return database.pdfAnnotationDao()
    }

    @Provides
    fun providePdfMetadataDao(database: AppDatabase): PdfMetadataDao {
        return database.pdfMetadataDao()
    }

    @Provides
    fun provideNotificationDao(database: AppDatabase): NotificationDao {
        return database.notificationDao()
    }

    @Provides
    fun provideAppLimitDao(database: AppDatabase): AppLimitDao {
        return database.appLimitDao()
    }

    @Provides
    fun provideCaffeinateDao(database: AppDatabase): CaffeinateDao {
        return database.caffeinateDao()
    }

    @Provides
    fun provideClipboardDao(database: AppDatabase): ClipboardDao {
        return database.clipboardDao()
    }

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideAiDao(database: AppDatabase): AiDao {
        return database.aiDao()
    }

    @Provides
    fun provideEventDao(database: AppDatabase): EventDao {
        return database.eventDao()
    }

    @Provides
    fun providePasswordDao(database: AppDatabase): PasswordDao {
        return database.passwordDao()
    }

    @Provides
    fun provideSearchDao(database: AppDatabase): SearchDao {
        return database.searchDao()
    }

    @Provides
    fun provideCryptoDao(database: AppDatabase): CryptoDao {
        return database.cryptoDao()
    }

    @Provides
    fun provideDeviceSpecsDao(database: AppDatabase): DeviceSpecsDao {
        return database.deviceSpecsDao()
    }

    @Provides
    fun provideWhisperMessageDao(database: AppDatabase): WhisperMessageDao {
        return database.whisperMessageDao()
    }

    @Provides
    fun provideSpeedHistoryDao(database: AppDatabase): com.frerox.toolz.data.network.SpeedHistoryDao {
        return database.speedHistoryDao()
    }

    @Provides
    fun provideDeviceInventoryDao(database: AppDatabase): com.frerox.toolz.data.network.DeviceInventoryDao {
        return database.deviceInventoryDao()
    }

    @Provides
    fun provideScanSnapshotDao(database: AppDatabase): com.frerox.toolz.data.network.ScanSnapshotDao {
        return database.scanSnapshotDao()
    }
}
