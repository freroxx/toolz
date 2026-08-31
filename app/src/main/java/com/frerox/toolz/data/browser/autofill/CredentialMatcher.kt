/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.browser.autofill
import com.frerox.toolz.data.password.PasswordEntity
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class CredentialMatcher @Inject constructor(){
    private val publicSuffixes=setOf("com","org","net","edu","gov","mil","int","io","me","ai","app","dev","co.uk","org.uk","co.jp","com.au","info","biz","tv","cc","top","xyz")
    fun normalizeHost(urlOrHost:String):String{ var h=urlOrHost.trim().lowercase(); if(h.startsWith("http://")) h=h.removePrefix("http://") else if(h.startsWith("https://")) h=h.removePrefix("https://"); h=h.substringBefore("/").substringBefore(":").substringBefore("?").trim(); if(h.startsWith("www.")) h=h.removePrefix("www."); return h }
    fun registrableDomain(host:String):String{ val n=normalizeHost(host); val parts=n.split("."); if(parts.size<=2) return n; val lastTwo=parts.takeLast(2).joinToString("."); if(publicSuffixes.contains(lastTwo)&&parts.size>=3) return parts.takeLast(3).joinToString("."); return parts.takeLast(2).joinToString(".") }
    fun findBestMatches(host:String, all:List<PasswordEntity>):List<PasswordEntity>{
        val norm=normalizeHost(host); val reg=registrableDomain(norm)
        return all.filter{ e->
            val urlHost=e.url?.let{ try{ java.net.URI(it).host?.lowercase() ?: normalizeHost(it)} catch(_:Exception){ normalizeHost(it)} } ?: ""
            val nameHost=normalizeHost(e.name)
            listOf(urlHost, nameHost).any{ c-> c==norm || c==reg || normalizeHost(c)==norm || registrableDomain(c)==reg }
        }.distinctBy{ it.id}.sortedWith(compareByDescending<PasswordEntity>{ it.isComplete}.thenByDescending{ it.lastUsedAt})
    }
}
