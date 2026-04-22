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

        val appNames = installedApps.map { it.first }.joinToString(", ")
        val prompt = """
            Categorize the following list of Android apps into exactly one of these categories: 
            'Gaming', 'School', 'Work', 'Social Media', 'Reading', 'Utility', 'Other'.
            Return ONLY a raw JSON array of objects with keys "name", "package", and "category".
            Do not include any other text, markdown blocks, or explanations.
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
                    // Clean-up response: some models wrap in markdown blocks
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

    suspend fun updateAppAutoEnable(app: CaffeinateApp, isEnabled: Boolean) {
        caffeinateDao.updateApp(app.copy(isAutoEnabled = isEnabled))
    }

    suspend fun getAutoEnabledPackages(): Set<String> {
        return caffeinateDao.getAutoEnabledApps().map { it.packageName }.toSet()
    }
}

data class AppCategoryInfo(
    val name: String,
    val `package`: String,
    val category: String
)
