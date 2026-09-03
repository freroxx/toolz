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

package com.frerox.toolz.ui.screens.browser

import com.frerox.toolz.data.browser.autofill.AutofillJsBridge
import com.frerox.toolz.data.browser.TabEntry
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.ViewGroup
import android.view.View
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import com.frerox.toolz.data.browser.AdBlockList
import com.frerox.toolz.data.browser.BrowserAddressResolver
import com.frerox.toolz.data.browser.BrowserReaderArticle
import com.frerox.toolz.data.browser.BrowserReaderExtractor
import com.frerox.toolz.data.browser.BrowserSitePermission
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.screens.browser.components.AutofillBottomSheet
import com.frerox.toolz.ui.screens.browser.components.AutofillSuccessOverlay
import com.frerox.toolz.ui.screens.browser.components.DownloadsSheet
import com.frerox.toolz.ui.screens.browser.components.ManualPasswordBottomSheet
import com.frerox.toolz.ui.screens.browser.components.ReaderViewSheet
import com.frerox.toolz.ui.screens.browser.components.BrowserStartPage
import com.frerox.toolz.ui.screens.search.components.PrivacyFaviconImage
import com.frerox.toolz.ui.screens.search.components.SearchPill
import com.frerox.toolz.ui.screens.search.components.safeHostFromUrl
import com.frerox.toolz.util.network.AdBlockWebViewClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File

// ══════════════════════════════════════════════════════════
//  WebViewScreen
// ══════════════════════════════════════════════════════════

/** UA applied when the per-tab desktop-site toggle is on. */
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    onBack: () -> Unit,
    onManageTabs: () -> Unit,
    onNavigateToPdf: (String, String) -> Unit = { _, _ -> },
    onNavigateToMusic: (Int) -> Unit = { _ -> },
    onNavigateToSearch: (String) -> Unit = { _ -> onBack() },
    viewModel: WebViewViewModel = hiltViewModel(),
) {
    val activity    = LocalContext.current as AppCompatActivity
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    val haptic      = LocalHapticFeedback.current

    // WebView state
    var webView: WebView? by remember { mutableStateOf(null) }
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }
    var lastAppliedDesktopMode by remember { mutableStateOf<Boolean?>(null) }
    var renderedUaTarget by remember { mutableStateOf("") }
    var progress     by remember { mutableFloatStateOf(0f) }
    var isLoading    by remember { mutableStateOf(true) }
    var currentUrl   by remember { mutableStateOf(url) }
    var renderedTabId by remember { mutableStateOf<String?>(null) }
    var pageTitle    by remember { mutableStateOf("") }
    var canGoBack    by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var blockedRequests by remember { mutableIntStateOf(0) }

    // UI state
    var showFindInPage by remember { mutableStateOf(false) }
    var findQuery      by remember { mutableStateOf("") }
    var isDockVisible  by remember { mutableStateOf(true) }
    var isTopBarVisible by remember { mutableStateOf(true) }
    var scrollStartTime by remember { mutableLongStateOf(0L) }
    var scrollIdleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var hideTopBarJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var searchOverlayQuery by remember { mutableStateOf("") }
    var showDownloadsSheet by remember { mutableStateOf(false) }
    var showPasswordsSheet by remember { mutableStateOf(false) }
    var readerArticle by remember { mutableStateOf<BrowserReaderArticle?>(null) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var pendingFileSelection by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingWebPermission by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingWebPermissionOrigin by remember { mutableStateOf<String?>(null) }
    var fullscreenContent by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var permVersion by remember { mutableIntStateOf(0) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingFileSelection?.onReceiveValue(uris.toTypedArray())
        pendingFileSelection = null
    }
    var pendingGeolocationOrigin by remember { mutableStateOf<String?>(null) }
    var pendingGeolocationCallback by remember { mutableStateOf<android.webkit.GeolocationPermissions.Callback?>(null) }

    val webPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val request = pendingWebPermission
        if (request != null) {
            val approved = request.resources.filter { resource ->
                when (resource) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> grants[Manifest.permission.CAMERA] == true
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> grants[Manifest.permission.RECORD_AUDIO] == true
                    else -> false
                }
            }.toTypedArray()
            if (approved.isNotEmpty()) request.grant(approved) else request.deny()
            // Persist per-type decisions
            val origin = pendingWebPermissionOrigin.orEmpty()
            request.resources.forEach { res ->
                val type = when (res) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE
                    else -> null
                }
                if (type != null) {
                    val granted = when (type) {
                        com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA -> grants[Manifest.permission.CAMERA] == true
                        com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE -> grants[Manifest.permission.RECORD_AUDIO] == true
                        else -> false
                    }
                    viewModel.setSitePermission(origin, type, if (granted) BrowserSitePermission.ALLOW else BrowserSitePermission.DENY)
                }
            }
            if (approved.isEmpty()) {
                // If none approved, keep as DENY for asked types
                request.resources.forEach { res ->
                    val type = when (res) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE
                        else -> null
                    }
                    if (type != null) viewModel.setSitePermission(origin, type, BrowserSitePermission.DENY)
                }
            }
            permVersion++
        }
        pendingWebPermission = null
        pendingWebPermissionOrigin = null
    }

    val geolocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val origin = pendingGeolocationOrigin
        val callback = pendingGeolocationCallback
        if (origin != null && callback != null) {
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            callback.invoke(origin, granted, false)
            viewModel.setSitePermission(origin, com.frerox.toolz.data.browser.BrowserPermissionType.GEOLOCATION, if (granted) BrowserSitePermission.ALLOW else BrowserSitePermission.DENY)
            permVersion++
        }
        pendingGeolocationOrigin = null
        pendingGeolocationCallback = null
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val origin = pendingGeolocationOrigin // reuse for notification? Actually separate
        if (origin != null) {
            viewModel.setSitePermission(origin, com.frerox.toolz.data.browser.BrowserPermissionType.NOTIFICATION, if (granted) BrowserSitePermission.ALLOW else BrowserSitePermission.DENY)
            permVersion++
        }
        pendingGeolocationOrigin = null
    }

    // Pull to refresh
    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    // ViewModel state
    val isBookmarked   by viewModel.isBookmarked.collectAsState()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsState(initial = true)
    val adScriptEnabled by viewModel.adScriptEnabled.collectAsState(initial = false)
    val floatingToolbarVisible by viewModel.floatingToolbarVisible.collectAsState(initial = true)
    val tabs           by viewModel.tabs.collectAsState(initial = emptyList())
    val activeTabId    by viewModel.activeTabId.collectAsState(initial = null)
    val activeTab = tabs.find { it.id == activeTabId }
    val isDesktopMode = activeTab?.isDesktopMode ?: false
    val downloads by viewModel.downloads.collectAsState()
    val browserHistory by viewModel.browserHistory.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState(initial = emptyList())
    val readingList by viewModel.readingList.collectAsState()
    val autofillSuggestions by viewModel.autofillSuggestions.collectAsState()
    val autofillSuccess by viewModel.autofillSuccess.collectAsState()
    val isSavedForLater by viewModel.isSavedForLater.collectAsState()
    
    val currentAdBlockEnabled by rememberUpdatedState(adBlockEnabled)
    val currentAdScriptEnabled by rememberUpdatedState(adScriptEnabled)
    val currentTabIsPrivate by rememberUpdatedState(activeTab?.isPrivate == true)

    LaunchedEffect(Unit) {
        viewModel.ensureTabExists(url)
    }

    BackHandler {
        if (fullscreenContent != null) {
            fullscreenCallback?.onCustomViewHidden()
            fullscreenContent = null
            fullscreenCallback = null
        } else if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onBack()
        }
    }

    // Handle Tab change
    LaunchedEffect(activeTabId, webView) {
        activeTab?.let { tab ->
            val oldTabId = renderedTabId
            if (oldTabId != null && oldTabId != tab.id) {
                webView?.let { viewModel.captureTabState(oldTabId, it) }
            }
            renderedTabId = tab.id
            // Apply the tab's desktop-mode UA BEFORE any load/restore so the new tab's
            // first request is never issued with the previous tab's user agent.
            val defaultUa = defaultUserAgent ?: android.webkit.WebSettings.getDefaultUserAgent(webView?.context ?: return@let)
            val targetUa = if (tab.isDesktopMode) DESKTOP_USER_AGENT else defaultUa
            webView?.settings?.userAgentString = targetUa
            val restored = webView?.let { viewModel.restoreTabState(tab.id, it) } == true
            if (restored) {
                currentUrl = webView?.url ?: tab.url
                pageTitle = webView?.title.orEmpty()
            } else if (oldTabId != tab.id) {
                // Load unconditionally on an actual tab switch: a URL equality check
                // silently skipped loading when two tabs shared the same URL (e.g. the
                // new-tab page), leaving the previous tab's content on screen.
                webView?.loadUrl(tab.url)
            }
        }
    }

    LaunchedEffect(currentUrl) {
        viewModel.checkBookmark(currentUrl)
        viewModel.checkReadingList(currentUrl)
    }

    // Dynamic Desktop Mode toggle handler
    // Desktop mode is handled in AndroidView update block to avoid double-reload race

    // ── Root layout ───────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {

            // Top chrome — always visible (user requested: hide bottom pill not top on scroll)
            TopChrome(
                    pageTitle        = pageTitle,
                currentUrl       = currentUrl,
                isLoading        = isLoading,
                progress         = progress,
                isBookmarked     = isBookmarked,
                canGoForward     = canGoForward,
                showFindInPage   = showFindInPage,
                findQuery        = findQuery,
                isDesktopMode    = isDesktopMode,
                isPrivate        = activeTab?.isPrivate == true,
                blockedRequests  = blockedRequests,
                adBlockEnabled   = adBlockEnabled,
                floatingToolbarVisible = floatingToolbarVisible,
                tabs             = tabs,
                activeTabId      = activeTabId,
                onSwitchTab      = { id -> viewModel.switchTab(id) },
                onCloseTab       = { id -> viewModel.closeTab(id) },
                onNewTab         = {
                    onNavigateToSearch("")
                },
                onOpenTabOverview = onManageTabs,
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
                onForward        = { webView?.goForward() },
                onReload         = { webView?.reload() },
                onStop           = { webView?.stopLoading() },
                onBookmarkToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleBookmark(pageTitle, currentUrl)
                },
                isSavedForLater = isSavedForLater,
                onReadingListToggle = { viewModel.toggleReadingList(pageTitle, currentUrl) },
                onUrlBarClick    = {
                    searchOverlayQuery = if (currentUrl == "about:blank") "" else currentUrl
                    showSearchOverlay = true
                },
                downloads = downloads,
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
                onToggleAdScriptBlock = { viewModel.setAdScriptBlockEnabled(!adScriptEnabled) },
                adScriptEnabled = adScriptEnabled,
                onToggleFloatingToolbar = { viewModel.setFloatingToolbarVisible(!floatingToolbarVisible) },
                onShowDownloads = { showDownloadsSheet = true },
                onShowPasswords = {
                    viewModel.verifyBiometric(activity) {
                        viewModel.findManualPasswords(currentUrl)
                        showPasswordsSheet = true
                    }
                },
                onNewPrivateTab = {
                    onNavigateToSearch("")
                },
                onClosePrivateTabs = { viewModel.clearPrivateTabs() },
                onOpenReader = {
                    webView?.evaluateJavascript(BrowserReaderExtractor.script) { raw ->
                        readerArticle = BrowserReaderExtractor.parseJavascriptResult(raw)
                    }
                },
                onClearBrowsingData = { showClearDataDialog = true },
                onResetSitePermissions = { viewModel.resetSitePermission(safeHostFromUrl(currentUrl)); permVersion++ },
                sitePermissions = remember(currentUrl, permVersion) { viewModel.getPermissionsForOrigin(currentUrl) },
                onRevokePermission = { type -> viewModel.setSitePermission(currentUrl, type, BrowserSitePermission.ASK); permVersion++ },
            )

            // WebView framed — 4-corner polished frame same color as TopChrome (surfaceContainerLow)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                ) {
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
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
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
                                // Desktop pages must load fully zoomed out (entire site width
                                // visible, like every browser). scale 0 = auto overview fit.
                                @Suppress("DEPRECATION")
                                setInitialScale(0)
                                builtInZoomControls     = true
                                displayZoomControls     = false
                                setSupportZoom(true)
                                cacheMode               = WebSettings.LOAD_DEFAULT
                                // A browser should never silently downgrade an HTTPS page by
                                // allowing insecure subresources.
                                mixedContentMode        = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                allowContentAccess      = true
                                allowFileAccess         = false
                                databaseEnabled         = true
                                setGeolocationEnabled(true)
                                // Convert target=_blank/pop-up navigation into Toolz tabs.
                                setSupportMultipleWindows(true)
                                javaScriptCanOpenWindowsAutomatically = false
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    safeBrowsingEnabled = true
                                }
                            }

                            // Scroll listener for auto-hide TopChrome pill after 1s continuous scroll
                            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                val dy = kotlin.math.abs(scrollY - oldScrollY)
                                if (dy < 2) return@setOnScrollChangeListener
                                // Always show bottom pill at top
                                if (scrollY == 0) {
                                    isDockVisible = true
                                    scrollStartTime = 0L
                                    scrollIdleJob?.cancel()
                                    hideTopBarJob?.cancel()
                                    return@setOnScrollChangeListener
                                }
                                // Don't hide when find bar or fullscreen
                                if (showFindInPage || fullscreenContent != null) {
                                    isDockVisible = true
                                    return@setOnScrollChangeListener
                                }
                                // Cancel pending show
                                scrollIdleJob?.cancel()
                                // Start hide timer if not already — hide bottom pill after 1s continuous scroll
                                if (hideTopBarJob == null || hideTopBarJob?.isActive != true) {
                                    if (scrollStartTime == 0L) scrollStartTime = System.currentTimeMillis()
                                    hideTopBarJob = scope.launch {
                                        delay(1000)
                                        if (System.currentTimeMillis() - scrollStartTime >= 1000) {
                                            isDockVisible = false
                                        }
                                    }
                                }
                                // Schedule show when idle (no scroll for 350ms) — show bottom pill again
                                scrollIdleJob = scope.launch {
                                    delay(350)
                                    isDockVisible = true
                                    scrollStartTime = 0L
                                    hideTopBarJob?.cancel()
                                }
                            }

                            // Force dark mode in WebView when system is dark
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                                WebSettingsCompat.setForceDark(
                                    settings, WebSettingsCompat.FORCE_DARK_ON
                                )
                            }
                            // Autofill JS bridge — enables MutationObserver callback for dynamic SPA forms
                            addJavascriptInterface(object {
                                @android.webkit.JavascriptInterface
                                fun onAuthFieldsDetected() {
                                    // Must trigger autofill even on non-login URLs
                                    scope.launch { viewModel.findAutofillSuggestions(currentUrl, force = true) }
                                }
                            }, "AndroidAutofill")

                            setDownloadListener { d_url, userAgent, contentDisposition, mimetype, _ ->
                                viewModel.startDownload(d_url, userAgent, contentDisposition, mimetype)
                                showDownloadsSheet = true
                            }

                            webViewClient = object : AdBlockWebViewClient(
                                adBlockEnabled = { currentAdBlockEnabled },
                                onPageStarted = { u ->
                                    isLoading = true
                                    blockedRequests = 0
                                    u?.let { currentUrl = it; viewModel.updateTab(url = it) }
                                },
                                onBlockedRequest = { scope.launch { blockedRequests++ } },
                                onPageFinished = { u ->
                                    isLoading    = false
                                    isRefreshing = false
                                    canGoBack    = canGoBack()
                                    canGoForward = canGoForward()
                                    title?.let { t ->
                                        pageTitle = t
                                        viewModel.updateTab(title = t)
                                    }
                                    u?.let { viewModel.recordPageVisit(it, pageTitle) }
                                    activeTabId?.let { viewModel.captureTabState(it, this@apply) }
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
                                            val captured = runCatching {
                                                val wv = webView
                                                if (wv == null || wv.width < 1 || wv.height < 1) return@runCatching null
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                    // Hardware-accelerated WebViews render via the GPU, so a plain
                                                    // View.draw(Canvas) yields a blank bitmap. PixelCopy reads the
                                                    // actual on-screen surface instead.
                                                    val bmp = android.graphics.Bitmap.createBitmap(
                                                        wv.width,
                                                        (wv.height * 0.6f).toInt().coerceAtLeast(1),
                                                        android.graphics.Bitmap.Config.ARGB_8888,
                                                    )
                                                    // PixelCopy needs a Window/SurfaceView — read the web
                                                    // content off the webview's drawing cache via a
                                                    // software layer toggle as fallback for WebView.
                                                    val copied = suspendCancellableCoroutine { cont: kotlinx.coroutines.CancellableContinuation<Boolean> ->
                                                        val activity = wv.context as? android.app.Activity
                                                        val window = activity?.window
                                                        if (window == null) {
                                                            cont.resume(false) { _, _, _ -> }
                                                        } else {
                                                            android.view.PixelCopy.request(
                                                                window, bmp,
                                                                { result -> cont.resume(result == android.view.PixelCopy.SUCCESS) { _, _, _ -> } },
                                                                android.os.Handler(android.os.Looper.getMainLooper()),
                                                            )
                                                        }
                                                    }
                                                    if (!copied) { bmp.recycle(); return@runCatching null }
                                                    bmp
                                                } else {
                                                    val bmp = android.graphics.Bitmap.createBitmap(
                                                        wv.width.coerceAtLeast(1),
                                                        (wv.height * 0.6f).toInt().coerceAtLeast(1),
                                                        android.graphics.Bitmap.Config.ARGB_8888,
                                                    )
                                                    wv.draw(android.graphics.Canvas(bmp))
                                                    bmp
                                                }
                                            }.onFailure {
                                                android.util.Log.w("TabPreview", "Capture failed for tab $tabId", it)
                                            }.getOrNull()
                                            if (captured != null) {
                                                viewModel.saveTabPreview(tabId, captured)
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
                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    if (view == null) return
                                    fullscreenContent = view
                                    fullscreenCallback = callback
                                }

                                override fun onHideCustomView() {
                                    fullscreenContent = null
                                    fullscreenCallback = null
                                }

                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    val supported = request?.resources?.filter {
                                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                                            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                                    }.orEmpty()
                                    val origin = request?.origin?.toString().orEmpty()
                                    if (request == null || supported.isEmpty()) {
                                        request?.deny()
                                    } else {
                                        // Check per-type stored permission
                                        val anyDenied = supported.any { res ->
                                            val type = if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA else com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE
                                            viewModel.sitePermission(origin, type) == BrowserSitePermission.DENY
                                        }
                                        if (anyDenied) {
                                            request.deny()
                                            return
                                        }
                                        val allAllowedAndGranted = supported.all { resource ->
                                            val type = if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE) com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA else com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE
                                            val stored = viewModel.sitePermission(origin, type)
                                            val androidGranted = ContextCompat.checkSelfPermission(context, if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                            stored == BrowserSitePermission.ALLOW && androidGranted
                                        }
                                        if (allAllowedAndGranted) {
                                            request.grant(supported.toTypedArray())
                                        } else {
                                            pendingWebPermission = request
                                            pendingWebPermissionOrigin = origin
                                        }
                                    }
                                }

                                override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                                    if (pendingWebPermission == request) pendingWebPermission = null
                                    pendingWebPermissionOrigin = null
                                }

                                override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback?) {
                                    if (origin == null || callback == null) return
                                    val stored = viewModel.sitePermission(origin, com.frerox.toolz.data.browser.BrowserPermissionType.GEOLOCATION)
                                    when (stored) {
                                        BrowserSitePermission.ALLOW -> {
                                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                            if (granted) callback.invoke(origin, true, false) else {
                                                pendingGeolocationOrigin = origin
                                                pendingGeolocationCallback = callback
                                                geolocationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                            }
                                        }
                                        BrowserSitePermission.DENY -> callback.invoke(origin, false, false)
                                        BrowserSitePermission.ASK -> {
                                            pendingGeolocationOrigin = origin
                                            pendingGeolocationCallback = callback
                                            // Show dialog via state: we reuse pendingWebPermission dialog? Instead trigger via UI state below
                                            // For now, directly show system permission request if needed, else show custom dialog via pendingGeolocation
                                            // We'll rely on UI dialog for geolocation handled below in the pendingGeolocation UI
                                            // To trigger UI, we keep callback pending and show dialog
                                            // If Android permission not granted, request it first
                                            val needsAndroid = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                            if (needsAndroid) {
                                                geolocationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                            } else {
                                                // Keep pending for UI dialog — will be handled in composable state
                                            }
                                        }
                                    }
                                }

                                override fun onGeolocationPermissionsHidePrompt() {
                                    pendingGeolocationOrigin = null
                                    pendingGeolocationCallback = null
                                }

                                override fun onShowFileChooser(
                                    view: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: WebChromeClient.FileChooserParams?,
                                ): Boolean {
                                    pendingFileSelection?.onReceiveValue(null)
                                    pendingFileSelection = filePathCallback
                                    val acceptedTypes = fileChooserParams?.acceptTypes
                                        ?.filter { it.isNotBlank() }
                                        ?.toTypedArray()
                                        ?.takeIf { it.isNotEmpty() }
                                        ?: arrayOf("*/*")
                                    documentPicker.launch(acceptedTypes)
                                    return true
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?,
                                ): Boolean {
                                    if (!isUserGesture || resultMsg == null) return false
                                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                    val popup = WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        // Popup tabs must inherit the ad-block client too —
                                        // a bare WebViewClient here silently bypassed blocking
                                        // for every target=_blank navigation.
                                        webViewClient = object : AdBlockWebViewClient(
                                            adBlockEnabled = { currentAdBlockEnabled },
                                        ) {
                                            override fun onPageStarted(popupView: WebView?, popupUrl: String?, favicon: Bitmap?) {
                                                val destination = popupUrl?.takeIf { it.startsWith("http") } ?: return
                                                val newTab = viewModel.addTab(destination, isPrivate = currentTabIsPrivate)
                                                // The screen owns one rendering WebView; the temporary popup
                                                // only resolves the destination, then hands it to the new tab.
                                                webView?.loadUrl(destination)
                                                currentUrl = destination
                                                viewModel.updateTab(url = destination)
                                                popupView?.stopLoading()
                                                popupView?.destroy()
                                                viewModel.switchTab(newTab.id)
                                            }
                                        }
                                    }
                                    transport.webView = popup
                                    resultMsg.sendToTarget()
                                    return true
                                }

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                }
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    pageTitle = title ?: ""
                                    viewModel.updateTab(title = title)
                                }
                                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                                    super.onReceivedIcon(view, icon)
                                    if (icon != null && view?.url != null) {
                                        com.frerox.toolz.data.browser.FaviconResolver.cacheIcon(view.url.orEmpty(), icon)
                                    }
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
                        wv.settings.apply {
                            val targetUA = if (isDesktopMode) DESKTOP_USER_AGENT else {
                                defaultUserAgent ?: android.webkit.WebSettings.getDefaultUserAgent(wv.context)
                            }
                            // Viewport must ALWAYS be wide + overview for proper responsive layout.
                            // Desktop toggle ONLY changes UA; viewport mode stays overview so full-page
                            // width is always visible and zoom-out works.
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            // Ensure desktop pages load fully zoomed-out (entire width visible)
                            // Initial scale 0 = auto overview fit; we also force viewport meta via JS.
                            @Suppress("DEPRECATION")
                            if (isDesktopMode) wv.setInitialScale(0) else wv.setInitialScale(0)
                            val uaChanged = targetUA.isNotEmpty() && userAgentString != targetUA
                            if (uaChanged) {
                                userAgentString = targetUA
                                lastAppliedDesktopMode = isDesktopMode
                                renderedUaTarget = targetUA
                                @Suppress("DEPRECATION")
                                wv.setInitialScale(0)
                                wv.reload()
                                if (isDesktopMode) {
                                    wv.postDelayed({
                                        try {
                                            wv.evaluateJavascript(
                                                "(function(){try{var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}m.content='width=980, initial-scale=0.25, minimum-scale=0.25, user-scalable=yes';}catch(e){}})();",
                                                null
                                            )
                                            repeat(3) { wv.zoomOut() }
                                        } catch (_: Exception) {}
                                    }, 900)
                                }
                            } else if (lastAppliedDesktopMode != isDesktopMode) {
                                lastAppliedDesktopMode = isDesktopMode
                                renderedUaTarget = targetUA
                                @Suppress("DEPRECATION")
                                wv.setInitialScale(0)
                                wv.reload()
                                if (isDesktopMode) {
                                    wv.postDelayed({
                                        try {
                                            wv.evaluateJavascript(
                                                "(function(){try{var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}m.content='width=980, initial-scale=0.25';}catch(e){}})();",
                                                null
                                            )
                                            repeat(3) { wv.zoomOut() }
                                        } catch (_: Exception) {}
                                    }, 900)
                                }
                            }
                            cacheMode = if (activeTab?.isPrivate == true) {
                                WebSettings.LOAD_NO_CACHE
                            } else {
                                WebSettings.LOAD_DEFAULT
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
                    } // PullToRefreshBox
                } // Surface frame
            } // Box frame outer
        } // Column

        // Previously about:blank new-tab page removed — new tabs now navigate to search home.
        // Keep empty state to avoid blank WebView if tabs become empty via last-tab close.
        LaunchedEffect(tabs.isEmpty(), activeTabId) {
            if (tabs.isEmpty() && activeTabId == null) {
                // Defer navigation to avoid recomposition clash
                kotlinx.coroutines.delay(100)
                onNavigateToSearch("")
            }
        }

        // ── Single, calm bottom navigation surface ────────────────────────────
        AnimatedVisibility(
            visible = isDockVisible && floatingToolbarVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            BrowserNavigationBar(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                tabCount = tabs.size,
                onBack = { if (canGoBack) webView?.goBack() else onBack() },
                onForward = { webView?.goForward() },
                onAddress = { showSearchOverlay = true },
                onTabs = onManageTabs,
                onHome = {
                    onNavigateToSearch("")
                },
                onSwipeDown   = { isDockVisible = false },
            )
        }

        // ── Restore Dock Button ───────────────────────────────────────────────
        if (!isDockVisible && floatingToolbarVisible) {
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
                            contentDescription = stringResource(R.string.st_WebViewScreen_g7h8),
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
                            when (val dest = BrowserAddressResolver.resolveDestination(q)) {
                                is com.frerox.toolz.data.browser.AddressDestination.DirectUrl -> {
                                    viewModel.resolveAddress(q) { target ->
                                        webView?.loadUrl(target)
                                        currentUrl = target
                                        viewModel.updateTab(url = target)
                                    }
                                }
                                is com.frerox.toolz.data.browser.AddressDestination.SearchQuery -> {
                                    onNavigateToSearch(dest.query)
                                }
                            }
                        },
                        active = true,
                        onActiveChange = { if (!it) showSearchOverlay = false },
                        onBackClick = { showSearchOverlay = false },
                        onSettingsClick = { /* NOP */ },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = {
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    searchOverlayQuery = clip
                                }
                            },
                            label = { Text("Paste") },
                            leadingIcon = { Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
                            AssistChip(
                                onClick = {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                label = { Text("Copy URL") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        }
                        if (searchOverlayQuery.isNotBlank()) {
                            AssistChip(
                                onClick = { searchOverlayQuery = "" },
                                label = { Text("Clear") },
                                leadingIcon = { Icon(Icons.Rounded.Clear, null, modifier = Modifier.size(16.dp)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    val normalizedQuery = searchOverlayQuery.trim()
                    val suggestions = remember(normalizedQuery, bookmarks, browserHistory) {
                        val needle = normalizedQuery.lowercase()
                        buildList {
                            bookmarks.forEach { bookmark ->
                                if (needle.isBlank() || bookmark.title.contains(needle, true) || bookmark.url.contains(needle, true)) {
                                    add(OmniboxSuggestion(bookmark.title, bookmark.url, Icons.Rounded.Bookmark, "Saved site"))
                                }
                            }
                            browserHistory.forEach { visit ->
                                if (needle.isBlank() || visit.title.contains(needle, true) || visit.url.contains(needle, true)) {
                                    add(OmniboxSuggestion(visit.title, visit.url, Icons.Rounded.History, "Recent"))
                                }
                            }
                        }.distinctBy { it.url }.take(6)
                    }

                    if (suggestions.isEmpty()) {
                        Text(
                            if (normalizedQuery.isBlank()) "Search the web or enter an address" else "Press search to open this address or search",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            if (normalizedQuery.isBlank()) "Suggestions" else "Matches",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                suggestions.forEachIndexed { index, suggestion ->
                                    if (index > 0) HorizontalDivider(
                                        modifier = Modifier.padding(start = 56.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showSearchOverlay = false
                                                viewModel.resolveAddress(suggestion.url) { target ->
                                                    webView?.loadUrl(target)
                                                    currentUrl = target
                                                    viewModel.updateTab(url = target)
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(suggestion.icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                suggestion.title.ifBlank { BrowserAddressResolver.displayHost(suggestion.url) },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "${suggestion.kind} · ${BrowserAddressResolver.displayHost(suggestion.url)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
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

        readerArticle?.let { article ->
            ReaderViewSheet(article = article, onDismiss = { readerArticle = null })
        }

        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                icon = { Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Clear browsing data?") },
                text = { Text("This clears visited pages, cookies, cached files, form data, and the current tab's back/forward history.") },
                confirmButton = {
                    Button(
                        onClick = {
                            webView?.let { viewModel.clearBrowsingData(it) }
                            showClearDataDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("Clear data") }
                },
                dismissButton = { TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") } },
            )
        }

        fullscreenContent?.let { content ->
            AndroidView(
                factory = { content },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }

        pendingWebPermission?.let { request ->
            val needsCamera = PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources
            val needsMic = PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources
            AlertDialog(
                onDismissRequest = {
                    request.deny()
                    val origin = pendingWebPermissionOrigin.orEmpty()
                    request.resources.forEach { res ->
                        val type = when (res) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE
                            else -> null
                        }
                        if (type != null) viewModel.setSitePermission(origin, type, BrowserSitePermission.DENY)
                    }
                    permVersion++
                    pendingWebPermission = null
                    pendingWebPermissionOrigin = null
                },
                icon = { Icon(Icons.Rounded.Videocam, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Allow site access?") },
                text = {
                    Text(buildString {
                        append(safeHostFromUrl(currentUrl))
                        append(" wants to use your ")
                        append(listOfNotNull(if (needsCamera) "camera" else null, if (needsMic) "microphone" else null).joinToString(" and "))
                        append(".")
                    })
                },
                confirmButton = {
                    Button(onClick = {
                        val androidPermissions = buildList {
                            if (needsCamera && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.CAMERA)
                            if (needsMic && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
                        }
                        if (androidPermissions.isEmpty()) {
                            request.grant(request.resources.filter {
                                it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                            }.toTypedArray())
                            val origin = pendingWebPermissionOrigin.orEmpty()
                            request.resources.forEach { res ->
                                val type = when (res) {
                                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA
                                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE
                                    else -> null
                                }
                                if (type != null) viewModel.setSitePermission(origin, type, BrowserSitePermission.ALLOW)
                            }
                            permVersion++
                            pendingWebPermission = null
                            pendingWebPermissionOrigin = null
                        } else {
                            webPermissionLauncher.launch(androidPermissions.toTypedArray())
                        }
                    }) { Text("Allow") }
                },
                dismissButton = { TextButton(onClick = {
                    request.deny()
                    val origin = pendingWebPermissionOrigin.orEmpty()
                    request.resources.forEach { res ->
                        val type = when (res) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE
                            else -> null
                        }
                        if (type != null) viewModel.setSitePermission(origin, type, BrowserSitePermission.DENY)
                    }
                    permVersion++
                    pendingWebPermission = null
                    pendingWebPermissionOrigin = null
                }) { Text("Block site") } },
            )
        }

        pendingGeolocationCallback?.let { callback ->
            val origin = pendingGeolocationOrigin.orEmpty()
            AlertDialog(
                onDismissRequest = {
                    callback.invoke(origin, false, false)
                    viewModel.setSitePermission(origin, com.frerox.toolz.data.browser.BrowserPermissionType.GEOLOCATION, BrowserSitePermission.DENY)
                    permVersion++
                    pendingGeolocationCallback = null
                    pendingGeolocationOrigin = null
                },
                icon = { Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Allow location access?") },
                text = { Text("${safeHostFromUrl(origin.ifBlank { currentUrl })} wants to know your location.") },
                confirmButton = {
                    Button(onClick = {
                        val needsAndroid = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        if (needsAndroid) {
                            geolocationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        } else {
                            callback.invoke(origin, true, false)
                            viewModel.setSitePermission(origin, com.frerox.toolz.data.browser.BrowserPermissionType.GEOLOCATION, BrowserSitePermission.ALLOW)
                            permVersion++
                            pendingGeolocationCallback = null
                            pendingGeolocationOrigin = null
                        }
                    }) { Text("Allow") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        callback.invoke(origin, false, false)
                        viewModel.setSitePermission(origin, com.frerox.toolz.data.browser.BrowserPermissionType.GEOLOCATION, BrowserSitePermission.DENY)
                        permVersion++
                        pendingGeolocationCallback = null
                        pendingGeolocationOrigin = null
                    }) { Text("Block") }
                }
            )
        }

        if (autofillSuggestions.isNotEmpty()) {
            AutofillBottomSheet(
                passwords = autofillSuggestions,
                onDismiss = { viewModel.clearAutofillSuggestions() },
                onSelect = { pwd ->
                    viewModel.onCredentialSelected(activity, pwd) { user, pass ->
                        webView?.evaluateJavascript(AutofillJsBridge.fillJs(user, pass), null)
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
                    webView?.evaluateJavascript(AutofillJsBridge.fillJs(pwd.username, pwd.password), null)
                }
            )
        }
    }
}

// ── Components ───────────────────────────────────────────────────────────────

private data class OmniboxSuggestion(
    val title: String,
    val url: String,
    val icon: ImageVector,
    val kind: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopChrome(
    pageTitle: String,
    currentUrl: String,
    isLoading: Boolean,
    progress: Float,
    isBookmarked: Boolean,
    isSavedForLater: Boolean,
    canGoForward: Boolean,
    showFindInPage: Boolean,
    findQuery: String,
    isDesktopMode: Boolean,
    isPrivate: Boolean,
    blockedRequests: Int,
    adBlockEnabled: Boolean,
    adScriptEnabled: Boolean = false,
    floatingToolbarVisible: Boolean,
    tabs: List<TabEntry>,
    activeTabId: String?,
    onSwitchTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onOpenTabOverview: () -> Unit,
    onFindQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onFindPrev: () -> Unit,
    onToggleFind: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onReadingListToggle: () -> Unit,
    onUrlBarClick: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleAdScriptBlock: () -> Unit = {},
    onToggleFloatingToolbar: () -> Unit,
    onShowDownloads: () -> Unit,
    onShowPasswords: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onClosePrivateTabs: () -> Unit,
    onOpenReader: () -> Unit,
    onClearBrowsingData: () -> Unit,
    onResetSitePermissions: () -> Unit,
    downloads: List<com.frerox.toolz.data.browser.DownloadItem> = emptyList(),
    sitePermissions: Map<com.frerox.toolz.data.browser.BrowserPermissionType, BrowserSitePermission> = emptyMap(),
    onRevokePermission: (com.frerox.toolz.data.browser.BrowserPermissionType) -> Unit = {},
) {
    var showOptions by remember { mutableStateOf(false) }
    var showSiteControls by remember { mutableStateOf(false) }

    val progressAlpha by animateFloatAsState(
        targetValue   = if (isLoading) 1f else 0f,
        animationSpec = tween(350),
        label         = "progressAlpha",
    )

    // ── Flat header — no rounded corners, frame provides polish instead
    // User requested remove rounded corners from top bar; webpage frame now has 4-corner rounding
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // URL bar row — expressive pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = onUrlBarClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val isSecure = currentUrl.startsWith("https://", ignoreCase = true)
                        Surface(
                            onClick = { showSiteControls = true },
                            shape = CircleShape,
                            color = if (isSecure) Color(0xFF4CAF50).copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isSecure) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    contentDescription = "Site controls",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSecure) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(22.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                PrivacyFaviconImage(url = currentUrl, size = 16.dp)
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            if (pageTitle.isNotBlank()) {
                                Text(
                                    text = pageTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = safeHostFromUrl(currentUrl),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (isPrivate) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), modifier = Modifier.size(22.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.VisibilityOff, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }

                        if (blockedRequests > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shadowElevation = 1.dp,
                            ) {
                                Text(
                                    text = blockedRequests.coerceAtMost(99).toString(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }

                // Reload / Stop — FilledIconButton expressive 40dp 14dp rounded
                FilledIconButton(
                    onClick = if (isLoading) onStop else onReload,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        imageVector = if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = if (isLoading) "Stop loading" else "Reload",
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Overflow — tonal 40dp
                FilledTonalIconButton(
                    onClick = { showOptions = true },
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.st_WebViewScreen_1a2b), modifier = Modifier.size(20.dp))
                }
            }

        // Always-visible Tab Strip below the address bar
        BrowserTabStrip(
            tabs = tabs,
            activeTabId = activeTabId,
            onSwitchTab = onSwitchTab,
            onCloseTab = onCloseTab,
            onNewTab = onNewTab,
            onOpenOverview = onOpenTabOverview,
        )

        // Download pill progress (colored per mime, on pill lower border)
        if (downloads.isNotEmpty()) {
            val active = downloads.firstOrNull { it.status != android.app.DownloadManager.STATUS_SUCCESSFUL } ?: downloads.first()
            val dColor = when {
                active.mimeType?.contains("pdf", ignoreCase = true) == true -> Color(0xFFE53935)
                active.mimeType?.contains("apk", ignoreCase = true) == true -> Color(0xFF43A047)
                active.mimeType?.contains("image", ignoreCase = true) == true -> Color(0xFF1E88E5)
                active.mimeType?.contains("video", ignoreCase = true) == true -> Color(0xFF8E24AA)
                active.mimeType?.contains("audio", ignoreCase = true) == true -> Color(0xFFFB8C00)
                else -> MaterialTheme.colorScheme.primary
            }
            LinearProgressIndicator(
                progress = { active.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                color = dColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
        // Thin progress bar
        if (progressAlpha > 0.01f) {
            ExpressiveLinearProgressIndicator(
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
                        Text(stringResource(R.string.st_WebViewScreen_3d5b), style = MaterialTheme.typography.bodyMedium)
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
        } // close Column
    } // close expressive header Surface

    if (showSiteControls) {
        SiteControlsSheet(
            url = currentUrl,
            blockedRequests = blockedRequests,
            onDismiss = { showSiteControls = false },
            onResetPermissions = onResetSitePermissions,
        )
    }

    if (showOptions) {
        BrowserOptionsSheet(
            canGoForward = canGoForward,
            isBookmarked = isBookmarked,
            isSavedForLater = isSavedForLater,
            isPrivate = isPrivate,
            isDesktopMode = isDesktopMode,
            adBlockEnabled = adBlockEnabled,
            adScriptEnabled = adScriptEnabled,
            floatingToolbarVisible = floatingToolbarVisible,
            currentUrl = currentUrl,
            sitePermissions = sitePermissions,
            onRevokePermission = onRevokePermission,
            onDismiss = { showOptions = false },
            onForward = onForward,
            onReload = onReload,
            onBookmarkToggle = onBookmarkToggle,
            onReadingListToggle = onReadingListToggle,
            onToggleFind = onToggleFind,
            onShare = onShare,
            onCopy = onCopy,
            onOpenExternal = onOpenExternal,
            onToggleDesktop = onToggleDesktop,
            onToggleAdBlock = onToggleAdBlock,
            onToggleAdScriptBlock = onToggleAdScriptBlock,
            onToggleFloatingToolbar = onToggleFloatingToolbar,
            onShowDownloads = onShowDownloads,
            onShowPasswords = onShowPasswords,
            onNewPrivateTab = onNewPrivateTab,
            onClosePrivateTabs = onClosePrivateTabs,
            onOpenReader = onOpenReader,
            onClearBrowsingData = onClearBrowsingData,
            onResetSitePermissions = onResetSitePermissions,
        )
    }
}

@Composable
private fun BrowserTabStrip(
    tabs: List<TabEntry>,
    activeTabId: String?,
    onSwitchTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onOpenOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Remade tabs bar — pill-shaped expressive chips, active = primaryContainer, inactive = surfaceContainerHigh
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // One tab at a time, horizontal swiping via HorizontalPager (no free scroll)
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = (tabs.indexOfFirst { it.id == activeTabId }.takeIf { it >= 0 } ?: 0),
                pageCount = { tabs.size.coerceAtLeast(1) }
            )
            androidx.compose.runtime.LaunchedEffect(activeTabId, tabs.size) {
                val idx = tabs.indexOfFirst { it.id == activeTabId }
                if (idx >= 0 && pagerState.currentPage != idx) {
                    try { pagerState.animateScrollToPage(idx) } catch (_: Exception) {}
                }
            }
            // Swipe-to-switch removed per bug report: swiping the tab strip was auto-switching the
            // website and causing tab duplication/override races. Only taps explicitly switch.
            if (tabs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                    Text("No tabs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    pageSpacing = 8.dp,
                    userScrollEnabled = false,
                ) { page ->
                    if (page >= tabs.size) return@HorizontalPager
                    val tab = tabs[page]
                    val isActive = tab.id == activeTabId
                    Surface(
                        onClick = { onSwitchTab(tab.id) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = if (isActive) 2.dp else 0.dp,
                        shadowElevation = if (isActive) 1.dp else 0.dp,
                        border = if (!isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .padding(horizontal = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    PrivacyFaviconImage(url = tab.url, size = 14.dp)
                                }
                            }
                            Text(
                                text = tab.title.ifBlank { safeHostFromUrl(tab.url) },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (tabs.size > 1) {
                                Surface(
                                    onClick = { onCloseTab(tab.id) },
                                    shape = CircleShape,
                                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Close, "Close tab", modifier = Modifier.size(12.dp), tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tab count overview — tonal pill with count + grid icon
            Surface(
                onClick = onOpenOverview,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.height(36.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.GridView, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        text = tabs.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            // New tab — primary tonal 36dp
            FilledTonalIconButton(
                onClick = onNewTab,
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            ) {
                Icon(Icons.Rounded.Add, "New tab", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteControlsSheet(
    url: String,
    blockedRequests: Int,
    onDismiss: () -> Unit,
    onResetPermissions: () -> Unit,
    viewModel: WebViewViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val host = remember(url) { safeHostFromUrl(url) }
    val isSecure = url.startsWith("https://", ignoreCase = true)
    val perms = remember(url) { viewModel.getPermissionsForOrigin(url) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrivacyFaviconImage(url = url, size = 32.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = host.ifBlank { "Current site" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isSecure) "Connection is secure (HTTPS)" else "Connection is not secure (HTTP)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSecure) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Security & Privacy metrics
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Trackers & Ads Blocked", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = "$blockedRequests",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Permissions section
            Text(
                text = "Site Permissions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Camera, microphone, notifications and location access for $host.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (perms.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No permissions granted", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("This site hasn't been granted any special permissions yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        perms.forEach { (type, perm) ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    when (type) {
                                        com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA -> Icons.Rounded.Videocam
                                        com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE -> Icons.Rounded.Mic
                                        com.frerox.toolz.data.browser.BrowserPermissionType.NOTIFICATION -> Icons.Rounded.Notifications
                                        com.frerox.toolz.data.browser.BrowserPermissionType.GEOLOCATION -> Icons.Rounded.LocationOn
                                    },
                                    null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    when (type) {
                                        com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA -> "Camera"
                                        com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE -> "Microphone"
                                        com.frerox.toolz.data.browser.BrowserPermissionType.NOTIFICATION -> "Notifications"
                                        com.frerox.toolz.data.browser.BrowserPermissionType.GEOLOCATION -> "Location"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (perm == BrowserSitePermission.ALLOW) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        if (perm == BrowserSitePermission.ALLOW) "Allowed" else "Blocked",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold,
                                        color = if (perm == BrowserSitePermission.ALLOW) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Scroll-safe prominent reset button
            FilledTonalButton(
                onClick = {
                    onResetPermissions()
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset permissions for this site", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BrowserNavigationBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAddress: () -> Unit,
    onTabs: () -> Unit,
    onHome: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount > 25f) onSwipeDown()
                }
            },
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BrowserNavIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", true, onBack)
            BrowserNavIcon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", canGoForward, onForward)
            Surface(onClick = onAddress, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Search, "Address bar", modifier = Modifier.padding(13.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Surface(onClick = onTabs, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(tabCount.coerceAtLeast(1).toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                }
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onHome()
            }) { Icon(Icons.Rounded.Home, "Home") }
        }
    }
}

@Composable
private fun BrowserNavIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, label, tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserOptionsSheet(
    canGoForward: Boolean,
    isBookmarked: Boolean,
    isSavedForLater: Boolean,
    isPrivate: Boolean,
    isDesktopMode: Boolean,
    adBlockEnabled: Boolean,
    adScriptEnabled: Boolean = false,
    floatingToolbarVisible: Boolean,
    currentUrl: String = "",
    sitePermissions: Map<com.frerox.toolz.data.browser.BrowserPermissionType, BrowserSitePermission> = emptyMap(),
    onRevokePermission: (com.frerox.toolz.data.browser.BrowserPermissionType) -> Unit = {},
    onDismiss: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onReadingListToggle: () -> Unit,
    onToggleFind: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleAdScriptBlock: () -> Unit = {},
    onToggleFloatingToolbar: () -> Unit,
    onShowDownloads: () -> Unit,
    onShowPasswords: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onClosePrivateTabs: () -> Unit,
    onOpenReader: () -> Unit,
    onClearBrowsingData: () -> Unit,
    onResetSitePermissions: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Text(
                stringResource(R.string.st_WebViewScreen_e5f6),
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
                    label = stringResource(R.string.st_WebViewScreen_9e2c),
                    onClick = { onReload(); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                OptionQuickAction(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    label = stringResource(R.string.st_WebViewScreen_5f6e),
                    enabled = canGoForward,
                    onClick = { onForward(); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                OptionQuickAction(
                    icon = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    label = if (isBookmarked) stringResource(R.string.st_WebViewScreen_2b8a) else stringResource(R.string.st_WebViewScreen_4d9c),
                    active = isBookmarked,
                    onClick = { onBookmarkToggle(); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                OptionQuickAction(
                    icon = Icons.Rounded.AutoStories,
                    label = "Reader",
                    onClick = { onOpenReader(); onDismiss() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))
            OptionSectionTitle("View")

            // View section
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Column {
                    OptionToggleRow(
                        icon = Icons.Rounded.DesktopMac,
                        label = stringResource(R.string.st_WebViewScreen_1b2c),
                        checked = isDesktopMode,
                        onCheckedChange = { onToggleDesktop(); onDismiss() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OptionToggleRow(
                        icon = Icons.Rounded.ViewStream,
                        label = stringResource(R.string.st_WebViewScreen_5d6e),
                        checked = floatingToolbarVisible,
                        onCheckedChange = { onToggleFloatingToolbar(); onDismiss() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OptionActionRow(stringResource(R.string.st_AiAssistantScreen_9f0a), Icons.Rounded.Search, onToggleFind, onDismiss)
                }
            }

            Spacer(Modifier.height(20.dp))
            OptionSectionTitle("Privacy & Security")

            // Privacy section
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Column {
                    OptionToggleRow(
                        icon = Icons.Rounded.Shield,
                        label = stringResource(R.string.st_WebViewScreen_3c4d),
                        checked = adBlockEnabled,
                        onCheckedChange = { onToggleAdBlock(); onDismiss() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OptionToggleRow(
                        icon = Icons.Rounded.Code,
                        label = "Ad scripts block",
                        checked = adScriptEnabled,
                        onCheckedChange = { onToggleAdScriptBlock(); onDismiss() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OptionActionRow("New private tab", Icons.Rounded.VisibilityOff, onNewPrivateTab, onDismiss)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OptionActionRow("Close private tabs", Icons.Rounded.DeleteSweep, onClosePrivateTabs, onDismiss)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OptionActionRow("Clear browsing data", Icons.Rounded.DeleteSweep, onClearBrowsingData, onDismiss)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OptionActionRow(stringResource(R.string.st_WebViewScreen_6a1b), Icons.Rounded.Key, onShowPasswords, onDismiss)
                }
            }

            Spacer(Modifier.height(20.dp))

            Spacer(Modifier.height(20.dp))
            OptionSectionTitle("This Site")

            // Permission Card — shows current site granted permissions
            val host = remember(currentUrl) { com.frerox.toolz.ui.screens.search.components.safeHostFromUrl(currentUrl) }
            val hasAnyPermission = sitePermissions.isNotEmpty()
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Security, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Site permissions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(host.ifBlank { "Current site" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (hasAnyPermission) {
                            TextButton(onClick = { onResetSitePermissions(); onDismiss() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("Reset", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (!hasAnyPermission) {
                        Text("No special permissions granted. Camera, microphone, notifications and location will ask when needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sitePermissions.forEach { (type, perm) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        when (type) {
                                            com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA -> Icons.Rounded.Videocam
                                            com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE -> Icons.Rounded.Mic
                                            com.frerox.toolz.data.browser.BrowserPermissionType.NOTIFICATION -> Icons.Rounded.Notifications
                                            com.frerox.toolz.data.browser.BrowserPermissionType.GEOLOCATION -> Icons.Rounded.LocationOn
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        when (type) {
                                            com.frerox.toolz.data.browser.BrowserPermissionType.CAMERA -> "Camera"
                                            com.frerox.toolz.data.browser.BrowserPermissionType.MICROPHONE -> "Microphone"
                                            com.frerox.toolz.data.browser.BrowserPermissionType.NOTIFICATION -> "Notifications"
                                            com.frerox.toolz.data.browser.BrowserPermissionType.GEOLOCATION -> "Location"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (perm == BrowserSitePermission.ALLOW) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            if (perm == BrowserSitePermission.ALLOW) "Allowed" else "Blocked",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (perm == BrowserSitePermission.ALLOW) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    TextButton(onClick = { onRevokePermission(type) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text("Revoke", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            OptionSectionTitle("Actions")

            // Actions list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isPrivate) {
                    OptionActionRow(
                        if (isSavedForLater) "Remove from read later" else "Read later",
                        if (isSavedForLater) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd,
                        onReadingListToggle,
                        onDismiss,
                    )
                }
                OptionActionRow(stringResource(R.string.st_WebViewScreen_7e8f), Icons.Rounded.Download, onShowDownloads, onDismiss)
                OptionActionRow(stringResource(R.string.st_WebViewScreen_9f0a), Icons.Rounded.Share, onShare, onDismiss)
                OptionActionRow(stringResource(R.string.st_WebViewScreen_a1b2), Icons.Rounded.ContentCopy, onCopy, onDismiss)
                OptionActionRow(stringResource(R.string.st_WebViewScreen_c3d4), Icons.Rounded.OpenInBrowser, onOpenExternal, onDismiss)
            }
        }
    }
}

@Composable
private fun OptionSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
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
