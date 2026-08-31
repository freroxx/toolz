/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.browser.nextdns
import com.frerox.toolz.ui.screens.search.NextDnsHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class NextDnsClient @Inject constructor(){
    suspend fun checkHealth(nextDnsId:String): NextDnsHealth = withContext(Dispatchers.IO){
        if(nextDnsId.isBlank()) return@withContext NextDnsHealth.NOT_LINKED
        try{
            val bootstrapClient=OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
            val dns=DnsOverHttps.Builder().client(bootstrapClient).url("https://dns.nextdns.io/$nextDnsId".toHttpUrl()).bootstrapDnsHosts(listOf(InetAddress.getByName("45.90.28.0"), InetAddress.getByName("45.90.30.0"))).build()
            val client=OkHttpClient.Builder().dns(dns).connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
            val req=Request.Builder().url("https://test.nextdns.io/?_=${System.currentTimeMillis()}").header("User-Agent","Mozilla/5.0").header("Cache-Control","no-cache").build()
            val resp=client.newCall(req).execute()
            val body=resp.body?.string() ?: return@withContext NextDnsHealth.ERROR
            val isOk=body.contains("\"status\":\"ok\"")||body.contains("\"status\": \"ok\"")
            val hasConfig=body.contains("\"configuration\":\"$nextDnsId\"")||body.contains("\"configuration\": \"$nextDnsId\"")
            val isUnconf=body.contains("\"status\":\"unconfigured\"")||body.contains("\"status\": \"unconfigured\"")
            when{
                isOk && hasConfig -> NextDnsHealth.CONNECTED
                isOk && !hasConfig -> NextDnsHealth.NOT_LINKED
                isUnconf -> NextDnsHealth.NOT_LINKED
                else -> NextDnsHealth.ERROR
            }
        } catch(_:Exception){ NextDnsHealth.ERROR }
    }
}
