package com.frerox.toolz.di

import android.content.Context
import androidx.room.Room
import com.frerox.toolz.data.AppDatabase
import com.frerox.toolz.data.notepad.NoteDao
import com.frerox.toolz.data.music.MusicDao
import com.frerox.toolz.data.steps.StepDao
import com.frerox.toolz.data.math.MathHistoryDao
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
        .fallbackToDestructiveMigration()
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
}
