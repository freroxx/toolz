package com.frerox.toolz.di

import android.content.Context
import com.frerox.toolz.data.cleaner.CleanerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CleanerModule {

    @Provides
    @Singleton
    fun provideCleanerRepository(
        @ApplicationContext context: Context
    ): CleanerRepository = CleanerRepository(context)
}
