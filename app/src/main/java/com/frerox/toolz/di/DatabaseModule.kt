package com.frerox.toolz.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
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
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.search.SearchDao
import com.frerox.toolz.util.security.KeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
        
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
        .openHelperFactory(factory)
        .fallbackToDestructiveMigration() // Changed to true to resolve the schema mismatch crash
        .build()
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
}
