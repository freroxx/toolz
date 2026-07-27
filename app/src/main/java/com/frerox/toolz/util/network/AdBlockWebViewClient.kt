package com.frerox.toolz.util.network

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.frerox.toolz.data.browser.AdBlockList
import java.io.ByteArrayInputStream

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
        return intercept(view, url)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        return intercept(view, url ?: "")
    }

    private fun intercept(view: WebView?, url: String): WebResourceResponse? {
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("blob:")) return null
        
        // SAME-ROOT PROTECTION: Never block resources from the same root domain as the current page.
        // This ensures Brave/Google Search assets are NEVER blocked, even if hosted on different subdomains.
        val pageUrl = currentPageUrl
        if (pageUrl != null && DomainUtils.isSameRootDomain(pageUrl, url)) {
            return null 
        }

        if (adBlockEnabled() && AdBlockList.isBlocked(url)) {
            android.util.Log.d("AdBlock", "Blocked: $url")
            
            // Determine likely mime type to avoid "broken icon" or "script error"
            val lowerUrl = url.lowercase()
            val mimeType = when {
                lowerUrl.contains(".js")   -> "application/javascript"
                lowerUrl.contains(".css")  -> "text/css"
                lowerUrl.contains(".png")  -> "image/png"
                lowerUrl.contains(".jpg")  -> "image/jpeg"
                lowerUrl.contains(".gif")  -> "image/gif"
                lowerUrl.contains(".svg")  -> "image/svg+xml"
                else -> "text/plain"
            }

            // High-Score Block Response:
            // For images, return a 1x1 transparent GIF. For scripts/CSS, return empty text.
            val inputStream = if (mimeType.startsWith("image/")) {
                val pixel = android.util.Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", android.util.Base64.DEFAULT)
                ByteArrayInputStream(pixel)
            } else {
                ByteArrayInputStream("".toByteArray())
            }

            // Return 200 OK with appropriate content. This is the most compatible way to block.
            return WebResourceResponse(
                mimeType, 
                "UTF-8", 
                200, 
                "OK",
                mapOf(
                    "Cache-Control" to "no-store",
                    "Access-Control-Allow-Origin" to "*",
                    "X-Content-Type-Options" to "nosniff"
                ),
                inputStream
            )
        }
        
        return null
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
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
