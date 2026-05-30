package com.frerox.toolz.di

import android.content.Context
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.VibrationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UtilModule {

    @Provides
    @Singleton
    fun provideVibrationManager(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): VibrationManager {
        return VibrationManager(context, settingsRepository)
    }
}