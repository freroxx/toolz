package com.frerox.toolz.data.news

import retrofit2.http.GET
import retrofit2.http.Query

interface NewsApi {
    @GET("api/news")
    suspend fun getNews(
        @Query("appVersion") appVersion: String,
        @Query("platform") platform: String = "android"
    ): NewsFeedDto
}
