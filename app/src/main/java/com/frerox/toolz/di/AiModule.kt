package com.frerox.toolz.di

import com.frerox.toolz.data.ai.AiRepositoryImpl
import com.frerox.toolz.data.ai.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Tells Hilt: "whenever something asks for [ChatRepository],
 * provide the [AiRepositoryImpl] singleton."
 *
 * Why @Binds instead of @Provides?
 * - @Binds is zero-overhead — Dagger implements it as a direct cast,
 *   no method body is generated, no extra factory class is created.
 * - @Provides would require an abstract module + a companion object or
 *   a separate non-abstract module, adding unnecessary boilerplate.
 *
 * The module must be abstract because @Binds methods are abstract by
 * definition — Dagger generates the implementation at compile time.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        impl: AiRepositoryImpl,
    ): ChatRepository
}