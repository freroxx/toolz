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

package com.frerox.toolz.util.network

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.frerox.toolz.data.browser.AdBlockList

/**
 * A customized WebViewClient that integrates with AdBlockList.
 * Intercepts all subresource requests and blocks them if they match known ad/tracker domains.
 */
open class AdBlockWebViewClient(
    private val adBlockEnabled: () -> Boolean = { true },
    private val onPageStarted: (String?) -> Unit = {},
    private val onPageFinished: (String?) -> Unit = {},
    private val onBlockedRequest: () -> Unit = {},
    private val shouldOverrideUrl: (String?) -> Boolean = { false }
) : WebViewClient() {

    @Volatile
    private var currentPageUrl: String? = null

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        // CRITICAL FIX: Never block the main website page, only its sub-resources (ads, scripts)
        if (request != null && request.isForMainFrame) {
            currentPageUrl = request.url.toString()
            return null
        }
        
        val url = request?.url?.toString() ?: return null
        return intercept(url)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        // The deprecated single-URL variant has no isForMainFrame flag, so we heuristically
        // skip short URLs (scheme + host only, no path) to avoid blocking page navigations.
        // Sub-resources always carry a path or query, so this is a safe approximation.
        val u = url ?: return null
        val normalized = if (u.startsWith("//")) "https:$u" else u
        val hasPath = try {
            val uri = java.net.URI(normalized)
            uri.path?.let { it.length > 1 } ?: false
        } catch (_: Exception) {
            normalized.substringAfter("://").contains("/")
        }
        return if (hasPath) intercept(u) else null
    }

    private fun intercept(url: String): WebResourceResponse? {
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("blob:")) return null

        // SAME-ROOT PROTECTION: Never block resources from the same root domain as the current page.
        val pageUrl = currentPageUrl
        if (pageUrl != null && DomainUtils.isSameRootDomain(pageUrl, url)) {
            return null
        }

        if (adBlockEnabled() && AdBlockList.isBlocked(url)) {
            android.util.Log.d("AdBlock", "Blocked: $url")
            onBlockedRequest()

            // Return a network-error response so that:
            //  • <img>.onerror fires  (ad-block test tools detect this)
            //  • <script> fails to execute
            //  • fetch() / XHR rejects with a network error
            //
            // Returning HTTP 200 with a 1x1 GIF (old approach) causes test tools to score
            // 0% because the request "succeeded". A null-stream WebResourceResponse causes
            // the WebView to emit net::ERR_FAILED — the correct signal for a blocked resource.
            return WebResourceResponse("text/plain", "UTF-8", null)
        }
        
        return null
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        currentPageUrl = url
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (adBlockEnabled()) {
            injectCosmeticFilter(view)
        }
        onPageFinished(url)
    }

    /**
     * Injects a targeted JS snippet that hides common ad/tracker/cookie-consent elements
     * that survived network-level blocking because they are served inline in HTML.
     *
     * Uses display:none on matched elements and sets up a MutationObserver so that
     * dynamically-inserted elements are hidden as well.
     */
    private fun injectCosmeticFilter(view: WebView?) {
        view ?: return
        val js = """
(function() {
  if (window.__adblockInjected) return;
  window.__adblockInjected = true;

  var adSelectors = [
    /* Standard Google / DFP ad elements */
    'ins.adsbygoogle',
    'iframe[id^="google_ads_iframe"]',
    'div[id^="google_ads_iframe"]',
    'div[id*="google_ads_"]',
    'div[id*="dfp-ad-"]',
    'div[id*="gpt-ad-"]',
    
    /* Standard dedicated ad containers */
    '.ad-container',
    '.ad-wrapper',
    '.ad-banner',
    '.ad-slot',
    '.ad_slot',
    '.advertisement',
    '.banner-ad',
    '.banner_ad',
    '.sponsored-post',
    '.sponsored-ad',
    '.native-ad',
    
    /* Content discovery widgets (Taboola, Outbrain, Revcontent, MGID) */
    '.OUTBRAIN',
    '[data-widget-id*="outbrain"]',
    '.trc_rbox_container',
    '.taboola',
    '[id*="taboola-"]',
    '.revcontent',
    '[id*="rc-widget-"]',
    '.mgid',
    
    /* Intrusive Cookie / GDPR Overlays */
    '#onetrust-banner-sdk',
    '#onetrust-consent-sdk',
    '#CybotCookiebotDialog',
    '.qc-cmp2-container',
    '#cookie-law-info-bar'
  ];

  function hideAds() {
    adSelectors.forEach(function(sel) {
      try {
        document.querySelectorAll(sel).forEach(function(el) {
          /* Safeguard: Never hide body, main, article, header, nav, or section */
          var tag = el.tagName ? el.tagName.toLowerCase() : '';
          if (tag === 'body' || tag === 'html' || tag === 'main' || tag === 'article' || tag === 'header' || tag === 'nav') return;
          if (el.style.display !== 'none') {
            el.style.setProperty('display', 'none', 'important');
          }
        });
      } catch(e) {}
    });
  }

  hideAds();

  /* Watch for dynamically inserted ad elements */
  var observer = new MutationObserver(function(mutations) {
    hideAds();
  });
  if (document.documentElement) {
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }
})();
""".trimIndent()
        view.evaluateJavascript(js, null)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString()
        if (shouldOverrideUrl(url)) return true
        return super.shouldOverrideUrlLoading(view, request)
    }
}
