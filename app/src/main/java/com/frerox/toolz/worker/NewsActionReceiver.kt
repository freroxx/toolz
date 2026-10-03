package com.frerox.toolz.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frerox.toolz.data.news.NewsNotifier
import com.frerox.toolz.data.news.NewsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NewsActionReceiver : BroadcastReceiver() {
    @Inject lateinit var newsRepository: NewsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.frerox.toolz.NEWS_DISMISS") return
        val id = intent.getStringExtra("news_id") ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                newsRepository.dismiss(id)
            } catch (_: Exception) { /* ignore */ }
        }
        // Dismissing must also clear the shade row (same stable ID as post()).
        NewsNotifier.cancel(context, id)
    }
}
