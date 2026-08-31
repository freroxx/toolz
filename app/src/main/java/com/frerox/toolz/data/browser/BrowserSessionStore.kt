package com.frerox.toolz.data.browser

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Durable, local-only browser session state. Private tabs deliberately never enter it. */
@Singleton
class BrowserSessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("browser_session_v2", Context.MODE_PRIVATE)

    fun restore(): List<TabEntry> = runCatching {
        val items = JSONArray(prefs.getString("tabs", "[]"))
        buildList {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                add(TabEntry(
                    id = item.getString("id"), url = item.getString("url"),
                    title = item.optString("title", "New tab"),
                    faviconUrl = item.optString("favicon").takeIf { it.isNotBlank() },
                    previewPath = item.optString("preview").takeIf { it.isNotBlank() },
                    isDesktopMode = item.optBoolean("desktop"),
                    createdAt = item.optLong("created", System.currentTimeMillis()),
                    lastAccessed = item.optLong("accessed", System.currentTimeMillis()),
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun restoreActiveTabId(): String? = prefs.getString("active", null)

    fun save(tabs: List<TabEntry>, activeTabId: String?) {
        val storedTabs = tabs.filterNot { it.isPrivate }.takeLast(30)
        val array = JSONArray()
        storedTabs.forEach { tab ->
            array.put(JSONObject().apply {
                put("id", tab.id); put("url", tab.url); put("title", tab.title)
                put("favicon", tab.faviconUrl ?: ""); put("preview", tab.previewPath ?: "")
                put("desktop", tab.isDesktopMode); put("created", tab.createdAt); put("accessed", tab.lastAccessed)
            })
        }
        prefs.edit().putString("tabs", array.toString()).putString("active", activeTabId).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
