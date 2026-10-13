package com.frerox.toolz.di

import com.frerox.toolz.data.skin.SkinApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SkinModule {
    @Provides
    @Singleton
    fun provideSkinApi(okHttpClient: OkHttpClient, json: Json): SkinApi =
        Retrofit.Builder()
            .baseUrl("https://toolz-app.vercel.app/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SkinApi::class.java)
}
