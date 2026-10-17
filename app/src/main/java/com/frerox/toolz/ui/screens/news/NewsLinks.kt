package com.frerox.toolz.ui.screens.news

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.frerox.toolz.R
import com.frerox.toolz.data.news.NewsConstants
import com.frerox.toolz.data.news.NewsEntity

/**
 * News link + share plumbing shared by the popup, the history screen and the
 * settings surface. Every call is exception-isolated — a broken link, a
 * missing browser or a chooser failure must never take the app down.
 */
object NewsLinks {

    /** Opens https URLs and `toolz://` deep links via the generic ACTION_VIEW resolver. */
    fun open(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
        }
    }

    /**
     * Share sheet: the bare, stable `#news-<id>` anchor on the website —
     * paste-anywhere friendly, no titles or captions, just the link.
     */
    fun share(context: Context, item: NewsEntity) {
        try {
            val link = "${NewsConstants.WEBSITE_NEWS_URL}#news-${item.id}"
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, link)
            }
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.st_News_share_chooser))
            )
        } catch (_: Exception) {
        }
    }
}
