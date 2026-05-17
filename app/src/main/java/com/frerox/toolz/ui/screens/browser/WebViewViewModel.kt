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
import com.frerox.toolz.data.browser.DownloadItem

@HiltViewModel
class WebViewViewModel @Inject constructor(
    private val application: Application,
    private val repository: WebSearchRepository,
    private val settingsRepository: SettingsRepository,
    private val tabManager: TabManager,
    private val passwordDao: PasswordDao,
    private val downloadManager: BrowserDownloadManager
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
    val dnsProvider = settingsRepository.searchDnsProvider
    val customDns = settingsRepository.searchCustomDns

    val tabs = tabManager.tabs
    val activeTabId = tabManager.activeTabId
    val autofillEnabled = settingsRepository.searchAutofillEnabled

    val downloads = downloadManager.downloads

    init {
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
            val domain = if (host.startsWith("www.")) host.substring(4) else host

            // Smart check: keywords in URL or forced by DOM detection
            val isAuthPage = url.contains("login", ignoreCase = true) ||
                    url.contains("signin", ignoreCase = true) ||
                    url.contains("signup", ignoreCase = true) ||
                    url.contains("register", ignoreCase = true) ||
                    url.contains("auth", ignoreCase = true) ||
                    url.contains("account", ignoreCase = true) ||
                    force

            if (!isAuthPage) {
                _autofillSuggestions.value = emptyList()
                return@launch
            }

            val exactMatch = passwordDao.getPasswordsByDomain(host)
            val baseMatch = passwordDao.getPasswordsByDomain(domain)

            val combined = (exactMatch + baseMatch).distinctBy { it.id }
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

            if (now - lastVerification > cooldown) {
                BiometricPromptUtils.showBiometricPrompt(
                    activity = activity,
                    onSuccess = {
                        viewModelScope.launch {
                            settingsRepository.setLastBiometricVerificationTime(now)
                            onCredentials(password.username, password.password)
                            _autofillSuccess.value = true
                            _autofillSuggestions.value = emptyList()
                        }
                    }
                )
            } else {
                onCredentials(password.username, password.password)
                _autofillSuccess.value = true
                _autofillSuggestions.value = emptyList()
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
        tabManager.removeTab(id)
    }

    fun addTab(url: String) {
        tabManager.addTab(url)
    }

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
