package com.frerox.toolz.data.browser

import android.os.Bundle
import android.webkit.WebView
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps WebView's opaque navigation state keyed by browser tab. This is
 * intentionally memory-only: process restoration falls back to durable URLs,
 * while normal tab switches retain each tab's back/forward stack.
 */
@Singleton
class BrowserTabStateStore @Inject constructor() {
    private val states = mutableMapOf<String, Bundle>()

    fun capture(tabId: String, webView: WebView) {
        states[tabId] = Bundle().also(webView::saveState)
    }

    fun restore(tabId: String, webView: WebView): Boolean {
        val state = states[tabId] ?: return false
        return webView.restoreState(Bundle(state)) != null
    }

    fun remove(tabId: String) { states.remove(tabId) }
    fun removeAll(tabIds: Set<String>) { tabIds.forEach(states::remove) }
}
