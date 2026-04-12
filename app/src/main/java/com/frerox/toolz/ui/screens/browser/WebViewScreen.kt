package com.frerox.toolz.ui.screens.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    onBack: () -> Unit,
    viewModel: WebViewViewModel = hiltViewModel()
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf(url) }
    var pageTitle by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsState(initial = true)
    val dnsProvider by viewModel.dnsProvider.collectAsState(initial = "ADGUARD")
    val customDns by viewModel.customDns.collectAsState(initial = "")

    val context = LocalContext.current
    val currentAdBlockEnabled by rememberUpdatedState(adBlockEnabled)
    val currentDnsProvider by rememberUpdatedState(dnsProvider)
    val currentCustomDns by rememberUpdatedState(customDns)

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    LaunchedEffect(currentUrl) {
        viewModel.checkBookmark(currentUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pageTitle.ifBlank { "Loading..." },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleBookmark(pageTitle, currentUrl) }) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                        context.startActivity(browserIntent)
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in External Browser")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            AndroidView(
                modifier = Modifier.weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        }

                        // Apply Dark Mode if available
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                url?.let { currentUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                pageTitle = view?.title ?: ""
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val requestUrl = request?.url?.toString() ?: return false
                                if (requestUrl.startsWith("tel:") || requestUrl.startsWith("mailto:") || requestUrl.startsWith("intent:")) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
                                        context.startActivity(intent)
                                        return true
                                    } catch (e: Exception) {
                                        return false
                                    }
                                }
                                return false
                            }

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                if (currentAdBlockEnabled) {
                                    val host = request?.url?.host ?: ""
                                    
                                    // Block specific domains based on DNS provider selected
                                    // AdGuard DNS known blocklists or patterns
                                    val adguardBlocked = listOf(
                                        "ads.", "googleads", "doubleclick", "adservice", "analytics", "tracking",
                                        "googlesyndication.com", "adnxs.com", "outbrain.com", "taboola.com",
                                        "adform.net", "adroll.com", "quantserve.com", "scorecardresearch.com",
                                        "zedo.com", "advertising.com", "amazon-adsystem.com", "casalemedia.com",
                                        "criteo.com", "pubmatic.com", "rubiconproject.com", "yieldmo.com",
                                        "ad-delivery.net", "adgrx.com", "adhigh.net", "adlightning.com",
                                        "ad-score.com", "ad-sys.com", "adtech.de", "ad-traffic.com"
                                    )
                                    
                                    val isBlocked = when (currentDnsProvider) {
                                        "ADGUARD" -> adguardBlocked.any { host.contains(it) } || host.endsWith(".adguard.com")
                                        "CLOUDFLARE" -> host.contains("analytics") || host.contains("tracking")
                                        "GOOGLE" -> host.contains("doubleclick") || host.contains("googleads")
                                        "CUSTOM" -> host.contains(currentCustomDns) && currentCustomDns.isNotEmpty()
                                        else -> adguardBlocked.any { host.contains(it) }
                                    }

                                    if (isBlocked) {
                                        return WebResourceResponse("text/plain", "UTF-8", null)
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title ?: ""
                            }
                        }

                        loadUrl(url)
                        webView = this
                    }
                },
                update = {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                        WebSettingsCompat.setForceDark(it.settings, WebSettingsCompat.FORCE_DARK_ON)
                    }
                }
            )
        }
    }
}
