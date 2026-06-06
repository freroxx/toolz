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
import com.frerox.toolz.data.ai.LrcLibService
import com.frerox.toolz.data.device.DeviceSpecsApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(MessageContentAdapter())
        .add(ClaudeMessageAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideKotlinxJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

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

    @Provides
    @Singleton
    fun provideLrcLibService(okHttpClient: OkHttpClient, moshi: Moshi): LrcLibService =
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/api/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LrcLibService::class.java)

    @Provides
    @Singleton
    fun provideDeviceSpecsApi(okHttpClient: OkHttpClient, json: Json): DeviceSpecsApi =
        Retrofit.Builder()
            .baseUrl("https://toolz-app.vercel.app/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DeviceSpecsApi::class.java)
}
