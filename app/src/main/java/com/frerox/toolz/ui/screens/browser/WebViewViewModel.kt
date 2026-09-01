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

import com.frerox.toolz.data.password.PasswordEntity
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.search.WebSearchRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

import com.frerox.toolz.data.browser.TabManager
import com.frerox.toolz.data.browser.TabEntry
import android.app.Application
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.util.security.BiometricPromptUtils
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow

import com.frerox.toolz.data.browser.BrowserDownloadManager
import com.frerox.toolz.data.browser.BrowserAddressResolver
import com.frerox.toolz.data.browser.BrowserTabStateStore
import com.frerox.toolz.data.browser.BrowserHistoryStore
import com.frerox.toolz.data.browser.BrowserSitePermission
import com.frerox.toolz.data.browser.BrowserSitePermissionStore
import com.frerox.toolz.data.browser.BrowserReadingListStore
import com.frerox.toolz.data.browser.DownloadItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

@HiltViewModel
class WebViewViewModel @Inject constructor(
    private val application: Application,
    private val repository: WebSearchRepository,
    private val settingsRepository: SettingsRepository,
    private val tabManager: TabManager,
    private val passwordDao: PasswordDao,
    private val downloadManager: BrowserDownloadManager,
    private val tabStateStore: BrowserTabStateStore,
    private val browserHistoryStore: BrowserHistoryStore,
    private val sitePermissionStore: BrowserSitePermissionStore,
    private val readingListStore: BrowserReadingListStore,
) : ViewModel() {

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked = _isBookmarked.asStateFlow()
    private val _isSavedForLater = MutableStateFlow(false)
    val isSavedForLater = _isSavedForLater.asStateFlow()

    private val _autofillSuggestions = MutableStateFlow<List<PasswordEntity>>(emptyList())
    val autofillSuggestions = _autofillSuggestions.asStateFlow()

    private val _autofillSuccess = MutableStateFlow(false)
    val autofillSuccess = _autofillSuccess.asStateFlow()

    fun clearAutofillSuccess() {
        _autofillSuccess.value = false
    }

    val adBlockEnabled = settingsRepository.searchAdBlockEnabled
    val floatingToolbarVisible = settingsRepository.searchFloatingToolbarVisible
    val dnsProvider = settingsRepository.searchDnsProvider
    val customDns = settingsRepository.searchCustomDns

    val tabs = tabManager.tabs
    val activeTabId = tabManager.activeTabId
    val autofillEnabled = settingsRepository.searchAutofillEnabled
    val searchEngine = settingsRepository.searchEngine
    val customSearchEngineUrl = settingsRepository.searchCustomEngineUrl

    val downloads = downloadManager.downloads
    val browserHistory = browserHistoryStore.items
    val bookmarks = repository.bookmarks
    val readingList = readingListStore.items

    init {
        // Initialization handled by AdBlockManager
        // Ensure there's at least one tab if we are in browser
        if (tabManager.tabs.value.isEmpty()) {
            // We'll let the Screen call addTab with the initial URL if needed
        }
        cleanOrphanPreviews()
    }

    fun ensureTabExists(url: String) {
        val active = tabManager.activeTabId.value?.let { id -> tabManager.tabs.value.find { it.id == id } }
        if (active?.url == url) return
        if (tabManager.tabs.value.isEmpty() || url != "about:blank") {
            tabManager.addTab(url)
        }
    }

    fun findAutofillSuggestions(url: String, force: Boolean = false) {
        viewModelScope.launch {
            if (!settingsRepository.searchAutofillEnabled.first()) return@launch
            val host = try { java.net.URI(url).host } catch (_: Exception) { null } ?: return@launch
            val normHost = if (host.startsWith("www.")) host.substring(4) else host
            // REWRITE: remove URL keyword gate — trust JS detection force flag; if force==false we still query when DOM reports password fields.
            // Only skip when not forced and host empty — otherwise show candidates (fixes "isAuthPage false" bug where login forms on non-login URLs never surface)
            val shouldQuery = force || url.contains("login", true) || url.contains("signin", true) || url.contains("auth", true) || true // always query when JS says true, otherwise still try matching
            if (!shouldQuery) { _autofillSuggestions.value = emptyList(); return@launch }
            // Use precise matcher via DAO — fallback to LIKE for compat but de-duplicate with registrable domain walk
            val exactMatch = passwordDao.getPasswordsByDomain(host)
            val baseMatch = if (normHost != host) passwordDao.getPasswordsByDomain(normHost) else emptyList()
            // Additional registrable domain matches (e.g., accounts.google.com -> google.com entries)
            val regDomain = com.frerox.toolz.data.browser.autofill.CredentialMatcher().registrableDomain(host)
            val regMatch = if (regDomain != host && regDomain != normHost) passwordDao.getPasswordsByDomain(regDomain) else emptyList()
            val combined = (exactMatch + baseMatch + regMatch).distinctBy { it.id }
                .sortedWith(compareByDescending<PasswordEntity> { it.isComplete }.thenByDescending { it.lastUsedAt })
            _autofillSuggestions.value = combined
        }
    }

    fun clearAutofillSuggestions() {
        _autofillSuggestions.value = emptyList()
    }

    private val _manualPasswords = MutableStateFlow<List<PasswordEntity>>(emptyList())
    val manualPasswords: StateFlow<List<PasswordEntity>> = _manualPasswords

    fun findManualPasswords(url: String) {
        viewModelScope.launch {
            val host = try { java.net.URI(url).host } catch (_: Exception) { null } ?: return@launch
            val domain = if (host.startsWith("www.")) host.substring(4) else host

            val exactMatch = passwordDao.getPasswordsByDomain(host)
            val baseMatch = passwordDao.getPasswordsByDomain(domain)

            val combined = (exactMatch + baseMatch).distinctBy { it.id }
                .sortedByDescending { it.lastUsedAt }
            _manualPasswords.value = combined
        }
    }

    fun clearManualPasswords() {
        _manualPasswords.value = emptyList()
    }

    fun verifyBiometric(activity: AppCompatActivity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val lastVerification = settingsRepository.lastBiometricVerificationTime.first()
            val now = System.currentTimeMillis()
            val cooldown = 5 * 60 * 1000L

            if (now - lastVerification > cooldown) {
                BiometricPromptUtils.showBiometricPrompt(
                    activity = activity,
                    onSuccess = {
                        viewModelScope.launch {
                            settingsRepository.setLastBiometricVerificationTime(now)
                            onSuccess()
                        }
                    }
                )
            } else {
                onSuccess()
            }
        }
    }

    fun onCredentialSelected(activity: AppCompatActivity, password: PasswordEntity, onCredentials: (String, String) -> Unit) {
        viewModelScope.launch {
            val lastVerification = settingsRepository.lastBiometricVerificationTime.first()
            val now = System.currentTimeMillis()
            val cooldown = 5 * 60 * 1000L // 5 minutes
            suspend fun doFill(){
                try{ passwordDao.updateLastUsed(password.id, now) }catch(_:Exception){}
                onCredentials(password.username, password.password)
                _autofillSuccess.value = true
                _autofillSuggestions.value = emptyList()
            }
            if (now - lastVerification > cooldown) {
                BiometricPromptUtils.showBiometricPrompt(
                    activity = activity,
                    onSuccess = {
                        viewModelScope.launch {
                            settingsRepository.setLastBiometricVerificationTime(now)
                            doFill()
                        }
                    }
                )
            } else {
                doFill()
            }
        }
    }

    fun updateTab(url: String? = null, title: String? = null, faviconUrl: String? = null, previewPath: String? = null, isDesktopMode: Boolean? = null) {
        val currentActiveId = tabManager.activeTabId.value
        if (currentActiveId != null) {
            tabManager.updateTab(currentActiveId, url, title, faviconUrl, previewPath, isDesktopMode)
        }
    }

    fun toggleDesktopMode() {
        val currentActiveId = tabManager.activeTabId.value ?: return
        val currentTab = tabManager.tabs.value.find { it.id == currentActiveId } ?: return
        updateTab(isDesktopMode = !currentTab.isDesktopMode)
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSearchAdBlockEnabled(enabled)
        }
    }

    fun setFloatingToolbarVisible(visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSearchFloatingToolbarVisible(visible)
        }
    }

    fun closeTabs(ids: Set<String>) {
        tabStateStore.removeAll(ids)
        tabManager.removeTabs(ids)
    }

    fun saveTabPreview(tabId: String, bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val previewDir = File(application.cacheDir, "tab_previews")
                if (!previewDir.exists()) previewDir.mkdirs()

                val file = File(previewDir, "preview_$tabId.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                }
                tabManager.updateTab(tabId, previewPath = file.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** Removes the stored preview for a tab (e.g. when its URL changes so the thumbnail stays fresh). */
    fun invalidateTabPreview(tabId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = File(File(application.cacheDir, "tab_previews"), "preview_$tabId.jpg")
                if (file.exists()) file.delete()
                tabManager.updateTab(tabId, previewPath = null)
            }
        }
    }

    /** Deletes preview files that no longer belong to any open tab. */
    fun cleanOrphanPreviews() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val previewDir = File(application.cacheDir, "tab_previews")
                if (!previewDir.exists()) return@runCatching
                val liveIds = tabManager.tabs.value.mapTo(hashSetOf()) { it.id }
                previewDir.listFiles()?.forEach { file ->
                    val tabId = file.name.removePrefix("preview_").removeSuffix(".jpg")
                    if (tabId !in liveIds) file.delete()
                }
            }
        }
    }

    fun switchTab(id: String) {
        tabManager.switchTab(id)
    }

    fun closeTab(id: String) {
        tabStateStore.remove(id)
        tabManager.removeTab(id)
    }

    suspend fun fetchSuggestions(query: String): List<String> = repository.fetchSuggestions(query)

    fun addTab(url: String, isPrivate: Boolean = false): TabEntry =
        tabManager.addTab(url, isPrivate = isPrivate)

    /** Resolves omnibox text using the user's selected search provider. */
    fun resolveAddress(input: String, onResolved: (String) -> Unit) {
        viewModelScope.launch {
            onResolved(BrowserAddressResolver.resolve(
                raw = input,
                engine = settingsRepository.searchEngine.first(),
            ))
        }
    }

    fun clearPrivateTabs() {
        val privateIds = tabManager.tabs.value.filter { it.isPrivate }.mapTo(linkedSetOf()) { it.id }
        tabStateStore.removeAll(privateIds)
        tabManager.clearPrivateTabs()
    }

    fun captureTabState(tabId: String, webView: android.webkit.WebView) =
        tabStateStore.capture(tabId, webView)

    fun restoreTabState(tabId: String, webView: android.webkit.WebView): Boolean =
        tabStateStore.restore(tabId, webView)

    fun recordPageVisit(url: String, title: String) {
        browserHistoryStore.record(url, title, tabManager.tabs.value.find { it.id == tabManager.activeTabId.value }?.isPrivate == true)
    }

    /** A single, predictable privacy boundary for all browser-owned local data. */
    fun clearBrowsingData(webView: WebView, onFinished: () -> Unit = {}) {
        webView.apply {
            clearHistory()
            clearCache(true)
            clearFormData()
        }
        browserHistoryStore.clear()
        sitePermissionStore.clearAll()
        tabStateStore.clear()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            onFinished()
        }
    }

    fun sitePermission(origin: String): BrowserSitePermission = sitePermissionStore.decision(origin)
    fun sitePermission(origin: String, type: com.frerox.toolz.data.browser.BrowserPermissionType): BrowserSitePermission = sitePermissionStore.decisionFor(origin, type)
    fun setSitePermission(origin: String, decision: BrowserSitePermission) = sitePermissionStore.setDecision(origin, decision)
    fun setSitePermission(origin: String, type: com.frerox.toolz.data.browser.BrowserPermissionType, decision: BrowserSitePermission) = sitePermissionStore.setDecisionFor(origin, type, decision)
    fun resetSitePermission(origin: String) = sitePermissionStore.clear(origin)
    fun clearAllSitePermissions() = sitePermissionStore.clearAll()
    fun getAllSitePermissions(): Map<String, Map<com.frerox.toolz.data.browser.BrowserPermissionType, BrowserSitePermission>> = sitePermissionStore.getAllPermissions()
    fun getPermissionsForOrigin(origin: String): Map<com.frerox.toolz.data.browser.BrowserPermissionType, BrowserSitePermission> = sitePermissionStore.getPermissionsForOrigin(origin)

    fun checkBookmark(url: String) {
        viewModelScope.launch {
            _isBookmarked.value = repository.isBookmarked(url)
        }
    }

    fun checkReadingList(url: String) { _isSavedForLater.value = readingListStore.contains(url) }

    fun toggleReadingList(title: String, url: String) {
        _isSavedForLater.value = readingListStore.toggle(url, title)
    }

    fun toggleBookmark(title: String, url: String) {
        viewModelScope.launch {
            if (_isBookmarked.value) {
                repository.removeBookmark(url)
                _isBookmarked.value = false
            } else {
                repository.addBookmark(title, url)
                _isBookmarked.value = true
            }
        }
    }

    fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        downloadManager.startDownload(url, userAgent, contentDisposition, mimeType)
    }

    fun refreshDownloads() {
        downloadManager.refreshDownloads()
    }

    fun deleteDownload(item: DownloadItem) {
        downloadManager.deleteDownload(item)
    }
}
