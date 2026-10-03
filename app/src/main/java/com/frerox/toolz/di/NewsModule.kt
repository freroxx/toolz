package com.frerox.toolz.di

import android.content.Context
import androidx.room.Room
import com.frerox.toolz.data.news.NewsApi
import com.frerox.toolz.data.news.NewsDao
import com.frerox.toolz.data.news.NewsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NewsModule {
    @Provides
    @Singleton
    fun provideNewsApi(okHttpClient: OkHttpClient, json: Json): NewsApi =
        Retrofit.Builder()
            .baseUrl("https://toolz-app.vercel.app/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NewsApi::class.java)

    @Provides
    @Singleton
    fun provideNewsDatabase(@ApplicationContext context: Context): NewsDatabase =
        Room.databaseBuilder(context, NewsDatabase::class.java, "toolz_news_db")
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideNewsDao(db: NewsDatabase): NewsDao = db.newsDao()
}
