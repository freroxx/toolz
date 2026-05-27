package com.frerox.toolz.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.content.pm.PackageManager
import android.util.Log
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.ai.MessageContent
import com.frerox.toolz.data.ai.OpenAiMessage
import com.frerox.toolz.data.ai.OpenAiRequest
import com.frerox.toolz.data.ai.OpenAiService
import com.frerox.toolz.data.notifications.NotificationDao
import com.frerox.toolz.data.notifications.NotificationEntry
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationVaultService : NotificationListenerService() {

    @Inject
    lateinit var notificationDao: NotificationDao

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var aiSettingsManager: AiSettingsManager

    @Inject
    lateinit var openAiService: OpenAiService

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        serviceScope.launch {
            val hiddenApps = settingsRepository.hiddenNotificationApps.first()
            if (hiddenApps.contains(packageName)) return@launch

            // Hide system and own app notifications
            if (packageName == "com.frerox.toolz") return@launch
            
            val isSystemApp = try {
                val ai = packageManager.getApplicationInfo(packageName, 0)
                (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (e: Exception) { false }

            if (isSystemApp && !packageName.contains("messaging") && !packageName.contains("phone") && !packageName.contains("contacts")) {
                // Keep system messaging/phone apps, but filter others
                if (packageName == "com.android.systemui" || 
                    packageName == "android" ||
                    packageName == "com.android.vending" ||
                    packageName.contains("system", ignoreCase = true) ||
                    packageName.contains("overlay", ignoreCase = true)
                ) return@launch
            }

            if (sbn.isOngoing && !packageName.contains("music") && !packageName.contains("player") && !packageName.contains("spotify")) {
                // Filter ongoing unless it's music
                return@launch
            }

            val notification = sbn.notification
            val extras = notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            
            if (title.isNullOrBlank() && text.isNullOrBlank()) return@launch
            
            // Filter by category
            val sysCategory = notification.category
            if (sysCategory == Notification.CATEGORY_PROGRESS || 
                sysCategory == Notification.CATEGORY_SERVICE ||
                sysCategory == Notification.CATEGORY_SYSTEM ||
                sysCategory == Notification.CATEGORY_TRANSPORT ||
                sysCategory == Notification.CATEGORY_NAVIGATION
            ) return@launch

            val appName = try {
                val pm = packageManager
                val ai = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (e: Exception) {
                packageName
            }

            val userMappings = settingsRepository.appCategoryMappings.first()
            var category = userMappings[packageName]

            if (category == null) {
                // Try hardcoded guess first for speed/offline
                val guess = guessCategory(packageName, sysCategory)
                if (guess != "General") {
                    category = guess
                    // Store the hardcoded guess forever too
                    settingsRepository.setAppCategoryMapping(packageName, category)
                } else {
                    // Try AI categorization for unknown apps
                    category = categorizeWithAi(packageName, appName)
                    settingsRepository.setAppCategoryMapping(packageName, category)
                }
            }

            val isSpam = checkSpam(packageName, title, text)

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

    private suspend fun categorizeWithAi(packageName: String, appName: String): String {
        val groqKey = aiSettingsManager.resolveApiKey("Groq").value
        if (groqKey.isBlank()) return "General"

        return try {
            val prompt = "Identify the most fitting category for the Android app '$appName' ($packageName). " +
                    "Return ONLY one word from this exact list: Social, Finance, Work, Shopping, Entertainment, Productivity, News, Tools, Travel, Education. " +
                    "If you are unsure or it doesn't fit, return 'General'."
            
            val request = OpenAiRequest(
                model = "llama-3.1-8b-instant",
                messages = listOf(
                    OpenAiMessage("system", MessageContent.Text("You are an expert at Android app categorization. You reply with exactly one word.")),
                    OpenAiMessage("user", MessageContent.Text(prompt))
                ),
                maxTokens = 12
            )

            val response = openAiService.getChatCompletion(
                url = "https://api.groq.com/openai/v1/chat/completions",
                authHeader = "Bearer $groqKey",
                request = request
            )
            
            val cat = response.choices.firstOrNull()?.message?.content?.trim()?.removeSuffix(".") ?: "General"
            
            // Validate against the allowed list to prevent hallucinations
            val valid = listOf("Social", "Finance", "Work", "Shopping", "Entertainment", "Productivity", "News", "Tools", "Travel", "Education", "General")
            valid.firstOrNull { it.equals(cat, ignoreCase = true) } ?: "General"
        } catch (e: Exception) {
            Log.e("NotificationVaultService", "AI Categorization failed for $packageName", e)
            "General"
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
            p.contains("paypal") || p.contains("binance") || p.contains("trading") ||
            p.contains("coinbase") || p.contains("metamask") -> "Finance"
            
            p.contains("mail") || p.contains("slack") || p.contains("teams") || 
            p.contains("office") || p.contains("outlook") || p.contains("gmail") || 
            p.contains("trello") || p.contains("asana") || p.contains("zoom") ||
            p.contains("linkedin") || p.contains("jira") || p.contains("confluence") -> "Work"
            
            systemCategory == Notification.CATEGORY_MESSAGE || systemCategory == Notification.CATEGORY_SOCIAL -> "Social"
            systemCategory == Notification.CATEGORY_EMAIL -> "Work"
            else -> "General"
        }
    }

    private fun checkSpam(packageName: String, title: String?, text: String?): Boolean {
        val spamKeywords = listOf("promo", "sale", "discount", "offer", "lottery", "prize", "win ", "free", "cashback", "advertisement")
        val combinedText = ((title ?: "") + " " + (text ?: "")).lowercase()
        return spamKeywords.any { combinedText.contains(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
