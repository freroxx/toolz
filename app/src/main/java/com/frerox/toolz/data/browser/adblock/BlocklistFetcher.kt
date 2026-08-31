/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.browser.adblock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class BlocklistFetcher @Inject constructor(){
    suspend fun fetchListFromNetwork(url: String): Set<String> = withContext(Dispatchers.IO){
        try{
            val client=OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
            val req=Request.Builder().url(url).header("Accept","text/plain, */*").header("User-Agent","Mozilla/5.0").build()
            val resp=client.newCall(req).execute()
            if(!resp.isSuccessful) return@withContext emptySet()
            val body=resp.body?.string() ?: return@withContext emptySet()
            RuleParser.parseBlocklistText(body)
        } catch(e:Exception){ emptySet() }
    }
}
