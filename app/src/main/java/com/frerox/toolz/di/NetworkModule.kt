package com.frerox.toolz.di

import com.frerox.toolz.data.ai.ClaudeMessageAdapter
import com.frerox.toolz.data.ai.MessageContentAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.frerox.toolz.data.ai.OpenAiService

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Provides the Moshi instance used by Retrofit for all JSON
     * serialization and deserialization.
     *
     * ADAPTER REGISTRATION ORDER MATTERS:
     *
     * Custom adapters MUST be added before [KotlinJsonAdapterFactory].
     * Moshi resolves adapters in registration order — if KotlinJsonAdapterFactory
     * comes first, it claims every Kotlin class via reflection before the custom
     * adapters get a chance to handle their specific types.
     *
     * [MessageContentAdapter] — handles [MessageContent] sealed class:
     *   serializes as a plain JSON string for text turns, or a JSON array
     *   of content blocks for vision turns.
     *
     * [ClaudeMessageAdapter] — handles [ClaudeMessage.content: Any]:
     *   serializes as a plain string or a typed block array depending on
     *   whether the turn includes an image.
     *
     * [KotlinJsonAdapterFactory] — handles all remaining Kotlin data classes
     *   via reflection, respecting @Json field name annotations.
     */
    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(MessageContentAdapter())   // ← must be before KotlinJsonAdapterFactory
        .add(ClaudeMessageAdapter())    // ← must be before KotlinJsonAdapterFactory
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
        .build()

    /**
     * The base URL here is a placeholder — all OpenAiService methods use
     * Retrofit's @Url annotation to supply the full endpoint URL dynamically.
     * Retrofit still requires a syntactically valid base URL at construction time.
     */
    @Provides
    @Singleton
    fun provideRetrofit(moshi: Moshi, okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideOpenAiService(retrofit: Retrofit): OpenAiService =
        retrofit.create(OpenAiService::class.java)
}