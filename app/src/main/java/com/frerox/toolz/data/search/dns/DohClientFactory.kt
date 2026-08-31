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
private data class DnsKey(val provider: String, val customDns: String, val nextDnsId: String)
@Singleton
class DohClientFactory @Inject constructor(private val settingsRepository: SettingsRepository) {
    private val baseClient: OkHttpClient = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).writeTimeout(8, TimeUnit.SECONDS).followRedirects(true).build()
    private val cache = AtomicReference<Pair<DnsKey, OkHttpClient>?>(null)
    suspend fun getClient(): OkHttpClient {
        val provider = settingsRepository.searchDnsProvider.first()
        val customDns = settingsRepository.searchCustomDns.first()
        val nextDnsId = settingsRepository.searchNextDnsId.first()
        val key = DnsKey(provider, customDns, nextDnsId)
        cache.get()?.let{ (k,c)-> if(k==key) return c }
        val dns: Dns = withContext(Dispatchers.IO){
            try{
                val primary: Dns = when(provider){
                    "ADGUARD"->doh("https://dns.adguard-dns.com/dns-query","94.140.14.14")
                    "CLOUDFLARE"->doh("https://cloudflare-dns.com/dns-query","1.1.1.1","1.0.0.1")
                    "GOOGLE"->doh("https://dns.google/dns-query","8.8.8.8","8.8.4.4")
                    "QUAD9"->doh("https://dns.quad9.net/dns-query","9.9.9.9")
                    "NEXTDNS"->{
                        if (nextDnsId.isBlank()) {
                            android.util.Log.w("DohFactory","NEXTDNS id blank — falling back to SYSTEM")
                            Dns.SYSTEM
                        } else {
                            val url="https://dns.nextdns.io/$nextDnsId"
                            doh(url,"45.90.28.0","45.90.30.0")
                        }
                    }
                    "CUSTOM"->{ val url=if(customDns.startsWith("http")) customDns else if(customDns.isNotBlank()) "https://$customDns/dns-query" else ""; if(url.startsWith("http")) doh(url) else Dns.SYSTEM }
                    else->Dns.SYSTEM
                }
                if(primary===Dns.SYSTEM) Dns.SYSTEM else ResilientDns(primary, Dns.SYSTEM)
            } catch(e:Exception){ android.util.Log.e("DohFactory","dns build failed",e); Dns.SYSTEM }
        }
        val client = baseClient.newBuilder().dns(dns).connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
        cache.set(key to client); return client
    }
    private fun doh(url: String, vararg ips: String): Dns {
        val b=DnsOverHttps.Builder().client(baseClient).url(url.toHttpUrl())
        if(ips.isNotEmpty()) b.bootstrapDnsHosts(ips.map{ InetAddress.getByName(it) })
        return b.build()
    }
    private class ResilientDns(private val primary:Dns, private val fallback:Dns= Dns.SYSTEM): Dns{
        override fun lookup(hostname:String): List<InetAddress> = try{ primary.lookup(hostname)} catch(e:Exception){ try{ fallback.lookup(hostname)} catch(_:Exception){ throw e } }
    }
}
