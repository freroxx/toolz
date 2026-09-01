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

    companion object {
        // test.nextdns.io returns pretty-printed JSON where the separator after
        // "status" is a TAB or arbitrary whitespace — a literal "\"status\": \"ok\""
        // contains() check never matches. Parse the status value tolerantly instead.
        private val STATUS_REGEX = Regex("\"status\"\\s*:\\s*\"([a-zA-Z]+)\"")

        fun parseNextDnsBody(body: String): NextDnsHealth {
            val status = STATUS_REGEX.find(body)?.groupValues?.get(1)?.lowercase()
            return when (status) {
                "ok" -> NextDnsHealth.CONNECTED
                // "default"/"unconfigured" = the request reached NextDNS but NOT through
                // this profile, so the profile isn't actively linked on this network.
                "unconfigured", "default" -> NextDnsHealth.NOT_LINKED
                else -> NextDnsHealth.ERROR
            }
        }

        /** Normalizes whatever the user/setup flow stored into a bare profile id. */
        fun sanitizeId(raw: String): String {
            var id = raw.trim().lowercase()
            id = id.removePrefix("https://").removePrefix("http://")
            if (id.contains("dns.nextdns.io/")) id = id.substringAfter("dns.nextdns.io/")
            id = id.removeSuffix(".dns.nextdns.io")
            id = id.substringBefore("/").substringBefore("?").substringBefore("&")
            return id.trim()
        }

        /**
         * The DoH endpoint itself is the real health oracle: a DoH query for a known
         * domain through the profile either answers (profile live, regardless of
         * linkage) or throws. Live-verified: test.nextdns.io can return an EMPTY 200
         * body on some networks, which the old body-parse-only logic reported as
         * ERROR even when the profile was working.
         */
        private fun dohProbeSucceeds(cleanId: String): Boolean = try {
            val bootstrapClient = OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
            val dns = DnsOverHttps.Builder().client(bootstrapClient)
                .url("https://dns.nextdns.io/$cleanId".toHttpUrl())
                .bootstrapDnsHosts(listOf(InetAddress.getByName("45.90.28.0"), InetAddress.getByName("45.90.30.0")))
                .build()
            // Resolve a host that always exists — any non-empty answer means the DoH
            // endpoint is alive and answering queries for this profile.
            dns.lookup("www.google.com").isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun checkHealth(nextDnsId: String): NextDnsHealth = withContext(Dispatchers.IO) {
        val cleanId = sanitizeId(nextDnsId)
        if (cleanId.isBlank()) return@withContext NextDnsHealth.NOT_LINKED

        // Probe 1: the DoH endpoint itself — authoritative for "profile resolves DNS".
        val dohAlive = dohProbeSucceeds(cleanId)

        // Probe 2: test.nextdns.io through the profile's own DoH resolution.
        val testUrl = "https://test.nextdns.io/?_=${System.currentTimeMillis()}"
        if (dohAlive) {
            try {
                val dns = DnsOverHttps.Builder().client(
                    OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
                ).url("https://dns.nextdns.io/$cleanId".toHttpUrl())
                    .bootstrapDnsHosts(listOf(InetAddress.getByName("45.90.28.0"), InetAddress.getByName("45.90.30.0")))
                    .build()
                val client = OkHttpClient.Builder().dns(dns).connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
                    .followRedirects(true).build()
                val req = Request.Builder().url(testUrl).header("User-Agent", "Mozilla/5.0").header("Cache-Control", "no-cache").build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val health = parseNextDnsBody(body)
                        if (health != NextDnsHealth.ERROR) return@withContext health
                    }
                }
            } catch (_: Exception) { }
        }

        // Probe 3: direct HTTP check via system DNS (302-challenge → NOT_LINKED).
        try {
            val directClient = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
                .followRedirects(true).build()
            val req = Request.Builder().url(testUrl).header("User-Agent", "Mozilla/5.0").header("Cache-Control", "no-cache").build()
            directClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val health = parseNextDnsBody(body)
                    if (health == NextDnsHealth.CONNECTED) return@withContext NextDnsHealth.CONNECTED
                }
            }
        } catch (_: Exception) { }

        // DoH endpoint answers but linkage tests were inconclusive (empty/challenge
        // bodies happen on some networks) — the profile IS resolving DNS, so report
        // connected rather than a false ERROR.
        if (dohAlive) NextDnsHealth.CONNECTED else NextDnsHealth.ERROR
    }
}
