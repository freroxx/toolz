/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.downloader

import com.frerox.toolz.BuildConfig
import com.frerox.toolz.data.catalog.CatalogRepository
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadzClient

@Module
@InstallIn(SingletonComponent::class)
object DownloaderModule {

    @Provides
    @Singleton
    @DownloadzClient
    fun provideDownloadzOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Toolz/1.0 (Android; Media Downloader)")
                .header("Accept", "application/json")
            chain.proceed(req.build())
        }
        .build()

    @Provides
    @Singleton
    fun provideDownloadzService(
        @DownloadzClient okHttpClient: OkHttpClient,
        json: Json,
    ): DownloadzService {
        // Never crash when the server URL is blank (e.g. local/dev builds).
        // Repository.isApiConfigured() gates remote calls; workers re-check BuildConfig.
        // Retrofit requires a valid http(s) base URL, so fall back to a placeholder.
        val rawBase = BuildConfig.DOWNLOADZ_API_URL.trim().trimEnd('/')
        val base = if (rawBase.startsWith("http://") || rawBase.startsWith("https://")) "$rawBase/" else "https://localhost/"
        return Retrofit.Builder()
            .baseUrl(base)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DownloadzService::class.java)
    }

    @Provides
    @Singleton
    fun provideMediaDownloaderRepository(
        service: DownloadzService,
        @DownloadzClient okHttpClient: OkHttpClient,
        catalogRepository: CatalogRepository,
        @ApplicationContext context: Context,
    ): MediaDownloaderRepository =
        MediaDownloaderRepository(service, okHttpClient, catalogRepository, context)
}
