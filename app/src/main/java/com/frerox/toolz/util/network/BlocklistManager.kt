package com.frerox.toolz.util.network

import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlocklistManager @Inject constructor(private val client: OkHttpClient) {
    suspend fun fetchList(url: String): List<String> {
        val data = fetchData(url)
        return parseData(data)
    }

    fun parseData(data: String): List<String> {
        return data.lines().filter { it.isNotBlank() && !it.startsWith("#") }
    }

    private fun fetchData(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }
}
