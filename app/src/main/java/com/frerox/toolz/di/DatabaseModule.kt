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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        System.loadLibrary("sqlcipher")

        val dbName = "toolz_db"
        val passphrase = KeyManager.getOrCreateMasterKey(context)
        val factory = SupportOpenHelperFactory(passphrase)
        
        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
        .openHelperFactory(factory)
        .fallbackToDestructiveMigration(true)

        val db = builder.build()
        
        // Verify database is openable. SQLiteNotADatabaseException occurs when 
        // the file exists but isn't a SQLCipher database or the key is wrong.
        try {
            db.openHelper.writableDatabase
        } catch (e: Exception) {
            if (e is SQLiteNotADatabaseException || (e.message?.contains("file is not a database") == true)) {
                context.deleteDatabase(dbName)
                return builder.build()
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
}
