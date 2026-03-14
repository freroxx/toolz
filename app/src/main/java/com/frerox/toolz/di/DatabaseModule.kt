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
import com.frerox.toolz.data.clipboard.ClipboardDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "toolz_db"
        )
        .fallbackToDestructiveMigration(false)
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
    fun provideClipboardDao(database: AppDatabase): ClipboardDao {
        return database.clipboardDao()
    }
}
