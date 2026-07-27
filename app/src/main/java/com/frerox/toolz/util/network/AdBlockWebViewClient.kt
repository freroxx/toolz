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

    private var currentHost: String? = null

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
        return intercept(url ?: "")
    }

    private fun intercept(url: String): WebResourceResponse? {
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("blob:")) return null
        
        // SAME-SITE PROTECTION: Never block resources from the same host as the current page.
        // This prevents breaking core site functionality (like Brave Search scripts).
        val requestHost = try { java.net.URI(url).host?.lowercase() } catch (_: Exception) { null }
        if (requestHost != null && currentHost != null) {
            if (requestHost == currentHost || requestHost.endsWith(".$currentHost") || currentHost!!.endsWith(".$requestHost")) {
                return null 
            }
        }

        if (adBlockEnabled() && AdBlockList.isBlocked(url)) {
            android.util.Log.d("AdBlock", "Blocked: $url")
            
            val mimeType = when {
                url.contains(".js")   -> "application/javascript"
                url.contains(".css")  -> "text/css"
                else -> "text/plain"
            }

            // Return 200 OK with empty body. 403 can sometimes crash fragile site scripts.
            return WebResourceResponse(
                mimeType, 
                "UTF-8", 
                200, 
                "OK",
                mapOf(
                    "Cache-Control" to "no-store",
                    "Access-Control-Allow-Origin" to "*"
                ),
                ByteArrayInputStream("".toByteArray())
            )
        }
        
        return null
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        currentHost = try { java.net.URI(url).host?.lowercase() } catch (_: Exception) { null }
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
