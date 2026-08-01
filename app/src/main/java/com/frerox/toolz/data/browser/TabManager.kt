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

package com.frerox.toolz.data.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabManager @Inject constructor() {
    private val _tabs = MutableStateFlow<List<TabEntry>>(emptyList())
    val tabs: StateFlow<List<TabEntry>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    fun addTab(url: String, title: String = "New Tab") {
        val newTab = TabEntry(url = url, title = title)
        _tabs.value = _tabs.value + newTab
        _activeTabId.value = newTab.id
    }

    fun removeTab(tabId: String) {
        val currentTabs = _tabs.value
        if (currentTabs.size <= 1 && currentTabs.any { it.id == tabId }) {
            // Keep at least one tab or handle empty state
            _tabs.value = emptyList()
            _activeTabId.value = null
            return
        }
        
        val newTabs = currentTabs.filter { it.id != tabId }
        _tabs.value = newTabs
        
        if (_activeTabId.value == tabId) {
            _activeTabId.value = newTabs.lastOrNull()?.id
        }
    }

    fun switchTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
            // Update last accessed
            _tabs.value = _tabs.value.map {
                if (it.id == tabId) it.copy(lastAccessed = System.currentTimeMillis()) else it
            }
        }
    }

    fun updateTab(tabId: String, url: String? = null, title: String? = null, faviconUrl: String? = null, previewPath: String? = null, isDesktopMode: Boolean? = null) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) {
                it.copy(
                    url = url ?: it.url,
                    title = title ?: it.title,
                    faviconUrl = faviconUrl ?: it.faviconUrl,
                    previewPath = previewPath ?: it.previewPath,
                    isDesktopMode = isDesktopMode ?: it.isDesktopMode
                )
            } else it
        }
    }

    fun removeTabs(tabIds: Set<String>) {
        val currentTabs = _tabs.value
        val newTabs = currentTabs.filter { it.id !in tabIds }
        _tabs.value = newTabs
        
        if (_activeTabId.value in tabIds) {
            _activeTabId.value = newTabs.lastOrNull()?.id
        }
    }
}
