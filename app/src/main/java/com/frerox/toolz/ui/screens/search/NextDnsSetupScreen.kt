package com.frerox.toolz.ui.screens.search

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextDnsSetupScreen(
    url: String,
    onBack: () -> Unit,
    viewModel: AdBlockSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var hasExtracted by remember { mutableStateOf(false) }

    fun extractAndSave(rawUrl: String?, html: String? = null) {
        if (hasExtracted) return
        // Try URL first: https://my.nextdns.io/<id> or https://my.nextdns.io/account/.../<id>
        val candidates = mutableListOf<String>()
        rawUrl?.let { candidates.add(it) }
        html?.let { candidates.add(it) }
        // Also check html for dns.nextdns.io/<id>
        val idRegex = Regex("""(?:my\.nextdns\.io/|dns\.nextdns\.io/)([a-f0-9]{6})(?:\b|/|")""", RegexOption.IGNORE_CASE)
        val dohRegex = Regex("""https://dns\.nextdns\.io/([a-f0-9]{6})""", RegexOption.IGNORE_CASE)
        for (candidate in candidates) {
            val m = idRegex.find(candidate) ?: continue
            val id = m.groupValues[1].lowercase()
            if (id.length != 6) continue
            // Build DoH hostname if found, else default
            val dohMatch = html?.let { dohRegex.find(it) }?.value ?: "https://dns.nextdns.io/$id"
            hasExtracted = true
            scope.launch {
                viewModel.setNextDnsId(id)
                viewModel.setNextDnsUrl(dohMatch)
                // Apply config: enables NEXTDNS provider
                viewModel.applyNextDnsConfig()
                snackbarHost.showSnackbar("NextDNS $id auto-configured ✓")
                kotlinx.coroutines.delay(900)
                onBack()
            }
            break
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NextDNS Setup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = settings.userAgentString
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val u = request?.url?.toString()
                                extractAndSave(u, null)
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                extractAndSave(url, null)
                                // Also scan HTML for doh hostname
                                view?.evaluateJavascript(
                                    "(function(){return document.documentElement.outerHTML;})();"
                                ) { html ->
                                    // html is quoted JSON string, unescape
                                    val unquoted = html?.removeSurrounding("\"")?.replace("\\u003C", "<")?.replace("\\\"", "\"")?.replace("\\n", "\n")
                                    extractAndSave(url, unquoted)
                                }
                            }
                        }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { wv ->
                    if (wv.url == null) wv.loadUrl(url)
                }
            )
        }
    }
}
