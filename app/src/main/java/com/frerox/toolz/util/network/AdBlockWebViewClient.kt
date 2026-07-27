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

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        // CRITICAL FIX: Never block the main website page, only its sub-resources (ads, scripts)
        if (request != null && request.isForMainFrame) {
            return null
        }
        
        val url = request?.url?.toString() ?: return null
        return intercept(url)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        // Note: url here doesn't tell us if it's main frame, but modern Android calls the other one first.
        return intercept(url ?: "")
    }

    private fun intercept(url: String): WebResourceResponse? {
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("blob:")) return null
        
        if (adBlockEnabled() && AdBlockList.isBlocked(url)) {
            android.util.Log.d("AdBlock", "Blocked (403): $url")
            
            // Determine likely mime type to avoid "broken icon" or "script error" in some browsers
            val mimeType = when {
                url.contains(".js")   -> "application/javascript"
                url.contains(".css")  -> "text/css"
                url.contains(".png")  -> "image/png"
                url.contains(".jpg")  -> "image/jpeg"
                url.contains(".gif")  -> "image/gif"
                url.contains(".svg")  -> "image/svg+xml"
                else -> "text/plain"
            }

            // Return 403 Forbidden. This ensures ad-block test sites recognize the block correctly.
            return WebResourceResponse(
                mimeType, 
                "UTF-8", 
                403, 
                "Forbidden",
                mapOf(
                    "Cache-Control" to "no-store",
                    "Access-Control-Allow-Origin" to "*",
                    "X-Content-Type-Options" to "nosniff"
                ),
                ByteArrayInputStream("".toByteArray())
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
