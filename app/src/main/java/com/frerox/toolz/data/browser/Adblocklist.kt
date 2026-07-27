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
    private val activePatterns = AtomicReference<List<Regex>>(emptyList())
    private val exceptionRules = AtomicReference<Set<String>>(emptySet())
    private val exceptionPatterns = AtomicReference<List<Regex>>(emptyList())
    private val allowlist      = AtomicReference<Set<String>>(emptySet())

    private val rawCustomBlocked = AtomicReference<Set<String>>(emptySet())
    private val rawImported      = AtomicReference<Set<String>>(emptySet())
    
    @Volatile
    private var isEngineReady = false

    init { refreshIndex() }

    /**
     * Cleans adblock-style markers and URL schemes from a rule to get a pure domain or pattern fragment.
     * Returns null if the rule is not a network rule (e.g. element hiding).
     */
    private fun cleanRule(rule: String): String? {
        var cleaned = rule.trim().lowercase()
        if (cleaned.isEmpty()) return null

        // Ignore CSS rules / element hiding
        if (cleaned.contains("##") || cleaned.contains("#?#") || cleaned.contains("#@#")) {
            return null
        }

        // Strip exception marker for parsing pattern
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
        
        // Strip adblock separator / options (e.g. $third-party, ^)
        // We keep '*' for regex conversion later
        if (cleaned.contains("$")) {
            cleaned = cleaned.substringBefore("$")
        }
        if (cleaned.contains("^")) {
            cleaned = cleaned.substringBefore("^")
        }

        // Strip trailing slash if it's a domain-only rule
        if (cleaned.endsWith("/") && !cleaned.substring(0, cleaned.length - 1).contains("/")) {
            cleaned = cleaned.removeSuffix("/")
        }
        
        return cleaned.trim().takeIf { it.isNotBlank() }
    }

    private fun log(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Exception) {
            println("[$tag] $msg")
        }
    }

    /**
     * Re-categorizes all rules into domain-only and wildcard-patterns for optimal matching.
     */
    fun refreshIndex() {
        val allRules = staticRules + rawCustomBlocked.get() + rawImported.get()
        val domains  = mutableSetOf<String>()
        val patterns = mutableListOf<Regex>()
        
        val excRules = mutableSetOf<String>()
        val excPatterns = mutableListOf<Regex>()

        allRules.forEach {
            val cleaned = cleanRule(it) ?: return@forEach
            // Ignore extremely short patterns (e.g. "ad") that cause massive false positives
            // unless they contain structural markers like / or *
            if (cleaned.length < 4 && !cleaned.contains("/") && !cleaned.contains("*")) return@forEach

            if (cleaned.contains("/") || cleaned.contains("*") || cleaned.contains("?")) {
                patterns.add(toRegex(cleaned))
            } else {
                domains.add(cleaned)
            }
        }
        
        // Exceptions need to be categorized too
        exceptionRules.get().forEach { 
            if (it.contains("/") || it.contains("*") || it.contains("?")) {
                excPatterns.add(toRegex(it))
            } else {
                excRules.add(it)
            }
        }

        activeDomains.set(domains)
        activePatterns.set(patterns)
        exceptionRules.set(excRules)
        exceptionPatterns.set(excPatterns)
        isEngineReady = true
        
        log("AdBlockList", "Engine Ready: ${domains.size} domains, ${patterns.size} patterns active")
    }

    private fun toRegex(pattern: String): Regex {
        // Convert ABP-style pattern to Regex
        // 1. Break by wildcards
        val parts = pattern.split("*", "?")
        // 2. Escape each part literally
        val escapedParts = parts.map { Regex.escape(it) }
        // 3. Join with .* (for *) and . (for ?) - we simplify both to .* for safety in this version
        val regexStr = escapedParts.joinToString(".*")
        
        return try {
            Regex(regexStr, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            // Fallback to literal if compilation fails
            Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
        }
    }

    fun updateCustomLists(blocked: Set<String>, allowed: Set<String>) {
        rawCustomBlocked.set(blocked.mapNotNull { cleanRule(it) }.toSet())
        allowlist.set(allowed.mapNotNull { cleanRule(it) }.toSet())
        refreshIndex()
    }

    fun updateImportedList(rules: Set<String>) {
        val blocked    = mutableSetOf<String>()
        val exceptions = mutableSetOf<String>()
        
        rules.forEach { 
            val lower = it.lowercase().trim()
            val cleaned = cleanRule(lower) ?: return@forEach
            if (lower.startsWith("@@")) {
                exceptions.add(cleaned)
            } else {
                blocked.add(cleaned)
            }
        }
        
        rawImported.set(blocked)
        exceptionRules.set(exceptions) // This is temporary, refreshIndex will re-categorize
        refreshIndex()
    }

    // ────────────────────────────────────────────────────────── Core Logic

    fun isBlocked(url: String): Boolean {
        if (!isEngineReady || url.isBlank()) return false
        
        // Normalize protocol-relative URLs
        val cleanUrl = if (url.startsWith("//")) "https:$url" else url
        val fullUrlLower = cleanUrl.lowercase().trim()
        
        // 1. Host Extraction
        val host = try {
            val uri = URI(if (cleanUrl.contains("://")) cleanUrl else "https://$cleanUrl")
            uri.host?.lowercase()
        } catch (_: Exception) {
            cleanUrl.substringAfter("://").substringBefore("/").substringBefore(":").lowercase()
        } ?: ""

        if (host.isNotBlank()) {
            // 2. Allowlist & Exceptions check (Highest Priority)
            if (allowlist.get().any { host == it || host.endsWith(".$it") }) return false
            if (exceptionRules.get().any { host == it || host.endsWith(".$it") }) return false
            if (exceptionPatterns.get().any { it.containsMatchIn(cleanUrl) }) return false

            // 3. Domain Blocking (with deep subdomain support)
            if (deepDomainMatch(host, activeDomains.get())) return hit("Domain", host)
        }
        
        // 4. Pattern Matching (contains path/segments/wildcards)
        if (activePatterns.get().any { it.containsMatchIn(cleanUrl) }) {
            return hit("Pattern", cleanUrl)
        }
        
        // 5. Built-in path fragments fallback
        if (blockedPaths.any { fullUrlLower.contains(it) }) return hit("StaticFragment", cleanUrl)
        
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
