package com.frerox.toolz.di

import com.frerox.toolz.data.catalog.CatalogRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CatalogClient

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {

    @Provides
    @Singleton
    @CatalogClient
    fun provideCatalogOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideCatalogRepository(
        @CatalogClient okHttpClient: OkHttpClient
    ): CatalogRepository {
        return CatalogRepository(okHttpClient)
    }
}
