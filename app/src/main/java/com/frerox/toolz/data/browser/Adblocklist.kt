/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
        // GOOGLE AD & TRACKING SERVICES (pure ad-serving hosts only)
        "doubleclick.net", "google-analytics.com", "googletagmanager.com",
        "googletagservices.com", "googlesyndication.com", "pagead2.googlesyndication.com",
        "analytics.google.com", "app-measurement.com", "adservice.google.com",
        "ad.doubleclick.net", "stats.g.doubleclick.net", "pagead.l.google.com",
        "partnerad.l.google.com", "googleadservices.com", "googleads.g.doubleclick.net",
        "googleads.com", "googleadsserving.cn", "pagead-googlehosted.com",
        "adssettings.google.com", "crashlyticsreports-pa.googleapis.com",

        // META / FACEBOOK TRACKING PIXELS & ADS ONLY (never block CDN/static/app domains)
        "connect.facebook.net", "pixel.facebook.com", "an.facebook.com",
        "graph.facebook.com/tr", "fbevents.com", "b-api.facebook.com",
        "dedicatedmedia.facebook.com", "fndows.facebook.com",

        // AMAZON / CRITEO / TABOOLA / OUTBRAIN / AD NETWORKS
        "amazon-adsystem.com", "aaxads.com", "mads.amazon.com", "assoc-amazon.com",
        "aax-eu.amazon-adsystem.com", "aax-us-east.amazon-adsystem.com",
        "amazon-adsystem.net", "amazonadvertising.com", "amazoncpm.com",
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "scorecardresearch.com",
        "adroll.com", "rubiconproject.com", "pubmatic.com", "openx.net", "appnexus.com",
        "ads.pinterest.com", "static.ads-twitter.com", "ads-api.twitter.com",
        "ads.tiktok.com", "analytics.tiktok.com", "clarity.ms",
        "adnxs.com", "adform.net", "adcolony.com", "admob.com", "unityads.unity3d.com",
        "applovin.com", "chartboost.com", "inmobi.com", "vungle.com", "ironsrc.com",
        "moatads.com", "adsafeprotected.com", "doubleverify.com", "mediamath.com",
        "bidswitch.net", "casalemedia.com", "spotxchange.com", "teads.tv", "sharethrough.com",
        "33across.com", "liveintent.com", "magnetic.com", "smartadserver.com", "yieldmo.com",

        // TELEMETRY & USER TRACKING
        "mixpanel.com", "amplitude.com", "hotjar.com", "luckyorange.com",
        "mouseflow.com", "optimizely.com", "crashlytics.com", "segment.io",
        "fullstory.com", "quantserve.com", "exelator.com", "agkn.com", "crwdcntrl.net",
        "demdex.net", "omtrdc.net", "everesttech.net", "mathtag.com", "rlcdn.com",
        "adsymptotic.com", "id5-sync.com", "adsco.re", "branch.io", "appsflyer.com",
        "kochava.com", "singular.net", "tenjin.io", "adjust.com"
    )

    // ────────────────────────────────────────────────────────── Default Allowlist (Search & Core Infra)
    // NOTE: never add a bare registrable domain here (e.g. "google.com") — the
    // allowlist is consulted BEFORE ad rules in isBlocked(), and host matching is
    // suffix-based, so "google.com" whitelisted adservice.google.com,
    // analytics.google.com and every other Google ad/tracker subdomain.
    private val defaultAllowlist: Set<String> = setOf(
        // SEARCH ENGINES & SUGGESTIONS
        "duckduckgo.com", "html.duckduckgo.com", "links.duckduckgo.com", "lite.duckduckgo.com",
        "www.google.com", "encrypted.google.com", "gstatic.com", "apis.google.com",
        "brave.com", "search.brave.com", "cdn.search.brave.com",
        "www.bing.com", "api.bing.com", "ssl.bing.com",
        "search.yahoo.com", "r.search.yahoo.com",
        "images.search.yahoo.com", "video.search.yahoo.com", "news.search.yahoo.com",
        "qwant.com", "www.qwant.com", "api.qwant.com", "lite.qwant.com",
        "marginalia.nu", "search.marginalia.nu", "api.marginalia.nu",
        "ecosia.org", "www.ecosia.org",
        "startpage.com", "www.startpage.com",
        "swisscows.com", "www.swisscows.com",
        "yandex.com", "yandex.ru",

        // DNS & DoH PROVIDERS
        "dns.adguard-dns.com", "dns-family.adguard-dns.com",
        "cloudflare-dns.com", "family.cloudflare-dns.com", "one.one.one.one",
        "dns.google", "dns.quad9.net", "doh.opendns.com",
        "dns.nextdns.io", "test.nextdns.io",
        "freedns.controld.com", "doh.cleanbrowsing.org",

        // ESSENTIAL INFRASTRUCTURE, CDNs & SOCIAL PLATFORMS FOR WEBVIEWS
        // NOTE: platform roots (facebook.com, x.com, reddit.com …) must NOT be
        // allowlisted wholesale — the allowlist is consulted BEFORE ad rules and
        // host matching is suffix-based, so "facebook.com" here silently whitelisted
        // connect.facebook.net / an.facebook.com and made those static rules dead.
        "github.com", "raw.githubusercontent.com", "github.io",
        "jsdelivr.net", "cdn.jsdelivr.net", "cdnjs.cloudflare.com", "unpkg.com",
        "wikipedia.org", "wikimedia.org", "cloudflare.com", "fastly.net", "akamaihd.net",
        "fbcdn.net", "web.facebook.com", "www.instagram.com", "cdninstagram.com",
        "abs.twimg.com", "pbs.twimg.com", "www.twitter.com", "www.reddit.com",
        "redditmedia.com", "preview.redd.it", "i.redd.it"
    )

    // ────────────────────────────────────────────────────────── Dynamic Indexes
    private val activeDomains         = AtomicReference<Set<String>>(emptySet())
    private val activePatterns        = AtomicReference<List<Regex>>(emptyList())
    private val exceptionRules        = AtomicReference<Set<String>>(emptySet())
    private val exceptionPatterns     = AtomicReference<List<Regex>>(emptyList())
    private val allowlist             = AtomicReference<Set<String>>(emptySet())

    private val rawCustomBlocked      = AtomicReference<Set<String>>(emptySet())
    private val rawImported           = AtomicReference<Set<String>>(emptySet())
    private val rawImportedExceptions = AtomicReference<Set<String>>(emptySet())
    
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

        // Ignore CSS rules / element hiding / procedural filters
        if (cleaned.contains("##") || cleaned.contains("#?#") || 
            cleaned.contains("#@#") || cleaned.contains("#$#")) {
            return null
        }

        // Strip exception marker
        if (cleaned.startsWith("@@")) {
            cleaned = cleaned.substring(2)
        }

        // Strip leading pipe anchors or domain anchors (|http://, |https://, ||domain)
        if (cleaned.startsWith("||")) {
            cleaned = cleaned.substring(2)
        } else if (cleaned.startsWith("|")) {
            cleaned = cleaned.substring(1)
        }

        // Strip URL scheme if present
        if (cleaned.startsWith("http://")) {
            cleaned = cleaned.substring(7)
        } else if (cleaned.startsWith("https://")) {
            cleaned = cleaned.substring(8)
        } else if (cleaned.startsWith("ws://")) {
            cleaned = cleaned.substring(5)
        } else if (cleaned.startsWith("wss://")) {
            cleaned = cleaned.substring(6)
        }

        // Strip scheme again if rule was `http://||domain`
        if (cleaned.startsWith("||")) {
            cleaned = cleaned.substring(2)
        }

        // Strip adblock options (e.g. $third-party, $script, $image, $domain=...)
        if (cleaned.contains("$")) {
            cleaned = cleaned.substringBefore("$")
        }

        // Strip caret separator ^
        if (cleaned.contains("^")) {
            cleaned = cleaned.substringBefore("^")
        }

        // Strip trailing pipe anchor |
        if (cleaned.endsWith("|")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }

        // Strip trailing slash for domain-only rules (e.g. `domain.com/` -> `domain.com`)
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
     * Filters out dangerous global wildcards (like `*.css`, `*.js`, `*.woff2`)
     * that match all website stylesheets or assets globally.
     */
    private fun isSafeNetworkPattern(cleaned: String): Boolean {
        val lower = cleaned.lowercase().trim()

        // Exact dangerous global extension wildcards
        val unsafeGlobals = setOf(
            "*.css", "*.js", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.svg",
            "*.woff", "*.woff2", "*.ttf", "*.eot", "*.ico", "*.json",
            "/*.css", "/*.js", "/*.png", "/*.jpg", "/*.gif", "/*.svg",
            "css", "js", "png", "jpg", "jpeg", "gif", "svg"
        )
        if (unsafeGlobals.contains(lower)) return false

        // Generic extension wildcards without domain/path anchors (e.g. `*.css` or `*/style.css`)
        if (lower.startsWith("*.") || lower.startsWith("/*.") || (lower.startsWith("*") && !lower.contains("/"))) {
            val ext = lower.substringAfterLast(".")
            if (ext in setOf("css", "js", "png", "jpg", "jpeg", "gif", "svg", "woff", "woff2", "ttf", "eot", "ico", "json")) {
                val hasAdKeyword = lower.contains("ad") || lower.contains("track") || lower.contains("pixel") ||
                                   lower.contains("telemetry") || lower.contains("banner") || lower.contains("analytics") ||
                                   lower.contains("doubleclick") || lower.contains("pagead")
                if (!hasAdKeyword) return false
            }
        }

        return true
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
            // Ignore extremely short plain text patterns (<4 chars without structural markers)
            if (cleaned.length < 4 && !cleaned.contains("/") && !cleaned.contains("*") && !cleaned.contains(".")) return@forEach

            // Enforce pattern safety to prevent wildcards like *.css from breaking site stylesheets
            if (!isSafeNetworkPattern(cleaned)) return@forEach

            if (cleaned.contains("/") || cleaned.contains("*") || cleaned.contains("?")) {
                patterns.add(toRegex(cleaned))
            } else {
                domains.add(cleaned)
            }
        }
        
        // Categorize exceptions cleanly from rawImportedExceptions
        rawImportedExceptions.get().forEach { 
            val cleaned = cleanRule(it) ?: return@forEach
            if (cleaned.contains("/") || cleaned.contains("*") || cleaned.contains("?")) {
                excPatterns.add(toRegex(cleaned))
            } else {
                excRules.add(cleaned)
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
        val parts = pattern.split("*", "?")
        val escapedParts = parts.map { Regex.escape(it) }
        val regexStr = escapedParts.joinToString(".*")
        
        return try {
            Regex(regexStr, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
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
        rawImportedExceptions.set(exceptions)
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
            if (defaultAllowlist.any { host == it || host.endsWith(".$it") }) return false
            if (allowlist.get().any { host == it || host.endsWith(".$it") }) return false
            if (exceptionRules.get().any { host == it || host.endsWith(".$it") }) return false
            if (exceptionPatterns.get().any { it.containsMatchIn(cleanUrl) }) return false
        }

        // 3. STYLESHEET & FONT PROTECTION (Critical: Never break page layout/fonts)
        // Stylesheets (.css) and web fonts (.woff2, .ttf, etc.) must NEVER be blocked
        // unless the host is an explicit dedicated ad server (doubleclick, pagead, adservice).
        val isCssOrFont = fullUrlLower.endsWith(".css") || fullUrlLower.contains(".css?") ||
                          fullUrlLower.endsWith(".woff") || fullUrlLower.endsWith(".woff2") ||
                          fullUrlLower.endsWith(".ttf") || fullUrlLower.endsWith(".eot") ||
                          fullUrlLower.contains("/fonts/") || fullUrlLower.contains("/css/")
        if (isCssOrFont) {
            val isDedicatedAdServer = host.contains("doubleclick.net") || 
                                     host.contains("pagead2.googlesyndication.com") ||
                                     host.contains("googleadservices.com") ||
                                     host.contains("adservice.google.com")
            if (!isDedicatedAdServer) return false
        }

        if (host.isNotBlank()) {
            // 4. Domain Blocking (with deep subdomain support)
            if (deepDomainMatch(host, activeDomains.get())) return hit("Domain", host)
        }
        
        // 5. Pattern Matching (contains path/segments/wildcards)
        if (activePatterns.get().any { it.containsMatchIn(cleanUrl) }) {
            return hit("Pattern", cleanUrl)
        }
        
        // 6. Built-in path fragments fallback
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
        "com", "org", "net", "edu", "gov", "mil", "int", "io", "me", "ai", "app", "dev",
        "co.uk", "org.uk", "me.uk", "gov.uk", "ac.uk", "co.jp", "co.kr", "com.au", "net.au",
        "org.au", "com.br", "com.cn", "com.de", "com.fr", "info", "biz", "tv", "cc", "top", "xyz"
    )

    fun totalCount(): Int = activeDomains.get().size + activePatterns.get().size
}
