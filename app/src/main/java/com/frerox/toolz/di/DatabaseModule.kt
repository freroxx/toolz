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
    // FIX v1.1.0->v1.1.1: version 44 (v1.1.0) to 52 (v1.1.1) jumped 8 versions
    // but MIGRATION_44_45 was missing, causing "A migration from 44 to 52 was
    // required but not found" and a crash loop at MusicPlayerService startup
    // (the first Hilt injection that opens the DB). v1.1.0 had no destructive
    // fallback disabled, so 44->52 never needed an explicit path. Reconstructed
    // from git history: 44->45 ONLY added whisper_messages, so CREATE TABLE +
    // indices copied verbatim from the exported 47 schema are exact.
    private val MIGRATION_44_45 = object : Migration(44, 45) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `whisper_messages` (`id` TEXT NOT NULL, `senderId` TEXT NOT NULL, " +
                    "`receiverId` TEXT NOT NULL, `content` TEXT NOT NULL, `contentIv` TEXT, " +
                    "`replyToId` TEXT, `isRead` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, " +
                    "`replyToContent` TEXT, `replyToSenderName` TEXT, `isDeletedForEveryone` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_whisper_messages_senderId` ON `whisper_messages` (`senderId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_whisper_messages_receiverId` ON `whisper_messages` (`receiverId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_whisper_messages_createdAt` ON `whisper_messages` (`createdAt`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_whisper_messages_senderId_receiverId_createdAt` " +
                    "ON `whisper_messages` (`senderId`, `receiverId`, `createdAt`)"
            )
        }
    }

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

    // P3: whisper prefs→Room consolidation — ciphertext outbox + local tombstones.
    // CREATE statements mirror Room's generated schema for the entities exactly
    // (column order/types/nullability, PKs, indices) so the exported-schema hash
    // validates. Purely additive: no existing table is touched.
    private val MIGRATION_51_52 = object : Migration(51, 52) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `whisper_outbox` (`clientId` TEXT NOT NULL, " +
                    "`senderId` TEXT NOT NULL, `receiverId` TEXT NOT NULL, `encryptedContent` TEXT NOT NULL, " +
                    "`contentIv` TEXT NOT NULL, `replyToId` TEXT, `createdAt` TEXT NOT NULL, " +
                    "`enqueuedAtMs` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, PRIMARY KEY(`clientId`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_whisper_outbox_enqueuedAtMs` ON `whisper_outbox` (`enqueuedAtMs`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_whisper_outbox_receiverId` ON `whisper_outbox` (`receiverId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `whisper_local_tombstones` (`messageId` TEXT NOT NULL, " +
                    "`deletedAtMs` INTEGER NOT NULL, PRIMARY KEY(`messageId`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_whisper_local_tombstones_deletedAtMs` ON `whisper_local_tombstones` (`deletedAtMs`)")
        }
    }

    // P1-03/P1-04: music_tracks.stableId + indexes for music player performance + future PK migration
    private val MIGRATION_52_53 = object : Migration(52, 53) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE music_tracks ADD COLUMN stableId TEXT NOT NULL DEFAULT ''")
            // Backfill stableId deterministically: prefer path > sourceUrl > uri
            db.execSQL("UPDATE music_tracks SET stableId = COALESCE(path, sourceUrl, uri) WHERE stableId = '' OR stableId IS NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_lastPlayed` ON `music_tracks` (`lastPlayed`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_playCount` ON `music_tracks` (`playCount`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_sourceUrl` ON `music_tracks` (`sourceUrl`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_path` ON `music_tracks` (`path`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_stableId` ON `music_tracks` (`stableId`)")
        }
    }

    private val MIGRATION_49_50 = object : Migration(49, 50) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // FIX: column name is protocolVersion (camelCase) per @ColumnInfo entity,
            // NOT protocol_version. Previous build shipped snake_case and crashed with
            // "Migration didn't properly handle: whisper_messages ... Expected protocolVersion Found protocol_version".
            db.execSQL("ALTER TABLE whisper_messages ADD COLUMN protocolVersion INTEGER NOT NULL DEFAULT 0")
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
        .addMigrations(MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51, MIGRATION_51_52, MIGRATION_52_53)
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
            // v1.1.0->v1.1.1 FIX: if a user hits a missing migration path (e.g. 44->52
            // before this fix shipped), Room throws "A migration from X to Y was
            // required but not found" on the main thread and crash-loops forever at
            // MusicPlayerService.onCreate. Treat it as unrecoverable schema drift
            // (same as hash mismatch) — delete and rebuild so the app can launch.
            // Data loss is preferable to a permanent crash; the explicit migrations
            // above make this path unreachable for current/future versions.
            // Also handle "Migration didn't properly handle" (e.g. protocol_version vs
            // protocolVersion typo in 49->50) which leaves the DB in a validated-failure
            // state that otherwise crash-loops forever without recovery.
            val isMissingMigration = e is IllegalStateException &&
                (e.message?.contains("A migration from") == true ||
                    e.message?.contains("Migration didn't properly handle") == true)

            if (isCorruptOrWrongKey || isLegacyHashMismatch || isMissingMigration) {
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
                    .addMigrations(MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51, MIGRATION_51_52, MIGRATION_52_53)
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
    fun provideWhisperOutboxDao(database: AppDatabase): com.frerox.toolz.data.whisper.WhisperOutboxDao {
        return database.whisperOutboxDao()
    }

    @Provides
    fun provideWhisperLocalTombstoneDao(database: AppDatabase): com.frerox.toolz.data.whisper.WhisperLocalTombstoneDao {
        return database.whisperLocalTombstoneDao()
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
