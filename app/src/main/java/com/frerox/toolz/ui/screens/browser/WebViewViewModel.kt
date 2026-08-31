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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.search.WebSearchRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked = _isBookmarked.asStateFlow()

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

    init {
        // Initialization handled by AdBlockManager
        // Ensure there's at least one tab if we are in browser
        if (tabManager.tabs.value.isEmpty()) {
            // We'll let the Screen call addTab with the initial URL if needed
        }
    }

    fun ensureTabExists(url: String) {
        if (tabManager.tabs.value.isEmpty()) {
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

    fun addTab(url: String, isPrivate: Boolean = false) {
        tabManager.addTab(url, isPrivate = isPrivate)
    }

    /** Resolves omnibox text using the user's selected search provider. */
    fun resolveAddress(input: String, onResolved: (String) -> Unit) {
        viewModelScope.launch {
            onResolved(BrowserAddressResolver.resolve(
                raw = input,
                engine = settingsRepository.searchEngine.first(),
                customTemplate = settingsRepository.searchCustomEngineUrl.first(),
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

    fun checkBookmark(url: String) {
        viewModelScope.launch {
            _isBookmarked.value = repository.isBookmarked(url)
        }
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
