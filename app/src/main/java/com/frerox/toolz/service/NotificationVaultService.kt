package com.frerox.toolz.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.content.pm.PackageManager
import com.frerox.toolz.data.notifications.NotificationDao
import com.frerox.toolz.data.notifications.NotificationEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationVaultService : NotificationListenerService() {

    @Inject
    lateinit var notificationDao: NotificationDao

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        // Stricter system notification filtering
        if (packageName == "com.frerox.toolz" || 
            packageName == "com.android.systemui" || 
            packageName == "android" ||
            packageName == "com.android.vending" || // Play Store
            packageName.contains("system") ||
            packageName.contains("overlay") ||
            sbn.isOngoing // Don't track persistent notifications like music players or services
        ) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        
        // Ignore empty notifications or those that are just progress bars/system info
        if (title.isNullOrBlank() && text.isNullOrBlank()) return
        if (sbn.notification.category == Notification.CATEGORY_PROGRESS || 
            sbn.notification.category == Notification.CATEGORY_SERVICE ||
            sbn.notification.category == Notification.CATEGORY_SYSTEM
        ) return

        val appName = try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        val category = guessCategory(packageName, sbn.notification.category)
        val isSpam = checkSpam(packageName, title, text)

        serviceScope.launch {
            val entry = NotificationEntry(
                packageName = packageName,
                appName = appName,
                title = title,
                text = text,
                timestamp = System.currentTimeMillis(),
                category = category,
                isSpam = isSpam
            )
            notificationDao.insert(entry)
        }
    }

    private fun guessCategory(packageName: String, systemCategory: String?): String {
        val p = packageName.lowercase()
        return when {
            p.contains("whatsapp") || p.contains("telegram") || p.contains("messenger") || 
            p.contains("instagram") || p.contains("facebook") || p.contains("twitter") || 
            p.contains("snapchat") || p.contains("discord") || p.contains("viber") ||
            p.contains("skype") || p.contains("tiktok") || p.contains("threads") -> "Social"
            
            p.contains("bank") || p.contains("pay") || p.contains("wallet") || 
            p.contains("crypto") || p.contains("finance") || p.contains("revolut") || 
            p.contains("paypal") || p.contains("binance") || p.contains("trading") -> "Finance"
            
            p.contains("mail") || p.contains("slack") || p.contains("teams") || 
            p.contains("office") || p.contains("outlook") || p.contains("gmail") || 
            p.contains("trello") || p.contains("asana") || p.contains("zoom") ||
            p.contains("linkedin") || p.contains("jira") -> "Work"
            
            systemCategory == Notification.CATEGORY_MESSAGE || systemCategory == Notification.CATEGORY_SOCIAL -> "Social"
            systemCategory == Notification.CATEGORY_EMAIL -> "Work"
            else -> "General"
        }
    }

    private fun checkSpam(packageName: String, title: String?, text: String?): Boolean {
        val spamKeywords = listOf("promo", "sale", "discount", "offer", "lottery", "prize", "win", "free", "cashback", "ad ", "advertise")
        val combinedText = ((title ?: "") + " " + (text ?: "")).lowercase()
        return spamKeywords.any { combinedText.contains(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
