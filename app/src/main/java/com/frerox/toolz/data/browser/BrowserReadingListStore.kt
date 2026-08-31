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

data class BrowserReadingItem(
    val url: String,
    val title: String,
    val savedAt: Long = System.currentTimeMillis(),
)

/** A small, durable read-later list kept separate from permanent bookmarks. */
@Singleton
class BrowserReadingListStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("browser_reading_list_v1", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<BrowserReadingItem>> = _items.asStateFlow()

    fun contains(url: String) = _items.value.any { it.url == url }

    fun toggle(url: String, title: String): Boolean {
        val alreadySaved = contains(url)
        _items.value = if (alreadySaved) {
            _items.value.filterNot { it.url == url }
        } else {
            listOf(BrowserReadingItem(url, title.ifBlank { BrowserAddressResolver.displayHost(url) })) + _items.value
        }
        persist()
        return !alreadySaved
    }

    fun remove(url: String) {
        _items.value = _items.value.filterNot { it.url == url }
        persist()
    }

    private fun load(): List<BrowserReadingItem> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(BrowserReadingItem(item.getString("url"), item.optString("title"), item.optLong("savedAt")))
            }
        }
    }.getOrDefault(emptyList())

    private fun persist() {
        val array = JSONArray()
        _items.value.take(100).forEach { item -> array.put(JSONObject().apply {
            put("url", item.url); put("title", item.title); put("savedAt", item.savedAt)
        }) }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
