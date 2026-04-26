package com.frerox.toolz.util

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.focus.AppLimitDao
import com.frerox.toolz.data.math.MathHistoryDao
import com.frerox.toolz.data.notepad.NoteDao
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.search.SearchDao
import com.frerox.toolz.data.todo.TaskDao
import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val noteDao: NoteDao,
    private val taskDao: TaskDao,
    private val searchDao: SearchDao,
    private val passwordDao: PasswordDao,
    private val appLimitDao: AppLimitDao,
    private val mathHistoryDao: MathHistoryDao,
    private val aiSettingsManager: AiSettingsManager,
    private val moshi: Moshi
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val backupData = mutableMapOf<String, Any>()
            backupData["notes"] = noteDao.getAllNotesSync()
            backupData["tasks"] = taskDao.getAllTasksSync()
            backupData["history"] = searchDao.getAllHistorySync()
            backupData["bookmarks"] = searchDao.getAllBookmarksSync()
            backupData["quickLinks"] = searchDao.getAllQuickLinksSync()
            backupData["passwords"] = passwordDao.getAllPasswordsSync()
            backupData["appLimits"] = appLimitDao.getAllLimitsSync()
            backupData["mathHistory"] = mathHistoryDao.getAllHistorySync()

            backupData["aiConfigs"] = aiSettingsManager.getSavedConfigs()
            val aiKeys = mutableMapOf<String, String>()
            listOf("Groq", "OpenAI", "Claude", "Gemini").forEach { provider ->
                val key = aiSettingsManager.getApiKey(provider)
                if (key.isNotBlank()) aiKeys[provider] = key
            }
            backupData["aiKeys"] = aiKeys

            val json = moshi.adapter(Map::class.java).toJson(backupData)
            val fileName = "toolz_backup_auto_${System.currentTimeMillis()}.json"
            
            val toolzDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Toolz/Backups")
            if (!toolzDir.exists()) toolzDir.mkdirs()
            
            val file = File(toolzDir, fileName)
            file.writeText(json)
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
