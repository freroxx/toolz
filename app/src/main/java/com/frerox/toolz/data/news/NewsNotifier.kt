package com.frerox.toolz.data.news

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.util.NotificationHelper

object NewsNotifier {
    /** Stable per-item notification ID inside the 8100–8899 news band. */
    fun notificationId(itemId: String): Int =
        NotificationHelper.ID_NEWS_BASE + ((itemId.hashCode() and 0x7fffffff) % 800)

    /** Stable per-item PendingIntent request code (28100/28200 bands, unused elsewhere). */
    fun requestCode(itemId: String, base: Int): Int =
        base + ((itemId.hashCode() and 0x7fffffff) % 100)

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
                context, requestCode(item.id, 28200),
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
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .addAction(R.drawable.ic_stat_toolz, "View", contentPi)
                .addAction(R.drawable.ic_stat_toolz, "Dismiss", dismissPi)
                .build()
            manager.notify(notificationId(item.id), notification)
        } catch (_: Exception) { /* never crash on news */ }
    }

    fun cancel(context: Context, itemId: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.cancel(notificationId(itemId))
        } catch (_: Exception) { /* ignore */ }
    }
}
