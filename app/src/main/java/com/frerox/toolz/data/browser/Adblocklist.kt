package com.frerox.toolz.data.browser

import java.net.URI
import java.util.concurrent.atomic.AtomicReference

/**
 * AdBlock Engine V3
 * Optimized for dynamic rule indexing and deep subdomain matching.
 * Handles both pure domains and path-based pattern rules.
 */
object AdBlockList {

    // ────────────────────────────────────────────────────────── Static built-in rules
    private val staticRules: HashSet<String> = hashSetOf(
        // GOOGLE
        "doubleclick.net", "google-analytics.com", "googletagmanager.com",
        "googletagservices.com", "googlesyndication.com", "pagead2.googlesyndication.com",
        "analytics.google.com", "app-measurement.com", "adservice.google.com",
        "ad.doubleclick.net", "stats.g.doubleclick.net", "pagead.l.google.com",
        "partnerad.l.google.com", "googleadservices.com", "googleads.g.doubleclick.net",
        "ad.plus.google.com", "google-analytics.l.google.com", "googleadsserving.cn",
        "googleadservices.com", "googleads4.com", "googletagmanager.cn", "googletagmanager.org",
        "google-analytics.biz", "google-analytics.cn", "gtm.digital", "gtmserver.com",
        "googleoptimize.com", "optimize.google.com", "region1.analytics.google.com",
        "region2.analytics.google.com", "region3.analytics.google.com",
        "clients2.google.com", "clients4.google.com", "clients6.google.com",
        "pagead-googlehosted.com", "pagead46.googlesyndication.com",
        "googleadservices.com", "googleads.com", "googleadsserving.cn",

        // META / FACEBOOK
        "connect.facebook.net", "pixel.facebook.com", "fbsbx.com",
        "facebook.com/tr", "fbcdn.net", "instagram.com/pixel",
        "an.facebook.com", "graph.facebook.com/tr", "facebook.net",
        "fbcdn-profile.com", "fbcdn-sphotos.com", "fbstatic.com",
        "fbevents.com", "facebookbrand.com/tr", "facebook.com/insights",
        "facebook.com/impression", "facebook.com/activity", "facebook.com/convert",
        "facebook.com/log", "facebook.com/events", "facebook.com/ads",
        "fb.com/tr", "instagram.com/tr", "cdninstagram.com/tr",
        "whatsapp.com/tr", "messenger.com/tr",

        // AMAZON / OTHERS
        "amazon-adsystem.com", "aaxads.com", "mads.amazon.com", "assoc-amazon.com",
        "aax-eu.amazon-adsystem.com", "aax-us-east.amazon-adsystem.com",
        "amazon-adsystem.net", "amazonadvertising.com", "amazoncpm.com",
        "amazon-adsystem.co.uk", "dsp.amazon.com", "ads.pinterest.com",
        "log.pinterest.com", "static.ads-twitter.com", "ads-api.twitter.com", "ads.tiktok.com",
        "analytics.tiktok.com", "app.getsentry.com", "api.bugsnag.com", "adm.hotjar.com",
        "script.hotjar.com", "surveys.hotjar.com", "insights.hotjar.com", "identify.hotjar.com",
        "luckyorange.com", "mouseflow.com", "clarity.ms", "vortex.data.microsoft.com"
    )

    // ────────────────────────────────────────────────────────── Dynamic Indexes
    private val activeDomains  = AtomicReference<Set<String>>(emptySet())
    private val activePatterns = AtomicReference<Set<String>>(emptySet())
    private val exceptionRules = AtomicReference<Set<String>>(emptySet())
    private val allowlist      = AtomicReference<Set<String>>(emptySet())

    private val rawCustomBlocked = AtomicReference<Set<String>>(emptySet())
    private val rawImported      = AtomicReference<Set<String>>(emptySet())
    
    @Volatile
    private var isEngineReady = false

    init { refreshIndex() }

    /**
     * Cleans adblock-style markers and URL schemes from a rule to get a pure domain or path fragment.
     */
    private fun cleanRule(rule: String): String {
        var cleaned = rule.trim().lowercase()
        if (cleaned.isEmpty()) return ""

        // Strip exception marker
        if (cleaned.startsWith("@@")) {
            cleaned = cleaned.substring(2)
        }
        // Strip adblock domain anchor
        if (cleaned.startsWith("||")) {
            cleaned = cleaned.substring(2)
        }
        // Strip URL scheme if present
        if (cleaned.startsWith("http://")) {
            cleaned = cleaned.substring(7)
        } else if (cleaned.startsWith("https://")) {
            cleaned = cleaned.substring(8)
        }
        // Strip adblock separator / options
        if (cleaned.contains("^")) {
            cleaned = cleaned.substringBefore("^")
        }
        if (cleaned.contains("$")) {
            cleaned = cleaned.substringBefore("$")
        }
        // Strip trailing slash if it's a domain-only rule
        if (cleaned.endsWith("/") && !cleaned.substring(0, cleaned.length - 1).contains("/")) {
            cleaned = cleaned.removeSuffix("/")
        }
        return cleaned.trim()
    }

    private fun log(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Exception) {
            println("[$tag] $msg")
        }
    }

    /**
     * Re-categorizes all rules into domain-only and path-patterns for optimal matching.
     */
    fun refreshIndex() {
        val allRules = staticRules + rawCustomBlocked.get() + rawImported.get()
        val domains  = mutableSetOf<String>()
        val patterns = mutableSetOf<String>()

        allRules.forEach {
            val cleaned = cleanRule(it)
            if (cleaned.isEmpty()) return@forEach
            if (cleaned.contains("/")) patterns.add(cleaned)
            else domains.add(cleaned)
        }

        activeDomains.set(domains)
        activePatterns.set(patterns)
        isEngineReady = true
        
        log("AdBlockList", "Engine Ready: ${domains.size} domains, ${patterns.size} patterns active")
    }

    fun updateCustomLists(blocked: Set<String>, allowed: Set<String>) {
        rawCustomBlocked.set(blocked.map { cleanRule(it) }.toSet())
        allowlist.set(allowed.map { cleanRule(it) }.toSet())
        refreshIndex()
    }

    fun updateImportedList(rules: Set<String>) {
        val blocked    = mutableSetOf<String>()
        val exceptions = mutableSetOf<String>()
        
        rules.forEach { 
            val lower = it.lowercase().trim()
            if (lower.startsWith("@@")) {
                exceptions.add(cleanRule(lower))
            } else {
                blocked.add(cleanRule(lower))
            }
        }
        
        rawImported.set(blocked)
        exceptionRules.set(exceptions)
        refreshIndex()
    }

    // ────────────────────────────────────────────────────────── Core Logic

    fun isBlocked(url: String): Boolean {
        if (!isEngineReady || url.isBlank()) return false
        
        val fullUrlLower = url.lowercase().trim()
        
        // 1. Quick Allowlist & Exceptions check (Highest Priority)
        if (allowlist.get().any { it.isNotBlank() && fullUrlLower.contains(it) }) return false
        if (exceptionRules.get().any { it.isNotBlank() && fullUrlLower.contains(it) }) return false

        // 2. Extract Host for deep domain matching
        val host = try {
            val uri = URI(if (url.contains("://")) url else "https://$url")
            uri.host?.lowercase()
        } catch (_: Exception) {
            // Manual host extraction fallback for complex/malformed tracker URLs
            url.substringAfter("://").substringBefore("/").substringBefore(":").lowercase()
        } ?: ""

        if (host.isNotBlank()) {
            if (deepDomainMatch(host, activeDomains.get())) return hit("Domain", host)
        }
        
        // 3. Pattern Matching (contains path/segments)
        // Optimization: only check if url actually has a path or query
        if (url.contains("/") || url.contains("?")) {
            if (activePatterns.get().any { it.isNotBlank() && fullUrlLower.contains(it) }) {
                return hit("Pattern", url)
            }
        }
        
        // 4. Built-in path fragments fallback
        if (blockedPaths.any { fullUrlLower.contains(it) }) return hit("StaticFragment", url)
        
        return false
    }

    /**
     * Recursively checks host and all parent domains.
     * e.g. "a.b.c.com" -> ["a.b.c.com", "b.c.com", "c.com"]
     */
    private fun deepDomainMatch(host: String, targetSet: Set<String>): Boolean {
        if (targetSet.isEmpty() || host.isBlank()) return false
        if (targetSet.contains(host)) return true

        var current = host
        while (current.contains(".")) {
            current = current.substringAfter(".")
            if (current.isBlank() || publicSuffixes.contains(current)) break
            if (targetSet.contains(current)) return true
        }
        return false
    }

    private fun hit(type: String, target: String): Boolean {
        log("AdBlockList", "Rule Hit [$type]: $target")
        return true
    }

    // ────────────────────────────────────────────────────────── Static Data
    
    private val blockedPaths: Set<String> = setOf(
        "/pagead/", "/ads/", "/adserv", "/adsystem", "/tracker", "/pixel.", "/beacon.", 
        "/telemetry", "/analytics", "/collect", "/gtm.js", "/clarity.", "/heatmap"
    )

    private val publicSuffixes: Set<String> = setOf(
        "com", "org", "net", "edu", "gov", "mil", "int", "co.uk", "co.jp", "co.kr", "io", "me"
    )

    fun totalCount(): Int = activeDomains.get().size + activePatterns.get().size
}
