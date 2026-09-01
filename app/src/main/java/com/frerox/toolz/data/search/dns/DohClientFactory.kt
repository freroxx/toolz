/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.dns

import com.frerox.toolz.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and caches an [OkHttpClient] configured for the user's chosen DNS-over-HTTPS
 * provider (or plain system DNS). This is the single source of truth for DoH provider
 * config in the app — [com.frerox.toolz.data.search.WebSearchRepository] and any other
 * network-touching search code should obtain their client from here rather than building
 * their own DNS resolution, to avoid the provider list drifting out of sync between copies.
 *
 * The built client is cached and only rebuilt when the user's DNS settings actually change,
 * since building a [DnsOverHttps] resolver does bootstrap I/O.
 */
@Singleton
class DohClientFactory @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

        private data class DnsKey(val provider: String, val customDns: String, val nextDnsId: String, val nextDnsUrl: String)

            private val cache = AtomicReference<Pair<DnsKey, OkHttpClient>?>(null)

            /** Returns a client wired to the user's current DNS provider, rebuilding only if settings changed since the last call. */
            suspend fun getClient(): OkHttpClient {
                val provider = settingsRepository.searchDnsProvider.first()
                val customDns = settingsRepository.searchCustomDns.first()
                val nextDnsId = settingsRepository.searchNextDnsId.first()
                val nextDnsUrl = settingsRepository.searchNextDnsDnsUrl.first()
                val key = DnsKey(provider, customDns, nextDnsId, nextDnsUrl)

                cache.get()?.let { (cachedKey, cachedClient) -> if (cachedKey == key) return cachedClient }

                val dns = withContext(Dispatchers.IO) { buildDns(provider, customDns, nextDnsId, nextDnsUrl) }
                val client = baseClient.newBuilder()
                .dns(dns)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()

                cache.set(key to client)
                return client
            }

            private fun buildDns(provider: String, customDns: String, nextDnsId: String, nextDnsUrl: String = ""): Dns = try {
                val primary = resolveProvider(provider, customDns, nextDnsId, nextDnsUrl)
                // Wrap every DoH provider (but not plain system DNS) in a fallback to system DNS,
                // so a DoH provider outage degrades to normal resolution instead of breaking search.
                if (primary === Dns.SYSTEM) Dns.SYSTEM else ResilientDns(primary, Dns.SYSTEM)
            } catch (e: Exception) {
                android.util.Log.e("DohClientFactory", "DNS client build failed for provider=$provider", e)
                Dns.SYSTEM
            }

            private fun resolveProvider(provider: String, customDns: String, nextDnsId: String, nextDnsUrl: String = ""): Dns = when (provider) {
                "ADGUARD" -> doh("https://dns.adguard-dns.com/dns-query", "94.140.14.14")
                "ADGUARD_FAMILY" -> doh("https://dns-family.adguard-dns.com/dns-query", "94.140.14.15")
                "CLOUDFLARE" -> doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1")
                "CLOUDFLARE_FAMILY" -> doh("https://family.cloudflare-dns.com/dns-query", "1.1.1.3")
                "GOOGLE" -> doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4")
                "QUAD9" -> doh("https://dns.quad9.net/dns-query", "9.9.9.9")
                "OPENDNS" -> doh("https://doh.opendns.com/dns-query", "208.67.222.222")
                "CONTROLD" -> doh("https://freedns.controld.com/p1", "76.76.2.0")
                "CLEANBROWSING" -> doh("https://doh.cleanbrowsing.org/doh/family-filter/", "185.228.168.168")
                "CLEANBROWSING_SECURITY" -> doh("https://doh.cleanbrowsing.org/doh/security-filter/", "185.228.168.168")
                "NEXTDNS" -> resolveNextDns(nextDnsId, nextDnsUrl)
                "CUSTOM" -> resolveCustom(customDns)
                else -> Dns.SYSTEM
            }

            private fun resolveNextDns(nextDnsId: String, nextDnsUrl: String): Dns {
                // A custom DoH hostname saved via the setup flow takes precedence — it
                // supports IPv6 bootstrap (dns.nextdns.io only bootstraps v4).
                if (nextDnsUrl.isNotBlank()) {
                    val url = if (nextDnsUrl.startsWith("http")) nextDnsUrl else "https://$nextDnsUrl"
                    return doh(url, "45.90.28.0", "45.90.30.0")
                }
                // Sanitize: users paste full hostnames ("abcdef.dns.nextdns.io") or URLs
                // into the ID field — building "https://dns.nextdns.io/abcdef.dns.nextdns.io"
                // silently breaks all resolution, so always reduce to the bare profile id.
                val cleanId = com.frerox.toolz.data.browser.nextdns.NextDnsClient.sanitizeId(nextDnsId)
                if (cleanId.isBlank()) {
                    android.util.Log.w("DohClientFactory", "NextDNS selected but id is blank — falling back to AdGuard DoH so filtering stays ON (was: silent system DNS, which made NextDNS appear active while blocking nothing)")
                    // Previously this returned Dns.SYSTEM: the UI showed NextDNS selected,
                    // but resolution went through unfiltered system DNS — the "NextDNS is
                    // completely dead" report. A safe DoH default keeps real filtering.
                    return doh("https://dns.adguard-dns.com/dns-query", "94.140.14.14")
                }
                return doh("https://dns.nextdns.io/$cleanId", "45.90.28.0", "45.90.30.0")
            }

            private fun resolveCustom(customDns: String): Dns {
                val url = when {
                    customDns.startsWith("http") -> customDns
                    customDns.isNotBlank() -> "https://$customDns/dns-query"
                    else -> return Dns.SYSTEM
                }
                return doh(url)
            }

            private fun doh(url: String, vararg bootstrapIps: String): Dns {
                val builder = DnsOverHttps.Builder().client(baseClient).url(url.toHttpUrl())
                if (bootstrapIps.isNotEmpty()) {
                    builder.bootstrapDnsHosts(bootstrapIps.map { InetAddress.getByName(it) })
                }
                return builder.build()
            }

            /** Tries [primary] first; on any failure (provider outage, malformed response) falls back to [fallback]. */
            private class ResilientDns(
                private val primary: Dns,
                    private val fallback: Dns = Dns.SYSTEM,
            ) : Dns {
                override fun lookup(hostname: String): List<InetAddress> = try {
                    primary.lookup(hostname)
                } catch (primaryFailure: Exception) {
                    try {
                        fallback.lookup(hostname)
                    } catch (_: Exception) {
                        throw primaryFailure
                    }
                }
            }
}
