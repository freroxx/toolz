package com.frerox.toolz.data.browser

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class BrowserHistoryItem(
    val url: String,
    val title: String,
    val visitedAt: Long,
    val visitCount: Int = 1,
)

/** Local-only browsing history, intentionally independent from search terms. */
@Singleton
class BrowserHistoryStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("browser_history_v1", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<BrowserHistoryItem>> = _items.asStateFlow()

    fun record(url: String, title: String, isPrivate: Boolean) {
        if (isPrivate || !url.startsWith("http")) return
        val existing = _items.value.firstOrNull { it.url == url }
        val item = BrowserHistoryItem(
            url = url,
            title = title.ifBlank { BrowserAddressResolver.displayHost(url) },
            visitedAt = System.currentTimeMillis(),
            visitCount = (existing?.visitCount ?: 0) + 1,
        )
        _items.value = (listOf(item) + _items.value.filterNot { it.url == url }).take(150)
        persist()
    }

    fun remove(url: String) {
        _items.value = _items.value.filterNot { it.url == url }
        persist()
    }

    fun clear() {
        _items.value = emptyList()
        persist()
    }

    private fun load(): List<BrowserHistoryItem> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(BrowserHistoryItem(
                    url = item.getString("url"),
                    title = item.optString("title"),
                    visitedAt = item.optLong("visitedAt"),
                    visitCount = item.optInt("visitCount", 1),
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun persist() {
        val array = JSONArray()
        _items.value.forEach { item -> array.put(JSONObject().apply {
            put("url", item.url); put("title", item.title); put("visitedAt", item.visitedAt); put("visitCount", item.visitCount)
        }) }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
