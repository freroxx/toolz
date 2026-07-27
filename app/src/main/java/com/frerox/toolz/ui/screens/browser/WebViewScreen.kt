package com.frerox.toolz.ui.screens.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.frerox.toolz.data.browser.AdBlockList
import com.frerox.toolz.data.browser.TabEntry
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.ui.screens.browser.components.AutofillBottomSheet
import com.frerox.toolz.ui.screens.browser.components.AutofillSuccessOverlay
import com.frerox.toolz.ui.screens.browser.components.DownloadsSheet
import com.frerox.toolz.ui.screens.browser.components.ManualPasswordBottomSheet
import com.frerox.toolz.ui.screens.search.components.FloatingSearchDock
import com.frerox.toolz.ui.screens.search.components.PrivacyFaviconImage
import com.frerox.toolz.ui.screens.search.components.SearchPill
import com.frerox.toolz.ui.screens.search.components.safeHostFromUrl
import com.frerox.toolz.util.network.AdBlockWebViewClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// ══════════════════════════════════════════════════════════
//  WebViewScreen
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    onBack: () -> Unit,
    onManageTabs: () -> Unit,
    onNavigateToPdf: (String, String) -> Unit = { _, _ -> },
    onNavigateToMusic: (Int) -> Unit = { _ -> },
    // NEW: navigate to SearchScreen and auto-open keyboard
    onNavigateToSearch: () -> Unit = onBack,
    viewModel: WebViewViewModel = hiltViewModel(),
) {
    val activity    = LocalContext.current as AppCompatActivity
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val haptic      = LocalHapticFeedback.current

    // WebView state
    var webView: WebView? by remember { mutableStateOf(null) }
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }
    var progress     by remember { mutableFloatStateOf(0f) }
    var isLoading    by remember { mutableStateOf(true) }
    var currentUrl   by remember { mutableStateOf(url) }
    var pageTitle    by remember { mutableStateOf("") }
    var canGoBack    by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // UI state
    var showFindInPage by remember { mutableStateOf(false) }
    var findQuery      by remember { mutableStateOf("") }
    var isDockVisible  by remember { mutableStateOf(true) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var searchOverlayQuery by remember { mutableStateOf("") }
    var showDownloadsSheet by remember { mutableStateOf(false) }
    var showPasswordsSheet by remember { mutableStateOf(false) }

    // Pull to refresh
    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    // ViewModel state
    val isBookmarked   by viewModel.isBookmarked.collectAsState()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsState(initial = true)
    val tabs           by viewModel.tabs.collectAsState(initial = emptyList())
    val activeTabId    by viewModel.activeTabId.collectAsState(initial = null)
    val activeTab = tabs.find { it.id == activeTabId }
    val isDesktopMode = activeTab?.isDesktopMode ?: false
    val downloads by viewModel.downloads.collectAsState()
    val autofillSuggestions by viewModel.autofillSuggestions.collectAsState()
    val autofillSuccess by viewModel.autofillSuccess.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.ensureTabExists(url)
    }

    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onBack()
        }
    }

    // Handle Tab change
    LaunchedEffect(activeTabId) {
        activeTab?.let { tab ->
            if (tab.url != currentUrl) {
                webView?.loadUrl(tab.url)
            }
        }
    }

    LaunchedEffect(currentUrl) { viewModel.checkBookmark(currentUrl) }

    // ── Root layout ───────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {

            // Modern top chrome
            TopChrome(
                pageTitle        = pageTitle,
                currentUrl       = currentUrl,
                isLoading        = isLoading,
                progress         = progress,
                isBookmarked     = isBookmarked,
                canGoForward     = canGoForward,
                showFindInPage   = showFindInPage,
                findQuery        = findQuery,
                tabs             = tabs,
                activeTabId      = activeTabId,
                isDesktopMode    = isDesktopMode,
                adBlockEnabled   = adBlockEnabled,
                onTabClick       = { tab -> viewModel.switchTab(tab.id) },
                onTabClose       = { tab -> viewModel.closeTab(tab.id) },
                onFindQueryChange = { q ->
                    findQuery = q
                    webView?.findAllAsync(q)
                },
                onFindNext       = { webView?.findNext(true) },
                onFindPrev       = { webView?.findNext(false) },
                onToggleFind     = {
                    showFindInPage = !showFindInPage
                    if (!showFindInPage) { webView?.clearMatches(); findQuery = "" }
                },
                onBack           = { if (canGoBack) webView?.goBack() else onBack() },
                onForward        = { webView?.goForward() },
                onReload         = { webView?.reload() },
                onStop           = { webView?.stopLoading() },
                onBookmarkToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleBookmark(pageTitle, currentUrl)
                },
                onUrlBarClick    = { showSearchOverlay = true },
                onShare = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, currentUrl)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                },
                onCopy = {
                    val cm = context.getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                },
                onOpenExternal = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                        )
                    }
                },
                onToggleDesktop = { viewModel.toggleDesktopMode() },
                onToggleAdBlock = { viewModel.setAdBlockEnabled(!adBlockEnabled) },
                onShowDownloads = { showDownloadsSheet = true },
                onShowPasswords = {
                    viewModel.verifyBiometric(activity) {
                        viewModel.findManualPasswords(currentUrl)
                        showPasswordsSheet = true
                    }
                }
            )

            // WebView with Pull-to-Refresh
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    webView?.reload()
                    scope.launch {
                        delay(1000)
                        isRefreshing = false
                    }
                },
                state = refreshState,
                modifier = Modifier.weight(1f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory  = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.apply {
                                javaScriptEnabled      = true
                                domStorageEnabled       = true
                                loadWithOverviewMode    = true
                                useWideViewPort         = true
                                builtInZoomControls     = true
                                displayZoomControls     = false
                                setSupportZoom(true)
                                cacheMode               = WebSettings.LOAD_DEFAULT
                                mixedContentMode        = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                allowContentAccess      = true
                                allowFileAccess         = false
                                databaseEnabled         = true
                            }

                            // Force dark mode in WebView when system is dark
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                                WebSettingsCompat.setForceDark(
                                    settings, WebSettingsCompat.FORCE_DARK_ON
                                )
                            }

                            setDownloadListener { d_url, userAgent, contentDisposition, mimetype, _ ->
                                viewModel.startDownload(d_url, userAgent, contentDisposition, mimetype)
                                showDownloadsSheet = true
                            }

                            webViewClient = object : AdBlockWebViewClient(
                                adBlockEnabled = { adBlockEnabled },
                                onPageStarted = { u ->
                                    isLoading = true
                                    u?.let { currentUrl = it; viewModel.updateTab(url = it) }
                                },
                                onPageFinished = { u ->
                                    isLoading    = false
                                    isRefreshing = false
                                    canGoBack    = canGoBack()
                                    canGoForward = canGoForward()
                                    title?.let { t ->
                                        pageTitle = t
                                        viewModel.updateTab(title = t)
                                    }
                                    // Autofill detection logic
                                    u?.let { finishedUrl ->
                                        scope.launch {
                                            delay(1000) // Wait for dynamic content
                                            evaluateJavascript(
                                                """
                                                (function(){
                                                  function isVisible(el) {
                                                    if (!el) return false;
                                                    var style = window.getComputedStyle(el);
                                                    return style.display !== 'none' && style.visibility !== 'hidden' && el.offsetWidth > 0 && el.offsetHeight > 0;
                                                  }
                                                  
                                                  var inputs = Array.from(document.querySelectorAll('input'));
                                                  var p = inputs.some(el => 
                                                    (el.type === 'password' || el.name.toLowerCase().includes('pass') || el.id.toLowerCase().includes('pass') || (el.getAttribute('autocomplete') || '').includes('password')) && isVisible(el) && !el.disabled
                                                  );
                                                  var u = inputs.some(el => 
                                                    (el.type === 'email' || el.type === 'text' || el.type === 'tel') && 
                                                    (el.name.toLowerCase().includes('user') || el.name.toLowerCase().includes('login') || el.name === 'identifier' || el.id.toLowerCase().includes('user') || el.id.toLowerCase().includes('login') || (el.getAttribute('autocomplete') || '').includes('username') || (el.getAttribute('autocomplete') || '').includes('email') || (el.getAttribute('aria-label') || '').toLowerCase().includes('email') || (el.getAttribute('aria-label') || '').toLowerCase().includes('user')) && 
                                                    isVisible(el) && !el.disabled
                                                  );
                                                  var isSearch = !!document.querySelector('input[name="q"], input[name="s"], input[id*="search"], input[name*="search"]');
                                                  
                                                  // Mutation observer to re-check if DOM changes
                                                  if (!window.autofillObserverSet) {
                                                    const observer = new MutationObserver((mutations) => {
                                                        // Check if we found new relevant inputs
                                                        var newP = Array.from(document.querySelectorAll('input[type="password"]')).some(isVisible);
                                                        if (newP) {
                                                            window.AndroidAutofill && window.AndroidAutofill.onAuthFieldsDetected();
                                                        }
                                                    });
                                                    observer.observe(document.body, { childList: true, subtree: true });
                                                    window.autofillObserverSet = true;
                                                  }

                                                  return (p || (u && !isSearch));
                                                })();
                                                """.trimIndent()
                                            ) { result ->
                                                if (result == "true") {
                                                    viewModel.findAutofillSuggestions(finishedUrl, force = true)
                                                }
                                            }
                                        }
                                    }
                                    // Capture tab preview
                                    val tabId = activeTabId
                                    if (tabId != null) {
                                        scope.launch {
                                            delay(800)
                                            runCatching {
                                                val bmp = Bitmap.createBitmap(
                                                    width.coerceAtLeast(1),
                                                    (height * 0.5f).toInt().coerceAtLeast(1),
                                                    Bitmap.Config.ARGB_8888,
                                                )
                                                draw(Canvas(bmp))
                                                viewModel.saveTabPreview(tabId, bmp)
                                            }
                                        }
                                    }
                                },
                                shouldOverrideUrl = { reqUrl ->
                                    if (reqUrl != null) {
                                        when {
                                            reqUrl.startsWith("tel:") ||
                                                    reqUrl.startsWith("mailto:") ||
                                                    reqUrl.startsWith("intent:") -> {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl))
                                                    )
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                            ) {
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    // Only handle main frame errors
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                        isRefreshing = false
                                    }
                                    super.onReceivedError(view, request, error)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                }
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    pageTitle = title ?: ""
                                    viewModel.updateTab(title = title)
                                }
                                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                                    super.onReceivedIcon(view, icon)
                                }
                            }

                            loadUrl(url)
                            webView = this
                            if (defaultUserAgent == null) {
                                defaultUserAgent = settings.userAgentString
                            }
                        }
                    },
                    update = { wv ->
                        // Desktop Mode handling
                        wv.settings.apply {
                            val targetUA = if (isDesktopMode) {
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            } else {
                                defaultUserAgent
                            }

                            if (targetUA != null && userAgentString != targetUA) {
                                userAgentString = targetUA
                                useWideViewPort = isDesktopMode
                                loadWithOverviewMode = isDesktopMode
                                setSupportZoom(true)
                                wv.reload()
                            }
                        }

                        // Re-apply force-dark on recomposition (theme changes)
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                            WebSettingsCompat.setForceDark(
                                wv.settings, WebSettingsCompat.FORCE_DARK_ON
                            )
                        }
                    },
                )
            }
        }

        // ── Floating search dock — WebView mode ───────────────────────────────
        AnimatedVisibility(
            visible = isDockVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            FloatingSearchDock(
                tabCount      = tabs.size,
                tabs          = tabs,
                activeTabId   = activeTabId,
                onTabClick    = { tab -> viewModel.switchTab(tab.id) },
                onManageTabs  = onManageTabs,
                onNewTab      = onBack,
                currentUrl    = currentUrl,
                onSearchClick = { showSearchOverlay = true },
                onSwipeDown   = { isDockVisible = false },
            )
        }

        // ── Restore Dock Button ───────────────────────────────────────────────
        if (!isDockVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
            ) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isDockVisible = true
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Show Bar",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // ── Search Overlay ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showSearchOverlay,
            enter   = fadeIn() + expandIn(expandFrom = Alignment.TopCenter),
            exit    = fadeOut() + shrinkOut(shrinkTowards = Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                    .statusBarsPadding()
                    .clickable(
                        onClick = { showSearchOverlay = false },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SearchPill(
                        query = searchOverlayQuery,
                        onQueryChange = { searchOverlayQuery = it },
                        onSearch = { q ->
                            showSearchOverlay = false
                            webView?.loadUrl(if (q.contains(".")) q else "https://www.google.com/search?q=$q")
                        },
                        active = true,
                        onActiveChange = { if (!it) showSearchOverlay = false },
                        onBackClick = { showSearchOverlay = false },
                        onSettingsClick = { /* NOP */ },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Search or type URL",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        if (showDownloadsSheet) {
            DownloadsSheet(
                downloads = downloads,
                onDismiss = { showDownloadsSheet = false },
                onOpenFile = { item ->
                    if (item.mimeType?.contains("pdf") == true) {
                        onNavigateToPdf(Uri.fromFile(File(item.filePath)).toString(), item.fileName)
                    } else if (item.mimeType?.contains("audio") == true) {
                        onNavigateToMusic(0)
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.fromFile(File(item.filePath)), item.mimeType)
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        runCatching { context.startActivity(intent) }
                    }
                    showDownloadsSheet = false
                },
                onShareFile = { item ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = item.mimeType
                        putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(item.filePath)))
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(Intent.createChooser(intent, "Share File"))
                },
                onDeleteFile = { item ->
                    viewModel.deleteDownload(item)
                }
            )
        }

        if (autofillSuggestions.isNotEmpty()) {
            AutofillBottomSheet(
                passwords = autofillSuggestions,
                onDismiss = { viewModel.clearAutofillSuggestions() },
                onSelect = { pwd ->
                    viewModel.onCredentialSelected(activity, pwd) { user, pass ->
                        webView?.evaluateJavascript(
                            """
                            (function(){
                              function fill(selectors, value) {
                                for (var i = 0; i < selectors.length; i++) {
                                  var elements = document.querySelectorAll(selectors[i]);
                                  for (var j = 0; j < elements.length; j++) {
                                    var el = elements[j];
                                    if (el && el.offsetParent !== null) {
                                      el.value = value;
                                      el.dispatchEvent(new Event('input', { bubbles: true }));
                                      el.dispatchEvent(new Event('change', { bubbles: true }));
                                      return true;
                                    }
                                  }
                                }
                                return false;
                              }

                              fill([
                                'input[type="email"]',
                                'input[name*="email"]',
                                'input[name="identifier"]',
                                'input[name*="user"]',
                                'input[name*="login"]',
                                'input[id*="user"]',
                                'input[id*="email"]',
                                'input[id*="login"]',
                                'input[autocomplete*="username"]',
                                'input[autocomplete*="email"]',
                                'input[aria-label*="Email"]',
                                'input[aria-label*="user"]',
                                'input[type="text"]'
                              ], '${user.replace("'", "\\'")}');

                              fill([
                                'input[type="password"]',
                                'input[name*="pass"]',
                                'input[name="password"]',
                                'input[id*="pass"]',
                                'input[autocomplete*="password"]',
                                'input[autocomplete*="current-password"]',
                                'input[aria-label*="Pass"]'
                              ], '${pass.replace("'", "\\'")}');
                            })();
                            """.trimIndent(),
                            null,
                        )
                    }
                }
            )
        }

        if (autofillSuccess) {
            AutofillSuccessOverlay()
            LaunchedEffect(Unit) {
                delay(2000)
                viewModel.clearAutofillSuccess()
            }
        }

        val manualPasswords by viewModel.manualPasswords.collectAsState()
        if (showPasswordsSheet) {
            ManualPasswordBottomSheet(
                passwords = manualPasswords,
                onDismiss = {
                    showPasswordsSheet = false
                    viewModel.clearManualPasswords()
                },
                onFill = { pwd ->
                    webView?.evaluateJavascript(
                        """
                        (function(){
                          function fill(selectors, value) {
                            for (var i = 0; i < selectors.length; i++) {
                              var elements = document.querySelectorAll(selectors[i]);
                              for (var j = 0; j < elements.length; j++) {
                                var el = elements[j];
                                if (el && el.offsetParent !== null) {
                                  el.value = value;
                                  el.dispatchEvent(new Event('input', { bubbles: true }));
                                  el.dispatchEvent(new Event('change', { bubbles: true }));
                                  return true;
                                }
                              }
                            }
                            return false;
                          }

                          fill([
                            'input[type="email"]', 'input[name*="email"]', 'input[name="identifier"]',
                            'input[name*="user"]', 'input[name*="login"]', 'input[id*="user"]',
                            'input[id*="email"]', 'input[id*="login"]', 'input[autocomplete*="username"]',
                            'input[autocomplete*="email"]', 'input[aria-label*="Email"]',
                            'input[aria-label*="user"]', 'input[type="text"]'
                          ], '${pwd.username.replace("'", "\\'")}');

                          fill([
                            'input[type="password"]', 'input[name*="pass"]', 'input[name="password"]',
                            'input[id*="pass"]', 'input[autocomplete*="password"]',
                            'input[autocomplete*="current-password"]', 'input[aria-label*="Pass"]'
                          ], '${pwd.password.replace("'", "\\'")}');
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            )
        }
    }
}

// ── Components ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopChrome(
    pageTitle: String,
    currentUrl: String,
    isLoading: Boolean,
    progress: Float,
    isBookmarked: Boolean,
    canGoForward: Boolean,
    showFindInPage: Boolean,
    findQuery: String,
    tabs: List<TabEntry>,
    activeTabId: String?,
    isDesktopMode: Boolean,
    adBlockEnabled: Boolean,
    onTabClick: (TabEntry) -> Unit,
    onTabClose: (TabEntry) -> Unit,
    onFindQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onFindPrev: () -> Unit,
    onToggleFind: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onUrlBarClick: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onShowDownloads: () -> Unit,
    onShowPasswords: () -> Unit,
) {
    var showOptions by remember { mutableStateOf(false) }

    val progressAlpha by animateFloatAsState(
        targetValue   = if (isLoading) 1f else 0f,
        animationSpec = tween(350),
        label         = "progressAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {

        // ── URL bar row ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Back
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    modifier = Modifier.size(20.dp),
                    tint     = MaterialTheme.colorScheme.onSurface,
                )
            }

            // URL pill — tapping triggers search navigation with auto-focus
            Surface(
                onClick        = onUrlBarClick,
                modifier       = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape          = RoundedCornerShape(22.dp),
                color          = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Favicon
                    PrivacyFaviconImage(url = currentUrl, size = 18.dp)

                    // Title + host stacked
                    Column(modifier = Modifier.weight(1f)) {
                        if (pageTitle.isNotBlank()) {
                            Text(
                                text       = pageTitle,
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                color      = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text     = safeHostFromUrl(currentUrl),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // HTTPS lock indicator
                    val isSecure = currentUrl.startsWith("https://")
                    Icon(
                        imageVector        = if (isSecure) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = null,
                        modifier           = Modifier.size(13.dp),
                        tint               = if (isSecure)
                            Color(0xFF4CAF50).copy(alpha = 0.9f)
                        else
                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    )
                }
            }

            // Reload / stop
            IconButton(
                onClick  = if (isLoading) onStop else onReload,
                modifier = Modifier.size(40.dp),
            ) {
                Crossfade(targetState = isLoading, label = "reloadStop") { loading ->
                    if (loading) {
                        CircularProgressIndicator(
                            progress   = { progress },
                            modifier   = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color      = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Refresh, "Reload",
                            modifier = Modifier.size(20.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Overflow menu (now a BottomSheet)
            Box {
                IconButton(onClick = { showOptions = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.MoreVert, "More",
                        modifier = Modifier.size(22.dp),
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Tab Strip ────────────────────────────────────────────────────────
        TabStrip(
            tabs = tabs,
            activeTabId = activeTabId,
            onTabClick = onTabClick,
            onTabClose = onTabClose
        )

        // Thin progress bar
        if (progressAlpha > 0.01f) {
            com.frerox.toolz.ui.components.ExpressiveLinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .alpha(progressAlpha),
                color      = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }

        // Separator
        HorizontalDivider(
            thickness = 1.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        )

        // Find-in-page bar
        AnimatedVisibility(
            visible = showFindInPage,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value         = findQuery,
                    onValueChange = onFindQueryChange,
                    placeholder   = {
                        Text("Find in page…", style = MaterialTheme.typography.bodyMedium)
                    },
                    modifier      = Modifier
                        .weight(1f)
                        .height(52.dp),
                    singleLine    = true,
                    textStyle     = MaterialTheme.typography.bodyMedium,
                    shape         = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onFindNext() }),
                    trailingIcon  = if (findQuery.isNotEmpty()) {
                        { IconButton(onClick = { onFindQueryChange("") }) {
                            Icon(Icons.Filled.Close, null, Modifier.size(18.dp))
                        }}
                    } else null,
                )
                IconButton(onClick = onFindPrev, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Prev", Modifier.size(24.dp))
                }
                IconButton(onClick = onFindNext, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Next", Modifier.size(24.dp))
                }
                IconButton(onClick = onToggleFind, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, "Close", Modifier.size(20.dp))
                }
            }
        }
    }

    if (showOptions) {
        BrowserOptionsSheet(
            canGoForward = canGoForward,
            isBookmarked = isBookmarked,
            isDesktopMode = isDesktopMode,
            adBlockEnabled = adBlockEnabled,
            onDismiss = { showOptions = false },
            onForward = onForward,
            onReload = onReload,
            onBookmarkToggle = onBookmarkToggle,
            onToggleFind = onToggleFind,
            onShare = onShare,
            onCopy = onCopy,
            onOpenExternal = onOpenExternal,
            onToggleDesktop = onToggleDesktop,
            onToggleAdBlock = onToggleAdBlock,
            onShowDownloads = onShowDownloads,
            onShowPasswords = onShowPasswords
        )
    }
}

@Composable
private fun TabStrip(
    tabs: List<TabEntry>,
    activeTabId: String?,
    onTabClick: (TabEntry) -> Unit,
    onTabClose: (TabEntry) -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to active tab when it changes
    LaunchedEffect(activeTabId) {
        val index = tabs.indexOfFirst { it.id == activeTabId }
        if (index != -1) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(tabs, key = { it.id }) { tab ->
            val isActive = tab.id == activeTabId
            val backgroundColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            val contentColor = if (isActive)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant

            Surface(
                onClick = { onTabClick(tab) },
                shape = RoundedCornerShape(12.dp),
                color = backgroundColor,
                modifier = Modifier
                    .widthIn(max = 160.dp)
                    .height(36.dp)
                    .animateContentSize()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PrivacyFaviconImage(url = tab.url, size = 16.dp)
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (tabs.size > 1) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tab",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onTabClose(tab) },
                            tint = contentColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserOptionsSheet(
    canGoForward: Boolean,
    isBookmarked: Boolean,
    isDesktopMode: Boolean,
    adBlockEnabled: Boolean,
    onDismiss: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onToggleFind: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onShowDownloads: () -> Unit,
    onShowPasswords: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Browser Options",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Quick actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OptionQuickAction(
                    icon = Icons.Rounded.Refresh,
                    label = "Reload",
                    onClick = { onReload(); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                OptionQuickAction(
                    icon = if (canGoForward) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowForward,
                    label = "Forward",
                    enabled = canGoForward,
                    onClick = { onForward(); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                OptionQuickAction(
                    icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    label = if (isBookmarked) "Saved" else "Bookmark",
                    active = isBookmarked,
                    onClick = { onBookmarkToggle(); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                OptionQuickAction(
                    icon = Icons.Rounded.Key,
                    label = "Password",
                    onClick = { onShowPasswords(); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Toggles section
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Column {
                    OptionToggleRow(
                        icon = Icons.Rounded.DesktopMac,
                        label = "Desktop Site",
                        checked = isDesktopMode,
                        onCheckedChange = { onToggleDesktop(); onDismiss() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OptionToggleRow(
                        icon = Icons.Rounded.Shield,
                        label = "Ad-blocker",
                        checked = adBlockEnabled,
                        onCheckedChange = { onToggleAdBlock(); onDismiss() }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Actions list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionActionRow("Find in page", Icons.Rounded.Search, onToggleFind, onDismiss)
                OptionActionRow("Downloads", Icons.Rounded.Download, onShowDownloads, onDismiss)
                OptionActionRow("Share page", Icons.Rounded.Share, onShare, onDismiss)
                OptionActionRow("Copy link", Icons.Rounded.ContentCopy, onCopy, onDismiss)
                OptionActionRow("Open in external browser", Icons.Rounded.OpenInBrowser, onOpenExternal, onDismiss)
            }
        }
    }
}

@Composable
private fun OptionQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val containerColor = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = modifier.height(84.dp),
        border = if (!active) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(26.dp), tint = contentColor)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}

@Composable
private fun OptionToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                { Icon(Icons.Filled.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
            } else null
        )
    }
}

@Composable
private fun OptionActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        onClick = { onClick(); onDismiss() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}
