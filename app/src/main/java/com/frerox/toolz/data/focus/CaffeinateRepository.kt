package com.frerox.toolz.data.focus

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.frerox.toolz.data.ai.AiMessage
import com.frerox.toolz.data.ai.ChatRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaffeinateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val caffeinateDao: CaffeinateDao,
    private val aiRepository: ChatRepository,
    private val moshi: Moshi
) {
    private val TAG = "CaffeinateRepo"

    val allApps = caffeinateDao.getAllApps()

    suspend fun refreshAppsWithAi() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 } // Only user apps
            .map { it.loadLabel(pm).toString() to it.packageName }

        if (installedApps.isEmpty()) return@withContext

        // Pre-categorize locally to ensure we have something if AI fails
        val localEntities = installedApps.map { (name, pkg) ->
            CaffeinateApp(
                packageName = pkg,
                appName = name,
                category = localCategorize(name, pkg)
            )
        }
        caffeinateDao.insertApps(localEntities)

        val appNames = installedApps.map { it.first }.joinToString(", ")
        val prompt = """
            Categorize the following list of Android apps into exactly one of these categories: 
            'Gaming', 'Social', 'Work', 'Reading', 'Video', 'Utility', 'Other'.
            
            Return ONLY a raw JSON array of objects. 
            Format: [{"name": "App Name", "package": "com.pkg", "category": "Category"}]
            
            Apps: $appNames
        """.trimIndent()

        aiRepository.getChatResponse(
            prompt = prompt,
            history = emptyList(),
            image = null,
            modelOverride = "llama-3.3-70b-versatile"
        ).collect { result ->
            result.onSuccess { response ->
                try {
                    val cleanedResponse = response.text.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    val jsonStart = cleanedResponse.indexOf("[")
                    val jsonEnd = cleanedResponse.lastIndexOf("]") + 1
                    
                    if (jsonStart != -1 && jsonEnd != -1) {
                        val jsonString = cleanedResponse.substring(jsonStart, jsonEnd)
                        val type = Types.newParameterizedType(List::class.java, AppCategoryInfo::class.java)
                        val adapter = moshi.adapter<List<AppCategoryInfo>>(type)
                        val categorizedApps = adapter.fromJson(jsonString)
                        
                        categorizedApps?.let { list ->
                            val entities = list.map { info ->
                                val pkg = installedApps.find { it.first == info.name }?.second ?: info.`package`
                                CaffeinateApp(
                                    packageName = pkg,
                                    appName = info.name,
                                    category = info.category
                                )
                            }
                            caffeinateDao.insertApps(entities)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing AI response", e)
                }
            }.onFailure {
                Log.e(TAG, "AI categorization failed", it)
            }
        }
    }

    private fun localCategorize(name: String, pkg: String): String {
        val lower = name.lowercase() + " " + pkg.lowercase()
        return when {
            lower.contains("game") || lower.contains("play") || lower.contains("pubg") -> "Gaming"
            lower.contains("social") || lower.contains("insta") || lower.contains("face") || lower.contains("twitter") || lower.contains("messeng") -> "Social"
            lower.contains("work") || lower.contains("office") || lower.contains("slack") || lower.contains("team") || lower.contains("meet") -> "Work"
            lower.contains("read") || lower.contains("book") || lower.contains("kindle") || lower.contains("news") -> "Reading"
            lower.contains("video") || lower.contains("tube") || lower.contains("netflix") || lower.contains("prime") -> "Video"
            else -> "Utility"
        }
    }

    suspend fun updateAppAutoEnable(app: CaffeinateApp, isEnabled: Boolean) {
        caffeinateDao.updateApp(app.copy(isAutoEnabled = isEnabled))
    }

    suspend fun getAutoEnabledPackages(): Set<String> {
        return caffeinateDao.getAutoEnabledApps().map { it.packageName }.toSet()
    }

    suspend fun manualAddApp(packageName: String, category: String) = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        try {
            val info = pm.getApplicationInfo(packageName, 0)
            val name = pm.getApplicationLabel(info).toString()
            caffeinateDao.insertApps(listOf(CaffeinateApp(packageName, name, category, true)))
        } catch (e: Exception) {
            Log.e(TAG, "Manual add failed", e)
        }
    }
}

data class AppCategoryInfo(
    val name: String,
    val `package`: String,
    val category: String
)
