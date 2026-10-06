package com.frerox.toolz.data.news

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.util.NotificationHelper

object NewsNotifier {
    const val GROUP_KEY = "toolz_news_group"
    const val SUMMARY_ID = 8099
    /** Stable per-item notification ID inside the 8100–8899 news band. */
    fun notificationId(itemId: String): Int =
        NotificationHelper.ID_NEWS_BASE + ((itemId.hashCode() and 0x7fffffff) % 800)

    /**
     * Stable per-item PendingIntent request code. Content uses 28100–28899,
     * dismiss uses 29100–29899 (both 800-wide, non-overlapping, unused
     * elsewhere). 800 slots match the notification band so View/Dismiss never
     * alias the wrong item after 100+ announcements (the old %100 did).
     */
    fun requestCode(itemId: String, base: Int): Int {
        val slot = (itemId.hashCode() and 0x7fffffff) % 800
        return base + slot
    }

    fun post(context: Context, item: NewsEntity) {
        try {
            NotificationHelper.createAllChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "toolz_news")
                putExtra("news_id", item.id)
            }
            val contentPi = PendingIntent.getActivity(
                context, requestCode(item.id, 28100),
                contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val dismissIntent = Intent(context, com.frerox.toolz.worker.NewsActionReceiver::class.java).apply {
                action = "com.frerox.toolz.NEWS_DISMISS"
                putExtra("news_id", item.id)
            }
            val dismissPi = PendingIntent.getBroadcast(
                context, requestCode(item.id, 29100),
                dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val preview = item.body.take(140)
            val notification = NotificationHelper.baseBuilder(context, NotificationHelper.CHANNEL_TOOLZ_NEWS)
                .setContentTitle(item.title)
                .setContentText(preview)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .setGroup(GROUP_KEY)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .addAction(R.drawable.ic_stat_toolz, "View", contentPi)
                .addAction(R.drawable.ic_stat_toolz, "Dismiss", dismissPi)
                .build()
            manager.notify(notificationId(item.id), notification)
        } catch (_: Exception) { /* never crash on news */ }
    }

    /** Group summary shown when 2+ news notifications are active. */
    fun postSummary(context: Context, count: Int, titles: List<String>) {
        try {
            if (count < 2) { cancelSummary(context); return }
            NotificationHelper.createAllChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "toolz_news")
            }
            val contentPi = PendingIntent.getActivity(
                context, 28099,
                contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle("$count new announcements")
            for (t in titles.take(5)) inbox.addLine(t)
            if (titles.size > 5) inbox.setSummaryText("+${titles.size - 5} more")
            val summary = NotificationHelper.baseBuilder(context, NotificationHelper.CHANNEL_TOOLZ_NEWS)
                .setContentTitle("$count new announcements")
                .setContentText(titles.firstOrNull() ?: "Toolz News")
                .setStyle(inbox)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .build()
            manager.notify(SUMMARY_ID, summary)
        } catch (_: Exception) { /* never crash on news */ }
    }

    fun cancelSummary(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.cancel(SUMMARY_ID)
        } catch (_: Exception) { /* ignore */ }
    }

    fun cancel(context: Context, itemId: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.cancel(notificationId(itemId))
        } catch (_: Exception) { /* ignore */ }
    }
}
