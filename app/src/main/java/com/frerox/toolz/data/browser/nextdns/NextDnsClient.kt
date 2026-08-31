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
    suspend fun checkHealth(nextDnsId: String): NextDnsHealth = withContext(Dispatchers.IO) {
        val cleanId = nextDnsId.trim().lowercase().removeSuffix(".dns.nextdns.io")
        if (cleanId.isBlank()) return@withContext NextDnsHealth.NOT_LINKED

        fun parseNextDnsBody(body: String): NextDnsHealth {
            val isOk = body.contains("\"status\":\"ok\"", ignoreCase = true) ||
                       body.contains("\"status\": \"ok\"", ignoreCase = true)
            val hasConfig = body.contains(cleanId, ignoreCase = true)
            val isUnconf = body.contains("\"status\":\"unconfigured\"", ignoreCase = true) ||
                           body.contains("\"status\": \"unconfigured\"", ignoreCase = true)
            return when {
                isOk && hasConfig -> NextDnsHealth.CONNECTED
                isOk && !hasConfig -> NextDnsHealth.CONNECTED // If NextDNS is active but ID isn't in JSON echo, it's still connected
                isUnconf -> NextDnsHealth.NOT_LINKED
                else -> NextDnsHealth.ERROR
            }
        }

        // Try 1: DoH with custom profile
        try {
            val bootstrapClient = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
            val dns = DnsOverHttps.Builder().client(bootstrapClient).url("https://dns.nextdns.io/$cleanId".toHttpUrl())
                .bootstrapDnsHosts(listOf(InetAddress.getByName("45.90.28.0"), InetAddress.getByName("45.90.30.0"))).build()
            val client = OkHttpClient.Builder().dns(dns).connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
            val req = Request.Builder().url("https://test.nextdns.io/?_=${System.currentTimeMillis()}").header("User-Agent", "Mozilla/5.0").header("Cache-Control", "no-cache").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            if (!body.isNullOrBlank()) {
                val health = parseNextDnsBody(body)
                if (health != NextDnsHealth.ERROR) return@withContext health
            }
        } catch (_: Exception) { }

        // Try 2: Standard Direct HTTP check
        try {
            val directClient = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
            val req = Request.Builder().url("https://test.nextdns.io/?_=${System.currentTimeMillis()}").header("User-Agent", "Mozilla/5.0").header("Cache-Control", "no-cache").build()
            val resp = directClient.newCall(req).execute()
            val body = resp.body?.string()
            if (!body.isNullOrBlank()) {
                return@withContext parseNextDnsBody(body)
            }
        } catch (_: Exception) { }

        NextDnsHealth.ERROR
    }
}
