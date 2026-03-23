package com.frerox.toolz.di

import com.frerox.toolz.data.update.UpdateConstants
import com.frerox.toolz.data.update.UpdateService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideUpdateService(okHttpClient: OkHttpClient, moshi: Moshi): UpdateService {
        return Retrofit.Builder()
            .baseUrl(UpdateConstants.GITHUB_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(UpdateService::class.java)
    }
}
