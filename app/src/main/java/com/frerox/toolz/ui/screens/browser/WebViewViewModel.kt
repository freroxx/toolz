package com.frerox.toolz.ui.screens.browser

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

@HiltViewModel
class WebViewViewModel @Inject constructor(
    private val application: Application,
    private val repository: WebSearchRepository,
    private val settingsRepository: SettingsRepository,
    private val tabManager: TabManager,
    private val passwordDao: PasswordDao
) : ViewModel() {

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked = _isBookmarked.asStateFlow()

    val adBlockEnabled = settingsRepository.searchAdBlockEnabled
    val dnsProvider = settingsRepository.searchDnsProvider
    val customDns = settingsRepository.searchCustomDns

    val tabs = tabManager.tabs
    val activeTabId = tabManager.activeTabId
    val autofillEnabled = settingsRepository.searchAutofillEnabled

    fun tryAutofill(activity: AppCompatActivity, url: String, onCredentials: (String, String) -> Unit) {
        viewModelScope.launch {
            if (!settingsRepository.searchAutofillEnabled.first()) return@launch

            val lastVerification = settingsRepository.lastBiometricVerificationTime.first()
            val now = System.currentTimeMillis()
            val cooldown = 5 * 60 * 1000L // 5 minutes

            if (now - lastVerification > cooldown) {
                BiometricPromptUtils.showBiometricPrompt(
                    activity = activity,
                    onSuccess = {
                        viewModelScope.launch {
                            settingsRepository.setLastBiometricVerificationTime(now)
                            performAutofillSearch(url, onCredentials)
                        }
                    }
                )
            } else {
                performAutofillSearch(url, onCredentials)
            }
        }
    }

    private suspend fun performAutofillSearch(url: String, onCredentials: (String, String) -> Unit) {
        val host = try { java.net.URI(url).host } catch (_: Exception) { null } ?: return
        val domain = if (host.startsWith("www.")) host.substring(4) else host
        
        val passwords = passwordDao.getPasswordsByDomain(domain)
        if (passwords.isNotEmpty()) {
            val bestMatch = passwords.first()
            onCredentials(bestMatch.username, bestMatch.password)
        }
    }

    fun updateTab(url: String? = null, title: String? = null, faviconUrl: String? = null, previewPath: String? = null) {
        val currentActiveId = tabManager.activeTabId.value
        if (currentActiveId != null) {
            tabManager.updateTab(currentActiveId, url, title, faviconUrl, previewPath)
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
}
