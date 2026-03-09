package com.frerox.toolz.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    // Content moved to PlayerModule.kt as per redesign requirements
}
