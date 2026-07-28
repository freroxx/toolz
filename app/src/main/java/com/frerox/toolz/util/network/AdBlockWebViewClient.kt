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
        return intercept(url ?: "")
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
        onPageFinished(url)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString()
        if (shouldOverrideUrl(url)) return true
        return super.shouldOverrideUrlLoading(view, request)
    }
}
