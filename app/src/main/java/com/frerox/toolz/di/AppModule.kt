/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.di

import com.frerox.toolz.data.whisper.session.KeystoreSessionSecretProtector
import com.frerox.toolz.data.whisper.session.WhisperSessionSecretProtector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Application-lifetime coroutine scope.
 *
 * Used for fire-and-forget work that must survive ViewModel/flow cancellation,
 * e.g. realtime channel teardown after a collector leaves (a plain `launch {}`
 * inside `awaitClose {}` never runs because the ProducerScope is already
 * cancelled), or presence-off signals from `onCleared()`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // V6 (planwhisper.md §3.1): ratchet session persistence binds through a protector
    // seam so JVM tests can substitute an in-memory wrapper (no Keystore on CI).
    @Provides
    @Singleton
    fun provideWhisperSessionSecretProtector(
        impl: KeystoreSessionSecretProtector,
    ): WhisperSessionSecretProtector = impl
}
